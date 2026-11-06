-- 销售业绩归属与提成 — 租户表白名单增量（Cloud 微服务模式）
--
-- 目标库：aryn_nacos（config_info 中的 aryn-promotion-biz-dev.yml）
-- 背景（2026-11-05 需求「销售业绩归属与提成」，见
--       docs/50-需求文档/2026-11-05-销售业绩归属与提成/IMPLEMENTATION_PLAN.md 6.2）：
--   aryn_promotion 新增 13 张表（销售员 / 客户归属 / 换绑日志 / 待转交认领 / 转交奖励 / 业绩单 /
--   发放台账 / 退款负数结转 / 全局配置 / 退款幂等记录 / 券包 / 券包明细 / 兑换记录），
--   必须登记到 aryn-promotion-biz 的 hx.tenant.tables，否则 MyBatis 租户拦截器不会自动注入
--   tenant_id，销售/业绩/券包数据会跨租户串读串写。
--   说明：计划 6.2 写「11 张新表」为早期口径，按第 2 节实际落地为 13 张（含
--   sales_payout_adjustment / sales_refund_record / distribution_coupon_pack_item 三张辅助表）。
-- 特性：幂等执行；只追加缺失行，不删除或重建 Nacos 配置数据。
-- 幂等锚点：以 hx.tenant.tables 中稳定存在的 `- distribution_refund_record` 为插入锚点
--           （见 db/cloud/3aryn_nacos.sql 基线），13 张新表紧随其后追加；
--           若目标环境该锚点缺失则会 REPLACE 不生效，下方自检返回 0，需人工按同一顺序补齐后重跑。
-- 执行：mysql -u root -p aryn_nacos < 207sales_performance_nacos_tenant_tables.sql

USE `aryn_nacos`;

SET @promotion_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  LIMIT 1
);
SET @promotion_content = IF(
  @promotion_content IS NULL OR @promotion_content LIKE '%- sales_staff\n%',
  @promotion_content,
  REPLACE(@promotion_content, '      - distribution_refund_record\n',
    '      - distribution_refund_record\n      - distribution_coupon_pack\n      - distribution_coupon_pack_item\n      - distribution_coupon_exchange\n      - sales_staff\n      - sales_customer_bind\n      - sales_customer_bind_log\n      - sales_customer_claim\n      - sales_transfer_reward\n      - sales_order\n      - sales_payout\n      - sales_payout_adjustment\n      - sales_config\n      - sales_refund_record\n')
);
UPDATE `config_info`
SET `content` = @promotion_content, `md5` = MD5(@promotion_content), `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' AND `tenant_id` = 'public'
  AND `content` <> @promotion_content;

-- 自检：销售域首表与券域末表都应出现在 promotion 服务租户表白名单中，两列都应为 1
SELECT
  (SELECT `content` LIKE '%      - sales_staff\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AS promotion_sales_staff_registered,
  (SELECT `content` LIKE '%      - distribution_coupon_exchange\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AS promotion_coupon_exchange_registered;
