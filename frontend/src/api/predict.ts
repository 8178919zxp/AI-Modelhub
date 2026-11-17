import { request } from "./request";

export interface PredictCandidate {
  label: string;
  probability: number;
  image_base64?: string;
  bbox?: number[];
}

export interface PredictResponse {
  code: number;
  message: string;
  data: {
    type: string;
    modelName: string;
    topLabel: string;
    confidence: number;
    duration: number;
    candidates: PredictCandidate[];
    resultType?: string;
    imageBase64?: string;
    predictedClose?: number;
  };
}

function getAuthHeaders(): Record<string, string> {
  const token = (() => {
    try {
      const stored = localStorage.getItem("ai-modelhub");
      if (stored) {
        const parsed = JSON.parse(stored);
        return parsed?.state?.token || null;
      }
    } catch {
            // 忽略（此处失败不影响主流程）
    }
    return null;
  })();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export function predictTextApi(payload: { modelId?: number; text: string }) {
  return request<PredictResponse>("/api/predict/text", {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
}

export function predictTabularApi(payload: {
  modelId?: number;
  features: Record<string, unknown>;
}) {
  return request<PredictResponse>("/api/predict/tabular", {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
}

export function predictImageApi(file: File, modelId: number) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("modelId", String(modelId));
  return request<PredictResponse>(
    "/api/predict/image",
    {
      method: "POST",
      headers: getAuthHeaders(),
      body: formData,
    },
    20000,
  );
}

/** 自定义模型预测（天气 / 股票 / 中文情感）。 */
export function predictCustomApi(payload: {
  modelId: number;
  text?: string;
  features?: Record<string, unknown>;
}) {
  return request<PredictResponse>("/api/predict/custom", {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
}
