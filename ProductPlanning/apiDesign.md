# API 设计 — Epiphaneia MVP

> 版本：v1.0-draft | 日期：2026-07-18
> 上游：`PRD.md` v1.0、`domainModel.md` v1.0-draft、`userFlow.md` v1.0-draft
> 格式：OpenAPI 3.0 语义描述。完整 YAML 在开发阶段产出，本文档为精确契约草案。
> 原则：API First——Web UI 是 API 的第一个消费者，不是唯一消费者（PRD FR-8）。

---

## 1. 基础约定

| 项 | 值 |
|----|-----|
| Base URL | `http://localhost:8080/api/v1` |
| 认证方式 | Web UI: Cookie Session（`Set-Cookie`）。外部调用: `Authorization: Bearer <token>` |
| Content-Type | `application/json`，SSE 端点为 `text/event-stream` |
| 速率限制 | 登录端点 5 次/分钟；API 通用 100 次/分钟（草案，架构阶段确定） |
| 版本策略 | URL 前缀 `/api/v1`。向前兼容：新增字段不断路，废弃字段至少保留一个 minor 版本 |

### 1.1 统一错误响应体

```json
{
  "error": {
    "code": "DIAGNOSIS_TIMEOUT",
    "message": "Diagnosis exceeded 120s limit, partial results available.",
    "details": {}
  }
}
```

### 1.2 统一列表响应体

```json
{
  "data": [...],
  "total": 42
}
```

分页参数（可选）：`?page=0&size=20`，缺省 `page=0, size=20`。

---

## 2. 认证端点 — `/auth`

### POST `/auth/login`

Web UI 登录。

```
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "admin", "password": "..." }
```

| 响应 | 状态码 | 说明 |
|------|--------|------|
| 成功 | 200 | `{ "mustChangePassword": true }` → 前端强制跳转 P2 向导 |
| 失败 | 401 | `{ "error": { "code": "INVALID_CREDENTIALS" } }` |
| 限速 | 429 | 连续 5 次失败后触发，60 秒窗口 |

验收：
- 正常：正确密码返回 200 + Set-Cookie；mustChangePassword=true 时 UI 跳转改密码
- 异常：错误密码 401；第 6 次尝试 429
- 边界：初始密码（仅首次启动有效）与修改后密码行为一致

### POST `/auth/change-password`

```
POST /api/v1/auth/change-password
Authorization: Bearer <token>  (or session cookie)

{ "currentPassword": "...", "newPassword": "..." }
```

| 状态码 | 说明 |
|--------|------|
| 200 | `{ "message": "Password changed" }`，mustChangePassword 置 false |
| 400 | 新密码不符合复杂度（≥8 位，含字母+数字，草案） |
| 401 | 未认证或当前密码错误 |
| 403 | 当前密码正确但新密码与当前相同 |

验收：
- 正常：改密成功，mustChangePassword 变 false
- 异常：当前密码错误 → 401；新密码太短 → 400
- 边界：改密后旧 token 仍有效（简化——不做强制会话失效）

### GET `/auth/tokens`

列出 API Token（ID、名称、前缀、创建时间、吊销状态）。

```
GET /api/v1/auth/tokens
Authorization: Bearer <token>
→ 200: { "data": [{ "id": "..", "name": "CI", "prefix": "epi_ab12...", "createdAt": "...", "revokedAt": null }], "total": 1 }
```

### POST `/auth/tokens`

生成新 Token（明文仅响应一次）。

```
POST /api/v1/auth/tokens
Authorization: Bearer <token>
Content-Type: application/json

{ "name": "CI pipeline" }
```

| 状态码 | 说明 |
|--------|------|
| 201 | `{ "id": "..", "name": "CI pipeline", "token": "epi_xxxxxxxx...", "prefix": "epi_ab12...", "createdAt": "..." }` —— 明文仅此一次 |
| 400 | name 为空或重复 |
| 401 | 未认证 |

验收：
- 正常：创建成功，返回明文 token；再次 GET `/auth/tokens` 只显示前缀
- 异常：未认证 → 401；name 重复 → 400
- 边界：token 长度 ≥32 字符随机生成；前缀 `epi_` 可识别

### DELETE `/auth/tokens/{id}`

吊销 Token。

```
DELETE /api/v1/auth/tokens/{id}
Authorization: Bearer <token>
→ 200: { "message": "Token revoked" }
```

| 状态码 | 说明 |
|--------|------|
| 200 | 吊销成功 |
| 404 | Token 不存在 |
| 409 | Token 已吊销（幂等返回 200 亦可，文档选 200） |

验收：
- 正常：吊销后该 token 立即不可用
- 异常：拿被吊销的 token 调其他 API → 401
- 边界：重复吊销 → 200（幂等）

---

## 3. 系统端点 — `/system`

### GET `/system/status`

获取系统引导状态。用于 P2 向导——判断是否需要首启配置。

```
GET /api/v1/system/status
→ 200: {
  "bootstrapped": false,
  "checks": {
    "adminPasswordChanged": false,
    "llmConfigured": false,
    "datasourceConfigured": false
  }
}
```

验收：
- 正常：首次启动 → bootstrapped=false；全部配置完成 → bootstrapped=true
- 异常：DB 连接失败 → 503
- 边界：此端点不做认证——引导阶段用户尚未登录（引导流程中 Step 1 改密码在前，登录在后）

---

## 4. 应用管理端点 — `/applications`

### GET `/applications`

```
GET /api/v1/applications
Authorization: Bearer <token>
→ 200: { "data": [{ "id": "..", "name": "user-service", "actuatorUrl": "..", "prometheusLabel": "user-service", "tags": ["prod"], "actuatorInfo": {...}, "createdAt": ".." }], "total": 1 }
```

### POST `/applications`

```
POST /api/v1/applications
Authorization: Bearer <token>

{ "name": "user-service", "actuatorUrl": "http://user-svc:8080/actuator", "prometheusLabel": "user-service", "tags": ["prod"] }
```

| 状态码 | 说明 |
|--------|------|
| 201 | 创建成功，返回 Application 对象（含自动探测的 actuatorInfo） |
| 400 | name 为空 |
| 409 | name 已存在（同名的应用名称冲突） |

验收：
- 正常：创建后自动触发 Actuator 探测（后端异步），响应立即返回（actuatorInfo 为 null，前端轮询或 SSE 推送更新）
- 异常：prometheusLabel 为空 → 默认取 name 值
- 边界：tags 可选；actuatorUrl 为空 → 降级为通用应用

### GET `/applications/{id}`

```
GET /api/v1/applications/{id}
Authorization: Bearer <token>
→ 200: { "id": "..", ... }
→ 404
```

### PUT `/applications/{id}`

```
PUT /api/v1/applications/{id}
Authorization: Bearer <token>

{ "name": "user-service-v2", "actuatorUrl": "..", "prometheusLabel": "..", "tags": ["prod", "critical"] }
```

| 状态码 | 说明 |
|--------|------|
| 200 | 更新成功 |
| 400 | name 为空 |
| 404 | 不存在 |
| 409 | 新 name 与已有应用冲突 |

### DELETE `/applications/{id}`

级联删除该应用下全部 Conversation 及 Message。

```
DELETE /api/v1/applications/{id}
Authorization: Bearer <token>
→ 200: { "message": "Application and all its conversations deleted" }
→ 404
```

验收：
- 正常：删除后 GET 列表不包含；关联 conversation 一并删除
- 异常：不存在 → 404
- 边界：当前 P3 正在该应用的诊断进行中 → 删除不中断管道（管道独立，完成后存档时发现 app 已不存在 → 诊断结果丢失，可接受）

### POST `/applications/{id}/probe`

手动触发 Actuator 探测。

```
POST /api/v1/applications/{id}/probe
Authorization: Bearer <token>
→ 202: { "message": "Probe started" } (异步)
→ 404
```

验收：
- 正常：探测完成后 actuatorInfo 更新
- 异常：Actuator 不可达 → actuatorInfo.healthStatus = "UNREACHABLE"
- 边界：通用应用（无 actuatorUrl）→ 400 "Probe not supported for generic applications"

---

## 5. 数据源端点 — `/datasources`

### GET `/datasources`

```
GET /api/v1/datasources
Authorization: Bearer <token>
→ 200: { "data": [{ "id": "..", "type": "PROMETHEUS", "name": "Production Prometheus", "url": "http://prom:9090", "isConnected": true, "metadata": {}, "createdAt": ".." }], "total": 1 }
```

注：响应中从不包含 `authConfig`（凭证不回显）。

### POST `/datasources`

```
POST /api/v1/datasources
Authorization: Bearer <token>

{
  "type": "PROMETHEUS",
  "name": "Production Prometheus",
  "url": "http://prom:9090",
  "authConfig": { "type": "NONE" },
  "metadata": { "defaultQueryStep": "15s" }
}
```

| 状态码 | 说明 |
|--------|------|
| 201 | 创建成功（authConfig 不回显） |
| 400 | type 不在允许列表中；url 格式无效 |
| 409 | 同 type 的 data source 已存在（MVP 每类型各只有一个） |

验收：
- 正常：创建成功后 `isConnected` 自动填充（后端触发连通测试）
- 异常：无效 URL → 400；重复 type → 409
- 边界：authConfig.type = "BASIC" → 额外需要 username/password；type = "BEARER" → 需要 token

### GET `/datasources/{id}`

```
GET /api/v1/datasources/{id}
Authorization: Bearer <token>
→ 200
→ 404
```

### PUT `/datasources/{id}`

```
PUT /api/v1/datasources/{id}
Authorization: Bearer <token>

{ "name": "..", "url": "..", "authConfig": {...}, "metadata": {...} }
```

| 状态码 | 说明 |
|--------|------|
| 200 | 更新成功 |
| 400 | url 格式无效 |
| 404 | 不存在 |

验收：
- 正常：更新后若 URL 或 authConfig 变化，自动触发连通测试并更新 isConnected
- 边界：PUT 中不传 authConfig → 保留原凭证不修改（非全量替换语义）

### DELETE `/datasources/{id}`

```
DELETE /api/v1/datasources/{id}
Authorization: Bearer <token>
→ 200: { "message": "Deleted" }
→ 404
```

### POST `/datasources/{id}/test`

手动连通测试。

```
POST /api/v1/datasources/{id}/test
Authorization: Bearer <token>
→ 200: { "isConnected": true, "latencyMs": 12, "message": "OK" }
→ 200: { "isConnected": false, "message": "Connection refused: http://prom:9090" }
→ 404
```

---

## 6. LLM 端点 — `/llm`

### GET `/llm`

```
GET /api/v1/llm
Authorization: Bearer <token>
→ 200: { "provider": "OPENAI", "modelName": "gpt-5", "baseUrl": null, "isConnected": true }
→ 404: LLM 未配置（引导阶段）
```

注：apiKey 从不回显。baseUrl 为 null 表示使用提供商默认地址。

### PUT `/llm`

```
PUT /api/v1/llm
Authorization: Bearer <token>

{ "provider": "OPENAI", "modelName": "gpt-5", "apiKey": "sk-...", "baseUrl": null }
```

| 状态码 | 说明 |
|--------|------|
| 200 | 更新成功 |
| 400 | provider 不在 `OPENAI | ANTHROPIC | DEEPSEEK | OLLAMA | CUSTOM` 中 |
| 400 | OLLAMA 且 baseUrl 为空（本地模型必须指定地址） |

验收：
- 正常：更新后切换 LLM 无需重启（FR-12）
- 异常：无效 provider → 400
- 边界：不传 apiKey → 保留原值（非全量替换语义，与 Datasource PUT 一致）

### POST `/llm/test`

```
POST /api/v1/llm/test
Authorization: Bearer <token>
→ 200: { "isConnected": true, "latencyMs": 230, "modelName": "gpt-5" }
→ 200: { "isConnected": false, "message": "401 Unauthorized - invalid API key" }
→ 412: LLM 尚未配置
```

---

## 7. 会话端点 — `/conversations`

### GET `/conversations`

列出会话。支持按应用和关键词过滤。

```
GET /api/v1/conversations?appId={id}&q=timeout&page=0&size=20
Authorization: Bearer <token>
→ 200: {
  "data": [{
    "id": "..",
    "applicationId": "..",
    "applicationName": "user-service",
    "title": "Why is user-service p99 latency high?",
    "messageCount": 5,
    "lastState": "COMPLETED",
    "createdAt": "..",
    "updatedAt": ".."
  }],
  "total": 1
}
```

| 状态码 | 说明 |
|--------|------|
| 200 | 空数组表示无匹配 |
| 400 | appId 格式无效 |

验收：
- 正常：按 appId 过滤且按 updatedAt 倒序；关键词匹配 title（ILIKE，审查建议 2：pg_trgm 索引由 dbSchema 实现）
- 异常：空列表 → 200 + empty data
- 边界：无 appId → 返回全部应用（跨应用混合列表）

### POST `/conversations`

创建新会话并发送首条问题，同步进入诊断管道。返回 SSE 流。

```
POST /api/v1/conversations
Authorization: Bearer <token>
Content-Type: application/json

{ "applicationId": "..", "question": "Why is user-service p99 latency 2s?" }
```

| 状态码 | Content-Type | 说明 |
|--------|-------------|------|
| 201 | `text/event-stream` | 创建成功，SSE 流建立 |
| 400 | `application/json` | applicationId 为空或不存在、question 为空 |
| 412 | `application/json` | LLM 未配置 → `{ "error": { "code": "LLM_NOT_CONFIGURED" } }` |
| 503 | `application/json` | 诊断管道不可用 |

SSE 事件流（首个事件含 conversation ID，后续与 §9 格式一致）：

```
event: state
data: {"state":"CREATED","conversationId":"<uuid>","messageId":"<uuid>","timestamp":"..."}

event: state
data: {"state":"PLANNING","conversationId":"...","messageId":"...","timestamp":"..."}

event: step
data: {"type":"QUERY","description":"Querying Prometheus: rate(http_requests_total[5m])","query":"rate(http_requests_total{service=\"user-service\"}[5m])","source":"PROMETHEUS","timestamp":"..."}

event: token
data: {"text":"The root cause appears to be..."}

event: done
data: {"state":"COMPLETED","conversationId":"...","messageId":"...","timestamp":"..."}
```

验收：
- 正常：SSE 事件序列符合 userFlow §3 状态机迁移
- 异常：LLM 未配置 → 412（不进管道）；数据源全部不可达 → 诊断仍进入 ANALYZING → COMPLETED_PARTIAL（降级，对应 FR-2/3）
- 边界：同一应用已有活跃会话时，POST 创建新会话——允许多会话共存（userFlow P5 可回头继续之前的）；前端自动切换到新会话
- 边界：超时（120s）→ `done{"state":"COMPLETED_PARTIAL"}` + error 事件注明"timeout"

### GET `/conversations/{id}`

获取会话详情（全部消息）。

```
GET /api/v1/conversations/{id}
Authorization: Bearer <token>
→ 200: {
  "id": "..",
  "applicationId": "..",
  "title": "..",
  "messages": [{
    "id": "..",
    "role": "USER",
    "content": "Why is user-service slow?",
    "createdAt": ".."
  }, {
    "id": "..",
    "role": "AGENT",
    "content": "The root cause appears to be...",
    "diagnosisState": "COMPLETED",
    "evidence": [{ "source": "PROMETHEUS", "query": "...", "summary": "...", "anomalyWindow": { "start": "...", "end": "..." }, "collectedAt": "..." }],
    "hypotheses": [{ "rank": 1, "description": "...", "confidence": 0.85, "supportingEvidence": [...] }],
    "suggestions": [{ "description": "...", "autoExecutionAllowed": false }],
    "riskAssessment": { "level": "HIGH", "impact": "...", "urgency": "..." },
    "tokenCount": 3420,
    "createdAt": "..",
    "completedAt": ".."
  }],
  "createdAt": "..",
  "updatedAt": ".."
}
```

验证：响应结构直接映射 domainModel Conversation 聚合 + Message 实体。

### DELETE `/conversations/{id}`

清空会话（硬删除，级联删除 Message）。

```
DELETE /api/v1/conversations/{id}
Authorization: Bearer <token>
→ 200: { "message": "Conversation and all messages deleted" }
→ 404
```

验收：
- 正常：删除后在 GET 列表中不出现
- 异常：不存在 → 404
- 边界：删除的是 P3 当前活跃会话 → 前端回退到该应用空白状态或最近另一会话（前端逻辑，API 不管）

### POST `/conversations/{id}/messages`

在已有会话中追加问题（追问）。返回 SSE 流，格式同 POST `/conversations`。

```
POST /api/v1/conversations/{id}/messages
Authorization: Bearer <token>

{ "question": "Which table does this slow query hit?" }
```

| 状态码 | 说明 |
|--------|------|
| 200 | SSE 流建立 |
| 400 | question 为空 |
| 404 | conversation 不存在 |
| 409 | 该会话的上一轮诊断仍在进行中（一个会话同时只有一个诊断运行） |
| 412 | LLM 未配置 |

验收：
- 正常：新 AGENT Message 追加进 Conversation，SSE 推送本轮诊断过程
- 异常：上一轮诊断未完成 → 409（"A diagnosis is already in progress for this conversation"）
- 边界：多轮追问后上下文窗口增长——截断策略在 Agent 编排框架实现（基础设施层，API 不感知）

### GET `/conversations/{id}/events`

SSE 重连端点。客户端断连后拉取增量事件补齐。

```
GET /api/v1/conversations/{id}/events?since={timestamp}
Authorization: Bearer <token>
Accept: text/event-stream
→ 200 (SSE): 回放 since 之后的所有增量事件
→ 404
```

验收：
- 正常：since 指定后只推送该时间戳之后的事件
- 异常：conversation 不存在 → 404
- 边界：since 之前的事件全部跳过；since 为空 → 全量推送（等同于首次连接）

### GET `/conversations/{id}/report`

会话级报告实时合成。不缓存——每次调用当场基于最新消息合成。

```
GET /api/v1/conversations/{id}/report
Authorization: Bearer <token>
Accept: application/json 或 text/markdown
```

| Accept | 响应 |
|--------|------|
| `application/json` | `{ "conversationId": "..", "markdown": "# Diagnosis Report\n\n...", "synthesizedAt": "..", "messageCount": 5 }` |
| `text/markdown` | 原始 Markdown 文本（浏览器可触发下载） |

| 状态码 | 说明 |
|--------|------|
| 200 | 报告合成成功 |
| 400 | 会话无消息 → `{ "error": { "code": "EMPTY_CONVERSATION", "message": "No diagnosis content to report" } }` |
| 404 | conversation 不存在 |

验收：
- 正常：合成结果基于全部已完成的 AGENT Message（跳过进行中的诊断）
- 异常：空会话 → 400
- 边界：会话包含 FAILED 消息 → 报告如实包含失败标注，不影响整体合成
- 边界：两次连续 GET（不追问）→ 内容相同（无新消息 = 同上下文 = 同报告，LLM 非确定性可能轻微不同，标注"报告基于最新上下文实时生成"）

---

## 8. Webhook 端点 — `/webhooks`（v0.95，契约先行）

### POST `/webhooks/alertmanager`

```
POST /api/v1/webhooks/alertmanager
Authorization: Bearer <token>
Content-Type: application/json

{
  "receiver": "epiphaneia",
  "status": "firing",
  "alerts": [{
    "labels": { "service": "user-service", "severity": "critical" },
    "annotations": { "summary": "p99 latency > 2s", "description": "..." },
    "startsAt": "2026-07-18T14:02:00Z"
  }]
}
```

| 版本 | 状态码 | 说明 |
|------|--------|------|
| v0.9 | 501 | `{ "error": { "code": "NOT_IMPLEMENTED", "message": "Webhook support planned for v0.95. See docs: /api/v1/docs#webhooks" } }` |
| v0.95 | 202 | 告警接收入队，异步进入诊断管道（复用 POST /conversations 的管道逻辑） |
| v0.95 | 400 | 请求体不符合 AlertManager webhook 格式 |

验收（v0.95）：
- 正常：告警标签 service=user-service → 自动匹配 Application.prometheusLabel → 创建 Conversation → 进入诊断管道
- 异常：service label 不匹配任何已配置 Application → 400 "Unknown service: xxx"
- 边界：同一告警重复发送（AlertManager 的 group_interval 机制）→ 幂等处理（基于 fingerprint，24h 窗口去重）

---

## 9. SSE 事件格式规范

所有 SSE 端点（`POST /conversations`、`POST /conversations/{id}/messages`、`GET /conversations/{id}/events`）使用统一事件格式。

| 事件类型 | 方向 | 载荷 | 说明 |
|----------|------|------|------|
| `state` | S→C | `{ "state": "QUERYING", "conversationId": "..", "messageId": "..", "timestamp": ".." }` | 状态机迁移 |
| `step` | S→C | `{ "type": "QUERY"|"DISCOVERY"|"ANALYSIS", "description": "..", "query": "..", "source": "PROMETHEUS"|"ELASTICSEARCH"|"ACTUATOR", "timestamp": ".." }` | 过程步骤（type=QUERY 时含实际查询语句，对标 userFlow FR-2/3 要求） |
| `token` | S→C | `{ "text": "The root..." }` | LLM 结论流式 token |
| `done` | S→C | `{ "state": "COMPLETED", "conversationId": "..", "messageId": "..", "tokenCount": 3420, "timestamp": ".." }` | 终态 |
| `error` | S→C | `{ "code": "..", "message": "..", "conversationId": "..", "messageId": ".." }` | 诊断过程错误（非终态错误，诊断继续降级或失败） |
| `close` | S→C | `{ "reason": "manual_stop"\|"timeout"\|"server_error", "conversationId": ".." }` | SSE 通道关闭（客户端主动关闭或服务端异常） |

---

## 10. 错误码汇总

| HTTP | 错误 code | 使用场景 |
|------|----------|---------|
| 400 | `VALIDATION_ERROR` | 请求参数校验失败 |
| 400 | `EMPTY_CONVERSATION` | 空会话请求报告 |
| 400 | `PROBE_NOT_SUPPORTED` | 通用应用请求 Actuator 探测 |
| 401 | `UNAUTHORIZED` | 未认证或 token 无效/已吊销 |
| 401 | `INVALID_CREDENTIALS` | 登录密码错误 |
| 403 | `SAME_PASSWORD` | 改密时新旧密码相同 |
| 404 | `NOT_FOUND` | 资源不存在 |
| 409 | `DIAGNOSIS_IN_PROGRESS` | 会话上轮诊断未完成时追加问题 |
| 409 | `RESOURCE_CONFLICT` | 同 type DataSource 或同名 Application 已存在 |
| 412 | `LLM_NOT_CONFIGURED` | 提问时 LLM 尚未配置 |
| 412 | `LLM_NOT_CONFIGURED` | /llm/test 时 LLM 未配置 |
| 429 | `RATE_LIMITED` | 登录或 API 频率超限 |
| 500 | `INTERNAL_ERROR` | 服务器内部错误 |
| 501 | `NOT_IMPLEMENTED` | v0.9 的 webhook 端点 |
| 503 | `SERVICE_UNAVAILABLE` | 诊断管道不可用 |

---

## 11. 验收清单（可测试性）

> 每 API 的完整验收标准在各自端点章节。此处为跨端点一致性抽检。

| # | 测试场景 | 涉及端点 | 预期结果 |
|---|---------|---------|---------|
| AC-1 | 完整诊断闭环（API Only） | `POST /applications` → `POST /conversations` (SSE) → `GET /conversations/{id}/report` | 无 UI 交互，仅凭 API 完成诊断全过程 |
| AC-2 | 多应用隔离 | 创建 app-A + app-B → 在 app-A 下发两轮提问 → 切换 app-B 发一轮 → 查 app-A 历史 | app-B 历史不含 app-A 的消息 |
| AC-3 | 降级诊断 | 创建无数据源对话（DS 全部不可达）→ 提问 | COMPLETED_PARTIAL + 报告中含数据缺口声明 |
| AC-4 | 认证阻断 | 不带 token / 假 token / 已吊销 token 调任意保护端点 | 401 + 统一错误格式 |
| AC-5 | 追问并发控制 | POST /conversations/{id}/messages → 未等第一轮完成再发 | 409 |
| AC-6 | 报告导出 | `GET /conversations/{id}/report` (text/markdown) | 浏览器触发下载，文件名含应用名+时间 |
| AC-7 | 会话清空 | `DELETE /conversations/{id}` → `GET /conversations/{id}` | 404 |

---

## 12. 版本演进预留

| 预留 | v0.9 行为 | 目标版本 |
|------|----------|---------|
| `/webhooks/alertmanager` | 501 | v0.95 |
| `/applications/{id}/metrics`（应用级指标汇总） | 不存在 | v1.0 |
| `/knowledge/search`（知识库检索） | 不存在 | v1.0 FR-16 |
| `PATCH /applications/{id}/tags`（标签部分更新） | 走 PUT 全量 | 评估中 |
| 多用户管理端点 (`/users`, `/teams`) | 不存在 | v2.0 |

---

> 一致性检查：
> - [ ] REST 资源路径匹配 domainModel 聚合根 → ✅ `/conversations` / `/applications` / `/datasources` / `/auth`
> - [ ] SSE 事件格式 = userFlow §4 → ✅ 5 类型一致 + `close` 补充
> - [ ] 无 report CRUD（报告 = 只读视图）→ ✅ 仅 `GET /conversations/{id}/report`
> - [ ] API First 验证 → ✅ AC-1 纯 API 完整诊断闭环
> - [ ] 每端点含 正常/异常/边界 验收标准 → ✅
>
> 下一步：`dbSchema.md` 以此契约 + domainModel 为输入，设计 PostgreSQL 表结构。
