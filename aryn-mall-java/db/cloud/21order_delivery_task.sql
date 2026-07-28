-- 商城配送履约：订单域
USE `aryn_order`;

CREATE TABLE `order_delivery_task` (
  `id` varchar(32) NOT NULL COMMENT '主键', `task_no` varchar(64) NOT NULL COMMENT '配送任务号',
  `order_id` varchar(32) NOT NULL COMMENT '订单ID', `order_no` varchar(64) NOT NULL COMMENT '订单号快照',
  `status` varchar(32) NOT NULL COMMENT '配送任务状态', `assignee_id` varchar(32) DEFAULT NULL COMMENT '当前配送员ID',
  `assignee_name` varchar(100) DEFAULT NULL COMMENT '当前配送员姓名快照', `assignee_mobile` varchar(32) DEFAULT NULL COMMENT '当前配送员手机号快照',
  `assigned_by` varchar(32) DEFAULT NULL COMMENT '派单管理员ID', `assigned_by_name` varchar(100) DEFAULT NULL COMMENT '派单管理员姓名快照',
  `attempt_no` int NOT NULL DEFAULT 1 COMMENT '配送尝试号', `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `picking_started_at` datetime DEFAULT NULL COMMENT '开始配货时间', `picked_up_at` datetime DEFAULT NULL COMMENT '确认取货时间',
  `delivered_at` datetime DEFAULT NULL COMMENT '送达时间', `return_pending_at` datetime DEFAULT NULL COMMENT '进入待退回时间',
  `returned_at` datetime DEFAULT NULL COMMENT '退回仓库时间', `closed_at` datetime DEFAULT NULL COMMENT '关闭时间',
  `exception_code` varchar(64) DEFAULT NULL COMMENT '当前异常原因编码', `exception_summary` varchar(500) DEFAULT NULL COMMENT '当前异常摘要',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_task_order` (`tenant_id`, `order_id`),
  UNIQUE KEY `uk_delivery_task_no` (`tenant_id`, `task_no`),
  KEY `idx_delivery_task_status` (`tenant_id`, `status`, `create_time`),
  KEY `idx_delivery_task_assignee` (`tenant_id`, `assignee_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商城配送任务';

CREATE TABLE `order_delivery_task_item` (
  `id` varchar(32) NOT NULL COMMENT '主键', `task_id` varchar(32) NOT NULL COMMENT '配送任务ID',
  `order_item_id` varchar(32) NOT NULL COMMENT '订单项ID', `attempt_no` int NOT NULL DEFAULT 1 COMMENT '当前配送尝试号',
  `checked` char(1) NOT NULL DEFAULT '0' COMMENT '是否已核对', `checked_by` varchar(32) DEFAULT NULL COMMENT '核对配送员ID',
  `checked_at` datetime DEFAULT NULL COMMENT '核对时间', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_task_item` (`tenant_id`, `task_id`, `order_item_id`),
  KEY `idx_delivery_task_item_check` (`tenant_id`, `task_id`, `attempt_no`, `checked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商城配送配货明细';

CREATE TABLE `order_delivery_evidence` (
  `id` varchar(32) NOT NULL COMMENT '主键', `task_id` varchar(32) NOT NULL COMMENT '配送任务ID',
  `attempt_no` int NOT NULL COMMENT '配送尝试号', `evidence_type` varchar(32) NOT NULL COMMENT '凭证类型',
  `material_id` varchar(32) NOT NULL COMMENT 'UPMS素材ID', `binding_status` varchar(16) NOT NULL DEFAULT 'BOUND' COMMENT '素材绑定状态',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '展示顺序', `uploaded_by` varchar(32) NOT NULL COMMENT '上传员工ID',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID', `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人', `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_delivery_evidence_material` (`tenant_id`, `material_id`),
  KEY `idx_delivery_evidence_task` (`tenant_id`, `task_id`, `attempt_no`, `evidence_type`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商城配送履约凭证';

CREATE TABLE `order_delivery_task_log` (
  `id` varchar(32) NOT NULL COMMENT '主键', `task_id` varchar(32) NOT NULL COMMENT '配送任务ID',
  `action` varchar(32) NOT NULL COMMENT '操作类型', `from_status` varchar(32) DEFAULT NULL COMMENT '原状态',
  `to_status` varchar(32) DEFAULT NULL COMMENT '目标状态', `attempt_no` int NOT NULL COMMENT '配送尝试号',
  `operator_type` varchar(32) NOT NULL COMMENT '操作人类型', `operator_id` varchar(32) NOT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人名称快照', `reason_code` varchar(64) DEFAULT NULL COMMENT '原因编码',
  `description` varchar(1000) DEFAULT NULL COMMENT '操作说明', `request_id` varchar(64) NOT NULL COMMENT '请求幂等号',
  `detail_payload` json DEFAULT NULL COMMENT '受控操作摘要', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_task_log_request` (`tenant_id`, `task_id`, `action`, `request_id`),
  KEY `idx_delivery_task_log_task` (`tenant_id`, `task_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商城配送任务操作日志';

CREATE TABLE `order_delivery_area` (
  `id` varchar(32) NOT NULL COMMENT '主键', `scope_level` varchar(16) NOT NULL COMMENT '范围层级',
  `area_code` varchar(32) NOT NULL COMMENT '当前层级行政区编码', `province_code` varchar(32) NOT NULL COMMENT '省编码',
  `province_name` varchar(100) NOT NULL COMMENT '省名称快照', `city_code` varchar(32) DEFAULT NULL COMMENT '市编码',
  `city_name` varchar(100) DEFAULT NULL COMMENT '市名称快照', `district_code` varchar(32) DEFAULT NULL COMMENT '区县编码',
  `district_name` varchar(100) DEFAULT NULL COMMENT '区县名称快照', `enabled` char(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID', `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人', `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_delivery_area_scope` (`tenant_id`, `scope_level`, `area_code`),
  KEY `idx_delivery_area_match` (`tenant_id`, `enabled`, `province_code`, `city_code`, `district_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商城配送范围';
