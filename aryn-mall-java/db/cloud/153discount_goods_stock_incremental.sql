-- 悦航购折扣商品级限量库存（Cloud 微服务模式）
-- 目标库：aryn_promotion
-- 背景：折扣会场需要「仅剩 N 件」。商品级真限量：
--       1) discount_goods 增 stock（总限量，NULL=不限量，与存量行为逐行等价）与
--          sold_count（已占用数量，下单占用、取消/退款回补）；
--       2) 新增 discount_stock_lock 记录每笔订单对折扣商品的占用，
--          唯一键 (tenant_id, order_id, sku_id) 保证重复调用幂等、并发不会重复扣减。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据；
--       存量行 stock 为 NULL、sold_count 为 0，等于不限量，行为与改造前一致。
-- 关联：boot 模式同名增量 153discount_goods_stock_incremental.sql 内容一致（目标库 aryn_boot）。

USE `aryn_promotion`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 总限量：NULL = 不限量（存量行全部为 NULL，不改变既有活动）
SET @add_stock = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `discount_goods` ADD COLUMN `stock` int NULL DEFAULT NULL COMMENT ''限量库存，NULL=不限量；下单占用、取消/退款回补'' AFTER `sku_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount_goods' AND COLUMN_NAME = 'stock'
);
PREPARE stmt FROM @add_stock; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 已占用数量：仅对 stock 非空的商品生效，stock 为 NULL 时保持 0
SET @add_sold = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `discount_goods` ADD COLUMN `sold_count` int NOT NULL DEFAULT ''0'' COMMENT ''已占用数量（下单占位，取消/退款回补）；stock 为 NULL 时恒为 0'' AFTER `stock`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount_goods' AND COLUMN_NAME = 'sold_count'
);
PREPARE stmt FROM @add_sold; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 占用明细：一笔订单对一个折扣 SKU 一行，释放时置 status=0 保留审计
CREATE TABLE IF NOT EXISTS `discount_stock_lock` (
    `id`          bigint      NOT NULL COMMENT '主键',
    `tenant_id`   varchar(64) NOT NULL DEFAULT '0' COMMENT '租户ID',
    `activity_id` bigint      NOT NULL COMMENT '活动ID',
    `sku_id`      varchar(64) NOT NULL COMMENT '商品SKU ID',
    `order_id`    varchar(64) NOT NULL COMMENT '订单ID',
    `quantity`    int         NOT NULL DEFAULT 0 COMMENT '占用数量',
    `status`      varchar(1)  NOT NULL DEFAULT '1' COMMENT '1占用 0已释放',
    `release_reason` varchar(64) DEFAULT NULL COMMENT '释放原因（取消/超时/退款）',
    `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    varchar(1)  NOT NULL DEFAULT '0' COMMENT '逻辑删除:0正常 1删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_order_sku` (`tenant_id`, `order_id`, `sku_id`),
    KEY `idx_activity_sku` (`activity_id`, `sku_id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='折扣商品库存占用';

SET FOREIGN_KEY_CHECKS = 1;
