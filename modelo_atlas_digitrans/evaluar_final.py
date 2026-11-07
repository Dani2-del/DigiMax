"""Compara candidatos en validacion y mide al ganador en una prueba posterior.

La seleccion del tipo de modelo y el umbral ocurre solo en VALIDACION; la prueba
temporal posterior no participa en esas decisiones.
"""
import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.metrics import average_precision_score, precision_recall_curve, roc_auc_score
from sklearn.preprocessing import StandardScaler
from modelo_predictivo_config import (
    MODELO_CATBOOST, MODELO_IMPULSO, MODELO_LIGHTGBM, MODELO_LOGISTICO, VARIABLES, VERSION_MODELO,
    crear_modelo, transformar_variables,
)

ORIGINAL = "tabla_usuario_semana_v6.csv"
EVALUACION = "tabla_eval.csv"
SALIDA = "evaluacion_final.csv"
OBJETIVO = "recarga_billetera_en_7_dias"
ENTRENAMIENTO = ("2024-01-01", "2025-06-16")
VALIDACION = ("2025-06-30", "2025-12-15")
PRUEBA = ("2026-01-05", "2026-10-19")   # misma ventana de prueba que antes, para comparar


def separar(t, ini, fin):
    s = t[(t["semana"] >= ini) & (t["semana"] <= fin)]
    return s[VARIABLES].values.astype(np.float32), s["y"].values.astype(int)


def validar_conjunto(nombre, X, y, necesita_dos_clases=False):
    if len(y) == 0:
        raise SystemExit(f"No hay filas para {nombre}; revisa las fechas y los CSV.")
    if not np.isfinite(X).all():
        raise SystemExit(f"{nombre} contiene valores vacios o no numericos en las variables.")
    if necesita_dos_clases and np.unique(y).size < 2:
        raise SystemExit(f"{nombre} debe incluir casos con y=0 y y=1.")


def elegir_umbral_f1(y, p):
    """Elige en validación el umbral que equilibra precisión y recall (máximo F1)."""
    prec, rec, umbrales = precision_recall_curve(y, p)
    f1 = 2 * prec[:-1] * rec[:-1] / np.maximum(prec[:-1] + rec[:-1], 1e-12)
    indice = int(np.argmax(f1))
    return float(umbrales[indice]), {
        "precision_validacion": float(prec[indice]),
        "recall_validacion": float(rec[indice]),
        "f1_validacion": float(f1[indice]),
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

    # 1 y 2: seleccionar el tipo de modelo y el umbral usando solo validación.
    X_tr, y_tr = separar(orig, *ENTRENAMIENTO)
    X_va, y_va = separar(orig, *VALIDACION)
    X_ev, y_ev = separar(ev, *PRUEBA)
    validar_conjunto("entrenamiento", X_tr, y_tr, necesita_dos_clases=True)
    validar_conjunto("validacion", X_va, y_va, necesita_dos_clases=True)
    validar_conjunto("evaluacion", X_ev, y_ev, necesita_dos_clases=True)
    X_tr, X_va, X_ev = map(transformar_variables, (X_tr, X_va, X_ev))
    candidatos = []
    for nombre in (MODELO_LOGISTICO, MODELO_IMPULSO, MODELO_CATBOOST, MODELO_LIGHTGBM):
        mod = crear_modelo(nombre, y_tr)
        esc = StandardScaler() if nombre == MODELO_LOGISTICO else None
        x_tr = esc.fit_transform(X_tr) if esc else X_tr
        x_va = esc.transform(X_va) if esc else X_va
        mod.fit(x_tr, y_tr)
        p_va = mod.predict_proba(x_va)[:, 1]
        umbral, seleccion = elegir_umbral_f1(y_va, p_va)
        seleccion.update({
            "modelo": nombre,
            "umbral": umbral,
            "auc_roc_validacion": float(roc_auc_score(y_va, p_va)),
            "auc_pr_validacion": float(average_precision_score(y_va, p_va)),
        })
        candidatos.append((seleccion, mod, esc))

    # F1 es el objetivo equilibrado principal; AUC-PR desempata sin usar la prueba.
    seleccion, mod, esc = max(
        candidatos, key=lambda item: (item[0]["f1_validacion"], item[0]["auc_pr_validacion"])
    )
    umbral = seleccion["umbral"]

    # 3: evaluacion sobre datos nuevos, una sola vez y sin recalibrar el umbral.
    p_ev = mod.predict_proba(esc.transform(X_ev) if esc else X_ev)[:, 1]
    res = metricas(y_ev, p_ev, umbral)

    # Linea base sobre los mismos datos nuevos
    X_b = X_ev[:, VARIABLES.index("tarjeta_7d")]
    base = metricas(y_ev, (X_b > 0).astype(float), 0.5)

    nombre_conjunto = "evaluacion independiente" if tiene_eval_independiente else "prueba temporal"
    filas = [{"conjunto": nombre_conjunto, "objetivo": OBJETIVO, "version_modelo": VERSION_MODELO,
              "modelo": seleccion["modelo"], "umbral": round(umbral, 6),
              "precision_validacion": round(seleccion["precision_validacion"], 4),
              "recall_validacion": round(seleccion["recall_validacion"], 4),
              "f1_validacion": round(seleccion["f1_validacion"], 4),
              "auc_roc_validacion": round(seleccion["auc_roc_validacion"], 4),
              "auc_pr_validacion": round(seleccion["auc_pr_validacion"], 4), **res},
             {"conjunto": nombre_conjunto, "objetivo": OBJETIVO, "version_modelo": VERSION_MODELO,
              "modelo": "linea base (tarjeta_7d > 0)", "umbral": 0.5, **base}]
    tabla = pd.DataFrame(filas)
    tabla.to_csv(SALIDA, index=False)
    print(f"Fuente de prueba: {EVALUACION if tiene_eval_independiente else ORIGINAL} ({nombre_conjunto})")
    print(f"Filas de evaluacion: {len(y_ev)} | proporcion y=1: {y_ev.mean():.4f}")
    print("Candidatos en validacion (sin usar la prueba para elegir):")
    for datos, _, _ in candidatos:
        print(f"  {datos['modelo']}: precision={datos['precision_validacion']:.1%} | "
              f"recall={datos['recall_validacion']:.1%} | F1={datos['f1_validacion']:.4f} | "
              f"AUC-ROC={datos['auc_roc_validacion']:.4f} | AUC-PR={datos['auc_pr_validacion']:.4f}")
    print(f"Umbral elegido en validacion por F1: {umbral:.6f} | "
          f"precision: {seleccion['precision_validacion']:.1%} | "
          f"recall: {seleccion['recall_validacion']:.1%} | "
          f"F1: {seleccion['f1_validacion']:.4f}")
    print(f"Modelo elegido: {seleccion['modelo']}")
    print(tabla.to_string(index=False))
    print(f"\nArchivo: {SALIDA}")


if __name__ == "__main__":
    main()
