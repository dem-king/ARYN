-- 专区商品管理菜单（Cloud 微服务模式）
--
-- 目标库：aryn_upms
-- 与 boot/199zone_tag_batch_menu.sql 内容一致。
-- 菜单：挂在「营销管理」父菜单 1779386604402573314 下，路由 /promotion/zone-goods，
--       页面权限复用 promotion:shipactivity:page（写操作沿用 product:goodsspu:edit）。
-- 特性：INSERT IGNORE 按固定 ID 幂等；向试点租户与已拥有「船供活动」菜单的角色/租户推导式补授。
--
-- 执行：mysql -u root -p aryn_upms < 201zone_tag_batch_menu.sql

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000305','专区商品管理','promotion:shipactivity:page','/promotion/zone-goods',NULL,'1779386604402573314','lucide:layout-grid','promotion/zone-goods/index',26,'0',NOW(),'0','0','app_base','system');

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT('1590229800633634816', ':menu:', m.`id`)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000305')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '2110000000000000101'
WHERE m.`id` IN ('2110000000000000305')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '2110000000000000101'
WHERE m.`id` IN ('2110000000000000305')
  AND m.`del_flag` = '0';

-- 自检：菜单存在且已向试点租户授权，两列都应为 1
SELECT
  (SELECT COUNT(*) FROM `sys_menu` WHERE `id` = '2110000000000000305' AND `del_flag` = '0') AS zone_goods_menu,
  (SELECT COUNT(*) FROM `sys_tenant_menu`
    WHERE `menu_id` = '2110000000000000305' AND `tenant_id` = '1590229800633634816') AS pilot_tenant_granted;

SET FOREIGN_KEY_CHECKS = 1;
