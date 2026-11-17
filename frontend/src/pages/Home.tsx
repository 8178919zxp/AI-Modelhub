import { Link } from "react-router-dom";
import type { LucideIcon } from "lucide-react";
import {
  ArrowRight,
  Scan,
  FileText,
  TrendingUp,
  Upload,
  Image as ImageIcon,
  Zap,
  Layers,
  Shield,
  Heart,
  Star,
  ChevronRight,
  Check,
  Clock,
  Building2,
  GraduationCap,
  Factory,
  Stethoscope,
} from "lucide-react";

/* ═══════════════════════════════════════════════
   数据
   ═══════════════════════════════════════════════ */

const ADVANTAGES: { icon: LucideIcon; title: string; desc: string }[] = [
  { icon: Zap, title: "秒级响应", desc: "单次推理通常在 1 秒内返回" },
  { icon: Shield, title: "数据可控", desc: "预测记录与图片存储在自有服务" },
  { icon: Heart, title: "开箱即用", desc: "无需部署，浏览器直接访问" },
  { icon: Layers, title: "四类场景", desc: "图像、文本、结构化数据全覆盖" },
];

const STEPS: { step: string; title: string; desc: string }[] = [
  { step: "01", title: "选择服务", desc: "按数据类型挑一个模型，页面会写清它能识别什么" },
  { step: "02", title: "上传或输入", desc: "图片、文本或几个数值字段，单次不超过 5MB" },
  { step: "03", title: "得到结果", desc: "返回类别、概率或数值，并附处理耗时" },
  { step: "04", title: "留存记录", desc: "登录后自动保存到个人中心，可随时回看" },
];

const CASES: { icon: LucideIcon; tag: string; title: string; desc: string; result: string }[] = [
  {
    icon: Building2,
    tag: "医疗健康",
    title: "影像辅助筛查",
    desc: "把 CT 切片按正常与三类病变分类，作为读片前的初筛参考。",
    result: "四分类 · 含概率",
  },
  {
    icon: Factory,
    tag: "工业制造",
    title: "表面缺陷检测",
    desc: "对产品图做目标检测与分类，替代人工目检的第一道工序。",
    result: "多目标 · 带位置",
  },
  {
    icon: GraduationCap,
    tag: "教育培训",
    title: "教学素材归类",
    desc: "批量判断文本与图片内容，整理课程素材与学员反馈。",
    result: "批量 · 可追溯",
  },
];

/* ═══════════════════════════════════════════════
   区块标题：左对齐 + 序号，统一全站层级
   ═══════════════════════════════════════════════ */

function SectionHead({
  index,
  label,
  title,
  desc,
  action,
}: {
  index: string;
  label: string;
  title: string;
  desc: string;
  action?: { to: string; text: string };
}) {
  return (
    <div className="mb-6 flex flex-col gap-4 sm:mb-8 sm:flex-row sm:items-end sm:justify-between">
      <div className="max-w-xl">
        <div className="flex items-center gap-2.5">
          <span className="font-mono text-[11px] tracking-[0.18em] text-gray-300">{index}</span>
          <span className="h-px w-6 bg-gray-200" />
          <span className="text-[11px] font-medium tracking-[0.14em] text-blue-600">{label}</span>
        </div>
        <h2 className="mt-3 text-balance text-2xl font-bold tracking-tight text-gray-900 sm:text-[30px]">
          {title}
        </h2>
        <p className="mt-2.5 text-[13px] leading-6 text-gray-500">{desc}</p>
      </div>
      {action && (
        <Link
          to={action.to}
          className="inline-flex shrink-0 items-center gap-1.5 text-[13px] font-medium text-gray-500 transition-colors hover:text-blue-600"
        >
          {action.text}
          <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════
   Hero 右侧：产品界面示意
   ═══════════════════════════════════════════════ */

function ProductScreenshot() {
  return (
    <div className="relative mx-auto w-full max-w-[500px]">
      <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-soft-lg">
        {/* 标题栏 */}
        <div className="flex items-center justify-between border-b border-gray-100 bg-gray-50/60 px-4 py-3">
          <div className="flex items-center gap-2">
            <div className="flex gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-red-400" />
              <span className="h-2.5 w-2.5 rounded-full bg-amber-400" />
              <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
            </div>
            <span className="ml-1.5 text-[11px] font-medium text-gray-500">图像分类</span>
          </div>
          <span className="text-[10px] text-gray-400">实时</span>
        </div>

        <div className="space-y-3 p-4">
          {/* 上传区 */}
          <div className="rounded-xl border border-dashed border-gray-200 px-4 py-5 text-center">
            <div className="mx-auto mb-2 flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50">
              <Upload className="h-5 w-5 text-blue-500" />
            </div>
            <div className="text-[12.5px] font-medium text-gray-700">上传图片文件</div>
            <div className="mt-0.5 text-[10.5px] text-gray-400">JPG / PNG，最大 5MB</div>
          </div>

          {/* 结果区 */}
          <div className="rounded-xl bg-gray-50/80 p-3.5">
            <div className="mb-2.5 flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
              <span className="text-[11px] font-semibold text-gray-700">分析结果</span>
              <span className="ml-auto font-mono text-[10px] text-gray-400">842ms</span>
            </div>
            <div className="space-y-2">
              {[
                { label: "工业零部件", conf: 96, active: true },
                { label: "金属零件", conf: 3, active: false },
                { label: "其他", conf: 1, active: false },
              ].map((r) => (
                <div key={r.label} className="space-y-1">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className={r.active ? "font-medium text-gray-800" : "text-gray-500"}>
                      {r.label}
                    </span>
                    <span className={r.active ? "font-mono font-semibold text-emerald-600" : "font-mono text-gray-400"}>
                      {r.conf}%
                    </span>
                  </div>
                  <div className="h-1 overflow-hidden rounded-full bg-gray-200">
                    <div
                      className={`h-full rounded-full ${r.active ? "bg-blue-600" : "bg-gray-400/60"}`}
                      style={{ width: `${r.conf}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* 角标：图片来源提示 */}
      <div className="mt-3 flex items-center justify-end gap-1.5 text-[10.5px] text-gray-400">
        <ImageIcon className="h-3 w-3" />
        界面示意，非真实截图
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════
   页面
   ═══════════════════════════════════════════════ */

export default function Home() {
  return (
    <div>
      {/* ═══ 01 Hero ═══ */}
      <section className="relative overflow-hidden bg-white">
        <div className="pointer-events-none absolute -right-40 -top-40 h-[520px] w-[520px] rounded-full bg-blue-50/50 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-40 -left-32 h-[420px] w-[420px] rounded-full bg-amber-50/40 blur-3xl" />

        <div className="relative mx-auto max-w-7xl px-6">
          <div className="grid items-center gap-10 py-10 sm:py-14 lg:grid-cols-2 lg:gap-14 lg:py-16">
            <div className="max-w-xl">
              <div className="flex items-center gap-2 text-[12px] font-medium text-gray-500">
                <span className="flex h-5 w-5 items-center justify-center rounded-md bg-gray-900 text-[10px] font-bold text-white">
                  AI
                </span>
                AI ModelHub
              </div>

              <h1 className="mt-5 text-[34px] font-bold leading-[1.12] tracking-tight text-gray-900 sm:text-[44px] lg:text-[50px]">
                让智能服务
                <br />
                更简单
              </h1>

              <p className="mt-4 max-w-md text-[14px] leading-7 text-gray-500">
                上传一张图片、粘贴一段文字，或填入几个数值，几秒内拿到可用的分析结果。
              </p>

              <div className="mt-7 flex flex-wrap items-center gap-2.5">
                <Link
                  to="/predict"
                  className="btn-ripple inline-flex items-center gap-2 rounded-xl bg-blue-600 px-5 py-3 text-[13.5px] font-semibold text-white shadow-brand transition-colors hover:bg-blue-700"
                >
                  开始使用
                  <ArrowRight className="h-4 w-4" />
                </Link>
                <Link
                  to="/models"
                  className="inline-flex items-center gap-1.5 rounded-xl px-4 py-3 text-[13.5px] font-medium text-gray-600 transition-colors hover:bg-gray-50 hover:text-gray-900"
                >
                  浏览服务
                  <ChevronRight className="h-4 w-4" />
                </Link>
              </div>

              <div className="mt-7 flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-gray-100 pt-5 text-[11.5px] text-gray-400">
                <span className="inline-flex items-center gap-1.5">
                  <Check className="h-3.5 w-3.5 text-emerald-500" />
                  无需安装
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <Clock className="h-3.5 w-3.5 text-gray-400" />
                  7×24 在线
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <Star className="h-3.5 w-3.5 text-amber-400" />
                  四类服务一站可用
                </span>
              </div>
            </div>

            <div className="hidden lg:block">
              <ProductScreenshot />
            </div>
          </div>
        </div>
      </section>

      {/* ═══ 02 产品服务 ═══ */}
      <section className="bg-[#F7F8FB] py-12 sm:py-16 lg:py-20">
        <div className="mx-auto max-w-6xl px-6">
          <SectionHead
            index="01"
            label="产品服务"
            title="四类服务，四种用途"
            desc="每一项都写明能识别什么、需要什么输入、返回什么结果，按数据类型选即可。"
            action={{ to: "/models", text: "浏览全部服务" }}
          />

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
            {/* 医学影像 — 深色主卡 */}
            <Link
              to="/models?category=medical"
              className="group relative overflow-hidden rounded-2xl bg-[#0B1220] p-6 sm:p-7 lg:col-span-7"
            >
              <div
                className="pointer-events-none absolute inset-0 opacity-[0.15]"
                style={{
                  backgroundImage:
                    "linear-gradient(rgba(255,255,255,0.2) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.2) 1px, transparent 1px)",
                  backgroundSize: "34px 34px",
                  maskImage: "radial-gradient(ellipse at 80% 10%, black 10%, transparent 65%)",
                  WebkitMaskImage:
                    "radial-gradient(ellipse at 80% 10%, black 10%, transparent 65%)",
                }}
              />
              <div className="pointer-events-none absolute -right-12 -top-14 h-48 w-48 rounded-full bg-teal-400/20 blur-3xl" />
              <svg
                aria-hidden="true"
                viewBox="0 0 200 200"
                className="pointer-events-none absolute -bottom-2 -right-6 hidden h-44 w-44 opacity-[0.2] sm:block"
                fill="none"
                stroke="white"
                strokeWidth="1"
              >
                <circle cx="100" cy="100" r="78" strokeDasharray="3 7" />
                <circle cx="100" cy="100" r="56" />
                <circle cx="100" cy="100" r="34" strokeDasharray="2 5" />
                <circle cx="100" cy="100" r="5" fill="white" stroke="none" />
                <path d="M100 14v18M100 168v18M14 100h18M168 100h18" strokeLinecap="round" />
              </svg>

              <div className="relative flex h-full flex-col">
                <div className="flex items-start justify-between">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-white/15 bg-white/10">
                    <Stethoscope className="h-5 w-5 text-teal-300" />
                  </div>
                  <span className="font-mono text-[10.5px] tracking-[0.18em] text-white/30">
                    01 / 医学影像
                  </span>
                </div>

                <h3 className="mt-6 text-xl font-semibold tracking-tight text-white">
                  医学影像分析
                </h3>
                <p className="mt-2 max-w-sm text-[13px] leading-6 text-white/55">
                  把一张肺部 CT 切片分成四类：正常、腺癌、大细胞癌、鳞状细胞癌，并给出每类概率。
                </p>

                <div className="mt-5 flex flex-wrap gap-1.5">
                  {["正常", "腺癌", "大细胞癌", "鳞状细胞癌"].map((c) => (
                    <span
                      key={c}
                      className="rounded-md border border-white/10 bg-white/[0.04] px-2 py-1 text-[11.5px] text-white/70"
                    >
                      {c}
                    </span>
                  ))}
                </div>

                <div className="mt-6 inline-flex items-center gap-1.5 text-[12px] font-medium text-teal-300">
                  查看影像服务
                  <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
                </div>
              </div>
            </Link>

            {/* 视觉理解 */}
            <Link
              to="/models?category=vision"
              className="group relative overflow-hidden rounded-2xl border border-indigo-100 bg-gradient-to-br from-indigo-50 via-white to-violet-50 p-6 sm:p-7 lg:col-span-5"
            >
              <div className="pointer-events-none absolute -bottom-10 -right-10 h-36 w-36 rotate-12 rounded-3xl border border-indigo-200/60" />
              <div className="pointer-events-none absolute -bottom-16 -right-2 h-36 w-36 rotate-6 rounded-3xl border border-violet-200/50" />

              <div className="relative flex h-full flex-col">
                <div className="flex items-start justify-between">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white shadow-sm ring-1 ring-indigo-100">
                    <Scan className="h-5 w-5 text-indigo-600" />
                  </div>
                  <span className="font-mono text-[10.5px] tracking-[0.18em] text-indigo-300">
                    02 / 视觉
                  </span>
                </div>

                <h3 className="mt-6 text-lg font-semibold tracking-tight text-gray-900">
                  图片内容检测
                </h3>
                <p className="mt-2 text-[13px] leading-6 text-gray-600">
                  识别图片里有什么、在什么位置，或直接把主体抠成透明底。
                </p>

                <div className="mt-5 flex flex-wrap gap-1.5">
                  {["图像分类", "目标检测", "智能抠图"].map((c) => (
                    <span
                      key={c}
                      className="rounded-md border border-indigo-100 bg-white/80 px-2 py-1 text-[11.5px] text-indigo-700"
                    >
                      {c}
                    </span>
                  ))}
                </div>

                <div className="mt-6 inline-flex items-center gap-1.5 text-[12px] font-medium text-indigo-600">
                  查看视觉服务
                  <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
                </div>
              </div>
            </Link>

            {/* 文本语言 */}
            <Link
              to="/models?category=text"
              className="group relative overflow-hidden rounded-2xl border border-amber-100 bg-gradient-to-br from-amber-50 via-orange-50/60 to-white p-6 sm:p-7 lg:col-span-5"
            >
              <span className="pointer-events-none absolute -right-1 -top-7 select-none font-serif text-[140px] leading-none text-amber-300/50">
                “
              </span>

              <div className="relative flex h-full flex-col">
                <div className="flex items-start justify-between">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white shadow-sm ring-1 ring-amber-100">
                    <FileText className="h-5 w-5 text-amber-600" />
                  </div>
                  <span className="font-mono text-[10.5px] tracking-[0.18em] text-amber-300">
                    03 / 文本
                  </span>
                </div>

                <h3 className="mt-6 text-lg font-semibold tracking-tight text-gray-900">
                  文字内容分析
                </h3>
                <p className="mt-2 text-[13px] leading-6 text-gray-600">
                  判断一段评论是正面还是负面，适合批量整理反馈与舆情。
                </p>

                <div className="mt-5 flex flex-wrap gap-1.5">
                  {[
                    { label: "正面", cls: "border-emerald-100 bg-emerald-50 text-emerald-700" },
                    { label: "负面", cls: "border-rose-100 bg-rose-50 text-rose-700" },
                    { label: "中性", cls: "border-gray-200 bg-gray-50 text-gray-600" },
                  ].map((c) => (
                    <span
                      key={c.label}
                      className={`rounded-md border px-2 py-1 text-[11.5px] ${c.cls}`}
                    >
                      {c.label}
                    </span>
                  ))}
                </div>

                <div className="mt-6 inline-flex items-center gap-1.5 text-[12px] font-medium text-amber-700">
                  查看文本服务
                  <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
                </div>
              </div>
            </Link>

            {/* 数据预测 */}
            <Link
              to="/models?category=data"
              className="group relative overflow-hidden rounded-2xl border border-gray-200 bg-white p-6 sm:p-7 lg:col-span-7"
            >
              <div className="pointer-events-none absolute inset-0 opacity-50">
                <div
                  className="h-full w-full"
                  style={{
                    backgroundImage: "radial-gradient(rgba(6,182,212,0.16) 1px, transparent 1px)",
                    backgroundSize: "18px 18px",
                    maskImage: "linear-gradient(to left, black, transparent 60%)",
                    WebkitMaskImage: "linear-gradient(to left, black, transparent 60%)",
                  }}
                />
              </div>

              <div className="relative flex h-full flex-col">
                <div className="flex items-start justify-between">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-50 ring-1 ring-cyan-100">
                    <TrendingUp className="h-5 w-5 text-cyan-600" />
                  </div>
                  <div className="flex h-12 items-end gap-1.5" aria-hidden="true">
                    {[38, 62, 46, 78, 58, 88, 70].map((h, i) => (
                      <span
                        key={i}
                        className="w-2 rounded-sm bg-gradient-to-t from-cyan-200/40 to-cyan-400/70"
                        style={{ height: `${h}%` }}
                      />
                    ))}
                  </div>
                </div>

                <h3 className="mt-6 text-xl font-semibold tracking-tight text-gray-900">
                  业务数据分析
                </h3>
                <p className="mt-2 max-w-md text-[13px] leading-6 text-gray-600">
                  填入几个结构化字段，直接得到分类结果或数值预测——品种、天气类型、收盘价。
                </p>

                <div className="mt-5 flex flex-wrap gap-1.5">
                  {["品种分类", "天气预测", "价格回归"].map((c) => (
                    <span
                      key={c}
                      className="rounded-md border border-cyan-100 bg-cyan-50/70 px-2 py-1 text-[11.5px] text-cyan-800"
                    >
                      {c}
                    </span>
                  ))}
                </div>

                <div className="mt-6 inline-flex items-center gap-1.5 text-[12px] font-medium text-cyan-700">
                  查看数据服务
                  <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
                </div>
              </div>
            </Link>
          </div>
        </div>
      </section>

      {/* ═══ 03 优势 + 流程（合并，压缩纵向空间） ═══ */}
      <section className="bg-white py-12 sm:py-16 lg:py-20">
        <div className="mx-auto max-w-6xl px-6">
          <SectionHead
            index="02"
            label="使用方式"
            title="不用部署，四步拿到结果"
            desc="从选择服务到留存记录，全流程都在浏览器里完成。"
          />

          <div className="grid gap-10 lg:grid-cols-12 lg:gap-12">
            {/* 左：四条优势 */}
            <div className="grid grid-cols-2 gap-x-6 gap-y-8 lg:col-span-5">
              {ADVANTAGES.map((a) => {
                const Icon = a.icon;
                return (
                  <div key={a.title}>
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50">
                      <Icon className="h-4.5 w-4.5 text-blue-600" />
                    </div>
                    <h3 className="mt-3 text-[13.5px] font-semibold text-gray-900">{a.title}</h3>
                    <p className="mt-1 text-[12px] leading-5 text-gray-500">{a.desc}</p>
                  </div>
                );
              })}
            </div>

            {/* 右：流程时间线 */}
            <div className="rounded-2xl border border-gray-200 bg-[#F7F8FB] p-6 sm:p-7 lg:col-span-7">
              <ol className="relative space-y-5 border-l border-gray-200 pl-7">
                {STEPS.map((s) => (
                  <li key={s.step} className="relative">
                    <span className="absolute -left-[35px] top-0 flex h-6 w-6 items-center justify-center rounded-full border border-gray-200 bg-white font-mono text-[10px] font-semibold text-gray-500">
                      {s.step}
                    </span>
                    <div className="text-[13.5px] font-medium text-gray-900">{s.title}</div>
                    <p className="mt-0.5 text-[12px] leading-5 text-gray-500">{s.desc}</p>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </div>
      </section>

      {/* ═══ 04 应用场景 ═══ */}
      <section className="bg-[#F7F8FB] py-12 sm:py-16 lg:py-20">
        <div className="mx-auto max-w-6xl px-6">
          <SectionHead
            index="03"
            label="应用场景"
            title="已经在这些场景里跑起来"
            desc="同一套服务，按数据类型落到不同业务环节。"
          />

          <div className="grid gap-4 sm:grid-cols-3">
            {CASES.map((c) => {
              const Icon = c.icon;
              return (
                <div
                  key={c.title}
                  className="group rounded-2xl border border-gray-200 bg-white p-5 transition-shadow hover:shadow-[0_16px_36px_-28px_rgba(15,23,42,0.4)]"
                >
                  <div className="flex items-center justify-between">
                    <span className="rounded-md bg-blue-50 px-2 py-0.5 text-[10.5px] font-medium text-blue-700">
                      {c.tag}
                    </span>
                    <Icon className="h-4 w-4 text-gray-300 transition-colors group-hover:text-gray-400" />
                  </div>
                  <h3 className="mt-3 text-[15px] font-semibold text-gray-900">{c.title}</h3>
                  <p className="mt-1.5 text-[12.5px] leading-6 text-gray-500">{c.desc}</p>
                  <div className="mt-4 flex items-center gap-1.5 border-t border-gray-100 pt-3 text-[11.5px] text-emerald-600">
                    <Check className="h-3.5 w-3.5" />
                    {c.result}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ═══ 05 CTA ═══ */}
      <section className="bg-white py-10 sm:py-14">
        <div className="mx-auto max-w-6xl px-6">
          <div className="relative overflow-hidden rounded-2xl bg-[#0B1220] px-6 py-9 sm:px-10 sm:py-10">
            <div
              className="pointer-events-none absolute inset-0 opacity-[0.12]"
              style={{
                backgroundImage:
                  "linear-gradient(rgba(255,255,255,0.25) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.25) 1px, transparent 1px)",
                backgroundSize: "40px 40px",
                maskImage: "radial-gradient(ellipse at 15% 50%, black 20%, transparent 70%)",
                WebkitMaskImage:
                  "radial-gradient(ellipse at 15% 50%, black 20%, transparent 70%)",
              }}
            />
            <div className="pointer-events-none absolute -right-10 -top-16 h-48 w-48 rounded-full bg-blue-500/25 blur-3xl" />

            <div className="relative flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-[22px] font-semibold tracking-tight text-white sm:text-[26px]">
                  打开浏览器，就能用
                </h2>
                <p className="mt-2 text-[13px] text-white/55">
                  无需安装与配置；登录后自动保存预测记录。
                </p>
              </div>
              <div className="flex shrink-0 flex-wrap items-center gap-2.5">
                <Link
                  to="/predict"
                  className="btn-ripple inline-flex items-center gap-2 rounded-xl bg-white px-5 py-2.5 text-[13px] font-semibold text-gray-900 transition-colors hover:bg-blue-50"
                >
                  开始使用
                  <ArrowRight className="h-4 w-4" />
                </Link>
                <Link
                  to="/models"
                  className="inline-flex items-center gap-1.5 rounded-xl border border-white/15 px-4 py-2.5 text-[13px] font-medium text-white/80 transition-colors hover:bg-white/10 hover:text-white"
                >
                  浏览服务
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
