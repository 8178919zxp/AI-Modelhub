import { useState, useEffect, useMemo } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { fetchModelsApi, type ModelVO } from "../api/models";
import {
  CATEGORY_META,
  getCategoryByApi,
  getDisplayName,
  getModelGuide,
  getTypeLabel,
  type ModelCategory,
} from "../data/models";
import { formatAccuracy } from "../utils/format";
import {
  ArrowRight,
  RefreshCw,
  Box,
  Sparkles,
  ShieldCheck,
  Layers,
  Gauge,
  Stethoscope,
  Eye,
  PenLine,
  BarChart3,
} from "lucide-react";

/* ═══════════════════════════════════════════════
   分类定义
   ═══════════════════════════════════════════════ */

const CATEGORY_ORDER: ModelCategory[] = ["medical", "vision", "text", "data"];

const CATEGORY_ICON: Record<ModelCategory, React.FC<{ className?: string }>> = {
  medical: Stethoscope,
  vision: Eye,
  text: PenLine,
  data: BarChart3,
};

interface TabDef {
  key: string;
  label: string;
}

const TABS: TabDef[] = [
  { key: "all", label: "全部服务" },
  { key: "medical", label: CATEGORY_META.medical.label },
  { key: "vision", label: CATEGORY_META.vision.label },
  { key: "text", label: CATEGORY_META.text.label },
  { key: "data", label: CATEGORY_META.data.label },
];

/* ═══════════════════════════════════════════════
   小组件
   ═══════════════════════════════════════════════ */

function StatChip({
  icon: Icon,
  value,
  label,
}: {
  icon: React.FC<{ className?: string }>;
  value: string;
  label: string;
}) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="h-9 w-9 rounded-xl bg-white/10 border border-white/10 flex items-center justify-center">
        <Icon className="h-4 w-4 text-white/80" />
      </div>
      <div>
        <div className="text-base font-semibold text-white leading-none">{value}</div>
        <div className="text-[11px] text-white/50 mt-1">{label}</div>
      </div>
    </div>
  );
}

function OutputChips({ labels, tone }: { labels: string[]; tone: string }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {labels.map((label) => (
        <span
          key={label}
          className={`inline-flex items-center px-2 py-0.5 rounded-md border text-[11px] leading-5 ${tone}`}
        >
          {label}
        </span>
      ))}
    </div>
  );
}

/* ═══════════════════════════════════════════════
   模型卡片
   ═══════════════════════════════════════════════ */

function ModelCard({
  model,
  variant = "card",
}: {
  model: ModelVO;
  variant?: "full" | "panel" | "card";
}) {
  const guide = getModelGuide(model.api, model.type);
  const meta = CATEGORY_META[guide.category];
  // 主推卡展示完整类别；其余按空间取前若干个，避免撑破版式
  const chips =
    variant === "full"
      ? guide.classes ?? guide.outputs
      : (guide.classes ?? guide.outputs).slice(0, variant === "panel" ? 4 : 3);
  const hasAccuracy = (model.accuracy ?? 0) > 0;

  const accuracyRow = (
    <div className="flex items-center gap-3 text-[11px] text-gray-400">
      {hasAccuracy && (
        <span className="inline-flex items-center gap-1">
          <Gauge className="h-3.5 w-3.5" />
          准确率 {formatAccuracy(model.accuracy)}
        </span>
      )}
      <span className="inline-flex items-center gap-1">
        <ShieldCheck className="h-3.5 w-3.5" />
        在线可用
      </span>
    </div>
  );

  const detailLink = (
    <span className={`inline-flex items-center gap-1 text-[12px] font-medium ${meta.text}`}>
      查看详情
      <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
    </span>
  );

  /* ── 独占整行：左右分栏，避免单模型领域留下大片空白 ── */
  if (variant === "full") {
    return (
      <Link
        to={`/models/${model.id}`}
        className="group relative flex flex-col gap-5 overflow-hidden rounded-2xl border border-gray-200/80 bg-white p-6 transition-all duration-300 hover:-translate-y-0.5 hover:border-transparent hover:shadow-[0_18px_44px_-24px_rgba(15,23,42,0.35)]"
      >
        <span className={`absolute inset-y-0 left-0 w-[3px] bg-gradient-to-b ${meta.gradient}`} />
        <div className="flex flex-1 flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-3">
              <div
                className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${meta.gradient} text-[24px] shadow-sm`}
              >
                {guide.icon}
              </div>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-[17px] font-semibold text-gray-900">
                    {getDisplayName(model.name)}
                  </h3>
                  <span className={`rounded-full border px-2 py-0.5 text-[11px] ${meta.soft}`}>
                    {getTypeLabel(model.type)}
                  </span>
                </div>
                <p className="mt-0.5 text-[13px] font-medium text-gray-500">{guide.tagline}</p>
              </div>
            </div>
            <p className="mt-3 max-w-2xl text-[13px] leading-6 text-gray-500">{guide.summary}</p>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
              {accuracyRow}
              {detailLink}
            </div>
          </div>

          <div className={`w-full shrink-0 rounded-2xl border p-4 sm:w-64 ${meta.border} ${meta.surface}`}>
            <div className="mb-2 text-[11px] font-medium text-gray-500">
              {guide.classes ? "可识别类别" : "输出内容"}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {chips.map((c) => (
                <span
                  key={c}
                  className="rounded-md border border-white/80 bg-white/80 px-2 py-1 text-[11.5px] text-gray-700"
                >
                  {c}
                </span>
              ))}
            </div>
            {guide.classNote && (
              <p className="mt-2 line-clamp-3 text-[10.5px] leading-5 text-gray-500">
                {guide.classNote}
              </p>
            )}
          </div>
        </div>
      </Link>
    );
  }

  const expanded = variant === "panel";

  return (
    <Link
      to={`/models/${model.id}`}
      className="group relative flex flex-col rounded-2xl border border-gray-200/80 bg-white p-5 transition-all duration-300 hover:-translate-y-0.5 hover:border-transparent hover:shadow-[0_18px_44px_-24px_rgba(15,23,42,0.35)]"
    >
      {/* 顶部主色条：hover 时展开，作为领域标识 */}
      <span
        className={`absolute inset-x-5 top-0 h-[2px] origin-left scale-x-0 rounded-full bg-gradient-to-r ${meta.gradient} transition-transform duration-300 group-hover:scale-x-100`}
      />

      <div className="flex items-start justify-between gap-3">
        <div
          className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${meta.gradient} text-[22px] shadow-sm`}
        >
          {guide.icon}
        </div>
        <span className={`rounded-full border px-2 py-0.5 text-[11px] ${meta.soft}`}>
          {getTypeLabel(model.type)}
        </span>
      </div>

      <h3 className="mt-4 text-[17px] font-semibold tracking-tight text-gray-900">
        {getDisplayName(model.name)}
      </h3>
      <p className="mt-1 text-[13px] font-medium text-gray-500">{guide.tagline}</p>

      {expanded && (
        <p className="mt-2.5 text-[12.5px] leading-6 text-gray-500">{guide.summary}</p>
      )}

      <div className="mt-4">
        <div className="mb-1.5 text-[11px] font-medium tracking-wide text-gray-400">
          {guide.classes ? "可识别类别" : "输出内容"}
        </div>
        <OutputChips labels={chips} tone={meta.soft} />
      </div>

      <div className="mt-auto flex items-center justify-between gap-3 border-t border-gray-100 pt-4">
        {accuracyRow}
        {detailLink}
      </div>
    </Link>
  );
}

/* ═══════════════════════════════════════════════
   页面
   ═══════════════════════════════════════════════ */

export default function ModelCenter() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get("category") || "all";
  const validTab = TABS.some((t) => t.key === initialTab) ? initialTab : "all";

  const [models, setModels] = useState<ModelVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [activeTab, setActiveTab] = useState(validTab);

  const loadModels = async () => {
    try {
      const resp = await fetchModelsApi();
      if (resp.code === 200) setModels(resp.data.filter((m) => m.status === 1));
    } catch {
            // 接口不可用
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadModels();
  }, []);

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadModels();
  };

  const handleTabChange = (key: string) => {
    setActiveTab(key);
    setSearchParams(key === "all" ? {} : { category: key }, { replace: true });
  };

  const grouped = useMemo(() => {
    const map = new Map<ModelCategory, ModelVO[]>();
    CATEGORY_ORDER.forEach((c) => map.set(c, []));
    models.forEach((m) => {
      const cat = getCategoryByApi(m.api, m.type);
      map.get(cat)!.push(m);
    });
    // 准确率高的排在前面；0 表示未标注指标，放到最后
    map.forEach((list) =>
      list.sort((a, b) => (b.accuracy || 0) - (a.accuracy || 0))
    );
    return map;
  }, [models]);

  const counts = useMemo(() => {
    const c: Record<string, number> = { all: models.length };
    CATEGORY_ORDER.forEach((k) => (c[k] = grouped.get(k)?.length || 0));
    return c;
  }, [models, grouped]);

  const avgAccuracy = useMemo(() => {
    const scored = models.filter((m) => (m.accuracy || 0) > 0);
    if (scored.length === 0) return "—";
    return `${(
      scored.reduce((sum, m) => sum + (m.accuracy || 0), 0) / scored.length
    ).toFixed(1)}%`;
  }, [models]);

  const coveredCategories = CATEGORY_ORDER.filter(
    (c) => (grouped.get(c)?.length || 0) > 0
  );

  const visibleCategories =
    activeTab === "all" ? coveredCategories : [activeTab as ModelCategory];

  /* ── 加载态 ──────────────────────────────── */

  if (loading) {
    return (
      <div className="min-h-screen bg-[#F6F7FA]">
        <div className="mx-auto max-w-6xl px-6 py-10">
          <div className="h-56 animate-pulse rounded-[28px] bg-slate-900/90" />
          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="h-52 animate-pulse rounded-2xl bg-white" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  /* ── 主渲染 ──────────────────────────────── */

  return (
    <div className="min-h-screen overflow-x-hidden bg-[#F6F7FA] pb-20">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 py-8">
        {/* ═══ Hero ═══ */}
        <div className="relative overflow-hidden rounded-[28px] bg-[#0B1220] px-6 py-9 sm:px-10 sm:py-11">
          {/* 网格底纹 */}
          <div
            className="pointer-events-none absolute inset-0 opacity-[0.18]"
            style={{
              backgroundImage:
                "linear-gradient(rgba(255,255,255,0.18) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.18) 1px, transparent 1px)",
              backgroundSize: "44px 44px",
              maskImage: "radial-gradient(ellipse at 20% 0%, black 25%, transparent 70%)",
              WebkitMaskImage:
                "radial-gradient(ellipse at 20% 0%, black 25%, transparent 70%)",
            }}
          />
          <div className="pointer-events-none absolute -right-20 -top-24 h-72 w-72 rounded-full bg-indigo-500/25 blur-3xl" />
          <div className="pointer-events-none absolute -bottom-28 left-1/3 h-64 w-64 rounded-full bg-teal-400/20 blur-3xl" />

          <div className="relative grid gap-8 lg:grid-cols-[minmax(0,1fr)_13rem] lg:items-center">
            <div>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/5 px-2.5 py-1 text-[11px] text-white/70">
                <Sparkles className="h-3 w-3" />
                产品服务目录
              </span>

              <h1 className="mt-4 text-[26px] font-bold leading-snug tracking-tight text-white sm:text-[32px]">
                每一个模型，
                <br />
                都对应一件具体的事
              </h1>
              <p className="mt-3 max-w-lg text-[13px] leading-6 text-white/60">
                这里不是参数列表。每项服务都写清了「能识别什么、需要什么输入、会返回什么结果」，
                选定后可直接上传数据体验。
              </p>

              <div className="mt-7 flex flex-wrap items-center gap-x-8 gap-y-4">
                <StatChip icon={Layers} value={`${models.length}`} label="在线服务" />
                <StatChip
                  icon={Box}
                  value={`${coveredCategories.length}`}
                  label="服务领域"
                />
                <StatChip icon={Gauge} value={avgAccuracy} label="平均准确率" />
              </div>
            </div>

            {/* 领域分布 */}
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-1">
              {CATEGORY_ORDER.map((cat) => {
                const cMeta = CATEGORY_META[cat];
                const count = grouped.get(cat)?.length || 0;
                if (count === 0) return null;
                return (
                  <div
                    key={cat}
                    className="flex items-center justify-between gap-2 rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2"
                  >
                    <span className="flex items-center gap-2 text-[11.5px] text-white/70">
                      <span
                        className={`h-1.5 w-1.5 rounded-full bg-gradient-to-r ${cMeta.gradient}`}
                      />
                      {cMeta.label}
                    </span>
                    <span className="font-mono text-[11px] text-white/40">{count}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* ═══ 筛选栏 ═══ */}
        <div className="sticky top-0 z-10 -mx-2 mt-5 px-2 py-2 backdrop-blur">
          <div className="flex items-center justify-between gap-3">
            <div className="no-scrollbar flex min-w-0 flex-1 items-center gap-1.5 overflow-x-auto">
              {TABS.map((tab) => {
                const active = activeTab === tab.key;
                return (
                  <button
                    key={tab.key}
                    onClick={() => handleTabChange(tab.key)}
                    className={`shrink-0 rounded-full px-4 py-2 text-[13px] font-medium transition-all ${
                      active
                        ? "bg-gray-900 text-white shadow-sm"
                        : "border border-gray-200 bg-white text-gray-600 hover:border-gray-300 hover:text-gray-900"
                    }`}
                  >
                    {tab.label}
                    <span
                      className={`ml-1.5 text-[11px] ${
                        active ? "text-white/50" : "text-gray-400"
                      }`}
                    >
                      {counts[tab.key] ?? 0}
                    </span>
                  </button>
                );
              })}
            </div>
            <button
              onClick={handleRefresh}
              disabled={refreshing}
              className="hidden shrink-0 items-center gap-1.5 rounded-full border border-gray-200 bg-white px-3 py-2 text-[12px] text-gray-500 transition hover:text-gray-800 disabled:opacity-50 sm:flex"
              title="刷新服务列表"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? "animate-spin" : ""}`} />
              {refreshing ? "刷新中" : "刷新"}
            </button>
          </div>
        </div>

        {/* ═══ 分类分组 ═══ */}
        {visibleCategories.every((c) => (grouped.get(c)?.length || 0) === 0) ? (
          <div className="py-24 text-center text-gray-400">
            <Box className="mx-auto mb-3 h-10 w-10 opacity-40" />
            <p className="text-sm">该分类下暂无服务</p>
          </div>
        ) : (
          visibleCategories.map((cat) => {
            const meta = CATEGORY_META[cat];
            const list = grouped.get(cat) || [];
            if (list.length === 0) return null;
            const Icon = CATEGORY_ICON[cat];

            return (
              <section key={cat} className="mt-10">
                {/* 分组标题 */}
                <div className="mb-4 flex items-end justify-between gap-4 border-b border-gray-200/70 pb-3">
                  <div className="flex items-center gap-3">
                    <div
                      className={`flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br ${meta.gradient} shadow-sm`}
                    >
                      <Icon className="h-5 w-5 text-white" />
                    </div>
                    <div>
                      <h2 className="text-[15px] font-semibold text-gray-900">
                        {meta.label}
                      </h2>
                      <p className="mt-0.5 text-[12px] text-gray-500">{meta.description}</p>
                    </div>
                  </div>
                  <span className="hidden shrink-0 text-[12px] text-gray-400 sm:block">
                    {list.length} 项服务
                  </span>
                </div>

                {/* 卡片网格：首张为主推，跨两列 */}
                <div
                  className={`grid gap-4 ${
                    list.length === 1
                      ? "grid-cols-1"
                      : list.length === 2 || list.length === 4
                        ? "sm:grid-cols-2"
                        : "sm:grid-cols-2 lg:grid-cols-3"
                  }`}
                >
                  {list.map((m) => (
                    <ModelCard
                      key={m.id}
                      model={m}
                      variant={
                        list.length === 1
                          ? "full"
                          : list.length === 2 || list.length === 4
                            ? "panel"
                            : "card"
                      }
                    />
                  ))}
                </div>
              </section>
            );
          })
        )}

        {/* ═══ 底部引导 ═══ */}
        <div className="mt-12 overflow-hidden rounded-2xl border border-gray-200 bg-white">
          <div className="flex flex-col items-start justify-between gap-4 p-6 sm:flex-row sm:items-center">
            <div>
              <h3 className="text-[15px] font-semibold text-gray-900">
                不知道从哪个开始？
              </h3>
              <p className="mt-1 text-[13px] text-gray-500">
                先试「智能抠图」——上传一张照片就能看到透明背景结果，不需要准备数据。
              </p>
            </div>
            <Link
              to="/predict"
              className="inline-flex shrink-0 items-center gap-2 rounded-xl bg-gray-900 px-5 py-2.5 text-[13px] font-medium text-white transition hover:bg-gray-800"
            >
              进入在线体验
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>

        <p className="mt-6 text-center text-[11px] text-gray-400">
          共 {models.length} 项在线服务 · 覆盖医学影像、视觉理解、文本语言、数据预测四大领域
        </p>
      </div>
    </div>
  );
}
