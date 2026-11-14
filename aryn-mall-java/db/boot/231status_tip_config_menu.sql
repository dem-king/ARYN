-- ============================================================================
-- 空态插画配置 — 菜单与按钮权限（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-13）：表见 230status_tip_config_incremental.sql。
--   入口挂在「系统设置」10001 下，与版权配置/文件存储配置同级，属租户自己的
--   品牌展示设置；不新增平台级入口。
--
-- 菜单（固定 ID，已确认 db/boot 与 db/cloud 全脚本无 231 前缀冲突）：
--   2310000000000000100 空态插画配置（父=系统设置 10001，app_base）
--   2310000000000000101 空态插画配置查询 upms:statustip:get
--   2310000000000000102 空态插画配置修改 upms:statustip:edit
--
-- 授权口径：按「文件存储配置」推导——能管存储配置的角色/租户即可管自己的空态图。
-- 注意：菜单/权限在登录时快照到会话，执行后需清理 Redis `menu_cache::*` 并重新登录才可见。
-- 执行：mysql -u root -p aryn_boot < 231status_tip_config_menu.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、菜单与按钮（INSERT IGNORE 幂等）
-- ============================================================================
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
('2310000000000000100','空态插画配置','upms:statustip:get','/system/status-tip-config',NULL,'10001','carbon:image','upms/status-tip-config/index',17,'0',NOW(),'0','0','app_base','system'),
('2310000000000000101','空态插画配置查询','upms:statustip:get',NULL,NULL,'2310000000000000100','','',1,'1',NOW(),'0','0','app_base','system'),
('2310000000000000102','空态插画配置修改','upms:statustip:edit',NULL,NULL,'2310000000000000100','','',2,'1',NOW(),'0','0','app_base','system');

-- ============================================================================
-- 二、菜单授权（按「文件存储配置」推导）
-- ============================================================================
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(receiver.`role_id`, ':menu:', m.`id`)), receiver.`role_id`, m.`id`, NOW(), receiver.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `sys_role_menu`
      WHERE `menu_id` = '1491948958826921986') receiver
JOIN `sys_menu` m ON m.`id` IN ('2310000000000000100','2310000000000000101','2310000000000000102')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(receiver.`tenant_id`, ':menu:', m.`id`)), receiver.`tenant_id`, m.`id`, NOW(), 'system'
FROM (SELECT DISTINCT `tenant_id` FROM `sys_tenant_menu`
      WHERE `menu_id` = '1491948958826921986') receiver
JOIN `sys_menu` m ON m.`id` IN ('2310000000000000100','2310000000000000101','2310000000000000102')
  AND m.`del_flag` = '0';

-- 祖先容器补齐：父容器缺失时管理端菜单树会丢弃整棵子树，入口静默消失
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(receiver.`role_id`, ':menu:', ancestor.`id`)), receiver.`role_id`, ancestor.`id`, NOW(), receiver.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `sys_role_menu`
      WHERE `menu_id` LIKE '23100000000000001%') receiver
JOIN `sys_menu` child ON child.`id` = '2310000000000000100' AND child.`del_flag` = '0'
JOIN `sys_menu` ancestor ON ancestor.`id` = child.`parent_id` AND ancestor.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(receiver.`tenant_id`, ':menu:', ancestor.`id`)), receiver.`tenant_id`, ancestor.`id`, NOW(), 'system'
FROM (SELECT DISTINCT `tenant_id` FROM `sys_tenant_menu`
      WHERE `menu_id` LIKE '23100000000000001%') receiver
JOIN `sys_menu` child ON child.`id` = '2310000000000000100' AND child.`del_flag` = '0'
JOIN `sys_menu` ancestor ON ancestor.`id` = child.`parent_id` AND ancestor.`del_flag` = '0';

-- ============================================================================
-- 三、自检
--   期望：menu_rows=3、role_menu_distinct_roles>0（锚点菜单被授权过的角色数）
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM `sys_menu`
    WHERE `id` IN ('2310000000000000100','2310000000000000101','2310000000000000102')
      AND `del_flag` = '0') AS menu_rows,
  (SELECT COUNT(DISTINCT `role_id`) FROM `sys_role_menu`
    WHERE `menu_id` = '1491948958826921986') AS anchor_role_rows,
  (SELECT COUNT(DISTINCT `role_id`) FROM `sys_role_menu`
    WHERE `menu_id` IN ('2310000000000000100','2310000000000000101','2310000000000000102')) AS granted_role_rows;

SET FOREIGN_KEY_CHECKS = 1;
