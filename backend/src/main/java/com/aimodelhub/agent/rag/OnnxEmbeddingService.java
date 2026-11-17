package com.aimodelhub.agent.rag;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地中文向量模型（BGE-small-zh-v1.5）的 ONNX 推理封装。
 *
 * <h3>为什么是本地模型</h3>
 * DeepSeek 没有 embedding 接口（实测 /models 只返回两个 chat 模型）。
 * 要「真向量检索」又不引入第二家 API 和向量数据库，本地 ONNX 是唯一现实选择：
 * 24MB 量化模型，CPU 单条编码约 10~30ms，零额外成本、零网络依赖。
 *
 * <h3>取向量方式：CLS + L2 归一化</h3>
 * BGE 系列官方用法是取 {@code [CLS]} 向量并归一化，之后余弦相似度退化为点积。
 * 归一化后存 JSON 也更省空间（值域稳定在 -1~1）。
 *
 * <h3>可靠性</h3>
 * 模型文件缺失/加载失败时 {@link #isAvailable()} 返回 false，
 * 检索自动降级为「仅 MySQL 全文检索」，而不是让整个 Agent 起不来。
 */
public class OnnxEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingService.class);

    /** BGE 官方建议：查询侧加指令前缀，文档侧不加。中英文各一条。 */
    private static final String QUERY_PREFIX_ZH = "为这个句子生成表示以用于检索相关文章：";

    private final Path modelPath;
    private final Path vocabPath;
    private final int maxLength;
    private final String modelName;

    private OrtEnvironment environment;
    private OrtSession session;
    private BertTokenizer tokenizer;
    private volatile boolean available;
    private int dimension;

    public OnnxEmbeddingService(String modelPath, String vocabPath, int maxLength, String modelName) {
        this.modelPath = Paths.get(modelPath);
        this.vocabPath = Paths.get(vocabPath);
        this.maxLength = maxLength;
        this.modelName = modelName;
    }

    /** 延迟加载：首次使用时初始化，避免拖慢应用启动。 */
    public synchronized void init() {
        if (available) {
            return;
        }
        try {
            if (!Files.exists(modelPath) || !Files.exists(vocabPath)) {
                log.warn("中文向量模型文件不存在（model={}，vocab={}），RAG 将仅使用 MySQL 全文检索",
                        modelPath, vocabPath);
                return;
            }
            long start = System.currentTimeMillis();
            tokenizer = new BertTokenizer(vocabPath, maxLength);
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            session = environment.createSession(modelPath.toString(), options);
            available = true;
            log.info("中文向量模型已加载: {}（{}，vocab={}，耗时 {}ms）",
                    modelName, modelPath.getFileName(), tokenizer.vocabSize(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            available = false;
            log.error("中文向量模型加载失败，RAG 将仅使用全文检索: {}", e.getMessage());
        }
    }

    public boolean isAvailable() {
        if (!available) {
            init();
        }
        return available;
    }

    public String getModelName() {
        return modelName;
    }

    public int getDimension() {
        return dimension;
    }

    /**
     * 编码单条文本。
     *
     * @param forQuery true 表示用于检索查询（会自动加 BGE 指令前缀）
     * @return 归一化向量；模型不可用时返回 {@code null}（调用方据此降级）
     */
    public float[] embed(String text, boolean forQuery) {
        if (text == null || text.isBlank() || !isAvailable()) {
            return null;
        }
        String input = forQuery ? QUERY_PREFIX_ZH + text : text;
        BertTokenizer.Encoded encoded = tokenizer.encode(input);
        try (OnnxTensor inputIds = OnnxTensor.createTensor(environment, new long[][]{encoded.inputIds()});
             OnnxTensor attentionMask = OnnxTensor.createTensor(environment, new long[][]{encoded.attentionMask()})) {

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIds);
            inputs.put("attention_mask", attentionMask);
            // 部分导出的 BERT 需要 token_type_ids，按模型实际输入名决定是否传
            Set<String> required = session.getInputNames();
            OnnxTensor tokenTypes = null;
            if (required.contains("token_type_ids")) {
                tokenTypes = OnnxTensor.createTensor(environment, new long[][]{encoded.tokenTypeIds()});
                inputs.put("token_type_ids", tokenTypes);
            }

            try (OrtSession.Result result = session.run(inputs)) {
                // 输出通常是 last_hidden_state: [1, seq, hidden]
                float[][][] hidden = (float[][][]) result.get(0).getValue();
                float[] cls = hidden[0][0].clone();
                normalize(cls);
                dimension = cls.length;
                return cls;
            } finally {
                if (tokenTypes != null) {
                    tokenTypes.close();
                }
            }
        } catch (Exception e) {
            log.warn("文本向量化失败，本条退化为关键词检索: {}", e.getMessage());
            return null;
        }
    }

    /** L2 归一化：之后余弦相似度 == 点积，比较时不必再算模长。 */
    private static void normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += (double) v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm < 1e-8) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }

    /** 点积即余弦相似度（入参都已归一化）。 */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }

    /** 供诊断日志使用。 */
    public List<String> describe() {
        return List.of(modelName, modelPath.toString(), "dim=" + dimension);
    }
}
