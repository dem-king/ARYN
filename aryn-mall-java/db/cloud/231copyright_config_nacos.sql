-- ============================================================================
-- 版权配置 — Nacos 配置增量（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（config_info 中的 aryn-upms-biz-dev.yml 与 aryn-gateway-dev.yml）
-- 背景（2026-11-10 需求「版权配置可配置化」）：表由 229 号脚本建在 aryn_upms。
--   三处变更（均幂等，只追加缺失内容，内容未变化时不产生 UPDATE）：
--   1. sys_copyright_config 登记进 upms 的 hx.tenant.tables：否则启动期
--      TenantSchemaValidator 报「未配置租户表」直接起不来；
--   2. 网关免登白名单两条：/upms/app/copyright-config 与 /boot/app/copyright-config
--      （三端登录页在登录前读取，漏配则网关 401，前端把 401 当登录失效清 token）；
--   3. 一条精确 boot 兼容路由（StripPrefix=1 → upms-biz 的 /app/copyright-config）。
--      ⚠ 路由 Path 必须精确，禁止写成 /boot/app/**（会抢走其他域接口）。
--
-- 与 boot 模式对应：白名单写在 aryn-boot/src/main/resources/application.yml（代码文件）。
-- 锚点：upms 租户表以 `- sys_tenant_menu` 为锚；网关白名单以 `/upms/app/tenant/shop-info`
--   与 `/boot/app/couponinfo/byIds/**` 为锚；路由以 `- Path=/upms/**` 为锚。
--   锚点缺失时 REPLACE 不生效，由末尾自检计数暴露，需人工按同一顺序补齐后重跑。
-- 执行：mysql -u root -p aryn_nacos < 231copyright_config_nacos.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、upms 租户表登记
-- ---------------------------------------------------------------------------
SET @upms_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

SET @upms_content = IF(
  @upms_content IS NULL OR @upms_content LIKE '%- sys_copyright_config\n%',
  @upms_content,
  REPLACE(@upms_content, '      - sys_tenant_menu\n',
    '      - sys_tenant_menu\n      - sys_copyright_config\n')
);

UPDATE `config_info`
SET `content` = @upms_content, `md5` = MD5(@upms_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND @upms_content IS NOT NULL
  AND `content` <> @upms_content;

-- ---------------------------------------------------------------------------
-- 二、网关免登白名单（cloud 直连前缀 + boot 兼容前缀）
-- ---------------------------------------------------------------------------
SET @gateway_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

-- 2.1 cloud 直连：/upms/app/copyright-config
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL OR @gateway_probe LIKE '%    - /upms/app/copyright-config\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /upms/app/tenant/shop-info\n%',
    REPLACE(@gateway_content, '    - /upms/app/tenant/shop-info',
      '    - /upms/app/tenant/shop-info\n    - /upms/app/copyright-config'),
    @gateway_content
  )
);

-- 2.2 boot 兼容：/boot/app/copyright-config
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL OR @gateway_probe LIKE '%    - /boot/app/copyright-config\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /boot/app/couponinfo/byIds/**\n%',
    REPLACE(@gateway_content, '    - /boot/app/couponinfo/byIds/**',
      '    - /boot/app/couponinfo/byIds/**\n    - /boot/app/copyright-config'),
    @gateway_content
  )
);

-- ---------------------------------------------------------------------------
-- 三、boot 兼容精确路由（一条）
-- ---------------------------------------------------------------------------
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL OR @gateway_probe LIKE '%id: aryn-upms-biz-boot-compat-copyright\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%                - Path=/upms/** %',
    REPLACE(@gateway_content, '                - Path=/upms/** ',
      '                - Path=/upms/** \n\n            - id: aryn-upms-biz-boot-compat-copyright\n              uri: lb://aryn-upms-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/copyright-config'),
    @gateway_content
  )
);

UPDATE `config_info`
SET `content` = @gateway_content, `md5` = MD5(@gateway_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND @gateway_content IS NOT NULL
  AND `content` <> @gateway_content;

-- ---------------------------------------------------------------------------
-- 四、自检：tenant_table_registered / public_count(应为 2) / route_registered 均应为 1
--     为 0 时看 anchor_* 定位锚点是否缺失
-- ---------------------------------------------------------------------------
SELECT
  (SELECT `content` LIKE '%      - sys_copyright_config\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS tenant_table_registered,
  (
    (SELECT CONCAT(`content`, '\n') LIKE '%    - /upms/app/copyright-config\n%' FROM `config_info`
      WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
    + (SELECT CONCAT(`content`, '\n') LIKE '%    - /boot/app/copyright-config\n%' FROM `config_info`
      WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  ) AS public_count,
  (
    (SELECT CONCAT(`content`, '\n') LIKE '%id: aryn-upms-biz-boot-compat-copyright\n%' FROM `config_info`
      WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
    AND (SELECT CONCAT(`content`, '\n') LIKE '%                - Path=/boot/app/copyright-config\n%' FROM `config_info`
      WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  ) AS route_registered,
  (SELECT CONCAT(`content`, '\n') LIKE '%      - sys_tenant_menu\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1) AS anchor_upms_table_present,
  (SELECT CONCAT(`content`, '\n') LIKE '%    - /upms/app/tenant/shop-info\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1) AS anchor_shop_info_present;