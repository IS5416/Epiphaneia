# 2026-08-02-01: Phase 8 批次 A/B/C 修复（安全 + 编排 + 连接器）

## 背景

引入 code-review-graph 知识图索引全项目后，派 4 子代理并行做全量基线审查（8 P0 + 20 P1 + 47 P2）。按优先级分批次修复，本日志覆盖已完成的 A/B/C 三批。审查详情见 `Development/codeReviews/2026-08-02-01-baseline-review.md`。

## 批次 A：安全（feat/phase8-sec-server）

- **初始密码泄漏**：密码不再打日志（原明文进 log.info）；凭据文件 POSIX 权限原子创建（`Files.createFile` + `rw-------`，消除 umask TOCTOU 窗口）；30 分钟自动删除 + 日志提示；密码 12→15 字节（120 bits）
- **CSRF 启用**：`CookieCsrfTokenRepository` + plain handler（前端 client.ts 已备好 X-XSRF-TOKEN 回传）；login 豁免（首访无 cookie）；`/setup` 路由包 ProtectedRoute 解决首次登录改密死锁（GET /auth/me 签发 cookie）
- **BearerTokenFilter**：principal 改真实 admin UUID（lazy proxy getId 无 DB 命中）；credentials 置 null（原始 token 不再进 SecurityContext）；admin null 守卫
- **RateLimitFilter 内存泄漏**：空闲桶 60 分钟 TTL + daemon 清理线程（10 分钟周期）；lastAccessNanos volatile

审查：初查 1 P0 + 4 P1，全部修复后复验通过。

## 批次 B：编排层（feat/phase8-orchestration）

- **超时机制接入执行循环**（P0）：`isTimedOut`（150s）此前定义完整、测试通过但从未调用，诊断可永久挂死。现每 phase 前 checkTimeout，超时 → ABORTED
- **templateReport NPE**（P0）：LLM 失败兜底路径 `confidence * 100` null 崩溃 = 双重故障。现显示 N/A
- **parseState 不再静默重启**（P1）：corrupt 状态字符串转 CREATED 会绕过 transition 校验重启已结束诊断。现抛 IllegalStateException → transition 转 ABORTED
- 顺手 9 项：空查询显式失败、risk 单词边界（highlight ≠ high）、建议上限 10、Pattern 常量、log NPE 防护、死参数清除、异常链保留、connector null 防护、LLM null 兜底

测试 +5（超时 ABORTED、corrupt abort、空查询 evidence、单词边界、null confidence）。

审查：无 P0/P1 可合入；4 个 P2 审查建议已修，2 项留后（重入窗口重置、corrupt 时超时跳过，均有兜底）。

## 批次 C：engine/connector（feat/phase8-engine-connector）

- **SSRF 重定向绕过**（P1 安全）：`followRedirects(NEVER)` — 302 不能再跳 169.254.169.254/127.0.0.1
- **PromQL 嵌套括号 bug**（P1）：lastIndexOf(")") 在嵌套聚合时吃掉闭合括号；改结构化构建 + 聚合函数名校验
- **认证传递缺口**（P1）：`QueryRequest.Typed` 加 AuthConfig 字段；orchestrator 从 DataSource 解析真实凭据；两个 Connector 应用 Basic/Bearer header（统一到 `AuthConfig.applyTo()`）；`DataSourceController.test` 不再用空凭据
- **null metric NPE**（P0）：buildRange/Rate/Instant 显式抛异常
- 顺手：IPv4-mapped IPv6 SSRF 绕过（手动字节检测，此 JDK 无 isIPv4MappedAddress API）、label key 校验、label value \r 转义、占位 QueryResult 改 Failure(NOT_IMPLEMENTED) + 类型校验、尾斜杠、日志堆栈

测试 +14（AuthConfig 解析、PromQL 边界、IPv4-mapped 禁止）。

审查：无 P0/P1 可合入；认证链路全路径验证无泄漏；3 P2 全部修复。

## 遗留（后续批次）

- 批次 B：重入 execute 超时窗口重置、corrupt 时 checkTimeout 跳过
- 批次 C：rangeWindow 格式未校验、actuatorUrl 日志可能含 userInfo 凭证
- **authConfig 明文存储**（批次 A 审查发现，未修）：DataSource.authConfig jsonb 透传未加密，需独立安全批次处理
- 批次 D/E 计划：LlmClient/OpenAiCompatibleChatModel 测试 + onStatus 错误映射、ConversationController 端点测试 + DiagnosisSkill 修复

## 批次 D：llm 模块（feat/phase8-llm）

- **LLM 链路零测试**（最大技术债）→ OpenAiCompatibleChatModel 6 个 MockWebServer 全链路测试（成功解析、429/401/500 映射、空 choices、/v1 归一化）+ ModelRouter 2 个 CUSTOM 测试
- **onStatus 错误映射** — 429 → LlmRateLimitedException（提取 Retry-After）；4xx → IllegalArgumentException；5xx → IllegalStateException。`LlmRateLimitedException` 此前定义了从未使用
- **CUSTOM 空 baseUrl** — 硬失败替代相对路径请求
- **异常体系移 api 层** — EpiphaneiaException + LlmRateLimitedException 从 infra internal 移 api.exception（跨模块契约归属）；internal 3 个异常补 import

审查：无 P0/P1 可合入；1 P2 留后（CUSTOM 校验时机 — 保存时校验）。
构建注意：`mvn install` 触发 OWASP dependency-check（NVD 下载可卡数分钟）— 用 `-Ddependency-check.skip=true`。

## 批次 E：server 层（feat/phase8-server）

- **hasActiveDiagnosis O(N)→O(1)** — 加载全部 messages 改为 DB 侧 existsBy 查询；corrupt 状态字符串不再 valueOf 抛异常
- **delete SSE 泄漏** — 先 close 再删（@Transactional 移除，deleteById 自带事务，SSE 生命周期与事务解耦）
- **replayEvents 假实现删除** — 前端零引用（创建后立即 close）
- **question 校验** — blank/2000 字符上限
- **SSE 异常加固** — send 捕获全部异常（含 IllegalStateException）；close 逐个 try-catch complete
- 测试 +13：ConversationController 纯 mock 单测（InOrder 顺序验证、诊断失败传播）

审查：通过（0 P0/P1）；2 P2 + 2 P3 已修，2 项留后（close 竞态窗口、虚拟线程 timeout 依赖 — 均低风险）。

## 提交

- `17c2d44` / merge `f29f024`（批次 A）
- `59fc0a4` / merge `6e2a775`（批次 B）
- `fa05cd6` / merge `33b801e`（批次 C）
- `4054e2c` / merge `0707c96`（批次 D）
- 批次 E 待提交
