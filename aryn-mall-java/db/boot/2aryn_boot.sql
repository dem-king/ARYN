USE aryn_boot;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for coupon_goods
-- ----------------------------
DROP TABLE IF EXISTS `coupon_goods`;
CREATE TABLE `coupon_goods`  (
                                 `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                 `coupon_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '优惠券id',
                                 `spu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品id',
                                 `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                 `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                 `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                 `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                 `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                 `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                 PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券关联商品表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of coupon_goods
-- ----------------------------

-- ----------------------------
-- Table structure for coupon_info
-- ----------------------------
DROP TABLE IF EXISTS `coupon_info`;
CREATE TABLE `coupon_info`  (
                                `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                `coupon_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '优惠券名称',
                                `coupon_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '优惠券类型：1.满减券；2.折扣券；',
                                `amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '优惠金额（元）',
                                `discount` decimal(2, 1) NULL DEFAULT NULL COMMENT '折扣',
                                `threshold` decimal(10, 2) NOT NULL COMMENT '使用门槛  0元表示无门槛',
                                `total_num` int NOT NULL COMMENT '发行数量',
                                `remain_num` int NOT NULL COMMENT '剩余数量',
                                `assign_count` int NOT NULL DEFAULT 0 COMMENT '已发放券数量',
                                `used_count` int NOT NULL DEFAULT 0 COMMENT '已使用数量',
                                `receive_count` int NULL DEFAULT 0 COMMENT '领取限制 0表示无限制',
                                `receive_started_at` datetime NOT NULL COMMENT '有效开始时间',
                                `receive_ended_at` datetime NOT NULL COMMENT '有效结束时间',
                                `use_range` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '可用范围：1.全部商品；2.指定商品；',
                                `spu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品id',
                                `use_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '使用说明',
                                `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态：0.正常；1.关闭；',
                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                `version` int NULL DEFAULT 0 COMMENT '版本号',
                                `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of coupon_info
-- ----------------------------

-- ----------------------------
-- Table structure for coupon_user
-- ----------------------------
DROP TABLE IF EXISTS `coupon_user`;
CREATE TABLE `coupon_user`  (
                                `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                `coupon_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '优惠券id',
                                `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户id',
                                `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态：0.未使用；1.已使用；2.已过期；3.冻结；',
                                `order_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单id',
                                `received_time` datetime NULL DEFAULT NULL COMMENT '领取时间',
                                `validat_time` datetime NULL DEFAULT NULL COMMENT '有效日期',
                                `used_time` datetime NULL DEFAULT NULL COMMENT '使用时间',
                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户领券记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of coupon_user
-- ----------------------------

-- ----------------------------
-- Table structure for gen_data_source
-- ----------------------------
DROP TABLE IF EXISTS `gen_data_source`;
CREATE TABLE `gen_data_source`  (
                                    `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                    `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                    `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                    `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                    `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
                                    `url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'jdbc url',
                                    `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名',
                                    `password` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码',
                                    `db_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据库名称',
                                    `port` int NULL DEFAULT NULL COMMENT '端口',
                                    `host` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主机',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据源表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of gen_data_source
-- ----------------------------

-- ----------------------------
-- Table structure for gen_table
-- ----------------------------
DROP TABLE IF EXISTS `gen_table`;
CREATE TABLE `gen_table`  (
                              `table_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                              `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表名称',
                              `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表描述',
                              `sub_table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联父表的表名',
                              `sub_table_fk_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '本表关联父表的外键名',
                              `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实体类名称(首字母大写)',
                              `tpl_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '使用的模板（crud单表操作 tree树表操作 sub主子表操作）',
                              `tpl_web_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '前端类型（element-ui模版 element-plus模版）',
                              `package_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成包路径',
                              `module_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成模块名',
                              `business_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成业务名',
                              `function_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成功能名',
                              `function_author` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成作者',
                              `gen_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
                              `gen_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成路径（不填默认项目路径）',
                              `options` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '其它生成选项',
                              `tree_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '树编码字段',
                              `tree_parent_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '树父编码字段',
                              `tree_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '树名称字段',
                              `parent_menu_id` bigint NULL DEFAULT NULL COMMENT '上级菜单ID字段',
                              `parent_menu_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上级菜单名称字段',
                              `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                              `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                              `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                              `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                              `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                              `ds_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据源名称',
                              PRIMARY KEY (`table_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of gen_table
-- ----------------------------

-- ----------------------------
-- Table structure for gen_table_column
-- ----------------------------
DROP TABLE IF EXISTS `gen_table_column`;
CREATE TABLE `gen_table_column`  (
                                     `column_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                     `table_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '归属表ID',
                                     `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列名称',
                                     `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列描述',
                                     `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列类型',
                                     `java_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA类型',
                                     `java_field` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'JAVA字段名',
                                     `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否主键（1是）',
                                     `is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否自增（1是）',
                                     `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否必填（1是）',
                                     `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否为插入字段（1是）',
                                     `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否编辑字段（1是）',
                                     `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否列表字段（1是）',
                                     `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否查询字段（1是）',
                                     `query_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '查询方式（EQ等于、NE不等于、GT大于、LT小于、LIKE模糊、BETWEEN范围）',
                                     `html_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示类型（input、textarea、select、checkbox、radio、datetime、image、upload、editor）',
                                     `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '字典类型',
                                     `sort` int NULL DEFAULT NULL COMMENT '排序',
                                     `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                     `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                     `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                     `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                                     `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                     PRIMARY KEY (`column_id`) USING BTREE,
                                     INDEX `idx_table_id`(`table_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务字段表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of gen_table_column
-- ----------------------------

-- ----------------------------
-- Table structure for goods_appraise
-- ----------------------------
DROP TABLE IF EXISTS `goods_appraise`;
CREATE TABLE `goods_appraise`  (
                                   `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                   `spu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'spu主键',
                                   `order_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '订单主键',
                                   `order_item_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '子订单主键',
                                   `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
                                   `avatar_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
                                   `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                   `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                   `pic_urls` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图片',
                                   `goods_score` int NULL DEFAULT NULL COMMENT '商品评分',
                                   `logistics_score` int NULL DEFAULT NULL COMMENT '物流评分',
                                   `service_score` int NULL DEFAULT NULL COMMENT '服务评分',
                                   `business_reply` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商家回复',
                                   `reply_time` datetime NULL DEFAULT NULL COMMENT '回复时间',
                                   `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '评论内容',
                                   `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                   `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                   `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品评价' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of goods_appraise
-- ----------------------------

-- ----------------------------
-- Table structure for goods_category
-- ----------------------------
DROP TABLE IF EXISTS `goods_category`;
CREATE TABLE `goods_category`  (
                                   `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                   `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类目名称',
                                   `parent_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上级类目（0.顶级类目）',
                                   `category_pic` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类目图片',
                                   `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类目描述',
                                   `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态：0.正常；1.停用；',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                   `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                   `sort` int NULL DEFAULT NULL COMMENT '排序序号',
                                   `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                   `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                   `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品类目' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of goods_category
-- ----------------------------
INSERT INTO `goods_category` VALUES ('1912861788486148097', '家用电器', '0', 'https://minio.aryn.co/aryn/file/f65cf2cb-3594-40e3-b720-ea72c9f685ef.png', '家用电器', '0', '2025-04-17 21:32:44', '2025-05-16 10:33:27', '0', 1, '1590229800633634816', 'admin', 'system');
INSERT INTO `goods_category` VALUES ('1912862220591734785', '电视', '1912861788486148097', 'https://minio.aryn.co/aryn/file/9baf470c-b17e-417e-88f9-38a3b5f2cf8e.png', '电视', '0', '2025-04-17 21:34:27', NULL, '0', 1, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912862615531593730', '空调', '1912861788486148097', 'https://minio.aryn.co/aryn/file/16e58200-e455-44aa-9c91-66b9d991b5ef.jpg', '空调', '0', '2025-04-17 21:36:01', NULL, '0', 2, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912863000879079426', '冰箱', '1912861788486148097', 'https://minio.aryn.co/aryn/file/1c3abd12-ec0c-4d18-897b-c789203b4f25.jpg', '冰箱', '0', '2025-04-17 21:37:33', NULL, '0', 3, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912863400222957569', '手机数码', '0', 'https://minio.aryn.co/aryn/file/9baf470c-b17e-417e-88f9-38a3b5f2cf8e.png', '手机数码', '0', '2025-04-17 21:39:09', NULL, '0', 2, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912863683464306689', '手机', '1912863400222957569', 'https://minio.aryn.co/aryn/file/cb7c6390-1602-4f66-a4fc-94bbb9adbf35.jpg', '手机', '0', '2025-04-17 21:40:16', NULL, '0', 1, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912863991967948801', '智能设备', '1912863400222957569', 'https://minio.aryn.co/aryn/file/8c25a91d-91b4-4728-8f20-4880fad18677.jpg', '智能设备', '0', '2025-04-17 21:41:30', NULL, '0', 2, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912864294511484929', '无人机', '1912863400222957569', 'https://minio.aryn.co/aryn/file/0f4b0f37-0844-4c43-a1d7-231eadc61ddb.jpg', '无人机', '0', '2025-04-17 21:42:42', NULL, '0', 3, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1925541056051564546', '电脑', '1912863400222957569', 'https://minio.aryn.co/aryn/1590229800633634816/file/e1a4f623-6773-4eea-9f78-c22ca4d4c12b.jpg', NULL, '0', '2025-05-22 21:15:37', NULL, '0', 4, '1590229800633634816', 'system', NULL);

-- ----------------------------
-- Table structure for goods_collect
-- ----------------------------
DROP TABLE IF EXISTS `goods_collect`;
CREATE TABLE `goods_collect`  (
                                  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户id',
                                  `spu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品id',
                                  `sales_price` decimal(10, 2) NOT NULL COMMENT '加入时销售价格（元）',
                                  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                  `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0、显示；1、隐藏',
                                  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                  `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                  PRIMARY KEY (`id`) USING BTREE,
                                  UNIQUE INDEX `uc_user_spu_id`(`user_id` ASC, `spu_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品收藏' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of goods_collect
-- ----------------------------

-- ----------------------------
-- Table structure for goods_footprint
-- ----------------------------
DROP TABLE IF EXISTS `goods_footprint`;
CREATE TABLE `goods_footprint`  (
                                    `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                    `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户主键',
                                    `spu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品主键',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                    `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                    `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                    `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                    `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品足迹' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of goods_footprint
-- ----------------------------

-- ----------------------------
-- Table structure for goods_sku
-- ----------------------------
DROP TABLE IF EXISTS `goods_sku`;
CREATE TABLE `goods_sku`  (
                              `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                              `spu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'spuId',
                              `sales_price` decimal(10, 2) NOT NULL COMMENT '销售价格（元）',
                              `original_price` decimal(10, 2) NOT NULL COMMENT '原价（元）',
                              `cost_price` decimal(10, 2) NOT NULL COMMENT '成本价（元）',
                              `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
                              `weight` decimal(10, 2) NULL DEFAULT NULL COMMENT '重量',
                              `volume` decimal(10, 2) NULL DEFAULT NULL COMMENT '体积',
                              `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                              `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                              `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                              `version` int NULL DEFAULT 0 COMMENT '版本号',
                              `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                              `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                              `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                              `specs_json` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SKU规格信息',
                              `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态',
                              `specs_arr` json NULL COMMENT '规格数组',
                              `pic_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图片地址',
                              PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品SKU' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of goods_sku
-- ----------------------------
INSERT INTO `goods_sku` VALUES ('1912882762380124162', '1912882761411239938', 5999.00, 5999.00, 5999.00, 973, 12.00, NULL, '2025-04-17 22:56:05', '2026-02-25 23:06:45', '0', 0, '1590229800633634816', 'admin', 'system', NULL, '0', NULL, NULL);
INSERT INTO `goods_sku` VALUES ('1913460546643709954', '1913460322839842818', 10.00, 10.00, 10.00, 1110, 0.00, NULL, '2025-04-19 13:11:59', '2025-04-19 13:42:46', '0', 0, '1590229800633634816', 'admin', NULL, NULL, '0', NULL, NULL);
INSERT INTO `goods_sku` VALUES ('2026667019995189250', '1925545886404988929', 1.00, 1.00, 1.00, 99, NULL, NULL, '2026-02-25 22:34:06', '2026-04-05 13:03:56', '0', 0, '1590229800633634816', 'system', 'ozexS3bXeBjIkrfIZoe07SWXBY4I', NULL, '0', '[{\"specsId\": \"1912866651538366466\", \"specsName\": \"外观\", \"specsValueId\": \"1912866747504041986\", \"specsValueName\": \"沙漠色钛金属\"}]', NULL);
INSERT INTO `goods_sku` VALUES ('2026667020779524097', '1925545886404988929', 1.00, 1.00, 1.00, 100, NULL, NULL, '2026-02-25 22:34:06', NULL, '0', 0, '1590229800633634816', 'system', NULL, NULL, '0', '[{\"specsId\": \"1912866651538366466\", \"specsName\": \"外观\", \"specsValueId\": \"1912866787874217985\", \"specsValueName\": \"原色钛金属\"}]', NULL);
INSERT INTO `goods_sku` VALUES ('2026667408727478273', '1925543036622884866', 1.00, 1.00, 1.00, 999, NULL, NULL, '2026-02-25 22:35:39', '2026-02-25 23:20:34', '0', 0, '1590229800633634816', 'system', '天启雨数科技', NULL, '0', '[{\"specsId\": \"1912866651538366466\", \"specsName\": \"外观\", \"specsValueId\": \"1912866747504041986\", \"specsValueName\": \"沙漠色钛金属\"}]', NULL);
INSERT INTO `goods_sku` VALUES ('2026667495822200834', '1912870113080680449', 9999.00, 9999.00, 9999.00, 1000, NULL, NULL, '2026-02-25 22:35:59', NULL, '0', 0, '1590229800633634816', 'system', NULL, NULL, '0', '[{\"specsId\": \"1912866651538366466\", \"specsName\": \"外观\", \"specsValueId\": \"1912866747504041986\", \"specsValueName\": \"沙漠色钛金属\"}]', NULL);
INSERT INTO `goods_sku` VALUES ('2026667593272659970', '1912867577569386497', 9299.00, 9299.00, 9299.00, 1000, NULL, NULL, '2026-02-25 22:36:23', NULL, '0', 0, '1590229800633634816', 'system', NULL, NULL, '0', '[{\"specsId\": \"1912866862587355137\", \"specsName\": \"版本\", \"specsValueId\": \"1912866886893346818\", \"specsValueName\": \"256GB\"}]', NULL);

-- ----------------------------
-- Table structure for goods_specs
-- ----------------------------
DROP TABLE IF EXISTS `goods_specs`;
CREATE TABLE `goods_specs`  (
                                `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                `name` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格名',
                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
                                `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品规格' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of goods_specs
-- ----------------------------
INSERT INTO `goods_specs` VALUES ('1912866651538366466', '外观', '2025-04-17 21:52:04', NULL, 'admin', NULL, '0', '1590229800633634816');
INSERT INTO `goods_specs` VALUES ('1912866862587355137', '版本', '2025-04-17 21:52:54', NULL, 'admin', NULL, '0', '1590229800633634816');
INSERT INTO `goods_specs` VALUES ('1919391795383545858', '选择屏幕尺寸', '2025-05-05 22:00:39', NULL, 'admin', NULL, '0', '1590229800633634816');
INSERT INTO `goods_specs` VALUES ('1925541900914413570', '颜色', '2025-05-22 21:18:59', NULL, 'system', NULL, '0', '1590229800633634816');
INSERT INTO `goods_specs` VALUES ('1935979409943912449', '测试', '2025-06-20 16:33:55', NULL, 'system', NULL, '0', '1881232176465358849');

-- ----------------------------
-- Table structure for goods_specs_value
-- ----------------------------
DROP TABLE IF EXISTS `goods_specs_value`;
CREATE TABLE `goods_specs_value`  (
                                      `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                      `specs_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规格ID',
                                      `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格值',
                                      `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                      `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                      `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                      `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                      `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                      `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                      PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品规格值' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of goods_specs_value
-- ----------------------------
INSERT INTO `goods_specs_value` VALUES ('1912866747504041986', '1912866651538366466', '沙漠色钛金属', '2025-04-17 21:52:27', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912866787874217985', '1912866651538366466', '原色钛金属', '2025-04-17 21:52:36', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912866817397923842', '1912866651538366466', '白色钛金属', '2025-04-17 21:52:43', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912866843499077633', '1912866651538366466', '黑色钛金属', '2025-04-17 21:52:49', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912866886893346818', '1912866862587355137', '256GB', '2025-04-17 21:53:00', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912866927229968386', '1912866862587355137', '512GB', '2025-04-17 21:53:09', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912866949661106178', '1912866862587355137', '1TB', '2025-04-17 21:53:15', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912869461206147074', '1912866651538366466', '黑色', '2025-04-17 22:03:14', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912869494215319554', '1912866651538366466', '白色', '2025-04-17 22:03:21', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912869535868952578', '1912866651538366466', '浅草色', '2025-04-17 22:03:31', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912869571721863169', '1912866651538366466', '丁香紫', '2025-04-17 22:03:40', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912869653045223426', '1912866862587355137', '12GB+256GB', '2025-04-17 22:03:59', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1912869723891212289', '1912866862587355137', '12GB+512GB', '2025-04-17 22:04:16', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1919391860428812289', '1919391795383545858', '85英寸', '2025-05-05 22:00:55', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1919391892674621441', '1919391795383545858', '75英寸', '2025-05-05 22:01:02', NULL, '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_specs_value` VALUES ('1925541940366036993', '1925541900914413570', '云水蓝', '2025-05-22 21:19:08', NULL, '0', '1590229800633634816', 'system', NULL);
INSERT INTO `goods_specs_value` VALUES ('1925542032317763585', '1925541900914413570', '天际白', '2025-05-22 21:19:30', NULL, '0', '1590229800633634816', 'system', NULL);
INSERT INTO `goods_specs_value` VALUES ('1925542130414145538', '1912866862587355137', '32GB+1TB', '2025-05-22 21:19:53', NULL, '0', '1590229800633634816', 'system', NULL);
INSERT INTO `goods_specs_value` VALUES ('1925542181177806849', '1912866862587355137', '32GB+2TB', '2025-05-22 21:20:05', NULL, '0', '1590229800633634816', 'system', NULL);
INSERT INTO `goods_specs_value` VALUES ('1925545078720450562', '1912866651538366466', '曜石黑', '2025-05-22 21:31:36', NULL, '0', '1590229800633634816', 'system', NULL);
INSERT INTO `goods_specs_value` VALUES ('1925545135712653313', '1912866651538366466', '星云白', '2025-05-22 21:31:50', NULL, '0', '1590229800633634816', 'system', NULL);
INSERT INTO `goods_specs_value` VALUES ('1925545170894475265', '1912866651538366466', '深海蓝', '2025-05-22 21:31:58', NULL, '0', '1590229800633634816', 'system', NULL);
INSERT INTO `goods_specs_value` VALUES ('1935979460262977538', '1935979409943912449', '测试1', '2025-06-20 16:34:07', NULL, '0', '1881232176465358849', 'system', NULL);

-- ----------------------------
-- Table structure for goods_spu
-- ----------------------------
DROP TABLE IF EXISTS `goods_spu`;
CREATE TABLE `goods_spu`  (
                              `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                              `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品名称',
                              `sub_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '子标题',
                              `spu_urls` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品图地址',
                              `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态：0.下架；1.上架',
                              `sales_volume` int NOT NULL DEFAULT 0 COMMENT '销量',
                              `category_first_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '一级类目id',
                              `category_second_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '二级类目id',
                              `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                              `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                              `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                              `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '描述',
                              `enable_specs` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '多规格：0.否；1.是',
                              `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                              `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                              `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                              `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
                              `freight_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '运费类型：0.包邮；1.固定运费',
                              `fixed_freight_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '固定运费金额，仅当运费类型为1时生效',
                              `sales_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '商品价格（元）',
                              `original_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '商品原价（元）',
                              `cost_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '成本价（元）',
                              PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品spu' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of goods_spu
-- ----------------------------
INSERT INTO `goods_spu` VALUES ('1912867577569386497', 'Apple/苹果 iPhone 16 Pro Max（A3297）', NULL, 'https://minio.aryn.co/aryn/file/e8e18e94-d352-45d9-acdd-f918a6f77f90.jpg,https://minio.aryn.co/aryn/file/88e79726-2368-4767-aa60-065b5ea9aabd.jpg,https://minio.aryn.co/aryn/file/cc5819fb-8f39-45ad-b584-52ff3fbdf642.jpg,https://minio.aryn.co/aryn/file/f9a3fe3b-22ee-437d-9676-8fe4127e04ec.jpg,https://minio.aryn.co/aryn/file/f7a85fb7-adde-4635-9738-88709be3b76a.jpg', '1', 1073, '1912863400222957569', '1912863683464306689', '2025-04-17 21:55:45', '2025-07-15 23:34:04', '0', '<p><br></p><p><img src=\"https://minio.aryn.co/aryn/file/9888cb29-e6b8-4d67-b129-0d3859c3717e.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p><p><img src=\"https://minio.aryn.co/aryn/file/1744aaa5-ed8a-46ca-ba53-3e0038aa5ea6.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', '1', '1590229800633634816', 'admin', NULL, 1130, '0', 0.00, 9299.00, 9299.00, 11299.00);
INSERT INTO `goods_spu` VALUES ('1912870113080680449', '小米15 国家补贴 徕卡光学Summilux高速镜头 骁龙8至尊版移动平台', NULL, 'https://minio.aryn.co/aryn/file/6b5481c8-ee15-41a2-bca8-d08463947208.jpg,https://minio.aryn.co/aryn/file/22e2fc4e-bc1e-4c42-a57f-af4cb04af847.jpg,https://minio.aryn.co/aryn/file/9f371b82-9843-4fd2-a206-ebbb0b9ddb9e.jpg,https://minio.aryn.co/aryn/file/505b78ac-7dfa-4379-a4df-d11e90d34ae0.jpg,https://minio.aryn.co/aryn/file/3f462f51-a183-465d-94cc-f8bfd1759869.jpg', '1', 245, '1912863400222957569', '1912863683464306689', '2025-04-17 22:05:49', '2025-07-15 23:34:04', '0', '<p><br></p><p><img src=\"https://minio.aryn.co/aryn/file/292fbf15-33cc-4eee-b1ef-3a4dc71ba4d2.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', '1', '1590229800633634816', 'admin', NULL, 69955, '0', 0.00, 9999.00, 9999.00, 9999.00);
INSERT INTO `goods_spu` VALUES ('1912882761411239938', '大疆 DJI Mini 3 优选迷你航拍机 智能高清拍摄无人机 小型遥控飞机 兼容带屏遥控器 大疆无人机', '', 'https://minio.aryn.co/aryn/file/e38f917c-c4a5-4fd8-9acf-e6a4f40aff60.jpg,https://minio.aryn.co/aryn/file/ff6de4fc-406e-4f02-8b9a-6269dd9b8ce8.jpg,https://minio.aryn.co/aryn/file/0f4f5061-3930-4ef8-9352-5307d3689307.jpg,https://minio.aryn.co/aryn/file/78fa70d0-faff-4440-a9a4-55b4b4ca71f7.jpg', '1', 145, '1912863400222957569', '1912864294511484929', '2025-04-17 22:56:05', '2025-06-21 00:05:10', '0', '<p><br></p><p><img src=\"https://minio.aryn.co/aryn/file/b775af49-8e44-4ba7-b19c-08b8141af9c5.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', '0', '1590229800633634816', 'admin', NULL, 973, '2', 0.00, 5999.00, 5999.00, 5999.00);
INSERT INTO `goods_spu` VALUES ('1925543036622884866', '华为MateBook Fold 非凡大师 笔记本电脑 鸿蒙操作系统 超轻薄折叠电脑双层OLED显示屏', '华为MateBook Fold 非凡大师 笔记本电脑 鸿蒙操作系统 超轻薄折叠电脑双层OLED显示屏', 'https://minio.aryn.co/aryn/1590229800633634816/file/5548b0e1-7982-468a-8740-ff0177aed995.jpg,https://minio.aryn.co/aryn/1590229800633634816/file/f818bd69-14e6-4c24-88f2-dfc9c8687750.jpg,https://minio.aryn.co/aryn/1590229800633634816/file/b116d626-b40b-4c21-ae34-408277623e85.jpg,https://minio.aryn.co/aryn/1590229800633634816/file/fa5a558d-5962-49b8-9695-a57b9239af3e.jpg,https://minio.aryn.co/aryn/1590229800633634816/file/646127e7-5462-4d27-9d2e-4ecc324fd19c.jpg', '1', 2003, '1912863400222957569', '1925541056051564546', '2025-05-22 21:23:29', '2025-06-21 00:05:10', '0', '<p>2133333333333333333333123</p>', '1', '1590229800633634816', 'system', NULL, 3997, '0', 0.00, 25999.00, 25999.00, 25999.00);
INSERT INTO `goods_spu` VALUES ('1925545886404988929', 'HUAWEI Mate X6 分布式玄武架构 鸿蒙大屏AI 红枫原色影像折叠旗舰手机 折叠屏', NULL, 'https://minio.aryn.co/aryn/1590229800633634816/file/dcb4b7d0-4293-4c3f-aa00-0068e8216db6.jpg,https://minio.aryn.co/aryn/1590229800633634816/file/2590cb12-279c-402d-aacd-ed171ff6189f.jpg,https://minio.aryn.co/aryn/1590229800633634816/file/64ffdbb4-0bc4-4def-a9eb-e2da1aa25c17.jpg,https://minio.aryn.co/aryn/1590229800633634816/file/f51a1ac7-d968-4473-ac6e-aad0fed31948.jpg,https://minio.aryn.co/aryn/1590229800633634816/file/797ef622-bb8d-4b67-b383-2531b46ed48e.jpg', '1', 2000, '1912863400222957569', '1912863683464306689', '2025-05-22 21:34:49', '2026-04-05 13:03:56', '0', '<p><img src=\"\" alt=\"\" data-href=\"\" style=\"\"/></p><p><img src=\"https://minio.aryn.co/aryn/1590229800633634816/file/64ffdbb4-0bc4-4def-a9eb-e2da1aa25c17.jpg\" alt=\"\" data-href=\"\" style=\"\"/><img src=\"\" alt=\"\" data-href=\"\" style=\"\"/></p><p><img src=\"https://minio.aryn.co/aryn/1590229800633634816/file/f51a1ac7-d968-4473-ac6e-aad0fed31948.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', '1', '1590229800633634816', 'system', 'ozexS3bXeBjIkrfIZoe07SWXBY4I', -1, '0', 0.00, 0.00, 0.00, 0.00);

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
INSERT INTO `order_config` VALUES ('1927385204186599425', 'https://xxx.Aetheryn.cn/api', '15', 7, 7, '12312312312', NULL, '1', '2025-05-27 23:23:36', '2025-06-02 00:02:01', '0', 'system', 'system', '1590229800633634816', '0');
INSERT INTO `order_config` VALUES ('1927386954763333634', 'https://xxxx.Aetheryn.cn', '15', 2, 2, '12321123', NULL, '0', '2025-05-27 23:30:34', '2025-06-02 00:06:49', '0', 'system', 'system', '1590229800633634816', '0');

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
                               `payment_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付类型：1.微信支付；2.支付宝支付',
                               `trade_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '交易类型：（预留）',
                               `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                               `pay_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '支付状态：0.未支付；1.已支付;',
                               `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单状态',
                               `appraise_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '评价状态：0.待评价；1.已平价;',
                               `total_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '订单总金额（元）',
                               `freight_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '运费（元）',
                               `coupon_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '优惠券优惠金额（元）',
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
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of order_info
-- ----------------------------
INSERT INTO `order_info` VALUES ('2040656660950806529', '2040656345832747009', '2', '2040656659658969088', NULL, NULL, NULL, '0', '1', '0', 1.00, 0.00, 0.00, 1.00, '2026-04-05 13:03:56', NULL, '0', NULL, NULL, NULL, NULL, NULL, NULL, '1590229800633634816', 'ozexS3bXeBjIkrfIZoe07SWXBY4I', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'wxe150c73d0376f899', 'ozexS3bXeBjIkrfIZoe07SWXBY4I');

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
INSERT INTO `order_item` VALUES ('2040656661730947073', '2040656660950806529', '1925545886404988929', '2026667019995189250', 'HUAWEI Mate X6 分布式玄武架构 鸿蒙大屏AI 红枫原色影像折叠旗舰手机 折叠屏', 'https://minio.aryn.co/aryn/1590229800633634816/file/dcb4b7d0-4293-4c3f-aa00-0068e8216db6.jpg', 1.00, 1, 1.00, 0.00, 0.00, 1.00, '2026-04-05 13:03:56', NULL, '0', '0', '沙漠色钛金属', '1590229800633634816', 'ozexS3bXeBjIkrfIZoe07SWXBY4I', NULL);

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
-- Table structure for page_design
-- ----------------------------
DROP TABLE IF EXISTS `page_design`;
CREATE TABLE `page_design`  (
                                `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                `page_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '页面名称',
                                `page_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '页面内容',
                                `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态：0.正常；1.停用；',
                                `home_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '首页页面：0.否；1.是；',
                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '页面设计表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of page_design
-- ----------------------------
INSERT INTO `page_design` VALUES ('1912871334512336898', '首页', '{\"components\":[{\"title\":\"图片广告\",\"id\":\"DdqIzXHxWfqzy47oOOGSp\",\"type\":\"image-ad\",\"formData\":{\"type\":\"2\",\"interval\":5,\"swiperType\":\"1\",\"height\":140,\"imgRadius\":10,\"indicatorDots\":true,\"indicatorColor\":\"rgba(249, 223, 237, 1)\",\"indicatorActiveColor\":\"rgba(198, 222, 236, 1)\",\"commonImageStyle\":{\"styleTopMargin\":0,\"styleBottomMargin\":0,\"styleLeftMargin\":0,\"styleRightMargin\":0,\"styleTopPadding\":0,\"styleBottomPadding\":0,\"styleLeftPadding\":0,\"styleRightPadding\":0,\"styleLtRadius\":0,\"styleRtRadius\":0,\"styleLbRadius\":0,\"styleRbRadius\":0,\"bgColorDirection\":\"to right\",\"bgStartColor\":\"\",\"bgEndColor\":\"\",\"bgPicUrl\":\"\"},\"commonStyle\":{\"styleTopMargin\":10,\"styleBottomMargin\":10,\"styleLeftMargin\":10,\"styleRightMargin\":10,\"styleTopPadding\":0,\"styleBottomPadding\":0,\"styleLeftPadding\":0,\"styleRightPadding\":0,\"styleLtRadius\":100,\"styleRtRadius\":100,\"styleLbRadius\":100,\"styleRbRadius\":100,\"bgColorDirection\":\"to right\",\"bgStartColor\":\"\",\"bgEndColor\":\"\",\"bgPicUrl\":\"\"},\"imageList\":[{\"url\":\"https://minio.aryn.co/aryn/file/545972b8-53d5-41d3-8847-d9a623daa161.jpg\"},{\"url\":\"https://minio.aryn.co/aryn/file/9ccf25ba-bc08-4c67-9a51-913e22b1dad2.jpg\"}]}},{\"title\":\"图文导航\",\"id\":\"E_5fdneuFjO2HZujfFtE9\",\"type\":\"tab-nav\",\"formData\":{\"type\":\"3\",\"fontColor\":\"rgba(0, 0, 0, 1)\",\"showNum\":4,\"imgSize\":30,\"imgRadius\":0,\"scrollShow\":false,\"commonStyle\":{\"styleTopMargin\":10,\"styleBottomMargin\":10,\"styleLeftMargin\":10,\"styleRightMargin\":10,\"styleTopPadding\":10,\"styleBottomPadding\":10,\"styleLeftPadding\":10,\"styleRightPadding\":10,\"styleLtRadius\":10,\"styleRtRadius\":10,\"styleLbRadius\":10,\"styleRbRadius\":10,\"bgColorDirection\":\"to right\",\"bgStartColor\":\"rgba(255, 255, 255, 1)\",\"bgEndColor\":\"\",\"bgPicUrl\":\"\"},\"navList\":[{\"url\":\"https://minio.aryn.co/aryn/file/41130969-2c09-48ae-8ae9-b3ac70baf3be.png\",\"title\":\"全部商品\",\"link\":{\"name\":\"商品列表\",\"url\":\"/sub-pages/product/goods-list/index\"}},{\"url\":\"https://minio.aryn.co/aryn/1590229800633634816/file/fef3de8f-4cc2-46da-8ad4-490d584e6b84.svg\",\"title\":\"浏览记录\",\"link\":{\"name\":\"足迹列表\",\"url\":\"/sub-pages/user/footprint/index\"}},{\"url\":\"https://minio.aryn.co/aryn/1590229800633634816/file/fef3de8f-4cc2-46da-8ad4-490d584e6b84.svg\",\"title\":\"浏览记录\",\"link\":{\"name\":\"足迹列表\",\"url\":\"/sub-pages/user/footprint/index\"}},{\"url\":\"https://minio.aryn.co/aryn/file/b676ed92-17f1-4035-ba31-406d15baabe1.png\",\"title\":\"领券中心\",\"link\":{\"name\":\"优惠券列表\",\"url\":\"/sub-pages/promotion/coupon/coupon-list/index\"}}],\"scrollShw\":true}},{\"title\":\"商品\",\"id\":\"016z_FTuG3Tofz2AfUQkc\",\"type\":\"goods\",\"formData\":{\"showType\":\"3\",\"showDesc\":false,\"descSize\":14,\"descColor\":\"rgba(0, 0, 0, 1)\",\"descStyle\":\"0\",\"showName\":true,\"nameSize\":14,\"nameColor\":\"rgba(0, 0, 0, 1)\",\"nameStyle\":\"0\",\"showTag\":false,\"showSalesPrice\":true,\"salesPriceSize\":14,\"salesPriceColor\":\"rgba(255, 0, 0, 1)\",\"salesPriceStyle\":\"0\",\"showOriginalPrice\":false,\"showSalesVolume\":false,\"salesVolumeSize\":14,\"salesVolumeColor\":\"rgba(0, 0, 0, 1)\",\"salesVolumeStyle\":\"0\",\"showStock\":false,\"stockSize\":14,\"stockColor\":\"rgba(0, 0, 0, 1)\",\"stockStyle\":\"0\",\"buyBtnColor\":\"rgba(255, 0, 0, 1)\",\"buyBtnSize\":14,\"buyBtnStyle\":\"1\",\"showBuyBtn\":true,\"buyBtnText\":\"购买\",\"goodsList\":[{\"id\":\"1912867577569386497\",\"name\":\"Apple/苹果 iPhone 16 Pro Max（A3297）\",\"subTitle\":null,\"spuUrls\":[\"https://minio.aryn.co/aryn/file/e8e18e94-d352-45d9-acdd-f918a6f77f90.jpg\",\"https://minio.aryn.co/aryn/file/88e79726-2368-4767-aa60-065b5ea9aabd.jpg\",\"https://minio.aryn.co/aryn/file/cc5819fb-8f39-45ad-b584-52ff3fbdf642.jpg\",\"https://minio.aryn.co/aryn/file/f9a3fe3b-22ee-437d-9676-8fe4127e04ec.jpg\",\"https://minio.aryn.co/aryn/file/f7a85fb7-adde-4635-9738-88709be3b76a.jpg\"],\"status\":\"1\",\"salesVolume\":1000,\"freightTemplateId\":null,\"placeShipmentId\":null,\"categoryFirstId\":\"1912863400222957569\",\"categorySecondId\":\"1912863683464306689\",\"description\":\"<p><br></p><p><img src=\\\"https://minio.aryn.co/aryn/file/9888cb29-e6b8-4d67-b129-0d3859c3717e.jpg\\\" alt=\\\"\\\" data-href=\\\"\\\" style=\\\"\\\"/></p><p><img src=\\\"https://minio.aryn.co/aryn/file/1744aaa5-ed8a-46ca-ba53-3e0038aa5ea6.jpg\\\" alt=\\\"\\\" data-href=\\\"\\\" style=\\\"\\\"/></p>\",\"enableSpecs\":\"1\",\"tenantId\":null,\"shopId\":\"1\",\"shopName\":\"悦航购自营旗舰店\",\"shopType\":\"1\",\"shopCategoryFirstId\":\"1912864433649131522\",\"shopCategorySecondId\":\"1912864638087897089\",\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:44:17\",\"delFlag\":\"0\",\"verifyStatus\":\"1\",\"verifyDesc\":\"\",\"categoryName\":\"手机\",\"shopCategoryName\":null,\"freightTemplate\":null,\"goodsSkus\":[{\"id\":\"1912867578517299201\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:43:59\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242117281624066\",\"skuId\":\"1912867578517299201\",\"spuId\":null,\"specsValueId\":\"1912866747504041986\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"沙漠色钛金属\",\"specsId\":null},{\"id\":\"1913242118242119682\",\"skuId\":\"1912867578517299201\",\"spuId\":null,\"specsValueId\":\"1912866886893346818\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867579440046081\",\"spuId\":\"1912867577569386497\",\"salesPrice\":11299,\"originalPrice\":11299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242119148089346\",\"skuId\":\"1912867579440046081\",\"spuId\":null,\"specsValueId\":\"1912866747504041986\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"沙漠色钛金属\",\"specsId\":null},{\"id\":\"1913242120075030530\",\"skuId\":\"1912867579440046081\",\"spuId\":null,\"specsValueId\":\"1912866927229968386\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867580371181569\",\"spuId\":\"1912867577569386497\",\"salesPrice\":13299,\"originalPrice\":13299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242120997777410\",\"skuId\":\"1912867580371181569\",\"spuId\":null,\"specsValueId\":\"1912866747504041986\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:03\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"沙漠色钛金属\",\"specsId\":null},{\"id\":\"1913242121920524290\",\"skuId\":\"1912867580371181569\",\"spuId\":null,\"specsValueId\":\"1912866949661106178\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:03\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"1TB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867581298122754\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242122826493954\",\"skuId\":\"1912867581298122754\",\"spuId\":null,\"specsValueId\":\"1912866787874217985\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:03\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"原色钛金属\",\"specsId\":null},{\"id\":\"1913242123782795265\",\"skuId\":\"1912867581298122754\",\"spuId\":null,\"specsValueId\":\"1912866886893346818\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:03\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867582258618370\",\"spuId\":\"1912867577569386497\",\"salesPrice\":11299,\"originalPrice\":11299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:46\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242124734902274\",\"skuId\":\"1912867582258618370\",\"spuId\":null,\"specsValueId\":\"1912866787874217985\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"原色钛金属\",\"specsId\":null},{\"id\":\"1913242125649260545\",\"skuId\":\"1912867582258618370\",\"spuId\":null,\"specsValueId\":\"1912866927229968386\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867583248474114\",\"spuId\":\"1912867577569386497\",\"salesPrice\":13299,\"originalPrice\":13299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:46\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242126576201729\",\"skuId\":\"1912867583248474114\",\"spuId\":null,\"specsValueId\":\"1912866787874217985\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"原色钛金属\",\"specsId\":null},{\"id\":\"1913242127503142913\",\"skuId\":\"1912867583248474114\",\"spuId\":null,\"specsValueId\":\"1912866949661106178\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"1TB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867584276078593\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:46\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242128434278402\",\"skuId\":\"1912867584276078593\",\"spuId\":null,\"specsValueId\":\"1912866817397923842\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色钛金属\",\"specsId\":null},{\"id\":\"1913242129377996801\",\"skuId\":\"1912867584276078593\",\"spuId\":null,\"specsValueId\":\"1912866886893346818\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:05\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867585219796994\",\"spuId\":\"1912867577569386497\",\"salesPrice\":11299,\"originalPrice\":11299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:46\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242130271383553\",\"skuId\":\"1912867585219796994\",\"spuId\":null,\"specsValueId\":\"1912866817397923842\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:05\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色钛金属\",\"specsId\":null},{\"id\":\"1913242131202519041\",\"skuId\":\"1912867585219796994\",\"spuId\":null,\"specsValueId\":\"1912866927229968386\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:05\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867586150932481\",\"spuId\":\"1912867577569386497\",\"salesPrice\":13299,\"originalPrice\":13299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":\"2025-04-18 22:44:01\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242132121071617\",\"skuId\":\"1912867586150932481\",\"spuId\":null,\"specsValueId\":\"1912866817397923842\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:05\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色钛金属\",\"specsId\":null},{\"id\":\"1913242133064790018\",\"skuId\":\"1912867586150932481\",\"spuId\":null,\"specsValueId\":\"1912866949661106178\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"1TB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867587086262274\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":\"2025-04-18 22:44:01\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242133979148290\",\"skuId\":\"1912867587086262274\",\"spuId\":null,\"specsValueId\":\"1912866843499077633\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色钛金属\",\"specsId\":null},{\"id\":\"1913242134901895169\",\"skuId\":\"1912867587086262274\",\"spuId\":null,\"specsValueId\":\"1912866886893346818\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867588017397761\",\"spuId\":\"1912867577569386497\",\"salesPrice\":11299,\"originalPrice\":11299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":\"2025-04-18 22:44:01\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242135820447745\",\"skuId\":\"1912867588017397761\",\"spuId\":null,\"specsValueId\":\"1912866843499077633\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色钛金属\",\"specsId\":null},{\"id\":\"1913242136739000321\",\"skuId\":\"1912867588017397761\",\"spuId\":null,\"specsValueId\":\"1912866927229968386\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867588935950337\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":\"2025-04-18 22:44:01\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242137665941505\",\"skuId\":\"1912867588935950337\",\"spuId\":null,\"specsValueId\":\"1912866843499077633\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:07\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色钛金属\",\"specsId\":null},{\"id\":\"1913242138651602946\",\"skuId\":\"1912867588935950337\",\"spuId\":null,\"specsValueId\":\"1912866949661106178\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:07\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"1TB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0}],\"goodsSpuSpecs\":[{\"id\":\"1912867589871280130\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912888755335921665\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 23:19:54\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912934226058850306\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 02:20:35\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913242115398381569\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:01\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912867590802415617\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:48\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912888756262862850\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 23:19:54\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912934227099037698\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 02:20:35\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913242116350488578\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null}],\"collectId\":null,\"stock\":1200,\"enableMemberPrice\":\"1\",\"enableGivePoints\":\"1\",\"pointsAmount\":100,\"enablePointDeduction\":\"1\",\"pointDeductionRatio\":10,\"freightType\":\"0\",\"fixedFreightPrice\":0,\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"distributionCalcType\":\"0\",\"distributionType\":\"0\"},{\"id\":\"1912870113080680449\",\"name\":\"小米15 国家补贴 徕卡光学Summilux高速镜头 骁龙8至尊版移动平台\",\"subTitle\":null,\"spuUrls\":[\"https://minio.aryn.co/aryn/file/6b5481c8-ee15-41a2-bca8-d08463947208.jpg\",\"https://minio.aryn.co/aryn/file/22e2fc4e-bc1e-4c42-a57f-af4cb04af847.jpg\",\"https://minio.aryn.co/aryn/file/9f371b82-9843-4fd2-a206-ebbb0b9ddb9e.jpg\",\"https://minio.aryn.co/aryn/file/505b78ac-7dfa-4379-a4df-d11e90d34ae0.jpg\",\"https://minio.aryn.co/aryn/file/3f462f51-a183-465d-94cc-f8bfd1759869.jpg\"],\"status\":\"1\",\"salesVolume\":100,\"freightTemplateId\":null,\"placeShipmentId\":null,\"categoryFirstId\":\"1912863400222957569\",\"categorySecondId\":\"1912863683464306689\",\"description\":\"<p><br></p><p><img src=\\\"https://minio.aryn.co/aryn/file/292fbf15-33cc-4eee-b1ef-3a4dc71ba4d2.jpg\\\" alt=\\\"\\\" data-href=\\\"\\\" style=\\\"\\\"/></p>\",\"enableSpecs\":\"1\",\"tenantId\":null,\"shopId\":\"1\",\"shopName\":\"悦航购自营旗舰店\",\"shopType\":\"1\",\"shopCategoryFirstId\":\"1912864433649131522\",\"shopCategorySecondId\":\"1912864567430651906\",\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:49\",\"updateTime\":\"2025-04-18 22:44:17\",\"delFlag\":\"0\",\"verifyStatus\":\"1\",\"verifyDesc\":\"\",\"categoryName\":\"手机\",\"shopCategoryName\":null,\"freightTemplate\":null,\"goodsSkus\":[{\"id\":\"1912870113982455809\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:49\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241979834281985\",\"skuId\":\"1912870113982455809\",\"spuId\":null,\"specsValueId\":\"1912869461206147074\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/3f462f51-a183-465d-94cc-f8bfd1759869.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色\",\"specsId\":null},{\"id\":\"1913241980786388994\",\"skuId\":\"1912870113982455809\",\"spuId\":null,\"specsValueId\":\"1912869653045223426\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870114942951425\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4499,\"originalPrice\":4499,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:49\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241981717524481\",\"skuId\":\"1912870114942951425\",\"spuId\":null,\"specsValueId\":\"1912869461206147074\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/3f462f51-a183-465d-94cc-f8bfd1759869.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色\",\"specsId\":null},{\"id\":\"1913241982698991618\",\"skuId\":\"1912870114942951425\",\"spuId\":null,\"specsValueId\":\"1912869723891212289\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:30\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870115911835649\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:50\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241983638515713\",\"skuId\":\"1912870115911835649\",\"spuId\":null,\"specsValueId\":\"1912869494215319554\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:30\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/6b5481c8-ee15-41a2-bca8-d08463947208.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色\",\"specsId\":null},{\"id\":\"1913241984603205634\",\"skuId\":\"1912870115911835649\",\"spuId\":null,\"specsValueId\":\"1912869653045223426\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:30\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870116868136962\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4499,\"originalPrice\":4499,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:50\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241985538535425\",\"skuId\":\"1912870116868136962\",\"spuId\":null,\"specsValueId\":\"1912869494215319554\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:30\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/6b5481c8-ee15-41a2-bca8-d08463947208.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色\",\"specsId\":null},{\"id\":\"1913241986532585473\",\"skuId\":\"1912870116868136962\",\"spuId\":null,\"specsValueId\":\"1912869723891212289\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870117761523714\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:50\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241987484692481\",\"skuId\":\"1912870117761523714\",\"spuId\":null,\"specsValueId\":\"1912869535868952578\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/4714dced-02c2-4300-9f57-862033c1f98f.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"浅草色\",\"specsId\":null},{\"id\":\"1913241988457771010\",\"skuId\":\"1912870117761523714\",\"spuId\":null,\"specsValueId\":\"1912869653045223426\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870118663299074\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4499,\"originalPrice\":4499,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:50\",\"updateTime\":\"2025-04-18 22:43:27\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241989363740674\",\"skuId\":\"1912870118663299074\",\"spuId\":null,\"specsValueId\":\"1912869535868952578\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/4714dced-02c2-4300-9f57-862033c1f98f.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"浅草色\",\"specsId\":null},{\"id\":\"1913241990324236290\",\"skuId\":\"1912870118663299074\",\"spuId\":null,\"specsValueId\":\"1912869723891212289\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870119623794689\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:51\",\"updateTime\":\"2025-04-18 22:43:27\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241991255371777\",\"skuId\":\"1912870119623794689\",\"spuId\":null,\"specsValueId\":\"1912869571721863169\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:32\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/29c5fa1e-d2b5-487f-a4fe-ddf72154303f.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"丁香紫\",\"specsId\":null},{\"id\":\"1913241992203284482\",\"skuId\":\"1912870119623794689\",\"spuId\":null,\"specsValueId\":\"1912869653045223426\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:32\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870120580096002\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4499,\"originalPrice\":4499,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:51\",\"updateTime\":\"2025-04-18 22:43:27\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241993117642753\",\"skuId\":\"1912870120580096002\",\"spuId\":null,\"specsValueId\":\"1912869571721863169\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:32\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/29c5fa1e-d2b5-487f-a4fe-ddf72154303f.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"丁香紫\",\"specsId\":null},{\"id\":\"1913241994073944065\",\"skuId\":\"1912870120580096002\",\"spuId\":null,\"specsValueId\":\"1912869723891212289\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:32\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0}],\"goodsSpuSpecs\":[{\"id\":\"1912870121486065666\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:51\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912880928009658370\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:48:48\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912888587442126850\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 23:19:14\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912934769850363905\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 02:22:44\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241972276146177\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:27\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912870122438172674\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:51\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912880928936599554\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:48:48\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912888588406816770\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 23:19:14\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912934770953465857\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 02:22:45\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241973224058881\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:27\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241974138417154\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":2,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:28\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241975115689986\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":3,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:28\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241976080379906\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":4,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:28\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241977019904002\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":5,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:28\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241977934262273\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":6,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241978894757889\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":7,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null}],\"collectId\":null,\"stock\":800,\"enableMemberPrice\":\"1\",\"enableGivePoints\":\"1\",\"pointsAmount\":120,\"enablePointDeduction\":\"1\",\"pointDeductionRatio\":10,\"freightType\":\"0\",\"fixedFreightPrice\":0,\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"distributionCalcType\":\"0\",\"distributionType\":\"0\"},{\"id\":\"1912882761411239938\",\"name\":\"大疆 DJI Mini 3 优选迷你航拍机 智能高清拍摄无人机 小型遥控飞机 兼容带屏遥控器 大疆无人机\",\"subTitle\":null,\"spuUrls\":[\"https://minio.aryn.co/aryn/file/e38f917c-c4a5-4fd8-9acf-e6a4f40aff60.jpg\",\"https://minio.aryn.co/aryn/file/ff6de4fc-406e-4f02-8b9a-6269dd9b8ce8.jpg\",\"https://minio.aryn.co/aryn/file/0f4f5061-3930-4ef8-9352-5307d3689307.jpg\",\"https://minio.aryn.co/aryn/file/78fa70d0-faff-4440-a9a4-55b4b4ca71f7.jpg\"],\"status\":\"1\",\"salesVolume\":111,\"freightTemplateId\":null,\"placeShipmentId\":null,\"categoryFirstId\":\"1912863400222957569\",\"categorySecondId\":\"1912864294511484929\",\"description\":\"<p><br></p><p><img src=\\\"https://minio.aryn.co/aryn/file/b775af49-8e44-4ba7-b19c-08b8141af9c5.jpg\\\" alt=\\\"\\\" data-href=\\\"\\\" style=\\\"\\\"/></p>\",\"enableSpecs\":\"0\",\"tenantId\":null,\"shopId\":\"1\",\"shopName\":\"悦航购自营旗舰店\",\"shopType\":\"1\",\"shopCategoryFirstId\":\"1912882883218022401\",\"shopCategorySecondId\":\"1912864567430651906\",\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:56:05\",\"updateTime\":\"2025-04-18 02:23:33\",\"delFlag\":\"0\",\"verifyStatus\":\"1\",\"verifyDesc\":\"\",\"categoryName\":\"无人机\",\"shopCategoryName\":null,\"freightTemplate\":null,\"goodsSkus\":[{\"id\":\"1912882762380124162\",\"spuId\":\"1912882761411239938\",\"salesPrice\":5999,\"originalPrice\":5999,\"costPrice\":5999,\"stock\":1000,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:56:05\",\"updateTime\":\"2025-04-17 23:18:31\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":null,\"distributionFirstValue\":0,\"distributionSecondValue\":0}],\"goodsSpuSpecs\":[],\"collectId\":null,\"stock\":1000,\"enableMemberPrice\":\"0\",\"enableGivePoints\":\"1\",\"pointsAmount\":24,\"enablePointDeduction\":\"1\",\"pointDeductionRatio\":10,\"freightType\":\"0\",\"fixedFreightPrice\":0,\"salesPrice\":5999,\"originalPrice\":5999,\"costPrice\":5999,\"distributionCalcType\":\"0\",\"distributionType\":\"0\"}],\"goodsCommonStyle\":{\"styleTopMargin\":5,\"styleBottomMargin\":5,\"styleLeftMargin\":5,\"styleRightMargin\":5,\"styleTopPadding\":4,\"styleBottomPadding\":4,\"styleLeftPadding\":4,\"styleRightPadding\":4,\"styleLtRadius\":10,\"styleRtRadius\":10,\"styleLbRadius\":10,\"styleRbRadius\":10,\"bgColorDirection\":\"to right\",\"bgStartColor\":\"rgba(255, 255, 255, 1)\",\"bgEndColor\":\"\",\"bgPicUrl\":\"\"},\"commonStyle\":{\"styleTopMargin\":10,\"styleBottomMargin\":10,\"styleLeftMargin\":10,\"styleRightMargin\":10,\"styleTopPadding\":0,\"styleBottomPadding\":0,\"styleLeftPadding\":0,\"styleRightPadding\":0,\"styleLtRadius\":10,\"styleRtRadius\":10,\"styleLbRadius\":10,\"styleRbRadius\":10,\"bgColorDirection\":\"to right\",\"bgStartColor\":\"rgba(255, 255, 255, 1)\",\"bgEndColor\":\"\",\"bgPicUrl\":\"\"},\"showSalePrice\":true,\"salePriceSize\":14,\"salePriceColor\":\"rgba(255, 0, 0, 1)\",\"salePriceStyle\":\"0\"}},{\"title\":\"图片广告\",\"id\":\"obN44DXHcWUp8plYAam0s\",\"type\":\"imageAd\",\"formData\":{\"type\":\"2\",\"interval\":5,\"swiperType\":\"1\",\"height\":100,\"imgRadius\":10,\"indicatorDots\":true,\"indicatorColor\":\"rgba(249, 223, 237, 1)\",\"indicatorActiveColor\":\"rgba(138, 218, 240, 1)\",\"commonStyle\":{\"styleTopMargin\":10,\"styleBottomMargin\":10,\"styleLeftMargin\":10,\"styleRightMargin\":10,\"styleTopPadding\":0,\"styleBottomPadding\":0,\"styleLeftPadding\":0,\"styleRightPadding\":0,\"styleLtRadius\":0,\"styleRtRadius\":0,\"styleLbRadius\":0,\"styleRbRadius\":0,\"bgColorDirection\":\"to right\",\"bgStartColor\":\"\",\"bgEndColor\":\"\",\"bgPicUrl\":\"\"},\"imageList\":[{\"url\":\"https://minio.aryn.co/aryn/file/3ed6a47b-eb96-40d0-a9a9-1faba067ea9b.png\",\"link\":{\"name\":\"优惠券列表\",\"url\":\"/pages/promotion/coupon/coupon-list/index\"}},{\"url\":\"https://minio.aryn.co/aryn/file/23293bbc-dba7-4159-9609-fd2a8d75daa3.png\",\"link\":null}]}},{\"title\":\"商品\",\"id\":\"J3RnU3Mjc_kbQKuPFRJJS\",\"type\":\"goods\",\"formData\":{\"showType\":\"2\",\"showDesc\":true,\"descSize\":14,\"descColor\":\"rgba(0, 0, 0, 1)\",\"descStyle\":\"0\",\"showName\":true,\"nameSize\":14,\"nameColor\":\"rgba(0, 0, 0, 1)\",\"nameStyle\":\"0\",\"showTag\":true,\"showSalesPrice\":true,\"salesPriceSize\":14,\"salesPriceColor\":\"rgba(255, 0, 0, 1)\",\"salesPriceStyle\":\"0\",\"showOriginalPrice\":false,\"showSalesVolume\":false,\"salesVolumeSize\":14,\"salesVolumeColor\":\"rgba(0, 0, 0, 1)\",\"salesVolumeStyle\":\"0\",\"showStock\":false,\"stockSize\":14,\"stockColor\":\"rgba(0, 0, 0, 1)\",\"stockStyle\":\"0\",\"buyBtnColor\":\"rgba(255, 0, 0, 1)\",\"buyBtnSize\":14,\"buyBtnStyle\":\"2\",\"showBuyBtn\":true,\"buyBtnText\":\"购买\",\"goodsList\":[{\"id\":\"1912867577569386497\",\"name\":\"Apple/苹果 iPhone 16 Pro Max（A3297）\",\"subTitle\":null,\"spuUrls\":[\"https://minio.aryn.co/aryn/file/e8e18e94-d352-45d9-acdd-f918a6f77f90.jpg\",\"https://minio.aryn.co/aryn/file/88e79726-2368-4767-aa60-065b5ea9aabd.jpg\",\"https://minio.aryn.co/aryn/file/cc5819fb-8f39-45ad-b584-52ff3fbdf642.jpg\",\"https://minio.aryn.co/aryn/file/f9a3fe3b-22ee-437d-9676-8fe4127e04ec.jpg\",\"https://minio.aryn.co/aryn/file/f7a85fb7-adde-4635-9738-88709be3b76a.jpg\"],\"status\":\"1\",\"salesVolume\":1000,\"freightTemplateId\":null,\"placeShipmentId\":null,\"categoryFirstId\":\"1912863400222957569\",\"categorySecondId\":\"1912863683464306689\",\"description\":\"<p><br></p><p><img src=\\\"https://minio.aryn.co/aryn/file/9888cb29-e6b8-4d67-b129-0d3859c3717e.jpg\\\" alt=\\\"\\\" data-href=\\\"\\\" style=\\\"\\\"/></p><p><img src=\\\"https://minio.aryn.co/aryn/file/1744aaa5-ed8a-46ca-ba53-3e0038aa5ea6.jpg\\\" alt=\\\"\\\" data-href=\\\"\\\" style=\\\"\\\"/></p>\",\"enableSpecs\":\"1\",\"tenantId\":null,\"shopId\":\"1\",\"shopName\":\"悦航购自营旗舰店\",\"shopType\":\"1\",\"shopCategoryFirstId\":\"1912864433649131522\",\"shopCategorySecondId\":\"1912864638087897089\",\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:44:17\",\"delFlag\":\"0\",\"verifyStatus\":\"1\",\"verifyDesc\":\"\",\"categoryName\":\"手机\",\"shopCategoryName\":null,\"freightTemplate\":null,\"goodsSkus\":[{\"id\":\"1912867578517299201\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:43:59\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242117281624066\",\"skuId\":\"1912867578517299201\",\"spuId\":null,\"specsValueId\":\"1912866747504041986\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"沙漠色钛金属\",\"specsId\":null},{\"id\":\"1913242118242119682\",\"skuId\":\"1912867578517299201\",\"spuId\":null,\"specsValueId\":\"1912866886893346818\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867579440046081\",\"spuId\":\"1912867577569386497\",\"salesPrice\":11299,\"originalPrice\":11299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242119148089346\",\"skuId\":\"1912867579440046081\",\"spuId\":null,\"specsValueId\":\"1912866747504041986\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"沙漠色钛金属\",\"specsId\":null},{\"id\":\"1913242120075030530\",\"skuId\":\"1912867579440046081\",\"spuId\":null,\"specsValueId\":\"1912866927229968386\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867580371181569\",\"spuId\":\"1912867577569386497\",\"salesPrice\":13299,\"originalPrice\":13299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242120997777410\",\"skuId\":\"1912867580371181569\",\"spuId\":null,\"specsValueId\":\"1912866747504041986\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:03\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"沙漠色钛金属\",\"specsId\":null},{\"id\":\"1913242121920524290\",\"skuId\":\"1912867580371181569\",\"spuId\":null,\"specsValueId\":\"1912866949661106178\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:03\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"1TB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867581298122754\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:45\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242122826493954\",\"skuId\":\"1912867581298122754\",\"spuId\":null,\"specsValueId\":\"1912866787874217985\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:03\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"原色钛金属\",\"specsId\":null},{\"id\":\"1913242123782795265\",\"skuId\":\"1912867581298122754\",\"spuId\":null,\"specsValueId\":\"1912866886893346818\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:03\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867582258618370\",\"spuId\":\"1912867577569386497\",\"salesPrice\":11299,\"originalPrice\":11299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:46\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242124734902274\",\"skuId\":\"1912867582258618370\",\"spuId\":null,\"specsValueId\":\"1912866787874217985\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"原色钛金属\",\"specsId\":null},{\"id\":\"1913242125649260545\",\"skuId\":\"1912867582258618370\",\"spuId\":null,\"specsValueId\":\"1912866927229968386\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867583248474114\",\"spuId\":\"1912867577569386497\",\"salesPrice\":13299,\"originalPrice\":13299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:46\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242126576201729\",\"skuId\":\"1912867583248474114\",\"spuId\":null,\"specsValueId\":\"1912866787874217985\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"原色钛金属\",\"specsId\":null},{\"id\":\"1913242127503142913\",\"skuId\":\"1912867583248474114\",\"spuId\":null,\"specsValueId\":\"1912866949661106178\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"1TB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867584276078593\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:46\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242128434278402\",\"skuId\":\"1912867584276078593\",\"spuId\":null,\"specsValueId\":\"1912866817397923842\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:04\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色钛金属\",\"specsId\":null},{\"id\":\"1913242129377996801\",\"skuId\":\"1912867584276078593\",\"spuId\":null,\"specsValueId\":\"1912866886893346818\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:05\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867585219796994\",\"spuId\":\"1912867577569386497\",\"salesPrice\":11299,\"originalPrice\":11299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:46\",\"updateTime\":\"2025-04-18 22:44:00\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242130271383553\",\"skuId\":\"1912867585219796994\",\"spuId\":null,\"specsValueId\":\"1912866817397923842\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:05\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色钛金属\",\"specsId\":null},{\"id\":\"1913242131202519041\",\"skuId\":\"1912867585219796994\",\"spuId\":null,\"specsValueId\":\"1912866927229968386\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:05\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867586150932481\",\"spuId\":\"1912867577569386497\",\"salesPrice\":13299,\"originalPrice\":13299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":\"2025-04-18 22:44:01\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242132121071617\",\"skuId\":\"1912867586150932481\",\"spuId\":null,\"specsValueId\":\"1912866817397923842\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:05\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色钛金属\",\"specsId\":null},{\"id\":\"1913242133064790018\",\"skuId\":\"1912867586150932481\",\"spuId\":null,\"specsValueId\":\"1912866949661106178\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"1TB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867587086262274\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":\"2025-04-18 22:44:01\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242133979148290\",\"skuId\":\"1912867587086262274\",\"spuId\":null,\"specsValueId\":\"1912866843499077633\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色钛金属\",\"specsId\":null},{\"id\":\"1913242134901895169\",\"skuId\":\"1912867587086262274\",\"spuId\":null,\"specsValueId\":\"1912866886893346818\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867588017397761\",\"spuId\":\"1912867577569386497\",\"salesPrice\":11299,\"originalPrice\":11299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":\"2025-04-18 22:44:01\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242135820447745\",\"skuId\":\"1912867588017397761\",\"spuId\":null,\"specsValueId\":\"1912866843499077633\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色钛金属\",\"specsId\":null},{\"id\":\"1913242136739000321\",\"skuId\":\"1912867588017397761\",\"spuId\":null,\"specsValueId\":\"1912866927229968386\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:06\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912867588935950337\",\"spuId\":\"1912867577569386497\",\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":\"2025-04-18 22:44:01\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913242137665941505\",\"skuId\":\"1912867588935950337\",\"spuId\":null,\"specsValueId\":\"1912866843499077633\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:07\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色钛金属\",\"specsId\":null},{\"id\":\"1913242138651602946\",\"skuId\":\"1912867588935950337\",\"spuId\":null,\"specsValueId\":\"1912866949661106178\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:07\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"1TB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0}],\"goodsSpuSpecs\":[{\"id\":\"1912867589871280130\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:47\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912888755335921665\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 23:19:54\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912934226058850306\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 02:20:35\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913242115398381569\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:01\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912867590802415617\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 21:55:48\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912888756262862850\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 23:19:54\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912934227099037698\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 02:20:35\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913242116350488578\",\"spuId\":\"1912867577569386497\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:44:02\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null}],\"collectId\":null,\"stock\":1200,\"enableMemberPrice\":\"1\",\"enableGivePoints\":\"1\",\"pointsAmount\":100,\"enablePointDeduction\":\"1\",\"pointDeductionRatio\":10,\"freightType\":\"0\",\"fixedFreightPrice\":0,\"salesPrice\":9299,\"originalPrice\":9299,\"costPrice\":9299,\"distributionCalcType\":\"0\",\"distributionType\":\"0\"},{\"id\":\"1912870113080680449\",\"name\":\"小米15 国家补贴 徕卡光学Summilux高速镜头 骁龙8至尊版移动平台\",\"subTitle\":null,\"spuUrls\":[\"https://minio.aryn.co/aryn/file/6b5481c8-ee15-41a2-bca8-d08463947208.jpg\",\"https://minio.aryn.co/aryn/file/22e2fc4e-bc1e-4c42-a57f-af4cb04af847.jpg\",\"https://minio.aryn.co/aryn/file/9f371b82-9843-4fd2-a206-ebbb0b9ddb9e.jpg\",\"https://minio.aryn.co/aryn/file/505b78ac-7dfa-4379-a4df-d11e90d34ae0.jpg\",\"https://minio.aryn.co/aryn/file/3f462f51-a183-465d-94cc-f8bfd1759869.jpg\"],\"status\":\"1\",\"salesVolume\":100,\"freightTemplateId\":null,\"placeShipmentId\":null,\"categoryFirstId\":\"1912863400222957569\",\"categorySecondId\":\"1912863683464306689\",\"description\":\"<p><br></p><p><img src=\\\"https://minio.aryn.co/aryn/file/292fbf15-33cc-4eee-b1ef-3a4dc71ba4d2.jpg\\\" alt=\\\"\\\" data-href=\\\"\\\" style=\\\"\\\"/></p>\",\"enableSpecs\":\"1\",\"tenantId\":null,\"shopId\":\"1\",\"shopName\":\"悦航购自营旗舰店\",\"shopType\":\"1\",\"shopCategoryFirstId\":\"1912864433649131522\",\"shopCategorySecondId\":\"1912864567430651906\",\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:49\",\"updateTime\":\"2025-04-18 22:44:17\",\"delFlag\":\"0\",\"verifyStatus\":\"1\",\"verifyDesc\":\"\",\"categoryName\":\"手机\",\"shopCategoryName\":null,\"freightTemplate\":null,\"goodsSkus\":[{\"id\":\"1912870113982455809\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:49\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241979834281985\",\"skuId\":\"1912870113982455809\",\"spuId\":null,\"specsValueId\":\"1912869461206147074\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/3f462f51-a183-465d-94cc-f8bfd1759869.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色\",\"specsId\":null},{\"id\":\"1913241980786388994\",\"skuId\":\"1912870113982455809\",\"spuId\":null,\"specsValueId\":\"1912869653045223426\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870114942951425\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4499,\"originalPrice\":4499,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:49\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241981717524481\",\"skuId\":\"1912870114942951425\",\"spuId\":null,\"specsValueId\":\"1912869461206147074\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/3f462f51-a183-465d-94cc-f8bfd1759869.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"黑色\",\"specsId\":null},{\"id\":\"1913241982698991618\",\"skuId\":\"1912870114942951425\",\"spuId\":null,\"specsValueId\":\"1912869723891212289\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:30\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870115911835649\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:50\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241983638515713\",\"skuId\":\"1912870115911835649\",\"spuId\":null,\"specsValueId\":\"1912869494215319554\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:30\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/6b5481c8-ee15-41a2-bca8-d08463947208.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色\",\"specsId\":null},{\"id\":\"1913241984603205634\",\"skuId\":\"1912870115911835649\",\"spuId\":null,\"specsValueId\":\"1912869653045223426\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:30\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870116868136962\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4499,\"originalPrice\":4499,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:50\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241985538535425\",\"skuId\":\"1912870116868136962\",\"spuId\":null,\"specsValueId\":\"1912869494215319554\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:30\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/6b5481c8-ee15-41a2-bca8-d08463947208.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"白色\",\"specsId\":null},{\"id\":\"1913241986532585473\",\"skuId\":\"1912870116868136962\",\"spuId\":null,\"specsValueId\":\"1912869723891212289\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870117761523714\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:50\",\"updateTime\":\"2025-04-18 22:43:26\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241987484692481\",\"skuId\":\"1912870117761523714\",\"spuId\":null,\"specsValueId\":\"1912869535868952578\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/4714dced-02c2-4300-9f57-862033c1f98f.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"浅草色\",\"specsId\":null},{\"id\":\"1913241988457771010\",\"skuId\":\"1912870117761523714\",\"spuId\":null,\"specsValueId\":\"1912869653045223426\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870118663299074\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4499,\"originalPrice\":4499,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:50\",\"updateTime\":\"2025-04-18 22:43:27\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241989363740674\",\"skuId\":\"1912870118663299074\",\"spuId\":null,\"specsValueId\":\"1912869535868952578\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/4714dced-02c2-4300-9f57-862033c1f98f.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"浅草色\",\"specsId\":null},{\"id\":\"1913241990324236290\",\"skuId\":\"1912870118663299074\",\"spuId\":null,\"specsValueId\":\"1912869723891212289\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:31\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870119623794689\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:51\",\"updateTime\":\"2025-04-18 22:43:27\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241991255371777\",\"skuId\":\"1912870119623794689\",\"spuId\":null,\"specsValueId\":\"1912869571721863169\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:32\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/29c5fa1e-d2b5-487f-a4fe-ddf72154303f.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"丁香紫\",\"specsId\":null},{\"id\":\"1913241992203284482\",\"skuId\":\"1912870119623794689\",\"spuId\":null,\"specsValueId\":\"1912869653045223426\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:32\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+256GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0},{\"id\":\"1912870120580096002\",\"spuId\":\"1912870113080680449\",\"salesPrice\":4499,\"originalPrice\":4499,\"costPrice\":4199,\"stock\":100,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:51\",\"updateTime\":\"2025-04-18 22:43:27\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[{\"id\":\"1913241993117642753\",\"skuId\":\"1912870120580096002\",\"spuId\":null,\"specsValueId\":\"1912869571721863169\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:32\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":\"https://minio.aryn.co/aryn/file/29c5fa1e-d2b5-487f-a4fe-ddf72154303f.jpg\",\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"丁香紫\",\"specsId\":null},{\"id\":\"1913241994073944065\",\"skuId\":\"1912870120580096002\",\"spuId\":null,\"specsValueId\":\"1912869723891212289\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:32\",\"updateTime\":null,\"delFlag\":\"0\",\"picUrl\":null,\"tenantId\":null,\"shopId\":null,\"specsValueName\":\"12GB+512GB\",\"specsId\":null}],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":\"0\",\"distributionFirstValue\":0,\"distributionSecondValue\":0}],\"goodsSpuSpecs\":[{\"id\":\"1912870121486065666\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:51\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912880928009658370\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:48:48\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912888587442126850\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 23:19:14\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912934769850363905\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 02:22:44\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241972276146177\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":0,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:27\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912870122438172674\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:05:51\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912880928936599554\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:48:48\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912888588406816770\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 23:19:14\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1912934770953465857\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 02:22:45\",\"updateTime\":null,\"delFlag\":\"1\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241973224058881\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":1,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:27\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241974138417154\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":2,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:28\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241975115689986\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866651538366466\",\"sort\":3,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:28\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241976080379906\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":4,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:28\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241977019904002\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":5,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:28\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241977934262273\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":6,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null},{\"id\":\"1913241978894757889\",\"spuId\":\"1912870113080680449\",\"specsId\":\"1912866862587355137\",\"sort\":7,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-18 22:43:29\",\"updateTime\":null,\"delFlag\":\"0\",\"tenantId\":null,\"shopId\":null,\"specsName\":null,\"goodsSkuSpecsValues\":null}],\"collectId\":null,\"stock\":800,\"enableMemberPrice\":\"1\",\"enableGivePoints\":\"1\",\"pointsAmount\":120,\"enablePointDeduction\":\"1\",\"pointDeductionRatio\":10,\"freightType\":\"0\",\"fixedFreightPrice\":0,\"salesPrice\":4199,\"originalPrice\":4199,\"costPrice\":4199,\"distributionCalcType\":\"0\",\"distributionType\":\"0\"},{\"id\":\"1912882761411239938\",\"name\":\"大疆 DJI Mini 3 优选迷你航拍机 智能高清拍摄无人机 小型遥控飞机 兼容带屏遥控器 大疆无人机\",\"subTitle\":null,\"spuUrls\":[\"https://minio.aryn.co/aryn/file/e38f917c-c4a5-4fd8-9acf-e6a4f40aff60.jpg\",\"https://minio.aryn.co/aryn/file/ff6de4fc-406e-4f02-8b9a-6269dd9b8ce8.jpg\",\"https://minio.aryn.co/aryn/file/0f4f5061-3930-4ef8-9352-5307d3689307.jpg\",\"https://minio.aryn.co/aryn/file/78fa70d0-faff-4440-a9a4-55b4b4ca71f7.jpg\"],\"status\":\"1\",\"salesVolume\":111,\"freightTemplateId\":null,\"placeShipmentId\":null,\"categoryFirstId\":\"1912863400222957569\",\"categorySecondId\":\"1912864294511484929\",\"description\":\"<p><br></p><p><img src=\\\"https://minio.aryn.co/aryn/file/b775af49-8e44-4ba7-b19c-08b8141af9c5.jpg\\\" alt=\\\"\\\" data-href=\\\"\\\" style=\\\"\\\"/></p>\",\"enableSpecs\":\"0\",\"tenantId\":null,\"shopId\":\"1\",\"shopName\":\"悦航购自营旗舰店\",\"shopType\":\"1\",\"shopCategoryFirstId\":\"1912882883218022401\",\"shopCategorySecondId\":\"1912864567430651906\",\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:56:05\",\"updateTime\":\"2025-04-18 02:23:33\",\"delFlag\":\"0\",\"verifyStatus\":\"1\",\"verifyDesc\":\"\",\"categoryName\":\"无人机\",\"shopCategoryName\":null,\"freightTemplate\":null,\"goodsSkus\":[{\"id\":\"1912882762380124162\",\"spuId\":\"1912882761411239938\",\"salesPrice\":5999,\"originalPrice\":5999,\"costPrice\":5999,\"stock\":1000,\"weight\":null,\"volume\":null,\"createBy\":null,\"updateBy\":null,\"createTime\":\"2025-04-17 22:56:05\",\"updateTime\":\"2025-04-17 23:18:31\",\"delFlag\":\"0\",\"firstRate\":null,\"secondRate\":null,\"version\":null,\"goodsSkuSpecsValues\":[],\"goodsSpu\":null,\"tenantId\":null,\"shopId\":null,\"status\":null,\"distributionFirstValue\":0,\"distributionSecondValue\":0}],\"goodsSpuSpecs\":[],\"collectId\":null,\"stock\":1000,\"enableMemberPrice\":\"0\",\"enableGivePoints\":\"1\",\"pointsAmount\":24,\"enablePointDeduction\":\"1\",\"pointDeductionRatio\":10,\"freightType\":\"0\",\"fixedFreightPrice\":0,\"salesPrice\":5999,\"originalPrice\":5999,\"costPrice\":5999,\"distributionCalcType\":\"0\",\"distributionType\":\"0\"}],\"goodsCommonStyle\":{\"styleTopMargin\":10,\"styleBottomMargin\":10,\"styleLeftMargin\":10,\"styleRightMargin\":10,\"styleTopPadding\":0,\"styleBottomPadding\":0,\"styleLeftPadding\":0,\"styleRightPadding\":0,\"styleLtRadius\":33,\"styleRtRadius\":33,\"styleLbRadius\":33,\"styleRbRadius\":33,\"bgColorDirection\":\"to right\",\"bgStartColor\":\"rgba(255, 255, 255, 1)\",\"bgEndColor\":\"\"},\"commonStyle\":{\"styleTopMargin\":10,\"styleBottomMargin\":10,\"styleLeftMargin\":10,\"styleRightMargin\":10,\"styleTopPadding\":0,\"styleBottomPadding\":0,\"styleLeftPadding\":0,\"styleRightPadding\":0,\"styleLtRadius\":10,\"styleRtRadius\":10,\"styleLbRadius\":10,\"styleRbRadius\":10,\"bgColorDirection\":\"to right\",\"bgStartColor\":\"rgba(255, 255, 255, 1)\",\"bgEndColor\":\"\",\"bgPicUrl\":\"\"},\"showSalePrice\":true,\"salePriceSize\":14,\"salePriceColor\":\"rgba(255, 0, 0, 1)\",\"salePriceStyle\":\"0\"}}]}', '0', '1', '2025-04-17 22:10:40', '2026-04-05 12:35:51', '0', '1590229800633634816', 'admin', 'system');

-- ----------------------------
-- Table structure for pay_config
-- ----------------------------
DROP TABLE IF EXISTS `pay_config`;
CREATE TABLE `pay_config`  (
                               `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                               `type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付类型：1.微信；2.支付宝；',
                               `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用Id',
                               `mch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商户号',
                               `mch_key` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商户密钥',
                               `key_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'apiclient_cert.p12文件的绝对路径，或者如果放在项目中，请以classpath:开头指定.',
                               `cert_serial_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '证书序列号',
                               `apiv3_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'apiV3秘钥',
                               `private_key_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'apiv3 商户apiclient_key.pem',
                               `private_cert_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'apiv3 商户apiclient_cert.pem',
                               `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                               `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                               `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                               `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                               `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                               `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                               `terminal_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付端类型',
                               `public_key_path` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信支付公钥',
                               `public_key_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信支付公钥ID',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付配置' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pay_config
-- ----------------------------
INSERT INTO `pay_config` VALUES ('1995858129627648002', '1', 'wx337f86e8f83c8575', '1730012632', NULL, '1', '66B832045864575002FF387F98DD03650603102B', '7kF9xQ3bZ8tP2sC5vM1nR4dY6gH7jK9l', '1', '1', '2025-12-02 22:10:34', NULL, '0', '1590229800633634816', 'system', NULL, '0', '1', 'PUB_KEY_ID_0117300126322025101900292098001800');

-- ----------------------------
-- Table structure for pay_notify_record
-- ----------------------------
DROP TABLE IF EXISTS `pay_notify_record`;
CREATE TABLE `pay_notify_record`  (
                                      `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                      `out_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户订单号',
                                      `channel_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道订单号',
                                      `request` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '请求报文',
                                      `response` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '响应报文',
                                      `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                      `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                      `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                      `type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '回调类型：1.支付回调；2.退款回调',
                                      `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                      PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '回调通知记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pay_notify_record
-- ----------------------------

-- ----------------------------
-- Table structure for pay_refund_order
-- ----------------------------
DROP TABLE IF EXISTS `pay_refund_order`;
CREATE TABLE `pay_refund_order`  (
                                     `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                     `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                     `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                     `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                     `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                     `notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '通知地址',
                                     `channel_mch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道号',
                                     `channel_refund_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道退款单号',
                                     `refund_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户退款单号',
                                     `refund_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '退款状态：0.待退款；1.退款中；2.已退款；3.退款失败；',
                                     `extra` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '额外参数',
                                     `err_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道退款错误码',
                                     `err_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道退款错误描述',
                                     `pay_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '支付总金额（元）',
                                     `refund_amount` decimal(10, 2) NOT NULL COMMENT '退款金额（元）',
                                     `out_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户支付订单号',
                                     `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                     `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                     `refund_success_time` datetime NULL DEFAULT NULL COMMENT '退款成功时间',
                                     `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户ID',
                                     PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '退款订单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pay_refund_order
-- ----------------------------

-- ----------------------------
-- Table structure for pay_trade_order
-- ----------------------------
DROP TABLE IF EXISTS `pay_trade_order`;
CREATE TABLE `pay_trade_order`  (
                                    `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                    `channel_mch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道号',
                                    `out_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户订单号',
                                    `description` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单描述',
                                    `trade_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '交易类型',
                                    `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户id（openid、支付宝buyerId）',
                                    `pay_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付状态：0.待支付；1.已支付；',
                                    `channel_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道订单号',
                                    `extra` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '额外参数',
                                    `err_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道支付错误码',
                                    `err_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道支付错误描述',
                                    `pay_success_time` datetime NULL DEFAULT NULL COMMENT '支付成功时间',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                    `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                    `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                    `notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '通知地址',
                                    `amount` decimal(10, 2) NOT NULL COMMENT '金额',
                                    `return_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                                    `quit_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                                    `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                    `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                    `terminal_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付端类型',
                                    `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户ID',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付订单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pay_trade_order
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
                                  `specs_info` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格信息',
                                  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                  `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '购物车' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of shopping_cart
-- ----------------------------
INSERT INTO `shopping_cart` VALUES ('2040658253926494209', '2040656345832747009', '1912867577569386497', '2026667593272659970', 1, 'Apple/苹果 iPhone 16 Pro Max（A3297）', 9299.00, 'https://minio.aryn.co/aryn/file/e8e18e94-d352-45d9-acdd-f918a6f77f90.jpg', '2026-04-05 13:10:16', '2026-04-05 13:15:27', '0', '256GB', '1590229800633634816', 'ozexS3bXeBjIkrfIZoe07SWXBY4I', 'ozexS3bXeBjIkrfIZoe07SWXBY4I');

-- ----------------------------
-- Table structure for social_account
-- ----------------------------
DROP TABLE IF EXISTS `social_account`;
CREATE TABLE `social_account`  (
                                   `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                   `type` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '账号类型',
                                   `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '账号',
                                   `app_secret` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密钥',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                   `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                   `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                   `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                   `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '三方账号表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of social_account
-- ----------------------------
INSERT INTO `social_account` VALUES ('1', 'WX_MA', 'wxe150c73d0376f899', 'xxxxxxxx', '2026-04-05 12:33:47', '2026-04-05 13:42:02', '0', NULL, 'system', '1590229800633634816');
-- ----------------------------
-- Table structure for social_user
-- ----------------------------
DROP TABLE IF EXISTS `social_user`;
CREATE TABLE `social_user`  (
                                `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                `social_account_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号ID',
                                `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号',
                                `open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'openid',
                                `session_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会话密钥',
                                `unionid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户在开放平台的唯一标识符，若当前小程序已绑定到微信开放平台帐号下会返回',
                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
                                `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
                                `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号码',
                                `mall_user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商城用户主键',
                                `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                PRIMARY KEY (`id`) USING BTREE,
                                UNIQUE INDEX `uk_social_openid`(`social_account_id` ASC, `open_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '三方用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of social_user
-- ----------------------------

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`  (
                             `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                             `parent_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '父级id',
                             `dept_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '部门名称',
                             `leader` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '负责人',
                             `leader_phone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '负责人联系电话',
                             `sort` int NULL DEFAULT NULL COMMENT '排序序号',
                             `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                             `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                             `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                             `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                             `status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态：0.正常；1.停用；',
                             `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                             `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '部门' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES ('1', '0', '天启雨数科技有限公司', 'lijx', '17615123397', 1, '0', '2022-02-18 17:46:40', '2024-11-08 16:25:23', '1590229800633634816', '0', NULL, 'admin');
INSERT INTO `sys_dept` VALUES ('1881232178197606401', '0', '系统租户', NULL, NULL, 1, '0', '2025-01-20 14:47:58', NULL, '1881232176465358849', NULL, 'lijx', NULL);

-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict`  (
                             `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                             `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型',
                             `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
                             `status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态：0.正常；1.停用；',
                             `remarks` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                             `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                             `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                             `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                             `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                             `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '字典表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dict
-- ----------------------------
INSERT INTO `sys_dict` VALUES ('1583357541572108290', 'sys_storage_type', '文件存储类型', '0', '文件存储配置类型', '0', '2022-10-21 15:20:31', '2022-10-21 15:55:51', NULL, NULL);
INSERT INTO `sys_dict` VALUES ('1585639342383202305', 'status', '状态', '0', '状态', '0', '2022-10-27 22:27:33', NULL, NULL, NULL);
INSERT INTO `sys_dict` VALUES ('1590901687026937857', 'application_key', '应用key', '0', '应用key用于租户授权', '0', '2022-11-11 10:58:14', NULL, NULL, NULL);
INSERT INTO `sys_dict` VALUES ('1597072635358756866', 'log_status', '日志状态', '0', NULL, '0', '2022-11-28 11:39:23', NULL, NULL, NULL);
INSERT INTO `sys_dict` VALUES ('1825786096614240258', 'pay_type', '支付类型', '0', '支付类型', '0', '2024-08-20 14:45:02', '2024-08-20 14:46:05', 'admin', 'admin');
INSERT INTO `sys_dict` VALUES ('1825786632403021826', 'pay_status', '支付状态', '0', '支付状态：0.未支付；1.已支付;', '0', '2024-08-20 14:47:09', '2024-08-20 14:47:09', 'admin', 'admin');
INSERT INTO `sys_dict` VALUES ('1825787265549987842', 'delivery_way', '配送方式', '0', '配送方式', '0', '2024-08-20 14:49:40', '2024-08-20 14:49:40', 'admin', 'admin');
INSERT INTO `sys_dict` VALUES ('1845468560374734850', 'user_source', '用户来源', '0', '用户来源', '0', '2024-10-13 22:16:07', NULL, 'admin', NULL);
INSERT INTO `sys_dict` VALUES ('1855529374229479425', 'menu_type', '菜单类型', '0', '菜单类型', '0', '2024-11-10 16:34:12', NULL, 'admin', NULL);
INSERT INTO `sys_dict` VALUES ('1869292192170770434', 'user_sex', '用户性别', '0', '用户性别', '0', '2024-12-18 16:02:43', NULL, 'lijx', NULL);
INSERT INTO `sys_dict` VALUES ('1871032754213273601', 'goods_status', '商品状态', '0', '商品状态', '0', '2024-12-23 11:19:05', NULL, 'admin', NULL);
INSERT INTO `sys_dict` VALUES ('1871032929883308033', 'goods_specs', '商品规格类型', '0', '商品规格类型', '0', '2024-12-23 11:19:47', NULL, 'admin', NULL);
INSERT INTO `sys_dict` VALUES ('1871381190397775873', 'refund_status', '退款状态', '0', '退款状态', '0', '2024-12-24 10:23:39', '2024-12-24 10:23:44', 'lijx', 'lijx');
INSERT INTO `sys_dict` VALUES ('1905998662952812546', 'freight_type', '运费类型', '0', '运费类型：0.包邮；1.固定运费；', '0', '2025-03-29 23:01:08', '2025-03-29 23:01:14', 'system', 'system');
INSERT INTO `sys_dict` VALUES ('1911974209927307266', 'order_status', '订单状态', '0', '订单状态', '0', '2025-04-15 10:45:49', NULL, 'system', NULL);
INSERT INTO `sys_dict` VALUES ('1915049899597111298', 'order_item_status', '订单项状态', '0', '订单项状态', '0', '2025-04-23 22:27:31', NULL, 'system', NULL);
INSERT INTO `sys_dict` VALUES ('1915429675147091970', 'refund_type', '退款类型', '0', '退款类型', '0', '2025-04-24 23:36:36', NULL, 'system', NULL);
INSERT INTO `sys_dict` VALUES ('1917043550726402050', 'order_delivery_status', '发货状态', '0', NULL, '0', '2025-04-29 10:29:34', NULL, 'system', NULL);
INSERT INTO `sys_dict` VALUES ('1927953218776702978', 'pay_terminal_type', '支付端类型', '0', '支付端类型：0-小程序，1-App，2-H5，3-PC，4-公众号', '0', '2025-05-29 13:00:42', NULL, 'system', NULL);
INSERT INTO `sys_dict` VALUES ('1929205944271659009', 'mq_delay_time_level', 'MQ延迟时间级别', '0', 'MQ延迟时间级别', '0', '2025-06-01 23:58:35', NULL, 'system', NULL);

-- ----------------------------
-- Table structure for sys_dict_value
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_value`;
CREATE TABLE `sys_dict_value`  (
                                   `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                   `dict_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典主键',
                                   `dict_label` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '字典标签',
                                   `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '字典键值',
                                   `dict_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '字典类型',
                                   `status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态：0.正常；1.停用；',
                                   `remarks` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                                   `sort` int NULL DEFAULT NULL COMMENT '排序序号',
                                   `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                                   `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                   `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                   `show_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '回显样式',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '字典键值表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dict_value
-- ----------------------------
INSERT INTO `sys_dict_value` VALUES ('1585639417511575553', '1585639342383202305', '正常', '0', 'status', '0', '正常', 1, '0', '2022-10-27 22:27:51', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1585639464043184129', '1585639342383202305', '停用', '1', 'status', '0', '停用', 2, '0', '2022-10-27 22:28:02', '2024-11-08 15:10:50', NULL, 'admin', NULL);
INSERT INTO `sys_dict_value` VALUES ('1590901958058668034', '1590901687026937857', '基础应用', 'app_base', 'application_key', '0', '商城基础功能', 1, '0', '2022-11-11 10:59:19', '2022-11-11 16:05:37', NULL, NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1590902082256203777', '1590901687026937857', '营销应用', 'app_market', 'application_key', '0', '营销应用包括（优惠券/多人拼团）', 2, '0', '2022-11-11 10:59:48', '2022-11-11 16:05:38', NULL, NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1592543489640525826', '1590901687026937857', '平台应用', 'sys_key', 'application_key', '0', '平台应用', 5, '0', '2022-11-15 23:42:11', '2022-11-15 23:44:14', NULL, NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1597072888086544385', '1597072635358756866', '成功', '1', 'log_status', '0', '成功', 1, '0', '2022-11-28 11:40:23', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1597072943552020482', '1597072635358756866', '失败', '0', 'log_status', '0', '失败', 2, '0', '2022-11-28 11:40:36', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1825786144924233730', '1825786096614240258', '微信支付', '1', 'pay_type', '0', '微信支付', 1, '0', '2024-08-20 14:45:13', '2024-12-24 10:04:11', 'admin', 'lijx', 'success');
INSERT INTO `sys_dict_value` VALUES ('1825786177023242241', '1825786096614240258', '支付宝支付', '2', 'pay_type', '0', '支付宝支付', 2, '0', '2024-08-20 14:45:21', '2024-12-24 10:04:08', 'admin', 'lijx', 'primary');
INSERT INTO `sys_dict_value` VALUES ('1825786695783149569', '1825786632403021826', '未支付', '0', 'pay_status', '0', '未支付', 0, '0', '2024-08-20 14:47:24', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1825786736451121153', '1825786632403021826', '已支付', '1', 'pay_status', '0', '已支付', 1, '0', '2024-08-20 14:47:34', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1825787338904170497', '1825787265549987842', '普通快递', '1', 'delivery_way', '0', '普通快递', 1, '0', '2024-08-20 14:49:58', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1825787372362133505', '1825787265549987842', '上门自提', '2', 'delivery_way', '0', '上门自提', 2, '0', '2024-08-20 14:50:06', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1845468623192825858', '1845468560374734850', '微信小程序', 'WX_MA', 'user_source', '0', '微信小程序', 1, '0', '2024-10-13 22:16:22', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1845468655694487554', '1845468560374734850', 'APP', 'APP', 'user_source', '0', 'APP', 2, '0', '2024-10-13 22:16:29', '2025-05-22 21:49:47', 'admin', 'system', NULL);
INSERT INTO `sys_dict_value` VALUES ('1845468737055596546', '1845468560374734850', '普通H5', 'H5', 'user_source', '0', '普通H5', 3, '0', '2024-10-13 22:16:49', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1855529435244019713', '1855529374229479425', '菜单', '0', 'menu_type', '0', '菜单', 0, '0', '2024-11-10 16:34:26', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1855529480534114306', '1855529374229479425', '按钮', '1', 'menu_type', '0', '按钮', 1, '0', '2024-11-10 16:34:37', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1869292244662484994', '1869292192170770434', '男', '1', 'user_sex', '0', '男', 1, '0', '2024-12-18 16:02:56', NULL, 'lijx', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1869292302673903618', '1869292192170770434', '女', '2', 'user_sex', '0', '女', 2, '0', '2024-12-18 16:03:09', NULL, 'lijx', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1871032820101595138', '1871032754213273601', '已下架', '0', 'goods_status', '0', '已下架', 0, '0', '2024-12-23 11:19:21', '2024-12-23 11:31:45', 'admin', 'admin', 'danger');
INSERT INTO `sys_dict_value` VALUES ('1871032850510299138', '1871032754213273601', '已上架', '1', 'goods_status', '0', '已上架', 1, '0', '2024-12-23 11:19:28', '2024-12-23 11:31:49', 'admin', 'admin', 'success');
INSERT INTO `sys_dict_value` VALUES ('1871033002629316609', '1871032929883308033', '单规格', '0', 'goods_specs', '0', '单规格', 0, '0', '2024-12-23 11:20:05', '2024-12-23 11:31:19', 'admin', 'admin', 'primary');
INSERT INTO `sys_dict_value` VALUES ('1871033035990810626', '1871032929883308033', '多规格', '1', 'goods_specs', '0', '多规格', 1, '0', '2024-12-23 11:20:13', '2024-12-23 11:31:25', 'admin', 'admin', 'success');
INSERT INTO `sys_dict_value` VALUES ('1871381497785733121', '1871381190397775873', '待审核', '1', 'refund_status', '0', '待审核', 1, '0', '2024-12-24 10:24:52', '2025-04-23 22:56:30', 'lijx', 'system', 'primary');
INSERT INTO `sys_dict_value` VALUES ('1871381692225277953', '1871381190397775873', '退款中', '5', 'refund_status', '0', '退款中', 5, '0', '2024-12-24 10:25:39', '2025-04-23 22:57:34', 'lijx', 'system', 'warning');
INSERT INTO `sys_dict_value` VALUES ('1905998759681851393', '1905998662952812546', '包邮', '0', 'freight_type', '0', '包邮', 0, '0', '2025-03-29 23:01:31', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1905998803931758593', '1905998662952812546', '固定运费', '1', 'freight_type', '0', '固定运费', 1, '0', '2025-03-29 23:01:41', NULL, 'system', NULL, 'success');
INSERT INTO `sys_dict_value` VALUES ('1911974266093232129', '1911974209927307266', '已完成', '4', 'order_status', '0', '已完成', 4, '0', '2025-04-15 10:46:02', '2025-04-15 10:46:41', 'system', 'system', 'success');
INSERT INTO `sys_dict_value` VALUES ('1911974503704748033', '1911974209927307266', '待付款', '1', 'order_status', '0', '待付款', 1, '0', '2025-04-15 10:46:59', NULL, 'system', NULL, 'danger');
INSERT INTO `sys_dict_value` VALUES ('1911974545500987393', '1911974209927307266', '待发货', '2', 'order_status', '0', '待发货', 2, '0', '2025-04-15 10:47:09', NULL, 'system', NULL, 'info');
INSERT INTO `sys_dict_value` VALUES ('1911974577667104769', '1911974209927307266', '待收货', '3', 'order_status', '0', '待收货', 3, '0', '2025-04-15 10:47:17', NULL, 'system', NULL, 'warning');
INSERT INTO `sys_dict_value` VALUES ('1911974706469986306', '1911974209927307266', '退款中', '5', 'order_status', '0', '退款中', 5, '0', '2025-04-15 10:47:47', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1911974804155326466', '1911974209927307266', '已取消', '11', 'order_status', '0', '已取消', 11, '0', '2025-04-15 10:48:11', NULL, 'system', NULL, 'danger');
INSERT INTO `sys_dict_value` VALUES ('1915055950136483842', '1915049899597111298', '待发货', '1', 'order_item_status', '0', '待发货', 1, '0', '2025-04-23 22:51:33', '2025-04-27 22:01:15', 'system', 'system', 'primary');
INSERT INTO `sys_dict_value` VALUES ('1915056021531926530', '1915049899597111298', '已发货', '2', 'order_item_status', '0', '已发货', 2, '0', '2025-04-23 22:51:50', NULL, 'system', NULL, 'success');
INSERT INTO `sys_dict_value` VALUES ('1915056057670049794', '1915049899597111298', '售后处理中', '3', 'order_item_status', '0', '售后处理中', 3, '0', '2025-04-23 22:51:59', NULL, 'system', NULL, 'warning');
INSERT INTO `sys_dict_value` VALUES ('1915056181846614018', '1915049899597111298', '退款完成', '6', 'order_item_status', '0', '退款完成', 6, '0', '2025-04-23 22:52:28', NULL, 'system', NULL, 'success');
INSERT INTO `sys_dict_value` VALUES ('1915059719180001281', '1871381190397775873', '退款完成', '6', 'refund_status', '0', '退款完成', 6, '0', '2025-04-23 23:06:32', NULL, 'system', NULL, 'success');
INSERT INTO `sys_dict_value` VALUES ('1915059788511846402', '1871381190397775873', '退款失败', '8', 'refund_status', '0', '退款失败', 8, '0', '2025-04-23 23:06:48', '2025-04-23 23:06:56', 'system', 'system', 'danger');
INSERT INTO `sys_dict_value` VALUES ('1915429758689239042', '1915429675147091970', '仅退款', '1', 'refund_type', '0', '仅退款', 1, '0', '2025-04-24 23:36:56', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1915429822904033282', '1915429675147091970', '退货', '2', 'refund_type', '0', '退货', 2, '0', '2025-04-24 23:37:11', '2025-05-04 20:02:10', 'system', 'system', 'danger');
INSERT INTO `sys_dict_value` VALUES ('1916502673673310210', '1871381190397775873', '审核拒绝', '9', 'refund_status', '0', '审核拒绝', 9, '0', '2025-04-27 22:40:19', NULL, 'system', NULL, 'danger');
INSERT INTO `sys_dict_value` VALUES ('1917043781987741698', '1917043550726402050', '在途', '0', 'order_delivery_status', '0', '在途', 0, '0', '2025-04-29 10:30:29', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1917043829500817409', '1917043550726402050', '揽收', '1', 'order_delivery_status', '0', '揽收', 1, '0', '2025-04-29 10:30:41', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1917043861100703745', '1917043550726402050', '疑难', '2', 'order_delivery_status', '0', '疑难', 2, '0', '2025-04-29 10:30:48', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1917043894059544578', '1917043550726402050', '签收', '3', 'order_delivery_status', '0', '签收', 3, '0', '2025-04-29 10:30:56', NULL, 'system', NULL, 'success');
INSERT INTO `sys_dict_value` VALUES ('1917043937143435265', '1917043550726402050', '退签', '4', 'order_delivery_status', '0', '退签', 4, '0', '2025-04-29 10:31:06', NULL, 'system', NULL, 'info');
INSERT INTO `sys_dict_value` VALUES ('1917043975538094081', '1917043550726402050', '派件', '5', 'order_delivery_status', '0', '派件', 5, '0', '2025-04-29 10:31:15', NULL, 'system', NULL, 'success');
INSERT INTO `sys_dict_value` VALUES ('1917044010703138818', '1917043550726402050', '清关', '8', 'order_delivery_status', '0', '清关', 8, '0', '2025-04-29 10:31:24', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1917044170027970561', '1917043550726402050', '退回', '6', 'order_delivery_status', '0', '退回', 6, '0', '2025-04-29 10:32:02', NULL, 'system', NULL, 'info');
INSERT INTO `sys_dict_value` VALUES ('1917044210259734529', '1917043550726402050', '转投', '7', 'order_delivery_status', '0', '转投', 7, '0', '2025-04-29 10:32:11', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1927953289186484225', '1927953218776702978', '小程序', '0', 'pay_terminal_type', '0', '小程序', 0, '0', '2025-05-29 13:00:58', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1927953320081727490', '1927953218776702978', 'App', '1', 'pay_terminal_type', '0', 'App', 1, '0', '2025-05-29 13:01:06', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1928119897173438466', '1825786096614240258', '0元支付', '0', 'pay_type', '0', '0元支付', 0, '0', '2025-05-30 00:03:01', '2025-05-30 00:03:06', 'system', 'system', 'danger');
INSERT INTO `sys_dict_value` VALUES ('1929205321706921986', '1915049899597111298', '已完成', '8', 'order_item_status', '0', '已完成', 8, '0', '2025-06-01 23:56:06', NULL, 'system', NULL, 'success');
INSERT INTO `sys_dict_value` VALUES ('1929206209452335106', '1929205944271659009', '5分钟', '9', 'mq_delay_time_level', '0', '5分钟', 9, '0', '2025-06-01 23:59:38', '2025-06-02 00:00:04', 'system', 'system', 'primary');
INSERT INTO `sys_dict_value` VALUES ('1929206253555441666', '1929205944271659009', '10分钟', '14', 'mq_delay_time_level', '0', '10分钟', 14, '0', '2025-06-01 23:59:48', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1929206298094755842', '1929205944271659009', '20分钟', '15', 'mq_delay_time_level', '0', '20分钟', 15, '0', '2025-06-01 23:59:59', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1929206364058574849', '1929205944271659009', '30分钟', '16', 'mq_delay_time_level', '0', '30分钟', 16, '0', '2025-06-02 00:00:15', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1929206407276683265', '1929205944271659009', '1小时', '17', 'mq_delay_time_level', '0', '1小时', 17, '0', '2025-06-02 00:00:25', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('1929206449693679617', '1929205944271659009', '2小时', '18', 'mq_delay_time_level', '0', '2小时', 18, '0', '2025-06-02 00:00:35', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('2040457078937530370', '1583357541572108290', '本机', 'local', 'sys_storage_type', '0', NULL, 1, '0', '2026-04-04 23:50:52', NULL, 'system', NULL, 'primary');
INSERT INTO `sys_dict_value` VALUES ('2040457134491086849', '1583357541572108290', 'OSS', 'oss', 'sys_storage_type', '0', NULL, 2, '0', '2026-04-04 23:51:05', NULL, 'system', NULL, 'primary');

-- ----------------------------
-- Table structure for sys_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log`  (
                            `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                            `ip_addr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ip地址',
                            `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态：0.失败；1.成功；',
                            `create_time` datetime NULL DEFAULT NULL COMMENT '新增时间',
                            `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '登录地点',
                            `user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '登录用户',
                            `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '日志标题',
                            `request_method` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求方式',
                            `request_uri` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求URI',
                            `request_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '请求数据',
                            `request_time` bigint NULL DEFAULT NULL COMMENT '请求时长',
                            `ex_msg` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '异常信息',
                            `method` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作方法',
                            `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                            `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                            `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                            PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_login_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log`  (
                                  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                  `ip_addr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ip地址',
                                  `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态：0.失败；1.成功；',
                                  `user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '登录用户',
                                  `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '登录地点',
                                  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '信息',
                                  `browser` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '浏览器',
                                  `os` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作系统',
                                  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '登录日志' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_login_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_logistics_company
-- ----------------------------
DROP TABLE IF EXISTS `sys_logistics_company`;
CREATE TABLE `sys_logistics_company`  (
                                          `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                          `code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物流编码',
                                          `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '物流名称',
                                          `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态：0.正常；1.停用；',
                                          `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                          `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                          `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                          `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                          `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                          `wx_delivery_id` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信运力公司ID',
                                          PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '物流公司表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_logistics_company
-- ----------------------------
INSERT INTO `sys_logistics_company` VALUES ('1612686422580277249', 'yuantong', '圆通速递', '0', '2023-01-10 13:43:01', '2025-06-01 21:36:04', '0', NULL, 'system', 'YTO');
INSERT INTO `sys_logistics_company` VALUES ('1612686819944443905', 'yunda', '韵达快递', '0', '2023-01-10 13:44:35', '2025-06-01 21:36:04', '0', NULL, 'system', 'YUNDA');
INSERT INTO `sys_logistics_company` VALUES ('1612689015016665090', 'zhongtong', '中通快递', '0', '2023-01-10 13:53:18', '2025-06-01 21:36:05', '0', NULL, 'system', 'ZTO');
INSERT INTO `sys_logistics_company` VALUES ('1612689189508100098', 'jtexpress', '极兔速递', '0', '2023-01-10 13:54:00', '2023-01-10 13:54:00', '0', NULL, NULL, NULL);
INSERT INTO `sys_logistics_company` VALUES ('1612689250992402433', 'shunfeng', '顺丰速运', '0', '2023-01-10 13:54:14', '2025-06-01 21:36:04', '0', NULL, 'system', 'SF');
INSERT INTO `sys_logistics_company` VALUES ('1612689310660571137', 'ems', 'EMS', '0', '2023-01-10 13:54:29', '2023-01-10 13:54:29', '0', NULL, NULL, NULL);
INSERT INTO `sys_logistics_company` VALUES ('1612689351961882626', 'jd', '京东物流', '0', '2023-01-10 13:54:38', '2023-01-10 13:54:38', '0', NULL, NULL, NULL);
INSERT INTO `sys_logistics_company` VALUES ('1612689394026557441', 'debangkuaidi', '德邦快递', '0', '2023-01-10 13:54:48', '2025-06-01 21:36:02', '0', NULL, 'system', 'DB');
INSERT INTO `sys_logistics_company` VALUES ('1612689470857818113', 'huitongkuaidi', '百世快递', '0', '2023-01-10 13:55:07', '2025-06-01 21:36:02', '0', NULL, 'system', 'BEST');
INSERT INTO `sys_logistics_company` VALUES ('1612699111595687938', 'rrs', '日日顺物流', '0', '2023-01-10 14:33:25', '2023-01-10 14:33:25', '0', NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_material
-- ----------------------------
DROP TABLE IF EXISTS `sys_material`;
CREATE TABLE `sys_material`  (
                                 `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主键',
                                 `type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型1、图片；2、视频',
                                 `group_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '-1' COMMENT '分组ID  -1.未分组',
                                 `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '素材名',
                                 `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '素材链接',
                                 `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                 `update_time` datetime NULL DEFAULT NULL COMMENT '最后更新时间',
                                 `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                 `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                 `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                 `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                 `file_size` int NULL DEFAULT NULL COMMENT '素材大小'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '素材' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_material
-- ----------------------------

-- ----------------------------
-- Table structure for sys_material_group
-- ----------------------------
DROP TABLE IF EXISTS `sys_material_group`;
CREATE TABLE `sys_material_group`  (
                                       `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                       `name` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组名称',
                                       `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                       `update_time` datetime NULL DEFAULT NULL COMMENT '最后更新时间',
                                       `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
                                       `type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '分组类型：1.图片；2.视频',
                                       `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                       `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                       `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                       `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                       PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '素材分组' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_material_group
-- ----------------------------
INSERT INTO `sys_material_group` VALUES ('1923732594174885889', '华为', '2025-05-17 21:29:26', NULL, 0, '1', '1590229800633634816', 'system', NULL, '0');
INSERT INTO `sys_material_group` VALUES ('1925780687978283010', '微信二维码', '2025-05-23 13:07:50', '2025-05-26 10:30:15', 0, '1', '1590229800633634816', 'system', 'system', '0');
INSERT INTO `sys_material_group` VALUES ('1932710590376075266', '123132', '2025-06-11 16:04:47', '2025-06-11 16:04:55', 0, '1', '1590229800633634816', 'system', 'system', '1');
INSERT INTO `sys_material_group` VALUES ('1936377069242032129', '1', '2025-06-21 18:54:04', NULL, 0, '1', '1590229800633634816', 'system', NULL, '0');

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
                             `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                             `name` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
                             `permission` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '菜单权限',
                             `path` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'URL',
                             `redirect` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '重定向url',
                             `parent_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '父菜单ID',
                             `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
                             `component` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '页面地址',
                             `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
                             `type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型: 0.菜单; 1.按钮;',
                             `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                             `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                             `outer_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '外链状态：0.否；1.是；',
                             `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                             `application_key` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用id',
                             `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                             `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '菜单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('100001', '菜单管理新增', 'upms:sysmenu:add', NULL, NULL, '10002', NULL, '', 0, '1', '2021-12-01 09:44:37', '2022-11-11 17:34:43', '0', '0', 'sys_key', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('10001', '系统设置', NULL, '/system', '/system/user', '0', 'carbon:settings', '', 80, '0', '2021-11-26 11:38:57', '2025-05-13 21:58:52', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('10002', '菜单管理', NULL, '/platform/menu', NULL, '1881217134381326338', 'carbon:menu', 'upms/menu/index', 3, '0', '2021-11-26 11:37:40', '2025-05-13 22:03:11', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1491684226094198786', '角色管理', NULL, '/system/role', NULL, '10001', 'carbon:user-role', 'upms/role/index', 2, '0', '2022-02-10 16:03:27', '2025-05-20 22:54:06', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1491690996678021121', '角色管理列表', 'upms:sysrole:page', NULL, NULL, '1491684226094198786', '', NULL, 1, '1', '2022-02-10 16:30:21', '2022-11-11 17:34:48', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1491752531735490561', '用户管理', NULL, '/system/user', NULL, '10001', 'carbon:user', 'upms/user/index', 1, '0', '2022-02-10 20:34:54', '2025-05-20 22:53:58', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1491756888363307009', '用户列表', 'upms:sysuser:page', NULL, NULL, '1491752531735490561', '', NULL, 1, '1', '2022-02-10 20:52:13', '2022-11-10 17:04:12', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1491757020773289986', '用户查询', 'upms:sysuser:get', NULL, NULL, '1491752531735490561', '', NULL, 1, '1', '2022-02-10 20:52:44', '2022-11-10 17:04:12', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1491757382771085313', '用户新增', 'upms:sysuser:add', NULL, NULL, '1491752531735490561', '', NULL, 1, '1', '2022-02-10 20:54:11', '2022-11-10 17:04:13', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1491948958826921986', '文件存储配置', NULL, '/system/storage-config', NULL, '10001', 'carbon:settings-edit', 'upms/storage-config/index', 15, '0', '2022-02-11 09:35:25', '2025-05-20 22:56:21', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1491949315883827201', '文件存储配置查询', 'upms:storageconfig:get', NULL, NULL, '1491948958826921986', '', NULL, 1, '1', '2022-02-11 09:36:50', '2022-11-11 17:34:51', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1491969633293729794', '文件存储配置修改', 'upms:storageconfig:edit', NULL, NULL, '1491948958826921986', '', NULL, 1, '1', '2022-02-11 10:57:34', '2022-11-11 17:34:53', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1491973212968632322', '素材中心', NULL, '/system/material', NULL, '10001', 'carbon:image', 'upms/material/index', 50, '0', '2022-02-11 11:11:47', '2025-05-20 22:56:38', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1493578977630121986', '素材中心列表', 'upms:material:page', NULL, NULL, '1491973212968632322', NULL, NULL, 1, '1', '2022-02-15 21:32:38', '2024-08-21 21:23:37', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493578977978249218', '素材中心查询', 'upms:material:get', NULL, NULL, '1491973212968632322', NULL, NULL, 1, '1', '2022-02-15 21:32:38', '2024-08-21 21:24:11', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493578978313793537', '素材中心添加', 'upms:material:add', NULL, NULL, '1491973212968632322', NULL, NULL, 1, '1', '2022-02-15 21:32:38', '2024-08-21 21:33:54', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493578978649337858', '素材中心修改', 'upms:material:edit', NULL, NULL, '1491973212968632322', NULL, NULL, 1, '1', '2022-02-15 21:32:38', '2024-08-21 21:34:23', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493587910381957121', '商品管理查询', 'product:goodsspu:get', NULL, NULL, '1532620395988029442', NULL, NULL, 1, '1', '2022-02-15 22:08:02', '2024-10-15 10:38:17', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493587910721695745', '商品管理添加', 'product:goodsspu:add', NULL, NULL, '1532620395988029442', NULL, NULL, 1, '1', '2022-02-15 22:08:02', '2024-10-15 10:38:24', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493587911057240066', '商品管理修改', 'product:goodsspu:edit', NULL, NULL, '1532620395988029442', NULL, NULL, 1, '1', '2022-02-15 22:08:02', '2024-10-15 10:38:30', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493596733666652162', '素材分组查询', 'upms:materialgroup:get', NULL, NULL, '1491973212968632322', '', NULL, 2, '1', '2022-02-15 22:43:05', '2024-08-21 21:36:48', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493766204259942401', '素材分组新增', 'upms:materialgroup:add', NULL, NULL, '1491973212968632322', '', NULL, 2, '1', '2022-02-16 09:56:27', '2024-08-21 21:36:55', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1493836091183411202', '角色管理查询', 'upms:sysrole:get', NULL, NULL, '1491684226094198786', '', NULL, 1, '1', '2022-02-16 14:34:10', '2022-11-11 17:34:56', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1493836209106268161', '角色管理新增', 'upms:sysrole:add', NULL, NULL, '1491684226094198786', '', NULL, 1, '1', '2022-02-16 14:34:38', '2022-11-11 17:34:57', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1493836280589791233', '角色管理修改', 'upms:sysrole:edit', NULL, NULL, '1491684226094198786', '', NULL, 1, '1', '2022-02-16 14:34:55', '2022-11-11 17:34:58', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1493841029473042434', '角色管理删除', 'upms:sysrole:del', NULL, NULL, '1491684226094198786', '', NULL, 1, '1', '2022-02-16 14:53:47', '2022-11-11 17:35:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1493884088730529793', '日志管理', NULL, '/system/log', '/system/log/loginlog', '10001', 'carbon:catalog', '', 10, '0', '2022-02-16 17:44:53', '2025-05-15 11:34:34', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1493884379760701442', '登录日志', NULL, '/system/log/login', NULL, '1493884088730529793', 'carbon:catalog', 'upms/login-log/index', 1, '0', '2022-02-16 17:46:02', '2025-05-19 17:08:14', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1494151991157673985', '操作日志', NULL, '/system/log/log', NULL, '1493884088730529793', 'carbon:catalog', 'upms/log/index', 20, '0', '2022-02-17 11:29:28', '2025-05-15 11:35:18', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1494153372996255746', '操作日志列表', 'upms:syslog:page', NULL, NULL, '1494151991157673985', NULL, NULL, 1, '1', '2022-02-17 11:34:57', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494153373352771586', '操作日志查询', 'upms:syslog:get', NULL, NULL, '1494151991157673985', NULL, NULL, 1, '1', '2022-02-17 11:34:58', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494153373696704514', '操作日志新增', 'upms:syslog:add', NULL, NULL, '1494151991157673985', NULL, NULL, 1, '1', '2022-02-17 11:34:58', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494153374053220354', '操作日志修改', 'upms:syslog:edit', NULL, NULL, '1494151991157673985', NULL, NULL, 1, '1', '2022-02-17 11:34:58', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494153374401347585', '操作日志删除', 'upms:syslog:del', NULL, NULL, '1494151991157673985', NULL, NULL, 1, '1', '2022-02-17 11:34:58', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494192758630694913', '登录日志列表', 'upms:sysloginlog:page', NULL, NULL, '1493884379760701442', NULL, NULL, 1, '1', '2022-02-17 14:11:28', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494192758974627842', '登录日志查询', 'upms:sysloginlog:get', NULL, NULL, '1493884379760701442', NULL, NULL, 1, '1', '2022-02-17 14:11:28', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494192759318560770', '登录日志新增', 'upms:sysloginlog:add', NULL, NULL, '1493884379760701442', NULL, NULL, 1, '1', '2022-02-17 14:11:28', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494192759662493698', '登录日志修改', 'upms:sysloginlog:edit', NULL, NULL, '1493884379760701442', NULL, NULL, 1, '1', '2022-02-17 14:11:28', '2022-11-11 17:35:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494192760010620930', '登录日志删除', 'upms:sysloginlog:del', NULL, NULL, '1493884379760701442', NULL, NULL, 1, '1', '2022-02-17 14:11:28', '2022-11-11 17:35:08', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494216988311183361', '部门管理', NULL, '/system/dept', NULL, '10001', 'carbon:model-builder', 'upms/dept/index', 4, '0', '2022-02-17 15:47:45', '2025-05-20 22:55:40', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1494217080162246658', '部门管理列表', 'upms:sysdept:page', NULL, NULL, '1494216988311183361', NULL, NULL, 1, '1', '2022-02-17 15:48:06', '2022-11-11 17:35:08', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494217080510373890', '部门管理查询', 'upms:sysdept:get', NULL, NULL, '1494216988311183361', NULL, NULL, 1, '1', '2022-02-17 15:48:06', '2022-11-11 17:35:08', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494217080858501121', '部门管理新增', 'upms:sysdept:add', NULL, NULL, '1494216988311183361', NULL, NULL, 1, '1', '2022-02-17 15:48:06', '2022-11-11 17:35:08', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494217081206628354', '部门管理修改', 'upms:sysdept:edit', NULL, NULL, '1494216988311183361', NULL, NULL, 1, '1', '2022-02-17 15:48:06', '2022-11-11 17:35:08', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494217081558949890', '部门管理删除', 'upms:sysdept:del', NULL, NULL, '1494216988311183361', NULL, NULL, 1, '1', '2022-02-17 15:48:07', '2022-11-11 17:35:08', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1494514119857180674', '用户修改', 'upms:sysuser:edit', NULL, NULL, '1491752531735490561', '', NULL, 1, '1', '2022-02-18 11:28:25', '2022-11-10 17:04:16', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1495687621054353410', '菜单管理修改', 'upms:sysmenu:edit', NULL, NULL, '10002', '', NULL, 1, '1', '2022-02-21 17:11:31', '2022-11-11 17:35:10', '0', '0', 'sys_key', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1495687858816864257', '菜单管理删除', 'upms:sysmenu:del', NULL, NULL, '10002', '', NULL, 1, '1', '2022-02-21 17:12:28', '2022-11-11 17:35:11', '0', '0', 'sys_key', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1496012487833960450', '商品管理删除', 'product:goodsspu:del', NULL, NULL, '1532620395988029442', '', NULL, 1, '1', '2022-02-22 14:42:24', '2024-10-15 10:38:38', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1496327483721789441', '商品类目', NULL, '/product/goods-category', NULL, '1779386487675092994', 'carbon:category', 'product/goods-category/index', 5, '0', '2022-02-23 11:34:04', '2025-06-23 22:19:08', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1496327552973942785', '商品类目列表', 'product:goodscategory:page', NULL, NULL, '1496327483721789441', NULL, NULL, 1, '1', '2022-02-23 11:34:20', '2024-10-15 10:41:07', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1496327553334652930', '商品类目查询', 'product:goodscategory:get', NULL, NULL, '1496327483721789441', NULL, NULL, 1, '1', '2022-02-23 11:34:21', '2024-10-15 10:41:21', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1496327553699557377', '商品类目新增', 'product:goodscategory:add', NULL, NULL, '1496327483721789441', NULL, NULL, 1, '1', '2022-02-23 11:34:21', '2024-10-15 10:41:28', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1496327554068656130', '商品类目修改', 'product:goodscategory:edit', NULL, NULL, '1496327483721789441', NULL, NULL, 1, '1', '2022-02-23 11:34:21', '2024-10-15 10:42:06', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1496327554433560577', '商品类目删除', 'product:goodscategory:del', NULL, NULL, '1496327483721789441', NULL, NULL, 1, '1', '2022-02-23 11:34:21', '2024-10-15 10:42:12', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1497468294740176898', '商品管理列表', 'product:goodsspu:page', NULL, NULL, '1532620395988029442', '', NULL, 1, '1', '2022-02-26 15:07:17', '2024-10-15 10:38:44', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1521469373525716994', '菜单管理列表', 'upms:sysmenu:page', NULL, NULL, '10002', '', NULL, 1, '1', '2022-05-03 20:39:03', '2022-11-11 17:35:14', '0', '0', 'sys_key', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1521496866882236418', '订单管理', NULL, '/order', '/order/orderinfo', '0', 'carbon:border-full', '', 40, '0', '2022-05-03 22:28:18', '2025-05-13 21:54:33', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1526179827628048385', '素材中心删除', 'upms:material:del', NULL, NULL, '1491973212968632322', 'icon-m-fuwenben', NULL, 1, '1', '2022-05-16 20:36:42', '2024-08-21 21:36:41', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1527471479688798209', '会员列表', NULL, '/user/user-list', NULL, '1779386898750439425', 'carbon:user-avatar', 'user/user-info/index', 1, '0', '2022-05-20 10:09:14', '2025-05-19 16:12:16', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1527471918001954818', '商城用户列表', 'user:userinfo:page', NULL, NULL, '1527471479688798209', NULL, NULL, 1, '1', '2022-05-20 10:10:58', '2024-10-13 22:23:17', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1527471918337499138', '商城用户查询', 'user:userinfo:get', NULL, NULL, '1527471479688798209', NULL, NULL, 1, '1', '2022-05-20 10:10:58', '2024-10-13 22:23:23', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1527471918694014977', '商城用户新增', 'user:userinfo:add', NULL, NULL, '1527471479688798209', NULL, NULL, 1, '1', '2022-05-20 10:10:58', '2024-10-13 22:23:28', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1527471919042142209', '商城用户修改', 'user:userinfo:edit', NULL, NULL, '1527471479688798209', NULL, NULL, 1, '1', '2022-05-20 10:10:58', '2024-10-13 22:23:35', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1527471919386075137', '商城用户删除', 'user:userinfo:del', NULL, NULL, '1527471479688798209', NULL, NULL, 1, '1', '2022-05-20 10:10:58', '2024-10-13 22:23:40', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1527835787455164418', '商城装修', NULL, '/page-design', '/theme/home', '0', 'carbon:color-palette', '', 25, '0', '2022-05-21 10:16:50', '2025-07-16 22:08:16', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1527928859455168514', '服务监控', NULL, '/platform/server', NULL, '1881217134381326338', 'carbon:manage-protection', 'upms/sys-server/index', 25, '0', '2022-05-21 16:26:40', '2025-05-19 16:21:13', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1527947365856878593', '服务监控查询', 'upms:sysserver:get', '', NULL, '1527928859455168514', '', NULL, 1, '1', '2022-05-21 17:40:14', '2025-06-15 23:20:54', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1531445896986435585', '支付配置', NULL, '/pay/config', NULL, '1670722611195203586', 'carbon:settings-edit', 'pay/config/index', 5, '0', '2022-05-31 09:22:09', '2025-05-19 16:16:59', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1531446009649635329', '支付配置列表', 'pay:payconfig:page', NULL, NULL, '1531445896986435585', NULL, NULL, 1, '1', '2022-05-31 09:22:36', '2024-10-15 10:59:58', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531446009779658754', '支付配置查询', 'pay:payconfig:get', NULL, NULL, '1531445896986435585', NULL, NULL, 1, '1', '2022-05-31 09:22:36', '2024-10-15 11:00:04', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531446009913876482', '支付配置新增', 'pay:payconfig:add', NULL, NULL, '1531445896986435585', NULL, NULL, 1, '1', '2022-05-31 09:22:36', '2024-10-15 11:00:11', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531446010039705602', '支付配置修改', 'pay:payconfig:edit', NULL, NULL, '1531445896986435585', NULL, NULL, 1, '1', '2022-05-31 09:22:36', '2024-10-15 11:00:17', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531446010169729026', '支付配置删除', 'pay:payconfig:del', NULL, NULL, '1531445896986435585', NULL, NULL, 1, '1', '2022-05-31 09:22:36', '2024-10-15 11:00:23', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531528760525074434', '商城订单', NULL, '/order/order-info', NULL, '1521496866882236418', 'carbon:order-details', 'order/order-info/index', 10, '0', '2022-05-31 14:51:23', '2025-05-19 16:15:41', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1531529196871102466', '商城退单', NULL, '/order/order-refund', NULL, '1521496866882236418', 'carbon:return', 'order/order-refund/index', 20, '0', '2022-05-31 14:53:07', '2025-05-19 16:16:15', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1531536449854517250', '商城订单列表', 'order:orderinfo:page', NULL, NULL, '1531528760525074434', '', NULL, 1, '1', '2022-05-31 15:21:57', '2024-10-15 10:49:27', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531536545572728833', '商城订单查询', 'order:orderinfo:get', NULL, NULL, '1531528760525074434', '', NULL, 1, '1', '2022-05-31 15:22:19', '2024-10-15 10:49:33', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531536746446336001', '商城订单删除', 'order:orderinfo:del', NULL, NULL, '1531528760525074434', '', NULL, 1, '1', '2022-05-31 15:23:07', '2024-10-15 10:49:39', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531536866638311426', '商城订单发货', 'order:orderinfo:deliver', NULL, NULL, '1531528760525074434', '', NULL, 1, '1', '2022-05-31 15:23:36', '2024-10-15 10:49:56', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531537172243689474', '商城退单列表', 'order:orderrefund:page', NULL, NULL, '1531529196871102466', '', NULL, 1, '1', '2022-05-31 15:24:49', '2024-10-15 10:59:31', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531537289042472961', '商城退单查询', 'order:orderrefund:get', NULL, NULL, '1531529196871102466', '', NULL, 1, '1', '2022-05-31 15:25:17', '2024-10-15 10:59:36', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1531846893999169537', '账号管理', NULL, '/social/account', NULL, '1539129183310196738', 'carbon:settings', 'user/social-account/index', 5, '0', '2022-06-01 11:55:34', '2026-04-05 13:23:03', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1531882841361915906', '三方账号列表', 'user:socialAccount:page', '', NULL, '1531846893999169537', '', NULL, 1, '1', '2022-06-01 14:18:22', '2026-04-05 13:22:24', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1532620101065543681', '商品规格', NULL, '/product/goods-specs', NULL, '1779386487675092994', 'carbon:settings', 'product/goods-specs/index', 15, '0', '2022-06-03 15:08:01', '2025-05-19 16:15:58', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1532620395988029442', '全部商品', NULL, '/product/goods/spu', '', '1779386487675092994', 'carbon:product', 'product/goods-spu/index', 10, '0', '2022-06-03 15:09:11', '2025-05-19 16:14:40', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1532620840659750914', '商品规格列表', 'product:goodsspecs:page', NULL, NULL, '1532620101065543681', '', NULL, 1, '1', '2022-06-03 15:10:57', '2024-10-15 10:38:53', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532620931885862913', '商品规格查询', 'product:goodsspecs:get', '', NULL, '1532620101065543681', '', NULL, 1, '1', '2022-06-03 15:11:19', '2024-10-15 10:39:00', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532621007333003266', '商品规格新增', 'product:goodsspecs:add', NULL, NULL, '1532620101065543681', '', NULL, 1, '1', '2022-06-03 15:11:37', '2024-10-15 10:39:07', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532621107589451778', '商品规格修改', 'product:goodsspecs:edit', NULL, NULL, '1532620101065543681', '', NULL, 1, '1', '2022-06-03 15:12:01', '2024-10-15 10:39:14', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532621196013768706', '商品规格删除', 'product:goodsspecs:del', NULL, NULL, '1532620101065543681', '', NULL, 1, '1', '2022-06-03 15:12:22', '2024-10-15 10:39:23', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532634358100430850', '商品规格值列表', 'product:goodsspecsvalue:page', NULL, NULL, '1532620101065543681', '', NULL, 2, '1', '2022-06-03 16:04:40', '2024-10-15 10:39:30', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532634435510505473', '商品规格值查询', 'product:goodsspecsvalue:get', NULL, NULL, '1532620101065543681', '', NULL, 2, '1', '2022-06-03 16:04:59', '2024-10-15 10:40:20', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532634517374930946', '商品规格值新增', 'product:goodsspecsvalue:add', NULL, NULL, '1532620101065543681', '', NULL, 2, '1', '2022-06-03 16:05:18', '2024-10-15 10:40:26', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532634586568364034', '商品规格值修改', 'product:goodsspecsvalue:edit', NULL, NULL, '1532620101065543681', '', NULL, 2, '1', '2022-06-03 16:05:35', '2024-10-15 10:40:32', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1532634687902748674', '商品规格值删除', 'product:goodsspecsvalue:del', NULL, NULL, '1532620101065543681', 'icon-ziti', NULL, 2, '1', '2022-06-03 16:05:59', '2025-04-23 22:17:05', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1535633963410956289', '物流信息查询', 'order:orderLogistics:get', NULL, NULL, '1531528760525074434', '', NULL, 1, '1', '2022-06-11 22:44:03', '2024-10-15 10:50:02', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1536174316656533505', '物流公司', NULL, '/platform/logistics-company', NULL, '1881217134381326338', 'carbon:settings-edit', 'upms/logistics-company/index', 100, '0', '2022-06-13 10:31:10', '2025-07-16 22:09:01', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1536174413406543874', '物流公司查询', 'upms:logisticscompany:get', '', NULL, '1536174316656533505', '', NULL, 1, '1', '2022-06-13 10:31:33', '2025-07-16 22:09:10', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1537048076783616001', '素材分组列表', 'upms:materialgroup:page', NULL, NULL, '1491973212968632322', '', NULL, 2, '1', '2022-06-15 20:23:13', '2024-08-21 21:37:01', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1537066492991012865', '素材分组修改', 'upms:materialgroup:edit', NULL, NULL, '1491973212968632322', '', NULL, 2, '1', '2022-06-15 21:36:23', '2024-08-21 21:37:07', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1537066557067395074', '素材分组删除', 'upms:materialgroup:del', NULL, NULL, '1491973212968632322', '', NULL, 2, '1', '2022-06-15 21:36:39', '2024-08-21 22:07:04', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1539129183310196738', '三方账号管理', NULL, '/social', '/miniapp/wxapp', '0', 'carbon:settings', '', 70, '0', '2022-06-21 14:12:46', '2026-04-05 13:22:52', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1543116535774879745', '商城退单退款', 'order:orderrefund:refund', NULL, NULL, '1531529196871102466', '', NULL, 1, '1', '2022-07-02 14:17:06', '2024-10-15 10:59:43', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1547110946242326529', '用户修改密码', 'upms:sysuser:password', NULL, NULL, '1491752531735490561', '', NULL, 1, '1', '2022-07-13 14:49:27', '2022-11-10 17:04:26', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1547111164111253505', '用户删除', 'upms:sysuser:del', NULL, NULL, '1491752531735490561', '', NULL, 1, '1', '2022-07-13 14:50:19', '2022-11-10 17:04:26', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583296344638287874', '字典管理', NULL, '/platform/dict', NULL, '1881217134381326338', 'carbon:ibm-cloud-sysdig-secure', 'upms/dict/index', 5, '0', '2022-10-21 11:17:20', '2025-06-15 23:20:06', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1583296419947016193', '字典列表', 'upms:sysdict:page', NULL, NULL, '1583296344638287874', '', NULL, 1, '1', '2022-10-21 11:17:38', '2022-11-10 17:04:30', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583296499370356738', '字典键值列表', 'upms:sysdictvalue:page', NULL, NULL, '1583296344638287874', '', NULL, 2, '1', '2022-10-21 11:17:57', '2022-11-10 17:04:30', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583346865315131394', '字典新增', 'upms:sysdict:add', NULL, NULL, '1583296344638287874', '', NULL, 1, '1', '2022-10-21 14:38:05', '2022-11-10 17:04:30', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583346942263832578', '字典修改', 'upms:sysdict:edit', NULL, NULL, '1583296344638287874', '', NULL, 1, '1', '2022-10-21 14:38:23', '2022-11-10 17:04:30', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583347000673710082', '字典删除', 'upms:sysdict:del', NULL, NULL, '1583296344638287874', '', NULL, 1, '1', '2022-10-21 14:38:37', '2022-11-10 17:04:31', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583351948518031361', '字典查询', 'upms:sysdict:get', NULL, NULL, '1583296344638287874', '', NULL, 1, '1', '2022-10-21 14:58:17', '2022-11-10 17:04:31', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583355071571648514', '字典键值查询', 'upms:sysdictvalue:get', NULL, NULL, '1583296344638287874', '', NULL, 2, '1', '2022-10-21 15:10:41', '2022-11-10 17:04:31', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583355125187436546', '字典键值新增', 'upms:sysdictvalue:add', NULL, NULL, '1583296344638287874', '', NULL, 2, '1', '2022-10-21 15:10:54', '2022-11-10 17:04:31', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583355179121991681', '字典键值修改', 'upms:sysdictvalue:edit', NULL, NULL, '1583296344638287874', '', NULL, 2, '1', '2022-10-21 15:11:07', '2022-11-10 17:04:31', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1583355228157599745', '字典键值删除', 'upms:sysdictvalue:del', NULL, NULL, '1583296344638287874', '', NULL, 2, '1', '2022-10-21 15:11:19', '2022-11-10 17:04:31', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1585191363078467586', '优惠券', NULL, '/promotion/coupon', '/promotion/coupon/info', '1779386604402573314', 'carbon:settings', '', 1, '0', '2022-10-26 16:47:27', '2025-05-13 22:19:19', '0', '0', 'app_market', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1585191589231144962', '优惠券管理', NULL, '/promotion/coupon/info', NULL, '1585191363078467586', 'carbon:settings-edit', 'promotion/coupon-info/index', 1, '0', '2022-10-26 16:48:21', '2025-05-19 16:17:53', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1585192004932808706', '优惠券列表', 'promotion:couponinfo:page', NULL, NULL, '1585191589231144962', '', NULL, 1, '1', '2022-10-26 16:50:00', '2024-10-15 11:04:26', '0', '0', 'app_market', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1585192140589182977', '优惠券查询', 'promotion:couponinfo:get', NULL, NULL, '1585191589231144962', '', NULL, 1, '1', '2022-10-26 16:50:32', '2024-10-15 11:04:34', '0', '0', 'app_market', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1585192296575348738', '优惠券新增', 'promotion:couponinfo:add', NULL, NULL, '1585191589231144962', '', NULL, 1, '1', '2022-10-26 16:51:09', '2024-10-15 11:04:40', '0', '0', 'app_market', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1585192489970511873', '优惠券修改', 'promotion:couponinfo:edit', NULL, NULL, '1585191589231144962', '', NULL, 1, '1', '2022-10-26 16:51:55', '2024-10-15 11:04:47', '0', '0', 'app_market', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1585192568882147330', '优惠券删除', 'promotion:couponinfo:del', NULL, NULL, '1585191589231144962', '', NULL, 1, '1', '2022-10-26 16:52:14', '2024-10-15 11:04:59', '0', '0', 'app_market', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1585916026725281793', '用户领券记录', NULL, '/promotion/coupon/user', NULL, '1585191363078467586', 'carbon:list', 'promotion/coupon-user/index', 10, '0', '2022-10-28 16:47:00', '2025-05-19 16:18:04', '0', '0', 'app_market', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1585916172586397697', '用户领券记录列表', 'promotion:couponuser:page', NULL, NULL, '1585916026725281793', '', NULL, 1, '1', '2022-10-28 16:47:35', '2024-10-15 11:05:07', '0', '0', 'app_market', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1590265776187023362', '租户管理', NULL, '/platform/tenant', NULL, '1881217134381326338', 'carbon:home', 'upms/tenant/index', 6, '0', '2022-11-09 16:51:21', '2025-05-13 22:05:01', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1590265951848669185', '租户列表', 'upms:systenant:page', NULL, NULL, '1590265776187023362', '', NULL, 1, '1', '2022-11-09 16:52:03', '2022-11-10 17:04:32', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590266107100831746', '租户新增', 'upms:systenant:add', NULL, NULL, '1590265776187023362', '', NULL, 1, '1', '2022-11-09 16:52:40', '2022-11-10 17:04:32', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590700851537625090', '更新角色菜单', 'upms:sysrole:update', NULL, NULL, '1491684226094198786', '', NULL, 1, '1', '2022-11-10 21:40:11', '2022-11-11 17:36:07', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590704386824192002', '租户查询', 'upms:systenant:get', NULL, NULL, '1590265776187023362', '', NULL, 1, '1', '2022-11-10 21:54:14', '2022-11-10 21:54:14', '0', '0', '', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590704512468762626', '租户修改', 'upms:systenant:edit', NULL, NULL, '1590265776187023362', '', NULL, 1, '1', '2022-11-10 21:54:44', '2022-11-10 21:54:44', '0', '0', NULL, NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590972046640115713', '租户套餐', NULL, '/platform/tenant-package', NULL, '1881217134381326338', 'carbon:settings', 'upms/tenant-package/index', 8, '0', '2022-11-11 15:37:49', '2025-05-19 16:11:12', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1590972187874914305', '租户套餐列表', 'upms:tenantpackage:page', NULL, NULL, '1590972046640115713', '', NULL, 1, '1', '2022-11-11 15:38:23', '2022-11-11 15:38:23', '0', '0', NULL, NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590972258943201281', '租户套餐查询', 'upms:tenantpackage:get', NULL, NULL, '1590972046640115713', '', NULL, 1, '1', '2022-11-11 15:38:40', '2022-11-11 15:38:40', '0', '0', NULL, NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590972321283141633', '租户套餐新增', 'upms:tenantpackage:add', NULL, NULL, '1590972046640115713', '', NULL, 1, '1', '2022-11-11 15:38:54', '2022-11-11 15:38:54', '0', '0', NULL, NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590972375171559426', '租户套餐修改', 'upms:tenantpackage:edit', NULL, NULL, '1590972046640115713', '', NULL, 1, '1', '2022-11-11 15:39:07', '2022-11-11 15:39:07', '0', '0', NULL, NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1590972439457656833', '租户删除套餐', 'upms:tenantpackage:del', NULL, NULL, '1590972046640115713', '', NULL, 1, '1', '2022-11-11 15:39:23', '2022-11-11 15:39:23', '0', '0', NULL, NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1600477837933047810', '微页面', '', '/promotion/page-design', '', '1527835787455164418', 'carbon:page-number', 'promotion/page-design/index', 10, '0', '2022-12-07 21:10:26', '2025-07-16 22:08:39', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1600478029277196290', '页面设计列表', 'promotion:pagedesign:page', '', NULL, '1600477837933047810', '', '', 1, '1', '2022-12-07 21:10:26', '2024-10-15 11:55:01', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1600478336006647809', '页面设计新增', 'promotion:pagedesign:add', '', NULL, '1600477837933047810', '', '', 1, '1', '2022-12-07 21:12:25', '2024-10-15 11:55:08', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1600478397226708994', '页面设计修改', 'promotion:pagedesign:edit', '', NULL, '1600477837933047810', '', '', 1, '1', '2022-12-07 21:12:39', '2024-10-15 11:55:15', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1600478467569381377', '页面设计删除', 'promotion:pagedesign:del', '', NULL, '1600477837933047810', '', '', 1, '1', '2022-12-07 21:12:56', '2024-10-15 11:55:22', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1600785452746854401', '页面设计查询', 'promotion:pagedesign:get', '', NULL, '1600477837933047810', '', '', 1, '1', '2022-12-08 17:32:48', '2024-10-15 11:55:28', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1605129651156598786', '在线用户', '', '/system/online-user', NULL, '10001', 'carbon:user', 'upms/online-user/index', 9, '0', '2022-12-20 17:15:05', '2025-05-20 22:56:08', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1605129783281369089', '在线用户查询', 'upms:onlineuser:get', '', NULL, '1605129651156598786', '', '', 1, '1', '2022-12-20 17:15:36', '2022-12-20 17:15:36', '', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1605130091042619393', '在线用户强退', 'upms:onlineuser:forced', '', NULL, '1605129651156598786', '', '', 1, '1', '2022-12-20 17:15:05', '2022-12-20 17:15:05', '', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('1612481861469302786', '物流公司修改', 'upms:logisticscompany:edit', '', NULL, '1536174316656533505', '', '', 1, '1', '2023-01-10 00:10:09', '2025-07-16 22:09:15', '', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1612683975455657985', '物流公司列表', 'upms:logisticscompany:page', '', NULL, '1536174316656533505', '', NULL, 1, '1', '2022-06-13 10:31:33', '2025-07-16 22:09:19', '0', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1612684125251031042', '物流公司新增', 'upms:logisticscompany:add', '', NULL, '1536174316656533505', '', '', 1, '1', '2023-01-10 00:10:09', '2025-07-16 22:09:26', '', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1612684389953556481', '物流公司删除', 'upms:logisticscompany:del', '', NULL, '1536174316656533505', '', '', 1, '1', '2023-01-10 13:34:55', '2025-07-16 22:09:30', '', '0', 'sys_key', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1615718593878704130', '三方账号查询', 'user:socialAccount:get', '', NULL, '1531846893999169537', '', '', 1, '1', '2023-01-18 22:31:48', '2026-04-05 13:22:28', '', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1615718665584525314', '三方账号新增', 'user:socialAccount:add', '', NULL, '1531846893999169537', '', '', 1, '1', '2023-01-18 22:32:05', '2026-04-05 13:22:33', '', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1615718738766741505', '三方账号修改', 'user:socialAccount:edit', '', NULL, '1531846893999169537', '', '', 1, '1', '2023-01-18 22:32:22', '2026-04-05 13:22:37', '', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1615718811491778562', '三方账号删除', 'user:socialAccount:del', '', NULL, '1531846893999169537', '', '', 1, '1', '2023-01-18 22:32:40', '2026-04-05 13:22:41', '', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1670722611195203586', '支付管理', '', '/pay', '/pay/payconfig', '0', 'carbon:settings', '', 50, '0', '2023-06-19 17:18:05', '2025-05-13 22:17:47', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1670722888228982785', '支付订单', '', '/pay/trade-order', NULL, '1670722611195203586', 'carbon:order-details', 'pay/trade-order/index', 10, '0', '2023-06-19 17:19:11', '2025-05-19 16:16:51', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1670723480636674049', '支付退单', '', '/pay/refund-order', NULL, '1670722611195203586', 'carbon:order-details', 'pay/refund-order/index', 15, '0', '2023-06-19 17:19:11', '2025-05-19 16:17:19', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1670724676428550146', '支付订单列表', 'pay:paytradeorder:page', '', NULL, '1670722888228982785', '', '', 1, '1', '2023-06-19 17:19:11', '2024-10-15 11:01:02', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1670725150502342658', '支付订单详情', 'pay:paytradeorder:get', '', NULL, '1670722888228982785', '', '', 1, '1', '2023-06-19 17:19:11', '2024-10-15 11:01:10', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1670725580288479233', '支付退单列表', 'pay:payrefundorder:page', '', NULL, '1670723480636674049', '', '', 1, '1', '2023-06-19 17:19:11', '2024-10-15 11:02:06', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1670725673255227394', '支付退单详情', 'pay:payrefundorder:get', '', NULL, '1670723480636674049', '', '', 1, '1', '2023-06-19 17:19:11', '2024-10-15 11:02:13', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1670732624412332034', '回调记录', '', '/pay/notify-record', NULL, '1670722611195203586', 'carbon:list', 'pay/notify-record/index', 20, '0', '2023-06-19 17:57:53', '2025-05-19 16:17:33', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1670732985965531137', '回调记录列表', 'pay:paynotifyrecord:page', '', NULL, '1670732624412332034', '', '', 1, '1', '2023-06-19 17:57:53', '2024-10-15 11:03:08', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1671121808444674049', '回调记录查询', 'pay:paynotifyrecord:get', '', NULL, '1670732624412332034', '', '', 1, '1', '2023-06-19 17:57:53', '2024-10-15 11:03:14', '', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1681556362827534338', '商城订单取消', 'order:orderinfo:cancel', '', NULL, '1531528760525074434', '', '', 1, '1', '2023-07-19 14:47:33', '2024-10-15 10:50:11', '0', '0', 'app_base', NULL, 'admin');
INSERT INTO `sys_menu` VALUES ('1779386487675092994', '商品管理', '', '/product', '/product/goods/spu', '0', 'carbon:shopping-bag', '', 30, '0', '2024-04-14 13:49:32', '2025-05-13 21:54:21', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1779386604402573314', '营销管理', '', '/promotion', '/promotion/coupon/info', '0', 'carbon:bookmark', '', 60, '0', '2024-04-14 13:50:00', '2025-05-13 21:55:49', '0', '0', 'app_market', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1779386898750439425', '会员管理', '', '/user', '/user/userinfo', '0', 'carbon:user', '', 10, '0', '2024-04-14 13:51:10', '2025-05-13 21:53:04', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('1881217134381326338', '平台管理', NULL, '/platform', '/platform/menu', '0', 'carbon:platforms', '', 5, '0', '2025-01-20 13:48:11', '2025-05-13 21:52:24', '0', '0', 'sys_key', 'lijx', 'system');
INSERT INTO `sys_menu` VALUES ('1925807685068963841', '商品评价', NULL, '/product/appraise', NULL, '1779386487675092994', 'carbon:add-comment', '/product/appraise/index', 70, '0', '2025-05-23 14:55:07', '2025-05-23 15:10:16', '0', '0', 'app_base', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1925809318075088898', '商品评价分页查询', 'product:appraise:page', NULL, NULL, '1925807685068963841', NULL, NULL, 1, '1', '2025-05-23 15:01:36', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1925914794987241473', '通过id查询商品评价', 'product:appraise:get', NULL, NULL, '1925807685068963841', NULL, NULL, 1, '1', '2025-05-23 22:00:44', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1925914842571620353', '商品评价新增', 'product:appraise:add', NULL, NULL, '1925807685068963841', NULL, NULL, 1, '1', '2025-05-23 22:00:55', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1925914891900829698', '商品评价修改', 'product:appraise:edit', NULL, NULL, '1925807685068963841', NULL, NULL, 1, '1', '2025-05-23 22:01:07', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1925914979066855425', '商品评价删除', 'product:appraise:del', NULL, NULL, '1925807685068963841', NULL, NULL, 1, '1', '2025-05-23 22:01:27', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1925925897117028353', '回复商品评价', 'product:appraise:reply', NULL, NULL, '1925807685068963841', NULL, NULL, 1, '1', '2025-05-23 22:44:51', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1927376759282610177', '订单配置', NULL, '/order/config', NULL, '1521496866882236418', 'carbon:settings-edit', '/order/config/index', 5, '0', '2025-05-27 22:50:03', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1927383125309149185', '订单配置列表', 'order:config:page', NULL, NULL, '1927376759282610177', NULL, NULL, 1, '1', '2025-05-27 23:15:21', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1927383173250043906', '订单配置查询', 'order:config:get', NULL, NULL, '1927376759282610177', NULL, NULL, 1, '1', '2025-05-27 23:15:32', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1927383226031165442', '订单配置新增', 'order:config:add', NULL, NULL, '1927376759282610177', NULL, NULL, 1, '1', '2025-05-27 23:15:45', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1927383271732301826', '订单配置修改', 'order:config:edit', NULL, NULL, '1927376759282610177', NULL, NULL, 1, '1', '2025-05-27 23:15:56', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1927383317672513537', '订单配置删除', 'order:config:del', NULL, NULL, '1927376759282610177', NULL, NULL, 1, '1', '2025-05-27 23:16:07', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1928789844748812290', '文件存储配置列表', 'upms:storageconfig:page', NULL, NULL, '1491948958826921986', NULL, NULL, 1, '1', '2025-05-31 20:25:09', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1928789932476874754', '文件存储配置新增', 'upms:storageconfig:add', NULL, NULL, '1491948958826921986', NULL, NULL, 1, '1', '2025-05-31 20:25:30', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1928790012067987457', '文件存储配置删除', 'upms:storageconfig:del', NULL, NULL, '1491948958826921986', NULL, NULL, 1, '1', '2025-05-31 20:25:49', NULL, '0', '0', 'app_base', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1986003264361078786', '代码生成', NULL, '/platform/generator', NULL, '1881217134381326338', 'carbon:ibm-cloud-code-engine', NULL, 160, '0', '2025-11-05 17:30:51', NULL, '0', '0', 'sys_key', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1986003505764245505', '数据源管理', NULL, '/datasource', NULL, '1986003264361078786', 'carbon:settings', 'gen/datasource/index', 10, '0', '2025-11-05 17:31:49', '2025-11-05 18:00:40', '0', '0', 'sys_key', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1986003578032103426', '数据源列表', 'gen:datasource:page', NULL, NULL, '1986003505764245505', NULL, NULL, 1, '1', '2025-11-05 17:32:06', NULL, '0', '0', 'sys_key', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1986003636857217026', '通过id查询数据源', 'gen:datasource:get', NULL, NULL, '1986003505764245505', NULL, NULL, 1, '1', '2025-11-05 17:32:20', NULL, '0', '0', 'sys_key', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1986003694830886913', '数据源新增', 'gen:datasource:add', NULL, NULL, '1986003505764245505', NULL, NULL, 1, '1', '2025-11-05 17:32:34', NULL, '0', '0', 'sys_key', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1986003761939750914', '数据源修改', 'gen:datasource:edit', NULL, NULL, '1986003505764245505', NULL, NULL, 1, '1', '2025-11-05 17:32:50', NULL, '0', '0', 'sys_key', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1986003823453413377', '数据源删除', 'gen:datasource:del', NULL, NULL, '1986003505764245505', NULL, NULL, 1, '1', '2025-11-05 17:33:04', NULL, '0', '0', 'sys_key', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1986010716532064257', '数据表管理', NULL, '/gen-table', NULL, '1986003264361078786', 'carbon:list', 'gen/gen-table/index', 20, '0', '2025-11-05 18:00:28', NULL, '0', '0', 'sys_key', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1986060144760565762', '数据表列表', 'gen:gen-table:page', NULL, NULL, '1986010716532064257', NULL, NULL, 1, '1', '2025-11-05 21:16:52', NULL, '0', '0', 'sys_key', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('2040661035425329153', '三方用户', NULL, '/social/user', NULL, '1539129183310196738', 'carbon:user', 'user/social-user/index', 20, '0', '2026-04-05 13:21:19', '2026-04-05 13:23:11', '0', '0', 'app_base', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('2040661118854230018', '三方用户列表', 'user:socialUser:page', '', NULL, '2040661035425329153', NULL, NULL, 1, '1', '2026-04-05 13:21:39', '2026-04-05 13:22:48', '0', '0', 'app_base', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('999999999999', '菜单管理查询', 'upms:sysmenu:get', '', NULL, '10002', NULL, NULL, 1, '1', '2022-02-21 16:11:30', '2025-06-15 23:12:33', '0', '0', 'sys_key', NULL, 'system');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
                             `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                             `role_name` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
                             `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
                             `role_desc` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '角色描述',
                             `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                             `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                             `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                             `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                             `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                             `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户角色表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES ('1', '超级管理员', 'ROLE_ADMIN', '系统管理员拥有全部权限', '2021-11-26 11:34:48', '2022-11-09 14:29:09', '0', '1590229800633634816', 'admin', NULL);
INSERT INTO `sys_role` VALUES ('1881232177484574722', '系统管理员', 'ROLE_ADMIN', NULL, '2025-01-20 14:47:57', NULL, '0', '1881232176465358849', 'lijx', NULL);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
                                  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                  `role_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
                                  `menu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单ID',
                                  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色关联菜单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES ('1881269776009961474', '1881232177484574722', '1536174316656533505', '2025-01-20 17:17:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269776630718466', '1881232177484574722', '10001', '2025-01-20 17:17:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269777142423554', '1881232177484574722', '1539129183310196738', '2025-01-20 17:17:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269777645740034', '1881232177484574722', '1779386604402573314', '2025-01-20 17:17:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269778157445121', '1881232177484574722', '1496327802522447873', '2025-01-20 17:17:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269778719481857', '1881232177484574722', '1491973212968632322', '2025-01-20 17:17:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269779260547073', '1881232177484574722', '1670722611195203586', '2025-01-20 17:17:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269779784835074', '1881232177484574722', '1521496866882236418', '2025-01-20 17:17:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269780317511681', '1881232177484574722', '1779386487675092994', '2025-01-20 17:17:23', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269780845993986', '1881232177484574722', '1531846396944785409', '2025-01-20 17:17:23', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269781349310465', '1881232177484574722', '1531529196871102466', '2025-01-20 17:17:23', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269781840044034', '1881232177484574722', '1532620101065543681', '2025-01-20 17:17:23', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269782351749122', '1881232177484574722', '1779386782480138242', '2025-01-20 17:17:23', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269782901202945', '1881232177484574722', '1788375299184926722', '2025-01-20 17:17:23', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269783467433985', '1881232177484574722', '1670732624412332034', '2025-01-20 17:17:23', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269783995916289', '1881232177484574722', '1494151991157673985', '2025-01-20 17:17:23', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269784524398593', '1881232177484574722', '1670723480636674049', '2025-01-20 17:17:24', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269785442951170', '1881232177484574722', '1491948958826921986', '2025-01-20 17:17:24', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269786097262593', '1881232177484574722', '1532620395988029442', '2025-01-20 17:17:24', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269786738991106', '1881232177484574722', '1496327483721789441', '2025-01-20 17:17:24', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269787380719618', '1881232177484574722', '1531528760525074434', '2025-01-20 17:17:24', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269788056002561', '1881232177484574722', '1585916026725281793', '2025-01-20 17:17:24', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269788563513345', '1881232177484574722', '1670722888228982785', '2025-01-20 17:17:25', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269789201047553', '1881232177484574722', '1527835787455164418', '2025-01-20 17:17:25', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269789746307074', '1881232177484574722', '1779386898750439425', '2025-01-20 17:17:25', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269790299955201', '1881232177484574722', '1531846725585281025', '2025-01-20 17:17:25', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269791012986882', '1881232177484574722', '1493884088730529793', '2025-01-20 17:17:25', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269792099311617', '1881232177484574722', '1605129651156598786', '2025-01-20 17:17:25', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269792904617985', '1881232177484574722', '1864964797636804609', '2025-01-20 17:17:26', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269793827364865', '1881232177484574722', '1867574302526791682', '2025-01-20 17:17:26', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269794620088321', '1881232177484574722', '1868158971727056897', '2025-01-20 17:17:26', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269795266011137', '1881232177484574722', '1583296344638287874', '2025-01-20 17:17:26', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269796037763074', '1881232177484574722', '1792432863812636673', '2025-01-20 17:17:26', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269796729823233', '1881232177484574722', '1531846893999169537', '2025-01-20 17:17:27', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269797442854913', '1881232177484574722', '1531445896986435585', '2025-01-20 17:17:27', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269798000697345', '1881232177484574722', '1494216988311183361', '2025-01-20 17:17:27', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269798747283457', '1881232177484574722', '1532634687902748674', '2025-01-20 17:17:27', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269799456120833', '1881232177484574722', '1579402420152627202', '2025-01-20 17:17:27', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269799967825922', '1881232177484574722', '1491684226094198786', '2025-01-20 17:17:27', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269800500502529', '1881232177484574722', '1532634435510505473', '2025-01-20 17:17:27', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269801012207617', '1881232177484574722', '1537066492991012865', '2025-01-20 17:17:28', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269801536495617', '1881232177484574722', '1537048076783616001', '2025-01-20 17:17:28', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269802148864001', '1881232177484574722', '1493766204259942401', '2025-01-20 17:17:28', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269802647986178', '1881232177484574722', '1532634586568364034', '2025-01-20 17:17:28', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269803189051394', '1881232177484574722', '1493596733666652162', '2025-01-20 17:17:28', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269803788836866', '1881232177484574722', '1532634517374930946', '2025-01-20 17:17:28', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269804359262209', '1881232177484574722', '1532634358100430850', '2025-01-20 17:17:28', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269804992602114', '1881232177484574722', '1537066557067395074', '2025-01-20 17:17:28', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269805638524930', '1881232177484574722', '1871099260888489985', '2025-01-20 17:17:29', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269806376722434', '1881232177484574722', '1615718811491778562', '2025-01-20 17:17:29', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269806913593346', '1881232177484574722', '1494153373696704514', '2025-01-20 17:17:29', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269807463047169', '1881232177484574722', '1788375651716177922', '2025-01-20 17:17:29', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269808096387073', '1881232177484574722', '1527471919042142209', '2025-01-20 17:17:29', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269808654229506', '1881232177484574722', '1491757020773289986', '2025-01-20 17:17:29', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269809199489026', '1881232177484574722', '1543116535774879745', '2025-01-20 17:17:29', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269809790885889', '1881232177484574722', '1788196733688225793', '2025-01-20 17:17:30', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269810701049858', '1881232177484574722', '1585191589231144962', '2025-01-20 17:17:30', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269811598630913', '1881232177484574722', '1864965476786896897', '2025-01-20 17:17:30', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269812206804994', '1881232177484574722', '1527836010248204290', '2025-01-20 17:17:30', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269812835950593', '1881232177484574722', '1585192296575348738', '2025-01-20 17:17:30', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269813590925314', '1881232177484574722', '1494192760010620930', '2025-01-20 17:17:31', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269814178127874', '1881232177484574722', '1788375917437919234', '2025-01-20 17:17:31', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269814836633602', '1881232177484574722', '1788375826039840770', '2025-01-20 17:17:31', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269815436419073', '1881232177484574722', '999999999999', '2025-01-20 17:17:31', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269815948124161', '1881232177484574722', '1670725580288479233', '2025-01-20 17:17:31', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269816501772289', '1881232177484574722', '1494192758630694913', '2025-01-20 17:17:31', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269816996700161', '1881232177484574722', '1585192140589182977', '2025-01-20 17:17:31', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269817483239425', '1881232177484574722', '1496327552973942785', '2025-01-20 17:17:31', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269817973972993', '1881232177484574722', '1600785452746854401', '2025-01-20 17:17:32', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269818510843906', '1881232177484574722', '1867574970650058754', '2025-01-20 17:17:32', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269819060297729', '1881232177484574722', '1491969633293729794', '2025-01-20 17:17:32', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269819572002818', '1881232177484574722', '1864965419459149826', '2025-01-20 17:17:32', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269820087902210', '1881232177484574722', '1497468294740176898', '2025-01-20 17:17:32', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269820599607297', '1881232177484574722', '1792433334379991041', '2025-01-20 17:17:32', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269821115506690', '1881232177484574722', '1494192759318560770', '2025-01-20 17:17:32', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269821618823170', '1881232177484574722', '1681556362827534338', '2025-01-20 17:17:32', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269822201831426', '1881232177484574722', '1493587910721695745', '2025-01-20 17:17:33', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269822751285250', '1881232177484574722', '1536174413406543874', '2025-01-20 17:17:33', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269823271378946', '1881232177484574722', '1496327862068981762', '2025-01-20 17:17:33', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269823862775809', '1881232177484574722', '1527836010944458754', '2025-01-20 17:17:33', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269824378675201', '1881232177484574722', '1864965367609163777', '2025-01-20 17:17:33', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269824928129026', '1881232177484574722', '1527836010596331522', '2025-01-20 17:17:33', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269825477582850', '1881232177484574722', '1547111164111253505', '2025-01-20 17:17:33', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269826022842370', '1881232177484574722', '1792433080825925634', '2025-01-20 17:17:33', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269826559713282', '1881232177484574722', '1792433157640409089', '2025-01-20 17:17:34', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269827482460162', '1881232177484574722', '1864965206908600322', '2025-01-20 17:17:34', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269828140965890', '1881232177484574722', '1600478467569381377', '2025-01-20 17:17:34', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269828711391234', '1881232177484574722', '1493836209106268161', '2025-01-20 17:17:34', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269829361508354', '1881232177484574722', '1615718665584525314', '2025-01-20 17:17:34', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269829894184961', '1881232177484574722', '1494217080510373890', '2025-01-20 17:17:34', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269830414278657', '1881232177484574722', '1491949315883827201', '2025-01-20 17:17:35', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269830921789441', '1881232177484574722', '1612684125251031042', '2025-01-20 17:17:35', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269831496409089', '1881232177484574722', '1496327554433560577', '2025-01-20 17:17:35', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269832066834433', '1881232177484574722', '1494153374401347585', '2025-01-20 17:17:35', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269832654036993', '1881232177484574722', '1788196790093225986', '2025-01-20 17:17:35', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269833278988290', '1881232177484574722', '1531882841361915906', '2025-01-20 17:17:35', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269833790693378', '1881232177484574722', '1496327553699557377', '2025-01-20 17:17:35', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269834344341505', '1881232177484574722', '1493884379760701442', '2025-01-20 17:17:35', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269834847657985', '1881232177484574722', '1527471918001954818', '2025-01-20 17:17:36', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269835493580801', '1881232177484574722', '1496327553334652930', '2025-01-20 17:17:36', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269836089171969', '1881232177484574722', '1585191363078467586', '2025-01-20 17:17:36', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269836609265665', '1881232177484574722', '1670732985965531137', '2025-01-20 17:17:36', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269837175496705', '1881232177484574722', '1867575019589197825', '2025-01-20 17:17:36', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269837716561921', '1881232177484574722', '1605130091042619393', '2025-01-20 17:17:36', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269838211489794', '1881232177484574722', '1788196662338920449', '2025-01-20 17:17:36', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269838702223361', '1881232177484574722', '1527471918694014977', '2025-01-20 17:17:37', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269839306203138', '1881232177484574722', '1600478397226708994', '2025-01-20 17:17:37', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269839843074050', '1881232177484574722', '1585192004932808706', '2025-01-20 17:17:37', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269840371556354', '1881232177484574722', '1493578977630121986', '2025-01-20 17:17:37', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269840929398785', '1881232177484574722', '1494514119857180674', '2025-01-20 17:17:37', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269841504018434', '1881232177484574722', '1670724676428550146', '2025-01-20 17:17:37', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269842015723522', '1881232177484574722', '1493578978649337858', '2025-01-20 17:17:37', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269842502262786', '1881232177484574722', '1531882726605758465', '2025-01-20 17:17:37', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269843026550786', '1881232177484574722', '1494217081206628354', '2025-01-20 17:17:38', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269843668279298', '1881232177484574722', '1792433410624049154', '2025-01-20 17:17:38', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269844192567298', '1881232177484574722', '1792433241006395393', '2025-01-20 17:17:38', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269844716855298', '1881232177484574722', '1531446009649635329', '2025-01-20 17:17:38', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269845316640769', '1881232177484574722', '1615718738766741505', '2025-01-20 17:17:38', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269845853511682', '1881232177484574722', '1867574920268079105', '2025-01-20 17:17:38', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269846348439554', '1881232177484574722', '1494192759662493698', '2025-01-20 17:17:38', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269846939836417', '1881232177484574722', '1491690996678021121', '2025-01-20 17:17:38', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269847485095938', '1881232177484574722', '1792433529037639682', '2025-01-20 17:17:39', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269848068104194', '1881232177484574722', '1527947365856878593', '2025-01-20 17:17:39', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269848613363713', '1881232177484574722', '1600478029277196290', '2025-01-20 17:17:39', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269849120874498', '1881232177484574722', '1600478336006647809', '2025-01-20 17:17:39', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269849716465666', '1881232177484574722', '1585916172586397697', '2025-01-20 17:17:39', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269850391748610', '1881232177484574722', '1494153374053220354', '2025-01-20 17:17:39', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269850949591042', '1881232177484574722', '1788375557960900610', '2025-01-20 17:17:39', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269851511627777', '1881232177484574722', '1493587911057240066', '2025-01-20 17:17:40', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269852056887297', '1881232177484574722', '1605129783281369089', '2025-01-20 17:17:40', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269852610535425', '1881232177484574722', '1493836280589791233', '2025-01-20 17:17:40', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269853130629122', '1881232177484574722', '1494217081558949890', '2025-01-20 17:17:40', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269853734608898', '1881232177484574722', '1527471919386075137', '2025-01-20 17:17:40', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269854342782977', '1881232177484574722', '1532621196013768706', '2025-01-20 17:17:40', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269854879653889', '1881232177484574722', '1864965305650905090', '2025-01-20 17:17:40', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269855403941889', '1881232177484574722', '1531446009913876482', '2025-01-20 17:17:40', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269855919841282', '1881232177484574722', '1670725150502342658', '2025-01-20 17:17:41', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269856507043842', '1881232177484574722', '1867575179505426433', '2025-01-20 17:17:41', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269857006166017', '1881232177484574722', '1496327861712465921', '2025-01-20 17:17:41', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269857538842625', '1881232177484574722', '1493836091183411202', '2025-01-20 17:17:41', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269858088296449', '1881232177484574722', '1496327554068656130', '2025-01-20 17:17:41', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269858654527490', '1881232177484574722', '1491752531735490561', '2025-01-20 17:17:41', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269859275284482', '1881232177484574722', '1494153372996255746', '2025-01-20 17:17:41', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269859766018049', '1881232177484574722', '1494192758974627842', '2025-01-20 17:17:42', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269860273528833', '1881232177484574722', '1612481861469302786', '2025-01-20 17:17:42', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269860873314306', '1881232177484574722', '1496327860647112706', '2025-01-20 17:17:42', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269861435351041', '1881232177484574722', '1526179827628048385', '2025-01-20 17:17:42', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269862009970689', '1881232177484574722', '1531537289042472961', '2025-01-20 17:17:42', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269862546841602', '1881232177484574722', '1532621007333003266', '2025-01-20 17:17:42', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269863092101121', '1881232177484574722', '1531536545572728833', '2025-01-20 17:17:42', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269863717052418', '1881232177484574722', '1532621107589451778', '2025-01-20 17:17:42', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269864249729026', '1881232177484574722', '1531537172243689474', '2025-01-20 17:17:43', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269864828542977', '1881232177484574722', '1532620840659750914', '2025-01-20 17:17:43', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269865440911361', '1881232177484574722', '1671121808444674049', '2025-01-20 17:17:43', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269865961005057', '1881232177484574722', '1867574701077946369', '2025-01-20 17:17:43', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269866489487361', '1881232177484574722', '1615718593878704130', '2025-01-20 17:17:43', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269867034746881', '1881232177484574722', '1493578977978249218', '2025-01-20 17:17:43', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269867554840578', '1881232177484574722', '1585192568882147330', '2025-01-20 17:17:43', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269868066545665', '1881232177484574722', '1535633963410956289', '2025-01-20 17:17:44', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269868615999489', '1881232177484574722', '1788196840919801858', '2025-01-20 17:17:44', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269869496803329', '1881232177484574722', '1531446009779658754', '2025-01-20 17:17:44', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269870067228674', '1881232177484574722', '1527836009900077057', '2025-01-20 17:17:44', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269870721540097', '1881232177484574722', '1532620931885862913', '2025-01-20 17:17:44', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269871807864834', '1881232177484574722', '1527835963171336193', '2025-01-20 17:17:44', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269872365707265', '1881232177484574722', '1867575119333941250', '2025-01-20 17:17:45', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269872944521218', '1881232177484574722', '1531536449854517250', '2025-01-20 17:17:45', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269873464614913', '1881232177484574722', '1527471479688798209', '2025-01-20 17:17:45', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269874064400386', '1881232177484574722', '1585192489970511873', '2025-01-20 17:17:45', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269874618048514', '1881232177484574722', '1493841029473042434', '2025-01-20 17:17:45', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269875159113730', '1881232177484574722', '1788196890592944129', '2025-01-20 17:17:45', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269875675013122', '1881232177484574722', '1527471918337499138', '2025-01-20 17:17:45', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269876228661250', '1881232177484574722', '1547110946242326529', '2025-01-20 17:17:45', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269876752949249', '1881232177484574722', '1612683975455657985', '2025-01-20 17:17:46', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269877314985985', '1881232177484574722', '1491756888363307009', '2025-01-20 17:17:46', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269877897994242', '1881232177484574722', '1531446010039705602', '2025-01-20 17:17:46', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269878376144898', '1881232177484574722', '1493587910381957121', '2025-01-20 17:17:46', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269878904627201', '1881232177484574722', '1493578978313793537', '2025-01-20 17:17:46', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269879454081026', '1881232177484574722', '1590700851537625090', '2025-01-20 17:17:46', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269879961591809', '1881232177484574722', '1531536866638311426', '2025-01-20 17:17:46', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269880477491202', '1881232177484574722', '1494217080858501121', '2025-01-20 17:17:46', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269881148579842', '1881232177484574722', '1531536746446336001', '2025-01-20 17:17:47', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269881672867842', '1881232177484574722', '1496012487833960450', '2025-01-20 17:17:47', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269882201350145', '1881232177484574722', '1670725673255227394', '2025-01-20 17:17:47', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269882788552705', '1881232177484574722', '1496327861003628545', '2025-01-20 17:17:47', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269883317035009', '1881232177484574722', '1494217080162246658', '2025-01-20 17:17:47', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269883862294529', '1881232177484574722', '1612684389953556481', '2025-01-20 17:17:47', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269884428525569', '1881232177484574722', '1496327861351755778', '2025-01-20 17:17:47', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269884931842050', '1881232177484574722', '1531446010169729026', '2025-01-20 17:17:48', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269885493878786', '1881232177484574722', '1491757382771085313', '2025-01-20 17:17:48', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269886043332610', '1881232177484574722', '1788375752018763778', '2025-01-20 17:17:48', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269886596980737', '1881232177484574722', '1494153373352771586', '2025-01-20 17:17:48', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269887083520001', '1881232177484574722', '1788196420663123969', '2025-01-20 17:17:48', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269887616196610', '1881232177484574722', '1527836011300974593', '2025-01-20 17:17:48', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('1881269888136290305', '1881232177484574722', '1600477837933047810', '2025-01-20 17:17:48', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167488', '1881232177484574722', '1881217134381326338', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167489', '1881232177484574722', '10002', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167490', '1881232177484574722', '100001', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167491', '1881232177484574722', '1495687621054353410', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167492', '1881232177484574722', '1495687858816864257', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167493', '1881232177484574722', '1521469373525716994', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167494', '1881232177484574722', '999999999999', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167495', '1881232177484574722', '1583296344638287874', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167496', '1881232177484574722', '1583296419947016193', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167497', '1881232177484574722', '1583346865315131394', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167498', '1881232177484574722', '1583346942263832578', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167499', '1881232177484574722', '1583347000673710082', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167500', '1881232177484574722', '1583351948518031361', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167501', '1881232177484574722', '1583296499370356738', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167502', '1881232177484574722', '1583355071571648514', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167503', '1881232177484574722', '1583355125187436546', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167504', '1881232177484574722', '1583355179121991681', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167505', '1881232177484574722', '1583355228157599745', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167506', '1881232177484574722', '1590265776187023362', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167507', '1881232177484574722', '1590265951848669185', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167508', '1881232177484574722', '1590266107100831746', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167509', '1881232177484574722', '1590704386824192002', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167510', '1881232177484574722', '1590704512468762626', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167511', '1881232177484574722', '1590972046640115713', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167512', '1881232177484574722', '1590972187874914305', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167513', '1881232177484574722', '1590972258943201281', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167514', '1881232177484574722', '1590972321283141633', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167515', '1881232177484574722', '1590972375171559426', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167516', '1881232177484574722', '1590972439457656833', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167517', '1881232177484574722', '1527928859455168514', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167518', '1881232177484574722', '1527947365856878593', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167519', '1881232177484574722', '1536174316656533505', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167520', '1881232177484574722', '1536174413406543874', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167521', '1881232177484574722', '1612481861469302786', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167522', '1881232177484574722', '1612683975455657985', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167523', '1881232177484574722', '1612684125251031042', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167524', '1881232177484574722', '1612684389953556481', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167525', '1881232177484574722', '1986003264361078786', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167526', '1881232177484574722', '1986003505764245505', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167527', '1881232177484574722', '1986003578032103426', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167528', '1881232177484574722', '1986003636857217026', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167529', '1881232177484574722', '1986003694830886913', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167530', '1881232177484574722', '1986003761939750914', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167531', '1881232177484574722', '1986003823453413377', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167532', '1881232177484574722', '1986010716532064257', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167533', '1881232177484574722', '1986060144760565762', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167534', '1881232177484574722', '1779386898750439425', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167535', '1881232177484574722', '1527471479688798209', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167536', '1881232177484574722', '1527471918001954818', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167537', '1881232177484574722', '1527471918337499138', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167538', '1881232177484574722', '1527471918694014977', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167539', '1881232177484574722', '1527471919042142209', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167540', '1881232177484574722', '1527471919386075137', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167541', '1881232177484574722', '1527835787455164418', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167542', '1881232177484574722', '1600477837933047810', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167543', '1881232177484574722', '1600478029277196290', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167544', '1881232177484574722', '1600478336006647809', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167545', '1881232177484574722', '1600478397226708994', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167546', '1881232177484574722', '1600478467569381377', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167547', '1881232177484574722', '1600785452746854401', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167548', '1881232177484574722', '1779386487675092994', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167549', '1881232177484574722', '1496327483721789441', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167550', '1881232177484574722', '1496327552973942785', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167551', '1881232177484574722', '1496327553334652930', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167552', '1881232177484574722', '1496327553699557377', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167553', '1881232177484574722', '1496327554068656130', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167554', '1881232177484574722', '1496327554433560577', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167555', '1881232177484574722', '1532620395988029442', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167556', '1881232177484574722', '1493587910381957121', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167557', '1881232177484574722', '1493587910721695745', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167558', '1881232177484574722', '1493587911057240066', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167559', '1881232177484574722', '1496012487833960450', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167560', '1881232177484574722', '1497468294740176898', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167561', '1881232177484574722', '1532620101065543681', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167562', '1881232177484574722', '1532620840659750914', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167563', '1881232177484574722', '1532620931885862913', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167564', '1881232177484574722', '1532621007333003266', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167565', '1881232177484574722', '1532621107589451778', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167566', '1881232177484574722', '1532621196013768706', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167567', '1881232177484574722', '1532634358100430850', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167568', '1881232177484574722', '1532634435510505473', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167569', '1881232177484574722', '1532634517374930946', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167570', '1881232177484574722', '1532634586568364034', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167571', '1881232177484574722', '1532634687902748674', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167572', '1881232177484574722', '1925807685068963841', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167573', '1881232177484574722', '1925809318075088898', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167574', '1881232177484574722', '1925914794987241473', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167575', '1881232177484574722', '1925914842571620353', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167576', '1881232177484574722', '1925914891900829698', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167577', '1881232177484574722', '1925914979066855425', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167578', '1881232177484574722', '1925925897117028353', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167579', '1881232177484574722', '1521496866882236418', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167580', '1881232177484574722', '1927376759282610177', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167581', '1881232177484574722', '1927383125309149185', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167582', '1881232177484574722', '1927383173250043906', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167583', '1881232177484574722', '1927383226031165442', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167584', '1881232177484574722', '1927383271732301826', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167585', '1881232177484574722', '1927383317672513537', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167586', '1881232177484574722', '1531528760525074434', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167587', '1881232177484574722', '1531536449854517250', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167588', '1881232177484574722', '1531536545572728833', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167589', '1881232177484574722', '1531536746446336001', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167590', '1881232177484574722', '1531536866638311426', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167591', '1881232177484574722', '1535633963410956289', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167592', '1881232177484574722', '1681556362827534338', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167593', '1881232177484574722', '1531529196871102466', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167594', '1881232177484574722', '1531537172243689474', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167595', '1881232177484574722', '1531537289042472961', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167596', '1881232177484574722', '1543116535774879745', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167597', '1881232177484574722', '1670722611195203586', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167598', '1881232177484574722', '1531445896986435585', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167599', '1881232177484574722', '1531446009649635329', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167600', '1881232177484574722', '1531446009779658754', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167601', '1881232177484574722', '1531446009913876482', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167602', '1881232177484574722', '1531446010039705602', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167603', '1881232177484574722', '1531446010169729026', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167604', '1881232177484574722', '1670722888228982785', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167605', '1881232177484574722', '1670724676428550146', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167606', '1881232177484574722', '1670725150502342658', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167607', '1881232177484574722', '1670723480636674049', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167608', '1881232177484574722', '1670725580288479233', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167609', '1881232177484574722', '1670725673255227394', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167610', '1881232177484574722', '1670732624412332034', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167611', '1881232177484574722', '1670732985965531137', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167612', '1881232177484574722', '1671121808444674049', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167613', '1881232177484574722', '1779386604402573314', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167614', '1881232177484574722', '1585191363078467586', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167615', '1881232177484574722', '1585191589231144962', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167616', '1881232177484574722', '1585192004932808706', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167617', '1881232177484574722', '1585192140589182977', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167618', '1881232177484574722', '1585192296575348738', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167619', '1881232177484574722', '1585192489970511873', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167620', '1881232177484574722', '1585192568882147330', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167621', '1881232177484574722', '1585916026725281793', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167622', '1881232177484574722', '1585916172586397697', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167623', '1881232177484574722', '1539129183310196738', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167624', '1881232177484574722', '1531846893999169537', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167625', '1881232177484574722', '1531882841361915906', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167626', '1881232177484574722', '1615718593878704130', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167627', '1881232177484574722', '1615718665584525314', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167628', '1881232177484574722', '1615718738766741505', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167629', '1881232177484574722', '1615718811491778562', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167630', '1881232177484574722', '2040661035425329153', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167631', '1881232177484574722', '2040661118854230018', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167632', '1881232177484574722', '10001', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167633', '1881232177484574722', '1491752531735490561', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167634', '1881232177484574722', '1491756888363307009', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167635', '1881232177484574722', '1491757020773289986', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167636', '1881232177484574722', '1491757382771085313', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167637', '1881232177484574722', '1494514119857180674', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167638', '1881232177484574722', '1547110946242326529', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167639', '1881232177484574722', '1547111164111253505', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167640', '1881232177484574722', '1491684226094198786', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167641', '1881232177484574722', '1491690996678021121', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167642', '1881232177484574722', '1493836091183411202', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167643', '1881232177484574722', '1493836209106268161', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167644', '1881232177484574722', '1493836280589791233', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167645', '1881232177484574722', '1493841029473042434', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167646', '1881232177484574722', '1590700851537625090', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167647', '1881232177484574722', '1494216988311183361', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167648', '1881232177484574722', '1494217080162246658', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167649', '1881232177484574722', '1494217080510373890', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167650', '1881232177484574722', '1494217080858501121', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167651', '1881232177484574722', '1494217081206628354', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167652', '1881232177484574722', '1494217081558949890', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167653', '1881232177484574722', '1605129651156598786', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167654', '1881232177484574722', '1605129783281369089', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167655', '1881232177484574722', '1605130091042619393', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167656', '1881232177484574722', '1493884088730529793', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167657', '1881232177484574722', '1493884379760701442', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167658', '1881232177484574722', '1494192758630694913', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167659', '1881232177484574722', '1494192758974627842', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167660', '1881232177484574722', '1494192759318560770', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167661', '1881232177484574722', '1494192759662493698', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167662', '1881232177484574722', '1494192760010620930', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167663', '1881232177484574722', '1494151991157673985', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167664', '1881232177484574722', '1494153372996255746', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167665', '1881232177484574722', '1494153373352771586', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167666', '1881232177484574722', '1494153373696704514', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167667', '1881232177484574722', '1494153374053220354', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167668', '1881232177484574722', '1494153374401347585', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167669', '1881232177484574722', '1491948958826921986', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167670', '1881232177484574722', '1491949315883827201', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167671', '1881232177484574722', '1491969633293729794', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167672', '1881232177484574722', '1928789844748812290', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167673', '1881232177484574722', '1928789932476874754', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167674', '1881232177484574722', '1928790012067987457', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167675', '1881232177484574722', '1491973212968632322', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167676', '1881232177484574722', '1493578977630121986', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167677', '1881232177484574722', '1493578977978249218', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167678', '1881232177484574722', '1493578978313793537', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167679', '1881232177484574722', '1493578978649337858', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167680', '1881232177484574722', '1526179827628048385', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167681', '1881232177484574722', '1493596733666652162', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167682', '1881232177484574722', '1493766204259942401', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167683', '1881232177484574722', '1537048076783616001', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167684', '1881232177484574722', '1537066492991012865', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040666892239167685', '1881232177484574722', '1537066557067395074', '2026-04-05 13:44:35', '1881232176465358849');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016640', '1', '1779386898750439425', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016641', '1', '1527471479688798209', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016642', '1', '1527471918001954818', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016643', '1', '1527471918337499138', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016644', '1', '1527471918694014977', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016645', '1', '1527471919042142209', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016646', '1', '1527471919386075137', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016647', '1', '1527835787455164418', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016648', '1', '1600477837933047810', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016649', '1', '1600478029277196290', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016650', '1', '1600478336006647809', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016651', '1', '1600478397226708994', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016652', '1', '1600478467569381377', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016653', '1', '1600785452746854401', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016654', '1', '1779386487675092994', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016655', '1', '1496327483721789441', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016656', '1', '1496327552973942785', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016657', '1', '1496327553334652930', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016658', '1', '1496327553699557377', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016659', '1', '1496327554068656130', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016660', '1', '1496327554433560577', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016661', '1', '1532620395988029442', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016662', '1', '1493587910381957121', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016663', '1', '1493587910721695745', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016664', '1', '1493587911057240066', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016665', '1', '1496012487833960450', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016666', '1', '1497468294740176898', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016667', '1', '1532620101065543681', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016668', '1', '1532620840659750914', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016669', '1', '1532620931885862913', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016670', '1', '1532621007333003266', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016671', '1', '1532621107589451778', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016672', '1', '1532621196013768706', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016673', '1', '1532634358100430850', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016674', '1', '1532634435510505473', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016675', '1', '1532634517374930946', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016676', '1', '1532634586568364034', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016677', '1', '1532634687902748674', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016678', '1', '1925807685068963841', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016679', '1', '1925809318075088898', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615772016680', '1', '1925914794987241473', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210944', '1', '1925914842571620353', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210945', '1', '1925914891900829698', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210946', '1', '1925914979066855425', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210947', '1', '1925925897117028353', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210948', '1', '1521496866882236418', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210949', '1', '1927376759282610177', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210950', '1', '1927383125309149185', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210951', '1', '1927383173250043906', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210952', '1', '1927383226031165442', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210953', '1', '1927383271732301826', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210954', '1', '1927383317672513537', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210955', '1', '1531528760525074434', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210956', '1', '1531536449854517250', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210957', '1', '1531536545572728833', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210958', '1', '1531536746446336001', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210959', '1', '1531536866638311426', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210960', '1', '1535633963410956289', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210961', '1', '1681556362827534338', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210962', '1', '1531529196871102466', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210963', '1', '1531537172243689474', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210964', '1', '1531537289042472961', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210965', '1', '1543116535774879745', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210966', '1', '1670722611195203586', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210967', '1', '1531445896986435585', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210968', '1', '1531446009649635329', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210969', '1', '1531446009779658754', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210970', '1', '1531446009913876482', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210971', '1', '1531446010039705602', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210972', '1', '1531446010169729026', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210973', '1', '1670722888228982785', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210974', '1', '1670724676428550146', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210975', '1', '1670725150502342658', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210976', '1', '1670723480636674049', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210977', '1', '1670725580288479233', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210978', '1', '1670725673255227394', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210979', '1', '1670732624412332034', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210980', '1', '1670732985965531137', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210981', '1', '1671121808444674049', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210982', '1', '1779386604402573314', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210983', '1', '1585191363078467586', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210984', '1', '1585191589231144962', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210985', '1', '1585192004932808706', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210986', '1', '1585192140589182977', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210987', '1', '1585192296575348738', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210988', '1', '1585192489970511873', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210989', '1', '1585192568882147330', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210990', '1', '1585916026725281793', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210991', '1', '1585916172586397697', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210992', '1', '1539129183310196738', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210993', '1', '1531846893999169537', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210994', '1', '1531882841361915906', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210995', '1', '1615718593878704130', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210996', '1', '1615718665584525314', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210997', '1', '1615718738766741505', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210998', '1', '1615718811491778562', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776210999', '1', '2040661035425329153', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211000', '1', '2040661118854230018', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211001', '1', '10001', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211002', '1', '1491752531735490561', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211003', '1', '1491756888363307009', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211004', '1', '1491757020773289986', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211005', '1', '1491757382771085313', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211006', '1', '1494514119857180674', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211007', '1', '1547110946242326529', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211008', '1', '1547111164111253505', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211009', '1', '1491684226094198786', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211010', '1', '1491690996678021121', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211011', '1', '1493836091183411202', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211012', '1', '1493836209106268161', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211013', '1', '1493836280589791233', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211014', '1', '1493841029473042434', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211015', '1', '1590700851537625090', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211016', '1', '1494216988311183361', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211017', '1', '1494217080162246658', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211018', '1', '1494217080510373890', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211019', '1', '1494217080858501121', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211020', '1', '1494217081206628354', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211021', '1', '1494217081558949890', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211022', '1', '1605129651156598786', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211023', '1', '1605129783281369089', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211024', '1', '1605130091042619393', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211025', '1', '1493884088730529793', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211026', '1', '1493884379760701442', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211027', '1', '1494192758630694913', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211028', '1', '1494192758974627842', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211029', '1', '1494192759318560770', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211030', '1', '1494192759662493698', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211031', '1', '1494192760010620930', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211032', '1', '1494151991157673985', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211033', '1', '1494153372996255746', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211034', '1', '1494153373352771586', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211035', '1', '1494153373696704514', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211036', '1', '1494153374053220354', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211037', '1', '1494153374401347585', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211038', '1', '1491948958826921986', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211039', '1', '1491949315883827201', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211040', '1', '1491969633293729794', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211041', '1', '1928789844748812290', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211042', '1', '1928789932476874754', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211043', '1', '1928790012067987457', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211044', '1', '1491973212968632322', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211045', '1', '1493578977630121986', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211046', '1', '1493578977978249218', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211047', '1', '1493578978313793537', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211048', '1', '1493578978649337858', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211049', '1', '1526179827628048385', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211050', '1', '1493596733666652162', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211051', '1', '1493766204259942401', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211052', '1', '1537048076783616001', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211053', '1', '1537066492991012865', '2026-04-05 14:03:22', '1590229800633634816');
INSERT INTO `sys_role_menu` VALUES ('2040671615776211054', '1', '1537066557067395074', '2026-04-05 14:03:22', '1590229800633634816');

-- ----------------------------
-- Table structure for sys_storage_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_storage_config`;
CREATE TABLE `sys_storage_config`  (
                                       `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                       `access_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'access_key',
                                       `access_secret` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'access_secret',
                                       `endpoint` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '地域节点',
                                       `bucket` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '域名',
                                       `type` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储类型',
                                       `create_time` datetime NULL DEFAULT NULL COMMENT '新增时间',
                                       `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                       `dir` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'material' COMMENT '指定文件夹',
                                       `is_https` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                                       `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                       `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                       `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                       `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                       `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0.正常；1.禁用；',
                                       `style_access_enabled` tinyint(1) NULL DEFAULT 0 COMMENT '是否 path-style',
                                       `domain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'domain',
                                       PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文件存储配置' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_storage_config
-- ----------------------------
INSERT INTO `sys_storage_config` VALUES ('1491967331820404738', 'xxxxxx', 'xxxxxxxxxxxxxxxxx', 'https://xxxxxxx.Aetheryn.cn', 'aryn', 'local', '2022-02-11 10:48:25', '2026-04-04 23:54:51', 'file', NULL, '1', '1590229800633634816', NULL, 'system', '0', 0, NULL);
INSERT INTO `sys_storage_config` VALUES ('2040458110761480194', 'x**xxx', 'x*************xxx', 'https://xxxxxxx.Aetheryn.cn', 'arynpro', 'local', '2026-04-04 23:54:58', '2026-04-04 23:55:09', 'file', NULL, '1', '1590229800633634816', 'system', 'system', '0', 0, NULL);
INSERT INTO `sys_storage_config` VALUES ('2040458282656641025', NULL, NULL, NULL, 'aryn', 'local', '2026-04-04 23:55:39', NULL, 'material', NULL, '0', '1590229800633634816', 'system', NULL, '0', 0, NULL);

-- ----------------------------
-- Table structure for sys_tenant
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant`  (
                               `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                               `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户名称',
                               `logo_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户logo',
                               `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户地址',
                               `site_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '官网地址',
                               `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态：0.正常；1.停用；',
                               `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
                               `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
                               `auth_begin_time` datetime NOT NULL COMMENT '授权开始时间',
                               `auth_end_time` datetime NOT NULL COMMENT '授权结束时间',
                               `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                               `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                               `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                               `package_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户套餐id',
                               `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                               `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租户管理' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant
-- ----------------------------
INSERT INTO `sys_tenant` VALUES ('1590229800633634816', '悦航购', NULL, '陕西省西安市软件园', 'www.Aetheryn.cn', '0', NULL, '17615123397', '2024-11-01 00:00:00', '2025-12-31 00:00:00', '0', '2022-11-09 17:43:41', '2024-12-19 15:36:10', '1639458123460681730', NULL, 'lijx');
INSERT INTO `sys_tenant` VALUES ('1881232176465358849', '系统租户', NULL, '陕西省西安市', 'https://www.Aetheryn.cn', '0', NULL, '17640212321', '2025-01-20 00:00:00', '2025-02-28 00:00:00', '0', '2025-01-20 14:47:57', '2025-04-23 21:29:30', '1639458123460681730', 'lijx', 'system');

-- ----------------------------
-- Table structure for sys_tenant_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_menu`;
CREATE TABLE `sys_tenant_menu`  (
                                    `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                    `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户ID',
                                    `menu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单ID',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租户分配菜单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_menu
-- ----------------------------
INSERT INTO `sys_tenant_menu` VALUES ('2040664027662692353', '1590229800633634816', '1779386898750439425', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027671080961', '1590229800633634816', '1527471479688798209', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027671080962', '1590229800633634816', '1527471918001954818', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027671080963', '1590229800633634816', '1527471918337499138', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027679469569', '1590229800633634816', '1527471918694014977', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027679469570', '1590229800633634816', '1527471919042142209', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027679469571', '1590229800633634816', '1527471919386075137', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027679469572', '1590229800633634816', '1527835787455164418', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027679469573', '1590229800633634816', '1600477837933047810', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027679469574', '1590229800633634816', '1600478029277196290', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027683663873', '1590229800633634816', '1600478336006647809', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027683663874', '1590229800633634816', '1600478397226708994', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027683663875', '1590229800633634816', '1600478467569381377', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027683663876', '1590229800633634816', '1600785452746854401', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027683663877', '1590229800633634816', '1779386487675092994', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027683663878', '1590229800633634816', '1496327483721789441', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027683663879', '1590229800633634816', '1496327552973942785', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027687858178', '1590229800633634816', '1496327553334652930', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027687858179', '1590229800633634816', '1496327553699557377', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027687858180', '1590229800633634816', '1496327554068656130', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027687858181', '1590229800633634816', '1496327554433560577', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027687858182', '1590229800633634816', '1532620395988029442', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027687858183', '1590229800633634816', '1493587910381957121', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052481', '1590229800633634816', '1493587910721695745', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052482', '1590229800633634816', '1493587911057240066', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052483', '1590229800633634816', '1496012487833960450', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052484', '1590229800633634816', '1497468294740176898', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052485', '1590229800633634816', '1532620101065543681', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052486', '1590229800633634816', '1532620840659750914', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052487', '1590229800633634816', '1532620931885862913', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052488', '1590229800633634816', '1532621007333003266', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052489', '1590229800633634816', '1532621107589451778', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052490', '1590229800633634816', '1532621196013768706', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027692052491', '1590229800633634816', '1532634358100430850', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441090', '1590229800633634816', '1532634435510505473', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441091', '1590229800633634816', '1532634517374930946', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441092', '1590229800633634816', '1532634586568364034', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441093', '1590229800633634816', '1532634687902748674', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441094', '1590229800633634816', '1925807685068963841', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441095', '1590229800633634816', '1925809318075088898', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441096', '1590229800633634816', '1925914794987241473', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441097', '1590229800633634816', '1925914842571620353', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441098', '1590229800633634816', '1925914891900829698', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441099', '1590229800633634816', '1925914979066855425', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441100', '1590229800633634816', '1925925897117028353', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441101', '1590229800633634816', '1521496866882236418', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027700441102', '1590229800633634816', '1927376759282610177', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027704635393', '1590229800633634816', '1927383125309149185', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027704635394', '1590229800633634816', '1927383173250043906', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027704635395', '1590229800633634816', '1927383226031165442', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027704635396', '1590229800633634816', '1927383271732301826', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027704635397', '1590229800633634816', '1927383317672513537', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027704635398', '1590229800633634816', '1531528760525074434', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027704635399', '1590229800633634816', '1531536449854517250', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027704635400', '1590229800633634816', '1531536545572728833', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027708829698', '1590229800633634816', '1531536746446336001', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027708829699', '1590229800633634816', '1531536866638311426', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027708829700', '1590229800633634816', '1535633963410956289', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027708829701', '1590229800633634816', '1681556362827534338', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027708829702', '1590229800633634816', '1531529196871102466', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027708829703', '1590229800633634816', '1531537172243689474', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027708829704', '1590229800633634816', '1531537289042472961', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027708829705', '1590229800633634816', '1543116535774879745', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027713024001', '1590229800633634816', '1670722611195203586', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027713024002', '1590229800633634816', '1531445896986435585', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027713024003', '1590229800633634816', '1531446009649635329', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027729801217', '1590229800633634816', '1531446009779658754', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995522', '1590229800633634816', '1531446009913876482', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995523', '1590229800633634816', '1531446010039705602', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995524', '1590229800633634816', '1531446010169729026', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995525', '1590229800633634816', '1670722888228982785', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995526', '1590229800633634816', '1670724676428550146', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995527', '1590229800633634816', '1670725150502342658', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995528', '1590229800633634816', '1670723480636674049', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995529', '1590229800633634816', '1670725580288479233', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027733995530', '1590229800633634816', '1670725673255227394', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027738189825', '1590229800633634816', '1670732624412332034', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027738189826', '1590229800633634816', '1670732985965531137', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027738189827', '1590229800633634816', '1671121808444674049', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027738189828', '1590229800633634816', '1779386604402573314', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027738189829', '1590229800633634816', '1585191363078467586', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027738189830', '1590229800633634816', '1585191589231144962', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027738189831', '1590229800633634816', '1585192004932808706', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027738189832', '1590229800633634816', '1585192140589182977', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027746578433', '1590229800633634816', '1585192296575348738', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027746578434', '1590229800633634816', '1585192489970511873', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027746578435', '1590229800633634816', '1585192568882147330', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027746578436', '1590229800633634816', '1585916026725281793', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027746578437', '1590229800633634816', '1585916172586397697', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027746578438', '1590229800633634816', '1539129183310196738', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027746578439', '1590229800633634816', '1531846893999169537', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027746578440', '1590229800633634816', '1531882841361915906', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772738', '1590229800633634816', '1615718593878704130', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772739', '1590229800633634816', '1615718665584525314', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772740', '1590229800633634816', '1615718738766741505', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772741', '1590229800633634816', '1615718811491778562', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772742', '1590229800633634816', '2040661035425329153', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772743', '1590229800633634816', '2040661118854230018', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772744', '1590229800633634816', '10001', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772745', '1590229800633634816', '1491752531735490561', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772746', '1590229800633634816', '1491756888363307009', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772747', '1590229800633634816', '1491757020773289986', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772748', '1590229800633634816', '1491757382771085313', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772749', '1590229800633634816', '1494514119857180674', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027750772750', '1590229800633634816', '1547110946242326529', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967042', '1590229800633634816', '1547111164111253505', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967043', '1590229800633634816', '1491684226094198786', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967044', '1590229800633634816', '1491690996678021121', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967045', '1590229800633634816', '1493836091183411202', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967046', '1590229800633634816', '1493836209106268161', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967047', '1590229800633634816', '1493836280589791233', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967048', '1590229800633634816', '1493841029473042434', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967049', '1590229800633634816', '1590700851537625090', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967050', '1590229800633634816', '1494216988311183361', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027754967051', '1590229800633634816', '1494217080162246658', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027763355650', '1590229800633634816', '1494217080510373890', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027763355651', '1590229800633634816', '1494217080858501121', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027763355652', '1590229800633634816', '1494217081206628354', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027763355653', '1590229800633634816', '1494217081558949890', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027771744257', '1590229800633634816', '1605129651156598786', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027771744258', '1590229800633634816', '1605129783281369089', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027771744259', '1590229800633634816', '1605130091042619393', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027771744260', '1590229800633634816', '1493884088730529793', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027771744261', '1590229800633634816', '1493884379760701442', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027771744262', '1590229800633634816', '1494192758630694913', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027771744263', '1590229800633634816', '1494192758974627842', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027775938562', '1590229800633634816', '1494192759318560770', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027775938563', '1590229800633634816', '1494192759662493698', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027780132865', '1590229800633634816', '1494192760010620930', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027784327170', '1590229800633634816', '1494151991157673985', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027784327171', '1590229800633634816', '1494153372996255746', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027784327172', '1590229800633634816', '1494153373352771586', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027784327173', '1590229800633634816', '1494153373696704514', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027784327174', '1590229800633634816', '1494153374053220354', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027784327175', '1590229800633634816', '1494153374401347585', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027784327176', '1590229800633634816', '1491948958826921986', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027792715777', '1590229800633634816', '1491949315883827201', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027792715778', '1590229800633634816', '1491969633293729794', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027792715779', '1590229800633634816', '1928789844748812290', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027792715780', '1590229800633634816', '1928789932476874754', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027792715781', '1590229800633634816', '1928790012067987457', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027792715782', '1590229800633634816', '1491973212968632322', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027792715783', '1590229800633634816', '1493578977630121986', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027792715784', '1590229800633634816', '1493578977978249218', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027817881601', '1590229800633634816', '1493578978313793537', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027822075906', '1590229800633634816', '1493578978649337858', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027822075907', '1590229800633634816', '1526179827628048385', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027822075908', '1590229800633634816', '1493596733666652162', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027822075909', '1590229800633634816', '1493766204259942401', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027822075910', '1590229800633634816', '1537048076783616001', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027826270210', '1590229800633634816', '1537066492991012865', NULL, NULL);
INSERT INTO `sys_tenant_menu` VALUES ('2040664027826270211', '1590229800633634816', '1537066557067395074', NULL, NULL);

-- ----------------------------
-- Table structure for sys_tenant_package
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_package`;
CREATE TABLE `sys_tenant_package`  (
                                       `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                       `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '套餐名称',
                                       `sub_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '子标题',
                                       `sales_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '销售价格（元）',
                                       `original_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '原价（元）',
                                       `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态：0.正常；1.停用；',
                                       `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '描述',
                                       `app_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用key',
                                       `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                       `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                       `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                       `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                       `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                       PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租户套餐' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_package
-- ----------------------------
INSERT INTO `sys_tenant_package` VALUES ('1590981945990287361', '悦航购专业版', '商城基础版/营销功能/微信小程序', 1299.00, 2000.00, '1', '<p><br></p><p><img src=\"https://aryn.oss-cn-beijing.aliyuncs.com/lijx/ea86c408-21eb-47af-81df-6367cb398075.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', 'app_base,app_market,app_wechat', '2022-11-11 16:17:10', '2024-11-07 19:39:40', '0', NULL, 'admin');
INSERT INTO `sys_tenant_package` VALUES ('1591735271929356289', '悦航购基础版', '商城基础功能', 888.00, 999.00, '1', '<p>阿达打撒 阿斯顿撒打算</p>', 'app_base,app_wechat', '2022-11-13 18:10:36', '2022-11-13 18:10:36', '0', NULL, NULL);
INSERT INTO `sys_tenant_package` VALUES ('1639458123460681730', '悦航购旗舰版', '悦航购旗舰版', 0.01, 0.01, '1', '<p><br></p><p><img src=\"https://aryn.oss-cn-beijing.aliyuncs.com/lijx/72ca917c-9cc3-4452-9aa7-93d99196782e.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', 'app_base,app_market,app_alipay,app_wechat', '2022-11-11 16:17:10', '2022-11-13 18:11:39', '0', NULL, NULL);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
                             `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                             `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名',
                             `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码',
                             `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
                             `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
                             `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
                             `dept_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '部门ID',
                             `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
                             `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                             `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                             `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                             `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                             `status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态：0.正常；1.停用；',
                             `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                             `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                             `type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号类型：0.系统主账户；',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES ('1', 'admin', '$2a$10$9p1BH9TCqoxGG1BZe8.cZeUs7xytTbg6LUqjB5uSDbIYCeXDBYnR2', '806@163.com', '天启雨数', 'https://aryn.oss-cn-beijing.aliyuncs.com/lijx/278a943b-52ac-4f6b-a6b6-506cc67f8d0a.jpg', '2', '17615123399', '0', '2022-05-20 17:33:24', '2025-04-09 22:18:24', '1590229800633634816', '0', NULL, 'admin', '0');
INSERT INTO `sys_user` VALUES ('1881232178902249473', 'system', '$2a$10$9p1BH9TCqoxGG1BZe8.cZeUs7xytTbg6LUqjB5uSDbIYCeXDBYnR2', NULL, '111', 'https://minio.aryn.co/aryn/file/eece8d88-c9a5-4d7f-b6b8-cf7c82d3c34a.png', '1881232178197606401', '17640212321', '0', '2025-01-20 14:47:58', '2025-04-13 05:01:21', '1881232176465358849', '0', 'lijx', 'system', '0');

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
                                  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
                                  `role_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
                                  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统用户关联角色表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES ('1909974176524726274', '1', '1', '2025-04-09 22:18:24', '1590229800633634816');
INSERT INTO `sys_user_role` VALUES ('1911162746203594754', '1881232178902249473', '1881232177484574722', '2025-04-13 05:01:21', '1881232176465358849');

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
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of undo_log
-- ----------------------------

-- ----------------------------
-- Table structure for user_address
-- ----------------------------
DROP TABLE IF EXISTS `user_address`;
CREATE TABLE `user_address`  (
                                 `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                 `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户主键',
                                 `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                 `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                 `recipient_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '收货人姓名',
                                 `telephone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系电话',
                                 `postal_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮政编码',
                                 `province_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '省名称（冗余字段）',
                                 `city_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '市名称（冗余字段）',
                                 `area_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '区名称（冗余字段）',
                                 `province_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '省编码',
                                 `city_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '市编码',
                                 `area_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '区编码',
                                 `is_default` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '默认地址 0.否；1.是；',
                                 `detail_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '详细地址',
                                 `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                                 `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                 `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                 PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户收货地址' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user_address
-- ----------------------------

-- ----------------------------
-- Table structure for user_info
-- ----------------------------
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info`  (
                              `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                              `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
                              `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
                              `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码',
                              `sex` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '性别：1、男；2、女；0、未知；',
                              `avatar_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
                              `city` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所在城市',
                              `province` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所在省份',
                              `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                              `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                              `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                              `user_source` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户来源',
                              `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
                              `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                              `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                              `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '平台用户唯一ID（openid / alipay userId 等）',
                              PRIMARY KEY (`id`) USING BTREE,
                              UNIQUE INDEX `uk_phone`(`tenant_id` ASC, `phone` ASC) USING BTREE COMMENT '用户手机号'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商城用户' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user_info
-- ----------------------------
INSERT INTO `user_info` VALUES ('2040654277629796353', '微信用户6798', NULL, NULL, NULL, NULL, NULL, NULL, '0', '2026-04-05 12:54:28', NULL, NULL, '1590229800633634816', 'ozexS3bXeBjIkrfIZoe07SWXBY4I', NULL, 'ozexS3bXeBjIkrfIZoe07SWXBY4I');
INSERT INTO `user_info` VALUES ('2040656345832747009', '176****2320', '17640212320', NULL, NULL, NULL, NULL, NULL, '0', '2026-04-05 13:02:41', NULL, NULL, '1590229800633634816', '17640212320', NULL, NULL);


-- ----------------------------
-- Table structure for distribution_config
-- ----------------------------
DROP TABLE IF EXISTS `distribution_config`;
CREATE TABLE `distribution_config` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `config_name` varchar(64) NOT NULL COMMENT '配置名称',
  `commission_rate` decimal(10,4) NOT NULL DEFAULT 0.1000 COMMENT '一级佣金比例',
  `commission_rate_level2` decimal(10,4) DEFAULT 0.0500 COMMENT '二级佣金比例',
  `min_withdraw_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '最小提现金额',
  `settle_cycle_days` int DEFAULT 7 COMMENT '结算周期(天)',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0启用 1禁用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `version` int DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_status` (`status`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销配置表';

-- ----------------------------
-- Table structure for distribution_user
-- ----------------------------
DROP TABLE IF EXISTS `distribution_user`;
CREATE TABLE `distribution_user` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `user_id` varchar(32) NOT NULL COMMENT '用户ID',
  `nickname` varchar(64) DEFAULT NULL COMMENT '用户昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '用户头像',
  `inviter_user_id` varchar(32) DEFAULT NULL COMMENT '邀请人ID',
  `total_commission` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计佣金',
  `available_commission` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '可提现佣金',
  `withdrawn_commission` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '已提现佣金',
  `frozen_commission` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '冻结佣金（提现申请中）',
  `subordinate_count` int DEFAULT 0 COMMENT '下级人数',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0启用 1禁用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `version` int DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_distribution_user_user_id` (`user_id`),
  KEY `idx_inviter_user_id` (`inviter_user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销用户表';

-- ----------------------------
-- Table structure for distribution_order
-- ----------------------------
DROP TABLE IF EXISTS `distribution_order`;
CREATE TABLE `distribution_order` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `biz_order_id` varchar(32) NOT NULL COMMENT '业务订单ID',
  `buyer_user_id` varchar(32) NOT NULL COMMENT '买家用户ID',
  `distributor_user_id` varchar(32) NOT NULL COMMENT '分销员用户ID',
  `order_amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `commission_amount` decimal(10,2) NOT NULL COMMENT '佣金金额',
  `commission_level` int DEFAULT 1 COMMENT '佣金层级：1一级 2二级',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0待结算 1已结算 2已退款',
  `settle_time` datetime DEFAULT NULL COMMENT '结算时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `version` int DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_distribution_order_biz_order` (`biz_order_id`),
  KEY `idx_distributor_user_id` (`distributor_user_id`),
  KEY `idx_buyer_user_id` (`buyer_user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销订单表';

-- ----------------------------
-- Table structure for distribution_commission_flow
-- ----------------------------
DROP TABLE IF EXISTS `distribution_commission_flow`;
CREATE TABLE `distribution_commission_flow` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `user_id` varchar(32) NOT NULL COMMENT '用户ID',
  `biz_order_id` varchar(32) DEFAULT NULL COMMENT '关联业务单号',
  `flow_type` varchar(16) NOT NULL COMMENT '流水类型：INCOME/EXPENSE',
  `amount` decimal(10,2) NOT NULL COMMENT '变动金额',
  `balance_after` decimal(10,2) NOT NULL COMMENT '变动后余额',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`),
  KEY `idx_biz_order_id` (`biz_order_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销佣金流水表';

-- ----------------------------
-- Table structure for distribution_withdraw
-- ----------------------------
DROP TABLE IF EXISTS `distribution_withdraw`;
CREATE TABLE `distribution_withdraw` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `withdraw_no` varchar(32) DEFAULT NULL COMMENT '提现单号',
  `user_id` varchar(32) NOT NULL COMMENT '用户ID',
  `amount` decimal(10,2) NOT NULL COMMENT '提现金额',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0待审核 1已通过 2已拒绝',
  `account_type` varchar(16) DEFAULT NULL COMMENT '收款类型',
  `account_name` varchar(64) DEFAULT NULL COMMENT '收款人',
  `account_no` varchar(128) DEFAULT NULL COMMENT '收款账号',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_by` varchar(60) DEFAULT NULL COMMENT '审核人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `version` int DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销提现表';

-- ----------------------------
-- 分销相关字典项
-- ----------------------------
INSERT INTO `sys_dict` VALUES ('1992000000000000001', 'distribution_order_status', '分销订单状态', '0', '分销订单结算状态', '0', NOW(), NULL, 'admin', NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000011', '1992000000000000001', '待结算', '0', 'distribution_order_status', '0', '', 1, '0', NOW(), NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000012', '1992000000000000001', '已结算', '1', 'distribution_order_status', '0', '', 2, '0', NOW(), NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000013', '1992000000000000001', '已退款', '2', 'distribution_order_status', '0', '', 3, '0', NOW(), NULL, 'admin', NULL, NULL);

INSERT INTO `sys_dict` VALUES ('1992000000000000002', 'distribution_withdraw_status', '提现审核状态', '0', '分销提现审核状态', '0', NOW(), NULL, 'admin', NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000021', '1992000000000000002', '待审核', '0', 'distribution_withdraw_status', '0', '', 1, '0', NOW(), NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000022', '1992000000000000002', '已通过', '1', 'distribution_withdraw_status', '0', '', 2, '0', NOW(), NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000023', '1992000000000000002', '已拒绝', '2', 'distribution_withdraw_status', '0', '', 3, '0', NOW(), NULL, 'admin', NULL, NULL);

INSERT INTO `sys_dict` VALUES ('1992000000000000003', 'distribution_user_status', '分销用户状态', '0', '分销用户启用/禁用状态', '0', NOW(), NULL, 'admin', NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000031', '1992000000000000003', '启用', '0', 'distribution_user_status', '0', '', 1, '0', NOW(), NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000032', '1992000000000000003', '禁用', '1', 'distribution_user_status', '0', '', 2, '0', NOW(), NULL, 'admin', NULL, NULL);

INSERT INTO `sys_dict` VALUES ('1992000000000000004', 'distribution_config_status', '分销配置状态', '0', '分销配置启用/禁用状态', '0', NOW(), NULL, 'admin', NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000041', '1992000000000000004', '启用', '0', 'distribution_config_status', '0', '', 1, '0', NOW(), NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000042', '1992000000000000004', '禁用', '1', 'distribution_config_status', '0', '', 2, '0', NOW(), NULL, 'admin', NULL, NULL);

INSERT INTO `sys_dict` VALUES ('1992000000000000005', 'commission_flow_type', '佣金流水类型', '0', '佣金收支类型', '0', NOW(), NULL, 'admin', NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000051', '1992000000000000005', '收入', 'INCOME', 'commission_flow_type', '0', '', 1, '0', NOW(), NULL, 'admin', NULL, NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000052', '1992000000000000005', '支出', 'EXPENSE', 'commission_flow_type', '0', '', 2, '0', NOW(), NULL, 'admin', NULL, NULL);

INSERT INTO `sys_dict` VALUES ('1992000000000000006', 'withdraw_account_type', '提现收款类型', '0', '提现收款账户类型', '0', NOW(), NULL, 'admin', NULL);
INSERT INTO `sys_dict_value` VALUES ('1992000000000000061', '1992000000000000006', '微信', 'WECHAT', 'withdraw_account_type', '0', '', 1, '0', NOW(), NULL, 'admin', NULL, NULL);

-- ----------------------------
-- 分销管理菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('1991000000000000001', '分销管理', NULL, '/promotion/distribution', '/promotion/distribution/config', '1779386604402573314', 'carbon:currency', '', 20, '0', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1991000000000000010', '分销配置', NULL, '/promotion/distribution/config', NULL, '1991000000000000001', 'carbon:settings', 'promotion/distribution-config/index', 1, '0', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1991000000000000011', '分销配置分页', 'promotion:distributionconfig:page', NULL, NULL, '1991000000000000010', NULL, NULL, 1, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000012', '分销配置查询', 'promotion:distributionconfig:get', NULL, NULL, '1991000000000000010', NULL, NULL, 2, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000013', '分销配置新增', 'promotion:distributionconfig:add', NULL, NULL, '1991000000000000010', NULL, NULL, 3, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000014', '分销配置修改', 'promotion:distributionconfig:edit', NULL, NULL, '1991000000000000010', NULL, NULL, 4, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000015', '分销配置删除', 'promotion:distributionconfig:del', NULL, NULL, '1991000000000000010', NULL, NULL, 5, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000020', '分销用户', NULL, '/promotion/distribution/user', NULL, '1991000000000000001', 'carbon:user-multiple', 'promotion/distribution-user/index', 2, '0', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1991000000000000021', '分销用户分页', 'promotion:distributionuser:page', NULL, NULL, '1991000000000000020', NULL, NULL, 1, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000022', '分销用户查询', 'promotion:distributionuser:get', NULL, NULL, '1991000000000000020', NULL, NULL, 2, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000023', '分销用户新增', 'promotion:distributionuser:add', NULL, NULL, '1991000000000000020', NULL, NULL, 3, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000024', '分销用户修改', 'promotion:distributionuser:edit', NULL, NULL, '1991000000000000020', NULL, NULL, 4, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000025', '分销用户删除', 'promotion:distributionuser:del', NULL, NULL, '1991000000000000020', NULL, NULL, 5, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000030', '分销订单', NULL, '/promotion/distribution/order', NULL, '1991000000000000001', 'carbon:order-details', 'promotion/distribution-order/index', 3, '0', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1991000000000000031', '分销订单分页', 'promotion:distributionorder:page', NULL, NULL, '1991000000000000030', NULL, NULL, 1, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000032', '分销订单查询', 'promotion:distributionorder:get', NULL, NULL, '1991000000000000030', NULL, NULL, 2, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000033', '分销订单结算', 'promotion:distributionorder:settle', NULL, NULL, '1991000000000000030', NULL, NULL, 3, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000040', '分销提现', NULL, '/promotion/distribution/withdraw', NULL, '1991000000000000001', 'carbon:money', 'promotion/distribution-withdraw/index', 4, '0', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1991000000000000041', '分销提现分页', 'promotion:distributionwithdraw:page', NULL, NULL, '1991000000000000040', NULL, NULL, 1, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000042', '分销提现查询', 'promotion:distributionwithdraw:get', NULL, NULL, '1991000000000000040', NULL, NULL, 2, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000043', '分销提现审核', 'promotion:distributionwithdraw:audit', NULL, NULL, '1991000000000000040', NULL, NULL, 3, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);

-- ----------------------------
-- 拼团管理菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('1991000000000000050', '拼团管理', NULL, '/promotion/groupbuy', '/promotion/groupbuy/activity', '1779386604402573314', 'carbon:group', '', 21, '0', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1991000000000000051', '拼团活动', NULL, '/promotion/groupbuy/activity', NULL, '1991000000000000050', 'carbon:flash', 'promotion/group-buy-activity/index', 1, '0', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1991000000000000052', '拼团活动分页', 'promotion:groupbuy:page', NULL, NULL, '1991000000000000051', NULL, NULL, 1, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000053', '拼团活动查询', 'promotion:groupbuy:get', NULL, NULL, '1991000000000000051', NULL, NULL, 2, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000054', '拼团活动新增', 'promotion:groupbuy:add', NULL, NULL, '1991000000000000051', NULL, NULL, 3, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000055', '拼团活动修改', 'promotion:groupbuy:edit', NULL, NULL, '1991000000000000051', NULL, NULL, 4, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000056', '拼团活动删除', 'promotion:groupbuy:del', NULL, NULL, '1991000000000000051', NULL, NULL, 5, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);
INSERT INTO `sys_menu` VALUES ('1991000000000000060', '拼团记录', NULL, '/promotion/groupbuy/record', NULL, '1991000000000000050', 'carbon:document', 'promotion/group-buy-record/index', 2, '0', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system');
INSERT INTO `sys_menu` VALUES ('1991000000000000061', '拼团记录分页', 'promotion:groupbuyrecord:page', NULL, NULL, '1991000000000000060', NULL, NULL, 1, '1', '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', NULL);

-- ----------------------------
-- 拼团活动表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `group_buy_activity` (
  `id` bigint NOT NULL COMMENT '主键',
  `activity_name` varchar(128) NOT NULL COMMENT '活动名称',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `original_price` decimal(10,2) NOT NULL COMMENT '商品原价',
  `group_price` decimal(10,2) NOT NULL COMMENT '拼团价',
  `group_num` int NOT NULL COMMENT '成团人数(最少2人)',
  `limit_num` int DEFAULT 0 COMMENT '限购数量(0=不限)',
  `virtual_num` int DEFAULT 0 COMMENT '虚拟成团人数(展示用)',
  `activity_status` char(1) DEFAULT '0' COMMENT '活动状态:0草稿,1进行中,2已结束',
  `started_at` datetime NOT NULL COMMENT '活动开始时间',
  `ended_at` datetime NOT NULL COMMENT '活动结束时间',
  `group_expire_hours` int DEFAULT 24 COMMENT '拼团过期时间(小时)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '逻辑删除:0正常,1删除',
  `version` int DEFAULT 0 COMMENT '版本号',
  `tenant_id` varchar(32) DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_group_buy_activity_spu` (`spu_id`),
  KEY `idx_group_buy_activity_status` (`activity_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团活动';

-- ----------------------------
-- 拼团记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `group_buy_record` (
  `id` bigint NOT NULL COMMENT '主键',
  `activity_id` bigint NOT NULL COMMENT '拼团活动ID',
  `spu_id` varchar(32) NOT NULL COMMENT '商品SPU ID',
  `sku_id` varchar(32) NOT NULL COMMENT '商品SKU ID',
  `group_price` decimal(10,2) NOT NULL COMMENT '拼团价',
  `group_num` int NOT NULL COMMENT '成团人数',
  `current_num` int DEFAULT 0 COMMENT '当前参团人数',
  `leader_user_id` varchar(32) NOT NULL COMMENT '团长用户ID',
  `group_status` char(1) DEFAULT '0' COMMENT '拼团状态:0拼团中,1成功,2失败',
  `expire_at` datetime NOT NULL COMMENT '拼团过期时间',
  `success_at` datetime DEFAULT NULL COMMENT '成团成功时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '逻辑删除:0正常,1删除',
  `version` int DEFAULT 0 COMMENT '版本号',
  `tenant_id` varchar(32) DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_group_buy_record_activity` (`activity_id`),
  KEY `idx_group_buy_record_status` (`group_status`),
  KEY `idx_group_buy_record_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团记录';

-- ----------------------------
-- 拼团参与记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `group_buy_member` (
  `id` bigint NOT NULL COMMENT '主键',
  `record_id` bigint NOT NULL COMMENT '拼团记录ID',
  `activity_id` bigint NOT NULL COMMENT '拼团活动ID',
  `user_id` varchar(32) NOT NULL COMMENT '参团用户ID',
  `order_id` varchar(32) DEFAULT NULL COMMENT '关联订单ID',
  `member_status` char(1) DEFAULT '0' COMMENT '参团状态:0待付款,1已付款,2已取消',
  `is_leader` char(1) DEFAULT '0' COMMENT '是否团长:0否,1是',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '逻辑删除:0正常,1删除',
  `version` int DEFAULT 0 COMMENT '版本号',
  `tenant_id` varchar(32) DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_group_buy_member_record` (`record_id`),
  KEY `idx_group_buy_member_user` (`user_id`),
  UNIQUE KEY `uk_record_user` (`record_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团参与记录';

SET FOREIGN_KEY_CHECKS = 1;
