USE aryn_boot;

UPDATE `goods_sku` SET `version` = 0 WHERE `version` IS NULL;
ALTER TABLE `goods_sku` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '版本号';

CREATE TABLE IF NOT EXISTS `product_refund_stock_record` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `refund_no` varchar(64) NOT NULL COMMENT '稳定退款业务号',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.正常；1.删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_product_refund_stock_record` (`tenant_id`, `refund_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品退款库存恢复消费记录' ROW_FORMAT = DYNAMIC;
