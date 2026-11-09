"""Entrena y publica en Atlas el objetivo de recarga dentro de 30 dias.

Lee una tabla multihorizonte con etiquetas completas, ajusta CatBoost sobre
pasajeros activos y reemplaza la proyeccion vigente en las colecciones Atlas
que ya consume la consola administrativa.
"""
import argparse
import getpass
import os
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

import numpy as np
import pandas as pd

from modelo_predictivo_config import MODELO_CATBOOST, VARIABLES, crear_modelo, transformar_variables

ARCHIVO = "tabla_usuario_semana_horizontes_v1.csv"
ARCHIVO_EVALUACION = "evaluacion_horizontes_v1.csv"
SALIDA_PRED = "predicciones_recarga.csv"
SALIDA_MET = "modelo_metricas.csv"
OBJETIVO = "recarga_billetera_en_30_dias"
VERSION = "recarga_historial_consumo_transporte_v11_horizonte30_catboost_top5"
HORIZONTE = 30
INICIO_ENTRENAMIENTO = pd.Timestamp("2024-01-01")
ANCLA_MUESTREO = pd.Timestamp("2024-01-01")
PASO_SEMANAS = 5
PORC_ALTO = 0.05
PORC_MEDIO = 0.35


def validar_uri_atlas(uri):
    uri = uri.strip().strip('"').strip("'")
    try:
        partes = urlsplit(uri)
        host = partes.hostname or ""
        hosts = [parte.rsplit("@", 1)[-1].split(":", 1)[0] for parte in partes.netloc.split(",")]
    except ValueError:
        partes, host, hosts = None, "", []
    if (partes is None or partes.scheme not in {"mongodb", "mongodb+srv"}
            or not host.endswith(".mongodb.net")
            or not hosts or not all(nombre.endswith(".mongodb.net") for nombre in hosts)):
        raise SystemExit(
            "DIGITRANS_ATLAS_URI debe ser una URI de MongoDB Atlas con host .mongodb.net; "
            "no se publicara en una base local."
        )


def activo(df):
    return (df["tarjeta_historica"] >= 2) & (df["tarjeta_365d"] >= 1)


def muestra_separada(df):
    semanas = ((df["semana"] - ANCLA_MUESTREO).dt.days // 7).astype(int)
    return (semanas % PASO_SEMANAS) == 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prefijo", default="digitrans",
                        help="prefijo de la base Atlas de Acceso donde se publican los datos")
    args = parser.parse_args()

    for archivo in (ARCHIVO, ARCHIVO_EVALUACION):
        if not Path(archivo).is_file():
            raise SystemExit(f"Falta {archivo}; ejecuta primero el flujo mensual completo.")

    resumen_eval = pd.read_csv(ARCHIVO_EVALUACION)
    # Compatibilidad con evaluaciones ya generadas antes de agregar F1 top 5 %
    # al resumen: la columna detallada por corte ya contiene ese valor.
    if "f1_top_5_media" not in resumen_eval.columns and "f1_top_5" in resumen_eval.columns:
        resumen_eval["f1_top_5_media"] = np.nan
        resumen_30 = (resumen_eval["corte"].eq("promedio_cortes")
                      & resumen_eval["horizonte_dias"].eq(HORIZONTE))
        cortes_30 = resumen_eval[(resumen_eval["corte"] != "promedio_cortes")
                                 & resumen_eval["horizonte_dias"].eq(HORIZONTE)]
        if resumen_30.sum() == 1 and cortes_30["f1_top_5"].notna().any():
            resumen_eval.loc[resumen_30, "f1_top_5_media"] = cortes_30["f1_top_5"].mean()
    requerido = {"corte", "horizonte_dias", "auc_roc_media", "auc_pr_media",
                 "precision_top_5_media", "recall_top_5_media", "precision_top_10_media",
                 "recall_top_10_media", "f1_top_5_media", "f1_top_10_media", "prevalencia_media"}
    faltantes = sorted(requerido - set(resumen_eval.columns))
    if faltantes:
        raise SystemExit(f"Faltan metricas en {ARCHIVO_EVALUACION}: {', '.join(faltantes)}")
    evaluacion = resumen_eval[
        (resumen_eval["corte"] == "promedio_cortes")
        & (resumen_eval["horizonte_dias"] == HORIZONTE)
    ]
    if len(evaluacion) != 1:
        raise SystemExit("La evaluacion no contiene un unico resumen walk-forward para 30 dias.")
    evaluacion = evaluacion.iloc[0]
    if int(evaluacion["cortes"]) < 3:
        raise SystemExit("Se requieren al menos tres cortes temporales para publicar el modelo mensual.")

    columnas = ["usuario_id", "semana", "tarjeta_historica", "tarjeta_365d", "y_30d"] + VARIABLES
    tabla = pd.read_csv(ARCHIVO, usecols=columnas, parse_dates=["semana"])
    tabla["semana"] = pd.to_datetime(tabla["semana"], errors="coerce")
    tabla = tabla.dropna(subset=["semana"])
    semana = tabla["semana"].max()
    if pd.isna(semana):
        raise SystemExit("La tabla no contiene una semana de referencia.")

    actual = tabla[tabla["semana"] == semana].copy()
    actual = actual[activo(actual)].copy()
    if actual.empty:
        raise SystemExit("No hay pasajeros activos elegibles en la ultima semana de referencia.")

    # Los resultados de entrenamiento deben haber madurado antes de la fecha
    # de proyeccion: se excluyen etiquetas cuyo horizonte de 30 dias la alcanza.
    ultimo_entrenamiento = semana - pd.Timedelta(days=HORIZONTE)
    entrenamiento = tabla[
        (tabla["semana"] >= INICIO_ENTRENAMIENTO)
        & (tabla["semana"] < ultimo_entrenamiento)
        & tabla["y_30d"].notna()
        & activo(tabla)
        & muestra_separada(tabla)
    ].copy()
    if entrenamiento.empty or entrenamiento["y_30d"].nunique() < 2:
        raise SystemExit("No hay suficientes etiquetas completas de 30 dias para entrenar.")

    x_train = transformar_variables(entrenamiento[VARIABLES].to_numpy(dtype=np.float32), VARIABLES)
    x_actual = transformar_variables(actual[VARIABLES].to_numpy(dtype=np.float32), VARIABLES)
    if not np.isfinite(x_train).all() or not np.isfinite(x_actual).all():
        raise SystemExit("Hay valores vacios o no numericos en las variables del modelo.")

    y_train = entrenamiento["y_30d"].to_numpy(dtype=np.int8)
    modelo = crear_modelo(MODELO_CATBOOST, y_train)
    modelo.fit(x_train, y_train)
    probabilidades = modelo.predict_proba(x_actual)[:, 1]

    # Mantiene los mismos tamaños top-K usados durante la evaluacion y evita
    # que empates en la cuantila inflen el grupo Alto por encima del 5 %.
    orden = np.argsort(-probabilidades, kind="mergesort")
    limite_alto = max(1, int(np.ceil(len(probabilidades) * PORC_ALTO)))
    limite_medio = max(limite_alto,
                       int(np.ceil(len(probabilidades) * (PORC_ALTO + PORC_MEDIO))))
    nivel = np.full(len(probabilidades), "Bajo", dtype=object)
    nivel[orden[:limite_alto]] = "Alto"
    nivel[orden[limite_alto:limite_medio]] = "Medio"
    decil = pd.qcut(pd.Series(probabilidades).rank(method="first"), 10,
                    labels=False, duplicates="drop").astype(int).to_numpy() + 1

    referencia = semana.strftime("%Y-%m-%d")
    predicciones = pd.DataFrame({
        "usuario_id": actual["usuario_id"].astype(str).to_numpy(),
        "nivel_riesgo": nivel,
        "decil": decil,
        "semana_referencia": referencia,
        "objetivo": OBJETIVO,
        "horizonte_dias": HORIZONTE,
        "version_modelo": VERSION,
    }).sort_values("decil", ascending=False)
    predicciones.to_csv(SALIDA_PRED, index=False)

    metricas = {
        "modelo": "CatBoost",
        "objetivo": OBJETIVO,
        "horizonte_dias": HORIZONTE,
        "version_modelo": VERSION,
        "metodo_evaluacion": "validacion walk-forward con ventanas temporales moviles",
        "cohorte": "pasajeros activos: >=2 recargas historicas y >=1 en 365 dias",
        "generado": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M"),
        "semana_referencia": referencia,
        "auc_roc": float(evaluacion["auc_roc_media"]),
        "auc_pr": float(evaluacion["auc_pr_media"]),
        # Compatibilidad con la consola: Alto ahora es el segmento superior 5 %.
        "precision": float(evaluacion["precision_top_5_media"]),
        "recall": float(evaluacion["recall_top_5_media"]),
        "f1": float(evaluacion["f1_top_5_media"]),
        "precision_top_5": float(evaluacion["precision_top_5_media"]),
        "recall_top_5": float(evaluacion["recall_top_5_media"]),
        "precision_top_10": float(evaluacion["precision_top_10_media"]),
        "recall_top_10": float(evaluacion["recall_top_10_media"]),
        "prevalencia": float(evaluacion["prevalencia_media"]),
        "cortes_temporales": int(evaluacion["cortes"]),
        "entrenamiento_desde": entrenamiento["semana"].min().strftime("%Y-%m-%d"),
        "entrenamiento_hasta": entrenamiento["semana"].max().strftime("%Y-%m-%d"),
        "filas_entrenamiento": int(len(entrenamiento)),
        "pasajeros_publicados": int(len(predicciones)),
    }
    pd.DataFrame([metricas]).to_csv(SALIDA_MET, index=False)

    print(f"Objetivo: recarga de Billetera dentro de {HORIZONTE} dias")
    print(f"Entrenamiento: {metricas['entrenamiento_desde']} a {metricas['entrenamiento_hasta']} "
          f"| filas activas: {len(entrenamiento)}")
    print(f"Referencia: {referencia} | pasajeros activos: {len(predicciones)}")
    print(predicciones["nivel_riesgo"].value_counts().to_string())
    print(f"Validacion top 5%: precision={metricas['precision']:.1%}, "
          f"recall={metricas['recall']:.1%}, F1={metricas['f1']:.4f}")
    print(f"AUC-ROC={metricas['auc_roc']:.4f} | AUC-PR={metricas['auc_pr']:.4f}")
    print(f"Archivos: {SALIDA_PRED}, {SALIDA_MET}")

    uri = os.environ.get("DIGITRANS_ATLAS_URI")
    if not uri:
        try:
            uri = getpass.getpass("Pega la URI de MongoDB Atlas (entrada oculta): ").strip()
        except (EOFError, KeyboardInterrupt):
            raise SystemExit("No se proporciono URI; Atlas no fue modificado.")
        if not uri:
            raise SystemExit("No se proporciono URI; Atlas no fue modificado.")
    validar_uri_atlas(uri)

    from pymongo import MongoClient, ReplaceOne

    cliente = MongoClient(uri, serverSelectionTimeoutMS=15000)
    db = cliente[args.prefijo + "_acceso"]
    registros = [{
        "usuario_id": str(fila.usuario_id),
        "nivel_riesgo": str(fila.nivel_riesgo),
        "decil": int(fila.decil),
        "semana_referencia": str(fila.semana_referencia),
        "objetivo": OBJETIVO,
        "horizonte_dias": HORIZONTE,
        "version_modelo": VERSION,
    } for fila in predicciones.itertuples(index=False)]

    # Primero confirma conectividad y luego reemplaza las proyecciones vigentes.
    cliente.admin.command("ping")
    coleccion_pred = db["predicciones_recarga"]
    operaciones = [ReplaceOne({"usuario_id": doc["usuario_id"]}, doc, upsert=True)
                   for doc in registros]
    for inicio in range(0, len(operaciones), 1000):
        coleccion_pred.bulk_write(operaciones[inicio:inicio + 1000], ordered=False)
    coleccion_pred.delete_many({"semana_referencia": {"$ne": referencia}})
    db["modelo_metricas"].delete_many({})
    db["modelo_metricas"].insert_one(metricas)
    print(f"Publicado objetivo de 30 dias en Atlas ({args.prefijo}_acceso): "
          "predicciones_recarga y modelo_metricas")
    cliente.close()


if __name__ == "__main__":
    main()
