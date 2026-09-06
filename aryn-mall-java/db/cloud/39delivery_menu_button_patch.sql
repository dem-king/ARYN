-- ============================================================================
-- 配送模块按钮权限补齐
-- 背景: 09174b7 将配送员详情/列表/状态与出车单详情四个按钮权限补入 27 号增量
--       脚本，但已按旧版执行过 27 号脚本的环境不会重跑，导致编辑配送员、
--       出车单详情等读接口报"无此权限: delivery:staff:get"。
-- 行为: 按固定 ID 幂等补齐 sys_menu 四个按钮行；并向已拥有同父菜单任一按钮
--       授权的角色与租户推导式补授，重复执行无副作用。
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2100000000000000024','配送员详情','delivery:staff:get',NULL,NULL,'2100000000000000003','',NULL,5,'1',NOW(),'0','0','app_base','system'),
('2100000000000000025','配送员列表','delivery:staff:list',NULL,NULL,'2100000000000000003','',NULL,6,'1',NOW(),'0','0','app_base','system'),
('2100000000000000026','配送员状态','delivery:staff:status',NULL,NULL,'2100000000000000003','',NULL,7,'1',NOW(),'0','0','app_base','system'),
('2100000000000000027','出车单详情','delivery:trip:get',NULL,NULL,'2100000000000000004','',NULL,2,'1',NOW(),'0','0','app_base','system');

-- 向已拥有同父菜单任一按钮授权的角色补授新按钮，主键用 MD5(role_id:menu:menu_id) 保证幂等。
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` IN ('2100000000000000024','2100000000000000025','2100000000000000026','2100000000000000027')
  AND m.`del_flag` = '0';

-- 向已开通同父菜单的租户补齐租户菜单记录。
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` IN ('2100000000000000024','2100000000000000025','2100000000000000026','2100000000000000027')
  AND m.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;

-- 执行结果自检：应返回 4 行菜单与对应的角色/租户授权。
SELECT m.`id`, m.`permission`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id`) AS role_grants,
       (SELECT COUNT(*) FROM `sys_tenant_menu` tm WHERE tm.`menu_id` = m.`id`) AS tenant_grants
FROM `sys_menu` m
WHERE m.`id` IN ('2100000000000000024','2100000000000000025','2100000000000000026','2100000000000000027');
