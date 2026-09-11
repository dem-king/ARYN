-- 悦航购商品批量导入 Cloud Nacos 配置增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
-- 变更内容：
--   product 服务租户白名单登记 product_import_job / product_import_error / product_change_log / product_import_row。

USE `aryn_nacos`;

SET @product_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);
SET @product_content = IF(
  @product_content IS NULL OR @product_content LIKE '%- product_import_job\n%',
  @product_content,
  REPLACE(
    @product_content,
    '      - product_code_mapping\n',
    '      - product_code_mapping\n      - product_import_job\n      - product_import_error\n      - product_change_log\n      - product_import_row\n'
  )
);
UPDATE `config_info`
SET `content` = @product_content,
    `md5` = MD5(@product_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-product-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @product_content IS NOT NULL
  AND `content` <> @product_content;
