-- 管理端概览统计索引（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：概览页的交易、趋势、用户、商品、退款与好评统计均按 tenant_id + 时间范围过滤，
--       原表只有主键/业务唯一键，时间字段缺少可用索引。本脚本补齐统计查询索引。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 133dashboard_statistics_index_incremental.sql

USE `aryn_boot`;

SET NAMES utf8mb4;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_info'
    AND index_name = 'idx_order_stats_pay_time'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `order_info` ADD INDEX `idx_order_stats_pay_time` (`tenant_id`, `del_flag`, `pay_status`, `payment_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_info'
    AND index_name = 'idx_order_stats_status_create'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `order_info` ADD INDEX `idx_order_stats_status_create` (`tenant_id`, `del_flag`, `status`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_info'
    AND index_name = 'idx_order_stats_user_pay'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `order_info` ADD INDEX `idx_order_stats_user_pay` (`tenant_id`, `user_id`, `pay_status`, `payment_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_item'
    AND index_name = 'idx_order_item_order'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `order_item` ADD INDEX `idx_order_item_order` (`tenant_id`, `order_id`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_item'
    AND index_name = 'idx_order_item_spu'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `order_item` ADD INDEX `idx_order_item_spu` (`tenant_id`, `spu_id`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_refund'
    AND index_name = 'idx_order_refund_create'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `order_refund` ADD INDEX `idx_order_refund_create` (`tenant_id`, `del_flag`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_refund'
    AND index_name = 'idx_order_refund_arrival_update'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `order_refund` ADD INDEX `idx_order_refund_arrival_update` (`tenant_id`, `del_flag`, `arrival_status`, `update_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_refund'
    AND index_name = 'idx_order_refund_item_arrival'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `order_refund` ADD INDEX `idx_order_refund_item_arrival` (`tenant_id`, `order_item_id`, `del_flag`, `arrival_status`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'goods_footprint'
    AND index_name = 'idx_goods_footprint_create'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `goods_footprint` ADD INDEX `idx_goods_footprint_create` (`tenant_id`, `del_flag`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'goods_appraise'
    AND index_name = 'idx_goods_appraise_create'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `goods_appraise` ADD INDEX `idx_goods_appraise_create` (`tenant_id`, `del_flag`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'goods_spu'
    AND index_name = 'idx_goods_spu_status'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `goods_spu` ADD INDEX `idx_goods_spu_status` (`tenant_id`, `del_flag`, `status`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'goods_spu'
    AND index_name = 'idx_goods_spu_stock'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `goods_spu` ADD INDEX `idx_goods_spu_stock` (`tenant_id`, `del_flag`, `stock`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'goods_spu'
    AND index_name = 'idx_goods_spu_sales'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `goods_spu` ADD INDEX `idx_goods_spu_sales` (`tenant_id`, `del_flag`, `sales_volume`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'user_info'
    AND index_name = 'idx_user_info_create'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `user_info` ADD INDEX `idx_user_info_create` (`tenant_id`, `del_flag`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
