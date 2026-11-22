-- ============================================================================
-- B2B 协议价/档期 C 端免登接口网关白名单增量（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（aryn-gateway-dev.yml）
-- 背景（2026-11-15 审查修复，方案 §11.7）：协议价批量查价、档期列表/特供、
--   阶梯价档位查询是 C 端免登公开读接口（游客可见；协议价接口对游客/非成员自动净化主体上下文，
--   不泄露合同价）。boot 模式对应 aryn-boot/application.yml secure.ignore 已同步。
--
-- 双前缀口径（C 端免登四件套）：
--   · cloud 直连：/promotion/app/policy/prices、/promotion/app/calendar/**、/promotion/app/ladder/prices
--   · boot 兼容：/boot/app/policy/**、/boot/app/calendar/**、/boot/app/ladder/**（StripPrefix=1 转发
--     promotion 域，与 pagedesign/groupbuy/couponinfo 兼容路由同款）
--
-- 安全边界：只放开这四个读接口，不用 /promotion/app/** 一把放开；
--   协议价下单试算/锁定等写接口仍走登录态。
-- 幂等：已登记则跳过；锚点不匹配时不修改（自检打印实际命中数）。
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

-- 1. 免登白名单：锚点 pagedesign 行（基线与运行环境都有，位置稳定）
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_content LIKE '%    - /promotion/app/policy/prices\n%',
  @gateway_content,
  REPLACE(
    @gateway_content,
    '    - /promotion/app/pagedesign/**\n',
    '    - /promotion/app/pagedesign/**\n    - /promotion/app/policy/prices\n    - /promotion/app/calendar/list\n    - /promotion/app/calendar/specials\n    - /boot/app/policy/prices\n    - /boot/app/calendar/list\n    - /boot/app/calendar/specials\n    - /promotion/app/ladder/prices\n    - /boot/app/ladder/prices\n'
  )
);

-- 2. boot 兼容路由：锚点 couponinfo 兼容路由块，向前插入 policy/calendar 两块
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_content LIKE '%aryn-promotion-biz-boot-compat-policy%',
  @gateway_content,
  REPLACE(
    @gateway_content,
    '            - id: aryn-promotion-biz-boot-compat-couponinfo\n',
    '            - id: aryn-promotion-biz-boot-compat-ladder\n              uri: lb://aryn-promotion-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/ladder/**\n                \n            - id: aryn-promotion-biz-boot-compat-policy\n              uri: lb://aryn-promotion-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/policy/**\n                \n            - id: aryn-promotion-biz-boot-compat-calendar\n              uri: lb://aryn-promotion-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/calendar/**\n                \n            - id: aryn-promotion-biz-boot-compat-couponinfo\n'
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

-- 自检：应输出 8（白名单 8 条）与 3（兼容路由 3 块）；
-- 历史教训（81 号脚本）：锚点不匹配时「执行成功但内容没改」，必须看实际命中数。
SELECT (
  (SELECT `content` LIKE '%    - /promotion/app/policy/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /promotion/app/calendar/list\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /promotion/app/calendar/specials\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /boot/app/policy/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /boot/app/calendar/list\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /boot/app/calendar/specials\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /promotion/app/ladder/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /boot/app/ladder/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS whitelist_registered_count,
(
  (SELECT `content` LIKE '%aryn-promotion-biz-boot-compat-policy%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%aryn-promotion-biz-boot-compat-calendar%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%aryn-promotion-biz-boot-compat-ladder%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS compat_route_registered_count;