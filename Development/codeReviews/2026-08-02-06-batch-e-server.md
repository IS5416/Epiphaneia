# 2026-08-02 批次 E server 层修复审查

**范围**：feat/phase8-server 分支（4 文件 + 1 新测试文件）
**审查方式**：代码审查子代理审查 git diff
**审查结论**：通过（0 P0 / 0 P1）；3 P2 + 3 P3 中 2 P2 + 2 P3 已修，1 P2 + 1 P3 留后

---

## 修复内容

### P1（基线审查遗留）

- **hasActiveDiagnosis O(N)→O(1)** — 加载全部 messages 检查改为 DB 侧 `existsByConversationAndDiagnosisStateIn`；corrupt 状态字符串不再 valueOf 抛异常（SQL IN 过滤天然安全）
- **delete 不清理 SSE** — 删除前 `sseManager.close(id)`，避免事件进入已删会话
- **replayEvents 假实现** — 前端零引用，删除路由（创建后立即 close 的假实现）
- **sendMessage 无长度校验** — blank/2000 字符上限
- **SSE 异常处理** — send 捕获全部异常（原仅 IOException，已完成的 emitter 抛 IllegalStateException 会中断 fan-out）；close 逐个 try-catch complete

### 测试（+13）

ConversationControllerTest 纯 mock 单测：list 三路径、create 校验、get 存在/缺失、delete 顺序（InOrder 验证）、sendMessage blank/超长/正常/诊断失败传播、getReport

## 审查发现与修复

- **P2 事务耦合**：delete 的 @Transactional 把 SSE close 兜进 DB 事务 — 级联删除失败时 SSE 已关但数据未删。修复：去掉 controller 层 @Transactional（JpaRepository.deleteById 自带事务），SSE 生命周期与事务解耦
- **P2 测试顺序**：deleteClosesSseFirst 未验证顺序 — 改 InOrder 验证
- **P2 虚拟线程测试**：timeout 2s→5s 缓解 CI 负载
- **P3 空 IN 子句**：Javadoc 注明调用方必须传非空集合
- **P3 缺失场景**：补诊断失败异常传播测试

## 审查遗留（不阻塞）

1. SseEmitterManager.close 竞态窗口（send 移除与再次 remove 之间新 emitter 注册会被误删）— 单管理员工具影响极小
2. 虚拟线程测试仍依赖 timeout（无线程引用 join）— 可接受

## 验证

- 全量测试 ✓（BUILD SUCCESS）
- 构建注意：增量编译偶发时间戳问题（"Nothing to compile" 误判）— 用 `mvn clean test` 排除
