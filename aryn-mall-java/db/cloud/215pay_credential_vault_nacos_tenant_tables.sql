-- ============================================================================
-- 支付安全配置底座 — 支付服务租户表白名单与日志级别（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（config_info 中的 aryn-pay-biz-dev.yml）
-- 注意：生产环境同名 Nacos 配置（如 aryn-pay-biz-prod.yml）需同步相同键，
--       主密钥仍只允许由部署 Secret/环境变量注入，不得写入 Nacos 明文。
-- 背景：2026-11-06《支付管理后台自助配置与交易闭环完善方案》P0。
--   1) pay_config_revision / pay_config_material 必须登记到 hx.tenant.tables，
--      否则 MyBatis 租户拦截器会忽略新表，造成跨租户串读串写；
--   2) 支付 Mapper 日志从 debug 收紧为 info，避免 SQL 参数与异常把材料带进日志；
--   3) 主密钥仅登记环境变量占位，禁止把实际值写入 Nacos/SQL/文档。
--
-- 幂等：只追加缺失内容，内容未变化时不产生 UPDATE；不删除或重建 Nacos 记录。
-- 执行：mysql -u root -p aryn_nacos < 215pay_credential_vault_nacos_tenant_tables.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

SET @pay_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-pay-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

-- 1) 日志级别收紧
SET @pay_content = REPLACE(@pay_content, 'com.aryn.cloud.pay.mapper: debug', 'com.aryn.cloud.pay.mapper: info');

-- 2) 租户表登记（以稳定存在的 pay_refund_order 为锚点，只追加一次）
SET @pay_content = IF(
  @pay_content IS NULL OR @pay_content LIKE '%      - pay_config_revision\n%',
  @pay_content,
  REPLACE(@pay_content, '      - pay_refund_order\n',
    '      - pay_refund_order\n      - pay_config_revision\n      - pay_config_material\n')
);

-- 3) 主密钥环境变量占位（空值占位，不写入任何真实密钥）
SET @pay_content = IF(
  @pay_content IS NULL OR @pay_content LIKE '%ARYN_PAY_CREDENTIAL_MASTER_KEY%',
  @pay_content,
  REPLACE(@pay_content, 'cert-dir: /root/aryn/cert\n',
    'cert-dir: /root/aryn/cert\n\naryn:\n  pay:\n    credential:\n      master-key: ${ARYN_PAY_CREDENTIAL_MASTER_KEY:}\n      key-id: ${ARYN_PAY_CREDENTIAL_KEY_ID:primary}\n      previous-keys: ${ARYN_PAY_CREDENTIAL_PREVIOUS_KEYS:}\n      max-material-bytes: ${ARYN_PAY_CREDENTIAL_MAX_BYTES:262144}\n    callback:\n      allow-http: ${ARYN_PAY_CALLBACK_ALLOW_HTTP:false}\n      allow-private: ${ARYN_PAY_CALLBACK_ALLOW_PRIVATE:false}\n    alipay:\n      gateway-prod: ${ARYN_PAY_ALIPAY_GATEWAY_PROD:https://openapi.alipay.com/gateway.do}\n      gateway-sandbox: ${ARYN_PAY_ALIPAY_GATEWAY_SANDBOX:https://openapi-sandbox.dl.alipaydev.com/gateway.do}\n')
);

UPDATE `config_info`
SET `content` = @pay_content, `md5` = MD5(@pay_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-pay-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @pay_content;

-- 自检：1 表示租户表已登记，1 表示日志级别已收紧
SELECT
  (SELECT `content` LIKE '%      - pay_config_revision\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-pay-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS pay_revision_table_registered,
  (SELECT `content` LIKE '%com.aryn.cloud.pay.mapper: info%' FROM `config_info`
    WHERE `data_id` = 'aryn-pay-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS pay_mapper_log_level_info;
