-- 悦航购靠港信息由海员申报增量迁移（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 租户白名单：本脚本仅对已有表 vessel_call 增加列与索引，不新增表，无需改动 hx.tenant.tables。
-- 特性：幂等执行，不删除或重建数据；不修改既有列类型，不覆盖业务数据。
--
-- 背景（业务事实，2026-09-20 确认）：
--   公司无法与船舶公司对接船期，ETA/ETD 只有船上的人知道。
--   此前 vessel_call 完全由运营在管理端录入，等于让运营编造船期。
--   正确分工：
--     · 港口/泊位/ETA/ETD  —— 海员在下单时申报（客观事实，他才知道）
--     · 配送时间窗/波次/司机 —— 运营收到申报后排产决策（公司能力）
--   因此新增 source 区分来源，并保留运营事后修正（走既有变更日志与站内信通知）。
--
-- 执行：mysql -u root -p aryn_boot < 67vessel_call_declare_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 1. 来源标记
--    存量数据均为运营维护，默认 '1'；海员申报写入 '2'。
-- ===========================================================================
SET @add_vessel_call_source = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vessel_call` ADD COLUMN `source` char(2) NOT NULL DEFAULT ''1'' COMMENT ''来源：1运营维护 2海员申报'' AFTER `status`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vessel_call' AND COLUMN_NAME = 'source'
);
PREPARE stmt FROM @add_vessel_call_source; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 2. 申报人（审计与后续沟通用；海员申报时写入）
-- ===========================================================================
SET @add_vessel_call_declared_by = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vessel_call` ADD COLUMN `declared_by` varchar(32) DEFAULT NULL COMMENT ''申报人商城用户ID（海员申报时写入）'' AFTER `source`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vessel_call' AND COLUMN_NAME = 'declared_by'
);
PREPARE stmt FROM @add_vessel_call_declared_by; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 3. 待处理申报索引（运营按来源筛选待排产的靠港）
-- ===========================================================================
SET @add_vessel_call_source_idx = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vessel_call` ADD INDEX `idx_vessel_call_source` (`tenant_id`, `source`, `status`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vessel_call' AND INDEX_NAME = 'idx_vessel_call_source'
);
PREPARE stmt FROM @add_vessel_call_source_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- 自检
SELECT `COLUMN_NAME`, `COLUMN_TYPE`, `COLUMN_DEFAULT`, `COLUMN_COMMENT`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vessel_call'
  AND `COLUMN_NAME` IN ('source', 'declared_by')
ORDER BY `COLUMN_NAME`;
