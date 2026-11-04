-- 专区让利报表快照（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：订单明细只存成交价，活动类型/活动ID 是非表字段，专区让利无法事后统计。
--       本脚本为明细补两个快照列：zone_source（member/clearance）与 activity_base_price
--       （活动前单价），报表按「活动前单价 - 成交单价 + 会员折扣」计算让利。
-- 特性：可重复执行（information_schema 守卫），不 DROP/TRUNCATE 存量数据。
--
-- 执行：mysql -u root -p aryn_boot < 192order_item_zone_report_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

SET @zone_source := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'zone_source'
);
SET @ddl := IF(@zone_source = 0,
  'ALTER TABLE `order_item` ADD COLUMN `zone_source` varchar(16) NULL DEFAULT NULL COMMENT ''专区来源：member/clearance；空=非专区'' AFTER `member_discount_price`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @base_price := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'activity_base_price'
);
SET @ddl := IF(@base_price = 0,
  'ALTER TABLE `order_item` ADD COLUMN `activity_base_price` decimal(10,2) NULL DEFAULT NULL COMMENT ''专区活动前单价快照（让利统计用）'' AFTER `zone_source`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_zone := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND INDEX_NAME = 'idx_order_item_zone'
);
SET @ddl := IF(@idx_zone = 0,
  'ALTER TABLE `order_item` ADD INDEX `idx_order_item_zone` (`tenant_id`, `zone_source`, `order_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
