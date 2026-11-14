-- 企业等级月度对账任务注册（Cloud 微服务模式）
--
-- 目标库：aryn_job（xxl-job 调度库）
-- 背景：与 boot/238enterprise_level_recalc_job.sql 同源，cloud 调度库为 aryn_job，同配置。
-- 幂等：只 INSERT 缺失的调度记录。可重复执行。

USE aryn_job;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '企业等级月度对账（全部绑定主体重算）', NOW(), NOW(), 'admin', '',
       'CRON', '0 30 2 1 * ?', 'DO_NOTHING', 'FIRST',
       'enterpriseLevelRecalcJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'enterpriseLevelRecalcJobHandler'
);

-- 自检（执行后人工核对，同 boot 238 号脚本自检段）

SET FOREIGN_KEY_CHECKS = 1;
