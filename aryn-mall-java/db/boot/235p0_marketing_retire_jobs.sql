-- P0 存量营销活动收口（Boot 单体模式 · 调度停用）
-- 目标库：aryn_boot_job（xxl-job 调度库）
-- 背景（2026-11-14 船供 B2B 活动与会员体系重构 · P0 停量）：
--   与 234p0_marketing_retire_data.sql 配套，停用会把已收口活动数据改回去、
--   或持续产生新运营动作的调度任务。必须与数据脚本一起执行。
-- 停用清单（trigger_status 1→0，仅停调度，不删除任务定义，恢复即改回 1）：
--   · seckillScheduleJobHandler  每日排期生成：不停则每 60 秒继续从模板生成新场次
--     （数据层模板已停用是第一道闸，本任务停用是第二道）；
--   · seckillStatusJobHandler    秒杀活动/场次状态流转：存量已全部置已结束，无需再流转；
--   · discountStatusJobHandler   折扣状态流转：存量已全部置已暂停，防止未开始活动被
--     按时间流转回「进行中」。
-- 保留运行（在途数据自然收敛，勿停）：
--   · seckillOrderExpireJobHandler  秒杀预扣单超时释放（在途预扣自然过期）；
--   · groupBuyExpireJobHandler      在途拼团超时失败/退款收敛；
--   · couponUserJobHandler          存量券过期扫描；
--   · promotionLockCompensateJobHandler / mallZoneSyncJobHandler / skuPriceDailySnapshotJobHandler
--     新体系仍依赖，保持不动。
-- 特性：可重复执行；不删除任何任务定义。

USE `aryn_boot_job`;
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

-- 自检（执行后人工核对）：三个任务应全部为 0，保留任务应仍为 1
-- SELECT executor_handler, trigger_status FROM xxl_job_info
--  WHERE executor_handler IN ('seckillScheduleJobHandler','seckillStatusJobHandler',
--    'discountStatusJobHandler','seckillOrderExpireJobHandler','groupBuyExpireJobHandler',
--    'couponUserJobHandler','promotionLockCompensateJobHandler','mallZoneSyncJobHandler');

SET FOREIGN_KEY_CHECKS = 1;
