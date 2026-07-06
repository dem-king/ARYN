-- Points Goods & Exchange Record Tables Init Script
-- @author aryn @date 2026/7/6

-- 1. points_goods
CREATE TABLE IF NOT EXISTS points_goods (
    id varchar(64) NOT NULL COMMENT '主键',
    name varchar(128) NOT NULL COMMENT '商品名称',
    cover varchar(512) DEFAULT NULL COMMENT '封面图',
    type varchar(16) NOT NULL COMMENT '商品类型：goods-实物商品；coupon-优惠券；gift-赠品；',
    target_id varchar(64) DEFAULT NULL COMMENT '目标ID（优惠券ID或商品SPU ID）',
    points_price int NOT NULL COMMENT '所需积分',
    stock int NOT NULL COMMENT '库存',
    limit_per_user int DEFAULT 0 COMMENT '每人限购数量，0表示不限购',
    start_time datetime DEFAULT NULL COMMENT '活动开始时间',
    end_time datetime DEFAULT NULL COMMENT '活动结束时间',
    create_by varchar(64) DEFAULT NULL COMMENT '创建人',
    update_by varchar(64) DEFAULT NULL COMMENT '修改人',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_time datetime DEFAULT NULL COMMENT '修改时间',
    del_flag varchar(2) DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    tenant_id varchar(64) DEFAULT NULL COMMENT '租户ID',
    version int DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    KEY idx_type (type),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商品';

-- 2. points_exchange_record
CREATE TABLE IF NOT EXISTS points_exchange_record (
    id varchar(64) NOT NULL COMMENT '主键',
    user_id varchar(64) NOT NULL COMMENT '用户ID',
    points_goods_id varchar(64) NOT NULL COMMENT '积分商品ID',
    points_cost int NOT NULL COMMENT '消耗积分',
    type varchar(16) DEFAULT NULL COMMENT '商品类型：goods-实物商品；coupon-优惠券；gift-赠品；',
    status varchar(16) DEFAULT 'pending' COMMENT '状态：pending-待处理；success-成功；failed-失败；',
    create_by varchar(64) DEFAULT NULL COMMENT '创建人',
    update_by varchar(64) DEFAULT NULL COMMENT '修改人',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_time datetime DEFAULT NULL COMMENT '修改时间',
    del_flag varchar(2) DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    tenant_id varchar(64) DEFAULT NULL COMMENT '租户ID',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_points_goods_id (points_goods_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换记录';