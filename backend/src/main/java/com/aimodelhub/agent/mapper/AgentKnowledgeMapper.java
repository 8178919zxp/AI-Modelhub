package com.aimodelhub.agent.mapper;

import com.aimodelhub.agent.entity.AgentKnowledgeDocument;
import com.aimodelhub.agent.vo.KnowledgeHitVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档与分块的 Mapper（合并成一个接口，避免两个 XML 都只有三五行）。
 *
 * <h3>两种检索方式并存</h3>
 * <ul>
 *   <li>{@link #searchFulltext}：MySQL FULLTEXT + ngram 分词，中文可用，走倒排索引，快；</li>
 *   <li>{@link #findChunksWithEmbedding}：把带向量的分块取出来，在应用层算余弦相似度。</li>
 * </ul>
 * 后者不做 SQL 排序，是因为 MySQL 8 没有原生向量距离函数，
 * 而我们刻意不引入向量数据库（分块规模小，暴力算完全够用）。
 */
@Mapper
public interface AgentKnowledgeMapper {

    // ── 文档 ──────────────────────────────────────────────

    int insertDocument(AgentKnowledgeDocument document);

    AgentKnowledgeDocument findDocumentById(@Param("id") Long id);

    List<AgentKnowledgeDocument> findDocuments(@Param("limit") int limit);

    int updateDocumentStats(@Param("id") Long id,
                            @Param("chunkCount") int chunkCount,
                            @Param("charCount") int charCount);

    int softDeleteDocument(@Param("id") Long id);

    // ── 分块 ──────────────────────────────────────────────

    int insertChunk(@Param("chunk") com.aimodelhub.agent.entity.AgentKnowledgeChunk chunk);

    int deleteChunksByDoc(@Param("docId") Long docId);

    List<com.aimodelhub.agent.entity.AgentKnowledgeChunk> findChunksByDoc(@Param("docId") Long docId);

    /** 取出所有带向量的分块（含文档标题），用于应用层相似度计算。 */
    List<com.aimodelhub.agent.entity.AgentKnowledgeChunk> findChunksWithEmbedding(@Param("limit") int limit);

    /** 中文全文检索。 */
    List<KnowledgeHitVO> searchFulltext(@Param("q") String query, @Param("limit") int limit);

    /** 为单个分块补写向量（不重建整篇文档）。 */
    int updateChunkEmbedding(@Param("id") Long id,
                             @Param("embedding") String embedding,
                             @Param("model") String model,
                             @Param("dim") Integer dim);

    int countChunks();
}
