-- 悦航购船供菜单归属调整（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：管理端信息架构按领域收敛——「船供活动」属于营销域（promotion_activity /
--       /promotion/ship-activity），「共享购物车」属于订单域（SharedCart / order/shared-cart），
--       但两者一直挂在「船供运营」目录下。两个页面的路由/组件早已分别位于
--       /promotion/ship-supply 与 /order/shared-cart，只有 parent_id 没有归位。
-- 语义：
--   1) 船供活动 → 营销管理（sort 2）；共享购物车 → 订单管理（sort 15）；
--   2) 应用系统纠偏：优惠券管理 app_base → app_market。它是营销管理下的历史错配，
--      修正后套餐「补祖先容器」时不会把营销优惠券管理页下发给基础版租户；
--   3) 权限补偿：持有被搬页面（以及已挂在营销管理下、仅因父目录缺失而不可见的
--      专区设置/出清折扣规则）但未持有新父目录的角色/租户，补授父目录；
--   4) 幂等：UPDATE 带旧值条件、INSERT IGNORE + NOT EXISTS；不删除、不覆盖业务数据。
-- 注意：菜单与权限在登录时快照，且 MENU_CACHE 为进程内缓存；执行后需重启后端并重新登录管理端才生效。
-- 关联：cloud 模式对应 db/cloud/200relocate_ship_supply_menus.sql，内容一致。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、菜单归属调整
-- ============================================================================
UPDATE `sys_menu`
SET `parent_id` = '1779386604402573314', `sort` = 2, `update_time` = NOW(), `update_by` = 'system'
WHERE `id` = '2110000000000000101'
  AND `parent_id` = '2110000000000000000'
  AND `del_flag` = '0';

UPDATE `sys_menu`
SET `parent_id` = '1521496866882236418', `sort` = 15, `update_time` = NOW(), `update_by` = 'system'
WHERE `id` = '2110000000000000121'
  AND `parent_id` = '2110000000000000000'
  AND `del_flag` = '0';

-- ============================================================================
-- 二、应用系统纠偏：优惠券管理属于营销应用（app_market）
-- ============================================================================
UPDATE `sys_menu`
SET `application_key` = 'app_market', `update_time` = NOW(), `update_by` = 'system'
WHERE `id` = '1585191589231144962'
  AND `application_key` = 'app_base'
  AND `del_flag` = '0';

-- ============================================================================
-- 三、角色权限补偿：持有页面但缺新父目录的角色，补授父目录
--     锚点：船供活动、共享购物车，以及营销管理下同类的专区设置/出清折扣规则
-- ============================================================================
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', parent.`id`)), gr.`role_id`, parent.`id`, NOW(), gr.`tenant_id`
FROM `sys_role_menu` gr
JOIN `sys_menu` child ON child.`id` = gr.`menu_id` AND child.`del_flag` = '0'
JOIN `sys_menu` parent ON parent.`id` = child.`parent_id` AND parent.`del_flag` = '0'
WHERE child.`id` IN (
    '2110000000000000101', -- 船供活动
    '2110000000000000121', -- 共享购物车
    '2110000000000000301', -- 专区设置
    '2110000000000000303'  -- 出清折扣规则
  )
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` existed
    WHERE existed.`role_id` = gr.`role_id` AND existed.`menu_id` = parent.`id`
  );

-- ============================================================================
-- 四、租户菜单补偿：持有页面但缺新父目录的租户，补授父目录
-- ============================================================================
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', parent.`id`)), gt.`tenant_id`, parent.`id`, NOW(), 'system'
FROM `sys_tenant_menu` gt
JOIN `sys_menu` child ON child.`id` = gt.`menu_id` AND child.`del_flag` = '0'
JOIN `sys_menu` parent ON parent.`id` = child.`parent_id` AND parent.`del_flag` = '0'
WHERE child.`id` IN (
    '2110000000000000101', -- 船供活动
    '2110000000000000121', -- 共享购物车
    '2110000000000000301', -- 专区设置
    '2110000000000000303'  -- 出清折扣规则
  )
  AND NOT EXISTS (
    SELECT 1 FROM `sys_tenant_menu` existed
    WHERE existed.`tenant_id` = gt.`tenant_id` AND existed.`menu_id` = parent.`id`
  );

SET FOREIGN_KEY_CHECKS = 1;
