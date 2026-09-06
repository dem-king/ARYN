# 数据库初始化指南

新环境（空 MySQL 8.0 实例）按以下步骤执行即可完成初始化。所有脚本均自带 `USE`，可直接整文件灌入；同名编号（如 `19*`、`20*`、`25*`）按文件名字母序执行。

## Boot 单体模式

方式一（推荐）：执行全量脚本

```bash
mysql -u root -p < db/boot/aryn_boot_full.sql
```

方式二：按文件名顺序执行 `db/boot/` 下脚本（`1schema.sql` → `2aryn_boot.sql` → `3aryn_boot_job.sql` → `4aryn_boot_member.sql` → `10*` ~ `27*`），但**必须跳过 `12tenant_lookup_uniqueness.sql`、`13distribution_financial_hardening.sql`、`14member_management_hardening.sql`、`16order_checkout_hardening.sql`、`17order_appraise_hardening.sql`**：这五个脚本是无 `information_schema` 守卫的裸 ALTER，且变更已并入 `2aryn_boot.sql` 基线，空库重放会报 `Duplicate key/column` 中止；它们仅用于存量库升级。其余脚本幂等可重复执行（`25delivery_menu_fix.sql` 会重写相同菜单，新库可跳过）。

维护约定：

- `aryn_boot_full.sql` 由 `node db/boot/build-full-sql.mjs` 合并生成；**新增或修改 `db/boot/` 增量脚本后必须重新生成**，并用 `node db/boot/verify-full-sql.mjs` 做静态校验（建表数量、DROP 覆盖、关键权限点）。
- `12`、`13`、`14`、`16`、`17` 号增量（租户唯一索引、分销资金加固、会员管理加固、订单下单加固、评价加固）的变更已包含在基础库 dump 中，不参与全量合并（校验脚本会拦截重复合并）；这些脚本无幂等守卫，仅用于存量库升级，空库初始化必须跳过。

## Cloud 微服务模式

1. 建库：`db/cloud/1schema.sql`（创建 upms/nacos/pay/job/user/message/order/product/promotion/gen 全部库）
2. 各库底座 dump（任意顺序，脚本自带 `USE`）：
   `2aryn_upms.sql`、`3aryn_nacos.sql`、`4aryn_user.sql`、`4aryn_user_menu.sql`、`6aryn_pay.sql`、`7aryn_order.sql`、`8aryn_product.sql`、`9aryn_promotion.sql`、`99aryn_gen.sql`、`999aryn_job.sql`
   可选辅助（均为 nacos `config_info` 更新，按需执行）：`4gateway_local_file_ignore.sql`（网关白名单追加 `/upms/file/local/**`，存储类型用本机存储时必需）、`5idea_host_config.sql`（IDEA 本地启动时把 nacos 配置中的 Docker 主机名替换为宿主机地址）。
3. 按文件名顺序执行增量：`10*` ~ `28*`。
   其中 `27delivery_fulfillment_incremental.sql`、`28promotion_tenant_config_incremental.sql` 会同步更新 `aryn_nacos.config_info`（租户表清单、XXL-JOB 端口），已做幂等处理。

## 通用约定

- **幂等策略**：菜单/授权类脚本用「先删后插」（`25delivery_menu_fix.sql`）或 `INSERT IGNORE`（`25/26/27` 号脚本）；加列类用 `information_schema` 条件 ALTER，均可重复执行。
- **默认授权对象**：配送等模块菜单默认授予超级管理员（`role_id='1'`）和默认租户（`tenant_id='1590229800633634816'`）。其他租户需在「租户菜单」中补授权后，角色才能在"分配菜单"树里看到这些菜单。
- **`25delivery_menu_fix.sql` 为存量库修复脚本**，新库初始化不需要（`25delivery_module.sql` + `27` 号增量已覆盖）。
- **警告**：`aryn_boot_full.sql`、`25delivery_module.sql`、`25seckill_discount.sql` 含 `DROP TABLE`，仅用于空库初始化，严禁对存量库执行；存量库升级一律使用对应的增量/修复脚本。
- 修改菜单/权限种子时，保持 boot 与 cloud 两份脚本及 `25delivery_module.sql`、`25delivery_menu_fix.sql`、`27delivery_fulfillment_incremental.sql` 三处菜单清单一致（可用 `rg -o "^\('2100000000000000[0-9]{3}'" <file> | sort -u` 比对集合）。
