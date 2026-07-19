# 数据库设计 — Epiphaneia MVP

> 版本：v1.0-draft | 日期：2026-07-18
> 上游：`domainModel.md` v1.0-draft、`apiDesign.md` v1.0-draft、`productPlanningReview.md`
> 数据库：PostgreSQL 15+
> 规范：表名/字段名 snake_case，必须定义索引策略（AGENTS.md 约束）

---

## 1. ER 概览

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐
│  admin   │     │  application │     │ data_source  │
│ (1 row)  │     │              │     │              │
└───┬──────┘     └──────┬───────┘     └──────────────┘
    │                   │                    独立，全局共享
    │ 1:N               │ 1:N
┌───┴──────┐     ┌──────┴───────┐
│api_token │     │ conversation │
└──────────┘     │              │
                 └──────┬───────┘
                        │ 1:N
                 ┌──────┴───────────────────────────┐
                 │            message               │
                 │  (role=USER | role=AGENT)        │
                 └──────┬───────────────────────────┘
                        │ 1:N (AGENT messages only)
        ┌───────────────┼───────────────┐
        │               │               │
  ┌─────┴─────┐  ┌──────┴──────┐  ┌────┴──────────┐
  │ evidence  │  │ hypothesis  │  │fix_suggestion │
  └───────────┘  └─────────────┘  └───────────────┘

┌──────────────┐
│ llm_provider │ (1 row, 全局配置)
└──────────────┘
```

10 张表。`application` 删除 → CASCADE `conversation` → CASCADE `message` → CASCADE `evidence` / `root_cause_hypothesis` / `fix_suggestion`。

设计原则：
- 无独立 Report 表（报告是 conversation + message 的只读视图，PRD FR-5 + domainModel §2.1）
- 无 SSE Event 表（重连回放走 Message 当前状态 + Evidence 表查询合成，无需事件日志）
- 无软删除（清空 = 硬删除，userFlow 流程 5）
- 单值对象扁平化到父表（RiskAssessment → message 表字段；ActuatorInfo → application 表 JSONB 快照）

---

## 2. 表结构

### 2.1 `admin` — 管理员

单用户，系统首次启动时创建初始密码。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `username` | VARCHAR(50) | NOT NULL, UNIQUE | 固定 `admin` |
| `password_hash` | VARCHAR(255) | NOT NULL | bcrypt ($2a$...) |
| `must_change_password` | BOOLEAN | NOT NULL, DEFAULT TRUE | 首次启动后强制改密 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

索引：
- PK: `admin_pkey` (id)
- `admin_username_key` (username UNIQUE)

### 2.2 `api_token` — API 令牌

明文仅在生成时返回一次，此后仅存 SHA-256 哈希。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `admin_id` | UUID | FK → admin(id) | MVP 恒指向唯一 admin |
| `name` | VARCHAR(100) | NOT NULL | 用户标签（如 "CI pipeline"） |
| `token_hash` | VARCHAR(64) | NOT NULL, UNIQUE | SHA-256(raw_token) |
| `prefix` | VARCHAR(12) | NOT NULL | 明文前 8 位 + `epi_`（UI 展示用，如 `epi_ab12cd34`） |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| `revoked_at` | TIMESTAMPTZ | | NULL = 有效 |

索引：
- PK: `api_token_pkey` (id)
- UNIQUE: `api_token_token_hash_key` (token_hash) — 验证时 O(1) 查找
- `idx_api_token_admin_id` (admin_id) — 列出用户 token

### 2.3 `application` — 被监控应用

PRD FR-6：单用户多应用。与 conversation 是一对多级联。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `name` | VARCHAR(200) | NOT NULL | 用户自定义名（如 "user-service"） |
| `actuator_url` | VARCHAR(500) | | NULL = 通用应用（无 Actuator） |
| `prometheus_label` | VARCHAR(200) | NOT NULL | Prometheus service label，诊断时拼接 PromQL 过滤 |
| `tags` | JSONB | DEFAULT '[]' | 标签列表，如 `["prod", "critical"]` |
| `actuator_info` | JSONB | | 最近一次 Actuator 探测快照（见 §3.1） |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

索引：
- PK: `application_pkey` (id)
- `idx_application_prometheus_label` (prometheus_label) — webhook 告警匹配应用时按 label 查找

### 2.4 `data_source` — 数据源配置

Prometheus / Elasticsearch 配置。全局共享，不属于特定应用。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `type` | VARCHAR(50) | NOT NULL | `PROMETHEUS` \| `ELASTICSEARCH` |
| `name` | VARCHAR(200) | NOT NULL | 显示名 |
| `url` | VARCHAR(500) | NOT NULL | API 基地址 |
| `auth_type` | VARCHAR(20) | NOT NULL, DEFAULT 'NONE' | `NONE` \| `BASIC` \| `BEARER` |
| `auth_config` | JSONB | | 加密存储的凭证。BASIC: {username, password}；BEARER: {token}。加密方式见 §4。API 响应从不包含 |
| `is_connected` | BOOLEAN | DEFAULT FALSE | 最近一次连通测试结果 |
| `metadata` | JSONB | | 类型特定配置（如 `{"defaultQueryStep": "15s", "indexPattern": "logs-*"}`） |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

索引：
- PK: `data_source_pkey` (id)
- `idx_data_source_type` (type) — 按类型查找连接器

### 2.5 `llm_provider` — LLM 后端配置

单行全局配置。apiKey 加密存储。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `provider` | VARCHAR(50) | NOT NULL | `OPENAI` \| `ANTHROPIC` \| `DEEPSEEK` \| `OLLAMA` \| `CUSTOM` |
| `model_name` | VARCHAR(200) | NOT NULL | 如 `gpt-5` / `claude-opus-4-8` |
| `api_key_encrypted` | TEXT | | 加密存储。OLLAMA 可为空 |
| `base_url` | VARCHAR(500) | | 自定义 endpoint（Ollama 必填，代理转发选填） |
| `is_connected` | BOOLEAN | DEFAULT FALSE | 最近一次连通测试 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

索引：
- PK: `llm_provider_pkey` (id)
- 单行表不需要业务索引

### 2.6 `conversation` — 诊断会话

PRD FR-1 + domainModel Conversation 聚合根。一个应用可有多个会话。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `application_id` | UUID | FK → application(id), NOT NULL, ON DELETE CASCADE | 所属应用（多应用隔离） |
| `title` | VARCHAR(500) | NOT NULL | 首条用户问题的截断（≤80 字符 UI 展示，DB 存完整以避免信息丢失） |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 最后一条消息时间 |

索引：
- PK: `conversation_pkey` (id)
- `idx_conversation_application_id` (application_id) — 按应用过滤会话列表
- `idx_conversation_updated_at` (updated_at DESC) — 历史列表排序（最近优先）
- `idx_conversation_title_trgm` USING GIN (title gin_trgm_ops) — 关键词 ILIKE 搜索（审查建议 2：pg_trgm 三元组索引，v1.0 前足够，v1.0 FR-16 引入时评估升级 tsvector）

### 2.7 `message` — 诊断消息

domainModel Message 实体。一条 USER 消息 + 一条 AGENT 消息构成一轮 Q&A。AGENT 消息承载诊断状态机。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `conversation_id` | UUID | FK → conversation(id), NOT NULL, ON DELETE CASCADE | |
| `role` | VARCHAR(10) | NOT NULL | `USER` \| `AGENT` |
| `content` | TEXT | NOT NULL | USER: 问题原文；AGENT: 最终回答全文 |
| `diagnosis_state` | VARCHAR(20) | | 仅 AGENT 有值：`CREATED`→`PLANNING`→`QUERYING`→`ANALYZING`→`COMPLETED`/`COMPLETED_PARTIAL`/`FAILED`/`ABORTED` |
| `failure_reason` | TEXT | | ABORTED/FAILED 时的原因。ABORTED："Server restart during diagnosis" / "Timeout exceeded"；FAILED："LLM returned unrecoverable error" 等 |
| `risk_level` | VARCHAR(20) | | RiskAssessment.level：`LOW` \| `MEDIUM` \| `HIGH` \| `CRITICAL` |
| `risk_impact` | TEXT | | 如果不修复的影响描述 |
| `risk_urgency` | TEXT | | 修复时限建议 |
| `token_count` | INTEGER | | LLM 消耗 token 数（终态后填入） |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| `completed_at` | TIMESTAMPTZ | | 诊断终态时填入 |

约束：
- `diagnosis_state` 仅 `role='AGENT'` 时允许非 NULL
- `failure_reason` 仅 `diagnosis_state` 为 ABORTED 或 FAILED 时允许非 NULL
- `risk_level` / `risk_impact` / `risk_urgency` 仅 `diagnosis_state` 为 COMPLETED 或 COMPLETED_PARTIAL 时允许非 NULL
- `token_count` 仅 `diagnosis_state` 为终态（COMPLETED/COMPLETED_PARTIAL/FAILED/ABORTED）时允许非 NULL

索引：
- PK: `message_pkey` (id)
- `idx_message_conversation_order` (conversation_id, created_at) — 会话内消息有序查询 + 历史回放
- `idx_message_diagnosis_state` (diagnosis_state) WHERE diagnosis_state IN ('CREATED','PLANNING','QUERYING','ANALYZING') — 查找进行中的诊断 + 启动扫描悬挂 Message（部分索引，避免全表扫描）

### 2.8 `evidence` — 诊断证据

domainModel Evidence 值对象。每条 AGENT 消息可有多条证据。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `message_id` | UUID | FK → message(id), NOT NULL, ON DELETE CASCADE | |
| `source` | VARCHAR(50) | NOT NULL | `PROMETHEUS` \| `ELASTICSEARCH` \| `ACTUATOR` |
| `query_text` | TEXT | NOT NULL | 实际执行的查询（PromQL / ES DSL / Actuator endpoint） |
| `summary` | TEXT | NOT NULL | LLM 对查询结果的摘要 |
| `anomaly_start` | TIMESTAMPTZ | | 异常区间起始 |
| `anomaly_end` | TIMESTAMPTZ | | 异常区间结束 |
| `collected_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

设计要点：不存储原始数据（定位原则）。`query_text` + `summary` 足够支撑报告全文。

索引：
- PK: `evidence_pkey` (id)
- `idx_evidence_message_id` (message_id) — 查询单轮诊断的全部证据（报告合成使用）

### 2.9 `root_cause_hypothesis` — 根因假设

domainModel RootCauseHypothesis 值对象。每条 AGENT 消息 0-3 条（0 条 = 证据不足以形成假设，对应 COMPLETED_PARTIAL）。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `message_id` | UUID | FK → message(id), NOT NULL, ON DELETE CASCADE | |
| `rank` | SMALLINT | NOT NULL, CHECK (rank BETWEEN 1 AND 3) | 置信度排名 |
| `description` | TEXT | NOT NULL | 根因假设描述 |
| `confidence` | DOUBLE PRECISION | CHECK (confidence BETWEEN 0.0 AND 1.0) | LLM 自行评估的置信度（非统计量——报告附免责声明） |
| `supporting_evidence_ids` | JSONB | DEFAULT '[]' | 引用的 evidence.id 列表，如 `["uuid1", "uuid2"]` |

约束：
- UNIQUE (message_id, rank) — 同一消息内 rank 不重复

索引：
- PK: `root_cause_hypothesis_pkey` (id)
- UNIQUE: `root_cause_hypothesis_message_rank_key` (message_id, rank)
- `idx_hypothesis_message_id` (message_id) — 查询单轮诊断的全部假设

### 2.10 `fix_suggestion` — 修复建议

domainModel FixSuggestion 值对象。MVP 所有建议 `auto_execution_allowed = FALSE`（审查建议 1：合并 complexity+isSafe）。

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `message_id` | UUID | FK → message(id), NOT NULL, ON DELETE CASCADE | |
| `description` | TEXT | NOT NULL | 修复步骤说明 |
| `auto_execution_allowed` | BOOLEAN | NOT NULL, DEFAULT FALSE | MVP 一律 FALSE。v1.5 扩展为安全分级 |

索引：
- PK: `fix_suggestion_pkey` (id)
- `idx_suggestion_message_id` (message_id)

---

## 3. JSONB 字段约定

### 3.1 `application.actuator_info` 结构

```json
{
  "healthStatus": "UP",
  "healthDetails": "All components OK",
  "metrics": {
    "jvm.memory.max": "2048MB",
    "jvm.memory.used": "1024MB",
    "process.cpu.usage": "0.15"
  },
  "env": {
    "SPRING_PROFILES_ACTIVE": "prod",
    "SERVER_PORT": "8080"
  },
  "info": {
    "version": "1.2.3",
    "java.version": "21.0.1"
  },
  "probedAt": "2026-07-18T14:00:00Z"
}
```

注：`env` 仅包含白名单 key，含 `password`/`secret`/`key`/`token` 的变量名一律跳过不存储。

### 3.2 `data_source.auth_config` — 加密存储

| auth_type | auth_config 结构（加密前） |
|-----------|--------------------------|
| NONE | `{}` |
| BASIC | `{"username": "...", "password": "..."}` |
| BEARER | `{"token": "..."}` |

### 3.3 `root_cause_hypothesis.supporting_evidence_ids`

JSON 数组，元素为 evidence UUID 字符串：`["550e8400-...", "550e8401-..."]`。仅引用，不硬外键（避免循环依赖）。报告合成时按 id 批量查 evidence 表补齐详情。

---

## 4. 加密存储策略

以下字段在应用层加密后写入，从数据库层读取的始终是密文：

| 表 | 字段 | 加密原因 |
|----|------|---------|
| `data_source.auth_config` | 全部值 | Prometheus/ES 凭证 |
| `llm_provider.api_key_encrypted` | 全部值 | LLM API Key |

加密方案：AES-256-GCM，密钥通过环境变量 `EPIPHANEIA_ENCRYPTION_KEY` 注入。实现层在基础设施模块（ArchitectureDesign 阶段确定具体方案）。dbSchema 阶段只要求：**字段类型足够大（JSONB/TEXT）+ 不存明文这个约束。**

`admin.password_hash` 走 bcrypt 单向哈希，不在此列。

API 响应与日志中永不输出明文凭证（PRD FR-10 + apiDesign §5/§6：authConfig/apiKey 永不回显）。

---

## 5. 索引策略汇总

| 表 | 索引 | 类型 | 用途 |
|----|------|------|------|
| admin | PK | B-tree | |
| admin | username UNIQUE | B-tree | 登录查找 |
| api_token | PK | B-tree | |
| api_token | token_hash UNIQUE | B-tree (hash) | API 调用时 Token 验证——最高频的验证查询，必须唯一索引 |
| api_token | admin_id | B-tree | 列表查询 |
| application | PK | B-tree | |
| application | prometheus_label | B-tree | Webhook 告警匹配应用 |
| data_source | PK | B-tree | |
| data_source | type | B-tree | 按类型查找连接器 |
| llm_provider | PK | B-tree | 单行表，无业务索引 |
| conversation | PK | B-tree | |
| conversation | application_id | B-tree | 按应用过滤会话 |
| conversation | updated_at DESC | B-tree | 历史列表排序 |
| conversation | title | GIN (pg_trgm) | 关键词 ILIKE 搜索。需 `CREATE EXTENSION IF NOT EXISTS pg_trgm` |
| message | PK | B-tree | |
| message | (conversation_id, created_at) | B-tree | 会话内消息分页查询 |
| message | diagnosis_state (partial) | B-tree | 仅索引进行中的状态，查找活跃诊断 |
| evidence | PK | B-tree | |
| evidence | message_id | B-tree | 单轮诊断的全部证据查询 |
| root_cause_hypothesis | PK | B-tree | |
| root_cause_hypothesis | (message_id, rank) UNIQUE | B-tree | 单轮诊断假设排序 |
| fix_suggestion | PK | B-tree | |
| fix_suggestion | message_id | B-tree | 单轮诊断的建议查询 |

设计要点：
- 无冗余索引——每个索引对应 apiDesign 中至少一条查询路径
- `pg_trgm` 索引是 MVP 最重的存储投入（三元组约 3-5 倍原文大小），但 v1.0 知识库引入前足够——避免过早引入 `tsvector`
- `api_token.token_hash` 用 B-tree hash 索引即可——SHA-256 固定长度，hash 索引比 B-tree 更小更快。但 PostgreSQL B-tree 对固定长度字符串性能已足够，不引入额外复杂度

---

## 6. 初始化 DDL 骨架

```sql
-- 首次部署时执行（schema-only，不含种子数据）

CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";   -- conversation title 三元组搜索

CREATE TABLE admin (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE api_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES admin(id),
    name VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    prefix VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_api_token_admin_id ON api_token(admin_id);

CREATE TABLE application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    actuator_url VARCHAR(500),
    prometheus_label VARCHAR(200) NOT NULL,
    tags JSONB DEFAULT '[]',
    actuator_info JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_application_prometheus_label ON application(prometheus_label);

CREATE TABLE data_source (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    auth_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    auth_config JSONB,
    is_connected BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_data_source_type ON data_source(type);

CREATE TABLE llm_provider (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(200) NOT NULL,
    api_key_encrypted TEXT,
    base_url VARCHAR(500),
    is_connected BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversation_application_id ON conversation(application_id);
CREATE INDEX idx_conversation_updated_at ON conversation(updated_at DESC);
CREATE INDEX idx_conversation_title_trgm ON conversation USING GIN (title gin_trgm_ops);

CREATE TABLE message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'AGENT')),
    content TEXT NOT NULL,
    diagnosis_state VARCHAR(20),
    failure_reason TEXT,
    risk_level VARCHAR(20),
    risk_impact TEXT,
    risk_urgency TEXT,
    token_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_message_conversation_order ON message(conversation_id, created_at);
CREATE INDEX idx_message_diagnosis_state ON message(diagnosis_state)
    WHERE diagnosis_state IN ('CREATED', 'PLANNING', 'QUERYING', 'ANALYZING');

CREATE TABLE evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    source VARCHAR(50) NOT NULL,
    query_text TEXT NOT NULL,
    summary TEXT NOT NULL,
    anomaly_start TIMESTAMPTZ,
    anomaly_end TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_evidence_message_id ON evidence(message_id);

CREATE TABLE root_cause_hypothesis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    rank SMALLINT NOT NULL CHECK (rank BETWEEN 1 AND 3),
    description TEXT NOT NULL,
    confidence DOUBLE PRECISION CHECK (confidence BETWEEN 0.0 AND 1.0),
    supporting_evidence_ids JSONB DEFAULT '[]',
    UNIQUE (message_id, rank)
);
CREATE INDEX idx_hypothesis_message_id ON root_cause_hypothesis(message_id);

CREATE TABLE fix_suggestion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    auto_execution_allowed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_suggestion_message_id ON fix_suggestion(message_id);
```

---

## 7. 种子数据（首次启动时写入）

```sql
-- admin 初始密码在生产代码中生成随机值，此处为示例
INSERT INTO admin (username, password_hash, must_change_password)
VALUES ('admin', '$2a$10$...', TRUE);

-- llm_provider 占位行（引导阶段为空，等待用户配置）
INSERT INTO llm_provider (provider, model_name, is_connected)
VALUES ('OPENAI', '', FALSE);
```

引导阶段（首次启动）逻辑：`GET /api/v1/system/status` 返回 `bootstrapped=false` → UI 走 P2 向导 → `PUT /api/v1/llm` 更新 → `bootstrapped=true`。

---

## 8. 迁移策略（ArchitectureDesign + Development 阶段执行）

MVP 无历史数据，迁移策略简单：

| 原则 | 说明 |
|------|------|
| 工具 | Flyway（Java 生态标配）或 Liquibase。ArchitectureDesign 做最终选型 |
| 命名 | `V{序号}__{描述}.sql`，如 `V001__init_schema.sql`、`V002__add_xxx.sql` |
| 方向 | 不可逆变更（DEFAULT 值修改可直接跑；删列分两步：标记废弃 → 下版本真删） |
| 回滚 | 不做自动回滚——向前修复是更安全的删改策略（ponytail: Flyway undo 插件增加维护负担，手动脚本够用） |
| 测试 | CI 中 `docker compose up` 后自动跑 Flyway → 写一条种子会话验证所有表可读写 |

v1.0 知识库（FR-16）引入时，首版迁移新增 `knowledge_entry` 表和 `tsvector` 索引（取代 pg_trgm 或互补）。

---

## 9. 一致性验证（对照 domainModel）

| domainModel 概念 | 数据库映射 | 策略 |
|------------------|-----------|------|
| Conversation（聚合根） | `conversation` 表 | 直接映射 |
| Message（实体） | `message` 表 + FK→conversation | 聚合内实体，ON DELETE CASCADE |
| Evidence（值对象） | `evidence` 表 + FK→message | 1:N 关系（单表扁平化） |
| RootCauseHypothesis（值对象） | `root_cause_hypothesis` 表 + FK→message | 1:N，rank 列维护排序 |
| FixSuggestion（值对象） | `fix_suggestion` 表 + FK→message | 1:N |
| RiskAssessment（值对象） | `risk_level` / `risk_impact` / `risk_urgency` 在 message 表 | 1:1 扁平化到父表 |
| Application（聚合根） | `application` 表 | 直接映射 |
| ActuatorInfo（值对象） | `application.actuator_info` JSONB | 快照序列化 |
| DataSource（聚合根） | `data_source` 表 | 直接映射 + JSONB 元数据 |
| LLMProvider（实体） | `llm_provider` 表 | 单行全局配置 |
| Admin（聚合根） | `admin` 表 | 单行 |
| ApiToken（实体） | `api_token` 表 + FK→admin | 聚合内实体 |
| DiagnosisState（枚举） | `message.diagnosis_state` VARCHAR | PostgreSQL 原生 enum 可选，VARCHAR 更易迁移——ArchitectureDesign 做最终选型 |
| Report（只读视图） | **无对应表** | 符合 PRD FR-5 + domainModel 设计决策 |
| DiagnosisEvent（无领域实体） | **无对应表** | SSE 重连走 Message + Evidence 查询合成 |

---

> 一致性检查清单（退出条件之一——“领域模型、API、数据库三者一致”）：
> - [ ] 每个 domainModel 聚合/实体/值对象有对应 DB 表或字段 → ✅
> - [ ] 每个 apiDesign 端点有对应表支撑查询 → ✅
> - [ ] 无独立 report 表（PRD FR-5 = domainModel §2.1 = dbSchema 无表） → ✅
> - [ ] `conversation.title` 有 pg_trgm GIN 索引（审查建议 2） → ✅
> - [ ] `fix_suggestion` 字段为 `auto_execution_allowed`（审查建议 1） → ✅
> - [ ] 凭证字段加密标注（handoff 1.2/1.6 安全约束） → ✅
>
> 下一步：本阶段全部产出物完成（PRD / userFlow / domainModel / apiDesign / dbSchema / architectureDesignHandoff / productPlanningReview），等待用户最终确认后退出 ProductPlanning 阶段。
