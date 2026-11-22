USE `aryn_boot`;
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `campaign_draft` (
  `id` varchar(64) NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `actor_id` varchar(64) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `title` varchar(100) NOT NULL,
  `definition_json` longtext NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `main_activity_id` varchar(64) DEFAULT NULL,
  `main_activity_revision` bigint DEFAULT NULL,
  `page_id` varchar(64) DEFAULT NULL,
  `page_revision` bigint DEFAULT NULL,
  `page_digest` char(64) DEFAULT NULL,
  `simulation_id` varchar(64) DEFAULT NULL,
  `simulation_revision` bigint DEFAULT NULL,
  `generation_version` int NOT NULL DEFAULT 0,
  `generation_reason` varchar(1000) DEFAULT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_campaign_draft_owner` (`tenant_id`,`actor_id`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控活动包草稿，不自动发布';
CREATE TABLE IF NOT EXISTS `campaign_generation_job` (
  `id` varchar(64) NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `draft_id` varchar(64) NOT NULL,
  `actor_id` varchar(64) NOT NULL,
  `request_id` varchar(64) NOT NULL,
  `base_revision` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `input_json` longtext NOT NULL,
  `lease_token` varchar(64) DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `attempt` int NOT NULL DEFAULT 0,
  `next_retry_at` datetime DEFAULT NULL,
  `config_version` varchar(64) NOT NULL,
  `provider_id` varchar(64) DEFAULT NULL,
  `model` varchar(255) DEFAULT NULL,
  `prompt_version` varchar(64) NOT NULL,
  `input_digest` char(64) NOT NULL,
  `output_json` longtext,
  `error_code` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_campaign_generation_request` (`tenant_id`,`draft_id`,`request_id`),
  KEY `idx_campaign_generation_due` (`tenant_id`,`status`,`next_retry_at`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动包生成租约与修订审计，输出仅内部使用';
-- 权限由管理员显式分配，不自动授予角色。
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by)
SELECT '2551100000000000001','活动包编辑','promotion:campaign:edit',NULL,NULL,m.id,'',NULL,91,'1',NOW(),'0','0','app_base','system'
FROM sys_menu m WHERE m.component='promotion/ship-supply/index' AND m.del_flag='0' LIMIT 1;
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-edit')),g.tenant_id,'2551100000000000001',NOW(),'system'
FROM sys_tenant_menu g JOIN sys_menu p ON p.id=g.menu_id
WHERE p.component='promotion/ship-supply/index' AND p.del_flag='0';
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by)
SELECT '2551100000000000002','活动方案生成','promotion:campaign:generate',NULL,NULL,m.id,'',NULL,92,'1',NOW(),'0','0','app_base','system'
FROM sys_menu m WHERE m.component='promotion/ship-supply/index' AND m.del_flag='0' LIMIT 1;
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-generate')),g.tenant_id,'2551100000000000002',NOW(),'system'
FROM sys_tenant_menu g JOIN sys_menu p ON p.id=g.menu_id
WHERE p.component='promotion/ship-supply/index' AND p.del_flag='0';
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by)
SELECT '2551100000000000003','活动包发布','promotion:campaign:publish',NULL,NULL,m.id,'',NULL,93,'1',NOW(),'0','0','app_base','system'
FROM sys_menu m WHERE m.component='promotion/ship-supply/index' AND m.del_flag='0' LIMIT 1;
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-publish')),g.tenant_id,'2551100000000000003',NOW(),'system'
FROM sys_tenant_menu g JOIN sys_menu p ON p.id=g.menu_id
WHERE p.component='promotion/ship-supply/index' AND p.del_flag='0';
SET FOREIGN_KEY_CHECKS = 1;
