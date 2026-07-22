# 商品品牌排序规则一致性修复设计

## 背景

`db/boot/19product_brand.sql` 与 `db/cloud/19product_brand.sql` 创建 `goods_brand` 时只声明了 `DEFAULT CHARSET=utf8mb4`，没有声明项目既有商品表统一使用的 `utf8mb4_general_ci`。在 MySQL 8 默认排序规则为 `utf8mb4_0900_ai_ci` 的环境中，`goods_brand.id`、`goods_brand.tenant_id` 与 `goods_spu.brand_id`、`goods_spu.tenant_id` 联表比较会触发 `Illegal mix of collations`。

## 目标

- 新建 `goods_brand` 时显式使用 `utf8mb4_general_ci`。
- 已存在的 `goods_brand` 表可通过重复执行迁移脚本修复排序规则。
- Boot、Cloud 和 Boot 全量初始化 SQL 保持一致。
- 自动化测试阻止后续迁移再次遗漏排序规则或存量表修复语句。

## 方案

在 Boot 与 Cloud 的 `19product_brand.sql` 中：

1. 将 `goods_brand` 建表尾部改为 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci`。
2. 在建表后执行 `ALTER TABLE goods_brand CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci`，使已存在的表也能收敛到统一排序规则。
3. 保持 `goods_spu.brand_id` 的现有增量添加逻辑不变；该字段继承 `goods_spu` 的 `utf8mb4_general_ci`。
4. 重新生成 `db/boot/aryn_boot_full.sql`，避免全量初始化与增量脚本漂移。

不在 Mapper 中添加 `COLLATE` 表达式，因为排序规则属于 schema 契约，查询层补丁会重复扩散并掩盖其他联表风险。也不升级整个数据库到 `utf8mb4_0900_ai_ci`，避免扩大到所有历史表和索引。

## 测试

- 新增 SQL 契约测试，读取 Boot、Cloud 和 Boot 全量 SQL，断言品牌表建表规则与存量表转换语句存在。
- 先运行测试确认现有脚本失败，再修改迁移脚本并确认测试通过。
- 对本地 `aryn_boot.goods_brand` 执行相同转换，核对关键列排序规则并执行截图中的商品联表查询。
- 运行 `TenantJoinAliasAuditTest`，确认消息联表修复未回退。

## 风险与回滚

`ALTER TABLE ... CONVERT` 会重建表；当前品牌表规模较小且本次环境为本地开发库，风险可控。线上执行时应根据表数据量安排维护窗口。若需要回滚，可将表转换回原排序规则，但不建议恢复不一致状态。
