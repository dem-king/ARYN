-- ============================================================================
-- 靠港计划状态流转定时任务注册（Cloud 微服务模式）
-- 内容：xxl_job_info 注册 vesselCallStatusJobHandler（FIX_RATE 60 秒，每分钟扫描一次）。
--       任务把 vessel_call.status 按时间推进：已到 ETA 且未过 ETD → 2靠泊中；
--       已过 ETD → 3已完成（1/2 均可推 3）。与 db/boot/112vessel_call_status_job.sql
--       内容保持一致，仅库名不同。
-- 特性：按 executor_handler 判重，可重复执行；trigger_status=1 起调；
--       已取消(4)的记录不触碰。
-- 执行：mysql -u root -p aryn_job < 113vessel_call_status_job.sql
--
-- 注意：本脚本沿用基线 999aryn_job.sql 的执行器分组（job_group=1）。若部署为
--   各微服务独立执行器（vessel 服务 appName=aryn-vessel、执行器端口 9011），
--   请在 xxl-job 管理台把该任务改挂到 vessel 执行器分组。
-- ============================================================================

USE aryn_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '靠港计划状态流转', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '60', 'DO_NOTHING', 'FIRST',
       'vesselCallStatusJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'vesselCallStatusJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
