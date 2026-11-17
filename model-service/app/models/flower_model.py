"""
    Flower 模型 —— 花卉分类（从零训练的 CNN）

    对外提供：train_model()、load_model()、predict(model, image_bytes)
    权重文件：weights/flower_cnn_model.h5
"""

import os
import io
import numpy as np
from pathlib import Path
from PIL import Image, UnidentifiedImageError

os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
os.environ["TF_AUTOGRAPH_WARNINGS"] = "0"

IMG_HEIGHT = 180
IMG_WIDTH = 180


def _weight_path():
    """返回花卉模型权重文件的路径。"""
    project_root = Path(__file__).resolve().parent.parent.parent
    return project_root / "weights" / "flower_cnn_model.h5"


def _data_dir():
    """返回花卉数据集的根目录路径。"""
    project_root = Path(__file__).resolve().parent.parent.parent
    return project_root / "Dataset" / "flower_photos"


def _class_names():
    """
        从数据集目录推导类别名称。
    """
    data = _data_dir()
    if data.exists():
        return sorted([d.name for d in data.iterdir() if d.is_dir()])
    return ["daisy", "dandelion", "roses", "sunflowers", "tulips"]  # fallback


def train_model():
    """
        从零训练花卉 CNN，并保存到 weights/ 目录。
    """
    import tensorflow as tf
    from tensorflow.keras import layers
    from tensorflow.keras.models import Sequential

    save_path = _weight_path()
    save_path.parent.mkdir(exist_ok=True, parents=True)
    data_dir = _data_dir()

    batch_size = 4
    input_shape = (IMG_HEIGHT, IMG_WIDTH, 3)

    train_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir, validation_split=0.2, subset="training", seed=123,
        image_size=(IMG_HEIGHT, IMG_WIDTH), batch_size=batch_size,
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir, validation_split=0.2, subset="validation", seed=123,
        image_size=(IMG_HEIGHT, IMG_WIDTH), batch_size=batch_size,
    )

    class_names = train_ds.class_names
    print("分类标签:", class_names)
    num_classes = len(class_names)

    AUTOTUNE = tf.data.AUTOTUNE
    train_ds = train_ds.cache().shuffle(1000).prefetch(buffer_size=AUTOTUNE)
    val_ds = val_ds.cache().prefetch(buffer_size=AUTOTUNE)

    data_augmentation = Sequential([
        layers.RandomFlip("horizontal"),
        layers.RandomRotation(0.1),
        layers.RandomZoom(0.1),
    ])

    model = Sequential([
        data_augmentation,
        layers.Rescaling(1./255, input_shape=input_shape),
        layers.Conv2D(16, 3, padding="same", activation="relu"),
        layers.MaxPooling2D(),
        layers.Conv2D(32, 3, padding="same", activation="relu"),
        layers.MaxPooling2D(),
        layers.Conv2D(64, 3, padding="same", activation="relu"),
        layers.MaxPooling2D(),
        layers.Dropout(0.2),
        layers.Flatten(),
        layers.Dense(128, activation="relu"),
        layers.Dense(num_classes),
    ])

    model.compile(
        optimizer="adam",
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True),
        metrics=["accuracy"],
    )
    model.build(input_shape=(None, *input_shape))
    model.summary()

    print("开始训练花卉分类模型...")
    model.fit(train_ds, validation_data=val_ds, epochs=15, verbose=1)
    # 原子写入：先写 .tmp 再 os.replace，避免中途崩溃留下损坏文件
    tmp_path = str(save_path) + ".tmp"
    model.save(tmp_path, save_format="h5")
    os.replace(tmp_path, str(save_path))  # atomic rename on same filesystem
    print(f"模型已保存至: {save_path}")
    return model


def load_model():
    """
        从 weights/flower_cnn_model.h5 加载花卉模型。

        使用 tf.keras.models.load_model()，而不是独立的 keras 包。
    """
    import tensorflow as tf

    path = str(_weight_path())

    print("[Flower] ========================================")
    print(f"[Flower] Loading model from: {path}")
    print(f"[Flower] File exists: {os.path.exists(path)}")
    if os.path.exists(path):
        print(f"[Flower] File size: {os.path.getsize(path) / 1024 / 1024:.1f} MB")
    else:
        raise FileNotFoundError(f"[Flower] 模型文件不存在: {path}")

    try:
        print(f"[Flower] Calling tf.keras.models.load_model()...")
        model = tf.keras.models.load_model(path)
        print(f"[Flower] Model loaded successfully!")
        print(f"[Flower] Model type: {type(model).__name__}")
        print(f"[Flower] ========================================")
        return model
    except Exception as e:
        import sys
        print(f"[Flower] ========================================")
        print(f"[Flower] ERROR: Model load failed!")
        print(f"[Flower] Exception type: {type(e).__name__}")
        print(f"[Flower] Exception message: {e}")
        print(f"[Flower] Python version: {sys.version}")
        print(f"[Flower] TensorFlow version: {tf.__version__}")
        print(f"[Flower] File path: {path}")

        # 附加诊断：读取文件头判断实际格式
        try:
            with open(path, "rb") as f:
                header = f.read(8)
                print(f"[Flower] File header (hex): {header.hex()}")
                if header[:4] == b"PK\x03\x04":
                    print("[Flower] → 文件是 ZIP 格式（Keras v3 .keras 格式）")
                elif header[:4] == b"\x89HDF":
                    print("[Flower] → 文件是 HDF5 格式")
                else:
                    print("[Flower] → 文件格式未知，可能已损坏")
        except Exception as diag_err:
            print(f"[Flower] 无法读取文件头: {diag_err}")

        print(f"[Flower] ========================================")
        raise


def predict(model, image_bytes: bytes) -> dict:
    """
        对图片字节做花卉分类。
    """
    import tensorflow as tf
    import time

    t0 = time.time()

    try:
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except UnidentifiedImageError:
        raise ValueError("Cannot identify image file — file may be corrupt or in an unsupported format")
    image = image.resize((IMG_WIDTH, IMG_HEIGHT))
    img_array = np.expand_dims(np.array(image, dtype=np.float32) / 255.0, axis=0)

    logits = model.predict(img_array, verbose=0)[0]
    probs = tf.nn.softmax(logits).numpy()

    class_names = _class_names()
    idx = int(np.argmax(probs))

    candidates = [
        {"label": class_names[i], "probability": round(float(probs[i]), 4)}
        for i in np.argsort(probs)[::-1]
    ]

    elapsed = int((time.time() - t0) * 1000)
    return {
        "model_name": "FlowerCNN",
        "top_label": class_names[idx],
        "confidence": round(float(probs[idx]), 4),
        "duration": elapsed,
        "candidates": candidates,
    }


# ═══════════════════════════════════════════════════════════════
# 独立测试入口
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 60)
    print("Flower Model — 独立加载测试")
    print("=" * 60)
    model = load_model()
    print("=" * 60)
    print("✅ Model loaded successfully!")
    print("=" * 60)
