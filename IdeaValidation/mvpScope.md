# MVP 范围定义 — Epiphaneia

> 版本：v1.0 | 日期：2026-07-17
> 原则：不做半成品。一条闭环做到"真好用"，比四条闭环都"能用"强十倍。

---

## 1. MVP 目标

**一句话：** 让开发者用自然语言问"为什么 X 慢了/挂了？"，Epiphaneia 自动查询 Prometheus + ELK → LLM 分析 → 输出诊断报告。只读不写。

**成功标准：**
- 一个真实的 Spring Boot 项目接入后，能在 30 秒内完成一次典型诊断（"为什么我的 API 变慢了？"）
- 诊断结果包含：根因假设 + 证据链 + 修复建议 + 风险评估
- 零配置接入：检测到 Spring Boot 项目自动读取 Actuator 端点

---

## 2. MVP 功能范围

### 2.1 ✅ Must Have — 不上线就无意义

| 功能模块 | 具体内容 | 优先级 |
|----------|---------|--------|
| **自然语言诊断问答** | 用户输入自然语言描述问题，Agent 自动拆解、查询、分析、回答 | P0 |
| **Prometheus 集成** | 通过 PromQL 查询指标，自动生成查询语句，异常检测 | P0 |
| **ELK/Loki 集成** | 基于时间和关键词的日志检索，异常模式提取 | P0 |
| **LLM 根因推断** | 综合分析指标+日志 → 输出：根因假设、证据链、修复方案、风险评估 | P0 |
| **诊断报告生成** | Markdown 格式报告：时间线 + 证据 + 根因 + 建议，可分享 | P0 |
| **Spring Boot 零配置接入** | 自动发现 Actuator 端点，读取 health/metrics/env 信息 | P0 |
| **Web UI** | 对话式诊断界面（类 ChatGPT 交互）+ 报告查看页 | P0 |
| **REST API** | 所有诊断能力通过 API 暴露（API First 原则） | P0 |
| **Docker 部署** | docker-compose 一键启动（Nginx + Spring Boot） | P0 |

### 2.2 🟡 Should Have — 时间允许就做

| 功能模块 | 具体内容 | 优先级 |
|----------|---------|--------|
| **AlertManager Webhook** | 告警自动触发诊断，不用人主动提问 | P1 |
| **Git 变更关联** | 诊断时自动关联最近的代码提交（"这个慢查询是哪个 PR 引入的？"） | P1 |
| **简单知识库** | 历史诊断记录可搜索（初期用数据库全文索引，不引入向量数据库） | P1 |
| **飞书/钉钉 Webhook 推送** | 诊断结果推送到 IM 群 | P1 |
| **Java Adapter 完整实现** | 深度 Java 诊断：JVM 参数分析、GC 日志解析、连接池诊断 | P1 |

### 2.3 ❌ Won't Have — 架构预留但 MVP 坚决不做

| 功能模块 | 原因 | 远期计划 |
|----------|------|---------|
| **自动执行修复** | 安全第一——MVP 只诊断不建议执行。读操作无风险，写操作需更多验证 | v1.5 加入，需人类确认 |
| **Jaeger/Zipkin 链路追踪** | 多一个数据源多很多复杂度。Loki + Prometheus 足够诊断 80% 问题 | v2.0 |
| **非 Java Language Adapter** | 接口定义好，实现留给社区或后期 | v1.5+ |
| **测试/审查/文档 Skill** | Skill 接口预留，实现留给后面版本 | v1.5+ |
| **多用户/团队/权限** | MVP 单实例单用户 | v2.0 |
| **自建 Dashboard** | 不跟 Grafana 竞争——诊断结果可嵌入 Grafana Panel | 不做 |
| **移动端 App** | 开发者不在手机上做诊断 | 不做 |
| **Desktop App (Tauri)** | Web UI 够用 | v1.5 |

---

## 3. MVP 产品边界

### 做什么（智能层）
- AI 驱动的故障诊断与根因分析
- 自然语言交互——降低运维门槛
- 知识沉淀——每次诊断变成可复用的经验

### 不做什么（数据层 & 执行层）
- 不采集监控数据（Prometheus/ELK 的事）
- 不存储日志/指标（已有工具的事）
- 不做可视化 Dashboard（Grafana 的事）
- 不执行修复操作（MVP 阶段的强制安全边界）

### 核心公式
```
Epiphaneia ≠ 另一个监控工具
Epiphaneia = 监控工具的 AI 大脑层
           = 数据(已有监控) + 推理(LLM) + 经验(知识库)
```

---

## 4. MVP 架构概要（面向下一阶段的关键输入）

### 4.1 技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| **后端框架** | Spring Boot 3.x + Java 17+ | 项目根技术栈 |
| **LLM 集成** | Spring AI + LangChain4j | 模型无关的 LLM 调度层 |
| **前端** | React 18 + Vite + TypeScript | 前后端分离 SPA |
| **部署** | Docker + docker-compose + Nginx | 一键部署 |
| **数据库** | PostgreSQL | 诊断记录、知识库、配置 |
| **前端构建** | Vite | 快速开发 + 生产构建 |
| **API 风格** | REST (OpenAPI 3.0 文档) | API First 原则 |

### 4.2 六层架构（概要）

```
⑥ 接口层 — REST API + Web UI
⑤ 技能层 — Ops Skill（MVP 唯一 Skill）
④ Agent 编排框架 — Skill 注册中心 + 多步推理 + 人机协同节点
③ LLM 调度层 — 模型路由 + Prompt 模板管理 + 上下文窗口
② 数据引擎 — 代码理解引擎 + 数据聚合/关联 + 知识库 RAG
① 集成层 — Prometheus Connector + ELK Connector + GitHub Connector
```

### 4.3 三大扩展接口（架构预留）

| 接口类型 | 用途 | MVP 实现 |
|----------|------|---------|
| **Skill 接口** | 挂载新的 Agent 能力（Test/Review/Docs） | 接口定义 + Ops Skill 实现 |
| **Language Adapter** | 支持多语言代码理解 | 接口定义 + JavaAdapter 实现 |
| **Connector** | 对接新的数据源 | 接口定义 + Prometheus/ELK Connector 实现 |

### 4.4 关键设计约束

1. **核心引擎语言无关**：LLM 调度、Agent 编排、知识库不应包含 Java 特定逻辑。
2. **API First**：所有能力通过 REST API 暴露。Web UI 是 API 的第一个消费者，不是唯一消费者。
3. **安全边界**：Agent 执行任何写操作前必须经过人类确认。这条在 Agent 编排框架层强制执行，Skill 无权绕过。
4. **模型无关**：支持 OpenAI、Anthropic、DeepSeek、Ollama 等多种 LLM 后端，用户可自行切换。

---

## 5. MVP 不做但已决定的决策（传给下一阶段）

| 决策 | 结论 |
|------|------|
| 产品名称 | **Epiphaneia** |
| 市场定位 | B2D 开发者工具 |
| 产品形态 | 垂直领域 Agent 工作台，运维先行 + Skill 插件扩展 |
| 语言支持 | 后端多语言，Java 优先。核心引擎语言无关 |
| 前端策略 | React + Vite SPA，前后端分离，API First |
| 部署形态 | Docker Compose（Nginx + Spring Boot + PostgreSQL） |
| 商业模型 | 开源社区驱动（Apache 2.0 或 MIT），远期企业版/托管版 |
| 技术栈约束 | Java Spring Boot 后端 + React 前端 |

---

## 6. MVP 工作量估算

| 模块 | 工作量 | 说明 |
|------|--------|------|
| 项目脚手架 + 核心框架搭建 | 3-4 天 | Spring Boot 项目初始化、多模块 Maven、CI 配置 |
| 集成层（Prometheus + ELK Connector） | 4-5 天 | Connector 接口 + 两个实现 + 数据查询抽象 |
| 数据引擎（代码理解 + Java Adapter） | 5-7 天 | 多语言 AST 接口 + Java 实现 + 数据关联 |
| LLM 调度 + Agent 编排框架 | 5-7 天 | 模型路由 + Prompt 模板 + Skill 注册中心 + ReAct 策略 |
| Ops Skill（诊断提示词 + 规则 + 报告模板） | 5-7 天 | 这是核心价值——诊断准确度取决于此 |
| Web UI + REST API | 4-5 天 | React 对话界面 + 报告渲染 + OpenAPI 文档 |
| 测试 + 文档 + docker-compose | 3-4 天 | 集成测试 + README + 一键部署 |
| **合计** | **约 4-5 周** | 单人开发，含学习/踩坑缓冲 |

---

> 下一阶段参考：本文档是 `ProductPlanning` 阶段的核心输入——PRD、领域建模、API 设计、数据库设计均基于此范围。详细架构设计在 `ArchitectureDesign` 阶段进行。
