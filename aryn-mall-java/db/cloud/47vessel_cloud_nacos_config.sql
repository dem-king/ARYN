-- 悦航购船舶域 Cloud Nacos 配置增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
-- 变更内容：
--   1) 网关新增 aryn-vessel-biz 路由（/vessel/**）；
--   2) 创建 aryn-vessel-biz-dev.yml 服务配置（数据源 + 租户拦截表白名单）；
--   3) product 服务租户白名单登记 ship_goods_profile / ship_sku_profile / product_code_mapping；
--   4) order 服务租户白名单登记 shared_cart / fulfillment / promotion_snapshot 相关表。

USE `aryn_nacos`;

-- 1) 网关路由：以 mall-order 路由为锚点追加 vessel 路由
SET @gateway_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);
SET @gateway_content = IF(
  @gateway_content IS NULL OR @gateway_content LIKE '%- Path=/vessel/**%',
  @gateway_content,
  REPLACE(
    @gateway_content,
    '- Path=/mall-order/**\n',
    '- Path=/mall-order/**\n\n            - id: aryn-vessel-biz\n              uri: lb://aryn-vessel-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/vessel/**\n'
  )
);
UPDATE `config_info`
SET `content` = @gateway_content,
    `md5` = MD5(@gateway_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-gateway-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @gateway_content IS NOT NULL
  AND `content` <> @gateway_content;

-- 2) 创建 aryn-vessel-biz-dev.yml（已存在则不覆盖）
SET @vessel_content = 'spring:  \n  datasource:\n    type: com.alibaba.druid.pool.DruidDataSource\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    username: root\n    password: 123456\n    url: jdbc:mysql://aryn-mysql:3306/aryn_vessel?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true\n\nlogging:\n  level:\n    com.aryn.cloud.vessel.mapper: debug\n\nhx:\n  tenant:\n    tables: \n      - vessel_info\n      - vessel_member\n      - vessel_call\n';
INSERT INTO `config_info` (`data_id`,`group_id`,`content`,`md5`,`src_ip`,`tenant_id`,`c_desc`,`type`)
SELECT 'aryn-vessel-biz-dev.yml', 'DEFAULT_GROUP', @vessel_content, MD5(@vessel_content), 'system', '', '船舶与靠港计划服务配置', 'yaml'
WHERE NOT EXISTS (
  SELECT 1 FROM `config_info`
  WHERE `data_id` = 'aryn-vessel-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
);

-- 3) product 服务租户白名单：以 product_refund_stock_record 为锚点追加船供资料表
SET @product_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);
SET @product_content = IF(
  @product_content IS NULL OR @product_content LIKE '%- ship_goods_profile\n%',
  @product_content,
  REPLACE(
    @product_content,
    '      - product_refund_stock_record\n',
    '      - product_refund_stock_record\n      - ship_goods_profile\n      - ship_sku_profile\n      - product_code_mapping\n'
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

-- 4) order 服务租户白名单：以 delivery_qualification_operation 为锚点追加共享购物车与履约表
SET @order_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);
SET @order_content = IF(
  @order_content IS NULL OR @order_content LIKE '%- shared_cart\n%',
  @order_content,
  REPLACE(
    @order_content,
    '      - delivery_qualification_operation\n',
    '      - delivery_qualification_operation\n      - shared_cart\n      - shared_cart_member\n      - shared_cart_item\n      - fulfillment_wave\n      - fulfillment_pick_item\n      - fulfillment_exception\n      - promotion_snapshot\n'
  )
);
UPDATE `config_info`
SET `content` = @order_content,
    `md5` = MD5(@order_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-order-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @order_content IS NOT NULL
  AND `content` <> @order_content;
