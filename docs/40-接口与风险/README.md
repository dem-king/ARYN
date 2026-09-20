# 40 接口与风险

本层用于跨端联调和高风险变更前审阅。

- [接口映射](接口映射.md)：管理端/移动端 API 与后端模块覆盖。
- [缺失模块清单](缺失模块清单.md)：已确认缺口、风险级别和处理策略。
- [多租户 SQL 硬约束](#多租户-sql-硬约束)：改 Mapper 前必读。

接口状态来自静态路径与 Controller 对照，不代表真实服务、权限和数据库联调通过。

## 多租户 SQL 硬约束

**凡参与 FROM/JOIN 的租户表（`hx.tenant.tables` 白名单内的表），必须在 SQL 中声明表别名。**

原因：MyBatis-Plus `TenantLineInnerInterceptor` 会向每条 JOIN 的 ON 子句追加租户条件，
其 `getAliasColumn()` 在表**无别名时只输出裸列名** `tenant_id`（源码注释即写着「该起别名就要起别名」）。
JOIN 作用域内只要有两张表带 `tenant_id`，MySQL 就会报：

```
ERROR 1052 (23000): Column 'tenant_id' in on clause is ambiguous
```

**反直觉点**：语句中只要出现 `INNER JOIN`，MP 的 `processJoins` 会清空 `mainTables`，
导致 **WHERE 子句不再被追加租户条件**。因此不能靠「WHERE 看起来正常」来排除租户拦截器的嫌疑，
必须直接检查 ON 子句里的 `tenant_id` 是否带表前缀。

**守门测试**：`aryn-boot/src/test/java/com/aryn/cloud/boot/tenant/TenantJoinAliasAuditTest#allTenantTablesInJoinScopesUseExplicitAliases`
会扫描全仓 Mapper 并报出 `文件#statement:表名` 形式的违规。

**踩坑记录**：2026-09-19 `ShipGoodsProfileMapper.xml` 的 `goods_spu`（FROM）与 `goods_sku`（LEFT JOIN）
未起别名，导致船供采购页报上述 1052 错误。该测试当时已存在且逻辑正确，但因
`mvn -o test -pl aryn-boot`（离线、无 `-am`）解析不到 `aryn-vessel-biz` 构件而**从未真正执行**——
守门测试必须纳入常规验证命令才有意义。
