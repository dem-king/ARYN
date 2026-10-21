-- ============================================================================
-- 通知管理可用性增强：草稿删除、失败重试与收件人明细按钮权限（Cloud 微服务模式）
-- 目标库：aryn_upms
-- 背景（2026-10-21）：
--   通知管理此前只能编辑/发布/撤回，草稿无法删除；分发任务失败后也没有
--   可见状态与重试入口，发布后也看不到逐人收件情况。本次新增
--   DELETE /admin/notice/{id}、POST /admin/notice/{id}/dispatch/retry 与
--   GET /admin/notice/{id}/recipients，本脚本登记对应按钮权限：
--     message:notice:delete、message:notice:retry、message:notice:recipient
-- 授权：向已拥有「通知管理」页面菜单的角色/租户推导式补授。
-- 特性：INSERT IGNORE + MD5 固定授权 ID，可重复执行；无 DROP/TRUNCATE。
-- 注意：菜单权限在登录时快照，执行后需重新登录管理端才能看到新按钮。
--
-- 执行：mysql -u root -p aryn_upms < 141message_notice_admin_incremental.sql
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;

-- 一、登记按钮权限（挂在「通知管理」页面节点下，type=1 为按钮）
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,
 `create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
('2070000000000000017','删除通知草稿','message:notice:delete',NULL,NULL,
 '2070000000000000010','',NULL,7,'1',NOW(),'0','0','app_base','system'),
('2070000000000000018','重试通知分发','message:notice:retry',NULL,NULL,
 '2070000000000000010','',NULL,8,'1',NOW(),'0','0','app_base','system'),
('2070000000000000019','通知收件人明细','message:notice:recipient',NULL,NULL,
 '2070000000000000010','',NULL,9,'1',NOW(),'0','0','app_base','system');

-- 二、角色推导：向已拥有「通知管理」页面的角色补授
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '2070000000000000010'
WHERE m.`id` IN ('2070000000000000017', '2070000000000000018', '2070000000000000019')
  AND m.`del_flag` = '0';

-- 三、租户推导：向已拥有「通知管理」页面的租户补授
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '2070000000000000010'
WHERE m.`id` IN ('2070000000000000017', '2070000000000000018', '2070000000000000019')
  AND m.`del_flag` = '0';

-- 四、自检：应返回 3 行
SELECT m.`id`, m.`name`, m.`permission`, m.`parent_id`
FROM `sys_menu` m
WHERE m.`id` IN ('2070000000000000017', '2070000000000000018', '2070000000000000019');

SET FOREIGN_KEY_CHECKS = 1;
