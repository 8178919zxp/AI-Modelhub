"""
    模型包 —— 真实的机器学习模型实现。

    每个模型都继承 BaseModel 并实现：
    - model_name（属性）
    - load() —— 加载权重（启动时调用）
    - predict(input_data, **kwargs) —— 执行推理
"""

from app.models.base import BaseModel
from app.models.resnet50 import ResNet50Model
from app.models.bert import BertSentimentModel
from app.models.yolov8 import YOLOv8Model
from app.models.xgboost import XGBoostTabularModel
from app.models.u2net import U2NetModel

__all__ = [
    "BaseModel",
    "ResNet50Model",
    "BertSentimentModel",
    "YOLOv8Model",
    "XGBoostTabularModel",
    "U2NetModel",
]