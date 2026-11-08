-- SKU 价格快照定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 与 boot 217 号脚本同内容（除库上下文）；product-biz 已通过 P3 接入 @ArynEnableXxlJob
-- （Nacos aryn-product-biz-dev.yml 配 xxl.job.port=9011，见 227 号）。
-- 沿用 job_group=1 惯例（与 223 号 SPU 社证统计一致）：当前演示/单执行器部署下
-- 全部 handler 挂同一分组；若 product 部署为独立执行器，
-- 请在 xxl-job 管理台把任务改挂到对应执行器分组。
--
-- 执行：mysql -u root -p aryn_job < 226sku_price_daily_snapshot_job.sql

USE `aryn_job`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- SKU 价格快照：每日 01:30（Cron 0 30 1 * * ?），写当日非个性化到手价
INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, 'SKU价格快照（近N天最低价判定底表，每日01:30）', NOW(), NOW(), 'admin', '',
       'CRON', '0 30 1 * * ?', 'DO_NOTHING', 'FIRST',
       'skuPriceDailySnapshotJobHandler', '', 'SERIAL_EXECUTION', 1800,
       1, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'skuPriceDailySnapshotJobHandler'
);

-- 自检：应返回 1
SELECT COUNT(*) AS registered
FROM `xxl_job_info`
WHERE `executor_handler` = 'skuPriceDailySnapshotJobHandler';

SET FOREIGN_KEY_CHECKS = 1;
