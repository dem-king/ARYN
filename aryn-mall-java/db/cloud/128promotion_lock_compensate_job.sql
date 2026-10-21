-- 营销锁释放补偿任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 与 boot/126promotion_lock_compensate_job.sql 对应，内容一致。
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
--
-- 背景（2026-10-14 · 船供活动治理三期）：
--   取消/超时关单释放营销锁失败时仅留日志，泄漏的 LOCKED 锁会冻住新人价
--   「在途唯一」判定。新增 promotionLockCompensateJobHandler（OrderJobHandler）
--   扫描近 7 天已取消订单幂等补释放，此处注册调度（10 分钟一次）。

USE aryn_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '营销锁释放补偿（已取消订单补释放）', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '600', 'DO_NOTHING', 'FIRST',
       'promotionLockCompensateJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'promotionLockCompensateJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
