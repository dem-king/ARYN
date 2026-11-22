USE `aryn_boot`;
-- 稳定合送组、订单关系及本地操作outbox。不会改动订单、支付、签收或存量数据。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `delivery_group` (
  `id` varchar(64) NOT NULL,
  `warehouse_id` varchar(64) NOT NULL,
  `vessel_id` varchar(64) NOT NULL,
  `vessel_call_id` varchar(64) NOT NULL,
  `port_code` varchar(64) NOT NULL,
  `window_sequence` int NOT NULL,
  `window_start` datetime(6) DEFAULT NULL,
  `window_end` datetime(6) DEFAULT NULL,
  `cutoff` datetime(6) DEFAULT NULL,
  `max_orders` int NOT NULL,
  `status` varchar(32) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_window` (`tenant_id`,`warehouse_id`,`vessel_call_id`,`window_sequence`),
  KEY `idx_group_cutoff` (`tenant_id`,`status`,`cutoff`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `delivery_group_order_link` (
  `id` varchar(64) NOT NULL,
  `group_id` varchar(64) NOT NULL,
  `order_id` varchar(64) NOT NULL,
  `warehouse_id` varchar(64) NOT NULL,
  `wave_id` varchar(64) DEFAULT NULL,
  `task_id` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_group` (`tenant_id`,`order_id`),
  KEY `idx_group_active` (`tenant_id`,`group_id`,`status`),
  KEY `idx_group_wave` (`tenant_id`,`wave_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `delivery_group_operation` (
  `id` varchar(64) NOT NULL,
  `group_id` varchar(64) NOT NULL,
  `request_id` varchar(120) NOT NULL,
  `fingerprint` varchar(64) NOT NULL,
  `action` varchar(32) NOT NULL,
  `actor_id` varchar(64) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `request_json` longtext NOT NULL,
  `result_json` longtext NOT NULL,
  `notification_json` longtext NOT NULL,
  `outbox_status` varchar(32) NOT NULL,
  `attempts` int NOT NULL DEFAULT 0,
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `lease_token` varchar(64) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_request` (`tenant_id`,`request_id`),
  KEY `idx_group_outbox` (`tenant_id`,`outbox_status`,`next_attempt_at`,`lease_until`),
  KEY `idx_group_history` (`tenant_id`,`group_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 原订单没有仓库字段；归组/入波次时显式核定，旧记录保持NULL待核定。
SET @delivery_group_column=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='order_info' AND COLUMN_NAME='fulfillment_warehouse_id');
SET @delivery_group_ddl=IF(@delivery_group_column=0, 'ALTER TABLE order_info ADD COLUMN fulfillment_warehouse_id varchar(64) DEFAULT NULL COMMENT ''显式核定履约仓库''', 'SELECT 1');
PREPARE delivery_group_stmt FROM @delivery_group_ddl; EXECUTE delivery_group_stmt; DEALLOCATE PREPARE delivery_group_stmt;
SET @delivery_group_column=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='delivery_task' AND COLUMN_NAME='warehouse_id');
SET @delivery_group_ddl=IF(@delivery_group_column=0, 'ALTER TABLE delivery_task ADD COLUMN warehouse_id varchar(64) DEFAULT NULL COMMENT ''显式核定履约仓库''', 'SELECT 1');
PREPARE delivery_group_stmt FROM @delivery_group_ddl; EXECUTE delivery_group_stmt; DEALLOCATE PREPARE delivery_group_stmt;
SET FOREIGN_KEY_CHECKS = 1;
