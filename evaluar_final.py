"""
Evaluacion FINAL con datos nuevos (conjunto de evaluacion, nunca usado antes).
Procedimiento congelado (no se cambia nada aqui):
  1. Entrenar la regresion logistica con ENTRENAMIENTO del conjunto original.
  2. Elegir el umbral con VALIDACION del conjunto original (maximiza F1).
  3. Aplicar modelo y umbral, sin cambios, al conjunto de EVALUACION nuevo.
Entradas:
  tabla_usuario_semana.csv  -> conjunto original (entrenamiento y validacion)
  tabla_eval.csv            -> conjunto nuevo generado con otra semilla
Salida:   evaluacion_final.csv
Requisitos: python -m pip install pandas numpy scikit-learn
Uso:        python evaluar_final.py
"""
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, precision_recall_curve, roc_auc_score
from sklearn.preprocessing import StandardScaler

ORIGINAL = "tabla_usuario_semana.csv"
EVALUACION = "tabla_eval.csv"
SALIDA = "evaluacion_final.csv"
VARIABLES = ["rec_7d", "rec_7d_anterior", "rec_30d", "rec_90d", "movil_30d",
             "dias_desde_ultima", "antiguedad_dias", "tendencia_7d"]
ENTRENAMIENTO = ("2024-01-01", "2025-06-16")
VALIDACION = ("2025-06-30", "2025-12-15")
PRUEBA = ("2026-01-05", "2026-10-19")   # misma ventana de prueba que antes, para comparar


def separar(t, ini, fin):
    s = t[(t["semana"] >= ini) & (t["semana"] <= fin)]
    return s[VARIABLES].values.astype(np.float32), s["y"].values.astype(int)


def mejor_umbral(y, p):
    prec, rec, umbrales = precision_recall_curve(y, p)
    f1 = 2 * prec[:-1] * rec[:-1] / np.maximum(prec[:-1] + rec[:-1], 1e-12)
    return umbrales[int(np.argmax(f1))]


def metricas(y, p, umbral):
    pred = (p >= umbral).astype(int)
    tp = int(((pred == 1) & (y == 1)).sum())
    fp = int(((pred == 1) & (y == 0)).sum())
    fn = int(((pred == 0) & (y == 1)).sum())
    pr = tp / (tp + fp) if (tp + fp) else 0.0
    rc = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = 2 * pr * rc / (pr + rc) if (pr + rc) else 0.0
    return {"auc_roc": round(roc_auc_score(y, p), 4), "auc_pr": round(average_precision_score(y, p), 4),
            "precision": round(pr, 4), "recall": round(rc, 4), "f1": round(f1, 4)}


def main():
    cols = ["semana", "y"] + VARIABLES
    orig = pd.read_csv(ORIGINAL, usecols=cols, parse_dates=["semana"])
    ev = pd.read_csv(EVALUACION, usecols=cols, parse_dates=["semana"])

    # 1 y 2: modelo y umbral congelados con el conjunto original
    X_tr, y_tr = separar(orig, *ENTRENAMIENTO)
    X_va, y_va = separar(orig, *VALIDACION)
    esc = StandardScaler().fit(X_tr)
    mod = LogisticRegression(max_iter=1000).fit(esc.transform(X_tr), y_tr)
    umbral = mejor_umbral(y_va, mod.predict_proba(esc.transform(X_va))[:, 1])

    # 3: evaluacion sobre datos nuevos, una sola vez
    X_ev, y_ev = separar(ev, *PRUEBA)
    p_ev = mod.predict_proba(esc.transform(X_ev))[:, 1]
    res = metricas(y_ev, p_ev, umbral)

    # Linea base sobre los mismos datos nuevos
    X_b = X_ev[:, VARIABLES.index("rec_7d")]
    base = metricas(y_ev, (X_b > 0).astype(float), 0.5)

    filas = [{"conjunto": "evaluacion nueva", "modelo": "regresion logistica", "umbral": round(float(umbral), 4), **res},
             {"conjunto": "evaluacion nueva", "modelo": "linea base (rec_7d > 0)", "umbral": 0.5, **base}]
    tabla = pd.DataFrame(filas)
    tabla.to_csv(SALIDA, index=False)
    print(f"Filas de evaluacion: {len(y_ev)} | proporcion y=1: {y_ev.mean():.4f}")
    print(tabla.to_string(index=False))
    print("\nReferencia del conjunto original (prueba): regresion F1 = 0.2801 | linea base F1 = 0.1627")
    print(f"\nArchivo: {SALIDA}")


if __name__ == "__main__":
    main()
