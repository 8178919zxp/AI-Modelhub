package com.aimodelhub.agent.entity;

import java.time.LocalDateTime;

/**
 * 知识库分块（对应表 {@code agent_knowledge_chunk}）。
 * <p>
 * {@code embedding} 是归一化后的向量 JSON；为 {@code null} 表示该分块没有向量，
 * 只参与全文检索。之所以允许为空：向量模型可能加载失败，
 * 此时系统仍应能用关键词检索工作，而不是整库不可用。
 */
public class AgentKnowledgeChunk {

    private Long id;
    private Long docId;
    private Integer chunkIndex;
    private String heading;
    private String content;
    private String keywords;
    private String embedding;
    private String embeddingModel;
    private Integer embeddingDim;
    private LocalDateTime createTime;

    /**
     * 文档标题（非本表字段，来自 JOIN）。
     * <p>
     * 放在实体上是为了让向量检索一次查询就能拿到「来源文档」，
     * 不必对每个分块再查一次文档表（N+1）。
     */
    private String docTitle;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
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

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(Integer embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getDocTitle() {
        return docTitle;
    }

    public void setDocTitle(String docTitle) {
        this.docTitle = docTitle;
    }
}
