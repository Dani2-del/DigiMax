"""Compara variantes de CatBoost para el objetivo elegido de 30 dias.

Selecciona configuracion usando solo los cortes antiguos y reserva los dos
cortes mas recientes como una prueba temporal final. No escribe en Atlas.
"""
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score, roc_auc_score

from evaluar_horizontes import (
    ANCLA,
    DIAS_VALIDACION,
    MIN_CORTES,
    NUM_CORTES,
    PASO_SEMANAS,
    construir_cortes_temporales,
    precision_en_top,
)
from modelo_predictivo_config import MODELO_CATBOOST, VARIABLES, crear_modelo, transformar_variables

TABLA = "tabla_usuario_semana_horizontes_v1.csv"
SALIDA = "experimento_catboost_30d.csv"
HORIZONTE = 30
CORTES_RESERVADOS = 2

# El primer candidato reproduce exactamente los parametros actuales.
CANDIDATOS = {
    "base_actual": {},
    "depth5_regularizado": {
        "iterations": 700,
        "depth": 5,
        "learning_rate": 0.04,
        "l2_leaf_reg": 10.0,
    },
    "depth4_regularizado": {
        "iterations": 700,
        "depth": 4,
        "learning_rate": 0.05,
        "l2_leaf_reg": 12.0,
    },
    "depth6_mas_arboles": {
        "iterations": 800,
        "depth": 6,
        "learning_rate": 0.03,
        "l2_leaf_reg": 12.0,
    },
}


def cargar_tabla(ruta):
    columnas = ["semana", "tarjeta_365d", "tarjeta_historica", "y_30d"] + VARIABLES
    tabla = pd.read_csv(ruta, usecols=columnas, parse_dates=["semana"])
    tabla["semana"] = pd.to_datetime(tabla["semana"], errors="coerce")
    tabla = tabla.dropna(subset=["semana"])
    tabla = tabla[
        (tabla["tarjeta_historica"] >= 2)
        & (tabla["tarjeta_365d"] >= 1)
    ].copy()
    semanas_desde_ancla = ((tabla["semana"] - ANCLA).dt.days // 7).astype(int)
    tabla = tabla[(semanas_desde_ancla % PASO_SEMANAS) == 0].copy()
    if not np.isfinite(tabla[VARIABLES].to_numpy(dtype=np.float32)).all():
        raise SystemExit("Hay valores vacios o no numericos en las variables del modelo.")
    return tabla


def medir_modelo(nombre, parametros, train, valid, fase, corte):
    y_train = train["y_30d"].to_numpy(dtype=np.int8)
    y_valid = valid["y_30d"].to_numpy(dtype=np.int8)
    if np.unique(y_train).size < 2 or np.unique(y_valid).size < 2:
        raise SystemExit(f"{corte}: hacen falta positivos y negativos en entrenamiento y validacion.")

    x_train = transformar_variables(train[VARIABLES].to_numpy(dtype=np.float32), VARIABLES)
    x_valid = transformar_variables(valid[VARIABLES].to_numpy(dtype=np.float32), VARIABLES)
    modelo = crear_modelo(MODELO_CATBOOST, y_train, parametros=parametros)
    modelo.fit(x_train, y_train)
    puntuaciones = modelo.predict_proba(x_valid)[:, 1]
    p10, r10, f10, n10, positivos10 = precision_en_top(y_valid, puntuaciones, 0.10)
    resultado = {
        "fase": fase,
        "configuracion": nombre,
        "corte": corte,
        "inicio_validacion": valid["semana"].min().strftime("%Y-%m-%d"),
        "fin_validacion": valid["semana"].max().strftime("%Y-%m-%d"),
        "filas_entrenamiento": len(train),
        "filas_validacion": len(valid),
        "prevalencia": float(y_valid.mean()),
        "auc_pr": float(average_precision_score(y_valid, puntuaciones)),
        "auc_roc": float(roc_auc_score(y_valid, puntuaciones)),
        "precision_top_10": p10,
        "recall_top_10": r10,
        "f1_top_10": f10,
        "positivos_top_10": positivos10,
        "pasajeros_top_10": n10,
    }
    print(
        f"{fase:>8} | {nombre:<24} | {corte:<8} | "
        f"AUC-PR={resultado['auc_pr']:.4f} | "
        f"precision top 10%={p10:.1%} | recall top 10%={r10:.1%}",
        flush=True,
    )
    return resultado


def main():
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tabla", default=TABLA)
    parser.add_argument("--salida", default=SALIDA)
    args = parser.parse_args()
    if not Path(args.tabla).is_file():
        raise SystemExit(f"Falta {args.tabla}; primero ejecuta el flujo con -SoloEvaluar.")

    tabla = cargar_tabla(args.tabla)
    cortes = construir_cortes_temporales(
        tabla, HORIZONTE, NUM_CORTES, DIAS_VALIDACION
    )
    if len(cortes) <= CORTES_RESERVADOS:
        raise SystemExit("No hay cortes suficientes para comparar y reservar una prueba final.")

    desarrollo = cortes[:-CORTES_RESERVADOS]
    prueba = cortes[-CORTES_RESERVADOS:]
    if len(desarrollo) < MIN_CORTES:
        raise SystemExit(
            f"Se necesitan al menos {MIN_CORTES} cortes antiguos para elegir parametros; "
            f"hay {len(desarrollo)}. Reduce --cortes reservados solo si tienes otra prueba futura."
        )

    resultados = []
    for nombre, parametros in CANDIDATOS.items():
        for corte, train, valid in desarrollo:
            resultados.append(medir_modelo(
                nombre, parametros, train, valid, "desarrollo", corte
            ))

    resumen_desarrollo = (
        pd.DataFrame(resultados)
        .groupby("configuracion", as_index=False)
        .agg(
            auc_pr_media=("auc_pr", "mean"),
            precision_top_10_media=("precision_top_10", "mean"),
            recall_top_10_media=("recall_top_10", "mean"),
        )
        .sort_values(
            ["auc_pr_media", "precision_top_10_media"],
            ascending=False,
            kind="mergesort",
        )
        .reset_index(drop=True)
    )
    ganador = str(resumen_desarrollo.iloc[0]["configuracion"])
    print("\nPromedios de desarrollo (los dos cortes recientes siguen reservados):")
    print(resumen_desarrollo.to_string(index=False, float_format=lambda x: f"{x:.4f}"))
    print(f"\nConfiguracion elegida para la prueba final: {ganador}", flush=True)

    # Un solo entrenamiento previo al inicio del periodo reservado; el modelo
    # no aprende de etiquetas de ninguno de los dos cortes de prueba.
    entrenamiento_prueba = prueba[0][1]
    validacion_prueba = pd.concat([corte[2] for corte in prueba], ignore_index=True)
    nombres_prueba = ",".join(corte[0] for corte in prueba)
    for nombre in dict.fromkeys(("base_actual", ganador)):
        resultados.append(medir_modelo(
            nombre,
            CANDIDATOS[nombre],
            entrenamiento_prueba,
            validacion_prueba,
            "prueba_final",
            nombres_prueba,
        ))

    salida = pd.DataFrame(resultados)
    salida.to_csv(args.salida, index=False)
    prueba_resumen = salida[salida["fase"] == "prueba_final"].set_index("configuracion")
    base = prueba_resumen.loc["base_actual"]
    candidato = prueba_resumen.loc[ganador]
    delta_auc = float(candidato["auc_pr"] - base["auc_pr"])
    delta_precision = float(candidato["precision_top_10"] - base["precision_top_10"])
    delta_recall = float(candidato["recall_top_10"] - base["recall_top_10"])
    print("\nComparacion en el periodo final reservado:")
    print(
        f"AUC-PR: {base['auc_pr']:.4f} -> {candidato['auc_pr']:.4f} "
        f"({delta_auc:+.4f})"
    )
    print(
        f"Precision top 10%: {base['precision_top_10']:.1%} -> "
        f"{candidato['precision_top_10']:.1%} ({delta_precision:+.1%})"
    )
    print(
        f"Recall top 10%: {base['recall_top_10']:.1%} -> "
        f"{candidato['recall_top_10']:.1%} ({delta_recall:+.1%})"
    )
    if ganador != "base_actual" and delta_auc > 0 and delta_precision >= -0.02:
        print(f"Resultado: {ganador} mejora AUC-PR y conserva precision top 10% (caida maxima 2 puntos).")
    else:
        print("Resultado: conservar los parametros actuales; la variante no supera el criterio de promocion.")
    print(f"Informe guardado en {args.salida}. No se escribio en Atlas.")


if __name__ == "__main__":
    main()
