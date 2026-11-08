-- ============================================================================
-- 支付安全配置底座（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：2026-11-06《支付管理后台自助配置与交易闭环完善方案》P0 任务 2–4。
--   1) pay_config 只保留稳定路由与启用状态；
--   2) pay_config_revision 冻结渠道/商户/应用/环境身份，历史交易不再受换商户影响；
--   3) pay_config_material 只保存 AES-256-GCM 密文，明文不落库、不回显；
--   4) pay_trade_order 记录发起支付时的身份版本，回调/查单/退款按原交易身份取材料。
--
-- 幂等：列/索引均先查 information_schema；新表使用 IF NOT EXISTS；菜单 INSERT IGNORE。
-- 禁止：DROP/TRUNCATE 任何支付或退款记录；旧明文字段保留，迁移成功后的清理由
--       发布流程单独批准，不在本脚本内自动删除。
-- 执行：mysql -u root -p aryn_boot < 209pay_credential_vault_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 一、迁移前置检查（必须返回空集；发现冲突请人工核对后重跑）
-- ----------------------------------------------------------------------------
-- 同一租户同一渠道+终端只允许一个路由；历史数据若重复，唯一索引会中止。
SELECT tenant_id, `type`, terminal_type, COUNT(*) AS duplicate_count
FROM pay_config
WHERE del_flag = '0'
GROUP BY tenant_id, `type`, terminal_type
HAVING COUNT(*) > 1;

-- 旧支付单若已经绑定身份版本，但版本记录缺失，必须先人工修复，不能自动覆盖。
-- 首次执行时版本表尚不存在，用动态 SQL 跳过检查。
SET @revision_table_exists := (SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'pay_config_revision');
SET @check_sql := IF(@revision_table_exists = 1,
  'SELECT t.tenant_id, t.out_trade_no, t.config_revision_id
   FROM pay_trade_order t
   LEFT JOIN pay_config_revision r
     ON r.id = t.config_revision_id AND r.tenant_id = t.tenant_id AND r.del_flag = ''0''
   WHERE t.del_flag = ''0''
     AND t.config_revision_id IS NOT NULL
     AND t.config_revision_id <> ''''
     AND r.id IS NULL
   LIMIT 100',
  'SELECT 1');
PREPARE stmt FROM @check_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 二、pay_config：启用状态、活动身份版本、并发版本号
-- ----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'pay_config' AND column_name = 'enabled');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pay_config ADD COLUMN `enabled` char(2) NOT NULL DEFAULT ''0'' COMMENT ''是否启用：0.停用；1.启用；'' AFTER public_key_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'pay_config' AND column_name = 'active_revision_id');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pay_config ADD COLUMN `active_revision_id` varchar(32) NULL DEFAULT NULL COMMENT ''当前生效的身份版本ID'' AFTER `enabled`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'pay_config' AND column_name = 'config_version');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pay_config ADD COLUMN `config_version` int NOT NULL DEFAULT 0 COMMENT ''并发版本号，启用/停用时做乐观锁'' AFTER active_revision_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 三、pay_config_revision：不可变支付身份版本
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pay_config_revision` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
  `config_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付配置路由ID',
  `revision_no` int NOT NULL COMMENT '版本序号',
  `channel` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付渠道：1.微信；2.支付宝；',
  `terminal_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付端类型',
  `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用ID',
  `mch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户号/收款方PID',
  `environment` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'prod' COMMENT '环境：prod/sandbox',
  `wx_public_key_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信支付公钥ID',
  `material_refs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '材料引用 JSON：用途 -> 材料ID',
  `identity_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '身份+材料指纹，用于迁移幂等',
  `check_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '本地检查：0.未检查；1.通过；2.失败',
  `check_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '本地检查摘要，不含密钥内容',
  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_pay_config_revision_no` (`tenant_id`, `config_id`, `revision_no`) USING BTREE,
  UNIQUE KEY `uk_pay_config_revision_identity` (`tenant_id`, `config_id`, `identity_hash`) USING BTREE,
  KEY `idx_pay_config_revision_merchant` (`tenant_id`, `channel`, `app_id`, `mch_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付身份版本' ROW_FORMAT = DYNAMIC;

-- ----------------------------------------------------------------------------
-- 四、pay_config_material：支付材料密文
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pay_config_material` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `tenant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户id',
  `config_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付配置路由ID',
  `purpose` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '材料用途',
  `ciphertext` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密文（Base64）',
  `nonce` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'GCM nonce（Base64）',
  `key_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '加密密钥ID',
  `algorithm` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '加密算法',
  `fingerprint` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '明文 SHA-256 指纹',
  `content_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '内容类型：PEM/DER/RAW',
  `cert_serial_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '证书序列号',
  `cert_not_before` datetime NULL DEFAULT NULL COMMENT '证书生效时间',
  `cert_not_after` datetime NULL DEFAULT NULL COMMENT '证书到期时间',
  `byte_size` int NULL DEFAULT NULL COMMENT '明文字节数',
  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_pay_config_material_config` (`tenant_id`, `config_id`, `purpose`) USING BTREE,
  KEY `idx_pay_config_material_fingerprint` (`tenant_id`, `config_id`, `fingerprint`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付材料密文' ROW_FORMAT = DYNAMIC;

-- ----------------------------------------------------------------------------
-- 五、pay_trade_order：冻结发起支付时的身份版本
-- ----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'pay_trade_order' AND column_name = 'config_revision_id');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pay_trade_order ADD COLUMN `config_revision_id` varchar(32) NULL DEFAULT NULL COMMENT ''发起支付时冻结的支付身份版本ID'' AFTER user_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'pay_trade_order' AND index_name = 'idx_pay_trade_order_revision');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE pay_trade_order ADD INDEX `idx_pay_trade_order_revision` (`tenant_id`, `config_revision_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 六、支付配置按钮权限（启用/检查/上传资料/迁移为独立权限）
--   固定 ID，INSERT IGNORE；授权按现有“支付配置修改”权限推导，不扩大无关角色权限。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
('2140000000000000301','上传支付资料','pay:payconfig:material',NULL,NULL,'1531445896986435585','','',6,'1',NOW(),'0','0','app_base','system'),
('2140000000000000302','启用停用支付渠道','pay:payconfig:enable',NULL,NULL,'1531445896986435585','','',7,'1',NOW(),'0','0','app_base','system'),
('2140000000000000303','检查支付资料','pay:payconfig:check',NULL,NULL,'1531445896986435585','','',8,'1',NOW(),'0','0','app_base','system'),
('2140000000000000304','迁移旧支付配置','pay:payconfig:migrate',NULL,NULL,'1531445896986435585','','',9,'1',NOW(),'0','0','app_base','system');

-- 6.1 已分配支付配置菜单的租户补授（含父容器，避免树构建丢失入口）
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(receiver.`tenant_id`, ':menu:', m.`id`)), receiver.`tenant_id`, m.`id`, NOW(), 'system'
FROM (SELECT DISTINCT `tenant_id` FROM `sys_tenant_menu`
      WHERE `menu_id` IN ('1531445896986435585', '1531446010039705602')) receiver
JOIN `sys_menu` m
  ON m.`id` IN ('1531445896986435585',
                '2140000000000000301','2140000000000000302','2140000000000000303','2140000000000000304')
 AND m.`del_flag` = '0';

-- 6.2 已拥有“支付配置修改”按钮的角色补授
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(receiver.`role_id`, ':menu:', m.`id`)), receiver.`role_id`, m.`id`, NOW(), receiver.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `sys_role_menu`
      WHERE `menu_id` = '1531446010039705602') receiver
JOIN `sys_menu` m
  ON m.`id` IN ('2140000000000000301','2140000000000000302','2140000000000000303','2140000000000000304')
 AND m.`del_flag` = '0';

-- 自检：四个权限均已注册
SELECT COUNT(*) AS pay_credential_permissions
FROM `sys_menu`
WHERE `id` IN ('2140000000000000301','2140000000000000302','2140000000000000303','2140000000000000304')
  AND `del_flag` = '0';

-- 注意：菜单/权限在登录时快照，执行后需清理 Redis `menu_cache::*` 并重新登录。
SET FOREIGN_KEY_CHECKS = 1;
