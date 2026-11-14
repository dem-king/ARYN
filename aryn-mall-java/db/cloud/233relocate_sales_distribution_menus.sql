-- 分销管理与销售管理从「营销管理」独立为一级菜单（Cloud 微服务模式）
--
-- 目标库：aryn_upms
-- 背景（2026-11-11）：「营销管理」下已堆积 10 个二级目录（优惠券 / 船供活动 / 分销管理 /
--   拼团管理 / 秒杀管理 / 折扣管理 / 专区设置 / 出清折扣规则 / 专区商品管理 / 销售管理），
--   其中分销与销售是「渠道与人 + 资金台账」（佣金、提成、发放），与营销活动/优惠/货盘
--   不属于同一类事务；且两者含财务敏感数据，挂在营销管理下意味着授予任一页面都必须
--   连带授予「营销管理」祖先目录，权限边界被放大。
-- 语义：
--   1) 分销管理 1991000000000000001、销售管理 2130000000000000100 的 parent_id 改为 '0'，
--      各自成为一级菜单（sort 52 / 54，排在「配送管理」之后、「营销管理」之前）；
--   2) 清空「分销管理」的 redirect——原值指向 /promotion/distribution/config，基础版套餐
--      （app_base，不含 app_market）拿不到该页，目录点击会落 404；置空后由前端按第一个
--      可见子页面自动生成 redirect（packages/effects/access/src/accessible.ts）；
--   3) 权限补偿：持有被搬目录下页面但缺目录授权的角色/租户补授目录本身，否则菜单树构建
--      会丢弃父节点缺失的孤儿节点（200 号脚本的教训）；
--   4) path / component / permission / application_key / 页面代码一律不动——纯信息架构调整，
--      零页面、零接口、零路由改动；
--   5) 幂等：UPDATE 带旧值条件、INSERT IGNORE + NOT EXISTS；不删除、不覆盖业务数据。
-- 注意：菜单与权限在登录时快照进会话，MENU_CACHE 为进程内缓存；执行后需清理 Redis
--       `menu_cache::*` 并重新登录管理端才生效。
-- 执行：mysql -u root -p aryn_upms < 233relocate_sales_distribution_menus.sql
-- 关联：boot 模式对应 db/boot/222relocate_sales_distribution_menus.sql，内容一致。

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、菜单归属调整：升为一级菜单
-- ============================================================================
UPDATE `sys_menu`
SET `parent_id` = '0', `sort` = 52, `redirect` = NULL, `update_time` = NOW(), `update_by` = 'system'
WHERE `id` = '1991000000000000001'
  AND `parent_id` = '1779386604402573314'
  AND `del_flag` = '0';

UPDATE `sys_menu`
SET `parent_id` = '0', `sort` = 54, `update_time` = NOW(), `update_by` = 'system'
WHERE `id` = '2130000000000000100'
  AND `parent_id` = '1779386604402573314'
  AND `del_flag` = '0';

-- ============================================================================
-- 二、角色权限补偿：持有目录下页面但缺目录授权的角色，补授目录本身
--     目录已授权的角色由 NOT EXISTS 跳过，可重复执行。
-- ============================================================================
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', d.`id`)), gr.`role_id`, d.`id`, NOW(), gr.`tenant_id`
FROM `sys_role_menu` gr
JOIN `sys_menu` page ON page.`id` = gr.`menu_id` AND page.`del_flag` = '0'
JOIN `sys_menu` d ON d.`id` IN ('1991000000000000001', '2130000000000000100')
WHERE page.`parent_id` = d.`id`
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` existed
    WHERE existed.`role_id` = gr.`role_id` AND existed.`menu_id` = d.`id`
  );

-- ============================================================================
-- 三、租户菜单补偿：持有页面但缺目录授权的租户，补授目录本身
-- ============================================================================
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', d.`id`)), gt.`tenant_id`, d.`id`, NOW(), 'system'
FROM `sys_tenant_menu` gt
JOIN `sys_menu` page ON page.`id` = gt.`menu_id` AND page.`del_flag` = '0'
JOIN `sys_menu` d ON d.`id` IN ('1991000000000000001', '2130000000000000100')
WHERE page.`parent_id` = d.`id`
  AND NOT EXISTS (
    SELECT 1 FROM `sys_tenant_menu` existed
    WHERE existed.`tenant_id` = gt.`tenant_id` AND existed.`menu_id` = d.`id`
  );

-- ============================================================================
-- 四、自检：两个目录应为一级（parent_id='0'）、各自 6 个页面、授权数 > 0
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM `sys_menu`
     WHERE `id` IN ('1991000000000000001', '2130000000000000100')
       AND `parent_id` = '0' AND `del_flag` = '0') AS top_level_dirs,
  (SELECT COUNT(*) FROM `sys_menu`
     WHERE `parent_id` = '1991000000000000001' AND `del_flag` = '0') AS distribution_children,
  (SELECT COUNT(*) FROM `sys_menu`
     WHERE `parent_id` = '2130000000000000100' AND `del_flag` = '0') AS sales_children,
  (SELECT COUNT(*) FROM `sys_role_menu` WHERE `menu_id` = '1991000000000000001') AS dist_role_grants,
  (SELECT COUNT(*) FROM `sys_role_menu` WHERE `menu_id` = '2130000000000000100') AS sales_role_grants,
  (SELECT COUNT(*) FROM `sys_tenant_menu` WHERE `menu_id` = '1991000000000000001') AS dist_tenant_grants,
  (SELECT COUNT(*) FROM `sys_tenant_menu` WHERE `menu_id` = '2130000000000000100') AS sales_tenant_grants;

SET FOREIGN_KEY_CHECKS = 1;
