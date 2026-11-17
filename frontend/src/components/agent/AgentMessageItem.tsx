import { useRef, useState, type ReactNode } from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeSanitize from "rehype-sanitize";
import {
  AlertTriangle,
  ArrowRight,
  Bot,
  Check,
  CheckCircle2,
  Copy,
  FileImage,
  Loader2,
  User,
  Wrench,
} from "lucide-react";
import { Link } from "react-router-dom";
import { inputLabel, toolLabel } from "../../agent/labels";
import type { AgentItem } from "../../agent/types";

/** 代码块：右上角复制按钮。react-markdown 把围栏代码包在 <pre> 里，这里替换整个 pre。 */
function CodeBlock({ children }: { children?: ReactNode }) {
  const preRef = useRef<HTMLPreElement>(null);
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    const raw = preRef.current?.innerText ?? "";
    if (!raw) return;
    try {
      await navigator.clipboard.writeText(raw);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      /* 非安全上下文（http）下 clipboard 不可用，静默失败 */
    }
  };

  return (
    <div className="group/code relative my-2">
      <pre
        ref={preRef}
        className="overflow-x-auto rounded-lg bg-slate-900 px-3 py-2.5 text-[12px] leading-relaxed text-slate-100"
      >
        {children}
      </pre>
      <button
        type="button"
        onClick={() => void copy()}
        className="absolute right-2 top-2 inline-flex items-center gap-1 rounded-md bg-white/10 px-1.5 py-1 text-[10px] text-slate-200 opacity-0 transition group-hover/code:opacity-100 hover:bg-white/20 focus:opacity-100"
        title="复制代码"
      >
        {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
        {copied ? "已复制" : "复制"}
      </button>
    </div>
  );
}

const markdownComponents: Components = {
  pre: ({ children }) => <CodeBlock>{children}</CodeBlock>,
  p: ({ children }) => <p className="my-1.5 leading-relaxed first:mt-0 last:mb-0">{children}</p>,
  ul: ({ children }) => <ul className="my-1.5 list-disc space-y-1 pl-5">{children}</ul>,
  ol: ({ children }) => <ol className="my-1.5 list-decimal space-y-1 pl-5">{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  h1: ({ children }) => (
    <h3 className="mb-1.5 mt-3 text-[15px] font-semibold first:mt-0">{children}</h3>
  ),
  h2: ({ children }) => (
    <h3 className="mb-1.5 mt-3 text-[15px] font-semibold first:mt-0">{children}</h3>
  ),
  h3: ({ children }) => (
    <h4 className="mb-1 mt-2.5 text-sm font-semibold first:mt-0">{children}</h4>
  ),
  a: (props) => (
    <a {...props} target="_blank" rel="noopener noreferrer" className="text-blue-600 underline" />
  ),
  code: ({ children, className }) =>
    className ? (
      <code className={className}>{children}</code>
    ) : (
      <code className="rounded bg-slate-100 px-1 py-0.5 text-[12px] text-rose-600">{children}</code>
    ),
  table: ({ children }) => (
    <div className="my-2 overflow-x-auto">
      <table className="w-full border-collapse text-[12px]">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border border-gray-200 bg-gray-50 px-2 py-1 text-left font-medium">
      {children}
    </th>
  ),
  td: ({ children }) => <td className="border border-gray-200 px-2 py-1 align-top">{children}</td>,
  blockquote: ({ children }) => (
    <blockquote className="my-2 border-l-2 border-blue-200 pl-3 text-gray-600">
      {children}
    </blockquote>
  ),
};

interface Props {
  item: AgentItem;
  compact?: boolean;
  onConfirm: (itemKey: string, taskId: number) => void;
}

export default function AgentMessageItem({ item, compact = false, onConfirm }: Props) {
  const indent = compact ? "ml-9" : "ml-11";

  if (item.kind === "text") {
    const isUser = item.role === "USER";
    return (
      <div className="flex gap-2.5">
        <div
          className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${
            isUser ? "bg-gray-100 text-gray-600" : "bg-blue-50 text-blue-600"
          }`}
        >
          {isUser ? <User className="h-3.5 w-3.5" /> : <Bot className="h-3.5 w-3.5" />}
        </div>
        <div className="min-w-0 flex-1">
          <div className="mb-1 text-[11px] text-gray-400">{isUser ? "我" : "ModelHub Copilot"}</div>
          {isUser ? (
            <div className="whitespace-pre-wrap break-words text-sm text-gray-800">
              {item.content}
            </div>
          ) : (
            <div className="break-words text-sm text-gray-800">
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                // 安全要点：剥离 <script>、onerror 等一切可执行内容
                rehypePlugins={[rehypeSanitize]}
                components={markdownComponents}
              >
                {item.content || (item.streaming ? "…" : "")}
              </ReactMarkdown>
              {item.streaming && (
                <span className="ml-0.5 inline-block h-3.5 w-1.5 translate-y-0.5 animate-pulse bg-blue-500" />
              )}
            </div>
          )}
        </div>
      </div>
    );
  }

  if (item.kind === "tools") {
    return (
      <div className={`${indent} flex flex-wrap gap-1.5`}>
        {item.tools.map((t, i) => (
          <span
            key={`${item.key}-${i}`}
            className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] ${
              t.restored
                ? "border-gray-200 bg-gray-50 text-gray-500"
                : t.success
                  ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                  : "border-red-200 bg-red-50 text-red-600"
            }`}
            title={
              t.summary ? `${t.summary}${t.elapsedMs ? ` · ${t.elapsedMs}ms` : ""}` : undefined
            }
          >
            <Wrench className="h-3 w-3" />
            {toolLabel(t.tool)}
            {!t.restored &&
              (t.success ? (
                <CheckCircle2 className="h-3 w-3" />
              ) : (
                <AlertTriangle className="h-3 w-3" />
              ))}
          </span>
        ))}
      </div>
    );
  }

  if (item.kind === "recommend") {
    return (
      <div className={`${indent} space-y-2`}>
        {item.models.map((m) => (
          <div
            key={`${item.key}-${m.id}`}
            className="rounded-xl border border-gray-200 bg-white p-3 transition hover:border-blue-300"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="text-sm font-semibold text-gray-800">{m.name}</span>
                  <span className="rounded bg-blue-50 px-1.5 py-0.5 text-[10px] text-blue-600">
                    {m.typeLabel}
                  </span>
                  <span className="text-[10px] text-gray-400">ID {m.id}</span>
                </div>
                {m.description && (
                  <p className="mt-1 line-clamp-2 text-xs text-gray-500">{m.description}</p>
                )}
                {m.reason && (
                  <p className="mt-1.5 text-xs text-gray-700">
                    <span className="text-blue-600">推荐理由：</span>
                    {m.reason}
                  </p>
                )}
                <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-gray-400">
                  {m.inputType && <span>输入：{inputLabel(m.inputType)}</span>}
                  {m.accuracy != null && <span>准确率：{m.accuracy}%</span>}
                  <span className={m.status === 1 ? "text-emerald-600" : "text-gray-400"}>
                    {m.status === 1 ? "在线" : "已下架"}
                  </span>
                </div>
              </div>
              <Link
                to={`/models/${m.id}`}
                className="inline-flex shrink-0 items-center gap-1 rounded-lg border border-gray-200 px-2 py-1 text-[11px] text-gray-600 transition hover:border-blue-300 hover:text-blue-600"
              >
                详情 <ArrowRight className="h-3 w-3" />
              </Link>
            </div>
          </div>
        ))}
      </div>
    );
  }

  // confirm / result
  const done = item.state === "DONE" && item.result;
  const failed = item.state === "FAILED";
  return (
    <div className={indent}>
      <div
        className={`rounded-xl border p-3 ${
          done
            ? "border-emerald-200 bg-emerald-50/50"
            : failed
              ? "border-red-200 bg-red-50/50"
              : "border-amber-200 bg-amber-50/60"
        }`}
      >
        <div className="flex flex-wrap items-center gap-2 text-xs font-medium text-gray-700">
          {done ? (
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
          ) : failed ? (
            <AlertTriangle className="h-4 w-4 text-red-500" />
          ) : (
            <AlertTriangle className="h-4 w-4 text-amber-600" />
          )}
          {done ? "推理完成" : failed ? "执行失败" : "待确认执行"}
          <span className="text-[10px] font-normal text-gray-400">{item.taskNo}</span>
        </div>

        <div className="mt-2 space-y-1 text-xs text-gray-600">
          <div className="flex gap-1.5">
            <span className="shrink-0 text-gray-400">模型</span>
            <span className="min-w-0 break-words">{item.modelName}</span>
          </div>
          <div className="flex gap-1.5">
            <span className="shrink-0 text-gray-400">输入</span>
            <span className="min-w-0 break-words">{item.inputSummary}</span>
          </div>
          <div className="flex gap-1.5">
            <span className="shrink-0 text-gray-400">类型</span>
            <span>{inputLabel(item.inputType)}</span>
          </div>
        </div>

        {!done && !failed && (
          <div className="mt-2.5 flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => onConfirm(item.key, item.taskId)}
              disabled={item.state === "RUNNING"}
              className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-blue-700 disabled:bg-gray-300"
            >
              {item.state === "RUNNING" ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" /> 正在调用模型…
                </>
              ) : (
                "确认执行"
              )}
            </button>
            <span className="text-[11px] text-gray-400">确认后才会真正调用模型；10 分钟内有效</span>
          </div>
        )}

        {failed && (
          <p className="mt-2 rounded-lg bg-red-50 px-2.5 py-1.5 text-[11px] text-red-600">
            {item.error}
          </p>
        )}

        {done && item.result && (
          <div className="mt-2.5 rounded-lg bg-white/70 p-2.5">
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span className="text-lg font-semibold text-gray-900">
                {item.result.topLabel ?? "-"}
              </span>
              {item.result.confidence != null && (
                <span className="text-xs text-gray-500">
                  置信度 {(item.result.confidence * 100).toFixed(2)}%
                </span>
              )}
              {item.result.durationMs != null && (
                <span className="text-xs text-gray-400">{item.result.durationMs} ms</span>
              )}
              {item.result.recordId != null && (
                <Link to="/profile" className="text-[11px] text-blue-600 hover:underline">
                  查看记录 #{item.result.recordId}
                </Link>
              )}
            </div>
            {item.result.explanation && (
              <p className="mt-1.5 text-xs leading-relaxed text-gray-600">
                {item.result.explanation}
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

/** 附件条：上传中 / 已上传共用 */
export function AttachmentBar({
  name,
  size,
  uploading,
  onRemove,
}: {
  name: string;
  size?: number;
  uploading?: boolean;
  onRemove: () => void;
}) {
  return (
    <div className="mb-2 flex items-center gap-2 rounded-lg bg-blue-50 px-2.5 py-1.5 text-xs text-blue-700">
      {uploading ? (
        <Loader2 className="h-3.5 w-3.5 animate-spin" />
      ) : (
        <FileImage className="h-3.5 w-3.5" />
      )}
      <span className="min-w-0 flex-1 truncate">
        {name}
        {size != null ? `（${Math.max(1, Math.round(size / 1024))} KB）` : ""}
      </span>
      <button type="button" onClick={onRemove} className="text-blue-400 hover:text-blue-600">
        ×
      </button>
    </div>
  );
}
