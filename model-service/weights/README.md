# 模型权重目录

**仓库里只有这个 README，没有任何 `.pkl` / `.onnx` / `.h5` / `.pt` 文件。**

10 个模型的权重可以分为四类，缺了之后的后果完全不同——照着补，别按「全都要」或「都不用管」理解。

## 1. 构建镜像时自动下载（联网即可，无需手工准备）

| 模型 | 文件名 | 体积 | 下载方式 |
|---|---|---|---|
| ResNet-50 | `resnet50-0676ba61.pth` | ~98 MB | Dockerfile 里 `python download_weights.py` |
| YOLOv8 | `yolov8n.pt` | ~6 MB | 同上 |
| 英文情感（DistilBERT） | `bert_sentiment/`（目录） | ~265 MB | 同上 |

`download_weights.py` 只在**构建镜像**时跑。运行期由 `MODEL_STRICT_OFFLINE=true` 兜住，不会再联网——本地没有就直接报错，避免容器里悄悄下 270MB。

## 2. 首次使用自动训练（不需要外部文件）

| 模型 | 文件名 | 体积 | 说明 |
|---|---|---|---|
| 鸢尾花（XGBoost） | `xgboost_iris.pkl` + `xgboost_iris_meta.pkl` | 59 KB + 350 B | 用 `sklearn.datasets.load_iris()` 现场训练，数据随 sklearn 分发，**不依赖仓库里的 Dataset** |

## 3. 必须自己准备（仓库不含，也没有下载脚本）

| 模型 | 文件名 | 体积 | 缺失后的实际表现 |
|---|---|---|---|
| 抠图（U²-Net） | `u2net.onnx` | 168 MB | 启动即检测：缺失时**不会下载**，`/health` 标记 `error`，抠图接口返回 503「抠图模型暂不可用」。详见第 3.1 节 |
| 肺部 CT | `chest_ct_resnet50.h5` | 211 MB | 懒加载失败 → `/predict/image` 500 |
| 花卉识别 | `flower_cnn_model.h5` | 46 MB | 同上 |
| 中文情感 | `chinese_sentiment_mlp.pkl` | 7.33 MB | 见下方「中文情感的两个入口」 |
| 中文情感 | `chinese_vectorizer.pkl` | 375 KB | 同上 |
| 天气 | `weather_gbm.pkl` | 878 KB | 缺失时尝试自动训练，但训练数据 `Dataset/seattle-weather.csv` 不在仓库 → 500 |
| 股票 | `stock_regression.pkl` | 1 KB | 同上，依赖 `Dataset/TWTR.csv` → 500 |

> `Dataset/` 整个目录都不在仓库里（训练语料体积大且涉及授权）。所以「缺失自动训练」这条路，只有 XGBoost 走得通。

### 3.1 U²-Net 为什么不会自动下载

rembg 2.0.69 的 `U2netSession` 在构造时**无条件**调用 `download_models()`，由 `pooch.retrieve` 从 GitHub Releases 把 176 MB 的 u2net.onnx 拉到缓存目录。rembg 自身**没有**关闭这个行为的开关。

所以项目在 `app/models/u2net.py` 里改成「先查本地，再决定要不要构造 Session」：

| 情况 | 实际行为 |
|---|---|
| 找到 `u2net.onnx` | 把 rembg 的 `download_models` 替换成「返回本地路径」的补丁，运行时不可能联网 |
| 找不到，且未设 `U2NET_ALLOW_DOWNLOAD` | **抛错**，注册表记入 failed_models；`/health` 显示 `error: U²-Net 权重缺失…`；抠图接口 503；**不发生任何网络请求、不产生缓存文件** |
| 找不到，且 `U2NET_ALLOW_DOWNLOAD=true` | 只打印告警，交给 rembg 下载到 `U2NET_HOME`（默认 `~/.u2net/u2net.onnx`） |

查找顺序：`model-service/weights/u2net.onnx` → `$U2NET_HOME/u2net.onnx` → `~/.u2net/u2net.onnx`。放在 rembg 自己的缓存目录里也算数，不用再复制一份。

相关环境变量（全部可选）：

| 变量 | 默认 | 来源 | 作用 |
|---|---|---|---|
| `U2NET_ALLOW_DOWNLOAD` | `false` | 本项目新增 | 设成 `true` 才允许首次加载时自动下载 176 MB |
| `U2NET_HOME` | `~/.u2net` | rembg 自带 | 下载缓存目录；也可指向已放好 `u2net.onnx` 的目录 |
| `MODEL_CHECKSUM_DISABLED` | 未设置 | rembg 自带 | 设置后跳过 md5 校验，仅用于自备的非官方权重 |

> 缺权重不会让 FastAPI 启动失败：`U2NetModel.__init__` 抛出的异常被注册表捕获，其余 9 个模型照常加载。

## 4. 中文情感的两个入口（重点）

「中文评论情感分析」在平台上有两个入口，缺权重时表现**不一样**：

| 入口 | 调用路径 | 权重缺失时 |
|---|---|---|
| 在线体验 →「文本情感分析」，输入中文 | `ModelInferenceService` → `/text/classify` → `BertSentimentModel` 内部转中文链路 | **不报错**：退化为关键词规则，HTTP 200，`model_name` 明确写「中文情感（关键词规则）」，标签为「正面/负面」 |
| 在线体验 →「中文评论情感分析」 | `/predict/custom`，`model_key=chinese_sentiment` | **500**：该分支会先尝试 `train_model()`，需要 `Dataset/ChnSentiCorp_htl_all.csv`，文件不存在直接抛错 |

也就是说：**只补 `chinese_sentiment_mlp.pkl` + `chinese_vectorizer.pkl` 这两个文件**，「文本情感分析」的中文结果才是真正的 ML 输出；不补的话它降级成规则匹配，能跑但结论会明显偏粗。

两个文件必须成对放置，`load_model()` 会同时校验，少一个就当作缺失。

## 5. 放置位置

```
model-service/weights/
├── README.md
├── chinese_sentiment_mlp.pkl      # 自己准备
├── chinese_vectorizer.pkl         # 自己准备
├── u2net.onnx                     # 自己准备
├── chest_ct_resnet50.h5           # 自己准备
├── flower_cnn_model.h5            # 自己准备
├── weather_gbm.pkl                # 自己准备
├── stock_regression.pkl           # 自己准备
└── （resnet50 / yolov8 / bert_sentiment 由构建脚本下载）
```

容器内固定挂到 `/app/weights`（Dockerfile：`COPY weights/ /app/weights/`）。本地直接跑 `uvicorn` 时，路径按 `PROJECT_ROOT/weights` 解析，即 `model-service/weights/`。

## 6. 手动下载参考（离线环境）

```bash
# ResNet-50
wget https://download.pytorch.org/models/resnet50-0676ba61.pth -O weights/resnet50-0676ba61.pth

# YOLOv8 nano
wget https://github.com/ultralytics/assets/releases/download/v8.0.0/yolov8n.pt -O weights/yolov8n.pt

# DistilBERT（需要 huggingface_hub）
huggingface-cli download distilbert-base-uncased-finetuned-sst-2-english --local-dir weights/bert_sentiment

# U²-Net（不放也行，但那样抠图不可用；显式开启下载见 3.1 节）
wget https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2net.onnx -O weights/u2net.onnx
```

肺部 CT、花卉、中文情感、天气、股票这五个模型**没有公开下载地址**——它们是本项目的训练产物，仓库中未包含对应权重文件（以及天气/股票/中文情感训练所需的 `Dataset/*.csv`）。使用者需要根据各模型说明自行准备符合要求的文件，并确保具有合法使用授权；也可以直接使用 `app/models/` 里对应的训练函数、基于自己合法持有的数据重新训练生成。

## 7. 怎么看缺了哪些

```bash
curl -s http://localhost:8001/health | python -m json.tool
```

`models` 字段给出每个**预加载**模型的状态（`loaded` / `not_loaded` / `error: 原因`）。注意它只覆盖启动时预加载的 5 个（resnet50 / bert / yolov8 / xgboost / u2net）；另外 5 个是懒加载，只有第一次调用时才会暴露问题。

缺 u2net 权重时，`models.u2net` 会显示 `error: U²-Net 权重缺失（已查找：…）`，同时抠图接口返回 503 `{"error":"抠图模型暂不可用：…"}`——状态和调用两条路径给的结论一致。
