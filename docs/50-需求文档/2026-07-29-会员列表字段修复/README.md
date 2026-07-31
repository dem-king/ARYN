# 会员列表字段修复

## 问题

存量 Cloud 开发库 `aryn_user.user_info` 已有积分、余额和累计消费字段，但遗漏 `total_point`。切换到“悦航购”租户进入会员列表时，`UserInfoMapper.selectAdminPage` 查询该字段导致 SQL 错误。

## 范围

- 目标：修复 Boot/Cloud 存量库的会员表结构，恢复所有租户的会员列表和累计积分能力。
- 非目标：不改动管理端页面、会员接口、权限或单个租户的业务数据。
- 影响端：后端数据库部署脚本；表结构是全库共享的，数据仍由 `tenant_id` 隔离。
- 兼容策略：新库继续由初始化 SQL 建列，存量库执行独立幂等迁移；无接口与实体契约变更。

## 修复与验收

- Boot/Cloud 新增可重复执行的 `25member_total_point_repair.sql`，仅在字段不存在时补列。
- 首次补列时以当前 `point` 初始化 `total_point`；字段已存在时不重写历史累计积分。
- 存量 Cloud 环境执行 `db/cloud/25member_total_point_repair.sql`，Boot 环境执行对应 Boot 脚本。
- 验收包括：迁移二次执行成功、表结构包含 `total_point`、悦航购租户会员查询可正常返回。

## 实际验证

- Cloud 修复迁移在当前 MySQL 8 开发库连续执行两次成功。
- `aryn_user.user_info.total_point` 为 `int NOT NULL DEFAULT 0`；悦航购租户的会员列表同结构 JOIN 查询返回 2 条记录。
- `mvn -pl aryn-user/aryn-user-biz -am -Dtest=MemberSchemaMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过，15 个 Reactor 模块成功，契约测试 1/1 通过。
- `git diff --check` 通过。
