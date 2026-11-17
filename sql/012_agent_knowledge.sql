SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 012_agent_knowledge.sql
--
-- RAG 知识库：文档 + 分块（含可选向量）。
--
-- 分层原则（与任务书一致）：
--   model_info / agent_model_profile  → 模型事实的唯一权威来源
--   agent_knowledge_*                 → 只放「使用说明 / 规范 / FAQ / 平台手册」
-- 检索结果不得覆盖数据库里的模型状态。
--
-- 检索能力分两级：
--   P1  MySQL FULLTEXT + ngram 分词（中文可用，ngram_token_size=2 为默认值）
--   P2  本地 ONNX 中文 Embedding（BGE-small-zh），向量存 JSON，应用层算余弦相似度
-- 两级结果用 RRF 融合。embedding 为空的行只参与 P1。
--
-- 幂等：CREATE TABLE IF NOT EXISTS。
--
-- 执行：
--   mysql -h 127.0.0.1 -P3306 -u root -p --default-character-set=utf8mb4 \
--       < sql/012_agent_knowledge.sql
-- ============================================================

USE ai_modelhub;

CREATE TABLE IF NOT EXISTS agent_knowledge_document (
    id                BIGINT       PRIMARY KEY AUTO_INCREMENT,
    title             VARCHAR(200) NOT NULL,
    category          VARCHAR(50)  NULL COMMENT '如：模型说明 / 使用规范 / FAQ / 平台手册',
    source_type       VARCHAR(16)  NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD=用户上传 / SEED=内置',
    original_filename VARCHAR(255) NULL,
    char_count        INT          NOT NULL DEFAULT 0,
    chunk_count       INT          NOT NULL DEFAULT 0,
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '1=可用 0=已删除',
    uploader_id       BIGINT       NULL COMMENT '上传者；内置文档为 NULL',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_agent_kb_doc_status (status, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Agent 知识库文档';

CREATE TABLE IF NOT EXISTS agent_knowledge_chunk (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    doc_id          BIGINT       NOT NULL,
    chunk_index     INT          NOT NULL COMMENT '文档内序号，从 0 开始',
    heading         VARCHAR(200) NULL COMMENT '所属小节标题，用于给检索结果加来源定位',
    content         TEXT         NOT NULL,
    keywords        VARCHAR(512) NULL COMMENT '抽取的关键词，参与全文检索加权',
    embedding       JSON         NULL COMMENT '归一化后的向量（float 数组）；为 NULL 表示该分块未做向量化',
    embedding_model VARCHAR(80)  NULL,
    embedding_dim   INT          NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_kb_chunk_doc (doc_id, chunk_index),
    -- 中文全文检索：MySQL 默认 ngram_token_size=2，无需额外分词器
    FULLTEXT KEY ft_agent_kb_chunk_content (content) WITH PARSER ngram
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Agent 知识库分块';
