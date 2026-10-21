-- 管理端概览月度目标（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 135dashboard_target_incremental.sql

USE `aryn_boot`;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `dashboard_target` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `target_month` char(7) NOT NULL COMMENT '目标月份 yyyy-MM',
  `gmv_target` decimal(12, 2) NOT NULL DEFAULT 0.00 COMMENT 'GMV 目标',
  `order_target` int NOT NULL DEFAULT 0 COMMENT '支付订单目标',
  `new_user_target` int NOT NULL DEFAULT 0 COMMENT '新增用户目标',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_dashboard_target_tenant_month` (`tenant_id`, `target_month`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '管理端概览月度目标' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
