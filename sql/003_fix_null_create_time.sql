SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- =============================================
-- 003_fix_null_create_time.sql
-- Purpose: Fix historical records with NULL create_time
-- Run:    mysql -u root -p ai_modelhub < 003_fix_null_create_time.sql
-- =============================================

USE ai_modelhub;

-- Fix predict_record rows that have NULL create_time
-- Sets them to a sentinel value (2000-01-01) so they sort correctly
-- and can be identified as legacy data
UPDATE predict_record
SET create_time = '2000-01-01 00:00:00'
WHERE create_time IS NULL;

-- Fix sys_user rows that have NULL create_time/update_time
UPDATE sys_user
SET create_time = '2000-01-01 00:00:00',
    update_time = '2000-01-01 00:00:00'
WHERE create_time IS NULL OR update_time IS NULL;

SELECT CONCAT('Fixed ', ROW_COUNT(), ' rows') AS result;
