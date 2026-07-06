USE aryn_product;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
INSERT INTO `goods_category` VALUES ('1912861788486148097', '家用电器', '0', 'https://minio.Aetheryn.cn/aryn/file/f65cf2cb-3594-40e3-b720-ea72c9f685ef.png', '家用电器', '0', '2025-04-17 21:32:44', '2025-05-16 10:33:27', '0', 1, '1590229800633634816', 'admin', 'system');
INSERT INTO `goods_category` VALUES ('1912862220591734785', '电视', '1912861788486148097', 'https://minio.Aetheryn.cn/aryn/file/9baf470c-b17e-417e-88f9-38a3b5f2cf8e.png', '电视', '0', '2025-04-17 21:34:27', NULL, '0', 1, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912862615531593730', '空调', '1912861788486148097', 'https://minio.Aetheryn.cn/aryn/file/16e58200-e455-44aa-9c91-66b9d991b5ef.jpg', '空调', '0', '2025-04-17 21:36:01', NULL, '0', 2, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912863000879079426', '冰箱', '1912861788486148097', 'https://minio.Aetheryn.cn/aryn/file/1c3abd12-ec0c-4d18-897b-c789203b4f25.jpg', '冰箱', '0', '2025-04-17 21:37:33', NULL, '0', 3, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912863400222957569', '手机数码', '0', 'https://minio.Aetheryn.cn/aryn/file/9baf470c-b17e-417e-88f9-38a3b5f2cf8e.png', '手机数码', '0', '2025-04-17 21:39:09', NULL, '0', 2, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912863683464306689', '手机', '1912863400222957569', 'https://minio.Aetheryn.cn/aryn/file/cb7c6390-1602-4f66-a4fc-94bbb9adbf35.jpg', '手机', '0', '2025-04-17 21:40:16', NULL, '0', 1, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912863991967948801', '智能设备', '1912863400222957569', 'https://minio.Aetheryn.cn/aryn/file/8c25a91d-91b4-4728-8f20-4880fad18677.jpg', '智能设备', '0', '2025-04-17 21:41:30', NULL, '0', 2, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1912864294511484929', '无人机', '1912863400222957569', 'https://minio.Aetheryn.cn/aryn/file/0f4b0f37-0844-4c43-a1d7-231eadc61ddb.jpg', '无人机', '0', '2025-04-17 21:42:42', NULL, '0', 3, '1590229800633634816', 'admin', NULL);
INSERT INTO `goods_category` VALUES ('1925541056051564546', '电脑', '1912863400222957569', 'https://minio.Aetheryn.cn/aryn/1590229800633634816/file/e1a4f623-6773-4eea-9f78-c22ca4d4c12b.jpg', NULL, '0', '2025-05-22 21:15:37', NULL, '0', 4, '1590229800633634816', 'system', NULL);

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
INSERT INTO `goods_sku` VALUES ('2026667019995189250', '1925545886404988929', 1.00, 1.00, 1.00, 100, NULL, NULL, '2026-02-25 22:34:06', NULL, '0', 0, '1590229800633634816', 'system', NULL, NULL, '0', '[{\"specsId\": \"1912866651538366466\", \"specsName\": \"外观\", \"specsValueId\": \"1912866747504041986\", \"specsValueName\": \"沙漠色钛金属\"}]', NULL);
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
INSERT INTO `goods_spu` VALUES ('1912867577569386497', 'Apple/苹果 iPhone 16 Pro Max（A3297）', NULL, 'https://minio.Aetheryn.cn/aryn/file/e8e18e94-d352-45d9-acdd-f918a6f77f90.jpg,https://minio.Aetheryn.cn/aryn/file/88e79726-2368-4767-aa60-065b5ea9aabd.jpg,https://minio.Aetheryn.cn/aryn/file/cc5819fb-8f39-45ad-b584-52ff3fbdf642.jpg,https://minio.Aetheryn.cn/aryn/file/f9a3fe3b-22ee-437d-9676-8fe4127e04ec.jpg,https://minio.Aetheryn.cn/aryn/file/f7a85fb7-adde-4635-9738-88709be3b76a.jpg', '1', 1073, '1912863400222957569', '1912863683464306689', '2025-04-17 21:55:45', '2025-07-17 00:40:09', '0', '<p><br></p><p><img src=\"https://minio.Aetheryn.cn/aryn/file/9888cb29-e6b8-4d67-b129-0d3859c3717e.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p><p><img src=\"https://minio.Aetheryn.cn/aryn/file/1744aaa5-ed8a-46ca-ba53-3e0038aa5ea6.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', '1', '1590229800633634816', 'admin', NULL, 1129, '0', 0.00, 9299.00, 9299.00, 11299.00);
INSERT INTO `goods_spu` VALUES ('1912870113080680449', '小米15 国家补贴 徕卡光学Summilux高速镜头 骁龙8至尊版移动平台', NULL, 'https://minio.Aetheryn.cn/aryn/file/6b5481c8-ee15-41a2-bca8-d08463947208.jpg,https://minio.Aetheryn.cn/aryn/file/22e2fc4e-bc1e-4c42-a57f-af4cb04af847.jpg,https://minio.Aetheryn.cn/aryn/file/9f371b82-9843-4fd2-a206-ebbb0b9ddb9e.jpg,https://minio.Aetheryn.cn/aryn/file/505b78ac-7dfa-4379-a4df-d11e90d34ae0.jpg,https://minio.Aetheryn.cn/aryn/file/3f462f51-a183-465d-94cc-f8bfd1759869.jpg', '1', 245, '1912863400222957569', '1912863683464306689', '2025-04-17 22:05:49', '2025-07-17 00:06:18', '0', '<p><br></p><p><img src=\"https://minio.Aetheryn.cn/aryn/file/292fbf15-33cc-4eee-b1ef-3a4dc71ba4d2.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', '1', '1590229800633634816', 'admin', NULL, 69954, '0', 0.00, 9999.00, 9999.00, 9999.00);
INSERT INTO `goods_spu` VALUES ('1912882761411239938', '大疆 DJI Mini 3 优选迷你航拍机 智能高清拍摄无人机 小型遥控飞机 兼容带屏遥控器 大疆无人机', '', 'https://minio.Aetheryn.cn/aryn/file/e38f917c-c4a5-4fd8-9acf-e6a4f40aff60.jpg,https://minio.Aetheryn.cn/aryn/file/ff6de4fc-406e-4f02-8b9a-6269dd9b8ce8.jpg,https://minio.Aetheryn.cn/aryn/file/0f4f5061-3930-4ef8-9352-5307d3689307.jpg,https://minio.Aetheryn.cn/aryn/file/78fa70d0-faff-4440-a9a4-55b4b4ca71f7.jpg', '1', 145, '1912863400222957569', '1912864294511484929', '2025-04-17 22:56:05', '2025-06-21 00:05:10', '0', '<p><br></p><p><img src=\"https://minio.Aetheryn.cn/aryn/file/b775af49-8e44-4ba7-b19c-08b8141af9c5.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', '0', '1590229800633634816', 'admin', NULL, 973, '2', 0.00, 5999.00, 5999.00, 5999.00);
INSERT INTO `goods_spu` VALUES ('1925543036622884866', '华为MateBook Fold 非凡大师 笔记本电脑 鸿蒙操作系统 超轻薄折叠电脑双层OLED显示屏', '华为MateBook Fold 非凡大师 笔记本电脑 鸿蒙操作系统 超轻薄折叠电脑双层OLED显示屏', 'https://minio.Aetheryn.cn/aryn/1590229800633634816/file/5548b0e1-7982-468a-8740-ff0177aed995.jpg,https://minio.Aetheryn.cn/aryn/1590229800633634816/file/f818bd69-14e6-4c24-88f2-dfc9c8687750.jpg,https://minio.Aetheryn.cn/aryn/1590229800633634816/file/b116d626-b40b-4c21-ae34-408277623e85.jpg,https://minio.Aetheryn.cn/aryn/1590229800633634816/file/fa5a558d-5962-49b8-9695-a57b9239af3e.jpg,https://minio.Aetheryn.cn/aryn/1590229800633634816/file/646127e7-5462-4d27-9d2e-4ecc324fd19c.jpg', '1', 2003, '1912863400222957569', '1925541056051564546', '2025-05-22 21:23:29', '2025-06-21 00:05:10', '0', '<p>2133333333333333333333123</p>', '1', '1590229800633634816', 'system', NULL, 3997, '0', 0.00, 25999.00, 25999.00, 25999.00);
INSERT INTO `goods_spu` VALUES ('1925545886404988929', 'HUAWEI Mate X6 分布式玄武架构 鸿蒙大屏AI 红枫原色影像折叠旗舰手机 折叠屏', NULL, 'https://minio.Aetheryn.cn/aryn/1590229800633634816/file/dcb4b7d0-4293-4c3f-aa00-0068e8216db6.jpg,https://minio.Aetheryn.cn/aryn/1590229800633634816/file/2590cb12-279c-402d-aacd-ed171ff6189f.jpg,https://minio.Aetheryn.cn/aryn/1590229800633634816/file/64ffdbb4-0bc4-4def-a9eb-e2da1aa25c17.jpg,https://minio.Aetheryn.cn/aryn/1590229800633634816/file/f51a1ac7-d968-4473-ac6e-aad0fed31948.jpg,https://minio.Aetheryn.cn/aryn/1590229800633634816/file/797ef622-bb8d-4b67-b383-2531b46ed48e.jpg', '1', 2000, '1912863400222957569', '1912863683464306689', '2025-05-22 21:34:49', '2025-06-22 22:02:32', '0', '<p><img src=\"\" alt=\"\" data-href=\"\" style=\"\"/></p><p><img src=\"https://minio.Aetheryn.cn/aryn/1590229800633634816/file/64ffdbb4-0bc4-4def-a9eb-e2da1aa25c17.jpg\" alt=\"\" data-href=\"\" style=\"\"/><img src=\"\" alt=\"\" data-href=\"\" style=\"\"/></p><p><img src=\"https://minio.Aetheryn.cn/aryn/1590229800633634816/file/f51a1ac7-d968-4473-ac6e-aad0fed31948.jpg\" alt=\"\" data-href=\"\" style=\"\"/></p>', '1', '1590229800633634816', 'system', 'admin', 0, '0', 0.00, 0.00, 0.00, 0.00);

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
-- Table structure for pms_brand
-- ----------------------------
DROP TABLE IF EXISTS `pms_brand`;
CREATE TABLE `pms_brand`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '品牌ID',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '品牌名称',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '品牌Logo',
  `sort` int NULL DEFAULT 0 COMMENT '排序',
  `first_letter` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '首字母',
  `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态(0禁用1启用)',
  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户ID',
  `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0正常1删除)',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品品牌表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pms_brand
-- ----------------------------

-- ----------------------------
-- ALTER goods_spu add brand_id
-- ----------------------------
ALTER TABLE `goods_spu` ADD COLUMN `brand_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '品牌ID' AFTER `category_second_id`;

SET FOREIGN_KEY_CHECKS = 1;

