-- 悦航购船供营销二期菜单增量（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：按固定 ID 幂等补齐菜单与按钮；向试点租户与既有授权角色推导式补授。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 页面菜单：营销中心-船供活动、靠港日历、港口配送看板
INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000101','船供活动',NULL,'/promotion/ship-supply',NULL,'2110000000000000000','lucide:badge-percent','promotion/ship-supply/index',4,'0',NOW(),'0','0','app_base','system'),
('2110000000000000102','靠港日历',NULL,'/vessel/call-calendar',NULL,'2110000000000000000','lucide:calendar','vessel/call-calendar/index',5,'0',NOW(),'0','0','app_base','system'),
('2110000000000000103','港口配送看板',NULL,'/delivery/port-board',NULL,'2110000000000000000','lucide:anchor','delivery/port-board/index',6,'0',NOW(),'0','0','app_base','system'),
('2110000000000000111','活动列表','promotion:shipactivity:page',NULL,NULL,'2110000000000000101','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000112','活动保存','promotion:shipactivity:save',NULL,NULL,'2110000000000000101','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2110000000000000113','活动发布','promotion:shipactivity:publish',NULL,NULL,'2110000000000000101','',NULL,3,'1',NOW(),'0','0','app_base','system');

-- 向试点租户开通新菜单
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('2112', RIGHT(m.`id`, 16)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000101','2110000000000000102','2110000000000000103',
                 '2110000000000000111','2110000000000000112','2110000000000000113')
  AND m.`del_flag` = '0';

-- 向已拥有同页面菜单的租户/角色推导补授按钮（幂等）
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` IN ('2110000000000000111','2110000000000000112','2110000000000000113')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` IN ('2110000000000000111','2110000000000000112','2110000000000000113')
  AND m.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
