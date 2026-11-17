// 后端基址：
//   1) 显式配置 VITE_BACKEND_BASE_URL 时优先用它（留空字符串 = 同源）
//   2) 未配置时按构建模式取默认值：
//        dev  → http://localhost:8080（直连本机后端，配合 Vite 开发服务器）
//        prod → 空字符串，即同源请求 /api/**，由 Nginx 反向代理到后端
// 这样容器部署到任意主机都不需要再改代码或重新构建。
const RAW_BASE_URL = import.meta.env.VITE_BACKEND_BASE_URL;
const DEFAULT_BASE_URL = import.meta.env.PROD ? "" : "http://localhost:8080";
const BACKEND_BASE_URL = (RAW_BASE_URL ?? DEFAULT_BASE_URL).replace(/\/$/, "");

export class ApiError extends Error {
  status?: number;
  constructor(message: string, status?: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

function getToken(): string | null {
  try {
    const stored = localStorage.getItem("ai-modelhub");
    if (stored) {
      const parsed = JSON.parse(stored);
      return parsed?.state?.token || null;
    }
  } catch {
        // 忽略（响应体不是 JSON 时兜底）
  }
  return null;
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  timeout = 12000,
): Promise<T> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeout);

  const token = getToken();
  const headers: Record<string, string> = {};

  if (!(init.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

    // 与调用方传入的请求头合并（同名时以调用方为准）
  if (init.headers) {
    if (init.headers instanceof Headers) {
      init.headers.forEach((value, key) => {
        headers[key] = value;
      });
    } else if (Array.isArray(init.headers)) {
      for (const [k, v] of init.headers) {
        headers[k] = v;
      }
    } else {
      Object.assign(headers, init.headers);
    }
  }

  try {
    const response = await fetch(`${BACKEND_BASE_URL}${path}`, {
      ...init,
      signal: controller.signal,
      headers,
    });

    const data = await response.json().catch(() => null);

    // 统一返回体约定：HTTP 200 但 body.code >= 400 同样视为失败，
    // 否则业务失败会被当成成功，调用方读到 data=null。
    const businessCode: number | undefined = data?.code;
    const failed = !response.ok || (typeof businessCode === "number" && businessCode >= 400);

    if (failed) {
      const status = response.ok ? (businessCode as number) : response.status;
      const errorMessage = data?.message || `请求失败: ${status}`;

      // 429 限流 — 静默处理，仅 warning 级别日志，不打印错误堆栈
      if (status === 429) {
        console.warn(`[限流] ${errorMessage}`);
      }

      throw new ApiError(errorMessage, status);
    }

    return data as T;
  } finally {
    window.clearTimeout(timer);
  }
}

export { BACKEND_BASE_URL };
