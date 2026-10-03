-- 商城主题菜单入口增量（Cloud 微服务模式：菜单进 aryn_upms）
--
-- 目标库：aryn_upms
-- 背景（2026-10-02）：
--   「商城默认主题」（一键全商城换肤）此前只藏在装修器顶栏弹窗里，而它改的是
--   租户级全局配置，「设为默认」放在单页编辑器里作用域误导、发现性差。现将
--   主题库管理与设为默认迁到独立菜单，装修器只保留页级「本页主题」选择。
--   本脚本在「商城装修」(1527835787455164418) 下新增「商城主题」菜单
--   （sort=15，位于「微页面」与「模板市场」之间）。
-- 授权：向已拥有「微页面」(1600477837933047810) 的角色/租户推导式补授，
--       与 84template_market_menu.sql 同口径。
-- 特性：INSERT IGNORE 按固定 ID 幂等；无 DROP/TRUNCATE；重复执行不报错。
-- 注意：菜单权限在登录时快照，**执行后需重新登录**管理端才能看到新菜单。
--
-- 执行：mysql -u root -p aryn_upms < 109mall_theme_menu.sql

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 菜单（type='0'）：页面级 component 由前端 pageMap 按 views/{component}.vue 解析
INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000205','商城主题','promotion:pagedesign:theme','/promotion/mall-theme',NULL,'1527835787455164418','carbon:color-palette','promotion/mall-theme/index',15,'0',NOW(),'0','0','app_base','system');

-- 2. 试点租户直接授权（PK 与下方推导式同为 MD5(tenant:menu:id)，重复执行/推导命中自动 IGNORE）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT('1590229800633634816', ':menu:', m.`id`)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` = '2110000000000000205'
  AND m.`del_flag` = '0';

-- 3. 角色推导：向已拥有「微页面」菜单的角色补授
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '1600477837933047810'
WHERE m.`id` = '2110000000000000205'
  AND m.`del_flag` = '0';

-- 4. 租户推导：向已拥有「微页面」菜单的租户补授
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '1600477837933047810'
WHERE m.`id` = '2110000000000000205'
  AND m.`del_flag` = '0';

-- 5. 自检：菜单 1 行；租户授权应覆盖所有拥有「微页面」的租户
SELECT `id`, `name`, `permission`, `path`, `component`, `sort`, `parent_id`
FROM `sys_menu` WHERE `id` = '2110000000000000205';

SELECT `tenant_id`, `menu_id`
FROM `sys_tenant_menu` WHERE `menu_id` = '2110000000000000205';

SET FOREIGN_KEY_CHECKS = 1;
