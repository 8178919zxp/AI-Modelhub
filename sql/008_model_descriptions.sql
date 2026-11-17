SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- ============================================================
-- 008_model_descriptions.sql
--
-- 补齐模型的中文介绍：说清「这个模型做什么、能识别哪些类别、
-- 输出什么」，让前端卡片与详情页不再只有一句「经典图像分类模型」。
--
-- 幂等：按 model_name 更新，可重复执行；不存在的模型自动跳过。
-- 依赖：001_init_schema.sql（model_info）、005/007（自定义模型记录）。
-- 执行：mysql -u root -p --default-character-set=utf8mb4 < 008_model_descriptions.sql
-- ============================================================

-- 必须显式选择库：README 给出的执行方式是不带库名的
-- （mysql -u root -p < 008_xxx.sql），缺少 USE 会报
-- "ERROR 1046 (3D000): No database selected"。
USE ai_modelhub;

UPDATE model_info SET description =
  '基于 ResNet-50 与 ImageNet 预训练的通用图像分类模型，可识别 1000 类常见物体（动物、交通工具、食物、乐器、家具等）。上传一张图片，返回前 5 个中文类别及各自的置信度。'
WHERE model_name = 'ResNet-50';

UPDATE model_info SET description =
  'YOLOv8 目标检测模型，覆盖 COCO 80 类常见目标（人、汽车、自行车、狗、猫、椅子等）。一次推理即可定位画面中的多个对象，输出每个目标的类别、置信度与边界框。'
WHERE model_name = 'YOLOv8';

UPDATE model_info SET description =
  '二分类情感分析，按语种自动分流：英文走 DistilBERT（SST-2 微调），中文走中文评论情感模型（MLP + 词袋）。两条链路都只输出正负两类概率，不含「中性」。长句、反讽与双重否定容易误判；英文为主的混排文本中，中文片段会被忽略。'
WHERE model_name IN ('BERT-Base', 'BERT');

UPDATE model_info SET description =
  '在 Iris 鸢尾花数据集（150 样本 / 4 特征 / 3 品种）上训练的梯度提升树模型。输入花萼长度、花萼宽度、花瓣长度、花瓣宽度四个数值（cm），输出山鸢尾、变色鸢尾、维吉尼亚鸢尾三种的概率。'
WHERE model_name = 'XGBoost';

UPDATE model_info SET description =
  'U²-Net 显著目标分割模型，自动识别人像、商品、宠物等前景主体并生成 alpha 通道，输出透明背景的 PNG 图片，可在线预览与下载，适合电商白底图与人像抠图。'
WHERE model_name IN ('U2Net', 'U2-Net');

UPDATE model_info SET description =
  '基于 ResNet-50 迁移学习的胸部 CT 四分类模型，区分正常肺组织、腺癌、大细胞癌、鳞状细胞癌，返回四类概率与判定类别。仅用于科研与教学演示，不构成医学诊断依据。'
WHERE model_name = 'ChestCT';

UPDATE model_info SET description =
  '基于西雅图历史气象数据训练的梯度提升分类器。输入降水量、最高温、最低温、风速与日期，判断当天天气属于雨、晴、雾、毛毛雨、雪中的哪一类，输出五类概率。'
WHERE model_name = 'WeatherGBM';

UPDATE model_info SET description =
  '线性回归模型，根据当日开盘价、最高价、最低价与成交量四个交易特征，回归预测当日收盘价。输出的是价格数值而非涨跌方向，结果不构成投资建议。'
WHERE model_name = 'StockRegression';

UPDATE model_info SET description =
  '从零训练的卷积神经网络，识别雏菊、蒲公英、玫瑰、向日葵、郁金香五种常见花卉。上传花卉照片即可获得五种花的概率分布，适合作为图像分类入门示例。'
WHERE model_name = 'FlowerCNN';

UPDATE model_info SET description =
  '基于中文酒店评论训练的文本二分类模型（MLP + CountVectorizer）。判断评论是正面还是负面，输出两类概率，不含「中性」。它也是「文本情感分析」处理中文时的实际后端模型。'
WHERE model_name = 'ChineseSentiment';

-- 校验：应看到 10 条模型记录都带有较长中文描述
SELECT id, model_name, CHAR_LENGTH(description) AS desc_len, LEFT(description, 24) AS preview
FROM model_info
ORDER BY id;
