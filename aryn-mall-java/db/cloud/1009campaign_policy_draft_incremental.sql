USE `aryn_promotion`;
SET NAMES utf8mb4;
SET @campaign_column_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='campaign_draft' AND COLUMN_NAME='main_policy_id'), 'SELECT 1', 'ALTER TABLE `campaign_draft` ADD COLUMN `main_policy_id` varchar(64) DEFAULT NULL');
PREPARE campaign_column_stmt FROM @campaign_column_sql; EXECUTE campaign_column_stmt; DEALLOCATE PREPARE campaign_column_stmt;
SET @campaign_column_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='campaign_draft' AND COLUMN_NAME='main_policy_digest'), 'SELECT 1', 'ALTER TABLE `campaign_draft` ADD COLUMN `main_policy_digest` char(64) DEFAULT NULL');
PREPARE campaign_column_stmt FROM @campaign_column_sql; EXECUTE campaign_column_stmt; DEALLOCATE PREPARE campaign_column_stmt;
-- 协议主方案没有统一活动ID，使用活动包修订和主政策摘要校验。
ALTER TABLE `campaign_simulation` MODIFY COLUMN `draft_activity_id` varchar(64) DEFAULT NULL;
SET FOREIGN_KEY_CHECKS = 1;
