USE `aryn_boot`;
SET NAMES utf8mb4;
SET @campaign_page_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='page_design' AND COLUMN_NAME='campaign_draft_id'), 'SELECT 1', 'ALTER TABLE `page_design` ADD COLUMN `campaign_draft_id` varchar(64) DEFAULT NULL COMMENT ''活动包拥有者，仅经活动包发布编排启用''');
PREPARE campaign_page_stmt FROM @campaign_page_sql; EXECUTE campaign_page_stmt; DEALLOCATE PREPARE campaign_page_stmt;
SET @campaign_page_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='page_design' AND INDEX_NAME='uk_page_campaign_owner'), 'SELECT 1', 'ALTER TABLE `page_design` ADD UNIQUE KEY `uk_page_campaign_owner` (`tenant_id`,`campaign_draft_id`)');
PREPARE campaign_page_stmt FROM @campaign_page_sql; EXECUTE campaign_page_stmt; DEALLOCATE PREPARE campaign_page_stmt;
SET FOREIGN_KEY_CHECKS = 1;
