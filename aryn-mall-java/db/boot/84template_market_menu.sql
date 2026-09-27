-- 悦航购模板市场菜单入口增量（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：模板市场浏览页 views/promotion/template-market/index.vue 已就位，但 sys_menu 无入口。
--       在「商城装修」(1527835787455164418) 下新增「模板市场」菜单（sort=20，排于微页面之后），
--       并向已拥有「微页面」(1600477837933047810) 权限的角色/租户推导式补授。
-- 特性：INSERT IGNORE 按固定 ID 幂等；无 DROP/TRUNCATE；重复执行不报错。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 菜单与下载按钮（type='0' 菜单，type='1' 按钮）
INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000201','模板市场','promotion:pagedesign:template-market','/promotion/template-market',NULL,'1527835787455164418','lucide:store','promotion/template-market/index',20,'0',NOW(),'0','0','app_base','system'),
('2110000000000000202','模板市场下载','promotion:pagedesign:template-market:download',NULL,NULL,'2110000000000000201','',NULL,1,'1',NOW(),'0','0','app_base','system');

-- 2. 试点租户直接授权（PK 与下方推导式同为 MD5(tenant:menu:id)，重复执行/推导命中自动 IGNORE）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT('1590229800633634816', ':menu:', m.`id`)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000201','2110000000000000202')
  AND m.`del_flag` = '0';

-- 3. 角色推导：向已拥有「微页面」菜单的角色补授模板市场菜单与下载按钮
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '1600477837933047810'
WHERE m.`id` IN ('2110000000000000201','2110000000000000202')
  AND m.`del_flag` = '0';

-- 4. 租户推导：向已拥有「微页面」菜单的租户补授模板市场菜单与下载按钮
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '1600477837933047810'
WHERE m.`id` IN ('2110000000000000201','2110000000000000202')
  AND m.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
