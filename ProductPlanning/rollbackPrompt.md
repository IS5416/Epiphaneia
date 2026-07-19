# ProductPlanning 阶段修正提示词

> 本文件是统筹层根据 `ArchitectureDesign/comprehensiveReview.md`（统筹层校准版）提取的修正操作指南。
> 判决：go_with_adjustments — 本阶段无强制回滚项。
> 请在 ProductPlanning 目录下启动新对话，将本文件内容作为上下文，按指示完成修正。

---

## 使用方式

在新对话中引用本文件，说："请根据本修正提示词，对 ProductPlanning 阶段文档进行修正。"

---

## 前置阅读

1. `ArchitectureDesign/comprehensiveReview.md` — 全流程综合审查报告（统筹层校准版）
2. **`IdeaValidation/rollbackLog.md`** — IdeaValidation 修正后的级联影响（**等 IdeaValidation 完成后再读**）
3. **IdeaValidation 修改后的文件** — 特别是 `mvpScope.md`（确认架构保留 + 深度约束）
4. `ProductPlanning/AGENTS.md` — 本阶段行为约束
5. 本阶段全部已有文档

---

## 修正任务清单

### 无强制回滚项

经统筹层交叉审查，原 RB-4（消息悬挂 ABORTED）和 RB-5（协作式取消）**已降级为 ADJ-3**——在 Development 阶段编码时处理，不阻塞 projectScaffold。本阶段仅需做文档级补充描述。

### ADJ-3：ABORTED 终态文档补充（HIGH — Development 阶段实现，此处只补文档）

**问题**：服务端崩溃后 PLANNING/QUERYING/ANALYZING 的 Message 永久悬挂。状态机缺 ABORTED 终态。

**⚠️ 本阶段只做文档补充，具体编码在 Development 阶段。**

**修改文件**：`userFlow.md` §3、`domainModel.md` §2.2、`apiDesign.md` §7、`dbSchema.md` §2.7

**修改内容**：

1. **userFlow.md §3 状态机**：
   - 新增 `ABORTED` 终态，用户可见语义："诊断中断（服务器重启或超时）"
   - 所有非终态 → ABORTED 的迁移路径

2. **domainModel.md §2.2 Message**：
   - DiagnosisState 枚举新增 `ABORTED`
   - 新增可选字段 `failureReason: String?`

3. **apiDesign.md §7**：
   - POST /conversations/{id}/messages 的 409 场景增加豁免逻辑描述："若该会话非终态 Message 已超过超时 + 缓冲时间（如 150s），自动标记为 ABORTED 并允许新消息"

4. **dbSchema.md §2.7 message 表**：
   - `diagnosis_state` CHECK 约束新增 `'ABORTED'`
   - 新增 `failure_reason TEXT` 列

**级联影响（传递给 ArchitectureDesign）**：
- systemArchitecture 状态机描述需同步 ABORTED
- Development 阶段需实现 `@PostConstruct` 启动扫描（ArchitectureDesign 在 projectScaffold 无需实现）

---

### 适配 IdeaValidation 修正的级联（轻量）

**⚠️ IdeaValidation 完成后，根据其 rollbackLog.md 确认以下内容：**

1. **PRD.md**：
   - §4.1 P0 功能需求：确认 9 项 Must Have 全部保留（IdeaValidation ADJ-1 不砍功能）
   - §7 里程碑：PRD 已使用 6-8 周缓冲，无需修改
   - 如果 IdeaValidation 在 mvpScope 中对某项功能增加了"完成标准说明"，PRD 对应 FR 同步补充

2. **其他文档**：
   - userFlow / domainModel / apiDesign / dbSchema 的范围和架构均无需因 IdeaValidation 修正而改动
   - 如果 IdeaValidation 竞品分析（RB-7）发现对产品定位有影响，PRD §1 产品定位论述微调

---

## 产出物要求

在 `ProductPlanning/` 目录下创建 `rollbackLog.md`：

```markdown
# ProductPlanning 修正记录

> 日期：YYYY-MM-DD
> 依据：ArchitectureDesign/comprehensiveReview.md（统筹层校准版）+ IdeaValidation/rollbackLog.md

## 处理清单

| 编号 | 项目 | 状态 | 说明 |
|------|------|------|------|
| ADJ-3 | ABORTED 终态文档补充 | ✅/⚠️/❌ | |
| CASCADE | 适配 IdeaValidation 修正 | ... | 如无需改动，注明"无级联影响" |

## 修改文件清单

- userFlow.md：...
- domainModel.md：...
- apiDesign.md：...
- dbSchema.md：...

## 对下游的级联影响（传递给 ArchitectureDesign）

- systemArchitecture 状态机需同步 ABORTED 终态（ArchitectureDesign 负责）
- Development 阶段需实现启动扫描（ArchitectureDesign 无需实现，记录在 projectScaffold 的技术债务表即可）
```

---

## 注意事项

1. **本阶段是最轻松的** — 没有强制回滚项，只补文档
2. **等 IdeaValidation 完成** — 读完 rollbackLog 再开始，确认架构保留策略
3. **ADJ-3 只补文档不写代码** — ABORTED 的编码实现在 Development 阶段
4. **不修改 AGENTS.md 或 CLAUDE.md**
5. **保持 camelCase 文件命名**
