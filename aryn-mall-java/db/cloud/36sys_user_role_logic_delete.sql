-- 用户角色关联（sys_user_role）逻辑删除改造增量迁移（Cloud 微服务模式）
-- 目标库：aryn_upms（UPMS 基线 2aryn_upms.sql 已含 sys_user_role 表及其租户白名单登记）
-- 背景：项目规则要求逻辑删除，sys_user_role 原先仅物理删除；
--       本脚本补齐 del_flag/update_time/update_by 与有效关联唯一约束，
--       配合 SysUserRole @TableLogic 使回收角色改为逻辑删除并可审计。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 前置检查（步骤 1）如返回重复有效关联，必须先人工确认保留一条、
--       处理其余后（建议保留行 del_flag='0'，其余置为 '1'）再执行步骤 4，
--       禁止脚本自动删除。
-- 执行：mysql -u root -p < 36sys_user_role_logic_delete.sql

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 重复有效关联检查（应返回空结果集；如有返回，先人工处理再继续步骤 4）
SELECT tenant_id, user_id, role_id, COUNT(*) AS cnt
FROM sys_user_role
GROUP BY tenant_id, user_id, role_id
HAVING COUNT(*) > 1;

-- 2) 幂等补齐逻辑删除标记
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_role'
      AND COLUMN_NAME = 'del_flag') = 0,
  'ALTER TABLE `sys_user_role` ADD COLUMN `del_flag` char(2) NOT NULL DEFAULT ''0'' COMMENT ''逻辑删除：0.显示；1.隐藏；''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 幂等补齐审计字段
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_role'
      AND COLUMN_NAME = 'update_time') = 0,
  'ALTER TABLE `sys_user_role` ADD COLUMN `update_time` datetime DEFAULT NULL COMMENT ''修改时间''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_role'
      AND COLUMN_NAME = 'update_by') = 0,
  'ALTER TABLE `sys_user_role` ADD COLUMN `update_by` varchar(60) DEFAULT NULL COMMENT ''修改人''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 幂等补齐有效关联生成列与唯一键：
--    active_user_id 仅在 del_flag='0' 时取 user_id，回收行不占用唯一键，
--    数据库层保证同一租户内 (role_id, user_id) 最多一条有效关联。
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_role'
      AND COLUMN_NAME = 'active_user_id') = 0,
  'ALTER TABLE `sys_user_role` ADD COLUMN `active_user_id` varchar(32) GENERATED ALWAYS AS (CASE WHEN `del_flag` = ''0'' THEN `user_id` ELSE NULL END) STORED COMMENT ''有效关联用户ID（生成列，供唯一键使用）''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_role'
      AND INDEX_NAME = 'uk_sys_user_role_active') = 0,
  'ALTER TABLE `sys_user_role` ADD UNIQUE KEY `uk_sys_user_role_active` (`tenant_id`, `role_id`, `active_user_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
