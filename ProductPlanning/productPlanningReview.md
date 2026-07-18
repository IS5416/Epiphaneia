# ProductPlanning 阶段内部审查

> 审查时间：2026-07-18
> 审查范围：PRD.md、userFlow.md、domainModel.md、architectureDesignHandoff.md（当前版本）
> 结论：通过。三文档内部一致，可以进入 apiDesign + dbSchema。

---

## 一致性验证

| 检查项 | 结果 |
|--------|------|
| PRD FR-1~12 ↔ userFlow 全部路径 ↔ domainModel 聚合/实体/状态机 | ✅ 一致 |
| architectureDesignHandoff 决策 ↔ domainModel §10 映射 | ✅ 覆盖 |
| 无 Report 表（PRD FR-5）= 无 Report 聚合（domainModel §2.1）= 无 report CRUD（userFlow P4） | ✅ 三文档对齐 |
| 状态机（userFlow §3）= Message.diagnosisState（domainModel §2.2） | ✅ 7 状态 + 迁移规则一致 |
| 统一语言表覆盖全部 PRD 功能 | ✅ |
| 仅读不写安全边界在 FR-5/FR-10/domainModel FixSuggestion/architectureDesignHandoff 1.6 多处落地 | ✅ |

---

## 小修建议（不阻塞，apiDesign/dbSchema 阶段处理）

### 1. FixSuggestion 字段语义重叠

domainModel 中 `FixSuggestion.complexity` 和 `FixSuggestion.isSafe` 在 MVP 阶段语义重叠——两者都表达"不可自动执行"。MVP 所有修复建议均为需人类确认。

**建议**：apiDesign 阶段考虑合并为单一字段 `autoExecutionAllowed: boolean`（MVP 一律 `false`），避免两个字段表达同一件事。v1.5 自动修复功能引入时再重新建立 complexity 分级。

### 2. 历史会话搜索方案确认

userFlow P5 历史会话的关键词过滤写为 `LIKE 级`。dbSchema 阶段需确认 PostgreSQL `ILIKE` + `pg_trgm` 三元组索引足够支撑 v1.0 前的搜索需求，不必在 MVP 引入全文索引。

**建议**：dbSchema 中 `conversation` 表的 `title` 字段加 `pg_trgm` GIN 索引，v1.0 FR-16 知识库引入时再评估是否升级到 `tsvector`。

### 3. Connector DataSourceType 扩展性

PRD OQ-1 已决策日志后端首发 Elasticsearch、Loki 走 SPI。但 domainModel 中 `DataSourceType` enum 目前仅 `PROMETHEUS | ELASTICSEARCH | ACTUATOR`。Loki 走 SPI 后这个枚举要扩展——每次加数据源改领域枚举是反模式。

**建议**：ArchitectureDesign 阶段 Connector SPI 设计时考虑动态注册机制（如基于 `DataSource.type` 字符串匹配），并非每次新增数据源都要改领域层枚举。enum 可保留为常用类型的快捷常量，但 ConnectorRegistry 的匹配逻辑走 type 字符串而非 switch-case 枚举。

---

## 文档间引用完整性

| 源文档 | 引用目标 | 状态 |
|--------|---------|------|
| PRD §11 | architectureDesignHandoff | ✅ 指针正确 |
| PRD §10 | userFlow.md | ✅ |
| userFlow §3 | domainModel/dbSchema | ✅ 明确声明为下游依据 |
| userFlow §4 | apiDesign.md | ✅ 供细化 |
| domainModel §10 | architectureDesignHandoff | ✅ 逐项映射 |
| domainModel 末尾 | apiDesign.md | ✅ 聚合根 → REST 资源 |

---

> 本文档供 ProductPlanning 阶段内部使用——apiDesign 和 dbSchema 编写时对照处理上述建议项。阶段结束时随其他产出物一并交付 ArchitectureDesign。
