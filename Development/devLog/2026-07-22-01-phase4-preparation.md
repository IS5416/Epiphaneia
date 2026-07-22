# 2026-07-22 — Phase 4 启动准备完成

## 目标

在启动 Phase 4 Web UI 开发前，处理后端首次启动的兼容性问题和配置整合。

## 变更摘要

### 依赖调整

| 变更 | 原因 |
|------|------|
| 移除 `langchain4j-spring-boot4-starter` | 零调用、纯死重。`AiServicesAutoConfiguration` 作为 BFPP 启动时反射扫描 MapStruct 生成的类，导致 `NoClassDefFoundError: ApiTokenResponse` |
| `mapstruct-processor` 从 `annotationProcessorPaths` 改为 provided 依赖 | `annotationProcessorPaths` 改变 javac 多轮处理顺序，MapStruct 生成 Impl 时 DTO 类尚未编译 → 生成代码含 unresolved 引用。改为类路径自动发现后，javac 标准多轮编译正确处理依赖顺序 |
| 新增 `springdoc-openapi-starter-webmvc-ui` 2.7.0 | Swagger UI 文档（Phase 3 遗漏） |
| 新增 `spring-boot-starter-test`、`testcontainers`、`junit-jupiter`（test scope） | Controller 测试基础设施准备 |

### SB4.1.0 兼容性修复

| 问题 | 修复 |
|------|------|
| `@EntityScan` 在 SB4.1.0 被移除 | `JpaConfig` 使用 `EntityManagerFactoryBuilderCustomizer` + 类路径扫描注册实体 |
| `@EnableJpaRepositories` 缺失导致 0 repository 发现 | 添加 `@EnableJpaRepositories(basePackages = "io.epiphaneia.agent.api.repository")` |
| `@ComponentScan` 独立于 `scanBasePackages` | `@SpringBootApplication` 默认扫描自身包，用独立 `@ComponentScan(basePackages = "io.epiphaneia")` 跨模块扫描 |

### 配置变更

| 文件 | 变更 |
|------|------|
| `application-local.yml`（gitignored） | 本地开发配置：DB 连接、Flyway 禁用、Hibernate ddl-auto: update、DeepSeek API Key |
| `.env.example` | 修正注释（`OPENAI_API_KEY` 标记为可选，前端可配） |
| `.gitignore` | 新增 `**/application-local.yml` |
| `OpenApiConfig.java` | SpringDoc OpenAPI 配置 |
| `pom.xml` | OWASP skip 默认、springdoc.version property |

## 首次启动验证

```
Tomcat started on port 8080
Started EpiphaneiaApplication in 7.205 seconds
Admin credentials generated: admin / <random>
```

启动命令：

```powershell
cd product
.\mvnw clean install -DskipTests
$env:SPRING_PROFILES_ACTIVE="local"
.\mvnw spring-boot:run -pl epiphaneia-server
```

## 架构决策记录

### ADR-007: `@ComponentScan` 独立于 `@SpringBootApplication.scanBasePackages`

`@SpringBootApplication(scanBasePackages = "io.epiphaneia")` 在 SB4.1.0 fat jar (`nested:` URL) 下组件扫描行为不确定。改用独立 `@ComponentScan(basePackages = "io.epiphaneia")` 确保跨模块 Bean 发现。

### ADR-008: MapStruct processor 依赖方式

`annotationProcessorPaths` 方式干扰 javac 多轮编译，导致 MapStruct 生成的 Impl 编译错误（DTO 类型 unresolved）。改为 `provided` scope 依赖，javac 自动从类路径发现处理器，多轮编译正确处理依赖。
