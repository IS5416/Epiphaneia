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

0. **交流语言**：始终使用中文与用户交流。此规则优先级高于所有 skill（包括 caveman 等风格化 skill）的默认语言行为。
1. **顺序推进**：新想法从 `IdeaValidation` 开始，按节点顺序推进，不可跳跃。
2. **节点准入**：进入下一节点前，必须完成当前节点所有产出物。
3. **技术栈约束**：整体基于 Java Spring 生态，具体技术选型在 `ArchitectureDesign` 节点确定。
4. **禁止过早编码**：前三节点（IdeaValidation / ProductPlanning / ArchitectureDesign）禁止写生产代码。原型验证代码允许，但必须明确标记为丢弃件。
5. **跨节点回溯**：后续节点发现问题可回溯前序节点修正，如开发中发现架构不合理，回 `ArchitectureDesign` 调整。
6. **产出物管理**：每个节点的产出物存放在对应目录下，文件组织由该节点 AGENTS.md 约束。所有文档文件统一使用驼峰命名（camelCase），如 `marketResearch.md`、`ideaValidationReview.md`。目录使用 PascalCase（首字母大写），如 `IdeaValidation/`、`ProductPlanning/`。
7. **选项推荐**：提供多个选项时，必须同时给出推荐选项及其理由。理由需包含：为什么选这个、为什么不选其他、潜在风险。
8. **Git 提交规范**：commit message 使用简洁的一句式 Conventional Commits 格式（如 `feat:` / `fix:` / `chore:` / `docs:`），用冒号后一句话简要说明改动。禁止大段描述，禁止 `Co-Authored-By` 署名。示例：`fix: use trust_env=False instead of proxy=None to actually bypass system proxy`。
9. **子代理审查（高优先级）**：每个产品阶段的主 agent 在每次产出或修改文件/代码后，必须自主判断是否达到里程碑节点（如一个阶段内完成了若干文档、一个功能模块编码完毕）。若判断达到里程碑，或当前文档量/代码量已经较多，**禁止自查**（主观性太强，容易陷入上下文惯性），必须派出本领域相关的一个或多个子代理（subagent）进行文档或代码审查，审查结果写回本阶段目录。此规则适用于所有阶段（含 IdeaValidation / ProductPlanning / ArchitectureDesign / Development / Testing / ReleaseDeploy / PostLaunch），统筹层不再逐阶段提醒。
10. **当前状态**：ArchitectureDesign 阶段进行中——techStack、systemArchitecture 已完成，子代理审查已通过，等待 securityDesign / deploymentArchitecture / projectScaffold。

## 上下文

- 本文件为根节点统筹，每个子目录有各自的 `AGENTS.md` 约束该阶段行为。
- 各目录 `CLAUDE.md` 仅引用对应 `AGENTS.md`，不重复内容。
