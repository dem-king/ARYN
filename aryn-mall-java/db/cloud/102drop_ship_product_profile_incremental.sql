-- 移除船供资料三表（Cloud 微服务模式）
--
-- 目标库：aryn_product（三表）+ nacos 库 config_info（product 服务租户白名单清理）
-- 背景：船供资料已从三端下线（2026-09-29）——系统内没有维护 IMPA/ISSA/内部编码
--       与采购单位/MOQ/步长的商品，管理端「发布商品-船供资料」Tab、编码搜索、
--       购物车 MOQ/步长服务端校验一并移除；补给单 Excel 导入改按
--       SKU 编号（goods_sku.id）精确匹配、品名+规格兜底。
-- 范围：仅 DROP 三张船供资料表 + 清理 Nacos 白名单残留，不触碰业务数据。
-- 特性：可重复执行（DROP TABLE IF EXISTS + REPLACE 幂等）；DROP 前输出行数留痕。
-- 注意：直改 config_info 后需重启 aryn-product 服务（或通过 Nacos 控制台重新发布
--       同内容）让内存缓存失效；md5 列随内容一并更新。
--
-- 执行：mysql -u root -p < 102drop_ship_product_profile_incremental.sql

SET NAMES utf8mb4;

-- ===========================================================================
-- 0. 自检：DROP 前行数留痕（预期接近 0 行；若有存量数据请先确认业务已放弃）
-- ===========================================================================
SELECT 'ship_goods_profile' AS tbl, COUNT(*) AS rows_before
FROM `aryn_product`.`ship_goods_profile`
UNION ALL
SELECT 'ship_sku_profile', COUNT(*) FROM `aryn_product`.`ship_sku_profile`
UNION ALL
SELECT 'product_code_mapping', COUNT(*) FROM `aryn_product`.`product_code_mapping`;

-- ===========================================================================
-- 1. 删除三表
-- ===========================================================================
DROP TABLE IF EXISTS `aryn_product`.`ship_goods_profile`;
DROP TABLE IF EXISTS `aryn_product`.`ship_sku_profile`;
DROP TABLE IF EXISTS `aryn_product`.`product_code_mapping`;

-- ===========================================================================
-- 2. 自检：三条 SHOW 均应返回空集
-- ===========================================================================
SHOW TABLES FROM `aryn_product` LIKE 'ship_goods_profile';
SHOW TABLES FROM `aryn_product` LIKE 'ship_sku_profile';
SHOW TABLES FROM `aryn_product` LIKE 'product_code_mapping';

-- ===========================================================================
-- 3. Nacos 白名单清理：aryn-product-biz-dev.yml 租户表白名单移除三表
--    （47/49 号脚本登记的行；REPLACE 幂等，md5 随内容更新）
--    直改后需重启 aryn-product（或控制台重新发布）生效。
--    库名按实际 Nacos 库调整（默认 nacos）。
-- ===========================================================================
USE `nacos`;

UPDATE `config_info`
SET content = REPLACE(
                REPLACE(
                  REPLACE(content,
                    '      - ship_goods_profile\n', ''),
                  '      - ship_sku_profile\n', ''),
                '      - product_code_mapping\n', ''),
    md5 = MD5(REPLACE(
              REPLACE(
                REPLACE(content,
                  '      - ship_goods_profile\n', ''),
                '      - ship_sku_profile\n', ''),
              '      - product_code_mapping\n', '')),
    gmt_modified = NOW()
WHERE data_id = 'aryn-product-biz-dev.yml'
  AND content LIKE '%ship_goods_profile%';

-- 自检：应返回 0 行
SELECT id, data_id FROM `config_info`
WHERE data_id = 'aryn-product-biz-dev.yml'
  AND (content LIKE '%ship_goods_profile%'
       OR content LIKE '%ship_sku_profile%'
       OR content LIKE '%product_code_mapping%');
