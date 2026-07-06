USE aryn_boot;

SET NAMES utf8mb4;

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

-- ----------------------------
-- 品牌管理菜单（父菜单：商品管理 1779386487675092994）
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2060000000000000001', '品牌管理', NULL, '/product/brand', NULL, '1779386487675092994', 'carbon:star', 'product/brand/index', 20, '0', '2026-07-05 00:00:00', '2026-07-05 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2060000000000000002', '品牌列表', 'product:brand:page', NULL, NULL, '2060000000000000001', NULL, NULL, 1, '1', '2026-07-05 00:00:00', '2026-07-05 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2060000000000000003', '品牌查询', 'product:brand:get', NULL, NULL, '2060000000000000001', NULL, NULL, 2, '1', '2026-07-05 00:00:00', '2026-07-05 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2060000000000000004', '品牌新增', 'product:brand:add', NULL, NULL, '2060000000000000001', NULL, NULL, 3, '1', '2026-07-05 00:00:00', '2026-07-05 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2060000000000000005', '品牌编辑', 'product:brand:edit', NULL, NULL, '2060000000000000001', NULL, NULL, 4, '1', '2026-07-05 00:00:00', '2026-07-05 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2060000000000000006', '品牌删除', 'product:brand:del', NULL, NULL, '2060000000000000001', NULL, NULL, 5, '1', '2026-07-05 00:00:00', '2026-07-05 00:00:00', '0', '0', 'app_base', NULL, NULL);