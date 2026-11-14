-- 企业等级月度对账任务注册（Boot 单体模式）
--
-- 目标库：aryn_boot_job（xxl-job 调度库）
-- 背景（2026-11-14 船供 B2B 活动与会员体系重构 · P1）：
--   企业轨等级日常变化由订单完成事件驱动（MemberOrderCompleteListener →
--   EnterpriseMemberServiceImpl.accumulateOrderGrowth → recalculateSubjectLevel），
--   本任务兜底两类漂移：等级配置（门槛/档位）调整后的存量主体收敛、异常场景漏算
--   的历史主体。建议每月 1 日凌晨执行（CRON 0 30 2 1 * ?），幂等可重跑。
--
-- 幂等：只 INSERT 缺失的调度记录，不触碰既有任务。可重复执行。
-- 关联：cloud 模式对应 254enterprise_level_recalc_job.sql（aryn_job 库，同配置）。

USE aryn_boot_job;
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

-- 自检（执行后人工核对）：任务应存在且 trigger_status=1
-- SELECT executor_handler, schedule_conf, trigger_status FROM xxl_job_info
--  WHERE executor_handler = 'enterpriseLevelRecalcJobHandler';

SET FOREIGN_KEY_CHECKS = 1;
