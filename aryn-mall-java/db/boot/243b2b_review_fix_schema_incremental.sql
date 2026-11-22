-- ============================================================================
-- B2B 活动与会员体系审查修复 — 结构增量（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-15 代码审查修复，配套 236 号基础层脚本）：
--   1. promotion_lock 增 refunded_quota_units：档期特供退款按锁回补限量的幂等闸门
--      （CalendarSpecialRefundHandler 原子累计，MQ 重复投递不重复回补）；
--   2. enterprise_growth_ledger：企业成长值事件幂等台账（完成累计按 orderId、
--      退款回扣按 refundNo 唯一），修复 MQ 重复投递重复累计问题；
--   3. enterprise_statement_item：对账单明细表（方案 §6 账期预留，236 漏建，
--      本期只建表不启用）；
--   4. 注释口径修正：price_policy_item.min_qty 参与计价校验（起订量门槛）；
--      promotion_snapshot.activity_type 补 12 档期特供。
--
-- 口径：新表含 tenant_id，登记进 hx.tenant.tables（boot application.yml 已同步；
--       cloud 见 259 号脚本 Nacos 登记）。
-- 幂等：CREATE TABLE IF NOT EXISTS + information_schema 判断 + PREPARE 动态执行；
--       可重复执行，不删除存量业务数据。
-- 关联：cloud 模式对应 259b2b_review_fix_schema_incremental.sql。
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. promotion_lock：档期特供退款回补幂等列
-- ----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_lock' AND COLUMN_NAME = 'refunded_quota_units');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `promotion_lock` ADD COLUMN `refunded_quota_units` int NOT NULL DEFAULT 0 COMMENT ''档期特供已按退款回补的限量件数（原子累计防重；其余活动恒0）'' AFTER `release_reason`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. 企业成长值事件幂等台账
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise_growth_ledger` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `biz_type` char(2) NOT NULL COMMENT '业务类型：1订单完成累计 2退款回扣',
  `biz_no` varchar(64) NOT NULL COMMENT '业务号：完成=orderId，退款=refundNo（幂等唯一键）',
  `subject_type` char(2) NOT NULL COMMENT '主体类型：1岸上企业 2船舶',
  `subject_id` varchar(32) NOT NULL COMMENT '主体ID（buyer_org_id / vessel_id）',
  `period` varchar(7) NOT NULL COMMENT '归属月份 yyyy-MM（按订单完成时间）',
  `amount` decimal(14,2) NOT NULL COMMENT '本次变动金额（元，恒正；方向由 biz_type 决定）',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_growth_ledger_biz` (`tenant_id`,`biz_type`,`biz_no`),
  KEY `idx_growth_ledger_subject` (`tenant_id`,`subject_type`,`subject_id`,`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业成长值事件幂等台账';

-- ----------------------------------------------------------------------------
-- 3. 对账单明细（账期预留，只建表不启用）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise_statement_item` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `statement_id` varchar(32) NOT NULL COMMENT '对账单ID（enterprise_statement.id）',
  `order_id` varchar(32) DEFAULT NULL COMMENT '关联订单ID（出账/退款明细）',
  `order_no` varchar(64) DEFAULT NULL COMMENT '关联订单号',
  `biz_type` char(2) NOT NULL COMMENT '明细类型：1订单出账 2回款核销 3调整',
  `amount` decimal(14,2) NOT NULL COMMENT '明细金额（元，恒正；方向由 biz_type 决定）',
  `biz_time` datetime DEFAULT NULL COMMENT '业务发生时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_statement_item_statement` (`tenant_id`,`statement_id`),
  KEY `idx_statement_item_order` (`tenant_id`,`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业对账单明细（账期预留）';

-- ----------------------------------------------------------------------------
-- 4. 注释口径修正（MODIFY 幂等：重复执行结果一致）
-- ----------------------------------------------------------------------------
ALTER TABLE `price_policy_item`
  MODIFY COLUMN `min_qty` int NOT NULL DEFAULT 1 COMMENT '协议起订量（件）：购买件数未达时协议价不生效，C端展示「N件起」';

ALTER TABLE `promotion_snapshot`
  MODIFY COLUMN `activity_type` char(2) NOT NULL COMMENT '活动类型：1优惠券 2满减 3折扣 4阶梯价 5买赠 6首单复购 7船供整船优惠 8协议价 12档期特供';

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 自检（执行后人工核对）
-- ---------------------------------------------------------------------------
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_lock' AND COLUMN_NAME='refunded_quota_units';
-- SHOW TABLES LIKE 'enterprise_growth_ledger';
-- SHOW TABLES LIKE 'enterprise_statement_item';