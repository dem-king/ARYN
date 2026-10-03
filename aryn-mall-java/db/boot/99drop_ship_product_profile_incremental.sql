-- 移除船供资料三表（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：船供资料已从三端下线（2026-09-29）——系统内没有维护 IMPA/ISSA/内部编码
--       与采购单位/MOQ/步长的商品，管理端「发布商品-船供资料」Tab、编码搜索、
--       购物车 MOQ/步长服务端校验一并移除；补给单 Excel 导入改按
--       SKU 编号（goods_sku.id）精确匹配、品名+规格兜底。
-- 范围：仅 DROP 本脚本列出的三张船供资料表，不触碰 goods_spu/goods_sku 等业务数据。
-- 特性：可重复执行（DROP TABLE IF EXISTS）；DROP 前先输出行数留痕。
-- 配套：代码侧同分支移除全部读写面；db/boot/build-full-sql.mjs 已不再创建这三张表。
--
-- 执行：mysql -u root -p aryn_boot < 99drop_ship_product_profile_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ===========================================================================
-- 0. 自检：DROP 前行数留痕（预期接近 0 行；若有存量数据请先确认业务已放弃）
-- ===========================================================================
SELECT 'ship_goods_profile' AS tbl, COUNT(*) AS rows_before FROM `ship_goods_profile`
UNION ALL
SELECT 'ship_sku_profile', COUNT(*) FROM `ship_sku_profile`
UNION ALL
SELECT 'product_code_mapping', COUNT(*) FROM `product_code_mapping`;

-- ===========================================================================
-- 1. 删除三表
-- ===========================================================================
DROP TABLE IF EXISTS `ship_goods_profile`;
DROP TABLE IF EXISTS `ship_sku_profile`;
DROP TABLE IF EXISTS `product_code_mapping`;

-- ===========================================================================
-- 2. 自检：三条 SHOW 均应返回空集
-- ===========================================================================
SHOW TABLES LIKE 'ship_goods_profile';
SHOW TABLES LIKE 'ship_sku_profile';
SHOW TABLES LIKE 'product_code_mapping';
