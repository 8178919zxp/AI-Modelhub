SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 006_fix_predict_record_columns.sql
--
-- Align predict_record with the entity/mapper DDL after the
-- JPA -> MyBatis migration (which removed Hibernate ddl-auto).
--   * add the missing original_filename VARCHAR(255)
--   * widen file_url from VARCHAR(255) to VARCHAR(512)
--
-- Idempotent: safe to re-run. MySQL 8.0 has no
-- "ADD COLUMN IF NOT EXISTS", so column existence is checked
-- against INFORMATION_SCHEMA and the ALTER is prepared only
-- when the column is missing.
--
-- Fresh installs: 001_init_schema.sql already creates these
--   columns, so this script is a no-op there.
-- Existing databases (already-initialized volume): the MySQL
--   image only runs /docker-entrypoint-initdb.d/*.sql on the
--   FIRST init of an empty data directory — it will NOT upgrade
--   an existing volume automatically. Run this script manually:
--
--   mysql -h <host> -P 3306 -u root -p ai_modelhub \
--       < sql/006_fix_predict_record_columns.sql
-- ============================================================

USE ai_modelhub;

-- 1) Add original_filename only if it is missing.
SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'predict_record'
      AND COLUMN_NAME = 'original_filename'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE predict_record ADD COLUMN original_filename VARCHAR(255)',
    'SELECT ''original_filename already exists; skipped'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) Widen file_url (MODIFY is safe to re-run).
ALTER TABLE predict_record MODIFY COLUMN file_url VARCHAR(512);
