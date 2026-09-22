-- 补给单计划量与执行状态（Cloud 微服务模式）
--
-- 目标库：aryn_order（订单库）
-- 特性：可重复执行；不 DROP/TRUNCATE；不覆盖已有业务数据；仅加列与建索引。
--
-- 背景（2026-09-22 · B 版「船供清单驱动」原型）：
--   原型首页与补给单详情要展示「12 项待采 · 已采 8 项 · 合计 ¥1,286」
--   以及每行的「目标 4 · 已采 4」。而现有 shared_cart_item 只有
--   requested_quantity（成员申请量）与 approved_quantity（确认人核定数量），
--   且 approved_quantity 只在「确认人提交整船订单」那一次事务里写入
--   （SharedCartServiceImpl#applyApprovedQuantities）——收集阶段恒为 NULL，
--   因此**算不出「已采」**。
--
--   本脚本补的正是这个缺口：把「计划采购量」与「实际采购/执行状态」拆成
--   独立字段，使收集阶段也能表达进度。
--
-- 语义约定（务必与代码注释保持一致）：
--   planned_quantity  计划量（采购单位）。NULL = 未设计划，此时不做进度计算，
--                     **不得**回落成 requested_quantity 假造进度。
--   fulfilled_quantity 已采量（采购单位）。默认 0。
--   进度口径统一为：还差 = GREATEST(planned_quantity - fulfilled_quantity, 0)；
--   已采满的条件是 fulfilled_quantity >= planned_quantity。
--
--   注意与既有字段的分工，避免出现第三套数量口径：
--   requested_quantity  成员报的需求量（报多报少由确认人核定）
--   approved_quantity   确认人在提交整船订单时的核定数量（仅提交瞬间写入）
--   planned_quantity    本次计划采购量（可先设后采，收集期间可反复调）
--   fulfilled_quantity  已采量（运营/采购回填）
--
-- 执行：mysql -u root -p aryn_order < 77shared_cart_item_planned_qty.sql

USE `aryn_order`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、shared_cart_item 加计划量与已采量
-- ---------------------------------------------------------------------------
SET @add_planned_quantity = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart_item` ADD COLUMN `planned_quantity` int DEFAULT NULL COMMENT ''计划采购量（采购单位）；NULL 表示未设计划，不做进度计算'' AFTER `approved_quantity`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_item' AND COLUMN_NAME = 'planned_quantity'
);
PREPARE stmt FROM @add_planned_quantity; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_fulfilled_quantity = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart_item` ADD COLUMN `fulfilled_quantity` int NOT NULL DEFAULT 0 COMMENT ''已采量（采购单位）'' AFTER `planned_quantity`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_item' AND COLUMN_NAME = 'fulfilled_quantity'
);
PREPARE stmt FROM @add_fulfilled_quantity; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 二、按购物车统计进度时需要走 (cart_id, status)，补一个覆盖索引
--    （同表已有 idx_shared_cart_item_cart(tenant_id, cart_id)，
--     这里补上 status 便于"只看未采满项"的查询）
-- ---------------------------------------------------------------------------
SET @add_idx_pending = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart_item` ADD INDEX `idx_shared_cart_item_progress` (`tenant_id`, `cart_id`, `status`, `planned_quantity`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_item' AND INDEX_NAME = 'idx_shared_cart_item_progress'
);
PREPARE stmt FROM @add_idx_pending; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 三、自检：确认两个列都存在，且存量行 fulfilled_quantity 默认 0
-- ---------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_item'
       AND COLUMN_NAME IN ('planned_quantity', 'fulfilled_quantity')) AS added_columns,
  (SELECT COUNT(*) FROM `shared_cart_item` WHERE `fulfilled_quantity` IS NULL) AS null_fulfilled_rows;

-- 自检口径：added_columns 应为 2；null_fulfilled_rows 应为 0。
-- 存量数据的 planned_quantity 允许为 NULL（视为未设计划），不回填成
-- requested_quantity —— 那会把"成员报的需求量"当成"计划采购量"，是假数据。
