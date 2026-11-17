from fastapi import APIRouter

router = APIRouter()


@router.get("/health")
def health():
    """
        健康检查。

        status 恒为 UP —— 它表示「HTTP 服务活着」，不表示「模型都能推理」：模型在后台
        线程逐个加载，单个失败只记日志、不阻断启动。模型维度的状态放在 models 字段里，
        供排障与运维探针使用（loaded / not_loaded / error: 原因）。

        保持 status 字段不变，是为了兼容 compose 与 Dockerfile 里的健康检查命令。
    """
    payload = {"status": "UP"}
    try:
        from app.models.registry import get_registry
        payload["models"] = get_registry().health()
    except Exception as e:
        # 注册表尚未初始化（进程刚起来的一瞬间）时不让健康检查本身失败
        payload["models"] = {"available": False, "reason": type(e).__name__}
    return payload
