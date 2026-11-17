import { BACKEND_BASE_URL, ApiError } from "./request";

/**
 * ModelHub Copilot 的前端 API 封装。
 *
 * 说明两点：
 * 1. 普通接口复用 request.ts（统一鉴权、统一错误结构）；
 * 2. 对话接口<b>不能</b>用 EventSource——它无法携带 Authorization 头，
 *    而本项目 JWT 只认 Header。因此这里用 fetch + ReadableStream 手动解析 SSE。
 */

export interface AgentSession {
  id: number;
  title: string;
  messageCount: number;
  lastMessageAt: string;
}

export interface AgentMessage {
  id: number;
  role: "USER" | "ASSISTANT" | "TOOL";
  content: string;
  toolName?: string;
  createTime: string;
}

export interface AgentChatResult {
  sessionId: number;
  messageId: number;
  content: string;
}

export interface AgentAttachment {
  id: number;
  filename: string;
  size: number;
  contentType: string;
}

export interface ModelRecommend {
  id: number;
  name: string;
  type: string;
  typeLabel: string;
  algorithm?: string;
  accuracy?: number;
  status: number;
  description?: string;
  inputType?: string;
  requiredFields?: string[];
  reason?: string;
  score?: number;
}

export interface TaskResult {
  taskId: number;
  taskNo: string;
  status: string;
  modelId: number;
  modelName: string;
  topLabel?: string;
  confidence?: number;
  durationMs?: number;
  explanation?: string;
  recordId?: number;
  message?: string;
}

/** 任务列表项（GET /api/agent/tasks 返回；不含 sessionId）。 */
export interface AgentTask {
  taskId: number;
  taskNo: string;
  status: string;
  modelId: number;
  modelName: string;
  inputType: string;
  inputSummary: string;
  resultSummary?: string;
  errorMessage?: string;
  recordId?: number;
  createTime?: string;
}

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

function getToken(): string | null {
  try {
    const stored = localStorage.getItem("ai-modelhub");
    if (stored) {
      return JSON.parse(stored)?.state?.token || null;
    }
  } catch {
    /* 存储内容损坏时当作未登录 */
  }
  return null;
}

async function json<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken();
  const res = await fetch(`${BACKEND_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers as Record<string, string> | undefined),
    },
  });
  const body = (await res.json().catch(() => null)) as ApiResult<T> | null;
  if (!res.ok || !body || body.code >= 400) {
    throw new ApiError(body?.message || `请求失败: ${res.status}`, res.status);
  }
  return body.data;
}

// ── 会话管理 ────────────────────────────────────────────────────────

export const createSessionApi = (title?: string) =>
  json<AgentSession>("/api/agent/sessions", {
    method: "POST",
    body: JSON.stringify({ title }),
  });

export const listSessionsApi = () => json<AgentSession[]>("/api/agent/sessions");

export const deleteSessionApi = (id: number) =>
  json<void>(`/api/agent/sessions/${id}`, { method: "DELETE" });

export const listMessagesApi = (id: number) =>
  json<AgentMessage[]>(`/api/agent/sessions/${id}/messages`);

// ── 附件与任务 ──────────────────────────────────────────────────────

/**
 * 上传待推理文件。
 * 返回的 id 需要告诉 Agent（拼进下一条消息），Agent 才能用 prepareInference 引用它。
 */
export async function uploadAttachmentApi(
  file: File,
  sessionId?: number,
): Promise<AgentAttachment> {
  const token = getToken();
  const form = new FormData();
  form.append("file", file);
  const query = sessionId ? `?sessionId=${sessionId}` : "";
  const res = await fetch(`${BACKEND_BASE_URL}/api/agent/files${query}`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    body: form,
  });
  const body = (await res.json().catch(() => null)) as ApiResult<AgentAttachment> | null;
  if (!res.ok || !body || body.code >= 400) {
    throw new ApiError(body?.message || `上传失败: ${res.status}`, res.status);
  }
  return body.data;
}

/** 用户点击「确认执行」后才调用；服务端会校验归属、状态与时效。 */
export const confirmTaskApi = (taskId: number) =>
  json<TaskResult>(`/api/agent/tasks/${taskId}/confirm`, { method: "POST" });

export const getTaskApi = (taskId: number) => json<TaskResult>(`/api/agent/tasks/${taskId}`);

/**
 * 当前用户的任务列表。
 * 用途：刷新页面后把「还没确认」的任务重新显示出来（后端只按用户维度返回，不含会话 id）。
 */
export const listTasksApi = (limit = 20) => json<AgentTask[]>(`/api/agent/tasks?limit=${limit}`);

// ── 对话 ────────────────────────────────────────────────────────────

export type AgentSseEvent =
  | { type: "session"; data: { sessionId: number; title: string } }
  | { type: "token"; data: { text: string } }
  | {
      type: "done";
      data: { sessionId: number; messageId: number; tokenCount: number; elapsedMs: number };
    }
  | { type: "error"; data: { message: string } }
  // 后续阶段的扩展事件（工具调用、推荐卡片、待确认任务）
  | { type: "tool_call"; data: Record<string, unknown> }
  | { type: "tool_result"; data: Record<string, unknown> }
  | { type: "recommend"; data: Record<string, unknown> }
  | { type: "confirm_required"; data: Record<string, unknown> }
  | { type: "task"; data: Record<string, unknown> };

/**
 * 发起流式对话。
 *
 * SSE 报文形如：
 *   event:token
 *   data:{"text":"你"}
 *
 * 浏览器没有内置的「带 Header 的 EventSource」，所以这里读字节流、按空行切分帧，
 * 再交给 onEvent 逐条处理。
 */
export async function streamChat(
  payload: { sessionId?: number; message: string },
  onEvent: (event: AgentSseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken();
  const res = await fetch(`${BACKEND_BASE_URL}/api/agent/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal,
  });

  if (!res.ok) {
    // 未登录 / 参数错误等，后端返回的是普通 JSON
    const body = await res.json().catch(() => null);
    throw new ApiError(body?.message || `请求失败: ${res.status}`, res.status);
  }
  if (!res.body) {
    throw new ApiError("浏览器不支持流式响应", 0);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder("utf-8");

  // SSE 以「空行」分隔一帧，帧内是若干 `field:value` 行。
  // 网络分片可能把一帧切成两半，所以用 buffer 累积、按行消费，
  // 半截的行留在 buffer 里等下一片数据。
  let buffer = "";
  let eventName = "message";
  let dataLines: string[] = [];
  /** 是否已经收到终止事件（done / error）——用于把「服务端正常结束后断开」和真故障区分开 */
  let finished = false;

  const dispatch = () => {
    if (dataLines.length > 0) {
      const raw = dataLines.join("\n");
      try {
        const parsed = JSON.parse(raw);
        if (eventName === "done" || eventName === "error") {
          finished = true;
        }
        onEvent({ type: eventName, data: parsed } as AgentSseEvent);
      } catch {
        /* 忽略无法解析的帧，不中断整条流 */
      }
    }
    eventName = "message";
    dataLines = [];
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? "";
      for (const line of lines) {
        if (line === "") {
          dispatch();
        } else if (line.startsWith("event:")) {
          eventName = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          dataLines.push(line.slice(5).trim());
        }
      }
    }
    // 服务端若最后一帧没有以空行结尾，这里补一次
    dispatch();
  } catch (e) {
    // 有些环境（代理、Tomcat 收尾）会在发完 done 后直接断开连接，
    // 浏览器报 "terminated"/ERR_INCOMPLETE_CHUNKED_ENCODING。
    // 只要本轮已经收到终止事件，业务上就是成功的，不应该弹「网络错误」。
    if (finished) {
      return;
    }
    throw e;
  }
}
