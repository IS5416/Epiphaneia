# 系统架构 — Epiphaneia

> 版本：v1.0-draft | 日期：2026-07-18
> 输入：techStack.md、domainModel.md、apiDesign.md、architectureDesignHandoff.md
> 架构风格：模块化单体（Modular Monolith）——DDD 限界上下文 + 逻辑六层 + Maven 5 模块

---

## 1. 架构概览

### 1.1 架构风格

Epiphaneia 是 **模块化单体（Modular Monolith）**，不是微服务。理由：
- MVP 目标用户是单个开发者，`docker compose up -d` 一条命令启动
- 微服务化与"零配置接入"卖点冲突（handoff 1.4）
- Maven 多模块在代码层面提供清晰的模块边界，未来拆分为独立服务时边界已就绪

### 1.2 架构原则

| 原则 | 说明 | 技术落地 |
|------|------|---------|
| 只读不写 | Agent 不可执行写操作——架构层拦截，不依赖 prompt | Connector SPI 仅定义 `query()`，无 `execute()` |
| 领域隔离 | 领域层不感知框架（LangChain4j / Spring AI / Spring MVC） | 领域接口在 `agent-core`，实现在 `epiphaneia-server` 的基础设施包 |
| 可替换性 | LLM / 数据源 / 缓存可替换 | Spring AI 抽象层 + Connector SPI + Spring Cache 抽象 |
| 边界纪律 | 包依赖单向，禁止循环 | ArchUnit 测试 CI 强制 |
| 渐进式复杂度 | MVP 不做分布式，但预留扩展点 | 逻辑分层完整；物理模块 5 个而非 1 个 |

---

## 2. 逻辑分层

6 个逻辑层，按依赖方向自下而上排列（上层依赖下层，禁止反向）：

```
┌──────────────────────────────────────────────┐
│ ① API 层 (api)                               │  ← REST Controller / SSE / DTO
│    ┌──────────────────────────────────────┐  │
│    │ ② Skill 层 (skill)                   │  │  ← 用例编排 / 请求验证 / 权限检查
│    │    ┌──────────────────────────────┐  │  │
│    │    │ ③ Agent 编排 (orchestration) │  │  │  ← 诊断管道 / ReAct 循环
│    │    │    ┌──────────────────────┐  │  │  │
│    │    │    │ ④ LLM 调度 (llm)    │  │  │  │  ← Prompt 模板 / Token 管理 / 模型路由
│    │    │    │    ┌──────────────┐  │  │  │  │
│    │    │    │    │ ⑤ 数据引擎    │  │  │  │  │  ← PromQL/ES DSL 构建 / 结果解析
│    │    │    │    │   (engine)    │  │  │  │  │
│    │    │    │    │    ┌──────┐  │  │  │  │  │
│    │    │    │    │    │⑥集成层│  │  │  │  │  │  ← Connector SPI / HTTP 调用
│    │    │    │    │    │(conn) │  │  │  │  │  │
│    │    │    │    │    └──────┘  │  │  │  │  │
│    │    │    │    └──────────────┘  │  │  │  │
│    │    │    └──────────────────────┘  │  │  │
│    │    └──────────────────────────────┘  │  │
│    └──────────────────────────────────────┘  │
└──────────────────────────────────────────────┘

横切关注点 (cross-cutting):
  ┌────────────────────────────────────────────┐
  │ 基础设施 (infra): 加密 / 缓存 / 安全 / 事件总线 │
  └────────────────────────────────────────────┘
```

层级职责：

| 层 | 职责 | 不负责 |
|----|------|--------|
| ① API | HTTP 请求解析、SSE 流管理、输入验证、响应序列化 | 业务逻辑（委托给 Skill 层） |
| ② Skill | 用例编排（"提问诊断"是一个用例）、权限验证、参数转换 | Agent 执行细节（委托给编排层） |
| ③ 编排 | 诊断管道（PLANNING→QUERYING→ANALYZING→COMPLETED）、ReAct 循环控制 | 具体怎么调 LLM / 查数据源 |
| ④ LLM | Prompt 模板管理、多模型路由、Token 计数、响应解析 | HTTP 调用细节（委托给 Spring AI） |
| ⑤ 引擎 | PromQL / ES DSL 查询构建、结果类型化解析 | HTTP 通信（委托给 Connector） |
| ⑥ 集成 | 与外部系统（Prometheus/ES）的 HTTP 通信、认证、重试 | 查询语义（由引擎层决定查什么） |

---

## 3. 物理模块映射

逻辑 6 层 → 物理 5 Maven 模块（techStack §3）：

```
逻辑层          物理模块 (artifactId)
────────────────────────────────────
① API ─┐
② Skill ─┤──→ epiphaneia-server        (Spring Boot 入口 + Controller + SSE + Security)
③ 编排 ─┐
④ LLM   ─┤──→ epiphaneia-agent-core   (Agent 编排 + LLM 调度)
⑤ 引擎  ────→ epiphaneia-engine        (PromQL/ES DSL 构建 + 结果解析)
⑥ 集成  ────→ epiphaneia-connector     (Connector SPI + Prometheus/ES 实现)
横切    ────→ epiphaneia-infra          (加密/缓存/安全 Filter/ConnectorRegistry)

UI     ────→ epiphaneia-web-ui          (独立 npm 项目，非 Maven 模块)
```

### 3.1 模块依赖图

```
epiphaneia-web-ui ────────────────────────── (独立，无 Maven 依赖)

epiphaneia-server
 ├── epiphaneia-agent-core
 │    ├── epiphaneia-engine
 │    │    └── epiphaneia-connector
 │    │         └── epiphaneia-infra
 │    ├── epiphaneia-infra
 │    └── (epiphaneia-connector — 仅通过 ConnectorRegistry 接口，编译期无直接依赖)
 ├── epiphaneia-infra
 └── (epiphaneia-engine — 仅编译依赖，运行时通过 agent-core 调用)

依赖方向：上 → 下，右 → 左。禁止反向。
```

每个模块的 Java package 根：`io.epiphaneia.{module}`，如 `io.epiphaneia.connector`。

### 3.2 模块接口（public API 包）

每个模块暴露一个 `*.api` 包（public API），其他包为 `internal`（不对外暴露——ArchUnit 强制）：

| 模块 | public API 包 | 暴露内容 |
|------|-------------|---------|
| `epiphaneia-connector` | `io.epiphaneia.connector.api` | `Connector<T,R>` 接口、`QueryRequest`、`QueryResult`、`DataSourceType` 常量 |
| `epiphaneia-engine` | `io.epiphaneia.engine.api` | `MetricsQueryService`、`LogQueryService`、`QueryPlan`、引擎 DTO（`PrometheusQuery`、`MetricSample`、`LogEntry`） |
| `epiphaneia-agent-core` | `io.epiphaneia.agent.api` | **领域服务接口**：`DiagnosisOrchestrator`、`ReportSynthesizer`。**Repository 接口**：`ConversationRepository`、`ApplicationRepository`、`DataSourceRepository`、`LlmProviderRepository`、`AdminRepository`。**聚合根/实体**：`Conversation`、`Message`、`Application`、`DataSource`、`LlmProvider`、`Admin`。**值对象**：`Evidence`、`RootCauseHypothesis`、`FixSuggestion`、`RiskAssessment`、`ActuatorInfo`、`AuthConfig`。**领域事件**：`DiagnosisCompleted`、`DiagnosisFailed`、`EvidenceCollected` 等。**DTO**：`DiagnosisResult`、`ReportData` |
| `epiphaneia-infra` | `io.epiphaneia.infra.api` | `EncryptionService` 接口、`ConnectorRegistry` 接口 |
| `epiphaneia-server` | 无 public API | 叶子模块——不暴露接口。含 `main()` + Spring 配置 + Controller + DTO + MapStruct Mapper |

> `agent-core` 暴露 JPA Entity 作为 public API：这是 ADR-007 的务实折中——聚合根直接是 `@Entity`，不引入 PO↔Entity 映射层。Repository 接口属于领域层（"我需要通过 ID 加载 Conversation 聚合"是领域诉求）。

### 3.3 依赖细节说明

**server → engine 直接编译依赖**：`systemArchitecture.md` §3.1 标注了此依赖。存在理由：server 模块的 Controller DTO 和 MapStruct Mapper 可能需要引用 engine 层的类型（如 `MetricSample`、`LogEntry`）作为响应字段。运行时调用路径仍然是 Controller → Skill → agent-core → engine，不跳过中间层。若实际开发中此依赖未被使用（DTO 完全不需要 engine 类型），移除该依赖——ArchUnit 会暴露冗余 import。

---

## 4. 组件架构

### 4.1 epiphaneia-server 内部组件

```
epiphaneia-server/
├── io.epiphaneia.server
│   ├── EpiphaneiaApplication          ← main() + @SpringBootApplication
│   ├── controller/                    ← REST Controller（API 层）
│   │   ├── AuthController             ← /api/v1/auth/*
│   │   ├── ApplicationController      ← /api/v1/applications/*
│   │   ├── DataSourceController       ← /api/v1/datasources/*
│   │   ├── LlmController              ← /api/v1/llm/*
│   │   ├── ConversationController     ← /api/v1/conversations/* (含 SSE)
│   │   ├── WebhookController          ← /api/v1/webhooks/* (v0.9 → 501)
│   │   └── SystemController           ← /api/v1/system/*
│   ├── dto/                           ← 请求/响应 DTO（Bean Validation 注解）
│   │   ├── LoginRequest / LoginResponse
│   │   ├── ConversationCreateRequest / ConversationListResponse
│   │   └── ...
│   ├── mapper/                        ← MapStruct Mapper（DTO ↔ Domain）
│   │   ├── ConversationMapper
│   │   ├── ApplicationMapper
│   │   └── ...
│   ├── exception/                     ← 全局异常处理
│   │   ├── GlobalExceptionHandler     ← @ControllerAdvice → 统一错误响应
│   │   └── ErrorResponse              ← 标准错误体 { code, message, details }
│   ├── sse/                           ← SSE 基础设施
│   │   ├── SseEmitterManager          ← SseEmitter 生命周期（含重连回放）
│   │   └── SseEventReplayer           ← since 参数 → Message+Evidence 增量合成
│   ├── security/                      ← Spring Security 配置
│   │   ├── SecurityConfig             ← SecurityFilterChain
│   │   ├── SessionAuthFilter          ← Cookie Session 认证
│   │   ├── BearerTokenFilter          ← Bearer Token 认证
│   │   └── RateLimitFilter            ← 速率限制 (bucket4j)
│   ├── skill/                         ← Skill 层（用例编排）
│   │   └── DiagnosisSkill             ← "提问诊断"用例：验证 → 查聚合 → 调编排器 → 推送 SSE
│   └── config/                        ← Spring 配置
│       ├── CacheConfig                ← @Import infra 的 CacheConfig，初始化 Caffeine
│       ├── AiConfig                   ← Spring AI ChatClient Bean
│       ├── JpaConfig                  ← @EnableJpaRepositories + @EntityScan(agent-core)
│       ├── AsyncConfig                ← @EnableAsync + TaskExecutor Bean
│       ├── JacksonConfig              ← ObjectMapper: JavaTimeModule, FAIL_ON_UNKNOWN_PROPERTIES=false
│       └── WebConfig                  ← CORS / 异步配置
```

**DDD 层与传统三层对照**（以"创建诊断会话"为例）：

```
传统三层              DDD 实现                         位置
────────────────────────────────────────────────────────────────
Controller           ConversationController             server/controller
Service (业务)       DiagnosisSkill (用例编排)          server/skill
                     + Conversation 聚合行为            agent-core (领域)
                     + DiagnosisOrchestrator (领域服务)  agent-core/api
Service (数据)       —                                 不需要（聚合根自带行为）
Mapper/DAO           ConversationRepository             agent-core/api
                     (extends JpaRepository,            (接口在领域层，
                      自动生成代理实现)                  实现在基础设施层)

具体调用链：
ConversationController
  → DiagnosisSkill.ask(applicationId, question)         // 用例编排
    → applicationRepository.findById(applicationId)      // 查应用
    → conversationRepository.save(new Conversation(...)) // 持久化
    → diagnosisOrchestrator.execute(conversation, q)     // 领域服务（ReAct管道）
      → connectorRegistry.getConnector("PROMETHEUS")     // 获取数据源
      → metricsQueryService.query(promql)                // 查 Prometheus
      → logQueryService.query(esDsl)                     // 查 ES
      → chatClient.call(prompt)                          // LLM 分析
    → conversationRepository.save(conversation)          // 存诊断结果
    → eventPublisher.publish(diagnosisCompleted)         // 推 SSE
```

**为什么没有传统 Service 类？**

Server 模块确实没有 `ConversationService`、`DiagnosisService` 这类"万能 Service"。业务逻辑分布在三个地方：
1. **Skill 层**（`DiagnosisSkill`）：用例步骤编排——"先做什么再做什么"
2. **聚合根行为**（`Conversation.askQuestion()`）：聚合自身的业务规则——"怎么创建消息、怎么更新状态"
3. **领域服务**（`DiagnosisOrchestrator`）：跨聚合/跨外部系统的复杂逻辑——"ReAct 循环怎么跑"

这三者加起来 = 传统三层的 Service。拆开不是减少代码，是让每部分职责更单一、可独立测试：
- `DiagnosisSkill` 可 mock 所有依赖做用例测试
- `Conversation` 聚合可纯单测（无框架依赖）
- `DiagnosisOrchestrator` 可替换实现（LangChain4j → Spring AI AgentCore）

### 4.2 epiphaneia-agent-core 内部组件

```
epiphaneia-agent-core/
├── io.epiphaneia.agent.api           ← public API（领域接口）
│   ├── DiagnosisOrchestrator          ← 诊断管道接口
│   ├── ReportSynthesizer              ← 报告合成接口
│   └── dto/                           ← 领域 DTO（跨模块传输）
│       ├── DiagnosisRequest
│       ├── DiagnosisResult
│       └── ReportData
├── io.epiphaneia.agent.internal       ← 内部实现
│   ├── orchestration/
│   │   ├── DiagnosisOrchestratorImpl  ← LangChain4j ReAct 循环实现
│   │   └── DiagnosisStateMachine     ← 状态机（CREATED→...→COMPLETED）
│   ├── llm/
│   │   ├── PromptTemplateManager      ← Prompt 模板库
│   │   ├── ModelRouter               ← 按 provider 路由到对应 ChatClient
│   │   └── TokenUsageTracker         ← Token 消耗统计
│   └── report/
│       └── ReportSynthesizerImpl      ← Markdown 报告合成（LLM + 模板混合）
```

关键设计：`DiagnosisOrchestrator` 是领域接口（在 `api` 包），`DiagnosisOrchestratorImpl` 在 `internal` 包中通过 LangChain4j `AiServices` 实现。领域接口不含任何框架导入。

### 4.3 epiphaneia-engine 内部组件

```
epiphaneia-engine/
├── io.epiphaneia.engine.api           ← public API
│   ├── MetricsQueryService            ← 指标查询接口
│   ├── LogQueryService                ← 日志查询接口
│   └── model/                         ← 引擎领域模型
│       ├── PrometheusQuery
│       ├── ElasticsearchQuery
│       ├── MetricSample
│       └── LogEntry
├── io.epiphaneia.engine.internal
│   ├── prometheus/
│   │   ├── PrometheusQueryBuilder     ← 自然语言意图 → PromQL
│   │   └── PrometheusResultParser     ← Prometheus JSON → MetricSample[]
│   ├── elasticsearch/
│   │   ├── EsQueryBuilder             ← 时间窗+关键词 → ES DSL
│   │   └── EsResultParser             ← ES JSON → LogEntry[]
│   └── actuator/
│       ├── ActuatorProbeService       ← Actuator 端点探测
│       └── ActuatorInfoParser         ← Actuator 响应 → ActuatorInfo
```

### 4.4 epiphaneia-connector 内部组件

```
epiphaneia-connector/
├── io.epiphaneia.connector.api        ← public API（SPI 定义）
│   ├── Connector                       ← SPI 接口
│   ├── QueryRequest / QueryResult     ← 泛型基类
│   ├── DataSourceType                  ← 快捷常量（字符串包装）
│   └── AuthConfig                      ← 认证配置值对象
├── io.epiphaneia.connector.internal
│   ├── prometheus/
│   │   └── PrometheusConnector        ← Connector 实现（HTTP → Prometheus API）
│   └── elasticsearch/
│       └── ElasticsearchConnector     ← Connector 实现（HTTP → ES REST API）
```

### 4.5 epiphaneia-infra 内部组件

```
epiphaneia-infra/
├── io.epiphaneia.infra.api
│   ├── EncryptionService               ← 加密服务接口
│   └── ConnectorRegistry               ← Connector 注册与查找。为何在 infra 而非 connector：Registry 是"基础设施能力"（Bean 收集 + Map 查找），非 connector 的领域概念。Connector SPI 本身在 connector/api，Registry 在 infra 中组装它们。这避免了 connector 模块承担"插件注册表"职责——connector 只负责"怎么与外部系统通信"，infra 负责"怎么找到合适的 connector"
├── io.epiphaneia.infra.internal
│   ├── encryption/
│   │   └── AesGcmEncryptionService     ← AES-256-GCM 实现
│   ├── connector/
│   │   └── ConnectorRegistryImpl       ← Spring Bean 收集 + Map 查找
│   ├── cache/
│   │   └── CacheConfig                 ← Caffeine CacheManager Bean（server 通过 @Import 引用）
│   ├── event/
│   │   └── DomainEventPublisher        ← 封装 ApplicationEventPublisher，提供类型安全的 publish() 方法
│   └── exception/                      ← 领域异常层级
│       ├── EpiphaneiaException         ← 基类（RuntimeException）
│       ├── DataSourceUnavailableException ← Prometheus/ES 不可达 → 诊断降级
│       ├── DiagnosisTimeoutException   ← 120s 超时 → COMPLETED_PARTIAL
│       ├── LlmRateLimitedException     ← LLM API 限流 → 重试或降级
│       └── InvalidConfigurationException ← 数据源/LLM 配置错误 → 412/503
```

**异常处理流程**：
```
Connector 层: HTTP 超时/5xx → DataSourceUnavailableException
LLM 调度层: API 限流 → LlmRateLimitedException
编排层: 120s 总超时 → DiagnosisTimeoutException
    ↓
DiagnosisOrchestratorImpl 捕获 → 记录 Evidence 缺口 → COMPLETED_PARTIAL（非 FAILED——FR-2/3 降级语义）
    ↓
若不可恢复: FAILED 终态 + 异常 message 写入 Message.content
    ↓
Controller 层: GlobalExceptionHandler（@ControllerAdvice）
  - MethodArgumentNotValidException → 400 VALIDATION_ERROR
  - AccessDeniedException → 401 UNAUTHORIZED
  - 其他未捕获 → 500 INTERNAL_ERROR
  - 统一包装为 ErrorResponse { code, message, details }
```

---

## 5. 数据流

### 5.1 诊断请求完整路径（核心流程）

```
用户提问 "Why is user-service p99 high?"
        │
        ▼
┌─────────────────────────────────────────────────────┐
│ Nginx :80                                           │
│ /api/v1/* → proxy_pass → server:8080               │
│ / → serve web-ui/dist/                              │
└─────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────┐
│ ConversationController                              │  ← ① API 层
│ POST /api/v1/conversations                         │
│   1. 验证 Bearer Token / Session                    │
│   2. 解析 JSON → DiagnosisRequest DTO               │
│   3. 创建 SseEmitter                                │
│   4. 调用 DiagnosisSkill.ask()                      │
└──────────┬──────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────┐
│ DiagnosisSkill                                      │  ← ② Skill 层
│   1. 按 applicationId 获取 Application 聚合          │
│   2. 创建/查找 Conversation 聚合                    │
│   3. conversation.askQuestion(question) → Message    │
│   4. 调用 DiagnosisOrchestrator.execute()           │
│   5. 监听 DiagnosisEvent → 转为 SSE 推送            │
└──────────┬──────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────┐
│ DiagnosisOrchestratorImpl                           │  ← ③ 编排层
│                                                     │
│   ┌─ PLANNING ─────────────────────────────────┐   │
│   │ LangChain4j AiServices:                     │   │
│   │   "我需要查 user-service 的 p99 latency     │   │
│   │    和 error rate，时间范围最近1小时"          │   │
│   │   → 查询计划: [MetricsQuery, LogQuery]       │   │
│   └────────────────────────────────────────────┘   │
│                    │                                │
│   ┌─ QUERYING (ReAct 循环) ────────────────────┐   │
│   │ 第1轮: 调 MetricsQueryService               │   │
│   │   → p99 在 14:02 从 200ms 跳至 2s           │   │
│   │ 第2轮: 调 LogQueryService                   │   │
│   │   → 14:02 日志: "Redis connection timeout"   │   │
│   │ 第3轮: 调 MetricsQueryService (追加)         │   │
│   │   → Redis 连接池 Active=30/30 (exhausted)    │   │
│   │ → 证据收集完毕                              │   │
│   └────────────────────────────────────────────┘   │
│                    │                                │
│   ┌─ ANALYZING ────────────────────────────────┐   │
│   │ LLM 综合分析:                                │   │
│   │   证据1: p99 在 14:02 突增                   │   │
│   │   证据2: 14:02 Redis 连接超时日志             │   │
│   │   证据3: Redis 连接池耗尽 (30/30)             │   │
│   │   → 根因: Redis 连接池达到上限                │   │
│   │   → 建议: 扩容 maxTotal 30→100               │   │
│   └────────────────────────────────────────────┘   │
│                    │                                │
│   ┌─ COMPLETED ────────────────────────────────┐   │
│   │ Message 写入 Evidence + Hypotheses          │   │
│   │ 推送 done SSE 事件                          │   │
│   └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                    │
           (每步状态迁移 → SSE event 推送)
                    │
           ┌────────┴────────┐
           ▼                 ▼
┌──────────────────┐  ┌──────────────────┐
│ PrometheusConn.  │  │ ESConnector      │  ← ⑥ 集成层
│ GET /api/v1/     │  │ POST /_search    │
│   query_range    │  │                  │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
         ▼                     ▼
    Prometheus           Elasticsearch
```

### 5.2 SSE 事件流时序

```
Client                    Server                     DiagnosisOrchestrator
  │                         │                              │
  │── POST /conversations ──→│                              │
  │   (Accept: text/        │                              │
  │    event-stream)        │                              │
  │                         │── askQuestion() ────────────→│
  │←── event:state ─────────│   CREATED                    │
  │←── event:state ─────────│   PLANNING                   │
  │←── event:step ──────────│   "Querying Prometheus..."   │
  │←── event:state ─────────│   QUERYING                   │
  │←── event:step ──────────│   "Querying Elasticsearch..."│
  │←── event:state ─────────│   ANALYZING                  │
  │←── event:token ─────────│   "The root cause..."        │
  │←── event:token ─────────│   "appears to be..."         │
  │←── event:done ──────────│   COMPLETED                  │
  │                         │                              │
  │   SSE 连接关闭           │                              │
```

### 5.3 报告合成路径

```
GET /api/v1/conversations/{id}/report
        │
        ▼
ConversationController
  → Conversation.findById(id)
  → ReportSynthesizer.synthesize(conversation)
        │
        ▼
ReportSynthesizerImpl
  1. 遍历 conversation.messages（仅 COMPLETED / COMPLETED_PARTIAL 的 AGENT 消息）
  2. 提取 Evidence / Hypotheses / Suggestions / RiskAssessment
  3. 构建 Prompt: "基于以下诊断历史，生成结构化 Markdown 报告..."
  4. LLM 合成（Spring AI ChatClient）
  5. 模板引擎补充时间线、证据编号等机械部分
  6. 返回 Markdown 字符串
        │
        ▼
Controller → Accept: text/markdown → 浏览器下载
          → Accept: application/json → { "markdown": "...", "synthesizedAt": "..." }
```

---

## 6. 安全架构边界

```
                        ┌─────────────────────┐
                        │     Nginx :80        │
                        │  - HTTPS (可选)      │
                        │  - 静态资源 serve    │
                        │  - API 反向代理      │
                        └────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  ▼                  │
              │  ┌──────────────────────────────┐  │
              │  │     SecurityFilterChain       │  │  ← 认证边界
              │  │  /api/v1/auth/**  → permitAll │  │
              │  │  /api/v1/system/** → permitAll│  │
              │  │  其余 /api/v1/**   → 需认证   │  │
              │  └──────────┬───────────────────┘  │
              │             │                      │
              │    ┌────────┴────────┐             │
              │    ▼                 ▼             │
              │ SessionAuth    BearerTokenAuth      │
              │ (Cookie)       (Header)            │
              │    │                 │              │
              │    └────────┬────────┘              │
              │             ▼                      │
              │  ┌──────────────────────────────┐  │
              │  │       Skill 层               │  │  ← 只读边界
              │  │  禁止写入操作（架构层强制）     │  │
              │  └──────────────────────────────┘  │
              │             │                      │
              │             ▼                      │
              │  ┌──────────────────────────────┐  │
              │  │     Connector SPI             │  │  ← 外部调用边界
              │  │  仅定义 query(), testConn()   │  │
              │  │  无 execute() / write()       │  │
              │  └──────────────────────────────┘  │
              │                                    │
              └────────────────────────────────────┘
```

三层安全边界：
1. **认证边界**（Spring Security Filter）：未认证 → 401
2. **只读边界**（Skill 层 + Connector SPI）：无写入路径——架构层约束，非 prompt 层约束
3. **凭证边界**（EncryptionService）：API Key / 密码在 DB 中加密，API 响应永不回显

---

## 7. 横切关注点

### 7.1 事务管理

Spring `@Transactional` 边界在 **Skill 层**（`DiagnosisSkill`），不在 Controller 也不在 Repository。理由：
- 一次诊断包含多次 Repository 操作（创建 Conversation → 追加 Message → 保存 Evidence → 更新 Message 终态），需要事务一致性
- Skill 层是"用例"的入口——"完成一次提问诊断"是一个事务边界
- Repository 各自独立，由 Skill 协调提交或回滚

隔离级别：默认 `READ_COMMITTED`（PostgreSQL 默认）。单用户场景下无脏读/不可重复读风险。

事务超时：与诊断超时对齐（120s），但事务应远短于此——诊断管道的 QUERYING 阶段在事务外（Prometheus/ES 查询无需事务），仅 PLANNING 开始前和 ANALYZING 完成后涉及 DB 写。

### 7.2 并发控制

单用户 MVP 下并发冲突概率极低，但仍有两处需要防护：
- **会话级诊断互斥**：apiDesign 规定"一个会话同时只有一个诊断运行"→ 通过 `message` 表的 `diagnosis_state` 部分索引查询进行中诊断，应用层返回 409
- **并发创建同名 Application**：`application.name` 不强制 DB UNIQUE（允许用户自己区分），但 UI 上提示"同名应用已存在，是否继续？"

不加 `@Version` 乐观锁的理由：单用户场景下用户不会同时从两个 tab 发同一个会话的提问。Webhook（v0.95）引入后评估乐观锁需求。

### 7.3 异步任务

Spring `@EnableAsync` + `ThreadPoolTaskExecutor`（虚拟线程执行器，Boot 4.x 默认）。唯一异步场景：
- `POST /applications/{id}/probe` → Actuator 探测（apiDesign 明确标注"异步"）→ `@Async` 方法，返回 202
- Webhook 接收（v0.95）→ `@Async` 方法消费告警 → 创建诊断会话

虚拟线程执行器无池大小限制，与 Boot 4.x 默认虚拟线程模型一致。

### 7.4 ActuatorProbeService 位置说明

`ActuatorProbeService` 归属 engine 模块而非 connector 模块的理由：
- 它同时涉及 HTTP 调用（类似 Connector）+ 结果解析（类似 Engine）
- 但 Actuator 探测返回的数据（health/metrics/env/info）是 Epiphaneia 的诊断上下文，不是外部数据源——语义上属于"应用元信息采集"而非"外部系统集成"
- Connector SPI 是社区贡献扩展点——Actuator 探测不应暴露为 SPI（无社区扩展场景）
- 如果未来需要探测非 Spring Boot 应用的运行状态（Go pprof、Node.js inspector），Engine 层的 `ActuatorProbeService` 接口可扩展为更通用的 `RuntimeProbeService`



```
┌─────────────────┐     ┌──────────────────┐
│  Epiphaneia     │     │  Prometheus      │
│  (PostgreSQL)   │     │  (外部，只读)    │
│                 │     │                  │
│  admin          │     │  - 时序指标       │
│  api_token      │     │  - 不做数据采集   │
│  application    │     └──────────────────┘
│  data_source    │
│  llm_provider   │     ┌──────────────────┐
│  conversation   │     │  Elasticsearch   │
│  message        │     │  (外部，只读)    │
│  evidence       │     │                  │
│  hypothesis     │     │  - 应用日志       │
│  fix_suggestion │     │  - 不做数据采集   │
└─────────────────┘     └──────────────────┘
     │                          │
     │ 自身数据                  │ 外部数据
     │ (持久化)                  │ (查询后即弃)
     │                          │
     └──────────┬───────────────┘
                │
        ┌───────┴───────────────────────┐
        │      Epiphaneia App           │
        │  只查询 Prometheus/ES         │
        │  摘要存入 Evidence (PG)        │
        │  原始数据不入库               │
        └───────────────────────────────┘
```

核心原则：Epiphaneia 的 PostgreSQL 只存自身运营数据（会话、证据摘要、配置）。原始监控数据和日志数据永远不写入 Epiphaneia 的存储——查询后只保留摘要作为诊断证据。

---

## 8. 容器拓扑

```
docker-compose
─────────────────────────────────────────────────────────
    ┌──────────┐
    │  Nginx   │ :80 (反向代理 + 静态文件)
    └────┬─────┘
         │
    ┌────┴─────────┐
    │  epiphaneia  │ :8080 (Spring Boot, 单实例)
    │  -server     │
    └────┬─────────┘
         │
    ┌────┴─────────┐
    │  PostgreSQL  │ :5432
    └──────────────┘

(不在 compose 中——用户已有的基础设施)
    Prometheus :9090
    Elasticsearch :9200
```

详见 deploymentArchitecture.md。

---

## 9. 扩展点架构

### 9.1 Connector SPI（社区贡献入口）

```
                     ConnectorRegistry
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        Prometheus    Elasticsearch   Loki
        Connector     Connector       Connector
        (内置)        (内置)          (社区贡献)
```

新增 Connector 步骤：
1. 实现 `Connector<T,R>` 接口（`io.epiphaneia.connector.api`）
2. 添加 `@Component` 注解
3. `type()` 返回新的数据源类型字符串

无需修改领域层枚举或 ConnectorRegistry。

### 9.2 Skill 扩展（v1.x）

```
                     SkillRegistry (未来)
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
           OpsSkill     TestSkill    ReviewSkill
           (v0.9)       (v1.x)       (v1.x)
```

v0.9 只有 OpsSkill（运维诊断），Skill 扩展点接口保留但不实现多 Skill 注册。v1.x 引入新 Skill 时，仅需实现 `Skill` 接口并注册。

### 9.3 Language Adapter 扩展（v1.x）

```
                     LanguageAdapterRegistry (未来)
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
         JavaAdapter              GoAdapter
         (内置)                   (社区 wanted)
```

Adapter 负责解析语言特定的运行时信息（JVM GC 日志 / Go goroutine dump 等）。v0.9 仅 JavaAdapter（Spring Boot Actuator 集成）。

---

## 10. 关键架构决策记录 (ADR)

### ADR-001: 模块化单体而非微服务

**决策**：Maven 多模块单体应用。

**理由**：
- MVP 目标用户 `docker compose up -d` 一条命令
- 微服务化稀释"零配置接入"核心卖点
- Maven 模块边界已为未来拆分就绪
- 虚拟线程单体可承载 MVP 全量并发需求

**替代方案**：前后端分离 + 独立 Agent 服务 → 增加部署复杂度，与 handoff 1.4/1.5 冲突。

### ADR-002: Spring MVC + Virtual Threads 而非 WebFlux

**决策**：Spring MVC + Tomcat 11 + Virtual Threads（默认）。

**理由**（techStack D-1）：
- Boot 4.x 虚拟线程为 Tomcat 默认执行模型，同步代码 = 异步效果
- LangChain4j 是同步 API 范式，WebFlux 需大量 `block()` 桥接
- 单人开发体验：同步代码易调试

### ADR-003: Spring AI 2.0 + LangChain4j 双框架

**决策**：ChatClient 走 Spring AI 2.0，Agent 编排走 LangChain4j 1.16。

**理由**（techStack §2.4）：
- Spring AI 2.0 新 AgentCore 成熟度待验证，MVP 保守不押注
- LangChain4j ReAct 循环有更多生产验证
- 两个框架职责不重叠，切换成本仅在一层

**复评时点**：Spring AI 2.1 或 2.2 发布时评估 AgentCore 是否可替代 LangChain4j。

### ADR-004: 报告不持久化

**决策**：报告为 Conversation 聚合的只读视图，每次实时合成。

**理由**：
- PRD FR-5 已定："报告为会话级实时合成"
- 减少一张 `report` 表 + 报告版本管理复杂度
- 导出即下载——用户自行管理文件
- 追问可改变结论——过期报告比没报告更危险

**升级路径**：若用户需求报告归档/审计，加 `conversation_report` 表 + 导出时快照写入。

### ADR-005: 诊断"停止"不取消服务端

**决策**：客户端关闭 SSE 不中断服务端诊断管道。

**理由**：
- userFlow 流程 1 定："服务端总是跑到终态并存档"
- 取消语义需协作式检查点，增复杂度且与 LLM 流式调用冲突
- 用户可稍后在历史会话中看到完整结果

### ADR-006: 单用户单实例安全模型

**决策**：单 Admin + Bearer Token，不做多用户/RBAC。

**理由**：
- PRD FR-10 定："单管理员账号"
- 多用户/RBAC 在 v2.0——当前 YAGNI
- 认证复杂度与用户量成正比，MVP 最小可行安全模型

### ADR-007: 领域层 package 隔离，不拆 Maven 模块

**决策**：领域对象（聚合根、实体、值对象、Repository 接口、领域服务接口）与领域服务实现放在同一 Maven 模块 `epiphaneia-agent-core`，通过 `api/` vs `internal/` package 隔离，不拆 `epiphaneia-domain` + `epiphaneia-application` 两个模块。

**理由**：
- handoff 1.8 目标物理模块 **4±1**（最多 5 个），当前已达 5。拆 domain → 6 模块，偏离"不过度拆分"原则
- ArchUnit 的 `..api..` 包依赖规则（不允许外部模块依赖 `..internal..`）在 CI 强制执行，效果等价于 Maven 编译期隔离——违规在 PR 阶段被拦截，不会进 main
- Spring Data JPA 的 `@Entity` / `@OneToMany` 等注解已引入 Hibernate 依赖到领域对象——无法做到"纯领域模块"（zero framework dependency）。要纯化需引入 PO→领域对象映射层，10 张表做双向映射是过度设计
- MVP 所有领域服务仅一个实现（LangChain4j），无多实现切换需求——独立 application 模块的价值不成立

**升级路径**：v2.0 多 Skill（Test + Review Skill）引入时，若不同 Skill 需要不同编排实现，拆：
```
epiphaneia-domain/              ← 聚合根 + Repository 接口 + 领域服务接口
epiphaneia-skill-ops/          ← OpsSkill 实现
epiphaneia-skill-test/         ← TestSkill 实现
```
在此之前，5 Maven 模块已覆盖全部隔离需求。

---

## 11. 技术债务与已知简化

| 简化项 | 影响 | 偿还条件 |
|--------|------|---------|
| 无请求级取消（只跑完不中断） | 用户"停止"后 LLM token 浪费 | CANCELLED 状态需求出现时（成本超 阈值/用户投诉） |
| 无消息队列 | AlertManager 高并发时 `@Async` + 任务表有瓶颈 | v1.x 告警风暴削峰需求 |
| 无 Redis | 单实例够了但无跨实例缓存 | v2.0 多用户/多实例 |
| 无服务降级熔断 | LLM/Prometheus 不可用时依赖 HTTP 重试 | LLM 调用稳定性差时引入 Resilience4j |
| 无请求链路追踪 | 单体单线程，日志 correlationId 够用 | 微服务拆分时引入 Micrometer Tracing |

---

## 12. 模块包结构总览

```
io.epiphaneia
├── connector/
│   ├── api/          ← Connector<T,R> SPI
│   └── internal/     ← PrometheusConnector, ESConnector
├── engine/
│   ├── api/          ← MetricsQueryService, LogQueryService
│   └── internal/     ← PrometheusQueryBuilder, EsQueryBuilder
├── agent/
│   ├── api/          ← DiagnosisOrchestrator, ReportSynthesizer
│   │                    + Repository 接口 (ConversationRepository, ApplicationRepository, ...)
│   │                    + 聚合根 (Conversation, Message, Application, ...)
│   │                    + 值对象 (Evidence, RootCauseHypothesis, ...)
│   │                    + 领域事件 (DiagnosisCompleted, ...)
│   └── internal/     ← DiagnosisOrchestratorImpl (LangChain4j)
│                       + ReportSynthesizerImpl
│                       + PromptTemplateManager, ModelRouter
├── infra/
│   ├── api/          ← EncryptionService, ConnectorRegistry
│   └── internal/     ← AesGcmEncryptionService, ConnectorRegistryImpl
├── server/
│   ├── controller/   ← REST Controller
│   ├── dto/          ← 请求/响应 DTO
│   ├── security/     ← Spring Security Filter 配置
│   ├── sse/          ← SseEmitterManager, DiagnosisEventPublisher
│   ├── skill/        ← DiagnosisSkill (用例编排)
│   └── config/       ← JpaConfig, AiConfig, CacheConfig, WebConfig
└── (web-ui/ — 前端，非 Java 包)
```

> 关键约定：
> - **Repository 接口在 agent-core/api**——"我需要通过 ID 加载 Conversation 聚合"是领域诉求，非基础设施细节。Spring Data JPA 自动生成代理实现，不写 Impl 类
> - **领域实体（@Entity）在 agent-core/api**——见 ADR-007：包级隔离 + JPA 注解的务实折中
> - ArchUnit 规则：任何 `*.internal.*` 包的类被其他模块 `import` → CI 挂构建。模块间仅通过 `*.api.*` 包通信

---

> 下一步：用户审查 systemArchitecture 后，进入 securityDesign。
> 配套图表：架构图可单独生成 HTML（archify），不在本文档中嵌入——保持 markdown 可读性。
