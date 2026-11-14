-- ============================================================================
-- 新人三场景接口网关免登白名单（Cloud 微服务模式）
--
-- 目标库：aryn_nacos
-- 背景：P1 新增 `GET /promotion/app/newbie/scenes`，未登录也需返回个人/船供/企业
--       三张卡（资格字段为 null，登录后才判定）。网关白名单缺该路径时返回 401，
--       小程序会把 401 当作登录态失效并清 token、跳登录页。
-- 前置：131/165 已登记 `/promotion/app/newbie/prices` 与 `/boot/app/newbie/prices`；
--       网关路由 `/promotion/app/newbie/**`、`/boot/app/newbie/**` 已存在。
-- 特性：幂等，不删除/重建 Nacos 配置；锚点缺失时本次不改动，由末尾自检暴露。
--
-- 执行：mysql -u root -p < 242newbie_scenes_gateway_whitelist_nacos_config.sql
-- ============================================================================

USE `aryn_nacos`;

SET @gateway_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml'
    AND `group_id` = 'DEFAULT_GROUP'
    AND `tenant_id` = 'public'
  LIMIT 1
);

-- 1. cloud 直连前缀：/promotion/app/newbie/scenes
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /promotion/app/newbie/scenes\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /promotion/app/newbie/prices\n%',
    REPLACE(
      @gateway_content,
      '    - /promotion/app/newbie/prices',
      '    - /promotion/app/newbie/prices\n    - /promotion/app/newbie/scenes'
    ),
    @gateway_content
  )
);

-- 2. boot 兼容前缀：/boot/app/newbie/scenes
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /boot/app/newbie/scenes\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /boot/app/newbie/prices\n%',
    REPLACE(
      @gateway_content,
      '    - /boot/app/newbie/prices',
      '    - /boot/app/newbie/prices\n    - /boot/app/newbie/scenes'
    ),
    @gateway_content
  )
);

UPDATE `config_info`
SET `content` = @gateway_content,
    `md5` = MD5(@gateway_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-gateway-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `tenant_id` = 'public'
  AND @gateway_content IS NOT NULL
  AND `content` <> @gateway_content;

-- 自检：两个计数都应为 1；为 0 表示锚点没命中，需人工检查网关配置。
SELECT
  (LENGTH(@gateway_content) - LENGTH(REPLACE(@gateway_content,
    '    - /promotion/app/newbie/scenes', ''))) / LENGTH('    - /promotion/app/newbie/scenes')
    AS registered_cloud_scenes_count,
  (LENGTH(@gateway_content) - LENGTH(REPLACE(@gateway_content,
    '    - /boot/app/newbie/scenes', ''))) / LENGTH('    - /boot/app/newbie/scenes')
    AS registered_boot_scenes_count;
