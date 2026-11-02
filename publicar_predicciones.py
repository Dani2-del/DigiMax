"""
Publica las predicciones del modelo para el panel de administracion.
1. Entrena la regresion logistica con entrenamiento + validacion (2024-01 a 2025-12).
2. Calcula el riesgo de cada pasajero en la ultima semana disponible.
3. Asigna un nivel de riesgo (Alto / Medio / Bajo). Se muestra el nivel, no el porcentaje,
   porque el decil mas alto estaba subestimado en calibracion.
4. Guarda CSV locales y, si existe MONGO_URI, sube a digitrans_acceso:
     predicciones_recarga  -> una fila por pasajero (reemplaza la anterior)
     modelo_metricas       -> un documento con las metricas de la prueba
Entrada:  tabla_usuario_semana.csv, regresion_resultados.csv (misma carpeta)
Salida:   predicciones_recarga.csv, modelo_metricas.csv (y Atlas si MONGO_URI)
Requisitos: python -m pip install pandas numpy scikit-learn pymongo
Uso:        python publicar_predicciones.py
"""
import os
from datetime import datetime, timezone

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

ARCHIVO = "tabla_usuario_semana.csv"
REG = "regresion_resultados.csv"
SALIDA_PRED = "predicciones_recarga.csv"
SALIDA_MET = "modelo_metricas.csv"

VARIABLES = ["rec_7d", "rec_7d_anterior", "rec_30d", "rec_90d", "movil_30d",
             "dias_desde_ultima", "antiguedad_dias", "tendencia_7d"]
ENTRENAMIENTO = ("2024-01-01", "2025-12-15")   # entrenamiento + validacion
ULTIMA_SEMANA_REFERENCIA = None                # None = la ultima semana de la tabla
PORC_ALTO = 0.10                               # 10 % de pasajeros con mayor riesgo
PORC_MEDIO = 0.30                              # siguiente 30 %


def main():
    t = pd.read_csv(ARCHIVO, usecols=["usuario_id", "semana"] + VARIABLES + ["y"], parse_dates=["semana"])
    tr = t[(t["semana"] >= ENTRENAMIENTO[0]) & (t["semana"] <= ENTRENAMIENTO[1])]
    X_tr = tr[VARIABLES].values.astype(np.float32)
    y_tr = tr["y"].values.astype(int)

    esc = StandardScaler().fit(X_tr)
    modelo = LogisticRegression(max_iter=1000).fit(esc.transform(X_tr), y_tr)

    semana = t["semana"].max() if ULTIMA_SEMANA_REFERENCIA is None else pd.Timestamp(ULTIMA_SEMANA_REFERENCIA)
    actual = t[t["semana"] == semana].copy()
    p = modelo.predict_proba(esc.transform(actual[VARIABLES].values.astype(np.float32)))[:, 1]

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

    # Metricas de la EVALUACION FINAL (datos nuevos que el modelo nunca vio)
    met = {"modelo": "Regresion logistica", "datos": "sinteticos (evaluacion nueva, no vista)",
           "generado": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M")}
    if os.path.exists("evaluacion_final.csv"):
        ev = pd.read_csv("evaluacion_final.csv")
        r = ev[ev["modelo"] == "regresion logistica"].iloc[0]
        b = ev[ev["modelo"].str.startswith("linea base")].iloc[0]
        met.update({"auc_roc": float(r["auc_roc"]), "auc_pr": float(r["auc_pr"]),
                    "precision": float(r["precision"]), "recall": float(r["recall"]), "f1": float(r["f1"]),
                    "f1_linea_base": float(b["f1"]), "precision_linea_base": float(b["precision"]),
                    "recall_linea_base": float(b["recall"])})
    else:
        print("AVISO: no existe evaluacion_final.csv; se usan las metricas de la prueba original.")
        if os.path.exists(REG):
            r = pd.read_csv(REG)
            r = r[r["bloque"] == "prueba"].iloc[0]
            met.update({"auc_roc": float(r["auc_roc"]), "auc_pr": float(r["auc_pr"]),
                        "precision": float(r["precision"]), "recall": float(r["recall"]), "f1": float(r["f1"])})
        if os.path.exists("linea_base_resultados.csv"):
            lb = pd.read_csv("linea_base_resultados.csv")
            lb = lb[(lb["bloque"] == "prueba") & (lb["regla"].str.startswith("R1"))]
            if len(lb):
                met.update({"f1_linea_base": float(lb.iloc[0]["f1"]),
                            "precision_linea_base": float(lb.iloc[0]["precision"]),
                            "recall_linea_base": float(lb.iloc[0]["recall"])})
    pd.DataFrame([met]).to_csv(SALIDA_MET, index=False)

    print(f"Semana de referencia: {semana.date()} | pasajeros: {len(pred)}")
    print(pred["nivel_riesgo"].value_counts().to_string())
    print(f"Archivos: {SALIDA_PRED}, {SALIDA_MET}")

    uri = os.environ.get("MONGO_URI")
    if uri:
        from pymongo import MongoClient
        db = MongoClient(uri)["digitrans_acceso"]
        db["predicciones_recarga"].drop()
        registros = [{"usuario_id": str(r.usuario_id), "nivel_riesgo": str(r.nivel_riesgo),
                      "decil": int(r.decil), "semana_referencia": str(r.semana_referencia)}
                     for r in pred.itertuples(index=False)]
        db["predicciones_recarga"].insert_many(registros)
        db["modelo_metricas"].delete_many({})
        met_limpio = {k: (float(v) if isinstance(v, (np.floating,)) else (v.item() if hasattr(v, "item") else v))
                      for k, v in met.items()}
        db["modelo_metricas"].insert_one(met_limpio)
        print("Publicado en Atlas (digitrans_acceso): predicciones_recarga y modelo_metricas")
    else:
        print("MONGO_URI no definida: solo se guardaron los CSV.")


if __name__ == "__main__":
    main()
