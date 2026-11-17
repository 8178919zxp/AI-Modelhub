SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
CREATE DATABASE IF NOT EXISTS ai_modelhub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_modelhub;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    avatar VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'user',
    -- 密保问题/答案用于「找回密码」。必须建表时就存在：
    -- 002_init_seed.sql 会写入这两列，若等 004 才 ALTER，全新库执行 002 会直接报
    -- "Unknown column 'security_question'"，导致演示账号建不出来、无法登录。
    -- 004_add_security_fields.sql 仍然保留，用于升级那些早于本版本的旧库。
    security_question VARCHAR(255) COMMENT '密保问题',
    security_answer VARCHAR(255) COMMENT '密保答案（BCrypt加密）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_name VARCHAR(100) NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    algorithm VARCHAR(150),
    accuracy DOUBLE,
    description TEXT,
    cover VARCHAR(255),
    model_api VARCHAR(255),
    status INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS predict_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    model_id BIGINT,
    file_url VARCHAR(512),
    input_text TEXT,
    predict_result TEXT,
    confidence DOUBLE,
    duration INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    original_filename VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS favorite_model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_model (user_id, model_id)
);
