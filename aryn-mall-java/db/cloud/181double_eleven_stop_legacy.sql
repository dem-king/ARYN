-- ============================================================
-- 双十一「航海囤货节」前置：下线日常活动，为 2026-11-01 ~ 11-15 大促让位
-- 目标库：aryn_promotion（Cloud 模式；与 db/boot/179 号停旧内容一致）
-- 创建日期：2026-11-01
--
-- 停旧口径（与各状态 Job 的状态机一致，仅通过数据生效，不改代码）：
--   · 优惠券：关闭领取窗口（领取中心即刻隐藏；已领取用户 validity 领取时已
--     落库 coupon_user.validat_time，不受影响）
--   · 限时折扣：end_time=NOW + status 1→2（DiscountStatusJobHandler 同口径）
--   · 秒杀：status=3 停用（SeckillScheduleJob/开抢提醒订阅均以 3 为闸口），
--     并物理删除未结束场次实例与场次商品（含订阅），释放 uk_template_date
--     占位；Redis 库存 key TTL=场次结束+1h，孤儿 key 自然过期
--   · 拼团：ended_at=NOW + activity_status '1'→'2'（GroupBuyActivityStatusEnum 已结束）
--   · 会员价（promotion_activity）：end_time=NOW（取价按时间窗口判定）
--
-- 特性：全部语句幂等，可重复执行。
-- 执行：mysql -u root -p aryn_promotion < 181double_eleven_stop_legacy.sql
-- ============================================================

USE `aryn_promotion`;
SET NAMES utf8mb4;

-- ---------- 1. 六张日常券停止发放 ----------
UPDATE `coupon_info`
SET `receive_ended_at` = NOW(), `update_by` = 'double11', `update_time` = NOW()
WHERE `id` IN ('3150000000000000101', '3150000000000000102', '3150000000000000103',
               '3150000000000000104', '3150000000000000105', '3150000000000000106')
  AND `del_flag` = '0';

-- ---------- 2. 零食酒水狂欢8折专区：结束 ----------
UPDATE `discount_activity`
SET `end_time` = NOW(), `status` = 2, `update_by` = 'double11', `update_time` = NOW()
WHERE `id` = 3150000000000000402 AND `del_flag` = '0';

-- ---------- 3. 两个日常秒杀活动停用 ----------
UPDATE `seckill_activity`
SET `end_time` = NOW(), `status` = 3, `update_by` = 'double11', `update_time` = NOW()
WHERE `id` IN (3150000000000000301, 3160000000000000301) AND `del_flag` = '0';

-- 3.1 清理旧活动未结束场次的订阅、场次商品、场次实例（物理删除，释放唯一键占位）
DELETE `sg` FROM `seckill_goods` `sg`
JOIN `seckill_session` `s` ON `sg`.`session_id` = `s`.`id`
WHERE `s`.`activity_id` IN (3150000000000000301, 3160000000000000301)
  AND `s`.`end_time` > NOW();
DELETE FROM `seckill_subscribe`
WHERE `activity_id` IN (3150000000000000301, 3160000000000000301);
DELETE FROM `seckill_session`
WHERE `activity_id` IN (3150000000000000301, 3160000000000000301)
  AND `end_time` > NOW();

-- ---------- 4. 四个日常拼团下架 ----------
UPDATE `group_buy_activity`
SET `ended_at` = NOW(), `activity_status` = '2', `update_by` = 'double11', `update_time` = NOW()
WHERE `id` IN (3150000000000000201, 3150000000000000202, 3150000000000000203, 3150000000000000204)
  AND `del_flag` = '0';

-- ---------- 5. 旧会员价活动结束（由 180 号脚本的双11会员价接管价盘） ----------
UPDATE `promotion_activity`
SET `end_time` = NOW(), `update_by` = 'double11', `update_time` = NOW()
WHERE `id` = '2114325311846989826' AND `del_flag` = '0';

-- ---------- 自检 ----------
SELECT '停旧自检：券领取窗口（6 行 receive_ended_at 应≈NOW）' AS step;
SELECT `id`, `coupon_name`, `receive_ended_at` FROM `coupon_info`
WHERE `id` LIKE '31500000000000001%' AND `del_flag` = '0';
SELECT '停旧自检：折扣/秒杀/拼团状态' AS step;
SELECT `id`, `status`, `end_time` FROM `discount_activity` WHERE `id` = 3150000000000000402;
SELECT `id`, `status`, `end_time` FROM `seckill_activity` WHERE `id` IN (3150000000000000301, 3160000000000000301);
SELECT COUNT(*) AS 旧秒杀残留未结束场次 FROM `seckill_session`
WHERE `activity_id` IN (3150000000000000301, 3160000000000000301) AND `end_time` > NOW();
SELECT `id`, `activity_status`, `ended_at` FROM `group_buy_activity`
WHERE `id` IN (3150000000000000201, 3150000000000000202, 3150000000000000203, 3150000000000000204);
SELECT `id`, `end_time` FROM `promotion_activity` WHERE `id` = '2114325311846989826';
