/** 工具名与输入类型的中文标签：UI 与提示词共用，避免各写一份。 */

const TOOL_LABELS: Record<string, string> = {
  listAvailableModels: "查询平台模型",
  getModelDetail: "查询模型详情",
  listModelsByCategory: "查询模型分类",
  getModelInputRequirements: "查询输入要求",
  recommendModels: "智能推荐",
  getInferenceHistory: "查询推理历史",
  prepareInference: "准备推理任务",
  executeInference: "执行推理",
  searchKnowledgeBase: "检索知识库",
};

export const toolLabel = (tool: string): string => TOOL_LABELS[tool] ?? tool;

export const inputLabel = (inputType?: string): string => {
  switch (inputType) {
    case "IMAGE":
      return "图片";
    case "TEXT":
      return "文本";
    case "FEATURES":
      return "数值字段";
    default:
      return "未知";
  }
};
