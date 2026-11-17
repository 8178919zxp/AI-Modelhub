"""
    === 统一预测接口 ===

    POST /predict
    - 接收 JSON：model_key + 输入（text / image_base64 / image_url / features）
    - 通过 ModelRegistry 分派到对应模型
    - 返回统一的 PredictResponse

    同时作为旧版接口（/text/classify、/tabular/predict、/image/classify）的底层实现。
"""

from fastapi import APIRouter, UploadFile, File, Form
from fastapi.concurrency import run_in_threadpool
import threading
import asyncio

from app.models.registry import get_registry
from app.schemas.predict import (
    PredictRequest,
    PredictResponse,
    PredictResult,
)

router = APIRouter(tags=["predict"])

# 模块级模型缓存（避免每次请求都从磁盘重新加载）
_MODEL_CACHE = {}

# ── P2 安全防护 ────────────────────────────────────────────────
_CACHE_LOCK = threading.Lock()        # for sync endpoints (custom_predict)
_ASYNC_CACHE_LOCK = None              # lazily initialized in predict_image (asyncio.Lock)
_TRAIN_LOCK = threading.Lock()        # prevents concurrent auto-training
_ASYNC_TRAIN_LOCK = None              # lazily initialized in predict_image (asyncio.Lock)

# 上传大小上限，避免超大图片导致内存溢出
_MAX_UPLOAD_MB = 5
_MAX_UPLOAD_BYTES = _MAX_UPLOAD_MB * 1024 * 1024

# 允许的 model_key 白名单
_VALID_IMAGE_KEYS = {"u2net", "chest_ct", "flower", "resnet50", "yolov8"}
_VALID_CUSTOM_KEYS = {"weather", "stock", "chinese_sentiment"}


@router.post("/predict", response_model=PredictResponse)
def unified_predict(req: PredictRequest):
    """
        统一预测接口。

        按 model_key 分派：
        - "resnet50" → 图像分类（需要 image_base64 或 image_url）
        - "bert"     → 文本情感（需要 text）
        - "yolov8"   → 目标检测（需要 image_base64 或 image_url）
        - "xgboost"  → 表格预测（需要 features）
    """
    registry = get_registry()
    model = registry.get(req.model_key)

    if model is None:
        # 尝试懒加载
        try:
            model = registry.load(req.model_key)
        except Exception as e:
            return PredictResponse(
                model_name=req.model_key,
                results=[],
                duration_ms=0,
                timestamp="",
            )

    # 按模型类型准备输入数据
    if req.model_key in ("resnet50", "yolov8"):
        pil_image = req.get_pil_image()
        if pil_image is None:
            raise ValueError(
                f"Model '{req.model_key}' requires image_base64 or image_url"
            )
        kwargs = {"top_k": req.top_k}
        if req.model_key == "yolov8":
            kwargs["conf_threshold"] = req.conf_threshold
        raw = model.predict(pil_image, **kwargs)

    elif req.model_key == "bert":
        # 空文本不做分类：返回 400 比编一个「中性」结论更诚实
        if req.text is None or not str(req.text).strip():
            from fastapi.responses import JSONResponse
            return JSONResponse(status_code=400, content={"error": "Model 'bert' requires non-empty 'text'"})
        raw = model.predict(req.text)

    elif req.model_key == "xgboost":
        if not req.features:
            raise ValueError("Model 'xgboost' requires 'features' dict")
        raw = model.predict(req.features, top_k=req.top_k)
    else:
        raise ValueError(f"Unknown model_key: {req.model_key}")

    # 转换为 PredictResponse
    results = [
        PredictResult(
            label=r["label"],
            probability=r["probability"],
            bbox=r.get("bbox"),
        )
        for r in raw.get("results", [])
    ]

    return PredictResponse(
        model_name=raw["model_name"],
        results=results,
        duration_ms=raw["duration_ms"],
        timestamp=raw["timestamp"],
    )


# ---- 旧版接口适配（向后兼容） ----
# 这些接口接收 SpringBoot 后端的旧请求格式：
#   /text/classify:    {"text": "...", "model_id": 3}
#   /tabular/predict:  {"model_id": 3, "features": {...}}
#   /image/classify:   multipart 文件上传

from app.schemas.text import TextPredictRequest
from app.schemas.tabular import TabularPredictRequest


@router.post("/text/classify")
def legacy_text_classify(payload: TextPredictRequest):
    """
        旧版接口：转调统一预测入口，固定使用 bert。

        语种分流在 BertSentimentModel 内部完成：中文走 ChineseSentiment，
        英文走 distilbert SST-2。响应里的 model_name 会反映实际使用的模型。
    """
    if payload.text is None or not str(payload.text).strip():
        from fastapi.responses import JSONResponse
        return JSONResponse(status_code=400, content={"error": "text 不能为空"})

    req = PredictRequest(
        model_key="bert",
        text=payload.text,
    )
    return _legacy_format(unified_predict(req))


@router.post("/tabular/predict")
def legacy_tabular_predict(payload: TabularPredictRequest):
    """
        旧版接口：转调统一预测入口，固定使用 XGBoost 模型。
    """
    req = PredictRequest(
        model_key="xgboost",
        features=payload.features if isinstance(payload.features, dict) else {},
    )
    return _legacy_format(unified_predict(req))


@router.post("/image/classify")
def legacy_image_classify(file: UploadFile = File(...)):
    """
        旧版接口：接收 multipart 文件上传，转调统一预测入口并使用 resnet50。
    """
    import base64
    img_bytes = file.file.read()
    img_b64 = base64.b64encode(img_bytes).decode("utf-8")

    req = PredictRequest(
        model_key="resnet50",
        image_base64=img_b64,
        top_k=5,
    )
    resp = unified_predict(req)
    return {
        "type": "image",
        "model_name": resp.model_name,
        "top_label": resp.results[0].label if resp.results else "unknown",
        "confidence": resp.results[0].probability if resp.results else 0,
        "duration": resp.duration_ms,
        "candidates": [
            {"label": r.label, "probability": r.probability}
            for r in resp.results
        ],
        "filename": file.filename or "upload",
    }


@router.post("/predict/image")
async def predict_image(
    file: UploadFile = File(...),
    model_key: str = Form(...),
):
    """
        multipart 图像预测接口。

        支持：
        - u2net    → 背景移除（抠图）
        - resnet50 → 图像分类
        - yolov8   → 目标检测
    """

    import base64
    from fastapi.responses import JSONResponse

    # 延迟初始化异步锁（兼容 Python 3.8——asyncio.Lock 需要已运行的事件循环）
    global _ASYNC_CACHE_LOCK, _ASYNC_TRAIN_LOCK
    if _ASYNC_CACHE_LOCK is None:
        _ASYNC_CACHE_LOCK = asyncio.Lock()
    if _ASYNC_TRAIN_LOCK is None:
        _ASYNC_TRAIN_LOCK = asyncio.Lock()

    # ✅ 统一大小写，避免大小写导致分支失效
    model_key = model_key.lower()

    # P2-6：任何处理之前先用白名单校验 model_key
    if model_key not in _VALID_IMAGE_KEYS:
        return JSONResponse(
            status_code=400,
            content={"error": f"Unknown model_key: '{model_key}'. Valid keys: {', '.join(sorted(_VALID_IMAGE_KEYS))}"},
        )

    img_bytes = await file.read()

    # P2-4：拒绝超限上传，避免内存溢出
    if len(img_bytes) > _MAX_UPLOAD_BYTES:
        return JSONResponse(
            status_code=400,
            content={"error": f"Image too large ({len(img_bytes) / 1024 / 1024:.1f} MB). Max allowed: {_MAX_UPLOAD_MB} MB"},
        )

    # ── U²-Net 背景移除 ─────────────────────────────────────────
    if model_key == "u2net":
        registry = get_registry()
        model = registry.get("u2net")

        if model is None:
            try:
                model = registry.load("u2net")
            except Exception as e:
                return JSONResponse(
                    status_code=503,
                    content={
                        "type": "image",
                        "error": f"抠图模型暂不可用：{e}",
                    },
                )

        try:
            # ✅ 直接传 bytes，让模型内部处理
            raw = model.predict(img_bytes)

            return_payload = {
                "resultType": "image_processing",
                "imageBase64": raw.get("result", ""),
                "modelName": "U2Net",
                "duration": raw.get("cost_ms", 0),
                "candidates": [],
            }
            print("FastAPI raw result:", raw)
            print("FastAPI return payload:", return_payload)
            return return_payload

        except Exception as e:
            import traceback
            print("====== U2Net Exception ======")
            traceback.print_exc()
            print("====== End Exception ======")
            return JSONResponse(
                status_code=500,
                content={
                    "type": "image",
                    "error": f"U2Net inference failed: {repr(e)}",
                },
            )

    # ── ChestCT 胸部 CT 肺癌分类 ───────────────────────────
    if model_key == "chest_ct":
        import os as _os2
        from app.models.chest_ct_model import train_model as ct_train, load_model as ct_load, predict as ct_predict

        # 归一化路径，消除 .. 避免跨平台不一致
        weight_path = _os2.path.normpath(_os2.path.join(
            _os2.path.dirname(_os2.path.abspath(__file__)), "..", "..",
            "weights", "chest_ct_resnet50.h5",
        ))
        print(f"[ChestCT] 检查权重路径: {weight_path}")

        # P2-2：自动训练放在锁内，避免并发训练相互干扰
        if not _os2.path.exists(weight_path):
            async with _ASYNC_TRAIN_LOCK:
                if not _os2.path.exists(weight_path):
                    print("[ChestCT] 权重不存在，开始自动训练...")
                    await run_in_threadpool(ct_train)

        # P2-1：双重检查锁 + 缓存，避免懒加载时的竞态
        if "chest_ct" not in _MODEL_CACHE:
            async with _ASYNC_CACHE_LOCK:
                if "chest_ct" not in _MODEL_CACHE:
                    print("[ChestCT] 加载模型到缓存...")
                    _MODEL_CACHE["chest_ct"] = await run_in_threadpool(ct_load)
        model = _MODEL_CACHE["chest_ct"]

        raw = await run_in_threadpool(ct_predict, model, img_bytes)
        return {
            "type": "image",
            "model_name": raw["model_name"],
            "top_label": raw["top_label"],
            "confidence": raw["confidence"],
            "duration": raw["duration"],
            "candidates": raw.get("candidates", []),
            "filename": file.filename or "upload",
        }

    # ── Flower 花卉分类 ────────────────────────────────────
    if model_key == "flower":
        import os as _os3
        from app.models.flower_model import train_model as fl_train, load_model as fl_load, predict as fl_predict

        weight_path = _os3.path.normpath(_os3.path.join(
            _os3.path.dirname(_os3.path.abspath(__file__)), "..", "..",
            "weights", "flower_cnn_model.h5",
        ))
        print(f"[Flower] 检查权重路径: {weight_path}")

        # P2-2：自动训练放在锁内，避免并发训练相互干扰
        if not _os3.path.exists(weight_path):
            async with _ASYNC_TRAIN_LOCK:
                if not _os3.path.exists(weight_path):
                    print("[Flower] 权重不存在，开始自动训练...")
                    await run_in_threadpool(fl_train)

        # P2-1：双重检查锁 + 缓存，避免懒加载时的竞态
        if "flower" not in _MODEL_CACHE:
            async with _ASYNC_CACHE_LOCK:
                if "flower" not in _MODEL_CACHE:
                    print("[Flower] 加载模型到缓存...")
                    _MODEL_CACHE["flower"] = await run_in_threadpool(fl_load)
        model = _MODEL_CACHE["flower"]

        raw = await run_in_threadpool(fl_predict, model, img_bytes)
        return {
            "type": "image",
            "model_name": raw["model_name"],
            "top_label": raw["top_label"],
            "confidence": raw["confidence"],
            "duration": raw["duration"],
            "candidates": raw.get("candidates", []),
            "filename": file.filename or "upload",
        }

    # ── 分类 / 检测 ────────────────────────────────────────
    img_b64 = base64.b64encode(img_bytes).decode("utf-8")

    req = PredictRequest(
        model_key=model_key,
        image_base64=img_b64,
    )

    resp = unified_predict(req)

    return {
        "type": "image",
        "model_name": resp.model_name,
        "top_label": resp.results[0].label if resp.results else "unknown",
        "confidence": resp.results[0].probability if resp.results else 0,
        "duration": resp.duration_ms,
        "candidates": [
            {"label": r.label, "probability": r.probability}
            for r in resp.results
        ],
        "filename": file.filename or "upload",
    }


# ═══════════════════════════════════════════════════════════════
# 自定义模型接口 —— 处理表格类与文本类模型
# 接收 JSON：{ model_key, text?, features? }
# ═══════════════════════════════════════════════════════════════

from pydantic import BaseModel as _PydanticBaseModel2
from typing import Optional as _Optional2, Dict as _Dict2


class CustomPredictRequest(_PydanticBaseModel2):
    model_key: str
    text: _Optional2[str] = None
    features: _Optional2[_Dict2[str, float]] = None


@router.post("/predict/custom")
def custom_predict(req: CustomPredictRequest):
    """
        处理自定义模型（天气、股票、中文情感）。
    """
    import os as _os4
    model_key = req.model_key.lower()

    # P2-6：任何处理之前先用白名单校验 model_key
    if model_key not in _VALID_CUSTOM_KEYS:
        from fastapi.responses import JSONResponse
        return JSONResponse(
            status_code=400,
            content={"error": f"Unknown model_key: '{model_key}'. Valid keys: {', '.join(sorted(_VALID_CUSTOM_KEYS))}"},
        )

    # ── 天气 ───────────────────────────────────────────────
    if model_key == "weather":
        from app.models.weather_model import train_model, load_model, predict

        weight_path = _os4.path.normpath(_os4.path.join(
            _os4.path.dirname(_os4.path.abspath(__file__)), "..", "..",
            "weights", "weather_gbm.pkl",
        ))
        print(f"[Weather] 检查权重路径: {weight_path}")
        print(f"[Weather] 文件存在: {_os4.path.exists(weight_path)}")

        # P2-2：自动训练放在锁内，避免并发训练相互干扰
        if not _os4.path.exists(weight_path):
            with _TRAIN_LOCK:
                if not _os4.path.exists(weight_path):
                    print("[Weather] 权重不存在，开始自动训练...")
                    train_model()

        if not req.features:
            from fastapi.responses import JSONResponse
            return JSONResponse(status_code=400, content={"error": "Weather model requires features dict"})

        # P2-1：双重检查锁 + 缓存，避免懒加载时的竞态
        if "weather" not in _MODEL_CACHE:
            with _CACHE_LOCK:
                if "weather" not in _MODEL_CACHE:
                    print("[Weather] 加载模型到缓存...")
                    _MODEL_CACHE["weather"] = load_model()
        model = _MODEL_CACHE["weather"]

        try:
            raw = predict(model, req.features)
        except ValueError as ve:
            from fastapi.responses import JSONResponse
            return JSONResponse(status_code=400, content={"error": str(ve)})
        return {
            "type": "tabular",
            "model_name": raw["model_name"],
            "top_label": raw["top_label"],
            "confidence": raw["confidence"],
            "duration": raw["duration"],
            "candidates": raw.get("candidates", []),
        }

    # ── 股票 ───────────────────────────────────────────────
    if model_key == "stock":
        from app.models.stock_model import train_model, load_model, predict

        weight_path = _os4.path.normpath(_os4.path.join(
            _os4.path.dirname(_os4.path.abspath(__file__)), "..", "..",
            "weights", "stock_regression.pkl",
        ))
        print(f"[Stock] 检查权重路径: {weight_path}")
        print(f"[Stock] 文件存在: {_os4.path.exists(weight_path)}")

        # P2-2：自动训练放在锁内，避免并发训练相互干扰
        if not _os4.path.exists(weight_path):
            with _TRAIN_LOCK:
                if not _os4.path.exists(weight_path):
                    print("[Stock] 权重不存在，开始自动训练...")
                    train_model()

        if not req.features:
            from fastapi.responses import JSONResponse
            return JSONResponse(status_code=400, content={"error": "Stock model requires features dict"})

        # P2-1：双重检查锁 + 缓存，避免懒加载时的竞态
        if "stock" not in _MODEL_CACHE:
            with _CACHE_LOCK:
                if "stock" not in _MODEL_CACHE:
                    print("[Stock] 加载模型到缓存...")
                    _MODEL_CACHE["stock"] = load_model()
        model = _MODEL_CACHE["stock"]

        try:
            raw = predict(model, req.features)
        except ValueError as ve:
            from fastapi.responses import JSONResponse
            return JSONResponse(status_code=400, content={"error": str(ve)})
        return {
            "type": "tabular",
            "model_name": raw["model_name"],
            "top_label": raw.get("top_label", ""),
            "confidence": raw.get("confidence", 1.0),
            "predicted_close": raw["predicted_close"],
            "duration": raw["duration"],
            "candidates": raw.get("candidates", []),
        }

    # ── 中文情感 ───────────────────────────────────────────
    if model_key == "chinese_sentiment":
        from app.models.chinese_sentiment_model import train_model, load_model, predict

        weight_path = _os4.path.normpath(_os4.path.join(
            _os4.path.dirname(_os4.path.abspath(__file__)), "..", "..",
            "weights", "chinese_sentiment_mlp.pkl",
        ))
        vect_path = _os4.path.normpath(_os4.path.join(
            _os4.path.dirname(_os4.path.abspath(__file__)), "..", "..",
            "weights", "chinese_vectorizer.pkl",
        ))
        print(f"[ChineseSentiment] 检查权重路径: {weight_path}")
        print(f"[ChineseSentiment] MLP 文件存在: {_os4.path.exists(weight_path)}")
        print(f"[ChineseSentiment] 检查向量器路径: {vect_path}")
        print(f"[ChineseSentiment] Vectorizer 文件存在: {_os4.path.exists(vect_path)}")

        # P2-2：自动训练放在锁内，避免并发训练相互干扰
        if not _os4.path.exists(weight_path) or not _os4.path.exists(vect_path):
            with _TRAIN_LOCK:
                if not _os4.path.exists(weight_path) or not _os4.path.exists(vect_path):
                    print("[ChineseSentiment] 权重不存在，开始自动训练...")
                    train_model()

        if not req.text:
            from fastapi.responses import JSONResponse
            return JSONResponse(status_code=400, content={"error": "Chinese Sentiment model requires text"})

        # P2-1：双重检查锁 + 缓存，避免懒加载时的竞态
        if "chinese_sentiment" not in _MODEL_CACHE:
            with _CACHE_LOCK:
                if "chinese_sentiment" not in _MODEL_CACHE:
                    print("[ChineseSentiment] 加载模型到缓存...")
                    _MODEL_CACHE["chinese_sentiment"] = load_model()
        model = _MODEL_CACHE["chinese_sentiment"]

        raw = predict(model, req.text)
        return {
            "type": "text",
            "model_name": raw["model_name"],
            "top_label": raw["top_label"],
            "confidence": raw["confidence"],
            "duration": raw["duration"],
            "candidates": raw.get("candidates", []),
        }

    from fastapi.responses import JSONResponse
    return JSONResponse(status_code=400, content={"error": f"Unknown model_key: {model_key}"})


def _legacy_format(resp: PredictResponse) -> dict:
    """
        把 PredictResponse 转成旧版响应格式，保持向后兼容。
    """
    top = resp.results[0] if resp.results else PredictResult(label="unknown", probability=0.0)
    return {
        "type": _model_type(resp.model_name),
        "model_name": resp.model_name,
        "top_label": top.label,
        "confidence": top.probability,
        "duration": resp.duration_ms,
        "candidates": [
            {"label": r.label, "probability": r.probability}
            for r in resp.results
        ],
    }


def _model_type(model_name: str) -> str:
    """由模型名推断模型类型，用于兼容旧版响应格式。"""
    mapping = {
        "ResNet-50": "image",
        "BERT-Base": "text",
        "ChineseSentiment": "text",   # 中文文本由 BertSentimentModel 分流到这里
        "中文情感（关键词规则）": "text",  # 中文模型缺权重时的规则兜底
        "YOLOv8": "image",
        "XGBoost": "tabular",
    }
    return mapping.get(model_name, "unknown")

