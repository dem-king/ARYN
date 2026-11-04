-- 会员/出清专区系统活动兜底同步定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 与 boot/184mall_zone_sync_job.sql 对应，内容一致。
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
--
-- 执行：mysql -u root -p aryn_job < 186mall_zone_sync_job.sql

USE aryn_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '会员/出清专区系统活动兜底同步', NOW(), NOW(), 'admin', '',
       'CRON', '0 10 3 * * ?', 'DO_NOTHING', 'FIRST',
       'mallZoneSyncJobHandler', '', 'SERIAL_EXECUTION', 0,
       1, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'mallZoneSyncJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
