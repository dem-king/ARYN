-- ============================================================================
-- 个人新人活动购买场景修复 — Cloud 微服务模式
--
-- 目标库：aryn_promotion
-- 背景（2026-11-12 需求「新人首单双轨与采购主体改造」P0）：
--   个人新人专享活动 3170000000000000501 的 purchase_scene 为空，
--   促销引擎不会按场景过滤，个人新人价会漏到船供/企业订单。
--   P0 只把该活动收敛到个人场景（1），不新建船供/企业首单活动；
--   首单活动 228/240 必须等 P1 引擎 firstOrderOnly 上线后再执行。
--
-- 幂等：只在值不为 '1' 时更新；不触碰其它活动、不碰 SKU/价格/库存。
-- 执行：mysql -u root -p aryn_promotion < 239buyer_org_personal_scene_fix_incremental.sql
-- ============================================================================

USE `aryn_promotion`;
SET NAMES utf8mb4;

UPDATE `promotion_activity`
SET `purchase_scene` = '1', `update_time` = NOW()
WHERE `id` = '3170000000000000501'
  AND (`purchase_scene` IS NULL OR `purchase_scene` <> '1');

-- 自检：期望 purchase_scene = 1
SELECT `id`, `activity_name`, `purchase_scene`, `status`
FROM `promotion_activity`
WHERE `id` = '3170000000000000501';

SET FOREIGN_KEY_CHECKS = 1;
