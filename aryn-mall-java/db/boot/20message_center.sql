-- 站内信与客服会话
USE `aryn_boot`;

DROP TABLE IF EXISTS `message_assignment_log`;
DROP TABLE IF EXISTS `message_agent`;
DROP TABLE IF EXISTS `message_chat`;
DROP TABLE IF EXISTS `message_participant`;
DROP TABLE IF EXISTS `message_conversation`;
DROP TABLE IF EXISTS `message_dispatch_task`;
DROP TABLE IF EXISTS `message_recipient`;
DROP TABLE IF EXISTS `message_notice`;

CREATE TABLE `message_notice` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要',
  `content` text NOT NULL COMMENT '纯文本正文',
  `category` varchar(64) NOT NULL COMMENT '通知分类',
  `priority` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
  `source_type` varchar(64) DEFAULT NULL COMMENT '来源类型',
  `source_key` varchar(128) DEFAULT NULL COMMENT '来源幂等键',
  `target_types` varchar(64) NOT NULL COMMENT '目标端快照',
  `audience_snapshot` json DEFAULT NULL COMMENT '受众条件快照',
  `sender_type` varchar(32) NOT NULL COMMENT '发送人类型',
  `sender_id` varchar(32) NOT NULL COMMENT '发送人ID',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '发送人名称快照',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `card_payload` json DEFAULT NULL COMMENT '安全业务卡片快照',
  `jump_type` varchar(32) DEFAULT NULL COMMENT '安全跳转类型',
  `jump_payload` json DEFAULT NULL COMMENT '安全跳转参数',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_notice_source` (`tenant_id`, `source_type`, `source_key`),
  KEY `idx_message_notice_status_time` (`tenant_id`, `status`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内通知';

CREATE TABLE `message_recipient` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `message_id` varchar(32) NOT NULL COMMENT '通知ID',
  `recipient_type` varchar(32) NOT NULL COMMENT '收件人类型',
  `recipient_id` varchar(32) NOT NULL COMMENT '收件人ID',
  `recipient_name` varchar(100) DEFAULT NULL COMMENT '收件人名称快照',
  `read_status` char(1) NOT NULL DEFAULT '0' COMMENT '已读状态',
  `read_time` datetime DEFAULT NULL COMMENT '已读时间',
  `received_time` datetime NOT NULL COMMENT '接收时间',
  `inbox_status` varchar(16) NOT NULL DEFAULT 'VISIBLE' COMMENT '收件箱状态',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_recipient_identity` (`tenant_id`, `message_id`, `recipient_type`, `recipient_id`),
  KEY `idx_message_recipient_inbox` (`tenant_id`, `recipient_type`, `recipient_id`, `inbox_status`, `received_time`),
  KEY `idx_message_recipient_unread` (`tenant_id`, `recipient_type`, `recipient_id`, `read_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知收件人';

CREATE TABLE `message_dispatch_task` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `message_id` varchar(32) NOT NULL COMMENT '通知ID',
  `target_type` varchar(32) NOT NULL COMMENT '目标端',
  `audience_type` varchar(32) NOT NULL COMMENT '受众类型',
  `audience_condition` json DEFAULT NULL COMMENT '受众条件',
  `cursor_value` varchar(64) DEFAULT NULL COMMENT '分发游标',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `estimated_count` bigint NOT NULL DEFAULT 0 COMMENT '预计人数',
  `success_count` bigint NOT NULL DEFAULT 0 COMMENT '成功数',
  `failure_count` bigint NOT NULL DEFAULT 0 COMMENT '失败数',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '最后心跳时间',
  `error_summary` varchar(1000) DEFAULT NULL COMMENT '错误摘要',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_dispatch_target` (`tenant_id`, `message_id`, `target_type`),
  KEY `idx_message_dispatch_recovery` (`tenant_id`, `status`, `last_heartbeat_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知分发任务';

CREATE TABLE `message_conversation` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `conversation_type` varchar(32) NOT NULL COMMENT '会话类型',
  `queue_code` varchar(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '客服队列',
  `customer_id` varchar(32) DEFAULT NULL COMMENT '会员ID',
  `assigned_staff_id` varchar(32) DEFAULT NULL COMMENT '当前负责人',
  `staff_pair_key` varchar(80) DEFAULT NULL COMMENT '排序后的工作人员组合键',
  `status` varchar(32) NOT NULL COMMENT '会话状态',
  `last_seq` bigint NOT NULL DEFAULT 0 COMMENT '最后消息序号',
  `last_message_id` varchar(32) DEFAULT NULL COMMENT '最后消息ID',
  `last_message_summary` varchar(500) DEFAULT NULL COMMENT '最后消息摘要',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后消息时间',
  `closed_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `reopen_deadline` datetime DEFAULT NULL COMMENT '重开截止时间',
  `close_reason` varchar(500) DEFAULT NULL COMMENT '关闭原因',
  `context_payload` json DEFAULT NULL COMMENT '业务上下文',
  `customer_active_key` varchar(160) GENERATED ALWAYS AS (CASE WHEN `conversation_type` = 'CUSTOMER_SERVICE' AND `status` IN ('WAITING','ASSIGNED','ACTIVE') AND `del_flag` = '0' THEN CONCAT(`customer_id`, ':', `queue_code`) ELSE NULL END) STORED COMMENT '客服活跃唯一键',
  `staff_active_key` varchar(80) GENERATED ALWAYS AS (CASE WHEN `conversation_type` = 'STAFF_DIRECT' AND `status` IN ('WAITING','ASSIGNED','ACTIVE') AND `del_flag` = '0' THEN `staff_pair_key` ELSE NULL END) STORED COMMENT '私信活跃唯一键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_conversation_customer_active` (`tenant_id`, `customer_active_key`),
  UNIQUE KEY `uk_message_conversation_staff_active` (`tenant_id`, `staff_active_key`),
  KEY `idx_message_conversation_waiting` (`tenant_id`, `queue_code`, `status`, `create_time`),
  KEY `idx_message_conversation_assignee` (`tenant_id`, `assigned_staff_id`, `status`, `last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息会话';

CREATE TABLE `message_participant` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `conversation_id` varchar(32) NOT NULL COMMENT '会话ID',
  `participant_type` varchar(32) NOT NULL COMMENT '参与者类型',
  `participant_id` varchar(32) NOT NULL COMMENT '参与者ID',
  `participant_name` varchar(100) DEFAULT NULL COMMENT '名称快照',
  `participant_avatar` varchar(1024) DEFAULT NULL COMMENT '头像快照',
  `last_read_seq` bigint NOT NULL DEFAULT 0 COMMENT '最后已读序号',
  `joined_time` datetime NOT NULL COMMENT '加入时间',
  `exited_time` datetime DEFAULT NULL COMMENT '退出时间',
  `participant_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '参与状态',
  `notification_enabled` char(1) NOT NULL DEFAULT '1' COMMENT '通知开关',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_participant_identity` (`tenant_id`, `conversation_id`, `participant_type`, `participant_id`),
  KEY `idx_message_participant_inbox` (`tenant_id`, `participant_type`, `participant_id`, `participant_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会话参与者';

CREATE TABLE `message_chat` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `conversation_id` varchar(32) NOT NULL COMMENT '会话ID',
  `seq_no` bigint NOT NULL COMMENT '会话内序号',
  `sender_type` varchar(32) NOT NULL COMMENT '发送人类型',
  `sender_id` varchar(32) NOT NULL COMMENT '发送人ID',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '发送人名称快照',
  `sender_avatar` varchar(1024) DEFAULT NULL COMMENT '发送人头像快照',
  `message_type` varchar(32) NOT NULL COMMENT '消息类型',
  `content` text DEFAULT NULL COMMENT '纯文本内容',
  `payload` json DEFAULT NULL COMMENT '结构化消息负载',
  `client_message_id` varchar(64) NOT NULL COMMENT '客户端幂等号',
  `quoted_message_id` varchar(32) DEFAULT NULL COMMENT '引用消息ID',
  `recall_status` char(1) NOT NULL DEFAULT '0' COMMENT '撤回状态',
  `recall_time` datetime DEFAULT NULL COMMENT '撤回时间',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_chat_seq` (`tenant_id`, `conversation_id`, `seq_no`),
  UNIQUE KEY `uk_message_chat_client` (`tenant_id`, `sender_type`, `sender_id`, `client_message_id`),
  KEY `idx_message_chat_cursor` (`tenant_id`, `conversation_id`, `seq_no`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会话消息';

CREATE TABLE `message_agent` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `staff_id` varchar(32) NOT NULL COMMENT '工作人员ID',
  `enabled` char(1) NOT NULL DEFAULT '1' COMMENT '客服资格开关',
  `auto_accept` char(1) NOT NULL DEFAULT '1' COMMENT '自动接待开关',
  `max_active_count` int NOT NULL DEFAULT 10 COMMENT '最大活跃会话数',
  `current_active_count` int NOT NULL DEFAULT 0 COMMENT '当前活跃会话数',
  `last_assigned_time` datetime DEFAULT NULL COMMENT '最后分配时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_agent_staff` (`tenant_id`, `staff_id`),
  KEY `idx_message_agent_assignment` (`tenant_id`, `enabled`, `auto_accept`, `current_active_count`, `last_assigned_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客服坐席配置';

CREATE TABLE `message_assignment_log` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `conversation_id` varchar(32) NOT NULL COMMENT '会话ID',
  `action_type` varchar(32) NOT NULL COMMENT '分配动作',
  `from_staff_id` varchar(32) DEFAULT NULL COMMENT '原客服ID',
  `to_staff_id` varchar(32) DEFAULT NULL COMMENT '目标客服ID',
  `operator_type` varchar(32) NOT NULL COMMENT '操作人类型',
  `operator_id` varchar(32) NOT NULL COMMENT '操作人ID',
  `reason` varchar(500) DEFAULT NULL COMMENT '操作原因',
  `detail_payload` json DEFAULT NULL COMMENT '审计详情',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
  PRIMARY KEY (`id`),
  KEY `idx_message_assignment_conversation` (`tenant_id`, `conversation_id`, `create_time`),
  KEY `idx_message_assignment_staff` (`tenant_id`, `to_staff_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客服分配审计日志';
