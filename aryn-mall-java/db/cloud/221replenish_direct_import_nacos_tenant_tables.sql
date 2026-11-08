-- ============================================================================
-- 清单直下单导入 — Nacos 租户表白名单（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（aryn-order-biz-dev.yml）
-- 背景：2026-11-07 「导入清单下单」不挂共享购物车。
--   replenish_direct_import / replenish_direct_import_row 必须登记进
--   hx.tenant.tables，否则租户拦截器忽略这两张表，预览/确认会串租户读到
--   他人的导入任务。生产 Nacos 同名配置需同步相同键。
--   表本身由 220 号脚本在 aryn_order 建；boot 侧白名单写在
--   aryn-boot/src/main/resources/application.yml（代码文件，非本脚本）。
--
-- 幂等：只追加缺失内容，内容未变化时不产生 UPDATE。
-- 执行：mysql -u root -p aryn_nacos < 221replenish_direct_import_nacos_tenant_tables.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

SET @order_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);

-- 锚点沿用 81 号脚本登记的最后一行 shared_cart_import_row，两表紧跟其后追加
SET @order_content = IF(
  @order_content IS NULL OR @order_content LIKE '%      - replenish_direct_import_row\n%',
  @order_content,
  REPLACE(@order_content, '      - shared_cart_import_row\n',
    '      - shared_cart_import_row\n      - replenish_direct_import\n      - replenish_direct_import_row\n')
);

UPDATE `config_info`
SET `content` = @order_content, `md5` = MD5(@order_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @order_content;

-- 自检：1 表示两张表都已登记
SELECT
  (SELECT `content` LIKE '%      - replenish_direct_import\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS direct_import_table_registered,
  (SELECT `content` LIKE '%      - replenish_direct_import_row\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS direct_import_row_table_registered;
