-- ============================================================================
-- 会员价活动功能整体下线：清理手工会员价活动与历史迁移痕迹（Cloud 微服务模式）
-- ============================================================================
-- 背景（2026-11-03 开发阶段）：会员价只保留「商品专区标签 + 系统托管活动
--   SYS_MEMBER_ZONE」一套模型；「发布会员价活动」功能整体删除，旧手工会员价活动
--   不做迁移、直接清理（当前环境均为测试数据）。
--
-- ⚠ 本脚本会**物理删除**手工会员价活动，并回滚历史上「手工活动迁移为商品标签」
--   写入的标签与价格历史；仅适用于本功能整体下线后的开发/测试环境。
--   若某个环境仍需保留旧活动价格，请先人工备份后再执行。
--
-- 目标库（Cloud 拆分部署）：
--   1. aryn_product：商品标签 / 价格历史回滚
--   2. aryn_promotion：手工会员价活动清理
--   3. aryn_job：已下线的迁移清理 Job 注册删除
-- 与 boot/194member_price_activity_purge_incremental.sql 对应。
--
-- 幂等：迁移痕迹以 goods_price_history.change_source='migration' 识别，
--   执行后该批历史被删除，重复执行均为空操作；活动删除按类型+系统标识判断。
-- ============================================================================

-- ---------- 1. 商品域：回滚迁移标签与价格历史 ----------
USE `aryn_product`;

UPDATE `goods_sku` s
JOIN (
    SELECT DISTINCT `sku_id`
    FROM `goods_price_history`
    WHERE `change_source` = 'migration' AND `price_type` = 'member_rate'
) h ON h.`sku_id` = s.`id`
SET s.`is_member_item` = NULL,
    s.`member_discount_rate` = NULL,
    s.`member_price_fixed` = NULL,
    s.`member_price_floor` = NULL
WHERE s.`del_flag` = '0';

UPDATE `goods_spu` p
JOIN (
    SELECT DISTINCT `spu_id`
    FROM `goods_price_history`
    WHERE `change_source` = 'migration'
) m ON m.`spu_id` = p.`id`
LEFT JOIN (
    SELECT DISTINCT `spu_id`
    FROM `goods_sku`
    WHERE `del_flag` = '0' AND `is_member_item` = 1
) t ON t.`spu_id` = p.`id`
SET p.`is_member_item` = 0,
    p.`member_discount_rate` = NULL,
    p.`member_price_fixed` = NULL,
    p.`member_price_floor` = NULL
WHERE t.`spu_id` IS NULL;

DELETE FROM `goods_price_history`
WHERE `change_source` = 'migration';

-- ---------- 2. 促销域：物理删除手工会员价活动（系统托管活动保留） ----------
USE `aryn_promotion`;

DELETE FROM `promotion_activity`
WHERE `activity_type` = '10'
  AND (`activity_code` IS NULL OR `activity_code` = '' OR `activity_code` NOT LIKE 'SYS\_%');

-- ---------- 3. 调度库：删除已下线的迁移清理 Job 注册 ----------
USE `aryn_job`;
SET @legacy_job_id := (
    SELECT `id` FROM `xxl_job_info`
    WHERE `executor_handler` = 'mallZoneLegacyCleanupJobHandler'
    LIMIT 1
);
DELETE FROM `xxl_job_log` WHERE `job_id` = @legacy_job_id;
DELETE FROM `xxl_job_info` WHERE `id` = @legacy_job_id;

SET FOREIGN_KEY_CHECKS = 1;
