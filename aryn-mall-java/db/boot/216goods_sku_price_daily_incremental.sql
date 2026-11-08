-- SKU 每日价格快照（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 特性：可重复执行（CREATE TABLE IF NOT EXISTS）；不 DROP/TRUNCATE；不覆盖已有业务数据。
--
-- 背景（2026-11-08 · 「商品卡营销标签」P3，见
--       docs/50-需求文档/2026-11-08-商品卡营销标签/README.md）：
--   商品卡新增价格声明「近14天最低价」（对标小象）：合规自证的前提是
--   「折减基准价可回溯」——当前价是否为近 N 天最低，必须有逐日价格历史可查。
--   因此 skuPriceDailySnapshotJobHandler 每日 01:30 按租户对全部在售 SKU 写一行快照，
--   C 端 card-tags 页级一次 GROUP BY 聚合出「窗口最低价/变价次数/当日价」判标。
--
--   快照价口径（即合规证据，禁止随意变更）：sale_price = MIN(标价, 出清生效价, 秒杀价)，
--   即当日「非个性化到手价」——出清价复用专区同源口径（SKU 覆盖 + 系统活动最终价 +
--   倒挂过滤），秒杀价经 RemoteCardMarketingService.batchActiveSeckill 批量拉取、
--   失败降级仅本地价；会员价/新人价是 user-scoped 身份价，无法全局快照，不计入口径。
--   口径变更必须走需求评审，禁止 Job 顺手改（表注释与需求文档双重固化）。
--
-- 白名单：本表须登记进 aryn-boot application.yml 的 hx.tenant.tables（拦截器注入租户）。
--
-- 执行：mysql -u root -p aryn_boot < 216goods_sku_price_daily_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `goods_sku_price_daily` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `stat_date` date NOT NULL COMMENT '快照日',
  `sale_price` decimal(10,2) NOT NULL COMMENT '非个性化到手价=MIN(标价,出清生效价,秒杀价)；会员/新人价不计入（user-scoped）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_sku_date` (`tenant_id`,`sku_id`,`stat_date`),
  KEY `idx_tenant_date` (`tenant_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SKU每日价格快照：近N天最低价判定底表，口径即合规证据禁止随意变更';

SET FOREIGN_KEY_CHECKS = 1;
