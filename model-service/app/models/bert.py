"""
    === 文本情感分类器（按语种分流）===

    这个模型名沿用历史的 "BERT-Base"，但它**不是**一个模型，而是两条链路：

    ┌ 英文 → distilbert-base-uncased-finetuned-sst-2-english（SST-2，二分类）
    └ 中文 → ChineseSentiment（MLP + jieba 词袋，ChnSentiCorp 酒店评论训练，二分类）

    为什么要分流：SST-2 是英文模型，中文喂进去不会报错，只会输出与语义无关的
    概率（实测「这家酒店位置很好」被判成 NEGATIVE 0.94）。所以中文绝不能走它。

    输出含义（务必区分）：
    - 英文结果只有 POSITIVE / NEGATIVE 两类，概率和为 1。SST-2 本身就是二分类，
      不存在 NEUTRAL；早先版本把 NEUTRAL 写死成 0.02，属于虚构，已移除。
    - 中文结果只有 正面 / 负面 两类，来自 MLP 的 predict_proba。
    - 中文模型权重缺失时，退化为关键词规则（engine=keyword_rules），
      响应里的 model_name 会明确写「关键词规则」，不会伪装成机器学习模型。

    权重加载策略（由 MODEL_STRICT_OFFLINE 环境变量控制）：
    - true  → 只读本地路径，缺失时不联网（Docker）
    - false → 优先本地路径，缺失时从 HF Hub 自动下载（开发环境）

    示例：
    model = BertSentimentModel(device="cpu")
    model.load()
    result = model.predict("This movie is fantastic!")
"""

import os
import time
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from app.models.base import BaseModel

# ── 离线模式开关 ───────────────────────────────────────────────
_STRICT_OFFLINE = os.getenv("MODEL_STRICT_OFFLINE", "false").lower() == "true"

# 中文字符占比达到该阈值即按中文处理（混排句子里中文通常承载情感词）
_ZH_RATIO_THRESHOLD = 0.2

# 规则兜底在响应里的标识——不能让用户以为这是模型输出。
# 中英各用一个名字：规则表本身是中文情感词，英文没有可用规则，如实标出来。
_RULE_ENGINE_NAME_ZH = "中文情感（关键词规则）"
_RULE_ENGINE_NAME_EN = "英文情感（关键词规则）"


def _chinese_ratio(text: str) -> float:
    """中文字符占非空白字符的比例，用于语种判定。"""
    compact = "".join(text.split())
    if not compact:
        return 0.0
    zh = sum(1 for c in compact if '\u4e00' <= c <= '\u9fff')
    return zh / len(compact)

# 中文情感关键词（在原 mock 版本基础上扩充）
_POSITIVE_CN = {
    "好", "棒", "精彩", "推荐", "喜欢", "满意", "优秀", "完美", "开心",
    "感动", "惊艳", "赞", "出色", "经典", "不错", "给力", "好评",
    "值得", "好看", "好听", "好玩", "好吃", "厉害", "牛逼", "太棒了",
    "非常", "很好", "真好", "太好了", "超赞", "神作", "良心",
}

_NEGATIVE_CN = {
    "差", "糟", "烂", "失望", "垃圾", "讨厌", "无聊", "糟糕", "难",
    "差劲", "坑", "骗", "恶心", "后悔", "不值", "差评", "难受",
    "不行", "不好", "太差", "很差", "烂片", "浪费时间", "坑爹",
}

_NEUTRAL_CN = {
    "一般", "还行", "凑合", "普通", "及格", "中规中矩", "马马虎虎",
    "还可以", "过得去",
}


class BertSentimentModel(BaseModel):
    """
        基于 BERT 的情感分类器，中文场景回落到关键词规则。

        严格离线模式（MODEL_STRICT_OFFLINE=true）：只加载本地文件，
        缺失时回落到关键词引擎。

        开发模式（MODEL_STRICT_OFFLINE=false）：优先本地文件，
        缺失时从 HuggingFace Hub 自动下载。
    """

    # HuggingFace 模型标识（开发模式下用于自动下载）
    HF_MODEL_ID = "distilbert-base-uncased-finetuned-sst-2-english"

    # 预下载模型在本地的路径（Docker 构建阶段写入）
    DEFAULT_MODEL_PATH = "weights/bert_sentiment"

    def __init__(self, device: str = "cpu", model_id: Optional[str] = None):
        """
            参数：
            device: "cpu" 或 "cuda"（仅 HF pipeline 使用）
            model_id: 覆盖本地模型目录或 HF 模型 ID。
            默认：weights/bert_sentiment
        """
        super().__init__(device=device)
        self.model_path = model_id or self.DEFAULT_MODEL_PATH
        self._pipeline = None
        # 中文链路：(MLP, CountVectorizer) 元组；加载失败后不再重试
        self._zh_model = None
        self._zh_load_failed = False

    @property
    def model_name(self) -> str:
        """返回模型名称 BERT-Base。"""
        return "BERT-Base"

    def load(self) -> None:
        """
            加载 HuggingFace 情感分析 pipeline。

            优先使用本地文件；开发模式下可从 HF Hub 自动下载。
            pipeline 不可用时，一律回落到关键词规则引擎。
        """
        print(f"[{self.model_name}] Loading pipeline "
              f"(strict_offline={_STRICT_OFFLINE}) ...")
        t0 = time.time()

        if os.path.isdir(self.model_path):
            self._load_from_local()
        elif _STRICT_OFFLINE:
            print(f"[{self.model_name}] Local model not found: {self.model_path}")
            print(f"[{self.model_name}] MODEL_STRICT_OFFLINE=true — "
                  f"refusing to download.")
            print(f"[{self.model_name}] Will use keyword engine for all text.")
            self._pipeline = None
            self._loaded = True
            return
        else:
            self._load_from_huggingface()

    def _load_from_local(self) -> None:
        """
            从本地目录加载 pipeline（完全离线）。
        """
        from transformers import pipeline

        print(f"[{self.model_name}] Loading from local: {self.model_path}")
        device_id = 0 if self.device == "cuda" else -1
        self._pipeline = pipeline(
            "sentiment-analysis",
            model=self.model_path,
            tokenizer=self.model_path,
            device=device_id,
            truncation=True,
            max_length=512,
        )
        self._loaded = True

    def _load_from_huggingface(self) -> None:
        """
            从 HuggingFace Hub 自动下载（仅开发模式）。
        """
        # 支持 HF 镜像（例如国内的 https://hf-mirror.com）
        os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
        os.environ.setdefault("HF_HUB_DOWNLOAD_TIMEOUT", "30")

        print(f"[{self.model_name}] Local model not found — "
              f"auto-downloading from HuggingFace: {self.HF_MODEL_ID} (~270 MB)...")
        if os.environ.get("HF_ENDPOINT") != "https://huggingface.co":
            print(f"[{self.model_name}] Using HF mirror: "
                  f"{os.environ['HF_ENDPOINT']}")

        try:
            from transformers import pipeline

            device_id = 0 if self.device == "cuda" else -1
            self._pipeline = pipeline(
                "sentiment-analysis",
                model=self.HF_MODEL_ID,
                tokenizer=self.HF_MODEL_ID,
                device=device_id,
                truncation=True,
                max_length=512,
            )
            self._loaded = True

        except Exception as e:
            print(f"[{self.model_name}] HF download failed: {type(e).__name__}: {e}")
            print(f"[{self.model_name}] Will use keyword engine for all text.")
            self._pipeline = None
            self._loaded = True  # Still usable via keyword engine

    def predict(self, input_data: Any, **kwargs) -> Dict:
        """
            对输入文本做情感分类，按语种自动分流。

            参数：
            input_data: str —— 待分类的文本

            返回：
            {
            "model_name": "BERT-Base" | "ChineseSentiment" | "中文情感（关键词规则）",
            "results": [{"label": ..., "probability": ...}, ...],
            "duration_ms": 89,
            "timestamp": "2026-06-03T12:00:00Z",
            "engine": "distilbert_sst2" | "chinese_mlp" | "keyword_rules",
            "language": "zh" | "en" | "mixed",
            "binary": true
            }
        """
        self._validate_loaded()

        if not isinstance(input_data, str):
            input_data = str(input_data)

        text = input_data.strip()
        if not text:
            return self._empty_result()

        t0 = time.time()

        zh_ratio = _chinese_ratio(text)
        # 中文占 20% 以上就交给中文模型：混排句子里中文往往承载情感词，
        # 而英文 SST-2 对混排中文只会输出噪声。
        if zh_ratio >= _ZH_RATIO_THRESHOLD:
            results, engine, model_name = self._predict_chinese(text)
            language = "zh"
        elif zh_ratio > 0:
            results, engine, model_name = self._predict_english(text)
            language = "mixed"
        else:
            results, engine, model_name = self._predict_english(text)
            language = "en"

        duration_ms = int((time.time() - t0) * 1000)
        print(f"[{self.model_name}] Input: {len(text)} chars, language={language}, "
              f"engine={engine}, duration={duration_ms}ms, "
              f"top: {results[0]['label']} ({results[0]['probability']:.4f})")

        return self._build_response(results, duration_ms, engine, language, model_name)

    # ── 中文链路 ────────────────────────────────────────────────

    def _predict_chinese(self, text: str):
        """
            中文文本：优先用 ChineseSentiment（MLP + 词袋）。
            权重缺失或加载失败时退化为关键词规则，并在 model_name 里标明。
        """
        if self._zh_model is None and not self._zh_load_failed:
            try:
                from app.models import chinese_sentiment_model as zh_model
                print(f"[{self.model_name}] 中文文本 → 加载 ChineseSentiment 模型 ...")
                self._zh_model = zh_model.load_model()
            except Exception as e:
                # 缺权重是部署问题，不阻断服务：退化为规则引擎并留下日志
                print(f"[{self.model_name}] ChineseSentiment 不可用 "
                      f"({type(e).__name__}: {e})，中文改用关键词规则")
                self._zh_load_failed = True

        if self._zh_model is not None:
            from app.models import chinese_sentiment_model as zh_model
            raw = zh_model.predict(self._zh_model, text)
            candidates = raw.get("candidates") or []
            if candidates:
                return candidates, "chinese_mlp", "ChineseSentiment"

        return self._classify_keywords(text, ("正面", "负面", "中性")), "keyword_rules", _RULE_ENGINE_NAME_ZH

    # ── 英文链路 ────────────────────────────────────────────────

    def _predict_english(self, text: str):
        """
            英文/其他语种：走 distilbert SST-2。模型不可用时退化为关键词规则。
        """
        if self._pipeline is not None:
            results = self._classify_hf(text)
            if results:
                return results, "distilbert_sst2", self.model_name
        return self._classify_keywords(text), "keyword_rules", _RULE_ENGINE_NAME_EN

    def _classify_hf(self, text: str) -> Optional[List[Dict]]:
        """
            使用 HuggingFace pipeline 做情感分类；失败时返回 None。

            SST-2 是二分类，这里只输出 POSITIVE / NEGATIVE，概率归一化到 1。
            不再补一个写死的 NEUTRAL——那会让前端误以为这是三分类模型。
        """
        try:
            raw = self._pipeline(text)[0]
            label = str(raw["label"]).upper()
            score = float(raw["score"])

            if label.startswith("POS"):
                positive = score
            elif label.startswith("NEG"):
                positive = 1.0 - score
            else:
                print(f"[{self.model_name}] 未识别的标签: {label}")
                return None

            # 必须按概率降序：上游 /text/classify 与后端都取 results[0] 当 top_label，
            # 固定「POSITIVE 在前」会让负面文本的 top_label 变成 POSITIVE 0.001。
            results = [
                {"label": "POSITIVE", "probability": round(positive, 6)},
                {"label": "NEGATIVE", "probability": round(1.0 - positive, 6)},
            ]
            results.sort(key=lambda x: x["probability"], reverse=True)
            return results
        except Exception as e:
            print(f"[{self.model_name}] HF pipeline error: {e}")
            return None

    def _classify_keywords(self, text: str, labels: tuple = ("POSITIVE", "NEGATIVE", "NEUTRAL")) -> List[Dict]:
        """
            基于关键词规则的情感分类（不是机器学习，仅在模型不可用时兜底）。

            规则命中数做拉普拉斯平滑后归一化。标签可选，中文场景传中文标签，
            免得出现「模型名写着中文、标签却是 POSITIVE」这种不一致。
        """
        pos_label, neg_label, neu_label = labels
        pos = sum(1 for w in _POSITIVE_CN if w in text)
        neg = sum(1 for w in _NEGATIVE_CN if w in text)
        neu = sum(1 for w in _NEUTRAL_CN if w in text)

        # 附加的启发式规则
        if "!" in text or "！" in text:
            pos += 1
        if "..." in text or "……" in text:
            neu += 1
        if "?" in text or "？" in text:
            neu += 0.5

        total = pos + neg + neu
        if total == 0:
            # 没有命中任何关键词——默认判为中性
            return [
                {"label": neu_label, "probability": 0.60},
                {"label": pos_label, "probability": 0.25},
                {"label": neg_label, "probability": 0.15},
            ]

        # 做平滑与归一化
        smoothing = 1.0
        pos_prob = (pos + smoothing) / (total + 3 * smoothing)
        neg_prob = (neg + smoothing) / (total + 3 * smoothing)
        neu_prob = (neu + smoothing) / (total + 3 * smoothing)

        # 按概率降序排序
        results = [
            {"label": pos_label, "probability": round(pos_prob, 6)},
            {"label": neg_label, "probability": round(neg_prob, 6)},
            {"label": neu_label, "probability": round(neu_prob, 6)},
        ]
        results.sort(key=lambda x: x["probability"], reverse=True)
        return results

    def _build_response(self, results: List[Dict], duration_ms: int,
                        engine: str, language: str, model_name: str) -> Dict:
        """
            按统一结构组装响应。

            engine / language 是给接口调用方看的「结果是怎么来的」，
            前端不展示，但排障和二次开发时需要能区分。
        """
        return {
            "model_name": model_name,
            "results": results,
            "duration_ms": duration_ms,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "engine": engine,
            "language": language,
            "binary": True,
        }

    def _empty_result(self) -> Dict:
        """
            空输入：不编造中性结论，直接返回空候选，由调用方按参数错误处理。

            历史实现返回「NEUTRAL 1.0」，等于把一个空字符串说成中性情感，
            既没有依据，也会让统计口径失真。
        """
        return {
            "model_name": self.model_name,
            "results": [],
            "duration_ms": 0,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "engine": "none",
            "language": "unknown",
            "binary": True,
            "note": "空输入，未做分类",
        }
