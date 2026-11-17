# 本地中文向量模型（RAG）

本目录存放 RAG 使用的**本地中文 Embedding 模型**。模型文件是二进制（约 24MB），
不纳入 Git，需要按下面的步骤自行下载。

## 为什么用本地模型

DeepSeek 官方没有 embedding 接口（实测 `/models` 只返回 chat 模型），
而 RAG 要做「语义相似度检索」就必须有一个向量化模型。
用本地 ONNX 的好处：零额外 API 成本、无网络依赖、数据不出本机；
代价是首次部署要手动放一次模型文件。

## 下载步骤

模型：`BAAI/bge-small-zh-v1.5`（ONNX 量化版，来自 `Xenova/bge-small-zh-v1.5`）

```bash
# 国内可直连的镜像站
BASE=https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main
mkdir -p backend/models/bge-small-zh-v1.5
cd backend/models/bge-small-zh-v1.5

curl -L -o model_quantized.onnx "$BASE/onnx/model_quantized.onnx"   # 约 24MB
curl -L -o vocab.txt           "$BASE/vocab.txt"                    # 约 107KB
curl -L -o config.json         "$BASE/config.json"
```

也可以换成 fp32 版本 `onnx/model.onnx`（约 95MB，精度略高、速度略慢），
然后把 `app.agent.rag.embedding-model-path` 指过去即可。

## 缺失时会怎样

启动不会失败。首次检索时会打印一条 WARN：

```
中文向量模型文件不存在（model=...），RAG 将仅使用 MySQL 全文检索
```

此时 RAG 仍然可用——走 MySQL FULLTEXT + ngram 分词那一路召回，
只是少了「换个说法也能命中」的语义能力。补上文件后重启即可恢复，
或用管理员接口 `POST /api/agent/knowledge/reindex` 为已有分块补算向量。

## 模型规格

| 项 | 值 |
|---|---|
| 参数量 | 24M |
| 向量维度 | 512 |
| 最大序列长度 | 512 |
| 体积（量化） | ~24MB |
| CPU 单条编码 | 约 10~30ms |
| 归一化方式 | CLS + L2 |
