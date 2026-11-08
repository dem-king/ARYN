-- ============================================================================
-- SKU 价格快照 — 产品服务 Nacos 配置增量（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（config_info 中的 aryn-product-biz-dev.yml）
-- 背景：2026-11-08 「商品卡营销标签」P3 价格快照（goods_sku_price_daily）。
--   两处变更（均幂等，只追加缺失内容，内容未变化时不产生 UPDATE）：
--   1. goods_sku_price_daily 登记进 hx.tenant.tables：否则租户拦截器忽略该表，
--      快照写入与窗口聚合会跨租户串读串写（多租户 SaaS 高危）。
--   2. xxl.job.port=9011：product-biz 本次接入 @ArynEnableXxlJob（快照 Job 落
--      product-biz），独立端口避免与 order-biz 默认 9009 / promotion 9010 冲突。
--   表本身由 225 号脚本在 aryn_product 建；boot 侧白名单写在
--   aryn-boot/src/main/resources/application.yml（代码文件，非本脚本）。
-- 幂等锚点：租户表以 `- goods_category_badge`（212 号已登记的尾部表）为插入锚点；
--   xxl 端口以 product 独有的日志行 `com.aryn.cloud.product.mapper` 为插入锚点。
--   若目标环境锚点缺失则 REPLACE 不生效，自检返回 0，需人工按同一顺序补齐后重跑。
-- 执行：mysql -u root -p aryn_nacos < 227sku_price_daily_nacos_tenant_tables.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

SET @product_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

-- 1) hx.tenant.tables 追加 goods_sku_price_daily（锚点：goods_category_badge）
SET @product_content = IF(
  @product_content IS NULL OR @product_content LIKE '%- goods_sku_price_daily\n%',
  @product_content,
  REPLACE(@product_content, '      - goods_category_badge\n',
    '      - goods_category_badge\n      - goods_sku_price_daily\n')
);

-- 2) 追加 xxl 执行器端口（锚点：product 独有 logging 行）
SET @product_content = IF(
  @product_content IS NULL OR @product_content LIKE '%xxl:%\n  job:%\n    port: 9011%',
  @product_content,
  REPLACE(@product_content, 'logging:\n  level:\n    com.aryn.cloud.product.mapper: debug\n',
    'logging:\n  level:\n    com.aryn.cloud.product.mapper: debug\n\nxxl:\n  job:\n    port: 9011\n')
);

UPDATE `config_info`
SET `content` = @product_content, `md5` = MD5(@product_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @product_content;

-- 自检：两项均应返回 1
SELECT
  (SELECT `content` LIKE '%      - goods_sku_price_daily\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS price_daily_table_registered,
  (SELECT `content` LIKE '%    port: 9011%' FROM `config_info`
    WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS xxl_port_registered;
