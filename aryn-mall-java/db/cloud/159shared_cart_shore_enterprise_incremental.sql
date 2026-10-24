-- 共享购物车岸上企业车支持（Cloud 微服务模式）
--
-- 目标库：aryn_order（订单库）
-- 租户白名单：仅对已有表 shared_cart 增加列/生成列/索引，不新增表，无需改动 hx.tenant.tables。
-- 特性：幂等执行，不删除或重建数据，不覆盖业务数据。
--
-- 背景（2026-10-24）：共享购物车此前从创建到下单全部绑死「船舶成员+靠港计划」，
-- 港口附近工厂等岸上客户无法使用。本次引入车类型：
--   cart_type=1 船供整车（默认，存量数据同此）：vessel_id/vessel_call_id 必填；
--   cart_type=2 岸上企业：vessel 两列置空，改挂发起人地址簿收货地址（user_address_id）
--   与展示快照（address_snapshot），下单走公司配送（岸上）delivery_way=3。
--
-- 执行：mysql -u root -p aryn_order < 159shared_cart_shore_enterprise_incremental.sql

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 1. 车类型与岸上配送字段
-- ===========================================================================
SET @add_shared_cart_type = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `cart_type` char(2) NOT NULL DEFAULT ''1'' COMMENT ''车类型：1船供整车（默认） 2岸上企业'' AFTER `cart_no`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'cart_type'
);
PREPARE stmt FROM @add_shared_cart_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_shared_cart_address_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `user_address_id` varchar(32) DEFAULT NULL COMMENT ''岸上车收货地址ID（地址簿）；船供车为空'' AFTER `vessel_call_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'user_address_id'
);
PREPARE stmt FROM @add_shared_cart_address_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_shared_cart_address_snapshot = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `address_snapshot` varchar(500) DEFAULT NULL COMMENT ''岸上车收货地址快照（创建时拼接，展示用）'' AFTER `user_address_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'address_snapshot'
);
PREPARE stmt FROM @add_shared_cart_address_snapshot; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 2. 船舶两列放开可空（岸上车不挂船）
--    仅在仍为 NOT NULL 时执行 MODIFY；存量行已有值不受影响。
-- ===========================================================================
SET @modify_vessel_id_nullable = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `shared_cart` MODIFY COLUMN `vessel_id` varchar(32) NULL COMMENT ''船舶ID（船供车必填；岸上车为空）''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'vessel_id' AND IS_NULLABLE = 'NO'
);
PREPARE stmt FROM @modify_vessel_id_nullable; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @modify_vessel_call_nullable = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `shared_cart` MODIFY COLUMN `vessel_call_id` varchar(32) NULL COMMENT ''靠港计划ID（船供车必填；岸上车为空）''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'vessel_call_id' AND IS_NULLABLE = 'NO'
);
PREPARE stmt FROM @modify_vessel_call_nullable; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 3. 岸上车「同一发起人同时一张收集中」唯一性 —— 生成列 + 唯一索引
--    既有 uk_shared_cart_active 按 vessel_id 去重：船供车照常生效；
--    岸上车 vessel_id 为 NULL，MySQL 唯一索引对 NULL 不去重，并发插入会开出多张，
--    因此按发起人单列兜底（岸上地址是发起人私有的，没有「同船」共享维度）。
--    与 active_flag 同一手法：仅岸上车且收集中且未删除时取 owner_user_id，其余 NULL。
-- ===========================================================================
SET @add_shared_cart_shore_flag = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `shore_active_owner` varchar(32) GENERATED ALWAYS AS (CASE WHEN `cart_type` = ''2'' AND `status` = ''2'' AND `del_flag` = ''0'' THEN `owner_user_id` ELSE NULL END) STORED COMMENT ''岸上车进行中标记：岸上+收集中+未删除时为发起人，其余NULL以允许多行''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'shore_active_owner'
);
PREPARE stmt FROM @add_shared_cart_shore_flag; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_shared_cart_shore_uk = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD UNIQUE KEY `uk_shared_cart_shore_active` (`tenant_id`, `shore_active_owner`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND INDEX_NAME = 'uk_shared_cart_shore_active'
);
PREPARE stmt FROM @add_shared_cart_shore_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 4. 岸上车按发起人查询的支撑索引
-- ===========================================================================
SET @add_shared_cart_owner_type_idx = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD INDEX `idx_shared_cart_owner_type` (`tenant_id`, `owner_user_id`, `cart_type`, `status`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND INDEX_NAME = 'idx_shared_cart_owner_type'
);
PREPARE stmt FROM @add_shared_cart_owner_type_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ===========================================================================
-- 5. 自检：新增列/生成列/索引应存在，vessel 两列应为可空
-- ===========================================================================
SELECT `COLUMN_NAME`, `COLUMN_TYPE`, `IS_NULLABLE`, `COLUMN_COMMENT`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart'
  AND `COLUMN_NAME` IN ('cart_type', 'user_address_id', 'address_snapshot', 'shore_active_owner', 'vessel_id', 'vessel_call_id')
ORDER BY `COLUMN_NAME`;

SELECT `INDEX_NAME`, `NON_UNIQUE`, GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart'
  AND `INDEX_NAME` IN ('uk_shared_cart_shore_active', 'idx_shared_cart_owner_type')
GROUP BY `INDEX_NAME`, `NON_UNIQUE`;

SET FOREIGN_KEY_CHECKS = 1;
