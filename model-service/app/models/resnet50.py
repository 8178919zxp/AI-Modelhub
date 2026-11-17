"""
    === ResNet-50 图像分类器 ===

    模型来源：torchvision.models.resnet50（ImageNet-1K 预训练）
    输入格式：PIL Image（RGB）或图片字节
    输出格式：Top-K 类别及概率（中文标签优先，缺失时回落英文）

    权重加载策略（由 MODEL_STRICT_OFFLINE 环境变量控制）：
    - true  → 只读本地权重，缺失时报错（Docker 生产环境）
    - false → 优先本地，缺失时从 torchvision 自动下载（开发环境）
"""

import io
import json
import os
import time
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

import numpy as np
import torch
import torchvision.transforms as transforms
from PIL import Image
from torchvision.models import resnet50, ResNet50_Weights

from app.models.base import BaseModel

# ── 离线模式开关 ───────────────────────────────────────────────
_STRICT_OFFLINE = os.getenv("MODEL_STRICT_OFFLINE", "false").lower() == "true"


class ResNet50Model(BaseModel):
    """
        ResNet-50 图像分类器。

        严格离线模式（MODEL_STRICT_OFFLINE=true）下只加载本地权重；
        否则优先本地、缺失时自动下载。
    """

    # 默认本地权重文件（构建阶段预下载）
    DEFAULT_WEIGHTS = "weights/resnet50-0676ba61.pth"

    def __init__(self, device: str = "cpu", weights_path: Optional[str] = None):
        """
            参数：
            device: "cpu" 或 "cuda"
            weights_path: 可选的本地 .pth 权重路径。不传则使用默认路径。
        """
        super().__init__(device=device)
        self.weights_path = weights_path or self.DEFAULT_WEIGHTS
        self._model = None
        self._categories_en: List[str] = []
        self._zh_map: Dict[str, str] = {}

        # 图像预处理流程
        self._transform = transforms.Compose([
            transforms.Resize(256),
            transforms.CenterCrop(224),
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[0.485, 0.456, 0.406],
                std=[0.229, 0.224, 0.225]
            ),
        ])

    @property
    def model_name(self) -> str:
        """返回模型名称 ResNet-50。"""
        return "ResNet-50"

    def load(self) -> None:
        """
            加载 ResNet-50：优先本地权重，允许时自动下载。
        """
        print(f"[{self.model_name}] Loading model on device={self.device} "
              f"(strict_offline={_STRICT_OFFLINE}) ...")
        t0 = time.time()

        if os.path.exists(self.weights_path):
            self._load_from_local()
        elif _STRICT_OFFLINE:
            raise FileNotFoundError(
                f"[{self.model_name}] Weights file not found: {self.weights_path}. "
                f"MODEL_STRICT_OFFLINE=true — refusing to download. "
                f"Run download_weights.py during Docker build to pre-download."
            )
        else:
            self._load_from_torchvision()

        self._model.to(self.device)
        self._model.eval()

        # 加载中文标签映射
        self._load_zh_map()

        self._loaded = True
        elapsed = time.time() - t0
        print(f"[{self.model_name}] Loaded successfully in {elapsed:.1f}s "
              f"({sum(p.numel() for p in self._model.parameters()) / 1e6:.1f}M params, "
              f"{len(self._categories_en)} ImageNet classes, "
              f"{len(self._zh_map)} Chinese labels)")

    def _load_from_local(self) -> None:
        """
            从本地 .pth 文件加载权重（完全离线）。
        """
        print(f"[{self.model_name}] Loading from local: {self.weights_path}")
        self._model = resnet50(weights=None)
        state_dict = torch.load(self.weights_path, map_location=self.device,
                                weights_only=True)
        self._model.load_state_dict(state_dict)
        self._categories_en = list(
            ResNet50_Weights.DEFAULT.meta.get("categories", [])
        )

    def _load_from_torchvision(self) -> None:
        """
            从 torchvision 自动下载权重（仅开发模式）。
        """
        print(f"[{self.model_name}] Local file not found — "
              f"auto-downloading from torchvision (~100 MB)...")
        try:
            weights = ResNet50_Weights.DEFAULT
            self._model = resnet50(weights=weights)
            self._categories_en = list(weights.meta["categories"])
        except Exception as e:
            # 兼容旧版 API 的兜底分支
            import torchvision.models as models
            self._model = models.resnet50(pretrained=True)
            self._categories_en = list(
                ResNet50_Weights.DEFAULT.meta.get("categories", [])
            )

    def _load_zh_map(self) -> None:
        """
            从 JSON 文件加载中文标签映射。
        """
        try:
            with open("app/resources/imagenet_zh.json", "r", encoding="utf-8") as f:
                self._zh_map = json.load(f)
            print(f"[{self.model_name}] Loaded {len(self._zh_map)} Chinese labels "
                  f"from imagenet_zh.json")
        except FileNotFoundError:
            print(f"[{self.model_name}] Warning: imagenet_zh.json not found, "
                  f"using English labels only")
            self._zh_map = {}
        except Exception as e:
            print(f"[{self.model_name}] Warning: Failed to load imagenet_zh.json: {e}")
            self._zh_map = {}

    def predict(self, input_data: Any, top_k: int = 5, **kwargs) -> Dict:
        """
            对 PIL 图片做分类。

            参数：
            input_data: PIL.Image（RGB）或图片字节（JPEG/PNG）
            top_k: 返回前 K 个结果
        """
        self._validate_loaded()

        t0 = time.time()

        # 需要时把字节转换成 PIL Image
        image = self._to_pil(input_data)

        # 预处理
        input_tensor = self._transform(image).unsqueeze(0).to(self.device)

        # 记录输入信息，便于排查
        c, h, w = input_tensor.shape[1], input_tensor.shape[2], input_tensor.shape[3]
        print(f"[{self.model_name}] Input: {h}x{w}x{c}, top_k={top_k}")

        # 推理
        with torch.no_grad():
            output = self._model(input_tensor)
            probabilities = torch.nn.functional.softmax(output[0], dim=0)

        # 取 Top-K
        topk_probs, topk_indices = torch.topk(probabilities, min(top_k, len(probabilities)))

        results = []
        for idx, prob in zip(topk_indices.tolist(), topk_probs.tolist()):
            # 从 torchvision 官方类别表取英文标签
            if idx < len(self._categories_en):
                label_en = self._categories_en[idx]
            else:
                label_en = f"class_{idx}"

            # 映射为中文（没有中文时回落英文）
            label = self._zh_map.get(label_en, label_en)

            results.append({"label": label, "probability": round(prob, 6)})

        duration_ms = int((time.time() - t0) * 1000)
        print(f"[{self.model_name}] Inference: {duration_ms}ms, "
              f"top-1: {results[0]['label']} ({results[0]['probability']:.4f})")

        return {
            "model_name": self.model_name,
            "results": results,
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
        raise TypeError(f"Unsupported input type: {type(data)}. "
                        f"Expected PIL.Image, bytes, or np.ndarray.")
