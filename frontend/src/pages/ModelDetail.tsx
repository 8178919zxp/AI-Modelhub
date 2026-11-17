import { useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { useStore } from "../store/useStore";
import { fetchModelApi, fetchModelsApi, type ModelVO } from "../api/models";
import {
  CATEGORY_META,
  getDisplayName,
  getModelGuide,
  getTypeLabel,
} from "../data/models";
import { formatAccuracy } from "../utils/format";
import {
  ArrowLeft,
  ArrowRight,
  Heart,
  Loader2,
  RefreshCw,
  ChevronDown,
  Upload,
  AlertTriangle,
  CheckCircle2,
  Target,
  ListChecks,
  Compass,
  Cpu,
} from "lucide-react";

/* ═══════════════════════════════════════════════
   小组件
   ═══════════════════════════════════════════════ */

function SectionTitle({
  icon: Icon,
  title,
  hint,
}: {
  icon: React.FC<{ className?: string }>;
  title: string;
  hint?: string;
}) {
  return (
    <div className="mb-3 flex items-center gap-2">
      <Icon className="h-4 w-4 text-gray-400" />
      <h2 className="text-[14px] font-semibold text-gray-900">{title}</h2>
      {hint && <span className="text-[11px] text-gray-400">{hint}</span>}
    </div>
  );
}

/* ═══════════════════════════════════════════════
   页面
   ═══════════════════════════════════════════════ */

export default function ModelDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { isFavorite, toggleFavorite, isAuthenticated } = useStore();
  const [model, setModel] = useState<ModelVO | null>(null);
  const [siblings, setSiblings] = useState<ModelVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [techOpen, setTechOpen] = useState(false);

  const loadDetail = async () => {
    try {
      const [resp, list] = await Promise.all([
        fetchModelApi(Number(id)),
        fetchModelsApi().catch(() => null),
      ]);
      if (resp.code === 200) {
        setModel(resp.data);
        if (list && list.code === 200) {
          const cat = getModelGuide(resp.data.api, resp.data.type).category;
          const others = list.data.filter((m) => m.status === 1 && m.id !== resp.data.id);
          const sameCat = others.filter(
            (m) => getModelGuide(m.api, m.type).category === cat
          );
          // 同领域不足 2 个时用其它领域补齐，避免详情页底部只剩空白
          const fill = others.filter((m) => !sameCat.includes(m));
          setSiblings([...sameCat, ...fill].slice(0, 3));
        }
      }
    } catch {
            // 模型不存在或加载失败
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadDetail();
  }, [id]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadDetail();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-32">
        <Loader2 className="h-6 w-6 animate-spin text-blue-600" />
      </div>
    );
  }

  if (!model) {
    return (
      <div className="mx-auto max-w-lg p-12 text-center">
        <p className="mb-4 text-gray-500">服务不存在或加载失败</p>
        <button
          onClick={() => navigate("/models")}
          className="text-sm text-blue-600 hover:text-blue-700"
        >
          返回产品服务
        </button>
      </div>
    );
  }

  const guide = getModelGuide(model.api, model.type);
  const meta = CATEGORY_META[guide.category];
  const fav = isFavorite(model.id);
  const hasAccuracy = (model.accuracy ?? 0) > 0;

  return (
    <div className="min-h-screen bg-[#F6F7FA] pb-20">
      <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
        {/* 顶部操作栏 */}
        <div className="mb-4 flex items-center justify-between">
          <Link
            to="/models"
            className="inline-flex items-center gap-1.5 text-[13px] text-gray-500 transition-colors hover:text-gray-900"
          >
            <ArrowLeft className="h-4 w-4" /> 返回产品服务
          </Link>
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-[12px] text-gray-500 transition hover:text-gray-700 disabled:opacity-50"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? "animate-spin" : ""}`} />
            {refreshing ? "刷新中…" : "刷新"}
          </button>
        </div>

        {/* ═══ Hero ═══ */}
        <div className="relative overflow-hidden rounded-3xl border border-gray-200/80 bg-white">
          <div
            className={`pointer-events-none absolute inset-x-0 top-0 h-[3px] bg-gradient-to-r ${meta.gradient}`}
          />
          <div
            className={`pointer-events-none absolute -right-16 -top-20 h-64 w-64 rounded-full bg-gradient-to-br ${meta.gradient} opacity-[0.08] blur-2xl`}
          />
          <div className="pointer-events-none absolute -bottom-6 right-6 hidden select-none text-[150px] leading-none opacity-[0.05] sm:block">
            {guide.icon}
          </div>

          <div className="relative p-6 sm:p-8">
            <div className="flex flex-wrap items-center gap-2">
              <span
                className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[11px] ${meta.soft}`}
              >
                {meta.icon} {meta.label}
              </span>
              <span className="rounded-full border border-gray-200 bg-gray-50 px-2.5 py-0.5 text-[11px] text-gray-500">
                {getTypeLabel(model.type)}
              </span>
              {hasAccuracy && (
                <span className="rounded-full border border-emerald-100 bg-emerald-50 px-2.5 py-0.5 text-[11px] text-emerald-700">
                  准确率 {formatAccuracy(model.accuracy)}
                </span>
              )}
              <span className="inline-flex items-center gap-1 rounded-full border border-gray-200 bg-white px-2.5 py-0.5 text-[11px] text-gray-500">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" /> 在线可用
              </span>
            </div>

            <div className="mt-4 flex items-start gap-4">
              <div
                className={`hidden h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${meta.gradient} text-[30px] shadow-sm sm:flex`}
              >
                {guide.icon}
              </div>
              <div className="min-w-0">
                <h1 className="text-[24px] font-bold leading-tight tracking-tight text-gray-900 sm:text-[28px]">
                  {getDisplayName(model.name)}
                </h1>
                <p className="mt-1 font-mono text-[12px] text-gray-400">{model.name}</p>
                <p className="mt-2 text-[14px] font-medium text-gray-700">{guide.tagline}</p>
              </div>
            </div>

            <p className="mt-4 max-w-3xl text-[13.5px] leading-7 text-gray-600">
              {model.description || guide.summary}
            </p>

            <div className="mt-6 flex flex-wrap items-center gap-2.5">
              <Link
                to={`/predict?model=${model.id}`}
                className="inline-flex items-center gap-2 rounded-xl bg-gray-900 px-5 py-2.5 text-[13px] font-medium text-white transition hover:bg-gray-800"
              >
                开始使用
                <ArrowRight className="h-4 w-4" />
              </Link>
              {isAuthenticated && (
                <button
                  onClick={() => toggleFavorite(model.id)}
                  className={`inline-flex items-center gap-1.5 rounded-xl border px-4 py-2.5 text-[13px] font-medium transition ${
                    fav
                      ? "border-rose-200 bg-rose-50 text-rose-600"
                      : "border-gray-200 bg-white text-gray-600 hover:border-gray-300 hover:bg-gray-50"
                  }`}
                >
                  <Heart className={`h-4 w-4 ${fav ? "fill-current" : ""}`} />
                  {fav ? "已收藏" : "收藏"}
                </button>
              )}
            </div>
          </div>
        </div>

        {/* ═══ 正文 ═══ */}
        <div className="mt-5 grid gap-5 lg:grid-cols-3">
          {/* 左 2/3 */}
          <div className="space-y-5 lg:col-span-2">
            {/* 能识别什么 */}
            {guide.classes && guide.classes.length > 0 && (
              <div className="rounded-2xl border border-gray-200/80 bg-white p-6">
                <SectionTitle
                  icon={Target}
                  title={guide.category === "text" ? "会输出哪些倾向" : "能识别哪些类别"}
                  hint={`${guide.classes.length} 类`}
                />
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                  {guide.classes.map((cls, i) => (
                    <div
                      key={cls}
                      className={`flex items-center gap-2 rounded-xl border bg-white px-3 py-2.5 ${meta.border}`}
                    >
                      <span
                        className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-md bg-gradient-to-br ${meta.gradient} text-[10px] font-semibold text-white`}
                      >
                        {i + 1}
                      </span>
                      <span className="truncate text-[12.5px] font-medium text-gray-800">
                        {cls}
                      </span>
                    </div>
                  ))}
                </div>
                {guide.classNote && (
                  <p className="mt-3 text-[12px] leading-6 text-gray-400">{guide.classNote}</p>
                )}
              </div>
            )}

            {/* 输入要求 + 输出内容 */}
            <div className="grid gap-5 sm:grid-cols-2">
              <div className="rounded-2xl border border-gray-200/80 bg-white p-6">
                <SectionTitle icon={Upload} title="输入要求" />
                <p className="text-[13px] leading-7 text-gray-600">{guide.input}</p>
              </div>
              <div className="rounded-2xl border border-gray-200/80 bg-white p-6">
                <SectionTitle icon={ListChecks} title="输出内容" />
                <p className="text-[13px] leading-7 text-gray-600">{guide.output}</p>
                <div className="mt-3 space-y-1.5">
                  {guide.outputs.map((o) => (
                    <div key={o} className="flex items-start gap-1.5 text-[12px] text-gray-500">
                      <CheckCircle2 className={`mt-0.5 h-3.5 w-3.5 shrink-0 ${meta.text}`} />
                      <span>{o}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* 使用限制 / 免责声明 */}
            {guide.notice && (
              <div className="flex items-start gap-2.5 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
                <p className="text-[12.5px] leading-6 text-amber-800">{guide.notice}</p>
              </div>
            )}
          </div>

          {/* 右 1/3 */}
          <div className="space-y-5">
            {/* 适用场景 */}
            <div className="rounded-2xl border border-gray-200/80 bg-white p-6">
              <SectionTitle icon={Compass} title="适用场景" />
              <ul className="space-y-2.5">
                {guide.scenario.map((s) => (
                  <li key={s} className="flex items-start gap-2 text-[12.5px] leading-6 text-gray-600">
                    <span
                      className={`mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full bg-gradient-to-r ${meta.gradient}`}
                    />
                    {s}
                  </li>
                ))}
              </ul>
            </div>

            {/* 技术信息 */}
            <div className="rounded-2xl border border-gray-200/80 bg-white p-6">
              <button
                onClick={() => setTechOpen(!techOpen)}
                className="flex w-full items-center justify-between text-left"
              >
                <span className="flex items-center gap-2">
                  <Cpu className="h-4 w-4 text-gray-400" />
                  <span className="text-[14px] font-semibold text-gray-900">技术信息</span>
                </span>
                <ChevronDown
                  className={`h-4 w-4 text-gray-400 transition-transform ${techOpen ? "rotate-180" : ""}`}
                />
              </button>

              {techOpen && (
                <div className="mt-4 space-y-3 border-t border-gray-100 pt-4 text-[12.5px]">
                  <div>
                    <div className="text-[11px] text-gray-400">技术方案</div>
                    <div className="mt-0.5 text-gray-700">{guide.algorithmCn}</div>
                  </div>
                  <div>
                    <div className="text-[11px] text-gray-400">原始标识</div>
                    <div className="mt-0.5 font-mono text-[11.5px] text-gray-500">
                      {model.algorithm || "—"}
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] text-gray-400">服务入口</span>
                    <span className="font-mono text-[11.5px] text-gray-600">
                      {model.api || "/api/predict"}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] text-gray-400">更新时间</span>
                    <span className="text-[11.5px] text-gray-600">
                      {model.updateTime?.replace("T", " ") || "—"}
                    </span>
                  </div>
                </div>
              )}
            </div>

            {/* 体验入口 */}
            <div
              className={`rounded-2xl border p-6 ${meta.border} ${meta.surface}`}
            >
              <div className="text-[13px] font-semibold text-gray-900">
                直接试试这个服务
              </div>
              <p className="mt-1.5 text-[12px] leading-6 text-gray-600">
                {guide.category === "text"
                  ? "粘贴一段文字即可得到情感倾向，无需准备数据。"
                  : guide.category === "data"
                    ? "按表单填入数值字段即可得到预测结果。"
                    : "上传一张图片即可看到识别结果，支持 JPG / PNG。"}
              </p>
              <Link
                to={`/predict?model=${model.id}`}
                className="mt-4 inline-flex items-center gap-1.5 text-[12.5px] font-medium text-gray-900"
              >
                前往在线体验
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
          </div>
        </div>

        {/* ═══ 同领域其他服务 ═══ */}
        {siblings.length > 0 && (
          <div className="mt-8">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-[14px] font-semibold text-gray-900">
                你可能还会用到
              </h2>
              <Link
                to="/models"
                className="inline-flex items-center gap-1 text-[12px] text-gray-500 transition hover:text-gray-900"
              >
                查看全部
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              {siblings.map((m) => {
                const g = getModelGuide(m.api, m.type);
                return (
                  <Link
                    key={m.id}
                    to={`/models/${m.id}`}
                    className="group flex items-center gap-3 rounded-2xl border border-gray-200/80 bg-white p-4 transition hover:-translate-y-0.5 hover:shadow-[0_16px_36px_-24px_rgba(15,23,42,0.35)]"
                  >
                    <div
                      className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br ${CATEGORY_META[g.category].gradient} text-[18px]`}
                    >
                      {g.icon}
                    </div>
                    <div className="min-w-0">
                      <div className="truncate text-[13px] font-medium text-gray-900">
                        {getDisplayName(m.name)}
                      </div>
                      <div className="truncate text-[11.5px] text-gray-500">{g.tagline}</div>
                    </div>
                  </Link>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
