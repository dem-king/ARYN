-- 出车单结清口径修正与存量僵尸出车单回填（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：出车单原先只在客户「签收」（delivery_task.status=6）时才被置为已完成，
--       而司机侧履约终点其实是「已送达」（status=5）。客户不点确认收货，
--       出车单就一直停在 3配送中：
--         · 配送员工作台「当前出车单」永远显示「配送中」，与出车单详情的
--           「全部配送完成 / 已送达」自相矛盾；
--         · 配送员下一趟车无法体现为「当前出车单」（getActiveTrip 取最新在途）。
--       代码侧已改为：任一任务进入结清状态后，若该出车单已无未结清任务，
--       即置为 4已完成并写 complete_time；客户签收不再影响出车单收车。
--       本脚本按同一口径回填存量出车单。
-- 结清状态：5已送达 6已签收 7已取消（异常关闭/退回确认）；
--           8异常、9待退回不结清——异常等管理员改派或关闭，退回等商品回仓确认。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有 complete_time。
-- 执行：mysql -u root -p aryn_boot < 104delivery_trip_settle_backfill.sql

USE `aryn_boot`;

SET NAMES utf8mb4;

-- ============ delivery_trip：回填全部任务已结清的存量出车单 ============
-- 只处理 1待配货/2配货中/3配送中；已完结的出车单不进 WHERE。
-- 子查询 HAVING 要求该出车单至少有一个任务（零任务的出车单按代码口径不收车），
-- 且所有任务都在结清状态；complete_time 取任务侧最晚的终结时间，缺失时回落 NOW()。

UPDATE `delivery_trip` t
JOIN (
    SELECT `trip_id`,
           MAX(COALESCE(`sign_time`, `arrive_time`, `close_time`)) AS `settled_time`
    FROM `delivery_task`
    WHERE `del_flag` = '0'
      AND `trip_id` IS NOT NULL
      AND `trip_id` <> ''
    GROUP BY `trip_id`
    HAVING SUM(CASE WHEN `status` NOT IN ('5', '6', '7') THEN 1 ELSE 0 END) = 0
) s ON s.`trip_id` = t.`id`
SET t.`status` = '4',
    t.`complete_time` = COALESCE(t.`complete_time`, s.`settled_time`, NOW()),
    t.`update_time` = NOW()
WHERE t.`del_flag` = '0'
  AND t.`status` IN ('1', '2', '3');

-- 验证：不应再有「任务全结清但仍未完成」的出车单
-- SELECT t.id, t.trip_no, t.status FROM delivery_trip t
-- JOIN (SELECT trip_id FROM delivery_task WHERE del_flag='0' AND trip_id IS NOT NULL AND trip_id <> ''
--       GROUP BY trip_id HAVING SUM(CASE WHEN status NOT IN ('5','6','7') THEN 1 ELSE 0 END)=0) s
--   ON s.trip_id = t.id
-- WHERE t.del_flag='0' AND t.status IN ('1','2','3');

SET FOREIGN_KEY_CHECKS = 1;
