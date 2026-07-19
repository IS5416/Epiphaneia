# 项目骨架设计 — Epiphaneia

> 版本：v1.0-final | 日期：2026-07-19
> 输入：techStack.md、systemArchitecture.md、deploymentArchitecture.md、rollbackPrompt.md
> 状态：✅ 已生成 + 子代理审查通过（4/4 维度 PASS）
> 原则：仅生成空项目结构和基础配置，不写业务逻辑。代码产出至 `product/` 目录。

---

## 1. 骨架范围

| 产出 | 实际文件数 | 内容 |
|------|----------|------|
| Maven 多模块项目 | 6 个 pom.xml + Maven Wrapper | 父 POM（5 子模块，infra 首位） |
| 接口 + 实体 + DTO | ~31 个 Java 文件 | 接口签名 + 8 个 JPA 实体 + 不写业务方法体 |
| Spring Boot 入口 | 1 个 Application 类 | `@SpringBootApplication` + `main()` |
| Spring 配置类 | 6 个 @Configuration | 空 Bean 骨架（Jpa/Cache/Ai/Async/Jackson/Web） |
| Flyway 迁移 | 2 个 SQL 文件 | V001: 10 张表 DDL + V002: 种子说明 |
| 应用配置 | 3 个 application.yml | dev/prod 环境 + 默认配置 |
| 安全配置 | 1 个 SecurityConfig | permitAll 骨架（Development 阶段加固） |
| 前端骨架 | package.json + vite + tsconfig + App.tsx | React 19 + Vite 6，`npm install` 可运行 |
| Docker 相关 | 3 个文件 | Dockerfile（多阶段+非root）+ nginx.conf（CSP+HSTS）+ docker-compose.yml |
| 项目根文件 | 5 个文件 | .gitignore + .dockerignore + .env.example + README + .mvn/ |

---

## 2. 目录结构（RB-1 修正后）

```
product/                                   ← 真实软件项目根目录
│
├── pom.xml                                ← Maven 父 POM
├── .gitignore
├── .dockerignore
├── .env.example
├── README.md
├── .mvn/                                  ← Maven Wrapper（mvnw + mvnw.cmd）
│
├── epiphaneia-connector/                  ← 纯实现模块（SPI 在 infra/api）
│   ├── pom.xml
│   └── src/main/java/io/epiphaneia/connector/
│       └── internal/
│           ├── prometheus/PrometheusConnector.java
│           └── elasticsearch/ElasticsearchConnector.java
│
├── epiphaneia-engine/
│   ├── pom.xml
│   └── src/main/java/io/epiphaneia/engine/
│       ├── api/
│       │   ├── MetricsQueryService.java
│       │   ├── LogQueryService.java
│       │   └── model/{PrometheusQuery,ElasticsearchQuery,MetricSample,LogEntry}.java
│       └── internal/
│           ├── prometheus/{PrometheusQueryBuilder,PrometheusResultParser}.java
│           ├── elasticsearch/{EsQueryBuilder,EsResultParser}.java
│           └── actuator/{ActuatorProbeService,ActuatorInfoParser}.java
│
├── epiphaneia-agent-core/
│   ├── pom.xml
│   └── src/main/java/io/epiphaneia/agent/
│       ├── api/
│       │   ├── DiagnosisOrchestrator.java         ← 接口
│       │   ├── ReportSynthesizer.java            ← 接口
│       │   ├── repository/（6 个 JPA Repository）
│       │   ├── model/（10 个 @Entity + 值对象）
│       │   └── event/（5 个领域事件）
│       └── internal/
│           ├── orchestration/{DiagnosisOrchestratorImpl,DiagnosisStateMachine}.java
│           ├── llm/{PromptTemplateManager,ModelRouter,TokenUsageTracker}.java
│           └── report/ReportSynthesizerImpl.java
│
├── epiphaneia-server/
│   ├── pom.xml
│   └── src/main/java/io/epiphaneia/server/
│       ├── EpiphaneiaApplication.java            ← @SpringBootApplication
│       ├── controller/（7 个 Controller）
│       ├── dto/（16 个 DTO）
│       ├── mapper/（4 个 MapStruct Mapper）
│       ├── exception/{GlobalExceptionHandler,ErrorResponse}.java
│       ├── sse/{SseEmitterManager,SseEventReplayer}.java
│       ├── security/{SecurityConfig,SessionAuthFilter,BearerTokenFilter,RateLimitFilter}.java
│       ├── skill/DiagnosisSkill.java
│       └── config/{Jpa,Cache,Ai,Async,Jackson,Web}Config.java
│   └── src/
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml
│       │   └── db/migration/
│       │       ├── V001__init_schema.sql
│       │       └── V002__seed_admin.sql
│       └── test/java/io/epiphaneia/server/
│           └── ArchitectureTest.java             ← ArchUnit 规则
│
├── epiphaneia-infra/
│   ├── pom.xml
│   └── src/main/java/io/epiphaneia/infra/
│       ├── api/
│       │   ├── connector/{Connector,QueryRequest,QueryResult,DataSourceType,AuthConfig}.java
│       │   ├── EncryptionService.java
│       │   └── ConnectorRegistry.java
│       └── internal/
│           ├── encryption/AesGcmEncryptionService.java
│           ├── connector/ConnectorRegistryImpl.java
│           ├── cache/CacheConfig.java
│           ├── event/DomainEventPublisher.java
│           └── exception/{EpiphaneiaException,DataSourceUnavailable,...}.java
│
├── epiphaneia-web-ui/                       ← 前端（npm）
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── api/client.ts
│       ├── pages/{Login,SetupWizard,DiagnosisWorkspace,ReportView,History,Settings}Page.tsx
│       ├── hooks/useSse.ts
│       └── i18n/index.ts
│
└── docker/
    ├── Dockerfile
    ├── nginx/nginx.conf
    └── docker-compose.yml
```

---

## 3. POM 设计要点

### 3.1 框架依赖（已确认 Maven Central 可用）

| 依赖 | artifactId | 版本 |
|------|-----------|------|
| Spring Boot | `spring-boot-starter-parent` | 4.1.0 |
| Spring AI | `spring-ai-starter-model-openai`（1.0.0+ 改名） | 2.0.0（BOM 管理） |
| LangChain4j | `langchain4j-spring-boot4-starter`（Boot 4 专用 artifact） | 1.17.2-beta27 |
| MapStruct | `mapstruct` | 1.6.2 |
| bucket4j | `bucket4j-core` | 8.10.1 |
| ArchUnit | `archunit-junit5` | 1.3.0 |

### 3.2 RB-1 循环依赖修正

- **infra 不声明对 connector 的编译依赖** — `ConnectorRegistryImpl` 的 `List<Connector>` 类型来自 `infra/api` 自身，Spring 运行时注入 connector 的 `@Component` Bean
- **connector 仅依赖 infra** — 实现 `io.epiphaneia.infra.api.connector.Connector` 接口
- **Maven 编译期无循环** — 已通过 review 验证

### 3.3 Maven 模块顺序

```xml
<modules>
    <module>epiphaneia-infra</module>        <!-- 基础模块，须先编译 -->
    <module>epiphaneia-connector</module>
    <module>epiphaneia-engine</module>
    <module>epiphaneia-agent-core</module>
    <module>epiphaneia-server</module>
</modules>
```

---

## 4. 模块接口骨架约定

- 接口文件：完整方法签名 + Javadoc，方法体为 `throw new UnsupportedOperationException("Not implemented")`
- DTO 类：字段 + getter/setter + Bean Validation 注解
- @Entity 类：JPA 映射注解 + 字段（8 个：Admin、ApiToken、Application、Conversation、Message、DataSource、LlmProvider、Evidence）
- Repository 接口：6 个 JPA Repository（Admin、ApiToken、Application、Conversation、DataSource、LlmProvider）
- @Configuration 类：空 Bean 方法骨架
- SecurityConfig：permitAll 骨架，Development 阶段按 securityDesign 加固

## 5. 构建验证

```bash
cd product && ./mvnw compile          # ✅ BUILD SUCCESS — 全部 6 模块编译通过
cd product && ./mvnw test             # Development 阶段跑 ArchUnit 规则
```

## 6. 子代理审查结果（2026-07-19）

| 维度 | 结果 | 关键确认 |
|------|------|---------|
| Build & RB-1 | ✅ PASS | `./mvnw compile` SUCCESS，infra 无 connector 依赖 |
| Structure | ✅ PASS | 父 POM 5 模块、6 个关键接口全在、8 Entity + 6 Repository |
| Config | ✅ PASS | application.yml 完整、Flyway 10 表、Docker 多阶段+非root+CSP+HSTS |
| Dependencies | ✅ PASS | `spring-ai-starter-model-openai`、`langchain4j-spring-boot4-starter`、版本号正确 |

> 详细 POM 内容、application.yml、Dockerfile 等见 `product/` 目录下实际文件。
