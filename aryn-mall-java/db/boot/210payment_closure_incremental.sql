-- ============================================================================
-- 支付交易闭环（Boot 单体模式）：生命周期、事件 outbox
--
-- 目标库：aryn_boot
-- 背景：2026-11-06 支付方案 P1 任务 7。
--   1) pay_trade_order 增加 trade_status 生命周期；旧 pay_status 仅做 0/1 兼容投影；
--   2) pay_event_outbox 与支付/退款落账同事务写入，提交后异步重投；
--   3) 存量已支付单回填 PAID，其余回填 PENDING，由取消门禁查单收敛。
--
-- 幂等：列/索引先查 information_schema；新表 IF NOT EXISTS；不删除任何资金记录。
-- 执行：mysql -u root -p aryn_boot < 210payment_closure_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'pay_trade_order' AND column_name = 'trade_status');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pay_trade_order ADD COLUMN `trade_status` varchar(16) NOT NULL DEFAULT ''PENDING'' COMMENT ''生命周期：SUBMITTING/PENDING/CLOSING/UNKNOWN/CLOSED/PAID'' AFTER config_revision_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'pay_trade_order' AND column_name = 'prepay_time');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pay_trade_order ADD COLUMN `prepay_time` datetime NULL DEFAULT NULL COMMENT ''预支付提交时间'' AFTER trade_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'pay_trade_order' AND column_name = 'close_time');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pay_trade_order ADD COLUMN `close_time` datetime NULL DEFAULT NULL COMMENT ''渠道关单时间'' AFTER prepay_time',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'pay_trade_order' AND index_name = 'idx_pay_trade_order_status');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE pay_trade_order ADD INDEX `idx_pay_trade_order_status` (`tenant_id`, `trade_status`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量状态回填：已支付必须为 PAID；其余保守标记 PENDING，由查单/关单门禁收敛。
UPDATE pay_trade_order
SET trade_status = 'PAID'
WHERE pay_status = '1' AND trade_status <> 'PAID' AND del_flag = '0';

UPDATE pay_trade_order
SET trade_status = 'PENDING'
WHERE pay_status <> '1' AND (trade_status IS NULL OR trade_status = '') AND del_flag = '0';

CREATE TABLE IF NOT EXISTS `pay_event_outbox` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
  `event_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '事件唯一ID',
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'PAY_SUCCESS/REFUND_SUCCESS',
  `business_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单号/退款单号',
  `topic` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '投递 Topic',
  `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '非敏感消息负载 JSON',
  `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '0.待投递；1.已投递；2.死信；3.投递中',
  `attempts` int NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `next_retry_time` datetime NOT NULL COMMENT '下次重试时间',
  `last_error` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最近失败原因',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_pay_event_outbox_event` (`event_id`) USING BTREE,
  KEY `idx_pay_event_outbox_due` (`status`, `next_retry_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付事件 outbox' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
