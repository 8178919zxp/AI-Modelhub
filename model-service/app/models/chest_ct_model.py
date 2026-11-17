"""
    ChestCT 模型 —— 胸部 CT 肺癌分类（ResNet50 迁移学习）

    对外提供：train_model()、load_model()、predict(model, image_bytes)
    权重文件：weights/chest_ct_resnet50.h5
"""

import os
import io
import sys
import numpy as np
from pathlib import Path
from PIL import Image, UnidentifiedImageError

os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

CLASS_NAMES = ["adenocarcinoma", "large.cell.carcinoma", "normal", "squamous.cell.carcinoma"]
IMAGE_SHAPE = (224, 224)

# ── 统一路径：project_root / weights / chest_ct_resnet50.h5 ──
_SCRIPT_DIR = Path(__file__).resolve().parent          # app/models
_PROJECT_ROOT = _SCRIPT_DIR.parent.parent               # model-service/
_WEIGHT_PATH = _PROJECT_ROOT / "weights" / "chest_ct_resnet50.h5"


def _data_paths():
    """返回训练集与验证集所在的目录路径。"""
    base = _PROJECT_ROOT / "Dataset" / "archive" / "Data"
    return base / "train", base / "valid", base / "test"


# ═══════════════════════════════════════════════════════════════
# 训练模型
# ═══════════════════════════════════════════════════════════════

def train_model():
    """
        在胸部 CT 数据集上训练 ResNet50，并把权重保存到 weights/ 目录。
    """
    import tensorflow as tf
    from tensorflow.keras import optimizers
    from tensorflow.keras.preprocessing.image import ImageDataGenerator
    from tensorflow.keras.models import Sequential
    from tensorflow.keras.layers import Dense, Dropout, BatchNormalization
    from tensorflow.keras.callbacks import EarlyStopping
    from tensorflow.keras.applications import ResNet50
    from tensorflow.keras.optimizers.schedules import ExponentialDecay

    train_path, valid_path, test_path = _data_paths()
    save_path = _WEIGHT_PATH
    save_path.parent.mkdir(exist_ok=True, parents=True)

    print(f"[ChestCT] 权重将保存至: {save_path}")
    print(f"[ChestCT] 数据集路径: train={train_path}, valid={valid_path}, test={test_path}")

    N_CLASSES = len(CLASS_NAMES)
    BATCH_SIZE = 32

    train_datagen = ImageDataGenerator(
        preprocessing_function=tf.keras.applications.resnet50.preprocess_input,
        dtype="float32",
        shear_range=0.2,
        zoom_range=0.2,
        horizontal_flip=True,
    )
    train_generator = train_datagen.flow_from_directory(
        train_path, batch_size=BATCH_SIZE, target_size=IMAGE_SHAPE,
        class_mode="categorical", shuffle=True, classes=CLASS_NAMES,
    )
    valid_datagen = ImageDataGenerator(
        preprocessing_function=tf.keras.applications.resnet50.preprocess_input,
        dtype="float32",
    )
    valid_generator = valid_datagen.flow_from_directory(
        valid_path, batch_size=BATCH_SIZE, target_size=IMAGE_SHAPE,
        class_mode="categorical", shuffle=False, classes=CLASS_NAMES,
    )

    print(f"[ChestCT] 训练样本: {train_generator.samples}, 验证样本: {valid_generator.samples}")

    res_model = ResNet50(include_top=False, pooling="avg", weights="imagenet", input_shape=(*IMAGE_SHAPE, 3))
    for layer in res_model.layers:
        layer.trainable = "conv5" not in layer.name

    model = Sequential([
        res_model,
        Dropout(0.4),
        BatchNormalization(),
        Dense(256, activation="relu"),
        Dropout(0.3),
        Dense(N_CLASSES, activation="softmax"),
    ])

    lr_schedule = ExponentialDecay(initial_learning_rate=0.0001, decay_steps=10000, decay_rate=0.9, staircase=True)
    optimizer = optimizers.Adam(learning_rate=lr_schedule)
    model.compile(optimizer=optimizer, loss="categorical_crossentropy", metrics=["accuracy"])

    callbacks = [EarlyStopping(monitor="val_loss", patience=15, restore_best_weights=True)]

    print("[ChestCT] 开始训练...")
    model.fit(
        train_generator, steps_per_epoch=len(train_generator), epochs=100,
        validation_data=valid_generator, validation_steps=len(valid_generator),
        callbacks=callbacks, verbose=1,
    )
    # 原子写入：先写 .tmp，再用 os.replace 替换，避免中途崩溃留下损坏文件
    tmp_path = str(save_path) + ".tmp"
    model.save(tmp_path, save_format="h5")
    os.replace(tmp_path, str(save_path))  # atomic rename on same filesystem
    print(f"[ChestCT] 模型已保存至: {save_path}")
    print(f"[ChestCT] 文件大小: {save_path.stat().st_size / 1024 / 1024:.1f} MB")

    # 在测试集上评估
    test_datagen = ImageDataGenerator(
        preprocessing_function=tf.keras.applications.resnet50.preprocess_input, dtype="float32",
    )
    test_generator = test_datagen.flow_from_directory(
        test_path, batch_size=BATCH_SIZE, target_size=IMAGE_SHAPE,
        class_mode="categorical", shuffle=False, classes=CLASS_NAMES,
    )
    test_loss, test_acc = model.evaluate(test_generator, steps=len(test_generator))
    print(f"[ChestCT] 测试损失: {test_loss:.4f} | 测试准确率: {test_acc:.4f}")
    return model


# ═══════════════════════════════════════════════════════════════
# 加载模型
# ═══════════════════════════════════════════════════════════════

def load_model():
    """
        从 weights/chest_ct_resnet50.h5 加载训练好的模型。

        使用 tf.keras.models.load_model()，而不是独立的 keras 包。
    """
    import tensorflow as tf

    path = str(_WEIGHT_PATH)

    print("[ChestCT] ========================================")
    print(f"[ChestCT] Loading model from: {path}")
    print(f"[ChestCT] File exists: {os.path.exists(path)}")
    if os.path.exists(path):
        print(f"[ChestCT] File size: {os.path.getsize(path) / 1024 / 1024:.1f} MB")
    else:
        raise FileNotFoundError(f"[ChestCT] 模型文件不存在: {path}")

    try:
        print(f"[ChestCT] Calling tf.keras.models.load_model()...")
        model = tf.keras.models.load_model(path)
        print(f"[ChestCT] Model loaded successfully!")
        print(f"[ChestCT] Model type: {type(model).__name__}")
        print(f"[ChestCT] ========================================")
        return model
    except Exception as e:
        print(f"[ChestCT] ========================================")
        print(f"[ChestCT] ERROR: Model load failed!")
        print(f"[ChestCT] Exception type: {type(e).__name__}")
        print(f"[ChestCT] Exception message: {e}")
        print(f"[ChestCT] Python version: {sys.version}")
        print(f"[ChestCT] TensorFlow version: {tf.__version__}")
        print(f"[ChestCT] File path: {path}")
        print(f"[ChestCT] ========================================")

        # 额外诊断：尝试读取文件头来判断实际格式
        try:
            with open(path, "rb") as f:
                header = f.read(8)
                print(f"[ChestCT] File header (hex): {header.hex()}")
                if header[:4] == b"PK\x03\x04":
                    print("[ChestCT] → 文件是 ZIP 格式（Keras v3 .keras 格式）")
                elif header[:4] == b"\x89HDF":
                    print("[ChestCT] → 文件是 HDF5 格式")
                else:
                    print("[ChestCT] → 文件格式未知，可能已损坏")
        except Exception as diag_err:
            print(f"[ChestCT] 无法读取文件头: {diag_err}")

        raise


# ═══════════════════════════════════════════════════════════════
# 推理
# ═══════════════════════════════════════════════════════════════

def predict(model, image_bytes: bytes) -> dict:
    """
        对图片字节做胸部 CT 分类。
    """
    import tensorflow as tf
    import time

    t0 = time.time()

    try:
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except UnidentifiedImageError:
        raise ValueError("Cannot identify image file — file may be corrupt or in an unsupported format")
    image = image.resize(IMAGE_SHAPE)
    img_array = tf.keras.applications.resnet50.preprocess_input(
        np.expand_dims(np.array(image, dtype=np.float32), axis=0)
    )

    probs = model.predict(img_array, verbose=0)[0]
    idx = int(np.argmax(probs))

    candidates = [
        {"label": CLASS_NAMES[i], "probability": round(float(probs[i]), 4)}
        for i in np.argsort(probs)[::-1]
    ]

    elapsed = int((time.time() - t0) * 1000)
    return {
        "model_name": "ChestCT",
        "top_label": CLASS_NAMES[idx],
        "confidence": round(float(probs[idx]), 4),
        "duration": elapsed,
        "candidates": candidates,
    }


# ═══════════════════════════════════════════════════════════════
# 独立测试入口
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 60)
    print("ChestCT Model — 独立加载测试")
    print("=" * 60)
    model = load_model()
    print("=" * 60)
    print("✅ Model loaded successfully!")
    print("=" * 60)
