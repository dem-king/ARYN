-- 折扣/拼团定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
-- 与 db/boot/115promotion_job_register.sql 内容保持一致，仅库名不同。
--
-- 背景（2026-10-07 · 营销三件套上线核查）：
--   后端已有两个 Handler 但从未在 xxl_job_info 注册，调度器不会触发：
--     · discountStatusJobHandler（DiscountStatusJobHandler）：折扣活动状态按时间流转
--       （0 未开始 → 1 进行中 → 2 已结束）。C 端折扣会场与下单取价均要求落库 status=1，
--       不注册则活动到点不会自动上线（只能运营在管理端手动「启用」）。
--     · groupBuyExpireJobHandler（GroupBuyExpireJobHandler）：拼团超时失败处理（回滚占坑
--       库存、已付款成员发起退款）与拼团活动自动结束。不注册则开团/参团占用的商品库存
--       永不回滚、成团失败已付款订单不退款。
--   调度频率：折扣状态与秒杀状态一致（固定 10 秒）；拼团超时按分钟粒度（固定 60 秒）。
--
-- 注意：本脚本沿用基线 999aryn_job.sql 的执行器分组（job_group=1）。若部署为
--   各微服务独立执行器（promotion 服务 appName=aryn-promotion、端口 9010），
--   请在 xxl-job 管理台把该任务改挂到 promotion 执行器分组。

USE aryn_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '折扣活动状态流转', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '10', 'DO_NOTHING', 'FIRST',
       'discountStatusJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'discountStatusJobHandler'
);

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '拼团超时失败与活动结束处理', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '60', 'DO_NOTHING', 'FIRST',
       'groupBuyExpireJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'groupBuyExpireJobHandler'
);

-- 自检：应返回 2 行
SELECT `id`, `job_desc`, `executor_handler`, `schedule_type`, `schedule_conf`, `trigger_status`
FROM `xxl_job_info`
WHERE `executor_handler` IN ('discountStatusJobHandler', 'groupBuyExpireJobHandler');

SET FOREIGN_KEY_CHECKS = 1;
