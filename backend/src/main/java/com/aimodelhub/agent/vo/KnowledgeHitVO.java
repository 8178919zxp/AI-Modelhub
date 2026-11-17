package com.aimodelhub.agent.vo;

/**
 * 知识库检索命中项。
 * <p>
 * {@code score} 是全文检索的 MySQL 相关度，{@code similarity} 是向量余弦相似度，
 * 二者量纲不同，不能直接相加。融合排序在服务层做
 * （{@code AgentKnowledgeService#retrieve}：归一化后取较强者，双路命中再加权），
 * 前端只展示内容与来源，不展示这两个分数（对用户没有意义，反而容易被误读成「准确率」）。
 */
public class KnowledgeHitVO {

    private Long chunkId;
    private Long docId;
    private String docTitle;
    private String heading;
    private String content;
    private Double score;
    private Double similarity;

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getDocTitle() {
        return docTitle;
    }

    public void setDocTitle(String docTitle) {
        this.docTitle = docTitle;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }
}
