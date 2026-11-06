-- 销售业绩归属与提成 — 菜单与字典（Cloud 微服务模式）
--
-- 目标库：aryn_upms（sys_menu / sys_tenant_menu / sys_role_menu / sys_dict / sys_dict_value）
-- 背景（2026-11-05 需求「销售业绩归属与提成」，见
--       docs/50-需求文档/2026-11-05-销售业绩归属与提成/IMPLEMENTATION_PLAN.md 6.1 / 6.3）：
--   营销管理下新增「销售管理」目录（销售员 / 客户归属 / 业绩单 / 发放台账 / 业绩报表 / 全局配置），
--   分销管理下新增「券包配置」「兑换记录」；同时补齐 8 个业务字典（含字典值 show_class 回显样式）。
-- 与 boot 脚本一致性：菜单段同 db/boot/203sales_performance_menu.sql，
--                     字典段同 db/boot/202sales_performance_incremental.sql 的「三、字典」小节。
-- 特性：INSERT IGNORE 按固定 ID 幂等；向试点租户显式补授、试点租户超管角色兜底授权，
--       并按「分销管理」菜单推导式补授其余角色/租户；字典按 type 判重、值按固定 ID 写入；
--       不修改、不删除存量数据。菜单可见性 = 角色菜单 ∩ 租户菜单。
-- 注意：菜单/权限在登录时快照到会话，执行后管理端需重新登录才能看到新菜单。
-- 执行：mysql -u root -p aryn_upms < 206sales_performance_menu.sql

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、菜单与按钮（固定 ID，INSERT IGNORE 幂等）
-- ============================================================================
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
-- 销售管理目录
('2130000000000000100','销售管理','','/promotion/sales',NULL,'1779386604402573314','carbon:user-multiple','',27,'0',NOW(),'0','0','app_base','system'),
-- 销售员
('2130000000000000110','销售员','promotion:salesstaff:page','/promotion/sales/staff',NULL,'2130000000000000100','carbon:user-avatar','promotion/sales/staff/index',1,'0',NOW(),'0','0','app_base','system'),
('2130000000000000111','销售员新增','promotion:salesstaff:add',NULL,NULL,'2130000000000000110','','',1,'1',NOW(),'0','0','app_base','system'),
('2130000000000000112','销售员编辑（含离职交接/状态）','promotion:salesstaff:edit',NULL,NULL,'2130000000000000110','','',2,'1',NOW(),'0','0','app_base','system'),
('2130000000000000113','销售员详情','promotion:salesstaff:get',NULL,NULL,'2130000000000000110','','',3,'1',NOW(),'0','0','app_base','system'),
-- 客户归属
('2130000000000000120','客户归属','promotion:salescustomer:page','/promotion/sales/customer',NULL,'2130000000000000100','carbon:user-role','promotion/sales/customer/index',2,'0',NOW(),'0','0','app_base','system'),
('2130000000000000121','客户改归属','promotion:salescustomer:edit',NULL,NULL,'2130000000000000120','','',1,'1',NOW(),'0','0','app_base','system'),
-- 业绩单
('2130000000000000130','业绩单','promotion:salesorder:page','/promotion/sales/order',NULL,'2130000000000000100','carbon:order-details','promotion/sales/order/index',3,'0',NOW(),'0','0','app_base','system'),
('2130000000000000131','业绩单导出','promotion:salesorder:export',NULL,NULL,'2130000000000000130','','',1,'1',NOW(),'0','0','app_base','system'),
('2130000000000000132','业绩单详情','promotion:salesorder:get',NULL,NULL,'2130000000000000130','','',2,'1',NOW(),'0','0','app_base','system'),
-- 发放台账
('2130000000000000140','发放台账','promotion:salespayout:page','/promotion/sales/payout',NULL,'2130000000000000100','carbon:money','promotion/sales/payout/index',4,'0',NOW(),'0','0','app_base','system'),
('2130000000000000141','生成台账','promotion:salespayout:generate',NULL,NULL,'2130000000000000140','','',1,'1',NOW(),'0','0','app_base','system'),
('2130000000000000142','确认发放','promotion:salespayout:confirm',NULL,NULL,'2130000000000000140','','',2,'1',NOW(),'0','0','app_base','system'),
('2130000000000000143','台账导出','promotion:salespayout:export',NULL,NULL,'2130000000000000140','','',3,'1',NOW(),'0','0','app_base','system'),
-- 业绩报表
('2130000000000000150','业绩报表','promotion:salesreport:page','/promotion/sales/report',NULL,'2130000000000000100','carbon:catalog','promotion/sales/report/index',5,'0',NOW(),'0','0','app_base','system'),
('2130000000000000151','报表导出','promotion:salesreport:export',NULL,NULL,'2130000000000000150','','',1,'1',NOW(),'0','0','app_base','system'),
-- 全局配置
('2130000000000000160','全局配置','promotion:salesconfig:page','/promotion/sales/config',NULL,'2130000000000000100','carbon:settings','promotion/sales/config/index',6,'0',NOW(),'0','0','app_base','system'),
('2130000000000000161','配置编辑','promotion:salesconfig:edit',NULL,NULL,'2130000000000000160','','',1,'1',NOW(),'0','0','app_base','system'),
-- 券包配置（父=分销管理）
('2130000000000000170','券包配置','promotion:distributioncouponpack:page','/promotion/distribution/coupon-pack',NULL,'1991000000000000001','lucide:gift','promotion/distribution-coupon-pack/index',5,'0',NOW(),'0','0','app_base','system'),
('2130000000000000171','券包新增','promotion:distributioncouponpack:add',NULL,NULL,'2130000000000000170','','',1,'1',NOW(),'0','0','app_base','system'),
('2130000000000000172','券包编辑','promotion:distributioncouponpack:edit',NULL,NULL,'2130000000000000170','','',2,'1',NOW(),'0','0','app_base','system'),
('2130000000000000173','券包删除','promotion:distributioncouponpack:del',NULL,NULL,'2130000000000000170','','',3,'1',NOW(),'0','0','app_base','system'),
-- 兑换记录（父=分销管理）
('2130000000000000180','兑换记录','promotion:distributioncouponexchange:page','/promotion/distribution/coupon-exchange',NULL,'1991000000000000001','carbon:document','promotion/distribution-coupon-exchange/index',6,'0',NOW(),'0','0','app_base','system');

-- ============================================================================
-- 二、授权（INSERT IGNORE 幂等；主键 MD5(tenant|role : menu : menu_id)）
-- ============================================================================

-- 2.1 试点租户显式补授（与 186/199 号菜单脚本口径一致）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT('1590229800633634816', ':menu:', m.`id`)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2130000000000000100','2130000000000000110','2130000000000000111','2130000000000000112',
                 '2130000000000000113','2130000000000000120','2130000000000000121','2130000000000000130',
                 '2130000000000000131','2130000000000000132','2130000000000000140','2130000000000000141',
                 '2130000000000000142','2130000000000000143','2130000000000000150','2130000000000000151',
                 '2130000000000000160','2130000000000000161','2130000000000000170','2130000000000000171',
                 '2130000000000000172','2130000000000000173','2130000000000000180')
  AND m.`del_flag` = '0';

-- 2.2 试点租户超级管理员角色兜底授权（同 91ship_menu_role_grant.sql 口径）
--     全新初始化时「分销管理」可能尚未授给超管角色，推导条件会落空导致整目录不可见，
--     这里显式授予试点租户超管角色(sys_role id=1)，保证按仓库脚本初始化后管理端可见。
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT('1', ':menu:', m.`id`)), '1', m.`id`, NOW(), '1590229800633634816'
FROM `sys_menu` m
WHERE m.`id` IN ('2130000000000000100','2130000000000000110','2130000000000000111','2130000000000000112',
                 '2130000000000000113','2130000000000000120','2130000000000000121','2130000000000000130',
                 '2130000000000000131','2130000000000000132','2130000000000000140','2130000000000000141',
                 '2130000000000000142','2130000000000000143','2130000000000000150','2130000000000000151',
                 '2130000000000000160','2130000000000000161','2130000000000000170','2130000000000000171',
                 '2130000000000000172','2130000000000000173','2130000000000000180')
  AND m.`del_flag` = '0';

-- 2.3 已拥有「分销管理」菜单的角色推导式补授
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '1991000000000000001'
WHERE m.`id` IN ('2130000000000000100','2130000000000000110','2130000000000000111','2130000000000000112',
                 '2130000000000000113','2130000000000000120','2130000000000000121','2130000000000000130',
                 '2130000000000000131','2130000000000000132','2130000000000000140','2130000000000000141',
                 '2130000000000000142','2130000000000000143','2130000000000000150','2130000000000000151',
                 '2130000000000000160','2130000000000000161','2130000000000000170','2130000000000000171',
                 '2130000000000000172','2130000000000000173','2130000000000000180')
  AND m.`del_flag` = '0';

-- 2.4 已拥有「分销管理」菜单的租户推导式补授
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '1991000000000000001'
WHERE m.`id` IN ('2130000000000000100','2130000000000000110','2130000000000000111','2130000000000000112',
                 '2130000000000000113','2130000000000000120','2130000000000000121','2130000000000000130',
                 '2130000000000000131','2130000000000000132','2130000000000000140','2130000000000000141',
                 '2130000000000000142','2130000000000000143','2130000000000000150','2130000000000000151',
                 '2130000000000000160','2130000000000000161','2130000000000000170','2130000000000000171',
                 '2130000000000000172','2130000000000000173','2130000000000000180')
  AND m.`del_flag` = '0';

-- ============================================================================
-- 二·补、祖先容器与存量「分销管理」菜单授权补齐
--
-- 背景（2026-11-05 联调发现）：仓库基线从未把「分销管理」目录写入 sys_tenant_menu /
--   sys_role_menu（2aryn_boot.sql 中 0 条授权），全新初始化的库上该目录及其 4 个页面
--   对租户管理员整组不可见；而本次新增的「券包配置 / 兑换记录」挂在「分销管理」下，
--   菜单树构建（Hutool TreeUtil.build）会丢弃父节点缺失的孤儿节点，于是这两页也一起消失。
--   处理：
--     1) 为已获得本次新菜单的角色/租户递归补齐祖先容器（营销管理、分销管理）；
--     2) 试点租户 + 超管角色补授「分销管理」存量子树（分销配置/分销用户/分销订单/分销提现），
--        保证分销员等既有页面与新增券页面在全新库上同样可见。
--   幂等：INSERT IGNORE + MD5 固定主键；不修改/删除存量授权。
-- ============================================================================

-- 2.5 已获得新菜单的角色补齐祖先容器（父目录缺失会导致整棵子树被丢弃）
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(receiver.`role_id`, ':menu:', ancestor.`id`)), receiver.`role_id`, ancestor.`id`, NOW(),
       receiver.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `sys_role_menu`
      WHERE `menu_id` LIKE '21300000000000001%') receiver
JOIN `sys_menu` ancestor ON ancestor.`id` IN ('1779386604402573314', '1991000000000000001')
  AND ancestor.`del_flag` = '0';

-- 2.6 已获得新菜单的租户补齐祖先容器
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(receiver.`tenant_id`, ':menu:', ancestor.`id`)), receiver.`tenant_id`, ancestor.`id`, NOW(),
       'system'
FROM (SELECT DISTINCT `tenant_id` FROM `sys_tenant_menu`
      WHERE `menu_id` LIKE '21300000000000001%') receiver
JOIN `sys_menu` ancestor ON ancestor.`id` IN ('1779386604402573314', '1991000000000000001')
  AND ancestor.`del_flag` = '0';

-- 2.7 试点租户补充授权「分销管理」存量子树（分销配置/分销用户/分销订单/分销提现）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT('1590229800633634816', ':menu:', m.`id`)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('1991000000000000001',
                 '1991000000000000010','1991000000000000011','1991000000000000012',
                 '1991000000000000013','1991000000000000014','1991000000000000015',
                 '1991000000000000020','1991000000000000021','1991000000000000022',
                 '1991000000000000023','1991000000000000024','1991000000000000025',
                 '1991000000000000030','1991000000000000031','1991000000000000032',
                 '1991000000000000033',
                 '1991000000000000040','1991000000000000041','1991000000000000042',
                 '1991000000000000043')
  AND m.`del_flag` = '0';

-- 2.8 试点租户超管角色补充授权同一子树
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT('1', ':menu:', m.`id`)), '1', m.`id`, NOW(), '1590229800633634816'
FROM `sys_menu` m
WHERE m.`id` IN ('1991000000000000001',
                 '1991000000000000010','1991000000000000011','1991000000000000012',
                 '1991000000000000013','1991000000000000014','1991000000000000015',
                 '1991000000000000020','1991000000000000021','1991000000000000022',
                 '1991000000000000023','1991000000000000024','1991000000000000025',
                 '1991000000000000030','1991000000000000031','1991000000000000032',
                 '1991000000000000033',
                 '1991000000000000040','1991000000000000041','1991000000000000042',
                 '1991000000000000043')
  AND m.`del_flag` = '0';

-- ============================================================================
-- 三、自检：菜单条数应为 23（页面/目录 9 + 按钮 14）、授权数 > 0
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM `sys_menu`
     WHERE `id` LIKE '21300000000000001%' AND `del_flag` = '0') AS menu_rows,
  (SELECT COUNT(*) FROM `sys_menu`
     WHERE `id` LIKE '21300000000000001%' AND `type` = '1' AND `del_flag` = '0') AS button_rows,
  (SELECT COUNT(*) FROM `sys_role_menu` WHERE `menu_id` LIKE '21300000000000001%') AS role_grants,
  (SELECT COUNT(*) FROM `sys_tenant_menu` WHERE `menu_id` LIKE '21300000000000001%') AS tenant_grants,
  -- 分销管理目录必须已授给试点租户超管角色与试点租户，否则挂在下面的券包/兑换页面会被菜单树丢弃
  (SELECT COUNT(*) FROM `sys_role_menu` WHERE `role_id` = '1' AND `menu_id` = '1991000000000000001')
    AS dist_container_role_granted,
  (SELECT COUNT(*) FROM `sys_tenant_menu`
     WHERE `tenant_id` = '1590229800633634816' AND `menu_id` = '1991000000000000001')
    AS dist_container_tenant_granted;

-- ============================================================================
-- 四、字典：销售业绩 / 分销售券（sys_dict + sys_dict_value，固定 ID，按 type 判重）
-- ============================================================================

-- 4.1 字典类型
INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000201', 'sales_staff_status', '销售员状态', '0', '0在职 1离职', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_staff_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000202', 'sales_bind_source', '销售客户归属来源', '0', 'SHARE/QRCODE/ADMIN/TRANSFER/CLAIM', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_bind_source');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000203', 'sales_order_status', '销售业绩单状态', '0', '0待确认 1可发放 2已发放 3已全额退款', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_order_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000204', 'sales_payout_status', '销售提成发放台账状态', '0', '0待确认 1已发放', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_payout_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000205', 'sales_claim_status', '销售待转交认领状态', '0', '0待首单 1已转交 2已失效', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_claim_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000206', 'sales_transfer_reward_status', '销售客户转交奖励状态', '0', '0待结算 1已入账 2已作废', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_transfer_reward_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000207', 'distribution_coupon_pack_status', '分销券包状态', '0', '0上架 1下架', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'distribution_coupon_pack_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000208', 'distribution_coupon_exchange_status', '分销佣金兑换状态', '0', '0发放中 1已发放 2发放失败', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'distribution_coupon_exchange_status');

-- 4.2 字典值：sales_staff_status（0在职 / 1离职）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000211', d.`id`, '在职', '0', 'sales_staff_status', '0', NULL, 1, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_staff_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000212', d.`id`, '离职', '1', 'sales_staff_status', '0', NULL, 2, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'sales_staff_status' AND d.`del_flag` = '0' LIMIT 1;

-- 4.3 字典值：sales_bind_source（SHARE/QRCODE/ADMIN/TRANSFER/CLAIM）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000221', d.`id`, '分享链接', 'SHARE', 'sales_bind_source', '0', NULL, 1, '0', NOW(), 'system', 'primary'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000222', d.`id`, '推广码', 'QRCODE', 'sales_bind_source', '0', NULL, 2, '0', NOW(), 'system', 'primary'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000223', d.`id`, '后台指派', 'ADMIN', 'sales_bind_source', '0', NULL, 3, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000224', d.`id`, '离职交接', 'TRANSFER', 'sales_bind_source', '0', NULL, 4, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000225', d.`id`, '首单转交', 'CLAIM', 'sales_bind_source', '0', NULL, 5, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

-- 4.4 字典值：sales_order_status（0待确认 / 1可发放 / 2已发放 / 3已全额退款）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000231', d.`id`, '待确认', '0', 'sales_order_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_order_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000232', d.`id`, '可发放', '1', 'sales_order_status', '0', NULL, 2, '0', NOW(), 'system', 'primary'
FROM `sys_dict` d WHERE d.`type` = 'sales_order_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000233', d.`id`, '已发放', '2', 'sales_order_status', '0', NULL, 3, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_order_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000234', d.`id`, '已全额退款', '3', 'sales_order_status', '0', NULL, 4, '0', NOW(), 'system', 'danger'
FROM `sys_dict` d WHERE d.`type` = 'sales_order_status' AND d.`del_flag` = '0' LIMIT 1;

-- 4.5 字典值：sales_payout_status（0待确认 / 1已发放）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000241', d.`id`, '待确认', '0', 'sales_payout_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_payout_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000242', d.`id`, '已发放', '1', 'sales_payout_status', '0', NULL, 2, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_payout_status' AND d.`del_flag` = '0' LIMIT 1;

-- 4.6 字典值：sales_claim_status（0待首单 / 1已转交 / 2已失效）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000251', d.`id`, '待首单', '0', 'sales_claim_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_claim_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000252', d.`id`, '已转交', '1', 'sales_claim_status', '0', NULL, 2, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_claim_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000253', d.`id`, '已失效', '2', 'sales_claim_status', '0', NULL, 3, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'sales_claim_status' AND d.`del_flag` = '0' LIMIT 1;

-- 4.7 字典值：sales_transfer_reward_status（0待结算 / 1已入账 / 2已作废）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000261', d.`id`, '待结算', '0', 'sales_transfer_reward_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_transfer_reward_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000262', d.`id`, '已入账', '1', 'sales_transfer_reward_status', '0', NULL, 2, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_transfer_reward_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000263', d.`id`, '已作废', '2', 'sales_transfer_reward_status', '0', NULL, 3, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'sales_transfer_reward_status' AND d.`del_flag` = '0' LIMIT 1;

-- 4.8 字典值：distribution_coupon_pack_status（0上架 / 1下架）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000271', d.`id`, '上架', '0', 'distribution_coupon_pack_status', '0', NULL, 1, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_pack_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000272', d.`id`, '下架', '1', 'distribution_coupon_pack_status', '0', NULL, 2, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_pack_status' AND d.`del_flag` = '0' LIMIT 1;

-- 4.9 字典值：distribution_coupon_exchange_status（0发放中 / 1已发放 / 2发放失败）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000281', d.`id`, '发放中', '0', 'distribution_coupon_exchange_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_exchange_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000282', d.`id`, '已发放', '1', 'distribution_coupon_exchange_status', '0', NULL, 2, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_exchange_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000283', d.`id`, '发放失败', '2', 'distribution_coupon_exchange_status', '0', NULL, 3, '0', NOW(), 'system', 'danger'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_exchange_status' AND d.`del_flag` = '0' LIMIT 1;

-- 五、字典自检：类型应为 8、值应为 24
SELECT
  (SELECT COUNT(*) FROM `sys_dict`
     WHERE `type` IN ('sales_staff_status','sales_bind_source','sales_order_status','sales_payout_status',
                      'sales_claim_status','sales_transfer_reward_status','distribution_coupon_pack_status',
                      'distribution_coupon_exchange_status') AND `del_flag` = '0') AS sales_dict_types,
  (SELECT COUNT(*) FROM `sys_dict_value`
     WHERE `dict_type` IN ('sales_staff_status','sales_bind_source','sales_order_status','sales_payout_status',
                           'sales_claim_status','sales_transfer_reward_status','distribution_coupon_pack_status',
                           'distribution_coupon_exchange_status') AND `del_flag` = '0') AS sales_dict_values;

SET FOREIGN_KEY_CHECKS = 1;
