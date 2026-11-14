-- ============================================================================
-- 采购主体管理 — 菜单与按钮权限种子（Cloud 微服务模式）
--
-- 目标库：aryn_upms
-- 背景（2026-11-12 需求「新人首单双轨与采购主体改造」P0）：
--   表结构见 237buyer_org_schema_incremental.sql。P0 先用管理端 API 验证审核流，
--   正式审核页在 P1-FE-05 / P2-FE-01，本脚本先把菜单与按钮权限入库，
--   避免上线时菜单漏配导致页面不可见。
--
-- 菜单（与 boot/226 同 ID、同权限串，双模式保持一致）：
--   2260000000000000100 采购主体（父=订单管理 1521496866882236418）
--   2260000000000000101 采购主体分页  order:buyerorg:page
--   2260000000000000102 采购主体创建  order:buyerorg:create
--   2260000000000000103 采购主体修改  order:buyerorg:edit
--   2260000000000000104 采购主体审核  order:buyerorg:audit
--   2260000000000000105 采购主体成员  order:buyerorg:member
--   2260000000000000106 采购主体邀请码 order:buyerorg:invite
--
-- 幂等：INSERT IGNORE；租户菜单/角色授权只补缺失，不覆盖既有授权。
-- 注意：菜单权限在登录时快照，执行后需清理 Redis `menu_cache::*` 并重新登录才可见。
--
-- 执行：mysql -u root -p aryn_upms < 238buyer_org_menu_incremental.sql
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu`
(`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
 `create_time`, `outer_status`, `del_flag`, `application_key`, `create_by`)
VALUES
('2260000000000000100', '采购主体', 'order:buyerorg:page', '/order/buyer-org', NULL,
 '1521496866882236418', 'carbon:industry', 'order/buyer-org/index', 40, '0',
 NOW(), '0', '0', 'app_base', 'system'),
('2260000000000000101', '采购主体分页', 'order:buyerorg:page', NULL, NULL,
 '2260000000000000100', '', NULL, 1, '1', NOW(), '0', '0', 'app_base', 'system'),
('2260000000000000102', '采购主体创建', 'order:buyerorg:create', NULL, NULL,
 '2260000000000000100', '', NULL, 2, '1', NOW(), '0', '0', 'app_base', 'system'),
('2260000000000000103', '采购主体修改', 'order:buyerorg:edit', NULL, NULL,
 '2260000000000000100', '', NULL, 3, '1', NOW(), '0', '0', 'app_base', 'system'),
('2260000000000000104', '采购主体审核', 'order:buyerorg:audit', NULL, NULL,
 '2260000000000000100', '', NULL, 4, '1', NOW(), '0', '0', 'app_base', 'system'),
('2260000000000000105', '采购主体成员', 'order:buyerorg:member', NULL, NULL,
 '2260000000000000100', '', NULL, 5, '1', NOW(), '0', '0', 'app_base', 'system'),
('2260000000000000106', '采购主体邀请码', 'order:buyerorg:invite', NULL, NULL,
 '2260000000000000100', '', NULL, 6, '1', NOW(), '0', '0', 'app_base', 'system');

-- 旧版本种子曾使用不存在的 carbon:factory（小程序构建会丢图标），已入库环境同步纠正
UPDATE `sys_menu` SET `icon` = 'carbon:industry'
WHERE `id` = '2260000000000000100' AND `icon` = 'carbon:factory';

-- 租户菜单：给所有存活租户（菜单可见性 = 角色菜单 ∩ 租户菜单）
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(t.`id`, ':menu:', m.`id`)), t.`id`, m.`id`, NOW(), 'system'
FROM `sys_tenant` t
JOIN `sys_menu` m ON m.`id` IN ('2260000000000000100', '2260000000000000101',
                               '2260000000000000102', '2260000000000000103',
                               '2260000000000000104', '2260000000000000105',
                               '2260000000000000106')
  AND m.`del_flag` = '0'
WHERE t.`del_flag` = '0';

-- 角色授权：向已拥有「订单管理」父菜单的角色推导补授（新角色走菜单树勾选）
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(r.`role_id`, ':menu:', m.`id`)), r.`role_id`, m.`id`, NOW(), r.`tenant_id`
FROM `sys_role_menu` r
JOIN `sys_menu` m ON m.`id` IN ('2260000000000000100', '2260000000000000101',
                               '2260000000000000102', '2260000000000000103',
                               '2260000000000000104', '2260000000000000105',
                               '2260000000000000106')
  AND m.`del_flag` = '0'
WHERE r.`menu_id` = '1521496866882236418';

SET FOREIGN_KEY_CHECKS = 1;
