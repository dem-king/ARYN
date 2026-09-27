-- 秒杀预扣超时释放定时任务注册（Boot 单体模式）
--
-- 目标库：aryn_boot_job（xxl-job 调度库）
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
--
-- 背景（2026-09-26 · 秒杀改为随购物车结算）：
--   下单前预扣秒杀库存（ArynOrderCreateBeforeEventListener）此前从未被激活，
--   seckill_order 预扣记录恒为空，连带本任务、支付/退款处理都空转。
--   激活预扣后必须有释放兜底：未支付订单超时后要归还 Redis 秒杀库存与单人限购名额，
--   否则活动名额只减不增。订单取消（orderCancelJobHandler，30 分钟）已自行释放秒杀预扣，
--   本任务取 45 分钟作为兜底，处理取消路径未覆盖的异常单。
--
-- 幂等与安全：
--   · 只 INSERT 缺失的调度记录，不触碰既有任务；
--   · trigger_status=1 自动起调，执行器自动注册，与现有任务同配置（固定 60 秒扫描）。

USE aryn_boot_job;

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
