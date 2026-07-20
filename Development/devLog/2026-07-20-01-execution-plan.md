# 2026-07-20 — Development 阶段执行计划

## 背景

ArchitectureDesign 阶段全部完成（5 份设计文档 + projectScaffold.md + product/ 骨架代码编译通过），进入 Development 阶段。

当前脚手架状态：
- Flyway V001（10 张表完整 DDL）已就绪
- 8 个 JPA 实体（骨架，基本 getter/setter，缺关系映射和 JPA Converter）
- 6 个 Repository 接口（空桩）
- Connector SPI、EncryptionService、ConnectorRegistry 接口已定义
- DiagnosisOrchestrator、ReportSynthesizer 接口需修正类型签名
- 2 个 Connector 实现类（空桩）
- Engine 层仅 MetricsQueryService 存在
- Server 层 6 个 @Configuration + SecurityConfig + GlobalExceptionHandler（骨架）
- Web UI 仅 App.tsx + main.tsx

## 执行策略

按依赖关系自底向上，每阶段内部多路并行。Phase 2（ReAct 编排）是唯一单线程关键路径。

前端在 Phase 4 开始——后端 API 完全稳定后，直连真实后端，零 mock 数据。

## 阶段划分

### Phase 0 — 基础层

**产出**：完整 JPA 实体映射 + 全部 Repository + Infra 服务

三路并行：

| 工作流 | 内容 | 分支 |
|--------|------|------|
| 0A 实体增强 | 补全 RootCauseHypothesis/FixSuggestion 实体，Message 加关系集合，JPA AttributeConverter（JSONB + 加密字段），`@DataJpaTest` 验证 DDL 一致性 | `feat/phase0-entity-enhancement` |
| 0B Repository | 新建 MessageRepository/RootCauseHypothesisRepository/FixSuggestionRepository，补全 AdminRepository 等自定义查询，集成测试 | `feat/phase0-repositories` |
| 0C Infra | AesGcmEncryptionService 实现，ConnectorRegistryImpl（Spring Bean 自动发现），单元测试 | `feat/phase0-infra-services` |

**验证**：`./mvnw test -pl epiphaneia-infra,epiphaneia-agent-core` 全部通过。

### Phase 1 — 领域逻辑 + 引擎 + 连接器

**产出**：状态机、Prompt 模板、模型路由、查询构建器、HTTP 连接器

三路并行：

| 工作流 | 内容 | 分支 |
|--------|------|------|
| 1A 领域逻辑 | DiagnosisStateMachine（8 状态 + ABORTED）、PromptTemplateManager（4 模板）、ModelRouter（5 LLM 后端） | `feat/phase1-domain-logic` |
| 1B 引擎 | PrometheusQueryBuilder、EsQueryBuilder、ActuatorProbeService、MetricsQueryServiceImpl、LogQueryService | `feat/phase1-engine` |
| 1C 连接器 | PrometheusConnector + ElasticsearchConnector 完整实现（HTTP 调用 + 解析 + testConnection），WireMock 测试 | `feat/phase1-connectors` |

**验证**：PromQL 生成正确，Connector 解析真实响应正确。

### Phase 2 — Agent 编排（关键路径）

**产出**：ReAct 诊断循环 + 报告合成

| 内容 | 分支 |
|------|------|
| DiagnosisContext、@Tool 类（PrometheusQueryTool/EsQueryTool）、DiagnosisOrchestratorImpl（PLANNING→QUERYING→ANALYZING→COMPLETED，120s 超时，ABORTED/COMPLETED_PARTIAL）、ReportSynthesizerImpl（Markdown 报告）、DiagnosisSseEventPublisher | `feat/phase2-orchestration` |

**缓解风险**：TDD 优先，Mock LLM + Mock Connector。拆 4 个子 PR（context → planning → querying → analysis → completion）。

**验证**：模拟完整诊断 "Why is user-service p99 latency 2s?"，验证状态转换、Evidence/Hypothesis/Suggestion 持久化、SSE 事件发射。

### Phase 3 — Server 层

**产出**：安全 + DTO + 7 个 Controller + SSE + DiagnosisSkill

A/B 可在 Phase 1 完成后提前启动，C 等 Phase 2。

| 工作流 | 内容 | 分支 |
|--------|------|------|
| 3A 安全 | SessionAuthFilter、BearerTokenFilter、RateLimitFilter（Bucket4j）、AdminSeeder、SecurityConfig 装配 | `feat/phase3-security` |
| 3B DTO/Mapper | ~16 个 DTO records、4 个 MapStruct Mapper | `feat/phase3-dtos-mappers` |
| 3C Controllers/SSE | 7 个 Controller、SseEmitterManager、SseEventReplayer、DiagnosisSkill、ArchUnit 测试 | `feat/phase3-controllers-sse` |

**验证**：AC-1 纯 API 走通。未认证 401。Bearer Token 可用。速率限制生效。

### Phase 4 — Web UI

**产出**：6 个 React 页面，直连真实后端，零 mock

| 内容 | 分支 |
|------|------|
| API 客户端、LoginPage、SetupWizard、DiagnosisWorkspace（SSE 实时流）、ReportView（Markdown + 导出）、History（搜索 + 删除）、Settings（5 Tab）、共享组件 | `feat/phase4-web-ui` |

**验证**：docker compose up → 完整用户旅程（登录 → 配置 → 诊断 → 报告 → 历史 → 设置）。

### Phase 5 — Docker 收尾

| 内容 | 分支 |
|------|------|
| Docker 多阶段构建验证、Nginx 配置、.env.example、product/README.md 快速开始 | `feat/phase5-docker-docs` |

## 依赖图

```
Phase 0 → Phase 1 (三路并行) → Phase 2 (单线程关键路径) → Phase 3C (Controllers/SSE)
                                                          ↗
Phase 0 → Phase 3A (安全) / Phase 3B (DTO/Mapper) ──────┘

Phase 3 完成 → Phase 4 (Web UI) → Phase 5 (收尾)
```

## 分支规范

- 命名：`feat/phase{N}-{scope}`
- 流程：`main` 创建分支 → 功能 + 测试 → PR（≤ 400 行新增）→ 审查 → squash merge → 删除分支
- 提交：Conventional Commits 简洁格式

## 关键风险

| 风险 | 缓解 |
|------|------|
| ReAct 循环复杂度高 | TDD + Mock LLM，拆 4 个子 PR |
| LangChain4j beta 不稳定 | 钉死 `1.17.2-beta27`，隔离在 `agent-core/internal` |
| PR 行数限制 | 大功能拆子 PR，每个独立可测 |
| 前端集成延后 API 差距 | Phase 3 完成后先跑 AC-1 再动前端 |

## 决策记录

1. **前端时机**：后端 API 稳定后（Phase 4），零 mock，直连真实后端。理由：避免 mock 数据清理成本，API 变更时前端联动修改一次到位。
2. **DB SQL 时机**：已完成（Flyway V001）。Phase 0 做实体映射验证而非重写 DDL。理由：DDL 经 dbSchema.md 审查已成熟，改动可能性低。
3. **API JSON 时机**：Phase 3-B，Controller 之前。理由：DTO 是前后端共同契约，先定义后实现，前端可据此预写类型定义。
4. **devLog 组织**：`Development/devLog/` 文件夹存放按日期编号的日志条目，`Development/devLog.md` 作为索引文件。理由：一天可能多份日志，文件夹 + 编号后缀比扁平文件更清晰。

## 详细步骤

完整实施步骤见计划文件：`C:\Users\Neo\.claude\plans\fluttering-zooming-russell.md`
