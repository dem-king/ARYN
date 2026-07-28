-- 商城配送履约：消息通道任务
USE `aryn_message`;

CREATE TABLE `message_channel_task` (
  `id` varchar(32) NOT NULL COMMENT '主键', `message_id` varchar(32) NOT NULL COMMENT '站内通知ID',
  `recipient_type` varchar(32) NOT NULL COMMENT '收件人类型', `recipient_id` varchar(32) NOT NULL COMMENT '收件人ID',
  `channel` varchar(32) NOT NULL COMMENT '消息通道', `template_code` varchar(64) DEFAULT NULL COMMENT '模板编码',
  `template_params` json DEFAULT NULL COMMENT '受控模板参数', `source_type` varchar(64) NOT NULL COMMENT '来源类型',
  `source_key` varchar(128) NOT NULL COMMENT '来源幂等键', `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数', `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `last_attempt_time` datetime DEFAULT NULL COMMENT '最后尝试时间', `error_summary` varchar(1000) DEFAULT NULL COMMENT '错误摘要',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID', `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人', `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间', `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_channel_source` (`tenant_id`, `source_type`, `source_key`, `recipient_type`, `recipient_id`, `channel`),
  KEY `idx_message_channel_retry` (`tenant_id`, `status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息通道发送任务';
