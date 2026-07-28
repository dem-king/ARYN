USE aryn_nacos;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for config_info
-- ----------------------------
DROP TABLE IF EXISTS `config_info`;
CREATE TABLE `config_info`  (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
                                `data_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
                                `group_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'group_id',
                                `content` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
                                `md5` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'md5',
                                `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
                                `src_user` text CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL COMMENT 'source user',
                                `src_ip` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'source ip',
                                `app_name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'app_name',
                                `tenant_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT '' COMMENT '租户字段',
                                `c_desc` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'configuration description',
                                `c_use` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'configuration usage',
                                `effect` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '配置生效的描述',
                                `type` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '配置的类型',
                                `c_schema` text CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL COMMENT '配置的模式',
                                `encrypted_data_key` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT '密钥',
                                PRIMARY KEY (`id`) USING BTREE,
                                UNIQUE INDEX `uk_configinfo_datagrouptenant`(`data_id` ASC, `group_id` ASC, `tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = 'config_info' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of config_info
-- ----------------------------
INSERT INTO `config_info` VALUES (1, 'application-dev.yml', 'DEFAULT_GROUP', 'spring:\n  cache: \n    type: redis\n  data:  \n    redis:\n      host: aryn-redis\n      port: 6379\n      # password: \n      database: 11\n  servlet:\n    multipart:\n      location: /data/tmp\n  cloud:\n    sentinel:\n      eager: true\n      transport:\n        dashboard: aryn-sentinel:8080\n\nrocketmq:\n  name-server: aryn-rocketmq:9876\n  producer:\n    group: producer_group\n    retry-times-when-send-failed: 5\n    send-message-timeout: 5000\njackson:\n  default-property-inclusion: always\n  date-format: yyyy-mm-dd hh:mm:ss\n\nsa-token:\n  token-name: satoken\n  timeout: 2592000\n  active-timeout: -1\n  is-concurrent: true\n  is-share: false\n  is-read-cookie: false\n  token-style: uuid\n  is-log: true\n  is-print: false\n\nmybatis-plus:\n  mapper-locations: classpath*:/mapper/*Mapper.xml\n  type-handlers-package: com.aryn.cloud.common.myabtis.handler\n  global-config:\n    banner: false\n    db-config:\n      id-type: auto\n      where-strategy: not_empty\n      insert-strategy: not_empty\n      update-strategy: not_null\n  configuration:\n    jdbc-type-for-null: \'null\'\n    call-setters-on-nulls: true\n    shrink-whitespaces-in-sql: true\nmanagement:\n  endpoints:\n    web:\n      exposure:\n        include: \'*\'\n  endpoint:\n    restart:\n      enabled: true\n    health:\n      show-details: ALWAYS\n\nlogging:\n  file:\n    name: logs/${spring.application.name}/debug.log\n    \nxxl:\n  job:\n    adminAddresses: http://localhost:7002/xxl-job-admin  \n    accessToken: hxsadadasdasdasda\n\nhx:\n  mall:\n    notifyDomain: https://aryn-test.Aetheryn.cn\n    orderTimeOut: 30\n    defaultReceiverTime: 10\n    defaultAppraiseTime: 10\n    logisticsKey: \'121231321231\'\nsms:\n  ali:\n    accessKeyId: xxxxxxxxx\n    accessKeySecret: xxxxxxxxxxxxxx\n    loginTemplateCode: SMS_xxxxxxxxxx\n    loginSignName: 悦航购\n\ncallback:\n  prefix:\n    pay: pay\n    logistics: mall-order\n\n', '06691ed71335dfbd8b2dfb2b20b4a654', '2025-07-06 22:56:39', '2026-03-11 22:47:38', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (2, 'aryn-gateway-dev.yml', 'DEFAULT_GROUP', 'spring:\n  cloud:\n    gateway:\n      server:\n        webflux:\n          discovery:\n            locator:\n              enabled: true\n          httpclient:\n            max-header-size: 64KB\n          routes:\n            - id: aryn-auth  \n              uri: lb://aryn-auth \n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/auth/**\n\n            - id: aryn-upms-biz\n              uri: lb://aryn-upms-biz   \n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/upms/** \n\n            - id: aryn-user-biz \n              uri: lb://aryn-user-biz  \n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/mall-user/**\n\n            - id: aryn-product-biz \n              uri: lb://aryn-product-biz \n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/product/**\n\n            - id: aryn-message-biz-ws\n              uri: lb:ws://aryn-message-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/message/ws/**\n\n            - id: aryn-message-biz\n              uri: lb://aryn-message-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/message/**\n\n            - id: aryn-promotion-biz \n              uri: lb://aryn-promotion-biz  \n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/promotion/**\n                \n            - id: aryn-pay-biz  \n              uri: lb://aryn-pay-biz  \n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/pay/** \n\n            - id: aryn-order-biz  \n              uri: lb://aryn-order-biz  \n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/mall-order/**\n                  \n            - id: aryn-generator \n              uri: lb://aryn-generator\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/gen/**  \n\nsecure:\n  ignore:\n    urls:\n    - /swagger-resources/**\n    - /webjars/**\n    - /v3/api-docs/**\n    - /swagger-ui.html/**\n    - /doc.html/**\n    - /error/**\n    - /favicon.ico/**\n    - /swagger-ui/**\n    - /actuator/**\n    - /auth/token/**\n    - /auth/toc-token/**\n    - /auth/code/**\n    - /upms/sms/**\n    - /upms/user/check/phone\n    - /upms/tenant/register\n    - /upms/tenantpackage/list\n    - /upms/app/tenant/shop-info\n    - /pay/notify/**\n    - /shop/app/**\n    - /product/app/**\n    - /promotion/app/pagedesign/**\n    - /promotion/app/groupbuy/activity/**\n    - /promotion/app/groupbuy/record/page\n    - /promotion/app/couponinfo/page', '33c18ac6d0dbfcd0c4c99c487fe79790', '2025-07-06 22:56:39', '2026-04-05 14:21:28', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (3, 'aryn-auth-dev.yml', 'DEFAULT_GROUP', 'aj: \n  captcha: \n    cache-type: redis\n    water-mark: aryn\n\nencode:\n  key: VyBcekSelErhMYN4       ', 'a35fcf93f603eae9d5033d7675c16a7a', '2025-07-06 22:56:39', '2025-07-06 22:56:39', 'nacos', '0:0:0:0:0:0:0:1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (4, 'aryn-monitor-dev.yml', 'DEFAULT_GROUP', 'spring:\n  security:\n    user:\n      name: \"admin\"\n      password: \"123456\"', 'ccc4df5a18a2e54e744312541fcb8b32', '2025-07-06 22:56:39', '2025-07-06 22:56:39', 'nacos', '0:0:0:0:0:0:0:1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (5, 'aryn-user-biz-dev.yml', 'DEFAULT_GROUP', 'spring:  \n  datasource:\n    type: com.alibaba.druid.pool.DruidDataSource\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    username: root\n    password: 123456\n    url: jdbc:mysql://aryn-mysql:3306/aryn_user?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT%2B8&allowMultiQueries=true&allowPublicKeyRetrieval=true\n\nlogging:\n  level:\n    com.aryn.cloud.user.mapper: debug\n\nhx:\n  tenant:\n    tables: \n      - user_address\n      - user_info\n      - member_level\n      - member_level_record\n      - points_config\n      - points_record\n      - sign_in_config\n      - sign_in_record\n      - recharge_config\n      - balance_record\n      - recharge_order\n      - member_tag\n      - user_tag_rel\n      - member_benefit\n      - member_benefit_level_rel\n      - member_order_growth\n      - social_account\n      - social_user\n', '00025a6f64c459784638aa44b087d522', '2025-07-06 22:56:39', '2026-04-05 14:26:13', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (7, 'aryn-upms-biz-dev.yml', 'DEFAULT_GROUP', '\nspring:  \n  datasource:\n    type: com.alibaba.druid.pool.DruidDataSource\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    username: root\n    password: 123456\n    url: jdbc:mysql://aryn-mysql:3306/aryn_upms?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true\n\nlogging:\n  level:\n    com.aryn.cloud.upms.mapper: debug\n\nhx:\n  tenant:\n    tables: \n      - sys_user\n      - sys_user_role\n      - sys_storage_config\n      - sys_role_menu\n      - sys_tenant_menu\n      - sys_role\n      - sys_login_log\n      - sys_log\n      - sys_dept\n      - sys_material\n      - sys_user_wechat_binding\n      - sys_material_group', 'c125df1a1662fd93649b2ea96b843b7d', '2025-07-06 22:56:39', '2026-04-05 14:26:03', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (8, 'aryn-product-biz-dev.yml', 'DEFAULT_GROUP', 'spring:  \n  datasource:\n    type: com.alibaba.druid.pool.DruidDataSource\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    username: root\n    password: 123456\n    url: jdbc:mysql://aryn-mysql:3306/aryn_product?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true\n\nlogging:\n  level:\n    com.aryn.cloud.product.mapper: debug\n\nhx:\n  tenant:\n    tables: \n      - goods_appraise\n      - goods_brand\n      - goods_category\n      - goods_collect\n      - goods_footprint\n      - goods_sku\n      - goods_specs\n      - goods_specs_value\n      - goods_spu\n      - product_order_pay_record\n      - product_refund_stock_record\n', 'd269b74fa09ebc2ba0a793b18bd7ef4c', '2025-07-06 22:56:39', '2026-04-05 14:25:53', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (9, 'aryn-promotion-biz-dev.yml', 'DEFAULT_GROUP', '\nspring:  \n  datasource:\n    type: com.alibaba.druid.pool.DruidDataSource\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    username: root\n    password: 123456\n    url: jdbc:mysql://aryn-mysql:3306/aryn_promotion?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true\n\nlogging:\n  level:\n    com.aryn.cloud.promotion.mapper: debug\n\n\nhx:\n  tenant:\n    tables: \n      - coupon_goods\n      - coupon_info\n      - coupon_user\n      - distribution_config\n      - distribution_user\n      - distribution_order\n      - distribution_commission_flow\n      - distribution_withdraw\n      - distribution_refund_record\n      - group_buy_activity\n      - group_buy_record\n      - group_buy_member\n      - page_design\n      - page_design_version\n      - page_design_template\n', '0caafa04a3f23a4190be39571fce0f7a', '2025-07-06 22:56:39', '2026-04-05 14:25:35', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (10, 'aryn-pay-biz-dev.yml', 'DEFAULT_GROUP', 'spring:  \n  datasource:\n    type: com.alibaba.druid.pool.DruidDataSource\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    username: root\n    password: 123456\n    url: jdbc:mysql://aryn-mysql:3306/aryn_pay?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true\n\ncert-dir: /root/aryn/cert\n\nlogging:\n  level:\n    com.aryn.cloud.pay.mapper: debug   \n\nhx:\n  tenant:\n    tables: \n      - pay_config\n      - pay_trade_order\n      - pay_notify_record\n      - pay_refund_order\n', '3d98dc71c35d4561a78f8ddc26c8822d', '2025-07-06 22:56:39', '2026-04-05 14:24:43', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (12, 'aryn-order-biz-dev.yml', 'DEFAULT_GROUP', 'spring:  \n  datasource:\n    type: com.alibaba.druid.pool.DruidDataSource\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    username: root\n    password: 123456\n    url: jdbc:mysql://aryn-mysql:3306/aryn_order?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true\n\nlogging:\n  level:\n    com.aryn.cloud.order.mapper: debug\n\nhx:\n  tenant:\n    tables: \n      - order_info\n      - order_item\n      - order_delivery\n      - order_delivery_logistics\n      - order_config\n      - order_refund\n      - shopping_cart\n      - order_delivery_task\n      - order_delivery_task_item\n      - order_delivery_evidence\n      - order_delivery_task_log\n      - order_delivery_area\n      \n            \n', 'a570e63d3b82e0565343b59ded7d4a9c', '2025-07-06 22:56:39', '2026-04-05 14:24:55', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');
INSERT INTO `config_info` VALUES (13, 'aryn-generator-dev.yml', 'DEFAULT_GROUP', 'spring:  \n  datasource:\n    dynamic:\n      primary: master   # 指定默认数据源\n      datasource:\n        master:         # 注意，这里要嵌套在 datasource 下\n          url: jdbc:mysql://aryn-mysql:3306/aryn_gen?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true\n          username: root\n          password: 123456\n          driver-class-name: com.mysql.cj.jdbc.Driver\n          type: com.alibaba.druid.pool.DruidDataSource\nlogging:\n  level:\n    com.aryn.cloud.generator.mapper: debug     \n    \nhx:\n  tenant:\n    validate-schema: false\n    tables: []', 'e416b251bf4209a3054e22543a7bc81e', '2025-12-09 11:22:14', '2026-04-05 14:25:14', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');

-- ----------------------------
-- Table structure for config_info_beta
-- ----------------------------
DROP TABLE IF EXISTS `config_info_beta`;
CREATE TABLE `config_info_beta`  (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
                                     `data_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
                                     `group_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
                                     `app_name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'app_name',
                                     `content` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
                                     `beta_ips` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'betaIps',
                                     `md5` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'md5',
                                     `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
                                     `src_user` text CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL COMMENT 'source user',
                                     `src_ip` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'source ip',
                                     `tenant_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT '' COMMENT '租户字段',
                                     `encrypted_data_key` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT '密钥',
                                     PRIMARY KEY (`id`) USING BTREE,
                                     UNIQUE INDEX `uk_configinfobeta_datagrouptenant`(`data_id` ASC, `group_id` ASC, `tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = 'config_info_beta' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of config_info_beta
-- ----------------------------

-- ----------------------------
-- Table structure for config_info_gray
-- ----------------------------
DROP TABLE IF EXISTS `config_info_gray`;
CREATE TABLE `config_info_gray`  (
                                     `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
                                     `data_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'data_id',
                                     `group_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'group_id',
                                     `content` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'content',
                                     `md5` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'md5',
                                     `src_user` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT 'src_user',
                                     `src_ip` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'src_ip',
                                     `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_create',
                                     `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_modified',
                                     `app_name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'app_name',
                                     `tenant_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT '' COMMENT 'tenant_id',
                                     `gray_name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'gray_name',
                                     `gray_rule` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'gray_rule',
                                     `encrypted_data_key` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '' COMMENT 'encrypted_data_key',
                                     PRIMARY KEY (`id`) USING BTREE,
                                     UNIQUE INDEX `uk_configinfogray_datagrouptenantgray`(`data_id` ASC, `group_id` ASC, `tenant_id` ASC, `gray_name` ASC) USING BTREE,
                                     INDEX `idx_dataid_gmt_modified`(`data_id` ASC, `gmt_modified` ASC) USING BTREE,
                                     INDEX `idx_gmt_modified`(`gmt_modified` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = 'config_info_gray' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of config_info_gray
-- ----------------------------

-- ----------------------------
-- Table structure for config_info_tag
-- ----------------------------
DROP TABLE IF EXISTS `config_info_tag`;
CREATE TABLE `config_info_tag`  (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
                                    `data_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
                                    `group_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
                                    `tenant_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT '' COMMENT 'tenant_id',
                                    `tag_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'tag_id',
                                    `app_name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'app_name',
                                    `content` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
                                    `md5` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'md5',
                                    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
                                    `src_user` text CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL COMMENT 'source user',
                                    `src_ip` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'source ip',
                                    PRIMARY KEY (`id`) USING BTREE,
                                    UNIQUE INDEX `uk_configinfotag_datagrouptenanttag`(`data_id` ASC, `group_id` ASC, `tenant_id` ASC, `tag_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = 'config_info_tag' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of config_info_tag
-- ----------------------------

-- Table structure for config_tags_relation
-- ----------------------------
DROP TABLE IF EXISTS `config_tags_relation`;
CREATE TABLE `config_tags_relation`  (
                                         `id` bigint NOT NULL COMMENT 'id',
                                         `tag_name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'tag_name',
                                         `tag_type` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'tag_type',
                                         `data_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
                                         `group_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
                                         `tenant_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT '' COMMENT 'tenant_id',
                                         `nid` bigint NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增长标识',
                                         PRIMARY KEY (`nid`) USING BTREE,
                                         UNIQUE INDEX `uk_configtagrelation_configidtag`(`id` ASC, `tag_name` ASC, `tag_type` ASC) USING BTREE,
                                         INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = 'config_tag_relation' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of config_tags_relation
-- ----------------------------

-- ----------------------------
-- Table structure for group_capacity
-- ----------------------------
DROP TABLE IF EXISTS `group_capacity`;
CREATE TABLE `group_capacity`  (
                                   `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `group_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT 'Group ID，空字符表示整个集群',
                                   `quota` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '配额，0表示使用默认值',
                                   `usage` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用量',
                                   `max_size` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
                                   `max_aggr_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '聚合子配置最大个数，，0表示使用默认值',
                                   `max_aggr_size` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
                                   `max_history_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '最大变更历史数量',
                                   `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
                                   PRIMARY KEY (`id`) USING BTREE,
                                   UNIQUE INDEX `uk_group_id`(`group_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '集群、各Group容量信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of group_capacity
-- ----------------------------
INSERT INTO `group_capacity` VALUES (1, '', 0, 20, 0, 0, 0, 0, '2025-06-15 22:49:08', '2026-04-05 06:21:15');

-- ----------------------------
-- Table structure for his_config_info
-- ----------------------------
DROP TABLE IF EXISTS `his_config_info`;
CREATE TABLE `his_config_info`  (
                                    `id` bigint UNSIGNED NOT NULL COMMENT 'id',
                                    `nid` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增标识',
                                    `data_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
                                    `group_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
                                    `app_name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'app_name',
                                    `content` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
                                    `md5` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'md5',
                                    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
                                    `src_user` text CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL COMMENT 'source user',
                                    `src_ip` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'source ip',
                                    `op_type` char(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'operation type',
                                    `tenant_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT '' COMMENT '租户字段',
                                    `encrypted_data_key` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT '密钥',
                                    `publish_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT 'formal' COMMENT 'publish type gray or formal',
                                    `gray_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'gray name',
                                    `ext_info` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL COMMENT 'ext info',
                                    PRIMARY KEY (`nid`) USING BTREE,
                                    INDEX `idx_gmt_create`(`gmt_create` ASC) USING BTREE,
                                    INDEX `idx_gmt_modified`(`gmt_modified` ASC) USING BTREE,
                                    INDEX `idx_did`(`data_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 194 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '多租户改造' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of his_config_info
-- ----------------------------

-- ----------------------------
-- Table structure for permissions
-- ----------------------------
DROP TABLE IF EXISTS `permissions`;
CREATE TABLE `permissions`  (
                                `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'role',
                                `resource` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'resource',
                                `action` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'action',
                                UNIQUE INDEX `uk_role_permission`(`role` ASC, `resource` ASC, `action` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of permissions
-- ----------------------------

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles`  (
                          `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'username',
                          `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'role',
                          UNIQUE INDEX `idx_user_role`(`username` ASC, `role` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of roles
-- ----------------------------
INSERT INTO `roles` VALUES ('nacos', 'ROLE_ADMIN');

-- ----------------------------
-- Table structure for tenant_capacity
-- ----------------------------
DROP TABLE IF EXISTS `tenant_capacity`;
CREATE TABLE `tenant_capacity`  (
                                    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                    `tenant_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT 'Tenant ID',
                                    `quota` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '配额，0表示使用默认值',
                                    `usage` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用量',
                                    `max_size` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
                                    `max_aggr_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '聚合子配置最大个数',
                                    `max_aggr_size` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
                                    `max_history_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '最大变更历史数量',
                                    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
                                    PRIMARY KEY (`id`) USING BTREE,
                                    UNIQUE INDEX `uk_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '租户容量信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of tenant_capacity
-- ----------------------------
INSERT INTO `tenant_capacity` VALUES (1, 'dubbo', 0, 6, 0, 0, 0, 0, '2025-06-15 22:49:08', '2026-03-11 15:34:01');
INSERT INTO `tenant_capacity` VALUES (2, 'public', 0, 11, 0, 0, 0, 0, '2025-07-16 13:41:08', '2026-04-05 06:21:15');

-- ----------------------------
-- Table structure for tenant_info
-- ----------------------------
DROP TABLE IF EXISTS `tenant_info`;
CREATE TABLE `tenant_info`  (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
                                `kp` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT 'kp',
                                `tenant_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT '' COMMENT 'tenant_id',
                                `tenant_name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT '' COMMENT 'tenant_name',
                                `tenant_desc` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'tenant_desc',
                                `create_source` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT 'create_source',
                                `gmt_create` bigint NOT NULL COMMENT '创建时间',
                                `gmt_modified` bigint NOT NULL COMMENT '修改时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                UNIQUE INDEX `uk_tenant_info_kptenantid`(`kp` ASC, `tenant_id` ASC) USING BTREE,
                                INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = 'tenant_info' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of tenant_info
-- ----------------------------
INSERT INTO `tenant_info` VALUES (1, '1', 'dubbo', 'dubbo', 'dubbo', 'nacos', 1740028215790, 1740028215790);

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
                          `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'username',
                          `password` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'password',
                          `enabled` tinyint(1) NOT NULL COMMENT 'enabled',
                          PRIMARY KEY (`username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES ('nacos', '$2a$10$tUiUXHTZVAN077j51yNBY.agNmwGno9Ln69FQsLZvLRxpKch4/acm', 1);

-- 站内信与客服会话服务配置
INSERT INTO `config_info` VALUES (14, 'aryn-message-biz-dev.yml', 'DEFAULT_GROUP', 'spring:\n  datasource:\n    type: com.alibaba.druid.pool.DruidDataSource\n    driver-class-name: com.mysql.cj.jdbc.Driver\n    username: root\n    password: 123456\n    url: jdbc:mysql://aryn-mysql:3306/aryn_message?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true\n\nlogging:\n  level:\n    com.aryn.cloud.message.mapper: debug\n\nhx:\n  tenant:\n    tables:\n      - message_notice\n      - message_recipient\n      - message_dispatch_task\n      - message_channel_task\n      - message_conversation\n      - message_participant\n      - message_chat\n      - message_agent\n      - message_assignment_log\n', 'e7cb3c0ed2efaea15e51cd19cdb71d23', '2026-07-21 22:30:00', '2026-07-21 22:30:00', 'nacos', '127.0.0.1', '', 'public', '', NULL, NULL, 'yaml', NULL, '');

SET FOREIGN_KEY_CHECKS = 1;
