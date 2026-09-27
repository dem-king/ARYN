-- 悦航购船供菜单角色授权补齐（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：45/55/64 等脚本的推导式授权依赖「同父节点下已授权的兄弟菜单」，
--       在按仓库脚本全新初始化的库上推导条件落空（兄弟节点同为新建），
--       导致超级管理员角色拿不到「船供运营」目录及页面菜单，管理端整目录不可见。
--       cloud 运行库当时已手工补齐超级管理员的全量授权，本脚本将该状态幂等固化进仓库；
--       cloud 版（db/cloud/ 同名脚本）与运行库现状一致，执行为空操作。
-- 语义：向试点租户(1590229800633634816)的超级管理员角色(sys_role id=1)授予
--       sys_menu 中全部 2110000000000000% 前缀菜单（目录/页面/按钮）；
--       并按 45 号脚本 id 风格幂等兜底试点租户 sys_tenant_menu。
--       全部 INSERT IGNORE + 固定 id，可重复执行，不删除、不覆盖既有数据。
-- 注意：菜单可见性 = 角色菜单 ∩ 租户菜单，改完授权后须重新登录才生效（权限登录快照）。

USE `aryn_boot`;
SET NAMES utf8mb4;

-- 一、超级管理员角色全量授权（目录与页面决定可见性，按钮决定操作权限）
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT('1', ':menu:', m.`id`)), '1', m.`id`, NOW(), '1590229800633634816'
FROM `sys_menu` m
WHERE m.`id` LIKE '2110000000000000%'
  AND m.`del_flag` = '0';

-- 二、试点租户菜单兜底（缺则补、有则跳过）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('2111', RIGHT(m.`id`, 16)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` LIKE '2110000000000000%'
  AND m.`del_flag` = '0';
