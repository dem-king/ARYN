-- 配送员商城账号绑定表结构升级增量迁移（Cloud 微服务模式）
-- 目标库：aryn_order
-- 背景：29delivery_account_binding.sql 使用 CREATE TABLE IF NOT EXISTS，
--       对已存在旧版 delivery_account_binding（缺少 active_sys_user_id 生成列
--       或唯一索引）的环境不会补齐缺失结构，约束并未生效。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 前置检查（步骤 1）如返回重复有效绑定，必须先由管理员确认保留记录、
--       通过业务解绑/逻辑删除处理另一条后再执行步骤 2-4，禁止脚本自动删除。
-- 执行：mysql -u root -p < 35delivery_account_binding_schema_upgrade.sql

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 重复有效绑定检查（应返回空结果集；如有返回，先人工处理再继续）
SELECT tenant_id, sys_user_id, COUNT(*) AS cnt
FROM delivery_account_binding
WHERE status = '1' AND del_flag = '0'
GROUP BY tenant_id, sys_user_id
HAVING COUNT(*) > 1;

-- 2) 幂等补齐有效绑定员工账号生成列
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_account_binding'
      AND COLUMN_NAME = 'active_sys_user_id') = 0,
  'ALTER TABLE `delivery_account_binding` ADD COLUMN `active_sys_user_id` varchar(32) GENERATED ALWAYS AS (CASE WHEN `status` = ''1'' AND `del_flag` = ''0'' THEN `sys_user_id` ELSE NULL END) STORED COMMENT ''有效绑定员工账号（生成列，供唯一键使用）''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 幂等补齐同一员工账号最多一个有效绑定的唯一键
SET @ddl = IF(
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_account_binding'
      AND INDEX_NAME = 'uk_delivery_binding_active_sys_user') = 0,
  'ALTER TABLE `delivery_account_binding` ADD UNIQUE KEY `uk_delivery_binding_active_sys_user` (`tenant_id`, `active_sys_user_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 幂等补齐辅助索引（旧版本表可能缺失）
SET @ddl = IF(
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_account_binding'
      AND INDEX_NAME = 'idx_delivery_binding_sys_user') = 0,
  'ALTER TABLE `delivery_account_binding` ADD KEY `idx_delivery_binding_sys_user` (`tenant_id`, `sys_user_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_account_binding'
      AND INDEX_NAME = 'idx_delivery_binding_staff') = 0,
  'ALTER TABLE `delivery_account_binding` ADD KEY `idx_delivery_binding_staff` (`tenant_id`, `delivery_staff_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
