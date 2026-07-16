# 多租户隔离加固设计

## 背景

项目通过 MyBatis-Plus `TenantLineInnerInterceptor` 为 `hx.tenant.tables` 中的表注入
`tenant_id` 条件。当前 Boot 与 Nacos 微服务配置未覆盖会员、余额、标签、分销和拼团等
已包含 `tenant_id` 的业务表，遗漏时拦截器会静默跳过，存在跨租户访问风险。

## 目标

- Boot 配置覆盖单体数据库内全部租户业务表。
- 各微服务 Nacos 配置只覆盖本服务数据库内的租户业务表。
- 配置缺表、多表或表名大小写差异能够在测试和应用启动阶段被发现。
- `@InterceptorIgnore(tenantLine = "true")` 只能用于经过审计、显式处理租户边界的查询。
- 保持现有平台管理员切租户、移动端 `tenant-id` 和 Dubbo 租户透传协议不变。

## 方案

保留显式租户表白名单。将数据库 schema 中实际包含 `tenant_id` 的表视为权威来源，
双向校验 schema 与 `hx.tenant.tables`：schema 有但配置没有属于隔离缺口，配置有但 schema
没有会导致错误 SQL，两种情况都视为配置错误。

在 `aryn-common-mybatis` 增加可复用的 schema 校验组件。应用启动后通过 JDBC 元数据读取当前
schema 的表与列，规范化表名后和 `TenantConfigProperties` 比较；发现差异时抛出异常并拒绝
启动。校验默认启用，并提供显式配置开关，仅用于没有完整 schema 的特殊工具或测试环境。

仓库级测试读取 Boot/Cloud 建表脚本和 Nacos 内嵌 YAML，验证：

- Boot 白名单等于 Boot schema 的全部租户表；
- user、upms、product、promotion、pay、order 微服务白名单分别等于对应 Cloud schema；
- 白名单无空值、重复项或大小写不一致；
- 受控的租户拦截绕过点保持在允许清单内。

## 数据流

请求中的 `tenant-id` 继续由 `ArynTenantContextFilter` 写入上下文，Dubbo 调用继续传播该值。
MyBatis-Plus 只对已验证的租户表注入条件。启动校验只读取元数据，不修改数据库，也不改变
业务查询结果。

## 错误处理

启动校验失败时记录缺配表和误配表，并抛出明确异常。缺少 DataSource、无法读取元数据或
白名单为空时不静默降级；只有显式关闭校验才允许跳过。

## 验证

采用 TDD：先为表名规范化、双向差异和异常信息编写失败测试，再实现校验组件；先让配置
一致性测试暴露现有缺表，再补齐 Boot/Nacos 配置。最后运行公共 MyBatis 模块测试、后端
聚合测试、配置一致性测试和 `git diff --check`。

