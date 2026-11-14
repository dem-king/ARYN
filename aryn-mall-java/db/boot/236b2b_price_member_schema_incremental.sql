-- ============================================================================
-- B2B 价格与会员体系基础层 — Boot 单体模式
--
-- 目标库：aryn_boot
-- 背景（2026-11-14 需求「船供 B2B 活动与会员体系重构」P1）：
--   客群转为船供采购/海员个人/工厂餐厅采购，需要以「分层价格 + 档期经营 + 双轨会员」
--   替代零售限时玩法。本脚本落新体系的数据基础层：
--     1. price_policy / price_policy_item：协议价（按采购主体/船舶/客群标签的专属合同价）；
--     2. promo_calendar：采购档期（周度/靠港/自然月/自定义时间窗），活动可归属档期；
--     3. enterprise_level / enterprise_benefit / rel / record / growth：
--        企业会员双轨（岸上企业 buyer_org + 船舶 vessel 双挂点，按近12月采购额分档）；
--     4. enterprise_credit_account / enterprise_statement：账期授信预留表（本期只建表
--        不启用，二期做额度校验/月结对账/还款核销）；
--     5. buyer_org.level_id / vessel_info.level_id：企业等级挂点冗余列；
--     6. promotion_activity 增加档期归属与档期特供限量（calendar_id/total_quota/consumed_quota）；
--     7. promotion_snapshot.activity_type 注释扩展：8 协议价。
--
-- 口径：
--   · 新表全部含 tenant_id，登记进 hx.tenant.tables（boot application.yml 与 cloud 各 biz yml）；
--   · price_policy_item 唯一键 (tenant_id, policy_id, sku_id) 不含 del_flag：
--     保存时按唯一键 upsert 复用行（复活 del_flag=0），禁止物理删除；
--   · 企业等级门槛 condition_value = 近12个月主体已支付采购额（元）；
--   · enterprise_growth 按「订单完成」事件增量累计（scene=2 按 vessel、scene=3 按 buyer_org），
--     等级重算读 growth 表近12期求和，不跨库聚合。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + information_schema 判断 + PREPARE 动态执行；
--       可重复执行，不删除存量业务数据。
-- 关联：cloud 模式对应 252b2b_price_member_schema_incremental.sql（分 aryn_promotion /
--       aryn_user / aryn_order / aryn_vessel 四库）；菜单见 237 号脚本。
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. 协议价：价格政策主表 + SKU 明细
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `price_policy` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `policy_name` varchar(64) NOT NULL COMMENT '政策名称',
  `apply_type` char(2) NOT NULL DEFAULT '1' COMMENT '适用对象：1指定采购主体 2指定船舶 3客群标签',
  `apply_value` varchar(1000) NOT NULL COMMENT '适用对象ID集合（主体/船舶/标签ID逗号分隔）',
  `price_mode` char(2) NOT NULL DEFAULT '1' COMMENT '价格模式：1固定协议价 2折扣率',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级（同SKU多政策命中取大，同级取低价）',
  `start_time` datetime NOT NULL COMMENT '生效开始',
  `end_time` datetime NOT NULL COMMENT '生效结束',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0草稿 1生效 2停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_price_policy_status` (`tenant_id`,`status`,`apply_type`,`start_time`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='价格政策（协议价）';

CREATE TABLE IF NOT EXISTS `price_policy_item` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `policy_id` varchar(32) NOT NULL COMMENT '政策ID',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU',
  `policy_price` decimal(12,2) DEFAULT NULL COMMENT '协议固定价（price_mode=1 必填）',
  `policy_rate` decimal(8,4) DEFAULT NULL COMMENT '协议折扣率（price_mode=2 必填，如0.9000=9折）',
  `min_qty` int NOT NULL DEFAULT 1 COMMENT '协议起订量（展示提示，不参与计价校验）',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_policy_sku` (`tenant_id`,`policy_id`,`sku_id`),
  KEY `idx_policy_item_sku` (`tenant_id`,`sku_id`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='价格政策SKU明细';

-- ----------------------------------------------------------------------------
-- 2. 采购档期
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `promo_calendar` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `calendar_name` varchar(64) NOT NULL COMMENT '档期名称',
  `anchor_type` char(2) NOT NULL DEFAULT '1' COMMENT '锚点类型：1周度档期 2靠港档期 3自然月 4自定义时间窗',
  `anchor_ref` varchar(64) DEFAULT NULL COMMENT '锚点引用：靠港计划ID（anchor_type=2）等',
  `start_time` datetime NOT NULL COMMENT '档期开始',
  `end_time` datetime NOT NULL COMMENT '档期结束',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0草稿 1生效 2停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_promo_calendar_window` (`tenant_id`,`status`,`start_time`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购档期';

-- ----------------------------------------------------------------------------
-- 3. 企业会员双轨（等级 / 权益 / 关联 / 变更流水 / 采购额月度累计）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise_level` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `level_name` varchar(32) NOT NULL COMMENT '等级名称',
  `level_icon` varchar(255) DEFAULT NULL COMMENT '等级徽章图',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序号（越大等级越高）',
  `condition_value` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '升级门槛：近12个月主体采购额（元）',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0启用 1禁用',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_enterprise_level_sort` (`tenant_id`,`status`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业会员等级';

CREATE TABLE IF NOT EXISTS `enterprise_benefit` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `benefit_name` varchar(64) NOT NULL COMMENT '权益名称',
  `benefit_type` char(2) NOT NULL COMMENT '权益类型：1企业折扣率 2免邮 3协议价政策池 4开票资质 5专属客服 6返利率(预留) 7账期天数(预留)',
  `benefit_value` varchar(255) DEFAULT NULL COMMENT '权益值（折扣率0.9500/免邮1/协议价政策ID/返利率百分数/账期天数）',
  `description` varchar(255) DEFAULT NULL COMMENT '权益说明',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0启用 1禁用',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_enterprise_benefit_type` (`tenant_id`,`benefit_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业会员权益';

CREATE TABLE IF NOT EXISTS `enterprise_benefit_level_rel` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `benefit_id` varchar(32) NOT NULL COMMENT '权益ID',
  `level_id` varchar(32) NOT NULL COMMENT '企业等级ID',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_benefit_level` (`tenant_id`,`benefit_id`,`level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业权益-等级关联';

CREATE TABLE IF NOT EXISTS `enterprise_level_record` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `subject_type` char(2) NOT NULL COMMENT '主体类型：1岸上企业 2船舶',
  `subject_id` varchar(32) NOT NULL COMMENT '主体ID（buyer_org_id / vessel_id）',
  `old_level_id` varchar(32) DEFAULT NULL COMMENT '原等级',
  `new_level_id` varchar(32) DEFAULT NULL COMMENT '新等级',
  `change_reason` varchar(255) DEFAULT NULL COMMENT '变更原因',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_enterprise_level_record_subject` (`tenant_id`,`subject_type`,`subject_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业等级变更记录';

CREATE TABLE IF NOT EXISTS `enterprise_growth` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `subject_type` char(2) NOT NULL COMMENT '主体类型：1岸上企业 2船舶',
  `subject_id` varchar(32) NOT NULL COMMENT '主体ID（buyer_org_id / vessel_id）',
  `period` varchar(7) NOT NULL COMMENT '统计月份 yyyy-MM（Asia/Shanghai）',
  `consume_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '当月主体已支付采购额累计（元）',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_growth_subject_period` (`tenant_id`,`subject_type`,`subject_id`,`period`),
  KEY `idx_enterprise_growth_period` (`tenant_id`,`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业采购额月度累计（等级成长值）';

-- ----------------------------------------------------------------------------
-- 4. 账期授信预留（本期只建表不启用；二期：额度校验/月结对账/还款核销）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise_credit_account` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `subject_type` char(2) NOT NULL COMMENT '主体类型：1岸上企业 2船舶',
  `subject_id` varchar(32) NOT NULL COMMENT '主体ID',
  `credit_limit` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '授信额度（元）',
  `used_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '已用额度（未核销出账合计）',
  `frozen_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '冻结额度（在途订单占用）',
  `credit_days` int NOT NULL DEFAULT 0 COMMENT '账期天数',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0未启用 1启用 2冻结',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_credit_subject` (`tenant_id`,`subject_type`,`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业授信账户（预留）';

CREATE TABLE IF NOT EXISTS `enterprise_statement` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `statement_no` varchar(64) NOT NULL COMMENT '对账单号',
  `subject_type` char(2) NOT NULL COMMENT '主体类型：1岸上企业 2船舶',
  `subject_id` varchar(32) NOT NULL COMMENT '主体ID',
  `period` varchar(7) NOT NULL COMMENT '对账月份 yyyy-MM',
  `begin_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '期初应付',
  `charge_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '本期出账',
  `repay_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '本期回款',
  `end_amount` decimal(14,2) NOT NULL DEFAULT 0 COMMENT '期末应付',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0待确认 1已确认 2已关闭',
  `confirm_time` datetime DEFAULT NULL COMMENT '客户确认时间',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_statement_period` (`tenant_id`,`subject_type`,`subject_id`,`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业月度对账单（预留）';

-- ----------------------------------------------------------------------------
-- 5. 挂点列：buyer_org / vessel_info 企业等级
-- ----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buyer_org' AND COLUMN_NAME = 'level_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `buyer_org` ADD COLUMN `level_id` varchar(32) DEFAULT NULL COMMENT ''企业会员等级（enterprise_level.id）'' AFTER `audit_status`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vessel_info' AND COLUMN_NAME = 'level_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `vessel_info` ADD COLUMN `level_id` varchar(32) DEFAULT NULL COMMENT ''企业会员等级（enterprise_level.id）''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 6. 统一引擎增强：档期归属 + 档期特供限量
-- ----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND COLUMN_NAME = 'calendar_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `promotion_activity` ADD COLUMN `calendar_id` varchar(32) DEFAULT NULL COMMENT ''归属采购档期（promo_calendar.id，空不限）'' AFTER `purchase_scene`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND COLUMN_NAME = 'total_quota');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `promotion_activity` ADD COLUMN `total_quota` int DEFAULT NULL COMMENT ''活动级限量（件数，档期特供用；NULL不限）'' AFTER `calendar_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND COLUMN_NAME = 'consumed_quota');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `promotion_activity` ADD COLUMN `consumed_quota` int NOT NULL DEFAULT 0 COMMENT ''已消耗限量（件数，原子累计）'' AFTER `total_quota`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND INDEX_NAME = 'idx_promotion_activity_calendar');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `promotion_activity` ADD INDEX `idx_promotion_activity_calendar` (`tenant_id`,`calendar_id`,`status`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 7. 快照编码扩展：promotion_snapshot.activity_type 注释增加 8 协议价
-- ----------------------------------------------------------------------------
ALTER TABLE `promotion_snapshot`
  MODIFY COLUMN `activity_type` char(2) NOT NULL COMMENT '活动类型：1优惠券 2满减 3折扣 4阶梯价 5买赠 6首单复购 7船供整船优惠 8协议价';

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 自检（执行后人工核对）
-- ---------------------------------------------------------------------------
-- SHOW TABLES LIKE 'price_policy%';
-- SHOW TABLES LIKE 'promo_calendar';
-- SHOW TABLES LIKE 'enterprise_%';
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='buyer_org' AND COLUMN_NAME='level_id';
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vessel_info' AND COLUMN_NAME='level_id';
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_activity'
--    AND COLUMN_NAME IN ('calendar_id','total_quota','consumed_quota');
