-- 悦航购船供化菜单权限与租户能力增量（Cloud 微服务模式）
-- 目标库：菜单/权限库 aryn_upms
-- 特性：按固定 ID 幂等补齐 sys_menu / sys_tenant_menu / sys_role_menu / sys_dict_value；
--       不删除、不覆盖已有业务数据。
-- 菜单范围：船舶档案（含成员/靠港按钮）、拣货波次、履约异常、商品批量导入按钮；
--           权限标识与 Web 管理端 v-access 代码一致。

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、sys_tenant 能力字段：business_mode（1 综合模式=个人购买与船供采购并存；2 纯零售）
-- 个人购买与船供采购不允许用租户级开关互相排斥，因此只保留“并存/纯零售”两种取值。
-- ============================================================================
SET @add_tenant_business_mode = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_tenant` ADD COLUMN `business_mode` char(2) DEFAULT ''1'' COMMENT ''业务模式：1综合（个人+船供并存） 2纯零售''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tenant' AND COLUMN_NAME = 'business_mode'
);
PREPARE stmt FROM @add_tenant_business_mode; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 二、菜单：船供运营目录 + 三个页面 + 按钮权限
-- ============================================================================
INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000000','船供运营',NULL,'/ship-supply-op',NULL,'0','lucide:anchor','',1,'0',NOW(),'0','0','app_base','system'),
('2110000000000000001','船舶档案',NULL,'/vessel/archive',NULL,'2110000000000000000','lucide:ship','vessel/vessel/index',1,'0',NOW(),'0','0','app_base','system'),
('2110000000000000002','拣货波次',NULL,'/fulfillment/wave',NULL,'2110000000000000000','lucide:clipboard-list','fulfillment/wave/index',2,'0',NOW(),'0','0','app_base','system'),
('2110000000000000003','履约异常',NULL,'/fulfillment/exception',NULL,'2110000000000000000','lucide:alert-triangle','fulfillment/exception/index',3,'0',NOW(),'0','0','app_base','system'),
('2110000000000000011','船舶列表','vessel:vessel:page',NULL,NULL,'2110000000000000001','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000012','船舶新增','vessel:vessel:save',NULL,NULL,'2110000000000000001','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2110000000000000013','船舶修改','vessel:vessel:update',NULL,NULL,'2110000000000000001','',NULL,3,'1',NOW(),'0','0','app_base','system'),
('2110000000000000014','成员列表','vessel:member:list',NULL,NULL,'2110000000000000001','',NULL,4,'1',NOW(),'0','0','app_base','system'),
('2110000000000000015','成员绑定','vessel:member:save',NULL,NULL,'2110000000000000001','',NULL,5,'1',NOW(),'0','0','app_base','system'),
('2110000000000000016','靠港列表','vessel:call:list',NULL,NULL,'2110000000000000001','',NULL,6,'1',NOW(),'0','0','app_base','system'),
('2110000000000000017','靠港新增','vessel:call:save',NULL,NULL,'2110000000000000001','',NULL,7,'1',NOW(),'0','0','app_base','system'),
('2110000000000000018','靠港修改','vessel:call:update',NULL,NULL,'2110000000000000001','',NULL,8,'1',NOW(),'0','0','app_base','system'),
('2110000000000000021','波次列表','fulfillment:wave:page',NULL,NULL,'2110000000000000002','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000022','波次创建','fulfillment:wave:save',NULL,NULL,'2110000000000000002','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2110000000000000023','扫码拣货','fulfillment:wave:pick',NULL,NULL,'2110000000000000002','',NULL,3,'1',NOW(),'0','0','app_base','system'),
('2110000000000000024','波次复核','fulfillment:wave:review',NULL,NULL,'2110000000000000002','',NULL,4,'1',NOW(),'0','0','app_base','system'),
('2110000000000000025','交接司机','fulfillment:wave:hand-over',NULL,NULL,'2110000000000000002','',NULL,5,'1',NOW(),'0','0','app_base','system'),
('2110000000000000031','异常列表','fulfillment:exception:page',NULL,NULL,'2110000000000000003','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000032','异常上报','fulfillment:exception:save',NULL,NULL,'2110000000000000003','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2110000000000000033','异常关闭','fulfillment:exception:close',NULL,NULL,'2110000000000000003','',NULL,3,'1',NOW(),'0','0','app_base','system'),
('2110000000000000041','导入模板','product:import:template',NULL,NULL,'1532620395988029442','',NULL,20,'1',NOW(),'0','0','app_base','system'),
('2110000000000000042','导入预览','product:import:preview',NULL,NULL,'1532620395988029442','',NULL,21,'1',NOW(),'0','0','app_base','system'),
('2110000000000000043','确认导入','product:import:confirm',NULL,NULL,'1532620395988029442','',NULL,22,'1',NOW(),'0','0','app_base','system');

-- ============================================================================
-- 三、租户开通：向试点租户直接开通全部新菜单
-- ============================================================================
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('2111', RIGHT(m.`id`, 16)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` LIKE '2110000000000000%'
  AND m.`del_flag` = '0';

-- 向已拥有同页面菜单任一按钮的角色推导式补授按钮（幂等）。
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_role_menu` gr ON gr.`menu_id` = peer.`id`
WHERE m.`id` LIKE '2110000000000000%'
  AND m.`type` = '1'
  AND m.`del_flag` = '0'
  AND m.`parent_id` <> '2110000000000000000';

-- 向已开通同目录页面的租户补齐按钮（幂等）。
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_menu` peer ON peer.`parent_id` = m.`parent_id` AND peer.`del_flag` = '0'
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = peer.`id`
WHERE m.`id` LIKE '2110000000000000%'
  AND m.`type` = '1'
  AND m.`del_flag` = '0'
  AND m.`parent_id` <> '2110000000000000000';

SET FOREIGN_KEY_CHECKS = 1;
