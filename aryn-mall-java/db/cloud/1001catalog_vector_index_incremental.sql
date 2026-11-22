USE `aryn_product`;
-- 持久向量索引；查询模型与generation固定成套，不在用户查询内重建目录。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `catalog_vector_generation` (
  `id` varchar(64) NOT NULL,
  `config_version` varchar(64) NOT NULL,
  `model_fingerprint` varchar(64) NOT NULL,
  `dimension` int NOT NULL,
  `catalog_fingerprint` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `cursor_id` varchar(64) DEFAULT NULL,
  `indexed_rows` int NOT NULL DEFAULT 0,
  `expected_rows` int NOT NULL DEFAULT 0,
  `lease_token` varchar(64) DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `catalog_vector_item` (
  `id` varchar(64) NOT NULL,
  `generation_id` varchar(64) NOT NULL,
  `sku_id` varchar(64) NOT NULL,
  `source_name` varchar(500) NOT NULL,
  `vector_json` longtext NOT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_generation_sku` (`tenant_id`,`generation_id`,`sku_id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `catalog_vector_state` (
  `id` varchar(64) NOT NULL,
  `generation_id` varchar(64) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
USE `aryn_nacos`;
UPDATE config_info SET content=REPLACE(content, '      - goods_sku\n', '      - goods_sku\n      - catalog_vector_generation\n'), md5=MD5(content) WHERE data_id='aryn-product-biz-dev.yml' AND INSTR(content,'      - catalog_vector_generation\n')=0;
UPDATE config_info SET content=REPLACE(content, '      - goods_sku\n', '      - goods_sku\n      - catalog_vector_item\n'), md5=MD5(content) WHERE data_id='aryn-product-biz-dev.yml' AND INSTR(content,'      - catalog_vector_item\n')=0;
UPDATE config_info SET content=REPLACE(content, '      - goods_sku\n', '      - goods_sku\n      - catalog_vector_state\n'), md5=MD5(content) WHERE data_id='aryn-product-biz-dev.yml' AND INSTR(content,'      - catalog_vector_state\n')=0;
