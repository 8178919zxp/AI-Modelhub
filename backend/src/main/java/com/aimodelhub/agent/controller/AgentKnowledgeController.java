package com.aimodelhub.agent.controller;

import com.aimodelhub.agent.entity.AgentKnowledgeDocument;
import com.aimodelhub.agent.service.AgentKnowledgeService;
import com.aimodelhub.agent.support.CurrentUser;
import com.aimodelhub.agent.vo.KnowledgeHitVO;
import com.aimodelhub.common.api.Result;
import com.aimodelhub.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 知识库管理接口。
 *
 * <h3>权限划分</h3>
 * <ul>
 *   <li>读取 / 检索：任意登录用户（用户有权知道平台文档怎么写的）</li>
 *   <li>上传 / 删除 / 重建向量：<b>仅管理员</b> —— 知识库内容会影响所有用户的回答，
 *       属于全局共享状态，不能让任意用户投毒</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/agent/knowledge")
public class AgentKnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(AgentKnowledgeController.class);

    /** 只接受纯文本类文档：PDF/Word 解析需要额外依赖，且容易引入不可控格式噪音 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("md", "markdown", "txt");

    private static final long MAX_DOC_SIZE = 2L * 1024 * 1024;

    private final AgentKnowledgeService knowledgeService;

    public AgentKnowledgeController(AgentKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** 上传并导入文档（管理员）。 */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> upload(@RequestPart("file") MultipartFile file,
                                              @RequestParam(value = "title", required = false) String title,
                                              @RequestParam(value = "category", required = false) String category) {
        CurrentUser.requireAdmin();
        Long userId = CurrentUser.requireId();

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的文档");
        }
        if (file.getSize() > MAX_DOC_SIZE) {
            throw new BusinessException("文档不能超过 2MB");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 Markdown / 纯文本（.md / .markdown / .txt）");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取文档失败，请重试");
        }

        AgentKnowledgeDocument document = knowledgeService.ingest(
                userId, title == null || title.isBlank() ? filename : title, category, filename, content);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", document.getId());
        data.put("title", document.getTitle());
        data.put("chunkCount", document.getChunkCount());
        data.put("charCount", document.getCharCount());
        return Result.ok(data);
    }

    /** 文档列表（登录用户）。 */
    @GetMapping("/documents")
    public Result<List<Map<String, Object>>> list() {
        CurrentUser.requireId();
        List<Map<String, Object>> items = knowledgeService.list(200).stream().map(doc -> {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", doc.getId());
            one.put("title", doc.getTitle());
            one.put("category", doc.getCategory());
            one.put("chunkCount", doc.getChunkCount());
            one.put("charCount", doc.getCharCount());
            one.put("createTime", doc.getCreateTime() == null ? null : doc.getCreateTime().toString());
            return one;
        }).toList();
        return Result.ok(items);
    }

    /** 删除文档（管理员）。 */
    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        CurrentUser.requireAdmin();
        knowledgeService.delete(id);
        return Result.ok(null);
    }

    /** 检索（登录用户）：便于验证召回质量，也给前端做「资料出处」展示。 */
    @GetMapping("/search")
    public Result<List<KnowledgeHitVO>> search(@RequestParam("q") String query,
                                               @RequestParam(value = "topK", defaultValue = "5") int topK) {
        CurrentUser.requireId();
        return Result.ok(knowledgeService.retrieve(query, topK));
    }

    /** 为缺向量的分块补算向量（管理员）。 */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        CurrentUser.requireAdmin();
        int updated = knowledgeService.reindexEmbeddings();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("embeddedChunks", updated);
        data.put("vectorSearchAvailable", knowledgeService.isVectorSearchAvailable());
        log.info("知识库向量重建完成: {}", data);
        return Result.ok(data);
    }
}
