-- Distribution Tables Init Script
-- @author 雨滴kian @date 2025/4/8

-- 1. distribution_config
CREATE TABLE IF NOT EXISTS distribution_config (
    id varchar(64) NOT NULL,
    config_name varchar(128) NOT NULL,
    commission_rate decimal(5,4) NOT NULL,
    commission_rate_level2 decimal(5,4) DEFAULT NULL,
    min_withdraw_amount decimal(12,2) NOT NULL,
    settle_cycle_days int DEFAULT NULL,
    status varchar(2) NOT NULL DEFAULT '1',
    create_by varchar(64) DEFAULT NULL,
    update_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    del_flag varchar(2) NOT NULL DEFAULT '0',
    active_config_key char(1) GENERATED ALWAYS AS (CASE WHEN status = '0' AND del_flag = '0' THEN '1' ELSE NULL END) STORED,
    tenant_id varchar(64) NOT NULL,
    version int DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_distribution_config_active (tenant_id, active_config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. distribution_user
CREATE TABLE IF NOT EXISTS distribution_user (
    id varchar(64) NOT NULL,
    user_id varchar(64) NOT NULL,
    nickname varchar(128) DEFAULT NULL,
    avatar varchar(512) DEFAULT NULL,
    inviter_user_id varchar(64) DEFAULT NULL,
    total_commission decimal(12,2) DEFAULT 0.00,
    available_commission decimal(12,2) DEFAULT 0.00,
    pending_commission decimal(12,2) DEFAULT 0.00,
    withdrawn_commission decimal(12,2) DEFAULT 0.00,
    frozen_commission decimal(12,2) DEFAULT 0.00,
    commission_debt decimal(12,2) DEFAULT 0.00,
    subordinate_count int DEFAULT 0,
    status varchar(2) DEFAULT '0',
    create_by varchar(64) DEFAULT NULL,
    update_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    del_flag varchar(2) DEFAULT '0',
    tenant_id varchar(64) DEFAULT NULL,
    version int DEFAULT 0,
    active_user_id varchar(64) GENERATED ALWAYS AS (CASE WHEN del_flag = '0' THEN user_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_distribution_user_active (tenant_id, active_user_id),
    KEY idx_inviter_user_id (inviter_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. distribution_order
CREATE TABLE IF NOT EXISTS distribution_order (
    id varchar(64) NOT NULL,
    biz_order_id varchar(64) NOT NULL,
    buyer_user_id varchar(64) NOT NULL,
    distributor_user_id varchar(64) NOT NULL,
    order_amount decimal(12,2) NOT NULL,
    freight_amount decimal(12,2) NOT NULL DEFAULT 0.00,
    commission_base_amount decimal(12,2) NOT NULL,
    commission_amount decimal(12,2) NOT NULL,
    refunded_base_amount decimal(12,2) NOT NULL DEFAULT 0.00,
    refunded_commission_amount decimal(12,2) NOT NULL DEFAULT 0.00,
    commission_level int DEFAULT 1,
    status varchar(2) DEFAULT '0',
    settle_at datetime DEFAULT NULL,
    settle_time datetime DEFAULT NULL,
    create_by varchar(64) DEFAULT NULL,
    update_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    del_flag varchar(2) DEFAULT '0',
    tenant_id varchar(64) DEFAULT NULL,
    version int DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_distribution_order_level (tenant_id, biz_order_id, commission_level),
    KEY idx_distributor_user_id (distributor_user_id),
    KEY idx_buyer_user_id (buyer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. distribution_commission_flow
CREATE TABLE IF NOT EXISTS distribution_commission_flow (
    id varchar(64) NOT NULL,
    user_id varchar(64) NOT NULL,
    biz_order_id varchar(64) DEFAULT NULL,
    flow_type varchar(16) NOT NULL,
    amount decimal(12,2) NOT NULL,
    balance_after decimal(12,2) NOT NULL,
    remark varchar(256) DEFAULT NULL,
    create_by varchar(64) DEFAULT NULL,
    update_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    del_flag varchar(2) DEFAULT '0',
    tenant_id varchar(64) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_biz_order_id (biz_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. distribution_withdraw
CREATE TABLE IF NOT EXISTS distribution_withdraw (
    id varchar(64) NOT NULL,
    withdraw_no varchar(64) DEFAULT NULL,
    user_id varchar(64) NOT NULL,
    amount decimal(12,2) NOT NULL,
    status varchar(2) DEFAULT '0',
    account_type varchar(32) DEFAULT NULL,
    account_name varchar(128) DEFAULT NULL,
    account_no varchar(512) DEFAULT NULL,
    reject_reason varchar(256) DEFAULT NULL,
    remark varchar(256) DEFAULT NULL,
    audit_time datetime DEFAULT NULL,
    audit_by varchar(64) DEFAULT NULL,
    payout_no varchar(64) DEFAULT NULL,
    payout_time datetime DEFAULT NULL,
    payout_by varchar(64) DEFAULT NULL,
    create_by varchar(64) DEFAULT NULL,
    update_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    del_flag varchar(2) DEFAULT '0',
    tenant_id varchar(64) DEFAULT NULL,
    version int DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_withdraw_no (withdraw_no),
    UNIQUE KEY uk_distribution_withdraw_payout (tenant_id, payout_no),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. distribution_refund_record
CREATE TABLE IF NOT EXISTS distribution_refund_record (
    id varchar(64) NOT NULL,
    refund_no varchar(64) NOT NULL,
    biz_order_id varchar(64) NOT NULL,
    refund_amount decimal(12,2) NOT NULL,
    refund_base_amount decimal(12,2) NOT NULL,
    applied char(1) NOT NULL DEFAULT '0',
    applied_time datetime DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    tenant_id varchar(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_distribution_refund_no (tenant_id, refund_no),
    KEY idx_distribution_refund_order (tenant_id, biz_order_id),
    KEY idx_distribution_refund_pending (tenant_id, applied, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
