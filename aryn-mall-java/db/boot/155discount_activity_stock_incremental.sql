-- 悦航购全场型折扣活动级限量（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：D2 拍板「全场型折扣（scope=1）也要限量」。全场活动没有 discount_goods 行，
--       额度只能挂在活动上：下单按实际生效的活动扣减，取消/退款按占用明细回补。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据；
--       存量活动 stock 保持 NULL、sold_count 为 0，等于不限量，行为与改造前一致。
-- 关联：cloud 模式同名增量内容一致（目标库 aryn_promotion）。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @add_act_stock = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `discount_activity` ADD COLUMN `stock` int NULL DEFAULT NULL COMMENT ''活动级限量（仅全场型 scope=1 生效），NULL=不限量'' AFTER `scope`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'stock'
);
PREPARE stmt FROM @add_act_stock; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_act_sold = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `discount_activity` ADD COLUMN `sold_count` int NOT NULL DEFAULT ''0'' COMMENT ''活动级已占用数量；stock 为 NULL 时恒为 0'' AFTER `stock`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount_activity' AND COLUMN_NAME = 'sold_count'
);
PREPARE stmt2 FROM @add_act_sold; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

SET FOREIGN_KEY_CHECKS = 1;
