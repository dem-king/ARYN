-- 会员管理加固（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：会员等级成长值、标签/权益关联唯一键、充值运维所需的结构加固。
--       本脚本为历史遗留的非幂等脚本，重放会因「列/索引已存在」中断；
--       现按 92schema_drift_repair.sql 的 information_schema 守卫模式重写，
--       结构与 4aryn_boot_member.sql 基线保持一致（基线已含 total_point、
--       member_order_growth 及两个唯一键，全新库执行本脚本应为空操作）。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 14member_management_hardening.sql

USE `aryn_boot`;

SET NAMES utf8mb4;

-- ============ user_info：累计获得积分 ============

SET @user_info_total_point_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_info'
    AND column_name = 'total_point'
);
SET @user_info_total_point_sql := IF(@user_info_total_point_exists = 0,
  'ALTER TABLE `user_info` ADD COLUMN `total_point` int NOT NULL DEFAULT 0 COMMENT ''累计获得积分'' AFTER `point`',
  'SELECT 1');
PREPARE user_info_total_point_stmt FROM @user_info_total_point_sql;
EXECUTE user_info_total_point_stmt;
DEALLOCATE PREPARE user_info_total_point_stmt;

-- 存量数据回填：以当前可用积分为下限，避免累计值小于可用值
UPDATE user_info SET total_point = GREATEST(COALESCE(point, 0), COALESCE(total_point, 0));

-- ============ user_tag_rel：租户内用户+标签唯一 ============

-- 先清掉可能存在的重复关联，再补唯一键（重放时唯一键已存在则跳过）
DELETE duplicate_rel FROM user_tag_rel duplicate_rel
JOIN user_tag_rel retained ON retained.tenant_id = duplicate_rel.tenant_id
 AND retained.user_id = duplicate_rel.user_id AND retained.tag_id = duplicate_rel.tag_id
 AND retained.id < duplicate_rel.id;

SET @user_tag_rel_uk_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'user_tag_rel'
    AND index_name = 'uk_user_tag_tenant'
);
SET @user_tag_rel_uk_sql := IF(@user_tag_rel_uk_exists = 0,
  'ALTER TABLE `user_tag_rel` ADD UNIQUE KEY `uk_user_tag_tenant` (`tenant_id`, `user_id`, `tag_id`)',
  'SELECT 1');
PREPARE user_tag_rel_uk_stmt FROM @user_tag_rel_uk_sql;
EXECUTE user_tag_rel_uk_stmt;
DEALLOCATE PREPARE user_tag_rel_uk_stmt;

-- ============ member_benefit_level_rel：租户内权益+等级唯一 ============

DELETE duplicate_rel FROM member_benefit_level_rel duplicate_rel
JOIN member_benefit_level_rel retained ON retained.tenant_id = duplicate_rel.tenant_id
 AND retained.benefit_id = duplicate_rel.benefit_id AND retained.level_id = duplicate_rel.level_id
 AND retained.id < duplicate_rel.id;

SET @benefit_level_rel_uk_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'member_benefit_level_rel'
    AND index_name = 'uk_benefit_level_tenant'
);
SET @benefit_level_rel_uk_sql := IF(@benefit_level_rel_uk_exists = 0,
  'ALTER TABLE `member_benefit_level_rel` ADD UNIQUE KEY `uk_benefit_level_tenant` (`tenant_id`, `benefit_id`, `level_id`)',
  'SELECT 1');
PREPARE benefit_level_rel_uk_stmt FROM @benefit_level_rel_uk_sql;
EXECUTE benefit_level_rel_uk_stmt;
DEALLOCATE PREPARE benefit_level_rel_uk_stmt;

-- ============ member_order_growth：订单完成成长值幂等表 ============

CREATE TABLE IF NOT EXISTS `member_order_growth` (
  `id` varchar(32) NOT NULL COMMENT '主键', `order_id` varchar(32) NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) DEFAULT NULL COMMENT '订单编号', `user_id` varchar(32) NOT NULL COMMENT '用户ID',
  `goods_payment_amount` decimal(10,2) NOT NULL COMMENT '实付商品金额',
  `points_awarded` int NOT NULL DEFAULT 0 COMMENT '本次发放积分', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_time` datetime NOT NULL COMMENT '创建时间', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_growth_order` (`tenant_id`, `order_id`), KEY `idx_member_growth_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员订单成长幂等记录';

-- ============ 会员折扣与积分倍率列（订单域） ============
-- 说明：order_info/order_item 的会员折扣列与 coupon_user 发放来源列也由
--       92schema_drift_repair.sql 覆盖，两处均为幂等守卫，重复执行互不影响。

SET @order_info_member_discount_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'order_info' AND column_name = 'member_discount_price'
);
SET @order_info_member_discount_sql := IF(@order_info_member_discount_exists = 0,
  'ALTER TABLE `order_info` ADD COLUMN `member_discount_price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT ''会员折扣优惠金额'' AFTER `coupon_price`',
  'SELECT 1');
PREPARE order_info_member_discount_stmt FROM @order_info_member_discount_sql;
EXECUTE order_info_member_discount_stmt;
DEALLOCATE PREPARE order_info_member_discount_stmt;

SET @order_info_points_multiplier_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'order_info' AND column_name = 'points_multiplier'
);
SET @order_info_points_multiplier_sql := IF(@order_info_points_multiplier_exists = 0,
  'ALTER TABLE `order_info` ADD COLUMN `points_multiplier` decimal(10,2) NOT NULL DEFAULT 1.00 COMMENT ''下单时会员积分倍率'' AFTER `member_discount_price`',
  'SELECT 1');
PREPARE order_info_points_multiplier_stmt FROM @order_info_points_multiplier_sql;
EXECUTE order_info_points_multiplier_stmt;
DEALLOCATE PREPARE order_info_points_multiplier_stmt;

SET @order_item_member_discount_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'order_item' AND column_name = 'member_discount_price'
);
SET @order_item_member_discount_sql := IF(@order_item_member_discount_exists = 0,
  'ALTER TABLE `order_item` ADD COLUMN `member_discount_price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT ''会员折扣优惠金额'' AFTER `coupon_price`',
  'SELECT 1');
PREPARE order_item_member_discount_stmt FROM @order_item_member_discount_sql;
EXECUTE order_item_member_discount_stmt;
DEALLOCATE PREPARE order_item_member_discount_stmt;

-- ============ 优惠券发放来源与幂等唯一键（营销域） ============

SET @coupon_user_source_type_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'coupon_user' AND column_name = 'source_type'
);
SET @coupon_user_source_type_sql := IF(@coupon_user_source_type_exists = 0,
  'ALTER TABLE `coupon_user` ADD COLUMN `source_type` varchar(32) DEFAULT NULL COMMENT ''发放来源类型'' AFTER `tenant_id`',
  'SELECT 1');
PREPARE coupon_user_source_type_stmt FROM @coupon_user_source_type_sql;
EXECUTE coupon_user_source_type_stmt;
DEALLOCATE PREPARE coupon_user_source_type_stmt;

SET @coupon_user_source_id_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'coupon_user' AND column_name = 'source_id'
);
SET @coupon_user_source_id_sql := IF(@coupon_user_source_id_exists = 0,
  'ALTER TABLE `coupon_user` ADD COLUMN `source_id` varchar(128) DEFAULT NULL COMMENT ''发放来源ID'' AFTER `source_type`',
  'SELECT 1');
PREPARE coupon_user_source_id_stmt FROM @coupon_user_source_id_sql;
EXECUTE coupon_user_source_id_stmt;
DEALLOCATE PREPARE coupon_user_source_id_stmt;

SET @coupon_user_source_uk_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'coupon_user' AND index_name = 'uk_coupon_user_source'
);
SET @coupon_user_source_uk_sql := IF(@coupon_user_source_uk_exists = 0,
  'ALTER TABLE `coupon_user` ADD UNIQUE KEY `uk_coupon_user_source` (`tenant_id`, `user_id`, `source_type`, `source_id`)',
  'SELECT 1');
PREPARE coupon_user_source_uk_stmt FROM @coupon_user_source_uk_sql;
EXECUTE coupon_user_source_uk_stmt;
DEALLOCATE PREPARE coupon_user_source_uk_stmt;

