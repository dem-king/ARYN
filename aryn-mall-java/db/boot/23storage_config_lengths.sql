USE `aryn_boot`;

SET @storage_config_exists = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'sys_storage_config'
);
SET @storage_lengths_sql = IF(
  @storage_config_exists = 1,
  'ALTER TABLE sys_storage_config MODIFY COLUMN endpoint varchar(255) NULL DEFAULT NULL COMMENT ''地域节点'', MODIFY COLUMN bucket varchar(255) NULL DEFAULT NULL COMMENT ''Bucket或本地存储根目录''',
  'SELECT 1'
);
PREPARE storage_lengths_stmt FROM @storage_lengths_sql;
EXECUTE storage_lengths_stmt;
DEALLOCATE PREPARE storage_lengths_stmt;
