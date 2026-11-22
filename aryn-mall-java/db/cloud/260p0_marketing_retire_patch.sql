-- ============================================================================
-- P0 存量收口审查修正（Cloud 微服务模式）
--
-- 目标库：aryn_upms（菜单）+ aryn_job（调度）
-- 与 boot 244p0_marketing_retire_patch.sql 内容一致：
--   1. 恢复优惠券目录三层菜单（250 号脚本整体逻辑删除了优惠券目录，
--      方案口径是「下线领券中心，保留定向发放」，定向发券入口须保留）；
--      授权行 250 未动，del_flag 改回即生效。券模板停发维持 249 口径。
--   2. 补停 seckillOrderExpireJobHandler / groupBuyExpireJobHandler
--      （方案 §9.2 停用清单，251 号脚本只停了 3 个）。
-- 菜单缓存：sys_menu 登录后快照，执行后需清 Redis menu_cache 并重新登录。
-- 幂等：可重复执行；不删除任何数据。
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. 恢复优惠券目录三层菜单（目录 + 子页面 + 按钮）
-- ----------------------------------------------------------------------------
USE `aryn_upms`;

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

-- ----------------------------------------------------------------------------
-- 2. 补停秒杀预扣单过期 / 拼团过期两个调度（方案 §9.2 清单补齐）
-- ----------------------------------------------------------------------------
USE `aryn_job`;

UPDATE `xxl_job_info`
SET `trigger_status` = 0, `trigger_next_time` = 0, `update_time` = NOW()
WHERE `executor_handler` IN (
  'seckillOrderExpireJobHandler',
  'groupBuyExpireJobHandler'
)
  AND `trigger_status` = 1;

SET FOREIGN_KEY_CHECKS = 1;

-- 自检（执行后人工核对）：
-- SELECT id, name, del_flag FROM aryn_upms.sys_menu
--  WHERE id = '1585191363078467586' OR parent_id = '1585191363078467586';  -- 应全部 del_flag=0
-- SELECT executor_handler, trigger_status FROM aryn_job.xxl_job_info
--  WHERE executor_handler IN ('seckillOrderExpireJobHandler','groupBuyExpireJobHandler');  -- 应为 0