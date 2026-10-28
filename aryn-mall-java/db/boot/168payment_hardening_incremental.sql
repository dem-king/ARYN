USE aryn_boot;

-- 以下检查必须返回空集；若存在重复，唯一索引 DDL 会中止。
-- 支付配置与售后单均为资金相关配置，发现重复必须人工核对后再处理，禁止自动删除。
SELECT tenant_id, `type`, terminal_type, COUNT(*) AS duplicate_count
FROM pay_config
WHERE del_flag = '0'
GROUP BY tenant_id, `type`, terminal_type
HAVING COUNT(*) > 1;

SELECT tenant_id, order_item_id, COUNT(*) AS duplicate_count
FROM order_refund
WHERE del_flag = '0'
  AND IFNULL(`status`, '') <> '9'
GROUP BY tenant_id, order_item_id
HAVING COUNT(*) > 1;

SET @pay_config_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'pay_config'
    AND index_name = 'uk_pay_config_type_terminal'
);
SET @pay_config_index_sql := IF(@pay_config_index_exists = 0,
  'ALTER TABLE pay_config ADD UNIQUE KEY uk_pay_config_type_terminal ((IF(del_flag = ''0'', CONCAT(tenant_id, '':'', `type`, '':'', terminal_type), NULL)))',
  'SELECT 1');
PREPARE pay_config_index_stmt FROM @pay_config_index_sql;
EXECUTE pay_config_index_stmt;
DEALLOCATE PREPARE pay_config_index_stmt;

SET @order_refund_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'order_refund'
    AND index_name = 'uk_order_refund_active_item'
);
SET @order_refund_index_sql := IF(@order_refund_index_exists = 0,
  'ALTER TABLE order_refund ADD UNIQUE KEY uk_order_refund_active_item ((IF(del_flag = ''0'' AND IFNULL(`status`, '''') <> ''9'', CONCAT(tenant_id, '':'', order_item_id), NULL)))',
  'SELECT 1');
PREPARE order_refund_index_stmt FROM @order_refund_index_sql;
EXECUTE order_refund_index_stmt;
DEALLOCATE PREPARE order_refund_index_stmt;

SET FOREIGN_KEY_CHECKS = 1;
