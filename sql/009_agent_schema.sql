SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 009_agent_schema.sql
--
-- ModelHub Copilot —— Agent 会话与消息持久化。
--
-- 设计原则：
--   1. 只新增表，不改动 sys_user / model_info / predict_record 等既有表。
--   2. 所有业务表都带 user_id，用户隔离靠 Mapper 层 WHERE user_id = ? 强制，
--      不依赖上层代码自觉。
--   3. 主键统一 BIGINT AUTO_INCREMENT，命名 snake_case，与 001 保持一致。
--   4. 软删除用 status（1=正常，0=已删除），不做物理删除，
--      便于事后审计「用户问过什么、Agent 答过什么」。
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可重复执行。
--
-- 执行：
--   mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 \
--       < sql/009_agent_schema.sql
-- ============================================================

USE ai_modelhub;

-- ------------------------------------------------------------
-- 会话表：一个会话 = 一段独立的多轮对话上下文
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_session (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '所属用户，来自 JWT principal（服务端写入，不接受前端传值）',
    title           VARCHAR(120) NOT NULL DEFAULT '新会话' COMMENT '会话标题，首条用户消息自动截取',
    message_count   INT          NOT NULL DEFAULT 0 COMMENT '消息条数（含用户与助手），列表页展示用',
    last_message_at DATETIME     NULL COMMENT '最后一条消息时间，用于会话列表倒序',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=已删除(软删除)',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 会话列表查询：WHERE user_id = ? AND status = 1 ORDER BY last_message_at DESC
    KEY idx_agent_session_user (user_id, status, last_message_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Agent 会话';

-- ------------------------------------------------------------
-- 消息表：用户消息 / 助手回复 / 工具调用记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_message (
    id           BIGINT      PRIMARY KEY AUTO_INCREMENT,
    session_id   BIGINT      NOT NULL COMMENT '所属会话',
    user_id      BIGINT      NOT NULL COMMENT '冗余存一份，便于按用户维度审计与删除',
    role         VARCHAR(16) NOT NULL COMMENT 'USER / ASSISTANT / TOOL',
    content      MEDIUMTEXT  NULL COMMENT '文本内容（Markdown）。TOOL 消息存工具返回摘要',
    tool_name    VARCHAR(64) NULL COMMENT 'role=TOOL 时记录工具名，如 listAvailableModels',
    tool_payload JSON        NULL COMMENT '工具入参/出参的结构化快照，便于排查「模型为什么这么答」',
    token_count  INT         NULL COMMENT '粗估 token 数，用于上下文裁剪与服务端限流',
    status       TINYINT     NOT NULL DEFAULT 1 COMMENT '1=正常 0=已隐藏',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 按会话顺序拉取：WHERE session_id = ? ORDER BY id
    KEY idx_agent_message_session (session_id, id),
    -- 用户维度审计 / 清理
    KEY idx_agent_message_user (user_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Agent 消息';
