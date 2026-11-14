-- P0 存量营销活动收口（Cloud 微服务模式 · 营销库数据）
-- 目标库：aryn_promotion
-- 背景：与 boot/234p0_marketing_retire_data.sql 同源，cloud 模式下营销数据落在
--   aryn_promotion 库（菜单在 aryn_upms、调度在 aryn_job，分别见 250/251 号脚本）。
-- 语义（与 boot 版一致，详见 234 号脚本头注）：
--   1) 秒杀活动全部结束、排期模板停用、未开始/进行中场次结束；
--   2) 限时折扣未开始/进行中 → 已暂停；
--   3) 拼团草稿/进行中 → 已结束；
--   4) 优惠券模板全部禁用；
--   5) 统一引擎手工活动（4/5/7/9）暂停，系统托管专区活动（10/11）保留。
-- 特性：可重复执行；不 DROP/TRUNCATE；不物理删除任何数据。
-- 注意：必须与 251p0_marketing_retire_jobs.sql（aryn_job 调度停用）一起执行。

USE `aryn_promotion`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 秒杀活动全部结束
UPDATE `seckill_activity`
SET `status` = 2, `end_time` = NOW(), `update_by` = 'system', `update_time` = NOW()
WHERE `status` IN (0, 1) AND `del_flag` = '0';

-- 2. 秒杀排期模板全部停用
UPDATE `seckill_session_template`
SET `status` = 1, `update_time` = NOW()
WHERE `status` = 0 AND `del_flag` = '0';

-- 3. 已生成的未开始/进行中场次一并结束
UPDATE `seckill_session`
SET `status` = 2, `update_time` = NOW()
WHERE `status` IN (0, 1) AND `del_flag` = '0';

-- 4. 限时折扣：未开始/进行中 → 已暂停
UPDATE `discount_activity`
SET `status` = 3, `update_by` = 'system', `update_time` = NOW()
WHERE `status` IN (0, 1) AND `del_flag` = '0';

-- 5. 拼团：草稿/进行中 → 已结束
UPDATE `group_buy_activity`
SET `activity_status` = '2', `update_by` = 'system', `update_time` = NOW()
WHERE `activity_status` IN ('0', '1') AND `del_flag` = '0';

-- 6. 优惠券模板全部禁用
UPDATE `coupon_info`
SET `status` = '1', `update_by` = 'system', `update_time` = NOW()
WHERE `status` = '0' AND `del_flag` = '0';

-- 7. 统一引擎手工活动暂停（保留系统托管专区 10/11）
UPDATE `promotion_activity`
SET `status` = '3', `update_by` = 'system', `update_time` = NOW()
WHERE `activity_type` IN ('4', '5', '7', '9')
  AND `status` IN ('1', '2')
  AND `del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;

-- 自检（执行后人工核对，同 boot 234 号脚本自检段）
