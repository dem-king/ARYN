-- 消息中心客服会话维护与通知分发恢复定时任务注册（Boot 单体模式）
--
-- 目标库：aryn_boot_job（xxl-job 调度库）
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
--
-- 背景（2026-11-04 客服功能审计）：
--   后端此前只有 @XxlJob 处理器，从未写入 xxl_job_info，调度中心不会触发，导致
--   ① 24 小时无消息客服会话永不自动关闭，坐席 current_active_count 只涨不跌，最终打满
--      接待上限让整个客服池停止分配；
--   ② 中断的站内信分发任务不会恢复。
--   本次按仓库既有任务注册惯例（参考 111cod_pay_remind_job.sql / 200ip_region_refresh_job.sql）
--   补齐两条任务，并在任务实现内按租户遍历（RemoteTenantService.list）。
--
-- 执行：mysql -u root -p aryn_boot_job < 201message_job_register.sql

USE aryn_boot_job;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 客服会话维护：每分钟扫描，关闭 24h 无消息会话、回收待领取池、校正坐席负载快照
INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '客服会话维护（超时关闭/待领取回收/负载校正）', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '60', 'DO_NOTHING', 'FIRST',
       'conversationMaintenanceJob', '', 'SERIAL_EXECUTION', 300,
       1, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'conversationMaintenanceJob'
);

-- 通知分发恢复：每 5 分钟扫描超时 PENDING/RUNNING 任务并恢复
INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '通知分发任务恢复（超时 PENDING/RUNNING 重试）', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '300', 'DO_NOTHING', 'FIRST',
       'noticeDispatchRecoveryJob', '', 'SERIAL_EXECUTION', 300,
       1, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'noticeDispatchRecoveryJob'
);

SET FOREIGN_KEY_CHECKS = 1;
