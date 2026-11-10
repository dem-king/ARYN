-- ============================================================================
-- 版权配置 — 菜单、权限与品牌种子（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-10 需求「版权配置可配置化」）：表见 219copyright_config_incremental.sql。
--   权限按「平台管两端、租户管用户端」拆成两个页面，共用同一组件（读 route.path 判作用域）：
--     /platform/copyright-config  平台管理 → 版权配置（商户端 + 平台端两个 Tab，仅平台管理员）
--     /system/copyright-config    系统设置 → 版权配置（只有用户端，各租户自己维护）
--   拆两个菜单而不是一个页面按身份隐藏 Tab：菜单可见性 = 角色菜单 ∩ 租户菜单，
--   平台专属应用 sys_key 不会随套餐下发给租户，租户天然只看到用户端入口。
--
-- 菜单（固定 ID，已确认 db/boot 与 db/cloud 全脚本无 220 前缀冲突）：
--   2200000000000000100 平台版权（父=平台管理 1881217134381326338，sys_key）
--   2200000000000000101 平台版权查询 upms:copyright:platform:get
--   2200000000000000102 平台版权修改 upms:copyright:platform:edit
--   2200000000000000110 版权配置（租户，父=系统设置 10001，app_base）
--   2200000000000000111 版权配置查询（租户）upms:copyright:get
--   2200000000000000112 版权配置修改（租户）upms:copyright:edit
--
-- 种子：把当前写死在前端的品牌/版权文案落库，保证上线当天三端展示与改造前一致；
--   名称与 Logo 一律留空（跟随租户品牌），年份不入库（渲染时取当前年，避免再写死过期年份）。
--   全部 INSERT IGNORE / 条件写入，可重复执行且不覆盖运营后续修改。
--
-- 注意：菜单/权限在登录时快照到会话，执行后需清理 Redis `menu_cache::*` 并重新登录才可见。
-- 执行：mysql -u root -p aryn_boot < 220copyright_config_menu.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、菜单与按钮（固定 ID，INSERT IGNORE 幂等）
-- ============================================================================
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
('2200000000000000100','平台版权','upms:copyright:platform:get','/platform/copyright-config',NULL,'1881217134381326338','carbon:certificate','upms/copyright-config/index',105,'0',NOW(),'0','0','sys_key','system'),
('2200000000000000101','平台版权查询','upms:copyright:platform:get',NULL,NULL,'2200000000000000100','','',1,'1',NOW(),'0','0','sys_key','system'),
('2200000000000000102','平台版权修改','upms:copyright:platform:edit',NULL,NULL,'2200000000000000100','','',2,'1',NOW(),'0','0','sys_key','system');

-- 租户入口挂在「系统设置」10001 下（与文件存储配置/素材中心同级，属租户自己的品牌设置）
INSERT IGNORE INTO `sys_menu`
(`id`,`name`,`permission`,`path`,`redirect`,`parent_id`,`icon`,`component`,`sort`,`type`,`create_time`,`outer_status`,`del_flag`,`application_key`,`create_by`)
VALUES
('2200000000000000110','版权配置','upms:copyright:get','/system/copyright-config',NULL,'10001','carbon:certificate','upms/copyright-config/index',16,'0',NOW(),'0','0','app_base','system'),
('2200000000000000111','版权配置查询','upms:copyright:get',NULL,NULL,'2200000000000000110','','',1,'1',NOW(),'0','0','app_base','system'),
('2200000000000000112','版权配置修改','upms:copyright:edit',NULL,NULL,'2200000000000000110','','',2,'1',NOW(),'0','0','app_base','system');

-- ============================================================================
-- 二、平台菜单授权（平台管理员走 listAllMenus，无需授权行；此处只补平台租户可见性）
-- ============================================================================
INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(t.`id`, ':menu:', m.`id`)), t.`id`, m.`id`, NOW(), 'system'
FROM `sys_tenant` t
JOIN `sys_menu` m ON m.`id` IN ('1881217134381326338',
                               '2200000000000000100','2200000000000000101','2200000000000000102')
  AND m.`del_flag` = '0'
WHERE t.`tenant_type` = '1' AND t.`del_flag` = '0';

-- ============================================================================
-- 三、租户菜单授权（按「文件存储配置」推导：能管存储配置的角色/租户即可管自己的用户端版权）
-- ============================================================================
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(receiver.`role_id`, ':menu:', m.`id`)), receiver.`role_id`, m.`id`, NOW(), receiver.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `sys_role_menu`
      WHERE `menu_id` = '1491948958826921986') receiver
JOIN `sys_menu` m ON m.`id` IN ('2200000000000000110','2200000000000000111','2200000000000000112')
  AND m.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(receiver.`tenant_id`, ':menu:', m.`id`)), receiver.`tenant_id`, m.`id`, NOW(), 'system'
FROM (SELECT DISTINCT `tenant_id` FROM `sys_tenant_menu`
      WHERE `menu_id` = '1491948958826921986') receiver
JOIN `sys_menu` m ON m.`id` IN ('2200000000000000110','2200000000000000111','2200000000000000112')
  AND m.`del_flag` = '0';

-- 祖先容器补齐：父容器缺失时管理端菜单树会丢弃整棵子树，入口静默消失
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`tenant_id`)
SELECT MD5(CONCAT(receiver.`role_id`, ':menu:', ancestor.`id`)), receiver.`role_id`, ancestor.`id`, NOW(), receiver.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `sys_role_menu`
      WHERE `menu_id` LIKE '220000000000000011%') receiver
JOIN `sys_menu` child ON child.`id` = '2200000000000000110' AND child.`del_flag` = '0'
JOIN `sys_menu` ancestor ON ancestor.`id` = child.`parent_id` AND ancestor.`del_flag` = '0';

INSERT IGNORE INTO `sys_tenant_menu` (`id`,`tenant_id`,`menu_id`,`create_time`,`create_by`)
SELECT MD5(CONCAT(receiver.`tenant_id`, ':menu:', ancestor.`id`)), receiver.`tenant_id`, ancestor.`id`, NOW(), 'system'
FROM (SELECT DISTINCT `tenant_id` FROM `sys_tenant_menu`
      WHERE `menu_id` LIKE '220000000000000011%') receiver
JOIN `sys_menu` child ON child.`id` = '2200000000000000110' AND child.`del_flag` = '0'
JOIN `sys_menu` ancestor ON ancestor.`id` = child.`parent_id` AND ancestor.`del_flag` = '0';

-- ============================================================================
-- 四、品牌种子（与改造前前端写死值一致；名称/Logo 留空跟随租户品牌）
--
-- 平台租户取 tenant_type='1'（196 号脚本后为悦航购），不写死租户 ID。
-- 用户端按租户逐个铺：版权文案沿用 C 端语言包现值，租户改过之后本脚本不再覆盖。
-- ============================================================================
SET @platform_tenant_id = (
  SELECT `id` FROM `sys_tenant` WHERE `tenant_type` = '1' AND `del_flag` = '0'
  ORDER BY `create_time`, `id` LIMIT 1
);

INSERT IGNORE INTO `sys_copyright_config`
(`id`,`client_type`,`logo_url`,`name`,`copyright_text`,`copyright_link`,`icp`,`icp_link`,
 `login_title`,`login_subtitle`,`login_icon`,`del_flag`,`tenant_id`,`create_by`,`create_time`,`update_time`)
SELECT MD5(CONCAT(@platform_tenant_id, ':copyright:platform')), 'platform', NULL, NULL,
       '悦航购 Aetheryn Mall', 'https://www.iaryn.cn', NULL, NULL,
       '账号登录', '请使用工作账号登录，成功后将进入您所属企业的管理后台。', NULL,
       '0', @platform_tenant_id, 'system', NOW(), NOW()
WHERE @platform_tenant_id IS NOT NULL;

INSERT IGNORE INTO `sys_copyright_config`
(`id`,`client_type`,`logo_url`,`name`,`copyright_text`,`copyright_link`,`icp`,`icp_link`,
 `login_title`,`login_subtitle`,`login_icon`,`del_flag`,`tenant_id`,`create_by`,`create_time`,`update_time`)
SELECT MD5(CONCAT(@platform_tenant_id, ':copyright:merchant')), 'merchant', NULL, NULL,
       '悦航购 Aetheryn Mall', 'https://www.iaryn.cn', NULL, NULL,
       NULL, NULL, NULL, '0', @platform_tenant_id, 'system', NOW(), NOW()
WHERE @platform_tenant_id IS NOT NULL;

INSERT IGNORE INTO `sys_copyright_config`
(`id`,`client_type`,`logo_url`,`name`,`copyright_text`,`copyright_link`,`icp`,`icp_link`,
 `login_title`,`login_subtitle`,`login_icon`,`del_flag`,`tenant_id`,`create_by`,`create_time`,`update_time`)
SELECT MD5(CONCAT(t.`id`, ':copyright:app')), 'app', NULL, NULL,
       '天启雨数版权所有', NULL, NULL, NULL,
       NULL, NULL, NULL, '0', t.`id`, 'system', NOW(), NOW()
FROM `sys_tenant` t
WHERE t.`del_flag` = '0';

-- ============================================================================
-- 五、自检
--   期望：platform_menu_rows=3、tenant_menu_rows=3、
--         platform_seed_rows=2、app_seed_rows=租户数
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM `sys_menu`
    WHERE `id` IN ('2200000000000000100','2200000000000000101','2200000000000000102')
      AND `del_flag` = '0') AS platform_menu_rows,
  (SELECT COUNT(*) FROM `sys_menu`
    WHERE `id` IN ('2200000000000000110','2200000000000000111','2200000000000000112')
      AND `del_flag` = '0') AS tenant_menu_rows,
  (SELECT COUNT(*) FROM `sys_copyright_config`
    WHERE `tenant_id` = @platform_tenant_id AND `client_type` IN ('platform','merchant')) AS platform_seed_rows,
  (SELECT COUNT(*) FROM `sys_copyright_config` WHERE `client_type` = 'app') AS app_seed_rows,
  (SELECT COUNT(*) FROM `sys_tenant` WHERE `del_flag` = '0') AS tenant_rows;

SET FOREIGN_KEY_CHECKS = 1;