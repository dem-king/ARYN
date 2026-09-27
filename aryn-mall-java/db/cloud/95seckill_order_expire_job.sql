-- 秒杀预扣超时释放定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
-- 与 db/boot/95seckill_order_expire_job.sql 内容保持一致，仅库名不同。
--
-- 背景：秒杀预扣激活后，未支付订单需有释放兜底（归还 Redis 秒杀库存与单人限购名额）。
--   订单取消任务（30 分钟）已自行释放，本任务取 45 分钟兜底异常单。
--
-- 注意：本脚本沿用基线 999aryn_job.sql 的执行器分组（job_group=1）。若部署为
--   各微服务独立执行器（promotion 服务 appName=aryn-promotion、端口 9010），
--   请在 xxl-job 管理台把该任务改挂到 promotion 执行器分组。

USE aryn_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '秒杀预扣超时释放', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '60', 'DO_NOTHING', 'FIRST',
       'seckillOrderExpireJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'seckillOrderExpireJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
