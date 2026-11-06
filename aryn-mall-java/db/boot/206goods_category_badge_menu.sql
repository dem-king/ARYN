-- ============================================================================
-- 类目角标配置化 — 菜单与权限（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-05 需求「类目角标配置化」，见
--       docs/50-需求文档/2026-11-05-类目角标配置化/IMPLEMENTATION_PLAN.md 第 3、5 节）：
--   商品管理下新增「类目角标」页面，运营在这里维护角标文字、底色、字色、排序与启停；
--   类目表单/列表按角标 key 引用，C 端分类页按服务端下发的样式渲染。
--   页面由本脚本注册菜单即可挂载（component=product/goods-category-badge/index），
--   前端无需改静态路由。
--
-- 菜单（固定 ID，已确认 db/boot 与 db/cloud 全脚本、aryn_boot_full.sql 无冲突）：
--   2140000000000000100 类目角标（页面，父=商品管理 1779386487675092994，sort=6）
--   2140000000000000101 列表按钮 product:goodscategorybadge:page
--   2140000000000000102 查询按钮 product:goodscategorybadge:get
--   2140000000000000103 新增按钮 product:goodscategorybadge:add
--   2140000000000000104 修改按钮 product:goodscategorybadge:edit
--   2140000000000000105 删除按钮 product:goodscategorybadge:del
--
-- 授权（菜单可见性 = 角色菜单 ∩ 租户菜单）：
--   1) 试点租户 1590229800633634816 + 超管角色 1 显式补授；
--   2) 按「已拥有商品类目菜单 1496327483721789441」推导补授其余角色/租户；
--   3) 补齐祖先容器「商品管理」1779386487675092994：父容器缺失时 Hutool TreeUtil.build
--      会丢弃整棵子树，页面入口会在管理端静默消失。
--   全部 INSERT IGNORE（主键为固定 MD5 串），可重复执行、不修改/删除存量授权。
--
-- 注意：菜单/权限在登录时快照到会话，执行后需清理 Redis `menu_cache::*` 并重新登录才可见。
-- 执行：mysql -u root -p aryn_boot < 206goods_category_badge_menu.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、菜单与按钮（固定 ID，INSERT IGNORE 幂等）
-- ============================================================================
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
('2140000000000000100','类目角标','product:goodscategorybadge:page','/product/goods-category-badge',NULL,'1779386487675092994','carbon:tag','product/goods-category-badge/index',6,'0',NOW(),'0','0','app_base','system'),
('2140000000000000101','类目角标列表','product:goodscategorybadge:page',NULL,NULL,'2140000000000000100','','',1,'1',NOW(),'0','0','app_base','system'),
('2140000000000000102','类目角标查询','product:goodscategorybadge:get',NULL,NULL,'2140000000000000100','','',2,'1',NOW(),'0','0','app_base','system'),
('2140000000000000103','类目角标新增','product:goodscategorybadge:add',NULL,NULL,'2140000000000000100','','',3,'1',NOW(),'0','0','app_base','system'),
('2140000000000000104','类目角标修改','product:goodscategorybadge:edit',NULL,NULL,'2140000000000000100','','',4,'1',NOW(),'0','0','app_base','system'),
('2140000000000000105','类目角标删除','product:goodscategorybadge:del',NULL,NULL,'2140000000000000100','','',5,'1',NOW(),'0','0','app_base','system');

-- ============================================================================
-- 二、授权（INSERT IGNORE 幂等；主键 = MD5(授权对象标识 : menu : menu_id)）
-- ============================================================================

-- 2.1 试点租户显式补授（含祖先容器「商品管理」）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT('1590229800633634816', ':menu:', m.`id`)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('1779386487675092994',
                 '2140000000000000100','2140000000000000101','2140000000000000102',
                 '2140000000000000103','2140000000000000104','2140000000000000105')
  AND m.`del_flag` = '0';

-- 2.2 试点租户超管角色 1 显式补授（同 91ship_menu_role_grant.sql / 203sales_performance_menu.sql 口径）
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT('1', ':menu:', m.`id`)), '1', m.`id`, NOW(), '1590229800633634816'
FROM `sys_menu` m
WHERE m.`id` IN ('1779386487675092994',
                 '2140000000000000100','2140000000000000101','2140000000000000102',
                 '2140000000000000103','2140000000000000104','2140000000000000105')
  AND m.`del_flag` = '0';

-- 2.3 已拥有「商品类目」菜单的角色推导式补授（含祖先容器）
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(receiver.`role_id`, ':menu:', m.`id`)), receiver.`role_id`, m.`id`, NOW(),
       receiver.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `sys_role_menu`
      WHERE `menu_id` = '1496327483721789441') receiver
JOIN `sys_menu` m ON m.`id` IN ('1779386487675092994',
                                '2140000000000000100','2140000000000000101','2140000000000000102',
                                '2140000000000000103','2140000000000000104','2140000000000000105')
  AND m.`del_flag` = '0';

-- 2.4 已拥有「商品类目」菜单的租户推导式补授（含祖先容器）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(receiver.`tenant_id`, ':menu:', m.`id`)), receiver.`tenant_id`, m.`id`, NOW(),
       'system'
FROM (SELECT DISTINCT `tenant_id` FROM `sys_tenant_menu`
      WHERE `menu_id` = '1496327483721789441') receiver
JOIN `sys_menu` m ON m.`id` IN ('1779386487675092994',
                                '2140000000000000100','2140000000000000101','2140000000000000102',
                                '2140000000000000103','2140000000000000104','2140000000000000105')
  AND m.`del_flag` = '0';

-- 2.5 已获得本次新菜单的角色补齐祖先容器「商品管理」（父容器缺失会导致整棵子树被丢弃）
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(receiver.`role_id`, ':menu:', ancestor.`id`)), receiver.`role_id`, ancestor.`id`, NOW(),
       receiver.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `sys_role_menu`
      WHERE `menu_id` LIKE '214000000000000010%') receiver
JOIN `sys_menu` ancestor ON ancestor.`id` = '1779386487675092994' AND ancestor.`del_flag` = '0';

-- 2.6 已获得本次新菜单的租户补齐祖先容器「商品管理」
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(receiver.`tenant_id`, ':menu:', ancestor.`id`)), receiver.`tenant_id`, ancestor.`id`, NOW(),
       'system'
FROM (SELECT DISTINCT `tenant_id` FROM `sys_tenant_menu`
      WHERE `menu_id` LIKE '214000000000000010%') receiver
JOIN `sys_menu` ancestor ON ancestor.`id` = '1779386487675092994' AND ancestor.`del_flag` = '0';

-- ============================================================================
-- 三、自检：菜单 6 条（1 页面 + 5 按钮）、试点租户/超管授权齐全、祖先容器已授
--   期望：menu_rows=6、button_rows=5、其余 4 列 > 0
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM `sys_menu`
    WHERE `id` LIKE '214000000000000010%' AND `del_flag` = '0') AS menu_rows,
  (SELECT COUNT(*) FROM `sys_menu`
    WHERE `id` LIKE '214000000000000010%' AND `type` = '1' AND `del_flag` = '0') AS button_rows,
  (SELECT COUNT(*) FROM `sys_tenant_menu`
    WHERE `tenant_id` = '1590229800633634816'
      AND `menu_id` LIKE '214000000000000010%') AS pilot_tenant_grants,
  (SELECT COUNT(*) FROM `sys_role_menu`
    WHERE `role_id` = '1' AND `menu_id` LIKE '214000000000000010%') AS pilot_role_grants,
  (SELECT COUNT(*) FROM `sys_tenant_menu`
    WHERE `tenant_id` = '1590229800633634816'
      AND `menu_id` = '1779386487675092994') AS container_tenant_granted,
  (SELECT COUNT(*) FROM `sys_role_menu`
    WHERE `role_id` = '1' AND `menu_id` = '1779386487675092994') AS container_role_granted;

SET FOREIGN_KEY_CHECKS = 1;
