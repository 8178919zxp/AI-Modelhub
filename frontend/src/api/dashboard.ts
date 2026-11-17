import { request } from "./request";

export interface DashboardOverview {
  totalModels: number;
  onlineModels: number;
  totalUsers: number;
  totalCalls: number;
  hotModels: string[];
}

export interface DashboardResponse {
  code: number;
  message: string;
  data: DashboardOverview;
}

export function fetchDashboardApi() {
  return request<DashboardResponse>("/api/dashboard/overview");
}
