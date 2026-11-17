package com.aimodelhub.agent.vo;

/**
 * 推荐卡片数据。
 * <p>
 * <b>所有展示字段都来自数据库</b>，只有 {@code reason} 与 {@code score}
 * 是大模型给的。这是「模型推荐」这条业务链的核心约束：
 * 大模型可以判断「哪个更合适、为什么」，但不能决定「平台上有哪些模型、叫什么、在不在线」。
 */
public class ModelRecommendVO extends ModelBriefVO {

    /** 推荐理由，由大模型生成，服务端只做长度截断 */
    private String reason;

    /** 匹配度 0~1，仅用于排序与展示，不代表模型准确率 */
    private Double score;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
