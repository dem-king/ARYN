-- 用户日订单汇总定时任务注册（Boot 单体模式）
-- 目标库：aryn_boot_job
-- 特性：可重复执行；按 executor_handler 判重，已注册则跳过。
-- 默认参数 1 汇总昨天；手动传 1-90 可回填最近 N 天。

USE aryn_boot_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '用户日订单汇总与回填', NOW(), NOW(), 'admin', '',
       'CRON', '0 10 1 * * ?', 'DO_NOTHING', 'FIRST',
       'orderDailyStatJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'orderDailyStatJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
