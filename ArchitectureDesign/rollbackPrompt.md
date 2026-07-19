# ArchitectureDesign 阶段修正提示词

> 本文件是统筹层根据 `comprehensiveReview.md`（统筹层校准版）提取的修正操作指南。
> 判决：go_with_adjustments — 1 项必须修正 + 少量建议修正。
> 请在 ArchitectureDesign 目录下启动新对话，将本文件内容作为上下文，按指示完成修正。

---

## 使用方式

在新对话中引用本文件，说："请根据本修正提示词，对 ArchitectureDesign 阶段文档进行修正。"

---

## 前置阅读

1. `ArchitectureDesign/comprehensiveReview.md` — 全流程综合审查报告（统筹层校准版，权威来源）
2. **`IdeaValidation/rollbackLog.md`** — IdeaValidation 修正后的级联影响（**等 IdeaValidation 完成**）
3. **`ProductPlanning/rollbackLog.md`** — ProductPlanning 修正后的级联影响（**等 ProductPlanning 完成**）
4. **前两阶段修改后的文件** — 特别是 `mvpScope.md`（确认架构保留）、`userFlow.md`（确认 ABORTED 终态）
5. `ArchitectureDesign/AGENTS.md` — 本阶段行为约束
6. 本阶段全部已有文档：techStack.md、systemArchitecture.md、securityDesign.md、deploymentArchitecture.md

---

## 必须修正（阻塞 projectScaffold）

### RB-1：修正 connector ↔ infra 循环依赖（CRITICAL — Maven 编译阻塞）

**问题**：`epiphaneia-infra` 中的 `ConnectorRegistry` 引用 `epiphaneia-connector` 中的 `Connector` 类型，而 `connector` 又声明依赖 `infra`。形成 `connector → infra → connector` 循环，Maven 拒绝编译。

**这是整个审查中唯一真正的编译级阻塞项。**

**修改文件**：`techStack.md` §3、`systemArchitecture.md` §3.2/§4.4/§4.5

**修改内容**：

1. **techStack.md §3 模块依赖表**：
   - 将 `Connector<T,R>`、`QueryRequest`、`QueryResult`、`DataSourceType` 常量从 `epiphaneia-connector/api` 移动到 `epiphaneia-infra/api`
   - `ConnectorRegistry` 接口保留在 `infra/api`（本来就是）
   - `connector` 模块职责从"SPI 定义 + 实现"退化为"纯实现"（仅含 PrometheusConnector、ElasticsearchConnector 的 `@Component` 实现类）
   - 新依赖方向：`connector → infra`（单向）、`engine → infra`、`agent-core → infra`
   - 禁止 `infra → connector` 的任何依赖（infra 不 import connector 的任何类）

2. **systemArchitecture.md §3.2 模块接口表**：
   - `epiphaneia-infra` 的 public API 包新增：`Connector<T,R>` SPI 接口、`QueryRequest`、`QueryResult`、`DataSourceType` 常量
   - `epiphaneia-connector` 的 public API 包：无（纯实现模块，不对外暴露接口）

3. **systemArchitecture.md §4.4 扩展点架构**：
   - Connector SPI 图更新：SPI 接口定义在 infra，具体实现在 connector
   - 新增 Connector 步骤："实现 `Connector<T,R>` 接口（`io.epiphaneia.infra.api`）→ `@Component` → 自动注册"

4. **systemArchitecture.md §4.5 ConnectorRegistry**：
   - `ConnectorRegistry` 和 `ConnectorRegistryImpl` 同属 infra 模块。`impl` 中 `import ...connector.internal.PrometheusConnector` 是 infra → connector 方向——**此方向正确**（infra 作为共享基础设施可以依赖具体实现模块来做 Bean 收集）

**预估工作量**：30 分钟（文档路径描述修正）

---

## 建议修正（提升质量，不阻塞 scaffold）

### ADJ-2：ArchUnit 示例代码包名修正（MEDIUM）

**⚠️ 原审查标注为 CRITICAL，已被统筹层校准为 MEDIUM。** techStack §2.12 的 ArchUnit 代码是**示例片段**，projectScaffold 时开发者会根据 systemArchitecture §12 的真实包结构写实际规则，不会照抄示例。

**修改文件**：`techStack.md` §2.12

**修改内容**：将示例代码中的 `..domain..` → `..agent..`、`..infrastructure..` → `..infra..`，使示例与项目实际包名一致。

**预估工作量**：15 分钟

### ADJ-7：M-F14 ConnectorRegistry 归属矛盾（MEDIUM，与 RB-1 一起解决）

domainModel §6.3 将 ConnectorRegistry 放在领域服务中，但 systemArchitecture §4.5 将其放在 infra 模块。RB-1 已明确 ConnectorRegistry 在 infra——domainModel 的措辞需同步。

**修改文件**：`domainModel.md` §6.3（本应在 ProductPlanning 阶段，但矛盾点是 ArchitectureDesign 文档发现的，在此一并修正）

**修改内容**：ConnectorRegistry 说明从"领域服务"改为"基础设施层注册表，接口定义在领域层（infra/api），实现在基础设施层（infra/internal）"

---

## 适配上游回滚的级联影响

**⚠️ 此部分必须在前两阶段完成后、阅读它们的 rollbackLog.md 之后执行。**

### 适配 IdeaValidation 修正

IdeaValidation 执行的是 ADJ-1（架构保留 + 深度约束），**对本阶段无破坏性级联**：

1. **techStack.md §3**：模块数 4±1 不变（架构未被推翻）
2. **systemArchitecture.md §2 逻辑分层**：六层不变
3. **systemArchitecture.md §3 物理映射**：5 模块不变
4. 如果 IdeaValidation 竞品补遗（RB-7）产生了对产品定位的修正，可选更新 ADR

### 适配 ProductPlanning 修正

ProductPlanning 执行的是 ADJ-3（ABORTED 终态文档补充），需要同步：

1. **systemArchitecture.md**：
   - §5.1 诊断请求路径中的状态图：确认与 userFlow/domainModel 的 ABORTED 终态一致
   - §11 技术债务表：新增一条"ABORTED 终态 + 启动扫描（Development 阶段实现）"

2. **deploymentArchitecture.md**：无需修改（启动扫描不影响部署拓扑）

---

## 产出物要求

在 `ArchitectureDesign/` 目录下创建 `rollbackLog.md`：

```markdown
# ArchitectureDesign 修正记录

> 日期：YYYY-MM-DD
> 依据：comprehensiveReview.md（统筹层校准版）+ IdeaValidation/rollbackLog.md + ProductPlanning/rollbackLog.md

## 自身修正

| 编号 | 项目 | 状态 | 说明 |
|------|------|------|------|
| RB-1 | 循环依赖修正 | ✅/⚠️/❌ | |
| ADJ-2 | ArchUnit 示例包名 | ... | |
| ADJ-7 | ConnectorRegistry 归属 | ... | |

## 适配上游级联

| 来源 | 适配内容 | 状态 |
|------|---------|------|
| IdeaValidation ADJ-1 | 确认架构保留，无级联影响 | ✅ 确认 |
| ProductPlanning ADJ-3 | ABORTED 终态同步 | ✅/⚠️/❌ |

## 修改文件清单

- techStack.md：...
- systemArchitecture.md：...
- （可选）domainModel.md：...

## projectScaffold 就绪检查

- [x] Maven 模块依赖方向单向无循环（RB-1 验证：connector → infra 单向）
- [x] 架构逻辑六层 + 物理 4±1 模块保留（ADJ-1 确认）
- [x] 状态机含 ABORTED 终态描述（ADJ-3 同步）
- [x] ArchUnit 示例与实际包名一致（ADJ-2）
```

---

## 注意事项

1. **等 IdeaValidation 和 ProductPlanning 都完成后再开始** — 本阶段最后执行
2. **RB-1 是唯一阻塞项** — 修完就可以进 projectScaffold
3. **架构完整保留** — 六层逻辑 + 4±1 物理模块 + SPI 接口定义全保留，不做结构性变更
4. **ADJ-2 顺手做** — 15 分钟，不改白不改
5. **不修改 AGENTS.md 或 CLAUDE.md**
6. **projectScaffold 就绪检查** — 修正完成后对照清单逐项确认
