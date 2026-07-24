USE aryn_pay;

-- 以下检查必须返回空集；若存在重复，唯一索引 DDL 会中止。
-- 支付和退款均为资金记录，必须人工核对渠道流水，禁止自动删除或合并。
SELECT tenant_id, out_trade_no, COUNT(*) AS duplicate_count
FROM pay_trade_order
GROUP BY tenant_id, out_trade_no
HAVING COUNT(*) > 1;

SELECT tenant_id, refund_trade_no, COUNT(*) AS duplicate_count
FROM pay_refund_order
GROUP BY tenant_id, refund_trade_no
HAVING COUNT(*) > 1;

SET @trade_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'pay_trade_order'
    AND index_name = 'uk_pay_trade_order_no'
);
SET @trade_index_sql := IF(@trade_index_exists = 0,
  'ALTER TABLE pay_trade_order ADD UNIQUE KEY uk_pay_trade_order_no (tenant_id, out_trade_no)',
  'SELECT 1');
PREPARE trade_index_stmt FROM @trade_index_sql;
EXECUTE trade_index_stmt;
DEALLOCATE PREPARE trade_index_stmt;

SET @refund_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'pay_refund_order'
    AND index_name = 'uk_pay_refund_order_no'
);
SET @refund_index_sql := IF(@refund_index_exists = 0,
  'ALTER TABLE pay_refund_order ADD UNIQUE KEY uk_pay_refund_order_no (tenant_id, refund_trade_no)',
  'SELECT 1');
PREPARE refund_index_stmt FROM @refund_index_sql;
EXECUTE refund_index_stmt;
DEALLOCATE PREPARE refund_index_stmt;
