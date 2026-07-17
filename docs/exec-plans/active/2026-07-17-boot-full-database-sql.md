# Boot 模式全量数据库 SQL 实施计划

> **For Codex:** 使用 `executing-plans` 按步骤生成并验证自包含 SQL，不修改历史分拆脚本。

**目标：** 生成一个可在空 MySQL 8 实例上一次执行的 Boot 模式全量初始化文件，同时创建 `aryn_boot` 与 `aryn_boot_job`。

**方案：** 以现有稳定 SQL 为来源机械合并，顺序为建库、Boot 基础库、会员模块、页面装修结构/发布迁移、XXL-JOB 库。`12tenant_lookup_uniqueness.sql` 不追加，因为当前 `2aryn_boot.sql` 已内置相同函数唯一索引；Cloud/Nacos SQL 不属于 Boot 数据库，不合并。

## 步骤

1. 创建合并脚本，固定来源文件与顺序，并在每段前插入清晰边界。
2. 生成 `aryn-mall-java/db/boot/aryn_boot_full.sql`，建库使用 `IF NOT EXISTS`，为所有 79 张表提供重建清理，并统一为 UTF-8（无 BOM）和 LF。
3. 静态校验两个数据库、会员字段、装修版本/模板表、装修权限、历史版本初始化和 XXL-JOB 表。
4. 校验 SQL 源文件内容完整嵌入、`FOREIGN_KEY_CHECKS` 最终恢复、无重复追加 `12tenant_lookup_uniqueness.sql`。
5. 运行装修 SQL 契约测试和 `git diff --check`；无 MySQL/Docker 时明确记录未做真实导入演练。

## 执行结果

- 已生成 `aryn-mall-java/db/boot/aryn_boot_full.sql`，包含 `aryn_boot` 与 `aryn_boot_job`。
- 静态验证覆盖 79 张唯一表、全部重建清理、会员字段、装修版本/模板、权限、历史快照和调度表。
- Promotion SQL 契约 6 个测试与 `git diff --check` 通过。
- 当前环境无 MySQL 客户端和 Docker，真实 MySQL 8 导入仍待有数据库环境时演练；本计划暂留 `active/`。
