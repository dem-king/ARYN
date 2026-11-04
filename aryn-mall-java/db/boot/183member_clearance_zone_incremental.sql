-- 会员专区与低价捡漏规则化（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：会员价/出清价从「运营逐个 SKU 配活动」下沉为「商品级标签 + 规则自动算价」。
--       会员专区按 is_member_item 选品，出清专区按 is_clearance_item + 到期时间选品；
--       标签同步到系统托管活动（activity_code=SYS_MEMBER_ZONE / SYS_CLEARANCE_ZONE）复用既有促销引擎。
-- 结构：
--   goods_spu：会员/出清标签与默认规则（SPU 级）
--   goods_sku：同义覆盖（NULL=继承 SPU）
--   promotion_activity：系统托管活动标识 activity_code
-- 特性：可重复执行（information_schema 守卫），不 DROP/TRUNCATE 存量数据。
-- 配套：GoodsSpu/GoodsSku 实体、MallZoneActivitySyncService、GoodsZoneTagService。
--
-- 执行：mysql -u root -p aryn_boot < 183member_clearance_zone_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ===========================================================================
-- 1. goods_spu：专区标签与默认规则
-- ===========================================================================
SET @spu_is_member_item := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND COLUMN_NAME = 'is_member_item'
);
SET @ddl := IF(@spu_is_member_item = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `is_member_item` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''会员专区标签：0否 1是'' AFTER `cost_price`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @spu_member_discount_rate := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND COLUMN_NAME = 'member_discount_rate'
);
SET @ddl := IF(@spu_member_discount_rate = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `member_discount_rate` decimal(5,4) NULL DEFAULT NULL COMMENT ''商品级会员折扣率，如0.9000；空=用等级折扣率'' AFTER `is_member_item`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @spu_member_price_fixed := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND COLUMN_NAME = 'member_price_fixed'
);
SET @ddl := IF(@spu_member_price_fixed = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `member_price_fixed` decimal(10,2) NULL DEFAULT NULL COMMENT ''会员固定价（迁移旧活动用）；与按率算价取低'' AFTER `member_discount_rate`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @spu_member_price_floor := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND COLUMN_NAME = 'member_price_floor'
);
SET @ddl := IF(@spu_member_price_floor = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `member_price_floor` decimal(10,2) NULL DEFAULT NULL COMMENT ''会员价下限（防击穿），空=0.01'' AFTER `member_price_fixed`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @spu_is_clearance_item := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND COLUMN_NAME = 'is_clearance_item'
);
SET @ddl := IF(@spu_is_clearance_item = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `is_clearance_item` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''捡漏专区标签：0否 1是'' AFTER `member_price_floor`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @spu_clearance_price := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND COLUMN_NAME = 'clearance_price'
);
SET @ddl := IF(@spu_clearance_price = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `clearance_price` decimal(10,2) NULL DEFAULT NULL COMMENT ''出清固定价，与 clearance_discount_rate 二选一'' AFTER `is_clearance_item`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @spu_clearance_discount_rate := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND COLUMN_NAME = 'clearance_discount_rate'
);
SET @ddl := IF(@spu_clearance_discount_rate = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `clearance_discount_rate` decimal(5,4) NULL DEFAULT NULL COMMENT ''出清折扣率（对 original_price 计算）'' AFTER `clearance_price`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @spu_clearance_expire_time := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND COLUMN_NAME = 'clearance_expire_time'
);
SET @ddl := IF(@spu_clearance_expire_time = 0,
  'ALTER TABLE `goods_spu` ADD COLUMN `clearance_expire_time` datetime NULL DEFAULT NULL COMMENT ''出清截止时间，到期自动退出专区与出清价'' AFTER `clearance_discount_rate`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 2. goods_sku：同义覆盖（NULL=继承 SPU）
-- ===========================================================================
SET @sku_is_member_item := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND COLUMN_NAME = 'is_member_item'
);
SET @ddl := IF(@sku_is_member_item = 0,
  'ALTER TABLE `goods_sku` ADD COLUMN `is_member_item` tinyint(1) NULL DEFAULT NULL COMMENT ''会员专区标签：NULL继承SPU 0否 1是'' AFTER `pic_url`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sku_member_discount_rate := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND COLUMN_NAME = 'member_discount_rate'
);
SET @ddl := IF(@sku_member_discount_rate = 0,
  'ALTER TABLE `goods_sku` ADD COLUMN `member_discount_rate` decimal(5,4) NULL DEFAULT NULL COMMENT ''SKU会员折扣率覆盖；空=继承SPU'' AFTER `is_member_item`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sku_member_price_fixed := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND COLUMN_NAME = 'member_price_fixed'
);
SET @ddl := IF(@sku_member_price_fixed = 0,
  'ALTER TABLE `goods_sku` ADD COLUMN `member_price_fixed` decimal(10,2) NULL DEFAULT NULL COMMENT ''SKU会员固定价覆盖；空=继承SPU'' AFTER `member_discount_rate`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sku_member_price_floor := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND COLUMN_NAME = 'member_price_floor'
);
SET @ddl := IF(@sku_member_price_floor = 0,
  'ALTER TABLE `goods_sku` ADD COLUMN `member_price_floor` decimal(10,2) NULL DEFAULT NULL COMMENT ''SKU会员价下限覆盖；空=继承SPU'' AFTER `member_price_fixed`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sku_is_clearance_item := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND COLUMN_NAME = 'is_clearance_item'
);
SET @ddl := IF(@sku_is_clearance_item = 0,
  'ALTER TABLE `goods_sku` ADD COLUMN `is_clearance_item` tinyint(1) NULL DEFAULT NULL COMMENT ''捡漏专区标签：NULL继承SPU 0否 1是'' AFTER `member_price_floor`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sku_clearance_price := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND COLUMN_NAME = 'clearance_price'
);
SET @ddl := IF(@sku_clearance_price = 0,
  'ALTER TABLE `goods_sku` ADD COLUMN `clearance_price` decimal(10,2) NULL DEFAULT NULL COMMENT ''SKU出清固定价覆盖；空=继承SPU'' AFTER `is_clearance_item`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sku_clearance_discount_rate := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND COLUMN_NAME = 'clearance_discount_rate'
);
SET @ddl := IF(@sku_clearance_discount_rate = 0,
  'ALTER TABLE `goods_sku` ADD COLUMN `clearance_discount_rate` decimal(5,4) NULL DEFAULT NULL COMMENT ''SKU出清折扣率覆盖；空=继承SPU'' AFTER `clearance_price`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sku_clearance_expire_time := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND COLUMN_NAME = 'clearance_expire_time'
);
SET @ddl := IF(@sku_clearance_expire_time = 0,
  'ALTER TABLE `goods_sku` ADD COLUMN `clearance_expire_time` datetime NULL DEFAULT NULL COMMENT ''SKU出清截止时间覆盖；空=继承SPU'' AFTER `clearance_discount_rate`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 3. 选品索引
-- ===========================================================================
SET @idx_member_item := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND INDEX_NAME = 'idx_member_item'
);
SET @ddl := IF(@idx_member_item = 0,
  'ALTER TABLE `goods_spu` ADD INDEX `idx_member_item` (`tenant_id`, `is_member_item`, `status`, `del_flag`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_clearance_item := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_spu' AND INDEX_NAME = 'idx_clearance_item'
);
SET @ddl := IF(@idx_clearance_item = 0,
  'ALTER TABLE `goods_spu` ADD INDEX `idx_clearance_item` (`tenant_id`, `is_clearance_item`, `clearance_expire_time`, `status`, `del_flag`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 4. promotion_activity：系统托管活动标识
-- ===========================================================================
SET @activity_code := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity' AND COLUMN_NAME = 'activity_code'
);
SET @ddl := IF(@activity_code = 0,
  'ALTER TABLE `promotion_activity` ADD COLUMN `activity_code` varchar(64) NULL DEFAULT NULL COMMENT ''系统托管活动标识（SYS_MEMBER_ZONE/SYS_CLEARANCE_ZONE），手工活动为空'' AFTER `activity_type`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.1 清理早期版本的非唯一索引（仅索引，不动数据）
SET @idx_activity_code_legacy := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity'
    AND INDEX_NAME = 'idx_promotion_activity_code'
);
SET @ddl := IF(@idx_activity_code_legacy > 0,
  'ALTER TABLE `promotion_activity` DROP INDEX `idx_promotion_activity_code`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.2 唯一索引：同步任务并发（打标保存 + 兜底 Job）时防止产生两条系统活动。
--     MySQL 唯一索引允许多个 NULL，手工活动（activity_code 为空）不受影响。
--     存量若已有重复系统活动，先不建索引（避免脚本失败），由 duplicate_activity_code_groups 暴露，
--     人工合并后重跑；应用层 DuplicateKey 兜底仍然生效。
SET @dup_activity_code_groups := (
  SELECT COUNT(*) FROM (
    SELECT `tenant_id`, `activity_code`
    FROM `promotion_activity`
    WHERE `activity_code` IS NOT NULL AND `del_flag` = '0'
    GROUP BY `tenant_id`, `activity_code`
    HAVING COUNT(*) > 1
  ) AS duplicated
);
SET @uk_activity_code := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity'
    AND INDEX_NAME = 'uk_promotion_activity_code'
);
SET @ddl := IF(@uk_activity_code = 0 AND @dup_activity_code_groups = 0,
  'ALTER TABLE `promotion_activity` ADD UNIQUE INDEX `uk_promotion_activity_code` (`tenant_id`, `activity_code`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT @dup_activity_code_groups AS duplicate_activity_code_groups;

SET FOREIGN_KEY_CHECKS = 1;
