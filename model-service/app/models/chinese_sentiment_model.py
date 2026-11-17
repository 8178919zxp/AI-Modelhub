"""
    中文情感模型 —— 中文酒店评论情感分类（MLPClassifier + CountVectorizer）

    对外提供：train_model()、load_model()、predict(model_tuple, text)
    权重文件：weights/chinese_sentiment_mlp.pkl 与 weights/chinese_vectorizer.pkl
"""

import os
import re
import time
import joblib
import jieba
import logging
import numpy as np
import pandas as pd
from pathlib import Path

jieba.setLogLevel(logging.ERROR)

# ── 统一路径 ─────────────────────────────────────────────────
_SCRIPT_PATH = Path(__file__).resolve()
_PROJECT_ROOT = _SCRIPT_PATH.parent.parent.parent

_CSV_PATH = _PROJECT_ROOT / "Dataset" / "ChnSentiCorp_htl_all.csv"
_MLP_PATH = _PROJECT_ROOT / "weights" / "chinese_sentiment_mlp.pkl"
_VECT_PATH = _PROJECT_ROOT / "weights" / "chinese_vectorizer.pkl"

# 标签映射（0=负面，1=正面）
_LABEL_NAMES = {0: "负面", 1: "正面"}


def clean_text(x):
    """
        清洗文本：只保留中文字符。
    """
    text = re.sub(r"[^一-龥]", "", str(x))
    return text if text.strip() else ""


# ═══════════════════════════════════════════════════════════════
# 训练模型
# ═══════════════════════════════════════════════════════════════

def train_model():
    """
        用中文酒店评论训练 MLPClassifier + CountVectorizer。
    """
    from sklearn.neural_network import MLPClassifier
    from sklearn.feature_extraction.text import CountVectorizer
    from sklearn.model_selection import train_test_split

    print("[ChineseSentiment] Training Chinese Sentiment model...")
    print(f"[ChineseSentiment] CSV path: {_CSV_PATH}")

    df = pd.read_csv(_CSV_PATH).dropna().reset_index(drop=True)
    df["review"] = df["review"].apply(clean_text)
    df = df[df["review"].str.strip() != ""].reset_index(drop=True)

    def seg_text(text):
        """用 jieba 对中文文本分词，词之间用空格连接，作为词袋特征输入。"""
        return " ".join(jieba.cut(text))

    df["seg"] = df["review"].apply(seg_text)

    vect = CountVectorizer(max_features=5000)
    X = vect.fit_transform(df["seg"])
    y = df["label"]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.1, random_state=42
    )

    model = MLPClassifier(
        hidden_layer_sizes=(64,), max_iter=300, early_stopping=True, random_state=42
    )
    model.fit(X_train, y_train)

    train_acc = model.score(X_train, y_train)
    test_acc = model.score(X_test, y_test)
    print(f"[ChineseSentiment] 训练准确率: {train_acc:.4f}")
    print(f"[ChineseSentiment] 测试准确率: {test_acc:.4f}")

    _MLP_PATH.parent.mkdir(exist_ok=True, parents=True)

    # 原子写入：先写 .tmp 再 os.replace，避免崩溃时留下损坏文件
    mlp_tmp = str(_MLP_PATH) + ".tmp"
    vect_tmp = str(_VECT_PATH) + ".tmp"
    joblib.dump(model, mlp_tmp)
    joblib.dump(vect, vect_tmp)
    os.replace(mlp_tmp, str(_MLP_PATH))
    os.replace(vect_tmp, str(_VECT_PATH))

    print(f"[ChineseSentiment] 模型已保存至: {_MLP_PATH}")
    print(f"[ChineseSentiment] 向量器已保存至: {_VECT_PATH}")


# ═══════════════════════════════════════════════════════════════
# 加载模型
# ═══════════════════════════════════════════════════════════════

def load_model():
    """
        从 weights/ 目录加载 MLPClassifier 与 CountVectorizer。

        返回：
        (model, vectorizer) 元组
    """
    mlp_path = str(_MLP_PATH)
    vect_path = str(_VECT_PATH)

    print("[ChineseSentiment] ========================================")
    print(f"[ChineseSentiment] Loading MLP model from: {mlp_path}")
    print(f"[ChineseSentiment] MLP file exists: {os.path.exists(mlp_path)}")
    if os.path.exists(mlp_path):
        print(f"[ChineseSentiment] MLP file size: {os.path.getsize(mlp_path) / 1024:.1f} KB")

    print(f"[ChineseSentiment] Loading vectorizer from: {vect_path}")
    print(f"[ChineseSentiment] Vectorizer file exists: {os.path.exists(vect_path)}")
    if os.path.exists(vect_path):
        print(f"[ChineseSentiment] Vectorizer file size: {os.path.getsize(vect_path) / 1024:.1f} KB")

    # 两个权重文件都必须存在
    missing = []
    if not os.path.exists(mlp_path):
        missing.append(mlp_path)
    if not os.path.exists(vect_path):
        missing.append(vect_path)
    if missing:
        raise FileNotFoundError(
            f"[ChineseSentiment] 模型文件缺失: {', '.join(missing)}"
        )

    try:
        model = joblib.load(mlp_path)
        print(f"[ChineseSentiment] MLP model loaded! Type: {type(model).__name__}")
    except Exception as e:
        print(f"[ChineseSentiment] ERROR: Failed to load MLP model!")
        print(f"[ChineseSentiment] Exception: {type(e).__name__}: {e}")
        raise

    try:
        vect = joblib.load(vect_path)
        print(f"[ChineseSentiment] Vectorizer loaded! Type: {type(vect).__name__}")
    except Exception as e:
        print(f"[ChineseSentiment] ERROR: Failed to load vectorizer!")
        print(f"[ChineseSentiment] Exception: {type(e).__name__}: {e}")
        raise

    print(f"[ChineseSentiment] ========================================")
    return model, vect


# ═══════════════════════════════════════════════════════════════
# 推理
# ═══════════════════════════════════════════════════════════════

def predict(model_tuple, text: str) -> dict:
    """
        对文本做中文情感分析。

        参数：
        model_tuple: load_model() 返回的 (MLPClassifier, CountVectorizer) 元组
    """
    model, vect = model_tuple
    t0 = time.time()

    # 清洗 + 分词
    cleaned = clean_text(text)
    if not cleaned:
        elapsed = int((time.time() - t0) * 1000)
        return {
            "model_name": "ChineseSentiment",
            "top_label": "无法识别（文本无有效中文）",
            "confidence": 0.0,
            "duration": elapsed,
            "candidates": [],
        }

    seg = " ".join(jieba.cut(cleaned))
    X = vect.transform([seg])

    # 预测类别与概率
    pred_class = int(model.predict(X)[0])
    try:
        proba = model.predict_proba(X)[0]
        confidence = round(float(proba[pred_class]), 4)
        candidates = [
            {"label": _LABEL_NAMES.get(i, f"class_{i}"), "probability": round(float(proba[i]), 4)}
            for i in np.argsort(proba)[::-1]
        ]
    except Exception:
        # 若 predict_proba 不可用，则只返回标签
        confidence = 1.0
        candidates = [
            {"label": _LABEL_NAMES.get(pred_class, str(pred_class)), "probability": 1.0}
        ]

    elapsed = int((time.time() - t0) * 1000)

    return {
        "model_name": "ChineseSentiment",
        "top_label": _LABEL_NAMES.get(pred_class, str(pred_class)),
        "confidence": confidence,
        "duration": elapsed,
        "candidates": candidates,
    }


# ═══════════════════════════════════════════════════════════════
# 独立测试入口
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 60)
    print("Chinese Sentiment Model — 独立加载测试")
    print("=" * 60)
    model = load_model()
    print("=" * 60)
    print("✅ Model loaded successfully!")

    # 简单预测测试
    test_text = "这家酒店非常好，服务态度很棒，下次还会再来！"
    print(f"\n测试文本: {test_text}")
    result = predict(model, test_text)
    print(f"预测结果: {result['top_label']} (置信度: {result['confidence']})")
    print("=" * 60)
