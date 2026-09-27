-- 秒杀活动/场次状态流转定时任务注册（Boot 单体模式）
--
-- 目标库：aryn_boot_job（xxl-job 调度库）
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
--
-- 背景（2026-09-26 · 小程序秒杀楼层恒为「暂无内容」）：
--   后端已有 seckillStatusJobHandler（SeckillStatusJobHandler）负责把活动/场次
--   状态按时间流转（0 未开始 → 1 进行中 → 2 已结束），但 xxl_job_info 里从未
--   注册该任务，调度器不会触发它：
--     · 活动时间已覆盖当前时刻，列表状态仍显示「未开始」；
--     · 场次 status 永远停在 0，读取「进行中场次」的链路（小程序秒杀楼层、
--       装修预览、秒杀会场页）恒为空。
--   本次服务端读取链路已改为按时间实时推导状态（不依赖落库 status），
--   但管理端活动列表展示、暂停/删除/编辑拦截等逻辑仍消费落库 status，
--   因此该任务必须注册；调度频率与既有两个任务一致（固定 10 秒）。
--
-- 幂等与安全：
--   · 只 INSERT 缺失的调度记录，不触碰既有任务（扫描过期优惠券/订单取消）；
--   · trigger_status=1 自动起调，执行器自动注册，与现有任务同配置。

USE aryn_boot_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '秒杀活动与场次状态流转', NOW(), NOW(), 'admin', '',
       'FIX_RATE', '10', 'DO_NOTHING', 'FIRST',
       'seckillStatusJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'seckillStatusJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
