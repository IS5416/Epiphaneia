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
7. **PR 规模**：单个 PR ≤ 400 行新增代码（不含测试和自动生成代码）。
8. **分支生命周期**：`main` 创建分支 → 功能 + 测试 → PR → 子代理审查 → **人工确认** → squash merge 到 main → 删除分支。

### 操作纪律

9. **人工确认闸门**：git commit、merge、push 等操作在子代理审查通过且人工确认之前，**禁止执行**。可准备好 commit message 和 staged changes，等待确认。
10. **禁止 force push**：任何情况下禁止 `git push --force`。禁止 `git reset --hard` 到远程分支之前的提交。
11. **禁止跳过 hooks**：禁止 `--no-verify`、`--no-gpg-sign`、`-c commit.gpgsign=false`。
12. **提交前检查**：每次 commit 前检查 `README.md` 是否需要同步更新（技术栈变更、阶段状态推进、项目结构变化等）。

## 里程碑审查

13. **子代理审查（强制执行）**：每个 Phase 完成后视为里程碑节点，必须派出相关领域的子代理（subagent）进行代码审查。审查内容：
    - 代码与设计文档（ArchitectureDesign/、ProductPlanning/）的一致性
    - 包边界规则（ArchUnit：`*.api.*` 不导入框架类，`*.internal.*` 不被其他模块导入）
    - 安全约束（只读边界、凭证零回显、参数化查询、PII 保护）
    - 测试覆盖率和测试质量
    - 审查结果写入 `Development/codeReviews/` 目录
14. **禁止自查**：主 agent 不得审查自己产出的代码。必须派出独立子代理。

## 行为约束

15. **测试先行**：核心业务逻辑优先写测试（TDD），工具类和 CRUD 允许测试后补。
16. **代码规范**：遵循 Java 社区规范（阿里巴巴 Java 开发手册），统一 `Google Java Style` 格式化。
17. **安全编码**：遵守 `ArchitectureDesign/securityDesign.md` 中定义的安全约束。
18. **依赖管理**：新增第三方依赖需在 PR 中注明用途和风险评估。
19. **环境变量**：所有配置信息（数据库连接、密钥等）通过环境变量注入，禁止硬编码。
20. **工作目录**：所有编码、构建、测试命令以 `product/` 为工作目录。禁止在仓库根目录或阶段目录中创建 Java 源文件。

## 退出条件

- 所有 P0 功能完成
- 单元测试全部通过，覆盖率达标
- 代码审查无未解决的阻塞性问题
- API 文档与实现一致
