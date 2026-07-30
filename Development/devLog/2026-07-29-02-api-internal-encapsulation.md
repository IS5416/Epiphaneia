# 2026-07-29-02: api/internal 封装规范化

## 背景

项目所有模块均采用 `api`/`internal` 包分离模式，但实际编码中出现大量跨模块 `internal` 引用违规，模块边界形同虚设。针对此结构性问题做全面修正。

## 问题分析

**违规统计（修复前）：~50 处跨模块 internal 引用**

| 被引用模块 | 违规类型 | 严重度 |
|-----------|---------|--------|
| domain | 实体和仓库在 `internal.entity`, `internal.repository`，但被 agent-core、server、llm 引用 | 🔴 严重 |
| llm | LlmClient、ModelRouter、PromptTemplateManager 在 `internal.*`，被 agent-core、server 引用 | 🟡 中等 |
| engine | EsQueryBuilder、PrometheusQueryBuilder、ActuatorProbeService 在 `internal.*`，被 agent-core、server 引用 | 🟡 中等 |
| agent-core | DiagnosisStateMachine 在 `internal.orchestration`，被 server 引用 | 🟡 中等 |

## 修复方案与执行

### 1. domain 模块扁平化

**决策：** JPA 实体和 Spring Data Repository 本身就是 domain 模块的全部公开 API，`api`/`internal` 分层对此模块无意义。

**操作：**
- `domain.internal.entity.*` → `domain.entity.*`
- `domain.internal.repository.*` → `domain.repository.*`
- 更新 `EpiphaneiaApplication.java` 的 `@EnableJpaRepositories` 扫描路径
- 更新全部跨模块 import 引用

### 2. engine 模块 API 提升

**操作：**
- `engine.internal.elasticsearch.EsQueryBuilder` → `engine.api.query.EsQueryBuilder`
- `engine.internal.prometheus.PrometheusQueryBuilder` → `engine.api.query.PrometheusQueryBuilder`
- `engine.internal.actuator.ActuatorProbeService` → `engine.api.actuator.ActuatorProbeService`
- `engine.internal.log.LogQueryServiceImpl` 留于 `internal`（实现 `api.LogQueryService`）
- `engine.internal.metrics.MetricsQueryServiceImpl` 留于 `internal`（实现 `api.MetricsQueryService`）

### 3. llm 模块 API 提升

**操作：**
- `llm.internal.client.LlmClient` → `llm.api.client.LlmClient`
- `llm.internal.client.OpenAiCompatibleChatModel` → `llm.api.client.OpenAiCompatibleChatModel`
- `llm.internal.routing.ModelRouter` → `llm.api.routing.ModelRouter`
- `llm.internal.template.PromptTemplateManager` → `llm.api.template.PromptTemplateManager`

### 4. agent-core 模块 API 提升

**操作：**
- `agent.internal.orchestration.DiagnosisStateMachine` → `agent.api.orchestration.DiagnosisStateMachine`
- `agent.internal.orchestration.DiagnosisOrchestratorImpl` 留于 `internal`（实现 `api.DiagnosisOrchestrator`）
- `agent.internal.orchestration.ReportSynthesizerImpl` 留于 `internal`（实现 `api.ReportSynthesizer`）

## 结果

- 跨模块 `internal` 引用：**50 → 0**
- `api/` 包：对外公开契约，外部模块可依赖
- `internal/` 包：模块私有实现，零外部引用
- 全部编译通过，测试通过
