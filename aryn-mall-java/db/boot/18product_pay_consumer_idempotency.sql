USE aryn_boot;

CREATE TABLE IF NOT EXISTS `product_order_pay_record` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `order_id` varchar(32) NOT NULL COMMENT '订单主键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.正常；1.删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_product_order_pay_record` (`tenant_id`, `order_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品支付消息消费记录' ROW_FORMAT = DYNAMIC;
