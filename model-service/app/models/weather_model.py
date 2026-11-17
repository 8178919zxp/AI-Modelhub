"""
    Weather 模型 —— 气象分类（GradientBoostingClassifier）

    对外提供：train_model()、load_model()、predict(model, features_dict)
    权重文件：weights/weather_gbm.pkl
"""

import os
from pathlib import Path

WEATHER_LABELS = ["rain", "sun", "fog", "drizzle", "snow"]


def _weight_path():
    """返回天气模型权重文件的路径。"""
    project_root = Path(__file__).resolve().parent.parent.parent
    return project_root / "weights" / "weather_gbm.pkl"


def _csv_path():
    """返回训练用的西雅图气象数据集路径。"""
    project_root = Path(__file__).resolve().parent.parent.parent
    return project_root / "Dataset" / "seattle-weather.csv"


def train_model():
    """
        在西雅图气象数据集上训练 GradientBoostingClassifier，并保存到 weights/ 目录。
    """
    import joblib
    import pandas as pd
    import numpy as np
    from sklearn.ensemble import GradientBoostingClassifier
    from sklearn.model_selection import train_test_split
    from sklearn.metrics import accuracy_score

    save_path = _weight_path()
    save_path.parent.mkdir(exist_ok=True, parents=True)

    df = pd.read_csv(_csv_path())
    weather_map = {"rain": 0, "sun": 1, "fog": 2, "drizzle": 3, "snow": 4}
    df["weather"] = df["weather"].map(weather_map)
    df["date"] = pd.to_datetime(df["date"])
    df["year"] = df["date"].apply(lambda x: x.year)
    df["month"] = df["date"].apply(lambda x: x.month)
    df["day"] = df["date"].apply(lambda x: x.day)

    features = ["precipitation", "temp_max", "temp_min", "wind", "year", "month", "day"]
    X = df[features]
    y = df["weather"]

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=100)
    model = GradientBoostingClassifier(random_state=42)
    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    acc = accuracy_score(y_test, y_pred)
    print(f"天气分类模型准确率: {acc:.4f}")

    # 原子写入：先写 .tmp 再 os.replace，避免中途崩溃留下损坏文件
    tmp_path = str(save_path) + ".tmp"
    joblib.dump(model, tmp_path)
    os.replace(tmp_path, str(save_path))  # atomic rename on same filesystem
    print(f"模型已保存至: {save_path}")
    return model


def load_model():
    """
        从 weights/weather_gbm.pkl 加载天气模型。
    """
    import joblib
    import os as _os

    path = str(_weight_path())

    print("[Weather] ========================================")
    print(f"[Weather] Loading model from: {path}")
    print(f"[Weather] File exists: {_os.path.exists(path)}")
    if _os.path.exists(path):
        print(f"[Weather] File size: {_os.path.getsize(path) / 1024:.1f} KB")
    else:
        raise FileNotFoundError(f"[Weather] 模型文件不存在: {path}")

    try:
        model = joblib.load(path)
        print(f"[Weather] Model loaded successfully!")
        print(f"[Weather] Model type: {type(model).__name__}")
        print(f"[Weather] ========================================")
        return model
    except Exception as e:
        print(f"[Weather] ERROR: Model load failed!")
        print(f"[Weather] Exception type: {type(e).__name__}")
        print(f"[Weather] Exception message: {e}")
        print(f"[Weather] ========================================")
        raise


def predict(model, features: dict) -> dict:
    """
        用特征字典做天气分类。

        必需字段：precipitation、temp_max、temp_min、wind、year、month、day
    """
    import pandas as pd
    import time
    import numpy as np

    t0 = time.time()

    feature_keys = ["precipitation", "temp_max", "temp_min", "wind", "year", "month", "day"]
    missing = [k for k in feature_keys if k not in features]
    if missing:
        raise ValueError(f"Missing required fields: {', '.join(missing)}")
    values = [features[k] for k in feature_keys]
    X = pd.DataFrame([values], columns=feature_keys)

    pred_class = int(model.predict(X)[0])
    probs = model.predict_proba(X)[0]

    candidates = [
        {"label": WEATHER_LABELS[i], "probability": round(float(probs[i]), 4)}
        for i in np.argsort(probs)[::-1]
    ]

    elapsed = int((time.time() - t0) * 1000)
    return {
        "model_name": "WeatherGBM",
        "top_label": WEATHER_LABELS[pred_class],
        "confidence": round(float(probs[pred_class]), 4),
        "duration": elapsed,
        "candidates": candidates,
    }


# ═══════════════════════════════════════════════════════════════
# 独立测试入口
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 60)
    print("Weather Model — 独立加载测试")
    print("=" * 60)
    model = load_model()
    print("=" * 60)
    print("✅ Model loaded successfully!")
    print("=" * 60)
