import { useState } from "react";
import { AlertTriangle, ArrowRight, CheckCircle2, Clock, Loader2, Sparkles } from "lucide-react";
import { confirmTaskApi, type TaskResult } from "../../api/agent";
import { useAgentActions, useAgentState } from "../../agent/agentStore";
import AgentMessageList from "./AgentMessageList";
import { inputLabel } from "../../agent/labels";

const SUGGESTIONS = [
  "帮我推荐一个适合图像分类的模型",
  "平台现在有哪些可以处理图片的模型？",
  "我有一批中文影评，想区分好评差评",
  "上传图片提示文件太大怎么办？",
];

interface Props {
  compact?: boolean;
}

/**
 * 聊天主体。悬浮窗与全屏页共用同一个组件，只靠 compact 调整密度，
 * 因此两处的消息渲染、卡片、确认逻辑完全一致。
 */
export default function AgentChatContent({ compact = false }: Props) {
  const { items, loadingHistory, pendingTasks } = useAgentState();
  const { send, confirmTask } = useAgentActions();

  const empty = (
    <div className="flex h-full flex-col items-center justify-center px-4 text-center">
      <div
        className={`mb-3 flex items-center justify-center rounded-2xl bg-blue-50 text-blue-600 ${
          compact ? "h-11 w-11" : "h-14 w-14"
        }`}
      >
        <Sparkles className={compact ? "h-5 w-5" : "h-7 w-7"} />
      </div>
      <h2 className={`font-semibold text-gray-800 ${compact ? "text-sm" : "text-base"}`}>
        我可以帮你挑选和使用模型
      </h2>
      <p className={`mt-1.5 text-gray-500 ${compact ? "text-xs" : "text-sm"}`}>
        描述你的任务，我会查平台真实模型、给出推荐，并在你确认后调用模型完成推理。
      </p>
      <div className="mt-4 grid w-full max-w-lg gap-2">
        {(compact ? SUGGESTIONS.slice(0, 3) : SUGGESTIONS).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => void send(s)}
            className="rounded-xl border border-gray-200 px-3 py-2 text-left text-xs text-gray-600 transition hover:border-blue-300 hover:bg-blue-50/50 sm:text-sm"
          >
            {s}
          </button>
        ))}
      </div>
    </div>
  );

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <AgentMessageList
        items={items}
        compact={compact}
        loading={loadingHistory}
        onConfirm={(key, id) => void confirmTask(key, id)}
        empty={empty}
        header={<PendingTasksPanel tasks={pendingTasks} compact={compact} />}
      />
      {!compact && <PendingHint />}
    </div>
  );
}

/**
 * 刷新后恢复的「未确认任务」。
 *
 * 后端 /api/agent/tasks 只按用户返回、不带 sessionId，因此这里明确标注
 * 「来自之前的对话」，不把它伪装成当前会话的消息。
 */
function PendingTasksPanel({
  tasks,
  compact,
}: {
  tasks: {
    taskId: number;
    taskNo: string;
    modelName: string;
    inputType: string;
    inputSummary: string;
  }[];
  compact: boolean;
}) {
  const [busy, setBusy] = useState<Record<number, boolean>>({});
  const [done, setDone] = useState<Record<number, TaskResult>>({});
  const [failed, setFailed] = useState<Record<number, string>>({});
  const { refreshPending } = useAgentActions();

  // 已在本面板里确认过的任务，不再算「待确认」；结果单独展示
  const pendingOnly = tasks.filter((t) => !done[t.taskId]);
  const finished = Object.entries(done);

  if (tasks.length === 0) return null;

  const confirm = async (taskId: number) => {
    setBusy((b) => ({ ...b, [taskId]: true }));
    try {
      const result = await confirmTaskApi(taskId);
      setDone((d) => ({ ...d, [taskId]: result }));
      // 服务端状态已变成 SUCCESS，重拉一次，避免导航后仍从旧列表恢复出这条 PENDING
      void refreshPending();
    } catch (e) {
      setFailed((f) => ({ ...f, [taskId]: e instanceof Error ? e.message : "执行失败" }));
    } finally {
      setBusy((b) => ({ ...b, [taskId]: false }));
    }
  };

  return (
    <div className="mb-3 rounded-xl border border-amber-200 bg-amber-50/70 p-2.5">
      <div className="flex items-center gap-1.5 text-[11px] font-medium text-amber-700">
        <Clock className="h-3.5 w-3.5" />
        {pendingOnly.length > 0
          ? `有 ${pendingOnly.length} 个任务在等你确认（来自之前的对话，10 分钟内有效）`
          : "刚才的任务已执行完成"}
      </div>
      <div className="mt-2 space-y-2">
        {pendingOnly.map((t) => {
          const isFailed = failed[t.taskId];
          return (
            <div key={t.taskId} className="rounded-lg border border-amber-200/70 bg-white/80 p-2">
              <div className="text-[11px] text-gray-700">
                <span className="font-medium">{t.modelName}</span>
                <span className="text-gray-400"> · {inputLabel(t.inputType)}</span>
                <span className="ml-1 text-[10px] text-gray-400">{t.taskNo}</span>
              </div>
              <div className={`mt-0.5 text-[11px] text-gray-500 ${compact ? "line-clamp-2" : ""}`}>
                {t.inputSummary}
              </div>

              {isFailed ? (
                <p className="mt-1.5 text-[11px] text-red-600">{isFailed}</p>
              ) : (
                <button
                  type="button"
                  onClick={() => void confirm(t.taskId)}
                  disabled={busy[t.taskId]}
                  className="mt-1.5 inline-flex items-center gap-1 rounded-lg bg-blue-600 px-2.5 py-1 text-[11px] font-medium text-white transition hover:bg-blue-700 disabled:bg-gray-300"
                >
                  {busy[t.taskId] ? (
                    <>
                      <Loader2 className="h-3 w-3 animate-spin" /> 执行中
                    </>
                  ) : (
                    <>确认执行</>
                  )}
                </button>
              )}
            </div>
          );
        })}
      </div>

      {finished.length > 0 && (
        <div className="mt-2 space-y-1 border-t border-amber-200/60 pt-2">
          {finished.map(([id, r]) => (
            <div
              key={id}
              className="flex flex-wrap items-baseline gap-2 text-[11px] text-emerald-700"
            >
              <CheckCircle2 className="h-3 w-3" />
              已执行：{r.modelName ?? ""} → {r.topLabel ?? "-"}
              {r.confidence != null && (
                <span className="text-gray-500">置信度 {(r.confidence * 100).toFixed(2)}%</span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** 错误条：两个容器共用，避免各写一份样式 */
export function AgentErrorBar() {
  const { error } = useAgentState();
  const { clearError } = useAgentActions();
  if (!error) return null;
  return (
    <div className="mx-3 mb-2 flex items-start gap-2 rounded-lg bg-red-50 px-2.5 py-1.5 text-xs text-red-600">
      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
      <span className="min-w-0 flex-1 break-words">{error}</span>
      <button type="button" onClick={clearError} className="text-red-400 hover:text-red-600">
        ×
      </button>
    </div>
  );
}

/** 全屏页底部的能力提示，避免用户误以为 AI 会自动执行推理 */
function PendingHint() {
  return (
    <div className="flex items-center justify-center gap-1.5 border-t border-gray-100 px-4 py-1.5 text-[11px] text-gray-400">
      <AlertTriangle className="h-3 w-3" />
      推理任务由你点击确认后才执行，AI 不能自行触发
      <ArrowRight className="h-3 w-3" />
      结果会写入预测记录
    </div>
  );
}
