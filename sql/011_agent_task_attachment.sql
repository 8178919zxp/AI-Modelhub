SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 011_agent_task_attachment.sql
--
-- Agent 的两张业务表：
--   agent_attachment —— 用户上传给 Agent 的文件（推理前暂存）
--   agent_task       —— 待确认/已执行的推理任务（状态机 + 审计）
--
-- 为什么附件要单独建表，而不是直接复用 MinIO：
--   1. Agent 的推理必须由用户「确认」后才执行，两次 HTTP 请求之间需要一个
--      稳定的文件引用（不能依赖前端重复上传）；
--   2. MinIO 可能不可用（既有设计允许推理时 MinIO 故障不阻断），
--      因此附件先落本地临时目录，确认执行时再读出来交给推理服务；
--   3. 需要记录「谁上传的、属于哪个会话、有没有被用过」，用于权限校验与清理。
--
-- 幂等：CREATE TABLE IF NOT EXISTS。
--
-- 执行：
--   mysql -h 127.0.0.1 -P3306 -u root -p --default-character-set=utf8mb4 \
--       < sql/011_agent_task_attachment.sql
-- ============================================================

USE ai_modelhub;

-- ------------------------------------------------------------
-- 附件：Agent 会话内的临时文件引用
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_attachment (
    id               BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL COMMENT '上传者（来自 JWT，不接受前端传值）',
    session_id       BIGINT       NULL COMMENT '所属会话；可为空，表示尚未绑定会话',
    original_filename VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    file_size        BIGINT       NOT NULL,
    storage_key      VARCHAR(255) NOT NULL COMMENT '后端本地存储的相对路径（不存文件内容）',
    status           VARCHAR(16)  NOT NULL DEFAULT 'TEMP' COMMENT 'TEMP=待使用 USED=已用于推理',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_attachment_user (user_id, status, create_time),
    KEY idx_agent_attachment_session (session_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Agent 附件';

-- ------------------------------------------------------------
-- 推理任务：用户确认制 + 状态机
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_task (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    task_no       VARCHAR(40)  NOT NULL COMMENT '对外展示的业务号，避免暴露自增 id 规律',
    user_id       BIGINT       NOT NULL,
    session_id    BIGINT       NULL,
    task_type     VARCHAR(32)  NOT NULL DEFAULT 'INFERENCE' COMMENT 'INFERENCE / RECOMMEND',
    model_id      BIGINT       NULL,
    model_name    VARCHAR(100) NULL COMMENT '下单时快照，避免模型改名后历史任务对不上',
    task_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待确认 / CONFIRMED 已确认 / RUNNING 执行中 / SUCCESS / FAILED / CANCELLED / EXPIRED',
    input_type    VARCHAR(16)  NULL COMMENT 'IMAGE / TEXT / FEATURES',
    input_summary VARCHAR(500) NULL COMMENT '给用户看的输入摘要（文件名或文本前 50 字）',
    input_payload JSON         NULL COMMENT '结构化入参（文本 / 数值特征），不含文件二进制',
    attachment_id BIGINT       NULL COMMENT 'input_type=IMAGE 时的附件',
    result_summary VARCHAR(500) NULL COMMENT '一句话结果，如 "adenocarcinoma 0.9868"',
    result_payload JSON        NULL COMMENT '完整推理结果快照',
    error_message VARCHAR(500) NULL,
    record_id     BIGINT       NULL COMMENT '写入 predict_record 后的记录 id',
    duration_ms   INT          NULL,
    confirmed_at  DATETIME     NULL,
    start_time    DATETIME     NULL,
    end_time      DATETIME     NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_task_no (task_no),
    KEY idx_agent_task_user (user_id, create_time),
    KEY idx_agent_task_session (session_id, id),
    KEY idx_agent_task_status (task_status, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Agent 推理任务';
