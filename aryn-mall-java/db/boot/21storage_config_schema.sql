USE `aryn_boot`;

SET @storage_config_table_exists = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_storage_config'
);

SET @style_access_enabled_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_storage_config'
    AND column_name = 'style_access_enabled'
);
SET @style_access_enabled_sql = IF(
  @storage_config_table_exists = 1 AND @style_access_enabled_exists = 0,
  'ALTER TABLE sys_storage_config ADD COLUMN style_access_enabled tinyint(1) NULL DEFAULT 0 COMMENT ''是否 path-style'' AFTER status',
  'SELECT 1'
);
PREPARE style_access_enabled_stmt FROM @style_access_enabled_sql;
EXECUTE style_access_enabled_stmt;
DEALLOCATE PREPARE style_access_enabled_stmt;

SET @domain_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_storage_config'
    AND column_name = 'domain'
);
SET @domain_sql = IF(
  @storage_config_table_exists = 1 AND @domain_exists = 0,
  'ALTER TABLE sys_storage_config ADD COLUMN domain varchar(255) NULL DEFAULT NULL COMMENT ''domain'' AFTER style_access_enabled',
  'SELECT 1'
);
PREPARE domain_stmt FROM @domain_sql;
EXECUTE domain_stmt;
DEALLOCATE PREPARE domain_stmt;
