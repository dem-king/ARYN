# 会员列表租户联表歧义修复设计

## 问题

会员列表 SQL 联表查询 `user_info` 与 `member_level`。两张表都含有 `tenant_id`，但 `member_level` 没有显式别名，MyBatis-Plus 租户拦截器因此向 `ON` 条件追加裸列 `tenant_id`，MySQL 报列名歧义。

## 设计

将联表声明改为 `LEFT JOIN member_level AS member_level`。显式别名会使租户拦截器生成 `member_level.tenant_id`，主表条件继续使用 `user_info.tenant_id`，不绕过租户隔离，也不修改公共拦截器。

## 验证

先增加 Mapper 契约测试，要求会员列表的租户表联接使用显式别名，并确认测试在修复前失败。修复后运行定向测试、`aryn-user-biz` 测试以及 Boot 聚合测试，并通过当前运行服务复测会员列表接口。
