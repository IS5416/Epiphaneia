# IdeaValidation 修正记录

> 日期：2026-07-19
> 依据：ArchitectureDesign/comprehensiveReview.md（统筹层校准版）
> 判决：go_with_adjustments — 1 CRITICAL + 3 HIGH + 26 MEDIUM

## 处理清单

| 编号 | 项目 | 严重度 | 状态 | 说明 |
|------|------|--------|------|------|
| RB-7 | 竞品分析补遗 | HIGH | ✅ 已修正 | 补充 6 个竞品 + Grafana 共存策略 |
| ADJ-1 | MVP 实现深度约束 | HIGH（原 CRITICAL） | ✅ 已修正 | 架构完整保留，每层加"仅 1 个硬编码实现"约束 |
| ADJ-5 | 市场数据可追溯性 | MEDIUM | ✅ 已修正 | 数据源标注级别 + TAM 推理链 |
| ADJ-6 | 关键假设量化验证标准 | MEDIUM | ✅ 已修正 | 每条假设加草案量化指标 |

## 修改文件清单

- **competitorAnalysis.md**：
  - §2.4 Argus：补充 GitHub 指标表（stars~500, 1-2 贡献者, 活跃 2026Q2）+ 三点差异化要点
  - §2.5 Grafana AI/ML（新增）：威胁等级 🟡、当前能力、共存策略（嵌入 Panel/Annotation）
  - §2.6 HolmesGPT（新增）：功能 1:1 重叠 🟡、CLI-first 无 Web UI、需持续监控
  - §2.7 k8sgpt（新增）：5k+ stars、K8s 特化 🟢 互补
  - §2.8 RunWhen Local（新增）：基础设施层排查 🟢 互补
  - §2.9 Keep（新增）：告警管理非诊断 🟢 互补 + 集成机会
  - §4 差异化壁垒：新增"Grafana 共存壁垒"

- **mvpScope.md**：
  - §2.1 Must Have：新增"完成标准"列——每项标注 MVP 深度约束（如 "ELK 集成 = 关键词查询 + 错误日志采样，不做 DSL 构建器"）
  - §4.1 技术栈：确认 Spring AI（LLM 调用抽象）+ LangChain4j（Agent 编排）双框架各司其职
  - §4.2 六层架构：保留原架构图，新增实现深度约束段落 + spi 同包原则
  - §6 工作量：4-5 周标注为"草稿"，同时列出 PRD 的 6-8 周缓冲值并注明以 PRD 为准

- **marketResearch.md**：
  - §5 数据源：从列表改为表格，每项标注 `[直接引用]` / `[转述自 X]` / `[作者推算]`
  - 新增 TAM 推算推理链：拆解假设 10% 的依据和 ARPU 对标方法

- **validationConclusion.md**：
  - §6 关键假设：4 条假设各补充量化验证指标（草案值，内测校准）
    - 假设 1：stars 100+、外部实例 ≥5、Discussions ≥10 条/月
    - 假设 2：根因命中率 ≥70%、误诊率 <20%
    - 假设 3：3 月内 ≥3 外部 PR、≥1 非 Java Adapter PR
    - 假设 4：注册意向 ≥50 邮箱、企业域名 ≥10

## 对下游的级联影响

| 影响项 | 程度 | 说明 |
|--------|------|------|
| PRD 竞品论述 | LOW | §2 补充的 5 个竞品在 PRD 竞品章节可微调（不阻塞 scaffold） |
| PRD 功能规格 | 无 | Must Have 9 项全部保留，仅加完成标准约束 |
| systemArchitecture 模块数 | 无 | 4±1 不变，被 ArchitectureDesign 确认 |
| 架构分层 | 无 | 六层完整保留——统筹层明确驳回"砍到三层"建议 |
| 开发工作量 | 无 | PRD 已修正为 6-8 周，本阶段文档同步注明 |
| README 竞品对比表 | LOW | 后续 ReleaseDeploy 阶段可参考本修正同步（不阻塞 scaffold） |

## 未处理项

本阶段无需处理的审查发现（校准后严重度为 MEDIUM/LOW）：

| 编号 | 原发现 | 校准后 | 不处理原因 |
|------|--------|--------|-----------|
| C-F1 技术栈 Java 版本 | MEDIUM | 非阻塞——Development 阶段编码时确定 Java 版本 |
| H-F1 竞品遗漏 | MEDIUM（已合并至 RB-7） | RB-7 已覆盖 |
| H-F2 市场数据 | MEDIUM | ADJ-5 已覆盖 |
| H-F3 假设量化 | MEDIUM | ADJ-6 已覆盖 |
| H-F4 RateLimitFilter | MEDIUM | Development 实现细节，5 分钟修 |
| H-F5 多 Prometheus | MEDIUM | MVP 单 Prometheus 合理，v2.0 再谈 |
| 其余 26 LOW | LOW | 不阻塞 scaffold，随 Development 自然解决 |

---

> **修正完成。** IdeaValidation 阶段文档已根据 comprehensiveReview.md（统筹层校准版）更新完毕。用户确认后即可进入 ProductPlanning 阶段。
