"""
    === U²-Net 背景移除模型（rembg 后端）===

    只使用项目 weights/ 目录下的本地 u2net.onnx，默认完全离线。

    rembg 的行为（2.0.69 实测）：
    `BaseSession.__init__` 会**无条件**调用 `download_models()`，后者用 `pooch.retrieve`
    从 GitHub Releases 拉 u2net.onnx（176 MB）到 `U2NET_HOME`（默认 `~/.u2net`）。
    也就是说——只要构造 Session，缺权重就会静默下载，没有任何开关。

    所以这里在构造 Session 之前先自己判断一次：

    - 本地有 `weights/u2net.onnx` → 把 `download_models` 打成返回本地路径的补丁，
      彻底掐断 pooch，运行时不可能联网；
    - 本地没有 → 默认**直接抛错**，让注册表记入 failed_models，`/health` 显示
      `error: ...`，抠图接口返回 503。不做任何下载、不留任何缓存文件；
    - 本地没有但显式设了 `U2NET_ALLOW_DOWNLOAD=true` → 只告警，交给 rembg 下载
      （下载到 `U2NET_HOME`，默认 `~/.u2net/u2net.onnx`）。

    ONNXRuntime 的 SessionOptions 做了针对性调整，避免 CPU 上出现 BFCArena 内存分配失败：
    - enable_cpu_mem_arena = False
    - enable_mem_pattern = False
    - graph_optimization_level = ORT_ENABLE_BASIC
"""

import base64
import io
import os
import time
from pathlib import Path
from typing import Any, Dict

import onnxruntime as ort
from PIL import Image
from rembg import remove
import rembg.sessions.u2net as _rembg_u2net
from rembg.sessions.u2net import U2netSession

from app.models.base import BaseModel

# 项目根目录：容器内 /app，本地为 model-service/
_WEIGHTS_PATH = Path(__file__).resolve().parents[2] / "weights" / "u2net.onnx"

# 是否允许首次推理时静默下载 176 MB 权重。默认关闭，必须显式开启。
# 注意：这是本项目的开关，rembg 自身没有类似配置；rembg 只认缓存目录 U2NET_HOME。
_ALLOW_DOWNLOAD = os.getenv("U2NET_ALLOW_DOWNLOAD", "false").strip().lower() in {
    "1", "true", "yes", "on",
}


def _candidate_paths() -> list:
    """
        按优先级列出可能放着 u2net.onnx 的位置。

        除了项目自带的 weights/，也认 rembg 自己的缓存目录——之前手动跑过 rembg、
        或用 U2NET_HOME 指定过目录的话，不该因为「不在 weights/ 下」就判为缺失。
    """
    paths = [_WEIGHTS_PATH]
    home = os.getenv("U2NET_HOME")
    if home:
        paths.append(Path(os.path.expanduser(home)) / "u2net.onnx")
    paths.append(Path(os.path.expanduser("~")) / ".u2net" / "u2net.onnx")

    seen, unique = set(), []
    for p in paths:
        if str(p) not in seen:
            seen.add(str(p))
            unique.append(p)
    return unique


_RESOLVED_WEIGHT = next((p for p in _candidate_paths() if p.is_file()), None)


def _install_local_weight_patch() -> None:
    """把 rembg 的 download_models 指向已找到的本地文件，绕过 pooch 下载。"""
    weight_path = str(_RESOLVED_WEIGHT)

    @classmethod
    def _local_download(cls, *args, **kwargs):
        """绕过 pooch 的权重下载流程，直接返回本地 ONNX 文件路径。"""
        return weight_path

    _rembg_u2net.U2netSession.download_models = _local_download


if _RESOLVED_WEIGHT is not None:
    _install_local_weight_patch()
    print(f"[U2Net] Using local ONNX: {_RESOLVED_WEIGHT}")
else:
    print(f"[U2Net] WARNING: {_WEIGHTS_PATH} not found — "
          f"u2net will be reported as unavailable (download disabled by default)")


def _missing_weight_message() -> str:
    """权重缺失时给运维看的话：先说结论和路径，再说怎么补、怎么显式开启下载。"""
    searched = "、".join(str(p) for p in _candidate_paths())
    return (
        f"U²-Net 权重缺失：{_WEIGHTS_PATH} 不存在。"
        f"请放置 u2net.onnx，或设 U2NET_ALLOW_DOWNLOAD=true 允许自动下载（176 MB）。"
        f"已查找：{searched}"
    )


class U2NetModel(BaseModel):

    def __init__(self, device: str = "cpu"):
        """初始化模型：先校验本地权重，再创建 rembg 会话（默认不触发下载）。"""
        super().__init__(device=device)

        # 关键：必须在构造 U2netSession 之前拦住，否则 rembg 会直接开始下载
        if _RESOLVED_WEIGHT is None:
            if not _ALLOW_DOWNLOAD:
                raise RuntimeError(_missing_weight_message())
            print(f"[U2Net] WARNING: {_WEIGHTS_PATH} 不存在，"
                  f"U2NET_ALLOW_DOWNLOAD=true 已显式开启自动下载（约 176 MB）")

        # 自定义 SessionOptions —— 修复 CPU 上的 BFCArena 内存分配问题
        so = ort.SessionOptions()
        so.enable_cpu_mem_arena = False
        so.enable_mem_pattern = False
        so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC

        self._session = U2netSession(
            "u2net",
            so,
            providers=["CPUExecutionProvider"],
        )
        self._loaded = True
        print("[U2Net] Session created — local ONNX (BFCArena fix applied)")

    @property
    def model_name(self) -> str:
        """返回模型名称 U2Net。"""
        return "U2Net"

    def load(self) -> None:
        """会话已在 __init__ 中建好；这里只确认权重与会话就位。"""
        if _RESOLVED_WEIGHT is None and not _ALLOW_DOWNLOAD:
            raise RuntimeError(_missing_weight_message())
        self._loaded = True

    def predict(self, input_data: Any, **kwargs) -> Dict:
        """对输入图片做前景分割，返回透明背景的 PNG（base64）。"""
        self._validate_loaded()

        t0 = time.time()

        image = self._to_pil(input_data)

        # 限制最大尺寸，防止内存溢出
        max_size = 512
        if max(image.size) > max_size:
            ratio = max_size / max(image.size)
            new_size = (int(image.width * ratio), int(image.height * ratio))
            image = image.resize(new_size, Image.LANCZOS)

        image = image.convert("RGB")

        output = remove(image, session=self._session)

        buffer = io.BytesIO()
        output.save(buffer, format="PNG")

        base64_str = base64.b64encode(buffer.getvalue()).decode("utf-8")

        elapsed = int((time.time() - t0) * 1000)

        return {
            "type": "image",
            "result": base64_str,
            "cost_ms": elapsed
        }

    @staticmethod
    def _to_pil(data: Any) -> Image.Image:
        """把字节或路径统一转换成 PIL 图片。"""
        if isinstance(data, Image.Image):
            return data
        if isinstance(data, bytes):
            return Image.open(io.BytesIO(data))
        raise TypeError("Unsupported input type")
