package com.aimodelhub.agent.service;

import com.aimodelhub.agent.config.AgentProperties;
import com.aimodelhub.agent.entity.AgentKnowledgeChunk;
import com.aimodelhub.agent.entity.AgentKnowledgeDocument;
import com.aimodelhub.agent.mapper.AgentKnowledgeMapper;
import com.aimodelhub.agent.rag.KnowledgeChunker;
import com.aimodelhub.agent.rag.OnnxEmbeddingService;
import com.aimodelhub.agent.vo.KnowledgeHitVO;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库：入库、切分、向量化、混合检索。
 *
 * <h3>检索为什么是「混合」的</h3>
 * <ul>
 *   <li><b>全文检索（MySQL ngram）</b>：对专有名词、型号（ResNet-50、sepal_length）
 *       特别准，因为它精确匹配字面；但对「换个说法问同一件事」无能为力。</li>
 *   <li><b>向量检索（BGE-small-zh）</b>：能召回语义相近但用词不同的段落，
 *       但对精确术语反而不如字面匹配。</li>
 * </ul>
 * 两者互补，融合方式见 {@link #retrieve(String, int)}：
 * 全文相关度先按本次最高分归一化到 0~1，再与「余弦相似度 × 0.9」取较大者，
 * 两路都命中的分块额外加 0.15。不直接用 RRF，是因为 RRF 只看名次，
 * 会把「全文第一但相关度只有 0.1」排在「全文第五但相关度 0.9」前面。
 *
 * <h3>边界</h3>
 * 知识库只回答「怎么用、有什么限制、常见问题」。
 * 模型名称/在线状态/输入字段这些事实以 MySQL 为准（见 AgentModelQueryService），
 * 检索结果不得覆盖它们。检索不到就明说，不编。
 */
@Service
public class AgentKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(AgentKnowledgeService.class);

    private final AgentKnowledgeMapper knowledgeMapper;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final OnnxEmbeddingService embeddingService;

    public AgentKnowledgeService(AgentKnowledgeMapper knowledgeMapper,
                                 AgentProperties properties,
                                 ObjectMapper objectMapper,
                                 OnnxEmbeddingService embeddingService) {
        this.knowledgeMapper = knowledgeMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
    }

    // ================================================================
    // 入库
    // ================================================================

    /**
     * 导入一篇文档：切分 → 逐块向量化 → 落库。
     * <p>
     * 向量化失败不会让整个导入失败：该分块 embedding 留空，
     * 仍然可以参与全文检索。
     */
    @Transactional
    public AgentKnowledgeDocument ingest(Long uploaderId, String title, String category,
                                         String filename, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("文档内容为空，无法导入");
        }
        if (content.length() > 200_000) {
            throw new BusinessException("文档过大（上限 20 万字符），请拆分后再导入");
        }

        AgentProperties.Rag rag = properties.getRag();
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.split(content, rag.getChunkSize(), rag.getChunkOverlap());
        if (chunks.isEmpty()) {
            throw new BusinessException("文档切分后没有有效内容");
        }

        AgentKnowledgeDocument document = new AgentKnowledgeDocument();
        document.setTitle(title == null || title.isBlank() ? "未命名文档" : title.trim());
        document.setCategory(category);
        document.setSourceType("UPLOAD");
        document.setOriginalFilename(filename);
        document.setCharCount(content.length());
        document.setChunkCount(chunks.size());
        document.setStatus(1);
        document.setUploaderId(uploaderId);
        knowledgeMapper.insertDocument(document);

        int embedded = 0;
        for (KnowledgeChunker.Chunk chunk : chunks) {
            AgentKnowledgeChunk entity = new AgentKnowledgeChunk();
            entity.setDocId(document.getId());
            entity.setChunkIndex(chunk.index());
            entity.setHeading(chunk.heading());
            entity.setContent(chunk.content());
            entity.setKeywords(extractKeywords(chunk.content()));

            float[] vector = embeddingService.embed(chunk.content(), false);
            if (vector != null) {
                entity.setEmbedding(toJson(vector));
                entity.setEmbeddingModel(rag.getEmbeddingModelName());
                entity.setEmbeddingDim(vector.length);
                embedded++;
            }
            knowledgeMapper.insertChunk(entity);
        }

        document.setChunkCount(chunks.size());
        knowledgeMapper.updateDocumentStats(document.getId(), chunks.size(), content.length());
        log.info("知识库文档导入完成: id={}, title={}, chunks={}, embedded={}",
                document.getId(), document.getTitle(), chunks.size(), embedded);
        return document;
    }

    /**
     * 为历史上没有向量的分块补算向量（例如先导入文档、后补上模型文件）。
     *
     * @return 本次补算数量
     */
    @Transactional
    public int reindexEmbeddings() {
        if (!embeddingService.isAvailable()) {
            throw new BusinessException("中文向量模型不可用，无法补算向量");
        }
        int updated = 0;
        for (AgentKnowledgeDocument doc : knowledgeMapper.findDocuments(500)) {
            for (AgentKnowledgeChunk chunk : knowledgeMapper.findChunksByDoc(doc.getId())) {
                if (chunk.getEmbedding() != null) {
                    continue;
                }
                float[] vector = embeddingService.embed(chunk.getContent(), false);
                if (vector == null) {
                    continue;
                }
                knowledgeMapper.updateChunkEmbedding(chunk.getId(), toJson(vector),
                        properties.getRag().getEmbeddingModelName(), vector.length);
                updated++;
            }
        }
        log.info("向量补算完成: {} 个分块", updated);
        return updated;
    }

    // ================================================================
    // 查询
    // ================================================================

    public List<AgentKnowledgeDocument> list(int limit) {
        return knowledgeMapper.findDocuments(Math.min(Math.max(limit, 1), 200));
    }

    @Transactional
    public void delete(Long docId) {
        AgentKnowledgeDocument document = knowledgeMapper.findDocumentById(docId);
        if (document == null) {
            throw new NotFoundException("文档不存在");
        }
        knowledgeMapper.softDeleteDocument(docId);
        knowledgeMapper.deleteChunksByDoc(docId);
        log.info("知识库文档已删除: id={}, title={}", docId, document.getTitle());
    }

    public boolean isVectorSearchAvailable() {
        return properties.getRag().isEnabled() && embeddingService.isAvailable();
    }

    /**
     * 混合检索。
     * <p>
     * 融合策略不用纯 RRF，而是「归一化后取较强者 + 双路命中奖励」：
     * <pre>
     *   lexNorm = MySQL 相关度 / 本次最高相关度      （0~1）
     *   base    = max(lexNorm, similarity * 0.9)     （取更强的证据）
     *   bonus   = 两路都命中 ? +0.15 : 0             （双重佐证更可信）
     * </pre>
     * 原因：纯 RRF 只看名次，会在「全文第一但相关度很低」和「全文第五但相关度极高」
     * 之间判错。用归一化分数能保留「差多少」的信息，同时仍避免不同量纲硬加。
     *
     * @return 命中的分块，按融合分排序；空列表表示知识库没有相关内容
     */
    public List<KnowledgeHitVO> retrieve(String query, int topK) {
        if (!properties.getRag().isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(topK, properties.getRag().getTopK()));

        List<KnowledgeHitVO> lexical = safeFulltext(query, 20);
        List<KnowledgeHitVO> semantic = safeVectorSearch(query, 20);

        Map<Long, KnowledgeHitVO> hits = new LinkedHashMap<>();
        Map<Long, Double> scores = new HashMap<>();

        double maxLexical = lexical.stream()
                .mapToDouble(h -> h.getScore() == null ? 0 : h.getScore())
                .max().orElse(0);

        for (KnowledgeHitVO hit : lexical) {
            hits.putIfAbsent(hit.getChunkId(), hit);
            double norm = maxLexical <= 0 ? 0 : (hit.getScore() == null ? 0 : hit.getScore()) / maxLexical;
            scores.merge(hit.getChunkId(), norm, Math::max);
        }
        for (KnowledgeHitVO hit : semantic) {
            hits.putIfAbsent(hit.getChunkId(), hit);
            double sim = hit.getSimilarity() == null ? 0 : hit.getSimilarity();
            double base = sim * 0.9;
            scores.merge(hit.getChunkId(), base, Math::max);
        }
        // 两路都命中的分块加一点奖励
        for (KnowledgeHitVO hit : lexical) {
            boolean alsoSemantic = semantic.stream()
                    .anyMatch(s -> s.getChunkId().equals(hit.getChunkId()));
            if (alsoSemantic) {
                scores.merge(hit.getChunkId(), 0.15, Double::sum);
            }
        }

        List<KnowledgeHitVO> result = new ArrayList<>(hits.values());
        result.sort(Comparator.comparingDouble(
                (KnowledgeHitVO h) -> scores.getOrDefault(h.getChunkId(), 0.0)).reversed());

        List<KnowledgeHitVO> top = result.stream().limit(limit).toList();
        log.info("知识库检索: query='{}', 全文={}, 向量={}, 融合后取 {}",
                abbreviate(query), lexical.size(), semantic.size(), top.size());
        return top;
    }

    /** 拼成注入提示词的资料块。 */
    public String buildContext(List<KnowledgeHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        int max = properties.getRag().getMaxContextChars();
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (KnowledgeHitVO hit : hits) {
            String block = "【资料" + index + "】来源：《" + hit.getDocTitle() + "》"
                    + (hit.getHeading() == null || hit.getHeading().isBlank() ? "" : " / " + hit.getHeading())
                    + "\n" + hit.getContent() + "\n\n";
            if (sb.length() + block.length() > max) {
                break;
            }
            sb.append(block);
            index++;
        }
        return sb.toString().trim();
    }

    // ================================================================
    // 内部
    // ================================================================

    private List<KnowledgeHitVO> safeFulltext(String query, int limit) {
        try {
            // ngram 分词对超长查询不友好，截断到 100 字
            String q = query.length() > 100 ? query.substring(0, 100) : query;
            return knowledgeMapper.searchFulltext(q, limit);
        } catch (Exception e) {
            log.warn("全文检索失败（忽略该路召回）: {}", e.getMessage());
            return List.of();
        }
    }

    private List<KnowledgeHitVO> safeVectorSearch(String query, int limit) {
        if (!isVectorSearchAvailable()) {
            return List.of();
        }
        try {
            float[] queryVector = embeddingService.embed(query, true);
            if (queryVector == null) {
                return List.of();
            }
            List<AgentKnowledgeChunk> chunks = knowledgeMapper.findChunksWithEmbedding(5000);
            List<KnowledgeHitVO> scored = new ArrayList<>();
            for (AgentKnowledgeChunk chunk : chunks) {
                float[] vector = parseVector(chunk.getEmbedding());
                double similarity = OnnxEmbeddingService.cosine(queryVector, vector);
                if (similarity < properties.getRag().getMinSimilarity()) {
                    continue;
                }
                KnowledgeHitVO hit = new KnowledgeHitVO();
                hit.setChunkId(chunk.getId());
                hit.setDocId(chunk.getDocId());
                hit.setDocTitle(chunk.getDocTitle());
                hit.setHeading(chunk.getHeading());
                hit.setContent(chunk.getContent());
                hit.setSimilarity(similarity);
                scored.add(hit);
            }
            scored.sort(Comparator.comparingDouble(KnowledgeHitVO::getSimilarity).reversed());
            return scored.stream().limit(limit).toList();
        } catch (Exception e) {
            log.warn("向量检索失败（忽略该路召回）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 抽取关键词：取中文 2-4 字词组与英文标识符。
     * <p>
     * 这里不做真正的分词（那要再引一套词典），只作为全文检索的辅助字段与展示用。
     * 真正决定召回的是 MySQL 的 ngram 索引。
     */
    private String extractKeywords(String content) {
        Map<String, Integer> freq = new HashMap<>();
        java.util.regex.Matcher en = java.util.regex.Pattern
                .compile("[A-Za-z][A-Za-z0-9_\\-]{2,}")
                .matcher(content);
        while (en.find()) {
            freq.merge(en.group().toLowerCase(), 1, Integer::sum);
        }
        java.util.regex.Matcher zh = java.util.regex.Pattern
                .compile("[\\u4e00-\\u9fff]{2,4}")
                .matcher(content);
        while (zh.find()) {
            freq.merge(zh.group(), 1, Integer::sum);
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(12)
                .map(Map.Entry::getKey)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            return null;
        }
    }

    private float[] parseVector(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }
}
