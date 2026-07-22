# 多租户联表别名审计设计

## 背景

管理后台登录成功后，基础布局会并行加载工作人员通知列表和未读数量。`MessageRecipientMapper.xml` 同时关联 `message_recipient` 与 `message_notice`，两张表都配置为多租户表，但 `FROM`、`JOIN` 没有声明别名。MyBatis-Plus 多租户拦截器因此为两张表注入未限定表名的 `tenant_id` 条件，MySQL 抛出 `Column 'tenant_id' in on clause is ambiguous`。

全仓检查还发现订单统计和权限用户查询中存在同类联表写法。现有测试只检查了单个 `UserInfoMapper` 查询，无法阻止其他模块再次引入相同问题。

## 目标

- 修复消息通知列表和未读数量查询的租户列歧义。
- 修复当前仓库中其他已存在的同类多租户联表隐患。
- 增加全仓自动审计：多租户表参与显式 `JOIN` 时必须声明表别名。
- 保持查询字段、过滤条件和返回结果不变。

## 方案选择

采用“SQL 自别名 + 全仓审计测试”。

生产 SQL 使用与表名相同的显式自别名，例如：

```sql
FROM message_recipient AS message_recipient
INNER JOIN message_notice AS message_notice
```

这样 MyBatis-Plus 能生成带限定符的租户条件，同时现有 `message_recipient.xxx`、`message_notice.xxx` 字段引用无需修改，降低改动风险。

不采用仅修复消息 Mapper 的方案，因为它无法阻止其他模块复发；不采用覆盖所有 Mapper 的运行时集成测试，因为动态 SQL 参数构造和数据库环境成本过高，不适合作为当前问题的第一道防线。

## 影响范围

修复当前扫描出的无别名多租户联表查询：

- `MessageRecipientMapper.xml`：`selectInbox`、`selectInboxDetail`、`countUnread`
- `OrderStatisticsMapper.xml`：`getOrderTradeStatistics`
- `SysUserMapper.xml`：`selectMessageRecipients`、`countCustomerServiceStaff`

新增 `aryn-boot` 测试，原因是单体启动模块聚合全部业务模块，并已有多租户配置一致性与绕过审计测试，适合作为跨模块规则的统一验证入口。

## 审计规则

测试读取 `aryn-boot/src/main/resources/application.yml` 中的 `hx.tenant.tables`，递归扫描所有业务模块的 `src/main/resources/mapper/*.xml`。

对每个包含显式 `JOIN` 的 `<select>`：

1. 提取 `FROM` 和 `JOIN` 后的表引用。
2. 如果表属于多租户配置，则要求表名后存在显式别名，支持 `table alias` 和 `table AS alias` 两种形式。
3. 将 `INNER`、`LEFT`、`RIGHT`、`WHERE`、`ON` 等 SQL 关键字排除，避免误判为别名。
4. 失败信息输出相对文件路径、Mapper 方法 ID 和缺少别名的表名。

该规则聚焦本次根因，不解析完整动态 SQL，也不改变多租户拦截器的全局行为。

## 测试策略

按 TDD 执行：

1. 先新增全仓审计测试，并确认它因上述 6 个查询失败。
2. 给相关表声明显式自别名。
3. 重新运行审计测试，确认全部通过。
4. 运行消息模块测试。
5. 运行 `mvn test -pl aryn-boot -am` 完成后端聚合验证。

## 风险与边界

- 审计只覆盖显式 `JOIN`，不尝试处理存储过程、运行时拼接表名或旧式逗号联表。
- 自别名不改变 SQL 业务语义，但能影响多租户插件生成的限定列名，这是预期行为。
- 若未来需要支持复杂 CTE 或动态表名，应在测试中增加对应样例后再扩展解析规则。
