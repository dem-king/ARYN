-- ============================================================================
-- 销售业绩归属与提成 — 数据模型增量（Boot 单体模式）
--
-- 目标库：aryn_boot（13 张新表在 promotion 域，order_info 加列在 order 域，同库）
-- 背景（2026-11-05 需求「销售业绩归属与提成」，见
--       docs/50-需求文档/2026-11-05-销售业绩归属与提成/IMPLEMENTATION_PLAN.md 第 2/6 节）：
--   公司外勤销售跑成的客户下单后，订单要记录归属销售并据实付（不含运费）计算提成；
--   同时把普通分销的提现改成券包兑换。需求涉及三层数据：
--     ① 归属：销售员、客户归属、换绑日志、待转交认领、客户转交奖励；
--     ② 账本：销售业绩单、月度发放台账、已发放后退款的负数结转、退款幂等记录、全局配置；
--     ③ 改券：券包、券包→券模板、兑换记录。
--   订单表只加两个可空列做归属快照（不加索引，按销售统计一律查 sales_order）。
--
-- 内容：
--   一、销售业绩域 13 张新表（CREATE TABLE IF NOT EXISTS）
--   二、order_info 两个可空列（information_schema 判重，可重复执行）
--   三、8 个字典 + 字典值（sys_dict / sys_dict_value，固定 ID）
--   四、按存量租户补 sales_config 默认行（INSERT ... SELECT ... WHERE NOT EXISTS）
--
-- 幂等：新表 CREATE TABLE IF NOT EXISTS；加列 information_schema 判存在；
--       字典/配置行按固定 ID / 唯一键判重；全程无 DROP / TRUNCATE，不改存量业务数据。
-- 依赖：本脚本依赖存量基线已包含 order_info.group_buy_record_id（114group_buy_order_link.sql）。
-- 注意：菜单/权限见 203sales_performance_menu.sql，定时任务见 204sales_performance_job.sql；
--       租户表清单由 Lead 在 aryn-boot application.yml 的 hx.tenant.tables 维护。
-- 执行：mysql -u root -p aryn_boot < 202sales_performance_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- @DDL-BEGIN 销售业绩域 DDL 区块（与 db/cloud/205sales_performance_incremental.sql 逐列一致，单侧修改必须同步）
-- ============================================================================
-- 一、销售业绩域 13 张新表
-- ============================================================================

-- 2.1 sales_staff 销售员（后台员工账号 + 销售本人的小程序会员账号）
CREATE TABLE IF NOT EXISTS `sales_staff` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `sys_user_id` varchar(32) NOT NULL COMMENT '后台员工账号ID（登记时经 RemoteSysUserService 校验存在）',
  `staff_name` varchar(64) NULL DEFAULT NULL COMMENT '员工姓名快照',
  `phone` varchar(32) NULL DEFAULT NULL COMMENT '员工手机号快照',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门ID快照',
  `dept_name` varchar(64) NULL DEFAULT NULL COMMENT '部门名称快照',
  `member_user_id` varchar(32) NOT NULL COMMENT '销售本人的小程序会员ID（租户内唯一，生成分享参数用）',
  `commission_rate` decimal(10,4) NULL DEFAULT NULL COMMENT '个人提成比例（0-1；NULL 取全局默认比例）',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0在职 1离职',
  `leave_time` datetime NULL DEFAULT NULL COMMENT '离职时间',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_staff_member` (`tenant_id`, `member_user_id`),
  KEY `idx_sales_staff_sys_user` (`tenant_id`, `sys_user_id`),
  KEY `idx_sales_staff_status` (`tenant_id`, `status`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售员' ROW_FORMAT = DYNAMIC;

-- 2.2 sales_customer_bind 客户归属（一个客户同时只归属一个销售）
CREATE TABLE IF NOT EXISTS `sales_customer_bind` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `customer_user_id` varchar(32) NOT NULL COMMENT '客户会员ID',
  `sales_staff_id` varchar(32) NOT NULL COMMENT '归属销售ID',
  `bind_source` varchar(16) NOT NULL COMMENT '绑定来源：SHARE分享 QRCODE推广码 ADMIN后台指派 TRANSFER离职交接 CLAIM首单转交',
  `bind_time` datetime NOT NULL COMMENT '绑定时间',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_customer_bind` (`tenant_id`, `customer_user_id`),
  KEY `idx_sales_customer_bind_staff` (`tenant_id`, `sales_staff_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售客户归属' ROW_FORMAT = DYNAMIC;

-- 2.3 sales_customer_bind_log 换绑日志（只追加，不删除）
CREATE TABLE IF NOT EXISTS `sales_customer_bind_log` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `customer_user_id` varchar(32) NOT NULL COMMENT '客户会员ID',
  `old_sales_staff_id` varchar(32) NULL DEFAULT NULL COMMENT '原归属销售ID（首次绑定为 NULL）',
  `old_sales_staff_name` varchar(64) NULL DEFAULT NULL COMMENT '原归属销售姓名快照',
  `new_sales_staff_id` varchar(32) NOT NULL COMMENT '新归属销售ID',
  `new_sales_staff_name` varchar(64) NULL DEFAULT NULL COMMENT '新归属销售姓名快照',
  `bind_source` varchar(16) NOT NULL COMMENT '变更来源：SHARE分享 QRCODE推广码 ADMIN后台指派 TRANSFER离职交接 CLAIM首单转交',
  `reason` varchar(255) NULL DEFAULT NULL COMMENT '变更原因',
  `operator_id` varchar(32) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) NULL DEFAULT NULL COMMENT '操作人姓名',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_sales_bind_log_customer` (`tenant_id`, `customer_user_id`, `create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售客户换绑日志' ROW_FORMAT = DYNAMIC;

-- 2.4 sales_customer_claim 待转交认领（客户先归属分销员、又点了销售分享链接时生成）
CREATE TABLE IF NOT EXISTS `sales_customer_claim` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `customer_user_id` varchar(32) NOT NULL COMMENT '客户会员ID',
  `sales_staff_id` varchar(32) NOT NULL COMMENT '认领销售ID',
  `source` varchar(16) NOT NULL COMMENT '认领来源：SHARE分享 QRCODE推广码',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0待首单 1已转交 2已失效',
  `claim_time` datetime NOT NULL COMMENT '认领时间',
  `expire_time` datetime NOT NULL COMMENT '失效时间（默认 claim_time + sales_config.claim_expire_days）',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_claim_customer` (`tenant_id`, `customer_user_id`),
  KEY `idx_sales_claim_staff` (`tenant_id`, `sales_staff_id`, `status`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售客户待转交认领' ROW_FORMAT = DYNAMIC;

-- 2.5 sales_transfer_reward 客户转交奖励（分销员 → 销售首单转交一次性奖励）
CREATE TABLE IF NOT EXISTS `sales_transfer_reward` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `customer_user_id` varchar(32) NOT NULL COMMENT '客户会员ID',
  `distributor_user_id` varchar(32) NOT NULL COMMENT '原分销员会员ID（奖励归属人）',
  `sales_staff_id` varchar(32) NOT NULL COMMENT '接收销售ID',
  `biz_order_id` varchar(32) NOT NULL COMMENT '触发转交的业务订单ID',
  `first_order_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '首单实付金额（不含运费）',
  `reward_rate` decimal(10,4) NOT NULL DEFAULT 0.0000 COMMENT '奖励比例快照',
  `reward_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '奖励金额（2 位向下取整）',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0待结算 1已入账 2已作废',
  `settle_time` datetime NULL DEFAULT NULL COMMENT '入账/结算时间',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_transfer_reward` (`tenant_id`, `customer_user_id`, `biz_order_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售客户转交奖励' ROW_FORMAT = DYNAMIC;

-- 2.6 sales_order 销售业绩单（参照 distribution_order 设计，biz_order_id 幂等）
CREATE TABLE IF NOT EXISTS `sales_order` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `biz_order_id` varchar(32) NOT NULL COMMENT '业务订单ID',
  `order_no` varchar(64) NULL DEFAULT NULL COMMENT '订单号快照',
  `buyer_user_id` varchar(32) NOT NULL COMMENT '买家会员ID',
  `sales_staff_id` varchar(32) NOT NULL COMMENT '归属销售ID（订单快照）',
  `staff_name` varchar(64) NULL DEFAULT NULL COMMENT '归属销售姓名快照',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '归属部门ID快照',
  `payment_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额（含运费）',
  `freight_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费金额',
  `commission_base` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '提成基数（实付不含运费）',
  `commission_rate` decimal(10,4) NOT NULL DEFAULT 0.0000 COMMENT '提成比例快照（支付时）',
  `commission_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '提成金额',
  `refunded_base` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计退款基数',
  `refunded_commission` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计扣回提成',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0待确认 1可发放 2已发放 3已全额退款',
  `pay_time` datetime NULL DEFAULT NULL COMMENT '支付时间',
  `settle_at` datetime NULL DEFAULT NULL COMMENT '计划可发放时间（退款风险观察期止）',
  `settle_time` datetime NULL DEFAULT NULL COMMENT '实际可发放时间',
  `payout_id` varchar(32) NULL DEFAULT NULL COMMENT '所属发放批次ID',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_order_biz` (`tenant_id`, `biz_order_id`),
  KEY `idx_sales_order_staff` (`tenant_id`, `sales_staff_id`, `pay_time`),
  KEY `idx_sales_order_status` (`tenant_id`, `status`, `settle_at`),
  KEY `idx_sales_order_payout` (`tenant_id`, `payout_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售业绩单' ROW_FORMAT = DYNAMIC;

-- 2.7 sales_payout 月度发放台账（每个销售每月一条）
CREATE TABLE IF NOT EXISTS `sales_payout` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `sales_staff_id` varchar(32) NOT NULL COMMENT '销售ID',
  `staff_name` varchar(64) NULL DEFAULT NULL COMMENT '销售姓名快照',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门ID快照',
  `payout_month` varchar(7) NOT NULL COMMENT '发放月份（yyyy-MM，批次所属月=上月）',
  `order_count` int NOT NULL DEFAULT 0 COMMENT '业绩单数',
  `trade_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '成交额=Σ(提成基数-已退基数)',
  `commission_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '应发提成=Σ(提成金额-已扣回)',
  `refund_deduct_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '上期退款冲减（正数）',
  `payable_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '实发金额=应发-冲减',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0待确认 1已发放',
  `confirm_by` varchar(60) NULL DEFAULT NULL COMMENT '财务确认人',
  `confirm_time` datetime NULL DEFAULT NULL COMMENT '财务确认发放时间',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_payout_staff_month` (`tenant_id`, `sales_staff_id`, `payout_month`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售提成月度发放台账' ROW_FORMAT = DYNAMIC;

-- 2.8 sales_payout_adjustment 已发放后退款的负数结转（不改已发放台账，结转到下月）
CREATE TABLE IF NOT EXISTS `sales_payout_adjustment` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `sales_staff_id` varchar(32) NOT NULL COMMENT '销售ID',
  `staff_name` varchar(64) NULL DEFAULT NULL COMMENT '销售姓名快照',
  `biz_order_id` varchar(32) NOT NULL COMMENT '业务订单ID',
  `refund_no` varchar(64) NOT NULL COMMENT '退款单号（幂等键）',
  `amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '结转金额（负数）',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0待结转 1已结转',
  `payout_id` varchar(32) NULL DEFAULT NULL COMMENT '结转到的发放批次ID',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_payout_adjustment` (`tenant_id`, `refund_no`),
  KEY `idx_sales_adjustment_staff` (`tenant_id`, `sales_staff_id`, `status`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售提成退款负数结转' ROW_FORMAT = DYNAMIC;

-- 2.9 sales_config 全局配置（每租户单启用行，active_config_key 生成列保证唯一）
CREATE TABLE IF NOT EXISTS `sales_config` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `default_commission_rate` decimal(10,4) NOT NULL DEFAULT 0.0500 COMMENT '全局默认提成比例',
  `referral_reward_rate` decimal(10,4) NOT NULL DEFAULT 0.0000 COMMENT '转交奖励比例（0=不启用）',
  `monthly_cutoff_day` int NOT NULL DEFAULT 1 COMMENT '每月台账生成日（1-28）',
  `refund_risk_days` int NOT NULL DEFAULT 7 COMMENT '退款风险观察期天数（settle_at = pay_time + days）',
  `claim_expire_days` int NOT NULL DEFAULT 90 COMMENT '待转交认领有效期（天）',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0启用 1禁用',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `active_config_key` char(1) GENERATED ALWAYS AS (CASE WHEN status = '0' AND del_flag = '0' THEN '1' ELSE NULL END) STORED COMMENT '单租户启用配置唯一键',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_config_active` (`tenant_id`, `active_config_key`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售业绩全局配置' ROW_FORMAT = DYNAMIC;

-- 2.10 distribution_coupon_pack 券包配置（分销佣金改兑换优惠券）
CREATE TABLE IF NOT EXISTS `distribution_coupon_pack` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `pack_name` varchar(64) NOT NULL COMMENT '券包名称',
  `face_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '券面对额合计',
  `threshold_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛金额',
  `valid_days` int NULL DEFAULT NULL COMMENT '有效天数',
  `required_commission` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '兑换消耗佣金',
  `stock` int NULL DEFAULT NULL COMMENT '库存（NULL=不限）',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0上架 1下架',
  `sort` int NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_distribution_pack_status` (`tenant_id`, `status`, `sort`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分销券包配置' ROW_FORMAT = DYNAMIC;

-- 2.11 distribution_coupon_pack_item 券包→券模板（一个券包可含多个券模板）
CREATE TABLE IF NOT EXISTS `distribution_coupon_pack_item` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `pack_id` varchar(32) NOT NULL COMMENT '券包ID',
  `coupon_info_id` varchar(32) NOT NULL COMMENT '优惠券模板ID',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '发放数量',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_distribution_pack_item` (`tenant_id`, `pack_id`, `coupon_info_id`),
  KEY `idx_distribution_pack_item_pack` (`tenant_id`, `pack_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分销券包优惠券模板明细' ROW_FORMAT = DYNAMIC;

-- 2.12 distribution_coupon_exchange 兑换记录
CREATE TABLE IF NOT EXISTS `distribution_coupon_exchange` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `user_id` varchar(32) NOT NULL COMMENT '兑换用户ID（分销员会员ID）',
  `nickname` varchar(64) NULL DEFAULT NULL COMMENT '用户昵称快照',
  `pack_id` varchar(32) NOT NULL COMMENT '券包ID',
  `pack_name` varchar(64) NULL DEFAULT NULL COMMENT '券包名称快照',
  `face_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '券面额快照',
  `commission_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '消耗佣金',
  `coupon_user_ids` varchar(500) NULL DEFAULT NULL COMMENT '发放的 coupon_user 主键（逗号分隔）',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0发放中 1已发放 2发放失败',
  `exchange_time` datetime NULL DEFAULT NULL COMMENT '兑换时间',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_distribution_exchange_user` (`tenant_id`, `user_id`, `create_time`),
  KEY `idx_distribution_exchange_pack` (`tenant_id`, `pack_id`, `create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分销佣金兑换券包记录' ROW_FORMAT = DYNAMIC;

-- sales_refund_record 销售退款幂等记录（字段同 distribution_refund_record，按 refund_no 幂等）
CREATE TABLE IF NOT EXISTS `sales_refund_record` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `refund_no` varchar(64) NOT NULL COMMENT '退款业务号',
  `biz_order_id` varchar(32) NOT NULL COMMENT '业务订单ID',
  `refund_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '退款总额（含运费）',
  `refund_base_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '退款提成基数（不含运费）',
  `applied` char(1) NOT NULL DEFAULT '0' COMMENT '是否已扣回提成：0否 1是',
  `applied_time` datetime NULL DEFAULT NULL COMMENT '提成扣回完成时间',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示 1隐藏',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户id',
  `create_by` varchar(60) NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) NULL DEFAULT NULL COMMENT '修改人',
  `version` int NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sales_refund_no` (`tenant_id`, `refund_no`),
  KEY `idx_sales_refund_order` (`tenant_id`, `biz_order_id`),
  KEY `idx_sales_refund_pending` (`tenant_id`, `applied`, `create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售退款幂等记录' ROW_FORMAT = DYNAMIC;

-- ============================================================================
-- 二、order_info 归属销售快照列（2.13，information_schema 判重）
-- ============================================================================
SET @add_sales_staff_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `sales_staff_id` varchar(32) NULL COMMENT ''归属销售ID快照（下单时写入，换绑不影响）'' AFTER `group_buy_record_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'sales_staff_id'
);
PREPARE stmt FROM @add_sales_staff_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sales_staff_name = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `sales_staff_name` varchar(64) NULL COMMENT ''归属销售姓名快照'' AFTER `sales_staff_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'sales_staff_name'
);
PREPARE stmt FROM @add_sales_staff_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：应返回 2（order_info 归属销售两列均已存在）
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info'
       AND COLUMN_NAME IN ('sales_staff_id', 'sales_staff_name')) AS added_order_info_columns;
-- @DDL-END

-- ============================================================================
-- 三、字典：销售业绩 / 分销售券（sys_dict + sys_dict_value，固定 ID，按 type 判重）
-- ============================================================================

-- 3.1 字典类型
INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000201', 'sales_staff_status', '销售员状态', '0', '0在职 1离职', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_staff_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000202', 'sales_bind_source', '销售客户归属来源', '0', 'SHARE/QRCODE/ADMIN/TRANSFER/CLAIM', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_bind_source');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000203', 'sales_order_status', '销售业绩单状态', '0', '0待确认 1可发放 2已发放 3已全额退款', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_order_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000204', 'sales_payout_status', '销售提成发放台账状态', '0', '0待确认 1已发放', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_payout_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000205', 'sales_claim_status', '销售待转交认领状态', '0', '0待首单 1已转交 2已失效', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_claim_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000206', 'sales_transfer_reward_status', '销售客户转交奖励状态', '0', '0待结算 1已入账 2已作废', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'sales_transfer_reward_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000207', 'distribution_coupon_pack_status', '分销券包状态', '0', '0上架 1下架', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'distribution_coupon_pack_status');

INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000208', 'distribution_coupon_exchange_status', '分销佣金兑换状态', '0', '0发放中 1已发放 2发放失败', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'distribution_coupon_exchange_status');

-- 3.2 字典值：sales_staff_status（0在职 / 1离职）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000211', d.`id`, '在职', '0', 'sales_staff_status', '0', NULL, 1, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_staff_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000212', d.`id`, '离职', '1', 'sales_staff_status', '0', NULL, 2, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'sales_staff_status' AND d.`del_flag` = '0' LIMIT 1;

-- 3.3 字典值：sales_bind_source（SHARE/QRCODE/ADMIN/TRANSFER/CLAIM）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000221', d.`id`, '分享链接', 'SHARE', 'sales_bind_source', '0', NULL, 1, '0', NOW(), 'system', 'primary'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000222', d.`id`, '推广码', 'QRCODE', 'sales_bind_source', '0', NULL, 2, '0', NOW(), 'system', 'primary'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000223', d.`id`, '后台指派', 'ADMIN', 'sales_bind_source', '0', NULL, 3, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000224', d.`id`, '离职交接', 'TRANSFER', 'sales_bind_source', '0', NULL, 4, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000225', d.`id`, '首单转交', 'CLAIM', 'sales_bind_source', '0', NULL, 5, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_bind_source' AND d.`del_flag` = '0' LIMIT 1;

-- 3.4 字典值：sales_order_status（0待确认 / 1可发放 / 2已发放 / 3已全额退款）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000231', d.`id`, '待确认', '0', 'sales_order_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_order_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000232', d.`id`, '可发放', '1', 'sales_order_status', '0', NULL, 2, '0', NOW(), 'system', 'primary'
FROM `sys_dict` d WHERE d.`type` = 'sales_order_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000233', d.`id`, '已发放', '2', 'sales_order_status', '0', NULL, 3, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_order_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000234', d.`id`, '已全额退款', '3', 'sales_order_status', '0', NULL, 4, '0', NOW(), 'system', 'danger'
FROM `sys_dict` d WHERE d.`type` = 'sales_order_status' AND d.`del_flag` = '0' LIMIT 1;

-- 3.5 字典值：sales_payout_status（0待确认 / 1已发放）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000241', d.`id`, '待确认', '0', 'sales_payout_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_payout_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000242', d.`id`, '已发放', '1', 'sales_payout_status', '0', NULL, 2, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_payout_status' AND d.`del_flag` = '0' LIMIT 1;

-- 3.6 字典值：sales_claim_status（0待首单 / 1已转交 / 2已失效）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000251', d.`id`, '待首单', '0', 'sales_claim_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_claim_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000252', d.`id`, '已转交', '1', 'sales_claim_status', '0', NULL, 2, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_claim_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000253', d.`id`, '已失效', '2', 'sales_claim_status', '0', NULL, 3, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'sales_claim_status' AND d.`del_flag` = '0' LIMIT 1;

-- 3.7 字典值：sales_transfer_reward_status（0待结算 / 1已入账 / 2已作废）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000261', d.`id`, '待结算', '0', 'sales_transfer_reward_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'sales_transfer_reward_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000262', d.`id`, '已入账', '1', 'sales_transfer_reward_status', '0', NULL, 2, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'sales_transfer_reward_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000263', d.`id`, '已作废', '2', 'sales_transfer_reward_status', '0', NULL, 3, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'sales_transfer_reward_status' AND d.`del_flag` = '0' LIMIT 1;

-- 3.8 字典值：distribution_coupon_pack_status（0上架 / 1下架）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000271', d.`id`, '上架', '0', 'distribution_coupon_pack_status', '0', NULL, 1, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_pack_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000272', d.`id`, '下架', '1', 'distribution_coupon_pack_status', '0', NULL, 2, '0', NOW(), 'system', 'info'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_pack_status' AND d.`del_flag` = '0' LIMIT 1;

-- 3.9 字典值：distribution_coupon_exchange_status（0发放中 / 1已发放 / 2发放失败）
INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000281', d.`id`, '发放中', '0', 'distribution_coupon_exchange_status', '0', NULL, 1, '0', NOW(), 'system', 'warning'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_exchange_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000282', d.`id`, '已发放', '1', 'distribution_coupon_exchange_status', '0', NULL, 2, '0', NOW(), 'system', 'success'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_exchange_status' AND d.`del_flag` = '0' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`,`show_class`)
SELECT '2130000000000000283', d.`id`, '发放失败', '2', 'distribution_coupon_exchange_status', '0', NULL, 3, '0', NOW(), 'system', 'danger'
FROM `sys_dict` d WHERE d.`type` = 'distribution_coupon_exchange_status' AND d.`del_flag` = '0' LIMIT 1;

-- ============================================================================
-- 四、sales_config 按存量租户补默认行（每租户单启用配置，重复执行不新增）
-- ============================================================================
INSERT INTO `sales_config`
  (`id`,`default_commission_rate`,`referral_reward_rate`,`monthly_cutoff_day`,`refund_risk_days`,`claim_expire_days`,`status`,
   `create_time`,`del_flag`,`tenant_id`,`create_by`,`version`)
SELECT MD5(CONCAT(t.`id`, ':sales_config')), 0.0500, 0.0000, 1, 7, 90, '0', NOW(), '0', t.`id`, 'system', 0
FROM `sys_tenant` t
WHERE t.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sales_config` c
    WHERE c.`tenant_id` = t.`id` AND c.`del_flag` = '0'
  );

-- 自检：13 张新表数量 / order_info 两列 / 8 个字典类型 / 24 个字典值 / sales_config 行数
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('sales_staff','sales_customer_bind','sales_customer_bind_log','sales_customer_claim',
                          'sales_transfer_reward','sales_order','sales_payout','sales_payout_adjustment','sales_config',
                          'sales_refund_record','distribution_coupon_pack','distribution_coupon_pack_item',
                          'distribution_coupon_exchange')) AS new_tables,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info'
       AND COLUMN_NAME IN ('sales_staff_id','sales_staff_name')) AS order_info_columns,
  (SELECT COUNT(*) FROM `sys_dict`
     WHERE `type` IN ('sales_staff_status','sales_bind_source','sales_order_status','sales_payout_status',
                      'sales_claim_status','sales_transfer_reward_status','distribution_coupon_pack_status',
                      'distribution_coupon_exchange_status') AND `del_flag` = '0') AS sales_dict_types,
  (SELECT COUNT(*) FROM `sys_dict_value`
     WHERE `dict_type` IN ('sales_staff_status','sales_bind_source','sales_order_status','sales_payout_status',
                           'sales_claim_status','sales_transfer_reward_status','distribution_coupon_pack_status',
                           'distribution_coupon_exchange_status') AND `del_flag` = '0') AS sales_dict_values,
  (SELECT COUNT(*) FROM `sales_config` WHERE `del_flag` = '0') AS sales_config_rows;

SET FOREIGN_KEY_CHECKS = 1;
