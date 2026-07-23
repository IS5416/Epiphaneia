# Epiphaneia 全项目综合代码审查报告

**审查日期**: 2026-07-23 | **项目版本**: 0.9.0-SNAPSHOT
**审查范围**: 全项目 166 源文件，6 维度并行审查
**审查方式**: 6 个独立子代理并行审查 + 主代理综合提炼

---

## 审查维度与代理

| 维度 | 代理类型 | 审查文件数 |
|------|---------|-----------|
| 架构与模块设计 | Code Reviewer | 15 (pom.xml + 核心接口) |
| 后端基础层 (infra+connector+engine) | Code Reviewer | 30+ |
| 后端业务层 (agent-core+server) | Code Reviewer | 70+ |
| 前端代码 | Code Reviewer | 17 |
| 安全 | Security Architect | 25+ |
| 部署与运维 | DevOps Automator | 20+ |

---

## 综合评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | C+ | 模块分层方向正确，但接口弱类型化、包边界多处违规、agent-core臃肿 |
| 后端代码质量 | B- | 核心逻辑测试充分，但多处占位实现、事务边界缺失、LLM集成断裂 |
| 前端代码质量 | B | 现代化技术栈，但缺认证守卫、响应式、ESLint、测试 |
| 安全性 | C | 加密实现正确，但CSRF关闭、API Key硬编码、会话固定、OWASP跳过 |
| 部署与运维 | D+ | Dockerfile/nginx.conf 存在构建阻断问题，缺 CI/CD、备份、资源限制 |
| 测试覆盖 | C+ | agent-core 核心测试良好，server 模块仅 1 个测试，controller/security 零覆盖 |

**综合评分: C+**

---

## BLOCKER (15项) — 必须立即修复

### B1. Dockerfile 构建失败：mvnw 在 COPY 前被引用

- **文件**: `product/docker/Dockerfile:9`
- **来源**: Deployment Agent
- **描述**: Build stage 第 9 行执行 `RUN ./mvnw dependency:go-offline`，但 mvnw 和 .mvn/ 目录在第 10 行 `COPY . .` 才出现。Docker 构建必然报错 `./mvnw: not found`。
- **修复**: 在 RUN 前增加 `COPY mvnw ./` 和 `COPY .mvn .mvn/`。

### B2. nginx.conf 语法错误导致容器无法启动

- **文件**: `product/docker/nginx/nginx.conf:1`
- **来源**: Deployment Agent
- **描述**: nginx.conf 缺少顶层 `http {}` 和 `events {}` 块，直接以 `upstream` 开头。nginx 启动时报 `"upstream" directive is not allowed here`。
- **修复**: 补全 http + events 块，或将文件复制到 `/etc/nginx/conf.d/default.conf` 利用 nginx 镜像默认主配置。

### B3. LlmClient 完全忽略 Provider 配置 — 多模型切换失效

- **文件**: `product/epiphaneia-agent-core/src/main/java/io/epiphaneia/agent/internal/llm/LlmClient.java:74-78`
- **来源**: 主代理 + Architecture Agent + Backend B Agent
- **描述**: `buildClient(LlmProvider provider)` 始终返回 `chatClientBuilder.build()`，忽略 provider 的 modelName、API key（解密后）、baseUrl。用户配置不同 LLM 提供商实际不生效，所有调用共用 Spring AI 默认配置。
- **修复**: 在 AiConfig 中为每个 provider 预注册具名 ChatClient Bean，或在 buildClient 中动态配置 ChatClient 的 API key/model/baseUrl。

### B4. PrometheusConnector / ElasticsearchConnector 的 query() 为空壳

- **文件**: `product/epiphaneia-connector/src/main/java/io/epiphaneia/connector/internal/prometheus/PrometheusConnector.java:38-39`、`ElasticsearchConnector.java:37-38`
- **来源**: 主代理 + Backend A Agent
- **描述**: 两个 Connector 的 `query()` 方法均返回 `new QueryResult() {}`（空对象）。PrometheusQueryBuilder 和 EsQueryBuilder 已完整实现但从未被调用。ReAct 诊断循环的 QUERYING 阶段收集不到任何真实数据。
- **修复**: 在 `DiagnosisOrchestratorImpl.queryDataSource()` 中调用 engine 模块的 QueryBuilder 构建查询，将构建的查询传给 Connector.query() 执行。

### B5. DiagnosisOrchestrator 接口形同虚设

- **文件**: `product/epiphaneia-agent-core/src/main/java/io/epiphaneia/agent/api/DiagnosisOrchestrator.java:2-4`
- **来源**: Architecture Agent
- **描述**: 接口定义 `Object execute(Object, String)` — 参数/返回值均为 Object，完全无类型安全。实现类该方法直接抛 `UnsupportedOperationException`。实际使用的方法 `execute(DiagnosisContext, DiagnosisSseEventPublisher)` 不在接口中。所有调用方（DiagnosisSkill）直接依赖实现类，整个接口抽象等于白做。
- **修复**: 将正确的方法签名声明到接口中，删除旧的 execute 方法。

### B6. DiagnosisOrchestratorImpl 事务边界缺失

- **文件**: `product/epiphaneia-agent-core/src/main/java/io/epiphaneia/agent/internal/orchestration/DiagnosisOrchestratorImpl.java:60-71`
- **来源**: Backend B Agent
- **描述**: `@Transactional` 注解在永远抛异常的桩方法 `execute(Object, String)` 上。实际被调用的 `execute(DiagnosisContext, DiagnosisSseEventPublisher)` 无事务注解。方法内多次 `em.merge()` 和 `repo.save()` 在 auto-commit 模式下运行——中间状态可能被持久化，失败时无法回滚。
- **修复**: 将 `@Transactional` 移到 `execute(DiagnosisContext, DiagnosisSseEventPublisher)` 方法上。

### B7. GlobalExceptionHandler 未捕获 @Valid 校验异常

- **文件**: `product/epiphaneia-server/src/main/java/io/epiphaneia/server/exception/GlobalExceptionHandler.java:12-35`
- **来源**: Backend B Agent
- **描述**: 当 `@Valid @RequestBody` 校验失败时 Spring 抛出 `MethodArgumentNotValidException`，但 Handler 只处理了 IllegalArgumentException、IllegalStateException、Exception 三种。校验失败落到通用 Exception handler 返回 HTTP 500，而非正确的 400 + 字段错误详情。
- **修复**: 增加 `@ExceptionHandler(MethodArgumentNotValidException.class)` 处理器。

### B8. DataSourceController/LlmController/ApplicationController 连接测试为假实现

- **文件**: `DataSourceController.java:68-74`、`LlmController.java:53-61`、`ApplicationController.java:68-75`
- **来源**: 主代理 + Backend B Agent
- **描述**: 三个 "Test Connection" 端点全部硬编码返回成功，实际不执行任何连通性测试。用户点击测试按钮获得虚假的"Connection successful"反馈。
- **修复**: DataSource 测试 → 调用 Connector.testConnection()。LLM 测试 → 调用 LlmClient 做一次简单 chat。Application 测试 → 调用 ActuatorProbeService.probe()。

### B9. CSRF 保护被关闭但使用 Session 认证

- **文件**: `product/epiphaneia-server/src/main/java/io/epiphaneia/server/security/SecurityConfig.java:34`
- **来源**: Security Agent
- **描述**: `.csrf(csrf -> csrf.disable())` 全局关闭 CSRF。系统使用 session cookie 认证，所有状态变更操作（修改密码、创建/撤销 token、修改设置）暴露于 CSRF 攻击。
- **修复**: 重新启用 CSRF 保护，前端获取 CSRF token 并在请求中附带；或确认 `SameSite=Strict` + `Secure` 且仅内网部署。

### B10. API Key 硬编码在 application-local.yml

- **文件**: `product/epiphaneia-server/src/main/resources/application-local.yml:16`
- **来源**: Security Agent + Deployment Agent
- **描述**: `api-key: sk-b56d194020a841b7b802f6cc46c63202` 以明文硬编码。此文件虽被 gitignore 排除但存在于磁盘。此 key 应视为已泄露。
- **修复**: 立即在 DeepSeek 平台轮换此 key；改为 `${DEEPSEEK_API_KEY:}` 环境变量引用。

### B11. OWASP Dependency Check 全局跳过

- **文件**: `product/pom.xml:123`
- **来源**: Security Agent + Deployment Agent
- **描述**: `<skip>true</skip>` 使 `mvn verify` 跳过漏洞扫描。Spring AI 2.0、LangChain4j beta 等生态包更新频繁，CVEs 出现速度快。跳过扫描意味着已知漏洞的传递依赖会直接进入生产。
- **修复**: 移除 skip 或在 CI profile 中覆盖为 false。

### B12. 前端路由无认证守卫

- **文件**: `product/epiphaneia-web-ui/src/App.tsx:16-21`
- **来源**: Frontend Agent
- **描述**: Layout 包裹的 workspace/report/history/settings 四个页面无任何认证检查。用户可绕过登录页直接访问，仅在 API 调用返回 401 时才被重定向。
- **修复**: 添加 ProtectedRoute 组件，在 useEffect 中调用 `/api/v1/system/status` 检查登录状态，未认证时立即重定向。

### B13. 前端 SSE 流断线无重连

- **文件**: `product/epiphaneia-web-ui/src/api/sse.ts:10-81`
- **来源**: Frontend Agent
- **描述**: SSE 连接断开后无自动重连，无错误通知。用户在 DiagnosisWorkspace 中看到 SSE 面板停止更新但无任何反馈。
- **修复**: 区分正常关闭和异常断开；异常时通知用户并提供重试按钮；如需自动重连实现指数退避。

### B14. Repository 接口在 api 包中继承 JpaRepository — 违反包边界规则

- **文件**: `product/epiphaneia-agent-core/src/main/java/io/epiphaneia/agent/api/repository/*.java` (9 个文件)
- **来源**: Architecture Agent
- **描述**: 所有 9 个 Repository 接口在 `*.api.repository` 包下直接继承 `org.springframework.data.jpa.repository.JpaRepository`（Spring 框架类）。AGENTS.md 规定 `*.api.*` 包只含接口/POJO 不依赖框架类。
- **修复**: 方案A（推荐）：将 Repository 移到 `*.internal.repository` 包。方案B：api 包定义纯接口，internal 包提供 JpaRepository 实现。

### B15. server 模块显式依赖 connector 模块 + 直接导入 agent-core internal 类

- **文件**: `product/epiphaneia-server/pom.xml` + `DiagnosisSkill.java:6-7`
- **来源**: Architecture Agent
- **描述**: server 同时依赖 agent-core 和 connector（冗余传递路径）；DiagnosisSkill 导入 `DiagnosisOrchestratorImpl`（internal 类）和 `DiagnosisContext`（internal record），绕过接口抽象。
- **修复**: server 仅依赖 agent-core；DiagnosisSkill 依赖 DiagnosisOrchestrator 接口；DiagnosisContext 移到 api 包。

---

## HIGH (20项) — 应尽快修复

### H1. EsQueryBuilder query_string 注入风险 — escapeLucene() 为死代码

- **文件**: `epiphaneia-engine/.../EsQueryBuilder.java:44-46`
- **来源**: Backend A Agent
- **描述**: `buildSearchQuery` 中 queryString 使用 JSON escape 而非 Lucene escape。`escapeLucene()` 方法（22种特殊字符）已实现但从未被调用。
- **修复**: 切换为 `escapeLucene()` 或双重转义。

### H2. PrometheusQueryBuilder label 值未做 PromQL 转义

- **文件**: `epiphaneia-engine/.../PrometheusQueryBuilder.java:29,54,71`
- **来源**: Backend A Agent
- **描述**: label 值直接 `" + value + "` 拼接。若值含 `"` 或 `\` 会生成非法 PromQL。
- **修复**: 实现 label value escaping（`\` → `\\`, `"` → `\"`, `\n` → `\\n`）。

### H3. ActuatorProbeService SSRF 校验存在 DNS rebinding 绕过

- **文件**: `epiphaneia-engine/.../ActuatorProbeService.java:57-84`
- **来源**: Backend A Agent
- **描述**: validateUrl() 在验证阶段解析 DNS 检查 IP，但 safeGet() 实际请求时重新解析——DNS rebinding 攻击可绕过。
- **修复**: 请求前二次校验 IP 或使用自定义 SocketFactory 拦截。

### H4. 会话固定 (Session Fixation) 漏洞

- **文件**: `epiphaneia-server/.../AuthController.java:52`
- **来源**: Security Agent
- **描述**: 登录成功直接对现有 session 设置 ADMIN_ID，未先 invalidate。攻击者可劫持会话。
- **修复**: `session.invalidate()` 后 `request.getSession(true)` 创建新 session。

### H5. Session Cookie 缺少 Secure 标志

- **文件**: `epiphaneia-server/.../application.yml:7-9`
- **来源**: Security Agent
- **描述**: cookie 配置缺少 `secure: true`。生产环境通过 HTTPS 访问时 cookie 可能被明文传输。
- **修复**: 添加 `secure: true`，same-site 改为 `strict`。

### H6. 初始管理员密码明文写入日志

- **文件**: `epiphaneia-server/.../AdminSeeder.java:51`
- **来源**: Security Agent
- **描述**: `log.info("Password: {}", password)` 将初始密码以明文写入日志。日志聚合系统中任何有查询权限的人可见。
- **修复**: 不输出密码到日志，写入临时文件并提示运维人员读取后删除。

### H7. X-Forwarded-For 头可被伪造绕过速率限制

- **文件**: `epiphaneia-server/.../RateLimitFilter.java:63-68`
- **来源**: Security Agent
- **描述**: 直接信任客户端传入的 X-Forwarded-For。若攻击者绕过 nginx 直连后端容器，可任意伪造 IP 完全绕过限速。
- **修复**: 添加受信任代理白名单。

### H8. RateLimitFilter 内存无限增长

- **文件**: `epiphaneia-server/.../RateLimitFilter.java:33-34`
- **来源**: Security Agent + 主代理
- **描述**: ConcurrentHashMap 存储 bucket 永不过期清理。长期运行内存泄漏。
- **修复**: 定期清理过期 IP 条目，或使用 Caffeine 缓存替代。

### H9. docker-compose 所有服务缺少资源限制

- **文件**: `product/docker/docker-compose.yml`
- **来源**: Deployment Agent
- **描述**: 无 mem_limit / cpus。PostgreSQL 极易因无内存限制而 OOM。
- **修复**: 至少为 postgres 添加 `mem_limit: 512m`。

### H10. PostgreSQL 有 volume 但无备份策略

- **文件**: `product/docker/docker-compose.yml:55-56`
- **来源**: Deployment Agent
- **描述**: 数据有持久化但无备份方案。
- **修复**: 至少提供 pg_dump 备份脚本并在 README 中说明。

### H11. 缺少 CI/CD 流水线配置

- **文件**: 全项目
- **来源**: Deployment Agent
- **描述**: 无 .github/workflows/、Jenkinsfile 或任何 CI 配置。所有构建部署依赖手动操作。
- **修复**: 创建最小 GitHub Actions workflow。

### H12. ddl-auto: update + Flyway 禁用导致本地 schema 漂移

- **文件**: `epiphaneia-server/.../application-local.yml:10-13`
- **来源**: Deployment Agent
- **描述**: Local profile 用 Hibernate ddl-auto: update 管理表结构，Flyway 被禁用。本地 schema 可能与生产不一致，部署时迁移失败。
- **修复**: Local 环境启用 Flyway，ddl-auto 改回 validate。

### H13. 生产环境缺少结构化 JSON 日志

- **文件**: `epiphaneia-server/.../application-prod.yml`
- **来源**: Deployment Agent
- **描述**: 所有 profile 均输出 ANSI 带色文本日志。Docker 容器化部署标准是 JSON 格式供日志聚合解析。
- **修复**: 配置 JSON log pattern 或引入 logstash-logback-encoder。

### H14. ConversationController.sendMessage() 虚拟线程无超时控制

- **文件**: `epiphaneia-server/.../ConversationController.java:101-115`
- **来源**: Backend B Agent
- **描述**: `Thread.startVirtualThread()` 无外部超时。LLM 调用阻塞时诊断无限等待。
- **修复**: 使用 Future.get(timeout) 或定期超时检查的守护逻辑。

### H15. DataSource.authConfig 以明文 JSON 存储敏感凭证

- **文件**: `epiphaneia-agent-core/.../DataSource.java:27-28`
- **来源**: Backend B Agent
- **描述**: authConfig (jsonb) 可能包含 Prometheus Basic Auth 密码、ES API key 等，数据库泄露直接暴露。
- **修复**: 对 authConfig 应用与 LlmProvider.apiKeyEncrypted 相同的加密策略。

### H16. Application 级联删除导致诊断数据不可恢复丢失

- **文件**: `epiphaneia-agent-core/.../Application.java:35`
- **来源**: Backend B Agent
- **描述**: CascadeType.ALL + orphanRemoval 删除 Application 时级联删除所有 Conversation/Message/Evidence。无软删除、无二次确认。
- **修复**: 考虑软删除（deleted_at）或至少添加审计日志。

### H17. Message.diagnosisState 缺少索引

- **文件**: `epiphaneia-agent-core/.../Message.java:27-28`
- **来源**: Backend B Agent
- **描述**: diagnosisState 无索引注解。`findActiveDiagnoses()` / `findStaleDiagnoses()` 累积大量消息后全表扫描。
- **修复**: 添加 `@Index(name = "idx_message_diagnosis_state")` 及复合索引 `(diagnosis_state, created_at)`。

### H18. ConversationController.replayEvents() 立即关闭 emitter

- **文件**: `epiphaneia-server/.../ConversationController.java:121-127`
- **来源**: Backend B Agent
- **描述**: 创建 emitter 后立即关闭，客户端收到空连接。虽标记为 "simplified replay" 但行为与 API 契约不符。
- **修复**: 返回 501 Not Implemented 或发送一条说明事件后再关闭。

### H19. 前端接口请求无超时控制

- **文件**: `epiphaneia-web-ui/src/api/client.ts:24-65`
- **来源**: Frontend Agent
- **描述**: fetch 无超时设置。后端无响应时用户看到永久 loading spinner。
- **修复**: 使用 AbortController + setTimeout 实现 30s 超时。

### H20. agent-core 模块过于臃肿

- **文件**: `epiphaneia-agent-core/` (34 个 Java 文件)
- **来源**: Architecture Agent
- **描述**: 一个模块承担领域模型 + 持久化 + 编排 + LLM 集成四个职责。任一职责变更触发全模块重编译。
- **修复** (中远期): 拆分为 epiphaneia-domain + epiphaneia-llm + epiphaneia-agent-core。

---

## MEDIUM (20项) — 建议修复

### M1. WebConfig / AiConfig / AsyncConfig / CacheConfig 为空壳占位

- **文件**: `epiphaneia-server/.../config/*.java` (4 个文件)
- **来源**: Backend B Agent + 主代理
- **描述**: 多个 @Configuration 类为空。WebConfig 注释 "CORS configured in Development phase" 但 CORS 是安全关键配置，不应留空。
- **修复**: 删除依赖自动配置已足够的空壳；WebConfig 至少配置 CORS 白名单。

### M2. DiagnosisSkill 导入 agent-core internal 类 + 重复实现 isActive

- **文件**: `epiphaneia-server/.../DiagnosisSkill.java:6-7, 98-107`
- **来源**: Architecture Agent
- **描述**: 直接导入 DiagnosisOrchestratorImpl 和 DiagnosisContext（均为 internal 包）。hasActiveDiagnosis() 重新实现了 DiagnosisStateMachine.isActive() 等价逻辑。
- **修复**: 依赖接口而非实现类；复用 DiagnosisStateMachine.isActive()。

### M3. PromQL rangeWindow 插入逻辑在嵌套函数时可能失效

- **文件**: `epiphaneia-engine/.../PrometheusQueryBuilder.java:38-44`
- **来源**: Backend A Agent
- **描述**: `sb.lastIndexOf(")")` 在嵌套聚合函数如 `sum(rate(...))` 时 range window 插入位置错误。
- **修复**: 使用真正的 PromQL AST 解析或至少检测嵌套层级。

### M4. ActuatorProbeService 错误响应手动拼接 JSON

- **文件**: `epiphaneia-engine/.../ActuatorProbeService.java:53`
- **来源**: Backend A Agent
- **描述**: 使用 String.format 拼接 JSON，escape() 未处理所有控制字符。
- **修复**: 使用已存在的 ObjectMapper.writeValueAsString()。

### M5. ReportSynthesizerImpl 使用 messages.get(0) 无排序保证

- **文件**: `epiphaneia-agent-core/.../ReportSynthesizerImpl.java:87`
- **来源**: 主代理
- **描述**: 假定第一条消息是用户问题。如果消息顺序变化（如并发或手动插入），报告指向错误的问题文本。
- **修复**: 按 role=USER 过滤获取提问消息。

### M6. PromptTemplateManager 参数值可注入模板语法

- **文件**: `epiphaneia-agent-core/.../PromptTemplateManager.java:28-39`
- **来源**: Backend B Agent
- **描述**: String.replace() 进行模板插值。用户输入若包含 `{{evidence}}` 语法会被替换。
- **修复**: 对用户提供的参数值进行转义处理。

### M7. BearerTokenFilter.sha256() 作为静态工具方法放在 Filter 类中

- **文件**: `epiphaneia-server/.../BearerTokenFilter.java:77-85`
- **来源**: Backend B Agent
- **描述**: SHA-256 工具方法定义在 Filter 中，AuthController 直接依赖 Filter 类——违反单一职责。
- **修复**: 提取到独立工具类 TokenHasher。

### M8. ApplicationMapper / DataSourceMapper 缺少显式 ignore 映射

- **文件**: `epiphaneia-server/.../mapper/ApplicationMapper.java`、`DataSourceMapper.java`
- **来源**: Backend B Agent
- **描述**: toEntity 方法未显式 ignore id/createdAt 等字段（与 LlmProviderMapper 风格不一致）。
- **修复**: 添加 `@Mapping(target = "id", ignore = true)` 等。

### M9. 缺少登出 (logout) 端点

- **文件**: `epiphaneia-server/.../AuthController.java`
- **来源**: Security Agent + Backend B Agent
- **描述**: 无 POST /api/v1/auth/logout。用户无法主动终止服务端会话。前端 Layout 无登出按钮。
- **修复**: 新增 logout 端点 + 前端登出入口。

### M10. EntitySchemaTest 标记为 integration 但无需数据库

- **文件**: `epiphaneia-server/.../EntitySchemaTest.java`
- **来源**: 主代理
- **描述**: 测试全为反射验证，不连接数据库。@Tag("integration") 标签名不副实，会导致在不需要 Docker 的场景被跳过。
- **修复**: 改为 @Tag("unit") 或移除标签。

### M11. 前端 SettingsPage 过长 (496行) 需拆分

- **文件**: `epiphaneia-web-ui/src/pages/SettingsPage.tsx`
- **来源**: Frontend Agent
- **描述**: 单文件含 6 个函数组件。难以维护和测试。
- **修复**: 拆分为 settings/ 目录下的独立文件。

### M12. 前端 Markdown 渲染中 URL & 转义问题

- **文件**: `epiphaneia-web-ui/src/components/MarkdownRenderer.tsx:7-9,32-35`
- **来源**: Frontend Agent
- **描述**: 先整体 HTML 转义再提取链接 URL，URL 中的 & 被错误二次转义。
- **修复**: 在设置 href 前做反向还原。

### M13. 前端 ConfirmDialog 缺少 ARIA 属性和焦点管理

- **文件**: `epiphaneia-web-ui/src/components/ConfirmDialog.tsx:16-27`
- **来源**: Frontend Agent
- **描述**: 模态框无 role="dialog"、aria-modal、焦点陷阱、ESC 关闭。
- **修复**: 添加完整 ARIA 属性 + 键盘事件处理。

### M14. 前端完全无响应式设计

- **文件**: `epiphaneia-web-ui/src/index.css`
- **来源**: Frontend Agent
- **描述**: CSS 中无任何 @media 查询。sidebar 固定 220px 在移动设备上占满屏幕。
- **修复**: 至少添加 768px 断点的基础响应式。

### M15. 前端 getList 函数重复了 request 的大部分逻辑

- **文件**: `epiphaneia-web-ui/src/api/client.ts:67-88`
- **来源**: Frontend Agent
- **描述**: getList 的 401 处理、错误处理与 request 几乎完全一致，代码重复约 20 行。
- **修复**: getList 内部调用 request。

### M16. 前端 useCallback 缺失 + inline style 重复

- **文件**: 全部页面组件
- **来源**: Frontend Agent
- **描述**: 事件处理器未用 useCallback；相同的 inline style 对象出现 10+ 次。
- **修复**: 提取语义化 utility class，对传子组件的回调加 useCallback。

### M17. DataSourceUnavailableException 丢失原始 cause

- **文件**: `epiphaneia-infra/.../DataSourceUnavailableException.java`
- **来源**: Backend A Agent
- **描述**: 构造函数只接受 (type, message)，无法传入 Throwable cause。
- **修复**: 增加重载构造函数。

### M18. docker-compose 缺少日志轮转配置

- **文件**: `product/docker/docker-compose.yml`
- **来源**: Deployment Agent
- **描述**: 无 logging driver 或日志轮转。长时间运行可能填满磁盘。
- **修复**: 添加 json-file driver + max-size/max-file。

### M19. Dockerfile 未使用 Spring Boot layertools 分层

- **文件**: `product/docker/Dockerfile`
- **来源**: Deployment Agent
- **描述**: 未利用 layertools 实现依赖层缓存。每次源码修改都重新传输数百 MB 的依赖层。
- **修复**: 启用 layertools 分层构建。

### M20. 前端 Vite 构建缺少 vendor chunk 分割

- **文件**: `epiphaneia-web-ui/vite.config.ts:15-18`
- **来源**: Deployment Agent + Frontend Agent
- **描述**: React/ReactDOM 内联到主 bundle，浏览器不能利用缓存。
- **修复**: 配置 manualChunks 将 react/react-dom/react-router-dom 分离为 vendor chunk。

---

## LOW (12项) — 改进建议

1. **EsQueryBuilder.escapeLucene() 为死代码** — 启用或删除 (Backend A)
2. **PrometheusQueryBuilder 无用 import `java.time.Instant`** — 删除 (Backend A)
3. **QueryRequest/QueryResult 空标记接口** — 添加至少 getData() 方法 (Architecture)
4. **DataSourceType 为 String 常量而非 enum** — 改为 enum (Architecture)
5. **SseEventPublisher.NOOP 定义在接口内** — 提取为独立实现类 (Architecture)
6. **AuthController 密码修改不校验密码强度** — 后端加 @Size(min=12) (Security)
7. **Token 生成熵损失** — new byte[12] 被截断，改为 new byte[24] (Security)
8. **缺少 favicon 和 meta description** — 添加基础 SEO/UX meta 标签 (Frontend)
9. **index.html lang="en" 应为 "zh-CN"** — 匹配目标用户 (Frontend)
10. **application-dev.yml 加密密钥路径错误** — `epiphaneia.encryption.key` 应为 `epiphaneia.encryption-key` (Security)
11. **Dockerfile 缺少 OCI 镜像标签** — 添加 org.opencontainers 标签 (Deployment)
12. **所有空壳 @Configuration 类** — 删除或实现 (Backend B + 主代理)

---

## 测试覆盖评估

| 模块 | 测试文件 | 测试数 | 覆盖度 | 评价 |
|------|---------|--------|--------|------|
| epiphaneia-infra | 2 | ~15 | 良好 | 加密核心路径全覆蓋 |
| epiphaneia-connector | 2 | ~6 | 偏弱 | 仅 type() 和连接失败 |
| epiphaneia-engine | 5 | ~20 | 一般 | QueryBuilder 缺少注入场景 |
| epiphaneia-agent-core | 5 | ~43 | 良好 | 状态机/编排器/路由核心逻辑覆盖全 |
| epiphaneia-server | 1 | ~10 | 严重不足 | 仅反射实体校验，controller/security 零覆盖 |

**缺失的关键测试**:
- Controller 集成测试 (@WebMvcTest) — 0
- Repository 集成测试 (@DataJpaTest) — 0
- Security Filter 测试 — 0
- SSE Emitter 测试 — 0
- DiagnosisSkill 单元测试 — 0

---

## 优先修复路线图

### 第一批：阻断发布 (本周)
1. [ ] B1 — Dockerfile mvnw 复制顺序
2. [ ] B2 — nginx.conf http 块
3. [ ] B3 — LlmClient 动态 Provider 配置
4. [ ] B10 — 轮换泄露的 API Key
5. [ ] B6 — 事务边界修复
6. [ ] B7 — @Valid 校验异常处理
7. [ ] B9 — CSRF 保护恢复
8. [ ] H4 — 会话固定修复

### 第二批：功能完整性 (下周)
9. [ ] B4 — Connector query() 接入 QueryBuilder
10. [ ] B5 — DiagnosisOrchestrator 接口修复
11. [ ] B8 — 连接测试真实实现
12. [ ] B12 — 前端路由认证守卫
13. [ ] B13 — SSE 重连机制
14. [ ] H1/H2 — QueryBuilder 注入修复

### 第三批：架构改进 (两周内)
15. [ ] B14 — Repository 移到 internal 包
16. [ ] B15 — server 依赖清理 + DiagnosisSkill 依赖接口
17. [ ] B11 — 启用 OWASP Dependency Check
18. [ ] H11 — CI/CD 流水线
19. [ ] H10 — 数据库备份策略
20. [ ] H20 — agent-core 模块拆分规划

### 第四批：质量提升 (一月内)
21. [ ] MEDIUM 各项
22. [ ] Server 模块测试补全
23. [ ] 前端 ESLint 配置 + 代码拆分
24. [ ] LOW 各项
