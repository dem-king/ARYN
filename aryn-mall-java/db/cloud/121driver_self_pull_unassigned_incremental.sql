-- ============================================================================
-- 司机自助拉单开关增量
-- 背景：司机配货页的「加单」允许自己把未派送订单拉上本趟车。该能力会让司机
--       越过管理端派单（先到先得），不同租户的调度纪律不一致，需按租户可控。
-- 内容：order_config 补一列：
--       driver_self_pull_unassigned  1=允许（默认，含存量配置）0=仅可拉管理端
--                                    已派给自己的任务，未派送单必须由管理端派单。
-- 特性：information_schema 守卫，可重复执行；不修改、不删除存量数据。
-- 消费方：DeliveryTaskServiceImpl#isDriverSelfPullUnassignedAllowed（拉单候选查询
--         与 pullOrdersIntoTrip 写入前逐单校验都会读它），配送员端出车单详情
--         下发 selfPullUnassignedAllowed 供界面隐藏入口。
-- 执行：mysql -u root -p aryn_order < 120driver_self_pull_unassigned_incremental.sql
-- ============================================================================

USE `aryn_order`;

SET NAMES utf8mb4;

-- ============ order_config.driver_self_pull_unassigned ============
SET @add_driver_self_pull = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_config` ADD COLUMN `driver_self_pull_unassigned` char(1) NOT NULL DEFAULT ''1'' COMMENT ''司机可否自助拉未派送订单：1允许 0仅可拉管理端已派给自己的任务'' AFTER `cod_pay_remind_hours`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_config' AND COLUMN_NAME = 'driver_self_pull_unassigned'
);
PREPARE stmt FROM @add_driver_self_pull; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ 自检：应返回 1 ============
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_config'
       AND COLUMN_NAME = 'driver_self_pull_unassigned') AS added_columns;

SET FOREIGN_KEY_CHECKS = 1;
