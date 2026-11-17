"""
    === 模型基类 ===

    所有模型实现都必须继承 BaseModel，并实现：
    - model_name（属性）-> str
    - predict(input_data, **kwargs) -> dict

    predict() 必须返回如下结构的字典：
    {
    "model_name": str,
    "results": [{"label": str, "probability": float}, ...],
    "duration_ms": int,
    "timestamp": str（ISO 8601）
    }
"""

from abc import ABC, abstractmethod
from typing import Any, Dict


class BaseModel(ABC):
    """
        模型服务中所有 AI 模型的抽象基类。
    """

    def __init__(self, device: str = "cpu"):
        """
            参数：
            device: "cpu" 或 "cuda"。默认 "cpu"，兼容性最好。
        """
        self.device = device
        self._loaded = False

    @property
    @abstractmethod
    def model_name(self) -> str:
        """
            便于阅读的模型名称，例如 'ResNet-50'。
        """
        ...

    @property
    def is_loaded(self) -> bool:
        """
            模型是否已成功加载。
        """
        return self._loaded

    @abstractmethod
    def predict(self, input_data: Any, **kwargs) -> Dict:
        """
            执行推理并返回结构化结果。

            参数：
            input_data: 具体模型所需的输入（PIL Image、str、np.ndarray 等）
            **kwargs: 附加参数（例如 top_k）

            返回：
            {
            "model_name": str,
            "results": [{"label": str, "probability": float}, ...],
            "duration_ms": int,
            "timestamp": str（ISO 8601）
            }
        """
        ...

    @abstractmethod
    def load(self) -> None:
        """
            加载模型权重。启动时调用一次。
        """
        ...

    def _validate_loaded(self) -> None:
        """
            模型未加载时抛出 RuntimeError。
        """
        if not self._loaded:
            raise RuntimeError(f"{self.model_name} is not loaded. Call load() first.")
