-- 补给单导入模板改为「按分类铺满在售商品」+ 未填数量结果类型（Cloud 微服务模式）
--
-- 目标库：aryn_order（订单库）、aryn_product（商品库）
-- 特性：可重复执行；不 DROP/TRUNCATE；不覆盖已有业务数据；仅加列与建索引。
--
-- 背景（2026-09-28）：
--   原「下载标准模板」只给一行示例（IMPA123456 / 鲜牛奶 950ml / …），
--   客户要自己把商品名、规格、编码一条条敲进去，商品一多就很容易：
--     · 编码抄错 → 整行未匹配；
--     · 品名带了自己的叫法 → 命中不了；
--     · 规格写法与商品资料不一致 → 被报「规格变更」。
--   改为导出「按分类铺满在售商品」的采购目录后，客户只需要在「数量」列
--   填要买的那几行，其余行留空即可。
--
--   这带来一个必须落库的语义变化：目录模板里**绝大多数行天然没填数量**。
--   原先 shared_cart_import_row.result_type 只有
--   OK/UNMATCHED/SPEC_CHANGED/OVER_STOCK/INVALID_QTY/OFF_SHELF 六种，
--   而 INVALID_QTY 的语义包含「数量为空」——若沿用，客户上传 273 行的目录、
--   只填了 5 行数量，报告会显示 268 行「数量异常」，
--   真正的错误（编码抄错、库存不足）会被彻底淹没。
--
--   因此新增 NOT_FILLED（未填数量 = 本次不采购，不是错误）并单独计数：
--   row.result_type        NOT_FILLED 与 INVALID_QTY 语义分离
--   import.not_filled_rows 未填数量行数（不计入 invalid_rows）
--
--   与 Boot 模式脚本（db/boot/100replenish_catalog_template_incremental.sql）内容一致，
--   仅拆分到对应的库执行。
--
-- 配套代码：ReplenishImportClassifier（判定优先级）、ReplenishImportExcel.catalogHead/catalogRow、
--          ReplenishImportMatchServiceImpl#exportCatalog、SharedCartServiceImpl#previewImport
--
-- 执行：mysql -u root -p < 100replenish_catalog_template_incremental.sql

-- ===========================================================================
-- 订单库：报告新增「未填数量」计数
-- ===========================================================================
USE `aryn_order`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、shared_cart_import 增加「未填数量」行数计数
--    单独计数而不并入 invalid_rows：两者归因完全不同，
--    混在一起后运营无法从报表看出「客户是没填，还是填错了」。
-- ---------------------------------------------------------------------------
SET @add_not_filled_rows = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart_import` ADD COLUMN `not_filled_rows` int NOT NULL DEFAULT 0 COMMENT ''未填数量行数（客户本次不采购，非错误）'' AFTER `off_shelf_rows`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_import' AND COLUMN_NAME = 'not_filled_rows'
);
PREPARE stmt FROM @add_not_filled_rows; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 二、result_type 注释补上 NOT_FILLED
--    列本身是 varchar(24)，无需变更类型；只更新注释以免后人以为取值只有六种。
-- ---------------------------------------------------------------------------
SET @row_column_type = (
  SELECT COLUMN_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart_import_row' AND COLUMN_NAME = 'result_type'
);
SET @comment_sql = IF(
  @row_column_type IS NULL,
  'SELECT 1',
  CONCAT('ALTER TABLE `shared_cart_import_row` MODIFY COLUMN `result_type` ', @row_column_type,
         ' NOT NULL DEFAULT ''UNMATCHED'' COMMENT ''结果：OK匹配成功/UNMATCHED未匹配/SPEC_CHANGED规格变更/OVER_STOCK超库存/INVALID_QTY数量异常/OFF_SHELF已下架/NOT_FILLED未填数量（客户本次不采购，非错误）''')
);
PREPARE stmt FROM @comment_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 商品库：目录导出按分类排序所需的覆盖索引
-- ===========================================================================
USE `aryn_product`;

-- ---------------------------------------------------------------------------
-- 三、目录导出按分类排序，补一个覆盖索引
--    导出语句是「在售商品全表按类目排序」，缺索引时每次下载都会 filesort。
--    仅加索引，不改动任何数据。
-- ---------------------------------------------------------------------------
SET @add_idx_catalog = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `goods_sku` ADD INDEX `idx_goods_sku_catalog` (`tenant_id`, `status`, `del_flag`, `spu_id`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku' AND INDEX_NAME = 'idx_goods_sku_catalog'
);
PREPARE stmt FROM @add_idx_catalog; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 四、自检
-- ---------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku'
       AND COLUMN_NAME = 'sales_price') AS sku_table_reachable,
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_sku'
       AND INDEX_NAME = 'idx_goods_sku_catalog') AS added_catalog_index;

-- 自检口径：sku_table_reachable 应为 1（确认当前库是 aryn_product）；
-- added_catalog_index 等于该索引的**列数**（information_schema.STATISTICS 每个索引列一行，
-- 本索引 4 列故为 4），不是 0 即表示索引已建。
-- 订单库的两个自检项请在 aryn_order 下单独确认：
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA='aryn_order' AND TABLE_NAME='shared_cart_import' AND COLUMN_NAME='not_filled_rows';
-- 存量报告的 invalid_rows 不回填：无法区分历史「数量为空」是被判成异常还是真的填错，
-- 改写历史统计会污染已有的运营报表。

SET FOREIGN_KEY_CHECKS = 1;
