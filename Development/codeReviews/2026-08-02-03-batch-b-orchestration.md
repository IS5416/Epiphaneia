# 2026-08-02 批次 B 编排层修复审查

**范围**：feat/phase8-orchestration 分支（DiagnosisOrchestratorImpl、ReportSynthesizerImpl + 2 测试文件）
**审查方式**：编排专长子代理审查 git diff
**审查结论**：无 P0/P1，通过可合入；6 P2 中 4 项本次已修复，2 项留后

---

## 修复内容

### P0

- **超时机制接入执行循环** — checkTimeout 在每 phase 前检查 `isTimedOut`（150s），超时抛 DiagnosisAbortedException → ABORTED。此前超时机制定义完整但从未被调用，诊断可永久挂死。
- **templateReport NPE** — hypothesis confidence null 时 `h.getConfidence() * 100` 崩溃。现显示 N/A。

### P1

- **parseState 非法值不再静默转 CREATED** — 拼写错误的状态会绕过 transition 校验重启已结束诊断。现 corrupt 状态抛 IllegalStateException：transition 捕获转 ABORTED（走 abort 而非重启）；execute catch 保护后走 FAILED（安全终态）。

### P2（修复 + 顺手）

- 空查询（buildQueryForDataSource 返回空串）→ 显式抛异常 → failure evidence 记录，不再把空查询传给 connector
- parseRiskAssessment 单词边界（"highlight" 不再误判 HIGH）
- parseSuggestions 上限 10
- Pattern 常量提取（extractConfidence 不再每次编译）
- analysis/plan null 时 log 不 NPE；parseHypotheses null → fallback hypothesis
- 死参数 plan 从 queryingPhase/analyzingPhase/queryDataSource 签名清除（审查发现清理不彻底）
- buildQueryForDataSource 异常链保留（不再吞根因）
- connector null 防护
- LLM 返回 null → 走 template 兜底；question null 统一处理

## 测试（+5 用例，9→13 和 5→6）

- 超时诊断 → ABORTED（反射设置 createdAt）
- corrupt state → ABORTED 不重启
- 空查询 → failure evidence + COMPLETED_PARTIAL
- risk 单词边界（highlight ≠ HIGH，含 assertNotEquals 强化）
- template 处理 null confidence 不 NPE

## 审查遗留（不阻塞，后续批次）

1. 重入 execute 时超时窗口重置（createdAt null 兜底用 start）— 设计选择，需注释
2. corrupt state 时 checkTimeout 跳过超时检查 — 依赖 transition 的 abort 兜底
3. failure evidence summary 仍泛化（根因在日志）— 可接受
4. 测试反射依赖字段名 — 字段重命名时更新测试即可

## 验证

- agent-core 全量测试 ✓（30 用例 0 失败，BUILD SUCCESS）
