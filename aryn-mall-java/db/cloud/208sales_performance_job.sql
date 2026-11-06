-- 销售业绩月度发放台账定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 背景（2026-11-05 需求「销售业绩归属与提成」，见
--       docs/50-需求文档/2026-11-05-销售业绩归属与提成/IMPLEMENTATION_PLAN.md 4.5/6）：
--   `SalesPayoutJobHandler` 每日 01:10 扫描一次：
--     ① 待转交认领过期置为失效（status=2）；
--     ② 重放未应用的退款记录；
--     ③ 到达 sales_config.monthly_cutoff_day（默认每月 1 日）时生成「上月」销售提成发放台账
--        （sales_payout），并把已过退款风险观察期的业绩单置为可发放。
--   任务实现内按租户遍历（RemoteTenantService.list），与 DistributionSettleJobHandler 口径一致。
-- 与 boot 脚本一致性：任务内容同 db/boot/204sales_performance_job.sql，仅目标库不同。
-- 注册：按 executor_handler 判重（NOT EXISTS），已注册则跳过；不 DROP/TRUNCATE。
-- 注意：任务需在 promotion 服务实现 @XxlJob("salesPayoutJobHandler") 并启用 XXL-JOB；
--   本脚本沿用基线 999aryn_job.sql 的执行器分组（job_group=1），若 promotion 服务独立执行器
--   （appName=aryn-promotion、端口 9010），请在 xxl-job 管理台把任务改挂到对应执行器分组。
-- 执行：mysql -u root -p aryn_job < 208sales_performance_job.sql

USE aryn_job;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 销售提成台账：每日 01:10（Cron 0 10 1 * * ?），认领过期 / 退款重放 / 月末生成上月台账
INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '销售提成台账（认领过期/退款重放/生成上月台账）', NOW(), NOW(), 'admin', '',
       'CRON', '0 10 1 * * ?', 'DO_NOTHING', 'FIRST',
       'salesPayoutJobHandler', '', 'SERIAL_EXECUTION', 1800,
       1, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'salesPayoutJobHandler'
);

-- 自检：应返回 1
SELECT COUNT(*) AS registered
FROM `xxl_job_info`
WHERE `executor_handler` = 'salesPayoutJobHandler';

SET FOREIGN_KEY_CHECKS = 1;
