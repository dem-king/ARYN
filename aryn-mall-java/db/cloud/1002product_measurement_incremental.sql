USE `aryn_product`;
SET NAMES utf8mb4;
-- 不从历史标题推断净含量；旧商品仍可按整数销售件购买。
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='sales_unit');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `sales_unit` varchar(24) NOT NULL DEFAULT ''件''', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='base_dimension');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `base_dimension` varchar(16) DEFAULT NULL', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='base_unit');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `base_unit` varchar(8) DEFAULT NULL', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='base_qty_per_sales_unit');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `base_qty_per_sales_unit` decimal(24,9) DEFAULT NULL', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='quantity_precision');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `quantity_precision` varchar(16) NOT NULL DEFAULT ''UNKNOWN''', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='min_order_qty');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `min_order_qty` int NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='step_qty');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `step_qty` int NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='unit_spec_text');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `unit_spec_text` varchar(500) DEFAULT NULL', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
SET @measurement_column_exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_sku' AND COLUMN_NAME='measurement_revision');
SET @measurement_ddl=IF(@measurement_column_exists=0, 'ALTER TABLE `goods_sku` ADD COLUMN `measurement_revision` bigint NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE measurement_stmt FROM @measurement_ddl; EXECUTE measurement_stmt; DEALLOCATE PREPARE measurement_stmt;
CREATE TABLE IF NOT EXISTS `product_sku_identifier` (
 `id` varchar(64) NOT NULL,
 `sku_id` varchar(64) NOT NULL,
 `identifier_type` varchar(24) NOT NULL,
 `display_value` varchar(200) NOT NULL,
 `normalized_value` varchar(200) NOT NULL,
 `status` varchar(16) NOT NULL DEFAULT 'PENDING',
 `source` varchar(32) NOT NULL,
 `review_by` varchar(64) DEFAULT NULL,
 `review_time` datetime DEFAULT NULL,
 `revision` bigint NOT NULL DEFAULT 0,
 `tenant_id` varchar(32) NOT NULL,
 `del_flag` char(1) NOT NULL DEFAULT '0',
 `create_by` varchar(64) DEFAULT NULL,
 `update_by` varchar(64) DEFAULT NULL,
 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `active_value` varchar(200) GENERATED ALWAYS AS (CASE WHEN del_flag='0' THEN normalized_value ELSE NULL END) STORED,
 PRIMARY KEY (`id`),
 UNIQUE KEY `uk_identifier_active` (`tenant_id`,`identifier_type`,`active_value`),
 KEY `idx_identifier_sku` (`tenant_id`,`sku_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
USE `aryn_nacos`;
UPDATE config_info SET content=REPLACE(content, '      - goods_sku\n', '      - goods_sku\n      - product_sku_identifier\n'), md5=MD5(content) WHERE data_id='aryn-product-biz-dev.yml' AND INSTR(content, '      - product_sku_identifier\n')=0;
