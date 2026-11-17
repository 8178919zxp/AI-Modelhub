# 安全设计说明

> 本文说明本项目的凭据管理方式、鉴权与限流机制，以及对外部署前必须确认的检查项。
> 文中不包含任何真实口令或密钥——仓库里也不应该有。

---

## 1. 凭据管理原则

**仓库只保存模板，不保存值。**

| 文件 | 是否入库 | 说明 |
|---|---|---|
| `application-example.yml` | ✅ 入库 | 全部敏感项写成 `${ENV:占位符}`，无真实值 |
| `application-dev.yml` | ✅ 入库 | 本机 dev profile：Redis 地址与日志级别，无凭据 |
| `application-docker.yml` | ✅ 入库 | 容器 profile：主机名用 compose 服务名，口令无默认值 |
| `application-prod.yml` | ✅ 入库 | 生产 profile：示例密钥/弱口令会被拒绝启动 |
| `application.yml` | ❌ 不入库 | 由模板复制而来，存放本机真实口令 |
| `deploy/.env` | ❌ 不入库 | Docker Compose 的变量文件 |
| `frontend/.env.*` | ❌ 不入库 | 只有 `.env.example` 入库 |

`.gitignore` 里的规则是「先整体忽略 `application*.yml`，再用 `!` 放行上面 4 个无凭据文件」，
新增 profile 文件时如果忘了加 `!` 放行，它不会被提交——这是有意的默认行为。

### 启动期强校验

`ProductionSafetyValidator`（`com.aimodelhub.config`）在 Bean 初始化阶段检查：

| 检查项 | dev / docker | prod |
|---|---|---|
| JWT 密钥是否为仓库示例值 | 告警，继续启动 | **抛异常，拒绝启动** |
| JWT 密钥是否为空 | 告警 | **拒绝启动** |
| 数据库口令是否为空 / 弱口令 | 告警 | **拒绝启动** |
| MinIO secret 是否为空 / 弱口令 | 告警 | **拒绝启动** |

弱口令清单包含 `123456`、`12345678`、`password`、`admin`、`root` 等常见值。
这样做的原因是：仓库里必然带着演示口令，一旦这套配置被直接搬上生产，
任何读过仓库的人都能伪造管理员令牌。宁可启动失败，也不要静默地带着已知密钥运行。

---

## 2. 鉴权

- **令牌格式**：JWT（HS256），签名密钥来自 `app.jwt.secret`，Base64 解码后必须 ≥ 32 字节。
- **principal 是 `Long userId`**，不是 `UserDetails`。
  业务代码统一通过 `CurrentUser.requireId()` 取当前用户，**不接受前端传 `userId`**。
- **吊销机制**：
  - 登出：`jti` 写入 `jwt:blacklist:{jti}`，TTL 等于令牌剩余有效期；
  - 重置密码（密保找回 / 管理员后台重置）：写入 `jwt:user-revoked-before:{userId}`，早于该时间点签发的令牌全部失效。
  - Redis 不可用时 `TokenBlacklistService` 内部降级（放行 + 告警），保证登录链路不被缓存故障阻断。
    这是一个**有意识的取舍**：可用性优先，代价是故障期间无法吊销令牌。

### 路径权限（`SecurityConfig`）

| 规则 | 路径 |
|---|---|
| 公开 | `/api/auth/login`、`/register`、`/forgot-password/**`、`/api/health/**`、`GET /api/models/**`、`/api/dashboard/overview`、`GET /api/files/**` |
| 需登录 | `/api/predict/**`、`/api/agent/**`、`/api/users/**` |
| 需 ADMIN | `/api/admin/**` |
| 显式拒绝 | `/actuator/**`（健康检查请用 `/api/health`） |
| 文档 | `/docs`、`/v3/api-docs/**` 仅在 `springdoc` 开启时可用（prod 关闭） |

---

## 3. 限流

`SlidingWindowRateLimiter`（Redis ZSET + Lua 原子执行，滑动窗口），由 `ModelInferenceService.checkRateLimit()` 显式调用——**没有注解式切面**。

- 维度：按用户，key 为 `predict:user:{userId}`
- 配额：20 次 / 60 秒，网页推理与 Agent 推理共用同一份额度
- 触发后抛 `RateLimitException` → HTTP 429，前端只打 warning 日志，不弹错误堆栈
- 匿名请求没有配额：`/api/predict/**` 与 `/api/agent/**` 都要求认证，未登录在过滤器链就被拦了

**为什么不用 AOP 切面**：Agent 的推理工具直接调用 `ModelInferenceService`，不经过代理，注解对它不生效；挂在 Controller 上的限流会让 Agent 成为绕过配额的旁路。

**Redis 不可用时限流 fail-open（放行）**——这是已知的可用性取舍，生产环境应保证 Redis 可用，或在网关层再加一层限流。

---

## 4. 上传与文件

- 单文件上限 **5MB**，相关配置要一起改：`spring.servlet.multipart.max-file-size`（5MB）、
  `max-request-size`（6MB）、`FileStorageService.MAX_FILE_SIZE`（5MB）、推理服务 `_MAX_UPLOAD_MB`（5）、
  nginx `client_max_body_size`（6M）。只改一处，会出现「后端收下了但推理服务拒绝」或「Nginx 先拦掉」。
- 上传前校验 MIME 类型白名单，不接受任意扩展名。
- 图片存 MinIO，数据库只存 object key；前端通过 `GET /api/files/**` 由后端代理读取，
  **不把 MinIO 地址暴露给浏览器**（避免 CORS 与桶策略问题）。
- Agent 附件存后端本地相对路径（`agent_attachment.storage_key`），库中不存二进制。

---

## 5. 生产部署检查清单

1. `JWT_SECRET` 用 `openssl rand -base64 32` 重新生成，**不要复用任何开发用值**。
2. `MYSQL_ROOT_PASSWORD`、`MINIO_ROOT_PASSWORD` 改为强口令，且两者不同。
3. `SPRING_PROFILES_ACTIVE=prod`，确认 `/docs` 与 `/actuator/**` 不可访问。
4. `app.cors.allowed-origins` 改为真实域名，不要保留 `localhost`（推荐用环境变量
   `APP_CORS_ALLOWED_ORIGINS` 注入，逗号分隔）。**不要写 `*`**：与 `allowCredentials=true`
   冲突，浏览器和 Spring 都会拒绝。
5. `APP_BOOTSTRAP_DEFAULT_USER_PASSWORD` 覆盖默认演示口令，或用「找回密码」把演示账号的口令换掉。
6. 若曾经把 `application.yml` / `.env` 推到过远端，**视为已泄露**：轮换数据库口令、
   MinIO 密钥与 JWT 密钥（JWT 轮换会让所有已签发令牌失效，属预期行为）。
7. 反向代理层补 `X-Forwarded-*` 透传，否则限流会把所有请求算到同一个 IP 上。
