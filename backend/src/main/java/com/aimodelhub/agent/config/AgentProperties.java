package com.aimodelhub.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 运行参数，全部来自 {@code application.yml} 的 {@code app.agent.*}。
 * <p>
 * 之所以集中成一个 {@code @ConfigurationProperties} 类而不是到处 {@code @Value}：
 * 参数一旦散落在各个 Service 里，改一个上下文长度要翻十几处；
 * 集中后既好改，也能在启动日志里一眼看到实际生效值。
 */
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

    /** 送给大模型的历史消息条数上限（不含 system prompt）。超出后只保留最近的。 */
    private int maxContextMessages = 20;

    /** 每用户每日大模型调用次数上限，存 Redis 计数。 */
    private int dailyLlmQuota = 200;

    /** 首条用户消息截取为会话标题时的最大字符数。 */
    private int titleMaxLength = 30;

    /** SSE 连接超时（毫秒）。超时后连接关闭，避免占满 Tomcat 线程。 */
    private long sseTimeoutMs = 180_000L;

    /** 单轮对话允许的最长用户输入字符数，防止超长文本打爆 token。 */
    private int maxInputChars = 4000;

    /** Redis 中上下文窗口的存活时间（秒）。 */
    private long contextTtlSeconds = 7200L;

    /**
     * 流式输出时「等下一个 token」的最大间隔（秒）。
     * <p>
     * 这是流式接口的看门狗：如果 DeepSeek 侧卡住不再吐 token，
     * 没有这个超时，Flux 会永远挂着，SSE 连接和上游连接都不会释放。
     * 注意它是<b>逐个元素</b>重置的，不是整个请求的总时长限制——
     * 长回答不会因为总时长被误杀。
     */
    private int idleTimeoutSeconds = 60;

    /**
     * Agent 附件暂存目录。
     * <p>
     * 为什么落本地而不是直接进 MinIO：推理要等用户确认，两次请求之间需要一个
     * 稳定的文件引用；而 MinIO 在既有设计里允许故障时不阻断推理，
     * 把「待确认文件」压在它上面会让确认流程依赖对象存储可用性。
     */
    private String uploadDir = System.getProperty("java.io.tmpdir") + "/ai-modelhub-agent";

    /** RAG 知识库相关配置。 */
    private Rag rag = new Rag();

    public int getMaxContextMessages() {
        return maxContextMessages;
    }

    public void setMaxContextMessages(int maxContextMessages) {
        this.maxContextMessages = maxContextMessages;
    }

    public int getDailyLlmQuota() {
        return dailyLlmQuota;
    }

    public void setDailyLlmQuota(int dailyLlmQuota) {
        this.dailyLlmQuota = dailyLlmQuota;
    }

    public int getTitleMaxLength() {
        return titleMaxLength;
    }

    public void setTitleMaxLength(int titleMaxLength) {
        this.titleMaxLength = titleMaxLength;
    }

    public long getSseTimeoutMs() {
        return sseTimeoutMs;
    }

    public void setSseTimeoutMs(long sseTimeoutMs) {
        this.sseTimeoutMs = sseTimeoutMs;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    public long getContextTtlSeconds() {
        return contextTtlSeconds;
    }

    public void setContextTtlSeconds(long contextTtlSeconds) {
        this.contextTtlSeconds = contextTtlSeconds;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    /**
     * RAG 配置。
     * <p>
     * 向量模型路径默认指向 {@code backend/models/bge-small-zh-v1.5/}：
     * 该目录被 .gitignore 排除（24MB 二进制不入库），首次部署需要按
     * models/README.md 的说明下载；文件缺失时系统自动退化为「仅全文检索」。
     */
    public static class Rag {

        private boolean enabled = true;

        private String embeddingModelPath = "models/bge-small-zh-v1.5/model_quantized.onnx";

        private String embeddingVocabPath = "models/bge-small-zh-v1.5/vocab.txt";

        private String embeddingModelName = "bge-small-zh-v1.5";

        /** BERT 最大序列长度 */
        private int maxSequenceLength = 512;

        /** 每个分块的目标字符数 */
        private int chunkSize = 400;

        /** 相邻分块的重叠字符数，避免答案被切断在边界 */
        private int chunkOverlap = 60;

        /** 最终注入提示词的分块数 */
        private int topK = 5;

        /** 注入提示词的最大字符数，防止把上下文塞爆 */
        private int maxContextChars = 2000;

        /** 向量召回的最低余弦相似度，低于它的分块直接丢弃 */
        private double minSimilarity = 0.30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEmbeddingModelPath() {
            return embeddingModelPath;
        }

        public void setEmbeddingModelPath(String embeddingModelPath) {
            this.embeddingModelPath = embeddingModelPath;
        }

        public String getEmbeddingVocabPath() {
            return embeddingVocabPath;
        }

        public void setEmbeddingVocabPath(String embeddingVocabPath) {
            this.embeddingVocabPath = embeddingVocabPath;
        }

        public String getEmbeddingModelName() {
            return embeddingModelName;
        }

        public void setEmbeddingModelName(String embeddingModelName) {
            this.embeddingModelName = embeddingModelName;
        }

        public int getMaxSequenceLength() {
            return maxSequenceLength;
        }

        public void setMaxSequenceLength(int maxSequenceLength) {
            this.maxSequenceLength = maxSequenceLength;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getMaxContextChars() {
            return maxContextChars;
        }

        public void setMaxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
        }

        public double getMinSimilarity() {
            return minSimilarity;
        }

        public void setMinSimilarity(double minSimilarity) {
            this.minSimilarity = minSimilarity;
        }
    }
}
