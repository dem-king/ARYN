-- 页面装修审计动作枚举补充（Cloud 微服务模式：表进 aryn_promotion）
-- 目标库：aryn_promotion
-- 背景：新增「设为首页」动作（page_design_audit_log.action = 'SET_HOME'，见
--       PageDesignServiceImpl#setAsHome）。该列原为 varchar(32) 枚举注释形式，
--       SQL 层只做注释同步，列类型/长度/可空性保持不变，因此不涉及数据改写。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_promotion < 82page_design_audit_action_comment.sql

USE `aryn_promotion`;

SET NAMES utf8mb4;

-- 幂等同步 action 列注释（column 不存在时跳过，避免在旧库上报错）
SET @comment_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'page_design_audit_log'
      AND COLUMN_NAME = 'action') = 1,
  'ALTER TABLE `page_design_audit_log` MODIFY COLUMN `action` varchar(32) NOT NULL COMMENT ''操作类型：SAVE_DRAFT/PUBLISH/UNPUBLISH/SET_HOME/ROLLBACK/RELEASE_SUBMIT/RELEASE_APPROVE/RELEASE_REJECT/RELEASE_CANCEL''',
  'SELECT 1');
PREPARE stmt FROM @comment_ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
