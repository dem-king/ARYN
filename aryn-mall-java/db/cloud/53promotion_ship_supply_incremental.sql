-- 悦航购船供营销二期增量迁移（Cloud 微服务模式）
-- 目标库：营销库 aryn_promotion；订单列变更库 aryn_order
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 内容：统一活动表（船供阶梯价/整船优惠）、活动锁定表、订单与明细的整船优惠分摊列。

USE `aryn_promotion`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 统一营销活动（二期：4 阶梯价 / 7 整船优惠；预留下列类型位）
CREATE TABLE IF NOT EXISTS `promotion_activity` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `activity_name` varchar(128) NOT NULL COMMENT '活动名称',
  `activity_type` char(2) NOT NULL COMMENT '活动类型：4阶梯价 7船供整船优惠（预留2满减 3折扣 5买赠 6首单复购）',
  `scope_type` char(2) NOT NULL DEFAULT '1' COMMENT '范围类型：1全场 2指定SKU 4指定购买场景 5指定船舶 6指定靠港计划 7指定港口',
  `scope_value` varchar(1000) DEFAULT NULL COMMENT '范围值（SKU/船舶/靠港ID逗号分隔或场景/港口编码）',
  `purchase_scene` char(2) DEFAULT NULL COMMENT '限定购买场景：1个人 2船供（空不限）',
  `rules` text DEFAULT NULL COMMENT '规则快照（受控JSON：阶梯价[{minQty,unitPrice}]；整船优惠[{minAmount,discountAmount}]）',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级（大者先）',
  `stackable` char(2) NOT NULL DEFAULT '0' COMMENT '是否可与同类叠加：0否 1是（同类取最优时忽略）',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1草稿 2已发布 3已暂停 4已结束',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_promotion_activity_status` (`tenant_id`,`activity_type`,`status`,`start_time`,`end_time`),
  KEY `idx_promotion_activity_scope` (`tenant_id`,`scope_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一营销活动';

-- 活动锁定（preview→reserve→confirm→release；按订单+活动幂等）
CREATE TABLE IF NOT EXISTS `promotion_lock` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `order_id` varchar(32) NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '订单号冗余',
  `activity_id` varchar(32) NOT NULL COMMENT '活动ID',
  `activity_type` char(2) NOT NULL COMMENT '活动类型快照',
  `activity_name` varchar(128) DEFAULT NULL COMMENT '活动名称快照',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '锁定优惠金额',
  `rule_snapshot` text DEFAULT NULL COMMENT '规则快照（受控JSON）',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1已锁定 2已确认 3已释放',
  `release_reason` varchar(64) DEFAULT NULL COMMENT '释放原因：CANCEL/TIMEOUT/REFUND',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_promotion_lock_order_activity` (`tenant_id`,`order_id`,`activity_id`),
  KEY `idx_promotion_lock_order` (`tenant_id`,`order_id`),
  KEY `idx_promotion_lock_activity` (`tenant_id`,`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='营销活动锁定';

-- order_info 整船优惠汇总列（aryn_order 库）

USE `aryn_order`;

SET @add_order_info_promo = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `promo_price` decimal(10,2) DEFAULT NULL COMMENT ''营销整单优惠金额（阶梯价计入明细单价，整船优惠在此汇总）'' AFTER `coupon_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'promo_price'
);
PREPARE stmt FROM @add_order_info_promo; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- order_item 整船优惠分摊列
SET @add_order_item_promo = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `promo_price` decimal(10,2) DEFAULT 0.00 COMMENT ''营销分摊优惠金额'' AFTER `coupon_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'promo_price'
);
PREPARE stmt FROM @add_order_item_promo; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
