-- ============================================================================
-- 货到付款实收金额与付款凭证增量
-- 背景：客户线下付款可能有折扣（如应收 1803 实付 1800），管理端「确认收款」
--       时需登记实收金额并上传付款凭证，C 端订单需同时展示应收总额与实付总额。
-- 内容：order_info 补两列：
--       actual_pay_price  实收金额（确认收款时登记，NULL=未确认收款）；
--       pay_vouchers      付款凭证快照 JSON 数组 [{materialId, materialUrl}]，
--                         URL 在确认收款时经素材服务快照，素材删除不影响回显。
-- 特性：information_schema 守卫，可重复执行；不修改、不删除存量数据。
-- 注意：本脚本只加列，不改数据；管理端确认收款改为携带实收金额与凭证上传。
-- 执行：mysql -u root -p aryn_boot < 109cod_pay_voucher_incremental.sql
-- ============================================================================

USE `aryn_boot`;

SET NAMES utf8mb4;

-- ============ order_info.actual_pay_price ============
SET @add_actual_pay_price = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `actual_pay_price` decimal(10,2) NULL COMMENT ''实收金额（货到付款确认收款时登记；NULL=未确认）'' AFTER `payment_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'actual_pay_price'
);
PREPARE stmt FROM @add_actual_pay_price; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ order_info.pay_vouchers ============
SET @add_pay_vouchers = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `pay_vouchers` json NULL COMMENT ''付款凭证快照 JSON 数组 [{materialId, materialUrl}]（确认收款时写入）'' AFTER `actual_pay_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'pay_vouchers'
);
PREPARE stmt FROM @add_pay_vouchers; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ 自检：应返回 2 ============
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info'
       AND COLUMN_NAME IN ('actual_pay_price', 'pay_vouchers')) AS added_columns;

SET FOREIGN_KEY_CHECKS = 1;
