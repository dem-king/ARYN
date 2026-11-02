-- 租户类型与「长期有效」授权（Boot 单体模式）
-- 目标库：aryn_boot
--
-- 背景：平台租户（系统租户 1881232176465358849）此前靠前端按固定 ID 显示「长期有效」，
--       数据库里的 auth_end_time 实际早已过期，导出、报表、巡检口径与界面不一致；
--       租户过期任务与管理端按钮也各自用 ID 常量判断平台身份，新增入口容易漏判。
-- 本次引入 sys_tenant.tenant_type：
--   tenant_type=1 平台租户：不参与授权期管理，auth_end_time 为 NULL 表示长期有效；
--   tenant_type=2 普通租户：保持现有授权开始/结束时间管控。
--
-- 特性：幂等；不删除、不覆盖业务数据；无 DROP/TRUNCATE。
-- 执行：mysql -u root -p aryn_boot < 170tenant_type_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、租户类型列（默认普通租户，存量行自动按普通租户处理）
-- ============================================================================
SET @add_tenant_type = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_tenant` ADD COLUMN `tenant_type` char(2) NOT NULL DEFAULT ''2'' COMMENT ''租户类型：1平台租户（长期有效） 2普通租户'' AFTER `business_mode`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tenant' AND COLUMN_NAME = 'tenant_type'
);
PREPARE stmt FROM @add_tenant_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 二、授权结束时间允许为空：NULL = 长期有效
--     仅当仍为 NOT NULL 时执行 MODIFY；存量普通租户已有值不受影响。
-- ============================================================================
SET @modify_auth_end_nullable = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `sys_tenant` MODIFY COLUMN `auth_end_time` datetime NULL COMMENT ''授权结束时间；NULL 表示长期有效''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tenant' AND COLUMN_NAME = 'auth_end_time' AND IS_NULLABLE = 'NO'
);
PREPARE stmt FROM @modify_auth_end_nullable; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 三、平台租户回填：类型置 1，授权结束时间置空
--     只动平台租户这一行；普通租户的类型与授权时间保持原值。
-- ============================================================================
UPDATE `sys_tenant` SET `tenant_type` = '1' WHERE `id` = '1881232176465358849';
UPDATE `sys_tenant` SET `auth_end_time` = NULL WHERE `id` = '1881232176465358849';

SET FOREIGN_KEY_CHECKS = 1;
