-- ============================================================================
-- 支付宝小程序身份模式（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：2026-11-06 支付方案 P2 支付宝小程序。
--   1) social_account 增加 identity_mode：ALI_MINI 必须显式配置 user_id/open_id；
--   2) social_user 增加 platform_type/identity_mode：区分微信 open_id 与支付宝身份，
--      防止把微信 openid 当作支付宝买家；
--   3) pay_config_revision 增加 buyer_identity_mode：支付身份版本冻结买家字段模式。
--
-- 幂等：列/索引先查 information_schema；不删除任何资金/身份记录。
-- 回填：历史 social_user 全部来自微信小程序，回填 platform_type=WX_MA、identity_mode=open_id。
-- 执行：mysql -u root -p aryn_boot < 212alipay_mini_identity_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 一、social_account：三方账号身份模式
-- ----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'social_account' AND column_name = 'identity_mode');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE social_account ADD COLUMN `identity_mode` varchar(16) NULL DEFAULT NULL COMMENT ''身份模式：ALI_MINI 为 user_id/open_id'' AFTER app_secret',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 二、social_user：平台类型与身份模式
-- ----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'social_user' AND column_name = 'platform_type');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE social_user ADD COLUMN `platform_type` varchar(16) NULL DEFAULT NULL COMMENT ''平台类型：WX_MA / ALI_MINI'' AFTER social_account_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'social_user' AND column_name = 'identity_mode');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE social_user ADD COLUMN `identity_mode` varchar(16) NULL DEFAULT NULL COMMENT ''身份模式：WX_MA=open_id；ALI_MINI=user_id/open_id'' AFTER platform_type',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE social_user
SET platform_type = 'WX_MA'
WHERE (platform_type IS NULL OR platform_type = '')
  AND open_id IS NOT NULL
  AND open_id <> '';

UPDATE social_user
SET identity_mode = 'open_id'
WHERE (identity_mode IS NULL OR identity_mode = '')
  AND platform_type = 'WX_MA';

-- 支付宝 buyer_open_id 官方上限 128，原 open_id 列宽 100 不足；仅放宽不缩窄。
SET @col_len := (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'social_user' AND column_name = 'open_id');
SET @ddl := IF(@col_len < 128,
  'ALTER TABLE social_user MODIFY COLUMN `open_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''openid/支付宝买家身份''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_len := (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'user_info' AND column_name = 'open_id');
SET @ddl := IF(@col_len < 128,
  'ALTER TABLE user_info MODIFY COLUMN `open_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''平台用户唯一ID（openid / alipay userId 等）''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'social_user' AND index_name = 'idx_social_user_platform_user');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE social_user ADD INDEX `idx_social_user_platform_user` (`tenant_id`, `platform_type`, `mall_user_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'social_user' AND index_name = 'idx_social_user_app_identity');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE social_user ADD INDEX `idx_social_user_app_identity` (`tenant_id`, `app_id`, `identity_mode`, `open_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 三、pay_config_revision：支付宝小程序买家身份模式
-- ----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'pay_config_revision' AND column_name = 'buyer_identity_mode');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pay_config_revision ADD COLUMN `buyer_identity_mode` varchar(16) NULL DEFAULT NULL COMMENT ''支付宝小程序买家身份模式：user_id/open_id'' AFTER wx_public_key_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
