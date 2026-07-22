# 2026-07-20 — Phase 4 启动前人工操作清单

## 配置分层原则

| 配置方式 | 适用于 | 示例 |
|---------|--------|------|
| **`.env` 环境变量** | 基础设施密钥，应用启动必需 | DB 密码、加密密钥、LLM API Key（可选） |
| **Web UI Settings 页面** | 应用级运行时可变配置 | Prometheus URL、ES URL、LLM 模型选择、应用注册 |

LLM API Key 两种方式都支持：env 变量优先，也可以在 Settings 页面输入（加密存 DB）。

---

## 必须操作（最少 3 步，启动后端）

### 步骤 1：启动 PostgreSQL

仅启动数据库（不启动 nginx，前端还未开发）：

```bash
docker run -d --name epiphaneia-postgres \
  -e POSTGRES_DB=epiphaneia \
  -e POSTGRES_USER=epiphaneia \
  -e POSTGRES_PASSWORD=请修改为你的密码 \
  -p 5432:5432 \
  postgres:16-alpine
```

全部完成后用 `docker compose up -d` 替代（含 nginx + app + postgres）。

### 步骤 2：创建 .env 文件

```bash
cd E:/MyProducts/FullProcess/product
cp .env.example .env
```

编辑 `.env`，至少填写：

```env
DB_USER=epiphaneia
DB_PASSWORD=你在步骤1设置的密码
EPIPHANEIA_ENCRYPTION_KEY=运行 openssl rand -hex 32 得到的64字符hex
```

**不能跳过**：`EPIPHANEIA_ENCRYPTION_KEY` 为空会导致启动失败。

### 步骤 3：启动应用

```bash
cd E:/MyProducts/FullProcess/product

# 设置环境变量后启动
$env:DB_URL="jdbc:postgresql://localhost:5432/epiphaneia"
$env:DB_USERNAME="epiphaneia"
$env:DB_PASSWORD="你的密码"
$env:EPIPHANEIA_ENCRYPTION_KEY="你的64字符hex"
./mvnw spring-boot:run
```

首次启动时 Flyway 自动建表，AdminSeeder 在控制台输出初始管理员密码：
```
============================================
  Epiphaneia initial admin credentials:
  Username: admin
  Password: xxxxxxxxxxxxxxxx  ← 记录这行
  You will be required to change this.
============================================
```

启动后访问：
- 应用：http://localhost:8080
- Swagger UI：http://localhost:8080/swagger-ui.html
- 登录：用户名 `admin`，密码为控制台输出的随机密码

---

## 可选操作（Phase 4 开发中按需配置）

| 操作 | 时机 | 方式 |
|------|------|------|
| 配置 LLM API Key | 开发 DiagnosisWorkspace 页面时 | `.env` 中加 `OPENAI_API_KEY=sk-...` 或 `ANTHROPIC_API_KEY=...`，或在 Settings 页面配置 |
| 安装本地 Ollama | 不想用云端 LLM 时 | `ollama pull llama3.1`，Settings 配置 `http://localhost:11434` |
| 启动 Prometheus | 开发诊断查询功能时 | `docker run -d --name prometheus -p 9090:9090 prom/prometheus`，Settings 配置 URL |
| 启动 Elasticsearch | 开发日志查询功能时 | `docker run -d --name es -p 9200:9200 -e "discovery.type=single-node" -e "xpack.security.enabled=false" elasticsearch:8.15.0`，Settings 配置 URL |

---

## 配置来源对照表

| 配置项 | `.env` | Web UI Settings | 存储位置 |
|--------|:---:|:---:|------|
| `DB_USER` | ✅ | — | Docker Compose |
| `DB_PASSWORD` | ✅ | — | Docker Compose |
| `EPIPHANEIA_ENCRYPTION_KEY` | ✅ | — | 环境变量 |
| `OPENAI_API_KEY` | ✅ 可选 | ✅ | env 或 DB（加密） |
| LLM Provider / Model | — | ✅ | `llm_provider` 表 |
| LLM Base URL | — | ✅ | `llm_provider` 表 |
| Prometheus URL | — | ✅ | `data_source` 表 |
| Elasticsearch URL | — | ✅ | `data_source` 表 |
| Data Source Auth | — | ✅ | `data_source.auth_config`（加密） |
| Application 注册 | — | ✅ | `application` 表 |

## 当前桩状态

以下端点返回假成功，配好外部服务后自动生效：
- `POST /applications/{id}/probe` → 总是 "reachable"
- `POST /datasources/{id}/test` → 总是 "successful"
- `POST /llm/test` → 总是 "successful"
