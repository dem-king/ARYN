-- ============================================================================
-- 员工账号「修改手机号」权限增量（Cloud 微服务模式）
-- 目标库：aryn_upms
--
-- 背景：员工账号编辑弹窗此前把手机号输入框禁用、后端编辑接口也丢弃 phone 字段，
--       管理员无法给员工换绑手机号；新接口 PUT /upms/user/{id}/phone 需要独立授权。
-- 内容：sys_menu 补按钮权限 upms:sysuser:phone:edit（修改员工手机号），
--       向已拥有同父「重置密码」按钮（upms:sysuser:password）授权的角色补授，
--       并向已开通「重置密码」按钮的租户补齐租户菜单。
-- 权限口径：改手机号与重置密码同属账号接管级敏感操作，按同一批角色授权；
--       查看完整手机号仍是单独的 upms:sysuser:phone，不随本次授权放开。
-- 幂等：固定 ID + INSERT IGNORE / 派生授权，可重复执行；仅新增行，不改存量数据。
-- 注意：菜单在登录时快照，执行后管理端需重新登录才出现按钮。
-- 执行：mysql -u root -p aryn_upms < 184sysuser_phone_edit_permission.sql
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;

-- 员工账号下补「修改员工手机号」按钮权限
INSERT IGNORE INTO `sys_menu`
	(`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
	 `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
	('2110000000000000207', '修改员工手机号', 'upms:sysuser:phone:edit', NULL, NULL,
	 '1491752531735490561', '', NULL, 10, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', 'system');

-- 向已拥有同父「重置密码」按钮授权的角色补授新按钮，主键 MD5(role_id:menu:menu_id) 保证幂等
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id`
	AND peer.`del_flag` = '0'
	AND peer.`permission` = 'upms:sysuser:password'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000207' AND m.`del_flag` = '0';

-- 向已开通同父「重置密码」按钮的租户补齐租户菜单记录
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id`
	AND peer.`del_flag` = '0'
	AND peer.`permission` = 'upms:sysuser:password'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` = '2110000000000000207' AND m.`del_flag` = '0';

-- 执行结果自检：应返回 1 行菜单及对应的角色/租户授权数
SELECT m.`id`, m.`permission`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id`) AS role_grants,
       (SELECT COUNT(*) FROM `sys_tenant_menu` tm WHERE tm.`menu_id` = m.`id`) AS tenant_grants
FROM `sys_menu` m
WHERE m.`id` = '2110000000000000207';

SET FOREIGN_KEY_CHECKS = 1;
