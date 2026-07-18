# 开发实现 (Development)

## 目标

基于架构设计和 PRD，按照任务优先级完成编码实现，产出可交付的软件产品。

## 输入

- `product/` — ArchitectureDesign 阶段产出的 Maven 多模块项目骨架（本阶段的工作目录）
- `ArchitectureDesign/` 的技术选型、系统架构、安全设计文档
- `ProductPlanning/` 的 PRD、API设计、数据库设计

## 产出物

| 产出 | 位置 | 说明 |
|------|------|------|
| 业务代码 | `product/` 下各 Maven 模块的 `src/main/` | 完整的业务逻辑实现 |
| 单元测试 | `product/` 下各 Maven 模块的 `src/test/` | 核心逻辑测试覆盖，目标覆盖率 ≥ 80% |
| 代码审查记录 | `Development/codeReviews/` | 每次代码审查的结论和改进项（流程文档，非代码） |
| 开发日志 | `Development/devLog.md` | 每日/每阶段的开发进度和决策记录（流程文档，非代码） |

> **工作目录约定**：编码操作以 `product/` 为根目录。构建命令从仓库根目录执行：`cd product && ./mvnw compile`。代码审查记录和开发日志写回 `Development/` 目录（流程文档与代码分离）。

## 行为约束

1. **分支策略**：每个功能模块使用独立分支开发，合并前通过代码审查。
2. **测试先行**：核心业务逻辑优先写测试（TDD），工具类和CRUD允许测试后补。
3. **代码规范**：遵循 Java 社区规范（阿里巴巴 Java 开发手册），统一 `Google Java Style` 格式化。
4. **禁止大PR**：单个 PR 不超过 400 行新增代码（不含测试和自动生成代码）。
5. **提交规范**：使用 Conventional Commits 格式 (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`)。
6. **安全编码**：遵守 `ArchitectureDesign/securityDesign.md` 中定义的安全约束。
9. **工作目录**：所有编码、构建、测试命令以 `product/` 为工作目录（如 `cd product && ./mvnw test`）。禁止在仓库根目录或阶段目录中创建 Java 源文件。
7. **依赖管理**：新增第三方依赖需在 PR 中注明用途和风险评估。
8. **环境变量**：所有配置信息（数据库连接、密钥等）通过环境变量注入，禁止硬编码。

## 退出条件

- 所有 P0 功能完成
- 单元测试全部通过，覆盖率达标
- 代码审查无未解决的阻塞性问题
- API 文档与实现一致
