-- SPU 社证统计定时任务注册（Boot 单体模式）
--
-- 目标库：aryn_boot_job（xxl-job 调度库）
-- 背景（2026-11-08 · 「商品卡营销标签」P2）：
--   `SpuSalesStatJobHandler`（@XxlJob("spuSalesStatJobHandler")）每日 02:30
--   按租户把近 30 天窗口的去重买家数/复购买家数/回购率写进 stat_daily_spu_sales
--   （214 号脚本建表，boot 库 aryn_boot）；参数 days 可回填最近 N 天（1-90，
--   首次部署建议传 14 预铺「近14天」社证底数，正常调度留空=只统计昨天）。
-- 注册：按 executor_handler 判重（NOT EXISTS），已注册则跳过；可重复执行。
-- 注意：执行器分组沿用基线 3aryn_boot_job.sql（job_group=1）；若部署为独立执行器，
--       请在 xxl-job 管理台把任务改挂到对应执行器分组。
-- 执行：mysql -u root -p aryn_boot_job < 215spu_sales_stat_job.sql

USE `aryn_boot_job`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- SPU 社证统计：每日 02:30（Cron 0 30 2 * * ?），T+1 统计昨天、支持参数回填
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
