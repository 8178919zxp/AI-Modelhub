SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- Migration 005: Add U²-Net Background Removal Model
-- ============================================================
-- Adds the U2Net model entry to the model_info table.
-- Model type: image_advanced (smart background removal / cutout)
-- Model key in model-service: u2net
-- ============================================================

USE ai_modelhub;

INSERT INTO model_info (id, model_name, model_type, algorithm, accuracy, description, model_api, status)
VALUES
(5, 'U2Net', 'image_advanced', 'U²-Net Salient Object Detection', 90.5, '智能抠图背景移除，输出透明背景 PNG', 'u2net', 1)
ON DUPLICATE KEY UPDATE model_name = VALUES(model_name);
