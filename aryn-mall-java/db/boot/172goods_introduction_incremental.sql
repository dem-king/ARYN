-- 商品卖点（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：商详首屏在属性卡下方需要一段运营可配的卖点文案，支持逐商品配色（小象超市同款）。
-- 结构：goods_spu.introduction TEXT，存白名单内的 HTML 片段（p/br/span/strong/em/u/s，
--       span 仅保留 color/font-weight/font-size/text-align 四个样式），
--       清洗入口：aryn-product-biz GoodsIntroductionSanitizer。
-- 特性：可重复执行（information_schema 守卫），不触碰存量数据。
--
-- 执行：mysql -u root -p aryn_boot < 172goods_introduction_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

SET @introduction_column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'goods_spu'
    AND COLUMN_NAME = 'introduction'
);

SET @introduction_ddl := IF(
  @introduction_column_exists = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `introduction` TEXT NULL COMMENT ''商品卖点（白名单HTML片段，商详首屏）'' AFTER `sub_title`',
  'SELECT 1'
);

PREPARE introduction_stmt FROM @introduction_ddl;
EXECUTE introduction_stmt;
DEALLOCATE PREPARE introduction_stmt;

SET FOREIGN_KEY_CHECKS = 1;
