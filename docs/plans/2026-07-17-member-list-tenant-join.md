# Member List Tenant Join Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复租户账号访问会员列表时 `tenant_id` 联表条件列名歧义。

**Architecture:** 保留 MyBatis-Plus 自动租户隔离，仅给 `member_level` 联表补充显式别名，使拦截器生成带表限定符的租户条件。通过读取 Mapper XML 的契约测试防止回归。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis-Plus 3.5.15、JUnit 5、AssertJ

---

### Task 1: 增加失败的租户联表契约测试

**Files:**
- Modify: `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantInterceptorBypassAuditTest.java`

1. 读取 `UserInfoMapper.xml` 的 `selectAdminPage` 语句。
2. 断言语句包含 `LEFT JOIN member_level AS member_level`。
3. 运行定向测试，预期因当前缺少 `AS member_level` 而失败。

### Task 2: 实施最小 SQL 修复

**Files:**
- Modify: `aryn-mall-java/aryn-user/aryn-user-biz/src/main/resources/mapper/UserInfoMapper.xml`

1. 将 `LEFT JOIN member_level` 改为 `LEFT JOIN member_level AS member_level`。
2. 重新运行定向测试，预期通过。

### Task 3: 验证与记录

**Files:**
- Modify: `docs/90-记录归档/需求记录.md`

1. 运行 `mvn test -pl aryn-user/aryn-user-biz -am`。
2. 运行 `mvn test -pl aryn-boot -am`。
3. 重启当前 Boot 服务并复测租户会员列表请求，确认生成 `member_level.tenant_id` 且接口成功。
4. 检查 `git diff --check` 与任务范围 diff。
5. 记录修改范围和实际验证结果；按后续用户指令提交并推送。
