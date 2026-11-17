package com.aimodelhub.agent.tool;

import com.aimodelhub.predict.entity.PredictRecord;
import com.aimodelhub.predict.service.PredictRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户数据类工具（推理历史）。
 *
 * <h3>为什么用户身份必须来自上下文而不是参数</h3>
 * 「查我的推理历史」如果做成 {@code getInferenceHistory(Long userId)}，
 * 大模型完全可能（被诱导后）传入别人的 id。这里直接用
 * {@link AgentToolContext#getUserId()}，参数里根本没有 id 这一项——
 * 攻击面从「依赖模型不犯错」变成「结构上不可能」。
 */
public class InferenceTool {

    private static final Logger log = LoggerFactory.getLogger(InferenceTool.class);

    private static final int MAX_RECORDS = 10;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AgentToolContext context;
    private final PredictRecordService predictRecordService;

    public InferenceTool(AgentToolContext context, PredictRecordService predictRecordService) {
        this.context = context;
        this.predictRecordService = predictRecordService;
    }

    @Tool(name = "getInferenceHistory",
            description = """
                    查询【当前登录用户本人】最近的模型推理记录（最多 10 条），
                    包含使用过的模型 id、输入摘要、识别结果、置信度与时间。
                    用于回答「我以前跑过什么」「上次那个结果是多少」这类问题。
                    只能查到自己数据，无法查询任何其他用户。
                    """)
    public Object getInferenceHistory(
            @ToolParam(description = "返回条数，默认 5，最大 10", required = false) Integer limit) {

        long start = System.currentTimeMillis();
        int max = limit == null ? 5 : Math.min(Math.max(limit, 1), MAX_RECORDS);
        try {
            List<PredictRecord> records = predictRecordService.getUserRecords(context.getUserId());
            List<Map<String, Object>> items = new ArrayList<>();
            for (PredictRecord record : records.stream().limit(max).toList()) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("recordId", record.getId());
                one.put("modelId", record.getModelId());
                one.put("result", record.getPredictResult());
                one.put("confidence", record.getConfidence());
                one.put("inputSummary", summary(record));
                one.put("time", record.getCreateTime() == null ? null : record.getCreateTime().format(TIME_FORMAT));
                items.add(one);
            }
            context.recordInvocation("getInferenceHistory", "limit=" + max,
                    "返回 " + items.size() + " 条记录", true, System.currentTimeMillis() - start);
            return Map.of("total", records.size(), "records", items);
        } catch (Exception e) {
            log.warn("查询推理历史失败: {}", e.getMessage());
            context.recordInvocation("getInferenceHistory", "limit=" + max,
                    "查询失败", false, System.currentTimeMillis() - start);
            return Map.of("error", "查询推理历史失败，请稍后重试");
        }
    }

    /** 输入摘要：优先文件名，其次截断文本，避免把整段输入塞进上下文。 */
    private String summary(PredictRecord record) {
        if (record.getOriginalFilename() != null && !record.getOriginalFilename().isBlank()) {
            return "文件 " + record.getOriginalFilename();
        }
        String text = record.getInputText();
        if (text == null) {
            return null;
        }
        return text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }
}
