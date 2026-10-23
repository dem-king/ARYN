-- 悦航购船供活动预算原子消耗（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：预算额度由「reserve 时聚合存量锁」改为活动行原子计数器，避免并发下单超卖；
--       额度不足时活动不参与试算，订单按原价继续成交，不再整单拦截。
-- 特性：可重复执行，不执行 DROP/TRUNCATE；consumed_budget 仅在首次加列时按有效锁定单回填，
--       重跑不会覆盖线上已累计的消耗值（如需对账可手工执行回填 SQL）。
-- 关联：cloud 模式同名增量 145promotion_activity_consumed_budget.sql。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @needs_budget_backfill = (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND COLUMN_NAME = 'consumed_budget'
);

SET @add_consumed_budget = IF(@needs_budget_backfill,
  'ALTER TABLE `promotion_activity` ADD COLUMN `consumed_budget` decimal(12,2) NOT NULL DEFAULT ''0.00'' COMMENT ''已占用让利金额（元）：LOCKED+CONFIRMED 锁定汇总，reserve 原子累加、release 归还'' AFTER `total_budget`',
  'SELECT 1');
PREPARE stmt FROM @add_consumed_budget; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 首次加列时按有效锁定单回填；后续重跑跳过，避免覆盖并发下单的最新累计值
SET @backfill_consumed_budget = IF(@needs_budget_backfill,
  'UPDATE `promotion_activity` a
   LEFT JOIN (
     SELECT `tenant_id`, `activity_id`, SUM(COALESCE(`discount_amount`, 0)) AS used
     FROM `promotion_lock`
     WHERE `status` IN (''1'', ''2'') AND `del_flag` = ''0''
     GROUP BY `tenant_id`, `activity_id`
   ) l ON l.`tenant_id` = a.`tenant_id` AND l.`activity_id` = a.`id`
   SET a.`consumed_budget` = COALESCE(l.used, 0)',
  'SELECT 1');
PREPARE stmt FROM @backfill_consumed_budget; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 预算语义已从「超限整单拦截」调整为「额度不足活动不参与」
ALTER TABLE `promotion_activity`
  MODIFY COLUMN `total_budget` decimal(12,2) NOT NULL DEFAULT '0.00'
  COMMENT '预算金额（元），0=不限；额度不足时活动不参与试算，订单按原价成交';

SET FOREIGN_KEY_CHECKS = 1;
