-- ============================================================================
-- P0 存量收口审查修正（Boot 单体模式）
--
-- 目标库：aryn_boot（菜单）+ aryn_boot_job（调度）
-- 背景（2026-11-15 代码审查修复，配套 234/235 号 P0 脚本）：
--   1. 恢复优惠券管理入口：方案 §2.1/§9 口径是「下线领券中心，保留定向发放
--      （企业满减券、个人权益券）」，234 号脚本把整个优惠券目录（含优惠券管理、
--      用户领券记录）逻辑删除，定向发券失去入口。本脚本恢复优惠券目录三层菜单
--      （del_flag 1→0，授权行 234 未动，恢复即生效）；领券中心是 C 端装修页面，
--      由运营替换楼层下线，不在 sys_menu 范围。券模板停发（coupon_info 禁用）
--      维持 234 口径不变，定向发放由运营按需重新启用模板。
--   2. 补停两个调度：方案 §9.2 停用清单含 seckillOrderExpireJobHandler 与
--      groupBuyExpireJobHandler，235 号脚本只停了 3 个。存量秒杀/拼团已收口
--      （234），在途预扣单由 promotionLockCompensateJobHandler 与取消链路收敛。
--
-- 菜单缓存：sys_menu 登录后快照，执行后需清 Redis menu_cache 并重新登录才可见
--           （与 234/237 号脚本同口径）。
-- 幂等：可重复执行；不删除任何数据。
-- 关联：cloud 模式对应 260p0_marketing_retire_patch.sql。
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. 恢复优惠券目录三层菜单（目录 + 子页面 + 按钮）
-- ----------------------------------------------------------------------------
UPDATE `sys_menu`
SET `del_flag` = '0', `update_by` = 'system', `update_time` = NOW()
WHERE `del_flag` = '1'
  AND `id` IN (
    SELECT `id` FROM (
      SELECT `id` FROM `sys_menu`
      WHERE `id` = '1585191363078467586'  -- 优惠券 /promotion/coupon
         OR `parent_id` = '1585191363078467586'
         OR `parent_id` IN (
              SELECT `id` FROM (
                SELECT `id` FROM `sys_menu`
                WHERE `parent_id` = '1585191363078467586'
              ) `t2`
           )
    ) `t1`
  );

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- 2. 补停秒杀预扣单过期 / 拼团过期两个调度（方案 §9.2 清单补齐）
-- ----------------------------------------------------------------------------
USE `aryn_boot_job`;

UPDATE `xxl_job_info`
SET `trigger_status` = 0, `trigger_next_time` = 0, `update_time` = NOW()
WHERE `executor_handler` IN (
  'seckillOrderExpireJobHandler',
  'groupBuyExpireJobHandler'
)
  AND `trigger_status` = 1;

-- 自检（执行后人工核对）：
-- SELECT id, name, del_flag FROM aryn_boot.sys_menu
--  WHERE id = '1585191363078467586' OR parent_id = '1585191363078467586';  -- 应全部 del_flag=0
-- SELECT executor_handler, trigger_status FROM xxl_job_info
--  WHERE executor_handler IN ('seckillOrderExpireJobHandler','groupBuyExpireJobHandler');  -- 应为 0

SET FOREIGN_KEY_CHECKS = 1;