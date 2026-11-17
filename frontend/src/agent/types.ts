/** Agent 悬浮助手与全屏页面共用的数据类型。 */

import type {
  AgentAttachment,
  AgentSession,
  AgentTask,
  ModelRecommend,
  TaskResult,
} from "../api/agent";

export type ChatRole = "USER" | "ASSISTANT";

export interface ToolChip {
  tool: string;
  summary: string;
  success: boolean;
  elapsedMs: number;
  /** 从数据库历史恢复的芯片：没有 payload，无法判断成功与否，按中性样式展示 */
  restored?: boolean;
}

/** 一条时间线里混排多种块，避免把「模型说它做了」和「后端确实做了」混成一段文本。 */
export type AgentItem =
  | { key: string; kind: "text"; role: ChatRole; content: string; streaming?: boolean }
  | { key: string; kind: "tools"; tools: ToolChip[] }
  | { key: string; kind: "recommend"; models: ModelRecommend[] }
  | {
      key: string;
      kind: "confirm";
      taskId: number;
      taskNo: string;
      modelName: string;
      inputType: string;
      inputSummary: string;
      state: "PENDING" | "RUNNING" | "DONE" | "FAILED";
      result?: TaskResult;
      error?: string;
    };

/** idle=空闲 waiting=已发送等首包 streaming=正在输出 */
export type AgentStatus = "idle" | "waiting" | "streaming" | "acting";

/** 悬浮窗几何信息：只持久化这四个数，聊天内容一律不落 localStorage。 */
export interface AgentWindowGeometry {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface AgentState {
  sessions: AgentSession[];
  activeId: number | null;
  items: AgentItem[];
  sending: boolean;
  error: string | null;
  loadingHistory: boolean;
  attachment: AgentAttachment | null;
  uploading: boolean;
  /** 用户维度未完成的推理任务（后端 /tasks 不含 sessionId，只能按用户恢复） */
  pendingTasks: AgentTask[];
  status: AgentStatus;
  /** 悬浮窗是否展开 */
  open: boolean;
  /** 展开后是否收成条状 */
  minimized: boolean;
  /** 窗口关闭时收到新回复 → 悬浮球打个点 */
  hasUnread: boolean;
  sessionsLoaded: boolean;
}

export interface AgentActions {
  openWindow: () => void;
  closeWindow: () => void;
  minimizeWindow: () => void;
  restoreWindow: () => void;
  clearError: () => void;
  send: (text: string) => Promise<void>;
  newChat: () => Promise<void>;
  selectSession: (id: number) => void;
  deleteSession: (id: number) => Promise<void>;
  uploadFile: (file: File | undefined) => Promise<void>;
  clearAttachment: () => void;
  confirmTask: (itemKey: string, taskId: number) => Promise<void>;
  refreshSessions: () => Promise<void>;
  /** 已确认的任务不再是 PENDING，重拉一次待确认列表，避免面板留陈旧数据 */
  refreshPending: () => Promise<void>;
  /** 输入草稿：放 ref 里，避免每次敲字都重渲染整个消息列表 */
  draftRef: { current: string };
}
