USE aryn_order;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for order_config
-- ----------------------------
DROP TABLE IF EXISTS `order_config`;
CREATE TABLE `order_config`  (
                                 `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                 `notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知地址',
                                 `order_cancel_timeout` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单超时取消时间级别',
                                 `order_auto_confirm_days` int NOT NULL COMMENT '订单确认收货时间（天）',
                                 `order_auto_comment_days` int NOT NULL COMMENT '订单评价时间（天）',
                                 `kuaidi100_app_key` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '快递100AppKey',
                                 `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注说明',
                                 `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
                                 `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                 `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                 `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
                                 `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                 `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                 `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                 `wx_delivery_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '微信发货状态',
                                 PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of order_config
-- ----------------------------
INSERT INTO `order_config` VALUES ('1927385204186599425', 'https://xxxx.Aetheryn.cn/api', '15', 7, 7, '12312312312', NULL, '1', '2025-05-27 23:23:36', '2025-06-02 00:02:01', '0', 'system', 'system', '1590229800633634816', '0');
INSERT INTO `order_config` VALUES ('1927386954763333634', 'https://xxxxx.Aetheryn.cn', '15', 2, 2, '12321123', NULL, '0', '2025-05-27 23:30:34', '2025-06-02 00:06:49', '0', 'system', 'system', '1590229800633634816', '0');

-- ----------------------------
-- Table structure for order_delivery
-- ----------------------------
DROP TABLE IF EXISTS `order_delivery`;
CREATE TABLE `order_delivery`  (
                                   `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                   `order_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单ID',
                                   `delivery_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发货单号（系统生成）',
                                   `logistics_company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物流公司编码',
                                   `logistics_company_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物流公司名称',
                                   `logistics_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物流单号',
                                   `delivery_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '发货状态',
                                   `deliver_time` datetime NULL DEFAULT NULL COMMENT '发货时间',
                                   `receiver_time` datetime NULL DEFAULT NULL COMMENT '收货时间',
                                   `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注说明',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                   `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
                                   `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                   `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                   `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                   `is_check` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否签收标记：0.未签收；1.已签收；',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '发货单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of order_delivery
-- ----------------------------

-- ----------------------------
-- Table structure for order_delivery_logistics
-- ----------------------------
DROP TABLE IF EXISTS `order_delivery_logistics`;
CREATE TABLE `order_delivery_logistics`  (
                                             `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                             `delivery_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联发货单ID',
                                             `logistics_company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物流公司编码（冗余）',
                                             `logistics_company_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物流公司名称（冗余）',
                                             `logistics_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物流单号（冗余）',
                                             `logistics_status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物流状态',
                                             `logistics_context` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '轨迹描述',
                                             `logistics_time` datetime NOT NULL COMMENT '轨迹发生时间',
                                             `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                             `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                             `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '逻辑删除：0正常；1删除',
                                             `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                             `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                             `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '发货单物流轨迹表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of order_delivery_logistics
-- ----------------------------

-- ----------------------------
-- Table structure for order_info
-- ----------------------------
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info`  (
                               `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                               `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户主键',
                               `delivery_way` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '配送方式：1.普通快递；2.上门自提',
								`order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单单号',
								`request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端请求幂等号',
                               `payment_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付类型：1.微信支付；2.支付宝支付',
                               `trade_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '交易类型：（预留）',
                               `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                               `pay_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '支付状态：0.未支付；1.已支付;',
                               `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单状态',
                               `appraise_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '评价状态：0.待评价；1.已平价;',
                               `total_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '订单总金额（元）',
                               `freight_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '运费（元）',
                               `coupon_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '优惠券优惠金额（元）',
                               `member_discount_price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '会员折扣优惠金额（元）',
                               `points_multiplier` decimal(10, 2) NOT NULL DEFAULT 1.00 COMMENT '下单时会员积分倍率',
                               `payment_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '支付金额（总金额-优惠券优惠金额+运费）',
                               `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                               `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                               `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                               `payment_time` datetime NULL DEFAULT NULL COMMENT '付款时间',
                               `deliver_time` datetime NULL DEFAULT NULL COMMENT '发货时间',
                               `cancel_time` datetime NULL DEFAULT NULL COMMENT '取消时间',
                               `receiver_time` datetime NULL DEFAULT NULL COMMENT '收货时间',
                               `transaction_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信支付单号',
                               `coupon_user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户优惠券id',
                               `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                               `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                               `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                               `recipient_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '收货人姓名',
                               `recipient_phone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '收货人电话',
                               `recipient_province` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '省',
                               `recipient_city` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '市',
                               `recipient_area` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '区/县',
                               `recipient_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '详细地址',
                               `recipient_province_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '省编码',
                               `recipient_city_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '市编码',
                               `recipient_area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '区/县编码',
                               `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用ID',
                               `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'openId',
								PRIMARY KEY (`id`) USING BTREE,
								UNIQUE KEY `uk_order_request` (`tenant_id`, `user_id`, `request_id`),
								UNIQUE KEY `uk_order_no` (`tenant_id`, `order_no`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of order_info
-- ----------------------------

-- ----------------------------
-- Table structure for order_item
-- ----------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item`  (
                               `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                               `order_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单主键',
                               `spu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'spuId',
                               `sku_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'skuId',
                               `spu_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'spu名称',
                               `pic_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品图',
                               `sales_price` decimal(10, 2) NOT NULL COMMENT '销售价格（元）',
                               `buy_quantity` int NULL DEFAULT NULL COMMENT '购买数量',
                               `total_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '订单金额（元）',
                               `freight_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '运费（元）',
                               `coupon_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '优惠券优惠金额（元）',
                               `member_discount_price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '会员折扣优惠金额（元）',
                               `payment_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '支付金额（总金额-优惠券优惠金额+运费= 支付金额）',
                               `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                               `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                               `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                               `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '状态',
                               `specs_info` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格信息',
                               `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                               `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                               `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '子订单信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of order_item
-- ----------------------------

-- ----------------------------
-- Table structure for order_refund
-- ----------------------------
DROP TABLE IF EXISTS `order_refund`;
CREATE TABLE `order_refund`  (
                                 `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                 `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户主键',
                                 `order_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单ID',
                                 `order_item_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '子订单ID',
                                 `refund_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '退款类型',
                                 `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退款状态',
                                 `arrival_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退款到账状态：0.未退款；1.退款中；2.已到账；',
                                 `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                 `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                 `refund_amount` decimal(10, 2) NOT NULL COMMENT '退款金额',
                                 `refund_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退款流水号',
                                 `refund_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退款原因',
                                 `refuse_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '拒绝退款原因',
                                 `user_received_account` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退款入账账户',
                                 `refund_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退款单号',
                                 `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                 `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                 `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                 PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商城退款单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of order_refund
-- ----------------------------

-- ----------------------------
-- Table structure for shopping_cart
-- ----------------------------
DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart`  (
                                  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
                                  `spu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品ID',
                                  `sku_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'skuId',
                                  `quantity` int NULL DEFAULT NULL COMMENT '加入数量',
                                  `spu_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品名称',
                                  `sales_price` decimal(10, 2) NOT NULL COMMENT '销售价格（元）',
                                  `pic_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品图',
                                  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
								  `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0、显示；1、隐藏',
								  `active_sku_id` varchar(32) GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN `sku_id` ELSE NULL END) STORED COMMENT '有效购物车SKU唯一键',
                                  `specs_info` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格信息',
                                  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                  `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
								  PRIMARY KEY (`id`) USING BTREE,
								  UNIQUE KEY `uk_shopping_cart_active_sku` (`tenant_id`, `user_id`, `active_sku_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '购物车' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of shopping_cart
-- ----------------------------

-- ----------------------------
-- Table structure for replenish_direct_import
-- ----------------------------
DROP TABLE IF EXISTS `replenish_direct_import`;
CREATE TABLE `replenish_direct_import`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `file_name` varchar(255) NULL DEFAULT NULL COMMENT '上传文件名',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件字节数',
  `file_sha256` char(64) NULL DEFAULT NULL COMMENT '文件内容摘要',
  `total_rows` int NOT NULL DEFAULT 0 COMMENT '解析出的数据行数（不含表头）',
  `matched_rows` int NOT NULL DEFAULT 0 COMMENT '匹配成功行数',
  `unmatched_rows` int NOT NULL DEFAULT 0 COMMENT '未匹配行数',
  `spec_changed_rows` int NOT NULL DEFAULT 0 COMMENT '规格变更行数',
  `over_stock_rows` int NOT NULL DEFAULT 0 COMMENT '超库存行数',
  `invalid_rows` int NOT NULL DEFAULT 0 COMMENT '数量/格式异常行数',
  `off_shelf_rows` int NOT NULL DEFAULT 0 COMMENT '已下架商品行数',
  `not_filled_rows` int NOT NULL DEFAULT 0 COMMENT '未填数量行数（客户本次不采购，非错误）',
  `confirmed_rows` int NOT NULL DEFAULT 0 COMMENT '确认后复核通过的明细项数（同 SKU 合并后）',
  `skipped_rows` int NOT NULL DEFAULT 0 COMMENT '确认时跳过或未处置的行数',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1待确认 2已确认 3已取消',
  `operator_user_id` varchar(32) NOT NULL COMMENT '上传操作人（确认/取回校验归属）',
  `confirmed_time` datetime NULL DEFAULT NULL COMMENT '确认时间',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_replenish_direct_import_operator` (`tenant_id`, `operator_user_id`, `status`),
  KEY `idx_replenish_direct_import_status` (`tenant_id`, `status`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '清单直下单导入任务' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for replenish_direct_import_row
-- ----------------------------
DROP TABLE IF EXISTS `replenish_direct_import_row`;
CREATE TABLE `replenish_direct_import_row`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `import_id` varchar(32) NOT NULL COMMENT '导入任务ID',
  `row_no` int NOT NULL DEFAULT 0 COMMENT '行号（从1开始，不含表头）',
  `raw_code` varchar(64) NULL DEFAULT NULL COMMENT 'Excel 商品编码原文',
  `raw_name` varchar(255) NULL DEFAULT NULL COMMENT 'Excel 品名原文',
  `raw_spec` varchar(128) NULL DEFAULT NULL COMMENT 'Excel 规格原文',
  `raw_quantity` int NULL DEFAULT NULL COMMENT 'Excel 数量原文',
  `raw_remark` varchar(255) NULL DEFAULT NULL COMMENT 'Excel 备注原文',
  `match_type` varchar(16) NULL DEFAULT NULL COMMENT '匹配方式：CODE编码 / NAME品名 / AMBIGUOUS多规格待选 / NONE未命中',
  `matched_sku_id` varchar(32) NULL DEFAULT NULL COMMENT '匹配到的SKU ID',
  `matched_spu_id` varchar(32) NULL DEFAULT NULL COMMENT '匹配到的SPU ID',
  `matched_name` varchar(255) NULL DEFAULT NULL COMMENT '匹配到的商品名',
  `matched_spec` varchar(255) NULL DEFAULT NULL COMMENT '匹配到的规格描述',
  `matched_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '匹配时的售价快照（仅展示）',
  `matched_stock` int NULL DEFAULT NULL COMMENT '匹配时的库存快照（仅展示）',
  `planned_quantity` int NULL DEFAULT NULL COMMENT '本次采购数量（确认复核后与 resolved_quantity 同值）',
  `suggested_quantity` int NULL DEFAULT NULL COMMENT '服务端建议数量（超库存调减/数量异常就近取整）',
  `result_type` varchar(24) NOT NULL DEFAULT 'UNMATCHED' COMMENT '结果：OK/UNMATCHED/SPEC_CHANGED/OVER_STOCK/INVALID_QTY/OFF_SHELF/NOT_FILLED',
  `result_message` varchar(500) NULL DEFAULT NULL COMMENT '结果说明（给用户看）',
  `resolved_action` varchar(24) NULL DEFAULT NULL COMMENT '用户处置：ACCEPT_SPEC/ADJUST_QTY/REPLACE_SKU/SKIP',
  `resolved_sku_id` varchar(32) NULL DEFAULT NULL COMMENT '人工补选后的SKU ID',
  `resolved_quantity` int NULL DEFAULT NULL COMMENT '调整后的数量',
  `resolved_time` datetime NULL DEFAULT NULL COMMENT '处置时间',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_replenish_direct_import_row` (`tenant_id`, `import_id`, `row_no`),
  KEY `idx_replenish_direct_import_row_result` (`tenant_id`, `import_id`, `result_type`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '清单直下单导入解析行' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for undo_log
-- ----------------------------
DROP TABLE IF EXISTS `undo_log`;
CREATE TABLE `undo_log`  (
                             `id` bigint NOT NULL AUTO_INCREMENT,
                             `branch_id` bigint NOT NULL,
                             `xid` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
                             `context` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
                             `rollback_info` longblob NOT NULL,
                             `log_status` int NOT NULL,
                             `log_created` datetime NOT NULL,
                             `log_modified` datetime NOT NULL,
                             `ext` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
                             PRIMARY KEY (`id`) USING BTREE,
                             UNIQUE INDEX `ux_undo_log`(`xid` ASC, `branch_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of undo_log
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;

-- 长期采购草稿，与共享车收集窗口解耦。旧导入报告仍保留只读；无法重建的存量贡献不得推算撤销。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `procurement_draft` (
  `id` varchar(64) NOT NULL,
  `owner_user_id` varchar(64) NOT NULL,
  `purchase_scene` varchar(64) NOT NULL,
  `buyer_org_id` varchar(64) DEFAULT NULL,
  `vessel_id` varchar(64) DEFAULT NULL,
  `vessel_call_id` varchar(64) DEFAULT NULL,
  `title` varchar(120) DEFAULT NULL,
  `client_request_id` varchar(64) NOT NULL,
  `context_fingerprint` varchar(64) NOT NULL,
  `status` varchar(64) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`,`id`),
  UNIQUE KEY `uk_draft_request` (`tenant_id`,`owner_user_id`,`client_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `procurement_source_batch` (
  `id` varchar(64) NOT NULL,
  `draft_id` varchar(64) NOT NULL,
  `operator_user_id` varchar(64) NOT NULL,
  `input_type` varchar(64) NOT NULL,
  `client_request_id` varchar(64) NOT NULL,
  `input_digest` varchar(64) NOT NULL,
  `explicit_new` tinyint(1) NOT NULL,
  `input_name` varchar(255) DEFAULT NULL,
  `input_text` longtext DEFAULT NULL,
  `input_bytes` longblob DEFAULT NULL,
  `template_version` varchar(64) DEFAULT NULL,
  `config_version` varchar(64) DEFAULT NULL,
  `parser_source` varchar(64) DEFAULT NULL,
  `parse_revision` int NOT NULL DEFAULT 0,
  `status` varchar(64) NOT NULL,
  `total_rows` int NOT NULL DEFAULT 0,
  `completed_rows` int NOT NULL DEFAULT 0,
  `source_cursor` int NOT NULL DEFAULT 0,
  `attempt` int NOT NULL DEFAULT 0,
  `lease_token` varchar(64) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `heartbeat` datetime(6) DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `confirmed_request_id` varchar(64) DEFAULT NULL,
  `confirmation_json` longtext DEFAULT NULL,
  `replaced_by_batch_id` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`,`id`),
  UNIQUE KEY `uk_batch_request` (`tenant_id`,`draft_id`,`operator_user_id`,`client_request_id`),
  KEY `idx_batch_digest` (`tenant_id`,`draft_id`,`operator_user_id`,`input_digest`),
  KEY `idx_batch_jobs` (`tenant_id`,`status`,`next_retry_at`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `procurement_draft_row` (
  `id` varchar(64) NOT NULL,
  `draft_id` varchar(64) NOT NULL,
  `batch_id` varchar(64) NOT NULL,
  `source_position` varchar(64) NOT NULL,
  `source_sheet` varchar(200) DEFAULT NULL,
  `source_row` int DEFAULT NULL,
  `source_offset` int DEFAULT NULL,
  `raw_text` longtext DEFAULT NULL,
  `raw_name` varchar(500) DEFAULT NULL,
  `raw_quantity` decimal(38,18) DEFAULT NULL,
  `raw_unit` varchar(24) DEFAULT NULL,
  `raw_spec` varchar(500) DEFAULT NULL,
  `raw_code` varchar(200) DEFAULT NULL,
  `raw_owner` varchar(120) DEFAULT NULL,
  `parse_status` varchar(64) DEFAULT NULL,
  `match_status` varchar(64) DEFAULT NULL,
  `unit_status` varchar(64) DEFAULT NULL,
  `quantity_status` varchar(64) DEFAULT NULL,
  `review_status` varchar(64) DEFAULT NULL,
  `candidates_json` longtext DEFAULT NULL,
  `matched_sku_id` varchar(64) DEFAULT NULL,
  `selected_sku_id` varchar(64) DEFAULT NULL,
  `normalized_base_quantity` decimal(60,30) DEFAULT NULL,
  `suggested_sales_quantity` int DEFAULT NULL,
  `confirmed_sales_quantity` int DEFAULT NULL,
  `excess_base_quantity` decimal(60,30) DEFAULT NULL,
  `measurement_revision` bigint DEFAULT NULL,
  `decision_json` longtext DEFAULT NULL,
  `review_actor` varchar(64) DEFAULT NULL,
  `review_reason` varchar(500) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `parse_revision` int NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`,`id`),
  UNIQUE KEY `uk_source_position` (`tenant_id`,`batch_id`,`parse_revision`,`source_position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `procurement_contribution` (
  `id` varchar(64) NOT NULL,
  `draft_id` varchar(64) NOT NULL,
  `batch_id` varchar(64) NOT NULL,
  `source_row_id` varchar(64) NOT NULL,
  `target_type` varchar(64) NOT NULL,
  `target_id` varchar(64) NOT NULL,
  `target_line_id` varchar(64) NOT NULL,
  `owner_user_id` varchar(64) NOT NULL,
  `sku_id` varchar(64) NOT NULL,
  `quantity` int NOT NULL,
  `status` varchar(64) NOT NULL,
  `origin` varchar(64) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`create_time`,`id`),
  UNIQUE KEY `uk_row_target_contribution` (`tenant_id`,`batch_id`,`source_row_id`,`target_type`,`target_line_id`),
  KEY `idx_target_contributions` (`tenant_id`,`target_type`,`target_line_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET @procurement_expand=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft_row' AND COLUMN_NAME='raw_quantity' AND NUMERIC_PRECISION<38);
SET @procurement_ddl=IF(@procurement_expand=1, 'ALTER TABLE procurement_draft_row MODIFY COLUMN raw_quantity decimal(38,18) DEFAULT NULL', 'SELECT 1');
PREPARE procurement_stmt FROM @procurement_ddl; EXECUTE procurement_stmt; DEALLOCATE PREPARE procurement_stmt;
SET @procurement_expand=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft_row' AND COLUMN_NAME='normalized_base_quantity' AND NUMERIC_PRECISION<60);
SET @procurement_ddl=IF(@procurement_expand=1, 'ALTER TABLE procurement_draft_row MODIFY COLUMN normalized_base_quantity decimal(60,30) DEFAULT NULL', 'SELECT 1');
PREPARE procurement_stmt FROM @procurement_ddl; EXECUTE procurement_stmt; DEALLOCATE PREPARE procurement_stmt;
SET @procurement_expand=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft_row' AND COLUMN_NAME='excess_base_quantity' AND NUMERIC_PRECISION<60);
SET @procurement_ddl=IF(@procurement_expand=1, 'ALTER TABLE procurement_draft_row MODIFY COLUMN excess_base_quantity decimal(60,30) DEFAULT NULL', 'SELECT 1');
PREPARE procurement_stmt FROM @procurement_ddl; EXECUTE procurement_stmt; DEALLOCATE PREPARE procurement_stmt;

SET @procurement_tenant_nullable=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft' AND COLUMN_NAME='tenant_id' AND IS_NULLABLE='YES');
SET @procurement_tenant_ddl=IF(@procurement_tenant_nullable=1, 'ALTER TABLE procurement_draft MODIFY COLUMN tenant_id varchar(32) NOT NULL', 'SELECT 1');
PREPARE procurement_tenant_stmt FROM @procurement_tenant_ddl; EXECUTE procurement_tenant_stmt; DEALLOCATE PREPARE procurement_tenant_stmt;
SET @procurement_tenant_nullable=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_source_batch' AND COLUMN_NAME='tenant_id' AND IS_NULLABLE='YES');
SET @procurement_tenant_ddl=IF(@procurement_tenant_nullable=1, 'ALTER TABLE procurement_source_batch MODIFY COLUMN tenant_id varchar(32) NOT NULL', 'SELECT 1');
PREPARE procurement_tenant_stmt FROM @procurement_tenant_ddl; EXECUTE procurement_tenant_stmt; DEALLOCATE PREPARE procurement_tenant_stmt;
SET @procurement_tenant_nullable=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_draft_row' AND COLUMN_NAME='tenant_id' AND IS_NULLABLE='YES');
SET @procurement_tenant_ddl=IF(@procurement_tenant_nullable=1, 'ALTER TABLE procurement_draft_row MODIFY COLUMN tenant_id varchar(32) NOT NULL', 'SELECT 1');
PREPARE procurement_tenant_stmt FROM @procurement_tenant_ddl; EXECUTE procurement_tenant_stmt; DEALLOCATE PREPARE procurement_tenant_stmt;
SET @procurement_tenant_nullable=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='procurement_contribution' AND COLUMN_NAME='tenant_id' AND IS_NULLABLE='YES');
SET @procurement_tenant_ddl=IF(@procurement_tenant_nullable=1, 'ALTER TABLE procurement_contribution MODIFY COLUMN tenant_id varchar(32) NOT NULL', 'SELECT 1');
PREPARE procurement_tenant_stmt FROM @procurement_tenant_ddl; EXECUTE procurement_tenant_stmt; DEALLOCATE PREPARE procurement_tenant_stmt;

-- 稳定合送组、订单关系及本地操作outbox。不会改动订单、支付、签收或存量数据。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `delivery_group` (
  `id` varchar(64) NOT NULL,
  `warehouse_id` varchar(64) NOT NULL,
  `vessel_id` varchar(64) NOT NULL,
  `vessel_call_id` varchar(64) NOT NULL,
  `port_code` varchar(64) NOT NULL,
  `window_sequence` int NOT NULL,
  `window_start` datetime(6) DEFAULT NULL,
  `window_end` datetime(6) DEFAULT NULL,
  `cutoff` datetime(6) DEFAULT NULL,
  `max_orders` int NOT NULL,
  `status` varchar(32) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_window` (`tenant_id`,`warehouse_id`,`vessel_call_id`,`window_sequence`),
  KEY `idx_group_cutoff` (`tenant_id`,`status`,`cutoff`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `delivery_group_order_link` (
  `id` varchar(64) NOT NULL,
  `group_id` varchar(64) NOT NULL,
  `order_id` varchar(64) NOT NULL,
  `warehouse_id` varchar(64) NOT NULL,
  `wave_id` varchar(64) DEFAULT NULL,
  `task_id` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_group` (`tenant_id`,`order_id`),
  KEY `idx_group_active` (`tenant_id`,`group_id`,`status`),
  KEY `idx_group_wave` (`tenant_id`,`wave_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `delivery_group_operation` (
  `id` varchar(64) NOT NULL,
  `group_id` varchar(64) NOT NULL,
  `request_id` varchar(120) NOT NULL,
  `fingerprint` varchar(64) NOT NULL,
  `action` varchar(32) NOT NULL,
  `actor_id` varchar(64) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `request_json` longtext NOT NULL,
  `result_json` longtext NOT NULL,
  `notification_json` longtext NOT NULL,
  `outbox_status` varchar(32) NOT NULL,
  `attempts` int NOT NULL DEFAULT 0,
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `lease_token` varchar(64) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `tenant_id` varchar(32) NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_request` (`tenant_id`,`request_id`),
  KEY `idx_group_outbox` (`tenant_id`,`outbox_status`,`next_attempt_at`,`lease_until`),
  KEY `idx_group_history` (`tenant_id`,`group_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 原订单没有仓库字段；归组/入波次时显式核定，旧记录保持NULL待核定。
SET @delivery_group_column=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='order_info' AND COLUMN_NAME='fulfillment_warehouse_id');
SET @delivery_group_ddl=IF(@delivery_group_column=0, 'ALTER TABLE order_info ADD COLUMN fulfillment_warehouse_id varchar(64) DEFAULT NULL COMMENT ''显式核定履约仓库''', 'SELECT 1');
PREPARE delivery_group_stmt FROM @delivery_group_ddl; EXECUTE delivery_group_stmt; DEALLOCATE PREPARE delivery_group_stmt;
SET @delivery_group_column=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='delivery_task' AND COLUMN_NAME='warehouse_id');
SET @delivery_group_ddl=IF(@delivery_group_column=0, 'ALTER TABLE delivery_task ADD COLUMN warehouse_id varchar(64) DEFAULT NULL COMMENT ''显式核定履约仓库''', 'SELECT 1');
PREPARE delivery_group_stmt FROM @delivery_group_ddl; EXECUTE delivery_group_stmt; DEALLOCATE PREPARE delivery_group_stmt;
