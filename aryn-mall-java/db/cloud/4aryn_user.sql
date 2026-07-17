USE aryn_user;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
                                   PRIMARY KEY (`id`) USING BTREE,
                                   UNIQUE KEY `uk_social_account_app_id` ((IF(`del_flag` = '0', CONCAT('active:', `app_id`), CONCAT('deleted:', `id`))))
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

-- ----------------------------
-- user_info表增加会员相关字段
-- ----------------------------
ALTER TABLE `user_info` ADD COLUMN `member_level_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会员等级ID' AFTER `open_id`;
ALTER TABLE `user_info` ADD COLUMN `point` int NOT NULL DEFAULT 0 COMMENT '积分余额' AFTER `member_level_id`;
ALTER TABLE `user_info` ADD COLUMN `balance` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '储值余额' AFTER `point`;
ALTER TABLE `user_info` ADD COLUMN `total_consume` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费金额' AFTER `balance`;

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
    INDEX `idx_benefit_id` (`benefit_id`) USING BTREE,
    INDEX `idx_level_id` (`level_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '权益等级关联表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
