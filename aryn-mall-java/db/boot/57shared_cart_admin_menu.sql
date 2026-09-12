-- 悦航购共享购物车管理页菜单增量（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：按固定 ID 幂等补齐；向试点租户与既有授权角色推导式补授。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000121','共享购物车',NULL,'/order/shared-cart',NULL,'2110000000000000000','lucide:shopping-basket','order/shared-cart/index',7,'0',NOW(),'0','0','app_base','system'),
('2110000000000000122','购物车列表','sharedcart:page',NULL,NULL,'2110000000000000121','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000123','购物车详情','sharedcart:get',NULL,NULL,'2110000000000000121','',NULL,2,'1',NOW(),'0','0','app_base','system');

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('2113', RIGHT(m.`id`, 16)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000121','2110000000000000122','2110000000000000123')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` IN ('2110000000000000122','2110000000000000123')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` IN ('2110000000000000122','2110000000000000123')
  AND m.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
