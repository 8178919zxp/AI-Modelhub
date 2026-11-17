package com.aimodelhub.agent.enums;

/**
 * 消息角色。
 * <p>
 * 刻意做成枚举而不是到处写字面量 {@code "USER"}：
 * 拼错一个字母 {@code "USRE"} 编译器不会报错，但存进库后回放会话就乱了。
 */
public enum MessageRole {
    /** 用户输入 */
    USER,
    /** 模型回复 */
    ASSISTANT,
    /** 工具调用快照（第二/三阶段启用） */
    TOOL
}
