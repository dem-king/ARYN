-- 积分体系增强：全局配置/排除清单/过期记账（Cloud 微服务模式）
--
-- 目标库：aryn_user（points_global_config/points_exclude/points_record）、
--        aryn_order（order_item）、aryn_job（xxl_job_info）、aryn_nacos（租户表白名单）
-- 背景：积分闭环（149）打通了「规则发放 -> 下单抵扣 -> 取消/退款返还」，本次补齐
--       风控四参数与过期治理（对标小象/山姆滚动到期、京东 180 天收紧的行业口径）：
--   1. points_global_config 积分全局规则配置（每租户一行；四参数 NULL=走代码缺省：
--      抵扣上限 20%、无门槛、不封顶、永久有效）；
--   2. points_exclude 积分排除清单（类目/商品黑名单，命中不参与积分抵扣与返积分）；
--   3. points_record 增加 expire_time/remain_point 批次记账列（FIFO 消耗与到期清零）；
--   4. order_item 增加 points_excluded 黑名单命中标记（下单落库，供退款返还剔除）；
--   5. xxl_job_info 注册 pointsExpireJobHandler（每日凌晨扫描到期批次并清零）。
-- 特性：可重复执行；仅新增表/列/索引/种子与任务登记，不修改或删除存量数据。
-- 依赖：4aryn_user.sql（points_record）、7aryn_order.sql（order_item）、
--       149points_closed_loop_incremental.sql（points_record.biz_no/order_item.points_price）、
--       999aryn_job.sql（xxl_job_info）。
-- 关联：boot 模式对应增量 160points_global_config_incremental.sql，语义一致，仅库名分段不同。
--
-- 执行：mysql -u root -p < 160points_global_config_incremental.sql
--       （含 aryn_user/aryn_order/aryn_job 三段 USE，连接库任意，需账号对三库有权限）

USE `aryn_user`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 1. 积分全局规则配置表（每租户一行，与按场景配置的 points_config 互补）
-- ===========================================================================
CREATE TABLE IF NOT EXISTS `points_global_config` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `validity_days` int DEFAULT NULL COMMENT '积分有效期（天），0=永不过期；NULL=未配置走缺省永久',
  `deduct_max_ratio` decimal(5, 2) DEFAULT NULL COMMENT '单笔抵扣上限比例（百分数，20.00=20%）；NULL=走缺省20%',
  `min_use_amount` decimal(10, 2) DEFAULT NULL COMMENT '最低使用门槛（积分可抵基数满X元可用，0=无门槛）；NULL=走缺省无门槛',
  `max_deduct_amount` decimal(10, 2) DEFAULT NULL COMMENT '单笔抵扣金额封顶（元，0=不封顶）；NULL=走缺省不封顶',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id` varchar(32) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  PRIMARY KEY (`id`), KEY `idx_points_global_config_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='积分全局规则配置';

-- ===========================================================================
-- 2. 积分排除清单表（品类黑名单：命中不参与积分抵扣与返积分）
-- ===========================================================================
CREATE TABLE IF NOT EXISTS `points_exclude` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `target_type` char(1) NOT NULL COMMENT '排除类型：1-类目；2-商品（SPU）',
  `target_id` varchar(64) NOT NULL COMMENT '类目ID/商品（SPU）ID',
  `target_name` varchar(255) DEFAULT NULL COMMENT '名称快照（展示用，便于类目/商品改名后仍可读）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id` varchar(32) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  PRIMARY KEY (`id`), KEY `idx_points_exclude_target` (`tenant_id`, `target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='积分排除清单';

-- ===========================================================================
-- 3. 存量发放行回填：按「先入先出」把用户当前余额分配到历史发放批次，
--    分到余额的批次补 365 天到期窗口（新老分批：存量积分从本脚本执行日起算，
--    365 为代码缺省有效期；租户后续改配置只影响新发放批次）。
--    幂等：仅处理 remain_point IS NULL 的行，重跑只补漏网行；
--    用户已删除（user_info.del_flag=1）的批次不回填、永不参与扫描。
-- ===========================================================================
UPDATE `points_record` pr
JOIN (
    SELECT t.id,
           LEAST(GREATEST(COALESCE(u.point, 0) - (t.running_sum - t.change_point), 0), t.change_point) AS remain_alloc
    FROM (
        SELECT id, user_id, change_point,
               SUM(change_point) OVER (PARTITION BY user_id ORDER BY create_time, id) AS running_sum
        FROM `points_record`
        WHERE change_type = '1' AND remain_point IS NULL AND del_flag = '0'
    ) t
    JOIN `user_info` u ON u.id = t.user_id AND u.del_flag = '0'
) alloc ON alloc.id = pr.id
SET pr.remain_point = alloc.remain_alloc,
    pr.expire_time = IF(alloc.remain_alloc > 0, DATE_ADD(NOW(), INTERVAL 365 DAY), NULL)
WHERE pr.remain_point IS NULL;

-- ===========================================================================
-- 4. points_record 批次记账列（发放类流水：到期时间+剩余量，FIFO 与过期清零）
-- ===========================================================================
SET @add_points_record_expire_time = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `points_record` ADD COLUMN `expire_time` datetime NULL COMMENT ''过期时间（发放类流水；NULL=永不过期，如有效期配置为0）'' AFTER `biz_no`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'points_record' AND COLUMN_NAME = 'expire_time'
);
PREPARE stmt FROM @add_points_record_expire_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_points_record_remain_point = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `points_record` ADD COLUMN `remain_point` int NULL COMMENT ''剩余可用积分（发放类流水，FIFO扣减与过期清零的记账位；NULL=存量未回填行）'' AFTER `expire_time`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'points_record' AND COLUMN_NAME = 'remain_point'
);
PREPARE stmt FROM @add_points_record_remain_point; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 5. 过期扫描支撑索引（定时任务按 expire_time 扫到期批次）
-- ===========================================================================
SET @add_points_record_expire_idx = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `points_record` ADD INDEX `idx_points_record_expire` (`expire_time`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'points_record' AND INDEX_NAME = 'idx_points_record_expire'
);
PREPARE stmt FROM @add_points_record_expire_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：user 库 points_record 应有 expire_time/remain_point；
--       在册用户未回填的发放行应为 0
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'points_record' AND COLUMN_NAME IN ('expire_time', 'remain_point');

SELECT COUNT(*) AS unbackfilled_income_rows
FROM `points_record` pr
WHERE pr.change_type = '1' AND pr.remain_point IS NULL AND pr.del_flag = '0'
  AND EXISTS (SELECT 1 FROM `user_info` u WHERE u.id = pr.user_id AND u.del_flag = '0');

USE `aryn_order`;
SET NAMES utf8mb4;

-- ===========================================================================
-- 6. order_item 积分黑名单命中标记（下单落库，供退款返还剔除）
-- ===========================================================================
SET @add_order_item_points_excluded = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `points_excluded` char(1) NOT NULL DEFAULT ''0'' COMMENT ''积分排除标记：1=命中积分黑名单，不参与积分抵扣与返积分'' AFTER `points_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'points_excluded'
);
PREPARE stmt FROM @add_order_item_points_excluded; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：order 库 order_item 应有 points_excluded
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'points_excluded';

USE `aryn_job`;
SET NAMES utf8mb4;

-- ===========================================================================
-- 7. XXL-JOB：积分过期清零定时任务（CRON 每日凌晨 1 点，按租户扫描到期批次）
--    注册方式与 112cod_pay_remind_job.sql 一致：按 executor_handler 判重，
--    trigger_status=1 起调；本脚本沿用基线 999aryn_job.sql 的执行器分组
--    （job_group=1），若部署为 user 独立执行器需在 xxl-job 管理台改挂分组。
-- ===========================================================================
INSERT INTO `xxl_job_info` (
    `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
    `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT 1, '积分过期清零', NOW(), NOW(), 'admin', '',
       'CRON', '0 0 1 * * ?', 'DO_NOTHING', 'FIRST',
       'pointsExpireJobHandler', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', 'GLUE代码初始化',
       NOW(), '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'pointsExpireJobHandler'
);

USE `aryn_nacos`;
SET NAMES utf8mb4;

-- ===========================================================================
-- 8. 租户表白名单：aryn-user-biz-dev.yml 的 hx.tenant.tables 追加
--    points_global_config / points_exclude。不登记则租户拦截器不为新表注入
--    tenant_id 过滤（跨租户读写），与 boot 模式 application.yml 保持同构。
--    幂等写法与 156/127 号一致：探针判重 + 锚点 REPLACE + 同步重算 md5；
--    锚点用 points_record 行（基线即有、位置稳定）；锚点不匹配时本次不改动，
--    由末尾自检 registered_tenant_tables 输出 0 暴露，不会静默成功。
-- ===========================================================================
SET @user_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-user-biz-dev.yml'
    AND `group_id` = 'DEFAULT_GROUP'
    AND `tenant_id` = 'public'
  LIMIT 1
);

SET @user_probe = CONCAT(IFNULL(@user_content, ''), '\n');
SET @user_content = IF(
  @user_content IS NULL
    OR @user_probe LIKE '%      - points_global_config\n%',
  @user_content,
  IF(
    @user_probe LIKE '%      - points_record\n%',
    REPLACE(
      @user_content,
      '      - points_record\n      - sign_in_config',
      '      - points_record\n      - points_global_config\n      - points_exclude\n      - sign_in_config'
    ),
    @user_content
  )
);

UPDATE `config_info`
SET `content` = @user_content,
    `md5` = MD5(@user_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-user-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `tenant_id` = 'public'
  AND @user_content IS NOT NULL;

-- 自检：两张新表都应已登记（count=2；基于改写后的 content 判定）
SELECT
  (CONCAT(@user_content, '\n') LIKE '%      - points_global_config\n%')
  + (CONCAT(@user_content, '\n') LIKE '%      - points_exclude\n%') AS registered_tenant_tables;

SET FOREIGN_KEY_CHECKS = 1;