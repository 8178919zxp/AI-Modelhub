import { Link } from "react-router-dom";
import {
  ArrowRight,
  Award,
  Boxes,
  BookOpen,
  Clock,
  Code2,
  Database,
  GitBranch,
  Server,
  Sparkles,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

/* ═══════════════════════════════════════════════
   数据（以仓库实际代码与依赖版本为准）
   ═══════════════════════════════════════════════ */

const FACTS = [
  { label: "项目定位", value: "AI 模型体验与推理平台" },
  { label: "技术栈", value: "React + Spring Boot + FastAPI" },
  { label: "核心亮点", value: "模型管理 / 在线推理 / Agent 助手" },
];

const STACK: { name: string; version: string }[] = [
  { name: "Spring Boot", version: "3.5.16" },
  { name: "MyBatis", version: "3.0.5" },
  { name: "Spring AI", version: "1.1.8" },
  { name: "React + Vite", version: "19 / 7" },
];

const MODULES: { icon: LucideIcon; title: string; desc: string }[] = [
  {
    icon: Boxes,
    title: "模型中心",
    desc: "模型元数据的登记、上下架与分类检索，覆盖图像、文本、表格三类任务。",
  },
  {
    icon: Sparkles,
    title: "在线推理",
    desc: "统一入口按 model_api 路由到具体模型，输入校验、限流与结果落库在同一链路完成。",
  },
  {
    icon: Server,
    title: "ModelHub Copilot",
    desc: "基于 Spring AI + DeepSeek 的对话式助手，工具调用查真实数据，推理需用户确认后执行。",
  },
  {
    icon: Database,
    title: "管理与统计",
    desc: "后台模型与用户管理、操作审计，以及平台维度的调用与用户统计。",
  },
];

const META = [
  {
    icon: Code2,
    label: "源码",
    value: "后端 / 前端 / 推理服务同仓管理",
  },
  {
    icon: BookOpen,
    label: "文档",
    value: "docs/ 下有架构、接口与 Agent 设计说明",
  },
  {
    icon: Clock,
    label: "启动方式",
    value: "Docker Compose 一键部署，或本地分服务启动",
  },
];

/* ═══════════════════════════════════════════════
   小节标题：与首页共用同一套层级
   ═══════════════════════════════════════════════ */

function SectionHead({
  index,
  label,
  title,
  desc,
}: {
  index: string;
  label: string;
  title: string;
  desc?: string;
}) {
  return (
    <div className="mb-6 max-w-xl sm:mb-8">
      <div className="flex items-center gap-2.5">
        <span className="font-mono text-[11px] tracking-[0.18em] text-gray-300">{index}</span>
        <span className="h-px w-6 bg-gray-200" />
        <span className="text-[11px] font-medium tracking-[0.14em] text-blue-600">{label}</span>
      </div>
      <h2 className="mt-3 text-2xl font-bold tracking-tight text-gray-900 sm:text-[30px]">
        {title}
      </h2>
      {desc && <p className="mt-2.5 text-[13px] leading-6 text-gray-500">{desc}</p>}
    </div>
  );
}

/* ═══════════════════════════════════════════════
   页面
   ═══════════════════════════════════════════════ */

export default function About() {
  return (
    <div className="bg-white">
      {/* ═══ Hero ═══ */}
      <section className="relative overflow-hidden bg-[#0B1220]">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.14]"
          style={{
            backgroundImage:
              "linear-gradient(rgba(255,255,255,0.22) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.22) 1px, transparent 1px)",
            backgroundSize: "44px 44px",
            maskImage: "radial-gradient(ellipse at 25% 0%, black 20%, transparent 72%)",
            WebkitMaskImage:
              "radial-gradient(ellipse at 25% 0%, black 20%, transparent 72%)",
          }}
        />
        <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-blue-500/25 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 left-1/4 h-64 w-64 rounded-full bg-teal-400/15 blur-3xl" />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute right-12 top-1/2 hidden -translate-y-1/2 select-none text-[160px] font-bold leading-none text-white/[0.035] lg:block"
        >
          AI
        </div>

        <div className="relative mx-auto max-w-6xl px-6 pb-8 pt-10 sm:pb-14 sm:pt-16">
          <div className="flex items-center gap-2.5">
            <span className="flex h-5 w-5 items-center justify-center rounded-md bg-white/10 text-[10px] font-bold text-white/90">
              AI
            </span>
            <span className="text-[12px] font-medium tracking-[0.14em] text-white/60">
              关于项目
            </span>
            <span className="h-px w-8 bg-white/20" />
            <span className="font-mono text-[11px] tracking-[0.18em] text-white/30">ABOUT</span>
          </div>

          <h1 className="mt-5 text-[32px] font-bold leading-[1.15] tracking-tight text-white sm:text-[44px]">
            AI ModelHub
            <br />
            模型体验与推理平台
          </h1>

          <p className="mt-4 max-w-xl text-[13.5px] leading-7 text-white/55">
            一个把「模型管理 → 在线推理 → 智能助手」串成完整链路的前后端分离项目，
            用于演示机器学习模型如何以服务形式被业务系统调用。
          </p>

          {/* 事实行：只列可核对的信息，不用装饰性数据 */}
          <dl className="mt-7 grid grid-cols-2 gap-y-4 border-t border-white/10 pt-6 sm:mt-8 sm:grid-cols-3 sm:gap-x-8">
            {FACTS.map((f, i) => (
              <div key={f.label} className={i === FACTS.length - 1 ? "col-span-2 sm:col-span-1" : ""}>
                <dt className="text-[11px] tracking-wide text-white/35">{f.label}</dt>
                <dd className="mt-1 text-[13px] font-medium text-white/85">{f.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* ═══ 01 项目简介 ═══ */}
      <section className="py-12 sm:py-16 lg:py-20">
        <div className="mx-auto max-w-6xl px-6">
          <div className="grid gap-7 lg:grid-cols-12 lg:gap-14">
            <div className="lg:col-span-4">
              <SectionHead
                index="01"
                label="项目简介"
                title="把模型做成能用的服务"
                desc="不是跑一个 demo 脚本，而是走完一次真实请求的完整链路。"
              />
              <div className="hidden lg:block">
                <div className="h-px w-full bg-gray-200" />
                <div className="mt-5 flex items-baseline gap-2">
                  <span className="text-[34px] font-bold leading-none tracking-tight text-gray-900">
                    3
                  </span>
                  <span className="text-[12px] text-gray-500">个独立服务</span>
                </div>
              </div>
            </div>

            <div className="lg:col-span-8">
              <div className="space-y-4 text-[13.5px] leading-7 text-gray-600">
                <p>
                  项目由三个进程组成：React 前端负责界面与交互，Spring Boot 后端承担鉴权、
                  业务编排与数据持久化，Python 推理服务负责真正的模型前向计算。
                </p>
                <p>
                  三者之间只通过 HTTP 通信，后端把「调用哪个模型」收敛到一个路由服务里，
                  新增模型只需要在后台登记 <span className="font-medium text-gray-900">model_api</span>，
                  不需要改动 Controller。
                </p>
                <p>
                  在平台之上叠加了 ModelHub Copilot：用自然语言查询模型、挑选模型，
                  并由用户在确认卡片上明确同意后才执行推理，避免大模型直接触发写操作。
                </p>
              </div>

              <div className="mt-7 flex items-center gap-3 rounded-xl border border-gray-200 bg-[#F7F8FB] px-4 py-3">
                <Award className="h-4 w-4 shrink-0 text-blue-600" />
                <p className="text-[12px] leading-6 text-gray-600">
                  平台展示的图像、文本与结构化数据模型均为公开可用的通用模型，
                  推理结果仅用于技术演示。
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ═══ 02 技术栈 ═══ */}
      <section className="bg-[#F7F8FB] py-12 sm:py-16 lg:py-20">
        <div className="mx-auto max-w-6xl px-6">
          <SectionHead
            index="02"
            label="技术栈"
            title="选型与版本"
            desc="版本号与仓库 pom.xml / package.json 保持一致。"
          />

          <div className="grid gap-3 sm:grid-cols-2">
            {STACK.map((c) => (
              <div
                key={c.name}
                className="flex items-center gap-3.5 rounded-xl border border-gray-200 bg-white px-5 py-4 transition-colors hover:border-blue-200"
              >
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gray-50">
                  <Award className="h-4 w-4 text-gray-400" />
                </div>
                <div className="min-w-0 flex-1 truncate text-[13.5px] font-medium text-gray-900">
                  {c.name}
                </div>
                <span className="shrink-0 rounded-md border border-blue-100 bg-blue-50 px-2 py-1 text-[11px] font-medium text-blue-700">
                  {c.version}
                </span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ═══ 03 功能模块 ═══ */}
      <section className="py-12 sm:py-16 lg:py-20">
        <div className="mx-auto max-w-6xl px-6">
          <SectionHead
            index="03"
            label="功能模块"
            title="四块能力，一条链路"
            desc="从模型登记、调用到结果沉淀，可以单独使用，也可以串起来用。"
          />

          <div className="grid gap-x-12 sm:grid-cols-2">
            {MODULES.map((b, i) => {
              const Icon = b.icon;
              return (
                <div
                  key={b.title}
                  className="group flex gap-4 border-t border-gray-200 py-5 transition-colors hover:border-blue-300"
                >
                  <span className="mt-0.5 font-mono text-[11px] tracking-wider text-gray-300">
                    0{i + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <Icon className="h-4 w-4 text-gray-400 transition-colors group-hover:text-blue-600" />
                      <h3 className="text-[14.5px] font-semibold text-gray-900">{b.title}</h3>
                    </div>
                    <p className="mt-1.5 text-[12.5px] leading-6 text-gray-500">{b.desc}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ═══ 04 如何获取 ═══ */}
      <section className="bg-[#F7F8FB] py-12 sm:py-16 lg:py-20">
        <div className="mx-auto max-w-6xl px-6">
          <SectionHead
            index="04"
            label="开始使用"
            title="源码与文档"
            desc="项目为开源代码，按 README 的步骤即可在本地或容器中启动。"
          />

          <div className="grid gap-4 lg:grid-cols-12">
            {/* 信息卡 */}
            <div className="rounded-2xl border border-gray-200 bg-white p-6 sm:p-7 lg:col-span-7">
              <dl className="divide-y divide-gray-100">
                {META.map((c) => {
                  const Icon = c.icon;
                  return (
                    <div key={c.label} className="flex gap-4 py-4 first:pt-0 last:pb-0">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gray-50">
                        <Icon className="h-4 w-4 text-gray-500" />
                      </div>
                      <div className="min-w-0">
                        <dt className="text-[11px] tracking-wide text-gray-400">{c.label}</dt>
                        <dd className="mt-0.5 whitespace-pre-line text-[13px] leading-6 text-gray-800">
                          {c.value}
                        </dd>
                      </div>
                    </div>
                  );
                })}
              </dl>

              <div className="mt-6 flex flex-wrap items-center gap-2.5 border-t border-gray-100 pt-5">
                <Link
                  to="/models"
                  className="inline-flex items-center gap-1.5 rounded-xl bg-gray-900 px-4 py-2.5 text-[12.5px] font-medium text-white transition-colors hover:bg-gray-800"
                >
                  看看有哪些模型
                  <ArrowRight className="h-3.5 w-3.5" />
                </Link>
                <Link
                  to="/predict"
                  className="inline-flex items-center gap-1.5 rounded-xl px-4 py-2.5 text-[12.5px] font-medium text-gray-600 transition-colors hover:bg-gray-50 hover:text-gray-900"
                >
                  直接在线体验
                </Link>
              </div>
            </div>

            {/* 架构示意卡：抽象图形，不使用真实地图 */}
            <div className="relative overflow-hidden rounded-2xl bg-[#0B1220] p-6 sm:p-7 lg:col-span-5">
              <div
                className="pointer-events-none absolute inset-0 opacity-[0.16]"
                style={{
                  backgroundImage:
                    "linear-gradient(rgba(255,255,255,0.22) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.22) 1px, transparent 1px)",
                  backgroundSize: "32px 32px",
                  maskImage: "radial-gradient(circle at 50% 40%, black 10%, transparent 70%)",
                  WebkitMaskImage:
                    "radial-gradient(circle at 50% 40%, black 10%, transparent 70%)",
                }}
              />
              <svg
                aria-hidden="true"
                viewBox="0 0 200 200"
                className="pointer-events-none absolute left-1/2 top-1/2 h-52 w-52 -translate-x-1/2 -translate-y-1/2 opacity-30"
                fill="none"
                stroke="white"
                strokeWidth="0.8"
              >
                <circle cx="100" cy="100" r="86" strokeDasharray="2 6" />
                <circle cx="100" cy="100" r="62" />
                <circle cx="100" cy="100" r="38" strokeDasharray="2 5" />
                <path d="M100 20v160M20 100h160" strokeDasharray="1 8" />
              </svg>

              <div className="relative flex h-full flex-col justify-between gap-16">
                <div className="flex items-center gap-2 text-[11px] tracking-[0.14em] text-white/40">
                  <GitBranch className="h-3.5 w-3.5" />
                  OPEN SOURCE
                </div>

                <div>
                  <div className="text-[15px] font-semibold text-white">三服务 · 一条推理链</div>
                  <p className="mt-1.5 text-[12px] leading-6 text-white/50">
                    前端 → 后端 → 推理服务
                    <br />
                    部署与接口说明见 README
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
