package com.aimodelhub.agent.config;

import com.aimodelhub.agent.memory.RedisChatMemoryRepository;
import com.aimodelhub.agent.rag.OnnxEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Spring AI 装配。
 *
 * <h3>这里到底装配了什么</h3>
 * <pre>
 *   DeepSeek HTTP API
 *        ↑ 由 spring-ai-starter-model-openai 的自动配置创建 OpenAiChatModel
 *        ↑ （base-url / api-key / model 均来自 application.yml）
 *   ChatClient  ← 本类显式 build，业务代码只与它打交道
 *        ↑
 *   MessageChatMemoryAdvisor  ← 把「历史消息」自动拼进 prompt、把本轮问答写回记忆
 *        ↑
 *   ChatMemory → RedisChatMemoryRepository（Redis 存窗口，MySQL 存全量档案）
 * </pre>
 *
 * <b>分工要说清楚：</b>
 * <ul>
 *   <li>「怎么发 HTTP、怎么解析 SSE 流、怎么把 tools 序列化成 OpenAI 的 JSON」
 *       —— Spring AI 负责，我们一行都不写。</li>
 *   <li>「这个用户能看到哪些历史、上下文留多少条、存哪里、超时怎么处理」
 *       —— 我们的业务判断，写在 Service 里。</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAiConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentAiConfig.class);

    /** 与 yml 中 ${DEEPSEEK_API_KEY:...} 的占位默认值保持一致，用于启动自检。 */
    private static final String PLACEHOLDER_KEY = "sk-not-configured";

    /**
     * 上下文窗口的存储后端：Redis。
     * <p>
     * 注意这里<b>没有</b>用 Spring AI 自带的 {@code InMemoryChatMemoryRepository}：
     * 它把上下文放在 JVM 堆里，重启即失忆，多实例部署还会各说各话。
     */
    @Bean
    public RedisChatMemoryRepository agentChatMemoryRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AgentProperties properties) {
        return new RedisChatMemoryRepository(
                stringRedisTemplate,
                objectMapper,
                Duration.ofSeconds(properties.getContextTtlSeconds()),
                properties.getMaxContextMessages());
    }

    /**
     * ChatMemory = 「窗口策略」+「存储」。
     * <p>
     * {@link MessageWindowChatMemory} 的策略是：只保留最近 N 条消息。
     * 为什么必须有窗口？因为多轮对话是累加的，不裁剪的话第 50 轮会把前 49 轮全塞进 prompt，
     * 既烧钱又容易触发模型「注意力涣散」。
     */
    @Bean
    public ChatMemory agentChatMemory(RedisChatMemoryRepository repository, AgentProperties properties) {
        log.info("Agent ChatMemory: maxMessages={}, ttl={}s",
                properties.getMaxContextMessages(), properties.getContextTtlSeconds());
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.getMaxContextMessages())
                .build();
    }

    /**
     * 记忆顾问：在请求前把历史消息注入 prompt，在响应后把本轮问答写回记忆。
     * <p>
     * 会话 id 不在这里固定——每次请求通过
     * {@code advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))} 传入，
     * 因此这个 Bean 是单例、可被所有用户共享。
     */
    @Bean
    public MessageChatMemoryAdvisor agentMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * 全局唯一的 ChatClient。模型、温度、max-tokens 全部来自
     * {@code spring.ai.openai.chat.options}，不在代码里硬编码。
     */
    @Bean
    public ChatClient agentChatClient(ChatClient.Builder builder,
                                      @Value("${spring.ai.openai.api-key:}") String apiKey,
                                      @Value("${spring.ai.openai.chat.options.model:}") String model) {
        if (apiKey == null || apiKey.isBlank() || PLACEHOLDER_KEY.equals(apiKey)) {
            // 不抛异常：没配 Key 时平台其它功能仍应可用，只是 Agent 会给出明确提示
            log.warn("DEEPSEEK_API_KEY 未配置，Agent 对话将不可用。"
                    + "请设置环境变量 DEEPSEEK_API_KEY 后重启（配置方法见 README「常见问题」与 deploy/.env.example）。");
        } else {
            log.info("Agent ChatClient ready: model={}, baseUrl={}",
                    model, System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com"));
        }
        return builder.build();
    }

    /**
     * 本地中文向量模型（BGE-small-zh）。
     * <p>
     * 这里只构造对象、不加载模型：ONNX 会话在首次使用时初始化，
     * 避免「没用 RAG」的场景也被 24MB 模型拖慢启动。
     * 模型文件缺失时会在首次调用时打警告并降级为纯全文检索。
     */
    @Bean
    public OnnxEmbeddingService onnxEmbeddingService(AgentProperties properties) {
        AgentProperties.Rag rag = properties.getRag();
        OnnxEmbeddingService service = new OnnxEmbeddingService(
                rag.getEmbeddingModelPath(),
                rag.getEmbeddingVocabPath(),
                rag.getMaxSequenceLength(),
                rag.getEmbeddingModelName());
        if (!rag.isEnabled()) {
            log.info("RAG 已在配置中关闭（app.agent.rag.enabled=false），知识库将不参与检索");
        }
        return service;
    }
}
