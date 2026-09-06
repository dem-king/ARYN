-- ============================================================
-- 限时秒杀 + 限时折扣 DDL（Boot 模式，统一库 aryn_boot）
-- 创建日期：2026-07-31
-- ============================================================

USE `aryn_boot`;

DROP TABLE IF EXISTS `seckill_activity`;
DROP TABLE IF EXISTS `seckill_session`;
DROP TABLE IF EXISTS `seckill_goods`;
DROP TABLE IF EXISTS `seckill_order`;
DROP TABLE IF EXISTS `discount_activity`;
DROP TABLE IF EXISTS `discount_goods`;

-- ============ 限时秒杀 ============

CREATE TABLE `seckill_activity` (
    `id`            bigint       NOT NULL COMMENT '主键',
    `tenant_id`     varchar(64)  NOT NULL DEFAULT '0' COMMENT '租户ID',
    `activity_name` varchar(128) NOT NULL COMMENT '活动名称',
    `start_time`    datetime     NOT NULL COMMENT '活动开始时间',
    `end_time`      datetime     NOT NULL COMMENT '活动结束时间',
    `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '状态:0未开始 1进行中 2已结束 3已暂停',
    `description`   varchar(512) DEFAULT NULL COMMENT '活动描述',
    `create_by`     varchar(64)  DEFAULT NULL COMMENT '创建人',
    `update_by`     varchar(64)  DEFAULT NULL COMMENT '修改人',
    `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `del_flag`      varchar(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除:0正常 1删除',
    `version`       int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

CREATE TABLE `seckill_session` (
    `id`            bigint       NOT NULL COMMENT '主键',
    `tenant_id`     varchar(64)  NOT NULL DEFAULT '0' COMMENT '租户ID',
    `activity_id`   bigint       NOT NULL COMMENT '活动ID',
    `session_name`  varchar(64)  NOT NULL COMMENT '场次名称',
    `start_time`    datetime     NOT NULL COMMENT '场次开始时间',
    `end_time`      datetime     NOT NULL COMMENT '场次结束时间',
    `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '状态:0未开始 1进行中 2已结束',
    `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag`      varchar(1)   NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    KEY `idx_activity` (`activity_id`),
    KEY `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀场次';

CREATE TABLE `seckill_goods` (
    `id`              bigint        NOT NULL COMMENT '主键',
    `tenant_id`       varchar(64)   NOT NULL DEFAULT '0' COMMENT '租户ID',
    `activity_id`     bigint        NOT NULL COMMENT '活动ID',
    `session_id`      bigint        NOT NULL COMMENT '场次ID',
    `spu_id`          varchar(64)   NOT NULL COMMENT '商品SPU ID',
    `sku_id`          varchar(64)   NOT NULL COMMENT '商品SKU ID',
    `seckill_price`   decimal(10,2) NOT NULL COMMENT '秒杀价',
    `seckill_stock`   int           NOT NULL COMMENT '秒杀库存',
    `limit_per_user`  int           NOT NULL DEFAULT 1 COMMENT '每人限购数',
    `sold_count`      int           NOT NULL DEFAULT 0 COMMENT '已售数量',
    `create_time`     datetime      DEFAULT CURRENT_TIMESTAMP,
    `update_time`     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag`        varchar(1)    NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    KEY `idx_session` (`session_id`),
    KEY `idx_sku` (`sku_id`),
    KEY `uk_session_sku` (`session_id`, `sku_id`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品';

CREATE TABLE `seckill_order` (
    `id`               bigint       NOT NULL COMMENT '主键',
    `tenant_id`        varchar(64)  NOT NULL DEFAULT '0',
    `activity_id`      bigint       NOT NULL COMMENT '活动ID',
    `session_id`       bigint       NOT NULL COMMENT '场次ID',
    `seckill_goods_id` bigint       NOT NULL COMMENT '秒杀商品ID',
    `order_id`         varchar(64)  NOT NULL COMMENT '订单ID',
    `user_id`          varchar(64)  NOT NULL COMMENT '用户ID',
    `sku_id`           varchar(64)  NOT NULL COMMENT 'SKU ID',
    `quantity`         int          NOT NULL COMMENT '购买数量',
    `create_time`      datetime     DEFAULT CURRENT_TIMESTAMP,
    `del_flag`         varchar(1)   NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_user_session` (`user_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单记录';

-- ============ 限时折扣 ============

CREATE TABLE `discount_activity` (
    `id`             bigint       NOT NULL COMMENT '主键',
    `tenant_id`      varchar(64)  NOT NULL DEFAULT '0' COMMENT '租户ID',
    `activity_name`  varchar(128) NOT NULL COMMENT '活动名称',
    `start_time`     datetime     NOT NULL COMMENT '开始时间',
    `end_time`       datetime     NOT NULL COMMENT '结束时间',
    `discount_type`  tinyint      NOT NULL COMMENT '折扣类型:1打折 2减价 3固定价',
    `discount_value` decimal(10,2) NOT NULL COMMENT '折扣值',
    `scope`          tinyint      NOT NULL DEFAULT 1 COMMENT '适用范围:1全场 2指定商品',
    `status`         tinyint      NOT NULL DEFAULT 0 COMMENT '状态:0未开始 1进行中 2已结束 3已暂停',
    `description`    varchar(512) DEFAULT NULL,
    `create_by`      varchar(64)  DEFAULT NULL,
    `update_by`      varchar(64)  DEFAULT NULL,
    `create_time`    datetime     DEFAULT CURRENT_TIMESTAMP,
    `update_time`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag`       varchar(1)   NOT NULL DEFAULT '0',
    `version`        int          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='折扣活动';

CREATE TABLE `discount_goods` (
    `id`          bigint       NOT NULL COMMENT '主键',
    `tenant_id`   varchar(64)  NOT NULL DEFAULT '0',
    `activity_id` bigint       NOT NULL COMMENT '活动ID',
    `spu_id`      varchar(64)  NOT NULL COMMENT '商品SPU ID',
    `sku_id`      varchar(64)  NOT NULL COMMENT '商品SKU ID',
    `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
    `del_flag`    varchar(1)   NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    KEY `idx_activity` (`activity_id`),
    KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='折扣商品关联';