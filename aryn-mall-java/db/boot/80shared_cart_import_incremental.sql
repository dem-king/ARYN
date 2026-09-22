-- 补给单 Excel 导入（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 特性：可重复执行；不 DROP/TRUNCATE；不覆盖已有业务数据；仅新增两张表与索引。
--
-- 背景（2026-09-22 · B 版原型「详情 → 导入 → 报告」三步）：
--   船员把线下 Excel 补给清单（编码/品名/规格/数量/单位/备注）一次性导入补给单，
--   服务端按「商品编码优先、品名+规格兜底」匹配 SKU，产出四类结果报告
--   （匹配成功 / 未匹配 / 规格变更 / 超库存，外加原型未画但必然出现的数量异常），
--   未匹配行由操作者人工补选，确认后并入 shared_cart_item。
--
--   与管理端 product_import_job / product_import_row 的区别：那是**商品主数据导入**
--   （新增/更新 goods_spu、goods_sku），本表是**补给清单导入**（写 shared_cart_item），
--   两者字段、校验、落库目标都不同，不能互相复用。
--
-- 语义约定：
--   import.status     1待确认（已解析出报告） 2已并入（明细已写进补给单） 3已取消
--   row.result_type   OK匹配成功 / UNMATCHED未匹配 / SPEC_CHANGED规格变更 /
--                     OVER_STOCK超库存 / INVALID_QTY数量异常 / OFF_SHELF已下架
--   row.resolved_action  ACCEPT_SPEC确认新规格 / ADJUST_QTY调整数量 /
--                        REPLACE_SKU人工补选 / SKIP跳过（null=未处置，确认时按跳过处理）
--   数量口径：planned_quantity = Excel 里的数量，行确认后 requested_quantity 同口径写入；
--             不参与 conversion_rate 换算（全仓无引用，库存按 sku.stock 直比）。
--
-- 执行：mysql -u root -p aryn_boot < 80shared_cart_import_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 一、导入任务（一次上传 = 一个任务，报告可反复取回）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shared_cart_import` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `cart_id` varchar(32) NOT NULL COMMENT '共享购物车ID',
  `file_name` varchar(255) DEFAULT NULL COMMENT '上传文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件字节数',
  `file_sha256` char(64) DEFAULT NULL COMMENT '文件内容摘要（重复上传提示用）',
  `total_rows` int NOT NULL DEFAULT 0 COMMENT '解析出的数据行数（不含表头）',
  `matched_rows` int NOT NULL DEFAULT 0 COMMENT '匹配成功行数',
  `unmatched_rows` int NOT NULL DEFAULT 0 COMMENT '未匹配行数',
  `spec_changed_rows` int NOT NULL DEFAULT 0 COMMENT '规格变更行数',
  `over_stock_rows` int NOT NULL DEFAULT 0 COMMENT '超库存行数',
  `invalid_rows` int NOT NULL DEFAULT 0 COMMENT '数量/格式异常行数',
  `off_shelf_rows` int NOT NULL DEFAULT 0 COMMENT '已下架商品行数',
  `imported_rows` int NOT NULL DEFAULT 0 COMMENT '确认后实际并入的明细项数（按 SKU 合并后）',
  `skipped_rows` int NOT NULL DEFAULT 0 COMMENT '确认时跳过或未处置的行数',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1待确认 2已并入 3已取消',
  `operator_user_id` varchar(32) NOT NULL COMMENT '上传并确认导入的成员ID',
  `confirmed_time` datetime DEFAULT NULL COMMENT '并入补给单时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_shared_cart_import_cart` (`tenant_id`,`cart_id`,`create_time`),
  KEY `idx_shared_cart_import_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='补给单Excel导入任务';

-- ---------------------------------------------------------------------------
-- 二、导入解析行（服务端落库，确认时按行处置重新校验，不信任客户端回传内容）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shared_cart_import_row` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `import_id` varchar(32) NOT NULL COMMENT '导入任务ID',
  `cart_id` varchar(32) NOT NULL COMMENT '共享购物车ID',
  `row_no` int NOT NULL DEFAULT 0 COMMENT '行号（从1开始，不含表头）',
  `raw_code` varchar(64) DEFAULT NULL COMMENT 'Excel 商品编码原文',
  `raw_name` varchar(255) DEFAULT NULL COMMENT 'Excel 品名原文',
  `raw_spec` varchar(128) DEFAULT NULL COMMENT 'Excel 规格原文',
  `raw_quantity` int DEFAULT NULL COMMENT 'Excel 数量原文',
  `raw_unit` varchar(32) DEFAULT NULL COMMENT 'Excel 单位原文',
  `raw_remark` varchar(255) DEFAULT NULL COMMENT 'Excel 备注原文',
  `match_type` varchar(16) DEFAULT NULL COMMENT '匹配方式：CODE编码 / NAME品名 / AMBIGUOUS多规格待选 / NONE未命中',
  `matched_sku_id` varchar(32) DEFAULT NULL COMMENT '匹配到的SKU ID',
  `matched_spu_id` varchar(32) DEFAULT NULL COMMENT '匹配到的SPU ID',
  `matched_name` varchar(255) DEFAULT NULL COMMENT '匹配到的商品名',
  `matched_spec` varchar(255) DEFAULT NULL COMMENT '匹配到的规格描述',
  `matched_unit` varchar(32) DEFAULT NULL COMMENT '匹配到的采购单位',
  `matched_price` decimal(10,2) DEFAULT NULL COMMENT '匹配时的售价快照（仅展示）',
  `matched_stock` int DEFAULT NULL COMMENT '匹配时的库存快照（仅展示）',
  `planned_quantity` int DEFAULT NULL COMMENT '本次计划采购量（确认后写入 shared_cart_item）',
  `suggested_quantity` int DEFAULT NULL COMMENT '服务端建议数量（超库存调减/数量异常就近取整）',
  `result_type` varchar(24) NOT NULL DEFAULT 'UNMATCHED' COMMENT '结果：OK/UNMATCHED/SPEC_CHANGED/OVER_STOCK/INVALID_QTY/OFF_SHELF',
  `result_message` varchar(500) DEFAULT NULL COMMENT '结果说明（给用户看）',
  `resolved_action` varchar(24) DEFAULT NULL COMMENT '用户处置：ACCEPT_SPEC/ADJUST_QTY/REPLACE_SKU/SKIP',
  `resolved_sku_id` varchar(32) DEFAULT NULL COMMENT '人工补选后的SKU ID',
  `resolved_quantity` int DEFAULT NULL COMMENT '调整后的数量',
  `resolved_time` datetime DEFAULT NULL COMMENT '处置时间',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shared_cart_import_row` (`tenant_id`,`import_id`,`row_no`),
  KEY `idx_shared_cart_import_row_cart` (`tenant_id`,`cart_id`),
  KEY `idx_shared_cart_import_row_result` (`tenant_id`,`import_id`,`result_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='补给单Excel导入解析行';

SET FOREIGN_KEY_CHECKS = 1;
