"""
    === YOLOv8 目标检测器 ===

    模型来源：ultralytics YOLOv8（COCO 预训练）
    - 默认：weights/yolov8n.pt（nano，约 6MB，构建阶段预下载）
    - 可选：yolov8s.pt（约 22MB）、yolov8m.pt（约 52MB）

    输入格式：PIL Image（RGB）或图片字节（JPEG/PNG）
    输出格式：检测到的目标列表，包含：
    - label：中文 COCO 类别名（如「人」「汽车」「狗」）
    - probability：置信度（0-1）
    - bbox：[x1, y1, x2, y2]，像素坐标

    中文标签：加载时从 app/resources/coco_zh.json 读取，缺失时回落英文。

    权重加载策略（由 MODEL_STRICT_OFFLINE 控制）：
    - true  → 只用本地文件，缺失时报错（Docker 生产环境）
    - false → 优先本地，缺失时自动下载（开发环境）
"""

import io
import json
import os
import time
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

import numpy as np
from PIL import Image

from app.models.base import BaseModel

# ── 离线模式开关 ───────────────────────────────────────────────
_STRICT_OFFLINE = os.getenv("MODEL_STRICT_OFFLINE", "false").lower() == "true"


class YOLOv8Model(BaseModel):
    """
        基于 ultralytics 的 YOLOv8 目标检测器。

        严格离线模式（MODEL_STRICT_OFFLINE=true）下只加载本地 .pt 文件；
        否则优先本地、缺失时自动下载。
    """

    # 可用的模型规格（按体积排序）
    VARIANTS = ["yolov8n.pt", "yolov8s.pt", "yolov8m.pt", "yolov8l.pt", "yolov8x.pt"]

    # 默认本地权重文件（构建阶段预下载）
    DEFAULT_WEIGHTS = "weights/yolov8n.pt"

    def __init__(self, device: str = "cpu", variant: str = "yolov8n.pt",
                 weights_path: Optional[str] = None):
        """
            参数：
            device: "cpu" 或 "cuda"
            variant: 模型文件名（如 "yolov8n.pt"）
            weights_path: 可选的本地权重路径
        """
        super().__init__(device=device)
        self.variant = variant
        self.weights_path = weights_path or self.DEFAULT_WEIGHTS
        self._model = None
        self._class_names: List[str] = []
        self._zh_map: Dict[str, str] = {}

    @property
    def model_name(self) -> str:
        """返回模型名称 YOLOv8。"""
        return "YOLOv8"

    def load(self) -> None:
        """
            加载 YOLOv8：优先本地文件，允许时自动下载。
        """
        print(f"[{self.model_name}] Loading on device={self.device} "
              f"(strict_offline={_STRICT_OFFLINE}) ...")
        t0 = time.time()

        try:
            from ultralytics import YOLO

            if os.path.exists(self.weights_path):
                self._load_from_local()
            elif _STRICT_OFFLINE:
                raise FileNotFoundError(
                    f"[{self.model_name}] Weights file not found: "
                    f"{self.weights_path}. "
                    f"MODEL_STRICT_OFFLINE=true — refusing to download. "
                    f"Run download_weights.py during Docker build to pre-download, "
                    f"or place a .pt file in weights/."
                )
            else:
                self._load_from_ultralytics()

            # 覆盖推理设备
            self._model.to(self.device)

            # COCO 类别名（英文）
            self._class_names = list(self._model.names.values()) if self._model.names else []

            self._loaded = True
            elapsed = time.time() - t0

            # 加载中文标签映射
            self._load_zh_map()

            print(f"[{self.model_name}] Loaded in {elapsed:.1f}s "
                  f"({len(self._class_names)} COCO classes, "
                  f"{len(self._zh_map)} Chinese labels)")

        except Exception as e:
            raise RuntimeError(
                f"[{self.model_name}] Failed to load: {e}"
            )

    def _load_from_local(self) -> None:
        """
            从本地 .pt 文件加载（完全离线）。
        """
        from ultralytics import YOLO

        print(f"[{self.model_name}] Loading from local: {self.weights_path}")
        self._model = YOLO(self.weights_path)

    def _load_from_ultralytics(self) -> None:
        """
            从 ultralytics 自动下载（仅开发模式）。
        """
        from ultralytics import YOLO

        print(f"[{self.model_name}] Local file not found — "
              f"auto-downloading from ultralytics: {self.variant} (~6 MB)...")
        self._model = YOLO(self.variant)

    def _load_zh_map(self) -> None:
        """
            从 JSON 文件加载 COCO 中文标签映射。
        """
        try:
            with open("app/resources/coco_zh.json", "r", encoding="utf-8") as f:
                self._zh_map = json.load(f)
            print(f"[{self.model_name}] Loaded {len(self._zh_map)} Chinese labels "
                  f"from coco_zh.json")
        except FileNotFoundError:
            print(f"[{self.model_name}] Warning: coco_zh.json not found, "
                  f"using English labels only")
            self._zh_map = {}
        except Exception as e:
            print(f"[{self.model_name}] Warning: Failed to load coco_zh.json: {e}")
            self._zh_map = {}

    def predict(self, input_data: Any, top_k: int = 10, conf_threshold: float = 0.25,
                **kwargs) -> Dict:
        """
            检测图片中的目标。

            参数：
            input_data: PIL.Image（RGB）、字节或文件路径
            top_k: 最多返回的目标数
        """
        self._validate_loaded()

        t0 = time.time()

        # 转成 PIL Image，便于记录图片尺寸
        image = self._to_pil(input_data)
        w, h = image.size
        print(f"[{self.model_name}] Input: {w}x{h}, "
              f"conf_threshold={conf_threshold}, top_k={top_k}")

        # 执行推理
        # 直接把图片交给 ultralytics（支持 PIL、ndarray、路径、字节）
        source = input_data
        if isinstance(input_data, bytes):
            source = image  # Use PIL version
        elif isinstance(input_data, np.ndarray):
            source = input_data

        try:
            raw_results = self._model(
                source,
                conf=conf_threshold,
                verbose=False,
            )
        except Exception:
            # 用 PIL 图片重试一次
            raw_results = self._model(image, conf=conf_threshold, verbose=False)

        # 解析结果
        detections = []
        for result in raw_results:
            boxes = result.boxes
            if boxes is not None and len(boxes) > 0:
                for box in boxes:
                    cls_id = int(box.cls[0])
                    conf = float(box.conf[0])
                    xyxy = box.xyxy[0].tolist()  # [x1, y1, x2, y2]

                    # 从 COCO 类别名取英文标签
                    label_en = (self._class_names[cls_id]
                                if cls_id < len(self._class_names)
                                else f"class_{cls_id}")

                    # 映射为中文（没有中文时回落英文）
                    label = self._zh_map.get(label_en, label_en)

                    detections.append({
                        "label": label,
                        "probability": round(conf, 6),
                        "bbox": [round(float(v), 1) for v in xyxy],
                    })

        # 按置信度排序并限制数量
        detections.sort(key=lambda x: x["probability"], reverse=True)
        detections = detections[:top_k]

        duration_ms = int((time.time() - t0) * 1000)

        if detections:
            labels_summary = ", ".join(
                f"{d['label']}({d['probability']:.2f})"
                for d in detections[:3]
            )
            print(f"[{self.model_name}] Found {len(detections)} objects: "
                  f"{labels_summary} in {duration_ms}ms")
        else:
            print(f"[{self.model_name}] No objects detected in {duration_ms}ms")

        return {
            "model_name": self.model_name,
            "results": detections,
            "duration_ms": duration_ms,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

    @staticmethod
    def _to_pil(data: Any) -> Image.Image:
        """
            把输入统一转换成 PIL Image。
        """
        if isinstance(data, Image.Image):
            return data.convert("RGB")
        if isinstance(data, bytes):
            return Image.open(io.BytesIO(data)).convert("RGB")
        if isinstance(data, np.ndarray):
            return Image.fromarray(data).convert("RGB")
        if isinstance(data, str):
            return Image.open(data).convert("RGB")
        raise TypeError(f"Unsupported input type: {type(data)}")
