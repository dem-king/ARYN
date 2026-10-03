-- ============================================================================
-- 订单商品按分类分组展示与导出增量
-- 背景：管理端订单详情/列表的商品信息按分类分组展示，并新增「订单导出」
--       （Excel 商品行按分类分组、含分类小计与汇总）。
-- 内容：sys_menu 补按钮权限 order:orderinfo:export（商城订单导出），
--       并向已拥有同父订单按钮授权的角色与已开通同父菜单的租户推导补授。
-- 幂等：固定 ID + INSERT IGNORE / NOT EXISTS，可重复执行；
--       仅新增行，不修改、不删除任何存量数据。
-- 注意：菜单在登录时快照，执行后管理端需重新登录才生效。
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;

-- 商城订单下补「商城订单导出」按钮权限
INSERT IGNORE INTO `sys_menu`
	(`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
	 `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
	('2110000000000000204', '商城订单导出', 'order:orderinfo:export', NULL, NULL,
	 '1531528760525074434', '', NULL, 10, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', 'system');

-- 向已拥有同父菜单任一按钮授权的角色补授新按钮，主键 MD5(role_id:menu:menu_id) 保证幂等
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000204' AND m.`del_flag` = '0';

-- 向已开通同父菜单的租户补齐租户菜单记录
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000204' AND m.`del_flag` = '0';

-- 执行结果自检：应返回 1 行菜单及对应的角色/租户授权数
SELECT m.`id`, m.`permission`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id`) AS role_grants,
       (SELECT COUNT(*) FROM `sys_tenant_menu` tm WHERE tm.`menu_id` = m.`id`) AS tenant_grants
FROM `sys_menu` m
WHERE m.`id` = '2110000000000000204';
