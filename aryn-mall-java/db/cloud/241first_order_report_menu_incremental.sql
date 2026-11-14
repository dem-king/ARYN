-- ============================================================================
-- 首单核销报表 — 菜单与按钮权限种子（Cloud 微服务模式）
--
-- 目标库：aryn_upms
-- 内容与 boot 229first_order_report_menu_incremental.sql 一致。
--
-- 执行：mysql -u root -p < 241first_order_report_menu_incremental.sql
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu`
(`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
 `create_time`, `outer_status`, `del_flag`, `application_key`, `create_by`)
VALUES
('2290000000000000100', '首单核销报表', 'promotion:firstorderreport:page', '/promotion/first-order-report', NULL,
 '1779386604402573314', 'carbon:chart-line', 'promotion/first-order-report/index', 50, '0',
 NOW(), '0', '0', 'app_base', 'system'),
('2290000000000000101', '报表分页', 'promotion:firstorderreport:page', NULL, NULL,
 '2290000000000000100', '', NULL, 1, '1', NOW(), '0', '0', 'app_base', 'system'),
('2290000000000000102', '报表导出', 'promotion:firstorderreport:export', NULL, NULL,
 '2290000000000000100', '', NULL, 2, '1', NOW(), '0', '0', 'app_base', 'system');

INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(t.`id`, ':menu:', m.`id`)), t.`id`, m.`id`, NOW(), 'system'
FROM `sys_tenant` t
JOIN `sys_menu` m ON m.`id` IN ('2290000000000000100', '2290000000000000101',
                               '2290000000000000102')
  AND m.`del_flag` = '0'
WHERE t.`del_flag` = '0';

INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(r.`role_id`, ':menu:', m.`id`)), r.`role_id`, m.`id`, NOW(), r.`tenant_id`
FROM `sys_role_menu` r
JOIN `sys_menu` m ON m.`id` IN ('2290000000000000100', '2290000000000000101',
                               '2290000000000000102')
  AND m.`del_flag` = '0'
WHERE r.`menu_id` = '1779386604402573314';

SET FOREIGN_KEY_CHECKS = 1;
