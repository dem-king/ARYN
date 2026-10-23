-- 悦航购折扣库存占用补齐订单明细维度（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：退款成功事件按「订单 + 明细」逐条发送，一单多件时只退一件。
--       P3-01：占用明细必须记录 order_item_id，释放时才能精确回补对应 SKU，
--       否则部分退款会把整单的折扣库存都还回去（导致超卖）。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据；
--       存量行 order_item_id 为 NULL，按整单释放（与改造前行为一致）。
-- 关联：cloud 模式同名增量 154discount_stock_lock_order_item.sql 内容一致（目标库 aryn_promotion）。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @add_order_item = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `discount_stock_lock` ADD COLUMN `order_item_id` varchar(64) DEFAULT NULL COMMENT ''订单明细ID；退款按明细精确回补'' AFTER `order_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount_stock_lock' AND COLUMN_NAME = 'order_item_id'
);
PREPARE stmt FROM @add_order_item; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_item_idx = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `discount_stock_lock` ADD KEY `idx_order_item` (`order_id`, `order_item_id`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount_stock_lock' AND INDEX_NAME = 'idx_order_item'
);
PREPARE stmt2 FROM @add_order_item_idx; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

SET FOREIGN_KEY_CHECKS = 1;
