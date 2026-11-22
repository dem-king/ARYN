USE `aryn_upms`;
-- AI 配置与不可变凭证版本；供应商/模型/用途作为单个版本JSON原子保存。
-- 可重复执行，不含有效密钥，不覆盖已配置数据。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_config_version` (
  `id` varchar(64) NOT NULL,
  `document` longtext NOT NULL,
  `digest` varchar(64) NOT NULL,
  `revision` bigint NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ai_credential_version` (
  `id` varchar(64) NOT NULL,
  `provider_id` varchar(64) NOT NULL,
  `sealed_value` text NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ai_runtime_state` (
  `id` varchar(64) NOT NULL,
  `draft_version` varchar(64) DEFAULT NULL,
  `active_version` varchar(64) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `emergency_epoch` bigint NOT NULL DEFAULT 0,
  `disabled` tinyint NOT NULL DEFAULT 0,
  `disabled_providers` text NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ai_capability_test` (
  `id` varchar(64) NOT NULL,
  `version_id` varchar(64) NOT NULL,
  `model_id` varchar(64) NOT NULL,
  `passed` tinyint NOT NULL,
  `status` varchar(64) NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ai_config_operation` (
  `id` varchar(64) NOT NULL,
  `operation` varchar(40) NOT NULL,
  `version_id` varchar(64) DEFAULT NULL,
  `previous_version` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id` varchar(64) NOT NULL,
  `usage_code` varchar(40) NOT NULL,
  `task_id` varchar(100) NOT NULL,
  `version_id` varchar(64) NOT NULL,
  `provider_id` varchar(64) NOT NULL,
  `model_id` varchar(200) NOT NULL,
  `result` varchar(40) NOT NULL,
  `total_tokens` bigint DEFAULT NULL,
  `reserved_tokens` bigint NOT NULL DEFAULT 0,
  `elapsed_millis` bigint DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


CREATE TABLE IF NOT EXISTS `ai_usage_budget` (
  `id` varchar(64) NOT NULL,
  `usage_code` varchar(40) NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_usage` (`tenant_id`,`usage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 平台专属菜单，普通租户套餐不得授权 sys_key。
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
('2471100000000000000','AI 配置','upms:ai-config:query','/platform/ai-config',NULL,'1881217134381326338','carbon:machine-learning','system/ai-config/index',110,'0',NOW(),'0','0','sys_key','system'),
('2471100000000000001','AI 配置查询','upms:ai-config:query',NULL,NULL,'2471100000000000000','','',1,'1',NOW(),'0','0','sys_key','system'),
('2471100000000000002','AI 配置编辑','upms:ai-config:edit',NULL,NULL,'2471100000000000000','','',2,'1',NOW(),'0','0','sys_key','system'),
('2471100000000000003','AI 能力测试','upms:ai-config:test',NULL,NULL,'2471100000000000000','','',3,'1',NOW(),'0','0','sys_key','system'),
('2471100000000000004','AI 启用与停用','upms:ai-config:activate',NULL,NULL,'2471100000000000000','','',4,'1',NOW(),'0','0','sys_key','system');

-- Nacos既有库租户白名单（AI部署环境变量仍由各服务application.yml读取）。
USE `aryn_nacos`;
UPDATE config_info SET content = REPLACE(content, '      - sys_storage_config\n', '      - sys_storage_config\n      - ai_config_version\n'), md5 = MD5(content) WHERE data_id = 'aryn-upms-biz-dev.yml' AND INSTR(content, '      - ai_config_version\n') = 0;
UPDATE config_info SET content = REPLACE(content, '      - sys_storage_config\n', '      - sys_storage_config\n      - ai_credential_version\n'), md5 = MD5(content) WHERE data_id = 'aryn-upms-biz-dev.yml' AND INSTR(content, '      - ai_credential_version\n') = 0;
UPDATE config_info SET content = REPLACE(content, '      - sys_storage_config\n', '      - sys_storage_config\n      - ai_runtime_state\n'), md5 = MD5(content) WHERE data_id = 'aryn-upms-biz-dev.yml' AND INSTR(content, '      - ai_runtime_state\n') = 0;
UPDATE config_info SET content = REPLACE(content, '      - sys_storage_config\n', '      - sys_storage_config\n      - ai_capability_test\n'), md5 = MD5(content) WHERE data_id = 'aryn-upms-biz-dev.yml' AND INSTR(content, '      - ai_capability_test\n') = 0;
UPDATE config_info SET content = REPLACE(content, '      - sys_storage_config\n', '      - sys_storage_config\n      - ai_config_operation\n'), md5 = MD5(content) WHERE data_id = 'aryn-upms-biz-dev.yml' AND INSTR(content, '      - ai_config_operation\n') = 0;
UPDATE config_info SET content = REPLACE(content, '      - sys_storage_config\n', '      - sys_storage_config\n      - ai_call_log\n'), md5 = MD5(content) WHERE data_id = 'aryn-upms-biz-dev.yml' AND INSTR(content, '      - ai_call_log\n') = 0;
UPDATE config_info SET content = REPLACE(content, '      - sys_storage_config\n', '      - sys_storage_config\n      - ai_usage_budget\n'), md5 = MD5(content) WHERE data_id = 'aryn-upms-biz-dev.yml' AND INSTR(content, '      - ai_usage_budget\n') = 0;

-- 模型凭证仅通过环境变量导入/加密；此处只登记部署占位符。
UPDATE config_info SET content=IF(INSTR(content, 'aryn:\n')>0, REPLACE(content, 'aryn:\n', 'aryn:\n  ai:\n    key-id: ${AI_MASTER_KEY_ID:primary}\n    keys:\n      primary: ${AI_MASTER_KEY_PRIMARY:}\n    allowed-private-hosts: ${AI_ALLOWED_PRIVATE_HOSTS:}\n    runtime-tokens:\n      order: ${AI_RUNTIME_TOKEN_ORDER:}\n      product: ${AI_RUNTIME_TOKEN_PRODUCT:}\n      promotion: ${AI_RUNTIME_TOKEN_PROMOTION:}\n    daily-token-limit: ${AI_DAILY_TOKEN_LIMIT:1000000}\n    max-concurrent: ${AI_MAX_CONCURRENT:4}\n    max-task-calls: ${AI_MAX_TASK_CALLS:100}\n'), CONCAT(content, '\naryn:\n  ai:\n    key-id: ${AI_MASTER_KEY_ID:primary}\n    keys:\n      primary: ${AI_MASTER_KEY_PRIMARY:}\n    allowed-private-hosts: ${AI_ALLOWED_PRIVATE_HOSTS:}\n    runtime-tokens:\n      order: ${AI_RUNTIME_TOKEN_ORDER:}\n      product: ${AI_RUNTIME_TOKEN_PRODUCT:}\n      promotion: ${AI_RUNTIME_TOKEN_PROMOTION:}\n    daily-token-limit: ${AI_DAILY_TOKEN_LIMIT:1000000}\n    max-concurrent: ${AI_MAX_CONCURRENT:4}\n    max-task-calls: ${AI_MAX_TASK_CALLS:100}\n')), md5=MD5(content) WHERE data_id='aryn-upms-biz-dev.yml' AND INSTR(content, '  ai:\n')=0;
UPDATE config_info SET content=IF(INSTR(content, 'aryn:\n')>0, REPLACE(content, 'aryn:\n', 'aryn:\n  ai:\n    runtime-tokens:\n      order: ${AI_RUNTIME_TOKEN_ORDER:}\n    allowed-private-hosts: ${AI_ALLOWED_PRIVATE_HOSTS:}\n'), CONCAT(content, '\naryn:\n  ai:\n    runtime-tokens:\n      order: ${AI_RUNTIME_TOKEN_ORDER:}\n    allowed-private-hosts: ${AI_ALLOWED_PRIVATE_HOSTS:}\n')), md5=MD5(content) WHERE data_id='aryn-order-biz-dev.yml' AND INSTR(content, '  ai:\n')=0;
UPDATE config_info SET content=IF(INSTR(content, 'aryn:\n')>0, REPLACE(content, 'aryn:\n', 'aryn:\n  ai:\n    runtime-tokens:\n      product: ${AI_RUNTIME_TOKEN_PRODUCT:}\n    allowed-private-hosts: ${AI_ALLOWED_PRIVATE_HOSTS:}\n    index-scan-ms: ${AI_INDEX_SCAN_MS:30000}\n'), CONCAT(content, '\naryn:\n  ai:\n    runtime-tokens:\n      product: ${AI_RUNTIME_TOKEN_PRODUCT:}\n    allowed-private-hosts: ${AI_ALLOWED_PRIVATE_HOSTS:}\n    index-scan-ms: ${AI_INDEX_SCAN_MS:30000}\n')), md5=MD5(content) WHERE data_id='aryn-product-biz-dev.yml' AND INSTR(content, '  ai:\n')=0;
