-- P0 存量营销活动收口（Boot 单体模式 · 数据与菜单）
-- 目标库：aryn_boot
-- 背景（2026-11-14 船供 B2B 活动与会员体系重构 · P0 停量）：
--   平台客群转为船供采购/海员个人/工厂餐厅采购，零售限时玩法（秒杀/折扣/拼团/
--   优惠券/新人价）整体停止运营，待新价格体系（协议价/档期特供/阶梯价/满量优惠）
--   上线后统一清理。按既定决策「仅结束存量数据」：不删代码、不删表，只结束在跑
--   活动数据、停用运营入口菜单；代码与表结构保留以便回溯。
-- 语义：
--   1) 秒杀：活动全部置已结束（end_time 收口到当下，C 端按时间实时推导展示态）；
--      排期模板全部停用——不停模板则 seckillScheduleJobHandler 每 60 秒会继续
--      生成新场次；已生成未开始的场次一并置已结束。
--   2) 限时折扣：未开始/进行中 → 已暂停（3），保留可恢复语义，discountStatusJobHandler
--      对状态 3 不做流转。
--   3) 拼团：草稿/进行中 → 已结束；在途拼团记录由 groupBuyExpireJobHandler 自然
--      超时收敛，调度不停。
--   4) 优惠券：模板全部禁用（停发），存量未领/未用券自然过期，不强制作废。
--   5) 统一引擎手工活动（4 阶梯价/5 买赠/7 整船满减/9 新人价）：草稿/已发布 → 已暂停；
--      系统托管专区活动（10 会员价 SYS_MEMBER_ZONE、11 出清价 SYS_CLEARANCE_ZONE）
--      保留不动——专区治理体系在新方案中继续沿用。
--   6) 管理端运营入口菜单逻辑删除（del_flag 0→1）：秒杀管理/折扣管理/拼团管理/
--      优惠券/分销管理（含各自子页面与按钮）；sys_role_menu / sys_tenant_menu
--      授权行保留，恢复显示只需把对应行 del_flag 改回 '0'。
--      新人专享菜单已于 143hide_newbie_activity_menu.sql 收敛隐藏，此处不重复处理。
-- 特性：可重复执行；不 DROP/TRUNCATE；不物理删除任何数据。
-- 注意：
--   · 配套调度停用见 235p0_marketing_retire_jobs.sql（boot）/ 251（cloud），
--     必须与本脚本一起执行，防止 Job 把已收口数据改回；
--   · 菜单在登录时快照，执行后需重新登录管理端才生效；
--   · 在途营销锁（promotion_lock LOCKED）不触碰，由补偿 Job 与订单闭环自然收敛。
-- 关联：cloud 模式对应 249p0_marketing_retire_promotion.sql + 250p0_marketing_retire_menu.sql，
--       数据语义一致；菜单部分单独拆分（cloud 菜单库为 aryn_upms）。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 秒杀活动全部结束（未开始/进行中 → 已结束，end_time 收口）
UPDATE `seckill_activity`
SET `status` = 2, `end_time` = NOW(), `update_by` = 'system', `update_time` = NOW()
WHERE `status` IN (0, 1) AND `del_flag` = '0';

-- 2. 秒杀排期模板全部停用（关键：不停模板则调度 Job 持续生成新场次）
UPDATE `seckill_session_template`
SET `status` = 1, `update_time` = NOW()
WHERE `status` = 0 AND `del_flag` = '0';

-- 3. 已生成的未开始/进行中场次一并结束
UPDATE `seckill_session`
SET `status` = 2, `update_time` = NOW()
WHERE `status` IN (0, 1) AND `del_flag` = '0';

-- 4. 限时折扣：未开始/进行中 → 已暂停
UPDATE `discount_activity`
SET `status` = 3, `update_by` = 'system', `update_time` = NOW()
WHERE `status` IN (0, 1) AND `del_flag` = '0';

-- 5. 拼团：草稿/进行中 → 已结束
UPDATE `group_buy_activity`
SET `activity_status` = '2', `update_by` = 'system', `update_time` = NOW()
WHERE `activity_status` IN ('0', '1') AND `del_flag` = '0';

-- 6. 优惠券模板全部禁用（停发；存量券自然过期）
UPDATE `coupon_info`
SET `status` = '1', `update_by` = 'system', `update_time` = NOW()
WHERE `status` = '0' AND `del_flag` = '0';

-- 7. 统一引擎手工活动暂停（保留系统托管专区 10/11 不动）
UPDATE `promotion_activity`
SET `status` = '3', `update_by` = 'system', `update_time` = NOW()
WHERE `activity_type` IN ('4', '5', '7', '9')
  AND `status` IN ('1', '2')
  AND `del_flag` = '0';

-- 8. 隐藏零售运营入口菜单（目录 + 子页面 + 按钮，覆盖三层）
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
        '1991000000000000001'   -- 分销管理（222 迁移后一级目录）
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

-- ---------------------------------------------------------------------------
-- 自检（执行后人工核对）：确认无残留的进行中活动、模板已停用、菜单已隐藏
-- ---------------------------------------------------------------------------
-- SELECT COUNT(*) AS seckill_running FROM seckill_activity WHERE status IN (0,1) AND del_flag='0';
-- SELECT COUNT(*) AS seckill_template_active FROM seckill_session_template WHERE status=0;
-- SELECT COUNT(*) AS seckill_session_open FROM seckill_session WHERE status IN (0,1);
-- SELECT COUNT(*) AS discount_running FROM discount_activity WHERE status IN (0,1) AND del_flag='0';
-- SELECT COUNT(*) AS groupbuy_running FROM group_buy_activity WHERE activity_status IN ('0','1') AND del_flag='0';
-- SELECT COUNT(*) AS coupon_active FROM coupon_info WHERE status='0' AND del_flag='0';
-- SELECT COUNT(*) AS manual_activity_running FROM promotion_activity
--   WHERE activity_type IN ('4','5','7','9') AND status IN ('1','2') AND del_flag='0';
-- SELECT COUNT(*) AS zone_activity_kept FROM promotion_activity
--   WHERE activity_type IN ('10','11') AND status='2' AND del_flag='0';  -- 应保留（专区）
-- SELECT id, name, del_flag FROM sys_menu
--   WHERE id IN ('1991000000000000070','1991000000000000080','1991000000000000050',
--                '1585191363078467586','1991000000000000001');

SET FOREIGN_KEY_CHECKS = 1;
