"""Configuracion compartida para entrenar, evaluar y publicar el modelo."""
import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression

VERSION_MODELO_BASE = "recarga_historial_consumo_transporte_v8_cadencia"
VERSION_MODELO = "recarga_historial_consumo_transporte_v9_cadencia_personal"
MODELO_LOGISTICO = "regresion logistica"
MODELO_IMPULSO = "boosting gradiente (histograma)"
MODELO_CATBOOST = "catboost"
MODELO_LIGHTGBM = "lightgbm"

VARIABLES_BASE = [
    "rec_7d", "rec_7d_anterior", "rec_30d", "rec_90d", "rec_180d", "rec_365d",
    "tarjeta_7d", "tarjeta_30d", "tarjeta_90d", "tarjeta_180d", "tarjeta_365d",
    "tarjeta_8_30d", "tarjeta_31_90d", "tarjeta_91_180d", "tarjeta_historica",
    "dias_desde_ultima_recarga", "dias_entre_ultimas_recargas", "movil_30d", "movil_90d",
    "viaje_7d", "viaje_30d", "viaje_90d", "viaje_365d", "viaje_historico",
    "dias_desde_ultimo_viaje", "dias_desde_ultima",
    "consumo_7d", "consumo_30d", "consumo_90d", "consumo_365d",
    "gasto_billetera_30d", "gasto_billetera_90d", "gasto_billetera_365d",
    "dias_desde_ultimo_consumo", "antiguedad_dias", "tendencia_7d",
    "servicio_transcaribe", "servicio_transmilenio", "servicio_transmetro",
]

# Variables experimentales estimadas solo con recargas anteriores a cada semana.
VARIABLES_CADENCIA = [
    "cadencia_mediana_recarga_dias",
    "variabilidad_cadencia_recarga",
    "progreso_ciclo_recarga",
    "intervalos_recarga_observados",
]
VARIABLES = VARIABLES_BASE + VARIABLES_CADENCIA


def transformar_variables(valores, variables=None):
    """Reduce asimetria de conteos, montos y recencias sin alterar indicadores."""
    variables = VARIABLES if variables is None else variables
    x = np.asarray(valores, dtype=np.float32).copy()
    i_tendencia = variables.index("tendencia_7d")
    i_binarias = {
        variables.index("servicio_transcaribe"),
        variables.index("servicio_transmilenio"),
        variables.index("servicio_transmetro"),
    }
    indices_log = [i for i in range(x.shape[1]) if i != i_tendencia and i not in i_binarias]
    x[:, indices_log] = np.log1p(np.maximum(x[:, indices_log], 0))
    x[:, i_tendencia] = np.sign(x[:, i_tendencia]) * np.log1p(np.abs(x[:, i_tendencia]))
    return x


def crear_modelo(nombre, y_entrenamiento=None):
    if nombre == MODELO_LOGISTICO:
        return LogisticRegression(max_iter=1500, C=1.0)
    if nombre == MODELO_IMPULSO:
        return HistGradientBoostingClassifier(
            max_iter=220,
            learning_rate=0.06,
            max_leaf_nodes=7,
            min_samples_leaf=200,
            l2_regularization=5.0,
            early_stopping=True,
            validation_fraction=0.1,
            n_iter_no_change=15,
            random_state=42,
        )
    if nombre == MODELO_CATBOOST:
        try:
            from catboost import CatBoostClassifier
        except ImportError as exc:
            raise SystemExit(
                "Falta CatBoost. Instálalo con: python -m pip install catboost"
            ) from exc
        return CatBoostClassifier(
            iterations=500,
            depth=6,
            learning_rate=0.05,
            loss_function="Logloss",
            eval_metric="AUC",
            auto_class_weights="SqrtBalanced",
            l2_leaf_reg=8.0,
            random_seed=42,
            thread_count=-1,
            verbose=False,
            allow_writing_files=False,
        )
    if nombre == MODELO_LIGHTGBM:
        try:
            from lightgbm import LGBMClassifier
        except ImportError as exc:
            raise SystemExit(
                "Falta LightGBM. Instálalo con: python -m pip install lightgbm"
            ) from exc
        if y_entrenamiento is None:
            raise ValueError("LightGBM necesita las etiquetas de entrenamiento para ponderar las clases.")
        positivos = int(np.count_nonzero(y_entrenamiento))
        negativos = len(y_entrenamiento) - positivos
        if positivos == 0 or negativos == 0:
            raise ValueError("LightGBM necesita ejemplos de ambas clases.")
        peso_positivo = float(np.sqrt(negativos / positivos))
        return LGBMClassifier(
            objective="binary",
            n_estimators=500,
            learning_rate=0.05,
            num_leaves=15,
            max_depth=6,
            min_child_samples=250,
            subsample=0.8,
            subsample_freq=1,
            colsample_bytree=0.8,
            reg_alpha=0.1,
            reg_lambda=5.0,
            scale_pos_weight=peso_positivo,
            random_state=42,
            n_jobs=-1,
            verbosity=-1,
            deterministic=True,
            force_col_wise=True,
        )
    raise ValueError(f"Modelo no reconocido: {nombre}")
