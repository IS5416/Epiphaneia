# 2026-07-29-01: LLM RestClient 重构 + 启动 Bug 修复

## 背景

Docker 启动应用后，前端预配置 DeepSeek 模型连接测试失败，后续 Prometheus 数据源配置、前端 Workspace 渲染均出现问题。本轮共修复 5 个 Bug。

## 改动清单

### 1. LLM 客户端重构：OpenAI SDK 4.x → RestClient 直调

**文件：** `product/epiphaneia-llm/src/main/java/io/epiphaneia/llm/internal/client/`

**问题：** Spring AI 2.0.0 底层使用 OpenAI Java SDK 4.39.1 的 `OpenAiSetup.setupSyncClient()` 构建客户端。该 SDK 的 `ClientOptions.Builder.build()` 要求三选一凭据源：`credential` / `adminApiKey` / `workloadIdentity`，且在不同端点类型（Azure vs 标准 OpenAI）间行为不一致。`BearerTokenCredential`、`AzureApiKeyCredential`、`adminApiKey` 均无法稳定通过校验。问题不在部署缓存，SDK 的 Kotlin `effectiveCredential()` 校验逻辑在 build 时和 API 调用时行为分离。

**修复：** 
- 新建 `OpenAiCompatibleChatModel.java`：实现 Spring AI `ChatModel` 接口，仅使用 Spring Boot 内置 `RestClient` 直调 `POST {baseUrl}/v1/chat/completions`
- 重写 `LlmClient.java` 的 `buildClient()`：移除全部 OpenAI SDK 4.x 依赖（`OpenAiSetup`、`ClientOptions`、`BearerTokenCredential`、`SpringAiOpenAiHttpClient`、`OpenAiChatModel`），改为 `new OpenAiCompatibleChatModel(baseUrl, apiKey, modelName)`
- 代码量：`LlmClient.buildClient()` 从 ~40 行减至 ~8 行

**理由：** OpenAI SDK 4.x 的凭据校验在不同提供商端点间行为不可预测，继续调试投入产出比太低。DeepSeek API 是标准 OpenAI 兼容协议，`RestClient` 直调即可，零额外依赖。泛用所有 OpenAI 兼容 API（OpenAI / DeepSeek / Ollama / Groq / Anthropic 兼容端点）。

### 2. JSONB 字段 Hibernate 6 类型映射

**文件：** `product/epiphaneia-domain/src/main/java/io/epiphaneia/domain/internal/entity/`

**问题：** PostgreSQL `JSONB` 列在 Hibernate 6（Spring Boot 4.1）中仅设 `@Column(columnDefinition = "jsonb")` 不足以在运行时绑定正确的 JDBC 类型。Hibernate 默认将 Java `String` 映射为 `VARCHAR`，PostgreSQL 拒绝 `character varying` 写入 `jsonb` 列。

**修复：** 5 个 JSONB 字段添加 `@JdbcTypeCode(SqlTypes.JSON)` 注解：
- `DataSource.authConfig`、`DataSource.metadata`
- `Application.tags`、`Application.actuatorInfo`
- `RootCauseHypothesis.supportingEvidenceIds`

### 3. DataSource authType NOT NULL 约束

**文件：** `product/epiphaneia-server/src/main/java/io/epiphaneia/server/dto/DataSourceRequest.java`

**问题：** 前端创建 DataSource 时不传 `authType`，Java record 组件为 `null`，MapStruct 映射覆盖实体默认值 `"NONE"`，导致 INSERT 违反 `auth_type NOT NULL` 约束。

**修复：** 添加 compact constructor：`if (authType == null || authType.isBlank()) authType = "NONE";`

### 4. 会话 Cookie Secure 标志在 HTTP 环境无效

**文件：** `product/docker/docker-compose.yml`

**问题：** `SPRING_PROFILES_ACTIVE` 默认值 `prod` 启用 `application-prod.yml` 中的 `cookie.secure: true`。本地 HTTP 环境下浏览器拒绝发送 Secure cookie → 每次请求丢失会话 → `/me` 返回 401 → `ProtectedRoute` 重定向 → 无限循环 → React 白屏。

**修复：** 将 docker-compose 默认值从 `prod` 改为空：`${SPRING_PROFILES_ACTIVE:-}`

### 5. API 列表端点响应格式不一致

**文件：** `product/epiphaneia-server/src/main/java/io/epiphaneia/server/controller/ApplicationController.java`、`ConversationController.java`

**问题：** `list()` 方法直接返回 `ApiListResponse<T>`，未包装在 `ApiResponse<T>` 中。前端 `request()` 函数自动解包一层 `ApiResponse.data`，导致拿到原始数组而非 `{data: [...], total: N}` 对象 → `appResult.data` = `undefined` → `.map()` 崩溃 → `Uncaught TypeError: Cannot read properties of undefined (reading 'map')`。

**修复：** 两个 list 端点返回类型改为 `ApiResponse<ApiListResponse<T>>`，`return ApiResponse.ok(ApiListResponse.of(result))`

## 技术决策

- **ADR-007: RestClient 直调替代 OpenAI SDK 4.x** — 对于 OpenAI 兼容 API，Spring Boot 内置 `RestClient` 足够。避免第三方 SDK 版本漂移风险。保留 `spring-ai-starter-model-openai` 依赖用于自动配置 fallback 路径（`ChatClient.Builder` 从环境变量注入）。
- **ADR-008: Hibernate 6 JSONB 映射规范** — 所有 JSONB/JSON 列必须同时标注 `@Column(columnDefinition = "jsonb")` 和 `@JdbcTypeCode(SqlTypes.JSON)`。前者用于 DDL 生成，后者用于运行时 JDBC 类型绑定。
