-- ============================================================================
-- 货到付款收款预警定时任务注册
-- 内容：xxl_job_info 注册 codPayRemindJobHandler（FIX_RATE 3600 秒，每小时扫描一次）。
--       提醒阈值本身在 order_config.cod_pay_remind_hours 按租户配置（见
--       111cod_pay_remind_config_incremental.sql），空串关闭提醒。
-- 特性：按 executor_handler 判重，可重复执行；trigger_status=1 起调。
-- 注意：与 db/boot/111cod_pay_remind_job.sql 内容保持一致，仅库名不同。
--       本脚本沿用基线 999aryn_job.sql 的执行器分组（job_group=1），若部署为
--       order 独立执行器需在 xxl-job 管理台改挂分组。
-- 执行：mysql -u root -p aryn_job < 112cod_pay_remind_job.sql
-- ============================================================================

USE aryn_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '货到付款收款预警提醒', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '3600', 'DO_NOTHING', 'FIRST',
       'codPayRemindJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'codPayRemindJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
