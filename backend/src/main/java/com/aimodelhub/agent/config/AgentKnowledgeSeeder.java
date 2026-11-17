package com.aimodelhub.agent.config;

import com.aimodelhub.agent.service.AgentKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 内置知识库初始化。
 *
 * <h3>为什么需要它</h3>
 * 新装环境如果知识库是空的，RAG 这条能力等于不存在——用户问「这个平台怎么用」，
 * Agent 只能泛泛而谈。把平台真实的操作手册、输入规范、排查手册随应用一起打包，
 * 首次启动自动导入，才谈得上「开箱可用」。
 *
 * <h3>三条安全约束</h3>
 * <ol>
 *   <li><b>只在知识库为空时导入</b>：不会覆盖管理员后来上传或删除的内容；</li>
 *   <li><b>导入失败不阻断启动</b>：知识库是增强能力，不该让平台起不来；</li>
 *   <li><b>内容来自 classpath</b>：随代码一起走版本管理，可审计，不是运行时抓取。</li>
 * </ol>
 */
@Component
public class AgentKnowledgeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentKnowledgeSeeder.class);

    private static final String PATTERN = "classpath*:knowledge/*.md";

    private final AgentKnowledgeService knowledgeService;
    private final AgentProperties properties;

    public AgentKnowledgeSeeder(AgentKnowledgeService knowledgeService, AgentProperties properties) {
        this.knowledgeService = knowledgeService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getRag().isEnabled()) {
            return;
        }
        try {
            if (!knowledgeService.list(1).isEmpty()) {
                log.debug("知识库已有内容，跳过内置文档导入");
                return;
            }
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(PATTERN);
            if (resources.length == 0) {
                log.info("未找到内置知识库文档（{}），知识库将保持为空", PATTERN);
                return;
            }
            Arrays.sort(resources, Comparator.comparing(r -> String.valueOf(r.getFilename())));

            int imported = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                knowledgeService.ingest(null, stripOrderPrefix(filename), "内置文档", filename, content);
                imported++;
            }
            log.info("内置知识库文档导入完成，共 {} 篇", imported);
        } catch (Exception e) {
            // 知识库不可用不应阻断应用启动
            log.warn("内置知识库文档导入失败（不影响平台其它功能）: {}", e.getMessage());
        }
    }

    /** {@code 01-模型选型指南.md} → {@code 模型选型指南} */
    private static String stripOrderPrefix(String filename) {
        if (filename == null) {
            return "未命名文档";
        }
        String name = filename.replaceAll("\\.md$", "");
        return name.replaceFirst("^\\d+[-_]", "");
    }
}
