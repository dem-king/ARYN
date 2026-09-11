-- 悦航购商品批量导入与变更审计域增量迁移（Cloud 微服务模式）
-- 目标库：aryn_product
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_product < 48product_import_incremental.sql

USE `aryn_product`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 商品导入任务
CREATE TABLE IF NOT EXISTS `product_import_job` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `job_no` varchar(64) NOT NULL COMMENT '任务编号',
  `file_name` varchar(255) DEFAULT NULL COMMENT '导入文件名',
  `file_url` varchar(500) DEFAULT NULL COMMENT '导入文件访问URL',
  `total_rows` int NOT NULL DEFAULT 0 COMMENT '总行数',
  `success_rows` int NOT NULL DEFAULT 0 COMMENT '成功行数',
  `error_rows` int NOT NULL DEFAULT 0 COMMENT '错误行数',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1待确认 2导入中 3已完成 4已关闭',
  `completed_time` datetime DEFAULT NULL COMMENT '导入完成时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_import_job_no` (`tenant_id`,`job_no`),
  KEY `idx_product_import_job_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品导入任务';

-- 商品导入错误行
CREATE TABLE IF NOT EXISTS `product_import_error` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `job_id` varchar(32) NOT NULL COMMENT '导入任务ID',
  `row_no` int NOT NULL DEFAULT 0 COMMENT '行号（从1开始）',
  `error_type` varchar(32) DEFAULT NULL COMMENT '错误类型：EMPTY_NAME/DUPLICATE_CODE/DUPLICATE_SKU/ILLEGAL_PRICE/ILLEGAL_STOCK/ILLEGAL_QTY_RULE/UNKNOWN_CATEGORY/UNKNOWN_BRAND/MISSING_UPDATE_TARGET/CROSS_TENANT_CODE/OTHER',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误说明',
  `raw_content` varchar(1000) DEFAULT NULL COMMENT '原始行内容（受控JSON）',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_product_import_error_job` (`tenant_id`,`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品导入错误行';

-- 商品资料变更审计
CREATE TABLE IF NOT EXISTS `product_change_log` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型：SPU/SKU/SHIP_PROFILE/CODE_MAPPING/IMPORT_JOB',
  `biz_id` varchar(32) NOT NULL COMMENT '业务ID',
  `change_type` varchar(16) NOT NULL COMMENT '变更类型：CREATE/UPDATE',
  `before_snapshot` text DEFAULT NULL COMMENT '变更前快照（受控JSON）',
  `after_snapshot` text DEFAULT NULL COMMENT '变更后快照（受控JSON）',
  `operator_id` varchar(32) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名快照',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_product_change_log_biz` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_product_change_log_time` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品资料变更审计';

SET FOREIGN_KEY_CHECKS = 1;
