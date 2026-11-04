-- ============================================================================
-- 会员价活动功能整体下线：清理手工会员价活动与历史迁移痕迹（Boot 单体模式）
-- ============================================================================
-- 背景（2026-11-03 开发阶段）：会员价只保留「商品专区标签 + 系统托管活动
--   SYS_MEMBER_ZONE」一套模型；「发布会员价活动」功能整体删除，旧手工会员价活动
--   不做迁移、直接清理（当前环境均为测试数据）。
--
-- ⚠ 本脚本会**物理删除**手工会员价活动，并回滚历史上「手工活动迁移为商品标签」
--   写入的标签与价格历史；仅适用于本功能整体下线后的开发/测试环境。
--   若某个环境仍需保留旧活动价格，请先人工备份后再执行。
--
-- 保留不动：系统托管活动（activity_code LIKE 'SYS\_%'）、商品级会员/出清标签、
--   系统活动价格载体、其它活动类型（阶梯价/整船满减/买赠/新人价/秒杀等）。
--
-- 幂等：迁移痕迹以 goods_price_history.change_source='migration' 识别，
--   执行后该批历史被删除，重复执行均为空操作；活动删除按类型+系统标识判断。
-- ============================================================================

USE `aryn_boot`;

-- 1. 回滚历史迁移写入的 SKU 会员标签（未执行过迁移的环境为空操作）
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

-- 2. 回滚历史迁移写入的 SPU 会员标签（仅当该 SPU 已没有其它仍打标的 SKU 时）
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

-- 3. 删除历史迁移价格记录（迁移已撤回，不再保留 migration 来源审计）
DELETE FROM `goods_price_history`
WHERE `change_source` = 'migration';

-- 4. 物理删除手工会员价活动（type=10 且非系统托管；系统活动 SYS_MEMBER_ZONE 保留）
DELETE FROM `promotion_activity`
WHERE `activity_type` = '10'
  AND (`activity_code` IS NULL OR `activity_code` = '' OR `activity_code` NOT LIKE 'SYS\_%');

-- 5. 删除已下线的「会员价活动迁移后清理」定时任务注册（调度库 aryn_boot_job）
--    对应历史脚本 187；Job 与接口已删除，避免调度中心继续触发不存在的 handler。
USE `aryn_boot_job`;
SET @legacy_job_id := (
    SELECT `id` FROM `xxl_job_info`
    WHERE `executor_handler` = 'mallZoneLegacyCleanupJobHandler'
    LIMIT 1
);
DELETE FROM `xxl_job_log` WHERE `job_id` = @legacy_job_id;
DELETE FROM `xxl_job_info` WHERE `id` = @legacy_job_id;

USE `aryn_boot`;

SET FOREIGN_KEY_CHECKS = 1;
