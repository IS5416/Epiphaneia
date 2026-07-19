# ProductPlanning 修正记录

> 日期：2026-07-19
> 依据：ArchitectureDesign/comprehensiveReview.md（统筹层校准版）+ IdeaValidation/rollbackLog.md
> 判决：go_with_adjustments — 无强制回滚项，仅 ADJ-3 文档补充

## 处理清单

| 编号 | 项目 | 严重度 | 状态 | 说明 |
|------|------|--------|------|------|
| ADJ-3 | ABORTED 终态文档补充 | HIGH | ✅ 已完成 | 四个文件补充 ABORTED 终态。编码在 Development 阶段 |
| CASCADE | 适配 IdeaValidation 修正 | — | ✅ 无级联影响 | IdeaValidation rollbackLog 确认：PRD 功能规格/模块数/架构分层/开发工作量均无变动。竞品分析补充不影响 ProductPlanning 文档范围（PRD 无独立竞品章节，引用到 IdeaValidation 文档自动获取更新） |

## 修改文件清单

- **userFlow.md** §3 状态机：
  - Mermaid 图新增 PLANNING→ABORTED、QUERYING→ABORTED、ANALYZING→ABORTED 迁移路径
  - 状态表新增 ABORTED 行（终态 ✅，语义："诊断中断（服务器重启或超时过期）"）
  - 设计要点新增 ABORTED 终态说明（用户可查看已收集部分证据，同一会话可重新提问）

- **domainModel.md** §2.2 Message：
  - DiagnosisState 枚举新增 `ABORTED`
  - Message 属性表新增 `failureReason: String?`（仅 ABORTED/FAILED 时有值）
  - 状态机 Mermaid 图同步更新
  - 设计要点新增 ABORTED 终态描述

- **apiDesign.md** §7：
  - POST /conversations/{id}/messages 409 状态码新增豁免逻辑：非终态诊断 `created_at` 距今超过 150 秒 → 自动标记 ABORTED + failureReason="Timeout exceeded" → 允许新消息
  - GET /conversations/{id} 响应示例新增 `failureReason` 字段
  - 错误码 `DIAGNOSIS_IN_PROGRESS` 描述更新

- **dbSchema.md** §2.7 message 表：
  - `diagnosis_state` 注释新增 `ABORTED`
  - 新增 `failure_reason TEXT` 列
  - 约束新增：failure_reason 仅 ABORTED/FAILED 时非 NULL；token_count 仅终态时非 NULL
  - 部分索引注释更新
  - §6 DDL 骨架同步新增 failure_reason 列

## 对下游的级联影响（传递给 ArchitectureDesign）

| 影响项 | 说明 |
|--------|------|
| systemArchitecture 状态机 | 需同步包含 ABORTED 终态的完整 8 状态机描述（ArchitectureDesign/systemArchitecture.md） |
| Development 启动扫描 | Development 阶段需实现 `@PostConstruct`（Spring Boot）启动时扫描 `diagnosis_state IN ('CREATED','PLANNING','QUERYING','ANALYZING')` 且 `created_at` 早于阈值（如启动前 150 秒）的 Message，批量标记 ABORTED。ArchitectureDesign 阶段在 projectScaffold 的技术债务表中记录此项，无需实现 |
| 恢复可见性 | ABORTED 的 Message 在 P5 历史列表中以 ABORTED 徽标展示，P4 报告合成时包含 ABORTED 标注 |

## 未处理项

本阶段无需处理的审查发现：

| 编号 | 说明 |
|------|------|
| RB-4（原） | 已降级为 ADJ-3，已处理 |
| RB-5（原，协作式取消） | 已降级为 ADJ-3 的一部分——ABORTED 豁免逻辑覆盖了取消语义（超时自动 ABORTED），主动取消（客户端 SSE 关闭）仍保持现有"服务端跑到终态"策略 |
| 所有 LOW/MEDIUM 项 | 不阻塞 projectScaffold，在 Development 阶段自然解决 |

---

> **修正完成。** ProductPlanning 阶段文档已根据 comprehensiveReview.md（统筹层校准版）更新完毕。ADJ-3 的编码实现在 Development 阶段。本阶段可以退出，进入 ArchitectureDesign。
