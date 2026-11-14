-- P0 存量营销活动收口（Cloud 微服务模式 · 菜单隐藏）
-- 目标库：菜单/权限库 aryn_upms
-- 背景：与 boot/234p0_marketing_retire_data.sql 第 8 段同源，cloud 模式菜单落在
--   aryn_upms 库，单独拆分。隐藏零售运营入口：秒杀管理/折扣管理/拼团管理/
--   优惠券/分销管理（目录 + 子页面 + 按钮三层，逻辑删除 del_flag 0→1）；
--   sys_role_menu / sys_tenant_menu 授权行保留，恢复显示只需把 del_flag 改回 '0'。
--   新人专享菜单已于 cloud 144hide_newbie_activity_menu.sql 收敛隐藏，不重复处理。
-- 特性：可重复执行；不物理删除任何数据。
-- 注意：菜单在登录时快照，执行后需重新登录管理端才生效。

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

UPDATE `sys_menu`
SET `del_flag` = '1', `update_by` = 'system', `update_time` = NOW()
WHERE `del_flag` = '0'
  AND `id` IN (
    SELECT `id` FROM (
      SELECT `id` FROM `sys_menu`
      WHERE `id` IN (
        '1991000000000000070',  -- 秒杀管理 /promotion/seckill-activity
        '1991000000000000080',  -- 折扣管理 /promotion/discount-activity
        '1991000000000000050',  -- 拼团管理 /promotion/groupbuy（含拼团活动/拼团记录）
        '1585191363078467586',  -- 优惠券 /promotion/coupon（含优惠券管理）
        '1991000000000000001'   -- 分销管理（cloud 222 迁移后一级目录）
      )
         OR `parent_id` IN (
        '1991000000000000070',
        '1991000000000000080',
        '1991000000000000050',
        '1585191363078467586',
        '1991000000000000001'
      )
         OR `parent_id` IN (
              SELECT `id` FROM (
                SELECT `id` FROM `sys_menu`
                WHERE `parent_id` IN (
                  '1991000000000000070',
                  '1991000000000000080',
                  '1991000000000000050',
                  '1585191363078467586',
                  '1991000000000000001'
                )
              ) `t2`
           )
    ) `t1`
  );

SET FOREIGN_KEY_CHECKS = 1;

-- 自检（执行后人工核对）：五个目录 del_flag 应全为 1
-- SELECT id, name, del_flag FROM sys_menu
--   WHERE id IN ('1991000000000000070','1991000000000000080','1991000000000000050',
--                '1585191363078467586','1991000000000000001');
