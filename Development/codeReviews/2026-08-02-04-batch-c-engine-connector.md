# 2026-08-02 批次 C engine/connector 修复审查

**范围**：feat/phase8-engine-connector 分支（12 文件：infra 3、engine 5、connector 2、server 1、agent-core 1）
**审查方式**：安全专长子代理（Senior SecOps Engineer）审查 git diff
**审查结论**：无 P0/P1，通过可合入；3 P2 + 1 P3 中 3 项本次修复

---

## 修复内容

### P0

- **PrometheusQueryBuilder null metric NPE** — buildRangeQuery/buildRateQuery null metric 显式抛 IllegalArgumentException（不再静默 NPE）

### P1

- **SSRF 重定向绕过** — HttpClient followRedirects(NEVER)：302 不能再跳到 169.254.169.254 / 127.0.0.1（此前 validateUrl 通过后重定向可绕过）
- **PromQL 嵌套聚合括号 bug** — 原 lastIndexOf(")") 在嵌套时吃掉闭合括号；改为结构化构建（selector + window → aggregation 包裹），聚合函数名校验（"sum(rate" 直接拒绝而非生成坏 PromQL）
- **认证传递缺口** — QueryRequest.Typed 增加 AuthConfig 字段；orchestrator 从 DataSource 持久化字段解析真实凭据；两个 Connector 应用 Basic/Bearer header；DataSourceController.test 用真实凭据（原为空字符串）

### P2（修复 + 顺手）

- IPv4-mapped IPv6 SSRF 绕过 — isForbiddenAddress 手动字节检测（此 JDK 无 isIPv4MappedAddress API）
- label key 格式校验（[a-zA-Z_][a-zA-Z0-9_]*）、label value 转义补 \r
- LogQuery/MetricsQueryServiceImpl — 匿名空 QueryResult 改 Failure(NOT_IMPLEMENTED) + datasourceType 校验
- 尾斜杠 strip、日志传异常对象、connector null 检查
- applyAuth 提取到 AuthConfig.applyTo()（审查建议：安全关键代码唯一化）

## 测试（+14）

- AuthConfig.from 解析 5（BASIC/BEARER/未知类型/坏 JSON/null）
- PromQL 7（嵌套窗口括号平衡、非法聚合名拒绝、null metric、非法 label key、\r 转义、instant 一致性）
- ActuatorProbe 3（IPv4-mapped 禁止、私网禁止、公网允许）

## 审查遗留（不阻塞）

1. buildInstantQuery 与 range/rate 的 null 行为已统一（MetricsQueryServiceImpl 捕获转 Failure 保持既有测试语义）
2. rangeWindow 格式未校验（Prometheus 会拒绝，风险低）
3. actuatorUrl 日志可能含 userInfo 凭证（原有问题，低优先级）

## 验证

- 全量测试 ✓（BUILD SUCCESS）
