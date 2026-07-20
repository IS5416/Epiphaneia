# 开发实现 (Development)

## 目标

基于架构设计和 PRD，按照任务优先级完成编码实现，产出可交付的软件产品。

## 输入

- `product/` — ArchitectureDesign 阶段产出的 Maven 多模块项目骨架（本阶段编码工作目录）
- `ArchitectureDesign/` 的技术选型、系统架构、安全设计文档
- `ProductPlanning/` 的 PRD、API设计、数据库设计

## 双工作目录约定

Development 阶段有两个工作目录，职责分离：

| 目录 | 用途 | 位置（相对于 Git 根） |
|------|------|----------------------|
| 编码目录 | 所有 Java/TypeScript 代码、构建、测试 | `product/` |
| 流程文档目录 | 执行计划、devLog、代码审查记录 | `Development/` |

**严禁混淆**：编码目录只放代码，流程文档目录只放文档。Java 源文件禁止出现在 `Development/` 中。

## 产出物

| 产出 | 位置 | 说明 |
|------|------|------|
| 业务代码 | `product/` 下各 Maven 模块的 `src/main/` | 完整的业务逻辑实现 |
| 单元测试 | `product/` 下各 Maven 模块的 `src/test/` | 核心逻辑测试覆盖，目标覆盖率 ≥ 80% |
| 代码审查记录 | `Development/codeReviews/` | 每次代码审查的结论和改进项（流程文档，非代码） |
| 开发日志 | `Development/devLog/` | `YYYY-MM-DD-NN-description.md` 格式，`devLog.md` 为索引 |

> **构建命令**：从仓库根目录（`E:/MyProducts/FullProcess`）执行：`cd product && ./mvnw compile`。

## Git 约束（最高优先级）

### 仓库边界

1. **Git 根目录**：`E:/MyProducts/FullProcess`。所有 git 操作（commit、merge、branch、push）必须在仓库根目录执行。
2. **禁止嵌套仓库**：`product/` 不是独立 Git 仓库，禁止在 `product/` 或任何子目录执行 `git init`。
3. **`.gitignore` 位置**：仅在仓库根目录维护。product/ 下的 `.gitignore` 仅用于本地 IDE 忽略，不参与 Git 版本控制决策。
4. **`git status` / `git diff` 检查**：每次操作前确认当前在仓库根目录，使用 `git -C E:/MyProducts/FullProcess <command>` 或先 `cd` 到根目录。

### 分支与提交

5. **分支命名**：`feat/phase{N}-{scope}`，从 `main` 创建。
6. **提交格式**：Conventional Commits 简洁一句式。禁止大段描述，禁止 `Co-Authored-By` 署名。示例：`feat(infra): implement AES-256-GCM encryption service`。
7. **单次变更规模**：单个分支 ≤ 400 行新增代码（不含测试和自动生成代码）。
8. **分支生命周期**：`main` 创建分支 → 功能 + 测试 → 子代理审查 → **人工确认** → 本地 squash merge 到 main → 推送 main 到远程 → 删除本地分支。
9. **远程策略**：仅推送 `main` 到远程。功能分支保留在本地，不走 GitHub PR（无远程协作需求，子代理审查在本地完成，PR 流程多余）。

### 操作纪律

10. **人工确认闸门**：git commit、merge、push 等操作在子代理审查通过且人工确认之前，**禁止执行**。可准备好 commit message 和 staged changes，等待确认。
11. **禁止 force push**：任何情况下禁止 `git push --force`。禁止 `git reset --hard` 到远程分支之前的提交。
12. **禁止跳过 hooks**：禁止 `--no-verify`、`--no-gpg-sign`、`-c commit.gpgsign=false`。
13. **提交前检查**：每次 commit 前检查 `README.md` 是否需要同步更新（技术栈变更、阶段状态推进、项目结构变化等）。

## 里程碑审查

14. **子代理审查（强制执行）**：每个 Phase 完成后视为里程碑节点，必须派出相关领域的子代理（subagent）进行代码审查。审查内容：
    - 代码与设计文档（ArchitectureDesign/、ProductPlanning/）的一致性
    - 包边界规则（ArchUnit：`*.api.*` 不导入框架类，`*.internal.*` 不被其他模块导入）
    - 安全约束（只读边界、凭证零回显、参数化查询、PII 保护）
    - 测试覆盖率和测试质量
    - 审查结果写入 `Development/codeReviews/` 目录
15. **禁止自查**：主 agent 不得审查自己产出的代码。必须派出独立子代理。

## 实现策略选择（编码前评估）

每次开始编码任务前，必须先评估工作量和难度，选择最合适的实现方案：

| 策略 | 适用场景 | 特点 |
|------|---------|------|
| **主 agent 自行编写** | ≤ 3 个文件、逻辑直接、单模块内 | 最快，上下文完整 |
| **单个子 agent 编写** | 独立模块、逻辑复杂但边界清晰、与主线程无耦合 | 并行执行，不占用主线程 |
| **多个子 agent 并行** | 多个独立文件、同一 pattern 重复、无相互依赖 | 加速，各管一摊 |
| **Workflow 工作流协作** | 跨模块、多步骤、需要审查 + 验证 + 修复循环、或 fan-out 探索 | 适合复杂编排，token 成本较高 |
| **混合方案** | 先主 agent 写核心框架，子 agent 并行补测试和边缘实现 | 兼顾质量与速度 |

**评估维度**：
1. **文件数量**：单模块 ≤ 3 个实现文件 → 主 agent；多模块或 > 5 个文件 → 考虑子 agent / Workflow
2. **依赖深度**：文件之间存在强依赖 → 主 agent 或 Workflow 流水线；完全独立 → 并行子 agent
3. **逻辑复杂度**：简单 CRUD/工具类 → 主 agent；核心业务（如 ReAct 循环）→ 主 agent 或 Workflow
4. **测试需求**：测试逻辑复杂、需要多场景覆盖 → 子 agent 并行写测试

**默认策略**：主 agent 自行编写。只在明确满足子 agent/Workflow 适用条件时才切换。

## 能力边界与人工干预

遇到以下场景，主 agent **必须主动告知用户**，提供清晰的操作指引。**禁止自行降级处理、禁止静默跳过、禁止用 mock 替代真实配置**。

### 需要人工操作的场景

| 场景 | 触发条件 | 告知内容 |
|------|---------|---------|
| **LLM 提供商配置** | 需要配置 API Key、选择模型、设置 base URL | 告知需要哪个环境变量（如 `OPENAI_API_KEY`），提供获取方式 |
| **外部服务依赖** | Prometheus URL、Elasticsearch URL、Actuator 端点等 | 告知需要准备哪些服务地址及认证凭证 |
| **Docker 环境** | 需要创建/启动容器、Testcontainers 不可用、Docker Compose 部署 | 告知需要安装 Docker、提供 `docker compose up` 命令 |
| **数据库初始化** | Flyway 迁移执行、初始密码生成、种子数据导入 | 告知数据库连接参数、查看初始密码的位置 |
| **SSL/证书配置** | Nginx TLS 终端、证书路径配置 | 告知证书放置路径和 Nginx 配置方式 |
| **生产密钥生成** | 加密密钥（`EPIPHANEIA_ENCRYPTION_KEY`）、JWT secret 等 | 告知生成命令（如 `openssl rand -hex 32`）和配置位置 |
| **端口/网络冲突** | 端口被占用、防火墙阻止 | 告知冲突端口号和修改方式 |
| **第三方 API 配额** | API 调用频率限制、余额不足 | 告知当前使用情况和解决方案 |

### 原则

- **能力边界诚实**：环境不存在、服务不可达、权限不足 → 如实报告，提供解决步骤
- **降级透明**：若确实无法满足某个条件，明确告知用户"当前缺少 X，导致 Y 功能不可用，需要你完成 X 后继续"
- **mock 仅限测试**：仅单元测试中允许使用 mock。任何涉及真实运行（集成测试、端到端验证、启动应用）的场景，必须使用真实配置

## 行为约束

16. **测试先行**：核心业务逻辑优先写测试（TDD），工具类和 CRUD 允许测试后补。
17. **代码规范**：遵循 Java 社区规范（阿里巴巴 Java 开发手册），统一 `Google Java Style` 格式化。
18. **安全编码**：遵守 `ArchitectureDesign/securityDesign.md` 中定义的安全约束。
19. **依赖管理**：新增第三方依赖需在 commit 中注明用途和风险评估。
20. **环境变量**：所有配置信息（数据库连接、密钥等）通过环境变量注入，禁止硬编码。
21. **工作目录**：所有编码、构建、测试命令以 `product/` 为工作目录。禁止在仓库根目录或阶段目录中创建 Java 源文件。

## 退出条件

- 所有 P0 功能完成
- 单元测试全部通过，覆盖率达标
- 代码审查无未解决的阻塞性问题
- API 文档与实现一致
