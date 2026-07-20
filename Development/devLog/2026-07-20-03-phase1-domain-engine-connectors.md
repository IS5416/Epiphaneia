# 2026-07-20 — Phase 1 领域逻辑 + 引擎 + 连接器完成

## 目标

实现诊断状态机、Prompt 模板管理、LLM 路由、PromQL/ES DSL 查询构建器、Actuator 探测服务、Prometheus 和 Elasticsearch 连接器。

## 产出汇总

### 1A — 领域逻辑 (agent-core)

| 文件 | 操作 | 说明 |
|------|------|------|
| `internal/orchestration/DiagnosisStateMachine.java` | 新增 | 8 状态枚举 + 转换规则 + 150s 超时检测 + ABORTED 终态逻辑 |
| `internal/prompt/PromptTemplateManager.java` | 新增 | 从 classpath 加载 4 个模板，{{key}} 插值，缓存 |
| `internal/llm/ModelRouter.java` | 新增 | 5 种 LLM 后端校验 + API Key 解密 + base URL 解析 |

**4 个 Prompt 模板**：
| 模板 | 用途 |
|------|------|
| `system.txt` | 系统角色定义 + READ-ONLY 约束 + prompt injection 防护 |
| `planning.txt` | 查询计划生成指引 |
| `analysis.txt` | 证据分析 + 根因推断指引 |
| `report.txt` | Markdown 报告合成模板 |

### 1B — 引擎 (engine)

| 文件 | 操作 | 说明 |
|------|------|------|
| `internal/prometheus/PrometheusQueryBuilder.java` | 新增 | intent → PromQL (instant/range/rate 查询) |
| `internal/elasticsearch/EsQueryBuilder.java` | 新增 | intent → ES DSL JSON (search/error log 查询，特殊字符转义) |
| `internal/actuator/ActuatorProbeService.java` | 新增 | HTTP 探测 /actuator/health|info|metrics，敏感变量名过滤 |
| `internal/metrics/MetricsQueryServiceImpl.java` | 新增 | PromQL 构建 + ConnectorRegistry 调度（占位） |
| `internal/log/LogQueryServiceImpl.java` | 新增 | ES DSL 构建 + ConnectorRegistry 调度（占位） |
| `api/LogQueryService.java` | 新增 | 日志查询服务接口 |
| `api/MetricsQueryService.java` | 修复 | Object → QueryResult 返回类型 |

### 1C — 连接器 (connector)

| 文件 | 操作 | 说明 |
|------|------|------|
| `internal/prometheus/PrometheusConnector.java` | 重写 | JDK HttpClient 调用 Prometheus API，testConnection → /api/v1/status/buildinfo |
| `internal/elasticsearch/ElasticsearchConnector.java` | 重写 | JDK HttpClient 调用 ES REST API，testConnection → /_cluster/health |

## 验证

```
./mvnw test — 82/82 测试通过
./mvnw compile — 6/6 模块 BUILD SUCCESS
```

| 模块 | 新增测试 | 累计测试 |
|------|---------|---------|
| epiphaneia-infra | — | 17 |
| epiphaneia-connector | 8 | 8 |
| epiphaneia-engine | 19 | 19 |
| epiphaneia-agent-core | 28 | 28 |
| epiphaneia-server | — | 10 |
| **合计** | **55** | **82** |

## 技术决策

1. **状态机用静态 Map + EnumSet**：比 if-else 链清晰，比 Spring StateMachine 轻量。150s 超时 = 120s 诊断 + 30s 缓冲
2. **Engine 层不直接调用 Connector**：查询构建和连接器调度分离。编排层（Phase 2）负责实际的 ConnectorRegistry 调用。Engine 层只构建查询字符串
3. **HTTP 用 JDK HttpClient**：connector 和 engine 模块不依赖 spring-web。JDK 21 HttpClient 足够，避免额外依赖
4. **Prompt 模板用 {{key}} 占位符**：简单字符串替换，比 Mustache/Thymeleaf 轻量。模板从 classpath 加载，支持缓存
5. **Actuator 探测静默失败**：单个端点不可用时跳过，不阻塞其他探测。敏感变量名（password/secret/key/token）可辨识

## 技术债务

| 项目 | 说明 |
|------|------|
| Connector query() 为占位实现 | Phase 2 编排层实现完整的 typed request → query → parse → result 流程 |
| Engine Service 实现不调用 Connector | 同上，编排层负责实际的连接器调度 |
| PrometheusConnector 不发起真实 HTTP query | 当前 testConnection 可验证连接，但 query() 需要 Phase 2 的 typed request |

## Git 提交

| Commit | 消息 |
|--------|------|
| `e5812a5` | feat(agent-core): implement DiagnosisStateMachine, PromptTemplateManager, and ModelRouter |
| `efe3a5c` | test(agent-core): add PromptTemplateManagerTest — fix .gitignore prompt/ scope |
| `e186baf` | feat(engine): implement PromQL/ES query builders and ActuatorProbeService |
| `f6ef999` | feat(connector): implement PrometheusConnector and ElasticsearchConnector with HTTP/testConnection |
