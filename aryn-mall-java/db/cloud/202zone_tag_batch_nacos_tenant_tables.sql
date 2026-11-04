-- 专区批量打标租户表白名单（Cloud 微服务模式）
--
-- 目标库：aryn_nacos
-- 背景：新增 goods_zone_tag_batch / goods_zone_tag_batch_item（product 库），
--       必须登记到 aryn-product-biz 的 hx.tenant.tables，否则 MyBatis 租户拦截器
--       不会自动注入 tenant_id，批次列表/撤销会跨租户。
--       mall_zone_setting 已在 189p1_zone_nacos_tenant_tables.sql 登记，本脚本不重复处理。
-- 特性：幂等执行；只追加缺失行，不删除或重建 Nacos 配置数据。
--
-- 执行：mysql -u root -p aryn_nacos < 202zone_tag_batch_nacos_tenant_tables.sql

USE `aryn_nacos`;

SET @product_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);
SET @product_content = IF(
  @product_content IS NULL OR @product_content LIKE '%- goods_zone_tag_batch_item\n%',
  @product_content,
  REPLACE(@product_content, '      - goods_spu\n',
    '      - goods_spu\n      - goods_zone_tag_batch\n      - goods_zone_tag_batch_item\n')
);
UPDATE `config_info`
SET `content` = @product_content, `md5` = MD5(@product_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @product_content;

-- 自检：两个表名都应出现在 product 服务租户表白名单中，两列都应为 1
SELECT
  (SELECT `content` LIKE '%      - goods_zone_tag_batch\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AS product_zone_batch_registered,
  (SELECT `content` LIKE '%      - goods_zone_tag_batch_item\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AS product_zone_batch_item_registered;
