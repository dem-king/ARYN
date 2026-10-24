-- 会员管理闭环补齐（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 内容：
--   1. user_info.status：会员启停用状态；
--   2. points_record.biz_no：基础库兜底补齐（已执行 148 时为幂等空操作）；
--   3. member_benefit / member_benefit_level_rel：五档会员权益示例种子；
--   4. sign_in_config：7 天连续签到奖励示例种子；
--   5. recharge_config：100/200/500 三档充值套餐示例种子；
--   6. 新增会员管理按钮权限并授权 ROLE_ADMIN 与全部租户。
-- 特性：可重复执行；仅新增列/索引、本脚本固定前缀种子与菜单权限行，不删除存量业务数据。
-- 依赖：2aryn_boot.sql（user_info）、4aryn_boot_member.sql（会员表）、147member_level_seed.sql（五档等级）。
-- 口径：种子只绑定默认租户 1590229800633634816；其他租户请把 @tenant_id 改成目标租户后重跑。
-- 注意：菜单权限在登录时快照，执行后需让管理员重新登录，新增按钮权限才会生效。
--
-- 执行：mysql -u root -p aryn_boot < 158member_management_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

SET @tenant_id = _utf8mb4'1590229800633634816' COLLATE utf8mb4_general_ci;
SET @benefit_prefix = '2140000000000000';
SET @benefit_rel_prefix = '2150000000000000';
SET @signin_prefix = '2160000000000000';
SET @recharge_prefix = '2170000000000000';

-- ---------- 1. user_info.status ----------
SET @add_user_status = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `user_info` ADD COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''0'' COMMENT ''状态：0-启用；1-禁用'' AFTER `open_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_info' AND COLUMN_NAME = 'status'
);
PREPARE stmt FROM @add_user_status; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `user_info` SET `status` = '0' WHERE `status` IS NULL OR `status` = '';

-- ---------- 2. points_record.biz_no 兜底 ----------
SET @add_points_record_biz_no = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `points_record` ADD COLUMN `biz_no` varchar(64) NULL COMMENT ''业务单号（积分发放/扣减/返还幂等键）'' AFTER `remark`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'points_record' AND COLUMN_NAME = 'biz_no'
);
PREPARE stmt FROM @add_points_record_biz_no; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_points_record_biz_uk = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `points_record` ADD UNIQUE KEY `uk_points_record_biz` (`tenant_id`, `biz_no`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'points_record' AND INDEX_NAME = 'uk_points_record_biz'
);
PREPARE stmt FROM @add_points_record_biz_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 会员权益示例种子（五档等级，不含专属券） ----------
DELETE FROM `member_benefit_level_rel`
WHERE `benefit_id` LIKE '2140000000000000%' AND `tenant_id` = @tenant_id;
DELETE FROM `member_benefit`
WHERE `id` LIKE '2140000000000000%' AND `tenant_id` = @tenant_id;

INSERT INTO `member_benefit`
  (`id`, `benefit_name`, `benefit_type`, `benefit_value`, `description`, `status`,
   `create_by`, `create_time`, `update_time`, `del_flag`, `tenant_id`)
VALUES
  ('2140000000000000001', '新客专享 9.8 折', '1', '0.98', '新客会员商品折扣', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000002', '银卡专享 9.5 折', '1', '0.95', '银卡会员商品折扣', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000003', '银卡 1.2 倍积分', '4', '1.2', '银卡会员订单积分倍率', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000004', '金卡专享 9.2 折', '1', '0.92', '金卡会员商品折扣', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000005', '金卡 1.5 倍积分', '4', '1.5', '金卡会员订单积分倍率', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000006', '金卡免运费', '2', '1', '金卡会员快递与商城配送免运费', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000007', '铂金专享 9 折', '1', '0.90', '铂金会员商品折扣', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000008', '铂金 2 倍积分', '4', '2', '铂金会员订单积分倍率', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000009', '铂金免运费', '2', '1', '铂金会员快递与商城配送免运费', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000010', '钻石专享 8.5 折', '1', '0.85', '钻石会员商品折扣', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000011', '钻石 3 倍积分', '4', '3', '钻石会员订单积分倍率', '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2140000000000000012', '钻石免运费', '2', '1', '钻石会员快递与商城配送免运费', '0', 'seed', NOW(), NULL, '0', @tenant_id);

INSERT INTO `member_benefit_level_rel`
  (`id`, `benefit_id`, `level_id`, `create_by`, `create_time`, `tenant_id`)
VALUES
  ('2150000000000000001', '2140000000000000001', '2120000000000000001', 'seed', NOW(), @tenant_id),
  ('2150000000000000002', '2140000000000000002', '2120000000000000002', 'seed', NOW(), @tenant_id),
  ('2150000000000000003', '2140000000000000003', '2120000000000000002', 'seed', NOW(), @tenant_id),
  ('2150000000000000004', '2140000000000000004', '2120000000000000003', 'seed', NOW(), @tenant_id),
  ('2150000000000000005', '2140000000000000005', '2120000000000000003', 'seed', NOW(), @tenant_id),
  ('2150000000000000006', '2140000000000000006', '2120000000000000003', 'seed', NOW(), @tenant_id),
  ('2150000000000000007', '2140000000000000007', '2120000000000000004', 'seed', NOW(), @tenant_id),
  ('2150000000000000008', '2140000000000000008', '2120000000000000004', 'seed', NOW(), @tenant_id),
  ('2150000000000000009', '2140000000000000009', '2120000000000000004', 'seed', NOW(), @tenant_id),
  ('2150000000000000010', '2140000000000000010', '2120000000000000005', 'seed', NOW(), @tenant_id),
  ('2150000000000000011', '2140000000000000011', '2120000000000000005', 'seed', NOW(), @tenant_id),
  ('2150000000000000012', '2140000000000000012', '2120000000000000005', 'seed', NOW(), @tenant_id);

-- ---------- 4. 签到奖励示例种子（连续 1-7 天） ----------
DELETE FROM `sign_in_config`
WHERE `id` LIKE '2160000000000000%' AND `tenant_id` = @tenant_id;

INSERT INTO `sign_in_config`
  (`id`, `consecutive_day`, `reward_point`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_time`, `del_flag`, `tenant_id`)
VALUES
  ('2160000000000000001', 1, 5,  1, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2160000000000000002', 2, 5,  2, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2160000000000000003', 3, 10, 3, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2160000000000000004', 4, 10, 4, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2160000000000000005', 5, 15, 5, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2160000000000000006', 6, 15, 6, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2160000000000000007', 7, 30, 7, '0', 'seed', NOW(), NULL, '0', @tenant_id);

-- ---------- 5. 充值套餐示例种子 ----------
DELETE FROM `recharge_config`
WHERE `id` LIKE '2170000000000000%' AND `tenant_id` = @tenant_id;

INSERT INTO `recharge_config`
  (`id`, `recharge_amount`, `gift_amount`, `gift_point`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_time`, `del_flag`, `tenant_id`)
VALUES
  ('2170000000000000001', 100.00, 5.00,  0, 1, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2170000000000000002', 200.00, 15.00, 0, 2, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2170000000000000003', 500.00, 50.00, 0, 3, '0', 'seed', NOW(), NULL, '0', @tenant_id);

-- ---------- 6. 新增按钮权限 ----------
INSERT INTO `sys_menu`
  (`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
   `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
  ('2050000000000000007', '等级变更记录', 'user:memberlevel:get', NULL, NULL, '2050000000000000001', NULL, NULL, 6, '1', NOW(), NOW(), '0', '0', 'app_base', NULL, NULL),
  ('2050000000000000113', '关闭充值订单', 'user:rechargeorder:edit', NULL, NULL, '2050000000000000110', NULL, NULL, 3, '1', NOW(), NOW(), '0', '0', 'app_base', NULL, NULL),
  ('2050000000000000136', '补发专属优惠券', 'user:memberbenefit:edit', NULL, NULL, '2050000000000000130', NULL, NULL, 6, '1', NOW(), NOW(), '0', '0', 'app_base', NULL, NULL),
  ('2050000000000000137', '优惠券模板查看', 'promotion:couponinfo:page', NULL, NULL, '2050000000000000130', NULL, NULL, 7, '1', NOW(), NOW(), '0', '0', 'app_base', NULL, NULL),
  ('2050000000000000138', '优惠券模板查询', 'promotion:couponinfo:get', NULL, NULL, '2050000000000000130', NULL, NULL, 8, '1', NOW(), NOW(), '0', '0', 'app_base', NULL, NULL)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`),
  `sort` = VALUES(`sort`),
  `del_flag` = '0';

-- ---------- 7. 授权 ROLE_ADMIN 与全部租户 ----------
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.menu_id, NOW(), role.tenant_id
FROM `sys_role` AS role
CROSS JOIN (
  SELECT '2050000000000000007' AS menu_id
  UNION ALL SELECT '2050000000000000113'
  UNION ALL SELECT '2050000000000000136'
  UNION ALL SELECT '2050000000000000137'
  UNION ALL SELECT '2050000000000000138'
) AS menu
WHERE role.del_flag = '0'
  AND role.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` AS role_menu
    WHERE role_menu.role_id = role.id
      AND role_menu.menu_id = menu.menu_id
      AND role_menu.tenant_id = role.tenant_id
  );

INSERT INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT REPLACE(UUID(), '-', ''), tenant.id, menu.menu_id, NOW(), 'system'
FROM `sys_tenant` AS tenant
CROSS JOIN (
  SELECT '2050000000000000007' AS menu_id
  UNION ALL SELECT '2050000000000000113'
  UNION ALL SELECT '2050000000000000136'
  UNION ALL SELECT '2050000000000000137'
  UNION ALL SELECT '2050000000000000138'
) AS menu
WHERE tenant.del_flag = '0'
  AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_tenant_menu` AS tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id
      AND tenant_menu.menu_id = menu.menu_id
  );

SET FOREIGN_KEY_CHECKS = 1;
