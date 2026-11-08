-- SPU 社证统计定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 与 boot 215 号脚本同内容（除库上下文）；order-biz 已启用 @ArynEnableXxlJob，
-- 沿用 job_group=1（aryn-order 订单执行器）。
--
-- 执行：mysql -u root -p aryn_job < 223spu_sales_stat_job.sql

USE `aryn_job`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, 'SPU社证统计（月下单人数/回头客/回购率 T+1 快照）', NOW(), NOW(), 'admin', '',
       'CRON', '0 30 2 * * ?', 'DO_NOTHING', 'FIRST',
       'spuSalesStatJobHandler', '', 'SERIAL_EXECUTION', 1800,
       1, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'spuSalesStatJobHandler'
);

-- 自检：应返回 1
SELECT COUNT(*) AS registered
FROM `xxl_job_info`
WHERE `executor_handler` = 'spuSalesStatJobHandler';

SET FOREIGN_KEY_CHECKS = 1;
