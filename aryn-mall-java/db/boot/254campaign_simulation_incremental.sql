USE `aryn_boot`;
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `campaign_simulation` (
  `id` varchar(64) NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `actor_id` varchar(64) NOT NULL,
  `draft_activity_id` varchar(64) NOT NULL,
  `draft_revision` bigint NOT NULL,
  `request_json` longtext NOT NULL,
  `draft_digest` char(64) NOT NULL,
  `input_digest` char(64) NOT NULL,
  `fact_digest` char(64) NOT NULL,
  `result_json` longtext NOT NULL,
  `expires_at` datetime NOT NULL,
  `create_time` datetime NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_campaign_sim_owner` (`tenant_id`,`actor_id`,`draft_activity_id`,`create_time`),
  KEY `idx_campaign_sim_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未发布方案只读模拟凭据，不占用库存额度';
-- 模拟权限独立于活动发布权限；由管理员分配角色，不自动扩展既有角色职责。
INSERT IGNORE INTO sys_menu (id,name,permission,path,redirect,parent_id,icon,component,sort,type,create_time,outer_status,del_flag,application_key,create_by)
SELECT '2541100000000000001','活动情景模拟','promotion:campaign:simulate',NULL,NULL,m.id,'',NULL,90,'1',NOW(),'0','0','app_base','system'
FROM sys_menu m WHERE m.component='promotion/ship-supply/index' AND m.del_flag='0' LIMIT 1;
INSERT IGNORE INTO sys_tenant_menu (id,tenant_id,menu_id,create_time,create_by)
SELECT MD5(CONCAT(g.tenant_id,':campaign-simulate')),g.tenant_id,'2541100000000000001',NOW(),'system'
FROM sys_tenant_menu g JOIN sys_menu p ON p.id=g.menu_id
WHERE p.component='promotion/ship-supply/index' AND p.del_flag='0';
SET FOREIGN_KEY_CHECKS = 1;
