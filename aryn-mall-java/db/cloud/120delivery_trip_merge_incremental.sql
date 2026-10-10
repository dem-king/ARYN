-- ============================================================================
-- 出车单归并：一个司机一辆车只留一张在途单（Cloud 微服务模式）
-- 目标库：aryn_order
-- 背景：出车单原先由「派单动作」创建——每派一批新建一张。同一司机的订单分几次派，
--       就产生多张在途出车单，各自独立配货、独立出发；司机端排序只能排「单内」，
--       跨单的两个订单无法排序（工作台也只显示被点开的那一张）。
--       代码侧已改为「一个司机一辆车 = 一张在途单」：派单并入该司机当前这趟车
--       （含已出发的配送中趟次），车开出后仍可继续加单。
-- 口径：把同一租户 + 同一配送员的多张在途出车单（1待配货/2配货中/3配送中）
--       归并到**创建时间最早**的那一张：
--         · 其余趟次的订单改属最早那张（按「已出发趟次优先、再按原 sort_no」重排 sort_no）；
--         · 被拉空的趟次置为 4已完成 并补 complete_time，避免残留空卡片；
--         · 目标趟次的 task_count 按实际任务数重算。
--       归并后订单的配送状态、取货确认状态一律不变 —— 只改「挂在哪张单上」。
-- 安全边界（重要）：
--       · 只按 (tenant_id, staff_id) 分组，绝不跨司机/跨租户合并；
--       · 只处理状态 1/2/3，已完成/已取消的趟次不参与；
--       · 归并后同一订单仍只属于一张单（uk_delivery_task_order 保证一单一任务，
--         本脚本只 UPDATE trip_id，不新增任务行）。
-- 特性：可重复执行（归并后每个司机只剩一张在途单，不再满足 HAVING 多张的条件）；
--       不执行 DROP/TRUNCATE；不改任务状态、金额、收货信息。
-- 执行：mysql -u root -p aryn_order < 120delivery_trip_merge_incremental.sql
-- 与 db/boot/119delivery_trip_merge_incremental.sql 内容保持一致，仅库名不同。
-- 
-- ============================================================================

USE `aryn_order`;

SET NAMES utf8mb4;

-- ============ 执行前预览：将被归并的司机与趟次（供人工核对） ============
-- 预期：下面列出的每个 staff_id 都有 2 张及以上在途单；keep_trip_id 是保留的目标单。
SELECT t.`staff_id`,
       t.`tenant_id`,
       COUNT(*)                        AS active_trip_count,
       MIN(t.`create_time`)            AS keep_create_time,
       GROUP_CONCAT(t.`trip_no` ORDER BY t.`create_time`) AS trip_nos
FROM `delivery_trip` t
WHERE t.`del_flag` = '0'
  AND t.`status` IN ('1', '2', '3')
GROUP BY t.`staff_id`, t.`tenant_id`
HAVING COUNT(*) > 1;

-- ============ 1. 确定每个司机的「保留趟次」：创建时间最早的一张 ============
-- 用临时表固定下来，后续步骤都引用它，保证同一脚本内多次执行结果稳定。
DROP TEMPORARY TABLE IF EXISTS `tmp_delivery_trip_keep`;
CREATE TEMPORARY TABLE `tmp_delivery_trip_keep` AS
SELECT t.`staff_id`,
       t.`tenant_id`,
       SUBSTRING_INDEX(MIN(CONCAT(DATE_FORMAT(t.`create_time`, '%Y%m%d%H%i%s'), '|', t.`id`)), '|', -1)
           AS `keep_trip_id`
FROM `delivery_trip` t
WHERE t.`del_flag` = '0'
  AND t.`status` IN ('1', '2', '3')
GROUP BY t.`staff_id`, t.`tenant_id`;

-- ============ 2. 把其余趟次的订单改属保留趟次并重排顺序 ============
-- 排序规则：先「已出发（3）」的订单 —— 它们的货已经在车上，理应排在前面；
--           再按原趟次创建时间、原 sort_no，尽量保持司机已排好的相对顺序。
DROP TEMPORARY TABLE IF EXISTS `tmp_delivery_task_merge`;
CREATE TEMPORARY TABLE `tmp_delivery_task_merge` AS
SELECT task.`id` AS `task_id`,
       keep.`keep_trip_id`,
       ROW_NUMBER() OVER (
           PARTITION BY keep.`keep_trip_id`
           ORDER BY (trip.`status` = '3') DESC, trip.`create_time`, task.`sort_no`, task.`id`
       ) AS `new_sort_no`
FROM `delivery_task` task
JOIN `delivery_trip` trip ON trip.`id` = task.`trip_id` AND trip.`del_flag` = '0'
JOIN `tmp_delivery_trip_keep` keep
     ON keep.`staff_id` = trip.`staff_id` AND keep.`tenant_id` = trip.`tenant_id`
WHERE task.`del_flag` = '0'
  AND trip.`status` IN ('1', '2', '3');
-- 注意：这里把「保留趟次自己的任务」也纳入重排，保证合并后 sort_no 连续无空洞。

UPDATE `delivery_task` task
JOIN `tmp_delivery_task_merge` merged ON merged.`task_id` = task.`id`
SET task.`trip_id`    = merged.`keep_trip_id`,
    task.`sort_no`    = merged.`new_sort_no`,
    task.`update_time` = NOW()
WHERE task.`del_flag` = '0'
  AND (task.`trip_id` <> merged.`keep_trip_id` OR task.`sort_no` <> merged.`new_sort_no`);

-- ============ 3. 被拉空的趟次置为已完成，避免残留空卡片 ============
UPDATE `delivery_trip` trip
JOIN `tmp_delivery_trip_keep` keep
     ON keep.`staff_id` = trip.`staff_id` AND keep.`tenant_id` = trip.`tenant_id`
SET trip.`status`        = '4',
    trip.`complete_time` = COALESCE(trip.`complete_time`, NOW()),
    trip.`task_count`    = 0,
    trip.`update_time`   = NOW()
WHERE trip.`del_flag` = '0'
  AND trip.`status` IN ('1', '2', '3')
  AND trip.`id` <> keep.`keep_trip_id`;

-- ============ 4. 保留趟次回写实际任务数 ============
UPDATE `delivery_trip` trip
JOIN `tmp_delivery_trip_keep` keep ON keep.`keep_trip_id` = trip.`id`
SET trip.`task_count` = (
        SELECT COUNT(*) FROM `delivery_task` task
        WHERE task.`del_flag` = '0' AND task.`trip_id` = trip.`id`
    ),
    trip.`update_time` = NOW()
WHERE trip.`del_flag` = '0';

DROP TEMPORARY TABLE IF EXISTS `tmp_delivery_task_merge`;
DROP TEMPORARY TABLE IF EXISTS `tmp_delivery_trip_keep`;

-- ============ 验证：不应再有「同一司机多张在途单」 ============
-- SELECT staff_id, tenant_id, COUNT(*) AS active_trip_count
-- FROM delivery_trip
-- WHERE del_flag='0' AND status IN ('1','2','3')
-- GROUP BY staff_id, tenant_id HAVING COUNT(*) > 1;

SET FOREIGN_KEY_CHECKS = 1;
