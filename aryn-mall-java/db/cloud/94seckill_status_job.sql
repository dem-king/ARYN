-- 秒杀活动/场次状态流转定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
-- 与 db/boot/94seckill_status_job.sql 内容保持一致，仅库名不同。
--
-- 背景：seckillStatusJobHandler 位于 aryn-promotion-biz，负责把秒杀活动/场次
--   状态按时间流转（0 未开始 → 1 进行中 → 2 已结束）。此前 xxl_job_info 未注册
--   该任务，场次 status 恒为 0，C 端/装修预览的「进行中场次」接口恒为空。
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
SELECT 1, '秒杀活动与场次状态流转', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '10', 'DO_NOTHING', 'FIRST',
       'seckillStatusJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'seckillStatusJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
