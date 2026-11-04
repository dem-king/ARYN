-- P1 专区治理菜单（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：新增「专区设置」与「出清折扣规则」两个管理页，需要菜单入口与按钮权限。
-- 权限：复用 promotion:shipactivity:page/save，避免新增权限点与菜单授权漂移。
-- 特性：INSERT IGNORE 按固定 ID 幂等；向试点租户与已拥有「船供活动」菜单的角色/租户推导式补授。
--
-- 执行：mysql -u root -p aryn_boot < 186p1_zone_governance_menu.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000301','专区设置','promotion:shipactivity:page','/promotion/zone-setting',NULL,'1779386604402573314','lucide:settings-2','promotion/zone-setting/index',24,'0',NOW(),'0','0','app_base','system'),
('2110000000000000302','专区设置保存','promotion:shipactivity:save',NULL,NULL,'2110000000000000301','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000303','出清折扣规则','promotion:shipactivity:page','/promotion/clearance-rule',NULL,'1779386604402573314','lucide:percent','promotion/clearance-rule/index',25,'0',NOW(),'0','0','app_base','system'),
('2110000000000000304','出清折扣规则保存','promotion:shipactivity:save',NULL,NULL,'2110000000000000303','',NULL,1,'1',NOW(),'0','0','app_base','system');

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT('1590229800633634816', ':menu:', m.`id`)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000301','2110000000000000302','2110000000000000303','2110000000000000304')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '2110000000000000101'
WHERE m.`id` IN ('2110000000000000301','2110000000000000302','2110000000000000303','2110000000000000304')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '2110000000000000101'
WHERE m.`id` IN ('2110000000000000301','2110000000000000302','2110000000000000303','2110000000000000304')
  AND m.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
