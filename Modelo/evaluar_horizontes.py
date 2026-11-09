"""Evalua recargas a 7, 14 y 30 dias con cortes temporales moviles.

Usa CatBoost, la misma cohorte de pasajeros activos y validacion walk-forward.
Las ventanas de validacion se calculan desde las etiquetas completas mas
recientes, para que el flujo siga evaluando periodos nuevos al refrescar Atlas.
"""
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score, roc_auc_score

from modelo_predictivo_config import (
    MODELO_CATBOOST,
    VARIABLES,
    crear_modelo,
    transformar_variables,
)

TABLA = "tabla_usuario_semana_horizontes_v1.csv"
SALIDA = "evaluacion_horizontes_v1.csv"
HORIZONTES = (7, 14, 30)
PASO_SEMANAS = 5
ANCLA = pd.Timestamp("2024-01-01")
NUM_CORTES = 6
DIAS_VALIDACION = 90
MIN_CORTES = 3


def precision_en_top(y, probabilidades, proporcion):
    n = max(1, int(np.ceil(len(y) * proporcion)))
    mejores = np.argsort(-probabilidades, kind="mergesort")[:n]
    positivos = int(np.asarray(y)[mejores].sum())
    total_positivos = int(np.asarray(y).sum())
    precision = positivos / n
    recall = positivos / total_positivos if total_positivos else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return precision, recall, f1, n, positivos


def validar_fechas(nombre, train, valid, horizonte):
    if train.empty or valid.empty:
        raise SystemExit(f"{nombre}: no hay filas para entrenamiento o validacion.")
    if train["semana"].max() + pd.Timedelta(days=horizonte) >= valid["semana"].min():
        raise SystemExit(
            f"{nombre}: las etiquetas de entrenamiento a {horizonte} dias alcanzan la validacion; "
            "ajusta el embargo temporal."
        )


def construir_cortes_temporales(tabla, horizonte, numero_cortes, dias_validacion):
    """Crea ventanas recientes no solapadas y deja embargo igual al horizonte."""
    columna_objetivo = f"y_{horizonte}d"
    etiquetadas = tabla[tabla[columna_objetivo].notna()].copy()
    if etiquetadas.empty:
        raise SystemExit(f"No hay etiquetas completas para el horizonte de {horizonte} dias.")

    ultima_semana = etiquetadas["semana"].max()
    ancho = pd.Timedelta(days=dias_validacion)
    horizonte_td = pd.Timedelta(days=horizonte)
    cortes = []

    # Se recorren primero las ventanas más recientes; se omiten solo las que
    # todavía no tienen suficiente historia de entrenamiento.
    for desplazamiento in range(numero_cortes):
        fin_valid = ultima_semana - desplazamiento * ancho
        inicio_valid = fin_valid - pd.Timedelta(days=dias_validacion - 1)
        fin_train = inicio_valid - horizonte_td - pd.Timedelta(days=1)

        train = etiquetadas[etiquetadas["semana"] <= fin_train]
        valid = etiquetadas[
            (etiquetadas["semana"] >= inicio_valid)
            & (etiquetadas["semana"] <= fin_valid)
        ]
        if train.empty or valid.empty:
            continue

        nombre = f"corte_{numero_cortes - desplazamiento:02d}"
        validar_fechas(nombre, train, valid, horizonte)
        cortes.append((nombre, train, valid))

    cortes.reverse()
    if len(cortes) < MIN_CORTES:
        raise SystemExit(
            f"Solo se pudieron crear {len(cortes)} cortes completos de {dias_validacion} dias "
            f"para {horizonte} dias. Se requieren al menos {MIN_CORTES}; "
            "se necesita mas historial etiquetado en Atlas o una ventana de validacion menor."
        )
    return cortes


def main():
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tabla", default=TABLA)
    parser.add_argument("--salida", default=SALIDA)
    parser.add_argument("--cortes", type=int, default=NUM_CORTES,
                        help=f"numero de ventanas temporales recientes (por defecto {NUM_CORTES})")
    parser.add_argument("--dias-validacion", type=int, default=DIAS_VALIDACION,
                        help=f"duracion de cada ventana (por defecto {DIAS_VALIDACION})")
    args = parser.parse_args()

    if args.cortes < MIN_CORTES:
        raise SystemExit(f"--cortes debe ser al menos {MIN_CORTES}.")
    if args.dias_validacion < 35:
        raise SystemExit("--dias-validacion debe ser al menos 35 para cubrir las fotos cada cinco semanas.")

    if not Path(args.tabla).is_file():
        raise SystemExit(
            f"Falta {args.tabla}. Genera la tabla separada con --horizontes 7,14,30."
        )
    columnas = ["semana", "usuario_id", "tarjeta_365d", "tarjeta_historica"]
    columnas += [f"y_{h}d" for h in HORIZONTES] + VARIABLES
    tabla = pd.read_csv(args.tabla, usecols=columnas, parse_dates=["semana"])
    tabla["semana"] = pd.to_datetime(tabla["semana"], errors="coerce")
    tabla = tabla.dropna(subset=["semana"])

    # Predice recurrencia entre pasajeros con historial, activos en los 365
    # dias previos. La elegibilidad solo consulta informacion disponible en T.
    tabla = tabla[(tabla["tarjeta_historica"] >= 2) & (tabla["tarjeta_365d"] >= 1)].copy()
    semanas_desde_ancla = ((tabla["semana"] - ANCLA).dt.days // 7).astype(int)
    tabla = tabla[(semanas_desde_ancla % PASO_SEMANAS) == 0].copy()

    if not np.isfinite(tabla[VARIABLES].to_numpy(dtype=np.float32)).all():
        raise SystemExit("Hay valores vacios o no numericos en las variables del modelo.")

    filas = []
    for horizonte in HORIZONTES:
        cortes = construir_cortes_temporales(
            tabla, horizonte, args.cortes, args.dias_validacion
        )
        for nombre_corte, train, valid in cortes:
            y_train = train[f"y_{horizonte}d"].to_numpy(dtype=np.int8)
            y_valid = valid[f"y_{horizonte}d"].to_numpy(dtype=np.int8)
            if np.unique(y_train).size < 2 or np.unique(y_valid).size < 2:
                raise SystemExit(
                    f"{nombre_corte}, {horizonte}d: hacen falta positivos y negativos "
                    "en entrenamiento y validacion."
                )

            x_train = transformar_variables(train[VARIABLES].to_numpy(dtype=np.float32), VARIABLES)
            x_valid = transformar_variables(valid[VARIABLES].to_numpy(dtype=np.float32), VARIABLES)
            modelo = crear_modelo(MODELO_CATBOOST, y_train)
            modelo.fit(x_train, y_train)
            p_valid = modelo.predict_proba(x_valid)[:, 1]

            p5, r5, f5, n5, pos5 = precision_en_top(y_valid, p_valid, 0.05)
            p10, r10, f10, n10, pos10 = precision_en_top(y_valid, p_valid, 0.10)
            p20, r20, f20, n20, pos20 = precision_en_top(y_valid, p_valid, 0.20)
            fila = {
                "corte": nombre_corte,
                "horizonte_dias": horizonte,
                "modelo": MODELO_CATBOOST,
                "filas_entrenamiento": len(train),
                "filas_validacion": len(valid),
                "semanas_validacion": int(valid["semana"].nunique()),
                "inicio_validacion": valid["semana"].min().strftime("%Y-%m-%d"),
                "fin_validacion": valid["semana"].max().strftime("%Y-%m-%d"),
                "prevalencia_validacion": float(y_valid.mean()),
                "auc_roc": float(roc_auc_score(y_valid, p_valid)),
                "auc_pr": float(average_precision_score(y_valid, p_valid)),
                "precision_top_5": p5,
                "recall_top_5": r5,
                "f1_top_5": f5,
                "pasajeros_top_5": n5,
                "positivos_top_5": pos5,
                "precision_top_10": p10,
                "recall_top_10": r10,
                "f1_top_10": f10,
                "pasajeros_top_10": n10,
                "positivos_top_10": pos10,
                "precision_top_20": p20,
                "recall_top_20": r20,
                "f1_top_20": f20,
                "pasajeros_top_20": n20,
                "positivos_top_20": pos20,
            }
            filas.append(fila)
            print(
                f"{horizonte:>2} dias | {nombre_corte}: base={fila['prevalencia_validacion']:.1%} | "
                f"AUC-PR={fila['auc_pr']:.4f} | top 10%: precision={p10:.1%}, "
                f"recall={r10:.1%}, F1={f10:.4f} ({pos10}/{n10})",
                flush=True,
            )

    detalle = pd.DataFrame(filas)
    resumen = detalle.groupby("horizonte_dias", as_index=False).agg(
        cortes=("corte", "count"),
        auc_roc_media=("auc_roc", "mean"),
        auc_pr_media=("auc_pr", "mean"),
        precision_top_5_media=("precision_top_5", "mean"),
        recall_top_5_media=("recall_top_5", "mean"),
        f1_top_5_media=("f1_top_5", "mean"),
        precision_top_10_media=("precision_top_10", "mean"),
        recall_top_10_media=("recall_top_10", "mean"),
        f1_top_10_media=("f1_top_10", "mean"),
        precision_top_20_media=("precision_top_20", "mean"),
        recall_top_20_media=("recall_top_20", "mean"),
        prevalencia_media=("prevalencia_validacion", "mean"),
    )
    resumen.insert(0, "corte", "promedio_cortes")
    pd.concat([detalle, resumen], ignore_index=True, sort=False).to_csv(args.salida, index=False)

    print("\nPromedio de cortes walk-forward; top-K es una politica relativa, no precision individual:")
    print(resumen[[
        "horizonte_dias", "prevalencia_media", "auc_pr_media",
        "precision_top_5_media", "recall_top_5_media", "f1_top_5_media",
        "precision_top_10_media", "recall_top_10_media", "f1_top_10_media",
    ]].to_string(index=False, float_format=lambda x: f"{x:.4f}"))
    print(f"\nCohorte activa: al menos 2 recargas historicas y 1 en los 365 dias previos.")
    print(f"Fotos cada {PASO_SEMANAS * 7} dias; ventanas de validacion de {args.dias_validacion} dias.")
    print(f"Embargo temporal igual al horizonte evaluado; no se publico en Atlas. Archivo: {args.salida}")


if __name__ == "__main__":
    main()
