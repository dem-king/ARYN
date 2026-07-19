USE aryn_boot;

UPDATE goods_appraise AS appraise
JOIN (
  SELECT id
  FROM (
    SELECT id,
      ROW_NUMBER() OVER (PARTITION BY tenant_id, order_item_id ORDER BY create_time ASC, id ASC) AS row_num
    FROM goods_appraise
    WHERE del_flag = '0' AND order_item_id IS NOT NULL
  ) AS ranked
  WHERE ranked.row_num > 1
) AS duplicate_appraise ON duplicate_appraise.id = appraise.id
SET appraise.del_flag = '1';

ALTER TABLE goods_appraise
  ADD COLUMN active_order_item_id varchar(32)
    GENERATED ALWAYS AS (CASE WHEN del_flag = '0' THEN order_item_id ELSE NULL END) STORED COMMENT '有效评价订单项唯一键',
  ADD UNIQUE KEY uk_goods_appraise_active_item (tenant_id, active_order_item_id);
