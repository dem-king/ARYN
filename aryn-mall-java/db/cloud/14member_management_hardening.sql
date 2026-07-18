USE aryn_user;

ALTER TABLE user_info
  ADD COLUMN total_point int NOT NULL DEFAULT 0 COMMENT '累计获得积分' AFTER point;
UPDATE user_info SET total_point = GREATEST(COALESCE(point, 0), 0);

DELETE duplicate_rel FROM user_tag_rel duplicate_rel
JOIN user_tag_rel retained ON retained.tenant_id = duplicate_rel.tenant_id
 AND retained.user_id = duplicate_rel.user_id AND retained.tag_id = duplicate_rel.tag_id
 AND retained.id < duplicate_rel.id;
ALTER TABLE user_tag_rel
  ADD UNIQUE KEY uk_user_tag_tenant (tenant_id, user_id, tag_id);

DELETE duplicate_rel FROM member_benefit_level_rel duplicate_rel
JOIN member_benefit_level_rel retained ON retained.tenant_id = duplicate_rel.tenant_id
 AND retained.benefit_id = duplicate_rel.benefit_id AND retained.level_id = duplicate_rel.level_id
 AND retained.id < duplicate_rel.id;
ALTER TABLE member_benefit_level_rel
  ADD UNIQUE KEY uk_benefit_level_tenant (tenant_id, benefit_id, level_id);

CREATE TABLE member_order_growth (
  id varchar(32) NOT NULL COMMENT '主键', order_id varchar(32) NOT NULL COMMENT '订单ID',
  order_no varchar(32) DEFAULT NULL COMMENT '订单编号', user_id varchar(32) NOT NULL COMMENT '用户ID',
  goods_payment_amount decimal(10,2) NOT NULL COMMENT '实付商品金额',
  points_awarded int NOT NULL DEFAULT 0 COMMENT '本次发放积分', tenant_id varchar(32) NOT NULL COMMENT '租户ID',
  create_time datetime NOT NULL COMMENT '创建时间', PRIMARY KEY (id),
  UNIQUE KEY uk_member_growth_order (tenant_id, order_id), KEY idx_member_growth_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员订单成长幂等记录';

USE aryn_order;
ALTER TABLE order_info
  ADD COLUMN member_discount_price decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '会员折扣优惠金额' AFTER coupon_price,
  ADD COLUMN points_multiplier decimal(10,2) NOT NULL DEFAULT 1.00 COMMENT '下单时会员积分倍率' AFTER member_discount_price;
ALTER TABLE order_item
  ADD COLUMN member_discount_price decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '会员折扣优惠金额' AFTER coupon_price;

USE aryn_promotion;
ALTER TABLE coupon_user
  ADD COLUMN source_type varchar(32) DEFAULT NULL COMMENT '发放来源类型' AFTER tenant_id,
  ADD COLUMN source_id varchar(128) DEFAULT NULL COMMENT '发放来源ID' AFTER source_type,
  ADD UNIQUE KEY uk_coupon_user_source (tenant_id, user_id, source_type, source_id);
