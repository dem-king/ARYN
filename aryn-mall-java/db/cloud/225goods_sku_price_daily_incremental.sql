-- SKU 每日价格快照（Cloud 微服务模式）
--
-- 目标库：aryn_product
-- 与 boot 216 号脚本同内容（除库上下文）；背景/口径见该脚本头注释。
-- 配套：226 号（aryn_job 注册 skuPriceDailySnapshotJobHandler）、
--       227 号（aryn_nacos 租户表白名单 hx.tenant.tables + xxl 执行器端口）。
--
-- 执行：mysql -u root -p aryn_product < 225goods_sku_price_daily_incremental.sql

USE `aryn_product`;
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
