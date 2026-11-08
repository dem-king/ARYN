-- ============================================================================
-- 支付交易闭环 — Nacos 租户表白名单与 P1 配置（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（aryn-pay-biz-dev.yml）
-- 背景：2026-11-06 支付方案 P1 任务 7。
--   pay_event_outbox 必须登记到 hx.tenant.tables，否则租户拦截器忽略该表，
--   跨租户重投可能读到其他租户事件。生产 Nacos 同名配置需同步相同键；
--   主密钥仍只允许部署 Secret/环境变量注入。
--
-- 幂等：只追加缺失内容，内容未变化时不产生 UPDATE。
-- 执行：mysql -u root -p aryn_nacos < 217payment_closure_nacos_tenant_tables.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

SET @pay_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-pay-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

SET @pay_content = IF(
  @pay_content IS NULL OR @pay_content LIKE '%      - pay_event_outbox\n%',
  @pay_content,
  REPLACE(@pay_content, '      - pay_config_material\n',
    '      - pay_config_material\n      - pay_event_outbox\n')
);

SET @pay_content = IF(
  @pay_content IS NULL OR @pay_content LIKE '%ARYN_PAY_ALLOWED_RETURN_HOSTS%',
  @pay_content,
  REPLACE(@pay_content,
    'gateway-sandbox: ${ARYN_PAY_ALIPAY_GATEWAY_SANDBOX:https://openapi-sandbox.dl.alipaydev.com/gateway.do}\n',
    'gateway-sandbox: ${ARYN_PAY_ALIPAY_GATEWAY_SANDBOX:https://openapi-sandbox.dl.alipaydev.com/gateway.do}\n    allowed-return-hosts: ${ARYN_PAY_ALLOWED_RETURN_HOSTS:}\n    client-ip:\n      trusted-proxy: ${ARYN_PAY_TRUSTED_PROXY:false}\n    outbox:\n      relay-interval-ms: ${ARYN_PAY_OUTBOX_RELAY_INTERVAL_MS:5000}\n      batch-size: ${ARYN_PAY_OUTBOX_BATCH_SIZE:100}\n      max-attempts: ${ARYN_PAY_OUTBOX_MAX_ATTEMPTS:20}\n')
);

UPDATE `config_info`
SET `content` = @pay_content, `md5` = MD5(@pay_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-pay-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @pay_content;

-- 自检：1 表示 outbox 表已登记，1 表示 P1 配置键已存在
SELECT
  (SELECT `content` LIKE '%      - pay_event_outbox\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-pay-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS pay_outbox_table_registered,
  (SELECT `content` LIKE '%ARYN_PAY_OUTBOX_RELAY_INTERVAL_MS%' FROM `config_info`
    WHERE `data_id` = 'aryn-pay-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS pay_outbox_config_present;
