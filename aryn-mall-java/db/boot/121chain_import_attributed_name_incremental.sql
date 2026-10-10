-- ============================================================================
-- 共享购物车接龙粘贴导入增量（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 租户白名单：本脚本仅对已有表 shared_cart_import_row / shared_cart_item 增加列
--             并调整唯一键，不新增表，无需改动白名单。
-- 特性：幂等执行，不删除或重建数据；不修改既有列类型，不覆盖业务数据。
--
-- 背景：船员报货接龙发在微信群，工作人员在小程序创建共享购物车代为下单。
--       接龙里的很多人从未登录过小程序、甚至不是系统用户——「归属」只能是
--       姓名标签而不是用户账号。粘贴整段接龙 → 解析成「人 × 商品 × 数量」→
--       人工核对 → 并入共享购物车 → 按人拆行提交整船订单（标签贴到人）。
--
-- 本次改造：
--   1. shared_cart_import_row 增加来源与接龙人名：
--        source_type   EXCEL=补给清单文件导入（存量行默认值）/ CHAIN=接龙文本粘贴
--        person_name   接龙人名原文（CHAIN 来源），纯职务称呼按原文保留
--   2. shared_cart_item 增加归属人姓名快照 attributed_name（NOT NULL DEFAULT ''，
--      空串 = 行归属就是 user_id 本人，与既有行为完全一致）：
--      接龙代报的明细挂在操作者名下，接龙人名落在这里——配送贴标签、按人
--      分装认的是这个名字。
--   3. 唯一键 uk_shared_cart_item 扩一列 (tenant,cart,user,sku,attributed_name)：
--      两个人在同一车里订同一种商品时靠归属姓名区分，否则同 SKU 撞键。
--      存量行 attributed_name=''，扩列后键值与原来逐行等价。
--
-- 消费方：SharedCartServiceImpl#previewChainImport / confirmImport（按人并入）、
--         splitByMember（contributorName 优先取归属姓名）。
-- 执行：mysql -u root -p aryn_boot < 121chain_import_attributed_name_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============ 1. shared_cart_import_row.source_type / person_name ============
SET @add_row_source_type = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart_import_row` ADD COLUMN `source_type` varchar(16) NOT NULL DEFAULT ''EXCEL'' COMMENT ''来源：EXCEL补给清单文件 / CHAIN接龙文本粘贴'' AFTER `row_no`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_import_row' AND COLUMN_NAME = 'source_type'
);
PREPARE stmt FROM @add_row_source_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_row_person_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart_import_row` ADD COLUMN `person_name` varchar(64) DEFAULT NULL COMMENT ''接龙人名原文（CHAIN 来源；纯职务称呼按原文保留）'' AFTER `source_type`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_import_row' AND COLUMN_NAME = 'person_name'
);
PREPARE stmt FROM @add_row_person_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ 2. shared_cart_item.attributed_name ============
SET @add_item_attributed_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart_item` ADD COLUMN `attributed_name` varchar(64) NOT NULL DEFAULT '''' COMMENT ''归属人姓名快照（接龙代报；空串=归属即 user_id 本人）'' AFTER `user_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_item' AND COLUMN_NAME = 'attributed_name'
);
PREPARE stmt FROM @add_item_attributed_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ 3. 唯一键扩列 (tenant,cart,user,sku) → (tenant,cart,user,sku,attributed_name) ============
-- 三态守卫：新键已存在 → 跳过；旧键存在 → 原子替换；键不存在 → 直接建。
SET @uk_has_name_col = (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_item'
    AND INDEX_NAME = 'uk_shared_cart_item' AND COLUMN_NAME = 'attributed_name'
);
SET @uk_exists = (
  SELECT COUNT(DISTINCT INDEX_NAME)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_item'
    AND INDEX_NAME = 'uk_shared_cart_item'
);
SET @rebuild_uk_shared_cart_item = (
  SELECT IF(@uk_exists = 0,
    'ALTER TABLE `shared_cart_item` ADD UNIQUE KEY `uk_shared_cart_item` (`tenant_id`,`cart_id`,`user_id`,`sku_id`,`attributed_name`)',
    IF(@uk_has_name_col = 0,
      'ALTER TABLE `shared_cart_item` DROP INDEX `uk_shared_cart_item`, ADD UNIQUE KEY `uk_shared_cart_item` (`tenant_id`,`cart_id`,`user_id`,`sku_id`,`attributed_name`)',
      'SELECT 1'))
);
PREPARE stmt FROM @rebuild_uk_shared_cart_item; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：两个新列应存在；唯一键应包含 5 列（attributed_name 出现即新键）
SELECT `TABLE_NAME`, `COLUMN_NAME`, `COLUMN_TYPE`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (   (`TABLE_NAME` = 'shared_cart_import_row' AND `COLUMN_NAME` IN ('source_type', 'person_name'))
       OR (`TABLE_NAME` = 'shared_cart_item'        AND `COLUMN_NAME` = 'attributed_name'))
ORDER BY `TABLE_NAME`, `COLUMN_NAME`;

SELECT `INDEX_NAME`, `SEQ_IN_INDEX`, `COLUMN_NAME`
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_item'
  AND INDEX_NAME = 'uk_shared_cart_item'
ORDER BY `SEQ_IN_INDEX`;

SET FOREIGN_KEY_CHECKS = 1;
