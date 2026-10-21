-- 商品属性标签（Cloud 微服务模式）
--
-- 目标库：aryn_product
-- 背景：商详首屏需要运营可配置的属性卡条（系列/储存条件/地标品种/榜单等）。
-- 结构：goods_spu.attr_tags JSON 数组；元素字段见 GoodsAttrTag：
--       {key,label,value,icon,tone,linkType,popupTitle,popupContent,linkUrl}
-- 特性：可重复执行（information_schema 守卫），不触碰存量数据。
-- 配套：aryn-product-api GoodsSpu.attrTags / GoodsAttrTagListTypeHandler。
--
-- 执行：mysql -u root -p aryn_product < 142goods_attr_tags_incremental.sql

USE `aryn_product`;
SET NAMES utf8mb4;

SET @attr_tags_column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'goods_spu'
    AND COLUMN_NAME = 'attr_tags'
);

SET @attr_tags_ddl := IF(
  @attr_tags_column_exists = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `attr_tags` JSON NULL COMMENT ''商品属性标签（JSON 数组，商详首屏属性卡）'' AFTER `cost_price`',
  'SELECT 1'
);

PREPARE attr_tags_stmt FROM @attr_tags_ddl;
EXECUTE attr_tags_stmt;
DEALLOCATE PREPARE attr_tags_stmt;

SET FOREIGN_KEY_CHECKS = 1;
