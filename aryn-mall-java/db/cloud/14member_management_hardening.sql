-- 会员管理加固（Cloud 微服务模式）
-- 目标库：aryn_user / aryn_order / aryn_promotion（跨库脚本，逐库 USE 切换）
-- 背景：会员等级成长值、标签/权益关联唯一键、优惠券发放来源所需的结构加固。
--       本脚本为历史遗留的非幂等脚本，重放会因「列/索引已存在」中断；
--       现按 92schema_drift_repair.sql 的 information_schema 守卫模式重写，
--       结构与 4aryn_user.sql / 7aryn_order.sql / aryn_promotion.sql 基线保持一致。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p < 14member_management_hardening.sql

SET NAMES utf8mb4;

-- ============ aryn_user：累计积分、关联唯一键、成长幂等表 ============

USE `aryn_user`;

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

CREATE TABLE IF NOT EXISTS `member_order_growth` (
  id varchar(32) NOT NULL COMMENT '主键', order_id varchar(32) NOT NULL COMMENT '订单ID',
  order_no varchar(32) DEFAULT NULL COMMENT '订单编号', user_id varchar(32) NOT NULL COMMENT '用户ID',
  goods_payment_amount decimal(10,2) NOT NULL COMMENT '实付商品金额',
  points_awarded int NOT NULL DEFAULT 0 COMMENT '本次发放积分', tenant_id varchar(32) NOT NULL COMMENT '租户ID',
  create_time datetime NOT NULL COMMENT '创建时间', PRIMARY KEY (id),
  UNIQUE KEY uk_member_growth_order (tenant_id, order_id), KEY idx_member_growth_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员订单成长幂等记录';

-- ============ aryn_order：会员折扣与积分倍率列 ============

USE `aryn_order`;

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

-- ============ aryn_promotion：优惠券发放来源与幂等唯一键 ============

USE `aryn_promotion`;

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
