-- ============================================================================
-- 货到付款收款提醒配置增量
-- 背景：货到付款（COD）订单允许未收款先确认收货，收货后长期未确认收款需要预警，
--       向买家与租户管理员发站内信催收。
-- 内容：order_config 补一列：
--       cod_pay_remind_hours  收款提醒时间点（收货后小时数，逗号分隔升序，如 72,168；
--                             空串关闭提醒）。NOT NULL DEFAULT '72,168'，存量配置与
--                             新建配置默认按收货后 72/168 小时两轮提醒。
-- 特性：information_schema 守卫，可重复执行；不修改、不删除存量数据。
-- 消费方：CodPayRemindJobHandler（XXL-JOB，注册见 112cod_pay_remind_job.sql），
--         逐租户读取 order_config 后扫描 order_info（payment_type=3 且 pay_status=0
--         且已完成）并发送站内信。
-- 注意：cloud 模式 order 服务数据源为 aryn_order 库（Nacos aryn-order-biz-dev.yml），
--       与 db/boot/110cod_pay_remind_config_incremental.sql 内容一致，仅库名不同。
-- 执行：mysql -u root -p aryn_order < 111cod_pay_remind_config_incremental.sql
-- ============================================================================

USE `aryn_order`;

SET NAMES utf8mb4;

-- ============ order_config.cod_pay_remind_hours ============
SET @add_cod_pay_remind_hours = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_config` ADD COLUMN `cod_pay_remind_hours` varchar(100) NOT NULL DEFAULT ''72,168'' COMMENT ''货到付款收款提醒时间点（收货后小时数，逗号分隔升序，如 72,168；空串关闭提醒）'' AFTER `order_auto_comment_days`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_config' AND COLUMN_NAME = 'cod_pay_remind_hours'
);
PREPARE stmt FROM @add_cod_pay_remind_hours; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ 自检：应返回 1 ============
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_config'
       AND COLUMN_NAME = 'cod_pay_remind_hours') AS added_columns;

SET FOREIGN_KEY_CHECKS = 1;
