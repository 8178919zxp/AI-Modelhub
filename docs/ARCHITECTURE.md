# 后端架构与实现说明

这份文档讲后端的代码是怎么组织的：分层、调用链、事务、异常、缓存、鉴权。README 里讲的是"这个项目是什么"，这里讲"这些类怎么配合"。

版本对应 `pom.xml`：Spring Boot 3.5.16 / JDK 17 / MyBatis 3.0.5 / Spring AI 1.1.8。

---

## 1. 分层

```
Controller  ──  收参数、校验入参、调用 Service、包装 Result
    │          不写业务判断，不直接碰 Mapper
Service     ──  业务规则、事务边界、缓存注解、调用外部服务
    │
Mapper      ──  一个接口 + 一个 XML，只做 SQL
    │
MySQL / Redis / MinIO / FastAPI / DeepSeek
```

包与类的对应关系：

| 包 | Controller | 主要 Service | 职责 |
|---|---|---|---|
| `model` | `ModelController` | `ModelService` | 在线模型列表、详情、后台 CRUD、上下架 |
| `predict` | `PredictController` | `ModelInferenceService` / `ModelGatewayService` / `PredictRecordService` | 推理编排、下游分派、结果落库 |
| `auth` | `AuthController` | `UserService` | 登录、注册、找回密码、登出 |
| `user` | `UserController` | `UserService` | 资料查看、历史记录、收藏 |
| `admin` | `AdminModelController` / `AdminUserController` | 复用 `ModelService` / `UserService` | 后台管理，靠 `@AuditLog` 记审计 |
| `dashboard` | `DashboardController` | `DashboardService` | 统计概览 |
| `agent` | `AgentController` / `AgentKnowledgeController` | 9 个 Service | 对话、推荐、推理任务、知识库 |
| `security` | — | `JwtUtil` / `JwtAuthFilter` / `TokenBlacklistService` | 令牌签发、验签、吊销 |
| `ratelimit` | — | `SlidingWindowRateLimiter`（Redis ZSET 滑动窗口，被 `ModelInferenceService` 显式调用） | 限流 |
| `cache` | — | `CacheManagementService` / `CacheMetricsService` | 缓存维护与未命中统计 |

Controller 都很薄。`ModelController.list()` 只有一行有效代码：调 `modelService.getOnlineModels()` 然后包 `Result.ok(...)`。**没有分页、没有筛选参数** —— 接口返回全部 `status = 1` 的模型，分类筛选和按准确率排序都在前端 `ModelCenter.tsx` 里做。这么做是因为缓存键只有一个 `models::online`，按查询条件拼键的话命中率会掉到接近 0，而模型总量只有十几条，一次全量返回比多次带参查询更划算。

---

## 2. 一条读请求：模型列表

```
GET /api/models
  │
  ├─ SecurityConfig：GET /api/models/** 是 permitAll，匿名也能看
  │
  ├─ ModelController.list()
  │
  ├─ ModelService.getOnlineModels()
  │     @Cacheable(value = "models", key = "'online'")
  │     ├─ 命中 → 直接返回 List<ModelInfo>
  │     └─ 未命中 → ModelInfoMapper.findByStatus(1) → 写缓存（TTL 5 分钟）
  │
  └─ Result.ok(list) → 前端按分类筛选、按准确率排序
```

两个细节：

- 缓存键是固定的 `models::online`，不带查询条件。所以筛选只能放在返回之后做。
- `CacheMissAspect` 在 `@Cacheable` 方法体真正执行时记一次未命中，指标走 Micrometer；dev 环境可以在 `/actuator/metrics` 看到。

写操作（新增 / 更新 / 上下架 / 删除）都标了 `@CacheEvict`，一次清两个键：`models::online` 和 `model_detail::{id}`。上下架和删除是同一个方法上的两个 `@CacheEvict`（`@Caching` 组合），少清一个就会出现"后台改了状态、前台还是旧的"。

---

## 3. 推理链路的编排

`PredictController` 有四个入口（`/text` `/tabular` `/image` `/custom`），但真正的编排只有一份，在 `ModelInferenceService`。

```
PredictController                     ← 只做参数绑定、取 userId、包 Result
  └─ modelInferenceService.predictImage(userId, modelId, file)
        │
        ├─ checkRateLimit(userId)                 Redis 滑动窗口 20 次/60 秒
        ├─ requireOnlineModel(modelId)            模型存在且 status=1
        ├─ requireModelKey(model)                 model_api 非空
        ├─ 图片：FileStorageService.upload(file, "predictions") → MinIO
        │        失败只记 warn，fileUrl=null，不抛异常
        ├─ ModelGatewayService → FastAPI
        ├─ PredictResultMapper.fromMap(...)       结果 + 耗时
        └─ PredictRecordService.saveRecord(...)   @Transactional 写库
```

为什么把编排抽到 `ModelInferenceService`：网页端和 Agent 都要跑推理。如果逻辑留在 Controller 里，Agent 那边就得复制一遍校验和落库，两边迟早跑偏。抽出来之后，`AgentInferenceService` 直接调同一个方法，走同一套限流之外的校验和同一张 `predict_record` 表。

`ModelGatewayService` 是接口，`FastApiModelGatewayServiceImpl` 是唯一实现，内部用 `RestTemplate`。分派规则在实现里，不在 Controller 里：

| 方法 | `model_api` | FastAPI 路径 |
|---|---|---|
| `predictText` | `bert` | `/text/classify` |
| `predictText` | 其它文本模型 | `/predict/custom` |
| `predictTabular` | `xgboost` | `/tabular/predict` |
| `predictTabular` | 其它表格模型 | `/predict/custom` |
| `predictImage` | 全部图像模型 | `/predict/image`（multipart） |
| `predictCustom` | 天气 / 股票 / 中文情感 | `/predict/custom`（JSON） |

---

## 4. MyBatis：接口和 XML 怎么对上

10 个 Mapper 接口配 10 个 XML，放在 `src/main/resources/mapper/`。对应关系靠两处：

**命名空间**：XML 根节点的 `namespace` 必须是接口全限定名。

```xml
<mapper namespace="com.aimodelhub.model.mapper.ModelInfoMapper">
```

**方法名**：`<select id="findByStatus">` 对应接口里的 `List<ModelInfo> findByStatus(int status)`。方法名对不上，启动时不会报错，调用时才抛 `BindingException`。

字段映射用了两种方式混搭：

- 大部分查询用 `resultMap` 显式映射（`model_name` → `modelName` 等）。开了 `map-underscore-to-camel-case` 其实可以不写，但显式写出来在大表上更好查。
- 插入用 `useGeneratedKeys="true" keyProperty="id"`，插入后主键直接回填到实体，省一次查询。

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id">
```

有一处不得不写动态 SQL 的地方：`AgentKnowledgeMapper` 的全文检索。`MATCH ... AGAINST` 在 MySQL 里不能用参数占位符传整段 SQL，只能传查询词；而排序又要按相关度，所以那条 `<select>` 里带了 `ORDER BY MATCH(content) AGAINST(#{q} IN NATURAL LANGUAGE MODE) DESC`，靠 ngram 解析器支持中文。

需要按条件拼 SQL 的地方都用了 `<if>`，没有用 XML 里现成的 `<where>` —— 因为条件个数少，手写 `WHERE 1=1` 之外的写法反而更容易看懂。

---

## 5. 事务

`@Transactional` 出现在 4 个 Service 的写方法上：

| 类 | 方法 | 说明 |
|---|---|---|
| `ModelService` | `createModel` / `updateModel` / `updateModelStatus` / `deleteModel` | 单表写，事务主要是为了和 `@CacheEvict` 的顺序对齐 |
| `UserService` | `register` / `resetPassword` | `resetPassword` 要同时改 `sys_user` 和写吊销标记 |
| `PredictRecordService` | `saveRecord` | 推理结果的唯一写入点 |
| `AgentSessionService` / `AgentKnowledgeService` | 会话计数、文档入库与分块 | 多表写（`agent_session` + `agent_message`、`document` + `chunk`） |

两个不那么直观的点：

**缓存清理要放在提交之后。** `saveRecord` 没有用 `@CacheEvict`，而是手动注册同步回调：

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override public void afterCommit() { cacheManagementService.evictUserRecordsCache(userId); }
});
```

原因写在代码注释里：`@CacheEvict` 默认在方法返回时执行，此时事务可能还没提交。并发场景下另一个请求读到的是提交前的旧列表，又把它写回缓存，脏数据就留下了。

**同类内部调用不走代理。** `DataInitializer` 里几个修复方法互相调用，`@Transactional` 在自调用上不生效，所以那里改用 `TransactionTemplate` 显式开事务。同理，Service 内部方法之间调用时不要指望注解生效。

---

## 6. 异常

业务代码只抛 4 个自定义异常，全部继承 `RuntimeException`：

| 异常 | HTTP | 用在哪 |
|---|---|---|
| `BusinessException` | 400 | 参数不合法、模型未上线、文件超限、余额/配额类错误 |
| `UnauthorizedException` | 401 | 未登录、令牌无效（`CurrentUser.requireId()`） |
| `NotFoundException` | 404 | 资源不存在、或不属于当前用户（刻意不返回 403，避免被探测） |
| `RateLimitException` | 429 | 限流触发 |

`GlobalExceptionHandler`（`@RestControllerAdvice`）把业务异常、Spring 异常和上游调用异常统一转成 `{code, message, data}`：

| 异常 | 状态码 |
|---|---|
| `MethodArgumentNotValidException` / `MissingServletRequestParameterException` / `MissingServletRequestPartException` / `MethodArgumentTypeMismatchException` / `HttpMessageNotReadableException` / `IllegalArgumentException` | 400 |
| `BusinessException` | 400 |
| `BadCredentialsException` / `UnauthorizedException` | 401 |
| `AccessDeniedException`（`CurrentUser.requireAdmin` 抛的） | 403 |
| `NotFoundException` | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `HttpMediaTypeNotSupportedException` | 415 |
| `MaxUploadSizeExceededException` | 413 |
| `RateLimitException` | 429 |
| `RestClientResponseException`（下游返回 4xx/5xx）/ `RestClientException`（连不上） | 502 |
| `RuntimeException` / `Exception` 兜底 | 500，日志打堆栈，响应只说"系统内部错误" |

上游异常单独映射成 502 而不是 500，是为了区分"我自己的 bug"和"模型服务挂了"——看到 502 直接去查 model-service，不用翻后端日志。

**SSE 是例外**：`/api/agent/chat` 一旦开始推流，响应头已经发出去了，改不了状态码，异常只能作为 `event:error` 下发。所以 Agent 的错误文案不走 `GlobalExceptionHandler`，而是由 `AgentChatService#toUserMessage()` 按异常类型翻译（映射表见 `docs/AGENT_ARCHITECTURE.md` §7.1）。兜底分支刻意不拼接原始 `message`，避免把 URL、SQL、密钥片段写进聊天窗口。

`FileProxyController` 是个特例：它不返回 `Result`，而是直接 `response.sendError(404)` 或写字节流，因为要输出图片二进制。`sendError` 会触发一次 ERROR 派发到 `/error`，所以 `SecurityConfig` 必须放行 ERROR 派发——否则图片 404 会变成 401（浏览器侧表现就是"登录过期"）。

---

## 7. 鉴权

`JwtAuthFilter` 继承 `OncePerRequestFilter`，只做三件事：

```
读 Authorization: Bearer <token>
  → JwtUtil.validateToken()      验签 + 过期（HS256，密钥来自 app.jwt.secret）
  → 查吊销标记
       jti 在 jwt:blacklist:{jti}                    → 视为无效（登出）
       签发时间早于 jwt:user-revoked-before:{userId} → 视为无效（重置密码）
  → 有效则 SecurityContext 里放 UsernamePasswordAuthenticationToken
       principal   = Long userId        ← 全项目统一约定
       authorities = ROLE_ADMIN / ROLE_USER
```

**principal 是 `Long`，不是 `UserDetails`。** 所有业务代码通过 `CurrentUser.requireId()` 或 `auth.getPrincipal() instanceof Long` 取当前用户。这个约定不能改，改了要同步改十几处。

**为什么用 `jti` 而不是删令牌。** JWT 本身无状态，登出只能在服务端记一笔。记 `jti` 能做到"只吊销这一个令牌"；而**重置密码**（走密保找回，或管理员后台重置）需要吊销该用户此前所有令牌，所以另存一个时间戳 `jwt:user-revoked-before:{userId}`，签发时间早于它的令牌一律无效。项目里没有独立的「修改密码」接口，改口令只有这两条路径。

**Redis 不可用时降级。** `TokenBlacklistService` 捕获 Redis 异常，记 warn 后放行。这是可用性优先的取舍：故障期间无法吊销令牌，但整个平台不会因此登不上。

`SecurityConfig` 的规则（按顺序匹配，先命中先生效）：

```java
ASYNC / ERROR 派发                  → permitAll
OPTIONS /**                          → permitAll    // CORS 预检
/actuator/**                         → denyAll      // 健康检查走 /api/health
/v3/api-docs/**, /docs, /swagger-ui  → 由 springdoc.api-docs.enabled 决定
/api/auth/{login,register,forgot-password/*} → permitAll
/api/health, /api/health/**          → permitAll
GET /api/models, /api/models/**      → permitAll
/api/dashboard/overview              → permitAll
GET /api/files/**                    → permitAll
/api/predict/**, /api/agent/**, /api/users/** → authenticated
/api/admin/**                        → hasRole("ADMIN")
其余                                  → authenticated
```

`ASYNC` 必须放行，否则 SSE 结束时会出错：`Flux<ServerSentEvent>` 在 Tomcat 上结束时会再走一次异步派发，那个线程里没有 `SecurityContext`，过滤器链二次鉴权抛 `AccessDeniedException`，现象是"内容都推完了，但连接异常中断或返回 500"。放行不降低安全性——能走到 ASYNC 派发，说明首次请求已经过鉴权。

Agent 的知识库写入额外在方法里调 `CurrentUser.requireAdmin()`。原因是投毒一篇文档会影响所有用户的对话，不能只靠 URL 规则——将来路由调整时容易漏。

**CORS 只对「真正的跨域」生效。** 前端由 Nginx 与后端同源暴露时（`POST /api/...` 的 Origin 与请求自身一致），Spring 直接跳过 CORS 校验；只有前端独立端口或独立域名访问后端时，`CorsConfig` 的白名单才会参与判断。名单通过 `app.cors.allowed-origins`（逗号分隔）配置，可用 `APP_CORS_ALLOWED_ORIGINS` 覆盖，**不接受 `*`**——`allowCredentials=true` 时通配符本身就会被浏览器拒绝。

踩过的坑：Nginx 反代时若写 `proxy_set_header Host $host`，`$host` 会丢掉端口，后端看到的「自身地址」就没有端口，于是把本机非同端口的请求误判成跨域，前端表现为「列表能打开、一登录就 `403 Invalid CORS request`」。要保留端口得用 `$http_host`。

---

## 8. 缓存

四个缓存名，TTL 在 `CacheConfig` 里按名字单独配（不是全局一个 TTL）：

| 缓存名 | 键 | TTL | 谁写 | 谁清 |
|---|---|---|---|---|
| `models` | `'online'` | 5 min | `ModelService.getOnlineModels` | 模型增删改上下架 |
| `model_detail` | `#id` | 10 min | `ModelService.getModelById` | 同一模型的写操作 |
| `dashboard_overview` | `'overview'` | 2 min | `DashboardService` | 模型写方法里手动调 `evictDashboardCache()`；预测不主动清，最多滞后 2 分钟 |
| `user_records` | `#userId` | 1 min | `PredictRecordService.getUserRecords` | `saveRecord` 提交后 |

`dashboard_overview` 没用 `@CacheEvict`，而是在 `ModelService` 的四个写方法里显式调 `cacheManagementService.evictDashboardCache()`。原因是统计概览的键和模型缓存的键不在一个 `@Caching` 组合里方便表达，显式调用更直白。

序列化用 `GenericJackson2JsonRedisSerializer` + 一个专门的 `ObjectMapper`（带 `JavaTimeModule`，否则 `LocalDateTime` 系列化会炸）。key 用 `StringRedisSerializer`，前缀 `aimh:dev:` / `aimh:docker:` / `aimh:prod:` 按 profile 区分。

`CacheErrorHandler` 把 Redis 异常吞掉并降级为直查数据库。这意味着 Redis 挂掉时业务不中断，但也意味着缓存"看起来在工作"其实没生效——所以限流和黑名单要单独考虑降级行为。

非 Spring Cache 的键（这些不走缓存抽象）：

| 键 | 用途 | TTL |
|---|---|---|
| `rate:limit:{业务}:user:{id}` / `:ip:{ip}` | 滑动窗口限流（ZSET + Lua） | 窗口长度 |
| `jwt:blacklist:{jti}` | 登出吊销 | 令牌剩余有效期 |
| `jwt:user-revoked-before:{userId}` | 重置密码后吊销旧令牌 | 长期 |
| `pwd:reset:fail:{username}` | 密保错误计数 | 10 分钟 |
| `agent:memory:{sessionId}` | Agent 上下文窗口 | 2 小时 |
| `agent:quota:{userId}:{date}` | 每日大模型调用配额 | 跨天 |

---

## 9. 限流与审计

**限流不走注解。** 配额在 `ModelInferenceService.checkRateLimit(userId)` 里显式调用 `SlidingWindowRateLimiter`，没有任何注解式切面。原因：Agent 的推理工具直接调 `ModelInferenceService`，不经过 AOP 代理，如果限流挂在 Controller 的切面上，Agent 那条路径就绕过了配额，等于开了个后门。写在 Service 里之后，两条路径共用 `predict:user:{userId}` 这同一个键。

（早先版本有一个 `@RateLimit` 注解 + `RateLimitAspect` 切面，但从未挂到任何方法上——第四阶段确认无引用后已删除，`ratelimit` 包现在只有 `SlidingWindowRateLimiter`。）

额度：20 次 / 60 秒。超限抛 `RateLimitException` → 429。方法开头有 `if (userId == null) return;`，不过 `/api/predict/**` 本来就要求登录，匿名到不了这里。

`SlidingWindowRateLimiter` 用 ZSET 存时间戳，一段 Lua 脚本完成"清过期成员 + 统计 + 超限判断 + 写入"，保证并发下不会数错。Redis 不可用时它 fail-open（放行），和缓存的降级策略一致。

**审计**用的是 `@AuditLog(action = "RESET_USER_PASSWORD", detail = "...")` + `AuditAspect` 环绕通知。记录操作人、动作、参数摘要、成功/失败、耗时，写到独立的 `audit` appender（`backend/logs/audit.log`），不混进应用日志。参数里有密码时不记明文——`AdminUserController` 重置密码时只记"重置了某人的密码"。

---

## 10. 前端怎么配合

前端只有一层薄封装：`src/api/request.ts` 的 `request()` 负责拼基址、加 `Bearer` 令牌、12 秒超时、统一解析 `{code, message, data}`（`code >= 400` 也当失败处理，因为后端业务失败会返回 HTTP 200 + 非 200 的 code）。各业务 `api/*.ts` 只声明路径和类型。

令牌存在 `localStorage` 的 `ai-modelhub` 键下（Zustand 的 persist），`getToken()` 从 `state.token` 取。

SSE 不走 `request()`，因为要流式读：`api/agent.ts` 里用 `fetch` + `response.body.getReader()` 手工解析 `event:` / `data:` 行。`EventSource` 用不了——它不能带 `Authorization` 头。

---

## 11. 数据表

4 张业务表 + 7 张 Agent 表，结构和索引见 [`DATABASE.md`](DATABASE.md)。这里只记一条设计约定：**所有表都不建外键**。删除模型走逻辑删除，历史预测记录里的 `model_id` 保留为弱引用；一致性由 Service 保证，不靠数据库。
