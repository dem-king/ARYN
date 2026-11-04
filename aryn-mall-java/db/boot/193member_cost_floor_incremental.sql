-- 会员价按成本自动兜底（Boot 单体模式）
--
-- 背景：会员价下限 member_price_floor 目前只能逐商品手填；批量打标无法按不同成本填统一值。
--       本脚本给专区设置增加成本兜底倍率：商品未显式配置下限时，
--       下限 = 成本价 × 倍率（如 1.05），避免会员价击穿成本。
-- 优先级：商品显式 member_price_floor > 成本兜底倍率 > 默认 0.01。
--
-- 执行：mysql -u root -p aryn_boot < 193member_cost_floor_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

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
