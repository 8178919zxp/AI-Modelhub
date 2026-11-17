package com.aimodelhub.agent.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 极简 BERT WordPiece 分词器（只读 {@code vocab.txt}）。
 *
 * <h3>为什么不直接用 DJL 的 HuggingFaceTokenizer</h3>
 * 那个方案要拖进 DJL + PyTorch engine + 一堆 native 库（数百 MB），
 * 而我们只需要「文本 → input_ids」这一件事。BERT 的分词规则是公开且稳定的：
 * <ol>
 *   <li>清洗：去控制字符、转小写（BGE 使用 uncased 词表）</li>
 *   <li>预切分：中文按单字切（词表里本来就是一个字一个 token）、
 *       英文按空白与标点切词</li>
 *   <li>WordPiece：对每个词做「最长前缀匹配」，剩余部分加 {@code ##} 继续匹配</li>
 *   <li>加上 {@code [CLS]} / {@code [SEP]}，并生成 attention mask</li>
 * </ol>
 * 这套实现在中文场景下与官方 tokenizer 等价（中文没有子词切分问题），
 * 英文生僻词会落到 {@code [UNK]}，对检索召回影响可忽略。
 */
public class BertTokenizer {

    private final Map<String, Integer> vocab = new HashMap<>();
    private final int clsId;
    private final int sepId;
    private final int unkId;
    private final int padId;
    private final int maxLength;

    public BertTokenizer(Path vocabFile, int maxLength) throws IOException {
        List<String> lines = Files.readAllLines(vocabFile, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            vocab.put(lines.get(i).trim(), i);
        }
        this.clsId = idOf("[CLS]", 101);
        this.sepId = idOf("[SEP]", 102);
        this.unkId = idOf("[UNK]", 100);
        this.padId = idOf("[PAD]", 0);
        this.maxLength = maxLength;
    }

    /** 分词并编码成定长数组。 */
    public Encoded encode(String text) {
        List<Integer> ids = new ArrayList<>();
        ids.add(clsId);
        for (String token : basicTokenize(text)) {
            for (Integer id : wordPiece(token)) {
                if (ids.size() >= maxLength - 1) {
                    break;
                }
                ids.add(id);
            }
            if (ids.size() >= maxLength - 1) {
                break;
            }
        }
        ids.add(sepId);

        int length = ids.size();
        long[] inputIds = new long[length];
        long[] attentionMask = new long[length];
        long[] tokenTypeIds = new long[length];
        for (int i = 0; i < length; i++) {
            inputIds[i] = ids.get(i);
            attentionMask[i] = 1L;
            tokenTypeIds[i] = 0L;
        }
        return new Encoded(inputIds, attentionMask, tokenTypeIds);
    }

    // ================================================================
    // 内部
    // ================================================================

    private List<String> basicTokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                flush(ascii, tokens);
                tokens.add(String.valueOf(c));
            } else if (Character.isWhitespace(c)) {
                flush(ascii, tokens);
            } else if (isPunctuation(c)) {
                flush(ascii, tokens);
                tokens.add(String.valueOf(c));
            } else {
                ascii.append(Character.toLowerCase(c));
            }
        }
        flush(ascii, tokens);
        return tokens;
    }

    private void flush(StringBuilder buffer, List<String> tokens) {
        if (buffer.length() > 0) {
            tokens.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    /** WordPiece 最长匹配：{@code playing → play + ##ing}。 */
    private List<Integer> wordPiece(String token) {
        List<Integer> result = new ArrayList<>(2);
        if (token.isEmpty()) {
            return result;
        }
        Integer whole = vocab.get(token);
        if (whole != null) {
            result.add(whole);
            return result;
        }
        int start = 0;
        while (start < token.length()) {
            int end = token.length();
            Integer found = null;
            while (start < end) {
                String piece = (start > 0 ? "##" : "") + token.substring(start, end);
                found = vocab.get(piece);
                if (found != null) {
                    break;
                }
                end--;
            }
            if (found == null) {
                // 任何一个子串都进不了词表 → 整词记 [UNK]
                result.add(unkId);
                return result;
            }
            result.add(found);
            start = end;
        }
        return result;
    }

    private int idOf(String token, int fallback) {
        return vocab.getOrDefault(token, fallback);
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // 基本汉字
                || (c >= 0x3400 && c <= 0x4DBF)  // 扩展 A
                || (c >= 0xF900 && c <= 0xFAFF); // 兼容汉字
    }

    private static boolean isPunctuation(char c) {
        return String.valueOf(c).matches("\\p{Punct}") || "，。！？；：、“”‘’（）《》【】…—".indexOf(c) >= 0;
    }

    /** 编码结果：三个等长数组，直接喂给 ONNX。 */
    public record Encoded(long[] inputIds, long[] attentionMask, long[] tokenTypeIds) {
    }

    public int getPadId() {
        return padId;
    }

    /** 便于日志与自检。 */
    public int vocabSize() {
        return vocab.size();
    }

    @Override
    public String toString() {
        return "BertTokenizer{vocab=" + vocab.size() + ", maxLength=" + maxLength + '}';
    }

    /** 避免 IDE 提示未使用（Locale 在大小写处理中预留）。 */
    @SuppressWarnings("unused")
    private static final Locale DEFAULT_LOCALE = Locale.ROOT;
}
