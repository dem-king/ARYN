-- 秒杀每日排期定时任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
-- 与 db/boot/175seckill_schedule_job.sql 内容保持一致，仅库名与执行器分组说明不同。
--
-- 注意：本脚本沿用基线 999aryn_job.sql 的执行器分组（job_group=1）。若部署为
--   各微服务独立执行器（promotion 服务 appName=aryn-promotion、端口 9010），
--   请在 xxl-job 管理台把该任务改挂到 promotion 执行器分组。
--
-- 背景：秒杀活动支持「每日重复排期」后，需要一条常驻任务按天生成场次实例：
--   1) 生成今天 + 未来 2 天的场次（幂等，唯一键 template_id + biz_date 防重）；
--   2) 场次结束把未售 Redis 库存顺延到当日下一场（carryover_done 防重）；
--   3) 开抢前 10 分钟投递微信订阅提醒（订阅行状态 0→1 抢占防重）。
--   固定 60 秒执行：生成/顺延/提醒都是幂等的轻量扫描，错过整点也能在下一分钟补齐。

USE aryn_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '秒杀每日排期（生成场次/库存顺延/开抢提醒）', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '60', 'DO_NOTHING', 'FIRST',
       'seckillScheduleJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'seckillScheduleJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
