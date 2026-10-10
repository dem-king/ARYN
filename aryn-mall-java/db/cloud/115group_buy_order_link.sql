-- ============================================================================
-- 拼团订单关联增量（Cloud 微服务模式）
-- 背景：拼团下单链路打通——C 端开团/参团后携拼团记录（recordId）进入结算页下单，
--       下单时服务端按拼团价成交，订单支付成功后按团内已付款人数判定成团。
--       order_info 需要持有拼团记录 ID，用于：
--         1) 下单成功后把订单号回填到拼团成员（支付回调按订单号推进成团）；
--         2) 订单取消/超时取消时释放拼团成员占坑（允许重新下单）。
-- 内容：order_info 新增 group_buy_record_id 列（订单关联的拼团记录 ID，普通下单为 NULL）。
-- 幂等：information_schema 判重后 ALTER，可重复执行；不修改/删除任何存量数据。
-- 与 db/boot/114group_buy_order_link.sql 内容保持一致，仅库名不同。
-- ============================================================================

USE `aryn_order`;
SET NAMES utf8mb4;

SET @add_group_buy_record_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `group_buy_record_id` varchar(64) NULL COMMENT ''拼团记录ID（拼团单关联开团/参团记录，用于成团判定与取消释放；普通订单为NULL）'' AFTER `coupon_user_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'group_buy_record_id'
);
PREPARE stmt FROM @add_group_buy_record_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：应返回 1 行且 COLUMN_NAME = group_buy_record_id
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'group_buy_record_id';
