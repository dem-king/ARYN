-- 悦航购下线新人专享活动独立菜单（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：新人专享价（activityType=9）与船供活动共用 promotion_activity 同一批数据与
--       /promotion/ship-activity 接口，独立页面只是 type=9 的筛选视图。两个入口并存时
--       同一活动可在两处编辑/发布/删除，交互口径还不一致。统一收敛到「船供活动」管理。
-- 语义：
--   1) 先向仍持有新人专享菜单的角色/租户补授船供活动页面与按钮，避免收敛后失去入口；
--   2) 仅逻辑删除 sys_menu 新人专享页面与按钮（del_flag 0→1），sys_role_menu /
--      sys_tenant_menu 授权行保留，恢复显示只需把对应行 del_flag 改回 '0'。
--      可重复执行，不执行 DROP/TRUNCATE，不物理删除任何数据。
-- 注意：菜单在登录时快照，执行后需重新登录管理端才生效。
-- 关联：cloud 模式对应增量 144hide_newbie_activity_menu.sql，内容一致。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 权限补偿：持有新人专享菜单的角色，补齐船供活动页面与按钮
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '2110000000000000140'
WHERE m.`id` IN ('2110000000000000101','2110000000000000111',
                 '2110000000000000112','2110000000000000113')
  AND m.`del_flag` = '0';

-- 2. 权限补偿：持有新人专享菜单的租户，补齐船供活动页面与按钮
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '2110000000000000140'
WHERE m.`id` IN ('2110000000000000101','2110000000000000111',
                 '2110000000000000112','2110000000000000113')
  AND m.`del_flag` = '0';

-- 3. 隐藏新人专享活动页面与按钮（140~143 段为新人专享专用，无其它用途）
UPDATE `sys_menu`
SET `del_flag` = '1', `update_time` = NOW(), `update_by` = 'system'
WHERE `id` IN (
  '2110000000000000140', -- 新人专享活动 /promotion/newbie-activity
  '2110000000000000141', -- 新人活动分页 promotion:newbieactivity:page
  '2110000000000000142', -- 新人活动保存 promotion:newbieactivity:save
  '2110000000000000143'  -- 新人活动发布 promotion:newbieactivity:publish
)
  AND `del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
