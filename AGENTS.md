# FullProcess — 产品全流程统筹

## 项目目标

从想法到上线，全流程产出一款产品。基础技术生态：**Java Spring 生态**。

## 全流程节点

| 节点 | 目录 | 职责 |
|------|------|------|
| 想法验证 | `IdeaValidation/` | 市场调研、竞品分析、用户需求确认、MVP范围界定 |
| 规划设计 | `ProductPlanning/` | PRD、领域建模、API设计、数据库设计 |
| 架构设计 | `ArchitectureDesign/` | 系统架构、技术选型、安全设计、部署架构 |
| 开发实现 | `Development/` | 编码、单元测试、代码审查 |
| 测试阶段 | `Testing/` | 集成测试、性能测试、验收测试 |
| 发布上线 | `ReleaseDeploy/` | CI/CD、环境配置、灰度发布 |
| 上线后 | `PostLaunch/` | 监控告警、用户反馈、迭代规划 |

## 行为约束

1. **顺序推进**：新想法从 `IdeaValidation` 开始，按节点顺序推进，不可跳跃。
2. **节点准入**：进入下一节点前，必须完成当前节点所有产出物。
3. **技术栈约束**：整体基于 Java Spring 生态，具体技术选型在 `ArchitectureDesign` 节点确定。
4. **禁止过早编码**：前三节点（IdeaValidation / ProductPlanning / ArchitectureDesign）禁止写生产代码。原型验证代码允许，但必须明确标记为丢弃件。
5. **跨节点回溯**：后续节点发现问题可回溯前序节点修正，如开发中发现架构不合理，回 `ArchitectureDesign` 调整。
6. **产出物管理**：每个节点的产出物存放在对应目录下，文件组织由该节点 AGENTS.md 约束。
7. **当前状态**：第一步初始化完成，等待从 `IdeaValidation` 开始。

## 上下文

- 本文件为根节点统筹，每个子目录有各自的 `AGENTS.md` 约束该阶段行为。
- 各目录 `CLAUDE.md` 仅引用对应 `AGENTS.md`，不重复内容。
