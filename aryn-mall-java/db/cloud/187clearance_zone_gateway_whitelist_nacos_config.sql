-- 低价捡漏专区接口网关白名单 + boot 兼容路由增量（Cloud 微服务模式）
-- 目标库：aryn_nacos（data_id='aryn-gateway-dev.yml'）
-- 特性：幂等执行，不删除或重建 Nacos 配置；锚点不匹配时本次不改动，由末尾自检计数暴露。
--
-- 背景（2026-11-03）：低价捡漏改为「商品级出清标签」驱动，新增两个免登接口：
--   GET /promotion/app/clearance/activity-goods   专区选品（boot 路径 /boot/app/clearance/activity-goods）
--   GET /promotion/app/clearance/prices           SKU 批量有效出清价（商详/购物车展示）
-- 未登录也照常返回（不含用户维度数据）。接口不在网关白名单时网关返回 401，
-- 前端把 401 当登录态失效 → 清 token 跳登录页（与 /app/member/prices 同一踩坑）。
--
-- 双模式说明：
-- * boot 模式：由 aryn-boot application.yml 的 secure.ignore 承担（已登记两条）；
-- * cloud 模式：本脚本补齐 4 条白名单 + 2 条 boot 兼容精确路由（缺一不可）：
--   1) /promotion/app/clearance/activity-goods、/promotion/app/clearance/prices
--      （路由由既有 /promotion/** → aryn-promotion-biz 承担）；
--   2) /boot/app/clearance/activity-goods、/boot/app/clearance/prices；
--   3) 两条 boot 兼容路由（StripPrefix=1 → product-biz 的 /app/clearance/...）。
--
-- ⚠ 路由 Path 必须精确，禁止写成 /boot/app/**（会抢走其他域接口）。
--
-- 锚点：优先锚在 165 号插入的 member 专区条目之后；兜底锚 newbie / couponinfo 基线行。
-- 每步均「已存在则跳过 + 锚点不存在则不改动」，末尾自检计数用于确认全部落地。

USE `aryn_nacos`;

SET @gateway_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml'
    AND `group_id` = 'DEFAULT_GROUP'
    AND `tenant_id` = 'public'
  LIMIT 1
);

-- ---------------------------------------------------------------------------
-- 一、boot 兼容路由（两条精确路径）
-- ---------------------------------------------------------------------------
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%id: aryn-product-biz-boot-compat-clearance-prices\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%                - Path=/boot/app/member/activity-goods\n%',
    REPLACE(
      @gateway_content,
      '                - Path=/boot/app/member/activity-goods',
      '                - Path=/boot/app/member/activity-goods\n\n            - id: aryn-product-biz-boot-compat-clearance-activity-goods\n              uri: lb://aryn-product-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/clearance/activity-goods\n\n            - id: aryn-product-biz-boot-compat-clearance-prices\n              uri: lb://aryn-product-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/clearance/prices'
    ),
    IF(
      @gateway_probe LIKE '%                - Path=/boot/app/couponinfo/**\n%',
      REPLACE(
        @gateway_content,
        '                - Path=/boot/app/couponinfo/**',
        '                - Path=/boot/app/couponinfo/**\n\n            - id: aryn-product-biz-boot-compat-clearance-activity-goods\n              uri: lb://aryn-product-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/clearance/activity-goods\n\n            - id: aryn-product-biz-boot-compat-clearance-prices\n              uri: lb://aryn-product-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/clearance/prices'
      ),
      @gateway_content
    )
  )
);

-- ---------------------------------------------------------------------------
-- 二、白名单四条（cloud 直连前缀 2 条 + boot 兼容前缀 2 条）
-- ---------------------------------------------------------------------------
-- 2.1 cloud 直连：/promotion/app/clearance/activity-goods
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /promotion/app/clearance/activity-goods\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /promotion/app/member/activity-goods\n%',
    REPLACE(
      @gateway_content,
      '    - /promotion/app/member/activity-goods',
      '    - /promotion/app/member/activity-goods\n    - /promotion/app/clearance/activity-goods'
    ),
    IF(
      @gateway_probe LIKE '%    - /promotion/app/newbie/prices\n%',
      REPLACE(
        @gateway_content,
        '    - /promotion/app/newbie/prices',
        '    - /promotion/app/newbie/prices\n    - /promotion/app/clearance/activity-goods'
      ),
      @gateway_content
    )
  )
);

-- 2.2 cloud 直连：/promotion/app/clearance/prices
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /promotion/app/clearance/prices\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /promotion/app/clearance/activity-goods\n%',
    REPLACE(
      @gateway_content,
      '    - /promotion/app/clearance/activity-goods',
      '    - /promotion/app/clearance/activity-goods\n    - /promotion/app/clearance/prices'
    ),
    @gateway_content
  )
);

-- 2.3 boot 兼容：/boot/app/clearance/activity-goods
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /boot/app/clearance/activity-goods\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /boot/app/member/activity-goods\n%',
    REPLACE(
      @gateway_content,
      '    - /boot/app/member/activity-goods',
      '    - /boot/app/member/activity-goods\n    - /boot/app/clearance/activity-goods'
    ),
    IF(
      @gateway_probe LIKE '%    - /boot/app/newbie/prices\n%',
      REPLACE(
        @gateway_content,
        '    - /boot/app/newbie/prices',
        '    - /boot/app/newbie/prices\n    - /boot/app/clearance/activity-goods'
      ),
      @gateway_content
    )
  )
);

-- 2.4 boot 兼容：/boot/app/clearance/prices
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /boot/app/clearance/prices\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /boot/app/clearance/activity-goods\n%',
    REPLACE(
      @gateway_content,
      '    - /boot/app/clearance/activity-goods',
      '    - /boot/app/clearance/activity-goods\n    - /boot/app/clearance/prices'
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

-- 自检：public_count 应为 4，route_count 应为 2；不足时看锚点计数定位。
SELECT (
  (SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/clearance/activity-goods\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/clearance/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT CONCAT(`content`, '\n') LIKE '%    - /boot/app/clearance/activity-goods\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT CONCAT(`content`, '\n') LIKE '%    - /boot/app/clearance/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS clearance_public_count,
(
  (SELECT CONCAT(`content`, '\n') LIKE '%id: aryn-product-biz-boot-compat-clearance-activity-goods\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AND (SELECT CONCAT(`content`, '\n') LIKE '%                - Path=/boot/app/clearance/activity-goods\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AND (SELECT CONCAT(`content`, '\n') LIKE '%id: aryn-product-biz-boot-compat-clearance-prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AND (SELECT CONCAT(`content`, '\n') LIKE '%                - Path=/boot/app/clearance/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS clearance_route_count,
(
  SELECT CONCAT(`content`, '\n') LIKE '%                - Path=/boot/app/member/activity-goods\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_member_route_present,
(
  SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/member/activity-goods\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_member_public_present;
