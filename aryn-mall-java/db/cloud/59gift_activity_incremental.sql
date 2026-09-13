-- 悦航购买赠活动增量迁移（Cloud 微服务模式）
-- 目标库：aryn_order
-- 特性：可重复执行（information_schema 守卫），不执行 DROP/TRUNCATE。
-- 内容：订单明细赠品标记（赠品单独成行、0 元、不混入付费数量）。

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @add_item_gift_flag = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `gift_flag` char(2) NOT NULL DEFAULT ''0'' COMMENT ''赠品标记：0普通 1买赠赠品（0元单独成行）'' AFTER `promo_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'gift_flag'
);
PREPARE stmt FROM @add_item_gift_flag; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
