SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 010_agent_model_profile.sql
--
-- 模型「输入规范」表 —— Agent 判断「这个模型能不能跑、要什么输入」的依据。
--
-- 为什么需要它：
--   model_info 只存名称/类型/简介/准确率，而「ResNet50 要一张图」
--   「XGBoost 要 sepal_length 等 4 个数值」「WeatherGBM 要 7 个字段」
--   这些信息原本只写在 description 的一段自然语言里。
--   Agent 若靠大模型去读这段文字猜参数，就会猜出并不存在的字段。
--   所以把输入契约结构化落到表里，由后端校验，模型只负责解释。
--
-- 设计约束：
--   1. 不改 model_info（原表语义保持不变）；
--   2. 以 model_id 唯一，一对一；
--   3. 不建外键（沿用项目现状），靠 JOIN model_info 过滤上下架；
--   4. 幂等：按 model_api 关联插入，重复执行不会产生重复行。
--
-- 执行：
--   mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 \
--       < sql/010_agent_model_profile.sql
-- ============================================================

USE ai_modelhub;

CREATE TABLE IF NOT EXISTS agent_model_profile (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    model_id        BIGINT       NOT NULL COMMENT '关联 model_info.id',
    input_type      VARCHAR(16)  NOT NULL COMMENT 'IMAGE=图片文件 / TEXT=纯文本 / FEATURES=结构化数值',
    required_fields JSON         NULL COMMENT 'input_type=FEATURES 时的必填字段名数组',
    example_input   VARCHAR(1000) NULL COMMENT '给用户看的输入示例',
    usage_note      VARCHAR(500) NULL COMMENT '一句话说明适用场景与限制',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_model_profile_model (model_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='模型输入规范（Agent 用）';

-- ------------------------------------------------------------
-- 种子：只记录「输入契约」这类结构化事实，
-- 不复述 description（避免同一事实存两处、将来不一致）
-- ------------------------------------------------------------

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'IMAGE', NULL, '一张 JPG/PNG 图片，≤5MB',
       '通用图像分类，输出 Top-5 类别与置信度'
FROM model_info m
WHERE m.model_api = 'resnet50'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'IMAGE', NULL, '一张包含待检测物体的 JPG/PNG 图片，≤5MB',
       '目标检测，输出检测框与类别'
FROM model_info m
WHERE m.model_api = 'yolov8'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'TEXT', NULL, '一段英文文本',
       '英文文本情感二分类（POSITIVE / NEGATIVE）'
FROM model_info m
WHERE m.model_api = 'bert'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'FEATURES',
       JSON_ARRAY('sepal_length', 'sepal_width', 'petal_length', 'petal_width'),
       '{"sepal_length":5.1,"sepal_width":3.5,"petal_length":1.4,"petal_width":0.2}',
       'Iris 鸢尾花三分类，输入 4 个数值特征'
FROM model_info m
WHERE m.model_api = 'xgboost'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'IMAGE', NULL, '一张 JPG/PNG 图片，≤5MB',
       '图像抠图 / 显著性检测，返回透明背景结果图'
FROM model_info m
WHERE m.model_api = 'u2net'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'IMAGE', NULL, '一张肺部 CT 切片图片（JPG/PNG），≤5MB',
       '肺部 CT 影像四分类（腺癌 / 鳞癌 / 良性 / 正常）'
FROM model_info m
WHERE m.model_api = 'chest_ct'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'IMAGE', NULL, '一张花朵图片（JPG/PNG），≤5MB',
       '花卉图像分类'
FROM model_info m
WHERE m.model_api = 'flower'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'FEATURES',
       JSON_ARRAY('precipitation', 'temp_max', 'temp_min', 'wind', 'year', 'month', 'day'),
       '{"precipitation":0,"temp_max":25,"temp_min":15,"wind":3.5,"year":2024,"month":6,"day":15}',
       '按日天气特征回归预测，输入 7 个字段'
FROM model_info m
WHERE m.model_api = 'weather'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'FEATURES',
       JSON_ARRAY('Open', 'High', 'Low', 'Volume'),
       '{"Open":100.5,"High":102.3,"Low":99.8,"Volume":1250000}',
       '股票价格回归预测，输入开盘/最高/最低/成交量'
FROM model_info m
WHERE m.model_api = 'stock'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);

INSERT INTO agent_model_profile (model_id, input_type, required_fields, example_input, usage_note)
SELECT m.id, 'TEXT', NULL, '一段中文文本',
       '中文情感二分类（正面 / 负面）'
FROM model_info m
WHERE m.model_api = 'chinese_sentiment'
  AND NOT EXISTS (SELECT 1 FROM agent_model_profile p WHERE p.model_id = m.id);
