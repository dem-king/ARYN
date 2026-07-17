# Boot 模式全量数据库 SQL

## 目标

为新环境提供单文件数据库初始化入口，一次创建并初始化 `aryn_boot` 商城库和 `aryn_boot_job` 调度库。

## 合并范围

- `db/boot/1schema.sql`
- `db/boot/2aryn_boot.sql`
- `db/boot/4aryn_boot_member.sql`
- `db/boot/10page_design_alter.sql`
- `db/boot/11page_design_publish.sql`
- `db/boot/3aryn_boot_job.sql`

不包含 Cloud/Nacos 数据，不重复追加已被基础 SQL 吸收的 `12tenant_lookup_uniqueness.sql`。

## 使用边界

生成的 `db/boot/aryn_boot_full.sql` 面向空库初始化，也允许在开发环境重复执行。脚本包含全表 `DROP TABLE IF EXISTS` 和种子数据，会清空同名库中的全部现有业务数据，不得在已有业务数据的生产库上执行。已有旧版 `aryn_boot` 应继续使用增量迁移。

## 生成与校验

```bash
cd aryn-mall-java
node db/boot/build-full-sql.mjs
node db/boot/verify-full-sql.mjs
```

生成器统一输出 UTF-8（无 BOM）和 LF。验证器检查两个数据库、79 张表及对应重建语句、会员字段、装修版本/模板、装修权限、历史发布快照和 XXL-JOB 表。

## 初始化

确认目标实例中的 `aryn_boot` 和 `aryn_boot_job` 不含需要保留的数据后执行：

```bash
mysql -uroot -p < db/boot/aryn_boot_full.sql
```

当前工作环境没有 MySQL 客户端或 Docker，因此只完成静态完整性和仓库契约验证，尚未做真实 MySQL 导入演练。
