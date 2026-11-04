-- 专区批量打标：批次记录/撤销 + 专区同步状态字段（Cloud 微服务模式）
--
-- 目标库：
--   1. aryn_product：goods_zone_tag_batch / goods_zone_tag_batch_item（批次头 + 逐商品快照）
--   2. aryn_promotion：mall_zone_setting 增加 last_sync_time / last_sync_status / last_sync_error
-- 与 boot/198zone_tag_batch_incremental.sql 内容一致，按微服务拆分归属。
-- 特性：可重复执行（CREATE TABLE IF NOT EXISTS + information_schema 判存），不 DROP/TRUNCATE 存量数据。
--
-- 执行：mysql -u root -p < 200zone_tag_batch_incremental.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 一、aryn_product：专区标签批量操作批次与明细
-- ===========================================================================
USE `aryn_product`;

CREATE TABLE IF NOT EXISTS `goods_zone_tag_batch` (
  `id` varchar(32) NOT NULL COMMENT '主键（雪花ID，即 batchId）',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `batch_no` varchar(64) NULL DEFAULT NULL COMMENT '批次号（展示用）',
  `operator_id` varchar(32) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) NULL DEFAULT NULL COMMENT '操作人姓名快照',
  `member_action` varchar(16) NULL DEFAULT NULL COMMENT '会员专区动作：ADD/REMOVE/NONE',
  `clearance_action` varchar(16) NULL DEFAULT NULL COMMENT '低价捡漏动作：ADD/REMOVE/NONE',
  `member_rule` text NULL COMMENT '会员规则快照 JSON',
  `clearance_rule` text NULL COMMENT '出清规则快照 JSON',
  `overwrite_sku_rules` tinyint(1) NULL DEFAULT NULL COMMENT '是否清除 SKU 精调：1是 0否',
  `total` int NOT NULL DEFAULT 0 COMMENT '请求商品数',
  `changed` int NOT NULL DEFAULT 0 COMMENT '实际变化数',
  `added` int NOT NULL DEFAULT 0 COMMENT '新增数',
  `updated` int NOT NULL DEFAULT 0 COMMENT '更新数',
  `removed` int NOT NULL DEFAULT 0 COMMENT '移出数',
  `no_change` int NOT NULL DEFAULT 0 COMMENT '无变化数',
  `failed` int NOT NULL DEFAULT 0 COMMENT '失败数',
  `sync_status` varchar(16) NOT NULL DEFAULT 'SKIPPED' COMMENT '同步状态：SUCCESS/FAILED/SKIPPED',
  `sync_message` varchar(500) NULL DEFAULT NULL COMMENT '同步失败原因',
  `undo_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '撤销状态：NONE/UNDONE',
  `undo_time` datetime NULL DEFAULT NULL COMMENT '撤销时间',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_zone_tag_batch_tenant` (`tenant_id`, `create_time`),
  KEY `idx_zone_tag_batch_no` (`tenant_id`, `batch_no`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '专区标签批量操作批次' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `goods_zone_tag_batch_item` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `batch_id` varchar(32) NOT NULL COMMENT '批次ID',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `spu_name` varchar(255) NULL DEFAULT NULL COMMENT '商品名称快照',
  `change_type` varchar(16) NULL DEFAULT NULL COMMENT '变更类型：ADD/UPDATE/REMOVE/NO_CHANGE/INVALID/FAILED',
  `success` tinyint(1) NULL DEFAULT NULL COMMENT '是否成功：1是 0否',
  `error_code` varchar(64) NULL DEFAULT NULL COMMENT '错误码',
  `message` varchar(500) NULL DEFAULT NULL COMMENT '结果说明',
  `before_snapshot` mediumtext NULL COMMENT '变更前快照 JSON（SPU 专区字段 + SKU 覆盖原值）',
  `after_snapshot` mediumtext NULL COMMENT '变更后快照 JSON（SPU 专区字段 + SKU 覆盖原值）',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_zone_tag_batch_item_batch` (`tenant_id`, `batch_id`),
  KEY `idx_zone_tag_batch_item_spu` (`tenant_id`, `spu_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '专区标签批量操作批次明细' ROW_FORMAT = DYNAMIC;

-- ===========================================================================
-- 二、aryn_promotion：专区设置上次同步结果（G19）
-- ===========================================================================
USE `aryn_promotion`;

SET @last_sync_time := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_zone_setting' AND COLUMN_NAME = 'last_sync_time'
);
SET @ddl := IF(@last_sync_time = 0,
  'ALTER TABLE `mall_zone_setting` ADD COLUMN `last_sync_time` datetime NULL DEFAULT NULL COMMENT ''上次系统活动同步时间'' AFTER `sort_mode`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @last_sync_status := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_zone_setting' AND COLUMN_NAME = 'last_sync_status'
);
SET @ddl := IF(@last_sync_status = 0,
  'ALTER TABLE `mall_zone_setting` ADD COLUMN `last_sync_status` varchar(16) NULL DEFAULT NULL COMMENT ''上次同步结果：SUCCESS/FAILED/UNKNOWN'' AFTER `last_sync_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @last_sync_error := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_zone_setting' AND COLUMN_NAME = 'last_sync_error'
);
SET @ddl := IF(@last_sync_error = 0,
  'ALTER TABLE `mall_zone_setting` ADD COLUMN `last_sync_error` varchar(500) NULL DEFAULT NULL COMMENT ''上次同步失败原因'' AFTER `last_sync_status`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：product 两张表 + promotion 三列
USE `aryn_product`;
SELECT COUNT(*) AS zone_tag_batch_tables
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('goods_zone_tag_batch', 'goods_zone_tag_batch_item');

USE `aryn_promotion`;
SELECT COUNT(*) AS mall_zone_setting_sync_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_zone_setting'
  AND COLUMN_NAME IN ('last_sync_time', 'last_sync_status', 'last_sync_error');

SET FOREIGN_KEY_CHECKS = 1;
