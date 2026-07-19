# ArchitectureDesign 阶段回滚提示词

> 本文件是统筹层根据 `comprehensiveReview.md` 提取的回滚操作指南。
> 请在 ArchitectureDesign 目录下启动新对话，将本文件内容作为上下文，按指示完成回滚修正。

---

## 使用方式

在新对话中引用本文件，说："请根据本回滚提示词，对 ArchitectureDesign 阶段文档进行修正。"

---

## 前置阅读

1. `ArchitectureDesign/comprehensiveReview.md` — 全流程综合审查报告（权威来源，特别关注 §需回滚项清单）
2. **`IdeaValidation/rollbackLog.md`** — IdeaValidation 回滚的级联影响（**必须等 IdeaValidation 完成后读取**）
3. **`ProductPlanning/rollbackLog.md`** — ProductPlanning 回滚的级联影响（**必须等 ProductPlanning 完成后读取**）
4. **IdeaValidation 和 ProductPlanning 修改后的文件** — 特别是 `mvpScope.md`、`PRD.md`、`domainModel.md`、`apiDesign.md`、`dbSchema.md`
5. `ArchitectureDesign/AGENTS.md` — 本阶段行为约束
6. 本阶段全部已有文档（回滚前原文）：techStack.md、systemArchitecture.md、securityDesign.md、deploymentArchitecture.md

---

## 本阶段自身回滚任务

### RB-1：修正 connector ↔ infra 循环依赖（CRITICAL — 编译级阻塞）

**问题**：`epiphaneia-infra` 中的 `ConnectorRegistry` 引用 `epiphaneia-connector` 中的 `Connector` 类型，而 `connector` 又声明依赖 `infra`。`connector → infra → connector` 循环，Maven 拒绝编译。

**修改文件**：`techStack.md` §3、`systemArchitecture.md` §3.2/§4.4/§4.5

**修改内容**：

1. **techStack.md §3 模块依赖表**：
   - 将 `Connector<T,R>`、`QueryRequest`、`QueryResult`、`DataSourceType` 从 `epiphaneia-connector/api` 移动到 `epiphaneia-infra/api`
   - `ConnectorRegistry` 接口保留在 `infra/api`（本来就是）
   - `connector` 模块职责从"SPI 定义 + 实现"退化为"纯实现"（仅含 PrometheusConnector、ElasticsearchConnector 的实现代码）
   - 新的模块依赖：`connector → infra`（单向），`engine → infra`（通过 ConnectorRegistry），`agent-core → infra`
   - 禁止 `infra → connector` 的任何依赖

2. **systemArchitecture.md §3.2 模块接口表**：
   - `epiphaneia-infra` 的 public API 包新增：`Connector<T,R>` 接口、`QueryRequest`、`QueryResult`、`DataSourceType` 常量
   - `epiphaneia-connector` 的 public API 包：无（纯实现模块，不对外暴露接口）

3. **systemArchitecture.md §4.4 扩展点架构**：
   - Connector SPI 图更新：SPI 定义在 infra，实现在 connector
   - 新增 Connector 步骤说明："实现 `Connector<T,R>` 接口（在 `io.epiphaneia.infra.api`）→ 添加 `@Component` → 自动注册"

4. **systemArchitecture.md §4.5 ConnectorRegistry**：
   - 位置说明更新：`ConnectorRegistry` 接口在 `infra/api`，`ConnectorRegistryImpl` 在 `infra/internal`——两者同模块，`impl` 中 `import ...connector.internal.PrometheusConnector` 是 `infra → connector` 方向。**此依赖方向正确**（infra 是共享基础设施，可以依赖具体实现模块来做 Bean 收集）

---

### RB-2：重写 ArchUnit 规则使用实际包名（CRITICAL — CI 阻塞）

**问题**：techStack §2.12 示例 ArchUnit 规则使用 `..domain..` 和 `..infrastructure..`，但项目实际包名是 `..agent..` 和 `..infra..`。规则永远匹配不到任何类。

**修改文件**：`techStack.md` §2.12、`systemArchitecture.md` §12 包结构

**修改内容**：

1. **techStack.md §2.12**：基于实际包名重写全部示例规则：

```java
// 领域层不导入基础设施层
classes().that().resideInAPackage("..agent..")
    .should().onlyDependOnClassesThat()
    .resideOutsideOfPackage("..infra.internal..")
    .resideOutsideOfPackage("..connector..")
    .resideOutsideOfPackage("..server..");

// infra/api 不依赖 connector、engine、server
classes().that().resideInAPackage("..infra.api..")
    .should().onlyDependOnClassesThat()
    .resideOutsideOfPackage("..connector..")
    .resideOutsideOfPackage("..engine..")
    .resideOutsideOfPackage("..server..");

// connector.internal 不依赖 engine、agent、server
// engine.internal 不依赖 agent、server
// server 可以依赖所有模块（应用入口）
```

规则清单（至少）：
- domain (agent) → 不依赖 infra.internal / connector / server
- infra.api → 不依赖 connector / engine / server（共享接口不依赖实现）
- connector.internal → 不依赖 engine / agent / server
- engine.internal → 不依赖 agent / server
- 任何模块的 internal 包 → 不被其他模块 import

2. **systemArchitecture.md §12**：模块包结构总览中的 ArchUnit 规则说明同步更新。

---

### RB-3：修正加密密钥配置绑定方式（建议修正）

**问题**：securityDesign §4.2 使用 `@Value("${epiphaneia.encryption.key}")`，可工作但改用 `@ConfigurationProperties` 是最佳实践。

**修改文件**：`securityDesign.md` §4.2

**修改内容**：

将 `AesGcmEncryptionService` 的构造函数参数从 `@Value` 改为注入 `@ConfigurationProperties` 类：

```java
@ConfigurationProperties(prefix = "epiphaneia.encryption")
public record EncryptionProperties(String key) {}

@Component
public class AesGcmEncryptionService implements EncryptionService {
    public AesGcmEncryptionService(EncryptionProperties props) {
        this.encryptor = new AesBytesEncryptor(
            Base64.getDecoder().decode(props.key()),
            new SecureRandom()
        );
    }
}
```

在 securityDesign 中补充说明：在 `@SpringBootApplication` 类上加 `@EnableConfigurationProperties(EncryptionProperties.class)`。

---

## 适配上游回滚的级联影响

**⚠️ 此部分必须在 IdeaValidation 和 ProductPlanning 阶段都完成后、阅读它们的 rollbackLog.md 和修改后的文档之后执行。**

### 适配 IdeaValidation RB-6（MVP 范围裁剪）

1. **techStack.md**：
   - §3 模块物理划分：Maven 模块数从 5 减到匹配新的需求（预计 2-3 个：`epiphaneia-server` + `epiphaneia-connector` 或合并为单模块）
   - 如果 LangChain4j/Spring AI 被移除，删除对应的依赖声明
   - 如果 LLM 后端只保留 OpenAI 兼容 API，删除多后端的 `ModelRouter` 描述

2. **systemArchitecture.md**：
   - §2 逻辑分层：六层压缩为三层（数据集成 + LLM 推理 + Web UI/API），保留作为包内部分层的概念
   - §3 物理模块映射：模块数从 5 → 2-3，重新画依赖图
   - §4 组件架构：删除 Skill SPI/Adapter SPI 相关描述，改为硬编码实现
   - §7 ADR：如果双框架决策（ADR-003）不再适用，标记为 `DEPRECATED` 并说明理由
   - §11 技术债务表更新

3. **securityDesign.md**：如果多后端被砍，§8.2 数据泄露风险中关于多 LLM 后端的内容可以简化

### 适配 ProductPlanning RB-4（ABORTED 终态）

1. **systemArchitecture.md**：
   - 状态机描述与 userFlow/domainModel 保持同步（新增 ABORTED）
   - §7.1 事务管理或 §7.3 异步任务中增加启动扫描逻辑描述：
     "应用启动时 `@PostConstruct` 扫描 `message` 表所有非终态记录 → 标记为 ABORTED（reason='Server restart'）→ 释放被锁定的会话"
   - 组件图中 `DiagnosisStateMachine` 增加 ABORTED 状态

2. **deploymentArchitecture.md**：如果增加了启动扫描，健康检查的 readiness 探针可能需要等待扫描完成

---

## 产出物要求

完成以上修正后，在 `ArchitectureDesign/` 目录下创建 `rollbackLog.md`，记录：

```markdown
# ArchitectureDesign 回滚修正记录

> 日期：YYYY-MM-DD
> 依据：comprehensiveReview.md + IdeaValidation/rollbackLog.md + ProductPlanning/rollbackLog.md

## 自身回滚

| 编号 | 项目 | 状态 | 说明 |
|------|------|------|------|
| RB-1 | 循环依赖修正 | ✅/⚠️/❌ | |
| RB-2 | ArchUnit 规则重写 | ... | |
| RB-3 | 加密配置最佳实践 | ... | |

## 适配上游级联

| 来源 | 适配内容 | 状态 |
|------|---------|------|
| IdeaValidation RB-6 | 模块数缩减、层精简 | ✅/⚠️/❌ |
| ProductPlanning RB-4 | ABORTED 终态同步 | ... |

## 修改文件清单

- techStack.md：...
- systemArchitecture.md：...
- securityDesign.md：...

## projectScaffold 就绪检查

- [ ] Maven 模块依赖方向单向无循环（RB-1 验证）
- [ ] ArchUnit 规则基于实际包名（RB-2 验证）
- [ ] 状态机含 ABORTED 终态（RB-4 验证）
- [ ] MVP 范围与 mvpScope 一致
```

---

## 来自 comprehensiveReview 的相关 MEDIUM 项

以下 MEDIUM 发现可在本阶段回滚中一并处理：

| 编号 | 问题 | 涉及文档 | 建议 |
|------|------|---------|------|
| M-F1 | 虚拟线程三项隐藏成本 | techStack §2.3、securityDesign | 补充 SecurityContext/MDC/HikariCP 配置说明 |
| M-F2 | 双 AI 框架论证不充分 | techStack §2.4、ADR-003 | 如果 MVP 砍了 LangChain4j，直接删除 ADR-003 |
| M-F13 | CSRF 配置用修正版 | securityDesign §7.1 | 只保留修正版代码，删除"注意"段 |
| M-F14 | ConnectorRegistry 归属矛盾 | domainModel §6.3 → RB-1 一起解决 | — |
| M-F17 | 请求体大小限制 | securityDesign §6 | 补充 `spring.servlet.multipart.max-request-size` 配置 |
| O-3 | SSE 回放内存缓存 | systemArchitecture §4.1 SseEventReplayer | 如 SSE 未降级 P1，增加 Caffeine 事件缓存策略 |

---

## 注意事项

1. **等 IdeaValidation 和 ProductPlanning 都完成后再开始** — 本阶段是最后一棒，上游改动全部确定后才能正确适配
2. **RB-1 是物理修改** — 接口移动会影响 projectScaffold 的包结构，必须准确
3. **RB-2 必须逐包验证** — 基于 systemArchitecture §12 包结构总览逐包写规则
4. **不修改 AGENTS.md 或 CLAUDE.md**
5. **有疑问参考 comprehensiveReview.md** — 本提示词是操作摘要
6. **projectScaffold 就绪检查清单** — 回滚完成后对照检查，确认无误后再进入 projectScaffold
