package com.aimodelhub.agent.service;

import com.aimodelhub.agent.config.AgentProperties;
import com.aimodelhub.agent.entity.AgentMessage;
import com.aimodelhub.agent.entity.AgentTask;
import com.aimodelhub.agent.entity.AgentSession;
import com.aimodelhub.agent.enums.MessageRole;
import com.aimodelhub.agent.memory.RedisChatMemoryRepository;
import com.aimodelhub.agent.support.AgentPrompts;
import com.aimodelhub.agent.tool.AgentToolContext;
import com.aimodelhub.agent.tool.AgentToolKit;
import com.aimodelhub.agent.vo.AgentChatResponse;
import com.aimodelhub.agent.vo.AgentEvent;
import com.aimodelhub.agent.vo.KnowledgeHitVO;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.NotFoundException;
import com.aimodelhub.common.exception.RateLimitException;
import com.aimodelhub.common.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Agent 对话编排。
 *
 * <h3>一轮对话发生了什么（按实际执行顺序）</h3>
 * <ol>
 *   <li>校验输入长度、校验 API Key 是否配置</li>
 *   <li>定位会话：前端没传 {@code sessionId} 就新建一个</li>
 *   <li>上下文自愈：Redis 窗口为空（首次对话 / 重启 / TTL 过期）时，从 MySQL 回填最近 N 条</li>
 *   <li>把用户消息<b>先</b>落库（即使模型调用失败，用户也确实说过这句话）</li>
 *   <li>扣减当日配额</li>
 *   <li>调 {@link ChatClient}：System Prompt + 本次用户消息 + 历史消息（由 Advisor 自动注入）</li>
 *   <li>流式转发 token；结束后把助手回复落库，并推送 {@code done} 事件</li>
 * </ol>
 *
 * <h3>关键设计：为什么流式和非流式共用一个方法</h3>
 * 两个入口如果各写一遍，迟早会出现「流式能带上历史、非流式忘了带」这类不一致。
 * 所以 {@link #stream} 是唯一实现，{@link #chatSync} 只是把同一串事件收集起来。
 */
@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);

    private static final String PLACEHOLDER_KEY = "sk-not-configured";

    /** 兜底文案：不区分具体原因时用它，绝不拼接原始异常信息。 */
    private static final String GENERIC_ERROR = "助手暂时不可用，请稍后重试";

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final ChatMemory chatMemory;
    private final AgentSessionService sessionService;
    private final RedisChatMemoryRepository memoryRepository;
    private final AgentQuotaService quotaService;
    private final AgentToolKit toolKit;
    private final AgentTaskService taskService;
    private final AgentKnowledgeService knowledgeService;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public AgentChatService(ChatClient chatClient,
                            MessageChatMemoryAdvisor memoryAdvisor,
                            ChatMemory chatMemory,
                            AgentSessionService sessionService,
                            RedisChatMemoryRepository memoryRepository,
                            AgentQuotaService quotaService,
                            AgentToolKit toolKit,
                            AgentTaskService taskService,
                            AgentKnowledgeService knowledgeService,
                            AgentProperties properties,
                            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.memoryAdvisor = memoryAdvisor;
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
        this.memoryRepository = memoryRepository;
        this.quotaService = quotaService;
        this.toolKit = toolKit;
        this.taskService = taskService;
        this.knowledgeService = knowledgeService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ================================================================
    // 对外入口
    // ================================================================

    /**
     * 流式对话：返回事件流，由 Controller 写入 SSE。
     * <p>
     * {@link Flux#defer} 让「准备阶段」（校验密钥、定位会话、扣配额）也发生在订阅之后。
     * 这里的 {@code onErrorResume} 是**最外层的兜底**：准备阶段的异常还没进入
     * 内层的 token 流，只有在这一层才能被转成 {@code error} 事件。
     * <p>
     * 内层（{@code doStream} 末尾）也有一处 {@code onErrorResume}，负责模型流式输出
     * 中途失败的情况。两个分支互斥，一次失败只会产生**一个** error 事件。
     */
    public Flux<AgentEvent> stream(Long userId, Long sessionId, String rawMessage) {
        return Flux.defer(() -> doStream(userId, sessionId, rawMessage))
                .onErrorResume(e -> {
                    // 日志留全栈，响应只给用户能看懂的那一句
                    log.error("Agent 对话失败（准备阶段）: userId={}, sessionId={}", userId, sessionId, e);
                    return Flux.just(AgentEvent.error(toUserMessage(e)));
                });
    }

    /**
     * 非流式对话：内部复用 {@link #stream}，把 token 拼成完整文本。
     * 主要用于自动化测试与不支持 SSE 的客户端。
     */
    public AgentChatResponse chatSync(Long userId, Long sessionId, String rawMessage) {
        List<AgentEvent> events = stream(userId, sessionId, rawMessage)
                .collectList()
                .block(Duration.ofMillis(properties.getSseTimeoutMs() + 30_000L));

        if (events == null) {
            throw new BusinessException("模型响应超时，请稍后重试");
        }

        StringBuilder text = new StringBuilder();
        AgentChatResponse response = new AgentChatResponse();
        for (AgentEvent event : events) {
            switch (event.getType()) {
                case "session" -> response.setSessionId(asLong(event.getData().get("sessionId")));
                case "token" -> text.append(event.getData().get("text"));
                case "done" -> {
                    response.setMessageId(asLong(event.getData().get("messageId")));
                    response.setSessionId(asLong(event.getData().get("sessionId")));
                    response.setTokenCount(asInt(event.getData().get("tokenCount")));
                    response.setElapsedMs(asLong(event.getData().get("elapsedMs")));
                }
                case "error" -> throw new BusinessException(String.valueOf(event.getData().get("message")));
                default -> {
                    // 其它事件（未来的 tool_call / recommend）在同步接口里忽略
                }
            }
        }
        response.setContent(text.toString());
        return response;
    }

    // ================================================================
    // 核心流程
    // ================================================================

    private Flux<AgentEvent> doStream(Long userId, Long sessionId, String rawMessage) {
        assertApiKeyConfigured();

        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) {
            throw new BusinessException("消息内容不能为空");
        }
        if (message.length() > properties.getMaxInputChars()) {
            throw new BusinessException("消息过长（上限 " + properties.getMaxInputChars() + " 字符），请分段提问");
        }

        // 1) 定位会话：没有就新建。归属校验在 requireOwned 里做（他人会话 → 404）
        AgentSession session = sessionId == null
                ? sessionService.create(userId, null)
                : sessionService.requireOwned(userId, sessionId);
        String conversationId = String.valueOf(session.getId());

        // 2) 上下文自愈 —— 必须在写入本轮用户消息之前，
        //    否则回填的历史里会包含本轮提问，模型会看到两遍同样的话
        restoreContextIfNeeded(userId, session.getId(), conversationId);

        // 3) 用户消息先落库
        sessionService.append(session.getId(), userId, MessageRole.USER, message);

        // 4) 配额
        quotaService.checkAndConsume(userId);

        // 5) 标题可能刚被首条消息改写，回读一次用于会话列表展示
        AgentSession refreshed = sessionService.requireOwned(userId, session.getId());

        long start = System.currentTimeMillis();
        StringBuilder buffer = new StringBuilder();

        // 工具上下文：身份固定在对象里，工具方法不接受 userId 参数（防越权）
        AgentToolContext toolContext = new AgentToolContext(userId, session.getId());
        ToolCallback[] toolCallbacks = ToolCallbacks.from(toolKit.build(toolContext));

        Flux<AgentEvent> tokens = chatClient.prompt()
                .system(buildSystemPrompt(message))
                .user(message)
                // Spring AI 负责：把 @Tool 方法反射成 OpenAI function schema、
                // 解析模型的 tool_calls、反射调用 Java 方法、把结果回灌给模型继续生成。
                // 我们只提供「工具对象」和「本地上下文」。
                .toolCallbacks(toolCallbacks)
                .advisors(spec -> spec
                        .advisors(memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                // 看门狗：连续 idleTimeoutSeconds 秒没有新 token 就判定卡死。
                // 放在 content() 之后、map 之前，超时同样会走 onErrorResume 分支。
                .timeout(Duration.ofSeconds(properties.getIdleTimeoutSeconds()))
                .doOnNext(buffer::append)
                .map(AgentEvent::token);

        return Flux.concat(
                        Flux.just(AgentEvent.session(refreshed.getId(), refreshed.getTitle())),
                        tokens,
                        Flux.defer(() -> {
                            String answer = buffer.toString();
                            AgentMessage saved = sessionService.append(
                                    session.getId(), userId, MessageRole.ASSISTANT, answer);
                            // 工具调用留痕：写入 agent_message(role=TOOL)，供审计与历史回放
                            persistToolTrail(session.getId(), userId, toolContext);

                            long elapsed = System.currentTimeMillis() - start;
                            log.info("Agent turn done: sessionId={}, userId={}, chars={}, tools={}, {}ms",
                                    session.getId(), userId, answer.length(),
                                    toolContext.getInvocations().size(), elapsed);

                            List<AgentEvent> tail = new ArrayList<>();
                            for (AgentToolContext.ToolInvocation invocation : toolContext.getInvocations()) {
                                tail.add(AgentEvent.toolResult(invocation.toolName(), invocation.success(),
                                        invocation.elapsedMs(), invocation.resultSummary()));
                            }
                            if (!toolContext.getRecommendations().isEmpty()) {
                                tail.add(AgentEvent.recommend(toolContext.getRecommendations()));
                            }
                            // 本轮若创建了待确认任务，推一张「确认执行」卡片给前端。
                            // 任务详情回查数据库，避免把 LLM 的说法当成事实。
                            Long pendingTaskId = toolContext.getPendingTaskId();
                            if (pendingTaskId != null) {
                                AgentTask task = taskService.findByIdAndUser(userId, pendingTaskId);
                                if (task != null) {
                                    tail.add(AgentEvent.confirmRequired(task.getId(), task.getTaskNo(),
                                            task.getModelName(), task.getInputType(), task.getInputSummary()));
                                }
                            }
                            tail.add(AgentEvent.done(session.getId(), saved.getId(),
                                    saved.getTokenCount(), elapsed));
                            return Flux.fromIterable(tail);
                        }))
                .onErrorResume(e -> {
                    log.error("Agent 对话失败: sessionId={}, userId={}", session.getId(), userId, e);
                    return Flux.just(AgentEvent.error(toUserMessage(e)));
                });
    }

    /**
     * 恢复上下文窗口。
     * <p>
     * Redis 里的窗口可能因为三种原因为空：新会话、Redis 重启、TTL 到期。
     * 如果此时不做处理，用户会发现「前面聊的内容模型全忘了」——
     * 明明历史消息还在 MySQL 里躺着。
     * <p>
     * 所以这里用 MySQL 的最近 N 条纯对话把窗口重新灌满。
     * 只回填 USER/ASSISTANT 文本：工具调用消息需要完整的 tool_call_id 链路，
     * 脱离原始往返重建容易让模型困惑，宁可少一点上下文。
     */
    private void restoreContextIfNeeded(Long userId, Long sessionId, String conversationId) {
        if (!memoryRepository.isEmpty(conversationId)) {
            return;
        }
        List<AgentMessage> history = sessionService.recentDialogue(
                sessionId, userId, properties.getMaxContextMessages());
        if (history.isEmpty()) {
            return;
        }
        List<Message> messages = new ArrayList<>(history.size());
        for (AgentMessage m : history) {
            if (MessageRole.USER.name().equals(m.getRole())) {
                messages.add(new UserMessage(m.getContent()));
            } else if (MessageRole.ASSISTANT.name().equals(m.getRole())) {
                messages.add(new AssistantMessage(m.getContent()));
            }
        }
        if (!messages.isEmpty()) {
            chatMemory.add(conversationId, messages);
            log.info("上下文已从 MySQL 回填: sessionId={}, messages={}", sessionId, messages.size());
        }
    }

    /**
     * 把本轮的工具调用写入 {@code agent_message}（role=TOOL）。
     * <p>
     * 存的是「摘要 + 结构化入参」，不是完整返回体：
     * 完整返回可能几百 KB（例如一次模型清单），存进消息表既浪费空间，
     * 也会在历史回放时让人误以为这些是对话内容。
     * 这些记录不参与上下文重建（{@code findRecentDialogueDesc} 会过滤掉 TOOL）。
     */
    private void persistToolTrail(Long sessionId, Long userId, AgentToolContext toolContext) {
        for (AgentToolContext.ToolInvocation invocation : toolContext.getInvocations()) {
            try {
                AgentMessage toolMessage = AgentMessage.of(
                        sessionId, userId, MessageRole.TOOL, invocation.resultSummary());
                toolMessage.setToolName(invocation.toolName());
                toolMessage.setToolPayload(objectMapper.writeValueAsString(java.util.Map.of(
                        "arguments", invocation.arguments() == null ? "" : invocation.arguments(),
                        "success", invocation.success(),
                        "elapsedMs", invocation.elapsedMs())));
                sessionService.appendEntity(toolMessage);
            } catch (Exception e) {
                // 审计留痕失败不应影响本轮回答
                log.warn("工具调用记录写入失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 组装系统提示词：人设 + 本轮检索到的知识库资料。
     * <p>
     * <b>为什么预检索一次，而不是全靠工具</b>：
     * 工具调用要多一轮往返（多一次模型调用），首轮响应会慢 1~2 秒。
     * 用户问「这个模型怎么用」时，直接带上资料能一次答完；
     * 追问到别的方向时，模型还可以用 searchKnowledgeBase 自己补查。
     * <p>
     * 没检索到就不注入任何内容——绝不能让模型把「资料为空」误读成「平台没有这个功能」。
     */
    private String buildSystemPrompt(String question) {
        try {
            List<KnowledgeHitVO> hits = knowledgeService.retrieve(question, 5);
            String context = knowledgeService.buildContext(hits);
            if (context.isBlank()) {
                return AgentPrompts.SYSTEM_BASE;
            }
            return AgentPrompts.SYSTEM_BASE + KNOWLEDGE_TEMPLATE.formatted(context);
        } catch (Exception e) {
            log.warn("知识库预检索失败，本轮不带资料: {}", e.getMessage());
            return AgentPrompts.SYSTEM_BASE;
        }
    }

    /** 知识库资料注入模板（抽成常量，避免每次请求重复拼接字符串字面量）。 */
    private static final String KNOWLEDGE_TEMPLATE = """

            ## 本轮检索到的平台知识库资料
            以下内容来自平台文档库，可作为回答依据：

            %s

            ## 使用这些资料的要求
            1. 资料只用于说明「怎么用、有什么限制、常见问题」；
               模型名称、在线状态、输入字段等事实仍须以工具返回为准。
            2. 引用时说明来源文档，例如「按《xxx》说明」。
            3. 资料与工具结果冲突时，以工具结果为准，并提示用户文档可能过期。
            4. 资料中没有答案时，明确说「知识库暂无相关资料」，不要据此编造。
            """;

    /** 没配 Key 时给出可操作的提示，而不是让用户看到 401。 */
    private void assertApiKeyConfigured() {
        if (apiKey == null || apiKey.isBlank() || PLACEHOLDER_KEY.equals(apiKey)) {
            // 具体做法写日志给运维，前端只说「没配置」——环境变量名不该出现在聊天窗口里
            log.warn("Agent 未配置大模型密钥：请设置环境变量 DEEPSEEK_API_KEY 后重启后端");
            throw new BusinessException("AI 助理尚未配置大模型密钥，请联系管理员");
        }
    }

    /**
     * 把底层异常翻译成用户能看懂、且不泄露内部信息的一句话。
     * <p>
     * 原则：
     * <ol>
     *   <li>自己抛的业务异常——文案本来就是写给用户看的，直接透传；</li>
     *   <li>上游 HTTP 状态码——只说「认证失败 / 频率超限 / 余额不足 / 暂不可用」；</li>
     *   <li>其他一切——统一兜底，<b>不拼接 e.getMessage()</b>，避免把堆栈里的
     *       URL、SQL、内部路径漏给前端。</li>
     * </ol>
     */
    private String toUserMessage(Throwable e) {
        if (e == null) {
            return GENERIC_ERROR;
        }
        // 1) 业务异常：Message 是我们自己写的，可以直接给用户
        if (e instanceof NotFoundException) {
            return "会话不存在或已失效";
        }
        if (e instanceof UnauthorizedException) {
            return "登录状态已失效，请重新登录";
        }
        if (e instanceof RateLimitException) {
            return e.getMessage() == null ? "操作过于频繁，请稍后重试" : e.getMessage();
        }
        if (e instanceof BusinessException) {
            return e.getMessage() == null ? GENERIC_ERROR : e.getMessage();
        }

        // 2) 超时：Reactor 的 timeout 抛 TimeoutException，WebClient 抛读超时
        if (e instanceof TimeoutException || e instanceof SocketTimeoutException) {
            return "大模型响应超时，请稍后重试";
        }

        // 3) 上游模型服务的 HTTP 错误
        if (e instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            if (status == 401 || status == 403) {
                return "大模型认证失败，请联系管理员检查密钥配置";
            }
            if (status == 402) {
                return "大模型账户余额不足，请联系管理员";
            }
            if (status == 429) {
                return "大模型调用频率已超限，请稍后重试";
            }
            if (status >= 500) {
                return "大模型服务暂时不可用，请稍后重试";
            }
            return "大模型服务返回异常（HTTP " + status + "），请稍后重试";
        }

        // 4) 工具执行失败：Spring AI 的工具异常类名里带 Tool，按类名判断避免绑死版本
        String type = e.getClass().getName();
        if (type.contains("ToolExecution") || type.contains("ToolCalling")) {
            return "助手调用工具时出错，请稍后重试";
        }

        // 5) 兜底：不拼接原始信息
        return GENERIC_ERROR;
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static Integer asInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }
}
