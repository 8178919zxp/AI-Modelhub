package com.aimodelhub.agent.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档切分器的单元测试。
 * <p>
 * 切分质量直接决定 RAG 效果：切太碎会丢上下文，切太大会稀释语义。
 * 这里钉住三条最容易回归的性质：按标题分节、块长受控、相邻块有重叠。
 */
@DisplayName("知识库文档切分")
class KnowledgeChunkerTest {

    @Test
    @DisplayName("按 Markdown 标题分节，标题写入 heading")
    void splitsByHeading() {
        String doc = """
                # 平台说明
                平台提供图像与文本模型。

                ## 输入要求
                图片不超过 5MB。

                ## 输出格式
                返回类别与置信度。
                """;
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.split(doc, 200, 20);

        assertFalse(chunks.isEmpty());
        assertEquals("平台说明", chunks.get(0).heading());
        assertTrue(chunks.stream().anyMatch(c -> "输入要求".equals(c.heading())));
        assertTrue(chunks.stream().anyMatch(c -> "输出格式".equals(c.heading())));
        assertTrue(chunks.stream().allMatch(c -> !c.content().isBlank()));
    }

    @Test
    @DisplayName("长文本被切成多块且块长不超过上限（允许句子边界带来的少量溢出）")
    void respectsChunkSize() {
        StringBuilder sb = new StringBuilder("# 长文档\n");
        for (int i = 0; i < 60; i++) {
            sb.append("这是第").append(i).append("句用于测试切分的内容，长度大致相同。");
        }
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.split(sb.toString(), 300, 50);

        assertTrue(chunks.size() > 3, "60 句应当被切成多块，实际=" + chunks.size());
        for (KnowledgeChunker.Chunk chunk : chunks) {
            assertTrue(chunk.content().length() <= 320,
                    "块长应接近上限，实际=" + chunk.content().length());
        }
        // 序号连续，便于按序回放
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
        }
    }

    @Test
    @DisplayName("相邻块存在重叠，避免答案被切断在边界")
    void hasOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("甲").append(i).append("乙丙丁戊己庚辛壬癸。");
        }
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.split(sb.toString(), 120, 40);

        assertTrue(chunks.size() >= 2);
        for (int i = 1; i < chunks.size(); i++) {
            String prevTail = tail(chunks.get(i - 1).content(), 12);
            String current = chunks.get(i).content();
            assertTrue(current.contains(prevTail.substring(Math.max(0, prevTail.length() - 4))),
                    "第 " + i + " 块应包含前一块尾部内容（重叠）");
        }
    }

    @Test
    @DisplayName("空文档返回空列表，不抛异常")
    void emptyInput() {
        assertTrue(KnowledgeChunker.split("", 200, 20).isEmpty());
        assertTrue(KnowledgeChunker.split(null, 200, 20).isEmpty());
    }

    private static String tail(String text, int n) {
        return text.length() <= n ? text : text.substring(text.length() - n);
    }
}
