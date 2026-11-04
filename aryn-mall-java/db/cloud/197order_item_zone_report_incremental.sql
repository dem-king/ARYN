-- 专区让利报表快照（Cloud 微服务模式）
--
-- 目标库：aryn_order
-- 与 boot/188order_item_zone_report_incremental.sql 内容一致。
-- 特性：可重复执行（information_schema 守卫），不 DROP/TRUNCATE 存量数据。
--
-- 执行：mysql -u root -p < 197order_item_zone_report_incremental.sql

SET NAMES utf8mb4;
USE `aryn_order`;

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
