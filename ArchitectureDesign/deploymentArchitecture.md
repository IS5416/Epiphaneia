# 部署架构 — Epiphaneia

> 版本：v1.0-draft | 日期：2026-07-18
> 输入：techStack.md、systemArchitecture.md、securityDesign.md、PRD.md
> 原则：docker-compose 为唯一官方部署形态（handoff 1.5），不引入 K8s/Helm

---

## 1. 环境规划

### 1.1 环境矩阵

| 环境 | 用途 | 部署方式 | 数据 |
|------|------|---------|------|
| **开发 (dev)** | 单开发者本地编码 + 调试 | IDE 直接启动 Spring Boot + Vite dev server + docker-compose 仅启动 PostgreSQL | H2 或本地 PostgreSQL，数据可丢弃 |
| **CI** | GitHub Actions 自动构建 + 测试 | docker-compose 完整启动 → 跑集成测试 → 销毁 | 每次全新 Flyway 迁移 + 种子数据 |
| **生产 (prod)** | 用户自部署 | `docker compose up -d` | PostgreSQL 数据卷持久化，用户自行备份 |

无 staging 环境——开源工具不设官方托管实例。预发布测试通过 GitHub Release 的 pre-release 机制完成。

### 1.2 资源需求

| 资源 | 最低 | 推荐 |
|------|------|------|
| CPU | 2 核 | 4 核（LLM 若本地 Ollama 则需求更高） |
| 内存 | 4 GB | 8 GB（含 JVM 堆 2GB + PostgreSQL shared_buffers 512MB + OS） |
| 磁盘 | 10 GB | 50 GB（PostgreSQL 数据增长 + Docker 镜像） |
| 网络 | 出网（LLM API 调用） | 若用本地 Ollama 则无需出网 |

---

## 2. Docker 化方案

### 2.1 镜像层次

```
┌─────────────────────────────────────┐
│         epiphaneia-app              │  ← 应用镜像（自构建）
│  FROM eclipse-temurin:21-jre-alpine │
│  COPY epiphaneia-server.jar         │
│  USER epiphaneia (UID 1000)         │
│  HEALTHCHECK /actuator/health       │
└─────────────────────────────────────┘
         ↑ 依赖（多阶段构建产物）
┌─────────────────────────────────────┐
│         epiphaneia-builder          │  ← 构建镜像（CI 阶段，不发布）
│  FROM eclipse-temurin:21-jdk-alpine │
│  + Maven + 源码 → mvn package       │
└─────────────────────────────────────┘

外部镜像（直接拉取，不自构建）：
  - nginx:1.27-alpine
  - postgres:16-alpine
```

### 2.2 应用 Dockerfile

```dockerfile
# 阶段 1：构建（CI 中执行，或用户手动）
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN ./mvnw package -DskipTests -pl epiphaneia-server -am

# 阶段 2：运行
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl
RUN addgroup -S epiphaneia && adduser -S epiphaneia -G epiphaneia
WORKDIR /app

COPY --from=builder /build/epiphaneia-server/target/epiphaneia-server-*.jar app.jar

USER epiphaneia
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+UseG1GC", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
```

### 2.3 多架构构建

```bash
docker buildx build \
    --platform linux/amd64,linux/arm64 \
    -t ghcr.io/epiphaneia/epiphaneia:0.9.0 \
    --push .
```

GitHub Actions 中用 `docker/setup-qemu-action` + `docker/setup-buildx-action` 实现。

### 2.4 前端构建

前端 `epiphaneia-web-ui` 不打包进应用镜像。生产部署时：
- `npm run build` → `web-ui/dist/`
- Nginx 直接 serve `dist/` 目录（volume 挂载或 COPY 进 Nginx 镜像）

Nginx 自定义镜像（可选，docker-compose 直接 volume 挂载也可）：

```dockerfile
FROM nginx:1.27-alpine
COPY epiphaneia-web-ui/dist /usr/share/nginx/html
COPY docker/nginx/nginx.conf /etc/nginx/nginx.conf
```

---

## 3. docker-compose 拓扑

### 3.1 服务拓扑图

```
                    ┌──────────────────────────────┐
                    │      epiphaneia-network       │
                    │      (bridge, internal)       │
                    │                              │
    Host :80 ───────┤  ┌──────────────────────┐    │
                    │  │       nginx           │    │
                    │  │  - 反向代理 :8080     │    │
                    │  │  - serve web-ui/dist/ │    │
                    │  │  - TLS 终端（可选）    │    │
                    │  └──────────┬───────────┘    │
                    │             │                │
                    │  ┌──────────┴───────────┐    │
                    │  │    epiphaneia-app     │    │
                    │  │  - Spring Boot :8080  │    │
                    │  │  - env: ENC_KEY, etc  │    │
                    │  └──────────┬───────────┘    │
                    │             │                │
                    │  ┌──────────┴───────────┐    │
                    │  │     postgres          │    │
                    │  │  - :5432 (不暴露主机) │    │
                    │  │  - volume: pg_data    │    │
                    │  └──────────────────────┘    │
                    │                              │
                    └──────────────────────────────┘

                   (不在 compose 中——用户已有基础设施)
                      Prometheus :9090
                      Elasticsearch :9200
                      LLM API (OpenAI/Anthropic/等) — 出网
```

### 3.2 docker-compose.yml

```yaml
version: "3.9"

services:
  nginx:
    image: nginx:1.27-alpine
    container_name: epiphaneia-nginx
    ports:
      - "${EPI_PORT:-80}:80"
    volumes:
      - ./epiphaneia-web-ui/dist:/usr/share/nginx/html:ro
      - ./docker/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./docker/nginx/certs:/etc/nginx/certs:ro  # TLS 证书（可选）
    depends_on:
      app:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - epiphaneia-network

  app:
    image: ghcr.io/epiphaneia/epiphaneia:${EPI_VERSION:-0.9.0}
    container_name: epiphaneia-app
    expose:
      - "8080"  # 不暴露到宿主机——仅 nginx 可访问
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 40s  # Spring Boot 启动时间
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/epiphaneia
      - SPRING_DATASOURCE_USERNAME=${DB_USER:-epiphaneia}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - EPIPHANEIA_ENCRYPTION_KEY=${EPIPHANEIA_ENCRYPTION_KEY}
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}
    env_file:
      - .env  # 包含 DB_PASSWORD, ENCRYPTION_KEY 等敏感变量
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - epiphaneia-network

  postgres:
    image: postgres:16-alpine
    container_name: epiphaneia-postgres
    expose:
      - "5432"  # 不暴露到宿主机
    environment:
      - POSTGRES_DB=epiphaneia
      - POSTGRES_USER=${DB_USER:-epiphaneia}
      - POSTGRES_PASSWORD=${DB_PASSWORD}
      - POSTGRES_INITDB_ARGS=--data-checksums
    volumes:
      - pg_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-epiphaneia} -d epiphaneia"]
      interval: 15s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    networks:
      - epiphaneia-network

volumes:
  pg_data:
    driver: local
    name: epiphaneia-pg-data

networks:
  epiphaneia-network:
    driver: bridge
    name: epiphaneia-network
```

### 3.3 Nginx 配置

```nginx
upstream epiphaneia_backend {
    server app:8080;
}

server {
    listen 80;
    server_name localhost;

    # 安全头（详见 securityDesign §5.2）
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff";
    add_header X-Frame-Options "DENY";
    add_header X-XSS-Protection "0";
    add_header Referrer-Policy "strict-origin-when-cross-origin";
    add_header Permissions-Policy "camera=(), microphone=(), geolocation=()";
    # CSP（详见 securityDesign §7.2）
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; font-src 'self'";

    # 前端静态文件
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;  # SPA fallback
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://epiphaneia_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE 长连接支持
        proxy_buffering off;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
    }

    # Actuator（仅内部访问）
    location /actuator/ {
        deny all;
    }
}
```

---

## 4. 环境变量参考

### 4.1 必需变量

| 变量 | 用途 | 生成方式 |
|------|------|---------|
| `DB_PASSWORD` | PostgreSQL 密码 | 用户指定或自动生成 |
| `EPIPHANEIA_ENCRYPTION_KEY` | AES-256-GCM 密钥 | 首次启动自动生成（Base64 编码 32 字节随机值），建议用户固定到 `.env` |

### 4.2 可选变量

| 变量 | 默认值 | 用途 |
|------|--------|------|
| `EPI_PORT` | `80` | 主机端口 |
| `EPI_VERSION` | `0.9.0` | 应用镜像版本 |
| `DB_USER` | `epiphaneia` | PostgreSQL 用户名 |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring 配置 Profile |

### 4.3 .env 模板

```bash
# Epiphaneia 环境配置
# 生成方式：./scripts/generate-env.sh 或手动填写

DB_PASSWORD=change-me-to-a-random-value
EPIPHANEIA_ENCRYPTION_KEY=  # 留空 = 首次启动自动生成（建议固定）
EPI_PORT=80
SPRING_PROFILES_ACTIVE=prod
```

`.env` 文件在 `.gitignore` 中——不提交版本控制。提供 `.env.example` 模板文件供用户复制。

---

## 5. CI/CD 流水线

### 5.1 GitHub Actions 设计

```
推送到 main / PR 触发
        │
        ▼
┌──────────────────────────────┐
│  Job 1: build-and-test       │
│  - checkout                  │
│  - Setup JDK 21 + Maven      │
│  - Setup Node 22 + npm       │
│  - mvn verify (含 ArchUnit)  │
│  - npm ci + npm run build    │
│  - 上传 test 报告            │
└──────────┬───────────────────┘
           │ main 分支 only
           ▼
┌──────────────────────────────┐
│  Job 2: docker-build-push    │
│  - docker buildx (多架构)    │
│  - 推送到 ghcr.io            │
│  - SBOM 生成 (CycloneDX)     │
│  - Trivy 漏洞扫描            │
└──────────┬───────────────────┘
           │ tag push (v*)
           ▼
┌──────────────────────────────┐
│  Job 3: release              │
│  - 创建 GitHub Release       │
│  - 上传 SBOM 附件            │
│  - 更新 CHANGELOG            │
└──────────────────────────────┘
```

### 5.2 构建命令速查

| 阶段 | 命令 |
|------|------|
| 后端编译 | `./mvnw verify`（含单元测试 + ArchUnit + OWASP Dependency-Check） |
| 后端打包 | `./mvnw package -DskipTests -pl epiphaneia-server -am` |
| 前端构建 | `cd epiphaneia-web-ui && npm ci && npm run build` |
| 集成测试 | `docker compose -f docker-compose.yml -f docker-compose.test.yml up --abort-on-container-exit` |
| 镜像构建 | `docker buildx build --platform linux/amd64,linux/arm64 -t ghcr.io/epiphaneia/epiphaneia:$VERSION .` |
| 漏洞扫描 | `trivy image ghcr.io/epiphaneia/epiphaneia:$VERSION` |

### 5.3 集成测试专用 compose

```yaml
# docker-compose.test.yml（CI 中用 docker compose -f ... -f ... up --abort-on-container-exit）
# 合并到主 docker-compose.yml 之上，覆盖 CI 特定配置
services:
  app:
    image: epiphaneia:ci-${GITHUB_SHA}  # 覆盖 registry 镜像 → 使用 CI 本地构建的镜像
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=test
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/epiphaneia_test
      - EPIPHANEIA_ENCRYPTION_KEY=dGVzdC1rZXktZm9yLWNpLWVudmlyb25tZW50LXRlc3Q=

  postgres:
    environment:
      - POSTGRES_DB=epiphaneia_test
```

---

## 6. 用户部署流程

### 6.1 首次部署

```bash
# 1. 克隆项目
git clone https://github.com/your-org/epiphaneia.git
cd epiphaneia

# 2. 准备环境变量
cp .env.example .env
# 编辑 .env: 设置 DB_PASSWORD
# EPIPHANEIA_ENCRYPTION_KEY 可选——留空让系统自动生成

# 3. 启动（拉取镜像或本地构建）
docker compose up -d

# 4. 查看初始密码
docker compose logs app | grep "Initial admin password"

# 5. 浏览器打开 http://localhost
# 6. 用初始密码登录 → 强制改密 → 配置 LLM/数据源 → 开始诊断
```

### 6.2 版本升级

```bash
# 0. 先备份数据库
docker compose exec postgres pg_dump -U epiphaneia epiphaneia > backup_before_upgrade_$(date +%Y%m%d).sql

# 1. 拉取新版本
git pull

# 2. 更新版本号（.env 中 EPI_VERSION）
sed -i 's/^EPI_VERSION=.*/EPI_VERSION=0.9.1/' .env

# 3. 重新创建容器
docker compose up -d
# Flyway 自动执行新迁移
```

### 6.3 数据备份

```bash
# PostgreSQL 备份
docker compose exec postgres pg_dump -U epiphaneia epiphaneia > backup_$(date +%Y%m%d).sql

# 恢复（推荐先清理以避冲突）
docker compose exec -T postgres psql -U epiphaneia epiphaneia \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker compose exec -T postgres psql -U epiphaneia epiphaneia < backup_20260718.sql
```

> **密码提示**：如果备份恢复命令要求密码，容器内的 `pg_hba.conf` 默认对本地 Unix socket 设为 trust——命令应无密码工作。若自定义了配置，设置 `PGPASSWORD` 变量或使用 `.pgpass` 文件。

---

## 7. 健康检查与自监控

### 7.1 健康端点

| 端点 | 用途 |
|------|------|
| `GET /actuator/health` | 综合健康状态（含 PostgreSQL 连接、LLM 连通性） |
| `GET /actuator/health/liveness` | 存活探针——进程是否响应 |
| `GET /actuator/health/readiness` | 就绪探针——DB 连接是否就绪 |
| `GET /actuator/metrics` | Micrometer 指标（JVM 内存、HTTP 请求、诊断时长） |
| `GET /actuator/prometheus` | Prometheus 格式指标（供 Epiphaneia 诊断自己——NFR-8 自举） |

### 7.2 Docker 健康检查

- **postgres**：`pg_isready` 检查（10s 间隔）
- **app**：`/actuator/health` HTTP 检查（30s 间隔）
- **nginx**：不设健康检查——由 `depends_on: app` + `restart: unless-stopped` 保证

### 7.3 启动顺序

```
postgres (health check passed)
    ↓
app (等待 postgres healthy → Flyway 迁移 → Tomcat 启动)
    ↓
nginx (等待 app 启动 → 开始接受请求)
```

不依赖 `depends_on` 的 Docker 原生顺序（它不等待服务就绪），使用 `condition: service_healthy`。

---

## 8. 监控与告警

### 8.1 应用日志

- 输出目标：stdout / stderr（Docker 标准采集方式）
- 格式：JSON（Logstash Logback Encoder），便于 `docker compose logs` 或 ELK 采集
- 日志级别：生产默认 `INFO`，可通过 `SPRING_PROFILES_ACTIVE=dev` 切换到 `DEBUG`

### 8.2 Prometheus 指标

Epiphaneia 自身暴露 Prometheus 端点（NFR-8 自举），用户可以将其接入自己的 Prometheus 实例。关键指标：

| 指标 | 类型 | 说明 |
|------|------|------|
| `epiphaneia_diagnosis_duration_seconds` | Histogram | 诊断耗时分布 |
| `epiphaneia_diagnosis_total` | Counter | 诊断总数（按状态分） |
| `epiphaneia_llm_tokens_total` | Counter | LLM Token 消耗 |
| `epiphaneia_connector_errors_total` | Counter | Connector 错误次数（按数据源分） |
| `jvm_memory_used_bytes` | Gauge | JVM 内存使用 |
| `jvm_gc_pause_seconds` | Summary | GC 暂停时间分布（影响 SSE 流延迟） |
| `http_server_requests_seconds` | Histogram | HTTP 请求时延分布（Micrometer 默认暴露） |
| `hikaricp_connections_active` | Gauge | HikariCP 活跃连接数 |

### 8.3 告警建议（用户侧配置）

| 告警 | 条件 | 严重度 |
|------|------|--------|
| Epiphaneia 自身 /health 不健康 | `up == 0` for 2min | Warning |
| 诊断失败率过高 | `rate(epiphaneia_diagnosis_total{status="FAILED"}[5m]) > 0.5` | Warning |
| JVM 内存接近上限 | `jvm_memory_used_bytes / jvm_memory_max_bytes > 0.9` | Warning |
| PostgreSQL 连接池耗尽 | HikariCP metrics `active_connections / max_pool_size > 0.9` | Critical |

---

## 9. 扩展路径

| 版本 | 部署变化 | 说明 |
|------|---------|------|
| v0.9 | docker-compose 3 容器 | Nginx + App + PostgreSQL |
| v1.0 | 无变化 | 仍 docker-compose，加知识库表（Flyway 迁移） |
| v2.0 | 可选 Redis 容器 | 多用户/多实例缓存共享 |
| v2.x | 可选 K8s manifests | 社区 wanted，官方提供 Helm chart 模板 |
| v3.0 | 微服务拆分评估 | 仅当社区需求强烈且 docker-compose 无法承载时 |

---

> 下一步：子代理审查 deploymentArchitecture，用户确认后进入 projectScaffold（最后一份产出物）。
