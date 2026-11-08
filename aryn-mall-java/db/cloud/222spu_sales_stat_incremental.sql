-- SPU 社证统计 T+1 快照（Cloud 微服务模式）
--
-- 目标库：aryn_order
-- 与 boot 214 号脚本同内容（除库上下文）；背景/口径见该脚本头注释。
-- 配套：223 号（aryn_job 注册 spuSalesStatJobHandler）、
--       224 号（aryn_nacos 租户表白名单 hx.tenant.tables）。
--
-- 执行：mysql -u root -p aryn_order < 222spu_sales_stat_incremental.sql

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `stat_daily_spu_sales` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `stat_date` date NOT NULL COMMENT '统计快照日（窗口截至当日，T+1 写昨天）',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `buyer_cnt` int NOT NULL DEFAULT 0 COMMENT '近30天有效订单去重买家数',
  `repeat_buyer_cnt` int NOT NULL DEFAULT 0 COMMENT '近30天不同订单数≥2的去重买家数',
  `repurchase_rate` decimal(5,2) NOT NULL DEFAULT 0 COMMENT '回购率%=repeat/buyer*100',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_daily_spu_sales` (`tenant_id`,`spu_id`,`stat_date`),
  KEY `idx_stat_daily_spu_sales_date` (`tenant_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SPU社证统计T+1快照（月N+人已下单/月回头客/X%人回购底表，口径=order_info.status IN 2,3,4 双方未删除 近30天按create_time，口径即展示承诺变更须评审）';

SET FOREIGN_KEY_CHECKS = 1;
