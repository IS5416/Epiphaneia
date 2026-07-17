# IdeaValidation 阶段审查反馈

> 本文档由 FullProcess 统筹层在 IdeaValidation 完成后生成，作为 ProductPlanning 阶段的前置注意事项。
> 来源：对 IdeaValidation 五份产出物的审查。

---

## 审查结论

IdeaValidation 产出物整体质量高，Go 决策逻辑自洽。以下内容不在 IdeaValidation 阶段回溯修改，而是作为 ProductPlanning 阶段的注意清单。

---

## 亮点（应在 PRD 中保持的方向）

| # | 亮点 | 对 ProductPlanning 的意义 |
|---|------|--------------------------|
| 1 | **"AI 大脑层"定位精准** — 不跟 Prometheus/Grafana/ELK 抢饭碗，在它们之上增值 | PRD 功能边界以此为纲：输入是已有监控系统的数据，输出是诊断结论 |
| 2 | **"只读不写"安全边界务实** — Agent 只诊断不执行，MVP 阶段零风险 | API 设计必须从架构层阻止写操作，不可依赖 Agent prompt 约束 |
| 3 | **P0/P1/Won't Have 划分有纪律** — 每项 Won't Have 写了"为什么不做"和"什么时候做" | PRD 直接沿用此优先级体系，不重新排序 |
| 4 | **三个用户画像有区分度** — 痛点、场景、付费意愿差异明显 | PRD 功能对标用户画像：P0 覆盖 A+B，P1 覆盖 B 深度场景，远期覆盖 C |
| 5 | **竞品四象限分析** — 开源/商业 × 通用/垂直，竞品启示录四条经验可操作 | 架构设计时逐条对照：不做数据采集、先读后写、开源透明、社区驱动 |

---

## 需要注意的点（ProductPlanning 阶段需处理）

### 1. MVP 六层架构偏重 — 建议精简

mvpScope.md 定义了 6 层架构（接口层 → 技能层 → Agent编排 → LLM调度 → 数据引擎 → 集成层）。单人 4-5 周完成所有层 + 前端 + 测试过于乐观。

**建议**：ProductPlanning 阶段重新审视架构分层——
- 合并 **LLM 调度层** 和 **Agent 编排层** 为单层（MVP 只有一个 Ops Skill，编排复杂度有限）
- 合并 **数据引擎** 和 **集成层** 为单层（MVP 只有 Prometheus + ELK 两个数据源）
- 精简后目标：**4 层架构**（接口层 → Agent核心层 → 数据适配层 → 集成层）

### 2. 产品名称"Epiphaneia"传播风险

拼写和发音门槛高，不利于开发者社区口口相传。不影响 MVP 开发。

**建议**：ProductPlanning 阶段不花时间纠结名称（保持 Epiphaneia 作为代号），但在 ReleaseDeploy 阶段前确定一个简洁别名或缩写，用于 GitHub repo 名、命令行工具名、域名。

### 3. P1 功能中告警集成诱惑大

AlertManager Webhook 和 IM 推送在 P1，但这两个是 B 类用户画像（SRE）的核心场景——"每告警自动触发诊断"比"有问题才打开 Web UI 问"更符合 SRE 的工作流。

**建议**：ProductPlanning 阶段评估是否拆出两个交付节点：
- v0.9：MVP（P0 全部） + AlertManager Webhook + 飞书推送（B 类用户的最小可用闭环）
- v1.0：v0.9 + 其余 P1 + 知识库

如果时间不够则保 MVP，但 API 设计时预留 Webhook 接口和消息推送的扩展点。

### 4. TAM/SAM 估算偏乐观

"10% 后端开发者需要 AI 运维工具"假设偏激进。大部分开发者不参与 on-call，不主动排查生产问题。不影响 MVP 方向和开源策略。

**建议**：ProductPlanning 阶段不调整（开源项目不依赖 TAM 估值），但企业版/托管版的商业计划书阶段需要更保守的 SAM 估算。

### 5. Git 变更关联功能的前置接口

"这个慢查询是哪个 PR 引入的？"功能虽在 P1，但其依赖的 **GitHub Connector** 接口是架构扩展点之一。

**建议**：即使 P1 功能不做，Development 阶段也先把 Connector 接口定义好。否则后期硬塞可能破坏架构。

### 6. 单人开发产能风险

mvpScope.md 的 4-5 周估算是理想情况（无踩坑、无返工）。实际单人开发通常需要 1.5x-2x 缓冲。

**建议**：
- ArchitectureDesign 阶段产出的项目脚手架必须可立即进入编码
- ProductPlanning 阶段严格控制 PRD 范围——任何 P1 功能进入 MVP 必须有一项 P0 被降级

---

## 带入 ProductPlanning 的决策记录

以下决策在 IdeaValidation 阶段已确定，ProductPlanning 不应重新讨论，直接采纳：

| 决策 | 结论 |
|------|------|
| 产品方向 | AI Agent 智能诊断工作台，运维场景先行 |
| 市场定位 | B2D 开源社区驱动，Java 生态优先 |
| 核心约束 | 只读不写（MVP），数据不入库（复用已有监控），API First |
| 技术栈方向 | Spring Boot 3.x + Java 17+ + React 18 + PostgreSQL + Docker |
| 优先用户 | 用户画像 A（后端开发者） > B（SRE） > C（Tech Lead） |
| 商业模式 | 开源（Apache 2.0 或 MIT）先行，远期企业版/托管版 |

---

> 本文档应在 ProductPlanning 阶段开始时作为上下文加载，PRD 完成后对照检查是否覆盖了上述注意点。
