"""
Publica las predicciones del modelo para el panel de administracion.
1. Entrena la regresion logistica con entrenamiento + validacion (2024-01 a 2025-12).
2. Calcula el riesgo de cada pasajero en la ultima semana disponible.
3. Asigna un nivel de riesgo (Alto / Medio / Bajo). Se muestra el nivel, no el porcentaje,
   porque el decil mas alto estaba subestimado en calibracion.
4. Guarda CSV locales y, si existe MONGO_URI, sube a digitrans_acceso:
     predicciones_recarga  -> una prediccion vigente por usuario
     modelo_metricas       -> un documento con las metricas de la prueba
Entrada:  tabla_usuario_semana.csv y evaluacion_final.csv (misma carpeta)
Salida:   predicciones_recarga.csv, modelo_metricas.csv (y Atlas si MONGO_URI)
Requisitos: python -m pip install pandas numpy scikit-learn pymongo
Uso:        python publicar_predicciones.py
"""
import os
import argparse
from datetime import datetime, timezone
from urllib.parse import urlsplit

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

ARCHIVO = "tabla_usuario_semana_v3.csv"
SALIDA_PRED = "predicciones_recarga.csv"
SALIDA_MET = "modelo_metricas.csv"
ARCHIVO_EVALUACION = "evaluacion_final.csv"
OBJETIVO = "recarga_billetera_en_7_dias"
VERSION_MODELO = "logistica_historial_transporte_v3"


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

VARIABLES = ["rec_7d", "rec_7d_anterior", "rec_30d", "rec_90d",
             "rec_180d", "rec_365d", "tarjeta_7d", "tarjeta_30d", "tarjeta_90d",
             "tarjeta_180d", "tarjeta_365d", "movil_30d", "movil_90d",
             "viaje_7d", "viaje_30d", "viaje_90d", "viaje_365d",
             "dias_desde_ultimo_viaje", "dias_desde_ultima", "antiguedad_dias", "tendencia_7d"]
ENTRENAMIENTO = ("2024-01-01", "2025-12-15")   # entrenamiento + validacion
ULTIMA_SEMANA_REFERENCIA = None                # None = la ultima semana de la tabla
PORC_ALTO = 0.10                               # 10 % de pasajeros con mayor riesgo
PORC_MEDIO = 0.30                              # siguiente 30 %


def transformar_variables(X):
    X = np.asarray(X, dtype=np.float32).copy()
    indice_tendencia = VARIABLES.index("tendencia_7d")
    indices_log = [i for i in range(len(VARIABLES)) if i != indice_tendencia]
    X[:, indices_log] = np.log1p(np.maximum(X[:, indices_log], 0))
    X[:, indice_tendencia] = np.sign(X[:, indice_tendencia]) * np.log1p(np.abs(X[:, indice_tendencia]))
    return X


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--prefijo", default="digitrans",
                    help="prefijo de la base de Acceso donde se publican predicciones")
    args = ap.parse_args()
    t = pd.read_csv(ARCHIVO, usecols=["usuario_id", "semana"] + VARIABLES + ["y"], parse_dates=["semana"])
    tr = t[(t["semana"] >= ENTRENAMIENTO[0]) & (t["semana"] <= ENTRENAMIENTO[1])]
    X_tr = tr[VARIABLES].values.astype(np.float32)
    y_tr = tr["y"].values.astype(int)
    if len(tr) == 0 or np.unique(y_tr).size < 2:
        raise SystemExit("No hay suficientes filas y ambas clases para entrenar; reconstruye la tabla.")
    if not np.isfinite(X_tr).all():
        raise SystemExit("El entrenamiento contiene valores vacios o no numericos.")
    X_tr = transformar_variables(X_tr)

    esc = StandardScaler().fit(X_tr)
    modelo = LogisticRegression(max_iter=1000).fit(esc.transform(X_tr), y_tr)

    semana = t["semana"].max() if ULTIMA_SEMANA_REFERENCIA is None else pd.Timestamp(ULTIMA_SEMANA_REFERENCIA)
    actual = t[t["semana"] == semana].copy()
    if actual.empty:
        raise SystemExit(f"No hay observaciones para la semana {semana.date()}.")
    if not np.isfinite(actual[VARIABLES].values.astype(np.float32)).all():
        raise SystemExit("La semana a publicar contiene valores vacios o no numericos.")
    X_actual = transformar_variables(actual[VARIABLES].values)
    p = modelo.predict_proba(esc.transform(X_actual))[:, 1]

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

    # No reutilizar metricas antiguas de otra etiqueta o conjunto de variables.
    if not os.path.exists(ARCHIVO_EVALUACION):
        raise SystemExit("Falta evaluacion_final.csv. Ejecuta evaluar_final.py antes de publicar.")
    ev = pd.read_csv(ARCHIVO_EVALUACION)
    if "objetivo" not in ev.columns or not ev["objetivo"].eq(OBJETIVO).all():
        raise SystemExit("evaluacion_final.csv pertenece a otra version del objetivo. Vuelve a ejecutar evaluar_final.py.")
    if "version_modelo" not in ev.columns or not ev["version_modelo"].eq(VERSION_MODELO).all():
        raise SystemExit("evaluacion_final.csv es de otra version del modelo. Ejecuta evaluar_final.py antes de publicar.")
    modelo_eval = ev[ev["modelo"] == "regresion logistica"]
    linea_base = ev[ev["modelo"] == "linea base (tarjeta_7d > 0)"]
    if modelo_eval.empty or linea_base.empty:
        raise SystemExit("evaluacion_final.csv no incluye el modelo y la linea base esperados.")
    r = modelo_eval.iloc[0]
    b = linea_base.iloc[0]
    met = {"modelo": "Regresion logistica", "objetivo": OBJETIVO, "version_modelo": VERSION_MODELO,
           "datos": str(r["conjunto"]),
           "generado": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M"),
           "auc_roc": float(r["auc_roc"]), "auc_pr": float(r["auc_pr"]),
           "precision": float(r["precision"]), "recall": float(r["recall"]), "f1": float(r["f1"]),
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
