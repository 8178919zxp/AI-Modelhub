SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 004_add_security_fields.sql
--
-- Phase 3B-1: add security question/answer fields to sys_user.
--
-- Idempotent: safe to re-run. MySQL 8.0 has no
-- "ADD COLUMN IF NOT EXISTS", so each column is checked against
-- INFORMATION_SCHEMA and the ALTER is prepared only when missing.
--
-- Run manually on an existing database:
--   mysql -h <host> -P 3306 -u root -p ai_modelhub \
--       --default-character-set=utf8mb4 \
--       < sql/004_add_security_fields.sql
-- ============================================================

USE ai_modelhub;

-- 1) security_question
SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'security_question'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN security_question VARCHAR(255) COMMENT ''密保问题''',
    'SELECT ''security_question already exists; skipped'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) security_answer
SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'security_answer'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN security_answer VARCHAR(255) COMMENT ''密保答案（BCrypt加密）''',
    'SELECT ''security_answer already exists; skipped'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
