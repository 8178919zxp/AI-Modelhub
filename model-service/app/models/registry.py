"""
    === 模型注册表 ===

    统一管理所有模型实例的注册表。模型在首次使用时才加载（懒加载），
    加载失败的模型会被记录下来，不影响其他模型继续服务。
"""

import time
from typing import Dict, Optional

from app.models.base import BaseModel
from app.models.resnet50 import ResNet50Model
from app.models.bert import BertSentimentModel
from app.models.yolov8 import YOLOv8Model
from app.models.xgboost import XGBoostTabularModel
from app.models.u2net import U2NetModel

# 映射关系：model_key → (模型类, 可读名称)
_MODEL_SPECS = {
    "resnet50": (ResNet50Model, "ResNet-50"),
    "bert":     (BertSentimentModel, "BERT-Base"),
    "yolov8":   (YOLOv8Model, "YOLOv8"),
    "xgboost":  (XGBoostTabularModel, "XGBoost"),
    "u2net":    (U2NetModel, "U2Net"),
}


class ModelRegistry:
    """
        管理模型的生命周期：加载、获取、健康检查。
    """

    def __init__(self, device: str = "cpu"):
        """初始化注册表，并登记全部可用模型。"""
        self.device = device
        self._models: Dict[str, BaseModel] = {}
        self._load_errors: Dict[str, str] = {}

    @property
    def available_models(self) -> list:
        """
            已注册的模型 key 列表。
        """
        return list(_MODEL_SPECS.keys())

    @property
    def loaded_models(self) -> list:
        """
            加载成功的模型 key 列表。
        """
        return [k for k, m in self._models.items() if m.is_loaded]

    @property
    def failed_models(self) -> Dict[str, str]:
        """
            加载失败的模型及其错误信息。
        """
        return dict(self._load_errors)

    def load_all(self) -> None:
        """
            加载全部已注册模型。非阻塞——失败只记录日志。
        """
        print(f"[Registry] Loading all models on device={self.device} ...")
        t0 = time.time()

        for key, (cls, _) in _MODEL_SPECS.items():
            try:
                self._load_one(key, cls)
            except Exception as e:
                self._load_errors[key] = str(e)
                print(f"[Registry] FAILED to load '{key}': {e}")

        elapsed = time.time() - t0
        loaded = self.loaded_models
        failed = self.failed_models
        print(f"[Registry] Done in {elapsed:.1f}s. "
              f"Loaded: {len(loaded)} ({', '.join(loaded) if loaded else 'none'}). "
              f"Failed: {len(failed)} ({', '.join(failed.keys()) if failed else 'none'})")

    def load(self, model_key: str) -> BaseModel:
        """
            按 key 加载指定模型；失败时抛出异常。
        """
        if model_key not in _MODEL_SPECS:
            raise ValueError(f"Unknown model_key '{model_key}'. "
                             f"Available: {self.available_models}")
        if model_key in self._models and self._models[model_key].is_loaded:
            return self._models[model_key]

        cls, _ = _MODEL_SPECS[model_key]
        return self._load_one(model_key, cls)

    def _load_one(self, key: str, cls) -> BaseModel:
        """
            实例化并加载单个模型。
        """
        model = cls(device=self.device)
        model.load()
        self._models[key] = model
        return model

    def get(self, model_key: str) -> Optional[BaseModel]:
        """
            按 key 获取已加载的模型。
            未加载时返回 None（调用方需自行判断）。
        """
        model = self._models.get(model_key)
        if model and model.is_loaded:
            return model
        return None

    def health(self) -> dict:
        """
            返回所有模型的健康状态。
        """
        models_status = {}
        for key in _MODEL_SPECS:
            m = self._models.get(key)
            if m and m.is_loaded:
                models_status[key] = "loaded"
            elif key in self._load_errors:
                reason = self._load_errors[key]
                # 只留前 80 字，但别让读者以为错误信息就到此为止
                if len(reason) > 80:
                    reason = reason[:80] + "…"
                models_status[key] = f"error: {reason}"
            else:
                models_status[key] = "not_loaded"

        return {
            "total_registered": len(_MODEL_SPECS),
            "loaded": len(self.loaded_models),
            "failed": len(self.failed_models),
            "models": models_status,
        }


# 单例实例
_registry: Optional[ModelRegistry] = None


def get_registry() -> ModelRegistry:
    """
        获取全局注册表实例，不存在则创建。
    """
    global _registry
    if _registry is None:
        _registry = ModelRegistry(device="cpu")
    return _registry


def init_registry(device: str = "cpu", eager: bool = True) -> ModelRegistry:
    """
        初始化全局注册表，可选择立即加载全部模型。
    """
    global _registry
    _registry = ModelRegistry(device=device)
    if eager:
        _registry.load_all()
    return _registry
