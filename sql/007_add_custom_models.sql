SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 007_add_custom_models.sql
--
-- Register the five "custom" models that model-service can already serve
-- but that are not part of the base seed:
--   chest_ct / flower        → POST /api/predict/image
--   weather / stock          → POST /api/predict/custom (features)
--   chinese_sentiment        → POST /api/predict/custom (text)
--
-- model_api MUST be the model-service model_key (never a URL) — it is the
-- routing key used by PredictController.
--
-- Idempotent **by model_name**, not by id: databases initialised before this
-- script may already contain these models under auto-increment ids, so a
-- fixed-id INSERT would create duplicates. Each row is inserted only when the
-- name is absent; existing rows are left untouched.
--
-- accuracy is intentionally NULL: these weights are demo artifacts and their
-- real metrics depend on the training run. Fill them in after a measured
-- evaluation instead of hardcoding a number.
--
-- Fresh installs: executed automatically only on an empty data volume.
-- Existing databases:
--   mysql -h <host> -P 3306 -u root -p ai_modelhub \
--       --default-character-set=utf8mb4 \
--       < sql/007_add_custom_models.sql
-- ============================================================

USE ai_modelhub;

INSERT INTO model_info (model_name, model_type, algorithm, accuracy, description, model_api, status)
SELECT 'ChestCT', 'image', 'ResNet-50 (Keras)', NULL,
       '胸部 CT 影像分类，判断是否存在肺部病灶（需要 weights/chest_ct_resnet50.h5）',
       'chest_ct', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_info WHERE model_name = 'ChestCT');

INSERT INTO model_info (model_name, model_type, algorithm, accuracy, description, model_api, status)
SELECT 'FlowerCNN', 'image', 'CNN (Keras)', NULL,
       '五种常见花卉的图像分类（需要 weights/flower_cnn_model.h5）',
       'flower', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_info WHERE model_name = 'FlowerCNN');

INSERT INTO model_info (model_name, model_type, algorithm, accuracy, description, model_api, status)
SELECT 'WeatherGBM', 'ml', 'Gradient Boosting Classifier', NULL,
       '西雅图气象观测数据的天气类型分类：rain / sun / fog / drizzle / snow。必填特征：precipitation, temp_max, temp_min, wind, year, month, day',
       'weather', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_info WHERE model_name = 'WeatherGBM');

INSERT INTO model_info (model_name, model_type, algorithm, accuracy, description, model_api, status)
SELECT 'StockRegression', 'ml', 'Random Forest Regressor', NULL,
       '基于当日 OHLCV 特征回归预测收盘价。必填特征：Open, High, Low, Volume',
       'stock', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_info WHERE model_name = 'StockRegression');

INSERT INTO model_info (model_name, model_type, algorithm, accuracy, description, model_api, status)
SELECT 'ChineseSentiment', 'text', 'TF-IDF + MLP', NULL,
       '中文评论情感二分类（正面 / 负面），需要 weights/chinese_sentiment_mlp.pkl 与 chinese_vectorizer.pkl',
       'chinese_sentiment', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_info WHERE model_name = 'ChineseSentiment');
