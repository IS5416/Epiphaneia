# 2026-08-02 全量基线审查

**范围**：product/ 全代码（首次 code-review-graph 索引后基线审查）
**方式**：4 个子代理并行审查（orchestration / llm / connector+engine / server），图数据辅助（大文件热点、未测试热点、内聚度）
**状态**：审查完成，待按优先级分批修复

---

## 汇总统计

| 模块 | P0 | P1 | P2/P3 | 健康度 |
|------|----|----|-------|--------|
| agent-core 编排层 | 2 | 1 | 17 | 中偏上 (6.5/10) |
| llm 模块 | 2 | 2 | 8 | 中偏下 (5/10) |
| connector + engine | 1 | 5 | 12 | 中偏上 (6/10) |
| server 层 | 3 | 12 | 10 | 中偏低 (6/10) |
| **合计** | **8** | **20** | **47** | — |

**测试覆盖严重不足**：server 模块仅 1 个 EntitySchemaTest；LlmClient/OpenAiCompatibleChatModel/connector executeQuery/ActuatorProbeService.probe 零测试；编排层 8 个核心方法无测试。

---

## P0（必须优先修复）

### server 层

- `AdminSeeder.java:50` — 初始管理员密码明文写入日志。日志常被集中采集，明文密码泄露。修复：日志只打用户名，密码只写临时文件。
- `AdminSeeder.java:56` — 初始密码明文写入 tmp 文件且权限未设 0600。多用户系统可被其他用户读取。修复：设置文件权限或改用 Spring 机制。
- `SecurityConfig.java:42` — CSRF 全局关闭，但 session cookie 认证存在。若浏览器前端走 cookie，状态变更接口无 CSRF 防护。修复：确认仅用 Bearer token 则注释声明；否则启用 CSRF。

### agent-core 编排层

- `DiagnosisStateMachine.java:24` — `isTimedOut` 定义完整、测试通过，但 `DiagnosisOrchestratorImpl.execute` 从未调用。诊断可能永久挂死在非终端状态（资源泄漏/状态卡死）。修复：执行循环中检查超时，或确认外部调度器存在。
- `ReportSynthesizerImpl.java:120` — `templateReport` 降级路径 `h.getConfidence() * 100` 无 null 保护。LLM 失败后的兜底路径崩溃 = 双重故障，报告完全不可用。修复：一行 null 检查。

### connector + engine

- `PrometheusQueryBuilder.java:26` — `buildRangeQuery`/`buildRateQuery` 无 null 检查，传 null metric 直接 NPE。修复：补 null guard。

---

## P1（重要）

### server 层（12 项，选列）

- `DataSourceController.java:85-88` — test() 用空字符串 AuthConfig 代替真实凭据，连接测试不验证认证有效性，误导运维。
- `BearerTokenFilter.java:66-69` — principal 设为字符串 "admin" 而非真实 Admin 实体；原始 token 设为 credentials 可能泄漏。
- `RateLimitFilter.java:33-34` — ConcurrentHashMap 无过期策略，IP 增长导致内存泄漏。
- `ConversationController.java:91-94` — delete() 不清理活跃 SSE 连接，无 @Transactional。
- `ConversationController.java:123-129` — replayEvents 创建 emitter 后立即 close，replay 功能完全不工作。
- `ConversationController.java:98-121` — sendMessage 无 question 长度上限。
- `DiagnosisSkill.java:99-101` — hasActiveDiagnosis 加载全部 messages 检查最后一条，O(N) 性能灾难。
- `DiagnosisSkill.java:101` — valueOf 非法状态抛异常中断诊断流程。
- `AesGcmEncryptionService.java:59` — 每次加密 new SecureRandom，高频场景性能瓶颈。
- `GlobalExceptionHandler.java` — 部分异常消息直接回传客户端，可能泄漏内网信息。
- `AuthController.java:80` — 未认证抛 IllegalStateException 映射为 409，应为 401。

### agent-core（1 项）

- `DiagnosisOrchestratorImpl.java:115-121` — 捕获 Exception 不捕获 Error；analyzingPhase 异常静默转 FAILED，调用方不知真实原因。

### llm（2 项）

- `OpenAiCompatibleChatModel.java:68` — 无 `.onStatus()` 错误处理，429/401/500 全变泛型异常，`LlmRateLimitedException` 定义了未使用。
- `ModelRouter.java:81` — CUSTOM provider 默认 baseUrl 空串，请求解析为相对路径全部失败。

### connector + engine（5 项）

- `ActuatorProbeService.java:43-55` — HttpClient 默认跟随重定向，302 可绕过 SSRF 检查访问 169.254.169.254 / 127.0.0.1。
- 两个 Connector `executeQuery` — SPI 签名无认证传递通道，认证环境永远 401/403。
- `PrometheusQueryBuilder.java:38-43` — lastIndexOf(")") 定位括号，嵌套函数（sum(rate(...))）生成错误 PromQL。
- `LogQueryServiceImpl.java:25` / `MetricsQueryServiceImpl.java:27` — 返回匿名空 QueryResult，非 Success 非 Failure，且 datasourceType 参数被忽略。

### 测试缺口（P1 级风险）

- LlmClient + OpenAiCompatibleChatModel 零测试（核心 LLM 链路盲改）
- connector 两个 executeQuery 无成功路径测试
- ActuatorProbeService.probe 无测试（SSRF 主流程未验证）
- ConversationController 全部端点零测试

---

## 建议修复批次（每分支 ≤400 行）

| 批次 | 分支 | 内容 |
|------|------|------|
| A | feat/phase8-sec-server | AdminSeeder 密码处理、CSRF 决策、BearerTokenFilter credentials、RateLimitFilter TTL |
| B | feat/phase8-orchestration | 超时接入执行循环、templateReport NPE、parseState 非法值、空 query 处理 |
| C | feat/phase8-engine-connector | SSRF followRedirects、PromQL null guard + 括号、尾斜杠、认证传递 |
| D | feat/phase8-llm-tests | LlmClient/OpenAiCompatibleChatModel 测试、onStatus 错误映射、CUSTOM baseUrl 校验 |
| E | feat/phase8-server-tests | ConversationController 端点测试、DiagnosisSkill 修复 |

---

## 架构告警（图数据，非阻塞）

- 编排层内聚 0.16 / 状态机社区 0.06 — 重构候选，与 P0 修复分开做
- 前端 api-data↔pages-handle 29 边、components-confirm↔pages-handle 25 边
- 大文件：SettingsPage.tsx 496 行、DiagnosisOrchestratorImpl 421 行类
