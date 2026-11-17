from fastapi import APIRouter
from app.api.health import router as health_router
from app.api.predict import router as predict_router

api_router = APIRouter()
api_router.include_router(health_router)
# 一个预测路由统一承载以下接口：
#   POST /predict          （统一入口）
#   POST /text/classify    （旧版，向后兼容）
#   POST /tabular/predict  （旧版，向后兼容）
#   POST /image/classify   （旧版，向后兼容）
api_router.include_router(predict_router)
