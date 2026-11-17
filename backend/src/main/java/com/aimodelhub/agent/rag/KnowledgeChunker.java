package com.aimodelhub.agent.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档切分器。
 *
 * <h3>为什么不能简单按固定长度切</h3>
 * 固定长度会把「## 输入要求」和它下面的说明切到两个分块里，
 * 检索命中后半截时，模型看到的是没有上下文的半句话。
 * 所以这里先按 Markdown 标题分「节」，再在节内按句子边界切块并保留重叠。
 *
 * <h3>重叠（overlap）的作用</h3>
 * 答案经常横跨句子边界，例如「ResNet-50 支持 1000 类……｜……输入为单张图片」。
 * 相邻块重叠 60 字，能保证至少有一块同时包含前后两句。
 */
public final class KnowledgeChunker {

    /** Markdown / 纯文本标题行：以 1~6 个 # 开头 */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s*(.+)$");

    /** 句子边界：中文句号、问号、叹号、分号 + 换行 */
    private static final Pattern SENTENCE_END = Pattern.compile("[。！？；\n]");

    private KnowledgeChunker() {
    }

    public record Chunk(int index, String heading, String content) {
    }

    /**
     * 切分文档。
     *
     * @param text      原文（Markdown 或纯文本）
     * @param chunkSize 目标块大小（字符）
     * @param overlap   相邻块重叠（字符），必须小于 chunkSize
     */
    public static List<Chunk> split(String text, int chunkSize, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int safeSize = Math.max(120, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeSize / 2));

        for (Section section : sections(text)) {
            String body = section.body().trim();
            if (body.isEmpty()) {
                continue;
            }
            int start = 0;
            while (start < body.length()) {
                int end = Math.min(body.length(), start + safeSize);
                if (end < body.length()) {
                    end = lastSentenceBoundary(body, start, end);
                }
                String piece = body.substring(start, end).trim();
                if (!piece.isEmpty()) {
                    chunks.add(new Chunk(chunks.size(), section.heading(), piece));
                }
                if (end >= body.length()) {
                    break;
                }
                start = Math.max(end - safeOverlap, start + 1);
            }
        }
        return chunks;
    }

    /** 按标题切节；没有标题的文档整体作为一节，heading 为空。 */
    private static List<Section> sections(String text) {
        List<Section> sections = new ArrayList<>();
        String currentHeading = "";
        StringBuilder buffer = new StringBuilder();

        for (String rawLine : text.replace("\r\n", "\n").split("\n")) {
            Matcher matcher = HEADING.matcher(rawLine.trim());
            if (matcher.matches()) {
                if (buffer.toString().trim().length() > 0) {
                    sections.add(new Section(currentHeading, buffer.toString()));
                    buffer.setLength(0);
                }
                currentHeading = matcher.group(2).trim();
            } else {
                buffer.append(rawLine).append('\n');
            }
        }
        if (buffer.toString().trim().length() > 0) {
            sections.add(new Section(currentHeading, buffer.toString()));
        }
        return sections;
    }

    /** 在 [start, end] 内找最后一个句末标点，找不到就硬切。 */
    private static int lastSentenceBoundary(String body, int start, int end) {
        int best = -1;
        Matcher matcher = SENTENCE_END.matcher(body.substring(start, end));
        while (matcher.find()) {
            best = start + matcher.end();
        }
        // 句末离块尾太远（< 40%）就干脆按长度切，避免块过短
        if (best > start + (end - start) * 2 / 5) {
            return best;
        }
        return end;
    }

    private record Section(String heading, String body) {
    }
}
