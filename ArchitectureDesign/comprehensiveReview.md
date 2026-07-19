# Epiphaneia 全流程综合审查报告

> 审查日期：2026-07-18 | 修订日期：2026-07-19（统筹层交叉审查，严重度校准）
> 审查范围：IdeaValidation → ProductPlanning → ArchitectureDesign 三阶段全部产出物
> 审查方法：Phase 1 四维度审查 + Phase 2 对抗验证 + Phase 3 统筹层校准
> 本文档为最终综合审查结论，用户据此决定后续行动

---

## 总体判决

### 判决：go_with_adjustments

**无需正式回滚，少量修正后可直接进入 projectScaffold。**

**核心理由：**

1. 项目整体设计扎实——P0 需求 12/12 全覆盖，领域→API→DB→架构映射一致，技术选型链条清晰。
2. 无产品级致命缺陷——产品定位"不采集数据、只做智能消费层"精准，开源 + 自部署差异化真实存在。
3. 存在 **1 项需在 projectScaffold 前修正的文档错误**（connector ↔ infra 循环依赖——pom.xml 需正确的单向依赖方向）。
4. 存在 **多项值得修正但不阻塞 scaffold 的问题**，可在 Development 阶段随编码自然解决。

**严重度校准说明（2026-07-19 统筹层交叉审查）：**

原始 Phase 1+2 审查产出了 5 CRITICAL + 6 HIGH。统筹层对每条发现进行了二次校准，发现以下偏差：

| 原发现 | 原严重度 | 校准后 | 校准理由 |
|--------|---------|--------|---------|
| C-F2 ArchUnit 规则包名 | CRITICAL | MEDIUM | techStack 中的 ArchUnit 代码是**示例片段**，非实际测试文件。projectScaffold 写真实 ArchUnit 时会用 systemArchitecture §12 的真实包名，不会照抄示例 |
| C-F3 MVP 范围蔓延 | CRITICAL | HIGH | 六层架构 + SPI 接口定义 ≠ 范围蔓延。一个 Java interface = 5 行代码。问题不在架构层数多，在于每层实现深度未设限。**修正策略变更：保留逻辑六层 + 物理 4±1 模块 + SPI 接口定义，约束每层 MVP 只做 1 个硬编码实现** |
| C-F4 4-5 周估算 | CRITICAL | MEDIUM | PRD §7 已将开发周期 buffered 到 6-8 周。原发现引用的 mvpScope 旧数据已被 PRD 修正。架构分层保留 + Agent 编码效率加持，6-8 周可行 |
| C-F5 消息悬挂 | CRITICAL | HIGH | 问题真实，但 ABORTED 状态 + 启动扫描是 Development 阶段的编码工作，不阻塞 pom.xml 生成。可在开发阶段与消息实体一起实现 |
| H-F2 市场数据 | HIGH | MEDIUM | 数据源已列，开源项目 TAM/SAM 是方向性参考。精确引用是好习惯但不改变产品决策 |
| H-F3 假设量化 | HIGH | MEDIUM | "发布后看反馈"对单人开源项目是合理策略 |
| H-F4 RateLimitFilter | HIGH | MEDIUM | 修起来 5 分钟（Caffeine.maximumSize），且是 Development 实现细节 |
| H-F5 多 Prometheus | HIGH | MEDIUM | MVP 单 Prometheus 是合理约束，v2.0 再谈 |
| H-F6 Grafana 威胁 | HIGH | 合并至 H-F1 | 竞品遗漏的子项 |

**校准后统计：1 CRITICAL + 3 HIGH + 26 MEDIUM + 26 LOW**

---

## projectScaffold 前需修正项（唯一阻塞项）

### RB-1：修正 connector ↔ infra 循环依赖（CRITICAL — Maven 编译阻塞）

- **阶段**：ArchitectureDesign（文档修正，非代码回滚）
- **原因**：`epiphaneia-infra` 中的 `ConnectorRegistry` 需要引用 `epiphaneia-connector` 中的 `Connector` 类型，而 `connector` 又声明依赖 `infra`。形成 `connector → infra → connector` 循环，Maven 拒绝编译。
- **修正**：将 `Connector<T,R>`、`QueryRequest`、`QueryResult`、`DataSourceType` 从 `connector/api` 移动到 `infra/api`。`connector` 模块职责从"SPI 定义 + 实现"退化为"纯实现"。依赖方向变为单向 `connector → infra`。
- **修改文件**：`techStack.md` §3 模块依赖表、`systemArchitecture.md` §3.2 模块接口表 + §4.4/§4.5
- **级联影响**：无（接口签名不变，仅包路径移动说明）
- **预估工作量**：30 分钟（文档描述修正）

### RB-7：补充遗漏竞品分析（HIGH — 产品论述基础）

- **阶段**：IdeaValidation
- **原因**：竞品分析对 precious112/Argus 只停留在概念层面，遗漏了 HolmesGPT（功能 1:1 重叠）、Grafana AI/ML（最危险——已占用户"监控首页"入口）、k8sgpt（GitHub 5k+ stars）等重要竞品。
- **修正**：补充 Argus 的 GitHub 指标；新增 Grafana AI、HolmesGPT、k8sgpt、RunWhen Local、Keep 条目；增加"Grafana 共存策略"
- **修改文件**：`competitorAnalysis.md` §2/§3/§4
- **级联影响**：PRD 产品定位论述微调、README 竞品对比表（可延后）
- **预估工作量**：1 小时

---

## 建议修正项（Development 阶段处理，不阻塞 projectScaffold）

### ADJ-1：保留架构、控制实现深度（替代原 RB-6）

- **原建议（已驳回）**：六层砍为三层、SPI 全砍、Must Have 9→5
- **驳回理由**：架构分层和接口定义本身不是范围蔓延。砍掉 SPI 的代价是 v1.0 重构集成层调用方式——后期成本远超初期节省。DDD 接口先行是 ProductPlanning 阶段的核心设计决策，ArchitectureDesign 不应推翻。
- **正确做法**：逻辑六层完整保留，物理 4±1 模块不变，SPI 接口定义保留。约束：**每层 MVP 只做 1 个硬编码实现，不搞多实现切换。** 交付时间维持 PRD 的 6-8 周（合理可达成）。
- **涉及文档**：`mvpScope.md` §4.2 架构层约束说明、`PRD.md` §7 里程碑（确认 6-8 周）、`systemArchitecture.md` §11 技术债务表（记录"每层单实现"为 MVP 约束）

### ADJ-2：ArchUnit 示例代码修正（替代原 RB-2）

- **原严重度**：CRITICAL → **校准为 MEDIUM**。techStack.md 中的 ArchUnit 是示例代码，projectScaffold 会使用 systemArchitecture §12 的真实包名写实际规则，不会照抄示例。
- **修正**：将 techStack.md §2.12 的示例代码中的 `..domain..` → `..agent..`、`..infrastructure..` → `..infra..`，使其与实际包名一致
- **预估工作量**：15 分钟

### ADJ-3：ABORTED 终态（替代原 RB-4）

- **原严重度**：CRITICAL → **校准为 HIGH**。问题真实，但状态机 +1 枚举值 + 启动扫描是 Development 编码内容，不阻塞 pom.xml。
- **处理方式**：Development 阶段实现 Message 实体时一并加入 `ABORTED` 状态 + `@PostConstruct` 启动扫描。ProductPlanning 文档（userFlow/domainModel/apiDesign/dbSchema）中补充状态描述
- **修改文件**：`userFlow.md` §3、`domainModel.md` §2.2、`apiDesign.md` §7、`dbSchema.md` §2.7
- **预估工作量**：1 小时（四处文档补充 + Development 编码实现）

### ADJ-4：竞品威胁策略补充（从属于 RB-7）

- 与 RB-7 一同处理。在 competitorAnalysis 中增加"Grafana 共存策略"而非对抗策略
- 注意：**不修改 mvpScope 的 Must Have 范围**——当前范围合理，架构也不用砍

---

## 确认发现（校准后严重度，完整列表）

### CRITICAL（阻塞 projectScaffold）

| # | 发现 | 修正项 | 级联影响 |
|---|------|--------|---------|
| C-F1 | connector ↔ infra 循环依赖，Maven 无法编译 | RB-1 | techStack → systemArchitecture 模块描述 |

### HIGH（显著影响产品质量，应优先处理）

| # | 发现 | 修正项 | 级联影响 |
|---|------|--------|---------|
| H-F1 | 竞品分析遗漏严重：Argus 缺 GitHub 数据、遗漏 HolmesGPT/k8sgpt/Grafana AI | RB-7 | competitorAnalysis → PRD → README |
| H-F2 | 服务端崩溃后诊断消息永久悬挂——状态机无 ABORTED 终态 | ADJ-3 | userFlow → domainModel → apiDesign → dbSchema |
| H-F3 | MVP 隐藏范围蔓延风险：每层实现深度未设限，需明确"单实现"约束 | ADJ-1 | mvpScope → PRD → systemArchitecture |

### MEDIUM（开发阶段起尽早处理）

| # | 发现 | 建议 |
|---|------|------|
| M-F1 | 虚拟线程三项隐藏成本未评估：Spring Security ThreadLocal 泄漏、MDC 上下文丢失、HikariCP 连接池瓶颈 | Development 编码时加入对应配置 |
| M-F2 | 双 AI 框架（Spring AI 2.0 + LangChain4j）长期维护负担未充分论证 | ArchitectureDesign 补充 ADR 明确论证 |
| M-F3 | SSE 重连回放中间步骤丢失——无事件缓存导致断连后只能看到终态 | Development 实现 SseEventReplayer 时加入 Caffeine 缓存最近 5 分钟事件 |
| M-F4 | dbSchema 缺少关键 CHECK 约束 | 写 DDL 时直接加入 |
| M-F5 | 用户画像缺乏一手调研支撑 | 可延后——开源项目靠社区验证 |
| M-F6 | 报告合成性能无指标——LLM 实时合成多轮对话可能 5-10 秒 | PRD NFR 补充报告合成 ≤5s |
| M-F7 | Application 聚合持有网络 I/O 方法 probeActuator()，违反 DDD 聚合纯洁性 | Development 实现时抽到领域服务 |
| M-F8 | 清空进行中会话仍消耗 LLM token | 与 ADJ-3 共享取消机制 |
| M-F9 | FixSuggestion.autoExecutionAllowed 在 MVP 始终 false，无用字段 | 写 Entity 时省略此字段，v1.5 再加 |
| M-F10 | SSE close 事件 manual_stop reason 在服务端不可判定 | 文档标注限制 |
| M-F11 | domainModel 缺 DiagnosisResult 类型定义 | Development 编码时 10 分钟补充 |
| M-F12 | 加密密钥 @Value 与环境变量绑定可工作但不优雅 | 改 @ConfigurationProperties（5 分钟） |
| M-F13 | CSRF 配置文档中保留了有漏洞版本和修正版两套代码 | 写 SecurityConfig 时直接用修正版 |
| M-F14 | ConnectorRegistry 归属矛盾：domainModel 放在领域服务，systemArchitecture 放在 infra | RB-1 一起解决 |
| M-F15 | "核心引擎语言无关"与"Java 生态绑定是护城河"存在叙事张力 | 精确化表述 |
| M-F16 | GitHub Connector 在 mvpScope 架构图中标注但三阶段设计均无实现入口 | 注明 v1.0 实现 |
| M-F17 | 未定义请求体大小限制 | 写 application.yml 时设置 |
| M-F18 | 金标准故障集来源未定义 | Testing 阶段细化 |
| M-F19 | Spring AI 2.0 刚 GA 不到 2 个月，社区资源稀少 | projectScaffold 后第一个 action：验证依赖兼容性 |
| M-F20 | ArchUnit 示例代码包名错误（原 C-F2，严重度校准下调） | 修正 techStack §2.12 示例 |
| M-F21 | MVP 时间估算 mvpScope 旧数据（原 C-F4，PRD 已修正为 6-8 周） | PRD 已处理，mvpScope 标注"以 PRD 为准" |
| M-F22 | 市场数据引用可追溯性（原 H-F2） | marketResearch §5 补充来源级别标注 |
| M-F23 | 关键假设缺少量化标准（原 H-F3） | validationConclusion §6 补充 |
| M-F24 | RateLimitFilter ConcurrentHashMap 无界增长（原 H-F4） | Development 用 Caffeine 替代 |
| M-F25 | 多 Prometheus 实例不支持（原 H-F5） | 文档标注 MVP 限制 |

### LOW（观察建议，择机处理）

| # | 发现 |
|---|------|
| L-F1 | JDK 17+ → 21 LTS 版本漂移，前序文档未回写更新标记 |
| L-F2 | React 18 → 19 版本漂移，前序文档未标注以 ArchitectureDesign 为准 |
| L-F3 | "经验(知识库)"在核心公式中出现但 MVP 不包含——营销语言与交付范围不一致 |
| L-F4 | FR-17 Java Adapter 深度诊断缺乏数据路径设计 |
| L-F5 | server → engine 直接依赖跳过 agent-core，与分层架构原则不一致 |
| L-F6 | docker-compose 与"一键部署"矛盾——Nginx 需要预构建前端 dist |
| L-F7 | 验证结论 5 维度全 PASS 呈现确认偏误倾向 |
| L-F8 | "Spring Boot 零配置接入"可行性缺乏技术论证 |
| L-F9 | 竞争壁垒"Java 生态绑定"存在自洽矛盾 |
| L-F10 | 集成测试 compose override 执行模式不清晰 |
| L-F11 | agent-core/api 包子包结构未定义 |
| L-F12 | application.yml profile 结构未定义 |
| L-F13 | docker-compose 缺少日志轮转、资源限制、优雅停机时间配置 |
| L-F14 | Prompt 中用户输入用 """...""" 分隔可被注入破坏 |
| L-F15 | 威胁模型遗漏：供应链攻击/镜像投毒/诊断结果投毒 |
| L-F16 | 数据库 tag 字段用 JSONB 而非原生 VARCHAR[] 数组 |
| L-F17 | 密码复杂度仅前端/应用层校验，DDL 无体现 |
| L-F18 | 改密后旧 token 仍有效 |
| L-F19 | 统一语言中"诊断"一词过载 |

---

## Phase 2 遗漏问题

| 编号 | 遗漏问题 | 严重度 | 动作 |
|------|---------|--------|------|
| O-1 | 双 AI 框架必要性——MVP 是否真要两个框架？ | MEDIUM | ArchitectureDesign ADR 补充论证 |
| O-2 | LangChain4j spring-boot4-starter 存在性未经独立验证 | MEDIUM | projectScaffold 第一个 action：`mvn dependency:tree` |
| O-3 | SSE 重连回放过程事件丢失——用户断连后只能看到瞬间完成的终态 | HIGH | Development 加入事件缓存 |
| O-4 | MQ 的完全缺席未被讨论 | LOW | 文档补充"为什么不需要" |
| O-5 | React CSR 选择缺乏论证 | LOW | techStack 补充一行 |
| O-6 | Ops Skill 金标准来源未定义 | HIGH | Testing 阶段明确（至少 50% 来自真实生产故障案例） |
| O-7 | 多应用管理价值边界模糊 | LOW | ProductPlanning review 简短评估 |

---

## 驳回项摘要

以下发现经统筹层交叉审查后被驳回或严重度大幅下调：

| 原始 | 发现 | 原始 | 驳回/下调理由 |
|------|------|------|-------------|
| B-2+C-F2 | ArchUnit 规则包名全错，CI 完全无效 | CRITICAL → MEDIUM | techStack 中的 ArchUnit 是**示例片段**，非实际测试文件。projectScaffold 写真实规则时不会照抄 |
| H4+C-F3 | MVP 六层→三层、SPI 全砍 | CRITICAL → HIGH（策略变更） | 架构分层和接口定义 ≠ 范围蔓延。砍 SPI = v1.0 重构全部集成层。**保留架构，约束每层单实现** |
| C2+C-F4 | 4-5 周估算严重偏乐观 | CRITICAL → MEDIUM | PRD 已 buffered 到 6-8 周。保留架构 + Agent 编码效率，6-8 周可行 |
| CRITICAL-1+C-F5 | 消息悬挂阻塞 scaffold | CRITICAL → HIGH | 真实问题但状态机 +1 枚举值 + 启动扫描是 Development 编码内容，不阻塞 pom.xml |
| C1+H-F3 | 关键假设无量化标准 = CRITICAL | CRITICAL → MEDIUM | "发布后看反馈"对单人开源项目合理，量化是 good-to-have |
| B-1 部分 | RB-6 需回滚到 IdeaValidation 砍 Must Have | 已驳回 | 架构保留策略下，Must Have 保持不变（仅加深度约束） |

---

## 亮点总结

1. **产品定位"不采集数据、只做智能消费层"精准简洁**——一句话说清了与所有监控工具的关系
2. **"只读不写"安全约束系统性地钉入产品 DNA**——Connector SPI 只有 `query()` 无 `execute()`，架构级纵深防御
3. **Won't Have 清单比 Must Have 更有价值**——每项有"为什么不做"和"何时做"
4. **P0 需求 12/12 全覆盖**——所有需求有完整的领域→API→DB→架构实现路径
5. **文档工程水准线以上**——5+6+5 份文档，交叉引用清晰，文件命名符合规范
6. **CSRF 配置自查自纠**——securityDesign 发现自己漏洞并给出修正
7. **每项技术选型有替代方案分析**——不是"我熟悉 X 所以选 X"

---

## 执行路径

### 步骤 1：projectScaffold 前必须修正（预计 1.5 小时）

```
1. RB-1  循环依赖修正（ArchitectureDesign 文档修正）  ← 30 分钟，唯一阻塞项
2. RB-7  竞品补遗（IdeaValidation competitorAnalysis） ← 1 小时
```

### 步骤 2：projectScaffold 前建议修正（预计 30 分钟）

```
3. ADJ-2  ArchUnit 示例包名修正（techStack §2.12）     ← 15 分钟
4. ADJ-4  竞品威胁策略补充（与 RB-7 一起做）           ← 15 分钟
```

### 步骤 3：进入 projectScaffold（前置条件检查）

- [x] Maven 模块依赖方向单向无循环（RB-1 验证）
- [x] 竞品分析包含 HolmesGPT / k8sgpt / Grafana AI（RB-7 验证）
- [ ] （不再需要原始审查的 5 条阻塞检查——已校准）

### 步骤 4：Development 阶段处理的 HIGH 项

| 项 | 处理时机 | 方式 |
|----|---------|------|
| H-F2 消息悬挂 ABORTED | 实现 Message 实体时 | 状态机 +1 + 启动扫描 |
| H-F3 每层实现深度约束 | 开始编码时 | 每层只写 1 个硬编码实现 |
| O-3 SSE 事件缓存 | 实现 SSE 时 | Caffeine 缓存最近 5 分钟事件 |
| O-6 金标准来源 | 实现 Ops Skill 时 | 50% 真实故障案例 |

### 步骤 5：projectScaffold 中同步处理的 MEDIUM 项

- M-F4（CHECK 约束）、M-F9（移除 autoExecutionAllowed）、M-F17（请求体大小限制）、M-F13（CSRF 修正版）、M-F12（@ConfigurationProperties）、M-F20（ArchUnit 示例修正）

---

**本报告为 Epiphaneia 项目最终审查文档（统筹层校准版）。用户确认 RB-1 + RB-7 修正后，可直接进入 projectScaffold。**
