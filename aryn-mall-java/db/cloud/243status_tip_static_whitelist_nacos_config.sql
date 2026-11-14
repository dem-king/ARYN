-- ============================================================================
-- 空态插画（wd-status-tip 预设图）— Nacos 配置增量（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（config_info 中的 aryn-gateway-dev.yml）
-- 背景（2026-11-13）：
--   wot-design-uni 的 wd-status-tip 默认 urlPrefix 指向
--   https://registry.npmmirror.com/wot-design-uni-assets/*/files/，
--   该包不在 npmmirror 的 unpkg 白名单内，文件请求返回 HTTP 451 + JSON 错误体，
--   小程序渲染层报 «Failed to load image ... net::OK»，7 张空态图全部裂图。
--
--   处理：7 张 png 改为随 aryn-upms-biz 静态资源发布
--   （aryn-upms-biz/src/main/resources/static/status-tip/），
--   前端用后端域名拼前缀：cloud `/upms/status-tip/`，boot `/boot/status-tip/`。
--
-- 双模式说明：
-- * boot 模式：白名单写在 aryn-boot/src/main/resources/application.yml
--   （secure.ignore.urls 已登记 `/status-tip/**`）；
-- * cloud 模式：本脚本在网关白名单追加 `/upms/status-tip/**`。
--   路由由既有 `/upms/**` → aryn-upms-biz 承担，无需新增 boot 兼容路由
--   （boot 形态由端上直连 aryn-boot，不经过网关）。
--
-- 幂等：已存在则跳过；锚点缺失时本次不改动，由末尾自检计数暴露。
-- 执行：mysql -u root -p aryn_nacos < 243status_tip_static_whitelist_nacos_config.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

SET @gateway_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

-- ---------------------------------------------------------------------------
-- 一、网关免登白名单：/upms/status-tip/**
--     锚点优先取版权配置条目（231 号已落地），兜底取门店信息条目（基线自带）。
-- ---------------------------------------------------------------------------
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL OR @gateway_probe LIKE '%    - /upms/status-tip/**\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /upms/app/copyright-config\n%',
    REPLACE(@gateway_content, '    - /upms/app/copyright-config',
      '    - /upms/app/copyright-config\n    - /upms/status-tip/**'),
    IF(
      @gateway_probe LIKE '%    - /upms/app/tenant/shop-info\n%',
      REPLACE(@gateway_content, '    - /upms/app/tenant/shop-info',
        '    - /upms/app/tenant/shop-info\n    - /upms/status-tip/**'),
      @gateway_content
    )
  )
);

UPDATE `config_info`
SET `content` = @gateway_content, `md5` = MD5(@gateway_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND @gateway_content IS NOT NULL
  AND `content` <> @gateway_content;

-- ---------------------------------------------------------------------------
-- 二、自检：status_tip_whitelisted 应为 1；为 0 时看 anchor_* 定位锚点是否缺失
-- ---------------------------------------------------------------------------
SELECT
  (SELECT CONCAT(`content`, '\n') LIKE '%    - /upms/status-tip/**\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS status_tip_whitelisted,
  (SELECT CONCAT(`content`, '\n') LIKE '%    - /upms/app/copyright-config\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS anchor_copyright_present,
  (SELECT CONCAT(`content`, '\n') LIKE '%    - /upms/app/tenant/shop-info\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS anchor_shop_info_present;
