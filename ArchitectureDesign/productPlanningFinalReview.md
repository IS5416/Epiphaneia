# ProductPlanning 阶段最终审查

> 审查时间：2026-07-18
> 审查范围：ProductPlanning 阶段全部七份产出物
> 结论：🟢 通过。全部产出物完整，三核心文档（PRD/domainModel/apiDesign/dbSchema）一致，准入 ArchitectureDesign。

---

## 产出物清单

| 文件 | 状态 | 说明 |
|------|------|------|
| PRD.md | ✅ | v1.0，已评审通过 |
| userFlow.md | ✅ | v1.0-draft，7 页面 + 7 状态机 + 4 SSE 事件 |
| domainModel.md | ✅ | v1.0-draft，4 限界上下文 + 统一语言 + 领域事件 |
| apiDesign.md | ✅ | v1.0-draft，8 资源组 + SSE 规范 + 验收清单 |
| dbSchema.md | ✅ | v1.0-draft，10 表 + DDL 骨架 + 索引策略 |
| architectureDesignHandoff.md | ✅ | 活文档，已定稿 |
| productPlanningReview.md | ✅ | 阶段内审查（统筹层 2026-07-18） |

---

## 审查建议处理情况

| # | 建议 | 状态 |
|---|------|------|
| 1 | FixSuggestion 字段合并 | ✅ dbSchema `fix_suggestion` 使用单一 `auto_execution_allowed` 字段（apiDesign §6 GET 响应同）；domainModel 原文已更新一致 |
| 2 | pg_trgm 索引 | ✅ dbSchema `conversation` 表含 `idx_conversation_title_trgm` GIN 索引；apiDesign §7 GET /conversations `q` 参数标注 ILIKE + pg_trgm |
| 3 | DataSourceType 扩展性 | ✅ dbSchema `data_source.type` 用 VARCHAR(50) 而非 PostgreSQL enum；apiDesign §5 支持字符串 type 动态匹配。架构阶段 Connector SPI 设计时注意动态注册，不作为硬性约束在此处重复 |

---

## 三文档内部一致性验证

| 检查项 | 结果 |
|--------|------|
| PRD 功能 → apiDesign 端点 | ✅ FR-1~12 均有对应端点 |
| PRD 功能 → dbSchema 表 | ✅ 全部功能有数据支撑 |
| domainModel 聚合 → apiDesign REST 资源 | ✅ Conversation/Application/DataSource/Admin 一一对应 |
| domainModel 聚合/实体 → dbSchema 表 | ✅ 值对象扁平化或独立表，映射见 dbSchema §9 |
| userFlow 状态机 → apiDesign SSE 事件 | ✅ 7 状态 + `state`/`step`/`token`/`done`/`error`/`close` |
| apiDesign 端点 → dbSchema 索引 | ✅ 每个业务索引有对应查询路径 |
| 无 Report 表（三文档对齐） | ✅ PRD FR-5 = domainModel §2.1 = dbSchema 无表 = apiDesign 仅 GET /conversations/{id}/report（实时合成） |
| 凭证不回显（安全） | ✅ apiDesign §5/§6 GET 响应不含 authConfig/apiKey；dbSchema §4 加密存储 |
| "仅读不写"安全边界 | ✅ FR-5/FR-10/NFR-2/domainModel FixSuggestion/architectureDesignHandoff 1.6 多处落地 |

---

## 交付 ArchitectureDesign 的输入总览

ArchitectureDesign 阶段启动时应加载以下文件：

| 输入 | 来源 | 用途 |
|------|------|------|
| PRD | ProductPlanning | 功能规格、NFR、里程碑——架构设计的业务需求输入 |
| domainModel | ProductPlanning | 限界上下文、聚合根、领域服务接口——模块划分依据 |
| apiDesign | ProductPlanning | REST 契约、SSE 规范——Spring MVC 路由与流式输出方案决策依据 |
| dbSchema | ProductPlanning | 表结构、索引策略、DDL 骨架——数据库选型确认与 Flyway/Liquibase 方案 |
| architectureDesignHandoff | ProductPlanning | 预先决策（JDK21/缓存/Agent层/分层/Web技术未决项）——架构阶段确认并落地 |
| productPlanningFinalReview | ArchitectureDesign | 本文档——审查结论与未决项 |

---

## 留给 ArchitectureDesign 的未决项

| # | 未决项 | 来源 | 说明 |
|---|--------|------|------|
| D-1 | Web 层：Spring MVC + 虚拟线程 vs WebFlux | handoff 1.7 | SSE 流式是硬约束，ArchitectureDesign 做最终选型 |
| D-2 | JDK 21 兼容矩阵确认 | handoff 1.1 | Spring Boot 3.x / LangChain4j / Spring AI 对 21 的兼容性验证 |
| D-3 | `diagnosis_state` 用 VARCHAR vs PostgreSQL 原生 enum | dbSchema §9 附注 | VARCHAR 更易迁移，enum 更严格——架构阶段决策 |
| D-4 | 加密方案：AES-256-GCM 具体实现位置 | dbSchema §4 | 基础设施层 vs 应用层加密，密钥管理方案 |
| D-5 | Flyway vs Liquibase 选型 | dbSchema §8 | Java 生态迁移工具选型 |
| D-6 | Connector SPI 动态注册机制 | 审查建议 3 | 避免硬编码枚举，支持新数据源不改变领域层 |
| D-7 | Maven 模块物理划分（目标 4±1） | handoff 1.8 | connector 独立 + 其余合并方案在架构阶段细定 |
| D-8 | ArchUnit 包边界测试落地 | handoff 1.8 | 逻辑分层的 CI 强制——架构阶段配置 |

---

## 审查结论

ProductPlanning 阶段全部产出物完整、内部一致、审查建议已处理。无需回滚。准予进入 ArchitectureDesign 阶段。

> 本审查由 FullProcess 统筹层在 ProductPlanning 阶段结束时生成，作为 ArchitectureDesign 阶段的前置输入。
