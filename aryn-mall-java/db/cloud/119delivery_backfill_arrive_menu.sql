-- ============================================================================
-- 管理端补录送达凭证：按钮权限种子（Cloud 微服务模式）
-- 目标库：aryn_upms（菜单/角色权限表）
-- 背景：货到付款确认收款以「货物已送达」为前提（delivery_task 已送达/签收），
--       而司机实际送达却漏点「送达」时订单会长期滞留待收货。
--       为此新增管理端补录送达凭证接口 POST /delivery/task/{id}/backfill-arrive，
--       本脚本登记对应按钮权限 delivery:task:backfill-arrive。
-- 语义：仅登记权限点并授予试点租户超级管理员角色（id=1）；
--       全部 INSERT IGNORE + 固定 id（2100000000000000018），可重复执行。
-- 注意：菜单可见性 = 角色菜单 ∩ 租户菜单，授权后须重新登录才生效（权限登录快照）。
-- 执行：mysql -u root -p aryn_upms < 119delivery_backfill_arrive_menu.sql
-- 与 db/boot/118delivery_backfill_arrive_menu.sql 内容保持一致，仅库名不同。
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;

-- 一、登记按钮权限（挂在「配送任务」页面节点下，type=1 为按钮）
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,
 `create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
('2100000000000000018','补录送达凭证','delivery:task:backfill-arrive',NULL,NULL,
 '2100000000000000002','',NULL,9,'1',NOW(),'0','0','app_base','system');

-- 二、授予试点租户超级管理员角色
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT('1', ':menu:', m.`id`)), '1', m.`id`, NOW(), '1590229800633634816'
FROM `sys_menu` m
WHERE m.`permission` = 'delivery:task:backfill-arrive'
  AND m.`del_flag` = '0';

-- 三、试点租户菜单兜底（可见性需同时落在租户菜单上）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('2118', RIGHT(m.`id`, 16)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`permission` = 'delivery:task:backfill-arrive'
  AND m.`del_flag` = '0';

-- 四、自检：应返回 1 行
SELECT m.`id`, m.`name`, m.`permission`, rm.`role_id`
FROM `sys_menu` m
LEFT JOIN `sys_role_menu` rm ON rm.`menu_id` = m.`id`
WHERE m.`permission` = 'delivery:task:backfill-arrive';

SET FOREIGN_KEY_CHECKS = 1;
