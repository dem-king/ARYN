-- ============================================================================
-- SPU 社证统计 — Nacos 租户表白名单（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（aryn-order-biz-dev.yml）
-- 背景：2026-11-08 「商品卡营销标签」P2 社证统计（stat_daily_spu_sales）。
--   本表必须登记进 hx.tenant.tables，否则租户拦截器忽略该表，Job 聚合与
--   最新期读取会串租户读到他租户的统计。生产 Nacos 同名配置需同步相同键。
--   表本身由 222 号脚本在 aryn_order 建；boot 侧白名单写在
--   aryn-boot/src/main/resources/application.yml（代码文件，非本脚本）。
--
-- 幂等：只追加缺失内容，内容未变化时不产生 UPDATE。
-- 执行：mysql -u root -p aryn_nacos < 224spu_sales_stat_nacos_tenant_tables.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

SET @order_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

-- 锚点用基线即有的 stat_daily_user_order（同为订单域统计表），新表紧跟其后追加
SET @order_content = IF(
  @order_content IS NULL OR @order_content LIKE '%      - stat_daily_spu_sales\n%',
  @order_content,
  REPLACE(@order_content, '      - stat_daily_user_order\n',
    '      - stat_daily_user_order\n      - stat_daily_spu_sales\n')
);

UPDATE `config_info`
SET `content` = @order_content, `md5` = MD5(@order_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @order_content;

-- 自检：1 表示已登记
SELECT
  (SELECT `content` LIKE '%      - stat_daily_spu_sales\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS spu_sales_stat_table_registered;
