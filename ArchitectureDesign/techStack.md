# 技术选型 — Epiphaneia

> 版本：v1.0-draft | 日期：2026-07-18
> 输入：ProductPlanning 阶段全部产出物 + productPlanningFinalReview（位于 `ArchitectureDesign/` 目录——统筹层审查产物，作为本阶段输入交付至此）
> 约束：Java Spring 生态（不可协商）
>
> **版本假设声明**：以下版本号基于 2026-07-18 可用版本。若实际 Maven Central / npm registry 可用版本与此有偏差，以 `pom.xml` / `package.json` 中的实际可用版本为准。所有版本号在 projectScaffold 阶段做最终锁定。

---

## 1. 总览

| 类别 | 选型 | 版本 | 决定来源 |
|------|------|------|---------|
| 语言 | Java | 21 LTS（强制） | Spring AI 2.0 硬要求 |
| 框架 | Spring Boot | 4.1.0 | 2026-07 最新稳定版（3.x 已于 2026-06-30 EOL） |
| 构建 | Maven | 3.9+ | 本阶段决策 |
| Web 层 | Spring MVC + Virtual Threads（默认） | — | D-1 决策；Boot 4.x 虚拟线程为 Tomcat 默认执行模型 |
| AI/Agent | Spring AI + LangChain4j | 2.0.0 / 1.16.1 | 本阶段确认（handoff 1.6 方向不变，版本升级） |
| 数据库 | PostgreSQL | 15+ | dbSchema |
| 迁移工具 | Flyway | 10+ | D-5 决策 |
| ORM | Spring Data JPA + Hibernate | 随 Boot 4.x | 本阶段决策（见理由） |
| 缓存 | Caffeine + Spring Cache | 3.x / 框架内 | handoff 1.2 |
| 安全框架 | Spring Security | 7.x（随 Boot 4.x / Spring Framework 7.x） | 本阶段默认 |
| 加密 | Spring Security Crypto (AES-256-GCM) | 7.x | D-4 决策 |
| HTTP 客户端 | Spring RestClient | 随 Boot 4.x | 本阶段决策——Connector 层 HTTP 调用基础 |
| Bean Validation | Hibernate Validator（随 spring-boot-starter-validation） | Jakarta Bean Validation 3.x | 本阶段决策——所有端点输入校验 |
| DTO 映射 | MapStruct | 1.6+ | 本阶段决策——Controller DTO ↔ 领域对象转换 |
| 前端 | React + Vite + TypeScript | 19 / 6.x | 本阶段确认（PRD 原写 React 18，已更新至 19） |
| 反向代理 | Nginx | 1.25+ | PRD FR-9 |
| 容器化 | Docker + Docker Compose | 24+ | PRD FR-9 |
| 测试 | JUnit 5 + Mockito + Testcontainers | 5.10+ | 本阶段决策 |
| 代码检查 | ArchUnit | 1.x | handoff 1.8 |

---

## 2. 逐项详述

### 2.1 语言与 JDK：Java 21 LTS ✅ 确认（已升级为强制要求）

**推荐：Java 21 LTS（强制）**

理由：
- **Spring AI 2.0 硬要求 Java 21**——不可协商。Spring AI 1.x 仅支持 Boot 3.x；Boot 4.x 必须配 Spring AI 2.x，而 2.x 最低 Java 21
- 虚拟线程（Virtual Threads）天然适合 LLM 调用、Prometheus/ES 查询——Spring Boot 4.x 将虚拟线程设为 Tomcat 默认执行模型，同步代码即获异步收益
- Spring Boot 4.x 自身最低要求 Java 17，推荐 Java 21，兼容至 Java 25
- 与 handoff 1.1 倾向一致，零迁移成本（项目从零开始）

> **版本决策记录**：ProductPlanning 阶段（2026-07-18 前）的 handoff 和 PRD 基于 Spring Boot 3.x + Spring AI 1.x 生态编写。ArchitectureDesign 阶段启动时（2026-07-18）Spring Boot 3.x 已 EOL（2026-06-30），Spring AI 2.0 已 GA（2026-06-12）。此为主版本升级，非 ProductPlanning 设计缺陷——时间窗口恰好跨越生态大版本更迭。全部版本选择以本阶段确认结果为准，ProductPlanning 文档中的版本引用（如 `Spring Boot 3.x`、`Spring AI 1.0+`）应理解为"当时假设"而非"当前约束"。

替代方案：
- **Java 17**：Spring AI 2.0 不兼容——不可选
- **Java 25**：Spring Boot 4.x 兼容但非 LTS，生产不推荐
- **GraalVM Native Image**：冷启动快但 LangChain4j 反射大量使用，native 兼容成本高。MVP 不引入

合规验证（D-2）：

| 组件 | Java 21 兼容 | 状态 |
|------|-------------|------|
| Spring Boot 4.1.0 | ✅ 推荐 JDK | GA（2026-06-10） |
| Spring AI 2.0.0 | ✅ 硬要求 | GA（2026-06-12） |
| LangChain4j 1.16.1 | ✅ `-spring-boot4-starter` | GA |
| Spring Security 7.x | ✅ 内置 | GA（随 Spring Framework 7.0.8） |
| Flyway 10.x | ✅ | GA |
| PostgreSQL JDBC 42.7+ | ✅ | GA |

> 结论：JDK 21 为强制要求（非可选），全链路兼容，无阻塞点。

---

### 2.2 构建工具：Maven

**推荐：Maven 3.9+**

理由：
- Java Spring 生态的事实标准，文档最丰富
- pom.xml 声明式依赖管理，Spring Boot 的 `spring-boot-starter-*` BOM 体系天然基于 Maven
- 多模块项目（D-7 的 4±1 模块）Maven 的 `<parent>` + `<module>` 机制简单够用
- 开源项目通常用 Maven——降低社区贡献门槛

替代方案：
- **Gradle**：增量构建更快，DSL 灵活但学习曲线陡。Kotlin DSL 对 AI 工具解析友好度略差于 XML。Spring 官方同时支持两者，选 Maven 不降低兼容性
- 不选 Gradle 的主要原因：开源社区 Maven 占比显著更高，"clone 即构建" 卖点下 Maven 摩擦最小

```xml
<!-- 父 POM 模板骨架 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
</parent>

<groupId>io.epiphaneia</groupId>
<artifactId>epiphaneia</artifactId>
<version>0.9.0-SNAPSHOT</version>
<packaging>pom</packaging>

<properties>
    <java.version>21</java.version>
    <spring-ai.version>2.0.0</spring-ai.version>
    <langchain4j.version>1.16.1</langchain4j.version>
</properties>
```

---

### 2.3 Web 层：Spring MVC + Virtual Threads（默认） ✅ D-1 决策

**推荐：Spring MVC + Tomcat 11+ / Virtual Threads（默认）**

理由：
- **Spring Boot 4.x 将虚拟线程设为 Tomcat 的默认执行模型**——无需 `spring.threads.virtual.enabled=true`，默认即用。同步代码写法直接获异步并发收益
- SSE 流式输出（PRD FR-11 硬约束）在 Spring MVC 下用 `SseEmitter` 或 `StreamingResponseBody` 直接支持，无需 WebFlux 的 `ServerSentEvent` 包装
- 虚拟线程池不受传统平台线程数限制，单实例承载诊断请求 + SSE 多连接无压力
- 开发体验：同步式代码编写、调试、堆栈追踪——单人体量友好

替代方案：
- **WebFlux (Netty)**：反应式链式调用（Mono/Flux）与 LangChain4j 的同步 API 范式冲突，需大量 `block()` 桥接——既失性能优势又增复杂度。Spring Boot 4.x 虚拟线程默认化后 WebFlux 的 IO 优势被大幅削弱，社区趋势明确推荐"虚拟线程 + MVC"替代大部分反应式场景。LangChain4j 社区同样推荐同步模式配合虚拟线程
- **Undertow**：Spring Boot 4.0 起已弃用，不可选

SSE 实现方案：Spring MVC `SseEmitter`，每个诊断请求创建一个 emitter，诊断管道通过事件总线（`ApplicationEventPublisher`）推送 SSE 事件。断连检测通过 `emitter.completeWithError()` / `onCompletion` 回调。

```java
// 示例：SSE 端点骨架（非生产代码，仅示意技术可行性）
@GetMapping(path = "/conversations/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamEvents(@PathVariable String id) {
    SseEmitter emitter = new SseEmitter(120_000L); // 120s 超时
    // 订阅领域事件 → emitter.send(SseEmitter.event().name("state").data(...))
    return emitter;
}
```

---

### 2.4 AI/Agent 层：Spring AI 2.0 + LangChain4j 1.16 ✅ 继承 handoff 1.6，版本随 Boot 4.x 升级

**推荐：Spring AI 2.0.0（LLM 抽象 + ChatClient）+ LangChain4j 1.16.1（Agent 编排）**

分层职责：

| 层 | 框架 | 职责 |
|----|------|------|
| LLM 调用抽象 | Spring AI 2.0 | 统一 ChatClient / 多后端切换（OpenAI/Anthropic/DeepSeek/Ollama）+ MCP 协议 |
| Agent 编排 | LangChain4j 1.16 | ReAct Agent / Tool Calling / 多步推理（AiServices） |
| 领域接口 | 纯 Java | DiagnosisOrchestrator / ConnectorRegistry / ReportSynthesizer（domainModel §6） |

理由：
- Spring AI 2.0 是 Boot 4.x 生态的唯一选项（1.x 不兼容 Boot 4.x），同时新增 MCP（Model Context Protocol）支持和流式工具调用
- Spring AI 2.0 新增 AgentCore 模块（Agent 抽象层），但其成熟度低于 LangChain4j 的 ReAct 循环实现。MVP 阶段保守策略：ChatClient 走 Spring AI，Agent 编排仍走 LangChain4j 的 `AiServices` + `@Tool`。观察 Spring AI AgentCore 1-2 个 minor 版本后评估替换 LangChain4j
- LangChain4j 1.16 提供独立的 `-spring-boot4-starter` artifact，与 Boot 4.x 原生兼容
- 两者互补：Spring AI 管"调哪个模型"，LangChain4j 管"Agent 怎么用模型做多步推理"

> **Spring AI 2.0 重大变化**（相对 ProductPlanning 阶段的 1.x 假设）：
> - Java 21 强制要求（原 17+）
> - 包名从 `org.springframework.ai` 调整部分模块
> - 新增 AgentCore 模块——未来可能吸收部分 LangChain4j 职责
> - Jackson 2 → Jackson 3（影响序列化配置）
> - LangChain4j starter 需用 `-spring-boot4-starter` 后缀（非 `-spring-boot-starter`）

替代方案：
- **只用 Spring AI 2.0（含 AgentCore）**：AgentCore 成熟度不够，ReAct 循环的 tool-calling 实现在 LangChain4j 中有更多生产验证
- **只用 LangChain4j 1.16**：多后端 ChatClient 配置不如 Spring AI 的 Boot Starter 原生顺滑。两框架协力成本低，收益明确
- **独立 Python Agent 服务**：handoff 1.6 已否决

领域隔离约束（handoff 1.6）：Agent 编排逻辑收敛在 `DiagnosisOrchestrator` 接口之后，领域层只持有接口。LangChain4j 的 `@Tool` 注解方法在基础设施层实现，领域层无框架导入。

---

### 2.5 数据库：PostgreSQL 15+

**推荐：PostgreSQL 15+**

理由：
- dbSchema 已基于 PostgreSQL 设计（pgcrypto、pg_trgm、JSONB、GIN 索引）
- Spring Boot + PostgreSQL 是 Java 生态最成熟的组合
- JSONB 用于 ActuatorInfo 快照、auth_config 元数据——MySQL 的 JSON 支持弱于 PostgreSQL
- 开源项目标配，docker-compose 零配置

本项非新决策——ProductPlanning 阶段已确定（PRD §8 依赖表 + dbSchema §1）。

连接池：HikariCP（Spring Boot 默认），默认 `maximumPoolSize=10`、`connectionTimeout=30000ms`。单用户 MVP 下默认值足够。调优触发条件：`connectionTimeout` 日志出现频率 &gt; 5 次/小时 → 调整 `spring.datasource.hikari.maximum-pool-size`。

---

### 2.6 数据库迁移：Flyway ✅ D-5 决策

**推荐：Flyway 10+**

理由：
- Spring Boot 内置 `spring-boot-starter-data-jpa` 级 Flyway 自动配置，零额外集成代码
- SQL-first 迁移风格：手写 SQL 版本文件，可审查、可回退、可版本控制
- Java 生态中 Flyway 社区占比 > Liquibase（Spring Boot 官方 starter 市场数据）
- MVP 迁移简单（10 张表 + 种子数据），Flyway 的简洁性匹配需求

替代方案：
- **Liquibase**：XML/YAML/JSON 声明式变更集比 SQL 更结构但学习成本高。当迁移需要跨数据库类型（PostgreSQL → MySQL）时有优势——Epiphaneia 锁定 PostgreSQL，不需要此能力，选更轻的 Flyway

```
V001__init_schema.sql
V002__seed_admin.sql
```

CI 验证：`docker compose up` 后 Flyway 自动执行迁移，集成测试对全部表 CRUD 一次验证。

---

### 2.6b ORM：Spring Data JPA + Hibernate

**推荐：Spring Data JPA + Hibernate 6.x（Boot 4.x 内置）**

理由：
- **聚合根映射自然**：Conversation → Message → Evidence/Hypothesis 的嵌套关系，JPA `@OneToMany(cascade = ALL, orphanRemoval = true)` 直接映射 DDD 聚合，增删改自动级联
- **JSONB 支持成熟**：Hibernate 6.x 原生 `@JdbcTypeCode(SqlTypes.JSON)` 处理 `actuator_info`、`auth_config`、`tags` 等 JSONB 字段，无需手动序列化
- **Spring Boot 零配置**：`spring-boot-starter-data-jpa` 开箱即用，`JpaRepository` 自动提供 CRUD
- **与 Flyway 互补**：JPA 管理运行时查询，Flyway 管理 schema 变更，各自最擅长的领域

替代方案：
- **MyBatis / MyBatis-Plus**：SQL 控制力更强，适合复杂报表查询。但 Epiphaneia 的查询模式是聚合根加载（`findById`、`findByApplicationId`），非多表联合报表。MyBatis 需要手写 ResultMap 映射嵌套对象——Conversation → Message → Evidence 三级嵌套的 XML 映射量很大。JPA 的 `@OneToMany` + `FetchType.LAZY` 零代码完成等价的关联查询
- **Spring Data JDBC**：更轻量、DDD 友好（无懒加载魔法），但 JSONB 支持和社区成熟度不如 JPA。10 张表规模用 JPA 不会踩到"Hibernate 太重"的陷阱
- 不选 MyBatis 的关键理由：DDD 聚合根加载模式是 JPA 的甜区——`conversationRepository.findById(id)` 一行代码加载整个聚合树。MyBatis 要实现等价的嵌套对象映射需要大量 XML 或注解配置，维护成本随聚合复杂度线性增长

> **ponytail 注释**：JPA 的 `FetchType.LAZY` + `@EntityGraph` 覆盖 90% 查询场景。如果出现复杂报表（如跨应用诊断统计），直接在 Repository 加 `@Query(nativeQuery = true)` 写原生 SQL——不开 MyBatis 第二通道。需要多表聚合分析时（v1.0 知识库阶段）再评估 MyBatis 或 jOOQ。

Repository 接口位置：
- **在 agent-core 的 `api` 包**——Repository 接口是领域概念（"我需要通过 ID 找到 Conversation 聚合"），属于领域层
- Spring Data JPA 自动生成代理实现，不需要手动写 Impl
- `epiphaneia-server` 的 `@SpringBootApplication` 通过 `@EnableJpaRepositories` 扫描 agent-core 的 Repository 接口

---

### 2.6c HTTP 客户端：Spring RestClient

**推荐：Spring RestClient（Boot 4.x 同步 HTTP 客户端内置）**

理由：
- Spring Boot 4.x / Spring Framework 7.x 推荐的同步 HTTP 客户端，替代已弃用的 `RestTemplate`
- Connector 层（PrometheusConnector、ElasticsearchConnector）的唯一 HTTP 通信方式
- 虚拟线程默认执行模型下，RestClient 的同步调用自动获异步并发收益——无需 `WebClient` 的反应式包装
- Builder API 风格，支持超时、拦截器、错误处理器链式配置
- 内置 Jackson 3 序列化/反序列化

配置要点（在 `epiphaneia-connector` 的 `ConnectorConfig` 中集中管理）：
- 连接超时：5s（Prometheus/ES 在内网，不应过长）
- 读取超时：30s（Prometheus range query 可能较大）
- 重试：1 次（仅对 5xx 和网络超时，不对 4xx）
- 连接池：RestClient 底层使用 `HttpClient`（Java `java.net.http.HttpClient`），默认连接池大小足够 MVP

替代方案：
- **WebClient**：反应式 HTTP 客户端，适合 WebFlux。项目已选 Spring MVC，用反应式客户端增加不必要的 Mono/Flux 包装。不选
- **Apache HttpClient 5 / OkHttp**：成熟第三方库，但 RestClient 是 Boot 4.x 内置推荐，零额外依赖。需要更复杂的连接池调优时可选 Apache HttpClient 5——目前不需要

### 2.6d Bean Validation：Hibernate Validator

**推荐：Hibernate Validator（`spring-boot-starter-validation` 内置）**

理由：
- `apiDesign.md` 全部端点均定义了输入校验需求（name 非空、URL 格式、密码复杂度、provider 枚举值、question 非空等）
- Spring Boot 自动配置，DTO 加 `@NotBlank` / `@NotNull` / `@URL` / `@Size` 等 Jakarta Bean Validation 注解即可
- 自定义校验器（如 `@ValidPassword` 密码复杂度）通过 `ConstraintValidator` 接口实现
- `@Validated` + `@Valid` 在 Controller 层自动触发校验，`MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` 统一错误响应

替代方案：无。Jakarta Bean Validation 是 Java 生态唯一标准。

### 2.6e DTO 映射：MapStruct

**推荐：MapStruct 1.6+**

理由：
- Java 生态标准的编译期代码生成映射工具，零运行时开销
- `@Mapper(componentModel = "spring")` 生成 Spring Bean，直接 `@Autowired`
- Controller DTO（`LoginRequest` / `ConversationCreateRequest` 等）↔ 领域对象（`Conversation`、`Application` 等）的转换由 MapStruct 接口完成

替代方案：
- **手工 static factory method（`DTO.from(entity)` / `entity.toDto()`）**：无依赖，适合 DTO 数量 &lt; 10 的项目。Epiphaneia 约 15-20 个 DTO，手工维护不划算。选 MapStruct 但保留极简场景直接用 factory method 的自由
- **ModelMapper / Dozer**：运行时反射映射，性能差且配置黑盒。不选

---

### 2.7 缓存：Caffeine + Spring Cache 抽象 ✅ 继承 handoff 1.2

**推荐：Caffeine (本地缓存) + Spring Cache 抽象**

理由：
- MVP 单实例，Caffeine 本地缓存满足全部场景（PromQL 结果短 TTL、LLM 响应去重、热点元数据）
- Spring Cache 抽象（`@Cacheable` / `CacheManager`）换 Redis 时只改配置 + pom 依赖，业务代码零修改
- 不引入 Redis 容器的理由（handoff 1.2）：单实例下无独占价值，稀释 docker-compose 一键部署极简卖点

升级路径：v2.0 多用户/多实例部署时，加 `spring-boot-starter-data-redis` + 改 `CacheManager` Bean 实现为 `RedisCacheManager`。

---

### 2.8 安全框架：Spring Security + Spring Security Crypto ✅ D-4 决策

**推荐：Spring Security 7.x + Spring Security Crypto**

认证方案：

| 通道 | 方式 | 说明 |
|------|------|------|
| Web UI | Cookie Session（`HttpSession` + `JSESSIONID`） | Spring Security 默认表单登录 |
| REST API | Bearer Token（SHA-256 哈希验证） | 自定义 `OncePerRequestFilter` 从 `Authorization: Bearer <token>` 头提取 |
| 初始密码 | 首次启动时生成随机值，`System.out` 打印 | 仅首次出现，非 Spring Security 范畴 |

凭证加密方案（D-4）：

| 加密对象 | 字段 | 方案 |
|---------|------|------|
| LLM API Key | `llm_provider.api_key_encrypted` | AES-256-GCM，密钥来自 `EPIPHANEIA_ENCRYPTION_KEY` 环境变量 |
| DataSource 凭证 | `data_source.auth_config` (JSONB) | 同上——存入前加密整段 JSON，取出后解密 |
| Admin 密码 | `admin.password_hash` | bcrypt（单向哈希，不属加密） |

实现位置：基础设施层 `EncryptionService` 接口（`encrypt(String): String` / `decrypt(String): String`），`AesGcmEncryptionService` 实现使用 Spring Security Crypto 的 `AesBytesEncryptor`。领域层无加密概念——实体字段始终是明文，加解密在 Repository 的读写边界完成（TypeHandler / JPA AttributeConverter）。

密钥管理 MVP 方案：`EPIPHANEIA_ENCRYPTION_KEY` 环境变量注入，docker-compose 中通过 `environment` 段或 `.env` 文件配置。首次启动若未检测到该变量，生成随机密钥并打印到日志（同初始密码策略）。不引入 KMS/HashiCorp Vault——MVP 单实例不需要密钥管理基础设施。

替代方案：
- **Jasypt**：社区成熟但维护放缓，Spring Security Crypto 是官方组件且零额外依赖
- **KMS/AWS Secrets Manager**：v2.0 多实例部署时评估，当前过度

速率限制：**bucket4j**（Token Bucket 算法，内存存储，单实例够用）。`RateLimitFilter` 使用 bucket4j 的 `Bucket` 接口，配置为：登录 5 次/分钟/IP，API 100 次/分钟/Token。重启后计数器归零——MVP 可接受（非金融场景，短暂超限不致命）。v2.0 多实例时换 Redis-backed bucket4j 或网关层限流。

---

### 2.9 Connector SPI 设计 ✅ D-6 决策

**推荐：String-based 类型匹配 + Spring Bean 动态注册**

理由（承接审查建议 3）：
- `DataSource.type` 是字符串（dbSchema 已选 VARCHAR(50)），ConnectorRegistry 通过 `Map<String, Connector>` 查找，避免 switch-case 枚举
- 新增 Loki/MySQL/Redis Connector 只需加一个 `@Component` Bean，不改领域层枚举
- Spring 的 `List<Connector> findAll()` 自动收集全部实现，`Connector.supports(type)` 方法声明支持的数据源类型

```java
// 领域层接口
public interface Connector<T, R> {
    String type();           // 返回支持的 data_source.type（如 "PROMETHEUS"）
    R query(T request);
    boolean testConnection(DataSource config);
}

// 基础设施层实现示例
@Component
public class PrometheusConnector implements Connector<PrometheusRequest, PrometheusResult> {
    @Override public String type() { return "PROMETHEUS"; }
    // ...
}

// ConnectorRegistry 实现
@Component
public class ConnectorRegistryImpl implements ConnectorRegistry {
    private final Map<String, Connector> connectors;

    public ConnectorRegistryImpl(List<Connector> connectorList) {
        this.connectors = connectorList.stream()
            .collect(Collectors.toMap(Connector::type, Function.identity()));
    }

    @Override
    public Connector getConnector(String type) {
        Connector c = connectors.get(type);
        if (c == null) throw new UnsupportedDataSourceException(type);
        return c;
    }
}
```

`DataSourceType` 枚举保留为常用类型的快捷常量（`DataSourceType.PROMETHEUS = "PROMETHEUS"`），方便代码补全，但核心匹配逻辑走字符串。未来社区贡献的自定义 Connector 注册时只需 Bean name / `@Order` 排序，零领域层修改。

---

### 2.10 前端：React 19 + Vite 6 + TypeScript

**推荐：React 19 + Vite 6 + TypeScript**

理由：
- React 19 稳定版已于 2024 年 12 月发布，至 2026 年 7 月已稳定 19 个月，React 18 处于维护模式
- React 19 引入 Server Actions、use() hook、改进的 Suspense、ref 作为 prop 等特性——未来复用的可能性高于 18 的新增成本
- Vite 6 配套 React 19 的 SSR/CSR 混合场景，开发体验持续提升
- TypeScript 类型安全减少 SSE 事件格式解析错误

> **版本决策记录**：PRD §8 依赖表中写定为 React 18——ProductPlanning 阶段的决策基于 2024 年生态（React 19 尚未 GA）。2026-07 ArchitectureDesign 阶段启动时 React 19 已稳定 19 个月，版本号以本阶段确认为准，PRD 中的 "React 18" 应理解为"前 18+ 系列"而非锁定 18。

开发/生产分离：
- **开发**：Vite dev server（`localhost:5173`）通过 proxy 转发 `/api/*` 到 Spring Boot（`localhost:8080`）。前后端各自独立启动
- **生产**：`vite build` 产出静态文件到 `dist/`，Nginx 直接 serve，反向代理 API 请求到 `epiphaneia-server:8080`

配套：
- UI 库：延后到 projectScaffold 阶段确定。候选：Tailwind CSS（原子化无锁定）、shadcn/ui（现代组件式但 Tailwind 基础）、Ant Design（Java 社区认知度高）。选型不影响 systemArchitecture / securityDesign / deploymentArchitecture 三份产出物
- i18n：`react-i18next`（PRD NFR-6 i18n 预留）
- SSE 客户端：`eventsource-parser` 或手写 `EventSource` 封装

---

### 2.11 容器化与部署

**推荐：Docker + Docker Compose（Nginx + Spring Boot + PostgreSQL）**

理由与 handoff 1.5 一致：docker-compose 是唯一官方部署形态，不引入 K8s/Helm。

```yaml
# docker-compose 拓扑（架构概览，非最终文件）
services:
  nginx:        # 反向代理 + 静态资源
  app:          # Spring Boot (单实例)
  postgres:     # PostgreSQL 15
```

多架构构建：`docker buildx` 产出 `linux/amd64` + `linux/arm64` 镜像（PRD NFR-4）。

---

### 2.12 测试栈

**推荐：JUnit 5 + Mockito + Testcontainers + ArchUnit（D-8）**

| 工具 | 用途 |
|------|------|
| JUnit 5 | 测试框架 |
| Mockito | Mock 外部依赖（LLM/Prometheus/ES） |
| Testcontainers | 集成测试——用真实 PostgreSQL 替代 H2 内存库 |
| ArchUnit | 包边界纪律检查——违规依赖在 CI 挂构建（handoff 1.8） |
| Spring Boot Test | `@SpringBootTest` / `@WebMvcTest` 切片测试 |
| REST Assured | API 端点测试（可选，MockMvc 覆盖大部分场景） |

不推荐 H2 内存数据库做集成测试——PostgreSQL 的 JSONB、pg_trgm、pgcrypto 扩展 H2 无法模拟，Testcontainers 提供真实环境。

ArchUnit 规则示例（D-8）：
```java
// 领域层不导入基础设施层
@AnalyzeClasses(packages = "io.epiphaneia")
class ArchitectureTest {
    @ArchTest
    static final ArchRule domainDoesNotDependOnInfra = classes()
        .that().resideInAPackage("..domain..")
        .should().onlyDependOnClassesThat()
        .resideOutsideOfPackage("..infrastructure..");
}
```

---

### 2.13 日志与可观测性

**推荐：SLF4J + Logback（Spring Boot 默认）+ Spring Boot Actuator**

理由：
- Spring Boot Starter 内置 Logback，零配置起步
- Actuator 暴露 `/actuator/health`、`/actuator/metrics`、`/actuator/prometheus`——Epiphaneia 能诊断自己（PRD NFR-8 自举）
- 结构化日志用 Logstash Logback Encoder（JSON 格式），便于后续 ELK 集成
- 不引入 Micrometer Tracing / OpenTelemetry Agent——MVP 单体应用，分布式追踪不需要

---

## 3. 模块物理划分 ✅ D-7 决策

**推荐：5 Maven 模块 + 1 独立前端项目**

```
epiphaneia/                  ← Maven 父 POM (pom.xml, <packaging>pom</packaging>)
├── epiphaneia-connector/    # ①集成层 Maven 模块（独立 artifact，社区贡献扩展点）
├── epiphaneia-engine/       # ②数据引擎 Maven 模块（PromQL/ES DSL 查询构建）
├── epiphaneia-agent-core/   # ③Agent编排+④LLM调度 Maven 模块（合并，MVP单Skill）
├── epiphaneia-server/       # Spring Boot 入口 Maven 模块（Controller + Security + SSE + 装配）
├── epiphaneia-infra/        # 共享基础设施 Maven 模块（加密、缓存、安全横切）
└── epiphaneia-web-ui/       # ⚠️ 非 Maven 模块——独立 npm/Vite/React 项目，无 pom.xml
```

| 模块（artifactId） | 类型 | 对应逻辑层 | 独立理由 |
|-------------------|------|-----------|---------|
| `epiphaneia-connector` | Maven | ⑥集成层 | **必须独立**——Connector 是社区贡献核心扩展点，独立 artifact + 清晰 SPI |
| `epiphaneia-engine` | Maven | ⑤数据引擎 | PromQL/ES DSL 构建逻辑复杂足够独立——未来 Loki Connector 复用同一 engine |
| `epiphaneia-agent-core` | Maven | ③编排 + ④LLM | MVP 单 Skill，Spring AI 已承担 LLM 流程大半，合并减模块数 |
| `epiphaneia-server` | Maven | ①接口 + ②Skill | Spring Boot 应用唯一入口（`main()`），依赖所有下层模块，含 REST Controller / SSE / Security 配置。**不含前端代码** |
| `epiphaneia-infra` | Maven | 横切 | 加密、缓存配置、安全 Filter、ConnectorRegistry——所有 Maven 模块共用 |
| `epiphaneia-web-ui` | **npm** | UI | React + Vite + TypeScript，独立构建。生产由 Nginx serve 静态文件。目录名遵循模块命名约定，但无 `pom.xml`，不参与 Maven 构建 |

**命名说明**：
- `epiphaneia-server` 而非 `epiphaneia-web`——业界惯例 `-server` 表示 Spring Boot 可运行 artifact（装配层），`-web` 容易与前端 UI 混淆。Spring Initializr 生成的多模块项目、Spring Cloud 官方示例均用此后缀
- `epiphaneia-web-ui` 而非 `frontend/`——目录名与 Maven 模块命名保持一致（`epiphaneia-` 前缀），可识读为同一产品家族。但无 `pom.xml`，Maven 构建跳过此目录
- 前端不设为 Maven 模块：React 构建工具链是 Vite/npm，用 `frontend-maven-plugin` 包装 npm 构建到 Maven 生命周期增加不必要的复杂度。前后端各自独立构建，CI 中并行——更快

依赖方向（禁止反向）：
```
epiphaneia-server → epiphaneia-agent-core → epiphaneia-engine → epiphaneia-connector
epiphaneia-server → epiphaneia-infra
epiphaneia-agent-core → epiphaneia-infra
epiphaneia-engine → epiphaneia-infra
epiphaneia-connector 仅依赖 epiphaneia-infra（无内部模块依赖）
epiphaneia-web-ui 独立——不参与 Maven 依赖图
```

groupId 统一 `io.epiphaneia`。

---

## 4. 数据库细节决策

### 4.1 diagnosis_state 类型 ✅ D-3 决策

**推荐：VARCHAR(20) + CHECK 约束**

理由：
- dbSchema 已选 VARCHAR（dbSchema §9 一致性验证附注）
- PostgreSQL enum 新增状态需 `ALTER TYPE ... ADD VALUE`，比 VARCHAR CHECK 多一次迁移
- COMPLETED_PARTIAL 是 v0.9 新增状态，v1.x 可能需要 CANCELLED——VARCHAR 更易演进
- CHECK 约束替代 enum 的类型安全

```sql
diagnosis_state VARCHAR(20)
    CHECK (diagnosis_state IN ('CREATED','PLANNING','QUERYING','ANALYZING','COMPLETED','COMPLETED_PARTIAL','FAILED'))
```

---

## 5. 未选型的讨论项

| 项 | 状态 | 说明 |
|----|------|------|
| UI 组件库 | 🟡 projectScaffold 阶段定 | Tailwind CSS vs shadcn/ui vs Ant Design。不影响 systemArchitecture / securityDesign / deploymentArchitecture。影响 `npm create` 初始化命令 |
| 消息队列 | — | MVP 不引入（handoff 1.3）。v1.x 告警风暴削峰时评估 RabbitMQ 或 Redis Streams |
| API 文档 | 默认选 SpringDoc OpenAPI | OpenAPI 3.0 规范随服务发布（PRD FR-8），用 springdoc-openapi-starter-webmvc-ui 自动生成 |
| SSO/OAuth | — | v2.0 多用户时评估 |
| 事件总线 | ApplicationEventPublisher（Spring 内建） | 够用，不引入 Guava EventBus / 外部 MQ |

### 5.1 配置管理

**Spring Profiles**：`application.yml`（默认）+ `application-dev.yml`（开发环境，H2/内存配置可选）+ `application-prod.yml`（生产环境，外部 PostgreSQL）。敏感配置（`spring.datasource.password`、`EPIPHANEIA_ENCRYPTION_KEY`）不写入 `application.yml`，通过 docker-compose `environment` 段注入。

### 5.2 Jackson 3 配置

Spring Boot 4.x / Spring Framework 7.x 内置 Jackson 3。需要显式配置的项：
- `JavaTimeModule` 注册（`java.time.Instant` ↔ ISO-8601 字符串，与 dbSchema 的 `TIMESTAMPTZ` 对齐）
- `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false`（向前兼容——apiDesign 版本策略：新增字段不断路）
- `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false`（ISO-8601 而非 epoch millis）

以上在 `JacksonConfig`（server 模块）中集中配置。

---

## 6. 决策依据速查

| # | 未决项 (productPlanningFinalReview) | 决策 | 本文档位置 |
|---|-----------------------------------|------|-----------|
| D-1 | Web 层选型 | Spring MVC + Virtual Threads | §2.3 |
| D-2 | JDK 21 兼容矩阵 | 全链路验证通过 | §2.1 |
| D-3 | diagnosis_state 类型 | VARCHAR(20) + CHECK | §4.1 |
| D-4 | 加密方案 | Spring Security Crypto AES-256-GCM + env var 密钥 | §2.8 |
| D-5 | 迁移工具 | Flyway 10+ | §2.6 |
| D-6 | Connector SPI 动态注册 | String-based 类型 + Spring Bean | §2.9 |
| D-7 | Maven 模块划分 | 5 模块（4±1） | §3 |
| D-8 | ArchUnit 边界测试 | Junit 5 + ArchUnit 规则 | §2.12 |

---

> 下一步：用户审查确认 techStack 后，进入 systemArchitecture 文档。
> 待讨论项（不影响后续产出物顺序）：UI 组件库选型可在 systemArchitecture 或 projectScaffold 阶段确定。
