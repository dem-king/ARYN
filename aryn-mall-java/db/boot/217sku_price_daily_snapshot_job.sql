-- SKU 价格快照定时任务注册（Boot 单体模式）
--
-- 目标库：aryn_boot_job（xxl-job 调度库）
-- 背景（2026-11-08 · 「商品卡营销标签」P3）：
--   `SkuPriceDailySnapshotJobHandler`（@XxlJob("skuPriceDailySnapshotJobHandler")）每日 01:30
--   按租户对全部在售 SKU 写当日价格快照（216 号脚本建表，boot 库 aryn_boot）。
--   价格是时点数据无法回填历史，不支持参数回填——快照表从部署日起逐日积累，
--   「近N天最低价」随窗口自然灰度放量（首日表空自然不出标）。
-- 注册：按 executor_handler 判重（NOT EXISTS），已注册则跳过；可重复执行。
-- 注意：执行器分组沿用基线 3aryn_boot_job.sql（job_group=1）；若部署为独立执行器，
--       请在 xxl-job 管理台把任务改挂到对应执行器分组。
-- 执行：mysql -u root -p aryn_boot_job < 217sku_price_daily_snapshot_job.sql

USE `aryn_boot_job`;

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
