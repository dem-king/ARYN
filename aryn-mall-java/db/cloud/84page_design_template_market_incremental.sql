-- 页面装修模板市场（跨租户模板共享）增量迁移（Cloud 微服务模式：表进 aryn_promotion）
-- 目标库：aryn_promotion
-- 背景：page_design_template 表新增市场上下架状态与下载量，支持租户将自有模板上架到
--       跨租户模板市场（market_status='1'），其他租户可预览并下载复制为本地模板。
-- 特性：可重复执行（按 information_schema 判断列/索引是否存在），不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_promotion < 84page_design_template_market_incremental.sql

USE `aryn_promotion`;

SET NAMES utf8mb4;

-- 市场状态列（market_status）：0.未上架；1.已上架；2.已下架
SET @market_col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'page_design_template' AND COLUMN_NAME = 'market_status'
);
SET @market_ddl = IF(
  @market_col_exists = 0,
  'ALTER TABLE `page_design_template` ADD COLUMN `market_status` char(1) NOT NULL DEFAULT ''0'' COMMENT ''市场状态：0.未上架；1.已上架；2.已下架；'' AFTER `industry_tag`',
  'SELECT 1'
);
PREPARE market_stmt FROM @market_ddl;
EXECUTE market_stmt;
DEALLOCATE PREPARE market_stmt;

-- 下载量列（download_count）：市场模板被其他租户下载复制的累计次数
SET @download_col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'page_design_template' AND COLUMN_NAME = 'download_count'
);
SET @download_ddl = IF(
  @download_col_exists = 0,
  'ALTER TABLE `page_design_template` ADD COLUMN `download_count` int NOT NULL DEFAULT 0 COMMENT ''下载量；'' AFTER `market_status`',
  'SELECT 1'
);
PREPARE download_stmt FROM @download_ddl;
EXECUTE download_stmt;
DEALLOCATE PREPARE download_stmt;

-- 市场列表按上架状态/下载量排序的索引（仅在索引不存在时创建）
SET @market_idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'page_design_template' AND INDEX_NAME = 'idx_page_design_template_market'
);
SET @market_idx_ddl = IF(
  @market_idx_exists = 0,
  'ALTER TABLE `page_design_template` ADD KEY `idx_page_design_template_market` (`market_status`,`download_count`)',
  'SELECT 1'
);
PREPARE market_idx_stmt FROM @market_idx_ddl;
EXECUTE market_idx_stmt;
DEALLOCATE PREPARE market_idx_stmt;
