# AI ModelHub 接口文档

> 面向前端联调与后端维护。响应示例取自本机实际调用（2026-10，backend `8080` / model-service `8001`），`duration`、`totalCalls` 这类值每次不同。
> 涉及密码、令牌的地方一律使用占位符。

---

## 目录

1. [调用约定](#1-调用约定)
2. [统一响应与分页结构](#2-统一响应与分页结构)
3. [错误码对照](#3-错误码对照)
4. [鉴权接口 `/api/auth`](#4-鉴权接口-apiauth)
5. [模型接口 `/api/models`](#5-模型接口-apimodels)
6. [预测接口 `/api/predict`](#6-预测接口-apipredict)
7. [用户接口 `/api/users`](#7-用户接口-apiusers)
8. [管理接口 `/api/admin`](#8-管理接口-apiadmin)
9. [统计接口 `/api/dashboard`](#9-统计接口-apidashboard)
10. [健康检查 `/api/health`](#10-健康检查-apihealth)
11. [文件代理 `/api/files`](#11-文件代理-apifiles)
12. [限流规则](#12-限流规则)
13. [已知限制与取舍](#13-已知限制与取舍)
14. [维护提醒](#维护提醒)
15. [附录 A：model_api 与模型对照表](#附录-amodel_api-与模型对照表)
16. [附录 B：模型服务内部接口（FastAPI）](#附录-b模型服务内部接口fastapi)
17. [附录 C：权限矩阵](#附录-c权限矩阵)
18. [附录 D：前端调用约定](#附录-d前端调用约定)

---

## 1. 调用约定

| 项目 | 值 |
|---|---|
| 后端基址（开发） | `http://localhost:8080` |
| 后端基址（前端变量） | `VITE_BACKEND_BASE_URL`，默认 `http://localhost:8080` |
| 模型服务基址 | `http://localhost:8001`（**内部服务，前端不直连**） |
| 请求/响应编码 | `UTF-8`，`Content-Type: application/json` |
| 鉴权方式 | `Authorization: Bearer <token>` |
| 令牌有效期 | dev 24 小时 / prod 2 小时（由 `app.jwt.expiration-ms` 控制） |
| 上传上限 | **5 MB**。相关配置：Spring `max-file-size` 5MB / `max-request-size` 6MB、`FileStorageService.MAX_FILE_SIZE` 5MB、推理服务 `_MAX_UPLOAD_MB` 5、nginx `client_max_body_size` 6M |

**典型调用**

```bash
# 1. 登录拿令牌
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<username>","password":"<password>"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 2. 带令牌访问
curl -s http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

**前端约定**：所有请求统一走 `frontend/src/api/request.ts` 的 `request()`，它自动注入基址、`Bearer` 令牌与 12 秒超时；令牌取自 `localStorage` 键 `ai-modelhub` 的 `state.token`。

---

## 2. 统一响应与分页结构

### 2.1 `Result<T>`

所有 JSON 接口都返回同一包装，**HTTP 状态码与 `code` 一致**：

```json
{ "code": 200, "message": "success", "data": { } }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | Integer | 与 HTTP 状态码相同的业务码 |
| `message` | String | 提示语；成功时多为 `success` 或具体中文提示 |
| `data` | T / null | 业务数据，失败时为 `null` |

### 2.2 `PageVO<T>`

后台分页接口（`GET /api/admin/models`、`GET /api/admin/users`）返回：

```json
{
  "content": [ ],
  "totalElements": 11,
  "totalPages": 6,
  "currentPage": 0,
  "pageSize": 2
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `content` | Array | 当前页数据 |
| `totalElements` | Long | 总记录数 |
| `totalPages` | Integer | 总页数 |
| `currentPage` | Integer | 当前页（0 起） |
| `pageSize` | Integer | 每页条数，被限制在 `[1, 100]` |

---

## 3. 错误码对照

由 `GlobalExceptionHandler` 与 `SecurityConfig` 共同决定：

| HTTP | 触发条件 | 响应体示例 |
|---|---|---|
| **400** | Bean Validation 校验失败 / `BusinessException` / `IllegalArgumentException` | `{"code":400,"message":"文本不能为空"}` |
| **400** | **登录失败**（用户名不存在或密码错误） | `{"code":400,"message":"用户名或密码错误"}` |
| **401** | 未携带令牌、令牌无效、令牌已登出（`SecurityConfig` 的 `AuthenticationEntryPoint`） | `{"code":401,"message":"未登录或登录已过期，请重新登录","data":null}` |
| **403** | 已登录但非管理员访问 `/api/admin/**`（`AccessDeniedHandler`） | `{"code":403,"message":"权限不足","data":null}` |
| **404** | `NotFoundException`，如模型/用户不存在 | `{"code":404,"message":"模型不存在"}` |
| **413** | 上传文件超过 5 MB（`MaxUploadSizeExceededException`） | `{"code":413,"message":"上传文件过大，单次请求不能超过 5MB"}` |
| **429** | 预测接口超过 20 次/60 秒（`RateLimitException`） | `{"code":429,"message":"请求过于频繁，请稍后再试"}` |
| **502** | 完全连不上模型服务（`RestClientException`） | `{"code":502,"message":"模型服务不可用，请确认 model-service 已启动"}` |
| **502** | 模型服务返回 4xx/5xx（`RestClientResponseException`） | `{"code":502,"message":"模型服务调用失败（HTTP 500）"}` |
| **502** | 模型服务返回 **503**：该模型没加载起来（如 U²-Net 缺 `u2net.onnx`） | `{"code":502,"message":"该模型当前不可用（未加载或权重缺失），请联系管理员"}` |
| **500** | 其他未预期异常；**不回显内部异常信息** | `{"code":500,"message":"系统内部错误，请稍后重试"}` |

> 业务异常**不会**返回 HTTP 200——这是 2026-10-23 修复批次（B01/B12）的关键改动。前端 `request()` 会同时校验 `response.ok` 与 `body.code >= 400`。

---

## 4. 鉴权接口 `/api/auth`

全部为**公开接口**（除 `/logout` 需认证）。

### 4.1 `POST /api/auth/login`

账号密码登录，成功后签发 JWT。

**请求体**

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| `username` | String | 是 | 非空 |
| `password` | String | 是 | 非空 |

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<password>"}'
```

**响应 200**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickname": "超级管理员",
    "role": "admin",
    "avatar": "👨‍💼",
    "token": "eyJhbGciOi..."
  }
}
```

| 字段 | 说明 |
|---|---|
| `role` | `admin` / `user`，前端据此控制后台入口 |
| `token` | JWT，含 `jti`（供登出吊销）与 `iat`（供重置密码后吊销比对） |

**错误**

| 场景 | 响应 |
|---|---|
| 密码错误或用户不存在 | `400 {"code":400,"message":"用户名或密码错误"}` |
| `username` 为空 | `400 {"code":400,"message":"用户名不能为空"}` |

> 登录失败返回 **400 而非 401**：`UserService#login` 抛出的是 `BusinessException`，不属于认证入口失败的语义。前端不要靠 `401` 判断"密码错了"。

---

### 4.2 `POST /api/auth/register`

注册新用户。

**请求体**

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| `username` | String | 是 | 非空、唯一 |
| `password` | String | 是 | 非空 |
| `nickname` | String | 是 | 非空 |
| `securityQuestion` | String | 否 | 找回密码用 |
| `securityAnswer` | String | 否 | 找回密码用 |

**响应 200**

```json
{
  "code": 200,
  "message": "注册成功",
  "data": { "username": "apidoc_25374", "nickname": "接口文档", "role": "user" }
}
```

**错误**

| 场景 | 响应 |
|---|---|
| 用户名已存在 | `400 {"code":400,"message":"用户名已存在"}` |
| 必填项为空 | `400 {"code":400,"message":"用户名不能为空"}`（按第一个失败字段返回） |

> 注册接口不返回 token，需再调一次 `/login`。

---

### 4.3 `POST /api/auth/forgot-password/question`

找回密码第一步：取密保问题。

**请求体**：`{ "username": "<username>" }`

**响应 200**

```json
{ "code": 200, "message": "success", "data": { "securityQuestion": "创建学校" } }
```

**错误**：用户不存在或未设置密保 → `400`（提示语统一，防止账号枚举）。

---

### 4.4 `POST /api/auth/forgot-password/reset`

找回密码第二步：校验密保答案并重置密码。

**请求体**

```json
{ "username": "<username>", "securityAnswer": "北京", "newPassword": "<new>" }
```

**响应 200**

```json
{ "code": 200, "message": "密码重置成功，请使用新密码登录", "data": null }
```

> 重置成功后会吊销该用户已签发的全部令牌（按用户时间点吊销）。

---

### 4.5 `POST /api/auth/logout`

**需认证**。把当前令牌的 `jti` 写入 Redis 黑名单，使其立即失效。

**请求头**：`Authorization: Bearer <token>`（无请求体）

**响应 200**

```json
{ "code": 200, "message": "已退出登录", "data": { "blacklisted": true } }
```

| `data` 字段 | 说明 |
|---|---|
| `blacklisted` | `true` 服务端已注销；`false` 未注销（见下） |
| `reason` | 仅在 `blacklisted=false` 时出现，取值见下表 |

| `reason` | 含义 |
|---|---|
| `token_missing_jti` | 旧版本签发的令牌，载荷没有 `jti` |
| `token_already_expired` | 令牌已自然过期，无需吊销 |
| `redis_write_unverified` | 写入后复核失败（疑似 Redis 连接问题） |
| `redis_unavailable` | Redis 不可用，降级放行 |

> Redis 不可用时接口仍返回 200，但令牌在自然过期前依旧可用。客户端应始终清除本地令牌。

---

## 5. 模型接口 `/api/models`

`GET` 请求**公开**，无需令牌（`SecurityConfig` 明确放行）。

### 5.1 `GET /api/models`

返回**上线状态**（`status=1`）的模型列表。下线（0）与已删除（-1）不会出现。

```bash
curl -s http://localhost:8080/api/models
```

**响应 200**（`data` 为数组，节选一条）

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "ResNet-50",
      "type": "image",
      "algorithm": "Deep Residual Learning",
      "accuracy": 92.1,
      "description": "基于 ResNet-50 与 ImageNet 预训练的通用图像分类模型……",
      "cover": "",
      "api": "resnet50",
      "status": 1,
      "createTime": "2026-06-03T21:29:50",
      "updateTime": "2026-10-23T22:04:42"
    }
  ]
}
```

**字段说明**

| 字段 | 说明 |
|---|---|
| `id` | 模型主键，预测接口通过 `modelId` 引用 |
| `name` / `type` / `api` | 库中列名分别为 `model_name` / `model_type` / `model_api`，经 `@JsonProperty` 序列化为简短名 |
| `api` | **model-service 的模型 key**，不是 URL。这是跨服务调度的唯一依据 |
| `accuracy` | **0–100 的百分数**（如 `92.1` 表示 92.1%） |
| `status` | `1`=上线，`0`=下线，`-1`=已删除 |

> 结果缓存在 Redis `aimh:dev:models::online`，模型写操作会同步驱逐。

---

### 5.2 `GET /api/models/{id}`

查询单个模型详情。

- 普通用户：仅能查看 `status=1` 的模型，其余返回 **404**。
- 管理员：可查看全部状态。

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 3,
    "name": "BERT-Base",
    "type": "text",
    "algorithm": "Transformer Encoder",
    "accuracy": 89.4,
    "description": "二分类情感分析，按语种自动分流：英文走 DistilBERT，中文走中文评论情感模型……",
    "status": 1,
    "createTime": "2026-06-03T21:29:50",
    "updateTime": "2026-10-23T22:21:58"
  }
}
```

**响应 404**

```json
{ "code": 404, "message": "模型不存在" }
```

---

## 6. 预测接口 `/api/predict`

**全部需认证**，且**全部受 60 秒 20 次限流**（见[第 12 节](#12-限流规则)）。

四个入口的共同前置逻辑（`PredictController` 的 `requireOnlineModel` / `requireModelKey`）：

1. 模型必须存在且 `status=1`，否则 `400 模型未上线，无法调用`；
2. `model_api` 必须非空，否则 `400 模型 {id} 未配置有效的 model_api`；
3. 调用成功后（用户已登录）写入 `predict_record` 并驱逐该用户的记录缓存。

### 6.1 `POST /api/predict/text`

文本预测。

**请求体**

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| `modelId` | Long | 是 | 模型必须上线 |
| `text` | String | 是 | `@NotBlank`，空串报 400 |

**路由规则**：`model_api == "bert"` 走模型服务 `/text/classify`；其他文本模型（如 `chinese_sentiment`）按 key 走 `/predict/custom`。

`bert` 这个 key 内部按语种分流：中文（占比 ≥ 20%）走中文情感模型，其余走英文 DistilBERT。响应里的 `modelName` 会如实反映**实际使用的模型**，不会把中文结果标成 BERT。

```bash
# 英文
curl -s -X POST http://localhost:8080/api/predict/text \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"modelId":3,"text":"This product is excellent, I love it"}'

# 中文（同一接口，modelName 会变成 ChineseSentiment）
curl -s -X POST http://localhost:8080/api/predict/text \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"modelId":3,"text":"房间干净整洁，服务热情"}'
```

**响应 200（英文）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "text",
    "modelName": "BERT-Base",
    "topLabel": "POSITIVE",
    "confidence": 0.999887,
    "duration": 643,
    "candidates": [
      { "label": "POSITIVE", "probability": 0.999887 },
      { "label": "NEGATIVE", "probability": 0.000113 }
    ]
  }
}
```

**响应 200（中文）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "text",
    "modelName": "ChineseSentiment",
    "topLabel": "正面",
    "confidence": 0.8864,
    "duration": 24,
    "candidates": [
      { "label": "正面", "probability": 0.8864 },
      { "label": "负面", "probability": 0.1136 }
    ]
  }
}
```

> **注意**：
> 1. `topLabel` / `candidates[].label` 是模型返回的**原始标签**，中文展示由前端 `getLabelDisplayName()` 映射（`POSITIVE`→正面、`setosa`→山鸢尾等），后端不做翻译。中文链路的标签本身就是「正面 / 负面」。
> 2. 两条链路都是**二分类**，`candidates` 只有两项，概率合计为 1。SST-2 不存在「中性」类，早先版本固定输出 `NEUTRAL: 0.02` 是虚构值，已移除。
>    唯一例外是权重缺失时的**关键词规则兜底**：`modelName` 会变成「中文情感（关键词规则）」/「英文情感（关键词规则）」，此时允许出现第三档「中性/NEUTRAL」——它来自规则打分，不是模型概率。
> 3. 语种判定以中文字符占比为准：中文 ≥ 20% 走中文模型；英文为主的混排文本仍走英文模型，其中的中文片段会被忽略，长句与反讽容易误判。

**错误**

```json
{ "code": 400, "message": "文本不能为空" }
{ "code": 404, "message": "模型不存在" }
```

---

### 6.2 `POST /api/predict/tabular`

结构化数据预测。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `modelId` | Long | 是 | 模型必须上线 |
| `features` | Object | 是 | 特征字典，不能为空对象 |

**路由规则**：`model_api == "xgboost"` 走 `/tabular/predict`；其他按 key 走 `/predict/custom`（body 为 `{"features": {...}}`）。

```bash
curl -s -X POST http://localhost:8080/api/predict/tabular \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"modelId":4,"features":{"sepal_length":5.1,"sepal_width":3.5,"petal_length":1.4,"petal_width":0.2}}'
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "tabular",
    "modelName": "XGBoost",
    "topLabel": "setosa",
    "confidence": 0.986552,
    "duration": 17,
    "candidates": [
      { "label": "setosa", "probability": 0.986552 },
      { "label": "versicolor", "probability": 0.007865 },
      { "label": "virginica", "probability": 0.005583 }
    ]
  }
}
```

> XGBoost 期望 **4 个** Iris 特征且顺序固定（`sepal_length, sepal_width, petal_length, petal_width`）。特征数不匹配时模型服务会回落启发式计算，返回的概率与模型权重无关——联调时请按此顺序传全 4 个字段。

**错误**

```json
{ "code": 400, "message": "features 不能为空" }
```

---

### 6.3 `POST /api/predict/image`

图像预测（分类 / 检测 / 抠图）。

**请求类型**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | File | 是 | 图片，≤ 5 MB |
| `modelId` | Long | 是 | 表单字段，不是 query 参数 |

**流程**：校验模型 → 读取并缓存文件字节 → 上传 MinIO（失败降级不阻断）→ 调用模型服务 → 落库并记录 `file_url`。

```bash
curl -s -X POST http://localhost:8080/api/predict/image \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./test.png;type=image/png" -F "modelId=5"
```

**响应 200**（U²-Net 抠图示例）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "image",
    "modelName": "U2Net",
    "topLabel": "background_removed",
    "confidence": 1.0,
    "duration": 743,
    "candidates": [],
    "resultType": "image_processing",
    "imageBase64": "iVBORw0KGgo..."
  }
}
```

**`PredictResultVO` 字段**

| 字段 | 说明 |
|---|---|
| `type` | `text` / `tabular` / `image` |
| `modelName` | 模型服务返回的模型名 |
| `topLabel` | 置信度最高的标签；图像抠图为 `background_removed` |
| `confidence` | 0–1 的置信度 |
| `duration` | 后端统计的耗时（毫秒） |
| `candidates` | 候选列表 `[{label, probability}]`，目标检测还会带 `bbox` |
| `resultType` | `image_processing` 表示结果为图片（抠图） |
| `imageBase64` | 结果图片的 base64（仅抠图） |
| `predictedClose` | 预测收盘价（仅股票模型） |

**错误**

```json
{ "code": 413, "message": "上传文件过大，单次请求不能超过 5MB" }
{ "code": 400, "message": "读取上传文件失败" }
{ "code": 502, "message": "模型服务不可用，请确认 model-service 已启动" }
```

---

### 6.4 `POST /api/predict/custom`

自定义模型（天气 / 股票 / 中文情感等）的统一入口，按 `model_api` 分派。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `modelId` | Long | 是 | 模型必须上线 |
| `text` | String | 二选一 | 文本类模型 |
| `features` | Object | 二选一 | 表格类模型 |

```bash
curl -s -X POST http://localhost:8080/api/predict/custom \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"modelId":17,"text":"房间很干净，服务态度很好"}'
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "text",
    "modelName": "ChineseSentiment",
    "topLabel": "正面",
    "confidence": 0.703,
    "duration": 848,
    "candidates": [
      { "label": "正面", "probability": 0.703 },
      { "label": "负面", "probability": 0.297 }
    ]
  }
}
```

> 中文情感模型直接返回中文标签（二分类，**没有中性**）。`model_api=bert` 的模型在中文输入时会自动转调同一个中文模型，两者结果一致。

---

## 7. 用户接口 `/api/users`

**全部需认证**。

### 7.1 `GET /api/users/me`

当前登录用户基本信息（不含密码与密保答案）。

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "超级管理员",
    "role": "admin",
    "avatar": "👨‍💼",
    "createTime": "2026-06-03T21:29:50"
  }
}
```

**错误**：`401`（未登录 / 令牌已登出）

---

### 7.2 `GET /api/users/records`

当前用户的预测记录，按 `createTime` 倒序。缓存在 `aimh:dev:user_records::{userId}`，新记录写入后于**事务提交之后**驱逐。

**响应 200**（`data` 为数组）

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 170,
      "modelId": 4,
      "originalFilename": null,
      "fileUrl": null,
      "inputText": "{sepal_length=5.1, sepal_width=3.5, petal_length=1.4, petal_width=0.2}",
      "predictResult": "virginica",
      "confidence": 0.91293,
      "duration": 6,
      "createTime": "2026-10-23T22:55:46"
    }
  ]
}
```

| 字段 | 说明 |
|---|---|
| `inputText` | 文本模型存原文；表格模型存 `features.toString()`；图像模型为 `null` |
| `originalFilename` / `fileUrl` | 仅图像模型有值；MinIO 不可用时 `fileUrl` 为 `null` |

---

### 7.3 `GET /api/users/favorites`

当前用户收藏的模型 ID 列表。

```json
{ "code": 200, "message": "success", "data": [] }
```

---

### 7.4 `POST /api/users/favorites/{modelId}`

收藏模型。**幂等**（底层 `INSERT IGNORE` + 唯一键，重复点击不会报错）。

```json
{ "code": 200, "message": "收藏成功", "data": null }
```

---

### 7.5 `DELETE /api/users/favorites/{modelId}`

取消收藏。

```json
{ "code": 200, "message": "取消收藏成功", "data": null }
```

---

## 8. 管理接口 `/api/admin`

**全部需 `ROLE_ADMIN`**，非管理员返回 `403`。每次模型写操作都会驱逐 `models::online`、`model_detail::{id}` 与 `dashboard_overview::overview` 缓存，并记录审计日志。

### 8.1 `GET /api/admin/models`

模型列表（**含逻辑删除**记录 `status=-1`）。

| 参数 | 默认 | 说明 |
|---|---|---|
| `page` | 无 | 与 `size` **同时传入**才启用分页 |
| `size` | 无 | 限制在 `[1, 100]` |

- 传 `page`+`size` → 返回 `PageVO<ModelInfo>`
- 不传 → 返回完整数组（兼容旧调用）

```bash
curl -s "http://localhost:8080/api/admin/models?page=0&size=2" -H "Authorization: Bearer $TOKEN"
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [ { "id": 1, "name": "ResNet-50", "...": "..." } ],
    "totalElements": 11,
    "totalPages": 6,
    "currentPage": 0,
    "pageSize": 2
  }
}
```

---

### 8.2 `GET /api/admin/models/{id}`

查询单个模型（含已删除）。**响应同 5.2**，但对下线/删除模型不返回 404。

---

### 8.3 `POST /api/admin/models`

新增模型。审计动作 `CREATE_MODEL`。

**请求体**（`ModelSaveDTO`）

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| `modelName` | String | 是 | 非空，≤100 |
| `modelType` | String | 是 | 非空，≤50。取值见下表 |
| `algorithm` | String | 否 | ≤150 |
| `accuracy` | Double | 否 | **0–100 的百分数** |
| `description` | String | 否 | 中文介绍 |
| `cover` | String | 否 | 封面 URL，≤255 |
| `modelApi` | String | 否 | **model-service 的 model_key**，≤255 |
| `status` | Integer | 否 | 1=上线（默认），0=下线 |

`modelType` 取值：`image`（图像分类）、`object`（目标检测）、`text`（文本分析）、`ml`（结构化预测）、`image_advanced`（图像处理/抠图）。

```bash
curl -s -X POST http://localhost:8080/api/admin/models \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"modelName":"WeatherGBM","modelType":"ml","modelApi":"weather","accuracy":87.5,"status":1}'
```

**响应 200**

```json
{ "code": 200, "message": "模型创建成功", "data": { "id": 14, "name": "WeatherGBM", "...": "..." } }
```

> `modelApi` 若填成 `/api/predict/image` 这类 URL，预测时会被 FastAPI 拒绝。启动时的 `DataInitializer#repairModelApis()` 会自愈已知模型名的历史脏数据。

---

### 8.4 `PUT /api/admin/models/{id}`

全量更新模型。审计动作 `UPDATE_MODEL`。请求体同 8.3。

```json
{ "code": 200, "message": "模型更新成功", "data": { "...": "..." } }
```

---

### 8.5 `PATCH /api/admin/models/{id}/status`

切换上架 / 下架。审计动作 `TOGGLE_STATUS`。

**请求体**：`{ "status": 0 }` 或 `{ "status": 1 }`

```json
{ "code": 200, "message": "模型已下架", "data": { "id": 1, "status": 0, "...": "..." } }
```

**错误**：`status` 缺失或不是 0/1 → `400 状态值无效，仅支持 0(下架) 或 1(上架)`

---

### 8.6 `DELETE /api/admin/models/{id}`

逻辑删除（置 `status=-1`）。审计动作 `DELETE_MODEL`。历史预测记录保留 `model_id` 引用。

```json
{
  "code": 200,
  "message": "模型已删除",
  "data": { "id": 1, "name": "ResNet-50", "status": -1, "message": "模型已删除（软删除，历史预测记录保留）" }
}
```

---

### 8.7 `GET /api/admin/users`

用户分页列表。

| 参数 | 默认 | 说明 |
|---|---|---|
| `page` | 0 | 0 起 |
| `size` | 10 | 限制在 `[1, 100]` |

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "username": "admin",
        "nickname": "超级管理员",
        "avatar": "👨‍💼",
        "role": "admin",
        "createTime": "2026-06-03T21:29:50",
        "securityQuestion": "创建学校",
        "updateTime": "2026-10-19T21:25:55"
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "currentPage": 0,
    "pageSize": 2
  }
}
```

> 实体上标了 `@JsonIgnore`，**密码与密保答案不会出现在响应里**。

---

### 8.8 `POST /api/admin/users/{id}/reset-password`

管理员重置用户密码为**随机 12 位临时密码**，并吊销该用户已签发的全部令牌。审计动作 `RESET_USER_PASSWORD`。

**响应 200**

```json
{
  "code": 200,
  "message": "密码重置成功",
  "data": {
    "username": "demo",
    "temporaryPassword": "Kx7mQ2vR9pLb",
    "notice": "临时密码仅显示一次，请通过安全渠道转交用户，并要求登录后立即修改"
  }
}
```

**错误**：`400 用户不存在`

---

## 9. 统计接口 `/api/dashboard`

### 9.1 `GET /api/dashboard/overview`

**公开**。数据统计概览，缓存在 `aimh:dev:dashboard_overview::overview`（TTL 较短）。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalModels": 11,
    "onlineModels": 10,
    "totalUsers": 2,
    "totalCalls": 165,
    "hotModels": ["YOLOv8", "StockRegression", "ResNet-50"]
  }
}
```

| 字段 | 说明 |
|---|---|
| `totalModels` | 模型总数（不含逻辑删除） |
| `onlineModels` | `status=1` 的数量 |
| `totalUsers` | 注册用户数 |
| `totalCalls` | 累计预测记录数 |
| `hotModels` | 按 accuracy 排序的前 3 个在线模型名 |

---

## 10. 健康检查 `/api/health`

**公开**。

### 10.1 `GET /api/health`

后端自身状态与数据库连通性。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "service": "backend",
    "port": 8080,
    "status": "UP",
    "database": "connected"
  }
}
```

`status` 取值：`UP`（数据库连通）/ `DEGRADED`（数据库连不上）。

> `/actuator/**` 已被 `denyAll`，**健康检查请用本接口**。

### 10.2 `GET /api/health/models`

探测模型服务（FastAPI）是否可达，原样透传其 `/health` 响应。

```json
{ "code": 200, "message": "success", "data": { "status": "UP" } }
```

模型服务不可达时**仍返回 200**，但 data 为：

```json
{ "status": "DOWN", "error": "Model service unreachable: ...", "loaded": 0, "failed": 4, "models": {} }
```

---

## 11. 文件代理 `/api/files`

### 11.1 `GET /api/files/{*objectKey}`

**公开**。代理下载 MinIO 中的文件，解决浏览器直连 MinIO 的 CORS 问题。

- 路径段完整捕获（含斜杠、UUID、扩展名），例如 `predictions/2026/10/23/xxx.png`
- 响应为**二进制流**，不是 JSON 包装
- 响应头：`Cache-Control: public, max-age=3600`

```bash
curl -s -o out.png -w "%{http_code} %{content_type} %{size_download}\n" \
  "http://localhost:8080/api/files/predictions/2026/10/23/abc.png"
# 实测：200 image/png 670
```

**错误响应**（实测值，与直觉不符，见下方说明）

| 场景 | 实测 HTTP | 说明 |
|---|---|---|
| 文件存在 | `200` | 返回二进制流 |
| 文件不存在 | `401` ⚠️ | 期望 404，实际被 Spring Security 拦截 |
| 路径为空（`/api/files/`） | `401` | `{*objectKey}` 匹配失败，落入 `anyRequest().authenticated()` |

> ⚠️ **已知缺陷**：`FileProxyController` 用 `response.sendError(404)` 报错，容器会 ERROR dispatch 到 `/error`，而 `/error` 未在 `SecurityConfig` 中放行，于是被认证过滤器拦下并返回 401（JSON：`{"code":401,"message":"未登录或登录已过期，请重新登录","data":null}`）。
> 修复方式二选一：
> 1. 在 `SecurityConfig` 加 `.requestMatchers("/error").permitAll()`（最小改动）；
> 2. 控制器改为返回 `ResponseEntity.status(404).body(...)`，不走 `sendError`。
>
> 在此之前，前端拿不到"文件不存在"的准确信号，图片加载失败时会被当成"未登录"。

> `predict_record.file_url` 存的就是本接口的绝对地址（默认 `http://localhost:8080/api/files/...`）。若前端不在本机访问，需把 `app.file-proxy.base-url` 改为对外可达的网关地址。

---

## 12. 限流规则

| 项目 | 值 |
|---|---|
| 作用范围 | `/api/predict/**` 四个接口 |
| 配额 | **20 次 / 60 秒 / 用户** |
| 算法 | Redis ZSET 滑动窗口，单条 Lua 脚本原子执行 |
| 键格式 | `rate:limit:predict:user:{userId}`（未登录退化为按 IP） |
| 超限响应 | `429 {"code":429,"message":"请求过于频繁，请稍后再试"}` |

超过配额后返回 429；滑动窗口随时间推移逐出旧成员，无需等待整个窗口过期。

```bash
# 观察限流键
redis-cli ZRANGE "rate:limit:predict:user:1" 0 -1 WITHSCORES
```

> 限流由 `ModelInferenceService.checkRateLimit()` 显式调用 `SlidingWindowRateLimiter` 实现，配额写在该类的 `RATE_LIMIT_MAX` / `RATE_LIMIT_WINDOW_SECONDS` 常量里。`ratelimit` 包里只剩 `SlidingWindowRateLimiter` 一个类——原来那套 `@RateLimit` 注解 + 切面已在第四阶段清理，因为它管不到 Agent 走 Service 的那条路径。

---

## 13. 已知限制与取舍

| # | 现象 | 原因 | 取舍 |
|---|---|---|---|
| 1 | 登录密码错误返回业务码 400，不是 401 | 密码错误走 `BusinessException`，与「未认证」在语义上分开 | 前端按 `code + message` 判断；第三方接入需注意这一点 |
| 2 | 预测结果标签为英文（`POSITIVE`、`setosa`） | 后端只透传模型原始输出，中文映射在前端 `LABEL_DISPLAY_MAP` | 后端保持与推理服务一致；如需对外 API 应在后端统一翻译 |
| 3 | `app.file-proxy.base-url` 默认 `http://localhost:8080` | 已支持 `FILE_PROXY_BASE_URL` 环境变量覆盖 | 跨机部署时必须改成对外可达地址，否则历史图片 URL 打不开 |

> 曾经有个问题：`GET /api/files/<不存在的 key>` 返回 401 而不是 404，因为 `sendError(404)` 会触发一次 ERROR 派发到 `/error`，而那个路径没有放行。现在 `SecurityConfig` 已对 `ASYNC` / `ERROR` 派发放行，行为恢复正常。

## 维护提醒

1. **上传上限四处同步**：`application*.yml` 的 `max-file-size`/`max-request-size`、`FileStorageService.MAX_FILE_SIZE`、model-service 的 `_MAX_UPLOAD_MB`、`frontend/nginx.conf` 的 `client_max_body_size`。改一处不算改完。
2. **新增模型五步**：权重 → model-service 模块 → registry/custom 分支 → `model_info` 记录（`sql/007` 风格，按 `model_name` 幂等）→ 前端 `MODEL_GUIDES` + 表单分支。
3. **改完模型描述要清缓存**：`DEL aimh:dev:models::online`、`aimh:dev:model_detail::{id}`。
4. **`application*.yml` 被 `.gitignore` 排除**，新克隆环境请从 `application-example.yml` 复制后填值。

---


---

## 附录 A：model_api 与模型对照表

`model_api` 是跨服务调度的唯一依据，取值必须是模型服务认识的 `model_key`。

| id | 模型名 | type | model_api | status | 输入 | 输出 |
|---:|---|---|---|---|---|---|
| 1 | ResNet-50 | image | `resnet50` | 1 | 图片 | ImageNet 1000 类中文标签 Top-5 |
| 2 | YOLOv8 | object | `yolov8` | 1 | 图片 | COCO 80 类目标 + bbox |
| 3 | BERT-Base | text | `bert` | 1 | 中英文文本 | 二分类：英文 POSITIVE / NEGATIVE，中文自动转 正面 / 负面 |
| 4 | XGBoost | ml | `xgboost` | 1 | 4 个 Iris 特征 | setosa / versicolor / virginica |
| 5 | U2Net | image_advanced | `u2net` | 1 | 图片 | 透明 PNG base64 |
| 13 | ChestCT | image | `chest_ct` | 1 | CT 切片 | 正常 / 腺癌 / 大细胞癌 / 鳞状细胞癌 |
| 14 | WeatherGBM | ml | `weather` | 1 | 气象特征 | rain / sun / fog / drizzle / snow |
| 15 | StockRegression | ml | `stock` | 1 | 行情特征 | 预测收盘价（数值，非涨跌方向） |
| 16 | FlowerCNN | image | `flower` | 1 | 图片 | 雏菊 / 蒲公英 / 玫瑰 / 向日葵 / 郁金香 |
| 17 | ChineseSentiment | text | `chinese_sentiment` | 1 | 中文文本 | 正面 / 负面（**无中性**，与 `bert` 中文链路同一模型） |

> 中文标签映射维护在 `frontend/src/data/models.ts` 的 `LABEL_DISPLAY_MAP` 与 `MODEL_GUIDES`，新增模型需同步更新。

---

## 附录 B：模型服务内部接口（FastAPI）

**仅后端调用，前端不要直连。** 基址 `http://localhost:8001`（由 `model-service.base-url` 配置）。

| 方法 | 路径 | 说明 | 后端调用方 |
|---|---|---|---|
| GET | `/health` | `{"status":"UP"}` | `ModelHealthController` |
| POST | `/predict` | 统一预测入口，body 为 `PredictRequest`（`model_key` + `text`/`image_base64`/`image_url`/`features`）；**`model_key` 白名单仅 `resnet50`/`bert`/`yolov8`/`xgboost`/`u2net`** | — |
| POST | `/text/classify` | BERT 文本分类 | `predictText` |
| POST | `/tabular/predict` | XGBoost 表格预测 | `predictTabular` |
| POST | `/image/classify` | 旧版图像分类（兼容） | — |
| POST | `/predict/image` | 图像预测主入口（multipart） | `predictImage` |
| POST | `/predict/custom` | 自定义模型（天气 / 股票 / 中文情感 / chest_ct / flower） | `predictCustom` |

`/predict` 的响应结构：

```json
{
  "model_name": "resnet50",
  "results": [ { "label": "golden retriever", "probability": 0.87, "bbox": null } ],
  "duration_ms": 123,
  "timestamp": "2026-10-24T06:00:00Z"
}
```

> 后端 `PredictResultMapper` 负责把「新版 `/predict`、`/predict/image`、`/predict/custom`、旧版 `/image/classify`」等多种响应形状归一化成 `PredictResultVO`。新增模型服务响应格式时，优先改这个适配器而不是控制器。

---

## 附录 C：权限矩阵

来自 `SecurityConfig#filterChain`：

| 路径 | 访问要求 |
|---|---|
| `OPTIONS /**` | 公开（CORS 预检） |
| `/api/auth/login`、`/register`、`/forgot-password/**` | 公开 |
| `/api/health`、`/api/health/**` | 公开 |
| `GET /api/models`、`GET /api/models/**` | 公开 |
| `/api/dashboard/overview` | 公开 |
| `GET /api/files/**` | 公开 |
| `/api/predict/**` | 需认证 |
| `/api/users/**` | 需认证 |
| `/api/admin/**` | 需认证 **且** `ROLE_ADMIN` |
| `/actuator/**` | **拒绝所有外部访问**（实测匿名访问返回 `401`） |
| `/docs`、`/v3/api-docs/**`、`/swagger-ui/**` | 仅当 `springdoc.api-docs.enabled=true`（dev 为 true，实测 `/docs` 返回 `302` 跳转 Swagger UI；prod 为 false，返回 `403`） |
| 其他任何请求 | 需认证 |

**响应差异**：未登录 → `401`；已登录但权限不足 → `403`。

**两处容易误解**

- **登录失败是 400 不是 401**：密码错误由 `BusinessException` 抛出，语义是"业务校验不通过"。只有"压根没带令牌/令牌失效"才是 401。
- **`/api/files/**` 文件不存在是 401 不是 404**，原因见[第 11 节](#11-文件代理-apifiles)。

---

## 附录 D：前端调用约定

`frontend/src/api/request.ts` 是所有请求的唯一出口：

- 自动拼接 `VITE_BACKEND_BASE_URL`（默认 `http://localhost:8080`）
- 令牌取自 `localStorage` 键 `ai-modelhub` 的 `state.token`
- 非 FormData 请求自动加 `Content-Type: application/json`
- 默认 12 秒超时（`AbortController`）
- 同时校验 `response.ok` 与 `body.code >= 400`，统一抛出 `ApiError`
- `429` 只打印 `console.warn`，不输出错误堆栈

**API 模块划分**

| 文件 | 覆盖接口 |
|---|---|
| `src/api/auth.ts` | `/api/auth/*` |
| `src/api/models.ts` | `/api/models/*` |
| `src/api/predict.ts` | `/api/predict/*` |
| `src/api/user.ts` | `/api/users/*`、`/api/admin/users/*` |
| `src/api/dashboard.ts` | `/api/dashboard/*` |

---

*本文档基于源码阅读 + 本机实跑生成（backend 8080、model-service 8001、MySQL/Redis/MinIO 在线）。示例中的响应为真实采集，但 `duration`、`totalCalls` 等随时间变化。*
