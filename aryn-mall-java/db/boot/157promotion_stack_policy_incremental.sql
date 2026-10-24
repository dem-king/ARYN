-- 营销优惠叠加策略（Boot 单体 模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 背景：C 端四类活动价（拼团/秒杀/限时折扣/新人专享）当前一律可与优惠券、积分抵现叠加，
--       秒杀规则页已对外承诺「不支持优惠券」但代码未强制。本次为四张活动表增加
--       「活动商品是否可用优惠券 / 是否可积分抵现」两个字段，订单侧按 SKU 收敛券门槛、
--       券分摊与积分基数（文档：docs/50-需求文档/2026-10-23-营销优惠叠加规则调研/03-规则设计说明书.md）。
-- 内容：
--   1. seckill_activity / discount_activity / group_buy_activity / promotion_activity
--      各增加 coupon_stackable、points_stackable（char(1) NOT NULL DEFAULT '1'）；
--   2. 存量回填：秒杀 → 0/0（与规则页文案对齐）、新人专享（promotion_activity type=9）→ 0/0（防 0 元单），
--      拼团/折扣/其余营销类型保持列默认 1/1（与现网行为一致，不改价格）。
-- 特性：可重复执行；仅新增列，不修改或删除存量数据；
--       存量回填只在「本次刚加列」的分支内执行一次，重复执行脚本不会覆盖运营后续修改。
-- 依赖：25seckill_discount.sql、9aryn_promotion.sql（boot 全量脚本已含对应表）。
--
-- 执行：mysql -u root -p aryn_boot < 157promotion_stack_policy_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------- 1. seckill_activity（秒杀：强制 0/0） ----------
SET @seckill_col_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seckill_activity'
    AND COLUMN_NAME = 'coupon_stackable'
);
SET @seckill_ddl = IF(@seckill_col_exists = 0,
  'ALTER TABLE `seckill_activity`
     ADD COLUMN `coupon_stackable` char(1) NOT NULL DEFAULT ''1''
       COMMENT ''活动商品是否可用优惠券：1可用 0不可用（秒杀服务端强制 0）'' AFTER `status`,
     ADD COLUMN `points_stackable` char(1) NOT NULL DEFAULT ''1''
       COMMENT ''活动商品是否可积分抵现：1可抵 0不可抵（秒杀服务端强制 0）'' AFTER `coupon_stackable`',
  'SELECT 1');
PREPARE stmt FROM @seckill_ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 存量回填：仅本次新加列时执行（秒杀规则页已承诺不叠券/积分，与文案对齐）
SET @seckill_backfill = IF(@seckill_col_exists = 0,
  'UPDATE `seckill_activity` SET `coupon_stackable` = ''0'', `points_stackable` = ''0'' WHERE `del_flag` = ''0''',
  'SELECT 1');
PREPARE stmt FROM @seckill_backfill; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. discount_activity（限时折扣：默认 1/1，列默认值即回填值，无需 UPDATE） ----------
SET @discount_col_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount_activity'
    AND COLUMN_NAME = 'coupon_stackable'
);
SET @discount_ddl = IF(@discount_col_exists = 0,
  'ALTER TABLE `discount_activity`
     ADD COLUMN `coupon_stackable` char(1) NOT NULL DEFAULT ''1''
       COMMENT ''活动商品是否可用优惠券：1可用 0不可用'' AFTER `status`,
     ADD COLUMN `points_stackable` char(1) NOT NULL DEFAULT ''1''
       COMMENT ''活动商品是否可积分抵现：1可抵 0不可抵'' AFTER `coupon_stackable`',
  'SELECT 1');
PREPARE stmt FROM @discount_ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. group_buy_activity（拼团：默认 1/1，列默认值即回填值，无需 UPDATE） ----------
SET @groupbuy_col_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_buy_activity'
    AND COLUMN_NAME = 'coupon_stackable'
);
SET @groupbuy_ddl = IF(@groupbuy_col_exists = 0,
  'ALTER TABLE `group_buy_activity`
     ADD COLUMN `coupon_stackable` char(1) NOT NULL DEFAULT ''1''
       COMMENT ''活动商品是否可用优惠券：1可用 0不可用'' AFTER `group_expire_hours`,
     ADD COLUMN `points_stackable` char(1) NOT NULL DEFAULT ''1''
       COMMENT ''活动商品是否可积分抵现：1可抵 0不可抵'' AFTER `coupon_stackable`',
  'SELECT 1');
PREPARE stmt FROM @groupbuy_ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. promotion_activity（新人专享 type=9 → 0/0；其余类型维持 1/1） ----------
SET @promotion_col_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_activity'
    AND COLUMN_NAME = 'coupon_stackable'
);
SET @promotion_ddl = IF(@promotion_col_exists = 0,
  'ALTER TABLE `promotion_activity`
     ADD COLUMN `coupon_stackable` char(1) NOT NULL DEFAULT ''1''
       COMMENT ''活动商品是否可用优惠券：1可用 0不可用（新人专享 type=9 默认 0）'' AFTER `stackable`,
     ADD COLUMN `points_stackable` char(1) NOT NULL DEFAULT ''1''
       COMMENT ''活动商品是否可积分抵现：1可抵 0不可抵（新人专享 type=9 默认 0）'' AFTER `coupon_stackable`',
  'SELECT 1');
PREPARE stmt FROM @promotion_ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 存量回填：仅本次新加列时执行（新人价已是最低价，叠券/积分易出 0 元单）
SET @promotion_backfill = IF(@promotion_col_exists = 0,
  'UPDATE `promotion_activity` SET `coupon_stackable` = ''0'', `points_stackable` = ''0''
     WHERE `activity_type` = ''9'' AND `del_flag` = ''0''',
  'SELECT 1');
PREPARE stmt FROM @promotion_backfill; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：四张表均应返回 coupon_stackable / points_stackable 两列
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('seckill_activity', 'discount_activity', 'group_buy_activity', 'promotion_activity')
  AND COLUMN_NAME IN ('coupon_stackable', 'points_stackable')
ORDER BY TABLE_NAME, COLUMN_NAME;

-- 兜底恢复外键检查（全量脚本末尾约定）
SET FOREIGN_KEY_CHECKS = 1;
