-- 悦航购船供活动熔断字段（Cloud 微服务模式）
-- 目标库：aryn_promotion
-- 与 boot/124promotion_activity_fuse_incremental.sql 对应，内容一致。
-- 背景：船供活动治理一期。promotion_activity 增三个熔断字段：预算金额、每人限享次数、
--       买赠赠品总量；reserve 锁定时按存量锁聚合扣减，超限整单拦截。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据；
--       新列默认 0=不限，与存量行为逐行等价。

USE `aryn_promotion`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @add_total_budget = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `promotion_activity` ADD COLUMN `total_budget` decimal(12,2) NOT NULL DEFAULT ''0.00'' COMMENT ''预算金额（元），0=不限；reserve 按锁定折扣聚合扣减，超限整单拦截'' AFTER `priority`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND COLUMN_NAME = 'total_budget'
);
PREPARE stmt FROM @add_total_budget; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_per_user_limit = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `promotion_activity` ADD COLUMN `per_user_limit` int NOT NULL DEFAULT ''0'' COMMENT ''每人限享次数，0=不限；按该用户活动锁定数计'' AFTER `total_budget`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND COLUMN_NAME = 'per_user_limit'
);
PREPARE stmt FROM @add_per_user_limit; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_gift_stock = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `promotion_activity` ADD COLUMN `gift_stock` int NOT NULL DEFAULT ''0'' COMMENT ''买赠赠品总量，0=不限；按锁定快照赠品数量聚合扣减'' AFTER `per_user_limit`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND COLUMN_NAME = 'gift_stock'
);
PREPARE stmt FROM @add_gift_stock; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
