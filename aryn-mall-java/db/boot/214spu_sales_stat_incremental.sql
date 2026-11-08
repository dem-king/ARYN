-- SPU 社证统计 T+1 快照（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 特性：可重复执行（CREATE TABLE IF NOT EXISTS）；不 DROP/TRUNCATE；不覆盖已有业务数据。
--
-- 背景（2026-11-08 · 「商品卡营销标签」P2，见
--       docs/50-需求文档/2026-11-08-商品卡营销标签/README.md）：
--   商品卡新增社证文案「月N+人已下单 / 月回头客N+人 / X%人回购」（对标盒马/小象）。
--   列表页 20 个商品 × 实时 COUNT(DISTINCT) 扫订单大表是灾难，且社证数字不需要实时性，
--   因此 T+1 离线聚合：spuSalesStatJobHandler 每日按租户把近 30 天窗口的
--   去重买家数/复购买家数/回购率写一行快照，C 端只读最新一期（O(1) 点查）。
--
--   聚合口径与热销榜/常购清单同源（OrderItemMapper#selectGoodsSalesRank 注释）：
--     order_info.status IN ('2','3','4')（已过支付环节，含货到付款）、
--     订单与明细均未删除、窗口按 order_info.create_time 近 30 天；
--     回购判定 = 同一 (用户,SPU) 的不同订单数 ≥ 2（同一订单买多件不算回购）。
--   表按 SPU 维度聚合（卡面只有 SPU 维度；SKU 级诉求出现前不加维度）。
--
--   展示门槛/取整（ buyers≥1000、回购率≥30%、floor 到千/万位加「+」）在
--   product-biz SocialProofFormatter 收口，不落库——改门槛不动数据。
--
-- 白名单：本表须登记进 aryn-boot application.yml 的 hx.tenant.tables（拦截器注入租户）。
--
-- 执行：mysql -u root -p aryn_boot < 214spu_sales_stat_incremental.sql

USE `aryn_boot`;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SPU社证统计T+1快照（月N+人已下单/月回头客/X%人回购底表，口径见脚本头注释，口径即展示承诺变更须评审）';

SET FOREIGN_KEY_CHECKS = 1;
