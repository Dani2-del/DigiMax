"""
Publica las predicciones del modelo para el panel de administracion.
1. Entrena el modelo seleccionado en validacion (2024-01 a 2025-12).
2. Calcula el riesgo de cada pasajero en la ultima semana disponible.
3. Asigna un nivel de riesgo (Alto / Medio / Bajo). Se muestra el nivel, no el porcentaje,
   porque el decil mas alto estaba subestimado en calibracion.
4. Guarda CSV locales y, si existe MONGO_URI, sube a digitrans_acceso:
     predicciones_recarga  -> una prediccion vigente por usuario
     modelo_metricas       -> un documento con las metricas de la prueba
Entrada:  tabla_usuario_semana_v6.csv y evaluacion_final.csv (misma carpeta)
Salida:   predicciones_recarga.csv, modelo_metricas.csv (y Atlas si MONGO_URI)
Requisitos: python -m pip install -r requirements.txt
Uso:        python publicar_predicciones.py
"""
import os
import argparse
from datetime import datetime, timezone
from urllib.parse import urlsplit

import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler
from modelo_predictivo_config import (
    MODELO_CATBOOST, MODELO_IMPULSO, MODELO_LIGHTGBM, MODELO_LOGISTICO, VARIABLES, VERSION_MODELO,
    crear_modelo, transformar_variables,
)

ARCHIVO = "tabla_usuario_semana_v6.csv"
SALIDA_PRED = "predicciones_recarga.csv"
SALIDA_MET = "modelo_metricas.csv"
ARCHIVO_EVALUACION = "evaluacion_final.csv"
OBJETIVO = "recarga_billetera_en_7_dias"


def validar_uri_atlas(uri):
    uri = uri.strip().strip('"').strip("'")
    partes = None
    try:
        partes = urlsplit(uri)
        host = partes.hostname or ""
    except ValueError:
        host = ""
    hosts_atlas = all(
        segmento.rsplit("@", 1)[-1].split(":", 1)[0].endswith(".mongodb.net")
        for segmento in (partes.netloc.split(",") if partes else [])
    )
    if partes is None or partes.scheme not in {"mongodb", "mongodb+srv"} or not host.endswith(".mongodb.net") or not hosts_atlas:
        raise SystemExit(
            "DIGITRANS_ATLAS_URI debe ser una URI de MongoDB Atlas "
            "con host terminado en .mongodb.net; no se publicara en una base local."
        )

ENTRENAMIENTO = ("2024-01-01", "2025-12-15")   # entrenamiento + validacion
ULTIMA_SEMANA_REFERENCIA = None                # None = la ultima semana de la tabla
PORC_ALTO = 0.10                               # 10 % de pasajeros con mayor riesgo
PORC_MEDIO = 0.30                              # siguiente 30 %


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--prefijo", default="digitrans",
                    help="prefijo de la base de Acceso donde se publican predicciones")
    args = ap.parse_args()
    if not os.path.exists(ARCHIVO_EVALUACION):
        raise SystemExit("Falta evaluacion_final.csv. Ejecuta evaluar_final.py antes de publicar.")
    ev = pd.read_csv(ARCHIVO_EVALUACION)
    if "objetivo" not in ev.columns or not ev["objetivo"].eq(OBJETIVO).all():
        raise SystemExit("evaluacion_final.csv pertenece a otro objetivo. Vuelve a ejecutar evaluar_final.py.")
    if "version_modelo" not in ev.columns or not ev["version_modelo"].eq(VERSION_MODELO).all():
        raise SystemExit("evaluacion_final.csv no contiene la seleccion del modelo v8; ejecuta evaluar_final.py otra vez.")
    filas_modelo = ev[ev["modelo"].isin([
        MODELO_LOGISTICO, MODELO_IMPULSO, MODELO_CATBOOST, MODELO_LIGHTGBM
    ])]
    if filas_modelo.empty:
        raise SystemExit("evaluacion_final.csv no identifica un modelo v8 que se pueda publicar.")
    nombre_modelo = str(filas_modelo.iloc[0]["modelo"])

    t = pd.read_csv(ARCHIVO, usecols=["usuario_id", "semana"] + VARIABLES + ["y"], parse_dates=["semana"])
    tr = t[(t["semana"] >= ENTRENAMIENTO[0]) & (t["semana"] <= ENTRENAMIENTO[1])]
    X_tr = transformar_variables(tr[VARIABLES].values.astype(np.float32))
    y_tr = tr["y"].values.astype(int)
    if len(tr) == 0 or np.unique(y_tr).size < 2:
        raise SystemExit("No hay suficientes filas y ambas clases para entrenar; reconstruye la tabla.")
    if not np.isfinite(X_tr).all():
        raise SystemExit("El entrenamiento contiene valores vacios o no numericos.")
    modelo = crear_modelo(nombre_modelo, y_tr)
    esc = StandardScaler() if nombre_modelo == MODELO_LOGISTICO else None
    modelo.fit(esc.fit_transform(X_tr) if esc else X_tr, y_tr)

    semana = t["semana"].max() if ULTIMA_SEMANA_REFERENCIA is None else pd.Timestamp(ULTIMA_SEMANA_REFERENCIA)
    actual = t[t["semana"] == semana].copy()
    if actual.empty:
        raise SystemExit(f"No hay observaciones para la semana {semana.date()}.")
    if not np.isfinite(actual[VARIABLES].values.astype(np.float32)).all():
        raise SystemExit("La semana a publicar contiene valores vacios o no numericos.")
    X_actual = transformar_variables(actual[VARIABLES].values)
    p = modelo.predict_proba(esc.transform(X_actual) if esc else X_actual)[:, 1]

    # Nivel de riesgo segun la distribucion de probabilidades de esta semana
    corte_alto = np.quantile(p, 1 - PORC_ALTO)
    corte_medio = np.quantile(p, 1 - PORC_ALTO - PORC_MEDIO)
    nivel = np.where(p >= corte_alto, "Alto", np.where(p >= corte_medio, "Medio", "Bajo"))
    decil = pd.qcut(pd.Series(p).rank(method="first"), 10, labels=False).values + 1

    pred = pd.DataFrame({
        "usuario_id": actual["usuario_id"].values,
        "nivel_riesgo": nivel,
        "decil": decil,
        "semana_referencia": semana.strftime("%Y-%m-%d"),
    }).sort_values("decil", ascending=False)
    pred["decil"] = pred["decil"].astype(int)
    pred.to_csv(SALIDA_PRED, index=False)

    modelo_eval = ev[ev["modelo"] == nombre_modelo]
    linea_base = ev[ev["modelo"] == "linea base (tarjeta_7d > 0)"]
    if modelo_eval.empty or linea_base.empty:
        raise SystemExit("evaluacion_final.csv no incluye el modelo y la linea base esperados.")
    r = modelo_eval.iloc[0]
    b = linea_base.iloc[0]
    etiquetas_modelo = {
        MODELO_LOGISTICO: "Regresion logistica",
        MODELO_IMPULSO: "Boosting de gradiente",
        MODELO_CATBOOST: "CatBoost",
        MODELO_LIGHTGBM: "LightGBM",
    }
    etiqueta_modelo = etiquetas_modelo[nombre_modelo]
    met = {"modelo": etiqueta_modelo, "objetivo": OBJETIVO, "version_modelo": VERSION_MODELO,
           "datos": str(r["conjunto"]),
           "generado": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M"),
           "auc_roc": float(r["auc_roc"]), "auc_pr": float(r["auc_pr"]),
           "precision": float(r["precision"]), "recall": float(r["recall"]), "f1": float(r["f1"]),
           "umbral_f1": float(r["umbral"]),
           "precision_validacion": float(r["precision_validacion"]),
           "recall_validacion": float(r["recall_validacion"]),
           "f1_validacion": float(r["f1_validacion"]),
           "f1_linea_base": float(b["f1"]), "precision_linea_base": float(b["precision"]),
           "recall_linea_base": float(b["recall"])}
    pd.DataFrame([met]).to_csv(SALIDA_MET, index=False)

    print(f"Semana de referencia: {semana.date()} | pasajeros: {len(pred)}")
    print(pred["nivel_riesgo"].value_counts().to_string())
    print(f"Archivos: {SALIDA_PRED}, {SALIDA_MET}")

    uri = os.environ.get("DIGITRANS_ATLAS_URI")
    if uri:
        validar_uri_atlas(uri)
        from pymongo import MongoClient, ReplaceOne
        db = MongoClient(uri)[args.prefijo + "_acceso"]
        registros = [{"usuario_id": str(r.usuario_id), "nivel_riesgo": str(r.nivel_riesgo),
                      "decil": int(r.decil), "semana_referencia": str(r.semana_referencia)}
                     for r in pred.itertuples(index=False)]
        predicciones = db["predicciones_recarga"]
        operaciones = [ReplaceOne({"usuario_id": doc["usuario_id"]}, doc, upsert=True)
                       for doc in registros]
        for inicio in range(0, len(operaciones), 1000):
            predicciones.bulk_write(operaciones[inicio:inicio + 1000], ordered=False)
        # Quitar semanas antiguas solo después de guardar todas las nuevas.
        predicciones.delete_many({"semana_referencia": {"$ne": semana.strftime("%Y-%m-%d")}})
        db["modelo_metricas"].delete_many({})
        met_limpio = {k: (float(v) if isinstance(v, (np.floating,)) else (v.item() if hasattr(v, "item") else v))
                      for k, v in met.items()}
        db["modelo_metricas"].insert_one(met_limpio)
        print(f"Publicado en Atlas ({args.prefijo}_acceso): predicciones_recarga y modelo_metricas")
    else:
        print("DIGITRANS_ATLAS_URI no definida: solo se guardaron los CSV; no se publico en MongoDB.")


if __name__ == "__main__":
    main()
