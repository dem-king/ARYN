USE `aryn_boot`;
SET NAMES utf8mb4;
-- 配置修订号与交易额度分离，存量活动从修订 0 开始；不改发布态、预算或历史锁。
SET @activity_revision_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='promotion_activity' AND COLUMN_NAME='revision'),
  'SELECT 1',
  'ALTER TABLE promotion_activity ADD COLUMN revision bigint NOT NULL DEFAULT 0 COMMENT ''配置修订号''');
PREPARE activity_revision_stmt FROM @activity_revision_ddl;
EXECUTE activity_revision_stmt;
DEALLOCATE PREPARE activity_revision_stmt;
SET FOREIGN_KEY_CHECKS = 1;
