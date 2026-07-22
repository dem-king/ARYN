# 多租户联表别名审计实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复消息通知和权限用户查询中的多租户联表列歧义，并通过全仓 Mapper 审计阻止无别名租户联表再次进入代码库。

**Architecture:** 生产 SQL 仅增加与原表名相同的显式自别名，使 MyBatis-Plus 多租户拦截器生成带表限定符的 `tenant_id`。跨模块规则放在聚合全部业务模块的 `aryn-boot` 测试中；测试读取 Boot 租户表配置，对 Mapper XML 做轻量、括号层级感知的 SQL 词法检查，只审计同一查询层级存在 `JOIN` 的 `FROM`/`JOIN` 租户表引用。

**Tech Stack:** Java 17、JUnit 5、AssertJ、SnakeYAML、Maven、MyBatis XML、MyBatis-Plus 3.5.15

---

### Task 1: 更新 Graphify 影响图

**Files:**
- Generated only: `graphify-out/`（Git 忽略）

**Step 1: 更新本地代码图谱**

Run:

```powershell
graphify update . --no-cluster
```

Expected: 命令成功更新 `graphify-out/graph.json`，不产生需提交文件。

**Step 2: 查询相关节点影响范围**

Run:

```powershell
graphify query "MessageRecipientMapper SysUserMapper tenant join alias" --graph graphify-out/graph.json --budget 2000
```

Expected: 确认变更局限于 Mapper SQL、租户拦截器生成 SQL 和对应调用方，不修改实体或公共拦截器。

### Task 2: 添加全仓租户联表别名审计测试（RED）

**Files:**
- Create: `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantJoinAliasAuditTest.java`

**Step 1: 编写词法分析器样例测试**

测试类需要包含以下行为：

```java
@Test
void rejectsUnaliasedTenantTablesInSameJoinScope() {
    String sql = "SELECT * FROM message_recipient "
            + "INNER JOIN message_notice ON message_notice.id = message_recipient.message_id";

    assertThat(missingAliases(sql, Set.of("message_recipient", "message_notice")))
            .containsExactlyInAnyOrder("message_recipient", "message_notice");
}

@Test
void acceptsAliasedTenantTablesInSameJoinScope() {
    String sql = "SELECT * FROM message_recipient AS message_recipient "
            + "INNER JOIN message_notice AS message_notice "
            + "ON message_notice.id = message_recipient.message_id";

    assertThat(missingAliases(sql, Set.of("message_recipient", "message_notice"))).isEmpty();
}

@Test
void ignoresUnaliasedSingleTableSubqueriesJoinedByOuterDerivedAliases() {
    String sql = "SELECT * FROM (SELECT * FROM order_info) o "
            + "LEFT JOIN (SELECT * FROM order_refund) r ON 1 = 1";

    assertThat(missingAliases(sql, Set.of("order_info", "order_refund"))).isEmpty();
}
```

词法分析器应：

- 删除 XML 标签与单引号字符串内容后再分词。
- 记录 `SELECT` 所在括号深度，分别分析每个查询层级。
- 只在该层级存在 `JOIN` 时检查 `FROM`、`JOIN` 表引用。
- 允许 `table alias` 与 `table AS alias`。
- 将 SQL 关键字排除为别名。

**Step 2: 编写全仓审计测试**

```java
@Test
void allTenantTablesInJoinScopesUseExplicitAliases() throws IOException {
    Set<String> tenantTables = tenantTablesFromBootYaml();
    List<String> violations = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(projectRoot)) {
        for (Path path : paths.filter(TenantJoinAliasAuditTest::isMapperXml).toList()) {
            String xml = Files.readString(path);
            Matcher statements = MAPPER_SELECT.matcher(xml);
            while (statements.find()) {
                String statementId = statements.group(1);
                for (String table : missingAliases(statements.group(2), tenantTables)) {
                    violations.add(relative(path) + "#" + statementId + ":" + table);
                }
            }
        }
    }

    assertThat(violations)
            .as("多租户表参与 JOIN 时必须声明表别名")
            .isEmpty();
}
```

失败输出必须包含相对文件、Mapper 方法 ID 和表名，便于开发者直接定位。

**Step 3: 运行测试并确认 RED**

Run:

```powershell
cd aryn-mall-java
mvn -pl aryn-boot -am "-Dtest=TenantJoinAliasAuditTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: 样例测试通过，全仓审计失败，并至少列出：

- `MessageRecipientMapper.xml#selectInbox`
- `MessageRecipientMapper.xml#selectInboxDetail`
- `MessageRecipientMapper.xml#countUnread`
- `SysUserMapper.xml#selectMessageRecipients`
- `SysUserMapper.xml#countCustomerServiceStaff`

### Task 3: 给消息通知联表增加显式自别名（GREEN 一部分）

**Files:**
- Modify: `aryn-mall-java/aryn-message/aryn-message-biz/src/main/resources/mapper/MessageRecipientMapper.xml`

**Step 1: 修改三个查询**

将三个查询中的：

```sql
FROM message_recipient
INNER JOIN message_notice ON
```

修改为：

```sql
FROM message_recipient AS message_recipient
INNER JOIN message_notice AS message_notice ON
```

只增加别名，不修改字段、过滤条件、排序或分页。

**Step 2: 运行审计测试**

Run:

```powershell
cd aryn-mall-java
mvn -pl aryn-boot -am "-Dtest=TenantJoinAliasAuditTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: 消息模块违规消失，测试仍因 `SysUserMapper.xml` 两个查询失败。

### Task 4: 给权限用户联表增加显式自别名（GREEN）

**Files:**
- Modify: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/resources/mapper/SysUserMapper.xml`

**Step 1: 修改 `selectMessageRecipients` 内层 EXISTS**

```sql
FROM sys_user_role AS sys_user_role
INNER JOIN sys_role_menu AS sys_role_menu ON
```

`sys_menu` 不是租户表，本次无需修改。

**Step 2: 修改 `countCustomerServiceStaff`**

```sql
FROM sys_user AS sys_user
INNER JOIN sys_user_role AS sys_user_role ON
INNER JOIN sys_role_menu AS sys_role_menu ON
```

保持现有全表名字段引用不变。

**Step 3: 运行审计测试并确认 GREEN**

Run:

```powershell
cd aryn-mall-java
mvn -pl aryn-boot -am "-Dtest=TenantJoinAliasAuditTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: `TenantJoinAliasAuditTest` 全部通过，零违规。

### Task 5: 补充真实租户拦截器与特殊 JOIN 回归

**Files:**
- Modify: `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantJoinAliasAuditTest.java`

**Step 1: 验证特殊 JOIN 和逗号联表不会绕过审计**

增加 `USING`、`NATURAL JOIN` 和逗号联表样例。先确认旧规则对三种语法存在漏报，再把 `USING`、`NATURAL` 纳入 SQL 关键字集合，并按 `FROM` 子句范围识别同层逗号表源。

**Step 2: 验证真实 MyBatis-Plus 重写结果**

读取 `MessageRecipientMapper.xml` 的实际 `countUnread` SQL，展开本地 `<sql>/<include>`，替换 MyBatis 参数后交给 `TenantLineInnerInterceptor.parserSingle`。断言注入条件包含：

```sql
message_recipient.tenant_id = 'tenant-1'
message_notice.tenant_id = 'tenant-1'
```

**Step 3: 确认运行时 RED/GREEN**

临时移除 `countUnread` 的两个自别名，定向运行该测试，预期输出两个未限定的 `tenant_id = 'tenant-1'` 并失败；恢复别名后预期通过。

### Task 6: 运行模块与聚合回归验证

**Files:**
- No production changes

**Step 1: 运行消息模块测试**

Run:

```powershell
cd aryn-mall-java
mvn test -pl aryn-message/aryn-message-biz -am
```

Expected: 消息模块及依赖模块测试通过。

**Step 2: 运行后端聚合测试**

Run:

```powershell
cd aryn-mall-java
mvn test -pl aryn-boot -am
```

Expected: Maven 返回 0，所有 Reactor 模块 `SUCCESS`，无测试失败。

**Step 3: 检查差异**

Run:

```powershell
git diff --check
git status --short
```

Expected: 无空白错误；只包含本计划文件、审计测试和两个 Mapper 的预期改动，用户原有未提交文件保持不变。

### Task 7: 回写完成记录并提交

**Files:**
- Modify: `docs/90-记录归档/需求记录.md`
- Modify: `docs/plans/2026-07-22-tenant-join-alias-audit.md`

**Step 1: 登记完成结果**

记录问题根因、修改文件、RED/GREEN 证据和最终 Maven 验证结果；不声称未执行的验证通过。

**Step 2: 提交本次实现文件**

仅暂存本任务文件，提交信息使用中文：

```powershell
git add -- aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantJoinAliasAuditTest.java aryn-mall-java/aryn-message/aryn-message-biz/src/main/resources/mapper/MessageRecipientMapper.xml aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/resources/mapper/SysUserMapper.xml docs/plans/2026-07-22-tenant-join-alias-audit.md docs/90-记录归档/需求记录.md
git commit -m "修复：防止多租户联表租户列歧义"
```

Expected: 提交只包含本任务文件，不包含用户已有未提交修改。

## 实施结果

- Graphify 已更新到当前工作区，确认影响集中在消息通知、工作人员筛选及其服务调用链，没有修改公共多租户拦截器。
- RED：审计测试首次有效运行时准确列出 `MessageRecipientMapper.xml` 3 个查询和 `SysUserMapper.xml` 2 个查询的 11 个无别名租户表引用；订单派生表子查询未被误报。
- 特殊联表 RED：`USING`、`NATURAL JOIN` 样例在旧规则下分别漏报一张租户表，逗号联表漏报全部租户表；完善关键字和同层 `FROM` 表源识别后通过。
- 运行时 RED：临时移除 `countUnread` 自别名后，真实 `TenantLineInnerInterceptor` 生成两个未限定的 `tenant_id = 'tenant-1'`，测试按预期失败。
- GREEN：恢复两个 Mapper 的显式自别名后，拦截器生成 `message_recipient.tenant_id` 与 `message_notice.tenant_id`；`TenantJoinAliasAuditTest` 10 个测试全部通过。
- 消息模块 `mvn test -pl aryn-message/aryn-message-biz -am` 通过，消息域 44 个测试零失败。
- 后端聚合 `mvn test -pl aryn-boot -am` 通过，41 个 Reactor 模块全部成功，Boot 层 22 个测试零失败。
