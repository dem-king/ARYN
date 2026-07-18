-- =====================================================
-- 会员管理模块 - Boot模式增量SQL
-- 执行顺序：在 2aryn_boot.sql 之后执行
-- =====================================================

-- ----------------------------
-- user_info表增加会员相关字段
-- ----------------------------
ALTER TABLE `user_info` ADD COLUMN `member_level_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会员等级ID' AFTER `open_id`;
ALTER TABLE `user_info` ADD COLUMN `point` int NOT NULL DEFAULT 0 COMMENT '积分余额' AFTER `member_level_id`;
ALTER TABLE `user_info` ADD COLUMN `total_point` int NOT NULL DEFAULT 0 COMMENT '累计获得积分' AFTER `point`;
ALTER TABLE `user_info` ADD COLUMN `balance` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '储值余额' AFTER `total_point`;
ALTER TABLE `user_info` ADD COLUMN `total_consume` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费金额' AFTER `balance`;

-- ----------------------------
-- Table structure for member_level
-- ----------------------------
DROP TABLE IF EXISTS `member_level`;
CREATE TABLE `member_level`  (
    `id`              varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `level_name`      varchar(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '等级名称',
    `level_icon`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '等级图标URL',
    `condition_type`  char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '升级条件类型：1-累计消费金额；2-累计积分',
    `condition_value` decimal(10,2) NOT NULL COMMENT '升级条件值',
    `sort_order`      int          NULL DEFAULT 0 COMMENT '排序号',
    `status`          char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0-启用；1-禁用',
    `create_by`       varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `update_by`       varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
    `create_time`     datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime     NULL DEFAULT NULL COMMENT '修改时间',
    `del_flag`        char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    `tenant_id`       varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会员等级配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for member_level_record
-- ----------------------------
DROP TABLE IF EXISTS `member_level_record`;
CREATE TABLE `member_level_record`  (
    `id`            varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `user_id`       varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
    `old_level_id`  varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '原等级ID',
    `new_level_id`  varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '新等级ID',
    `change_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '变更原因',
    `create_by`     varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `tenant_id`     varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会员等级变更记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for points_config
-- ----------------------------
DROP TABLE IF EXISTS `points_config`;
CREATE TABLE `points_config`  (
    `id`            varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `rule_name`     varchar(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称',
    `rule_type`     char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则类型：1-获取；2-消耗',
    `trigger_scene` varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发场景',
    `point_value`   int          NOT NULL COMMENT '积分值',
    `status`        char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0-启用；1-禁用',
    `create_by`     varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `update_by`     varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
    `create_time`   datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`   datetime     NULL DEFAULT NULL COMMENT '修改时间',
    `del_flag`      char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    `tenant_id`     varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '积分配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for points_record
-- ----------------------------
DROP TABLE IF EXISTS `points_record`;
CREATE TABLE `points_record`  (
    `id`            varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `user_id`       varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
    `change_type`   char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '变动类型：1-获取；2-消耗',
    `change_point`  int          NOT NULL COMMENT '变动积分',
    `balance_after` int          NOT NULL COMMENT '变动后余额',
    `trigger_scene` varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发场景',
    `remark`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    `create_by`     varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `tenant_id`     varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '积分记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sign_in_config
-- ----------------------------
DROP TABLE IF EXISTS `sign_in_config`;
CREATE TABLE `sign_in_config`  (
    `id`              varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `consecutive_day` int         NOT NULL COMMENT '连续签到天数',
    `reward_point`    int         NOT NULL COMMENT '奖励积分',
    `sort_order`      int         NULL DEFAULT 0 COMMENT '排序号',
    `status`          char(1)     CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0-启用；1-禁用',
    `create_by`       varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `update_by`       varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
    `create_time`     datetime    NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime    NULL DEFAULT NULL COMMENT '修改时间',
    `del_flag`        char(2)     CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    `tenant_id`       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '签到配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sign_in_record
-- ----------------------------
DROP TABLE IF EXISTS `sign_in_record`;
CREATE TABLE `sign_in_record`  (
    `id`              varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `user_id`         varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
    `sign_date`       date        NOT NULL COMMENT '签到日期',
    `consecutive_day` int         NOT NULL COMMENT '连续签到天数',
    `reward_point`    int         NOT NULL COMMENT '获得积分',
    `create_by`       varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `create_time`     datetime    NULL DEFAULT NULL COMMENT '创建时间',
    `tenant_id`       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_user_date` (`user_id`, `sign_date`) USING BTREE,
    INDEX `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '签到记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for recharge_config
-- ----------------------------
DROP TABLE IF EXISTS `recharge_config`;
CREATE TABLE `recharge_config`  (
    `id`              varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `recharge_amount` decimal(10,2) NOT NULL COMMENT '充值金额',
    `gift_amount`     decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '赠送金额',
    `gift_point`      int           NOT NULL DEFAULT 0 COMMENT '赠送积分',
    `sort_order`      int           NULL DEFAULT 0 COMMENT '排序号',
    `status`          char(1)       CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0-启用；1-禁用',
    `create_by`       varchar(60)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `update_by`       varchar(60)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
    `create_time`     datetime      NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime      NULL DEFAULT NULL COMMENT '修改时间',
    `del_flag`        char(2)       CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    `tenant_id`       varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '储值配置表' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 菜单权限初始化
-- 父菜单：会员管理(1779386898750439425)
-- =====================================================

-- ----------------------------
-- 会员等级管理菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000001', '会员等级', NULL, '/user/member-level', NULL, '1779386898750439425', 'carbon:star', 'user/member-level/index', 2, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000002', '等级列表', 'user:memberlevel:page', NULL, NULL, '2050000000000000001', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000003', '等级查询', 'user:memberlevel:get', NULL, NULL, '2050000000000000001', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000004', '等级新增', 'user:memberlevel:add', NULL, NULL, '2050000000000000001', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000005', '等级编辑', 'user:memberlevel:edit', NULL, NULL, '2050000000000000001', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000006', '等级删除', 'user:memberlevel:del', NULL, NULL, '2050000000000000001', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 积分管理父菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000010', '积分管理', NULL, '/user/points', '/user/points/config', '1779386898750439425', 'carbon:point', '', 3, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');

-- 积分配置
INSERT INTO `sys_menu` VALUES ('2050000000000000011', '积分配置', NULL, '/user/points/config', NULL, '2050000000000000010', 'carbon:settings-adjust', 'user/points/config/index', 1, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000012', '积分配置列表', 'user:pointsconfig:page', NULL, NULL, '2050000000000000011', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000013', '积分配置查询', 'user:pointsconfig:get', NULL, NULL, '2050000000000000011', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000014', '积分配置新增', 'user:pointsconfig:add', NULL, NULL, '2050000000000000011', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000015', '积分配置编辑', 'user:pointsconfig:edit', NULL, NULL, '2050000000000000011', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000016', '积分配置删除', 'user:pointsconfig:del', NULL, NULL, '2050000000000000011', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- 积分记录
INSERT INTO `sys_menu` VALUES ('2050000000000000021', '积分记录', NULL, '/user/points/record', NULL, '2050000000000000010', 'carbon:document', 'user/points/record/index', 2, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000022', '积分记录查询', 'user:pointsrecord:page', NULL, NULL, '2050000000000000021', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 签到管理父菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000030', '签到管理', NULL, '/user/sign-in', '/user/sign-in/config', '1779386898750439425', 'carbon:calendar', '', 4, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');

-- 签到配置
INSERT INTO `sys_menu` VALUES ('2050000000000000031', '签到配置', NULL, '/user/sign-in/config', NULL, '2050000000000000030', 'carbon:settings-adjust', 'user/sign-in/config/index', 1, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000032', '签到配置列表', 'user:signinconfig:page', NULL, NULL, '2050000000000000031', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000033', '签到配置查询', 'user:signinconfig:get', NULL, NULL, '2050000000000000031', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000034', '签到配置新增', 'user:signinconfig:add', NULL, NULL, '2050000000000000031', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000035', '签到配置编辑', 'user:signinconfig:edit', NULL, NULL, '2050000000000000031', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000036', '签到配置删除', 'user:signinconfig:del', NULL, NULL, '2050000000000000031', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- 签到记录
INSERT INTO `sys_menu` VALUES ('2050000000000000041', '签到记录', NULL, '/user/sign-in/record', NULL, '2050000000000000030', 'carbon:document', 'user/sign-in/record/index', 2, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000042', '签到记录查询', 'user:signinrecord:page', NULL, NULL, '2050000000000000041', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 储值配置菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000051', '储值配置', NULL, '/user/recharge', NULL, '1779386898750439425', 'carbon:wallet', 'user/recharge/index', 5, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000052', '储值配置列表', 'user:rechargeconfig:page', NULL, NULL, '2050000000000000051', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000053', '储值配置查询', 'user:rechargeconfig:get', NULL, NULL, '2050000000000000051', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000054', '储值配置新增', 'user:rechargeconfig:add', NULL, NULL, '2050000000000000051', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000055', '储值配置编辑', 'user:rechargeconfig:edit', NULL, NULL, '2050000000000000051', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000056', '储值配置删除', 'user:rechargeconfig:del', NULL, NULL, '2050000000000000051', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- Table structure for balance_record
-- ----------------------------
DROP TABLE IF EXISTS `balance_record`;
CREATE TABLE `balance_record`  (
    `id`              varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `user_id`         varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
    `change_type`     char(1)       CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '变动类型：1-充值；2-消费；3-调整',
    `change_amount`   decimal(10,2) NOT NULL COMMENT '变动金额',
    `balance_after`   decimal(10,2) NOT NULL COMMENT '变动后余额',
    `trigger_scene`   varchar(20)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发场景',
    `remark`          varchar(255)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    `create_by`       varchar(60)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `create_time`     datetime      NULL DEFAULT NULL COMMENT '创建时间',
    `tenant_id`       varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '余额变动记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for recharge_order
-- ----------------------------
DROP TABLE IF EXISTS `recharge_order`;
CREATE TABLE `recharge_order`  (
    `id`                 varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `user_id`            varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
    `recharge_config_id` varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '充值配置ID',
    `recharge_amount`    decimal(10,2) NOT NULL COMMENT '充值金额',
    `gift_amount`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '赠送金额',
    `gift_point`         int           NOT NULL DEFAULT 0 COMMENT '赠送积分',
    `order_no`           varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单号',
    `pay_status`         char(1)       CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '支付状态：0-待支付；1-已支付；2-已取消',
    `pay_time`           datetime      NULL DEFAULT NULL COMMENT '支付时间',
    `pay_order_no`       varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付订单号',
    `create_by`          varchar(60)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `update_by`          varchar(60)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
    `create_time`        datetime      NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`        datetime      NULL DEFAULT NULL COMMENT '修改时间',
    `del_flag`           char(2)       CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    `tenant_id`          varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_order_no` (`order_no`) USING BTREE,
    INDEX `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '充值订单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for member_tag
-- ----------------------------
DROP TABLE IF EXISTS `member_tag`;
CREATE TABLE `member_tag`  (
    `id`          varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `tag_name`    varchar(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签名称',
    `tag_color`   varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标签颜色',
    `sort_order`  int          NULL DEFAULT 0 COMMENT '排序号',
    `status`      char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0-启用；1-禁用',
    `create_by`   varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `update_by`   varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
    `create_time` datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime     NULL DEFAULT NULL COMMENT '修改时间',
    `del_flag`    char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    `tenant_id`   varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会员标签表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_tag_rel
-- ----------------------------
DROP TABLE IF EXISTS `user_tag_rel`;
CREATE TABLE `user_tag_rel`  (
    `id`          varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `user_id`     varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
    `tag_id`      varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签ID',
    `create_by`   varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime    NULL DEFAULT NULL COMMENT '创建时间',
    `tenant_id`   varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_user_tag_tenant` (`tenant_id`, `user_id`, `tag_id`) USING BTREE,
    INDEX `idx_user_id` (`user_id`) USING BTREE,
    INDEX `idx_tag_id` (`tag_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户标签关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for member_benefit
-- ----------------------------
DROP TABLE IF EXISTS `member_benefit`;
CREATE TABLE `member_benefit`  (
    `id`           varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `benefit_name` varchar(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权益名称',
    `benefit_type` char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权益类型：1-折扣；2-免运费；3-专属优惠券；4-积分倍率',
    `benefit_value` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '权益值',
    `description`  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
    `status`       char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0-启用；1-禁用',
    `create_by`    varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `update_by`    varchar(60)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
    `create_time`  datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime     NULL DEFAULT NULL COMMENT '修改时间',
    `del_flag`     char(2)      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    `tenant_id`    varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会员权益表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for member_benefit_level_rel
-- ----------------------------
DROP TABLE IF EXISTS `member_benefit_level_rel`;
CREATE TABLE `member_benefit_level_rel`  (
    `id`          varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `benefit_id`  varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权益ID',
    `level_id`    varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '等级ID',
    `create_by`   varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime    NULL DEFAULT NULL COMMENT '创建时间',
    `tenant_id`   varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '租户ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_benefit_level_tenant` (`tenant_id`, `benefit_id`, `level_id`) USING BTREE,
    INDEX `idx_benefit_id` (`benefit_id`) USING BTREE,
    INDEX `idx_level_id` (`level_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '权益等级关联表' ROW_FORMAT = DYNAMIC;

DROP TABLE IF EXISTS `member_order_growth`;
CREATE TABLE `member_order_growth` (
    `id` varchar(32) NOT NULL COMMENT '主键', `order_id` varchar(32) NOT NULL COMMENT '订单ID',
    `order_no` varchar(32) DEFAULT NULL COMMENT '订单编号', `user_id` varchar(32) NOT NULL COMMENT '用户ID',
    `goods_payment_amount` decimal(10,2) NOT NULL COMMENT '实付商品金额',
    `points_awarded` int NOT NULL DEFAULT 0 COMMENT '本次发放积分', `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
    `create_time` datetime NOT NULL COMMENT '创建时间', PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_growth_order` (`tenant_id`, `order_id`), KEY `idx_member_growth_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员订单成长幂等记录';

-- ----------------------------
-- 余额记录菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000100', '余额记录', NULL, '/user/balance-record', NULL, '2050000000000000010', 'carbon:currency', 'user/balance-record/index', 3, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000101', '余额记录查询', 'user:balancerecord:page', NULL, NULL, '2050000000000000100', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 充值订单菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000110', '充值订单', NULL, '/user/recharge-order', NULL, '1779386898750439425', 'carbon:receipt', 'user/recharge-order/index', 6, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000111', '充值订单列表', 'user:rechargeorder:page', NULL, NULL, '2050000000000000110', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000112', '充值订单查询', 'user:rechargeorder:get', NULL, NULL, '2050000000000000110', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 会员标签菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000120', '会员标签', NULL, '/user/member-tag', NULL, '1779386898750439425', 'carbon:tag', 'user/member-tag/index', 7, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000121', '标签列表', 'user:membertag:page', NULL, NULL, '2050000000000000120', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000122', '标签查询', 'user:membertag:get', NULL, NULL, '2050000000000000120', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000123', '标签新增', 'user:membertag:add', NULL, NULL, '2050000000000000120', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000124', '标签编辑', 'user:membertag:edit', NULL, NULL, '2050000000000000120', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000125', '标签删除', 'user:membertag:del', NULL, NULL, '2050000000000000120', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);

-- ----------------------------
-- 会员权益菜单
-- ----------------------------
INSERT INTO `sys_menu` VALUES ('2050000000000000130', '会员权益', NULL, '/user/member-benefit', NULL, '1779386898750439425', 'carbon:gift', 'user/member-benefit/index', 8, '0', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, 'system');
INSERT INTO `sys_menu` VALUES ('2050000000000000131', '权益列表', 'user:memberbenefit:page', NULL, NULL, '2050000000000000130', NULL, NULL, 1, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000132', '权益查询', 'user:memberbenefit:get', NULL, NULL, '2050000000000000130', NULL, NULL, 2, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000133', '权益新增', 'user:memberbenefit:add', NULL, NULL, '2050000000000000130', NULL, NULL, 3, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000134', '权益编辑', 'user:memberbenefit:edit', NULL, NULL, '2050000000000000130', NULL, NULL, 4, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
INSERT INTO `sys_menu` VALUES ('2050000000000000135', '权益删除', 'user:memberbenefit:del', NULL, NULL, '2050000000000000130', NULL, NULL, 5, '1', '2026-04-22 00:00:00', '2026-04-22 00:00:00', '0', '0', 'app_base', NULL, NULL);
