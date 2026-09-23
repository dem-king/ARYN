-- 悦航购补给单 Excel 导入 Cloud Nacos 配置增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
-- 变更内容：
--   1) order 服务租户白名单登记 shared_cart_import / shared_cart_import_row；
--   2) order 服务同步 multipart 上传上限（Cloud 默认 1MB，导入 xlsx 会直接被拒）。
--
-- 背景：Cloud 模式的 spring.servlet.multipart 只在 Nacos 的 application-dev.yml 里配了
--       location，未配 max-file-size，沿用 Spring Boot 默认 1MB；而 Boot 模式的
--       application-dev/prod.yml 是 100MB。不做这项对齐，同一份 Excel 在 boot 能传、
--       cloud 必失败（双模式强制规则要求两边行为一致）。
--
-- 执行：mysql -u root -p aryn_nacos < 81shared_cart_import_nacos_config.sql

USE `aryn_nacos`;

-- ---------------------------------------------------------------------------
-- 一、order 服务租户白名单
-- ---------------------------------------------------------------------------
SET @order_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);
SET @order_content = IF(
  @order_content IS NULL OR @order_content LIKE '%- shared_cart_import\n%',
  @order_content,
  REPLACE(
    @order_content,
    '      - shared_cart_item\n',
    '      - shared_cart_item\n      - shared_cart_import\n      - shared_cart_import_row\n'
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

-- ---------------------------------------------------------------------------
-- 二、multipart 上限对齐 Boot 模式（100MB），并显式指定临时目录
--     以 spring. 开头行做插入锚点：application-dev.yml 已有 `spring:` 根节点。
-- ---------------------------------------------------------------------------
SET @app_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'application-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);
-- 两种锚点都要覆盖：基线 dump 里 location 是字面量 /data/tmp，
-- 而实际运行环境的 Nacos 配置用的是 ${MULTIPART_LOCATION:${java.io.tmpdir}} 占位。
-- 只认其中一种会出现「脚本执行成功、上限却没改」的静默失效（首次执行实测踩到）。
SET @app_content = IF(
  @app_content IS NULL OR @app_content LIKE '%max-file-size%',
  @app_content,
  REPLACE(
    @app_content,
    '  servlet:\n    multipart:\n      location: /data/tmp\n',
    '  servlet:\n    multipart:\n      location: /data/tmp\n      max-file-size: 100MB\n      max-request-size: 100MB\n'
  )
);
SET @app_content = IF(
  @app_content IS NULL OR @app_content LIKE '%max-file-size%',
  @app_content,
  REPLACE(
    @app_content,
    '  servlet:\n    multipart:\n      location: ${MULTIPART_LOCATION:${java.io.tmpdir}}\n',
    '  servlet:\n    multipart:\n      location: ${MULTIPART_LOCATION:${java.io.tmpdir}}\n      max-file-size: 100MB\n      max-request-size: 100MB\n'
  )
);
UPDATE `config_info`
SET `content` = @app_content,
    `md5` = MD5(@app_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'application-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @app_content IS NOT NULL
  AND `content` <> @app_content;
