USE `aryn_upms`;

SET @storage_config_exists = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'sys_storage_config'
);
SET @storage_type_sql = IF(
  @storage_config_exists = 1,
  'ALTER TABLE sys_storage_config MODIFY COLUMN type char(10) NOT NULL COMMENT ''存储类型''',
  'SELECT 1'
);
PREPARE storage_type_stmt FROM @storage_type_sql;
EXECUTE storage_type_stmt;
DEALLOCATE PREPARE storage_type_stmt;

SET @storage_data_sql = IF(
  @storage_config_exists = 1,
  'UPDATE sys_storage_config SET type = CASE type WHEN ''1'' THEN ''aliyun'' WHEN ''2'' THEN ''tencent'' WHEN ''3'' THEN ''qiniu'' WHEN ''4'' THEN ''minio'' WHEN ''oss'' THEN IF(style_access_enabled = 1, ''minio'', ''aliyun'') ELSE type END WHERE type IN (''1'', ''2'', ''3'', ''4'', ''oss'')',
  'SELECT 1'
);
PREPARE storage_data_stmt FROM @storage_data_sql;
EXECUTE storage_data_stmt;
DEALLOCATE PREPARE storage_data_stmt;

SET @storage_dict_exists = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_value'
);
SET @storage_dict_sql = IF(
  @storage_dict_exists = 1,
  'INSERT INTO sys_dict_value (id, dict_id, dict_label, dict_value, dict_type, status, remarks, sort, del_flag, create_time, create_by, show_class) VALUES (''2040457078937530370'', ''1583357541572108290'', ''本机存储'', ''local'', ''sys_storage_type'', ''0'', ''服务器本地磁盘'', 1, ''0'', NOW(), ''system'', ''primary''), (''1583358198555303938'', ''1583357541572108290'', ''阿里OSS'', ''aliyun'', ''sys_storage_type'', ''0'', ''阿里云对象存储'', 2, ''0'', NOW(), ''system'', ''primary''), (''1583358231816134658'', ''1583357541572108290'', ''七牛云'', ''qiniu'', ''sys_storage_type'', ''0'', ''七牛云对象存储'', 3, ''0'', NOW(), ''system'', ''primary''), (''1583364488060952577'', ''1583357541572108290'', ''腾讯云'', ''tencent'', ''sys_storage_type'', ''0'', ''腾讯云对象存储'', 4, ''0'', NOW(), ''system'', ''primary''), (''1928797196747186177'', ''1583357541572108290'', ''MinIO'', ''minio'', ''sys_storage_type'', ''0'', ''S3兼容自建存储'', 5, ''0'', NOW(), ''system'', ''primary'') ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), status = ''0'', remarks = VALUES(remarks), sort = VALUES(sort), del_flag = ''0''',
  'SELECT 1'
);
PREPARE storage_dict_stmt FROM @storage_dict_sql;
EXECUTE storage_dict_stmt;
DEALLOCATE PREPARE storage_dict_stmt;

SET @legacy_storage_dict_sql = IF(
  @storage_dict_exists = 1,
  'UPDATE sys_dict_value SET del_flag = ''1'' WHERE id = ''2040457134491086849''',
  'SELECT 1'
);
PREPARE legacy_storage_dict_stmt FROM @legacy_storage_dict_sql;
EXECUTE legacy_storage_dict_stmt;
DEALLOCATE PREPARE legacy_storage_dict_stmt;

