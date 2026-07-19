# ArchitectureDesign 修正记录

> 日期：2026-07-19
> 依据：ArchitectureDesign/rollbackPrompt.md（统筹层校准版）+ IdeaValidation/rollbackLog.md + ProductPlanning/rollbackLog.md

## 处理清单

| 编号 | 项目 | 严重度 | 状态 | 说明 |
|------|------|--------|------|------|
| RB-1 | connector ↔ infra 循环依赖修正 | CRITICAL | ✅ 已修正 | Connector SPI 从 connector/api 上移到 infra/api，connector 退化为纯实现模块 |
| ADJ-2 | ArchUnit 示例包名修正 | MEDIUM | ✅ 已修正 | `..domain..` → `..api..`、`..infrastructure..` → `..internal..` |
| ADJ-7 | ConnectorRegistry 归属矛盾 | MEDIUM | ✅ 已修正 | domainModel §6.3 补充归属修正说明 |

## 适配上游级联

| 来源 | 适配内容 | 状态 |
|------|---------|------|
| IdeaValidation ADJ-1 | 确认架构保留（六层逻辑 + 4±1 物理模块不变），无级联影响 | ✅ 确认 |
| IdeaValidation RB-7 | 竞品分析补遗不涉及架构文档 | ✅ 无影响 |
| ProductPlanning ADJ-3 | ABORTED 终态同步 to systemArchitecture | ✅ 已同步 |

## 修改文件清单

- **techStack.md**：§3 模块依赖表（connector 纯实现、infra 含 SPI）、依赖方向图、§2.12 ArchUnit 示例包名
- **systemArchitecture.md**：§3.1 依赖图、§3.2 模块接口表、§3.3 RB-1 说明、§4.4 connector 组件（无 api 包）、§4.5 infra 组件（含 SPI）、§9.1 扩展点图、§12 包结构、§2 状态机行 + §11 技术债务表（ABORTED 终态）
- **ProductPlanning/domainModel.md**：§6.3 ConnectorRegistry 归属修正说明

## projectScaffold 就绪检查

- [x] Maven 模块依赖方向单向无循环（RB-1 验证：connector → infra 单向，infra 不声明 connector 依赖）
- [x] Connector SPI 定义在 infra/api（编译期无循环）
- [x] 架构逻辑六层 + 物理 4±1 模块保留（IdeaValidation ADJ-1 确认）
- [x] 状态机含 ABORTED 终态描述（ProductPlanning ADJ-3 同步）
- [x] ArchUnit 示例与实际包名一致（ADJ-2）
- [x] ConnectorRegistry 归属矛盾已消解（ADJ-7）

## projectScaffold 完成（2026-07-19）

| 产出 | 状态 | 验证 |
|------|------|------|
| projectScaffold.md | ✅ | 骨架设计文档，含完整目录结构、POM设计、模块接口约定 |
| product/ 骨架代码 | ✅ | 5 Maven 模块 + web-ui 前端 + Docker + Maven Wrapper |
| `./mvnw compile` | ✅ BUILD SUCCESS | 全部 6 模块编译通过，infra 无 connector 依赖 |
| 子代理审查 | ✅ 4/4 PASS | Build/RB-1、Structure、Config、Dependencies 全维度通过 |
