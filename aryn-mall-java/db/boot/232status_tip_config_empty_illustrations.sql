-- ============================================================================
-- 空态插画配置 — 增加购物车 / 订单 / 秒杀三张会场插画（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-13）：
--   空态配置表首版只覆盖 wd-status-tip 的 7 个预设位，购物车、订单列表、秒杀会场
--   仍直接引用随包 PNG（/static/promotion/empty/*.png）。这三处同样是运营需要换图
--   的空态，现统一收进 sys_status_tip_config：后台配了就用配置 URL，未配置仍回落
--   随包内置图，不需要重新发版。
--
-- 特性：可重复执行；只加列，不 DROP/TRUNCATE，不覆盖任何存量配置。
-- 与 cloud 版一致性：本脚本与 db/cloud/247status_tip_config_empty_illustrations.sql
--   内容一致，仅目标库不同。
-- 执行：mysql -u root -p aryn_boot < 232status_tip_config_empty_illustrations.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、sys_status_tip_config 增加 3 个空态插画列
--     empty_cart_url    购物车空态
--     empty_order_url   订单列表空态
--     empty_seckill_url 秒杀会场空态
-- ---------------------------------------------------------------------------
SET @add_empty_cart_url = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_status_tip_config` ADD COLUMN `empty_cart_url` varchar(500) DEFAULT NULL COMMENT ''购物车空态图 URL'' AFTER `message_url`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_status_tip_config'
    AND COLUMN_NAME = 'empty_cart_url'
);
PREPARE stmt FROM @add_empty_cart_url; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_empty_order_url = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_status_tip_config` ADD COLUMN `empty_order_url` varchar(500) DEFAULT NULL COMMENT ''订单列表空态图 URL'' AFTER `empty_cart_url`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_status_tip_config'
    AND COLUMN_NAME = 'empty_order_url'
);
PREPARE stmt FROM @add_empty_order_url; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_empty_seckill_url = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_status_tip_config` ADD COLUMN `empty_seckill_url` varchar(500) DEFAULT NULL COMMENT ''秒杀会场空态图 URL'' AFTER `empty_order_url`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_status_tip_config'
    AND COLUMN_NAME = 'empty_seckill_url'
);
PREPARE stmt FROM @add_empty_seckill_url; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 二、自检：3 个列都存在（期望 added_columns=3）
-- ---------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_status_tip_config'
      AND COLUMN_NAME IN ('empty_cart_url', 'empty_order_url', 'empty_seckill_url')
  ) AS added_columns;

SET FOREIGN_KEY_CHECKS = 1;
