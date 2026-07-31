USE aryn_boot;

-- 修复已创建会员字段但遗漏累计积分字段的存量库，可重复执行。
SET @aryn_total_point_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_info'
    AND column_name = 'total_point'
);
SET @aryn_total_point_ddl := IF(
  @aryn_total_point_exists = 0,
  'ALTER TABLE user_info ADD COLUMN total_point int NOT NULL DEFAULT 0 COMMENT ''累计获得积分'' AFTER point',
  'SELECT 1'
);
PREPARE aryn_total_point_stmt FROM @aryn_total_point_ddl;
EXECUTE aryn_total_point_stmt;
DEALLOCATE PREPARE aryn_total_point_stmt;

UPDATE user_info
SET total_point = GREATEST(COALESCE(point, 0), 0)
WHERE @aryn_total_point_exists = 0;
