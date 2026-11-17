import { request } from "./request";

export interface UserVO {
  id: number;
  username: string;
  nickname: string;
  role: "user" | "admin";
  avatar: string;
  createTime: string;
}

export interface UserResponse {
  code: number;
  message: string;
  data: UserVO;
}

export interface FavoritesResponse {
  code: number;
  message: string;
  data: number[];
}

export function fetchUserMeApi() {
  return request<UserResponse>("/api/users/me");
}

export function fetchFavoritesApi() {
  return request<FavoritesResponse>("/api/users/favorites");
}

export function addFavoriteApi(modelId: number) {
  return request<{ code: number; message: string }>(`/api/users/favorites/${modelId}`, {
    method: "POST",
  });
}

export function removeFavoriteApi(modelId: number) {
  return request<{ code: number; message: string }>(`/api/users/favorites/${modelId}`, {
    method: "DELETE",
  });
}

// 预测记录
export interface PredictRecordVO {
  id: number;
  modelId: number;
  originalFilename: string | null;
  fileUrl: string | null;
  inputText: string | null;
  predictResult: string;
  confidence: number;
  duration: number;
  createTime: string;
}

export interface RecordsResponse {
  code: number;
  message: string;
  data: PredictRecordVO[];
}

export function fetchRecordsApi() {
  return request<RecordsResponse>("/api/users/records");
}

// ── 后台用户管理 ────────────────────────────────────────────────

export interface AdminUserPageVO {
  content: UserVO[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface AdminUserPageResponse {
  code: number;
  message: string;
  data: AdminUserPageVO;
}

export interface ResetPasswordResponse {
  code: number;
  message: string;
  data: {
    username: string;
    temporaryPassword: string;
    notice?: string;
  };
}

export function fetchUsersApi(page: number, size: number) {
  return request<AdminUserPageResponse>(
    `/api/admin/users?page=${page}&size=${size}`,
  );
}

export function resetUserPasswordApi(userId: number) {
  return request<ResetPasswordResponse>(
    `/api/admin/users/${userId}/reset-password`,
    { method: "POST" },
  );
}
