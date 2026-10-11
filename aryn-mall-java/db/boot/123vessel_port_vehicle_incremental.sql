-- 悦航购港口报备车辆电话簿（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：船舶靠港后船员外出需要打车，只有完成港口报备的车辆才能进入港区。
--       本脚本落地「报备后名单」的承载：船务公司在管理端按港口维护车辆与司机电话，
--       船员在 C 端按当前靠港查询并一键拨打。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据；
--       菜单按固定 ID 幂等补齐，授权向已开通「船供运营」目录的角色/租户推导。
-- 关联：boot 的 application.yml hx.tenant.tables 需同步登记 vessel_port_vehicle（已随代码提交）。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、表：港口报备车辆
-- ============================================================================
CREATE TABLE IF NOT EXISTS `vessel_port_vehicle` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `port_name` varchar(128) NOT NULL COMMENT '港口名称（与vessel_call.port_name同口径）',
  `plate_no` varchar(32) NOT NULL COMMENT '车牌号',
  `driver_name` varchar(64) DEFAULT NULL COMMENT '司机姓名',
  `driver_phone` varchar(32) NOT NULL COMMENT '司机联系电话',
  `seat_count` int DEFAULT NULL COMMENT '座位数',
  `vehicle_desc` varchar(128) DEFAULT NULL COMMENT '车型描述（如7座商务）',
  `pickup_point` varchar(255) DEFAULT NULL COMMENT '港内等候点/上车位置',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注（服务时间等）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（越小越靠前）',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1启用 0停用',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vessel_port_vehicle` (`tenant_id`,`port_name`,`plate_no`),
  KEY `idx_vessel_port_vehicle_port` (`tenant_id`,`port_name`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='港口报备车辆';

-- ============================================================================
-- 二、菜单：船供运营目录下新增「港口报备车辆」页面 + 按钮权限
-- ============================================================================
INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000730','港口报备车辆',NULL,'/vessel/port-vehicle',NULL,'2110000000000000000','lucide:car-taxi-front','vessel/port-vehicle/index',4,'0',NOW(),'0','0','app_base','system'),
('2110000000000000731','车辆列表','vessel:portvehicle:page',NULL,NULL,'2110000000000000730','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000732','车辆新增','vessel:portvehicle:save',NULL,NULL,'2110000000000000730','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2110000000000000733','车辆修改','vessel:portvehicle:update',NULL,NULL,'2110000000000000730','',NULL,3,'1',NOW(),'0','0','app_base','system'),
('2110000000000000734','车辆删除','vessel:portvehicle:delete',NULL,NULL,'2110000000000000730','',NULL,4,'1',NOW(),'0','0','app_base','system');

-- 试点租户直开
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('2114', RIGHT(m.`id`, 16)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m
WHERE m.`id` IN ('2110000000000000730','2110000000000000731','2110000000000000732','2110000000000000733','2110000000000000734')
  AND m.`del_flag` = '0';

-- 推导式授权：能看「船供运营」目录（2110000000000000000）的角色，就应该能看本页。
-- （新页面父节点自身也是新建，不能按同父兄弟推导——64 号脚本踩过的坑。）
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '2110000000000000000'
WHERE m.`id` IN ('2110000000000000730','2110000000000000731','2110000000000000732','2110000000000000733','2110000000000000734')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '2110000000000000000'
WHERE m.`id` IN ('2110000000000000730','2110000000000000731','2110000000000000732','2110000000000000733','2110000000000000734')
  AND m.`del_flag` = '0';

-- 自检：五条菜单应就位，页面菜单至少被一个角色授权
SELECT m.`id`, m.`name`, m.`permission`, m.`type`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id`) AS granted_roles
FROM `sys_menu` m
WHERE m.`id` BETWEEN '2110000000000000730' AND '2110000000000000734'
ORDER BY m.`id`;

SET FOREIGN_KEY_CHECKS = 1;
