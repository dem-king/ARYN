-- ============================================================================
-- 共享购物车成员明细权限开关（Cloud 微服务模式）
-- 背景：shared_cart_member.can_edit 此前只在前端生效（C 端据此隐藏加购入口），
--       服务端不校验，运营也没有任何入口可以调整它。本次两头补齐：
--       服务端 requireCanEdit 已使该标记具备真实约束力，本脚本补管理端按钮权限
--       「成员权限设置 sharedcart:member:permission」，用于收回/恢复某成员的明细维护权。
-- 内容：sys_menu 补 1 个按钮权限，并向已拥有同父菜单授权的角色与已开通同父菜单的租户推导补授。
-- 幂等：固定 ID + INSERT IGNORE，可重复执行；仅新增行，不修改、不删除存量数据。
-- 注意：菜单与权限在登录时快照，执行后管理端需重新登录才生效。
-- 无需改表结构（can_edit 列早已存在于 shared_cart_member）。
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 共享购物车下补「成员权限设置」按钮权限
--    ID 段 2110000000000000124 为空位（121 父菜单、122 列表、123 详情已占用）
INSERT IGNORE INTO `sys_menu`
	(`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
	 `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
	('2110000000000000124', '成员权限设置', 'sharedcart:member:permission', NULL, NULL,
	 '2110000000000000121', '', NULL, 3, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', 'system');

-- 2) 向已拥有同父菜单任一按钮授权的角色补授新按钮（MD5(role_id:menu:menu_id) 保证幂等）
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000124' AND m.`del_flag` = '0';

-- 3) 向已开通同父菜单的租户补齐租户菜单记录
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000124' AND m.`del_flag` = '0';

-- 执行结果自检：应返回 1 行，permission 必须是 sharedcart:member:permission。
-- 注意：ID 段 2110000000000000xxx 为多域共用的人工段，若此处 permission 不是本按钮，
-- 说明 ID 被别的脚本占了（INSERT IGNORE 会静默跳过），需换号重跑。
SELECT m.`id`, m.`name`, m.`permission`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.menu_id = m.`id`) AS role_grants,
       (SELECT COUNT(*) FROM `sys_tenant_menu` tm WHERE tm.menu_id = m.`id`) AS tenant_grants
FROM `sys_menu` m
WHERE m.`id` = '2110000000000000124';

SET FOREIGN_KEY_CHECKS = 1;
