-- 悦航购船舶绑定申请审核菜单增量（Boot 单体模式）
-- 目标库：aryn_boot（单体模式所有表同库）
-- 特性：按固定 ID 幂等补齐；向试点租户与既有授权角色推导式补授。
--
-- 背景：VesselBindAdminController 的审核接口带 @SaCheckPermission，
--       不补菜单权限则运营在管理端看不到入口、调接口会 403。
--       权限在登录时快照进 token，授权后必须重新登录才生效。
--
-- 与 cloud/64vessel_bind_admin_menu.sql 内容一致，仅目标库不同。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000131','绑定申请',NULL,'/vessel/bind-apply',NULL,'2110000000000000000','lucide:user-check','vessel/bind-apply/index',3,'0',NOW(),'0','0','app_base','system'),
('2110000000000000132','申请列表','vessel:bindapply:page',NULL,NULL,'2110000000000000131','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000133','申请审核','vessel:bindapply:audit',NULL,NULL,'2110000000000000131','',NULL,2,'1',NOW(),'0','0','app_base','system');

-- 试点租户可见
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('2114', RIGHT(m.`id`, 16)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000131','2110000000000000132','2110000000000000133')
  AND m.`del_flag` = '0';

-- 推导式授权：基于「船供运营」父菜单（2110000000000000000）的既有授权推导。
--
-- 注意：不能按「同父节点推导」——本页三个菜单的父节点 131 本身也是新建的，
-- 其下没有已授权的兄弟节点，推导结果会是 0 条（第一版就踩了这个坑）。
-- 语义上正确的依据是：能看「船供运营」的角色，就应该能看「绑定申请」。
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '2110000000000000000'
WHERE m.`id` IN ('2110000000000000131','2110000000000000132','2110000000000000133')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '2110000000000000000'
WHERE m.`id` IN ('2110000000000000131','2110000000000000132','2110000000000000133')
  AND m.`del_flag` = '0';

-- 自检：三条菜单应就位，且至少被一个角色授权
SELECT m.`id`, m.`name`, m.`permission`, m.`type`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id`) AS granted_roles
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000131','2110000000000000132','2110000000000000133')
ORDER BY m.`id`;

SET FOREIGN_KEY_CHECKS = 1;
