# IdeaValidation 阶段回滚提示词

> 本文件是统筹层根据 `ArchitectureDesign/comprehensiveReview.md` 提取的回滚操作指南。
> 请在 IdeaValidation 目录下启动新对话，将本文件内容作为上下文，按指示完成回滚修正。

---

## 使用方式

在新对话中引用本文件，说："请根据本回滚提示词，对 IdeaValidation 阶段文档进行修正。"

---

## 前置阅读

1. `ArchitectureDesign/comprehensiveReview.md` — 全流程综合审查报告（权威来源）
2. `IdeaValidation/AGENTS.md` — 本阶段行为约束
3. 本阶段全部已有文档（回滚前原文）

---

## 回滚任务清单

### RB-6：裁剪 MVP Must Have 范围（CRITICAL — 最优先）

**问题**：MVP 定义了 6 层架构 + 3 大扩展接口 + 4 种 LLM 后端，隐藏范围蔓延。实际交付预估 10-14 周，不是 4-5 周。

**修改文件**：`mvpScope.md`

**修改内容**：

1. **§4.2 六层架构** → 压缩为三层：
   - 数据集成层：Prometheus Connector + ES Connector（硬编码，不走 SPI）
   - LLM 推理层：OpenAI 兼容 API 调用 + Prompt 模板（不做多后端抽象）
   - Web UI/API 层：Spring Boot + React + REST API

2. **§2.1 Must Have 清单** → 从 9 项砍到 5 项：
   - 保留：自然语言诊断问答（FR-1）、Prometheus 集成（FR-2）、LLM 根因推断（FR-4）、Web UI（FR-7）、Docker 部署（FR-9）
   - 降级为 P1：ELK/日志集成（FR-3，MVP 先用 Prometheus-only 诊断）、Spring Boot 零配置接入（FR-6，手动配置应用名+label 即可）、REST API（FR-8，Web UI 内嵌 API 够用，独立 API 文档推迟）
   - 降级为 P1：SSE 流式输出（FR-11，先返回完整结果，流式体验推迟）
   - 扩展接口（Skill/Adapter/Connector SPI）：全部砍掉，MVP 硬编码，v1.0 再抽象

3. **§4.1 技术栈表** → 更新：去掉 LangChain4j（MVP 直接 HTTP 调 LLM API）、去掉 Spring AI（同上）、LLM 后端只列 OpenAI 兼容 API（Ollama 默认兼容此格式）

4. **§6 工作量估算** → 重新估算：5 项 Must Have，预估 4-6 周

**级联影响（记录到 rollbackLog.md，传递给 ProductPlanning）**：
- PRD.md 的 FR 编号和 P0/P1 需要同步调整
- systemArchitecture 的 Maven 模块数从 5 减到 2-3
- Development 迭代计划从头重排

---

### RB-7：补充遗漏竞品分析（HIGH）

**问题**：竞品分析遗漏了 HolmesGPT、k8sgpt、Grafana AI/ML 等重要竞品。

**修改文件**：`competitorAnalysis.md`

**修改内容**：

1. 在 §2 直接竞品中补充 precious112/Argus 的 GitHub 指标（stars、最近更新时间、贡献者数）
2. 新增竞品条目：
   - **Grafana AI/ML**：已在用户"监控首页"，加入 AI 诊断 zero migration cost → 🟡 最危险的间接竞品
   - **HolmesGPT**：功能 1:1 重叠（自然语言 → 查数据源 → LLM 诊断 → 报告），需评估其社区规模
   - **k8sgpt**：K8s 生态 AI 诊断，GitHub 5k+ stars，但 K8s 特化而非通用 Spring Boot
   - **RunWhen Local**：自托管故障排查，侧重 K8s
   - **Keep**：开源告警管理，与 AlertManager 有重叠但非直接竞品
3. 在 §4 竞争优势中增加"Grafana 共存策略"：Epiphaneia 的诊断结果可嵌入 Grafana Panel / Annotation，而非取代 Grafana

**级联影响**：
- PRD 产品定位论述可能需要微调
- README 竞品对比表需同步更新（LOW，可延后）

---

### RB-8：为关键假设补充量化验证标准（HIGH 下调为建议）

**问题**：4 条关键假设全部使用软性表述，无量化验证标准。

**修改文件**：`validationConclusion.md`

**修改内容**：

在 §6 关键假设中，为每条假设补充量化验证标准，例如：
- 假设 1（Java 开发者意愿）：GitHub stars 100+ 且至少 5 个外部部署实例
- 假设 2（诊断准确度）：金标准故障集 top-1 ≥ 60%、top-3 ≥ 80%
- 假设 3（社区贡献）：发布后 3 个月内至少 1 个社区 PR（非作者）
- 假设 4（企业付费意愿）：至少 5 个"托管版"注册意向

每条假设增加止损条件：如果 N 个月后仍未达到 X，则调整策略为 Y。

---

### RB-9：增加市场数据可追溯性标注（HIGH 下调为建议）

**问题**：市场数据引用为二手转述，TAM 10% 假设无依据。

**修改文件**：`marketResearch.md`

**修改内容**：

1. 在 §5 数据源中为每个引用标注数据来源级别：`[直接引用]` vs `[转述自 XX]` vs `[作者推算]`
2. 补充 Google Trends 查询参数（关键词、时间范围、地域）
3. 在 §2.1 TAM 推算中，为 10% 假设增加推理链："基于 Stack Overflow 2025 调查中 X% 的开发者参与 on-call，其中 Y% 表示对 AI 诊断工具有兴趣"

---

## 产出物要求

完成以上修正后，在 `IdeaValidation/` 目录下创建 `rollbackLog.md`，记录：

```markdown
# IdeaValidation 回滚修正记录

> 日期：YYYY-MM-DD
> 依据：ArchitectureDesign/comprehensiveReview.md

## 处理清单

| 编号 | 项目 | 状态 | 说明 |
|------|------|------|------|
| RB-6 | MVP 范围裁剪 | ✅ 已处理 / ⚠️ 部分处理 / ❌ 驳回 | 简述改了什么 |
| RB-7 | 竞品补遗 | ... | |
| RB-8 | 量化验证标准 | ... | |
| RB-9 | 市场数据可追溯性 | ... | |

## 修改文件清单

- mvpScope.md：§2.1/§4.1/§4.2/§6 修改
- competitorAnalysis.md：§2/§3/§4 补充
- ...

## 对下游的级联影响（传递给 ProductPlanning）

- PRD FR 编号需从 12 项缩减为 X 项
- systemArchitecture Maven 模块数从 5 减到 Y
- ...
```

---

## 注意事项

1. **RB-6 是决定性修改** — 它会影响 ProductPlanning 和 ArchitectureDesign，改完必须清楚记录级联影响
2. **不要修改 AGENTS.md 或 CLAUDE.md** — 只改业务文档
3. **保持 camelCase 文件命名** — 不改文件名
4. **有疑问时参考 comprehensiveReview.md 为权威来源** — 本提示词是摘要，原文更详细
5. **驳回需说明理由** — 如果你认为某条回滚不应该执行，在 rollbackLog 中写明原因
