package com.aimodelhub.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentMessageCodec} 的单元测试。
 * <p>
 * 为什么值得单独测：这段代码是「Redis 上下文能不能活过重启」的唯一保障，
 * 一旦编解码不对称，症状是模型莫名失忆——线上很难定位，所以在这里用纯单测钉死。
 */
@DisplayName("Agent 上下文编解码")
class AgentMessageCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("USER / ASSISTANT / SYSTEM 往返编解码保持内容与顺序")
    void roundTrip() {
        List<Message> original = List.of(
                new SystemMessage("你是平台助理"),
                new UserMessage("我想识别图片里的猫"),
                new AssistantMessage("推荐使用图像分类模型"));

        String json = AgentMessageCodec.encode(original, mapper);
        List<Message> restored = AgentMessageCodec.decode(json, mapper);

        assertEquals(3, restored.size());
        assertEquals("SYSTEM", restored.get(0).getMessageType().name());
        assertEquals("你是平台助理", restored.get(0).getText());
        assertEquals("USER", restored.get(1).getMessageType().name());
        assertEquals("我想识别图片里的猫", restored.get(1).getText());
        assertEquals("ASSISTANT", restored.get(2).getMessageType().name());
        assertEquals("推荐使用图像分类模型", restored.get(2).getText());
    }

    @Test
    @DisplayName("带工具调用的助手消息不丢 toolCalls（第二阶段的上下文依赖它）")
    void keepsToolCalls() {
        AssistantMessage withTool = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "listAvailableModels", "{\"type\":\"image\"}")))
                .build();

        String json = AgentMessageCodec.encode(List.of(withTool), mapper);
        List<Message> restored = AgentMessageCodec.decode(json, mapper);

        assertEquals(1, restored.size());
        AssistantMessage msg = (AssistantMessage) restored.get(0);
        assertTrue(msg.hasToolCalls());
        assertEquals("listAvailableModels", msg.getToolCalls().get(0).name());
        assertEquals("call_1", msg.getToolCalls().get(0).id());
    }

    @Test
    @DisplayName("内容损坏时返回空列表而不是抛异常")
    void corruptedReturnsEmpty() {
        assertTrue(AgentMessageCodec.decode("{not-json", mapper).isEmpty());
        assertTrue(AgentMessageCodec.decode(null, mapper).isEmpty());
        assertTrue(AgentMessageCodec.decode("", mapper).isEmpty());
        assertTrue(AgentMessageCodec.decode("{\"role\":\"USER\"}", mapper).isEmpty());
    }
}
