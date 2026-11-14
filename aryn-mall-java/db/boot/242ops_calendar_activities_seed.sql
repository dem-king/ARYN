-- 运营回填②：11月下旬备货档期 + 五类活动 + 捡漏补档 + E2E 清理（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-15 运营方案确认执行）：
--   活动归属「11月下旬船供备货档期」（立即生效至 11-30），五类活动落到真实 SKU：
--     · 阶梯价整箱囤货（婴儿水/每日坚果/牛腩块，按件数档）
--     · 满量优惠双维度（满1000减60 / 满3000减200 / 满50件减100，仅船供场景）
--     · 档期特供每周爆款（樱桃 24.9 / 葡萄 15.9 / 汾酒 359，合计限量 80，每人 2）
--     · 买赠满 299 送椰子水×2
--   捡漏区补 5 个临期档（7/15/30 天三档，到期按执行日+N 天动态计算）；
--   E2E 测试数据清理：删 E2E 协议价（201/202）与 E2E 档期+特供（210/211）——
--   避免 E2E 协议价 19.90 与新档期特供 SKU（樱桃 24.90）同源压制；
--   保留 E2E 船成员（001）/企业绑定（220）/靠港（230）供演示登录与结算验证。
--
-- 幂等：INSERT IGNORE + 条件 UPDATE；不删 E2E 之外的任何数据。
-- 关联：cloud 模式对应 258ops_calendar_activities_seed.sql（分库分段同数据）。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 采购档期（立即生效至 11-30）
INSERT IGNORE INTO `promo_calendar`
  (`id`, `calendar_name`, `anchor_type`, `anchor_ref`, `start_time`, `end_time`, `status`, `remark`,
   `tenant_id`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('9000000000000000300', '11月下旬船供备货档期', '1', NULL,
   '2026-11-15 00:00:00', '2026-11-30 23:59:59', '1', '运营首期回填档期',
   '1590229800633634816', 'ops', NOW(), 'ops', NOW(), '0');

-- 2. 阶梯价「整箱囤货」（指定 SKU，三商品两档）
INSERT IGNORE INTO `promotion_activity`
  (`id`, `activity_name`, `activity_type`, `scope_type`, `scope_value`, `rules`, `priority`,
   `coupon_stackable`, `points_stackable`, `start_time`, `end_time`, `status`, `publish_time`,
   `calendar_id`, `tenant_id`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('9000000000000000301', '整箱囤货·按件数阶梯价', '4', '2',
   '10740000000000000228,9750000000000000133,1074000000000000018',
   '{"skuLadders":[{"skuId":"10740000000000000228","ladders":[{"minQty":5,"unitPrice":41.90},{"minQty":10,"unitPrice":38.90}]},{"skuId":"9750000000000000133","ladders":[{"minQty":5,"unitPrice":63.90},{"minQty":10,"unitPrice":59.90}]},{"skuId":"1074000000000000018","ladders":[{"minQty":5,"unitPrice":29.90},{"minQty":10,"unitPrice":27.90}]}]}',
   0, '1', '1', '2026-11-15 00:00:00', '2026-11-30 23:59:59', '2', NOW(), '9000000000000000300',
   '1590229800633634816', 'ops', NOW(), 'ops', NOW(), '0');

-- 3. 满量优惠「整船备货满减」（金额与件数双档并存，仅船供场景）
INSERT IGNORE INTO `promotion_activity`
  (`id`, `activity_name`, `activity_type`, `scope_type`, `scope_value`, `rules`, `priority`,
   `coupon_stackable`, `points_stackable`, `start_time`, `end_time`, `status`, `publish_time`,
   `calendar_id`, `tenant_id`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('9000000000000000302', '整船备货·满量优惠', '7', '4', '2',
   '{"tiers":[{"minAmount":1000,"discountAmount":60},{"minAmount":3000,"discountAmount":200},{"minQty":50,"discountAmount":100}],"maxDiscountAmount":300}',
   0, '1', '0', '2026-11-15 00:00:00', '2026-11-30 23:59:59', '2', NOW(), '9000000000000000300',
   '1590229800633634816', 'ops', NOW(), 'ops', NOW(), '0');

-- 4. 档期特供「每周爆款」（合计限量 80 件，每人限 2）
INSERT IGNORE INTO `promotion_activity`
  (`id`, `activity_name`, `activity_type`, `scope_type`, `scope_value`, `rules`, `priority`,
   `coupon_stackable`, `points_stackable`, `per_user_limit`, `start_time`, `end_time`, `status`, `publish_time`,
   `calendar_id`, `total_quota`, `consumed_quota`, `tenant_id`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('9000000000000000303', '每周爆款·档期特供', '12', '2',
   '10740000000000000201,1074000000000000004,9750000000000000085',
   '{"skus":[{"skuId":"10740000000000000201","price":24.90},{"skuId":"1074000000000000004","price":15.90},{"skuId":"9750000000000000085","price":359.00}]}',
   0, '0', '0', 2, '2026-11-15 00:00:00', '2026-11-30 23:59:59', '2', NOW(),
   '9000000000000000300', 80, 0,
   '1590229800633634816', 'ops', NOW(), 'ops', NOW(), '0');

-- 5. 买赠「满额赠礼」（满 299 送椰子水×2）
INSERT IGNORE INTO `promotion_activity`
  (`id`, `activity_name`, `activity_type`, `scope_type`, `scope_value`, `rules`, `priority`,
   `coupon_stackable`, `points_stackable`, `start_time`, `end_time`, `status`, `publish_time`,
   `calendar_id`, `tenant_id`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('9000000000000000304', '满额赠礼·椰子水', '5', '1', NULL,
   '{"minAmount":299,"gifts":[{"skuId":"10740000000000000201","quantity":2}]}',
   0, '1', '1', '2026-11-15 00:00:00', '2026-11-30 23:59:59', '2', NOW(), '9000000000000000300',
   '1590229800633634816', 'ops', NOW(), 'ops', NOW(), '0');

-- 6. 捡漏区补 5 个临期档（到期按执行日 +7/15/30 天动态计算）
UPDATE `goods_spu`
SET `is_clearance_item` = '1', `clearance_price` = 4.90,
    `clearance_expire_time` = DATE_ADD(NOW(), INTERVAL 7 DAY),
    `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '9740000000000000316' AND `del_flag` = '0';
UPDATE `goods_spu`
SET `is_clearance_item` = '1', `clearance_price` = 14.90,
    `clearance_expire_time` = DATE_ADD(NOW(), INTERVAL 15 DAY),
    `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '9740000000000000311' AND `del_flag` = '0';
UPDATE `goods_spu`
SET `is_clearance_item` = '1', `clearance_price` = 8.90,
    `clearance_expire_time` = DATE_ADD(NOW(), INTERVAL 15 DAY),
    `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '9740000000000000021' AND `del_flag` = '0';
UPDATE `goods_spu`
SET `is_clearance_item` = '1', `clearance_price` = 11.90,
    `clearance_expire_time` = DATE_ADD(NOW(), INTERVAL 30 DAY),
    `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '9740000000000000320' AND `del_flag` = '0';
UPDATE `goods_spu`
SET `is_clearance_item` = '1', `clearance_price` = 8.90,
    `clearance_expire_time` = DATE_ADD(NOW(), INTERVAL 30 DAY),
    `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '9740000000000000225' AND `del_flag` = '0';

-- 7. E2E 清理：删 E2E 协议价与 E2E 档期/特供（避免压制新档期特供；逻辑删除可恢复）
UPDATE `price_policy` SET `del_flag` = '1', `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '9000000000000000201';
UPDATE `price_policy_item` SET `del_flag` = '1', `update_by` = 'ops', `update_time` = NOW()
WHERE `policy_id` = '9000000000000000201';
UPDATE `promo_calendar` SET `del_flag` = '1', `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '9000000000000000210';
UPDATE `promotion_activity` SET `del_flag` = '1', `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '9000000000000000211';

-- 自检（执行后人工核对）
-- SELECT id, name, status FROM promo_calendar WHERE id='9000000000000000300';
-- SELECT id, activity_type, status, calendar_id FROM promotion_activity
--  WHERE id IN ('9000000000000000301','9000000000000000302','9000000000000000303','9000000000000000304');
-- SELECT COUNT(*) AS clearance_live FROM goods_spu
--  WHERE is_clearance_item='1' AND clearance_expire_time > NOW() AND del_flag='0';  -- 期望 7
-- SELECT del_flag FROM price_policy WHERE id='9000000000000000201';  -- 期望 1

SET FOREIGN_KEY_CHECKS = 1;
