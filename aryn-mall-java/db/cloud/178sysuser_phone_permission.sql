-- ============================================================================
-- 员工账号查看完整手机号权限增量
-- 背景：员工账号列表/详情响应统一脱敏（SysUserVO.phone -> 176****3399），
--       管理员需要联系同事时拿不到完整号码，也没有可授权的查看入口。
-- 内容：sys_menu 补按钮权限 upms:sysuser:phone（查看员工手机号），
--       并向已拥有同父员工账号按钮授权的角色与已开通同父菜单的租户推导补授。
-- 幂等：固定 ID + INSERT IGNORE / 派生授权，可重复执行；
--       仅新增行，不修改、不删除任何存量数据。
-- 注意：菜单在登录时快照，执行后管理端需重新登录才生效。
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;

-- 员工账号下补「查看员工手机号」按钮权限
INSERT IGNORE INTO `sys_menu`
	(`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
	 `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
	('2110000000000000206', '查看员工手机号', 'upms:sysuser:phone', NULL, NULL,
	 '1491752531735490561', '', NULL, 9, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', 'system');

-- 向已拥有同父菜单任一按钮授权的角色补授新按钮，主键 MD5(role_id:menu:menu_id) 保证幂等
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000206' AND m.`del_flag` = '0';

-- 向已开通同父菜单的租户补齐租户菜单记录
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000206' AND m.`del_flag` = '0';

-- 执行结果自检：应返回 1 行菜单及对应的角色/租户授权数
SELECT m.`id`, m.`permission`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id`) AS role_grants,
       (SELECT COUNT(*) FROM `sys_tenant_menu` tm WHERE tm.`menu_id` = m.`id`) AS tenant_grants
FROM `sys_menu` m
WHERE m.`id` = '2110000000000000206';
