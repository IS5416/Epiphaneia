# 2026-07-20 — Phase 0 基础层完成

## 目标

完成所有基础设施：加密服务、Connector 注册、10 个 JPA 实体完整映射、9 个 Repository。

## 产出汇总

### 0C — Infra Services

| 文件 | 操作 | 说明 |
|------|------|------|
| `infra/api/EpiphaneiaProperties.java` | 新增 | `@ConfigurationProperties(prefix="epiphaneia")`，含 encryptionKey |
| `infra/internal/crypto/AesGcmEncryptionService.java` | 新增 | AES-256-GCM 加解密，javax.crypto 直调，密钥 64 字符 hex 校验 |
| `infra/internal/registry/ConnectorRegistryImpl.java` | 新增 | Spring Bean 自动发现 Connector，按 type 索引，重复报错 |
| `infra/src/test/.../AesGcmEncryptionServiceTest.java` | 新增 | 10 个测试：往返、非确定性、篡改、错误密钥、null 等 |
| `infra/src/test/.../ConnectorRegistryImplTest.java` | 新增 | 7 个测试：查找、未知类型、重复、空列表、委托 |

### 0A — Entity Enhancement

| 文件 | 操作 | 说明 |
|------|------|------|
| `agent-core/api/model/RootCauseHypothesis.java` | 新增 | 根因假设，rank 1-3，confidence 0-1，unique(message_id, rank) |
| `agent-core/api/model/FixSuggestion.java` | 新增 | 修复建议，autoExecutionAllowed 默认 false |
| `agent-core/api/model/Message.java` | 重写 | 加 hypotheses/suggestions 关系集合，补全 14 个 getter/setter |
| `agent-core/api/model/Conversation.java` | 重写 | 补 createdAt/updatedAt getter/setter |
| `agent-core/api/model/Application.java` | 重写 | 补 tags/actuatorInfo getter/setter，长度约束 |
| `agent-core/api/model/DataSource.java` | 重写 | 补全全部 getter/setter |
| `agent-core/api/model/LlmProvider.java` | 重写 | 补全全部 getter/setter |
| `agent-core/api/model/Evidence.java` | 重写 | 补 anomalyStart/End/collectedAt getter/setter |
| `server/EpiphaneiaApplication.java` | 修改 | 移除 @EntityScan/@EnableJpaRepositories（SB4 不可用），保留 @ConfigurationPropertiesScan |
| `server/pom.xml` | 修改 | 加 spring-boot-starter-test、testcontainers、junit-jupiter 测试依赖 |
| `server/src/test/.../EntitySchemaTest.java` | 新增 | 8 个反射验证测试：@Entity/@Table/@Id/OneToMany/Unique/Cascade/Column |

### 0B — Repository Completion

| 文件 | 操作 | 说明 |
|------|------|------|
| `AdminRepository.java` | 重写 | +findByUsername, +existsByUsername |
| `ApiTokenRepository.java` | 重写 | +findByAdminIdOrderByCreatedAtDesc, +findActiveTokensByAdminId |
| `ApplicationRepository.java` | 重写 | +searchByName (ILIKE) |
| `ConversationRepository.java` | 重写 | searchByTitle 改为 LOWER LIKE（跨数据库兼容） |
| `DataSourceRepository.java` | 重写 | findByType 改为 List 返回 + findByName |
| `LlmProviderRepository.java` | 重写 | +findByProvider |
| `MessageRepository.java` | 新增 | findByConversation, findTopByConversation, findActiveDiagnoses, findStaleDiagnoses |
| `RootCauseHypothesisRepository.java` | 新增 | findByMessageOrderByRankAsc |
| `FixSuggestionRepository.java` | 新增 | findByMessage |

## 验证

```
./mvnw test — 25/25 测试通过
./mvnw compile — 6/6 模块 BUILD SUCCESS
```

| 模块 | 测试数 | 状态 |
|------|--------|------|
| epiphaneia-infra | 17 | 全部通过 |
| epiphaneia-server | 8 | 全部通过 |
| 其余模块 | 0 | 编译通过 |

## 技术决策

1. **加密用 javax.crypto 直调**：避开 Spring Security 7.x Encryptors API breaking change 风险，底层 AES-256-GCM 等价
2. **加密不在 JPA Converter 层**：AttributeConverter 无法注入 Spring Bean，MVP 阶段 service 层处理加密/解密（ponytail: 如后续出现直接实体访问路径，加静态 holder 模式的 Converter）
3. **JSONB 存 String**：Hibernate 6.x + PostgreSQL JDBC 自动处理 String ↔ jsonb 映射，无需 Converter
4. **移除 @EntityScan/@EnableJpaRepositories**：Spring Boot 4.1.0 中这些注解包路径变更，`scanBasePackages = "io.epiphaneia"` 已覆盖
5. **ConversationRepository.searchByTitle 改为 LOWER LIKE**：原 ILIKE 仅 PostgreSQL 支持，LOWER LIKE 跨数据库兼容

## 技术债务

| 项目 | 说明 |
|------|------|
| EntitySchemaTest 集成测试 | 标记 @Tag("integration")，需 Docker + Testcontainers，当前环境无 Docker |
| EncryptionAttributeConverter | 待 service 层完成后评估是否需要 |
| RootCauseHypothesis.confidence CHECK 约束 | JPA 层未强制 0.0-1.0 范围，依赖 service 层校验 |

## 变更量

~600 行新增代码（不含测试），~250 行测试代码。合计 ~850 行。

## Git 提交

| Commit | 消息 |
|--------|------|
| `51c9577` | feat(infra): implement AesGcmEncryptionService and ConnectorRegistryImpl |
| `f2f5961` | feat(agent-core): complete 10 JPA entities with relationships and validations |
| `1e1d132` | feat(agent-core): complete 9 JPA repositories with custom queries |
| `953212f` | fix: apply Phase 0 review fixes |
| `602b0b2` | docs: add Phase 0 devLog, codeReview report, and AGENTS.md updates |

## 审查后修订 (2026-07-20)

三路子代理并行审查（设计一致性、架构安全、测试质量），审查报告：[codeReview/2026-07-20-01-phase0-review.md](../codeReviews/2026-07-20-01-phase0-review.md)

### 已修复 (BLOCKER)

| # | 问题 | 修复 |
|---|------|------|
| B-1 | Message + Flyway 缺失 `failure_reason` | V001 添加列 + Message entity 添加字段和 getter/setter |
| B-2 | Admin.username 缺 `length=50` | 添加 `@Column(length = 50)` |
| B-3 | EpiphaneiaProperties 在 `api/` 包导入 Spring 注解 | 移至 `internal/config/EpiphaneiaProperties.java` |

### 已修复 (HIGH)

| # | 问题 | 修复 |
|---|------|------|
| H-1 | record toString() 泄露加密密钥 | 重写 toString() 返回 `****` |
| H-2 | javax.crypto vs AesBytesEncryptor 分歧 | 保留 javax.crypto，更新设计文档仅需改注释（ponytail: SB4.1.0 中 Encryptors API 有 breaking change 风险） |
| H-3 | Hex vs Base64 编码不一致 | 统一为 Hex，Javadoc 添加 `openssl rand -hex 32` 生成命令 |
| H-4 | SecureRandom.getInstanceStrong() 阻塞风险 | 改为 `new SecureRandom()` |

### 已修复 (MEDIUM)

| # | 问题 | 修复 |
|---|------|------|
| M-1 | 7 个实体 timestamp 缺 `nullable = false` | 统一添加 |
| M-2 | ApiToken.createdAt 未初始化 | 添加 `= Instant.now()` |
| M-3 | AuthConfig toString() 泄露凭据 | 覆盖 toString() 遮罩 password/token |
| M-4 | 配置路径不一致 | YAML 改为 `epiphaneia.encryption-key`，更新 ConfigurationPropertiesScan |

### 已修复 (测试)

| # | 问题 | 修复 |
|---|------|------|
| T-2 | integrationPersistence() 空方法 | 删除，替换为 3 个默认值测试 (Admin/DataSource/Application) |
| T-3 | messageRelationships 断言不够严格 | 对 hypotheses/suggestions 添加 @OneToMany + cascade 验证 |

### 延后处理

- T-1 (Repository 零测试)：Docker 不可用，@DataJpaTest 需 Testcontainers，标记为集成测试待办
- L-1~L-11 (低优先级)：不阻塞合入，后续 Phase 逐步修复
