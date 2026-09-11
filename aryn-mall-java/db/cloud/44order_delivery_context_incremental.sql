-- 悦航购订单配送上下文与履约域增量迁移（Cloud 微服务模式）
-- 目标库：订单库 aryn_order；字典库 aryn_upms
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p < 44order_delivery_context_incremental.sql

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、order_info 扩展：购买场景与内部配送上下文快照
-- ============================================================================
SET @add_order_info_purchase_scene = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `purchase_scene` char(2) DEFAULT NULL COMMENT ''购买场景：1海员个人购买 2船供采购'' AFTER `delivery_way`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'purchase_scene'
);
PREPARE stmt FROM @add_order_info_purchase_scene; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_vessel_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `vessel_id` varchar(32) DEFAULT NULL COMMENT ''配送船舶ID快照'' AFTER `purchase_scene`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'vessel_id'
);
PREPARE stmt FROM @add_order_info_vessel_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_vessel_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `vessel_name` varchar(128) DEFAULT NULL COMMENT ''配送船舶名称快照'' AFTER `vessel_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'vessel_name'
);
PREPARE stmt FROM @add_order_info_vessel_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_vessel_call_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `vessel_call_id` varchar(32) DEFAULT NULL COMMENT ''靠港计划ID快照'' AFTER `vessel_name`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'vessel_call_id'
);
PREPARE stmt FROM @add_order_info_vessel_call_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_port_code = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `port_code` varchar(32) DEFAULT NULL COMMENT ''港口编码快照'' AFTER `vessel_call_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'port_code'
);
PREPARE stmt FROM @add_order_info_port_code; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_port_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `port_name` varchar(128) DEFAULT NULL COMMENT ''港口名称快照'' AFTER `port_code`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'port_name'
);
PREPARE stmt FROM @add_order_info_port_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_berth = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `berth` varchar(128) DEFAULT NULL COMMENT ''泊位快照'' AFTER `port_name`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'berth'
);
PREPARE stmt FROM @add_order_info_berth; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_window_start = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `delivery_window_start` datetime DEFAULT NULL COMMENT ''配送时间窗开始快照'' AFTER `berth`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'delivery_window_start'
);
PREPARE stmt FROM @add_order_info_window_start; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_window_end = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `delivery_window_end` datetime DEFAULT NULL COMMENT ''配送时间窗结束快照'' AFTER `delivery_window_start`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'delivery_window_end'
);
PREPARE stmt FROM @add_order_info_window_end; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_agent_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `agent_name` varchar(64) DEFAULT NULL COMMENT ''船上代理/经办人姓名快照'' AFTER `delivery_window_end`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'agent_name'
);
PREPARE stmt FROM @add_order_info_agent_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_agent_phone = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `agent_phone` varchar(20) DEFAULT NULL COMMENT ''船上代理/经办人电话快照'' AFTER `agent_name`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'agent_phone'
);
PREPARE stmt FROM @add_order_info_agent_phone; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_info_shared_cart_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `shared_cart_id` varchar(32) DEFAULT NULL COMMENT ''来源共享购物车ID'' AFTER `agent_phone`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'shared_cart_id'
);
PREPARE stmt FROM @add_order_info_shared_cart_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 二、order_item 扩展：购买场景、采购单位和数量规则快照、共享购物车来源
-- ============================================================================
SET @add_order_item_purchase_scene = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `purchase_scene` char(2) DEFAULT NULL COMMENT ''购买场景快照：1海员个人购买 2船供采购'' AFTER `status`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'purchase_scene'
);
PREPARE stmt FROM @add_order_item_purchase_scene; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_item_purchase_unit = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `purchase_unit` varchar(32) DEFAULT NULL COMMENT ''采购单位快照'' AFTER `purchase_scene`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'purchase_unit'
);
PREPARE stmt FROM @add_order_item_purchase_unit; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_item_package_spec = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `package_spec` varchar(128) DEFAULT NULL COMMENT ''箱规快照'' AFTER `purchase_unit`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'package_spec'
);
PREPARE stmt FROM @add_order_item_package_spec; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_item_moq = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `moq` int DEFAULT NULL COMMENT ''最小起订量快照'' AFTER `package_spec`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'moq'
);
PREPARE stmt FROM @add_order_item_moq; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_item_step_qty = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `step_qty` int DEFAULT NULL COMMENT ''数量步长快照'' AFTER `moq`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'step_qty'
);
PREPARE stmt FROM @add_order_item_step_qty; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_item_contributor = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `contributor_user_id` varchar(32) DEFAULT NULL COMMENT ''共享购物车来源成员用户ID'' AFTER `step_qty`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'contributor_user_id'
);
PREPARE stmt FROM @add_order_item_contributor; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_item_member_remark = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `member_remark` varchar(255) DEFAULT NULL COMMENT ''共享购物车成员备注快照'' AFTER `contributor_user_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'member_remark'
);
PREPARE stmt FROM @add_order_item_member_remark; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 三、delivery_task 扩展：购买场景与船舶/港口配送上下文快照
-- ============================================================================
SET @add_task_purchase_scene = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `purchase_scene` char(2) DEFAULT NULL COMMENT ''购买场景快照：1海员个人购买 2船供采购'' AFTER `status`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'purchase_scene'
);
PREPARE stmt FROM @add_task_purchase_scene; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_task_vessel_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `vessel_id` varchar(32) DEFAULT NULL COMMENT ''配送船舶ID快照'' AFTER `purchase_scene`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'vessel_id'
);
PREPARE stmt FROM @add_task_vessel_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_task_vessel_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `vessel_name` varchar(128) DEFAULT NULL COMMENT ''配送船舶名称快照'' AFTER `vessel_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'vessel_name'
);
PREPARE stmt FROM @add_task_vessel_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_task_vessel_call_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `vessel_call_id` varchar(32) DEFAULT NULL COMMENT ''靠港计划ID快照'' AFTER `vessel_name`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'vessel_call_id'
);
PREPARE stmt FROM @add_task_vessel_call_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_task_port_code = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `port_code` varchar(32) DEFAULT NULL COMMENT ''港口编码快照'' AFTER `vessel_call_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'port_code'
);
PREPARE stmt FROM @add_task_port_code; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_task_port_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `port_name` varchar(128) DEFAULT NULL COMMENT ''港口名称快照'' AFTER `port_code`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'port_name'
);
PREPARE stmt FROM @add_task_port_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_task_berth = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `berth` varchar(128) DEFAULT NULL COMMENT ''泊位快照'' AFTER `port_name`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'berth'
);
PREPARE stmt FROM @add_task_berth; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_task_window_start = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `delivery_window_start` datetime DEFAULT NULL COMMENT ''配送时间窗开始快照'' AFTER `berth`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'delivery_window_start'
);
PREPARE stmt FROM @add_task_window_start; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_task_window_end = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD COLUMN `delivery_window_end` datetime DEFAULT NULL COMMENT ''配送时间窗结束快照'' AFTER `delivery_window_start`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND COLUMN_NAME = 'delivery_window_end'
);
PREPARE stmt FROM @add_task_window_end; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 四、补索引（幂等）
-- ============================================================================
SET @idx_order_info_scene = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD INDEX `idx_order_info_scene` (`tenant_id`,`purchase_scene`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND INDEX_NAME = 'idx_order_info_scene'
);
PREPARE stmt FROM @idx_order_info_scene; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_order_info_vessel = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD INDEX `idx_order_info_vessel` (`tenant_id`,`vessel_id`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND INDEX_NAME = 'idx_order_info_vessel'
);
PREPARE stmt FROM @idx_order_info_vessel; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_order_info_call = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD INDEX `idx_order_info_call` (`tenant_id`,`vessel_call_id`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND INDEX_NAME = 'idx_order_info_call'
);
PREPARE stmt FROM @idx_order_info_call; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_order_item_scene = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD INDEX `idx_order_item_scene` (`tenant_id`,`purchase_scene`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND INDEX_NAME = 'idx_order_item_scene'
);
PREPARE stmt FROM @idx_order_item_scene; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_delivery_task_call = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD INDEX `idx_delivery_task_call` (`tenant_id`,`vessel_call_id`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND INDEX_NAME = 'idx_delivery_task_call'
);
PREPARE stmt FROM @idx_delivery_task_call; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_delivery_task_port = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `delivery_task` ADD INDEX `idx_delivery_task_port` (`tenant_id`,`port_code`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'delivery_task' AND INDEX_NAME = 'idx_delivery_task_port'
);
PREPARE stmt FROM @idx_delivery_task_port; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 五、共享购物车
-- ============================================================================
CREATE TABLE IF NOT EXISTS `shared_cart` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `cart_no` varchar(64) NOT NULL COMMENT '共享购物车编号',
  `vessel_id` varchar(32) NOT NULL COMMENT '船舶ID（绑定后不可变更）',
  `vessel_call_id` varchar(32) NOT NULL COMMENT '靠港计划ID（绑定后不可变更）',
  `owner_user_id` varchar(32) NOT NULL COMMENT '发起人用户ID',
  `confirmer_user_id` varchar(32) DEFAULT NULL COMMENT '确认人用户ID（默认发起人）',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1草稿 2收集中 3待确认 4已提交 5已关闭',
  `expires_at` datetime DEFAULT NULL COMMENT '收集截止时间',
  `submit_order_id` varchar(32) DEFAULT NULL COMMENT '提交生成的订单ID',
  `submitted_time` datetime DEFAULT NULL COMMENT '提交时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shared_cart_no` (`tenant_id`,`cart_no`),
  KEY `idx_shared_cart_vessel_call` (`tenant_id`,`vessel_id`,`vessel_call_id`),
  KEY `idx_shared_cart_owner` (`tenant_id`,`owner_user_id`,`status`),
  KEY `idx_shared_cart_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='共享购物车';

CREATE TABLE IF NOT EXISTS `shared_cart_member` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `cart_id` varchar(32) NOT NULL COMMENT '共享购物车ID',
  `user_id` varchar(32) NOT NULL COMMENT '成员用户ID',
  `member_role` char(2) NOT NULL DEFAULT '2' COMMENT '成员角色：1发起人 2成员 3确认人',
  `can_edit` char(2) NOT NULL DEFAULT '1' COMMENT '可编辑自己的明细：1是 0否',
  `can_confirm` char(2) NOT NULL DEFAULT '0' COMMENT '可确认提交订单：1是 0否',
  `joined_time` datetime DEFAULT NULL COMMENT '加入时间',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shared_cart_member` (`tenant_id`,`cart_id`,`user_id`),
  KEY `idx_shared_cart_member_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='共享购物车成员';

CREATE TABLE IF NOT EXISTS `shared_cart_item` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `cart_id` varchar(32) NOT NULL COMMENT '共享购物车ID',
  `user_id` varchar(32) NOT NULL COMMENT '添加成员用户ID',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `requested_quantity` int NOT NULL DEFAULT 1 COMMENT '成员申请数量（采购单位）',
  `approved_quantity` int DEFAULT NULL COMMENT '确认人核定数量（采购单位）',
  `member_remark` varchar(255) DEFAULT NULL COMMENT '成员备注',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '明细状态：1待确认 2已确认 3已移除',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shared_cart_item` (`tenant_id`,`cart_id`,`user_id`,`sku_id`),
  KEY `idx_shared_cart_item_cart` (`tenant_id`,`cart_id`),
  KEY `idx_shared_cart_item_sku` (`tenant_id`,`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='共享购物车明细';

-- ============================================================================
-- 六、仓库履约：拣货波次、拣货明细、异常
-- ============================================================================
CREATE TABLE IF NOT EXISTS `fulfillment_wave` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `wave_no` varchar(64) NOT NULL COMMENT '波次编号',
  `warehouse_id` varchar(32) DEFAULT NULL COMMENT '仓库ID（delivery_warehouse_config）',
  `port_code` varchar(32) DEFAULT NULL COMMENT '目的港口编码',
  `port_name` varchar(128) DEFAULT NULL COMMENT '目的港口名称快照',
  `vessel_call_id` varchar(32) DEFAULT NULL COMMENT '关联靠港计划ID',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1待拣货 2拣货中 3已复核 4已交司机 5已完成 6已取消',
  `plan_delivery_time` datetime DEFAULT NULL COMMENT '计划交付时间',
  `picked_time` datetime DEFAULT NULL COMMENT '拣货完成时间',
  `reviewed_time` datetime DEFAULT NULL COMMENT '复核完成时间',
  `handed_over_time` datetime DEFAULT NULL COMMENT '交接司机时间',
  `completed_time` datetime DEFAULT NULL COMMENT '波次完成时间',
  `operator_id` varchar(32) DEFAULT NULL COMMENT '仓库操作员ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '仓库操作员姓名快照',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fulfillment_wave_no` (`tenant_id`,`wave_no`),
  KEY `idx_fulfillment_wave_status` (`tenant_id`,`status`),
  KEY `idx_fulfillment_wave_port` (`tenant_id`,`port_code`),
  KEY `idx_fulfillment_wave_call` (`tenant_id`,`vessel_call_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='拣货波次';

CREATE TABLE IF NOT EXISTS `fulfillment_pick_item` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `wave_id` varchar(32) NOT NULL COMMENT '拣货波次ID',
  `order_id` varchar(32) NOT NULL COMMENT '订单ID',
  `order_item_id` varchar(32) NOT NULL COMMENT '订单明细ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `spu_name` varchar(255) DEFAULT NULL COMMENT '商品名称快照',
  `sku_name` varchar(255) DEFAULT NULL COMMENT '规格名称快照',
  `sku_barcode` varchar(64) DEFAULT NULL COMMENT '条码快照（扫码校验用）',
  `required_quantity` int NOT NULL DEFAULT 0 COMMENT '应拣数量（采购单位）',
  `picked_quantity` int NOT NULL DEFAULT 0 COMMENT '实拣数量（采购单位）',
  `short_quantity` int NOT NULL DEFAULT 0 COMMENT '短装数量',
  `short_reason_code` varchar(64) DEFAULT NULL COMMENT '短装原因编码',
  `short_reason_desc` varchar(500) DEFAULT NULL COMMENT '短装原因说明',
  `pick_status` char(2) NOT NULL DEFAULT '1' COMMENT '拣货状态：1待拣 2已拣 3短装 4替代',
  `substituted_sku_id` varchar(32) DEFAULT NULL COMMENT '替代商品SKU ID',
  `scanned_time` datetime DEFAULT NULL COMMENT '扫码确认时间',
  `picker_id` varchar(32) DEFAULT NULL COMMENT '拣货员ID',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fulfillment_pick_item` (`tenant_id`,`wave_id`,`order_item_id`),
  KEY `idx_fulfillment_pick_item_order` (`tenant_id`,`order_id`),
  KEY `idx_fulfillment_pick_item_sku` (`tenant_id`,`sku_id`),
  KEY `idx_fulfillment_pick_item_status` (`tenant_id`,`pick_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='拣货明细';

CREATE TABLE IF NOT EXISTS `fulfillment_exception` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `wave_id` varchar(32) DEFAULT NULL COMMENT '拣货波次ID',
  `order_id` varchar(32) DEFAULT NULL COMMENT '订单ID',
  `task_id` varchar(32) DEFAULT NULL COMMENT '配送任务ID',
  `exception_type` varchar(32) NOT NULL COMMENT '异常类型：SHORT_PICK缺货 WRONG_ITEM错发 DAMAGE破损 REPLACE替代 OTHER其他',
  `description` varchar(500) DEFAULT NULL COMMENT '异常描述',
  `evidence_urls` varchar(1000) DEFAULT NULL COMMENT '证据图片URL（JSON数组）',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '处理状态：1待处理 2处理中 3已关闭',
  `handler_id` varchar(32) DEFAULT NULL COMMENT '处理人ID',
  `handle_remark` varchar(500) DEFAULT NULL COMMENT '处理说明',
  `handled_time` datetime DEFAULT NULL COMMENT '处理完成时间',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_fulfillment_exception_status` (`tenant_id`,`status`),
  KEY `idx_fulfillment_exception_order` (`tenant_id`,`order_id`),
  KEY `idx_fulfillment_exception_wave` (`tenant_id`,`wave_id`),
  KEY `idx_fulfillment_exception_task` (`tenant_id`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='履约异常';

-- ============================================================================
-- 七、营销快照
-- ============================================================================
CREATE TABLE IF NOT EXISTS `promotion_snapshot` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `order_id` varchar(32) NOT NULL COMMENT '订单ID',
  `order_item_id` varchar(32) DEFAULT NULL COMMENT '订单明细ID（整单优惠为空）',
  `activity_id` varchar(32) NOT NULL COMMENT '活动ID',
  `activity_type` char(2) NOT NULL COMMENT '活动类型：1优惠券 2满减 3折扣 4阶梯价 5买赠 6首单/复购 7船供整船优惠',
  `promotion_name` varchar(128) DEFAULT NULL COMMENT '活动名称快照',
  `rule_snapshot` text DEFAULT NULL COMMENT '活动规则快照（受控JSON）',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额（元）',
  `gift_items` text DEFAULT NULL COMMENT '赠品明细（受控JSON）',
  `applied_item_ids` varchar(1000) DEFAULT NULL COMMENT '适用订单明细ID集合',
  `purchase_scene` char(2) DEFAULT NULL COMMENT '购买场景快照：1海员个人购买 2船供采购',
  `price_before` decimal(10,2) DEFAULT NULL COMMENT '优惠前金额（元）',
  `price_after` decimal(10,2) DEFAULT NULL COMMENT '优惠后金额（元）',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_promotion_snapshot_order` (`tenant_id`,`order_id`),
  KEY `idx_promotion_snapshot_activity` (`tenant_id`,`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单营销快照';

-- ============================================================================
-- 八、配送方式字典值（delivery_way=4 公司港口/船舶内部配送，写入 aryn_upms）
-- ============================================================================
USE `aryn_upms`;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`)
SELECT '2103000000000000004', d.`id`, '公司港口/船舶内部配送', '4', 'delivery_way', '0', '公司司机按靠港计划送到港口或船舶', 4, '0', NOW(), 'system'
FROM `sys_dict` d WHERE d.`type` = 'delivery_way' LIMIT 1;

SET FOREIGN_KEY_CHECKS = 1;
