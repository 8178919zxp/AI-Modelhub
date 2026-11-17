import { useState, useEffect, useRef, useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import { useStore } from "../store/useStore";
import { fetchModelsApi, type ModelVO } from "../api/models";
import { predictImageApi, predictTabularApi, predictTextApi, predictCustomApi } from "../api/predict";
import {
  getDisplayName,
  getLabelDisplayName,
  getModelGuide,
  getTypeLabel,
  getOutputChips,
} from "../data/models";
import {
  Upload,
  Image as ImageIcon,
  X,
  Loader2,
  Sparkles,
  AlertCircle,
  Cpu,
  Clock,
  CheckCircle2,
  Type as TypeIcon,
  Download,
  GripHorizontal,
} from "lucide-react";

type Phase = "idle" | "predicting" | "done" | "error";
type ResultItem = { label: string; prob: number };


export default function Predict() {
  const { isAuthenticated } = useStore();
  const [searchParams] = useSearchParams();
  const [models, setModels] = useState<ModelVO[]>([]);
  const [modelId, setModelId] = useState<number>(1);
  const model = models.find((m) => m.id === modelId);

  // 支持从详情页 /models/:id 的「开始使用」直接带入模型
  const paramModelId = Number(searchParams.get("model"));
  useEffect(() => {
    if (Number.isFinite(paramModelId) && paramModelId > 0) {
      setModelId(paramModelId);
    }
  }, [paramModelId]);

  const [phase, setPhase] = useState<Phase>("idle");
  const [errorMsg, setErrorMsg] = useState("");
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [resultImage, setResultImage] = useState<string | null>(null);
  const [originalImageUrl, setOriginalImageUrl] = useState<string | null>(null);
  const [costMs, setCostMs] = useState(0);
  const [sliderPos, setSliderPos] = useState(50);
  const sliderRef = useRef<HTMLDivElement>(null);
  const dragging = useRef(false);

  const onSliderStart = useCallback(() => { dragging.current = true; }, []);
  const onSliderEnd = useCallback(() => { dragging.current = false; }, []);

  const onSliderMove = useCallback(
    (e: React.MouseEvent | React.TouchEvent) => {
      if (!dragging.current || !sliderRef.current) return;
      const rect = sliderRef.current.getBoundingClientRect();
      const clientX = "touches" in e ? e.touches[0].clientX : e.clientX;
      const x = Math.max(0, Math.min(clientX - rect.left, rect.width));
      setSliderPos((x / rect.width) * 100);
    },
    [],
  );
  const [resultType, setResultType] = useState("");
  const [results, setResults] = useState<ResultItem[]>([]);
  const [duration, setDuration] = useState(0);
  const [modelName, setModelName] = useState("");
  const [cooldownSeconds, setCooldownSeconds] = useState(0); // 429 限流冷却倒计时（秒）
  const [is429Error, setIs429Error] = useState(false);        // 当前错误是否为 429 限流
  const [textInput, setTextInput] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);

    // ── 天气 / 股票输入字段 ──────────────────────────────────────
  const now = new Date();
  const [weatherFields, setWeatherFields] = useState({
    precipitation: "",
    temp_max: "",
    temp_min: "",
    wind: "",
    year: now.getFullYear().toString(),
    month: (now.getMonth() + 1).toString(),
    day: now.getDate().toString(),
  });
  const [stockFields, setStockFields] = useState({
    Open: "",
    High: "",
    Low: "",
    Volume: "",
  });

    // XGBoost 模型的鸢尾花特征。顺序很重要：模型直接按
    // list(dict.values()) 的顺序取特征，必须与训练时一致
    // （花萼长度、花萼宽度、花瓣长度、花瓣宽度）。
  const [irisFields, setIrisFields] = useState({
    sepal_length: "5.1",
    sepal_width: "3.5",
    petal_length: "1.4",
    petal_width: "0.2",
  });

    // 从接口获取模型列表
  useEffect(() => {
    (async () => {
      try {
        const resp = await fetchModelsApi();
        if (resp.code === 200 && resp.data.length > 0) {
          setModels(resp.data);
          // 优先使用 URL 指定的模型，其次才回落到第一个
          const wanted = Number(new URLSearchParams(window.location.search).get("model"));
          const exists = resp.data.some((m) => m.id === wanted);
          setModelId(exists ? wanted : resp.data[0].id);
        }
      } catch {
        setErrorMsg("无法加载模型列表，请确认后端服务已启动");
      }
    })();
  }, []);

  // 429 限流冷却倒计时 — 每秒递减直到归零
  useEffect(() => {
    if (cooldownSeconds <= 0) return;
    const timer = setTimeout(() => {
      setCooldownSeconds((prev) => prev - 1);
    }, 1000);
    return () => clearTimeout(timer);
  }, [cooldownSeconds]);

    // 走 /custom 接口的自定义模型
  const CUSTOM_MODEL_APIS = ["weather", "stock", "chinese_sentiment"];
  const isCustomModel = (model?.api && CUSTOM_MODEL_APIS.includes(model.api)) ?? false;

  const isImageType = model?.type === "image" || model?.type === "object";
  const isImageAdvanced = model?.type === "image_advanced" || model?.api === "u2net";
  const isTextType = model?.type === "text";
  const isMLType = model?.type === "ml";

  const onFile = (file: File) => {
    if (!file.type.startsWith("image/")) {
      setErrorMsg("请上传图片文件");
      setPhase("error");
      return;
    }
    setImageFile(file);
    setImageUrl(URL.createObjectURL(file));
    setResults([]);
    setPhase("idle");
    setErrorMsg("");
  };

  const runPredict = async () => {
    if (!model) return;
    setPhase("predicting");
    setResults([]);
    setErrorMsg("");
    setIs429Error(false);
    const start = performance.now();

    try {
      let finalResults: ResultItem[] = [];

      if (isImageAdvanced && imageFile) {
                // 预测前先保存原图地址，便于对比
        if (imageUrl) setOriginalImageUrl(imageUrl);
        const resp = await predictImageApi(imageFile, model.id);
        const resultType = resp.data.resultType;
        const imageBase64 = resp.data.imageBase64;
        setModelName(resp.data.modelName || "U2Net");
        setResultType(resultType || "");
        if (resultType === "image_processing" && imageBase64) {
          const b64 = `data:image/png;base64,${imageBase64}`;
          setResultImage(b64);
          setCostMs(resp.data.duration || 0);
          finalResults = [{ label: "背景已移除", prob: 1.0 }];
        } else {
          throw new Error("U2Net 未返回有效图像");
        }
      } else if (isImageType && imageFile) {
        const resp = await predictImageApi(imageFile, model.id);
        setModelName(resp.data.modelName || "ResNet-50");
        finalResults = (resp.data.candidates ?? []).map((item) => ({
          label: getLabelDisplayName(item.label),
          prob: item.probability,
        }));
      } else if (isTextType && isCustomModel) {
                // 中文情感 → 自定义接口
        if (!textInput.trim()) throw new Error("请输入文本");
        const resp = await predictCustomApi({ modelId: model.id, text: textInput });
        setModelName(resp.data.modelName || "ChineseSentiment");
        finalResults = (resp.data.candidates ?? []).map((item) => ({
          label: getLabelDisplayName(item.label),
          prob: item.probability,
        }));
      } else if (isTextType) {
        if (!textInput.trim()) throw new Error("请输入文本");
        const resp = await predictTextApi({ modelId: model.id, text: textInput });
        setModelName(resp.data.modelName || "BERT-Base");
        finalResults = (resp.data.candidates ?? []).map((item) => ({
          label: getLabelDisplayName(item.label),
          prob: item.probability,
        }));
      } else if (isMLType && isCustomModel) {
                // 天气 / 股票 → 带用户输入特征的自定义接口
        const features =
          model.api === "stock"
            ? {
                Open: parseFloat(stockFields.Open) || 0,
                High: parseFloat(stockFields.High) || 0,
                Low: parseFloat(stockFields.Low) || 0,
                Volume: parseFloat(stockFields.Volume) || 0,
              }
            : {
                precipitation: parseFloat(weatherFields.precipitation) || 0,
                temp_max: parseFloat(weatherFields.temp_max) || 0,
                temp_min: parseFloat(weatherFields.temp_min) || 0,
                wind: parseFloat(weatherFields.wind) || 0,
                year: parseInt(weatherFields.year) || 2024,
                month: parseInt(weatherFields.month) || 1,
                day: parseInt(weatherFields.day) || 1,
              };
        const resp = await predictCustomApi({ modelId: model.id, features });
        setModelName(resp.data.modelName || model.name);
        if (resp.data.candidates && resp.data.candidates.length > 0) {
          finalResults = (resp.data.candidates ?? []).map((item) => ({
            label: getLabelDisplayName(item.label),
            prob: item.probability,
          }));
        } else if (resp.data.predictedClose != null) {
                    // 股票回归响应 —— 从 VO 中取 predictedClose
          finalResults = [{ label: `预测收盘价: ${resp.data.predictedClose}`, prob: 1.0 }];
        } else {
          finalResults = [{ label: getLabelDisplayName(resp.data.topLabel || "完成"), prob: resp.data.confidence || 1.0 }];
        }
      } else if (isMLType) {
        const resp = await predictTabularApi({
          modelId: model.id,
          features: {
            sepal_length: parseFloat(irisFields.sepal_length) || 0,
            sepal_width: parseFloat(irisFields.sepal_width) || 0,
            petal_length: parseFloat(irisFields.petal_length) || 0,
            petal_width: parseFloat(irisFields.petal_width) || 0,
          },
        });
        setModelName(resp.data.modelName || "XGBoost");
        finalResults = (resp.data.candidates ?? []).map((item) => ({
          label: getLabelDisplayName(item.label),
          prob: item.probability,
        }));
      } else {
        throw new Error("请先选择输入数据");
      }

      const elapsed = Math.round(performance.now() - start);
      setDuration(elapsed);
      setResults(finalResults);
      setPhase("done");
    } catch (err: any) {
      // 429 限流 → 冷却 5 秒，其余错误正常显示
      if (err?.status === 429) {
        setErrorMsg(err.message || "请求过于频繁，请稍后再试");
        setIs429Error(true);
        setCooldownSeconds(5);
      } else {
        setErrorMsg(err?.message || "分析失败，请确认后端服务已启动");
        setIs429Error(false);
      }
      setPhase("error");
    }
  };

  const reset = () => {
    setResultType("");
    setResults([]);
    setImageUrl(null);
    setImageFile(null);
    setResultImage(null);
    setOriginalImageUrl(null);
    setCostMs(0);
    setPhase("idle");
    setErrorMsg("");
    setIs429Error(false);
    setCooldownSeconds(0);
  };

  if (!model && phase !== "error") {
    return (
      <div className="flex items-center justify-center py-32">
        <Loader2 className="h-8 w-8 text-blue-600 animate-spin" />
      </div>
    );
  }

  const guide = getModelGuide(model?.api, model?.type);
  // 每个模型用自己的图标，避免同类模型在列表里长得一样
  const t = guide.icon;

  /** 按钮禁用状态：分析中 或 429 限流冷却中 */
  const isProcessing = phase === "predicting" || cooldownSeconds > 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
      {/* 页面标题 */}
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900">在线服务中心</h1>

        <p className="text-sm text-gray-500 mt-3">
          选择功能并上传文件或输入内容，即可获得实时分析结果。
          {!isAuthenticated && <span className="text-amber-600 ml-2">（登录后即可免费使用全部功能）</span>}
        </p>
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* Model Selector */}
        <div className="lg:col-span-1 space-y-3">
          <div className="glass rounded-2xl p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
              <Cpu className="h-4 w-4 text-blue-600" /> 选择功能
            </h3>
            <div className="space-y-1.5 max-h-[500px] overflow-y-auto pr-1 no-scrollbar">
              {models.map((m) => {
                const active = m.id === modelId;
                const itemGuide = getModelGuide(m.api, m.type);
                return (
                  <button
                    key={m.id}
                    onClick={() => {
                      setModelId(m.id);
                      reset();
                    }}
                    className={`w-full text-left flex items-center gap-2.5 p-2.5 rounded-xl transition border ${
                      active
                        ? "bg-blue-50 border-blue-300"
                        : "bg-gray-50 border-gray-200 hover:border-gray-300"
                    }`}
                  >
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gray-100 text-lg">
                      {itemGuide.icon}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div
                        className={`text-sm font-medium ${active ? "text-blue-700" : "text-gray-700"}`}
                      >
                        {getDisplayName(m.name)}
                      </div>
                      <div className="mt-0.5 truncate text-[11px] text-gray-400">
                        {itemGuide.tagline}
                      </div>
                    </div>
                    {active && <CheckCircle2 className="h-4 w-4 text-blue-600 shrink-0" />}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Main Area */}
        <div className="lg:col-span-2 space-y-4">
          {/* 当前选中模型 — 简化标题（仅名称，无类型标签/算法描述/详情链接） */}
          {model && (
            <div className="glass rounded-2xl p-4 bg-gradient-to-r from-blue-50 to-indigo-50 relative overflow-hidden border-blue-200">
              <div className="relative flex items-start gap-3">
                <div className="text-4xl leading-none">{t}</div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-lg font-bold text-gray-900">
                      {getDisplayName(model.name)}
                    </h2>
                    <span className="rounded-full border border-blue-200 bg-white/70 px-2 py-0.5 text-[11px] text-blue-700">
                      {getTypeLabel(model.type)}
                    </span>
                  </div>
                  <p className="mt-1 text-[12.5px] text-gray-600">{guide.tagline}</p>
                  <div className="mt-2 flex flex-wrap items-center gap-1.5">
                    <span className="text-[11px] text-gray-400">
                      {guide.classes ? "可识别：" : "输出："}
                    </span>
                    {getOutputChips(model.api, model.type).map((c) => (
                      <span
                        key={c}
                        className="rounded-md border border-blue-100 bg-white/80 px-1.5 py-0.5 text-[11px] text-gray-600"
                      >
                        {c}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {phase === "error" && (
            <div
              className={`glass rounded-2xl p-4 flex items-start gap-2 ${
                is429Error ? "border-amber-300 bg-amber-50" : "border-red-300 bg-red-50"
              }`}
            >
              <AlertCircle
                className={`h-5 w-5 shrink-0 mt-0.5 ${
                  is429Error ? "text-amber-500" : "text-red-500"
                }`}
              />
              <div
                className={`text-sm ${
                  is429Error ? "text-amber-700" : "text-red-700"
                }`}
              >
                {errorMsg}
              </div>
            </div>
          )}

          {/* Image Upload (classification, detection, background removal) */}
          {(phase === "idle" || phase === "done") && (isImageType || isImageAdvanced) && (
            <div className="glass rounded-2xl p-5">
              <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
                <ImageIcon className="h-4 w-4 text-blue-600" />{" "}
                {isImageAdvanced ? "上传图片进行抠图" : "上传图片"}
              </h3>
              {!imageUrl ? (
                <div
                  onClick={() => fileRef.current?.click()}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={(e) => {
                    e.preventDefault();
                    const f = e.dataTransfer.files[0];
                    if (f) onFile(f);
                  }}
                  className="border-2 border-dashed border-gray-300 rounded-2xl p-10 text-center cursor-pointer hover:border-blue-400 hover:bg-blue-50/50 transition"
                >
                  <Upload className="h-10 w-10 text-gray-400 mx-auto mb-3" />
                  <p className="text-sm text-gray-700 mb-1">点击或拖拽图片到此处上传</p>
                  <p className="text-xs text-gray-500">支持 JPG / PNG / WEBP</p>
                  <input
                    ref={fileRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={(e) => e.target.files?.[0] && onFile(e.target.files[0])}
                  />
                </div>
              ) : (
                <div className="relative">
                  <img
                    src={imageUrl}
                    alt="upload"
                    className="w-full max-h-80 object-contain rounded-xl bg-gray-100"
                  />
                  <button
                    onClick={reset}
                    className="absolute top-2 right-2 p-1.5 rounded-lg bg-white/80 border border-gray-200 text-gray-700 hover:bg-red-50 hover:text-red-500 shadow-sm"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
              )}
              <div className="mt-4 flex gap-2">
                <button
                  onClick={runPredict}
                  disabled={!imageFile || !isAuthenticated || isProcessing}
                  className="flex-1 py-2.5 rounded-xl bg-blue-600 text-white font-medium disabled:opacity-50 hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition flex items-center justify-center gap-2"
                >
                  {cooldownSeconds > 0 ? (
                    <><Clock className="h-4 w-4" /> 请等待 {cooldownSeconds}s</>
                  ) : (
                    <><Sparkles className="h-4 w-4" /> {isImageAdvanced ? "开始抠图" : "开始分析"}</>
                  )}
                </button>
                {imageUrl && (
                  <button
                    onClick={reset}
                    className="px-4 py-2.5 rounded-xl bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 transition shadow-sm"
                  >
                    清空
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Text Input */}
          {isTextType && (phase === "idle" || phase === "done") && (
            <div className="glass rounded-2xl p-5">
              <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
                <TypeIcon className="h-4 w-4 text-blue-600" />{" "}
                {model?.api === "chinese_sentiment" ? "输入中文评论" : "输入文本"}
              </h3>
              <textarea
                value={textInput}
                onChange={(e) => setTextInput(e.target.value)}
                rows={5}
                className="w-full px-4 py-3 rounded-xl bg-white border border-gray-300 text-gray-900 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 resize-none placeholder:text-gray-400"
                placeholder={
                  model?.api === "chinese_sentiment"
                    ? "输入中文酒店评论，进行情感分析（正面/负面）…"
                    : "输入一段文本，进行情感分析..."
                }
              />
              <div className="mt-1 text-xs text-gray-500">{textInput.length} 字</div>
              <button
                onClick={runPredict}
                disabled={!textInput.trim() || !isAuthenticated || isProcessing}
                className="mt-3 w-full py-2.5 rounded-xl bg-blue-600 text-white font-medium disabled:opacity-50 hover:bg-blue-700 hover:shadow-lg hover:shadow-blue-500/20 transition flex items-center justify-center gap-2"
              >
                {cooldownSeconds > 0 ? (
                  <><Clock className="h-4 w-4" /> 请等待 {cooldownSeconds}s</>
                ) : (
                  <><Sparkles className="h-4 w-4" /> 开始分析</>
                )}
              </button>
            </div>
          )}

          {/* ML Tabular — Weather (7 fields) */}
          {isMLType && model?.api === "weather" && (phase === "idle" || phase === "done") && (
            <div className="glass rounded-2xl p-5">
              <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
                🌦️ 气象特征输入（7 个字段）
              </h3>
              <p className="text-xs text-gray-500 mb-4">
                输入西雅图气象站观测数据，预测天气类型（雨 / 晴 / 雾 / 毛毛雨 / 雪）
              </p>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-4">
                {[
                  { key: "precipitation", label: "降水量 (mm)", placeholder: "例: 2.5" },
                  { key: "temp_max", label: "最高温度 (°C)", placeholder: "例: 18.0" },
                  { key: "temp_min", label: "最低温度 (°C)", placeholder: "例: 8.0" },
                  { key: "wind", label: "风速 (m/s)", placeholder: "例: 3.2" },
                  { key: "year", label: "年份", placeholder: "例: 2024" },
                  { key: "month", label: "月份", placeholder: "例: 6" },
                  { key: "day", label: "日期", placeholder: "例: 15" },
                ].map(({ key, label, placeholder }) => (
                  <div key={key}>
                    <label className="block text-xs text-gray-600 mb-1">{label}</label>
                    <input
                      type="number"
                      step="any"
                      value={(weatherFields as any)[key]}
                      onChange={(e) =>
                        setWeatherFields((prev) => ({ ...prev, [key]: e.target.value }))
                      }
                      placeholder={placeholder}
                      className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-sm text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
                    />
                  </div>
                ))}
              </div>
              <button
                onClick={runPredict}
                disabled={!isAuthenticated || isProcessing}
                className="w-full py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-medium hover:shadow-lg hover:shadow-blue-500/20 transition flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {cooldownSeconds > 0 ? (
                  <><Clock className="h-4 w-4" /> 请等待 {cooldownSeconds}s</>
                ) : (
                  <><Sparkles className="h-4 w-4" /> 预测天气</>
                )}
              </button>
            </div>
          )}
          {/* ML Tabular — Stock (4 fields) */}
          {isMLType && model?.api === "stock" && (phase === "idle" || phase === "done") && (
            <div className="glass rounded-2xl p-5">
              <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
                📈 股票交易特征输入（4 个字段）
              </h3>
              <p className="text-xs text-gray-500 mb-4">
                输入当日 OHLCV 数据中的 4 个特征，预测收盘价（Close）
              </p>
              <div className="grid grid-cols-2 gap-3 mb-4">
                {[
                  { key: "Open", label: "开盘价 (Open)", placeholder: "例: 45.0" },
                  { key: "High", label: "最高价 (High)", placeholder: "例: 46.5" },
                  { key: "Low", label: "最低价 (Low)", placeholder: "例: 44.2" },
                  { key: "Volume", label: "成交量 (Volume)", placeholder: "例: 15000000" },
                ].map(({ key, label, placeholder }) => (
                  <div key={key}>
                    <label className="block text-xs text-gray-600 mb-1">{label}</label>
                    <input
                      type="number"
                      step="any"
                      value={(stockFields as any)[key]}
                      onChange={(e) =>
                        setStockFields((prev) => ({ ...prev, [key]: e.target.value }))
                      }
                      placeholder={placeholder}
                      className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-sm text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
                    />
                  </div>
                ))}
              </div>
              <button
                onClick={runPredict}
                disabled={!isAuthenticated || isProcessing}
                className="w-full py-2.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 text-white font-medium hover:shadow-lg hover:shadow-emerald-500/20 transition flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {cooldownSeconds > 0 ? (
                  <><Clock className="h-4 w-4" /> 请等待 {cooldownSeconds}s</>
                ) : (
                  <><Sparkles className="h-4 w-4" /> 预测收盘价</>
                )}
              </button>
            </div>
          )}
          {/* ML Tabular — Iris / XGBoost (4 real features) */}
          {isMLType && !isCustomModel && (phase === "idle" || phase === "done") && (
            <div className="glass rounded-2xl p-5">
              <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
                🌲 鸢尾花特征输入（4 个字段）
              </h3>
              <p className="text-xs text-gray-500 mb-4">
                输入鸢尾花的四个测量值（单位 cm），由 XGBoost 输出山鸢尾 / 变色鸢尾 / 维吉尼亚鸢尾三种概率
              </p>
              <div className="grid grid-cols-2 gap-3 mb-4">
                {[
                  { key: "sepal_length", label: "花萼长度 (cm)", placeholder: "例: 5.1" },
                  { key: "sepal_width", label: "花萼宽度 (cm)", placeholder: "例: 3.5" },
                  { key: "petal_length", label: "花瓣长度 (cm)", placeholder: "例: 1.4" },
                  { key: "petal_width", label: "花瓣宽度 (cm)", placeholder: "例: 0.2" },
                ].map(({ key, label, placeholder }) => (
                  <div key={key}>
                    <label className="block text-xs text-gray-600 mb-1">{label}</label>
                    <input
                      type="number"
                      step="any"
                      value={(irisFields as any)[key]}
                      onChange={(e) =>
                        setIrisFields((prev) => ({ ...prev, [key]: e.target.value }))
                      }
                      placeholder={placeholder}
                      className="w-full px-3 py-2 rounded-lg bg-white border border-gray-300 text-sm text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
                    />
                  </div>
                ))}
              </div>
              <button
                onClick={runPredict}
                disabled={!isAuthenticated || isProcessing}
                className="w-full py-2.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 text-white font-medium hover:shadow-lg hover:shadow-emerald-500/20 transition flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {cooldownSeconds > 0 ? (
                  <><Clock className="h-4 w-4" /> 请等待 {cooldownSeconds}s</>
                ) : (
                  <><Sparkles className="h-4 w-4" /> 预测品种</>
                )}
              </button>
            </div>
          )}

          {/* Predicting */}
          {phase === "predicting" && (
            <div className="glass rounded-2xl p-10 text-center">
              <Loader2 className="h-10 w-10 text-blue-600 mx-auto animate-spin mb-3" />
              <p className="text-sm text-gray-700">正在分析中...</p>
              <p className="text-xs text-gray-500 mt-1">正在调用分析服务</p>
            </div>
          )}

          {/* Results */}
          {/* ── U²-Net background removal result (standalone) ── */}
          {resultImage && resultType === "image_processing" && (
            <div className="glass rounded-2xl p-5">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-semibold text-gray-700 flex items-center gap-1.5">
                  <CheckCircle2 className="h-4 w-4 text-emerald-600" /> 抠图结果
                </h3>
                <span className="text-xs text-gray-500 flex items-center gap-1">
                  <Clock className="h-3 w-3" /> {costMs || duration} ms
                </span>
              </div>
              {/* Real model engine badge */}
              {modelName && (
                <div className="mb-3 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-blue-50 border border-blue-200 text-xs">
                  <Cpu className="h-3 w-3 text-blue-600" />
                  <span className="text-blue-700">服务：</span>
                  <span className="text-gray-900 font-semibold">{getDisplayName(modelName)}</span>
                  <span className="text-blue-600">· 实时在线分析</span>
                </div>
              )}
              <div className="space-y-4">
                {/* Side-by-side: Original vs Result */}
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <div className="text-xs text-gray-500 mb-1.5 font-medium">原图</div>
                    <div className="rounded-xl overflow-hidden border border-gray-200 bg-gray-100">
                      <img
                        src={originalImageUrl || imageUrl || undefined}
                        alt="原图"
                        className="w-full h-56 object-contain"
                      />
                    </div>
                  </div>
                  <div>
                    <div className="text-xs text-gray-500 mb-1.5 font-medium">抠图结果</div>
                    <div
                      className="rounded-xl overflow-hidden border border-gray-200"
                      style={{
                        backgroundImage:
                          "linear-gradient(45deg,#ccc 25%,transparent 25%),linear-gradient(-45deg,#ccc 25%,transparent 25%),linear-gradient(45deg,transparent 75%,#ccc 75%),linear-gradient(-45deg,transparent 75%,#ccc 75%)",
                        backgroundSize: "20px 20px",
                        backgroundPosition: "0 0, 0 10px, 10px -10px, -10px 0px",
                      }}
                    >
                      <img
                        src={resultImage}
                        alt="抠图结果"
                        className="w-full h-56 object-contain"
                      />
                    </div>
                  </div>
                </div>

                {/* Before/After Slider Comparison */}
                <div>
                  <div className="text-xs text-gray-500 mb-1.5 font-medium">
                    滑动对比 <span className="text-gray-400">— 拖动中间滑块</span>
                  </div>
                  <div
                    ref={sliderRef}
                    className="relative rounded-xl overflow-hidden border border-gray-200 select-none cursor-ew-resize"
                    style={{ height: "360px" }}
                    onMouseDown={onSliderStart}
                    onMouseUp={onSliderEnd}
                    onMouseLeave={onSliderEnd}
                    onMouseMove={onSliderMove}
                    onTouchStart={onSliderStart}
                    onTouchEnd={onSliderEnd}
                    onTouchMove={onSliderMove}
                  >
                    {/* Layer 1: Original image (bottom) */}
                    <div className="absolute inset-0 bg-gray-100 flex items-center justify-center">
                      <img
                        src={originalImageUrl || imageUrl || undefined}
                        alt="原图"
                        className="w-full h-full object-contain"
                      />
                    </div>

                    {/* Layer 2: Result image (top, clipped from left by slider) */}
                    <div
                      className="absolute inset-0 overflow-hidden"
                      style={{ width: `${sliderPos}%` }}
                    >
                      <div
                        className="absolute inset-0"
                        style={{
                          backgroundImage:
                            "linear-gradient(45deg,#ccc 25%,transparent 25%),linear-gradient(-45deg,#ccc 25%,transparent 25%),linear-gradient(45deg,transparent 75%,#ccc 75%),linear-gradient(-45deg,transparent 75%,#ccc 75%)",
                          backgroundSize: "20px 20px",
                          backgroundPosition: "0 0, 0 10px, 10px -10px, -10px 0px",
                        }}
                      >
                        <img
                          src={resultImage}
                          alt="抠图结果"
                          className="w-full h-full object-contain"
                        />
                      </div>
                      {/* Label on result side */}
                      <div className="absolute top-3 left-3 px-2 py-0.5 rounded bg-black/50 text-white text-[10px]">
                        抠图
                      </div>
                    </div>

                    {/* Label on original side */}
                    <div className="absolute top-3 right-3 px-2 py-0.5 rounded bg-black/50 text-white text-[10px]">
                      原图
                    </div>

                    {/* Slider handle */}
                    <div
                      className="absolute top-0 bottom-0 w-0.5 bg-white shadow-lg pointer-events-none"
                      style={{ left: `${sliderPos}%` }}
                    >
                      <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 h-10 w-10 rounded-full bg-white shadow-lg border border-gray-200 flex items-center justify-center">
                        <GripHorizontal className="h-5 w-5 text-gray-600" />
                      </div>
                    </div>
                  </div>
                </div>

                {/* Action bar: Download + timing */}
                <div className="flex items-center justify-between">
                  <a
                    href={resultImage}
                    download="background-removed.png"
                    className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 hover:shadow-lg hover:shadow-emerald-500/20 transition"
                  >
                    <Download className="h-4 w-4" />
                    下载 PNG
                  </a>
                  <div className="flex items-center gap-3 text-xs text-gray-500">
                    <span className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      处理耗时 {costMs || duration}ms
                    </span>
                    <span className="flex items-center gap-1 px-2 py-0.5 rounded bg-purple-50 text-purple-600 border border-purple-100">
                      ✂️ 透明背景 PNG
                    </span>
                  </div>
                </div>
              </div>
              <div className="mt-4 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-700">
                ✅ 分析完成 · 服务：{getDisplayName(modelName || "U2Net")} · 耗时 {costMs || duration}ms · 实时在线分析
              </div>
            </div>
          )}

          {/* ── Classification / Detection / Text / Tabular results ── */}
          {phase === "done" && results.length > 0 && resultType !== "image_processing" && (
            <div className="glass rounded-2xl p-5">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-semibold text-gray-700 flex items-center gap-1.5">
                  <CheckCircle2 className="h-4 w-4 text-emerald-600" /> 分析结果
                </h3>
                <span className="text-xs text-gray-500 flex items-center gap-1">
                  <Clock className="h-3 w-3" /> {costMs || duration} ms
                </span>
              </div>
              {/* Real model engine badge */}
              {modelName && (
                <div className="mb-3 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-blue-50 border border-blue-200 text-xs">
                  <Cpu className="h-3 w-3 text-blue-600" />
                  <span className="text-blue-700">服务：</span>
                  <span className="text-gray-900 font-semibold">{getDisplayName(modelName)}</span>
                  <span className="text-blue-600">· 实时在线分析</span>
                </div>
              )}
              <div className="space-y-2.5">
                {results.map((r, i) => (
                  <div key={i} className="space-y-1.5">
                    <div className="flex items-center justify-between text-sm">
                      <span className={i === 0 ? "text-gray-900 font-medium" : "text-gray-600"}>
                        {i === 0 && "🏆 "}
                        {getLabelDisplayName(r.label)}
                      </span>
                      <span
                        className={`font-mono text-xs ${i === 0 ? "text-emerald-600 font-semibold" : "text-gray-500"}`}
                      >
                        {(r.prob * 100).toFixed(2)}%
                      </span>
                    </div>
                    <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                      <div
                        className={
                          i === 0
                            ? "h-full rounded-full bg-gradient-to-r from-blue-600 to-indigo-600"
                            : "h-full rounded-full bg-gradient-to-r from-gray-400 to-gray-500"
                        }
                        style={{ width: `${r.prob * 100}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-4 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-700">
                ✅ 分析完成 · 服务：{getDisplayName(modelName || "智能分析")} · 耗时 {costMs || duration}ms · 实时在线分析
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
