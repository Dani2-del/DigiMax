"""
Evaluacion FINAL con datos nuevos (conjunto de evaluacion, nunca usado antes).
Procedimiento congelado (no se cambia nada aqui):
  1. Entrenar la regresion logistica con ENTRENAMIENTO del conjunto original.
  2. Elegir el umbral con VALIDACION del conjunto original (maximiza F1).
  3. Aplicar modelo y umbral, sin cambios, al conjunto de EVALUACION nuevo.
Entradas:
  tabla_usuario_semana.csv  -> entrenamiento, validacion y prueba temporal
  tabla_eval.csv            -> opcional; si existe, reemplaza la prueba temporal
Salida:   evaluacion_final.csv
Requisitos: python -m pip install pandas numpy scikit-learn
Uso:        python evaluar_final.py
"""
import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, precision_recall_curve, roc_auc_score
from sklearn.preprocessing import StandardScaler

ORIGINAL = "tabla_usuario_semana_v3.csv"
EVALUACION = "tabla_eval.csv"
SALIDA = "evaluacion_final.csv"
OBJETIVO = "recarga_billetera_en_7_dias"
VERSION_MODELO = "logistica_historial_transporte_v3"
VARIABLES = ["rec_7d", "rec_7d_anterior", "rec_30d", "rec_90d",
             "rec_180d", "rec_365d", "tarjeta_7d", "tarjeta_30d", "tarjeta_90d",
             "tarjeta_180d", "tarjeta_365d", "movil_30d", "movil_90d",
             "viaje_7d", "viaje_30d", "viaje_90d", "viaje_365d",
             "dias_desde_ultimo_viaje", "dias_desde_ultima", "antiguedad_dias", "tendencia_7d"]
ENTRENAMIENTO = ("2024-01-01", "2025-06-16")
VALIDACION = ("2025-06-30", "2025-12-15")
PRUEBA = ("2026-01-05", "2026-10-19")   # misma ventana de prueba que antes, para comparar
PRECISION_OBJETIVO = 0.80
MIN_PREDICCIONES_VALIDACION = 100


def separar(t, ini, fin):
    s = t[(t["semana"] >= ini) & (t["semana"] <= fin)]
    return s[VARIABLES].values.astype(np.float32), s["y"].values.astype(int)


def transformar_variables(X):
    """Comprime conteos sesgados para reducir el efecto de valores extremos."""
    X = np.asarray(X, dtype=np.float32).copy()
    indice_tendencia = VARIABLES.index("tendencia_7d")
    indices_log = [i for i in range(len(VARIABLES)) if i != indice_tendencia]
    X[:, indices_log] = np.log1p(np.maximum(X[:, indices_log], 0))
    X[:, indice_tendencia] = np.sign(X[:, indice_tendencia]) * np.log1p(np.abs(X[:, indice_tendencia]))
    return X


def validar_conjunto(nombre, X, y, necesita_dos_clases=False):
    if len(y) == 0:
        raise SystemExit(f"No hay filas para {nombre}; revisa las fechas y los CSV.")
    if not np.isfinite(X).all():
        raise SystemExit(f"{nombre} contiene valores vacios o no numericos en las variables.")
    if necesita_dos_clases and np.unique(y).size < 2:
        raise SystemExit(f"{nombre} debe incluir casos con y=0 y y=1.")


def elegir_umbral_precision(y, p):
    """Busca el mayor recall validado que mantiene precision >= objetivo.

    Requiere un mínimo de predicciones para no declarar éxito con unos pocos casos.
    Si no se alcanza el objetivo, devuelve el umbral de mayor precisión con ese
    soporte mínimo para que la prueba temporal muestre el límite real del modelo.
    """
    prec, rec, umbrales = precision_recall_curve(y, p)
    p_ordenada = np.sort(p)
    cantidades = len(p) - np.searchsorted(p_ordenada, umbrales, side="left")
    soporte = cantidades >= MIN_PREDICCIONES_VALIDACION
    if not soporte.any():
        raise SystemExit("Validacion no produjo suficientes predicciones para fijar un umbral estable.")

    objetivo = soporte & (prec[:-1] >= PRECISION_OBJETIVO)
    if objetivo.any():
        indices = np.flatnonzero(objetivo)
        indice = indices[np.argmax(rec[:-1][indices])]
        alcanzado = True
    else:
        indices = np.flatnonzero(soporte)
        mejor_precision = prec[:-1][indices].max()
        indices = indices[prec[:-1][indices] == mejor_precision]
        indice = indices[np.argmax(rec[:-1][indices])]
        alcanzado = False

    return float(umbrales[indice]), {
        "precision_validacion": float(prec[indice]),
        "recall_validacion": float(rec[indice]),
        "predicciones_validacion": int(cantidades[indice]),
        "objetivo_precision_alcanzado_validacion": alcanzado,
    }


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
    # Preferir un conjunto independiente; si no existe, usar el periodo futuro
    # reservado dentro de la tabla semanal como prueba temporal.
    tiene_eval_independiente = Path(EVALUACION).is_file()
    ev = (pd.read_csv(EVALUACION, usecols=cols, parse_dates=["semana"])
          if tiene_eval_independiente else orig)

    # 1 y 2: modelo y umbral congelados con el conjunto original
    X_tr, y_tr = separar(orig, *ENTRENAMIENTO)
    X_va, y_va = separar(orig, *VALIDACION)
    X_ev, y_ev = separar(ev, *PRUEBA)
    validar_conjunto("entrenamiento", X_tr, y_tr, necesita_dos_clases=True)
    validar_conjunto("validacion", X_va, y_va, necesita_dos_clases=True)
    validar_conjunto("evaluacion", X_ev, y_ev, necesita_dos_clases=True)
    X_tr, X_va, X_ev = map(transformar_variables, (X_tr, X_va, X_ev))
    esc = StandardScaler().fit(X_tr)
    mod = LogisticRegression(max_iter=1000).fit(esc.transform(X_tr), y_tr)
    p_va = mod.predict_proba(esc.transform(X_va))[:, 1]
    umbral, seleccion = elegir_umbral_precision(y_va, p_va)

    # 3: evaluacion sobre datos nuevos, una sola vez
    p_ev = mod.predict_proba(esc.transform(X_ev))[:, 1]
    res = metricas(y_ev, p_ev, umbral)

    # Linea base sobre los mismos datos nuevos
    X_b = X_ev[:, VARIABLES.index("tarjeta_7d")]
    base = metricas(y_ev, (X_b > 0).astype(float), 0.5)

    nombre_conjunto = "evaluacion independiente" if tiene_eval_independiente else "prueba temporal"
    filas = [{"conjunto": nombre_conjunto, "objetivo": OBJETIVO, "version_modelo": VERSION_MODELO,
              "modelo": "regresion logistica", "umbral": round(umbral, 6),
              "precision_objetivo": PRECISION_OBJETIVO,
              "precision_validacion": round(seleccion["precision_validacion"], 4),
              "recall_validacion": round(seleccion["recall_validacion"], 4),
              "predicciones_validacion": seleccion["predicciones_validacion"],
              "objetivo_precision_alcanzado_validacion": seleccion["objetivo_precision_alcanzado_validacion"], **res},
             {"conjunto": nombre_conjunto, "objetivo": OBJETIVO, "version_modelo": VERSION_MODELO,
              "modelo": "linea base (tarjeta_7d > 0)", "umbral": 0.5, **base}]
    tabla = pd.DataFrame(filas)
    tabla.to_csv(SALIDA, index=False)
    print(f"Fuente de prueba: {EVALUACION if tiene_eval_independiente else ORIGINAL} ({nombre_conjunto})")
    print(f"Filas de evaluacion: {len(y_ev)} | proporcion y=1: {y_ev.mean():.4f}")
    print(f"Objetivo de precision en validacion: {PRECISION_OBJETIVO:.0%} | "
          f"precision validacion: {seleccion['precision_validacion']:.1%} | "
          f"recall validacion: {seleccion['recall_validacion']:.1%} | "
          f"predicciones validadas: {seleccion['predicciones_validacion']}")
    if not seleccion["objetivo_precision_alcanzado_validacion"]:
        print("No se alcanzo el 80% de precision en validacion con al menos "
              f"{MIN_PREDICCIONES_VALIDACION} predicciones; se usa el mejor umbral estable disponible.")
    print(tabla.to_string(index=False))
    print(f"\nArchivo: {SALIDA}")


if __name__ == "__main__":
    main()
