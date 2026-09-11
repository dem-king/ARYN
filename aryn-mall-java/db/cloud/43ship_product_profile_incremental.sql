-- 悦航购船供商品资料域增量迁移（Cloud 微服务模式）
-- 目标库：aryn_product
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_product < 43ship_product_profile_incremental.sql

USE `aryn_product`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- SPU 船供资料（与 goods_spu 一对一扩展）
CREATE TABLE IF NOT EXISTS `ship_goods_profile` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sale_scope` char(2) NOT NULL DEFAULT '3' COMMENT '销售范围：1仅个人购买 2仅船供采购 3个人和船供均可',
  `impa_code` varchar(32) DEFAULT NULL COMMENT 'IMPA 编码',
  `issa_code` varchar(32) DEFAULT NULL COMMENT 'ISSA 编码',
  `internal_item_code` varchar(64) DEFAULT NULL COMMENT '内部物料编码',
  `barcode` varchar(64) DEFAULT NULL COMMENT '条形码',
  `name_en` varchar(255) DEFAULT NULL COMMENT '英文品名',
  `search_aliases` varchar(500) DEFAULT NULL COMMENT '搜索别名（逗号分隔）',
  `storage_type` char(2) DEFAULT NULL COMMENT '储存条件：1常温 2冷藏 3冷冻 4危险品 5其他',
  `shelf_life_days` int DEFAULT NULL COMMENT '保质期天数',
  `temperature_requirement` varchar(128) DEFAULT NULL COMMENT '温度要求说明',
  `ship_supply_remark` varchar(500) DEFAULT NULL COMMENT '船供说明',
  `publish_completeness` int NOT NULL DEFAULT 0 COMMENT '资料完整度（0-100）',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ship_goods_profile_spu` (`tenant_id`,`spu_id`),
  KEY `idx_ship_goods_profile_impa` (`tenant_id`,`impa_code`),
  KEY `idx_ship_goods_profile_issa` (`tenant_id`,`issa_code`),
  KEY `idx_ship_goods_profile_internal` (`tenant_id`,`internal_item_code`),
  KEY `idx_ship_goods_profile_barcode` (`tenant_id`,`barcode`),
  KEY `idx_ship_goods_profile_scope` (`tenant_id`,`sale_scope`),
  KEY `idx_ship_goods_profile_completeness` (`tenant_id`,`publish_completeness`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SPU船供资料';

-- SKU 包装资料（与 goods_sku 一对一扩展）
CREATE TABLE IF NOT EXISTS `ship_sku_profile` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `base_unit` varchar(32) DEFAULT NULL COMMENT '基本单位（零售单位）',
  `purchase_unit` varchar(32) DEFAULT NULL COMMENT '采购单位',
  `conversion_rate` decimal(10,4) NOT NULL DEFAULT 1.0000 COMMENT '采购单位换算基本单位倍率',
  `package_spec` varchar(128) DEFAULT NULL COMMENT '箱规（中文包装规格）',
  `package_spec_en` varchar(255) DEFAULT NULL COMMENT '箱规（英文包装规格）',
  `moq` int NOT NULL DEFAULT 1 COMMENT '最小起订量',
  `step_qty` int NOT NULL DEFAULT 1 COMMENT '数量步长',
  `gross_weight` decimal(12,3) DEFAULT NULL COMMENT '毛重（kg）',
  `volume` decimal(12,4) DEFAULT NULL COMMENT '体积（m³）',
  `stock_warning_line` int NOT NULL DEFAULT 0 COMMENT '库存预警线',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ship_sku_profile_sku` (`tenant_id`,`sku_id`),
  KEY `idx_ship_sku_profile_spu` (`tenant_id`,`spu_id`),
  KEY `idx_ship_sku_profile_warning` (`tenant_id`,`stock_warning_line`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SKU包装资料';

-- 商品编码映射（IMPA/ISSA/条码/内部编码/供应商编码）
CREATE TABLE IF NOT EXISTS `product_code_mapping` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `code_type` varchar(16) NOT NULL COMMENT '编码类型：IMPA/ISSA/BARCODE/INTERNAL/SUPPLIER',
  `code_value` varchar(64) NOT NULL COMMENT '编码值',
  `match_source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '匹配来源：MANUAL人工/IMPORT导入/AI智能/EXTERNAL外部',
  `confidence` decimal(5,2) DEFAULT NULL COMMENT '匹配置信度（0-100，人工为空）',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1生效 0停用',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人', `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间', `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code_mapping_code` (`tenant_id`,`code_type`,`code_value`,`del_flag`),
  KEY `idx_product_code_mapping_sku` (`tenant_id`,`sku_id`),
  KEY `idx_product_code_mapping_spu` (`tenant_id`,`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品编码映射';

SET FOREIGN_KEY_CHECKS = 1;
