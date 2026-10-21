-- 商品足迹按用户+商品去重索引（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：商详每次浏览都按 tenant_id + user_id + spu_id 查询是否已有足迹，
--       足迹列表按 tenant_id + user_id 取每个商品的最近一次；
--       原表仅有 (tenant_id, del_flag, create_time) 索引，缺少 user_id/spu_id，
--       数据量增大后会退化为租户级全表扫描。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 134goods_footprint_user_spu_index_incremental.sql

USE `aryn_boot`;

SET NAMES utf8mb4;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'goods_footprint'
    AND index_name = 'idx_goods_footprint_user_spu'
);
SET @index_sql := IF(@index_exists = 0,
  'ALTER TABLE `goods_footprint` ADD INDEX `idx_goods_footprint_user_spu` (`tenant_id`, `user_id`, `del_flag`, `spu_id`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
