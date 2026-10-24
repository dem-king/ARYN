-- 积分兑换（P1）：积分兑优惠券（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：竞品共识是把积分包装成「兑券/兑权益」而非第二货币（小象积分商城、
--       叮咚/朴朴「兑券再抵现」、盒马兑权益），兑出的券可定向品类、设门槛，
--       补贴比无条件抵现更可控。本次新增积分兑换两张表：
--   1. points_exchange 兑换项（积分价/券模板/每人限兑/上下架）；
--   2. points_exchange_record 兑换记录（一次兑换=一条记录=扣一次积分+发一张券，
--      记录ID 是扣积分幂等键 POINTS_EXCHANGE:{记录ID} 与发券来源单号）。
-- 特性：可重复执行；仅新增表，不含种子（兑换项由运营在管理端配置）。
-- 依赖：4aryn_boot_member.sql（points_record/user_info）、160points_global_config_incremental.sql。
-- 关联：cloud 模式对应增量 161points_exchange_incremental.sql，语义一致。
--
-- 执行：mysql -u root -p aryn_boot < 161points_exchange_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 1. 积分兑换项
-- ===========================================================================
CREATE TABLE IF NOT EXISTS `points_exchange` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `title` varchar(128) NOT NULL COMMENT '兑换项标题（如「5 元无门槛券」）',
  `icon_url` varchar(512) NULL DEFAULT NULL COMMENT '图标URL（可选）',
  `points_cost` int NOT NULL COMMENT '所需积分',
  `coupon_id` varchar(32) NOT NULL COMMENT '优惠券模板ID（coupon_info.id）',
  `per_user_limit` int NOT NULL DEFAULT 0 COMMENT '每人限兑次数，0=不限',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态：0-启用；1-下架',
  `sort_order` int NULL DEFAULT NULL COMMENT '排序号（小在前）',
  `create_by` varchar(64) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) NULL DEFAULT NULL COMMENT '修改人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id` varchar(32) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_points_exchange_tenant` (`tenant_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='积分兑换项';

-- ===========================================================================
-- 2. 积分兑换记录
-- ===========================================================================
CREATE TABLE IF NOT EXISTS `points_exchange_record` (
  `id` varchar(32) NOT NULL COMMENT '主键（扣积分幂等键与发券来源单号）',
  `user_id` varchar(32) NOT NULL COMMENT '用户ID',
  `exchange_id` varchar(32) NOT NULL COMMENT '兑换项ID',
  `exchange_title` varchar(128) NULL DEFAULT NULL COMMENT '兑换项标题快照',
  `points_cost` int NOT NULL COMMENT '消耗积分快照',
  `coupon_id` varchar(32) NOT NULL COMMENT '优惠券模板ID快照',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `tenant_id` varchar(32) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_points_exchange_record_user` (`tenant_id`, `user_id`, `exchange_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='积分兑换记录';

-- ===========================================================================
-- 3. 自检：两张新表应存在
-- ===========================================================================
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('points_exchange', 'points_exchange_record');

SET FOREIGN_KEY_CHECKS = 1;
