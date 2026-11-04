-- 会员价按成本自动兜底（Cloud 微服务模式）
--
-- 目标库：aryn_promotion
-- 与 boot/193member_cost_floor_incremental.sql 内容一致。
--
-- 执行：mysql -u root -p < 198member_cost_floor_incremental.sql

SET NAMES utf8mb4;
USE `aryn_promotion`;

SET @cost_floor_multiplier := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_zone_setting'
    AND COLUMN_NAME = 'cost_floor_multiplier'
);
SET @ddl := IF(@cost_floor_multiplier = 0,
  'ALTER TABLE `mall_zone_setting` ADD COLUMN `cost_floor_multiplier` decimal(5,2) NULL DEFAULT NULL COMMENT ''会员价成本兜底倍率：下限=成本×倍率，空=不启用'' AFTER `default_discount_rate`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
