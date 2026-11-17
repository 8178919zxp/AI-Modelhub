import { request } from "./request";

// ── 公开模型相关类型 ──────────────────────────────────────────────

export interface ModelVO {
  id: number;
  name: string; // 对应库中 model_name，通过 @JsonProperty 序列化为 name
  type: string; // 对应库中 model_type，序列化为 type
  algorithm: string;
  accuracy: number;
  description: string;
  cover: string | null;
  api: string; // 对应库中 model_api，序列化为 api
  status: number; // 1=上线，0=下线，-1=已删除
  createTime: string;
  updateTime: string;
}

export interface ModelListResponse {
  code: number;
  message: string;
  data: ModelVO[];
}

export interface ModelDetailResponse {
  code: number;
  message: string;
  data: ModelVO;
}

// ── 公开接口 ────────────────────────────────────────────────────

export function fetchModelsApi() {
  return request<ModelListResponse>("/api/models");
}

export function fetchModelApi(id: number) {
  return request<ModelDetailResponse>(`/api/models/${id}`);
}

// ── 后台模型相关类型 ──────────────────────────────────────────────

/** 新增 / 更新模型的请求体（对应后端的 ModelSaveDTO）。 */
export interface ModelSavePayload {
  modelName: string;
  modelType: string;
  algorithm?: string;
  accuracy?: number;
  description?: string;
  cover?: string;
  modelApi?: string;
  status?: number; // 1=上线，0=下线；默认 1
}

export interface AdminModelListResponse {
  code: number;
  message: string;
  data: ModelVO[] | AdminPageVO<ModelVO>;
}

/** 传 ?page=N&size=M 时后端返回的分页包装。 */
export interface AdminPageVO<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface AdminModelResponse {
  code: number;
  message: string;
  data: ModelVO;
}

export interface AdminDeleteResponse {
  code: number;
  message: string;
  data: {
    id: number;
    name: string;
    status: number;
    message: string;
  };
}

// ── 后台接口 ────────────────────────────────────────────────────

/** 查询全部模型（含已删除），仅管理员可用。
 *  传 page/size 返回分页结果，否则返回完整列表。 */
export function fetchAdminModelsApi(page?: number, size?: number) {
  const params = page != null && size != null ? `?page=${page}&size=${size}` : "";
  return request<AdminModelListResponse>(`/api/admin/models${params}`);
}

/** 新增模型，仅管理员可用。 */
export function createModelApi(payload: ModelSavePayload) {
  return request<AdminModelResponse>("/api/admin/models", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/** 更新已有模型，仅管理员可用。 */
export function updateModelApi(id: number, payload: ModelSavePayload) {
  return request<AdminModelResponse>(`/api/admin/models/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

/** 切换模型的上架 / 下架状态，仅管理员可用。 */
export function toggleModelStatusApi(id: number, status: 0 | 1) {
  return request<AdminModelResponse>(`/api/admin/models/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

/** 逻辑删除模型，仅管理员可用。 */
export function deleteModelApi(id: number) {
  return request<AdminDeleteResponse>(`/api/admin/models/${id}`, {
    method: "DELETE",
  });
}
