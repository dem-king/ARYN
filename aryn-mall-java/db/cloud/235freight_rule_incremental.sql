-- ============================================================================
-- 订单级运费规则（满额包邮 / 基础运费）— Cloud 微服务模式
--
-- 目标库：aryn_order（规则表与订单列）、aryn_product（商品运费类型注释）、
--        aryn_upms（字典值与菜单权限）
-- 背景（2026-11-12 需求「满额包邮」）：与 boot 脚本 224freight_rule_incremental.sql 同口径。
--   现状运费只有商品级两态（goods_spu.freight_type 0 包邮 / 1 固定运费，且按件线性叠加），
--   订单级只有会员权益「免运费」一个开关，无法表达「满 X 元免运费、不满收基础运费」。
--
-- 口径（与后端 FreightComputeService 一致）：
--   门槛基数 = 仅 freight_type='2' 明细的成交金额合计（活动价后、券与积分前，不含运费）；
--   freight_type='0' 商品永远包邮，'1' 商品独立计费且不参与门槛，均不改变存量行为；
--   配送方式 2 上门自提、4 公司配送（到船）不进入运费计算。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + 列存在性判断 + INSERT IGNORE，可重复执行。
-- 依赖：Nacos 侧 freight_rule 需登记进 order-biz 的 hx.tenant.tables，
--       见 236freight_rule_nacos.sql；boot 侧白名单写在 application.yml（代码文件）。
--
-- 执行：mysql -u root -p < 235freight_rule_incremental.sql
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @tenant_id = _utf8mb4'1590229800633634816' COLLATE utf8mb4_general_ci;

-- ============================================================================
-- 一、aryn_order：运费规则表
-- ============================================================================
USE `aryn_order`;

CREATE TABLE IF NOT EXISTS `freight_rule` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `rule_name` varchar(64) NOT NULL COMMENT '规则名称',
  `biz_scene` char(2) NOT NULL DEFAULT '0' COMMENT '购买场景：0-全部；1-海员个人；2-船供采购；3-企业采购',
  `delivery_way` char(2) NOT NULL DEFAULT '1' COMMENT '配送方式：1-普通快递；3-公司配送（岸上）',
  `province_codes` varchar(255) NULL COMMENT '适用省份编码，英文逗号分隔；空=全国（偏远地区加价用）',
  `threshold_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '包邮门槛（元）；0=不设门槛',
  `base_freight` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '未达门槛时收取的基础运费（元）',
  `max_freight` decimal(10,2) NULL COMMENT '本单运费封顶（元）；NULL=不封顶',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级，数值大者优先（指定省份的规则应高于全国规则）',
  `enabled` char(2) NOT NULL DEFAULT '1' COMMENT '启用状态：1启用 0禁用',
  `remark` varchar(255) NULL COMMENT '运营备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `update_by` varchar(60) NULL COMMENT '修改人',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_time` datetime NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_freight_rule_name` (`tenant_id`, `rule_name`, `del_flag`),
  KEY `idx_freight_rule_match` (`tenant_id`, `enabled`, `delivery_way`, `biz_scene`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单级运费规则（满额包邮）';

-- 订单运费快照列（幂等：逐列判断存在性）
SET @add_freight_base = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `freight_base_price` decimal(10,2) NULL COMMENT ''运费原价（减免前，元）'' AFTER `freight_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'freight_base_price'
);
PREPARE stmt FROM @add_freight_base; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_freight_discount = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `freight_discount_price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT ''运费减免金额（元，免邮成本口径）'' AFTER `freight_base_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'freight_discount_price'
);
PREPARE stmt FROM @add_freight_discount; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_freight_rule_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `freight_rule_id` varchar(32) NULL COMMENT ''命中的运费规则ID'' AFTER `freight_discount_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'freight_rule_id'
);
PREPARE stmt FROM @add_freight_rule_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_freight_free_reason = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `freight_free_reason` varchar(32) NULL COMMENT ''免邮原因：THRESHOLD-满额包邮；MEMBER-会员免邮；PICKUP-自提；INTERNAL-到船配送；NONE-未减免'' AFTER `freight_rule_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'freight_free_reason'
);
PREPARE stmt FROM @add_freight_free_reason; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 默认规则种子（只绑默认租户；阈值按运营口径可后台改）
INSERT IGNORE INTO `freight_rule`
(`id`, `rule_name`, `biz_scene`, `delivery_way`, `province_codes`,
 `threshold_amount`, `base_freight`, `max_freight`, `priority`, `enabled`, `remark`,
 `tenant_id`, `create_by`, `create_time`, `update_time`, `del_flag`)
VALUES
('2240000000000000001', '海员个人-普通快递', '1', '1', NULL, 59.00, 6.00, 20.00, 10, '1',
 '满 59 元免运费，不满收 6 元；封顶 20 元（对标同城零售门槛型免邮）',
 @tenant_id, 'seed', NOW(), NULL, '0'),
('2240000000000000002', '企业采购-公司配送（岸上）', '3', '3', NULL, 300.00, 15.00, 60.00, 10, '1',
 '满 300 元免运费，不满收 15 元；封顶 60 元（批量采购兜底门槛）',
 @tenant_id, 'seed', NOW(), NULL, '0');

-- ============================================================================
-- 二、aryn_product：商品运费类型注释扩展 '2'
-- ============================================================================
USE `aryn_product`;

ALTER TABLE `goods_spu`
  MODIFY COLUMN `freight_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL
  COMMENT '运费类型：0.包邮；1.固定运费；2.随订单规则（参与满额包邮门槛）';

-- ============================================================================
-- 三、aryn_upms：字典值与菜单权限
-- ============================================================================
USE `aryn_upms`;

-- 字典值补 '2'：字典类型 freight_type（1905998662952812546）
INSERT INTO `sys_dict_value`
(`id`, `dict_id`, `dict_label`, `dict_value`, `dict_type`, `status`, `remarks`, `sort`, `del_flag`,
 `create_time`, `update_time`, `create_by`, `update_by`, `show_class`)
SELECT '2240000000000000010', d.`id`, '随订单规则', '2', 'freight_type', '0',
       '参与订单级满额包邮门槛，未达门槛按规则收基础运费', 2, '0', NOW(), NULL, 'system', NULL, 'warning'
FROM `sys_dict` d
WHERE d.`type` = 'freight_type' AND d.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_dict_value` v
	WHERE v.`dict_type` = 'freight_type' AND v.`dict_value` = '2' AND v.`del_flag` = '0');

UPDATE `sys_dict`
SET `remarks` = '运费类型：0.包邮；1.固定运费；2.随订单规则（参与满额包邮门槛）'
WHERE `type` = 'freight_type' AND `del_flag` = '0';

-- 菜单与按钮权限（配送管理 2100000000000000001 下）
INSERT IGNORE INTO `sys_menu`
(`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
 `create_time`, `outer_status`, `del_flag`, `application_key`, `create_by`)
VALUES
('2240000000000000100', '运费规则', 'delivery:freight:page', '/delivery/freight-rule', NULL,
 '2100000000000000001', 'carbon:delivery-truck', 'delivery/freight-rule/index', 60, '0',
 NOW(), '0', '0', 'app_base', 'system'),
('2240000000000000101', '运费规则分页', 'delivery:freight:page', NULL, NULL,
 '2240000000000000100', '', NULL, 1, '1', NOW(), '0', '0', 'app_base', 'system'),
('2240000000000000102', '运费规则新增', 'delivery:freight:add', NULL, NULL,
 '2240000000000000100', '', NULL, 2, '1', NOW(), '0', '0', 'app_base', 'system'),
('2240000000000000103', '运费规则修改', 'delivery:freight:edit', NULL, NULL,
 '2240000000000000100', '', NULL, 3, '1', NOW(), '0', '0', 'app_base', 'system'),
('2240000000000000104', '运费规则删除', 'delivery:freight:del', NULL, NULL,
 '2240000000000000100', '', NULL, 4, '1', NOW(), '0', '0', 'app_base', 'system');

-- 租户菜单：给所有存活租户
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(t.`id`, ':menu:', m.`id`)), t.`id`, m.`id`, NOW(), 'system'
FROM `sys_tenant` t
JOIN `sys_menu` m ON m.`id` IN ('2240000000000000100', '2240000000000000101',
                               '2240000000000000102', '2240000000000000103',
                               '2240000000000000104')
  AND m.`del_flag` = '0'
WHERE t.`tenant_type` = '0' AND t.`del_flag` = '0';

-- 角色授权：向已拥有配送管理父菜单的角色推导补授
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(r.`role_id`, ':menu:', m.`id`)), r.`role_id`, m.`id`, NOW(), r.`tenant_id`
FROM `sys_role_menu` r
JOIN `sys_menu` m ON m.`id` IN ('2240000000000000100', '2240000000000000101',
                               '2240000000000000102', '2240000000000000103',
                               '2240000000000000104')
  AND m.`del_flag` = '0'
WHERE r.`menu_id` = '2100000000000000001';

SET FOREIGN_KEY_CHECKS = 1;
