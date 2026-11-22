USE `aryn_boot`;
-- 长期采购草稿，与共享车收集窗口解耦。旧导入报告仍保留只读；无法重建的存量贡献不得推算撤销。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `procurement_draft` (
  `id` varchar(64) NOT NULL,
  `owner_user_id` varchar(64) NOT NULL,
  `purchase_scene` varchar(64) NOT NULL,
  `buyer_org_id` varchar(64) DEFAULT NULL,
  `vessel_id` varchar(64) DEFAULT NULL,
  `vessel_call_id` varchar(64) DEFAULT NULL,
  `title` varchar(120) DEFAULT NULL,
  `client_request_id` varchar(64) NOT NULL,
  `context_fingerprint` varchar(64) NOT NULL,
  `status` varchar(64) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`,`id`),
  UNIQUE KEY `uk_draft_request` (`tenant_id`,`owner_user_id`,`client_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `procurement_source_batch` (
  `id` varchar(64) NOT NULL,
  `draft_id` varchar(64) NOT NULL,
  `operator_user_id` varchar(64) NOT NULL,
  `input_type` varchar(64) NOT NULL,
  `client_request_id` varchar(64) NOT NULL,
  `input_digest` varchar(64) NOT NULL,
  `explicit_new` tinyint(1) NOT NULL,
  `input_name` varchar(255) DEFAULT NULL,
  `input_text` longtext DEFAULT NULL,
  `input_bytes` longblob DEFAULT NULL,
  `template_version` varchar(64) DEFAULT NULL,
  `config_version` varchar(64) DEFAULT NULL,
  `parser_source` varchar(64) DEFAULT NULL,
  `parse_revision` int NOT NULL DEFAULT 0,
  `status` varchar(64) NOT NULL,
  `total_rows` int NOT NULL DEFAULT 0,
  `completed_rows` int NOT NULL DEFAULT 0,
  `source_cursor` int NOT NULL DEFAULT 0,
  `attempt` int NOT NULL DEFAULT 0,
  `lease_token` varchar(64) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `heartbeat` datetime(6) DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `confirmed_request_id` varchar(64) DEFAULT NULL,
  `confirmation_json` longtext DEFAULT NULL,
  `replaced_by_batch_id` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`,`id`),
  UNIQUE KEY `uk_batch_request` (`tenant_id`,`draft_id`,`operator_user_id`,`client_request_id`),
  KEY `idx_batch_digest` (`tenant_id`,`draft_id`,`operator_user_id`,`input_digest`),
  KEY `idx_batch_jobs` (`tenant_id`,`status`,`next_retry_at`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `procurement_draft_row` (
  `id` varchar(64) NOT NULL,
  `draft_id` varchar(64) NOT NULL,
  `batch_id` varchar(64) NOT NULL,
  `source_position` varchar(64) NOT NULL,
  `source_sheet` varchar(200) DEFAULT NULL,
  `source_row` int DEFAULT NULL,
  `source_offset` int DEFAULT NULL,
  `raw_text` longtext DEFAULT NULL,
  `raw_name` varchar(500) DEFAULT NULL,
  `raw_quantity` decimal(38,18) DEFAULT NULL,
  `raw_unit` varchar(24) DEFAULT NULL,
  `raw_spec` varchar(500) DEFAULT NULL,
  `raw_code` varchar(200) DEFAULT NULL,
  `raw_owner` varchar(120) DEFAULT NULL,
  `parse_status` varchar(64) DEFAULT NULL,
  `match_status` varchar(64) DEFAULT NULL,
  `unit_status` varchar(64) DEFAULT NULL,
  `quantity_status` varchar(64) DEFAULT NULL,
  `review_status` varchar(64) DEFAULT NULL,
  `candidates_json` longtext DEFAULT NULL,
  `matched_sku_id` varchar(64) DEFAULT NULL,
  `selected_sku_id` varchar(64) DEFAULT NULL,
  `normalized_base_quantity` decimal(60,30) DEFAULT NULL,
  `suggested_sales_quantity` int DEFAULT NULL,
  `confirmed_sales_quantity` int DEFAULT NULL,
  `excess_base_quantity` decimal(60,30) DEFAULT NULL,
  `measurement_revision` bigint DEFAULT NULL,
  `decision_json` longtext DEFAULT NULL,
  `review_actor` varchar(64) DEFAULT NULL,
  `review_reason` varchar(500) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `parse_revision` int NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`,`id`),
  UNIQUE KEY `uk_source_position` (`tenant_id`,`batch_id`,`parse_revision`,`source_position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `procurement_contribution` (
  `id` varchar(64) NOT NULL,
  `draft_id` varchar(64) NOT NULL,
  `batch_id` varchar(64) NOT NULL,
  `source_row_id` varchar(64) NOT NULL,
  `target_type` varchar(64) NOT NULL,
  `target_id` varchar(64) NOT NULL,
  `target_line_id` varchar(64) NOT NULL,
  `owner_user_id` varchar(64) NOT NULL,
  `sku_id` varchar(64) NOT NULL,
  `quantity` int NOT NULL,
  `status` varchar(64) NOT NULL,
  `origin` varchar(64) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`,`id`),
  UNIQUE KEY `uk_row_target_contribution` (`tenant_id`,`batch_id`,`source_row_id`,`target_type`,`target_line_id`),
  KEY `idx_target_contributions` (`tenant_id`,`target_type`,`target_line_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
SET @procurement_expand=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft_row' AND COLUMN_NAME='raw_quantity' AND NUMERIC_PRECISION<38);
SET @procurement_ddl=IF(@procurement_expand=1, 'ALTER TABLE procurement_draft_row MODIFY COLUMN raw_quantity decimal(38,18) DEFAULT NULL', 'SELECT 1');
PREPARE procurement_stmt FROM @procurement_ddl; EXECUTE procurement_stmt; DEALLOCATE PREPARE procurement_stmt;
SET @procurement_expand=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft_row' AND COLUMN_NAME='normalized_base_quantity' AND NUMERIC_PRECISION<60);
SET @procurement_ddl=IF(@procurement_expand=1, 'ALTER TABLE procurement_draft_row MODIFY COLUMN normalized_base_quantity decimal(60,30) DEFAULT NULL', 'SELECT 1');
PREPARE procurement_stmt FROM @procurement_ddl; EXECUTE procurement_stmt; DEALLOCATE PREPARE procurement_stmt;
SET @procurement_expand=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft_row' AND COLUMN_NAME='excess_base_quantity' AND NUMERIC_PRECISION<60);
SET @procurement_ddl=IF(@procurement_expand=1, 'ALTER TABLE procurement_draft_row MODIFY COLUMN excess_base_quantity decimal(60,30) DEFAULT NULL', 'SELECT 1');
PREPARE procurement_stmt FROM @procurement_ddl; EXECUTE procurement_stmt; DEALLOCATE PREPARE procurement_stmt;
SET @procurement_tenant_nullable=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft' AND COLUMN_NAME='tenant_id' AND IS_NULLABLE='YES');
SET @procurement_tenant_ddl=IF(@procurement_tenant_nullable=1, 'ALTER TABLE procurement_draft MODIFY COLUMN tenant_id varchar(32) NOT NULL', 'SELECT 1');
PREPARE procurement_tenant_stmt FROM @procurement_tenant_ddl; EXECUTE procurement_tenant_stmt; DEALLOCATE PREPARE procurement_tenant_stmt;
SET @procurement_tenant_nullable=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_source_batch' AND COLUMN_NAME='tenant_id' AND IS_NULLABLE='YES');
SET @procurement_tenant_ddl=IF(@procurement_tenant_nullable=1, 'ALTER TABLE procurement_source_batch MODIFY COLUMN tenant_id varchar(32) NOT NULL', 'SELECT 1');
PREPARE procurement_tenant_stmt FROM @procurement_tenant_ddl; EXECUTE procurement_tenant_stmt; DEALLOCATE PREPARE procurement_tenant_stmt;
SET @procurement_tenant_nullable=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft_row' AND COLUMN_NAME='tenant_id' AND IS_NULLABLE='YES');
SET @procurement_tenant_ddl=IF(@procurement_tenant_nullable=1, 'ALTER TABLE procurement_draft_row MODIFY COLUMN tenant_id varchar(32) NOT NULL', 'SELECT 1');
PREPARE procurement_tenant_stmt FROM @procurement_tenant_ddl; EXECUTE procurement_tenant_stmt; DEALLOCATE PREPARE procurement_tenant_stmt;
SET @procurement_tenant_nullable=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_contribution' AND COLUMN_NAME='tenant_id' AND IS_NULLABLE='YES');
SET @procurement_tenant_ddl=IF(@procurement_tenant_nullable=1, 'ALTER TABLE procurement_contribution MODIFY COLUMN tenant_id varchar(32) NOT NULL', 'SELECT 1');
PREPARE procurement_tenant_stmt FROM @procurement_tenant_ddl; EXECUTE procurement_tenant_stmt; DEALLOCATE PREPARE procurement_tenant_stmt;
SET FOREIGN_KEY_CHECKS = 1;
