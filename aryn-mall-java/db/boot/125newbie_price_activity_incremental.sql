-- 悦航购新人专享价活动（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：新人专享定价需求。复用 promotion_activity 通用引擎（activityType=9），
--       不建新表；本脚本补齐两处结构性变更：
--         1) promotion_lock 增加 user_id：新人价「在途唯一」判定
--            （同用户已有 LOCKED 的新人价锁时拒绝重复下单）需要按用户查询；
--            对货到付款尤其关键——COD 单的 pay_status 要等确认收款才翻 1，
--            身份判定在此之前仍视其为新人，长窗口只能靠在途锁兜住。
--         2) 新人专享活动管理菜单与按钮权限（树挂在船供运营目录下）。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据；
--       存量 promotion_lock 行为 user_id=NULL，天然被在途唯一查询排除（不等于任何用户）。
-- 关联：cloud 模式同名增量 126newbie_price_activity_incremental.sql 内容一致。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. promotion_lock.user_id（新人价在途唯一查询用）
SET @add_lock_user_id = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `promotion_lock` ADD COLUMN `user_id` varchar(32) DEFAULT NULL COMMENT ''下单用户ID（新人价在途唯一查询用；历史行可为空）'' AFTER `activity_type`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_lock' AND COLUMN_NAME = 'user_id'
);
PREPARE stmt FROM @add_lock_user_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_lock_user_idx = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `promotion_lock` ADD INDEX `idx_lock_user` (`tenant_id`,`user_id`,`status`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_lock' AND INDEX_NAME = 'idx_lock_user'
);
PREPARE stmt FROM @add_lock_user_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 新人专享活动管理菜单与按钮权限（挂在船供运营目录 2110000000000000000 下）
--    编辑页路由注册在前端动态路由模块 routes/modules/promotion.ts（hideInMenu + activePath），
--    此处仅配置列表页与按钮权限。
INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`) VALUES
('2110000000000000140','新人专享活动',NULL,'/promotion/newbie-activity',NULL,'2110000000000000000','lucide:gift','promotion/newbie-activity/index',5,'0',NOW(),'0','0','app_base','system'),
('2110000000000000141','新人活动分页','promotion:newbieactivity:page',NULL,NULL,'2110000000000000140','',NULL,1,'1',NOW(),'0','0','app_base','system'),
('2110000000000000142','新人活动保存','promotion:newbieactivity:save',NULL,NULL,'2110000000000000140','',NULL,2,'1',NOW(),'0','0','app_base','system'),
('2110000000000000143','新人活动发布','promotion:newbieactivity:publish',NULL,NULL,'2110000000000000140','',NULL,3,'1',NOW(),'0','0','app_base','system');

-- 授权推导：以「船供运营」目录（2110000000000000000）为锚点。
-- 关键点：新按钮的 parent 是新建的页面菜单 140，它没有任何已授权 peer，
-- 若按「与本按钮同 parent 的菜单授权」推导会命中 0 行（INSERT IGNORE 静默跳过）；
-- 目录本身已有授权（平台与试点租户 ROLE_ADMIN），故按目录推导页面与按钮。
-- 租户菜单行 id 统一用 MD5(tenant:menu:id)，重复执行走主键去重，避免同一菜单插两行。
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(gr.`role_id`, ':menu:', m.`id`)), gr.`role_id`, m.`id`, NOW(), gr.`tenant_id`
FROM `sys_menu` m
JOIN `sys_role_menu` gr ON gr.`menu_id` = '2110000000000000000'
WHERE m.`id` IN ('2110000000000000140','2110000000000000141','2110000000000000142','2110000000000000143')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(gt.`tenant_id`, ':menu:', m.`id`)), gt.`tenant_id`, m.`id`, NOW(), 'system'
FROM `sys_menu` m
JOIN `sys_tenant_menu` gt ON gt.`menu_id` = '2110000000000000000'
WHERE m.`id` IN ('2110000000000000140','2110000000000000141','2110000000000000142','2110000000000000143')
  AND m.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
