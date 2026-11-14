-- ============================================================================
-- B2B 价格与会员体系 — 管理端菜单与按钮权限（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-14 需求「船供 B2B 活动与会员体系重构」P1）：
--   新体系管理入口：价格政策（协议价）/ 档期管理 挂「营销管理」；
--   企业等级 / 企业权益 挂「会员管理」。表结构见 236 号脚本。
--
-- 菜单 ID 段：2180000000000000000+（2180 段此前未占用）
-- 幂等：INSERT IGNORE；租户菜单/角色授权只向已持有父菜单的持有者补授，不覆盖既有授权。
-- 注意：菜单在登录时快照，执行后需重新登录管理端才生效。
-- 关联：cloud 模式对应 253b2b_price_member_menu_incremental.sql（同 ID 同权限串）。
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu`
  (id, name, permission, path, redirect, parent_id, icon, component, sort, type,
   create_time, update_time, outer_status, del_flag, application_key, create_by, update_by)
VALUES
  -- 价格政策（协议价）—— 营销管理下
  ('2180000000000000000', '价格政策', NULL, '/promotion/price-policy', NULL,
   '1779386604402573314', 'carbon:coins', 'promotion/price-policy/index', 30, '0',
   NOW(), NOW(), '0', '0', 'app_market', 'system', 'system'),
  ('2180000000000000001', '价格政策分页', 'promotion:pricepolicy:page', NULL, NULL,
   '2180000000000000000', NULL, NULL, 1, '1', NOW(), NOW(), '0', '0', 'app_market', 'system', NULL),
  ('2180000000000000002', '价格政策保存', 'promotion:pricepolicy:save', NULL, NULL,
   '2180000000000000000', NULL, NULL, 2, '1', NOW(), NOW(), '0', '0', 'app_market', 'system', NULL),
  ('2180000000000000003', '价格政策发布', 'promotion:pricepolicy:publish', NULL, NULL,
   '2180000000000000000', NULL, NULL, 3, '1', NOW(), NOW(), '0', '0', 'app_market', 'system', NULL),
  ('2180000000000000004', '价格政策删除', 'promotion:pricepolicy:del', NULL, NULL,
   '2180000000000000000', NULL, NULL, 4, '1', NOW(), NOW(), '0', '0', 'app_market', 'system', NULL),
  -- 档期管理 —— 营销管理下
  ('2180000000000000010', '档期管理', NULL, '/promotion/activity-calendar', NULL,
   '1779386604402573314', 'carbon:calendar', 'promotion/trade-calendar/index', 31, '0',
   NOW(), NOW(), '0', '0', 'app_market', 'system', 'system'),
  ('2180000000000000011', '档期分页', 'promotion:calendar:page', NULL, NULL,
   '2180000000000000010', NULL, NULL, 1, '1', NOW(), NOW(), '0', '0', 'app_market', 'system', NULL),
  ('2180000000000000012', '档期保存', 'promotion:calendar:save', NULL, NULL,
   '2180000000000000010', NULL, NULL, 2, '1', NOW(), NOW(), '0', '0', 'app_market', 'system', NULL),
  ('2180000000000000013', '档期删除', 'promotion:calendar:del', NULL, NULL,
   '2180000000000000010', NULL, NULL, 3, '1', NOW(), NOW(), '0', '0', 'app_market', 'system', NULL),
  -- 企业等级 —— 会员管理下
  ('2180000000000000020', '企业等级', NULL, '/user/enterprise-level', NULL,
   '1779386898750439425', 'carbon:enterprise', 'user/enterprise-level/index', 9, '0',
   NOW(), NOW(), '0', '0', 'app_base', 'system', 'system'),
  ('2180000000000000021', '企业等级分页', 'user:enterpriselevel:page', NULL, NULL,
   '2180000000000000020', NULL, NULL, 1, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', NULL),
  ('2180000000000000022', '企业等级保存', 'user:enterpriselevel:save', NULL, NULL,
   '2180000000000000020', NULL, NULL, 2, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', NULL),
  ('2180000000000000023', '企业等级删除', 'user:enterpriselevel:del', NULL, NULL,
   '2180000000000000020', NULL, NULL, 3, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', NULL),
  -- 企业权益 —— 会员管理下
  ('2180000000000000030', '企业权益', NULL, '/user/enterprise-benefit', NULL,
   '1779386898750439425', 'carbon:partner', 'user/enterprise-benefit/index', 10, '0',
   NOW(), NOW(), '0', '0', 'app_base', 'system', 'system'),
  ('2180000000000000031', '企业权益分页', 'user:enterprisebenefit:page', NULL, NULL,
   '2180000000000000030', NULL, NULL, 1, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', NULL),
  ('2180000000000000032', '企业权益保存', 'user:enterprisebenefit:save', NULL, NULL,
   '2180000000000000030', NULL, NULL, 2, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', NULL),
  ('2180000000000000033', '企业权益删除', 'user:enterprisebenefit:del', NULL, NULL,
   '2180000000000000030', NULL, NULL, 3, '1', NOW(), NOW(), '0', '0', 'app_base', 'system', NULL);

-- 租户授权：持有「营销管理」的租户补授价格政策/档期管理；持有「会员管理」的补授企业等级/权益
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_tenant_menu` gt
JOIN `sys_menu` m ON m.`id` IN (
  '2180000000000000000','2180000000000000001','2180000000000000002','2180000000000000003','2180000000000000004',
  '2180000000000000010','2180000000000000011','2180000000000000012','2180000000000000013')
  AND m.`del_flag` = '0'
WHERE gt.`menu_id` = '1779386604402573314';

INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_tenant_menu` gt
JOIN `sys_menu` m ON m.`id` IN (
  '2180000000000000020','2180000000000000021','2180000000000000022','2180000000000000023',
  '2180000000000000030','2180000000000000031','2180000000000000032','2180000000000000033')
  AND m.`del_flag` = '0'
WHERE gt.`menu_id` = '1779386898750439425';

-- 角色授权：持有父菜单的角色补授新菜单
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(r.`role_id`, ':menu:', m.`id`)), r.`role_id`, m.`id`, NOW(), r.`tenant_id`
FROM `sys_role_menu` r
JOIN `sys_menu` m ON m.`id` IN (
  '2180000000000000000','2180000000000000001','2180000000000000002','2180000000000000003','2180000000000000004',
  '2180000000000000010','2180000000000000011','2180000000000000012','2180000000000000013')
  AND m.`del_flag` = '0'
WHERE r.`menu_id` = '1779386604402573314';

INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(r.`role_id`, ':menu:', m.`id`)), r.`role_id`, m.`id`, NOW(), r.`tenant_id`
FROM `sys_role_menu` r
JOIN `sys_menu` m ON m.`id` IN (
  '2180000000000000020','2180000000000000021','2180000000000000022','2180000000000000023',
  '2180000000000000030','2180000000000000031','2180000000000000032','2180000000000000033')
  AND m.`del_flag` = '0'
WHERE r.`menu_id` = '1779386898750439425';

-- 自检（执行后人工核对）：新菜单 del_flag 应全为 0，授权行应随父菜单持有者生成
-- SELECT id, name, permission, del_flag FROM sys_menu WHERE id LIKE '218000000000000%' ORDER BY id;

SET FOREIGN_KEY_CHECKS = 1;
