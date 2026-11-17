"""
    === 统一预测的请求 / 响应模型 ===

    请求：model_key + 输入（文本、图片 base64/URL、或特征字典）
    响应：统一结构的预测结果
"""

import base64
import io
from typing import Dict, List, Optional

from PIL import Image
from pydantic import BaseModel, Field, field_validator


class PredictRequest(BaseModel):
    """
        统一预测请求。

        输入可通过以下任一方式提供：
        - text：字符串（用于 BERT 文本分类）
        - image_base64 / image_url：图片（用于图像类模型）
        - features：特征字典（用于表格类模型）
    """
    model_key: str = Field(
        ...,
        description="Model identifier: 'resnet50', 'bert', 'yolov8', or 'xgboost'",
        examples=["resnet50"],
    )
    text: Optional[str] = Field(
        None,
        description="Text input for BERT sentiment analysis",
    )
    image_base64: Optional[str] = Field(
        None,
        description="Base64-encoded image (JPEG/PNG) for ResNet-50 or YOLOv8",
    )
    image_url: Optional[str] = Field(
        None,
        description="URL to an image for ResNet-50 or YOLOv8",
    )
    features: Optional[Dict[str, float]] = Field(
        None,
        description="Feature dict for XGBoost tabular prediction",
    )
    top_k: int = Field(
        5,
        ge=1,
        le=20,
        description="Number of top predictions to return",
    )
    conf_threshold: float = Field(
        0.25,
        ge=0.0,
        le=1.0,
        description="Minimum confidence threshold (for YOLOv8)",
    )

    @field_validator("model_key")
    @classmethod
    def validate_model_key(cls, v: str) -> str:
        """校验 model_key 是否在白名单内，并统一转成小写。"""
        allowed = {"resnet50", "bert", "yolov8", "xgboost", "u2net"}
        v_lower = v.lower()
        if v_lower not in allowed:
            raise ValueError(f"model_key must be one of {allowed}, got '{v}'")
        return v_lower

    def get_image_bytes(self) -> Optional[bytes]:
        """
            从 base64 或 URL 输入中取出图片字节。
        """
        if self.image_base64:
            # 存在 data URI 前缀时先去掉
            b64 = self.image_base64
            if "," in b64 and b64.startswith("data:"):
                b64 = b64.split(",", 1)[1]
            try:
                return base64.b64decode(b64)
            except Exception:
                raise ValueError("Invalid base64 image data")
        if self.image_url:
            import urllib.request
            try:
                with urllib.request.urlopen(self.image_url, timeout=15) as resp:
                    return resp.read()
            except Exception as e:
                raise ValueError(f"Failed to fetch image from URL: {e}")
        return None

    def get_pil_image(self) -> Optional[Image.Image]:
        """
            把图片输入转换成 PIL Image。
        """
        img_bytes = self.get_image_bytes()
        if img_bytes:
            return Image.open(io.BytesIO(img_bytes)).convert("RGB")
        return None


class PredictResult(BaseModel):
    """
        单条预测结果。
    """
    label: str = Field(..., description="Class label or object name")
    probability: float = Field(..., description="Confidence score (0-1)")
    bbox: Optional[List[float]] = Field(
        None,
        description="Bounding box [x1, y1, x2, y2] (YOLOv8 only)",
    )


class PredictResponse(BaseModel):
    """
        统一的预测响应。
    """
    model_name: str = Field(..., description="Name of the model used")
    results: List[PredictResult] = Field(..., description="Prediction results (sorted by probability desc)")
    duration_ms: int = Field(..., description="Inference time in milliseconds")
    timestamp: str = Field(..., description="ISO 8601 timestamp")
