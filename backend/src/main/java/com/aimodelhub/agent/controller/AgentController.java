package com.aimodelhub.agent.controller;

import com.aimodelhub.agent.config.AgentProperties;
import com.aimodelhub.agent.dto.AgentChatRequest;
import com.aimodelhub.agent.dto.AgentCreateSessionDTO;
import com.aimodelhub.agent.entity.AgentSession;
import com.aimodelhub.agent.service.AgentAttachmentService;
import com.aimodelhub.agent.service.AgentChatService;
import com.aimodelhub.agent.service.AgentInferenceService;
import com.aimodelhub.agent.service.AgentSessionService;
import com.aimodelhub.agent.service.AgentTaskService;
import com.aimodelhub.agent.support.CurrentUser;
import com.aimodelhub.agent.vo.AgentAttachmentVO;
import com.aimodelhub.agent.vo.AgentChatResponse;
import com.aimodelhub.agent.vo.AgentMessageVO;
import com.aimodelhub.agent.vo.AgentTaskResultVO;
import com.aimodelhub.agent.vo.AgentTaskVO;
import com.aimodelhub.agent.vo.AgentSessionVO;
import com.aimodelhub.common.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * ModelHub Copilot 的 HTTP 入口。
 *
 * <h3>Controller 只做三件事</h3>
 * <ol>
 *   <li>取当前用户（{@link CurrentUser}，来自 JWT，不读请求体）</li>
 *   <li>调用 Service</li>
 *   <li>把结果包装成 {@link Result} 或 SSE 事件</li>
 * </ol>
 * 业务判断一律不写在这里——否则「哪些用户能看哪些会话」会散落在各种 if 里。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentSessionService sessionService;
    private final AgentChatService chatService;
    private final AgentAttachmentService attachmentService;
    private final AgentInferenceService inferenceService;
    private final AgentTaskService taskService;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public AgentController(AgentSessionService sessionService,
                           AgentChatService chatService,
                           AgentAttachmentService attachmentService,
                           AgentInferenceService inferenceService,
                           AgentTaskService taskService,
                           AgentProperties properties,
                           ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.chatService = chatService;
        this.attachmentService = attachmentService;
        this.inferenceService = inferenceService;
        this.taskService = taskService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ================================================================
    // 会话管理
    // ================================================================

    /** 新建会话。请求体可省略，标题留空则由首条用户消息自动生成。 */
    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(@RequestBody(required = false) AgentCreateSessionDTO dto) {
        Long userId = CurrentUser.requireId();
        String title = dto == null ? null : dto.getTitle();
        AgentSession session = sessionService.create(userId, title);
        return Result.ok(AgentSessionVO.from(session));
    }

    /** 当前用户的会话列表（按最后消息时间倒序）。 */
    @GetMapping("/sessions")
    public Result<List<AgentSessionVO>> listSessions() {
        return Result.ok(sessionService.list(CurrentUser.requireId()));
    }

    /** 删除会话（软删除）。只能删自己的，删别人的返回 404。 */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable("id") Long id) {
        sessionService.delete(CurrentUser.requireId(), id);
        return Result.ok(null);
    }

    /** 会话历史消息（时间正序）。 */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AgentMessageVO>> listMessages(@PathVariable("id") Long id) {
        return Result.ok(sessionService.listMessages(CurrentUser.requireId(), id));
    }

    // ================================================================
    // 对话
    // ================================================================

    /**
     * 流式对话（SSE）。
     * <p>
     * 事件类型见 {@code AgentEvent}：{@code session} / {@code token} / {@code tool_result} /
     * {@code recommend} / {@code confirm_required} / {@code done} / {@code error}。
     * <p>
     * <b>为什么返回 Flux 而不是手动 SseEmitter</b>：
     * 手动 SseEmitter 在 Tomcat 上结束时偶尔不写终止分块（客户端表现为
     * {@code ERR_INCOMPLETE_CHUNKED_ENCODING} / {@code terminated}，页面弹「network error」）。
     * 交给 Spring MVC 的 ReactiveTypeHandler 管理生命周期，流结束会正常收尾；
     * 客户端仍保留「已收到 done 即视为结束」的兜底。
     * <p>
     * <b>为什么用 SSE 而不是 EventSource</b>：
     * 浏览器原生 {@code EventSource} 不能自定义 {@code Authorization} 头，
     * 而本项目 JWT 只认 Header。前端用 {@code fetch} + {@code ReadableStream} 读这个响应。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody AgentChatRequest request) {
        Long userId = CurrentUser.requireId();

        return chatService.stream(userId, request.getSessionId(), request.getMessage())
                .map(event -> ServerSentEvent.builder(toJson(event.getData()))
                        .event(event.getType())
                        .build())
                // 兜底：编排层已经会转成 error 事件，这里再兜一层，避免连接悬挂
                .onErrorResume(e -> {
                    log.error("SSE 流异常: userId={}", userId, e);
                    return Flux.just(ServerSentEvent.builder("{\"message\":\"服务异常，请稍后重试\"}")
                            .event("error")
                            .build());
                });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 非流式对话：一次性返回完整回答。
     * 便于脚本化验证与不支持 SSE 的客户端；内部复用同一套编排逻辑。
     */
    @PostMapping("/chat/sync")
    public Result<AgentChatResponse> chatSync(@Valid @RequestBody AgentChatRequest request) {
        return Result.ok(chatService.chatSync(
                CurrentUser.requireId(), request.getSessionId(), request.getMessage()));
    }

    // ================================================================
    // 附件与推理任务
    // ================================================================

    /**
     * 上传待推理文件。
     * <p>
     * 为什么单独一个接口而不是塞进 chat：SSE 请求体是 JSON，
     * 二进制文件塞不进去；而且用户往往是「先说需求 → 再传图」，
     * 提前落地文件才能让 Agent 在 prepareInference 里引用它。
     */
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AgentAttachmentVO> upload(@RequestPart("file") MultipartFile file,
                                            @RequestParam(value = "sessionId", required = false) Long sessionId) {
        Long userId = CurrentUser.requireId();
        return Result.ok(AgentAttachmentVO.from(attachmentService.store(userId, sessionId, file)));
    }

    /**
     * 用户确认并执行推理任务。
     * <p>
     * 这是<b>唯一</b>能把任务从 PENDING 推到执行的入口——必须携带用户 JWT，
     * 且只能操作自己的任务。LLM 无法调用这个接口，因此「未确认不得执行」由状态机保证。
     */
    @PostMapping("/tasks/{id}/confirm")
    public Result<AgentTaskResultVO> confirmTask(@PathVariable("id") Long id) {
        return Result.ok(inferenceService.confirmAndExecute(CurrentUser.requireId(), id));
    }

    /** 查询任务状态（前端轮询或执行后刷新）。 */
    @GetMapping("/tasks/{id}")
    public Result<AgentTaskVO> getTask(@PathVariable("id") Long id) {
        Long userId = CurrentUser.requireId();
        return Result.ok(AgentTaskVO.from(taskService.requireOwned(userId, id)));
    }

    /** 当前用户最近的推理任务。 */
    @GetMapping("/tasks")
    public Result<List<AgentTaskVO>> listTasks(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.ok(taskService.listRecent(CurrentUser.requireId(), limit)
                .stream().map(AgentTaskVO::from).toList());
    }

}
