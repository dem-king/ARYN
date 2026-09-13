-- 悦航购购物车船舶归属增量迁移（Cloud 微服务模式）
-- 目标库：aryn_order
-- 特性：可重复执行（information_schema 守卫），不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 内容：购物车行归属船舶/靠港/场景快照；重建软删除唯一生成列以支持“同用户同 SKU 在不同靠港各一行”。

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 一、归属快照列
SET @add_cart_vessel_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shopping_cart` ADD COLUMN `vessel_id` varchar(32) DEFAULT NULL COMMENT ''加购船舶ID快照'' AFTER `specs_info`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_cart' AND COLUMN_NAME = 'vessel_id'
);
PREPARE stmt FROM @add_cart_vessel_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_cart_call_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shopping_cart` ADD COLUMN `vessel_call_id` varchar(32) DEFAULT NULL COMMENT ''加购靠港计划ID快照'' AFTER `vessel_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_cart' AND COLUMN_NAME = 'vessel_call_id'
);
PREPARE stmt FROM @add_cart_call_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_cart_scene = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shopping_cart` ADD COLUMN `purchase_scene` char(2) DEFAULT NULL COMMENT ''加购购买场景快照：1个人 2船供'' AFTER `vessel_call_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_cart' AND COLUMN_NAME = 'purchase_scene'
);
PREPARE stmt FROM @add_cart_scene; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 二、重建软删除唯一生成列：active_sku_id（仅 sku_id）→ active_row_key（sku_id + 靠港）
-- 去重语义：同用户同 SKU 同靠港仅一行；不同靠港各一行；已删除行键为 NULL。
SET @drop_cart_old_uk = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `shopping_cart` DROP INDEX `uk_shopping_cart_active_sku`',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_cart' AND INDEX_NAME = 'uk_shopping_cart_active_sku'
);
PREPARE stmt FROM @drop_cart_old_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_cart_old_col = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `shopping_cart` DROP COLUMN `active_sku_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_cart' AND COLUMN_NAME = 'active_sku_id'
);
PREPARE stmt FROM @drop_cart_old_col; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_cart_row_key = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shopping_cart` ADD COLUMN `active_row_key` varchar(96) GENERATED ALWAYS AS (CASE WHEN `del_flag` = ''0'' THEN CONCAT(`sku_id`, '':'', IFNULL(`vessel_call_id`, '''')) ELSE NULL END) STORED COMMENT ''有效行唯一键（sku+靠港）''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_cart' AND COLUMN_NAME = 'active_row_key'
);
PREPARE stmt FROM @add_cart_row_key; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_cart_new_uk = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shopping_cart` ADD UNIQUE KEY `uk_shopping_cart_active_row` (`tenant_id`, `user_id`, `active_row_key`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_cart' AND INDEX_NAME = 'uk_shopping_cart_active_row'
);
PREPARE stmt FROM @add_cart_new_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
