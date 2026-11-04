-- ip2region 归属地数据刷新定时任务注册（Boot 单体模式）
--
-- 目标库：aryn_boot_job（xxl-job 调度库）
-- 特性：可重复执行；不 DROP/TRUNCATE；按 executor_handler 判重，已注册则跳过。
--
-- 背景（2026-11-04）：登录日志/在线会话/操作日志的归属地改为 ip2region 离线库（本地 xdb），
--   官方数据「不定期」更新（近期约每月级别），商用离线数据承诺每月 >=1 次。
--   本任务每日低峰检查远端 ETag：有更新才下载 → 校验（结构 + 样本 IP）→ 原子替换 →
--   重建查询服务；任一步失败回滚并保留旧数据。
--   多副本部署用「分片广播」：各实例都会触发，文件锁保证同一时刻只有一个实例真正下载，
--   其余实例拿不到锁直接跳过（共享数据目录时不会重复下载 11MB）。
--   任务参数留空按 ETag 判断；手动强制刷新可在调度中心把任务参数改成 force。
--
-- 执行：mysql -u root -p aryn_boot_job < 200ip_region_refresh_job.sql

USE aryn_boot_job;

INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, 'IP 归属地数据刷新（ip2region xdb 校验更新）', NOW(), NOW(), 'admin', '',
       'CRON', '0 30 3 * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST',
       'ipRegionDataRefreshJobHandler', '', 'SERIAL_EXECUTION', 300,
       1, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'ipRegionDataRefreshJobHandler'
);

SET FOREIGN_KEY_CHECKS = 1;
