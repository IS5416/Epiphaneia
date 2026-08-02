# 2026-08-02 批次 A 安全修复审查

**范围**：feat/phase8-sec-server 分支（6 文件：AdminSeeder、SecurityConfig、BearerTokenFilter、RateLimitFilter、client.ts、sse.ts、App.tsx）
**审查方式**：安全专长子代理（Senior SecOps Engineer）审查 git diff
**审查结论**：初查不可合入（1 P0 + 4 P1），全部修复后复验通过

---

## 审查发现与修复

### P0

- **CSRF 死锁**（SecurityConfig + SetupWizard 流程）— mustChangePassword 首次登录跳 /setup，SetupWizard 无 GET 请求签发 XSRF-TOKEN cookie，change-password POST 无 token → 403，管理员锁死。
  修复：App.tsx 中 /setup 路由包 ProtectedRoute — 挂载即 GET /auth/me（触发 CsrfFilter 签发 cookie），同时补上未认证访问守卫。

### P1

- **TOCTOU 文件权限竞态**（AdminSeeder）— writeString 默认 umask 创建后收紧，窗口期全局可读。
  修复：POSIX 平台改用 `Files.createFile(path, asFileAttribute(rw-------))` 原子创建。
- **凭据文件永不删除**（AdminSeeder）— 密码明文残留 tmp。
  修复：日志显眼提示删除 + daemon 线程 30 分钟自动删除。
- **getAdmin() 无 null 守卫**（BearerTokenFilter）— 孤儿 token（admin 删除未级联）时 NPE → 500。
  修复：null 时按未认证放行下一 filter。
- **lastAccessNanos 非 volatile**（RateLimitFilter）— 弱内存模型下 cleanup 线程可能读到过期值，误逐活跃 bucket。
  修复：字段声明 volatile。

### P2（澄清，无需改动）

- 密码熵 15 字节 = 120 bits，Javadoc 已准确。

---

## CSRF 架构结论

CookieCsrfTokenRepository + plain CsrfTokenRequestAttributeHandler 是 Spring 标准 double-submit cookie 模式，同源 Nginx 部署 + 内网管理工具场景可接受。login 豁免合理（首访无 cookie；登录 CSRF 危害有限）。

## 验证

- Java 编译 ✓（./mvnw compile -pl epiphaneia-server -am）
- 前端类型检查 ✓（npx tsc --noEmit）
- 全量测试 ✓（BUILD SUCCESS，100+ 用例 0 失败）
- graph 增量更新 ✓
