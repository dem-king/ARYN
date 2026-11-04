-- P1 专区规则化与治理（Cloud 微服务模式）
--
-- 目标库：aryn_product（goods_price_history）、aryn_promotion（clearance_rule / mall_zone_setting）
-- 与 boot/185p1_zone_governance_incremental.sql 内容一致，按微服务拆分归属。
-- 特性：可重复执行（CREATE TABLE IF NOT EXISTS），不 DROP/TRUNCATE 存量数据。
--
-- 执行：mysql -u root -p < 187p1_zone_governance_incremental.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 一、aryn_product：商品价格变更历史
-- ===========================================================================
USE `aryn_product`;

CREATE TABLE IF NOT EXISTS `goods_price_history` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NULL DEFAULT NULL COMMENT '商品SKU ID（SPU级变更可空）',
  `price_type` varchar(32) NOT NULL COMMENT '价格类型：sales/original/clearance/member_fixed/member_rate',
  `old_value` decimal(12,4) NULL DEFAULT NULL COMMENT '变更前值',
  `new_value` decimal(12,4) NULL DEFAULT NULL COMMENT '变更后值',
  `change_source` varchar(32) NOT NULL COMMENT '来源：product_save/tag_save/migration/import',
  `change_reason` varchar(255) NULL DEFAULT NULL COMMENT '变更说明',
  `operator` varchar(64) NULL DEFAULT NULL COMMENT '操作人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_price_history_spu` (`tenant_id`, `spu_id`, `create_time`),
  KEY `idx_price_history_sku` (`tenant_id`, `sku_id`, `create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品价格变更历史' ROW_FORMAT = DYNAMIC;

-- ===========================================================================
-- 二、aryn_promotion：出清分层规则与专区设置
-- ===========================================================================
USE `aryn_promotion`;

CREATE TABLE IF NOT EXISTS `clearance_rule` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `rule_name` varchar(64) NOT NULL COMMENT '规则名称',
  `hours_before_expire` int NOT NULL COMMENT '距到期小时数：剩余时间 <= 该值时命中',
  `discount_rate` decimal(5,4) NOT NULL COMMENT '在基础出清价上再打折的比率，如0.8000',
  `enabled` char(1) NOT NULL DEFAULT '1' COMMENT '启用：1是 0否',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序（仅管理端展示）',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) NULL DEFAULT NULL COMMENT '修改人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_clearance_rule_tenant` (`tenant_id`, `enabled`, `hours_before_expire`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '出清时间分层规则' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `mall_zone_setting` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `zone_type` varchar(16) NOT NULL COMMENT '专区类型：member/clearance',
  `enabled` char(1) NOT NULL DEFAULT '1' COMMENT '专区开关：1开 0关（关闭时清空系统活动 rules）',
  `default_discount_rate` decimal(5,4) NULL DEFAULT NULL COMMENT '默认折扣率：商品未配置时兜底',
  `sort_mode` varchar(32) NOT NULL DEFAULT 'default' COMMENT '排序：default/price_asc/discount_desc',
  `title` varchar(64) NULL DEFAULT NULL COMMENT '专区标题',
  `subtitle` varchar(128) NULL DEFAULT NULL COMMENT '专区副标题',
  `notice` varchar(500) NULL DEFAULT NULL COMMENT '专区公告/说明',
  `create_by` varchar(64) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) NULL DEFAULT NULL COMMENT '修改人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_zone_setting_tenant_type` (`tenant_id`, `zone_type`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会员/出清专区设置' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
