# 多租户隔离加固实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐单体与微服务全部租户表隔离，并让 schema、配置或受控绕过发生漂移时在测试或启动阶段立即失败。

**Architecture:** 保留 `hx.tenant.tables` 显式白名单，以 JDBC 元数据中实际包含 `tenant_id` 的表为运行时权威来源，启动时双向校验。仓库测试同时解析 Boot/Cloud SQL 与 Nacos YAML，防止版本库配置漂移；登录前跨租户定位只允许使用数据库唯一标识。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis-Plus、JDBC DatabaseMetaData、JUnit 5、H2、Maven、MySQL SQL/Nacos YAML。

---

### Task 1: 租户 schema 启动校验器

**Files:**
- Modify: `aryn-mall-java/aryn-common/aryn-common-mybatis/pom.xml`
- Modify: `aryn-mall-java/aryn-common/aryn-common-mybatis/src/main/java/com/aryn/cloud/common/myabtis/properties/TenantConfigProperties.java`
- Create: `aryn-mall-java/aryn-common/aryn-common-mybatis/src/main/java/com/aryn/cloud/common/myabtis/tenant/TenantSchemaValidator.java`
- Modify: `aryn-mall-java/aryn-common/aryn-common-mybatis/src/main/java/com/aryn/cloud/common/myabtis/config/MybatisPlusConfig.java`
- Create: `aryn-mall-java/aryn-common/aryn-common-mybatis/src/test/java/com/aryn/cloud/common/myabtis/tenant/TenantSchemaValidatorTest.java`

**Step 1:** 写失败测试，使用 H2 创建租户表和全局表，覆盖配置缺表、配置多表、重复/空白/大小写表名及完全一致场景。

**Step 2:** 运行 `mvn -pl aryn-common/aryn-common-mybatis -am -Dtest=TenantSchemaValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认因校验器不存在而失败。

**Step 3:** 最小实现 `TenantSchemaValidator`：从当前 catalog 的 JDBC 元数据读取包含 `tenant_id` 的普通表，统一转小写，双向比较并输出稳定排序的差异；在 `TenantConfigProperties` 增加默认开启的 `validateSchema`。

**Step 4:** 在 `MybatisPlusConfig` 注册 `ApplicationRunner`，仅在 `hx.tenant.validate-schema=true` 时运行；读取失败或差异不为空时拒绝启动。

**Step 5:** 重跑目标测试并确认通过，再运行公共模块全部测试。

### Task 2: Boot 与 Cloud 配置一致性门禁

**Files:**
- Create: `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantConfigurationConsistencyTest.java`
- Modify: `aryn-mall-java/aryn-boot/pom.xml`
- Modify: `aryn-mall-java/aryn-boot/src/main/resources/application.yml`
- Modify: `aryn-mall-java/db/cloud/3aryn_nacos.sql`

**Step 1:** 写失败测试，解析 Boot/Cloud `CREATE TABLE` 块中的 `tenant_id`、Boot YAML 和 Nacos `config_info` 内嵌 YAML；断言 Boot 与各微服务集合双向相等，且 Nacos 配置 MD5 与内容一致。

**Step 2:** 运行 `mvn -pl aryn-boot -am -Dtest=TenantConfigurationConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认报告会员、分销、拼团缺表以及现有误配表。

**Step 3:** 补齐 Boot 61 张租户表合集，删除 schema 中没有 `tenant_id` 的误配表；分别补齐 user、promotion 等 Nacos 子集，保持稳定的业务分组顺序。

**Step 4:** 按最终 YAML 原文重新计算并写入 Nacos `config_info.md5`，避免导入后内容与校验值漂移。

**Step 5:** 重跑一致性测试直至通过。

### Task 3: 受控租户拦截绕过审计

**Files:**
- Create: `aryn-mall-java/aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantInterceptorBypassAuditTest.java`
- Modify: `aryn-mall-java/db/boot/2aryn_boot.sql`
- Modify: `aryn-mall-java/db/cloud/2aryn_upms.sql`
- Modify: `aryn-mall-java/db/cloud/4aryn_user.sql`
- Modify: `aryn-mall-java/db/cloud/6aryn_pay.sql`
- Create: `aryn-mall-java/db/boot/12tenant_lookup_uniqueness.sql`
- Create: `aryn-mall-java/db/cloud/12tenant_lookup_uniqueness.sql`

**Step 1:** 写失败审计测试，固定允许的 `@InterceptorIgnore(tenantLine = "true")` 方法；要求租户菜单 SQL 显式使用参数化 `tenant_id`，登录用户名/手机号、社交账号 `app_id`、支付配置 `app_id` 有与绕过语义一致的唯一索引。

**Step 2:** 运行目标测试，确认因基础 schema 缺唯一约束而失败。

**Step 3:** 在 Boot/Cloud 基础 schema 和增量迁移中增加唯一索引；迁移在建索引前提供重复数据检查 SQL，发现冲突时中止，不静默删除或合并数据。

**Step 4:** 重跑绕过审计测试并确认通过。

### Task 4: 租户处理器健壮性

**Files:**
- Modify: `aryn-mall-java/aryn-common/aryn-common-mybatis/src/main/java/com/aryn/cloud/common/myabtis/tenant/ArynTenantLineHandler.java`
- Create: `aryn-mall-java/aryn-common/aryn-common-mybatis/src/test/java/com/aryn/cloud/common/myabtis/tenant/ArynTenantLineHandlerTest.java`

**Step 1:** 写失败测试，验证表名匹配不受 MySQL 大小写影响、配置列表为空时安全忽略全局表、租户上下文为空时不产生跨租户条件。

**Step 2:** 运行目标测试确认当前大小写敏感行为失败。

**Step 3:** 最小修改处理器，通过 `TenantConfigProperties` 暴露的规范化不可变集合判断表名，避免每条 SQL 线性扫描可变 List。

**Step 4:** 重跑处理器与 schema 校验器测试。

### Task 5: 文档与全量验证

**Files:**
- Modify: `docs/40-接口与风险/缺失模块清单.md`
- Modify: `docs/20-业务与数据/数据模型.md`
- Modify: `docs/90-记录归档/需求记录.md`

**Step 1:** 将“多租户表清单不完整”从未解决风险更新为已建立配置、启动和测试三层门禁，同时记录数据库迁移前必须先处理重复全局标识。

**Step 2:** 运行 `mvn test -pl aryn-common/aryn-common-mybatis,aryn-boot -am`。

**Step 3:** 运行 `mvn test -pl aryn-boot -am`，读取完整退出码与失败数，不以局部测试代替聚合验证。

**Step 4:** 运行 `git diff --check`，检查只涉及本需求文件，并再次执行 `graphify explain ArynTenantLineHandler` 与 `graphify explain TenantSchemaValidator` 核对影响链路。

