-- 悦航购船舶与靠港计划域增量迁移（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 42vessel_context_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 船舶档案
CREATE TABLE IF NOT EXISTS `vessel_info` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `vessel_name` varchar(128) NOT NULL COMMENT '船舶中文名称',
  `vessel_name_en` varchar(255) DEFAULT NULL COMMENT '船舶英文名称',
  `imo_code` varchar(32) DEFAULT NULL COMMENT 'IMO 编号',
  `call_sign` varchar(32) DEFAULT NULL COMMENT '呼号',
  `vessel_type` char(2) DEFAULT NULL COMMENT '船舶类型：1集装箱 2散货 3油轮 4杂货 5其他',
  `flag_state` varchar(64) DEFAULT NULL COMMENT '船旗国',
  `dwt` decimal(12,2) DEFAULT NULL COMMENT '载重吨',
  `crew_capacity` int DEFAULT NULL COMMENT '船员定员',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1在营 0停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vessel_info_imo` (`tenant_id`,`imo_code`),
  KEY `idx_vessel_info_name` (`tenant_id`,`vessel_name`),
  KEY `idx_vessel_info_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='船舶档案';

-- 船舶成员（商城用户与船舶的绑定关系）
CREATE TABLE IF NOT EXISTS `vessel_member` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `vessel_id` varchar(32) NOT NULL COMMENT '船舶ID',
  `user_id` varchar(32) NOT NULL COMMENT '商城用户ID',
  `member_role` char(2) NOT NULL DEFAULT '2' COMMENT '成员角色：1发起人/船长授权 2普通船员 3采购确认人',
  `can_edit` char(2) NOT NULL DEFAULT '1' COMMENT '可编辑共享购物车：1是 0否',
  `can_confirm` char(2) NOT NULL DEFAULT '0' COMMENT '可确认提交订单：1是 0否',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '在船状态：1在船 0离船',
  `join_time` datetime DEFAULT NULL COMMENT '加入时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vessel_member_unique` (`tenant_id`,`vessel_id`,`user_id`),
  KEY `idx_vessel_member_user` (`tenant_id`,`user_id`),
  KEY `idx_vessel_member_vessel` (`tenant_id`,`vessel_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='船舶成员';

-- 靠港计划
CREATE TABLE IF NOT EXISTS `vessel_call` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `vessel_id` varchar(32) NOT NULL COMMENT '船舶ID',
  `port_code` varchar(32) NOT NULL COMMENT '港口编码',
  `port_name` varchar(128) NOT NULL COMMENT '港口名称',
  `berth` varchar(128) DEFAULT NULL COMMENT '泊位',
  `eta` datetime NOT NULL COMMENT '预计到港时间',
  `etd` datetime NOT NULL COMMENT '预计离港时间',
  `delivery_window_start` datetime DEFAULT NULL COMMENT '配送时间窗开始',
  `delivery_window_end` datetime DEFAULT NULL COMMENT '配送时间窗结束',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '靠港状态：1计划中 2靠泊中 3已完成 4已取消',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_vessel_call_vessel_eta` (`tenant_id`,`vessel_id`,`eta`),
  KEY `idx_vessel_call_port_eta` (`tenant_id`,`port_code`,`eta`),
  KEY `idx_vessel_call_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='靠港计划';

SET FOREIGN_KEY_CHECKS = 1;
