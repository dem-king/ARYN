-- 优惠券领取记录索引补充（Cloud 微服务模式：表进 aryn_promotion）
-- 目标库：aryn_promotion
-- 背景：C 端领券列表改为「库存 > 0 或当前用户已领取」的可见性口径后，
--       selectCouponPage 会按 (coupon_id, user_id) 走 correlated EXISTS 子查询；
--       原表仅有主键与 uk_coupon_user_source(tenant_id, user_id, source_type, source_id)，
--       该索引前缀不匹配 coupon_id，会退化成全表扫描。这里补一个覆盖索引。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_promotion < 83coupon_user_received_index.sql

USE `aryn_promotion`;

SET NAMES utf8mb4;

SET @coupon_user_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'coupon_user'
    AND index_name = 'idx_coupon_user_coupon_user'
);
SET @coupon_user_index_sql := IF(@coupon_user_index_exists = 0,
  'ALTER TABLE `coupon_user` ADD INDEX `idx_coupon_user_coupon_user` (`coupon_id`, `user_id`)',
  'SELECT 1');
PREPARE coupon_user_index_stmt FROM @coupon_user_index_sql;
EXECUTE coupon_user_index_stmt;
DEALLOCATE PREPARE coupon_user_index_stmt;

-- 自检：added_index 应为 1（按索引名去重计数，该索引含 coupon_id/user_id 两列）。
SELECT
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon_user'
       AND INDEX_NAME = 'idx_coupon_user_coupon_user') AS added_index;
