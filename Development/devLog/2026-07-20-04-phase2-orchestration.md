# 2026-07-20 — Phase 2 Agent 编排完成

## 目标

实现 ReAct 诊断循环和报告合成——Phase 2 是应用心脏，复杂度最高。

## 产出汇总

| 文件 | 操作 | 说明 |
|------|------|------|
| `agent-core/api/DiagnosisSseEventPublisher.java` | 新增 | SSE 回调接口，6 个事件方法 + NOOP 默认实现 |
| `agent-core/internal/orchestration/DiagnosisContext.java` | 新增 | 诊断管线不可变上下文 record，含 4 字段校验 |
| `agent-core/internal/llm/LlmClient.java` | 新增 | Spring AI ChatClient 封装，统一 LLM 调用入口 |
| `agent-core/internal/orchestration/DiagnosisOrchestratorImpl.java` | 新增 | ReAct 循环：PLANNING→QUERYING→ANALYZING→COMPLETED，含 ABORTED/FAILED/COMPLETED_PARTIAL 路径 |
| `agent-core/internal/orchestration/ReportSynthesizerImpl.java` | 新增 | Markdown 报告合成：LLM 模式 + 模板降级模式 |
| `agent-core/api/repository/EvidenceRepository.java` | 新增 | Phase 0 遗漏的 Evidence JPA Repository |
| `agent-core/src/test/.../DiagnosisOrchestratorImplTest.java` | 新增 | 6 个 TDD 测试：happy path、无数据源、LLM 失败、Connector 失败、null SSE、Context 校验 |

## ReAct 循环流程

```
CREATED → PLANNING (LLM 分析问题，规划查询)
       → QUERYING  (执行 Prometheus/ES 查询，采集证据)
       → ANALYZING (LLM 分析证据，生成根因假设)
       → COMPLETED (持久化结果，发 SSE done)
       
异常路径：FAILED (LLM异常) / ABORTED (无效状态转换) / COMPLETED_PARTIAL (数据源不可用)
```

## 技术决策

1. **LlmClient 使用 Spring AI ChatClient.Builder**：避免直接依赖具体 LLM 实现，由 AiConfig 在 server 模块注解决定
2. **Connector 调用使用 raw type**：Connector<?,?> 泛型捕获类型无法传参，使用 raw type + @SuppressWarnings 绕过
3. **ReportSynthesizer 内置模板降级**：LLM 调用失败时自动生成基础 Markdown 报告，确保总有输出
4. **SSE Publisher 通过 setter 注入**：避免构造函数参数膨胀，Skill 层负责设置；默认 NOOP 防 NPE
5. **parseHypotheses 用简单正则**：ponytail — 暂不用 JSON mode / function calling，后续升级到结构化 LLM 输出
6. **Evidence 缺失 Repository**：Phase 0 遗漏 EvidenceRepository（原 6 个增至 9 个但 Evidence 未建），Phase 2 补建

## 技术债务

| 项目 | 说明 |
|------|------|
| Connector 仍是桩代码 | PrometheusConnector.query() 返回空 QueryResult，Phase 3 前需实现真正 HTTP 调用 |
| LlmClient 无超时控制 | 依赖底层 HTTP 客户端默认超时，需添加 Spring AI 超时配置 |
| parseHypotheses 正则脆弱 | 依赖 LLM 输出格式 "Hypothesis N:..."，需 JSON mode / function calling |
| 无请求级取消 | SSE 客户端断开后服务端继续运行，ABORTED 仅靠 150s 超时扫描 |

## 验证

```
./mvnw test — 92/92 通过
./mvnw compile — 6/6 BUILD SUCCESS
```

| 模块 | 测试数 | 新增 |
|------|--------|------|
| infra | 17 | — |
| connector | 8 | — |
| engine | 23 | — |
| agent-core | 34 | +6 |
| server | 10 | — |
| **总计** | **92** | **+6** |

## 变更量

~500 行实现代码 + ~200 行测试代码 = ~700 行。

## Git 提交

| Commit | 消息 |
|--------|------|
| `97427b7` | feat(agent-core): implement ReAct diagnosis loop and report synthesis |
| `4178add` | test(agent-core): add TDD tests for ReAct diagnosis loop |
| `59c4328` | fix(agent-core): resolve 7 BLOCKERs from Phase 2 review |

## 审查后修订 (2026-07-20)

三路子代理并行审查（设计一致性、架构安全、测试质量），审查报告：[codeReview/2026-07-20-03-phase2-review.md](../codeReviews/2026-07-20-03-phase2-review.md)

### 已修复 (BLOCKER)

| # | 问题 | 修复 |
|---|------|------|
| B-1 | SSE Publisher 线程不安全（单例字段） | 移入 execute() 参数，DiagnosisOrchestratorImpl 无状态 |
| B-2 | COMPLETED_PARTIAL 降级路径缺失 | 新增条件：无成功查询→COMPLETED_PARTIAL，queryingPhase 返回成功数 |
| B-3 | QUERYING 期异常→FAILED（非法迁移） | 异常处理感知状态：QUERYING→COMPLETED_PARTIAL，PLANNING/ANALYZING→FAILED |
| B-4 | LlmClient 忽略 ModelRouter/LlmProvider | LlmClient 所有方法接受 LlmProvider，注入 ModelRouter |
| B-5 | 原始异常泄露至 SSE/DB/LLM | 通用错误消息；连接器异常不注入 LLM；日志仅记录长度 |
| B-6 | ReportSynthesizerImpl 零测试 | 新增 6 个测试：LLM 成功、降级、空消息、无 AGENT、非 Conversation、null 风险字段 |
| B-7 | transition() 对 null diagnosisState 无防护 | 新增 null→CREATED 转换 + 健壮的 parseState() |

### 已修复 (HIGH)

| # | 问题 | 修复 |
|---|------|------|
| H-1 | 连接器异常注入 LLM | 失败证据 summary 改为通用消息 |
| H-2 | URL 未净化（凭证泄露风险） | 新增 sanitizeUrl() 剥离 userInfo |
| H-3 | 超时未强制 | (延后—ponytail: 依赖底层 HTTP 客户端，Phase 3 加 Spring AI 超时配置) |
| H-4 | FixSuggestion 从未持久化 | parseSuggestions 从 LLM 分析中提取建议 |
| H-5 | RiskAssessment 字段未填充 | parseRiskAssessment 基于关键词提取 |
| H-6 | 多数据源部分失败无测试 | 新增 multipleDataSourcesPartialFailure 测试 |
| H-7 | SSE 事件顺序未验证 | 新增 assertLinesMatch 验证完整事件序列 |

### 延后处理

- H-3 (超时)：底层 HTTP 已有超时，Phase 3 加显式配置
- M-1 (LLM 响应日志)：DEBUG 级别记录长度而非内容
- M-2 (名称净化)：已添加 sanitizePromptValue
- L-1~L-6：低优先级，后续 Phase 逐步修复
