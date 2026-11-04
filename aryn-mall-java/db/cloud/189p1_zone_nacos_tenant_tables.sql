-- P1 专区治理租户表白名单（Cloud 微服务模式）
--
-- 目标库：aryn_nacos
-- 背景：新增 goods_price_history（product 库）、clearance_rule / mall_zone_setting（promotion 库），
--       必须登记到对应服务的 hx.tenant.tables，否则 MyBatis 租户拦截器不会自动注入 tenant_id。
-- 特性：幂等执行；只追加缺失行，不删除或重建 Nacos 配置数据。
--
-- 执行：mysql -u root -p aryn_nacos < 189p1_zone_nacos_tenant_tables.sql

USE `aryn_nacos`;

SET @product_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);
SET @product_content = IF(
  @product_content IS NULL OR @product_content LIKE '%- goods_price_history\n%',
  @product_content,
  REPLACE(@product_content, '      - goods_spu\n', '      - goods_spu\n      - goods_price_history\n')
);
UPDATE `config_info`
SET `content` = @product_content, `md5` = MD5(@product_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @product_content;

SET @promotion_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);
SET @promotion_content = IF(
  @promotion_content IS NULL OR @promotion_content LIKE '%- mall_zone_setting\n%',
  @promotion_content,
  REPLACE(@promotion_content, '      - promotion_lock\n',
    '      - promotion_lock\n      - clearance_rule\n      - mall_zone_setting\n')
);
UPDATE `config_info`
SET `content` = @promotion_content, `md5` = MD5(@promotion_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @promotion_content;

-- 自检：两项都应为 1；为 0 说明锚点不匹配，需要按注释口径人工补齐后重跑。
SELECT
  (SELECT `content` LIKE '%      - goods_price_history%' FROM `config_info`
    WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AS product_price_history_registered,
  (SELECT `content` LIKE '%      - mall_zone_setting%' AND `content` LIKE '%      - clearance_rule%'
    FROM `config_info`
    WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AS promotion_zone_tables_registered;
