-- 拼团活动表
CREATE TABLE IF NOT EXISTS `group_buy_activity` (
  `id` bigint NOT NULL COMMENT '主键',
  `activity_name` varchar(128) NOT NULL COMMENT '活动名称',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `original_price` decimal(10,2) NOT NULL COMMENT '商品原价',
  `group_price` decimal(10,2) NOT NULL COMMENT '拼团价',
  `group_num` int NOT NULL COMMENT '成团人数(最少2人)',
  `limit_num` int DEFAULT 0 COMMENT '限购数量(0=不限)',
  `virtual_num` int DEFAULT 0 COMMENT '虚拟成团人数(展示用)',
  `activity_status` char(1) DEFAULT '0' COMMENT '活动状态:0草稿,1进行中,2已结束',
  `started_at` datetime NOT NULL COMMENT '活动开始时间',
  `ended_at` datetime NOT NULL COMMENT '活动结束时间',
  `group_expire_hours` int DEFAULT 24 COMMENT '拼团过期时间(小时)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '逻辑删除:0正常,1删除',
  `version` int DEFAULT 0 COMMENT '版本号',
  `tenant_id` varchar(32) DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_group_buy_activity_spu` (`spu_id`),
  KEY `idx_group_buy_activity_status` (`activity_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团活动';

-- 拼团记录表(每个开团实例)
CREATE TABLE IF NOT EXISTS `group_buy_record` (
  `id` bigint NOT NULL COMMENT '主键',
  `activity_id` bigint NOT NULL COMMENT '拼团活动ID',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `group_price` decimal(10,2) NOT NULL COMMENT '拼团价',
  `group_num` int NOT NULL COMMENT '成团人数',
  `current_num` int DEFAULT 0 COMMENT '当前参团人数',
  `leader_user_id` varchar(32) NOT NULL COMMENT '团长用户ID',
  `group_status` char(1) DEFAULT '0' COMMENT '拼团状态:0拼团中,1成功,2失败',
  `expire_at` datetime NOT NULL COMMENT '拼团过期时间',
  `success_at` datetime DEFAULT NULL COMMENT '成团成功时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '逻辑删除:0正常,1删除',
  `version` int DEFAULT 0 COMMENT '版本号',
  `tenant_id` varchar(32) DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_group_buy_record_activity` (`activity_id`),
  KEY `idx_group_buy_record_status` (`group_status`),
  KEY `idx_group_buy_record_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团记录';

-- 拼团参与记录表
CREATE TABLE IF NOT EXISTS `group_buy_member` (
  `id` bigint NOT NULL COMMENT '主键',
  `record_id` bigint NOT NULL COMMENT '拼团记录ID',
  `activity_id` bigint NOT NULL COMMENT '拼团活动ID',
  `user_id` varchar(32) NOT NULL COMMENT '参团用户ID',
  `order_id` varchar(32) DEFAULT NULL COMMENT '关联订单ID',
  `member_status` char(1) DEFAULT '0' COMMENT '参团状态:0待付款,1已付款,2已取消',
  `is_leader` char(1) DEFAULT '0' COMMENT '是否团长:0否,1是',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '逻辑删除:0正常,1删除',
  `version` int DEFAULT 0 COMMENT '版本号',
  `tenant_id` varchar(32) DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_group_buy_member_record` (`record_id`),
  KEY `idx_group_buy_member_user` (`user_id`),
  UNIQUE KEY `uk_record_user` (`record_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团参与记录';
