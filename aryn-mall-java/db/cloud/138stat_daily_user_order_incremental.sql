-- 用户日订单汇总表（Cloud 微服务模式）
-- 目标库：aryn_order
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_order < 138stat_daily_user_order_incremental.sql

USE `aryn_order`;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `stat_daily_user_order` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `user_id` varchar(32) NOT NULL COMMENT '用户id',
  `order_count` int NOT NULL DEFAULT 0 COMMENT '当日支付订单数',
  `paid_amount` decimal(12, 2) NOT NULL DEFAULT 0.00 COMMENT '当日支付金额',
  `last_payment_time` datetime NULL DEFAULT NULL COMMENT '当日最后一次支付时间',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_daily_user_order` (`tenant_id`, `stat_date`, `user_id`) USING BTREE,
  KEY `idx_daily_user_order_user` (`tenant_id`, `user_id`, `stat_date`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户日订单汇总' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
