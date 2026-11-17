"""
    Stock 模型 —— 股票回归（LinearRegression）

    对外提供：train_model()、load_model()、predict(model, features_dict)
    权重文件：weights/stock_regression.pkl
"""

from pathlib import Path


def _weight_path():
    """返回股票回归模型权重文件的路径。"""
    project_root = Path(__file__).resolve().parent.parent.parent
    return project_root / "weights" / "stock_regression.pkl"


def _csv_path():
    """返回训练用的股票数据集路径。"""
    project_root = Path(__file__).resolve().parent.parent.parent
    return project_root / "Dataset" / "TWTR.csv"


def train_model():
    """
        在股票数据上训练 LinearRegression，并保存到 weights/ 目录。
    """
    import joblib
    import pandas as pd
    from sklearn.linear_model import LinearRegression
    from sklearn.model_selection import train_test_split
    from sklearn.metrics import mean_squared_error

    save_path = _weight_path()
    save_path.parent.mkdir(exist_ok=True, parents=True)

    df = pd.read_csv(_csv_path()).dropna()
    X = df[["Open", "High", "Low", "Volume"]]
    y = df["Close"]

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    model = LinearRegression()
    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    mse = mean_squared_error(y_test, y_pred)
    print(f"股票回归模型 MSE: {mse:.4f}")

    # 原子写入：先写 .tmp 再 os.replace，避免中途崩溃留下损坏文件
    import os as _atomic_os
    tmp_path = str(save_path) + ".tmp"
    joblib.dump(model, tmp_path)
    _atomic_os.replace(tmp_path, str(save_path))  # atomic rename on same filesystem
    print(f"模型已保存至: {save_path}")
    return model


def load_model():
    """
        从 weights/stock_regression.pkl 加载股票模型。
    """
    import joblib
    import os as _os

    path = str(_weight_path())

    print("[Stock] ========================================")
    print(f"[Stock] Loading model from: {path}")
    print(f"[Stock] File exists: {_os.path.exists(path)}")
    if _os.path.exists(path):
        print(f"[Stock] File size: {_os.path.getsize(path) / 1024:.1f} KB")
    else:
        raise FileNotFoundError(f"[Stock] 模型文件不存在: {path}")

    try:
        model = joblib.load(path)
        print(f"[Stock] Model loaded successfully!")
        print(f"[Stock] Model type: {type(model).__name__}")
        print(f"[Stock] ========================================")
        return model
    except Exception as e:
        print(f"[Stock] ERROR: Model load failed!")
        print(f"[Stock] Exception type: {type(e).__name__}")
        print(f"[Stock] Exception message: {e}")
        print(f"[Stock] ========================================")
        raise


def predict(model, features: dict) -> dict:
    """
        用特征字典做收盘价回归。

        必需字段：Open、High、Low、Volume
    """
    import pandas as pd
    import time

    t0 = time.time()

    feature_keys = ["Open", "High", "Low", "Volume"]
    missing = [k for k in feature_keys if k not in features]
    if missing:
        raise ValueError(f"Missing required fields: {', '.join(missing)}")
    values = [features[k] for k in feature_keys]
    X = pd.DataFrame([values], columns=feature_keys)

    pred_value = float(model.predict(X)[0])
    elapsed = int((time.time() - t0) * 1000)

    return {
        "model_name": "StockRegression",
        "top_label": f"预测收盘价: {pred_value:.4f}",
        "confidence": 1.0,
        "predicted_close": round(pred_value, 4),
        "duration": elapsed,
        "candidates": [
            {"label": f"预测收盘价: {pred_value:.4f}", "probability": 1.0}
        ],
    }


# ═══════════════════════════════════════════════════════════════
# 独立测试入口
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 60)
    print("Stock Model — 独立加载测试")
    print("=" * 60)
    model = load_model()
    print("=" * 60)
    print("✅ Model loaded successfully!")
    print("=" * 60)
