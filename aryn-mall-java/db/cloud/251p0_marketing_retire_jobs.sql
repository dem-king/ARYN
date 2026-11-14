-- P0 存量营销活动收口（Cloud 微服务模式 · 调度停用）
-- 目标库：aryn_job（xxl-job 调度库）
-- 背景：与 boot/235p0_marketing_retire_jobs.sql 同源，cloud 模式调度库为 aryn_job。
-- 停用：seckillScheduleJobHandler / seckillStatusJobHandler / discountStatusJobHandler。
-- 保留：seckillOrderExpireJobHandler / groupBuyExpireJobHandler / couponUserJobHandler /
--       promotionLockCompensateJobHandler / mallZoneSyncJobHandler / skuPriceDailySnapshotJobHandler。
-- 特性：可重复执行；不删除任何任务定义。

USE `aryn_job`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

UPDATE `xxl_job_info`
SET `trigger_status` = 0, `trigger_next_time` = 0, `update_time` = NOW()
WHERE `executor_handler` IN (
  'seckillScheduleJobHandler',
  'seckillStatusJobHandler',
  'discountStatusJobHandler'
)
  AND `trigger_status` = 1;

-- 自检（执行后人工核对，同 boot 235 号脚本自检段）

SET FOREIGN_KEY_CHECKS = 1;
