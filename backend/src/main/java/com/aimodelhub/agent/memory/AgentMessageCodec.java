package com.aimodelhub.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI {@link Message} 与 JSON 之间的编解码器。
 * <p>
 * <b>为什么需要它？</b>
 * Spring AI 的 Message 是抽象类（UserMessage / AssistantMessage / SystemMessage /
 * ToolResponseMessage），没有无参构造器，直接用 Jackson 反序列化会失败。
 * 但我们又必须把「上下文窗口」存进 Redis，所以这里手写一层扁平的中间结构：
 *
 * <pre>
 * { "role": "ASSISTANT",
 *   "text": "我推荐 ResNet-50",
 *   "toolCalls": [ { "id": "...", "type": "function", "name": "...", "arguments": "{}" } ],
 *   "responses": [ { "id": "...", "name": "...", "responseData": "..." } ] }
 * </pre>
 *
 * 只保留「重新构造 Message 所必需」的字段，不存 Spring AI 的内部元数据——
 * 那些字段对下一次请求没有意义，存了反而在升级 Spring AI 时容易解析失败。
 */
final class AgentMessageCodec {

    private AgentMessageCodec() {
    }

    /** 把消息列表序列化成 JSON 字符串。 */
    static String encode(List<Message> messages, ObjectMapper mapper) {
        ArrayNode root = mapper.createArrayNode();
        for (Message message : messages) {
            ObjectNode node = root.addObject();
            node.put("role", message.getMessageType().name());
            node.put("text", message.getText());

            if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                ArrayNode calls = node.putArray("toolCalls");
                for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                    ObjectNode c = calls.addObject();
                    c.put("id", call.id());
                    c.put("type", call.type());
                    c.put("name", call.name());
                    c.put("arguments", call.arguments());
                }
            }

            if (message instanceof ToolResponseMessage tool) {
                ArrayNode responses = node.putArray("responses");
                for (ToolResponseMessage.ToolResponse r : tool.getResponses()) {
                    ObjectNode rn = responses.addObject();
                    rn.put("id", r.id());
                    rn.put("name", r.name());
                    rn.put("responseData", r.responseData());
                }
            }
        }
        return root.toString();
    }

    /** 把 JSON 字符串还原成消息列表；结构不合法时返回空列表（宁可少上下文，也不要抛异常）。 */
    static List<Message> decode(String json, ObjectMapper mapper) {
        List<Message> messages = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return messages;
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) {
                return messages;
            }
            for (JsonNode node : root) {
                String role = node.path("role").asText("USER");
                String text = node.path("text").asText("");
                switch (role) {
                    case "SYSTEM" -> messages.add(new SystemMessage(text));
                    case "ASSISTANT" -> messages.add(toAssistant(node, text));
                    case "TOOL" -> messages.add(toToolResponse(node));
                    default -> messages.add(new UserMessage(text));
                }
            }
        } catch (Exception e) {
            // 缓存内容损坏（例如手工改过 key）时，丢弃上下文而不是让整轮对话失败
            return new ArrayList<>();
        }
        return messages;
    }

    private static AssistantMessage toAssistant(JsonNode node, String text) {
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        JsonNode callsNode = node.path("toolCalls");
        if (callsNode.isArray()) {
            for (JsonNode c : callsNode) {
                calls.add(new AssistantMessage.ToolCall(
                        c.path("id").asText(),
                        c.path("type").asText("function"),
                        c.path("name").asText(),
                        c.path("arguments").asText("{}")));
            }
        }
        return AssistantMessage.builder().content(text).toolCalls(calls).build();
    }

    private static ToolResponseMessage toToolResponse(JsonNode node) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        JsonNode array = node.path("responses");
        if (array.isArray()) {
            for (JsonNode r : array) {
                responses.add(new ToolResponseMessage.ToolResponse(
                        r.path("id").asText(),
                        r.path("name").asText(),
                        r.path("responseData").asText()));
            }
        }
        return ToolResponseMessage.builder().responses(responses).build();
    }
}
