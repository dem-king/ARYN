-- ============================================================================
-- 货到付款（COD）支付方式增量
-- 背景：订单确认页对「商城配送 / 公司港口船舶内部配送」新增货到付款支付方式：
--       C 端选择后无需在线支付即可生成待发货订单，客户收货后线下把货款转给公司，
--       管理端通过「货到付款确认收款」按钮将订单标记为已收款（视同支付成功）。
-- 内容：
--   1) pay_type 字典补「货到付款」（dict_value=3），管理端与 C 端回显用；
--   2) sys_menu 补按钮权限 order:orderinfo:payconfirm（商城订单确认收款），
--      并向已拥有同父订单按钮授权的角色与已开通同父菜单的租户推导补授。
-- 幂等：固定 ID + INSERT IGNORE / NOT EXISTS，可重复执行；
--       仅新增行，不修改、不删除任何存量数据。
-- 注意：菜单与字典在登录时快照，执行后管理端需重新登录才生效。
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;

-- 1) pay_type 字典补「货到付款」
INSERT INTO `sys_dict_value`
	(`id`, `dict_id`, `dict_label`, `dict_value`, `dict_type`, `status`, `remarks`, `sort`, `del_flag`,
	 `create_time`, `update_time`, `create_by`, `update_by`, `show_class`)
SELECT '2110000000000000301', d.`id`, '货到付款', '3', 'pay_type', '0',
       '货到付款（商城配送/内部配送收货后线下收款）', 3, '0', NOW(), NOW(), 'system', 'system', 'warning'
FROM `sys_dict` d
WHERE d.`type` = 'pay_type' AND d.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_dict_value` v
	WHERE v.`dict_type` = 'pay_type' AND v.`dict_value` = '3' AND v.`del_flag` = '0');

-- 2) 商城订单下补「货到付款确认收款」按钮权限
INSERT IGNORE INTO `sys_menu`
	(`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
	 `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
	('2110000000000000203', '货到付款确认收款', 'order:orderinfo:payconfirm', NULL, NULL,
	 '1531528760525074434', '', NULL, 9, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', 'system');

-- 向已拥有同父菜单任一按钮授权的角色补授新按钮，主键 MD5(role_id:menu:menu_id) 保证幂等
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000203' AND m.`del_flag` = '0';

-- 向已开通同父菜单的租户补齐租户菜单记录
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000203' AND m.`del_flag` = '0';

-- 执行结果自检：应返回 1 行菜单、1 行字典值及对应的角色/租户授权数
SELECT m.`id`, m.`permission`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id`) AS role_grants,
       (SELECT COUNT(*) FROM `sys_tenant_menu` tm WHERE tm.`menu_id` = m.`id`) AS tenant_grants
FROM `sys_menu` m
WHERE m.`id` = '2110000000000000203';

SELECT v.`dict_label`, v.`dict_value`
FROM `sys_dict_value` v
WHERE v.`dict_type` = 'pay_type' AND v.`dict_value` = '3' AND v.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
