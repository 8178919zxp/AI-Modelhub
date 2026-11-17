/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 基址；留空字符串表示同源（由 Nginx / 反向代理转发 /api） */
  readonly VITE_BACKEND_BASE_URL?: string;
  /** 仅本地调试推理服务时参考，前端不直接调用 */
  readonly VITE_MODEL_SERVICE_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
