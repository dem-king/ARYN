USE aryn_boot;

ALTER TABLE order_info ADD COLUMN request_id varchar(64) DEFAULT NULL COMMENT '客户端请求幂等号' AFTER order_no;
UPDATE order_info SET request_id = CONCAT('legacy-', id) WHERE request_id IS NULL OR request_id = '';
ALTER TABLE order_info
  MODIFY COLUMN request_id varchar(64) NOT NULL COMMENT '客户端请求幂等号',
  ADD UNIQUE KEY uk_order_request (tenant_id, user_id, request_id),
  ADD UNIQUE KEY uk_order_no (tenant_id, order_no);

UPDATE shopping_cart AS cart
JOIN (
  SELECT id
  FROM (
    SELECT id,
      ROW_NUMBER() OVER (PARTITION BY tenant_id, user_id, sku_id ORDER BY update_time DESC, create_time DESC, id DESC) AS row_num
    FROM shopping_cart
    WHERE del_flag = '0'
  ) AS ranked
  WHERE ranked.row_num > 1
) AS duplicate_cart ON duplicate_cart.id = cart.id
SET cart.del_flag = '1';

ALTER TABLE shopping_cart
  ADD COLUMN active_sku_id varchar(32)
    GENERATED ALWAYS AS (CASE WHEN del_flag = '0' THEN sku_id ELSE NULL END) STORED COMMENT '有效购物车SKU唯一键',
  ADD UNIQUE KEY uk_shopping_cart_active_sku (tenant_id, user_id, active_sku_id);
