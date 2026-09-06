-- 悦航购商城配送履约增量迁移（Cloud 微服务模式）
-- 订单库：aryn_order；菜单/权限库：aryn_upms
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p < 27delivery_fulfillment_incremental.sql

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `delivery_staff` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `user_id` varchar(32) DEFAULT NULL COMMENT '关联sys_user后台用户ID',
  `staff_name` varchar(64) NOT NULL COMMENT '配送员姓名',
  `staff_phone` varchar(20) NOT NULL COMMENT '手机号',
  `status` varchar(2) NOT NULL DEFAULT '3' COMMENT '状态：1在线 2忙碌 3离线',
  `vehicle_info` varchar(255) DEFAULT NULL COMMENT '车辆信息',
  `openid` varchar(64) DEFAULT NULL COMMENT '微信openid',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), KEY `idx_delivery_staff_tenant` (`tenant_id`), KEY `idx_delivery_staff_user` (`user_id`), KEY `idx_delivery_staff_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配送员';

CREATE TABLE IF NOT EXISTS `delivery_trip` (
  `id` varchar(32) NOT NULL COMMENT '主键', `trip_no` varchar(64) NOT NULL COMMENT '出车单号（系统生成）',
  `staff_id` varchar(32) NOT NULL COMMENT '配送员ID', `status` varchar(2) NOT NULL DEFAULT '1' COMMENT '状态：1待配货 2配货中 3配送中 4已完成',
  `task_count` int NOT NULL DEFAULT 0 COMMENT '关联订单数', `warehouse_address` varchar(500) DEFAULT NULL COMMENT '仓库地址快照',
  `start_load_time` datetime DEFAULT NULL COMMENT '开始配货时间', `depart_time` datetime DEFAULT NULL COMMENT '装货出发时间',
  `complete_time` datetime DEFAULT NULL COMMENT '全部完成时间', `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID', `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_delivery_trip_no` (`tenant_id`,`trip_no`), KEY `idx_delivery_trip_staff` (`tenant_id`,`staff_id`), KEY `idx_delivery_trip_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出车单';

CREATE TABLE IF NOT EXISTS `delivery_task` (
  `id` varchar(32) NOT NULL COMMENT '主键', `task_no` varchar(64) NOT NULL COMMENT '任务编号', `trip_id` varchar(32) DEFAULT NULL COMMENT '关联出车单ID，待派单时为空',
  `order_id` varchar(32) NOT NULL COMMENT '关联订单ID', `order_no` varchar(64) NOT NULL COMMENT '订单号冗余', `staff_id` varchar(32) DEFAULT NULL COMMENT '配送员ID，待派单时为空',
  `status` varchar(2) NOT NULL DEFAULT '1' COMMENT '状态：1待派单 2待取货 3配货中 4待送达 5已送达 6已签收 7已取消 8异常 9待退回',
  `sort_no` int NOT NULL DEFAULT 1 COMMENT '送货顺序', `warehouse_address` varchar(500) DEFAULT NULL COMMENT '取货仓库地址快照',
  `recipient_name` varchar(64) DEFAULT NULL COMMENT '收货人姓名', `recipient_phone` varchar(20) DEFAULT NULL COMMENT '收货人电话', `recipient_address` varchar(500) DEFAULT NULL COMMENT '收货完整地址',
  `assign_time` datetime DEFAULT NULL COMMENT '派单时间', `pick_up_time` datetime DEFAULT NULL COMMENT '取货开始时间', `depart_time` datetime DEFAULT NULL COMMENT '出发配送时间',
  `arrive_time` datetime DEFAULT NULL COMMENT '送达时间', `sign_time` datetime DEFAULT NULL COMMENT '签收时间', `exception_time` datetime DEFAULT NULL COMMENT '异常时间',
  `return_pending_time` datetime DEFAULT NULL COMMENT '待退回时间', `return_confirm_time` datetime DEFAULT NULL COMMENT '退回确认时间', `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `attempt_no` int NOT NULL DEFAULT 1 COMMENT '当前尝试号，从1递增', `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `exception_reason` varchar(64) DEFAULT NULL COMMENT '异常原因编码', `exception_desc` varchar(500) DEFAULT NULL COMMENT '异常说明', `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID', `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_delivery_task_no` (`tenant_id`,`task_no`), UNIQUE KEY `uk_delivery_task_order` (`tenant_id`,`order_id`),
  KEY `idx_delivery_task_trip` (`tenant_id`,`trip_id`), KEY `idx_delivery_task_order` (`tenant_id`,`order_id`), KEY `idx_delivery_task_staff` (`tenant_id`,`staff_id`), KEY `idx_delivery_task_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配送任务';

CREATE TABLE IF NOT EXISTS `delivery_task_item` (
  `id` varchar(32) NOT NULL COMMENT '主键', `task_id` varchar(32) NOT NULL COMMENT '关联配送任务ID', `order_item_id` varchar(32) NOT NULL COMMENT '关联订单明细ID',
  `spu_name` varchar(255) DEFAULT NULL COMMENT '商品名称快照', `sku_name` varchar(255) DEFAULT NULL COMMENT '规格名称快照', `quantity` int NOT NULL DEFAULT 0 COMMENT '应取数量',
  `image` varchar(500) DEFAULT NULL COMMENT '商品图片快照', `picked` varchar(2) NOT NULL DEFAULT '0' COMMENT '0未取 1已取', `picked_time` datetime DEFAULT NULL COMMENT '确认取货时间',
  `attempt_no` int NOT NULL DEFAULT 1 COMMENT '当前尝试号', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID', `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), KEY `idx_delivery_task_item_task` (`tenant_id`,`task_id`), KEY `idx_delivery_task_item_order_item` (`tenant_id`,`order_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='取货明细';

CREATE TABLE IF NOT EXISTS `delivery_warehouse_config` (
  `id` varchar(32) NOT NULL COMMENT '主键', `warehouse_name` varchar(128) NOT NULL COMMENT '仓库名称', `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人', `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `province` varchar(64) DEFAULT NULL COMMENT '省', `city` varchar(64) DEFAULT NULL COMMENT '市', `area` varchar(64) DEFAULT NULL COMMENT '区', `address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID', `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), KEY `idx_delivery_warehouse_config_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库配置';

CREATE TABLE IF NOT EXISTS `delivery_task_log` (
  `id` varchar(32) NOT NULL COMMENT '主键', `task_id` varchar(32) NOT NULL COMMENT '关联配送任务ID', `action` varchar(32) NOT NULL COMMENT '动作', `from_status` varchar(2) DEFAULT NULL COMMENT '原状态', `to_status` varchar(2) DEFAULT NULL COMMENT '目标状态',
  `attempt_no` int DEFAULT NULL COMMENT '尝试号', `operator_type` varchar(2) DEFAULT NULL COMMENT '操作人类型：1管理员 2配送员 3系统', `operator_id` varchar(32) DEFAULT NULL COMMENT '操作人ID', `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名快照',
  `reason_code` varchar(64) DEFAULT NULL COMMENT '原因编码', `reason_desc` varchar(500) DEFAULT NULL COMMENT '说明', `summary` text DEFAULT NULL COMMENT '受控JSON摘要', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `create_time` datetime DEFAULT NULL COMMENT '创建时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), KEY `idx_delivery_task_log_task` (`tenant_id`,`task_id`), KEY `idx_delivery_task_log_action` (`tenant_id`,`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配送任务操作日志';

CREATE TABLE IF NOT EXISTS `delivery_evidence` (
  `id` varchar(32) NOT NULL COMMENT '主键', `task_id` varchar(32) NOT NULL COMMENT '关联配送任务ID', `attempt_no` int NOT NULL COMMENT '尝试号', `evidence_type` varchar(2) NOT NULL COMMENT '凭证类型：1送达 2异常 3退回',
  `material_id` varchar(32) DEFAULT NULL COMMENT '关联素材ID', `material_url` varchar(500) DEFAULT NULL COMMENT '素材访问URL快照', `sort_no` int NOT NULL DEFAULT 1 COMMENT '排序', `upload_by` varchar(32) DEFAULT NULL COMMENT '上传人ID', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `create_time` datetime DEFAULT NULL COMMENT '创建时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), KEY `idx_delivery_evidence_task` (`tenant_id`,`task_id`), KEY `idx_delivery_evidence_material` (`tenant_id`,`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='送达凭证';

CREATE TABLE IF NOT EXISTS `delivery_area` (
  `id` varchar(32) NOT NULL COMMENT '主键', `province_code` varchar(12) DEFAULT NULL COMMENT '省编码', `province_name` varchar(64) DEFAULT NULL COMMENT '省名称', `city_code` varchar(12) DEFAULT NULL COMMENT '市编码', `city_name` varchar(64) DEFAULT NULL COMMENT '市名称',
  `area_code` varchar(12) DEFAULT NULL COMMENT '区县编码', `area_name` varchar(64) DEFAULT NULL COMMENT '区县名称', `enabled` varchar(2) NOT NULL DEFAULT '1' COMMENT '启用状态：1启用 0禁用', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人', `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), KEY `idx_delivery_area_tenant` (`tenant_id`), KEY `idx_delivery_area_codes` (`tenant_id`,`province_code`,`city_code`,`area_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配送范围';

-- 菜单、权限和默认租户授权位于 UPMS 库。ID 已固定，重复执行不会重复插入。
USE `aryn_upms`;
CREATE TABLE IF NOT EXISTS `sys_user_wechat_binding` (
  `id` varchar(32) NOT NULL COMMENT '主键', `user_id` varchar(32) NOT NULL COMMENT '员工ID', `app_id` varchar(64) NOT NULL COMMENT '小程序AppID', `openid` varchar(64) NOT NULL COMMENT 'openid', `status` varchar(2) NOT NULL DEFAULT '1' COMMENT '绑定状态：1有效 0解绑',
  `bind_time` datetime DEFAULT NULL COMMENT '绑定时间', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID', `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `create_time` datetime DEFAULT NULL COMMENT '创建时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_wechat_binding_openid` (`tenant_id`,`app_id`,`openid`,`status`), KEY `idx_wechat_binding_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工微信绑定';

-- 兼容旧版 Cloud 脚本误建在 aryn_order 的绑定表：存在时迁移数据，不删除旧表。
SET @copy_legacy_wechat_binding = (
  SELECT IF(COUNT(*) = 0, 'SELECT 1',
    'INSERT IGNORE INTO `aryn_upms`.`sys_user_wechat_binding` (`id`,`user_id`,`app_id`,`openid`,`status`,`bind_time`,`tenant_id`,`create_by`,`create_time`,`del_flag`) SELECT `id`,`user_id`,`app_id`,`openid`,`status`,`bind_time`,`tenant_id`,`create_by`,`create_time`,`del_flag` FROM `aryn_order`.`sys_user_wechat_binding`')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = 'aryn_order' AND TABLE_NAME = 'sys_user_wechat_binding'
);
PREPARE stmt FROM @copy_legacy_wechat_binding; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_material_object_key = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_material` ADD COLUMN `object_key` varchar(255) DEFAULT NULL COMMENT ''稳定对象键'' AFTER `url`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_material' AND COLUMN_NAME = 'object_key'
);
PREPARE stmt FROM @add_material_object_key; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @add_material_biz_tag = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_material` ADD COLUMN `biz_tag` varchar(64) DEFAULT NULL COMMENT ''业务归属：delivery-evidence=配送凭证'' AFTER `object_key`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_material' AND COLUMN_NAME = 'biz_tag'
);
PREPARE stmt FROM @add_material_biz_tag; EXECUTE stmt; DEALLOCATE PREPARE stmt;
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`)
SELECT '2103000000000000001', d.`id`, '商城配送', '3', 'delivery_way', '0', '商城配送', 3, '0', NOW(), 'system'
FROM `sys_dict` d WHERE d.`type` = 'delivery_way' LIMIT 1;

INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2100000000000000001','配送管理',NULL,'/delivery','/delivery/task','0','carbon:delivery-truck','',50,'0',NOW(),'0','0','app_base','system'),
('2100000000000000002','配送任务',NULL,'/delivery/task',NULL,'2100000000000000001','carbon:task','delivery/task/index',10,'0',NOW(),'0','0','app_base','system'),
('2100000000000000003','配送员管理',NULL,'/delivery/staff',NULL,'2100000000000000001','carbon:user-role','delivery/staff/index',20,'0',NOW(),'0','0','app_base','system'),
('2100000000000000004','出车单管理',NULL,'/delivery/trip',NULL,'2100000000000000001','carbon:truck','delivery/trip/index',30,'0',NOW(),'0','0','app_base','system'),
('2100000000000000005','仓库配置',NULL,'/delivery/config',NULL,'2100000000000000001','carbon:warehouse','delivery/config/index',40,'0',NOW(),'0','0','app_base','system'),
('2100000000000000006','配送范围',NULL,'/delivery/area',NULL,'2100000000000000001','carbon:location','delivery/area/index',50,'0',NOW(),'0','0','app_base','system'),
('2100000000000000010','任务分页','delivery:task:page',NULL,NULL,'2100000000000000002','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2100000000000000011','任务详情','delivery:task:get',NULL,NULL,'2100000000000000002','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2100000000000000012','派单','delivery:task:assign',NULL,NULL,'2100000000000000002','',NULL,3,'1',NOW(),'0','0','app_base','system'),
('2100000000000000013','改派','delivery:task:reassign',NULL,NULL,'2100000000000000002','',NULL,4,'1',NOW(),'0','0','app_base','system'),
('2100000000000000014','取消任务','delivery:task:cancel',NULL,NULL,'2100000000000000002','',NULL,5,'1',NOW(),'0','0','app_base','system'),
('2100000000000000015','异常处理','delivery:task:exception',NULL,NULL,'2100000000000000002','',NULL,6,'1',NOW(),'0','0','app_base','system'),
('2100000000000000016','退回确认','delivery:task:return',NULL,NULL,'2100000000000000002','',NULL,7,'1',NOW(),'0','0','app_base','system'),
('2100000000000000017','配送执行','delivery:execute',NULL,NULL,'2100000000000000002','',NULL,8,'1',NOW(),'0','0','app_base','system'),
('2100000000000000020','配送员分页','delivery:staff:page',NULL,NULL,'2100000000000000003','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2100000000000000021','新增配送员','delivery:staff:add',NULL,NULL,'2100000000000000003','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2100000000000000022','编辑配送员','delivery:staff:edit',NULL,NULL,'2100000000000000003','',NULL,3,'1',NOW(),'0','0','app_base','system'),
('2100000000000000023','删除配送员','delivery:staff:del',NULL,NULL,'2100000000000000003','',NULL,4,'1',NOW(),'0','0','app_base','system'),
('2100000000000000024','配送员详情','delivery:staff:get',NULL,NULL,'2100000000000000003','',NULL,5,'1',NOW(),'0','0','app_base','system'),
('2100000000000000025','配送员列表','delivery:staff:list',NULL,NULL,'2100000000000000003','',NULL,6,'1',NOW(),'0','0','app_base','system'),
('2100000000000000026','配送员状态','delivery:staff:status',NULL,NULL,'2100000000000000003','',NULL,7,'1',NOW(),'0','0','app_base','system'),
('2100000000000000030','出车单分页','delivery:trip:page',NULL,NULL,'2100000000000000004','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2100000000000000027','出车单详情','delivery:trip:get',NULL,NULL,'2100000000000000004','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2100000000000000040','配送范围分页','delivery:area:page',NULL,NULL,'2100000000000000006','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2100000000000000041','新增配送范围','delivery:area:add',NULL,NULL,'2100000000000000006','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2100000000000000042','编辑配送范围','delivery:area:edit',NULL,NULL,'2100000000000000006','',NULL,3,'1',NOW(),'0','0','app_base','system'),
('2100000000000000043','删除配送范围','delivery:area:del',NULL,NULL,'2100000000000000006','',NULL,4,'1',NOW(),'0','0','app_base','system'),
('2100000000000000050','查看仓库配置','delivery:warehouse:get',NULL,NULL,'2100000000000000005','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2100000000000000051','保存仓库配置','delivery:warehouse:edit',NULL,NULL,'2100000000000000005','',NULL,2,'1',NOW(),'0','0','app_base','system');

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT CONCAT('210100000000000', RIGHT(m.`id`, 4)), '1', m.`id`, NOW(), '1590229800633634816'
FROM `sys_menu` m WHERE m.`id` LIKE '2100000000000000%';
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT CONCAT('210200000000000', RIGHT(m.`id`, 4)), '1590229800633634816', m.`id`, NOW(), 'system'
FROM `sys_menu` m WHERE m.`id` LIKE '2100000000000000%';

-- Cloud 订单服务租户表清单（Nacos config_info）。重复执行不会重复追加。
USE `aryn_nacos`;
UPDATE `config_info`
SET `content` = REPLACE(`content`, '      - shopping_cart',
  '      - shopping_cart\n      - delivery_staff\n      - delivery_trip\n      - delivery_task\n      - delivery_task_item\n      - delivery_warehouse_config\n      - delivery_task_log\n      - delivery_evidence\n      - delivery_area'),
    `md5` = MD5(`content`),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  AND `content` NOT LIKE '%      - delivery_task%';
UPDATE `config_info`
SET `content` = REPLACE(`content`, '      - sys_material_group', '      - sys_material_group\n      - sys_user_wechat_binding')
WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  AND `content` NOT LIKE '%      - sys_user_wechat_binding%';

SET FOREIGN_KEY_CHECKS = 1;
