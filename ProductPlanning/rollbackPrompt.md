# ProductPlanning 阶段回滚提示词

> 本文件是统筹层根据 `ArchitectureDesign/comprehensiveReview.md` 提取的回滚操作指南。
> 请在 ProductPlanning 目录下启动新对话，将本文件内容作为上下文，按指示完成回滚修正。

---

## 使用方式

在新对话中引用本文件，说："请根据本回滚提示词，对 ProductPlanning 阶段文档进行修正。"

---

## 前置阅读

1. `ArchitectureDesign/comprehensiveReview.md` — 全流程综合审查报告（权威来源，特别关注 CRITICAL + HIGH 部分）
2. **`IdeaValidation/rollbackLog.md`** — IdeaValidation 阶段回滚后的级联影响说明（**必须等 IdeaValidation 完成后再读取**）
3. **IdeaValidation 修改后的文件** — 特别是 `mvpScope.md`（Must Have 清单可能已变更）
4. `ProductPlanning/AGENTS.md` — 本阶段行为约束
5. 本阶段全部已有文档（回滚前原文）

---

## 回滚任务清单

### RB-4：增加服务端崩溃后的消息恢复机制（CRITICAL）

**问题**：状态机无 ABORTED 终态。服务端崩溃后，PLANNING/QUERYING/ANALYZING 的 Message 永久悬挂，API 409 永久阻塞该会话。

**修改文件**：`userFlow.md`、`domainModel.md`、`apiDesign.md`、`dbSchema.md`

**修改内容**：

1. **userFlow.md §3 状态机**：
   - 新增 `ABORTED` 终态：`[*] → ABORTED：启动扫描发现未完成 / 超时兜底`
   - ABORTED 用户可见语义："诊断中断（服务器重启或超时）"
   - 所有非终态（CREATED/PLANNING/QUERYING/ANALYZING）→ ABORTED 的迁移路径

2. **domainModel.md §2.2 Message**：
   - DiagnosisState 枚举新增 `ABORTED`
   - Message 新增可选字段 `failureReason: String?`（ABORTED/FAILED 时记录原因）
   - 更新状态机图（与 userFlow 一致）

3. **apiDesign.md §7**：
   - `POST /conversations/{id}/messages` 的 409 场景增加豁免逻辑：
     - "若该会话最后一轮诊断的非终态 Message 已超过 N 分钟（建议 120s 超时 + 30s 缓冲 = 150s），自动将其标记为 ABORTED 并允许新消息"
   - `POST /conversations` 创建新会话时同样检查

4. **dbSchema.md §2.7 message 表**：
   - `diagnosis_state` CHECK 约束新增 `'ABORTED'`
   - 新增列 `failure_reason TEXT`（可选，ABORTED/FAILED 时填入）
   - 不需要迁移策略变更（MVP 无历史数据）

**级联影响（传递给 ArchitectureDesign）**：
- systemArchitecture 的状态机描述需同步
- 应用启动时需要 `@PostConstruct` 扫描逻辑（ArchitectureDesign 落地）

---

### RB-5：增加 Message 的协作式取消机制（与 RB-4 关联）

**问题**：DELETE Conversation/Application 时，进行中的诊断服务端继续消耗 LLM token 但结果被丢弃。

**修改文件**：`domainModel.md`、`apiDesign.md`、`dbSchema.md`

**修改内容**：

1. **domainModel.md §2.2 Message**：
   - 与 RB-4 共享 `failureReason` 字段
   - Message 行为新增：编排循环中每步检查取消标记

2. **apiDesign.md**：
   - `DELETE /conversations/{id}` 行为描述更新：
     - "如有进行中的诊断，先标记取消 → 等待编排循环退出（最多 X 秒）→ CASCADE DELETE"
   - `DELETE /applications/{id}` 同上

3. **dbSchema.md**：与 RB-4 共享 `failure_reason` 列，无需额外修改

---

### 适配 IdeaValidation 回滚的级联影响

**⚠️ 此部分必须在 IdeaValidation 阶段完成后、阅读其 rollbackLog.md 和修改后的 mvpScope.md 之后执行。**

根据 comprehensiveReview RB-6（MVP 范围裁剪），ProductPlanning 需要同步调整：

1. **PRD.md**：
   - §4.1 P0 功能需求：根据 mvpScope 新的 Must Have 清单（5 项），将降级的 FR 移到 P1
   - §4.2/§4.3 P1 重新编号
   - §7 里程碑版本拆分：v0.9 范围缩小，v0.95 吸收原 P0 降级项
   - §6 非功能需求：如果相关功能降级，对应 NFR 标注为"P1 适用"

2. **userFlow.md**：
   - 如果 ELK/日志集成降级为 P1，流程 1 中的 "Querying Elasticsearch" 步骤标注为 `(v0.95)`
   - 如果 SSE 流式降级为 P1，P3 诊断工作台的流式展示改为"完成后一次性展示"

3. **domainModel.md**：
   - 如果扩展接口（Skill/Connector SPI）被砍，§4 集成上下文的 Connector 改为具体实现描述，去掉 SPI 接口定义
   - §6 领域服务接口保留（DiagnosisOrchestrator 仍是领域概念），但标注 "MVP 硬编码实现，v1.0 抽象为 SPI"

4. **apiDesign.md**：
   - 降级的 FR 对应端点标注版本（如 `v0.9 → 501, v0.95 → 实现`）
   - 如果 REST API（FR-8）降级，§1 基础约定改为"API 为 Web UI 内嵌，独立 API 文档推迟到 v0.95"

5. **dbSchema.md**：
   - 如果数据源从 Prometheus+ES 缩减为 Prometheus-only，`data_source.type` 检查约束缩小范围
   - 如果多应用管理降级，`application` 表保留但标注 "v0.9 单应用模式，表结构支持多应用扩展"

---

## 产出物要求

完成以上修正后，在 `ProductPlanning/` 目录下创建 `rollbackLog.md`，记录：

```markdown
# ProductPlanning 回滚修正记录

> 日期：YYYY-MM-DD
> 依据：ArchitectureDesign/comprehensiveReview.md + IdeaValidation/rollbackLog.md

## 处理清单

| 编号 | 项目 | 状态 | 说明 |
|------|------|------|------|
| RB-4 | 消息悬挂恢复机制 | ✅/⚠️/❌ | |
| RB-5 | 协作式取消机制 | ... | |
| CASCADE | 适配 IdeaValidation RB-6 范围裁剪 | ... | 简述改了什么 |

## 修改文件清单

- PRD.md：...
- userFlow.md：...
- domainModel.md：...
- apiDesign.md：...
- dbSchema.md：...

## 对下游的级联影响（传递给 ArchitectureDesign）

- systemArchitecture 状态机需同步 ABORTED 终态
- 启动扫描逻辑需在 ArchitectureDesign 落地
- Maven 模块数可能从 5→2-3
- ...
```

---

## 来自 comprehensiveReview 的相关 MEDIUM 项

以下 MEDIUM 发现可在本阶段修正中一并处理（非强制，提高效率）：

| 编号 | 问题 | 涉及文档 | 建议 |
|------|------|---------|------|
| M-F3 | SSE 重连回放中间步骤丢失 | apiDesign GET /events、systemArchitecture SseEventReplayer | apiDesign 增加事件回放缓存策略描述 |
| M-F4 | dbSchema 缺 CHECK 约束 | dbSchema DDL | 补充 diagnosis_state/evidence.source 等的 CHECK |
| M-F6 | 报告合成性能无指标 | PRD NFR-1、apiDesign GET /report | NFR 中增加报告合成 ≤5s 指标 |
| M-F8 | 清空进行中会话仍消耗 LLM token | 与 RB-5 共享解决方案 | — |
| M-F9 | autoExecutionAllowed 始终 false 是噪音 | domainModel §2.3、dbSchema §2.10 | 移除该字段，v1.5 再引入 |

---

## 注意事项

1. **等 IdeaValidation 完成后再开始** — 必须先读到 IdeaValidation/rollbackLog.md 和修改后的 mvpScope.md
2. **PRD 是调整核心** — 所有其他文档（userFlow/domainModel/apiDesign/dbSchema）围绕 PRD 的 P0/P1 重新对齐
3. **不修改 AGENTS.md 或 CLAUDE.md**
4. **保持 camelCase 文件命名**
5. **有疑问参考 comprehensiveReview.md** — 本提示词是操作摘要
