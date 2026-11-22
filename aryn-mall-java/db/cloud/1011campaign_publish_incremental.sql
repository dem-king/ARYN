USE `aryn_promotion`;
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `campaign_publish_operation` (
 `id` varchar(64) NOT NULL,
 `tenant_id` varchar(32) NOT NULL,
 `actor_id` varchar(64) NOT NULL,
 `draft_id` varchar(64) NOT NULL,
 `request_id` varchar(64) NOT NULL,
 `command_json` longtext NOT NULL,
 `command_digest` char(64) NOT NULL,
 `status` varchar(32) NOT NULL,
 `steps_json` text NOT NULL,
 `main_activity_id` varchar(64) DEFAULT NULL,
 `main_policy_id` varchar(64) DEFAULT NULL,
 `coupon_id` varchar(64) DEFAULT NULL,
 `page_id` varchar(64) DEFAULT NULL,
 `page_version_id` varchar(64) DEFAULT NULL,
 `failure_code` varchar(64) DEFAULT NULL,
 `create_time` datetime NOT NULL,
 `update_time` datetime NOT NULL,
 `del_flag` char(1) NOT NULL DEFAULT '0',
 PRIMARY KEY (`id`),
 UNIQUE KEY `uk_campaign_publish_request` (`tenant_id`,`draft_id`,`request_id`),
 KEY `idx_campaign_publish_owner` (`tenant_id`,`actor_id`,`draft_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动包人工发布信封、原子步骤和恢复记录';
USE `aryn_nacos`;
UPDATE config_info SET content=REPLACE(content, '      - campaign_generation_job\n', '      - campaign_generation_job\n      - campaign_publish_operation\n'), md5=MD5(content) WHERE data_id='aryn-promotion-biz-dev.yml' AND INSTR(content,'      - campaign_publish_operation\n')=0;
SET FOREIGN_KEY_CHECKS = 1;
