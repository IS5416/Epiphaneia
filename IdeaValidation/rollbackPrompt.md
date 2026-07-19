# IdeaValidation 阶段修正提示词

> 本文件是统筹层根据 `ArchitectureDesign/comprehensiveReview.md`（统筹层校准版）提取的修正操作指南。
> 判决：go_with_adjustments — 少量修正，不砍架构，不推倒重来。
> 请在 IdeaValidation 目录下启动新对话，将本文件内容作为上下文，按指示完成修正。

---

## 使用方式

在新对话中引用本文件，说："请根据本修正提示词，对 IdeaValidation 阶段文档进行修正。"

---

## 前置阅读

1. `ArchitectureDesign/comprehensiveReview.md` — 全流程综合审查报告（统筹层校准版，权威来源）
2. `IdeaValidation/AGENTS.md` — 本阶段行为约束
3. 本阶段全部已有文档

---

## 修正任务清单

### RB-7：补充遗漏竞品分析（HIGH — 必须修正）

**问题**：竞品分析遗漏了多个重要竞品，H-F1 是唯一被 Phase 2 双重确认的 HIGH 发现。

**修改文件**：`competitorAnalysis.md`

**修改内容**：

1. 在 §2 直接竞品中补充 precious112/Argus 的 GitHub 指标（stars、最近更新时间、贡献者数）
2. 新增竞品条目：
   - **Grafana AI/ML**（🟡 最危险的间接竞品）：已占据用户"监控系统首页"，加入 AI 诊断功能时零用户迁移成本。Epiphaneia 策略应为**共存而非对抗**：诊断结果可嵌入 Grafana Panel/Annotation
   - **HolmesGPT**（🟡 功能 1:1 重叠）：自然语言 → 查数据源 → LLM 诊断 → 报告，需评估其社区规模和成熟度
   - **k8sgpt**（🟢 K8s 特化）：GitHub 5k+ stars，但聚焦 K8s 集群诊断而非通用 Spring Boot 应用
   - **RunWhen Local**（🟢）：自托管故障排查，侧重 K8s
   - **Keep**（🟢）：开源告警管理平台，与 AlertManager 有重叠但非直接诊断竞品
3. 在 §4 竞争优势中增加"Grafana 共存策略"

**级联影响**：
- PRD 产品定位论述可能微调（LOW，可延后）
- README 竞品对比表需同步（LOW，可延后到 ReleaseDeploy）

---

### ADJ-1：MVP 架构保留 + 增加实现深度约束（替代原 RB-6）

**⚠️ 原审查建议"六层砍为三层 + SPI 全砍"已被统筹层驳回。** 驳回理由：架构分层和 SPI 接口定义本身不是范围蔓延，砍掉 SPI 的代价是 v1.0 重构集成层全部调用方式。

**正确做法**：架构完整保留，约束每层 MVP 只做 1 个硬编码实现。

**修改文件**：`mvpScope.md`

**修改内容**（轻量修正，非大幅重写）：

1. **§4.2 架构概要**：保留六层架构图不变。在每个层的说明中增加一句话约束：
   - "MVP 阶段每层仅 1 个硬编码实现——Connector 仅 Prometheus，Skill 仅 Ops Skill，Adapter 仅 Java Adapter。多实现和多后端切换在 v1.0 通过已有 SPI 扩展，无需重构。"
2. **§4.1 技术栈表**：确认保留 Spring AI + LangChain4j（双框架各司其职，Spring AI 管 LLM 调用抽象，LangChain4j 管 Agent 编排——架构已定，不推翻）
3. **§2.1 Must Have**：**不砍任何一项。** 9 项 Must Have 全部保留。每项增加完成标准说明（如："ELK 集成 = 关键词查询 + 错误日志采样，不做 DSL 构建器"）
4. **§6 工作量估算**：确认 PRD 的 6-8 周缓冲估算（原 4-5 周已被 PRD 修正）

**级联影响**：
- PRD §7 里程碑确认 6-8 周（已修正，无需额外修改）
- systemArchitecture 模块数 4±1 不变（已被 ArchitectureDesign 确认）
- **不需要** ProductPlanning 重新排序 P0/P1

---

### ADJ-5：市场数据可追溯性补充（MEDIUM，建议顺手做）

**修改文件**：`marketResearch.md`

**修改内容**：在 §5 数据源中为引用标注来源级别 `[直接引用]` / `[转述自 XX]` / `[作者推算]`。为 TAM 推算中的 10% 假设增加推理链。

**工作量**：30 分钟。不阻塞任何下游。

---

### ADJ-6：关键假设量化验证标准（MEDIUM，建议顺手做）

**修改文件**：`validationConclusion.md`

**修改内容**：在 §6 关键假设中为每条假设补充可度量的验证指标（如 GitHub stars 100+、至少 5 个外部部署实例）。不强制精确阈值——标注"草案值，内测校准"。

**工作量**：20 分钟。

---

## 产出物要求

在 `IdeaValidation/` 目录下创建 `rollbackLog.md`：

```markdown
# IdeaValidation 修正记录

> 日期：YYYY-MM-DD
> 依据：ArchitectureDesign/comprehensiveReview.md（统筹层校准版）

## 处理清单

| 编号 | 项目 | 状态 | 说明 |
|------|------|------|------|
| RB-7 | 竞品补遗 | ✅/⚠️/❌ | |
| ADJ-1 | MVP 深度约束 | ... | 架构保留，仅加实现深度说明 |
| ADJ-5 | 市场数据可追溯性 | ... | 可选 |
| ADJ-6 | 量化验证标准 | ... | 可选 |

## 修改文件清单

- competitorAnalysis.md：...
- mvpScope.md：...
- marketResearch.md：...（可选）
- validationConclusion.md：...（可选）

## 对下游的级联影响

- PRD 无需大规模修改（Must Have 9 项保持，仅确认 6-8 周）
- systemArchitecture 模块数 4±1 不变
- 架构完整保留——ProductPlanning 和 ArchitectureDesign 的核心设计决策未被推翻
```

---

## 注意事项

1. **不要砍架构** — 原 RB-6 的"砍到三层"已被驳回。六层 + SPI 接口定义是 ProductPlanning/ArchitectureDesign 两阶段的共同设计成果
2. **RB-7 是核心产出** — 竞品分析补充是本阶段唯一的 HIGH 项
3. **ADJ-5/6 可选** — 时间不够可以不做，不阻塞 projectScaffold
4. **不修改 AGENTS.md 或 CLAUDE.md**
5. **保持 camelCase 文件命名**
