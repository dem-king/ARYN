USE aryn_promotion;

UPDATE distribution_config AS config
JOIN (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY create_time DESC, id DESC) AS row_num
  FROM distribution_config
  WHERE status = '0' AND del_flag = '0'
) AS ranked ON ranked.id = config.id
SET config.status = '1'
WHERE ranked.row_num > 1;

ALTER TABLE distribution_config
  ADD COLUMN active_config_key char(1)
    GENERATED ALWAYS AS (CASE WHEN status = '0' AND del_flag = '0' THEN '1' ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_distribution_config_active (tenant_id, active_config_key);

ALTER TABLE distribution_user
  ADD COLUMN pending_commission decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '待结算佣金' AFTER available_commission,
  ADD COLUMN commission_debt decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '退款产生的佣金欠款' AFTER frozen_commission,
  ADD COLUMN active_user_id varchar(32)
    GENERATED ALWAYS AS (CASE WHEN del_flag = '0' THEN user_id ELSE NULL END) STORED;

ALTER TABLE distribution_user
  DROP INDEX uk_distribution_user_user_id,
  ADD UNIQUE KEY uk_distribution_user_active (tenant_id, active_user_id);

UPDATE distribution_user AS parent
LEFT JOIN (
  SELECT tenant_id, inviter_user_id, COUNT(*) AS child_count
  FROM distribution_user
  WHERE del_flag = '0' AND inviter_user_id IS NOT NULL AND inviter_user_id <> ''
  GROUP BY tenant_id, inviter_user_id
) AS children
  ON children.tenant_id = parent.tenant_id AND children.inviter_user_id = parent.user_id
SET parent.subordinate_count = COALESCE(children.child_count, 0)
WHERE parent.del_flag = '0';

ALTER TABLE distribution_order
  ADD COLUMN freight_amount decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费金额' AFTER order_amount,
  ADD COLUMN commission_base_amount decimal(10,2) DEFAULT NULL COMMENT '佣金计算基数（不含运费，NULL表示历史口径未知）' AFTER freight_amount,
  ADD COLUMN refunded_base_amount decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计退款佣金基数' AFTER commission_amount,
  ADD COLUMN refunded_commission_amount decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计回退佣金' AFTER refunded_base_amount,
  ADD COLUMN settle_at datetime DEFAULT NULL COMMENT '计划结算时间' AFTER status;

UPDATE distribution_order
SET settle_at = COALESCE(settle_time, DATE_ADD(create_time, INTERVAL 7 DAY));

ALTER TABLE distribution_order
  DROP INDEX uk_distribution_order_biz_order;

UPDATE distribution_order
SET biz_order_id = LEFT(biz_order_id, CHAR_LENGTH(biz_order_id) - 3)
WHERE commission_level = 2
  AND RIGHT(biz_order_id, 3) = '_L2';

ALTER TABLE distribution_order
  ADD UNIQUE KEY uk_distribution_order_level (tenant_id, biz_order_id, commission_level);

ALTER TABLE distribution_withdraw
  MODIFY COLUMN account_no varchar(512) DEFAULT NULL COMMENT '加密收款账号',
  ADD COLUMN payout_no varchar(64) DEFAULT NULL COMMENT '线下打款流水号' AFTER audit_by,
  ADD COLUMN payout_time datetime DEFAULT NULL COMMENT '线下打款时间' AFTER payout_no,
  ADD COLUMN payout_by varchar(60) DEFAULT NULL COMMENT '线下打款确认人' AFTER payout_time,
  ADD UNIQUE KEY uk_distribution_withdraw_payout (tenant_id, payout_no);

CREATE TABLE distribution_refund_record (
  id varchar(32) NOT NULL COMMENT '主键',
  refund_no varchar(64) NOT NULL COMMENT '退款业务号',
  biz_order_id varchar(32) NOT NULL COMMENT '业务订单ID',
  refund_amount decimal(10,2) NOT NULL COMMENT '退款总额（含运费）',
  refund_base_amount decimal(10,2) NOT NULL COMMENT '退款佣金基数（不含运费）',
  applied char(1) NOT NULL DEFAULT '0' COMMENT '是否已回退佣金：0否 1是',
  applied_time datetime DEFAULT NULL COMMENT '佣金回退完成时间',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  tenant_id varchar(32) NOT NULL COMMENT '租户id',
  PRIMARY KEY (id),
  UNIQUE KEY uk_distribution_refund_no (tenant_id, refund_no),
  KEY idx_distribution_refund_order (tenant_id, biz_order_id),
  KEY idx_distribution_refund_pending (tenant_id, applied, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销退款幂等记录';
