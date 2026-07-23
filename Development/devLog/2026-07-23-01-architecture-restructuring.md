# 架构重构 — 5模块→7模块 + 全量审查修复

**日期**: 2026-07-23
**范围**: 全项目 (166 源文件)
**触发**: 全项目综合代码审查 (15 BLOCKER + 20 HIGH + 20 MEDIUM + 12 LOW)

## 架构变更

### 模块拆分

拆分 agent-core (原34文件，4个职责) 为3个模块：

| 模块 | 职责 | 来源 |
|------|------|------|
| `epiphaneia-domain` (NEW) | 领域实体 (10) + Spring Data 仓库 (9) | 从 agent-core 拆出 |
| `epiphaneia-llm` (NEW) | LlmClient, ModelRouter, PromptTemplateManager | 从 agent-core 拆出 |
| `epiphaneia-agent-core` | 编排引擎 + 状态机 + 报告合成 | 精简后保留 |

### 新的依赖图

```
epiphaneia-infra → epiphaneia-domain → epiphaneia-engine/epiphaneia-connector
                                      → epiphaneia-llm
                                      → epiphaneia-agent-core
                                      → epiphaneia-server
```

### 包路径变更

| 旧路径 | 新路径 |
|--------|--------|
| `io.epiphaneia.agent.api.model.*` | `io.epiphaneia.domain.internal.entity.*` |
| `io.epiphaneia.agent.api.repository.*` | `io.epiphaneia.domain.internal.repository.*` |
| `io.epiphaneia.agent.internal.llm.*` | `io.epiphaneia.llm.internal.{client,routing,template}.*` |
| `io.epiphaneia.agent.api.DiagnosisSseEventPublisher` | `io.epiphaneia.llm.api.DiagnosisSseEventPublisher` |
| `io.epiphaneia.agent.api.LlmProviderValidator` | `io.epiphaneia.llm.api.LlmProviderValidator` |
| `io.epiphaneia.infra.internal.config.EpiphaneiaProperties` | `io.epiphaneia.infra.api.config.EpiphaneiaProperties` |
| `io.epiphaneia.agent.internal.orchestration.DiagnosisContext` | `io.epiphaneia.agent.api.DiagnosisContext` |

## 接口契约修复

- **DiagnosisOrchestrator**: 删除 `Object execute(Object, String)`, 改为 `void execute(DiagnosisContext, DiagnosisSseEventPublisher)`
- **ReportSynthesizer**: 删除 `String synthesize(Object)`, 改为 `String synthesize(Conversation)`
- **DiagnosisSkill**: 依赖 `DiagnosisOrchestrator` 接口而非 `DiagnosisOrchestratorImpl` 实现
- **server→connector**: 移除冗余依赖路径

## 安全修复 (7项)

- CSRF 保护启用 + 前端 X-XSRF-TOKEN 自动附加
- 会话固定修复 (login 前 invalidate + new session)
- Cookie secure: true + same-site: strict
- AdminSeeder 密码从日志输出改为临时文件写入
- RateLimitFilter X-Forwarded-For 受信代理校验
- 新增 POST /api/v1/auth/logout 端点
- OWASP dependency-check 启用

## 代码质量修复 (10项)

- GlobalExceptionHandler: 添加 @Valid + MethodArgumentNotValidException 处理
- @Transactional: 移至正确的 execute 方法
- EsQueryBuilder: escapeLucene() 启用 (修复注入风险)
- PrometheusQueryBuilder: label value 转义添加
- TokenHasher: sha256 从 BearerTokenFilter 提取为独立工具类

## 部署修复 (5项)

- Dockerfile: 添加 mvnw/.mvn COPY (修复构建阻断)
- docker-compose: 资源限制 + 日志轮转
- 备份/恢复脚本: scripts/backup-db.sh + restore-db.sh

## 前端修复 (5项)

- 路由认证守卫 (ProtectedRoute)
- SSE 断线通知 + 用户提示
- API 请求 30s 超时
- getList 复用 request 避免代码重复
- Login 表单添加 <label> 元素

## 验证

- 全量编译: `./mvnw clean compile` BUILD SUCCESS (7模块)
- 全量测试: `./mvnw clean test` ~100 tests, 0 failures
- 前端: `npx tsc --noEmit` 零错误
