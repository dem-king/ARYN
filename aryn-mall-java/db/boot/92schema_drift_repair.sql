-- 订单/优惠券/秒杀结构漂移修复（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：14member_management_hardening.sql 只有会员部分（user_info.total_point、
--       member_order_growth、user_tag_rel/member_benefit_level_rel 唯一键）随
--       4aryn_boot_member.sql 并入了基线，订单与优惠券部分漏并入 2aryn_boot.sql，
--       导致「实体有字段、库中无列」的结构漂移：
--         · C 端「立即支付」读取 order_info 直接抛 Unknown column 'member_discount_price'；
--         · 会员权益发券走 CouponUserMapper 显式 INSERT，缺 source_type/source_id；
--         · 秒杀下单 save(SeckillOrder) 缺 status/seckill_price/update_time。
--       25seckill_discount.sql 建表语句也从未包含这三列，属同一类漂移。
--       本脚本补回缺失列与唯一键，并登记进 build-full-sql.mjs，保证双模式一致。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 92schema_drift_repair.sql

USE `aryn_boot`;

SET NAMES utf8mb4;

-- ============ order_info：会员折扣优惠金额、下单时积分倍率 ============

SET @order_info_member_discount_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'order_info'
    AND column_name = 'member_discount_price'
);
SET @order_info_member_discount_sql := IF(@order_info_member_discount_exists = 0,
  'ALTER TABLE `order_info` ADD COLUMN `member_discount_price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT ''会员折扣优惠金额（元）'' AFTER `coupon_price`',
  'SELECT 1');
PREPARE order_info_member_discount_stmt FROM @order_info_member_discount_sql;
EXECUTE order_info_member_discount_stmt;
DEALLOCATE PREPARE order_info_member_discount_stmt;

SET @order_info_points_multiplier_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'order_info'
    AND column_name = 'points_multiplier'
);
SET @order_info_points_multiplier_sql := IF(@order_info_points_multiplier_exists = 0,
  'ALTER TABLE `order_info` ADD COLUMN `points_multiplier` decimal(10,2) NOT NULL DEFAULT 1.00 COMMENT ''下单时会员积分倍率'' AFTER `member_discount_price`',
  'SELECT 1');
PREPARE order_info_points_multiplier_stmt FROM @order_info_points_multiplier_sql;
EXECUTE order_info_points_multiplier_stmt;
DEALLOCATE PREPARE order_info_points_multiplier_stmt;

-- ============ order_item：会员折扣优惠金额 ============

SET @order_item_member_discount_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'order_item'
    AND column_name = 'member_discount_price'
);
SET @order_item_member_discount_sql := IF(@order_item_member_discount_exists = 0,
  'ALTER TABLE `order_item` ADD COLUMN `member_discount_price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT ''会员折扣优惠金额（元）'' AFTER `coupon_price`',
  'SELECT 1');
PREPARE order_item_member_discount_stmt FROM @order_item_member_discount_sql;
EXECUTE order_item_member_discount_stmt;
DEALLOCATE PREPARE order_item_member_discount_stmt;

-- ============ coupon_user：发放来源与幂等唯一键 ============
-- 存量行的 source_type/source_id 均为 NULL，MySQL 唯一索引对 NULL 不判重，可安全补建。

SET @coupon_user_source_type_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'coupon_user'
    AND column_name = 'source_type'
);
SET @coupon_user_source_type_sql := IF(@coupon_user_source_type_exists = 0,
  'ALTER TABLE `coupon_user` ADD COLUMN `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''发放来源类型'' AFTER `tenant_id`',
  'SELECT 1');
PREPARE coupon_user_source_type_stmt FROM @coupon_user_source_type_sql;
EXECUTE coupon_user_source_type_stmt;
DEALLOCATE PREPARE coupon_user_source_type_stmt;

SET @coupon_user_source_id_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'coupon_user'
    AND column_name = 'source_id'
);
SET @coupon_user_source_id_sql := IF(@coupon_user_source_id_exists = 0,
  'ALTER TABLE `coupon_user` ADD COLUMN `source_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''发放来源ID'' AFTER `source_type`',
  'SELECT 1');
PREPARE coupon_user_source_id_stmt FROM @coupon_user_source_id_sql;
EXECUTE coupon_user_source_id_stmt;
DEALLOCATE PREPARE coupon_user_source_id_stmt;

SET @coupon_user_source_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'coupon_user'
    AND index_name = 'uk_coupon_user_source'
);
SET @coupon_user_source_index_sql := IF(@coupon_user_source_index_exists = 0,
  'ALTER TABLE `coupon_user` ADD UNIQUE KEY `uk_coupon_user_source` (`tenant_id`, `user_id`, `source_type`, `source_id`)',
  'SELECT 1');
PREPARE coupon_user_source_index_stmt FROM @coupon_user_source_index_sql;
EXECUTE coupon_user_source_index_stmt;
DEALLOCATE PREPARE coupon_user_source_index_stmt;

-- ============ seckill_order：状态、秒杀单价快照、状态变更时间 ============

SET @seckill_order_status_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'seckill_order'
    AND column_name = 'status'
);
SET @seckill_order_status_sql := IF(@seckill_order_status_exists = 0,
  'ALTER TABLE `seckill_order` ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT ''状态:0未支付 1已支付 2已取消 3已超时'' AFTER `quantity`',
  'SELECT 1');
PREPARE seckill_order_status_stmt FROM @seckill_order_status_sql;
EXECUTE seckill_order_status_stmt;
DEALLOCATE PREPARE seckill_order_status_stmt;

SET @seckill_order_price_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'seckill_order'
    AND column_name = 'seckill_price'
);
SET @seckill_order_price_sql := IF(@seckill_order_price_exists = 0,
  'ALTER TABLE `seckill_order` ADD COLUMN `seckill_price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT ''秒杀单价(下单时快照)'' AFTER `status`',
  'SELECT 1');
PREPARE seckill_order_price_stmt FROM @seckill_order_price_sql;
EXECUTE seckill_order_price_stmt;
DEALLOCATE PREPARE seckill_order_price_stmt;

SET @seckill_order_update_time_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'seckill_order'
    AND column_name = 'update_time'
);
SET @seckill_order_update_time_sql := IF(@seckill_order_update_time_exists = 0,
  'ALTER TABLE `seckill_order` ADD COLUMN `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''支付/状态变更时间'' AFTER `create_time`',
  'SELECT 1');
PREPARE seckill_order_update_time_stmt FROM @seckill_order_update_time_sql;
EXECUTE seckill_order_update_time_stmt;
DEALLOCATE PREPARE seckill_order_update_time_stmt;

-- 自检：以下五项应分别为 2、1、2、3、1。
SELECT
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'order_info'
       AND column_name IN ('member_discount_price', 'points_multiplier')) AS order_info_columns,
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'order_item'
       AND column_name = 'member_discount_price') AS order_item_columns,
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'coupon_user'
       AND column_name IN ('source_type', 'source_id')) AS coupon_user_columns,
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
       AND column_name IN ('status', 'seckill_price', 'update_time')) AS seckill_order_columns,
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'coupon_user'
       AND index_name = 'uk_coupon_user_source') AS coupon_user_index;
