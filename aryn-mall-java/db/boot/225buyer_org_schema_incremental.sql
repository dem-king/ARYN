-- ============================================================================
-- 采购主体基础层（四张表 + 岸上车/订单/营销锁字段与索引）— Boot 单体模式
--
-- 目标库：aryn_boot
-- 背景（2026-11-12 需求「新人首单双轨与采购主体改造」P0）：
--   岸上企业车当前只有 owner_user_id + 收货地址，没有「这家工厂是谁」；
--   船供/企业首单也无法按主体判定。P0 先落采购主体基础层：
--     1. buyer_org / buyer_org_member / buyer_org_apply / buyer_org_invite_code 四张表；
--     2. shared_cart 增加主体快照，并把岸上收集中车唯一键从「发起人」改为「发起人 + 主体」；
--     3. order_info 增加主体快照列与查询索引；
--     4. promotion_lock 增加首单主体列与索引（P1 首单锁预留，P0 不写入）。
--
-- 口径：
--   · 主体只能在同租户内可见/可绑；credit_code 为空存 NULL（唯一索引允许多个 NULL）；
--   · 岸上车历史数据 buyer_org_id 为 NULL：生成键为「owner_user_id:」，仍是一人一张，不阻塞历史；
--   · 新建岸上车必须带 buyer_org_id，同一发起人可在不同工厂各开一张收集中车。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + information_schema 判断 + PREPARE 动态执行，
--       可重复执行；不删除存量业务数据，仅重建 shared_cart 的岸上唯一约束。
-- 注意：shared_cart 需要 DROP/ADD 生成列与索引，生产请低峰执行并先备份（回滚见 05 文档）。
--
-- 执行：mysql -u root -p aryn_boot < 225buyer_org_schema_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、采购主体四张表
--   @DDL-BEGIN / @DDL-END 区块与 cloud/237 逐段比对，避免双模式结构漂移。
-- ============================================================================
-- @DDL-BEGIN
CREATE TABLE IF NOT EXISTS `buyer_org` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `org_code` varchar(64) NOT NULL COMMENT '主体编码，租户内唯一',
  `org_name` varchar(200) NOT NULL COMMENT '工厂/企业/餐厅名称',
  `org_type` char(2) NOT NULL DEFAULT '2' COMMENT '主体类型：1企业 2工厂 3园区餐厅',
  `credit_code` varchar(32) NULL COMMENT '统一社会信用代码；空值存 NULL，避免唯一索引冲突',
  `license_url` varchar(500) NULL COMMENT '营业执照/门头照',
  `province` varchar(64) NULL COMMENT '省',
  `city` varchar(64) NULL COMMENT '市',
  `district` varchar(64) NULL COMMENT '区/县',
  `address` varchar(500) NULL COMMENT '详细地址',
  `contact_name` varchar(64) NULL COMMENT '联系人',
  `contact_phone` varchar(32) NULL COMMENT '联系电话',
  `audit_status` char(2) NOT NULL DEFAULT '0' COMMENT '审核状态：0待审核 1已通过 2已驳回 3已停用',
  `audit_remark` varchar(500) NULL COMMENT '审核意见',
  `audit_by` varchar(64) NULL COMMENT '审核人',
  `audit_time` datetime NULL COMMENT '审核时间',
  `owner_user_id` varchar(32) NULL COMMENT '厂长/管理员商城用户ID',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1正常 0停用',
  `bind_source` varchar(32) NOT NULL DEFAULT 'APPLY_AUDIT' COMMENT '绑定来源：APPLY_AUDIT/INVITE_CODE/MEMBER_ADD/ADMIN_ADD/SHARE_CART/MANUAL',
  `remark` varchar(500) NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `update_by` varchar(60) NULL COMMENT '修改人',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_time` datetime NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_buyer_org_code` (`tenant_id`, `org_code`),
  UNIQUE KEY `uk_buyer_org_credit` (`tenant_id`, `credit_code`),
  KEY `idx_buyer_org_status` (`tenant_id`, `audit_status`, `status`),
  KEY `idx_buyer_org_owner` (`tenant_id`, `owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购主体（工厂/企业/园区餐厅）';

CREATE TABLE IF NOT EXISTS `buyer_org_member` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `buyer_org_id` varchar(32) NOT NULL COMMENT '采购主体ID',
  `user_id` varchar(32) NOT NULL COMMENT '商城用户ID',
  `member_role` char(2) NOT NULL DEFAULT '2' COMMENT '角色：1厂长/管理员 2采购员 3业务员 4查看者',
  `can_edit` char(2) NOT NULL DEFAULT '1' COMMENT '1可加购/改数/建车',
  `can_confirm` char(2) NOT NULL DEFAULT '0' COMMENT '1可确认提交',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1正常 0已移除',
  `bind_source` varchar(32) NOT NULL DEFAULT 'APPLY_AUDIT' COMMENT '绑定来源，口径同 buyer_org.bind_source',
  `apply_id` varchar(32) NULL COMMENT '关联申请单ID',
  `join_time` datetime NULL COMMENT '加入时间',
  `leave_time` datetime NULL COMMENT '退出/移除时间',
  `remark` varchar(500) NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `update_by` varchar(60) NULL COMMENT '修改人',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_time` datetime NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_buyer_org_member` (`tenant_id`, `buyer_org_id`, `user_id`),
  KEY `idx_buyer_org_member_user` (`tenant_id`, `user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购主体成员';

CREATE TABLE IF NOT EXISTS `buyer_org_apply` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `apply_no` varchar(64) NOT NULL COMMENT '申请单号',
  `user_id` varchar(32) NOT NULL COMMENT '申请人商城用户ID',
  `apply_type` char(2) NOT NULL COMMENT '申请类型：1创建主体 2加入已有主体',
  `buyer_org_id` varchar(32) NULL COMMENT '加入时填已有主体ID',
  `org_name` varchar(200) NOT NULL COMMENT '申请主体名称',
  `org_type` char(2) NOT NULL DEFAULT '2' COMMENT '主体类型：1企业 2工厂 3园区餐厅',
  `credit_code` varchar(32) NULL COMMENT '统一社会信用代码',
  `license_url` varchar(500) NULL COMMENT '营业执照/门头照',
  `province` varchar(64) NULL COMMENT '省',
  `city` varchar(64) NULL COMMENT '市',
  `district` varchar(64) NULL COMMENT '区/县',
  `address` varchar(500) NULL COMMENT '详细地址',
  `contact_name` varchar(64) NULL COMMENT '联系人',
  `contact_phone` varchar(32) NULL COMMENT '联系电话',
  `apply_role` char(2) NOT NULL DEFAULT '2' COMMENT '申请角色：1厂长/管理员 2采购员 3业务员 4查看者',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1待审核 2通过 3驳回 4取消',
  `audit_by` varchar(64) NULL COMMENT '审核人',
  `audit_time` datetime NULL COMMENT '审核时间',
  `audit_remark` varchar(500) NULL COMMENT '审核意见',
  `matched_org_id` varchar(32) NULL COMMENT '审核通过后落的主体ID',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `update_by` varchar(60) NULL COMMENT '修改人',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_time` datetime NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_buyer_org_apply_user` (`tenant_id`, `user_id`, `status`),
  KEY `idx_buyer_org_apply_audit` (`tenant_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购主体创建/加入申请';

CREATE TABLE IF NOT EXISTS `buyer_org_invite_code` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `buyer_org_id` varchar(32) NOT NULL COMMENT '采购主体ID',
  `code` varchar(32) NOT NULL COMMENT '邀请码，租户内唯一',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `max_uses` int NOT NULL DEFAULT 0 COMMENT '最大使用次数，0 表示不限',
  `used_count` int NOT NULL DEFAULT 0 COMMENT '已使用次数',
  `status` char(2) NOT NULL DEFAULT '1' COMMENT '状态：1有效 0已失效',
  `remark` varchar(255) NULL COMMENT '备注',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `update_by` varchar(60) NULL COMMENT '修改人',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_time` datetime NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_buyer_org_invite_code` (`tenant_id`, `code`),
  KEY `idx_buyer_org_invite_org` (`tenant_id`, `buyer_org_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购主体邀请码';
-- @DDL-END

-- ============================================================================
-- 二、shared_cart：主体快照 + 岸上唯一键改为「发起人 + 主体」
--   顺序不可颠倒：先删旧索引 → 删旧生成列 → 加新生成列 → 加新唯一索引。
-- ============================================================================
SET @add_shared_buyer_org_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `buyer_org_id` varchar(32) NULL COMMENT ''采购主体ID（岸上企业车必填；船供车为空）'' AFTER `user_address_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'buyer_org_id'
);
PREPARE stmt FROM @add_shared_buyer_org_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_shared_buyer_org_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `buyer_org_name` varchar(200) NULL COMMENT ''采购主体名称快照'' AFTER `buyer_org_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'buyer_org_name'
);
PREPARE stmt FROM @add_shared_buyer_org_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_shore_active_index = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `shared_cart` DROP INDEX `uk_shared_cart_shore_active`',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart'
    AND INDEX_NAME = 'uk_shared_cart_shore_active'
  LIMIT 1
);
PREPARE stmt FROM @drop_shore_active_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_shore_active_owner = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `shared_cart` DROP COLUMN `shore_active_owner`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'shore_active_owner'
);
PREPARE stmt FROM @drop_shore_active_owner; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_shore_active_scope = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `shore_active_scope` varchar(80) GENERATED ALWAYS AS (CASE WHEN `cart_type` = ''2'' AND `status` = ''2'' AND `del_flag` = ''0'' THEN CONCAT(`owner_user_id`, '':'', COALESCE(`buyer_org_id`, '''')) ELSE NULL END) STORED COMMENT ''岸上车进行中标记：岸上+收集中+未删除时为「发起人:主体」，其余NULL以允许多行''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'shore_active_scope'
);
PREPARE stmt FROM @add_shore_active_scope; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_shore_scope_index = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD UNIQUE KEY `uk_shared_cart_shore_scope` (`tenant_id`, `shore_active_scope`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart'
    AND INDEX_NAME = 'uk_shared_cart_shore_scope'
  LIMIT 1
);
PREPARE stmt FROM @add_shore_scope_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 三、order_info：主体快照列与按主体统计索引（P1 首单资格直接复用）
-- ============================================================================
SET @add_order_buyer_org_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `buyer_org_id` varchar(32) NULL COMMENT ''采购主体ID（企业采购快照，历史单为空）'' AFTER `purchase_scene`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'buyer_org_id'
);
PREPARE stmt FROM @add_order_buyer_org_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_buyer_org_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `buyer_org_name` varchar(200) NULL COMMENT ''采购主体名称快照'' AFTER `buyer_org_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'buyer_org_name'
);
PREPARE stmt FROM @add_order_buyer_org_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_buyer_org_credit = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `buyer_org_credit_code` varchar(32) NULL COMMENT ''统一社会信用代码快照'' AFTER `buyer_org_name`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'buyer_org_credit_code'
);
PREPARE stmt FROM @add_order_buyer_org_credit; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_buyer_org_index = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD KEY `idx_order_buyer_org` (`tenant_id`, `buyer_org_id`, `purchase_scene`, `pay_status`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info'
    AND INDEX_NAME = 'idx_order_buyer_org'
  LIMIT 1
);
PREPARE stmt FROM @add_order_buyer_org_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 四、promotion_lock：首单主体列与索引（P1 首单锁预留，P0 不写入）
-- ============================================================================
SET @add_lock_subject_type = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `promotion_lock` ADD COLUMN `subject_type` varchar(16) NULL COMMENT ''首单主体类型：1船 / 2采购主体；普通锁为空'' AFTER `user_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_lock' AND COLUMN_NAME = 'subject_type'
);
PREPARE stmt FROM @add_lock_subject_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_lock_subject_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `promotion_lock` ADD COLUMN `subject_id` varchar(32) NULL COMMENT ''船舶ID或采购主体ID'' AFTER `subject_type`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_lock' AND COLUMN_NAME = 'subject_id'
);
PREPARE stmt FROM @add_lock_subject_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_lock_subject_index = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `promotion_lock` ADD KEY `idx_promotion_lock_subject` (`tenant_id`, `activity_id`, `subject_type`, `subject_id`, `status`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_lock'
    AND INDEX_NAME = 'idx_promotion_lock_subject'
  LIMIT 1
);
PREPARE stmt FROM @add_lock_subject_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
