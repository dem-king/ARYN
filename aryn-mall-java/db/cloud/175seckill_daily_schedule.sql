-- 秒杀每日排期与开抢提醒（Cloud 微服务模式，营销库 aryn_promotion）
--
-- 目标库：aryn_promotion
-- 背景：秒杀从「一次性场次」升级为「每日重复排期」：
--   运营配置场次模板（每天几点到几点 + 周几 + 每场商品/计划库存），系统按天生成场次实例；
--   场次结束未售库存顺延到当日下一场；用户可订阅微信开抢提醒。
-- 特性：可重复执行（information_schema 守卫 + CREATE TABLE IF NOT EXISTS），不触碰存量数据。
-- 与 db/boot/174seckill_daily_schedule.sql 内容保持一致，仅库名不同。
--
-- 执行：mysql -u root -p aryn_promotion < 175seckill_daily_schedule.sql

USE `aryn_promotion`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. seckill_activity：排期方式 ----------
SET @schedule_type_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seckill_activity' AND COLUMN_NAME = 'schedule_type'
);
SET @schedule_type_ddl := IF(@schedule_type_exists = 0,
  'ALTER TABLE `seckill_activity` ADD COLUMN `schedule_type` tinyint NOT NULL DEFAULT 0 COMMENT ''排期方式:0一次性场次 1每日重复'' AFTER `status`',
  'SELECT 1');
PREPARE schedule_type_stmt FROM @schedule_type_ddl;
EXECUTE schedule_type_stmt;
DEALLOCATE PREPARE schedule_type_stmt;

-- ---------- 2. seckill_session：模板来源 / 业务日期 / 顺延标记 ----------
SET @template_id_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seckill_session' AND COLUMN_NAME = 'template_id'
);
SET @template_id_ddl := IF(@template_id_exists = 0,
  'ALTER TABLE `seckill_session` ADD COLUMN `template_id` bigint NULL COMMENT ''排期模板ID（每日重复生成时回填）'' AFTER `activity_id`',
  'SELECT 1');
PREPARE template_id_stmt FROM @template_id_ddl;
EXECUTE template_id_stmt;
DEALLOCATE PREPARE template_id_stmt;

SET @biz_date_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seckill_session' AND COLUMN_NAME = 'biz_date'
);
SET @biz_date_ddl := IF(@biz_date_exists = 0,
  'ALTER TABLE `seckill_session` ADD COLUMN `biz_date` date NULL COMMENT ''业务日期（每日重复生成时回填）'' AFTER `template_id`',
  'SELECT 1');
PREPARE biz_date_stmt FROM @biz_date_ddl;
EXECUTE biz_date_stmt;
DEALLOCATE PREPARE biz_date_stmt;

SET @carryover_done_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seckill_session' AND COLUMN_NAME = 'carryover_done'
);
SET @carryover_done_ddl := IF(@carryover_done_exists = 0,
  'ALTER TABLE `seckill_session` ADD COLUMN `carryover_done` tinyint NOT NULL DEFAULT 0 COMMENT ''未售库存是否已顺延:0否 1是'' AFTER `status`',
  'SELECT 1');
PREPARE carryover_done_stmt FROM @carryover_done_ddl;
EXECUTE carryover_done_stmt;
DEALLOCATE PREPARE carryover_done_stmt;

SET @uk_template_date_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seckill_session' AND INDEX_NAME = 'uk_template_date'
);
SET @uk_template_date_ddl := IF(@uk_template_date_exists = 0,
  'ALTER TABLE `seckill_session` ADD UNIQUE KEY `uk_template_date` (`template_id`, `biz_date`, `del_flag`)',
  'SELECT 1');
PREPARE uk_template_date_stmt FROM @uk_template_date_ddl;
EXECUTE uk_template_date_stmt;
DEALLOCATE PREPARE uk_template_date_stmt;

SET @idx_biz_date_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seckill_session' AND INDEX_NAME = 'idx_biz_date'
);
SET @idx_biz_date_ddl := IF(@idx_biz_date_exists = 0,
  'ALTER TABLE `seckill_session` ADD KEY `idx_biz_date` (`biz_date`, `start_time`)',
  'SELECT 1');
PREPARE idx_biz_date_stmt FROM @idx_biz_date_ddl;
EXECUTE idx_biz_date_stmt;
DEALLOCATE PREPARE idx_biz_date_stmt;

-- ---------- 3. 每日排期模板 ----------
CREATE TABLE IF NOT EXISTS `seckill_session_template` (
  `id`           bigint      NOT NULL COMMENT '主键',
  `tenant_id`    varchar(64) NOT NULL DEFAULT '0' COMMENT '租户ID',
  `activity_id`  bigint      NOT NULL COMMENT '活动ID',
  `session_name` varchar(64) NOT NULL COMMENT '场次名称',
  `start_time`   time        NOT NULL COMMENT '每日开始时间',
  `end_time`     time        NOT NULL COMMENT '每日结束时间',
  `weekdays`     varchar(20) NOT NULL DEFAULT '1,2,3,4,5,6,7' COMMENT '生效星期(1-7逗号分隔，1=周一)',
  `status`       tinyint     NOT NULL DEFAULT 0 COMMENT '状态:0启用 1停用',
  `create_time`  datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `del_flag`     varchar(1)  NOT NULL DEFAULT '0' COMMENT '逻辑删除:0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_activity` (`activity_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀每日排期模板';

-- ---------- 4. 排期商品模板（每场计划库存） ----------
CREATE TABLE IF NOT EXISTS `seckill_goods_template` (
  `id`             bigint        NOT NULL COMMENT '主键',
  `tenant_id`      varchar(64)   NOT NULL DEFAULT '0' COMMENT '租户ID',
  `activity_id`    bigint        NOT NULL COMMENT '活动ID',
  `template_id`    bigint        NOT NULL COMMENT '排期模板ID',
  `spu_id`         varchar(64)   NOT NULL COMMENT '商品SPU ID',
  `sku_id`         varchar(64)   NOT NULL COMMENT '商品SKU ID',
  `seckill_price`  decimal(10,2) NOT NULL COMMENT '秒杀价',
  `seckill_stock`  int           NOT NULL COMMENT '每场计划库存（每日配额=各场之和）',
  `limit_per_user` int           NOT NULL DEFAULT 1 COMMENT '每人每场限购数',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `del_flag`       varchar(1)    NOT NULL DEFAULT '0' COMMENT '逻辑删除:0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_template` (`template_id`),
  KEY `idx_sku` (`sku_id`),
  UNIQUE KEY `uk_template_sku` (`template_id`, `sku_id`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀排期商品模板';

-- ---------- 5. 开抢提醒订阅 ----------
CREATE TABLE IF NOT EXISTS `seckill_subscribe` (
  `id`             bigint       NOT NULL COMMENT '主键',
  `tenant_id`      varchar(64)  NOT NULL DEFAULT '0' COMMENT '租户ID',
  `activity_id`    bigint       NOT NULL COMMENT '活动ID',
  `session_id`     bigint       NOT NULL COMMENT '场次ID',
  `mall_user_id`   varchar(64)  NOT NULL COMMENT '商城用户ID',
  `template_id`    varchar(128) DEFAULT NULL COMMENT '微信订阅模板ID',
  `status`         tinyint      NOT NULL DEFAULT 0 COMMENT '状态:0待发送 1已投递 2已取消 3发送失败',
  `fail_reason`    varchar(255) DEFAULT NULL COMMENT '失败原因',
  `subscribe_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
  `send_time`      datetime     DEFAULT NULL COMMENT '投递时间',
  `create_time`    datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `del_flag`       varchar(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除:0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_user` (`session_id`, `mall_user_id`, `del_flag`),
  KEY `idx_status_session` (`status`, `session_id`),
  KEY `idx_tenant_user` (`tenant_id`, `mall_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀开抢提醒订阅';

SET FOREIGN_KEY_CHECKS = 1;
