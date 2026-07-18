# Epiphaneia 全流程综合审查报告

> 审查日期：2026-07-18
> 审查范围：IdeaValidation → ProductPlanning → ArchitectureDesign 三阶段全部产出物
> 审查方法：Phase 1 四维度审查 + Phase 2 对抗验证
> 本文档为最终综合审查结论，用户据此决定后续行动

---

## 总体判决

### 判决：conditional_go_with_rollback

**修改指定回滚项后可以进入 projectScaffold，无需全部三阶段推倒重来。**

**核心理由：**

1. 项目整体设计扎实——P0 需求 12/12 全覆盖，领域→API→DB→架构映射一致，技术选型链条清晰。
2. 无产品级致命缺陷——产品定位"不采集数据、只做智能消费层"精准，开源 + 自部署差异化真实存在。
3. 但存在 **2 项编译级阻塞**（B-1 循环依赖、B-2 ArchUnit 规则），不修正则 pom.xml 生成的模块依赖和 CI 规则都是错的。
4. 以及 **4 项需跨阶段回滚修正**的设计缺陷（C2 时间估算、H4 范围蔓延、H2 竞品遗漏、CRITICAL-1 消息悬挂），它们在 projectScaffold 前修正成本远低于开发中修正。

**不选其他判决的理由：**

| 判决 | 为什么不选 |
|------|----------|
| go_with_adjustments | B-1 是 Maven 编译级阻塞——不修 pom.xml 写出来就是错的。这不是"少量修改"能覆盖的。H4 的范围蔓延需要回滚到 IdeaValidation 阶段砍 Must Have，不是 ArchitectureDesign 内的局部调整。 |
| major_rollback_required | 项目的产品方向、技术选型、架构分层的核心决策均正确。所有 P0 需求有完整实现路径。没有根本性的架构错误或产品逻辑缺陷需要推翻重来。 |

---

## 需回滚项清单

以下是进入 projectScaffold 之前**必须完成**的回滚修正。按回滚阶段分组，标注级联影响和预估工作量。

### 回滚至 ArchitectureDesign 阶段

#### RB-1：修正 connector ↔ infra 循环依赖（对应 B-1）

- **回滚阶段**：ArchitectureDesign
- **原因**：`epiphaneia-infra` 包中 `ConnectorRegistry` 需要引用 `epiphaneia-connector` 包中的 `Connector` 类型，而 `connector` 又声明依赖 `infra`。形成 `connector → infra → connector` 循环，Maven 拒绝编译。
- **范围**：将 `Connector` SPI 接口（`Connector<T,R>`、`QueryRequest`、`QueryResult`、`DataSourceType`）从 `connector/api` 移动到 `infra/api`。`ConnectorRegistry` 接口保留在 `infra/api`，`ConnectorRegistryImpl` 调整 import。`connector` 模块职责从"SPI 定义 + 实现"退化为"纯实现"（PrometheusConnector、ElasticsearchConnector），依赖方向变为 `connector → infra`（单向）。
- **修改文件**：`techStack.md` §3 模块依赖表、`systemArchitecture.md` §3.2 模块接口表 + §4.4 扩展点架构 + §4.5 ConnectorRegistry
- **级联影响**：无下游文档影响（接口签名不变，仅包路径移动）
- **预估工作量**：30 分钟（移动接口声明 + 调整 import 路径描述）

#### RB-2：重写 ArchUnit 规则使用实际包名（对应 B-2）

- **回滚阶段**：ArchitectureDesign
- **原因**：techStack §2.12 的示例 ArchUnit 规则使用 `..domain..` 和 `..infrastructure..`，但实际包结构是 `..agent..` 和 `..infra..`。规则永远匹配不到任何类，CI 中的边界纪律检查完全无效。ADR-007 的"包隔离替代模块隔离"策略完全依赖 ArchUnit——而当前 ArchUnit 是空的。
- **范围**：基于实际包名 `io.epiphaneia.agent..`、`io.epiphaneia.infra..`、`io.epiphaneia.engine..`、`io.epiphaneia.connector..`、`io.epiphaneia.server..` 重写全部 ArchUnit 规则。
- **修改文件**：`techStack.md` §2.12、`systemArchitecture.md` §3.2 模块隔离规则说明
- **级联影响**：无
- **预估工作量**：45 分钟（6-8 条规则编写 + 包名校验）

#### RB-3：修正加密密钥配置绑定方式（对应 B-3，降级后仍建议修正）

- **回滚阶段**：ArchitectureDesign
- **原因**：`@Value("${epiphaneia.encryption.key}")` 搭配环境变量 `EPIPHANEIA_ENCRYPTION_KEY` 当前可以工作（Spring 的 `SystemEnvironmentPropertySource` 会自动做下划线→点号转换），但改用 `@ConfigurationProperties` 是最佳实践——类型安全、IDE 补全、更可靠的绑定。
- **范围**：创建 `@ConfigurationProperties(prefix = "epiphaneia.encryption")` 配置类，替换 `@Value` 用法。
- **修改文件**：`securityDesign.md` §4.2
- **级联影响**：无
- **预估工作量**：15 分钟

### 回滚至 ProductPlanning 阶段

#### RB-4：增加服务端崩溃后的消息恢复机制（对应 CRITICAL-1）

- **回滚阶段**：ProductPlanning
- **原因**：当前状态机无 `CANCELLED` / `ABORTED` / `STALE` 状态。服务端崩溃后，处于 `PLANNING` / `QUERYING` / `ANALYZING` 的 Message 永久停留在非终态。API 409（`DIAGNOSIS_IN_PROGRESS`）会永久阻塞该会话的新消息，造成僵尸会话。
- **范围**：(a) 在状态机中增加 `ABORTED` 终态；(b) 应用启动时扫描所有非终态 Message，自动迁移到 `ABORTED`（reason="Server restart"）；(c) 在 API 的 409 场景中增加超时豁免逻辑。
- **修改文件**：`userFlow.md` §3 状态机、`domainModel.md` §2.2 Message 状态机、`apiDesign.md` §7 POST /conversations 409 逻辑、`dbSchema.md` §2.7 message 表（新增枚举值 `ABORTED`）
- **级联影响**：domainModel → apiDesign（409 逻辑变更）→ dbSchema（新状态枚举值）→ userFlow（状态机 +1）
- **预估工作量**：1 小时（四处文档修改）

#### RB-5：增加 Message 的协作式取消机制（对应 MEDIUM-5 关联）

- **回滚阶段**：ProductPlanning
- **原因**：DELETE Conversation/Application 时，若有进行中的诊断，服务端继续消耗 LLM token 但结果被丢弃。虽不阻塞 projectScaffold，但与 RB-4 共享同一套状态机修改，建议一起修正。
- **范围**：(a) Message 增加 `isCancelled()` 检查 + `failureReason` 字段；(b) Agent 编排循环中每步检查取消状态；(c) DELETE 端点先标记取消，等待编排退出，再 CASCADE 删除。
- **修改文件**：`domainModel.md` §2.2 Message、`apiDesign.md` §4/§7 DELETE 行为描述、`dbSchema.md` §2.7 message 表（加 `failure_reason` 列）
- **级联影响**：domainModel → apiDesign → dbSchema
- **预估工作量**：30 分钟（与 RB-4 合并修改）

### 回滚至 IdeaValidation 阶段

#### RB-6：裁剪 MVP Must Have 范围（对应 H4 上调为 CRITICAL + C2）

- **回滚阶段**：IdeaValidation
- **原因**：MVP 定义了 6 层架构、3 大扩展接口（Skill/Adapter/Connector，每个要求"接口定义 + 一个实现"）、4 种 LLM 后端兼容。这些"为未来设计"的工程量单独看每个都是好的实践，但叠加在 MVP 上形成隐藏范围蔓延——实际交付时间预估 10-14 周而非 4-5 周。
- **范围**：(a) MVP 阶段不做 Skill/Adapter/Connector 插件化接口——直接硬编码 Ops Skill + Prometheus Connector + Java Adapter；(b) LLM 适配先只做 OpenAI 兼容 API（Ollama/DeepSeek 都兼容此格式），Anthropic 留到 P1；(c) 六层架构压缩为三层（数据集成 + LLM 推理 + Web UI/API）；(d) MVP Must Have 从 9 项砍到 5 项（保留：自然语言问答 + Prometheus 集成 + LLM 诊断 + Web UI + Docker 部署；ELK 集成和 REST API 可合并/降级）。
- **修改文件**：`mvpScope.md` §4 架构层 + §5 Must Have 清单、`PRD.md` §4 功能需求、`systemArchitecture.md` §3 模块划分（模块数可能从 5 减到 3-4）
- **级联影响**：IdeaValidation mvpScope → ProductPlanning PRD → ArchitectureDesign systemArchitecture（Maven 模块数量）→ Development 工作量估算
- **预估工作量**：1.5 小时（三处文档修改 + 决策确认）

#### RB-7：补充遗漏竞品分析（对应 H2）

- **回滚阶段**：IdeaValidation
- **原因**：竞品分析对 precious112/Argus 只停留在概念层面，无 GitHub 指标；遗漏了 Grafana AI/ML（最危险的遗漏——它已经是开发者的"监控系统首页"）、HolmesGPT（功能 1:1 重叠）、k8sgpt（GitHub 5k+ stars）、RunWhen Local、Keep 等竞品。
- **范围**：补充 Argus 的 GitHub 指标和功能对比表；新增 Grafana AI、HolmesGPT、k8sgpt、RunWhen Local、Keep 的竞品分析；在竞争策略中增加"Grafana 共存策略"。
- **修改文件**：`competitorAnalysis.md` §2 直接竞品 + §3 间接竞品
- **级联影响**：PRD 产品定位论述、README 竞品对比表
- **预估工作量**：1 小时

#### RB-8：为关键假设补充量化验证标准（对应 C1，降级后仍建议修正）

- **回滚阶段**：IdeaValidation
- **原因**：4 条关键假设全部使用"发布后收集反馈"软性表述，无量化验证标准。虽然对单人项目不构成阻塞，但量化标准（如"诊断根因正确率 >= 60%"）应在 PRD 验收标准中体现。
- **范围**：在 `validationConclusion.md` 中为每条关键假设补充量化指标。
- **修改文件**：`validationConclusion.md` §6 关键假设
- **级联影响**：PRD 验收标准
- **预估工作量**：20 分钟

#### RB-9：增加市场数据可追溯性标注（对应 H1，建议修正）

- **回滚阶段**：IdeaValidation
- **原因**：市场数据引用全部为二手转述，TAM 推算中的 10% 假设无依据。虽不阻塞开发，但影响产品论述可信度。
- **范围**：在引用后标注数据来源级别（直接/转述）；补充 Google Trends 查询参数；为 TAM 推算中的百分比假设增加推理链。
- **修改文件**：`marketResearch.md` §2-4
- **级联影响**：PRD 市场规模论述
- **预估工作量**：30 分钟

---

## 确认发现（按严重度排序）

以下为经 Phase 2 对抗验证后的全部确认发现。每个发现标注原始编号、Phase 2 调整后严重度、级联影响。

### CRITICAL（阻塞 projectScaffold 或影响产品存亡）

| # | 发现 | 原始编号 | Phase 1 严重度 | Phase 2 调整 | 级联影响 |
|---|------|---------|--------------|-------------|---------|
| C-F1 | connector ↔ infra 循环依赖，Maven 无法编译 | B-1 | CRITICAL | **CRITICAL（维持）** | systemArchitecture → techStack → 所有模块 POM |
| C-F2 | ArchUnit 规则包名全部错误（`..domain..` vs `..agent..`），CI 边界纪律检查完全无效 | B-2 | CRITICAL | **CRITICAL（维持）** | techStack → systemArchitecture → CI 配置 |
| C-F3 | MVP 隐藏范围蔓延：6 层架构 + 3 大扩展接口 + 4 种 LLM 后端，实际交付预估值 10-14 周而非 4-5 周 | H4+C2 | HIGH+CRITICAL | **CRITICAL（H4 上调）** | mvpScope → PRD → systemArchitecture（Maven 模块数）→ Development 迭代计划 |
| C-F4 | 单人 4-5 周交付 9 个 Must Have 模块估算严重偏乐观，Arithmetic 不成立（9×5 天中位数 = 45 天 ≈ 9 周），Ops Skill 的 Prompt 工程 5-7 天严重低估 | C2 | CRITICAL | **CRITICAL（维持）** | mvpScope → Development 迭代规划 → 社区预热节奏 |
| C-F5 | 服务端崩溃后诊断消息永久悬挂——状态机无 ABORTED 终态，API 409 导致会话变僵尸数据 | CRITICAL-1 | CRITICAL | **CRITICAL（维持）** | userFlow 状态机 → domainModel Message → apiDesign 409 逻辑 → dbSchema 枚举值 |

### HIGH（显著影响产品质量或可信度）

| # | 发现 | 原始编号 | Phase 1 严重度 | Phase 2 调整 | 级联影响 |
|---|------|---------|--------------|-------------|---------|
| H-F1 | 竞品分析遗漏严重：Argus 缺 GitHub 数据、遗漏 HolmesGPT/k8sgpt/Grafana AI | H2 | HIGH | **HIGH（维持）** | competitorAnalysis → PRD 产品定位 → README 竞品对比 → 社区推广叙事 |
| H-F2 | 市场数据引用缺乏可追溯性：付费报告二手转述、TAM 10% 假设无依据、Google Trends 无查询参数 | H1 | HIGH | **HIGH（维持）** | marketResearch → PRD 市场规模论述 |
| H-F3 | 关键假设未设定量化验证阈值，GO 决策缺少止损条件 | C1 | CRITICAL | **HIGH（下调）** | validationConclusion → PRD 验收标准 |
| H-F4 | RateLimitFilter 的 ConcurrentHashMap 无界增长——攻击者用随机 IP/Token 可导致 OOM | D-6 | D（重要缺陷） | **HIGH（上调）** | securityDesign → application.yml（Caffeine 配置） |
| H-F5 | PRD FR-6 多应用隔离隐式假设所有应用共享同一套 Prometheus + ES——不支持多 Prometheus 实例/多环境场景 | MEDIUM-4 | MEDIUM | **HIGH（上调）** | PRD FR-6 边界说明 → domainModel DataSource → dbSchema data_source 表 |
| H-F6 | 竞品威胁被低估：Grafana 已占用户"监控首页"入口，加入 AI 诊断功能时零用户迁移成本 | 报告04 §5/§8（遗漏） | — | **HIGH（新增）** | competitorAnalysis → 产品策略 → GTM 叙事 |

### MEDIUM（应在开发阶段起尽早处理）

| # | 发现 | 原始编号 | 级联影响 |
|---|------|---------|---------|
| M-F1 | 虚拟线程三项隐藏成本未评估：Spring Security ThreadLocal 泄漏、MDC 上下文丢失、HikariCP 连接池瓶颈（10 连接 vs 数万虚拟线程） | D-3 | techStack → SecurityConfig → logback 配置 |
| M-F2 | 双 AI 框架（Spring AI 2.0 + LangChain4j）长期维护负担未充分论证——MVP 是否需要两个 AI 框架？ | D-4 + O-1 | techStack → ADR-003 → pom.xml 依赖树 |
| M-F3 | SSE 重连回放中间步骤丢失——无 SSE Event 表导致断连后只能看到终态，用户可能怀疑 AI 未分析直接编结果 | MEDIUM-1 + S-3 + O-3 | apiDesign GET /events → systemArchitecture SseEventReplayer → dbSchema |
| M-F4 | dbSchema 缺少关键 CHECK 约束——diagnosis_state、evidence.source、application.name 唯一性、data_source.type 唯一性均只靠应用层 | MEDIUM-6 | dbSchema DDL → apiDesign 409 逻辑 |
| M-F5 | 用户画像缺乏一手调研支撑，画像 C（QA）需求与 MVP 严重不匹配 | H3（HIGH→MEDIUM 下调） | userPersona → PRD 目标用户定义 |
| M-F6 | 报告合成性能无指标——LLM 实时合成多轮对话上下文可能 5-10 秒延迟，NFR 只定义诊断 ≤30s 未定义报告 | MEDIUM-2 | PRD NFR-1 → apiDesign GET /report → domainModel ReportSynthesizer |
| M-F7 | Application 聚合持有网络 I/O 方法 `probeActuator()`，违反 DDD 聚合纯洁性 | MEDIUM-3 | domainModel §3.1 → apiDesign POST /applications/{id}/probe |
| M-F8 | 清空进行中会话仍消耗 LLM token——服务端继续跑完但结果被丢弃 | MEDIUM-5 | domainModel Message → apiDesign DELETE → dbSchema |
| M-F9 | FixSuggestion.autoExecutionAllowed 在 MVP 中始终为 false，是无用字段增加概念噪音 | MEDIUM-7 | domainModel §2.3 → dbSchema §2.10 → apiDesign |
| M-F10 | SSE close 事件 `manual_stop` reason 在服务端不可判定——TCP 断开无法区分用户主动停止还是网络故障 | CRITICAL-2（严重度下调） | apiDesign §9 → userFlow 流程 1 |
| M-F11 | domainModel 缺 DiagnosisResult 类型定义——领域服务签名缺口 | CRITICAL-3（严重度下调） | domainModel §6.1 |
| M-F12 | 加密密钥 @Value 与环境变量绑定可工作但不优雅，应改用 @ConfigurationProperties | B-3（严重度下调） | securityDesign §4.2 |
| M-F13 | CSRF 配置文档中保留了有漏洞版本和修正版两套代码——projectScaffold 应直接用修正版 | D-5 | securityDesign §7.1 → SecurityConfig.java |
| M-F14 | ConnectorRegistry 归属矛盾：domainModel 放在领域服务，systemArchitecture 放在 infra | CON-1 | domainModel §6.3 → systemArchitecture §4.5 |
| M-F15 | "核心引擎语言无关"与"Java 生态绑定是护城河"存在叙事张力——精确化表述：推理层语言无关，采集层语言深度绑定 | CON-2 | mvpScope §4.4 + competitorAnalysis §4 → 产品文案 |
| M-F16 | GitHub Connector 在 mvpScope 架构图中标注但三阶段设计均无实现入口——DataSourceType 枚举无 GITHUB 值 | GAP-1 | mvpScope → systemArchitecture 扩展点 → dbSchema data_source 表 |
| M-F17 | 未定义请求体大小限制——恶意用户可发送 10MB 诊断提问导致 OOM | D-8 | securityDesign §6 → application.yml |
| M-F18 | 金标准故障集来源未定义——自构造合成故障 vs 真实生产故障，准确率含义完全不同 | O-6 | ProductPlanning 金标准构建方案 → Development 验证计划 |
| M-F19 | Spring AI 2.0 刚 GA 不到 2 个月，社区资源稀少——学习成本未计入时间估算 | O-1 | techStack → Development 时间估算 |
| M-F20 | LangChain4j spring-boot4-starter 存在性未经独立验证——可能只是 Boot 3 starter 碰巧运行 | O-2 | techStack → projectScaffold 冒烟测试 |

### LOW（观察建议，择机处理）

| # | 发现 | 原始编号 |
|---|------|---------|
| L-F1 | JDK 17+ → 21 LTS 版本漂移，前序文档（mvpScope/PRD）未回写更新标记 | DRIFT-1 |
| L-F2 | React 18 → 19 版本漂移，前序文档未标注以 ArchitectureDesign 为准 | DRIFT-2 |
| L-F3 | "经验(知识库)"在核心公式中出现但 MVP 不包含——营销语言与交付范围不一致 | GAP-2 |
| L-F4 | FR-17 Java Adapter 深度诊断（GC 日志/连接池）缺乏数据路径设计 | GAP-3 |
| L-F5 | server → engine 直接依赖跳过 agent-core，与分层架构原则不一致 | D-2 |
| L-F6 | docker-compose 与"一键部署"矛盾——Nginx 需要预构建前端 dist | S-7 |
| L-F7 | 验证结论 5 维度全 PASS 呈现确认偏误倾向 | M3（IdeaValidation） |
| L-F8 | "Spring Boot 零配置接入"可行性缺乏技术论证——对 200+ 微服务的生产环境无法零配置 | M2（IdeaValidation） |
| L-F9 | 竞争壁垒"Java 生态绑定"存在自洽矛盾——依赖 Spring AI/LangChain4j 同时声称它们构成护城河 | M1（IdeaValidation） |
| L-F10 | 集成测试 compose override 执行模式不清晰 | S-2 |
| L-F11 | agent-core/api 包子包结构未定义，ArchUnit 精细规则暂无锚点 | S-6 |
| L-F12 | application.yml 的 profile 结构未定义哪些配置属于哪个 profile | S-4 |
| L-F13 | docker-compose 缺少日志轮转、资源限制、优雅停机时间配置 | S-1 |
| L-F14 | Prompt 中用户输入用 `"""..."""` 分隔可被注入破坏 | 专项审查 5.2 |
| L-F15 | 威胁模型遗漏：供应链攻击/镜像投毒/诊断结果投毒 | 专项审查 5.3 |
| L-F16 | 数据库 tag 字段用 JSONB 而非原生 VARCHAR[] 数组 | LOW-8（ProductPlanning） |
| L-F17 | 密码复杂度仅前端/应用层校验，DDL 无体现 | LOW-7（ProductPlanning） |
| L-F18 | 改密后旧 token 仍有效，与 API Token 吊销语义不一致 | LOW-4（ProductPlanning） |
| L-F19 | 统一语言中"诊断"一词过载（产品定位/会话/单次运行三层含义） | LOW-6（ProductPlanning） |

---

## Phase 2 遗漏问题（O-1 至 O-7）

以下问题在 Phase 1 四份审查报告中未被识别或讨论不足，由 Phase 2 对抗验证补充。

| 编号 | 遗漏问题 | 严重度 | 动作建议 |
|------|---------|--------|---------|
| O-1 | 双 AI 框架（Spring AI 2.0 + LangChain4j）的长期维护负担未充分质疑——MVP 为什么需要两个 AI 框架？如果 LangChain4j 能处理 LLM 调用 + Agent 编排，Spring AI 的 ChatClient 承担什么不可替代的职责？ | **HIGH** | ArchitectureDesign 增加 ADR 明确论证双框架必要性和单框架可行性。如果为了"未来灵活性"，MVP 阶段选一个并 hold 另一个 |
| O-2 | LangChain4j `spring-boot4-starter` 的存在性未经独立验证——社区项目是否真的跟上了 Spring Boot 4.x GA？ | **MEDIUM** | projectScaffold 第一个 action：`mvn dependency:tree` 验证传递依赖兼容性 |
| O-3 | SSE 重连回放的过程事件丢失问题被两份报告识别但均标记为非阻塞——用户断连后重连只能看到"瞬间完成"的终态，对"AI 正在分析你的系统"体验是重大损害 | **HIGH** | ArchitectureDesign 中至少定义 SSE 事件的内存缓存策略（Caffeine 缓存最近 5 分钟事件序列，key=conversationId），以便重连时回放过程事件 |
| O-4 | RabbitMQ/消息队列的完全缺席未被讨论——MVP 阶段不引入是正确的 YAGNI 决策，但四份报告均未记录"为什么不需要" | **LOW** | architectureDesignHandoff 或 systemArchitecture 增加简要说明 |
| O-5 | React SSR/CSR 混合模式选择缺乏论证——未记录为什么是纯 CSR 而非 Next.js/Remix | **LOW** | techStack 补充一行决策记录 |
| O-6 | Ops Skill Prompt 工程的"金标准"来源未定义——自构造合成故障 vs 真实生产故障，达到 60% top-1 准确率的难度天差地别 | **HIGH** | ProductPlanning 或 ArchitectureDesign 明确金标准构建方法——至少 50% 来自真实开源项目 issue 的生产故障案例 |
| O-7 | 单用户模型下"多应用管理"的价值边界模糊——工程量 15% 但价值可能仅 5% | **LOW** | ProductPlanning review 中增加简短评估 |

---

## 级联影响总览

大型修正项及其跨文档级联链：

```
RB-6 (MVP 范围裁剪)
  ├── IdeaValidation/mvpScope.md          ← 起始修改点
  ├── ProductPlanning/PRD.md              ← Must Have 清单缩减
  ├── ArchitectureDesign/systemArchitecture  ← Maven 模块数从 5→3-4
  ├── ArchitectureDesign/techStack.md     ← 依赖声明调整
  └── Development/                        ← 迭代计划重排

RB-1 (循环依赖修正)
  ├── ArchitectureDesign/systemArchitecture  ← 接口包路径移动
  ├── ArchitectureDesign/techStack.md     ← 模块依赖方向表
  └── Development/pom.xml                 ← 编译验证

RB-4 (消息悬挂恢复)
  ├── ProductPlanning/userFlow.md         ← 状态机 +1
  ├── ProductPlanning/domainModel.md      ← Message 状态 + ABORTED
  ├── ProductPlanning/apiDesign.md        ← 409 豁免逻辑
  └── ProductPlanning/dbSchema.md         ← 枚举值更新

RB-7 (竞品补遗)
  ├── IdeaValidation/competitorAnalysis   ← 起始修改点
  ├── ProductPlanning/PRD.md              ← 产品定位论述
  └── README.md                           ← 竞品对比表
```

---

## 驳回项摘要（附录）

以下发现经 Phase 2 对抗验证后严重度被**下调至 MEDIUM**，原始 CRITICAL/HIGH 标记不成立。已在 MEDIUM 清单中列出，此处仅简要说明为何驳回原始严重度。

| 原始编号 | 发现摘要 | 原始严重度 | 下调理由 |
|---------|---------|-----------|---------|
| C1 | 关键假设无量化止损条件 | CRITICAL | 对单人项目而言，止损条件是"开发者觉得没意思了就停"，非阻塞条件。量化标准应在 PRD 验收标准中体现 |
| CRITICAL-2 | SSE close 事件 `manual_stop` reason 语义矛盾 | CRITICAL | 规范文档中的枚举值逻辑错误——不影响运行时行为（服务端继续跑完并归档），不会导致崩溃或数据丢失 |
| CRITICAL-3 | domainModel 缺 DiagnosisResult 类型定义 | CRITICAL | 编码时 10 分钟可补充的定义缺失——不阻塞 ArchitectureDesign 阶段入口，不影响下游 apiDesign/dbSchema 契约完整性 |
| B-3 | @Value 与环境变量绑定不成立 | CRITICAL | `SystemEnvironmentPropertySource` 自动做下划线→点号转换——当前写法可以正常启动 |

---

## 审查统计数据

### 发现问题总览

| 原始阶段 | CRITICAL | HIGH | MEDIUM | LOW | 总计 |
|---------|----------|------|--------|-----|------|
| IdeaValidation | 2 | 4 | 4 | 3 | 13 |
| ProductPlanning | 3 | 0 | 7 | 8 | 18 |
| ArchitectureDesign | 3 | 0 | 8 | 7 | 18 |
| 跨阶段（报告04） | 0 | 3 | 3 | 5 | 11 |
| Phase 2 遗漏（O-1~7） | 0 | 3 | 1 | 3 | 7 |
| **合计** | **8** | **10** | **23** | **26** | **67** |

### Phase 2 验证统计

| 验证结果 | 数量 | 占比 |
|---------|------|------|
| 完全确认，严重度维持 | 11 | 69% |
| 确认，严重度下调 | 4 | 25% |
| 部分驳回，严重度下调 | 1 | 6% |

### 审查质量评分（Phase 1）

| 维度 | 得分 |
|------|------|
| 发现准确度 | 8.5/10 |
| 证据质量 | 9/10 |
| 建议可操作性 | 8.5/10 |
| 跨阶段联动 | 8/10 |
| 完整性 | 7.5/10 |
| 严重度校准 | 7/10 |
| 文字质量 | 9/10 |
| **总体** | **8.2/10** |

---

## 亮点总结

尽管审查持批判立场，以下方面值得肯定：

1. **产品定位"不采集数据、只做智能消费层"精准简洁**——一句话说清了与所有监控工具的关系，是整个产品策略中最清晰的决策。
2. **"只读不写"安全约束系统性地钉入产品 DNA**——Connector SPI 只有 `query()` 无 `execute()`，mvpScope Won't Have 清单反复强调，这是架构级纵深防御的典范。
3. **Won't Have 清单比 Must Have 更有价值**——每项有清晰的"为什么不做"和"何时做"，这是 MVP 定义中最成熟的实践。
4. **P0 需求 12/12 全覆盖**——所有需求有完整的领域→API→DB→架构实现路径，无裸奔需求。
5. **文档工程水准线以上**——5 份 IdeaValidation 文档 + 6 份 ProductPlanning 文档 + 5 份 ArchitectureDesign 文档，交叉引用清晰，文件命名符合规范。
6. **CSRF 配置自查自纠**——securityDesign 发现自己漏洞并给出修正，说明作者有安全意识。
7. **每项技术选型有替代方案分析**——不是"我选了 X 因为我熟悉 X"，而是"X 优于 Y 因为 Z"。

---

## 执行路径

### 步骤 1：回滚修正（优先级顺序，预计总计 6-7 小时）

```
1. RB-6  MVP 范围裁剪            ← 影响最大，先做
2. RB-1  循环依赖修正            ← 编译级阻塞
3. RB-2  ArchUnit 规则重写       ← CI 阻塞
4. RB-4  消息悬挂恢复机制        ← 状态机修正
5. RB-7  竞品补遗               ← 产品论述基础
6. RB-5  协作式取消机制          ← 与 RB-4 关联
7. RB-8  量化验证标准            ← 快速完成
8. RB-3  加密配置最佳实践        ← 快速完成
9. RB-9  市场数据可追溯性        ← 快速完成
```

### 步骤 2：进入 projectScaffold（前置条件检查清单）

- [ ] Maven 模块依赖方向单向无循环（验证：人工审查 POM 依赖）
- [ ] ArchUnit 规则基于实际包名且覆盖全部模块边界（验证：CI 运行通过）
- [ ] MVP Must Have 清单 ∏5 项且与 Maven 模块数一致
- [ ] 状态机包含 ABORTED 终态 + 启动扫描恢复逻辑
- [ ] 竞品分析包含 HolmesGPT / k8sgpt / Grafana AI 条目

### 步骤 3：projectScaffold 中同步处理的 MEDIUM 项

以下 MEDIUM 发现可在创建项目骨架时一并解决：

- M-F4（CHECK 约束）—— 写 DDL 时直接加入
- M-F9（autoExecutionAllowed 移除）—— 写 Entity 时不加此字段
- M-F17（请求体大小限制）—— 写 application.yml 时设置
- M-F13（CSRF 用修正版）—— 写 SecurityConfig 时直接用修正版
- M-F3（SSE 回放内存缓存）—— 实现 SseEventReplayer 时加入 Caffeine 缓存
- M-F1（虚拟线程三项成本）—— 配置 SecurityContext + MDC + HikariCP 时加入对应设置

### 步骤 4：开发阶段早期必须验证的高风险项

| 风险项 | 验证方法 | 时机 | 失败则 |
|--------|---------|------|--------|
| O-2 LangChain4j starter 兼容性 | `mvn dependency:tree` + 冒烟测试 | projectScaffold 完成后立即 | 换方案（只用 Spring AI 或只用 LangChain4j） |
| O-6 金标准诊断准确率 | 构造金标准故障集 + Prompt 实验 | 开发周期前 30% | 若 top-1 < 40%，重新评估项目可行性 |
| D-7 JPA Converter 注入 | `@SpringBootTest` 验证 EncryptedStringConverter 注入成功 | 写第一个 Entity 后 | 调整 Bean 创建顺序 |
| M-F20 LangChain4j Boot 4 starter 存在性 | 验证 Maven Central 实际 artifact 名和版本 | pom.xml 创建前 | 降级到 Boot 3 或纯 LangChain4j（无 starter） |

---

**本报告为 Epiphaneia 项目最终审查文档。用户确认回滚修正完成后，可进入 projectScaffold（脚手架生成）阶段。**
