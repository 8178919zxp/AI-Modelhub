# ModelHub Copilot —— 接口文档

所有接口前缀 `/api/agent`，除注明外均需 `Authorization: Bearer <JWT>`。
响应统一为 `{code, message, data}`；`code >= 400` 表示失败。

**权限**：普通用户只能访问自己的数据；访问他人会话/任务统一返回 **404**（不返回 403，
避免「该 id 是否存在」被探测）。

---

## 一、会话

### 1. 创建会话

```
POST /api/agent/sessions
Content-Type: application/json

{"title": "可选，留空为「新会话」"}
```

响应：
```json
{"code":200,"message":"success","data":{"id":12,"title":"新会话","messageCount":0,"lastMessageAt":"2026-11-03T01:20:00"}}
```

### 2. 会话列表

```
GET /api/agent/sessions
```

按最后消息时间倒序；只返回当前用户的。

```json
{"code":200,"data":[{"id":12,"title":"我有一批电影评论…","messageCount":10,"lastMessageAt":"..."}]}
```

### 3. 删除会话（软删除）

```
DELETE /api/agent/sessions/{id}
```
成功返回 `data: null`；他人会话 / 不存在 → 404。

### 4. 历史消息

```
GET /api/agent/sessions/{id}/messages
```

```json
{"code":200,"data":[
  {"id":91,"role":"USER","content":"帮我推荐模型","createTime":"..."},
  {"id":92,"role":"ASSISTANT","content":"**推荐**…","createTime":"..."},
  {"id":93,"role":"TOOL","content":"推荐 2 个模型","toolName":"recommendModels","createTime":"..."}
]}
```

`role` 取值：`USER` / `ASSISTANT` / `TOOL`。TOOL 记录是审计留痕，前端渲染时可跳过。

---

## 二、对话

### 1. 流式对话（SSE）

```
POST /api/agent/chat
Accept: text/event-stream
Content-Type: application/json

{"sessionId": 12, "message": "我想识别图片里的物体"}
```

`sessionId` 可省略，服务端会新建会话并先下发 `session` 事件。

事件序列（完整定义见 `docs/AGENT_ARCHITECTURE.md` §7）：

```
event:session
data:{"sessionId":12,"title":"我想识别图片里的物体"}

event:token
data:{"text":"你"}

event:tool_result
data:{"tool":"recommendModels","success":true,"elapsedMs":2149,"summary":"推荐 1 个模型"}

event:recommend
data:{"models":[{"id":13,"name":"ChestCT","typeLabel":"图像识别","inputType":"IMAGE","reason":"…","score":0.95}]}

event:confirm_required
data:{"taskId":1,"taskNo":"T202611030103110428","modelName":"ResNet-50","inputType":"IMAGE","inputSummary":"test.png（81 KB）"}

event:done
data:{"sessionId":12,"messageId":94,"tokenCount":121,"elapsedMs":1903}
```

错误：
```
event:error
data:{"message":"大模型响应超时（长时间无输出），请稍后重试"}
```

**注意**：`confirm_required` 只表示「任务已准备好」，此时模型**没有**被调用。

### 2. 非流式对话

```
POST /api/agent/chat/sync
{"sessionId": 12, "message": "…"}
```

```json
{"code":200,"data":{"sessionId":12,"messageId":94,"content":"完整回答","tokenCount":121,"elapsedMs":1903}}
```

内部复用同一套编排逻辑，供脚本与不支持 SSE 的客户端使用。

---

## 三、附件

### 上传待推理文件

```
POST /api/agent/files
Content-Type: multipart/form-data

file=<二进制>
sessionId=<可选>
```

限制：≤5MB；JPG / PNG / WEBP / GIF / BMP。

```json
{"code":200,"data":{"id":1,"filename":"test.png","size":83473,"contentType":"image/png"}}
```

返回的 `id` 需要告诉 Agent（前端会自动拼进消息正文），Agent 才能用 `prepareInference` 引用它。

---

## 四、推理任务

### 1. 确认并执行

```
POST /api/agent/tasks/{id}/confirm
```

**这是唯一能把任务从「待确认」推进到「执行」的入口**，且必须由用户主动触发。

成功：
```json
{"code":200,"data":{
  "taskId":1,"taskNo":"T202611030103110428","status":"SUCCESS",
  "modelId":1,"modelName":"ResNet-50",
  "topLabel":"围嘴","confidence":0.171928,"durationMs":420,
  "recordId":210,
  "explanation":"模型给出的首选类别是「围嘴」，但概率只有约 17%…"
}}
```

失败场景：

| 情况 | 返回 |
|---|---|
| 任务不存在 / 是别人的 | 404「任务不存在」 |
| 已执行过 | 400「该任务已执行完成，无需重复执行」 |
| 超过 10 分钟未确认 | 400「任务已过期…请重新发起」 |
| 正在执行 | 400「该任务正在执行中，请稍候」 |

### 2. 查询任务

```
GET /api/agent/tasks/{id}
```

```json
{"code":200,"data":{"taskId":1,"taskNo":"T2026…","status":"SUCCESS","modelName":"ResNet-50",
  "inputType":"IMAGE","inputSummary":"test.png（81 KB）","resultSummary":"围嘴 0.1719","recordId":210}}
```

### 3. 任务列表

```
GET /api/agent/tasks?limit=20
```

---

## 五、知识库

### 1. 上传文档（管理员）

```
POST /api/agent/knowledge/documents
Content-Type: multipart/form-data

file=<.md / .txt，≤2MB>
title=<可选>
category=<可选>
```

```json
{"code":200,"data":{"id":5,"title":"我的文档","chunkCount":7,"charCount":2314}}
```

### 2. 文档列表

```
GET /api/agent/knowledge/documents
```

### 3. 删除文档（管理员）

```
DELETE /api/agent/knowledge/documents/{id}
```

### 4. 检索（调试 / 展示来源）

```
GET /api/agent/knowledge/search?q=图片太大&topK=5
```

```json
{"code":200,"data":[
  {"chunkId":9,"docId":2,"docTitle":"模型输入输出规范","heading":"通用文件限制",
   "content":"图像类模型的上传限制：单文件不超过 5MB…","score":4.45,"similarity":0.71}
]}
```

`score` 是 MySQL 全文相关度，`similarity` 是向量余弦；二者量纲不同，服务端用
`max(归一化全文分, 余弦×0.9)` 融合后再排序。

### 5. 补算向量（管理员）

```
POST /api/agent/knowledge/reindex
```

用于「先导入文档、后补上模型文件」的场景。

---

## 六、错误码汇总

| code | 含义 | 典型场景 |
|---|---|---|
| 400 | 参数/状态不合法 | 空消息、缺附件、任务已过期、重复确认 |
| 401 | 未登录 | 缺 Token / Token 过期 / 已登出 |
| 403 | 权限不足 | 非管理员改知识库 |
| 404 | 不存在或无权访问 | 他人会话/任务 |
| 429 | 频率超限 | 每日 200 次大模型额度用尽 |
| 500 | 服务端异常 | 上游不可用等 |

---

## 七、curl 速查

```bash
TOKEN=<你的 JWT>

# 新建会话
curl -X POST http://localhost:8080/api/agent/sessions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}'

# 对话（流式）
curl -N -X POST http://localhost:8080/api/agent/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"平台有哪些图像模型？"}'

# 上传图片
curl -X POST http://localhost:8080/api/agent/files \
  -H "Authorization: Bearer $TOKEN" -F "file=@test.png;type=image/png"

# 确认执行
curl -X POST http://localhost:8080/api/agent/tasks/1/confirm \
  -H "Authorization: Bearer $TOKEN"
```
