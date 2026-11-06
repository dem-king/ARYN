-- 分销结算定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 背景（2026-11-05 需求「销售业绩归属与提成」，见
--       docs/50-需求文档/2026-11-05-销售业绩归属与提成/README.md §4.2.1）：
--   `DistributionSettleJobHandler`（@XxlJob("distributionSettleJobHandler")）每日扫描一次：
--     ① 按 distribution_config.settle_cycle_days 把到期的「待结算」分销订单佣金释放为可提现佣金；
--     ② 释放客户转交奖励（sales_transfer_reward.status=0 → 1，pending→available，先抵 commission_debt）；
--     ③ 重放未应用的分销退款记录。
--   需求复审新增「转交奖励按结算周期释放」，但该任务在 Boot/Cloud 两套基线调度库中都未注册，
--   新环境部署后佣金会永久停留在待结算（无法兑换优惠券），故补此注册脚本。
-- 与 boot 脚本一致性：任务内容同 db/boot/207distribution_settle_job.sql，仅目标库不同。
-- 注册：按 executor_handler 判重（NOT EXISTS），已注册则跳过；不 DROP/TRUNCATE，可重复执行。
-- 注意：本脚本沿用基线 999aryn_job.sql 的执行器分组（job_group=1）；若 promotion 服务独立执行器
--   （appName=aryn-promotion、端口 9010），请在 xxl-job 管理台把任务改挂到对应执行器分组。
-- 执行：mysql -u root -p aryn_job < 210distribution_settle_job.sql

USE `aryn_job`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 分销结算：每日 01:05（Cron 0 5 1 * * ?），释放到期佣金与转交奖励、重放退款
INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '分销结算（待结算佣金/转交奖励释放/退款重放）', NOW(), NOW(), 'admin', '',
       'CRON', '0 5 1 * * ?', 'DO_NOTHING', 'FIRST',
       'distributionSettleJobHandler', '', 'SERIAL_EXECUTION', 1800,
       1, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'distributionSettleJobHandler'
);

-- 自检：应返回 1
SELECT COUNT(*) AS registered
FROM `xxl_job_info`
WHERE `executor_handler` = 'distributionSettleJobHandler';

SET FOREIGN_KEY_CHECKS = 1;
