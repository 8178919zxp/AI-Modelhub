SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 002_init_seed.sql —— 演示账号与基础模型种子数据
--
-- 重要：sys_user.password 存的是 BCrypt 哈希，不是明文。
--   UserService.login() 用 BCryptPasswordEncoder.matches() 校验，
--   若这里写明文，新装环境将永远登录失败（400 用户名或密码错误）。
--
-- 演示口令：123456（首次登录后请立即在「个人中心」修改）
--   哈希生成方式：BCryptPasswordEncoder().encode("123456")
--   密保答案为 demo（用于验证找回密码流程）
--
-- 幂等：使用 ON DUPLICATE KEY UPDATE，且**只更新昵称**，
--   不会覆盖已存在的密码 / 密保，重复执行安全。
-- ============================================================

USE ai_modelhub;

INSERT INTO sys_user (username, password, nickname, avatar, role, security_question, security_answer)
VALUES
('admin',
 '$2a$10$omeVmNo1ah9Aj8op7L1iOObH.nJ666bp2QEMzgSD4sayOMjTJWKNu',
 '超级管理员', '👨‍💼', 'admin',
 '创建学校',
 '$2a$10$syXtAjGc6qSrQ0Q3KkHn6Or/cWSP.qY4UCw.QkOp62LXNbk5.EbQS'),
('demo',
 '$2a$10$omeVmNo1ah9Aj8op7L1iOObH.nJ666bp2QEMzgSD4sayOMjTJWKNu',
 '演示用户', '🧑‍🔬', 'user',
 '创建学校',
 '$2a$10$syXtAjGc6qSrQ0Q3KkHn6Or/cWSP.qY4UCw.QkOp62LXNbk5.EbQS')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

INSERT INTO model_info (id, model_name, model_type, algorithm, accuracy, description, model_api, status)
VALUES
(1, 'ResNet-50', 'image', 'Deep Residual Learning', 92.1, '经典图像分类模型', 'resnet50', 1),
(2, 'YOLOv8', 'object', 'You Only Look Once v8', 95.3, '经典目标检测模型', 'yolov8', 1),
(3, 'BERT-Base', 'text', 'Transformer Encoder', 89.4, '经典文本分类模型', 'bert', 1),
(4, 'XGBoost', 'ml', 'Gradient Boosting', 87.6, '经典结构化数据模型', 'xgboost', 1)
ON DUPLICATE KEY UPDATE model_name = VALUES(model_name);
