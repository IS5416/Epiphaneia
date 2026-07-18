# ArchitectureDesign 交接文档

> 来源：ProductPlanning 阶段与用户的交叉讨论沉淀。
> 性质：活文档——本阶段持续追加，阶段结束时定稿，作为 ArchitectureDesign 阶段的输入之一。
> 规则：本阶段正式产出物（PRD/domainModel/apiDesign/dbSchema/userFlow）已说明的内容，此处只留指针，不重复。

---

## 1. 已定方向（本阶段讨论结论，ArchitectureDesign 确认并落地）

### 1.1 JDK 版本
- 倾向 **JDK 21 LTS**（而非 mvpScope 中的 17+）。虚拟线程天然适合 LLM 调用、Prometheus/ELK 查询这类长 IO 等待场景，迁移成本为零。
- ArchitectureDesign 做最终确认（检查 Spring Boot 3.x / LangChain4j / Spring AI 对 21 的兼容矩阵）。

### 1.2 缓存策略
- **Spring Cache 抽象 + Caffeine（MVP）**，Redis 为既定升级路径。
- 缓存的真实需求：PromQL 查询结果（短 TTL）、LLM 响应去重（成本缓解）、知识库热点查询。
- 架构约束：CacheManager 必须走抽象层，业务代码零感知实现——切 Redis 时只动配置 + docker-compose。
- Redis 引入时机：多实例部署 / 跨实例共享缓存 / pub-sub 需求出现时（预计 v2.0 多用户）。
- MVP 不部署 Redis 容器的理由：单实例下 Redis 无独占价值，稀释"一键部署"极简卖点。

### 1.3 消息队列
- **MVP 不引入**。诊断为请求驱动同步流；AlertManager webhook 异步用 `@Async` + 任务表承接。
- 引入时机：v1.x 告警风暴削峰（AlertManager 高频触发场景）。

### 1.4 分布式 / 网关 / 服务治理
- **MVP 明确不做**。产品形态为"开发者 docker-compose 一键自部署单体"——这是卖点不是妥协，微服务化会杀死"零配置接入"。
- 重新评估时机：v2.0 多用户/团队/权限需求出现时。

### 1.5 云原生
- Docker Compose 为唯一官方部署形态（Nginx + Spring Boot + PostgreSQL）。
- Helm chart / K8s manifests 标 "community wanted"，不占官方产能。

### 1.6 Agent 层形态
- **Java 内嵌：Spring AI + LangChain4j**。不独立为 Python/TypeScript agent 服务。
- 理由：单体一键部署卖点；产品护城河是 Java 生态深度绑定，Agent 层换 Python 等于自砍差异化、向竞品 Argus 趋同；MVP 单 Skill 编排复杂度低，LangChain4j 的 tool-calling/ReAct 足够。
- 架构约束：**Agent 编排逻辑收敛在接口之后，领域层不感知 LangChain4j/Spring AI**。远期若 Java 编排生态落后，替换的是基础设施层实现，不动领域层。
- 明确否决：跨语言 agent 协议（判定为过度设计）。"核心引擎语言无关"指不含 Java 特定业务逻辑，不指运行时跨语言。

### 1.7 Web 层技术（未决，留给 ArchitectureDesign）
- Spring MVC + 虚拟线程 vs WebFlux——架构阶段决策。
- 硬约束：必须支持 **SSE 流式输出**（诊断过程实时推送），接口契约见 apiDesign.md。

### 1.8 架构分层：逻辑六层 + 物理模块按需合并
- **逻辑六层完整保留**，作为包结构与接口边界：`api / skill / orchestration / llm / engine / connector`。统筹层审查建议的"四层合并"仅在物理模块维度采纳，概念层不砍。
- 物理 Maven 模块只给"有独立替换/社区贡献需求"的层：
  - **①集成层（connector）必须独立模块**——Connector 是社区贡献核心扩展点，独立 artifact + 清晰 SPI。
  - ③LLM 调度 + ④Agent 编排可物理合并为 agent-core（MVP 单 Skill，Spring AI 已承担 ③ 大半职责）。
  - 其余合并方案架构阶段细定，目标模块数 **4±1**。
- 架构约束：包边界纪律用 **ArchUnit 测试**强制，违规依赖挂 CI——这是逻辑分层不靠自觉的保障，ArchitectureDesign 阶段落地。
- 决策理由存档：解耦难度取决于接口边界清晰度，不取决于 Maven 模块数；AI 辅助降低写码成本，但不降低每层 DTO 映射/透传接口的仪式成本。

---

## 2. 指向本阶段正式产出物的项（不重复书写）

| 事项 | 去向 |
|------|------|
| SSE 流式接口契约 | `apiDesign.md` |
| 安全 NFR（LLM key / Prometheus / ELK 凭证加密存储、API 鉴权） | `PRD.md` 非功能需求 |
| Agent 编排接口约束（领域层不感知具体框架） | `domainModel.md` |
| Webhook / 消息推送扩展点预留 | `apiDesign.md` |

---

## 3. 本阶段产品决策（详情在正式产出物，此处记录对架构的影响）

| 决策 | 详情去向 | 对 ArchitectureDesign 的影响 |
|------|---------|------------------------------|
| 版本拆分：v0.9 = P0 九项；v0.95 = AlertManager Webhook + 飞书推送（快速跟进）；v1.0 = 其余 P1 + 知识库 | `PRD.md` 里程碑 | Webhook 触发诊断的端点契约首发即定义（apiDesign.md），实现推迟到 v0.95；诊断管道设计须同时支持"用户提问"和"告警触发"两种入口 |
| MVP 最小鉴权：单管理员账号（首启生成初始密码，强制修改）+ API Bearer Token | `PRD.md` 非功能需求（P0） | 安全设计需覆盖：登录/Token 签发、LLM Key 与 Prometheus/ELK 凭证的加密存储方案 |
| 诊断质量验收：金标准故障集盲测（demo Spring Boot 应用 + 注入典型故障），草案 top-1 ≥60% / top-3 ≥80% | `PRD.md` 验收标准 | 需规划配套的故障注入 demo 应用（测试资产，非生产代码），脚手架阶段一并考虑 |
| 语言策略：UI English-first（预留 i18n）；诊断报告语言跟随提问语言；README 中英双语 | `PRD.md` / `userFlow.md` | 前端选型需含 i18n 框架预留；Prompt 模板设计需支持输出语言跟随 |
| 回退判断：无需回溯 IdeaValidation。JDK 21、分层物理合并、鉴权补充均属细化非推翻，偏差由本文档承接 | （本条即记录） | — |
| 诊断"停止"为纯客户端行为：关闭 SSE，服务端总是跑到终态并存档，无服务端取消语义 | `userFlow.md` 流程 1 / 状态机 | 诊断管道无需实现中断处理（简化）；若未来需要真取消（成本控制/长耗时场景），升级路径为在 Agent 编排层加协作式取消检查点，状态机届时增加 CANCELLED 终态 |
| 单用户多应用：P6 目标应用为列表管理，P3 含应用切换器；每个应用独立会话上下文、独立 LLM 上下文；共用 Prometheus + ES，数据隔离由 service label 达成。多应用 ≠ 多租户，无团队/权限/RBAC | `PRD.md` FR-6 / `userFlow.md` P3/P6 | DB schema 需 `application` 表 + `conversation.app_id` 外键；LLM 上下文管理需按 app 分区；若以后需要应用级凭证隔离（不同应用不同 Prometheus），Connector 配置需支持标签绑定——当前不做，v2.0 评估 |
| 报告策略：会话级实时合成 + 导出即下载，不持久存储报告文件，无独立 report 表 | `PRD.md` FR-5 / `userFlow.md` P4/流程 2 | dbSchema 无 report 表——Conversation 是聚合根、Message 是证据实体，报告为会话的只读视图（LLM 实时合成）。影响：报告 API 端点为 `GET /api/v1/conversations/{id}/report`，不带持久化存储的 CRUD。若未来需要报告归档/版本对比/审计留存，升级路径为增加 report 表 + 导出时快照写入——当前不需要 |

---

## 4. 待补充（讨论中）

（当前无）
