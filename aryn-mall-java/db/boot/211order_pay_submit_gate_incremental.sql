-- ============================================================================
-- 订单支付提交门禁（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：2026-11-06 支付方案 P1 任务 7。
--   「取消订单」与「首次预支付」必须争用同一持久化门禁：
--   - 预支付先短事务写入 pay_submitting=1，再调用渠道；
--   - 取消只允许 pay_submitting=0 的订单推进，避免本地未建支付单时释放库存；
--   - 进程崩溃留下的提交标记超过 2 分钟视为过期，由取消门禁忽略。
--
-- 幂等：列/索引先查 information_schema；不删除任何订单/资金记录。
-- 执行：mysql -u root -p aryn_boot < 211order_pay_submit_gate_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'order_info' AND column_name = 'pay_submitting');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE order_info ADD COLUMN `pay_submitting` char(2) NOT NULL DEFAULT ''0'' COMMENT ''支付提交门禁：0.空闲；1.提交中'' AFTER pay_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'order_info' AND column_name = 'pay_submit_time');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE order_info ADD COLUMN `pay_submit_time` datetime NULL DEFAULT NULL COMMENT ''支付提交时间'' AFTER pay_submitting',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'order_info' AND index_name = 'idx_order_info_pay_submitting');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE order_info ADD INDEX `idx_order_info_pay_submitting` (`tenant_id`, `pay_submitting`, `pay_submit_time`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
