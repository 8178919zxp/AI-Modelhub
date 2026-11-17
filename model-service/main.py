"""
    === AI ModelHub 模型服务 ===

    基于 FastAPI 的模型推理服务，对外提供真实的机器学习推理能力。

    启动策略：
    - 模型在后台线程中加载，服务本身可立即启动
    - 无论模型是否加载完成，健康检查都能立刻返回
    - 单个模型加载失败只记录日志，不影响服务启动

    接口：
    - GET  /health              — 健康检查（始终返回 UP）
    - POST /predict             — 统一预测入口（按 model_key 分派）
    - POST /text/classify       — 旧版文本接口（转 BERT）
    - POST /tabular/predict     — 旧版表格接口（转 XGBoost）
    - POST /image/classify      — 旧版图像接口（multipart 上传，转 ResNet-50）

    启动方式：
    uvicorn main:app --host 0.0.0.0 --port 8001
"""

import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI

# U²-Net 的权重判定与 rembg 下载补丁都在 app/models/u2net.py 里（单一来源）：
# 本地有 weights/u2net.onnx 就掐断 pooch 下载；没有就默认抛错、由注册表记入
# failed_models，不会在首次推理时静默下载 176 MB。这里不再重复打补丁。

from app.api.router import api_router
from app.models.registry import init_registry


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
        在后台线程中加载模型，使服务可以立即对外提供健康检查。
    """
    # 启动事件
    print("=" * 50)
    print("AI ModelHub Model Service — Starting up")
    print("=" * 50)

    # 在后台守护线程中加载全部模型。
    # 这样服务可以立即接受请求（包括健康检查），
    # 不必等待模型加载完成。
    def _load_models():
        """后台线程入口：按注册表加载全部模型；单个模型失败只记录，不中断其余模型。"""
        print("[Startup] Loading models in background thread...")
        init_registry(device="cpu", eager=True)
        print("[Startup] Background model loading complete.")

    thread = threading.Thread(target=_load_models, daemon=True,
                              name="model-loader")
    thread.start()

    yield
    # 关闭事件：无需清理，模型对象交给垃圾回收即可
    print("Model service shutting down.")


app = FastAPI(
    title="AI ModelHub Model Service",
    version="2.0.0",
    description="Real ML inference service: ResNet-50, BERT, YOLOv8, XGBoost",
    lifespan=lifespan,
)

app.include_router(api_router)
