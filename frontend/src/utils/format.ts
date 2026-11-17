/**
 * 准确率展示工具。
 *
 * 单位约定：后端 `model_info.accuracy` 存的是**百分数**（0–100，例如 92.1），
 * 不是 0–1 的小数。前端任何地方都不要再乘 100，否则会出现 9210% 这类量纲错误。
 */

/** 格式化为 "92.1%"；空值统一显示为 "—"（不带百分号）。 */
export function formatAccuracy(value?: number | null, digits = 1): string {
  if (value == null || Number.isNaN(value)) return "—";
  return `${value.toFixed(digits)}%`;
}

/** 取数值本身（保留 1 位小数），用于图表 dataKey；空值按 0 处理。 */
export function accuracyNumber(value?: number | null): number {
  if (value == null || Number.isNaN(value)) return 0;
  return Number(value.toFixed(1));
}
