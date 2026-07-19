# Epiphaneia

面向后端开发者的 AI Agent 智能诊断工作台——连接 Prometheus/Elasticsearch/Actuator 等数据源，通过 LLM 驱动的诊断引擎自动分析问题根因，输出结构化诊断报告。

本仓库承载从想法到上线的全流程管理，流程文档与产品代码（`product/`）物理隔离。

## 流程节点

| 阶段 | 目录 | 状态 |
|------|------|------|
| 想法验证 | `IdeaValidation/` | ✅ 完成 |
| 规划设计 | `ProductPlanning/` | ✅ 完成 |
| 架构设计 | `ArchitectureDesign/` | ✅ 完成 |
| 开发实现 | `Development/` | 🔜 即将开始 |
| 测试阶段 | `Testing/` | ⏳ 待开始 |
| 发布上线 | `ReleaseDeploy/` | ⏳ 待开始 |
| 上线后 | `PostLaunch/` | ⏳ 待开始 |

## 技术栈

| 类别 | 选型 | 版本 |
|------|------|------|
| 语言 | Java | 21 LTS |
| 框架 | Spring Boot | 4.1.0 |
| 构建 | Maven | 3.9+ |
| AI/Agent | Spring AI + LangChain4j | 2.0.0 / 1.17.2-beta27 |
| 数据库 | PostgreSQL | 15+ |
| 迁移工具 | Flyway | 10+ |
| ORM | Spring Data JPA + Hibernate | 随 Boot 4.x |
| 缓存 | Caffeine + Spring Cache | 3.x |
| 安全 | Spring Security + AES-256-GCM | 7.x |
| DTO 映射 | MapStruct | 1.6+ |
| 前端 | React + Vite + TypeScript | 19 / 6.x |
| 容器化 | Docker + Docker Compose + Nginx | 24+ |
| 测试 | JUnit 5 + Mockito + Testcontainers + ArchUnit | — |

## 项目结构

```
product/                          ← 软件项目根目录（Maven 多模块）
├── epiphaneia-infra/             ← 基础设施（Connector SPI、加密、异常）
├── epiphaneia-connector/         ← 数据源连接器实现（Prometheus、ES）
├── epiphaneia-engine/            ← 查询引擎（指标/日志/Actuator）
├── epiphaneia-agent-core/        ← 诊断核心（编排、LLM 路由、报告合成）
├── epiphaneia-server/            ← Spring Boot 入口 + REST API + SSE
├── epiphaneia-web-ui/            ← React 前端（独立 npm 项目）
└── docker/                       ← Dockerfile + docker-compose + nginx.conf
```

## 快速开始

```bash
# 构建全部模块
cd product && ./mvnw compile

# 启动（需要 PostgreSQL + 配置环境变量）
cd product && ./mvnw -pl epiphaneia-server spring-boot:run

# 前端开发
cd product/epiphaneia-web-ui && npm install && npm run dev
```

## 流程说明

每个阶段目录包含独立的 `AGENTS.md`（行为约束）和 `CLAUDE.md`（指针文件）。阶段按顺序推进，后续阶段发现问题可回溯前序阶段修正。详见根 [AGENTS.md](AGENTS.md)。
