-- 积分闭环改造（Boot 单体 模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 背景：积分只有发放没有消耗入口，points_config 后台可配但代码从未读取。
--       本次打通「规则发放 -> 下单抵扣 -> 取消/退款返还」闭环。
-- 内容：
--   1. points_record 增加 biz_no 业务单号并建立租户内唯一键，保证发放/扣减/返还幂等；
--   2. order_info 增加 points_used（使用积分）与 points_deduct_price（抵扣金额）；
--   3. order_item 增加 points_price（积分抵扣按明细分摊金额，保证逐行退款不超退）；
--   4. points_config 种子规则：订单完成（每元积分）、注册奖励、积分抵现（每元所需积分）。
-- 特性：可重复执行；仅新增列/索引/种子，不修改或删除存量数据。
-- 依赖：4aryn_boot_member.sql（points_config/points_record）、2aryn_boot.sql（order_info/order_item）。
--
-- 执行：mysql -u root -p aryn_boot < 148points_closed_loop_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------- 1. points_record.biz_no ----------
SET @add_points_record_biz_no = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `points_record` ADD COLUMN `biz_no` varchar(64) NULL COMMENT ''业务单号（积分发放/扣减/返还幂等键）'' AFTER `remark`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'points_record' AND COLUMN_NAME = 'biz_no'
);
PREPARE stmt FROM @add_points_record_biz_no; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. points_record 租户内业务单号唯一键 ----------
SET @add_points_record_biz_uk = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `points_record` ADD UNIQUE KEY `uk_points_record_biz` (`tenant_id`, `biz_no`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'points_record' AND INDEX_NAME = 'uk_points_record_biz'
);
PREPARE stmt FROM @add_points_record_biz_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. order_info 积分抵扣字段 ----------
SET @add_order_points_used = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `points_used` int NOT NULL DEFAULT 0 COMMENT ''本单使用积分数量'' AFTER `points_multiplier`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'points_used'
);
PREPARE stmt FROM @add_order_points_used; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_order_points_deduct_price = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `points_deduct_price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT ''积分抵扣金额（元）'' AFTER `points_used`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'points_deduct_price'
);
PREPARE stmt FROM @add_order_points_deduct_price; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. order_item 积分抵扣分摊 ----------
SET @add_order_item_points_price = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_item` ADD COLUMN `points_price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT ''积分抵扣分摊金额（元）'' AFTER `member_discount_price`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'points_price'
);
PREPARE stmt FROM @add_order_item_points_price; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 5. 积分配置种子（仅本脚本 2130 前缀，固定租户） ----------
DELETE FROM `points_config`
WHERE `id` LIKE '2130000000000000%' AND `tenant_id` = '1590229800633634816';

INSERT INTO `points_config`
  (`id`, `rule_name`, `rule_type`, `trigger_scene`, `point_value`, `status`,
   `create_by`, `update_time`, `create_time`, `del_flag`, `tenant_id`)
VALUES
  ('2130000000000000001', '订单完成积分（每元获得）', '1', 'ORDER_COMPLETE', 1,   '0', 'seed', NULL, NOW(), '0', '1590229800633634816'),
  ('2130000000000000002', '注册奖励',                 '1', 'REGISTER',       100, '0', 'seed', NULL, NOW(), '0', '1590229800633634816'),
  ('2130000000000000003', '积分抵现（每元所需积分）', '2', 'ORDER_DEDUCT',   100, '0', 'seed', NULL, NOW(), '0', '1590229800633634816');

-- 自检：应返回 biz_no、points_used、points_deduct_price、points_price 四列
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'points_record' AND COLUMN_NAME = 'biz_no')
    OR (TABLE_NAME = 'order_info' AND COLUMN_NAME IN ('points_used', 'points_deduct_price'))
    OR (TABLE_NAME = 'order_item' AND COLUMN_NAME = 'points_price'));

-- 验证：
-- SELECT rule_name, rule_type, trigger_scene, point_value, status
--   FROM points_config WHERE tenant_id = '1590229800633634816' AND del_flag = '0';

SET FOREIGN_KEY_CHECKS = 1;
