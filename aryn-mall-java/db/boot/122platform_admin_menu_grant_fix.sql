-- 悦航购平台管理员配送/船供菜单授权修复（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：system 用户（平台租户「系统租户」1881232176465358849 的「系统管理员」角色
--       ROLE_ADMIN，role_id=1881232177484574722）登录管理端看不到
--       「配送管理」(菜单前缀 2100000000000000%) 与「船供运营」(菜单前缀 2110000000000000%)。
--       平台租户登录时跳过租户菜单白名单交集（SysMenuServiceImpl.getLoginUserMenuTree），
--       菜单可见性完全由角色授权决定；但 sys_role_menu 在租户拦截表白名单内，
--       查询按当前租户过滤，历史手工补授权存在两类缺口：
--       1) ROLE_ADMIN 从未授予两个模块的目录与页面菜单（仅零星按钮，目录缺失整树不可见）；
--       2) 部分 2110 段授权行 tenant_id 误写为试点租户(1590229800633634816)，
--          对 system 恒不可见（连带模板市场、商城主题等一并失效）。
-- 语义：
--       一、修正 ROLE_ADMIN 授权行的租户归属：该角色属于系统租户，
--           其全部授权行 tenant_id 必须为 1881232176465358849；
--       二、幂等补全 2100/2110 段全部未删除菜单（目录/页面/按钮）授权，
--           新行 tenant_id 固定为系统租户，id 采用 MD5(角色:menu:菜单ID) 防撞风格。
--       全部可重复执行，不删除、不修改任何菜单数据（sys_menu 不动）。
-- 注意：sys_role_menu 查询按租户过滤，新增行 tenant_id 写成其他租户等于没授权；
--       执行后须清 Redis menu_cache 并重新登录管理端才生效（权限登录快照）。

USE `aryn_boot`;
SET NAMES utf8mb4;

-- 一、修正 ROLE_ADMIN 历史错行租户归属（该角色只属于系统租户）
UPDATE `sys_role_menu`
SET `tenant_id` = '1881232176465358849'
WHERE `role_id` = '1881232177484574722'
  AND `tenant_id` <> '1881232176465358849';

-- 二、补全配送管理(2100段)与船供运营(2110段)全量菜单授权
--     目录与页面决定菜单树可见性，按钮决定操作权限；del_flag='1' 的隐藏菜单不授。
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT('1881232177484574722', ':menu:', m.`id`)), '1881232177484574722', m.`id`, NOW(), '1881232176465358849'
FROM `sys_menu` m
WHERE (m.`id` LIKE '2100000000000000%' OR m.`id` LIKE '2110000000000000%')
  AND m.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm
    WHERE rm.`role_id` = '1881232177484574722' AND rm.`menu_id` = m.`id`
  );

-- 三、自检：ROLE_ADMIN 两个模块的授权行数与错租户行数（期望错行=0）
SELECT 'role_menu_2100_2110_count' AS item, COUNT(*) AS cnt
FROM `sys_role_menu`
WHERE `role_id` = '1881232177484574722'
  AND (`menu_id` LIKE '2100000000000000%' OR `menu_id` LIKE '2110000000000000%')
UNION ALL
SELECT 'role_menu_wrong_tenant_count', COUNT(*)
FROM `sys_role_menu`
WHERE `role_id` = '1881232177484574722'
  AND `tenant_id` <> '1881232176465358849';

SET FOREIGN_KEY_CHECKS = 1;
