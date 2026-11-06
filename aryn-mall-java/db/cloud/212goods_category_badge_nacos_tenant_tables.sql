-- ============================================================================
-- 类目角标配置化 — 产品服务租户表白名单增量（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（config_info 中的 aryn-product-biz-dev.yml）
-- 背景（2026-11-05 需求「类目角标配置化」，见
--       docs/50-需求文档/2026-11-05-类目角标配置化/IMPLEMENTATION_PLAN.md 第 5 节）：
--   aryn_product 新增 goods_category_badge 表，必须登记到 aryn-product-biz 的
--   hx.tenant.tables，否则 MyBatis 租户拦截器（ArynTenantLineHandler#ignoreTable）会忽略该表：
--   既不自动注入 tenant_id 也不按租户过滤，角标配置会跨租户串读串写（多租户 SaaS 高危）。
-- 与 boot 对齐：boot 模式同表登记在 aryn-boot/src/main/resources/application.yml 的
--   hx.tenant.tables（应用配置文件，非 SQL 脚本）。
-- 特性：幂等执行；只追加缺失行，不删除或重建 Nacos 配置数据；内容未变化时不产生 UPDATE。
-- 幂等锚点：以租户表清单中稳定存在的 `- product_import_row` 为插入锚点（见
--   db/cloud/3aryn_nacos.sql 基线 record 8），在其后追加 `- goods_category_badge`；
--   若目标环境该锚点缺失则 REPLACE 不生效，下方自检返回 0，需人工按同一顺序补齐后重跑。
-- 执行：mysql -u root -p aryn_nacos < 212goods_category_badge_nacos_tenant_tables.sql
-- ============================================================================

USE `aryn_nacos`;
SET NAMES utf8mb4;

SET @product_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);
SET @product_content = IF(
  @product_content IS NULL OR @product_content LIKE '%- goods_category_badge\n%',
  @product_content,
  REPLACE(@product_content, '      - product_import_row\n',
    '      - product_import_row\n      - goods_category_badge\n')
);
UPDATE `config_info`
SET `content` = @product_content, `md5` = MD5(@product_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @product_content;

-- 自检：product_badge_table_registered = 1 表示 goods_category_badge 已登记到产品服务租户表白名单
SELECT
  (SELECT `content` LIKE '%      - goods_category_badge\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-product-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
      AND `tenant_id` = 'public' LIMIT 1) AS product_badge_table_registered;
