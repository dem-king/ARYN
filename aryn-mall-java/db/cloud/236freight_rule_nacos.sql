-- ============================================================================
-- 订单级运费规则 — Nacos 租户表白名单（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（config_info 中的 aryn-order-biz-dev.yml）
-- 背景（2026-11-12 需求「满额包邮」）：表由 235 号脚本建在 aryn_order。
--   必须登记进 hx.tenant.tables，否则租户拦截器忽略该表：规则读取会跨租户串读，
--   可能命中他租户的门槛与基础运费（多租户 SaaS 高危）。
--   生产 Nacos 同名配置需同步相同键。
--   boot 侧白名单写在 aryn-boot/src/main/resources/application.yml（代码文件，非本脚本）。
--
-- 幂等：只追加缺失内容，内容未变化时不产生 UPDATE。
-- 锚点：基线即有的 stat_daily_user_order（同为订单域统计表）。
--   锚点缺失时不改动，由末尾自检返回 0 暴露，需人工按同一顺序补齐后重跑。
-- 执行：mysql -u root -p aryn_nacos < 236freight_rule_nacos.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

SET @order_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

SET @order_content = IF(
  @order_content IS NULL OR @order_content LIKE '%      - freight_rule\n%',
  @order_content,
  REPLACE(@order_content, '      - stat_daily_user_order\n',
    '      - stat_daily_user_order\n      - freight_rule\n')
);

UPDATE `config_info`
SET `content` = @order_content, `md5` = MD5(@order_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @order_content;

-- 自检：1 表示已登记
SELECT
  (SELECT `content` LIKE '%      - freight_rule\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS freight_rule_table_registered;
