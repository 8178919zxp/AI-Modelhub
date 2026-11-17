"""
    在 Docker 构建阶段预先下载全部模型权重。

    本脚本把运行时会自动下载的模型提前拉取到本地：
    - ResNet-50：torchvision 预训练权重（约 100 MB）
    - BERT：HuggingFace distilbert 情感分析 pipeline（约 270 MB）
    - YOLOv8：ultralytics yolov8n.pt（约 6 MB）

    所有权重统一保存到 /app/weights/，运行时可完全离线加载。

    用法：
    python download_weights.py          # 默认保存到 /app/weights
    WEIGHTS_DIR=/custom/path python download_weights.py
"""

import os
import shutil

WEIGHTS_DIR = os.environ.get("WEIGHTS_DIR", "/app/weights")
os.makedirs(WEIGHTS_DIR, exist_ok=True)

import torch


def download_resnet50():
    """
        从 torchvision 下载 ResNet-50 预训练权重，并保存 state_dict。
    """
    print("[download] Downloading ResNet-50 weights from torchvision (~100 MB)...")
    from torchvision.models import resnet50, ResNet50_Weights

    # 通过 torchvision 官方 API 触发下载
    model = resnet50(weights=ResNet50_Weights.DEFAULT)

    # 把 state_dict 保存到本地，供离线加载
    path = os.path.join(WEIGHTS_DIR, "resnet50-0676ba61.pth")
    torch.save(model.state_dict(), path)
    size_mb = os.path.getsize(path) / (1024 * 1024)
    print(f"[download] ResNet-50 saved: {path} ({size_mb:.1f} MB)")


def download_bert():
    """
        从 HuggingFace 下载 BERT 情感分析模型，保存到本地目录。
    """
    print("[download] Downloading BERT sentiment model from HuggingFace (~270 MB)...")
    from transformers import AutoTokenizer, AutoModelForSequenceClassification

    model_name = "distilbert-base-uncased-finetuned-sst-2-english"
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSequenceClassification.from_pretrained(model_name)

    save_dir = os.path.join(WEIGHTS_DIR, "bert_sentiment")
    os.makedirs(save_dir, exist_ok=True)
    tokenizer.save_pretrained(save_dir)
    model.save_pretrained(save_dir)

    total_size = sum(
        os.path.getsize(os.path.join(dirpath, filename))
        for dirpath, _dirnames, filenames in os.walk(save_dir)
        for filename in filenames
    )
    print(f"[download] BERT sentiment saved: {save_dir} ({total_size / (1024 * 1024):.1f} MB)")


def download_yolov8():
    """
        从 ultralytics 下载 YOLOv8n 权重。
    """
    print("[download] Downloading YOLOv8n weights from ultralytics (~6 MB)...")
    from ultralytics import YOLO

    # 该调用会把 yolov8n.pt 下载到当前目录
    YOLO("yolov8n.pt")

    # 如果权重目录还没有该文件，就复制过去
    src = "yolov8n.pt"
    dst = os.path.join(WEIGHTS_DIR, "yolov8n.pt")
    if os.path.abspath(src) != os.path.abspath(dst):
        shutil.copy(src, dst)
    size_mb = os.path.getsize(dst) / (1024 * 1024)
    print(f"[download] YOLOv8n saved: {dst} ({size_mb:.1f} MB)")


if __name__ == "__main__":
    print("=" * 60)
    print("Model weights download — Docker build phase")
    print(f"Target directory: {WEIGHTS_DIR}")
    print("=" * 60)

    download_resnet50()
    download_bert()
    download_yolov8()

    # 打印权重目录下所有文件的汇总
    print("\n[download] Weights directory contents:")
    for root, dirs, files in os.walk(WEIGHTS_DIR):
        level = root.replace(WEIGHTS_DIR, "").count(os.sep)
        indent = " " * 2 * level
        print(f"{indent}{os.path.basename(root)}/")
        subindent = " " * 2 * (level + 1)
        for fname in sorted(files):
            fpath = os.path.join(root, fname)
            size_kb = os.path.getsize(fpath) / 1024
            if size_kb > 1024:
                print(f"{subindent}{fname} ({size_kb / 1024:.1f} MB)")
            else:
                print(f"{subindent}{fname} ({size_kb:.1f} KB)")

    print("\n[download] All weights downloaded successfully.")
