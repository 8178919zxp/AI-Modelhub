import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  type AgentAttachment,
  type AgentMessage,
  type AgentSession,
  type AgentTask,
  type AgentSseEvent,
  type ModelRecommend,
  confirmTaskApi,
  createSessionApi,
  deleteSessionApi,
  listMessagesApi,
  listSessionsApi,
  listTasksApi,
  streamChat,
  uploadAttachmentApi,
} from "../../api/agent";
import type { AgentActions, AgentItem, AgentState, AgentStatus, ToolChip } from "../../agent/types";

/**
 * Agent 对话引擎 —— 整个应用里只有这一份。
 *
 * 为什么要有它：悬浮窗和全屏页是两套 UI，但绝不能是两套状态。
 * 如果各自 useState + 各自开 SSE，会出现「切到全屏后消息不见了」「两个窗口各发一次请求」
 * 这类问题。所以：
 *   · 状态与请求集中在这里，由 AgentProvider 挂在路由之上（Layout 内），路由切换不会卸载它；
 *   · UI 只通过 context 读写，自己不持有消息数组、也不自己发请求。
 *
 * 生命周期约定（三件事必须分开）：
 *   关闭窗口  = 只是隐藏 UI，SSE 继续跑，回复不会丢
 *   中断 SSE  = 只有组件卸载（退出登录 / 关闭页面）时才 abort
 *   取消任务  = 前端不做，任务状态只由后端状态机推进
 */

let seq = 0;
const nextKey = () => `k-${Date.now()}-${seq++}`;

const errText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

/**
 * 历史消息 → 时间线。
 * TOOL 记录不渲染成气泡，而是聚成一组芯片；因为没有 toolPayload，
 * 无法判断成功与否，所以标 restored，UI 上按「历史记录」中性样式展示。
 */
function toItems(messages: AgentMessage[]): AgentItem[] {
  const items: AgentItem[] = [];
  let tools: ToolChip[] = [];
  const flush = (keyHint: string) => {
    if (tools.length === 0) return;
    items.push({ key: `db-tools-${keyHint}`, kind: "tools", tools });
    tools = [];
  };
  for (const m of messages) {
    if (m.role === "TOOL") {
      tools.push({
        tool: m.toolName ?? "tool",
        summary: m.content ?? "",
        success: true,
        elapsedMs: 0,
        restored: true,
      });
      continue;
    }
    flush(String(m.id));
    items.push({ key: `db-${m.id}`, kind: "text", role: m.role, content: m.content ?? "" });
  }
  flush("tail");
  return items;
}

export function useAgentChat(): { state: AgentState; actions: AgentActions } {
  const [sessions, setSessions] = useState<AgentSession[]>([]);
  const [activeId, setActiveIdState] = useState<number | null>(null);
  const [items, setItems] = useState<AgentItem[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [attachment, setAttachment] = useState<AgentAttachment | null>(null);
  const [uploading, setUploading] = useState(false);
  const [pendingTasks, setPendingTasks] = useState<AgentTask[]>([]);
  const [status, setStatus] = useState<AgentStatus>("idle");
  const [open, setOpen] = useState(false);
  const [minimized, setMinimized] = useState(false);
  const [hasUnread, setHasUnread] = useState(false);
  const [sessionsLoaded, setSessionsLoaded] = useState(false);

  const activeIdRef = useRef<number | null>(null);
  const sendingRef = useRef(false);
  const abortRef = useRef<AbortController | null>(null);
  const openRef = useRef(false);
  const draftRef = useRef("");
  const loadSeqRef = useRef(0);

  const setActive = useCallback((id: number | null) => {
    activeIdRef.current = id;
    setActiveIdState(id);
  }, []);

  const refreshSessions = useCallback(async () => {
    try {
      const list = await listSessionsApi();
      setSessions(list);
      return list;
    } catch (e) {
      setError(errText(e, "加载会话失败"));
      return [] as AgentSession[];
    } finally {
      setSessionsLoaded(true);
    }
  }, []);

  const refreshPending = useCallback(async () => {
    try {
      const list = await listTasksApi(20);
      setPendingTasks(list.filter((t) => t.status === "PENDING"));
    } catch {
      /* 待确认任务只是辅助信息，失败不打断对话 */
    }
  }, []);

  const loadMessages = useCallback(async (id: number) => {
    const ticket = ++loadSeqRef.current;
    setLoadingHistory(true);
    try {
      const messages = await listMessagesApi(id);
      // 期间用户可能又切了会话，丢弃过期响应
      if (ticket !== loadSeqRef.current || activeIdRef.current !== id) return;
      setItems(toItems(messages));
    } catch (e) {
      if (ticket === loadSeqRef.current) setError(errText(e, "加载消息失败"));
    } finally {
      if (ticket === loadSeqRef.current) setLoadingHistory(false);
    }
  }, []);

  // 首次进入：恢复会话列表 + 最近一个会话的历史 + 未确认任务
  useEffect(() => {
    if (sessionsLoaded) return;
    void (async () => {
      const list = await refreshSessions();
      if (list.length > 0) {
        setActive(list[0].id);
        await loadMessages(list[0].id);
      }
      await refreshPending();
    })();
  }, [sessionsLoaded, refreshSessions, loadMessages, refreshPending, setActive]);

  // 卸载（退出登录 / 关闭页面）时中断在途 SSE，避免请求泄漏
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
      abortRef.current = null;
    };
  }, []);

  const openWindow = useCallback(() => {
    openRef.current = true;
    setOpen(true);
    setMinimized(false);
    setHasUnread(false);
  }, []);

  /** 只隐藏 UI：正在生成的回复会继续，重新打开还能看到 */
  const closeWindow = useCallback(() => {
    openRef.current = false;
    setOpen(false);
  }, []);

  const minimizeWindow = useCallback(() => setMinimized(true), []);
  const restoreWindow = useCallback(() => {
    setMinimized(false);
    setHasUnread(false);
  }, []);

  const newChat = useCallback(async () => {
    setError(null);
    try {
      const session = await createSessionApi();
      setSessions((prev) => [session, ...prev]);
      setActive(session.id);
      setItems([]);
      setAttachment(null);
    } catch (e) {
      setError(errText(e, "创建会话失败"));
    }
  }, [setActive]);

  const selectSession = useCallback(
    (id: number) => {
      if (sendingRef.current || id === activeIdRef.current) return;
      setError(null);
      setActive(id);
      setAttachment(null);
      void loadMessages(id);
    },
    [loadMessages, setActive],
  );

  const deleteSession = useCallback(
    async (id: number) => {
      if (!window.confirm("确定删除这个会话吗？删除后不可恢复。")) return;
      try {
        await deleteSessionApi(id);
        setSessions((prev) => prev.filter((s) => s.id !== id));
        if (activeIdRef.current === id) {
          setActive(null);
          setItems([]);
        }
      } catch (e) {
        setError(errText(e, "删除会话失败"));
      }
    },
    [setActive],
  );

  const uploadFile = useCallback(async (file: File | undefined) => {
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      const uploaded = await uploadAttachmentApi(file, activeIdRef.current ?? undefined);
      setAttachment(uploaded);
    } catch (e) {
      setError(errText(e, "上传失败"));
    } finally {
      setUploading(false);
    }
  }, []);

  const send = useCallback(
    async (text: string) => {
      const message = text.trim();
      if (!message || sendingRef.current) return;

      sendingRef.current = true;
      setSending(true);
      setStatus("waiting");
      setError(null);
      setHasUnread(false);

      const att = attachment;
      // 附件 id 拼进正文：模型只有看到 id 才能用 prepareInference 引用它
      const attachmentHint = att
        ? `\n\n[已上传附件] attachmentId=${att.id}，文件名=${att.filename}，大小=${Math.max(
            1,
            Math.round(att.size / 1024),
          )}KB`
        : "";

      const assistantKey = nextKey();
      setItems((prev) => [
        ...prev,
        { key: nextKey(), kind: "text", role: "USER", content: message },
        { key: assistantKey, kind: "text", role: "ASSISTANT", content: "", streaming: true },
      ]);
      if (att) setAttachment(null);

      const controller = new AbortController();
      abortRef.current = controller;

      const collectedTools: ToolChip[] = [];
      let recommend: ModelRecommend[] | null = null;
      let pending: Extract<AgentItem, { kind: "confirm" }> | null = null;
      let gotToken = false;

      const flushTrailing = () => {
        setItems((prev) => {
          const next = [...prev];
          if (collectedTools.length > 0) {
            next.push({ key: nextKey(), kind: "tools", tools: [...collectedTools] });
          }
          if (recommend) next.push({ key: nextKey(), kind: "recommend", models: recommend });
          if (pending) next.push({ ...pending });
          return next;
        });
      };

      try {
        await streamChat(
          { sessionId: activeIdRef.current ?? undefined, message: message + attachmentHint },
          (event: AgentSseEvent) => {
            switch (event.type) {
              case "session": {
                const data = event.data as { sessionId: number; title: string };
                setActive(data.sessionId);
                setSessions((prev) =>
                  prev.some((s) => s.id === data.sessionId)
                    ? prev
                    : [
                        {
                          id: data.sessionId,
                          title: data.title,
                          messageCount: 0,
                          lastMessageAt: new Date().toISOString(),
                        },
                        ...prev,
                      ],
                );
                break;
              }
              case "token": {
                const data = event.data as { text: string };
                if (!gotToken) {
                  gotToken = true;
                  setStatus("streaming");
                }
                setItems((prev) =>
                  prev.map((it) =>
                    it.key === assistantKey && it.kind === "text"
                      ? { ...it, content: it.content + data.text }
                      : it,
                  ),
                );
                break;
              }
              case "tool_result": {
                const data = event.data as {
                  tool: string;
                  success: boolean;
                  elapsedMs: number;
                  summary: string;
                };
                collectedTools.push(data);
                break;
              }
              case "recommend": {
                const data = event.data as { models: ModelRecommend[] };
                recommend = data.models;
                break;
              }
              case "confirm_required": {
                const data = event.data as {
                  taskId: number;
                  taskNo: string;
                  modelName: string;
                  inputType: string;
                  inputSummary: string;
                };
                pending = {
                  key: nextKey(),
                  kind: "confirm",
                  taskId: data.taskId,
                  taskNo: data.taskNo,
                  modelName: data.modelName,
                  inputType: data.inputType,
                  inputSummary: data.inputSummary,
                  state: "PENDING",
                };
                break;
              }
              case "done": {
                setItems((prev) =>
                  prev.map((it) =>
                    it.key === assistantKey && it.kind === "text"
                      ? { ...it, streaming: false }
                      : it,
                  ),
                );
                flushTrailing();
                void refreshSessions();
                void refreshPending();
                if (!openRef.current) setHasUnread(true);
                break;
              }
              case "error": {
                const data = event.data as { message: string };
                setError(data.message);
                setItems((prev) =>
                  prev.map((it) =>
                    it.key === assistantKey && it.kind === "text"
                      ? { ...it, streaming: false, content: it.content || "（本轮没有返回内容）" }
                      : it,
                  ),
                );
                break;
              }
              default:
                break;
            }
          },
          controller.signal,
        );
      } catch (e) {
        if ((e as Error).name !== "AbortError") setError(errText(e, "发送失败"));
        setItems((prev) =>
          prev.map((it) =>
            it.key === assistantKey && it.kind === "text" ? { ...it, streaming: false } : it,
          ),
        );
      } finally {
        abortRef.current = null;
        sendingRef.current = false;
        setSending(false);
        setStatus("idle");
      }
    },
    [attachment, refreshPending, refreshSessions, setActive],
  );

  const confirmTask = useCallback(
    async (itemKey: string, taskId: number) => {
      setItems((prev) =>
        prev.map((it) =>
          it.key === itemKey && it.kind === "confirm"
            ? { ...it, state: "RUNNING", error: undefined }
            : it,
        ),
      );
      try {
        const result = await confirmTaskApi(taskId);
        setItems((prev) =>
          prev.map((it) =>
            it.key === itemKey && it.kind === "confirm" ? { ...it, state: "DONE", result } : it,
          ),
        );
        void refreshPending();
      } catch (e) {
        const msg = errText(e, "执行失败");
        setItems((prev) =>
          prev.map((it) =>
            it.key === itemKey && it.kind === "confirm"
              ? { ...it, state: "FAILED", error: msg }
              : it,
          ),
        );
      }
    },
    [refreshPending],
  );

  /** 对外只承诺「刷新完成」，内部返回值仅自用 */
  const refreshSessionsAction = useCallback(async () => {
    await refreshSessions();
  }, [refreshSessions]);

  const state: AgentState = {
    sessions,
    activeId,
    items,
    sending,
    error,
    loadingHistory,
    attachment,
    uploading,
    pendingTasks,
    status,
    open,
    minimized,
    hasUnread,
    sessionsLoaded,
  };

  const actions = useMemo<AgentActions>(
    () => ({
      openWindow,
      closeWindow,
      minimizeWindow,
      restoreWindow,
      clearError: () => setError(null),
      send,
      newChat,
      selectSession,
      deleteSession,
      uploadFile,
      clearAttachment: () => setAttachment(null),
      confirmTask,
      refreshSessions: refreshSessionsAction,
      refreshPending,
      draftRef,
    }),
    [
      openWindow,
      closeWindow,
      minimizeWindow,
      restoreWindow,
      send,
      newChat,
      selectSession,
      deleteSession,
      uploadFile,
      confirmTask,
      refreshSessionsAction,
      refreshPending,
    ],
  );

  return { state, actions };
}
