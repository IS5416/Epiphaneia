# 2026-07-22 — Phase 5 Docker 收尾 + 文档完成

## 目标

完善 Docker 部署方案，使 `docker compose up` 一键启动全部服务，加快速开始文档。

## 变更

### Docker 架构调整

```
docker/
├── Dockerfile              # 后端多阶段构建（Maven → JRE）
├── Dockerfile.frontend     # 前端构建（Node → Nginx），NEW
├── docker-compose.yml      # 3 服务编排
└── nginx/
    └── nginx.conf          # 反向代理 + SPA fallback + CSP
```

**docker-compose.yml 改造：**
- `nginx` 服务 → `frontend` 服务：nginx 集成到前端镜像内，不再单独容器
- `app` 服务：新增 `build:` 指令，从本地 Dockerfile 构建（原仅 `image:` 拉取 ghcr.io）
- `build.context`: `..`（product/ 根目录），使前后端 Dockerfile 都能访问各自源代码

**Dockerfile.frontend（新增）：**
- Stage 1: `node:22-alpine` — `npm ci` + `npm run build`
- Stage 2: `nginx:1.27-alpine` — 复制 nginx.conf + React dist
- 无需单独启动 nginx 容器

### 文档更新

**product/README.md** 重写：
- Docker 快速开始（3 步：`.env` → `docker compose up -d` → 浏览器）
- 本地开发命令（后端 `spring-boot:run` + 前端 `npm run dev`）
- 服务端口表
- 配置优先级说明

### 策略评估

5 文件（Dockerfile.frontend 新建、docker-compose.yml 重写、nginx.conf 不变、product/README.md 重写、.env.example 已是最终版），主 agent 直接写。Docker 模式成熟（多阶段构建 + compose 编排 + nginx SPA 反向代理），无复杂逻辑。

## 最终状态

全流程完成：

| Phase | 内容 | 状态 |
|-------|------|------|
| Phase 0 | Infra 服务 + JPA 实体 + Repository | ✅ |
| Phase 1 | 状态机 + Prompt 模板 + 模型路由 + 引擎 + 连接器 | ✅ |
| Phase 2 | ReAct 诊断循环 + 报告合成 | ✅ |
| Phase 3 | Server 层：安全 + DTO/Mapper + 7 Controller + SSE | ✅ |
| Phase 4 | Web UI：6 React 页面 + SSE 流 + 29 API 端点 | ✅ |
| Phase 5 | Docker Compose 一键部署 + 文档 | ✅ |
