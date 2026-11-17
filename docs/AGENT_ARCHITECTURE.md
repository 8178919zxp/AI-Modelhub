# ModelHub Copilot —— Agent 架构与调用链

> 本文所有类名、表名、接口路径均对应仓库真实代码（`backend/src/main/java/com/aimodelhub/agent/`，共 54 个类 / 约 6500 行）。
> 不是设计稿，是「已经跑起来的版本」的说明。

---

## 1. 整体位置

Agent 不是独立服务，而是原平台上的一个业务域，和 `model` / `predict` / `user` 平级：

```
com.aimodelhub
├── model/         模型管理（原有）
├── predict/       推理业务（原有，阶段3把流程下沉为 ModelInferenceService）
├── user/          用户与收藏（原有）
├── admin/         后台（原有）
├── security/      JWT 鉴权（原有）
└── agent/         ★ 本次新增
    ├── config/     Spring AI 装配、参数、知识库初始化
    ├── controller/ HTTP 入口（会话/对话/任务/附件/知识库）
    ├── service/    业务编排（对话、推荐、推理、知识库、会话、任务、配额）
    ├── tool/       暴露给大模型的工具（请求级实例）
    ├── memory/     ChatMemory 的 Redis 实现 + 消息编解码
    ├── rag/        ONNX 中文向量模型、分词器、切分器
    ├── mapper/     MyBatis（6 个）
    ├── entity/     7 张表对应实体
    ├── dto/ vo/    请求与响应
    └── support/    系统提示词、当前用户
```

```
                    ┌───────────────────────────────┐
  浏览器 /agent ───▶│ AgentController               │
                    │  POST /chat (SSE)             │
                    │  POST /chat/sync              │
                    │  POST /tasks/{id}/confirm     │
                    └──────────────┬────────────────┘
                                   ▼
                    ┌───────────────────────────────┐
                    │ AgentChatService              │  唯一编排入口
                    │  会话定位 / 上下文自愈 / 配额  │
                    └───┬───────────┬───────────┬───┘
                        │           │           │
             ┌──────────▼──┐  ┌─────▼─────┐  ┌──▼──────────────┐
             │ AgentSession│  │ AgentTool │  │ AgentKnowledge  │
             │ Service     │  │ Kit       │  │ Service         │
             └──────┬──────┘  └─────┬─────┘  └──┬──────────────┘
                    │               │            │
                    ▼               ▼            ▼
              MySQL(会话/消息)   Spring AI     MySQL 全文 + ONNX 向量
                                 ChatClient
                                     │
                                     ▼
                              DeepSeek deepseek-flash
```

---

## 2. 一次对话的完整调用链

以「我有一批电影评论，想区分好评差评」为例：

```
1. 前端 POST /api/agent/chat  {sessionId, message}
2. JwtAuthFilter → principal = Long userId（原有链，未改）
3. AgentController.chat()
     CurrentUser.requireId()            ← 只认 SecurityContext，不读请求体
4. AgentChatService.stream(userId, sessionId, message)
     Flux.defer 把「准备阶段」也放进订阅后才执行，
     外层 onErrorResume 捕获准备阶段的异常 → toUserMessage() → error 事件
     4.1 assertApiKeyConfigured()       没配 Key → 业务异常（不是 401）
     4.2 校验输入长度（≤4000）
     4.3 AgentSessionService.requireOwned / create
            → agent_session（无 sessionId 时新建）
     4.4 restoreContextIfNeeded()
            Redis 窗口为空 → 从 agent_message 回填最近 N 条
     4.5 AgentSessionService.append(USER)   ← 先落库再调模型
     4.6 AgentQuotaService.checkAndConsume  ← Redis INCR，200 次/天
     4.7 AgentKnowledgeService.retrieve(message)
            → MySQL FULLTEXT + ONNX 向量 → 命中片段拼进 system prompt
     4.8 chatClient.prompt()
            .system(人设 + 本轮资料)
            .user(message)
            .toolCallbacks(5 个工具对象，共 9 个工具)
            .advisors(ChatMemory 顾问)
            .stream().content()
5. Spring AI 发 HTTP 到 https://api.deepseek.com/v1/chat/completions
6. 模型返回 tool_calls: recommendModels("中文影评二分类")
     Spring AI 反射调用 ModelRecommendTool.recommendModels(...)
       → AgentModelRecommendService.recommend()
           ① AgentModelQueryService.allOnline()  取真实在线模型
           ② 第二次调用 DeepSeek（JSON 模式）打分排序写理由
           ③ 按 id 回查数据库回填，非法 id 丢弃
           ④ 结果放进 AgentToolContext
     工具结果作为 role=tool 消息回灌，模型继续生成
7. 文本 token 流式返回 → SSE event:token
8. 流结束：
     AgentChatService 落库 ASSISTANT 消息
     工具留痕写入 agent_message(role=TOOL)
     推送 event:tool_result / event:recommend / event:done
```

关键点：**第 6 步的「第二次 DeepSeek 调用」是推荐质量的关键**——主对话模型负责理解与表达，
推荐子调用负责「在真实候选集内排序」，两者职责分开，避免主模型既当裁判又当选手。

---

## 3. Tool Calling 流程（为什么不会被滥用）

```
模型输出 tool_calls
       │
       ▼
Spring AI ToolCallingManager
   ├─ 解析 JSON 参数 → 反射调用 @Tool 方法
   ├─ 异常捕获 → 转成文本回灌（模型能看到错误）
   └─ 最多 N 轮，超过则停止
       │
       ▼
我们的 @Tool 方法（ModelQueryTool / ModelRecommendTool / ModelPredictTool /
                   InferenceTool / KnowledgeTool）
   ├─ 身份：从 AgentToolContext 读 userId（**方法签名里没有 userId 参数**）
   ├─ 业务：一律调用 Service，不写 SQL、不碰 Mapper
   └─ 留痕：context.recordInvocation(...) → 落 agent_message(role=TOOL)
```

五个工具对象是**每次请求 new 出来的**（`AgentToolKit.build(context)`），
依赖是单例。这样用户身份固定在对象字段里，模型无论怎么构造参数都改不了。

### 工具清单

| 工具 | 作用 | 落点 |
|---|---|---|
| `listAvailableModels` | 在线模型列表（可按类型/关键词过滤） | `AgentModelQueryService.listOnline` |
| `getModelDetail` | 单模型详情 + 输入要求 | `AgentModelQueryService.detail`（强制 status=1） |
| `listModelsByCategory` | 分类聚合 | `AgentModelQueryService.categories` |
| `getModelInputRequirements` | 输入类型/必填字段/示例 | `AgentModelQueryService.inputRequirements` |
| `recommendModels` | 语义推荐（真实 id 回填） | `AgentModelRecommendService.recommend` |
| `getInferenceHistory` | 本人推理历史 | `PredictRecordService.getUserRecords` |
| `prepareInference` | 创建**待确认**任务（不执行） | `AgentInferenceService.prepare` |
| `executeInference` | 执行**已确认**任务 | `AgentInferenceService.execute` |
| `searchKnowledgeBase` | 知识库检索 | `AgentKnowledgeService.retrieve` |

---

## 4. 推理闭环与「用户确认」边界

```
用户："用 ResNet-50 分析这张图" + 已上传附件 id=1
   │
   ├─ 模型调 prepareInference(modelId=1, attachmentId=1)
   │     AgentInferenceService.prepare()
   │       校验：模型在线 → 输入规范存在 → 附件属于本人 → 类型是图片
   │       写 agent_task(status=PENDING, task_no=T2026...)
   │       返回 taskId（**不调用模型**）
   │
   ├─ SSE 推 confirm_required{taskId, modelName, inputSummary}
   │     前端渲染「确认执行」按钮
   │
   ├─ 用户点击 → POST /api/agent/tasks/1/confirm   ← 只有这里能改状态
   │     ① AgentTaskService.requireOwned（他人任务 404）
   │     ② 过期校验（10 分钟）→ EXPIRED
   │     ③ confirm：UPDATE ... WHERE status='PENDING'  ← 影响行数=0 则拒绝
   │     ④ claimForRun：UPDATE ... WHERE status='CONFIRMED' ← 抢执行权
   │     ⑤ ModelInferenceService.predictImage(...)   ← 与网页端同一条链路
   │     ⑥ 写 agent_task SUCCESS + record_id
   │     ⑦ DeepSeek 生成解读 → 写回会话 + Redis 上下文
   │
   └─ 返回 {topLabel, confidence, durationMs, recordId, explanation}
```

**为什么 LLM 无法绕过**：

| 环节 | 约束 |
|---|---|
| 状态迁移 | 数据库条件 UPDATE + 影响行数判断，非内存标记 |
| 执行入口 | `executeInference` 先查状态，PENDING → 抛「需要用户确认」 |
| 确认接口 | 需 JWT，且只能操作 `user_id = 自己` 的任务 |
| 重复执行 | CONFIRMED→RUNNING 只成功一次；已 SUCCESS 的再确认返回 400 |

---

## 5. RAG 流程

```
导入：
  .md/.txt 上传（管理员）
    → 读取为 UTF-8 文本
    → KnowledgeChunker.split()：按 ## 标题分节 → 节内按句边界切块（400 字，重叠 60）
    → 每块 OnnxEmbeddingService.embed(块, forQuery=false)
          BGE-small-zh-v1.5（24MB ONNX，512 维）+ 自写 BertTokenizer
          CLS 池化 + L2 归一化
    → agent_knowledge_chunk(content, embedding JSON, doc_id, heading)

检索：
  query
    ├─ MySQL：MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE)   ← ngram 分词，中文可用
    └─ 向量：embed(query, forQuery=true) × 每个 chunk 的向量 → 余弦（已归一化，即点积）
         低于 min-similarity(0.42) 丢弃
    融合：score = max(全文归一化分, 余弦×0.9) + 双路命中 0.15
    取 topK=5 → 拼成【资料1】…【资料N】注入 system prompt
```

**关于 TopK 和重排**：两路各取 20 条候选，融合排序后截断到 `rag.top-k`（默认 5），这个参数是真在用的（`AgentKnowledgeService.retrieve` 里 `Math.min(topK, properties.getRag().getTopK())`）。**没有 rerank 模型**——排序完全靠上面那条融合公式，没有第二步交叉编码器重排。

融合策略也不是标准 RRF。纯 RRF 只看名次不看分差，会把"全文第一但相关度只有 0.1"和"全文第五但相关度 0.9"排反；这里用归一化后取较强者，再加一个双路命中奖励，保住分差信息。`KnowledgeHitVO` 里存两份原始分（MySQL 相关度和余弦相似度），融合在服务层做，因为两个分数不在一个量纲上。

**边界**：知识库只回答「怎么用、有什么限制、常见问题」。
模型名称/在线状态/输入字段一律以工具返回（MySQL）为准；资料与工具冲突时以工具为准。
检索为空 → 不注入任何内容，提示词要求模型明说「知识库暂无相关资料」。

**降级**：向量模型缺失时 `isVectorSearchAvailable()=false`，只走全文检索，功能不中断。

---

## 6. 上下文管理（MySQL + Redis 双层）

| 层 | 存什么 | 生命周期 | 作用 |
|---|---|---|---|
| MySQL `agent_message` | 全量消息（USER/ASSISTANT/TOOL） | 永久 | 历史回放、审计、上下文重建 |
| Redis `agent:memory:{sessionId}` | 最近 20 条（Spring AI `Message` 的 JSON） | 2 小时 | 每轮请求的工作记忆 |

- 写入：`MessageChatMemoryAdvisor` 在每轮结束后写回窗口
- 读取：advisor 在请求前注入历史
- **自愈**：窗口为空（重启/TTL/新会话）时，`AgentChatService.restoreContextIfNeeded`
  从 MySQL 取最近 20 条纯对话回填——只回填 USER/ASSISTANT，TOOL 消息依赖
  tool_call_id 链路，脱离原始往返重建会让模型困惑
- 故障：Redis 不可用时 `RedisChatMemoryRepository` 捕获异常并返回空列表，
  该轮退化为「无历史」，但对话本身仍然可用

---

## 7. SSE 事件协议

```
event:session            {"sessionId":12,"title":"我有一批电影评论…"}
event:token              {"text":"你"}
event:tool_result        {"tool":"recommendModels","success":true,"elapsedMs":2149,"summary":"推荐 1 个模型"}
event:recommend          {"models":[{id,name,typeLabel,inputType,reason,score,...}]}
event:confirm_required   {"taskId":1,"taskNo":"T2026...","modelName":"ResNet-50",
                          "inputType":"IMAGE","inputSummary":"test.png（81 KB）"}
event:done               {"sessionId":12,"messageId":94,"tokenCount":121,"elapsedMs":1903}
event:error              {"message":"大模型响应超时，请稍后重试"}
```

前端用 `fetch + ReadableStream` 解析（`EventSource` 不能带 `Authorization` 头）。
后端返回 `Flux<ServerSentEvent<String>>`，由 Spring MVC 管理流生命周期；
客户端在「已收到 done/error 后连接被断开」时按正常结束处理（部分环境会报
`ERR_INCOMPLETE_CHUNKED_ENCODING` / `terminated`）。

### 7.1 error 事件里的文案从哪来

SSE 一旦建立，HTTP 状态码就固定是 200，错误只能靠 `event:error` 传达。
所以「谁把异常翻译成人话」这件事必须收在一个地方：`AgentChatService#toUserMessage(Throwable)`。

| 异常来源 | 用户看到的话 |
|---|---|
| `DEEPSEEK_API_KEY` 未配置（`assertApiKeyConfigured`） | AI 助理尚未配置大模型密钥，请联系管理员 |
| `NotFoundException`（会话不属于当前用户 / 不存在） | 会话不存在或已失效 |
| `UnauthorizedException` | 登录状态已失效，请重新登录 |
| `RateLimitException` / 业务校验失败 | 异常自带的文案（这些文案本来就是写给用户的） |
| 上游 401 / 403 | 大模型认证失败，请联系管理员检查密钥配置 |
| 上游 402 | 大模型账户余额不足，请联系管理员 |
| 上游 429 | 大模型调用频率已超限，请稍后重试 |
| 上游 5xx | 大模型服务暂时不可用，请稍后重试 |
| 工具执行异常 | 助手调用工具时出错，请稍后重试 |
| 其余一切 | 助手暂时不可用，请稍后重试 |

两条硬规则：

1. **兜底分支不拼接 `e.getMessage()`**。上游异常的 message 里可能是完整的 URL、
   请求体甚至密钥片段，直接透传等于把内部信息送进聊天窗口；真实异常只进日志。
2. **只发一个 error 事件**。准备阶段的异常由 `stream()` 外层 `onErrorResume` 接住，
   流式中途的异常由 `doStream()` 内层接住，两者互斥，不会重复发，也不会在 error 之后再补一个 done。

### 7.2 两个只在部署态才出现的坑（已修，务必别改回去）

| 现象 | 根因 | 正确做法 |
|---|---|---|
| 本地正常，部署后推荐卡/确认卡不出现，气泡长时间空白 | Nginx 默认 `proxy_buffering on`，把 SSE 攒到流结束才吐 | `/api/agent/chat` 单独 location：`proxy_buffering off`、`gzip off`、`chunked_transfer_encoding on`、读超时 300s |
| 流正常输出完，但连接被异常中断 / 500 | 异步 Servlet 在结束时再走一次 `ASYNC` 派发，该派发线程没有 `SecurityContext`，过滤器链二次鉴权抛 `AccessDeniedException` | `dispatcherTypeMatchers(ASYNC, ERROR).permitAll()`——能走到异步派发说明首次请求已鉴权，放行不降低安全性 |

判断方法：同一请求「直连 8080 正常、经 Nginx 异常」→ 先看代理缓冲；
「后端日志出现 `Unable to handle the Spring Security Exception`」→ 看异步派发放行。

---

## 8. 数据库 ER

```
┌──────────────────┐        ┌──────────────────┐
│ agent_session    │1      N│ agent_message    │
│──────────────────│◀───────│──────────────────│
│ id (PK)          │        │ id (PK)          │
│ user_id    ──────┼──┐     │ session_id (FK*) │
│ title            │  │     │ user_id          │
│ message_count    │  │     │ role(U/A/T)      │
│ last_message_at  │  │     │ content          │
│ status           │  │     │ tool_name        │
└──────────────────┘  │     │ tool_payload JSON│
                      │     └──────────────────┘
┌──────────────────┐  │     ┌──────────────────┐
│ agent_task       │  │     │ agent_attachment │
│──────────────────│  │     │──────────────────│
│ id (PK)          │  │     │ id (PK)          │
│ task_no (UQ)     │  │     │ user_id          │
│ user_id     ─────┼──┤     │ session_id       │
│ session_id       │──┘     │ original_filename│
│ model_id ────────┼──┐     │ storage_key      │
│ task_status      │  │     │ status(TEMP/USED)│
│ input_type       │  │     └──────────────────┘
│ input_payload    │  │
│ attachment_id    │──┘     ┌────────────────────────┐
│ result_payload   │        │ agent_model_profile    │
│ record_id ───────┼──┐     │────────────────────────│
└──────────────────┘  │     │ model_id (UQ)          │
                      │     │ input_type             │
┌──────────────────┐  │     │ required_fields JSON   │
│ predict_record   │◀─┘     │ example_input          │
│ (原有表)          │        └───────────┬────────────┘
└──────────────────┘                    │
┌──────────────────┐        ┌───────────▼────────────┐
│ model_info (原有) │◀───────│ (逻辑关联，无外键)      │
└──────────────────┘        └────────────────────────┘

┌────────────────────────┐      ┌──────────────────────┐
│ agent_knowledge_document│1   N│ agent_knowledge_chunk│
│────────────────────────│◀─────│──────────────────────│
│ id (PK)                │      │ id (PK)              │
│ title / category       │      │ doc_id (FK*)         │
│ chunk_count            │      │ chunk_index          │
│ status                 │      │ content  (FULLTEXT)  │
└────────────────────────┘      │ embedding JSON       │
                                └──────────────────────┘

* 全部为逻辑关联，不建外键（沿用既有库风格）
```

迁移脚本：`sql/009`~`sql/012`，全部 `CREATE TABLE IF NOT EXISTS`，可重复执行。

---

## 9. Spring AI 负责什么，我们负责什么

用 Spring AI 不等于业务免费。分界线是这样的：

| 事情 | 谁做 |
|---|---|
| 拼 HTTP 请求、放 API Key、解析 SSE 增量 | Spring AI（`OpenAiChatModel`） |
| 把 `@Tool` 方法反射成 function schema | Spring AI（`ToolCallingManager`） |
| 解析 `tool_calls`、反射调用 Java 方法、把结果回灌 | Spring AI |
| 上下文按窗口裁剪（保留最近 N 条） | Spring AI（`MessageWindowChatMemory`） |
| 上下文持久化到 Redis | 我们（`RedisChatMemoryRepository`） |
| `Message` ↔ JSON 编解码 | 我们（`AgentMessageCodec`） |
| 工具的业务实现、权限、留痕 | 我们 |
| 推荐候选集筛选与 id 校验 | 我们 |
| 推理确认状态机 | 我们 |
| 知识库切分、向量化、混合检索 | 我们（onnxruntime 只负责跑前向） |
| SSE 事件协议 | 我们 |

换句话说，Spring AI 解决"怎么和大模型说话"，"能不能看这条数据、能不能执行这个操作"一行都不管——而这恰好是 LLM 应用里最容易出事的地方。

### DeepSeek 的接入方式

用的是 OpenAI 兼容协议，所以直接上 `spring-ai-starter-model-openai`，全项目只有这一个模型供应商，没有切换入口。配置在 `application*.yml`：

```yaml
spring:
  ai:
    openai:
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      api-key: ${DEEPSEEK_API_KEY:sk-not-configured}
      chat:
        options:
          model: ${DEEPSEEK_MODEL:deepseek-flash}
          temperature: 0.3
          max-tokens: 4096
```

`AgentAiConfig` 把这个自动配置好的 `OpenAiChatModel` 包成唯一的 `ChatClient` Bean，业务代码只注入 `ChatClient`。两个子调用共用它，但参数不同：

- 主对话：`temperature 0.3`、开工具调用、流式返回。
- 推荐排序：`temperature 0` + `responseFormat=JSON_OBJECT`，要结构稳定，不要它发挥。

调用形式（`AgentChatService`）：

```java
chatClient.prompt()
    .system(systemPrompt)          // 人设 + 本轮检索到的资料
    .user(message)
    .toolCallbacks(callbacks)      // 5 个工具对象，共 9 个 @Tool 方法
    .advisors(spec -> spec.advisors(memoryAdvisor)
                          .param(ChatMemory.CONVERSATION_ID, conversationId))
    .stream().content();           // Flux<String>
```

**两个踩过的坑**，都跟 `deepseek-flash` 的特性有关：

1. `max-tokens` 给小了会拿到空 `content`。这个模型的思维链 token 计入 `completion_tokens`，设 50 时会被思维链吃光，正文一个字都没有。项目里设 4096。
2. 响应里有 `reasoning_content` 字段，Spring AI 的响应模型没有对应属性，Jackson 会忽略（不报错）。所以拿不到思维链，只影响"思考中"这类展示，不影响业务。

---

## 10. 关键设计取舍

| 决策 | 选择 | 理由 |
|---|---|---|
| 上下文存哪 | MySQL 全量 + Redis 窗口 | 全量进 prompt 会爆 token；只存 Redis 重启即失忆 |
| 工具身份 | 请求级对象持有 userId | 方法参数传 userId 等于把越权入口交给 LLM |
| 推荐实现 | 两次模型调用（对话 + 排序） | 让主模型同时当裁判和选手，容易硬推 |
| 推理执行 | REST 确认接口触发 | 依赖 LLM 判断「用户确认了」不可靠 |
| 去重执行 | 数据库条件 UPDATE | 多实例部署时内存标记会失效 |
| 向量存储 | MySQL JSON + 应用层余弦 | 分块规模 <5000，暴力算足够；不引第三套基础设施 |
| Embedding | 本地 BGE-small-zh | DeepSeek 无 embedding 接口；本地零成本、数据不出机 |
| 流式输出 | Spring MVC + Flux<ServerSentEvent> | 项目是 MVC 栈，不为了流式引入 WebFlux 应用 |

### 如果继续做

1. **上下文压缩**：现在只按条数裁剪，长对话会丢掉早期的关键信息，可以加一层摘要记忆。
2. **推荐质量评估**：目前靠人工看结果，缺少「推荐是否合理」的量化口径。
3. **工具并行调用**：`listAvailableModels` 和 `getModelInputRequirements` 可以并发，省一轮往返。
4. **分块策略**：现在固定 400 字切，可以按语义边界（相邻句向量相似度突变点）切。
5. **工具调用可观测性**：成功率、首 token 延迟、token 消耗还没进 Prometheus。
