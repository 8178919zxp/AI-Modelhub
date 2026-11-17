import { request } from "./request";

export interface LoginResponse {
  code: number;
  message: string;
  data: {
    userId: number;
    username: string;
    nickname: string;
    role: "user" | "admin";
    avatar?: string;
    token: string;
  };
}

export function loginApi(payload: { username: string; password: string }) {
  return request<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function registerApi(payload: {
  username: string;
  password: string;
  nickname: string;
  securityQuestion: string;
  securityAnswer: string;
}) {
  return request<{
    code: number;
    message: string;
    data: { username: string; nickname: string; role: "user" | "admin" };
  }>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export interface LogoutResponse {
  code: number;
  message: string;
  data: {
    blacklisted: boolean;
    reason?: string; // 仅在 blacklisted=false 时存在
  };
}

/**
 * 调用后端退出登录接口，吊销当前 JWT。
 * Bearer 令牌由 request() 拦截器自动注入。
 * 即使调用失败（网络异常、Redis 不可用），调用方仍需清除本地状态。
 */
export function logoutApi() {
  return request<LogoutResponse>("/api/auth/logout", {
    method: "POST",
  });
}

/**
 * 找回密码第一步：按用户名获取密保问题。
 */
export function getSecurityQuestionApi(username: string) {
  return request<{
    code: number;
    message: string;
    data: { securityQuestion: string };
  }>("/api/auth/forgot-password/question", {
    method: "POST",
    body: JSON.stringify({ username }),
  });
}

/**
 * 找回密码第二步：校验密保答案并重置密码。
 */
export function resetPasswordApi(data: {
  username: string;
  securityAnswer: string;
  newPassword: string;
}) {
  return request<{ code: number; message: string; data: string }>(
    "/api/auth/forgot-password/reset",
    { method: "POST", body: JSON.stringify(data) },
  );
}
