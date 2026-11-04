-- ============================================================================
-- 平台管理员用户级身份与专属角色（Cloud 微服务模式）
-- 目标库：aryn_upms（租户 / 菜单 / 权限库）
--
-- 背景：
--   平台租户合并（201）后「悦航购」成为平台租户，system 与 admin 都在其中。
--   但「平台租户里的账号」此前都会被当作平台管理员——admin 是业务租户管理员，
--   不应看到平台管理菜单、不能切换租户。本次把平台管理员收敛为用户级身份：
--     sys_user.platform_admin = '1' 且所属租户为平台租户（tenant_type = '1'）；
--   全库唯一平台管理员是 system；同时给 system 分配「平台管理员」专属角色（全部菜单），
--   并把租户管理员角色还原到套餐范围。
--
-- 内容：
--   1. sys_user 增加 platform_admin 列（默认 0，不映射实体，接口不可改写）；
--   2. 唯一平台管理员：system（1881232178902249473）置 1，其余账号置 0；
--   3. 平台租户下创建「平台管理员」角色（ROLE_PLATFORM_ADMIN）并授予全部可见菜单；
--   4. system 只保留平台管理员角色；
--   5. 还原租户管理员角色：删除 196 曾给 ROLE_ADMIN 补授的套餐外菜单（含平台管理菜单）。
--
-- 特性：幂等；不覆盖普通账号业务数据；无 DROP/TRUNCATE。
-- 执行：mysql -u root -p aryn_upms < 202platform_admin_user.sql
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
SET FOREIGN_KEY_CHECKS = 0;

-- 固定种子 ID（与 2aryn_upms.sql 基线一致）
SET @target_tenant_id = '1590229800633634816'; -- 悦航购（平台租户）
SET @platform_admin_user_id = '1881232178902249473'; -- system（唯一平台管理员）
SET @platform_role_id = '1970000000000000001'; -- 平台管理员角色

-- ============================================================================
-- 一、sys_user.platform_admin：用户级平台管理员标记
-- ============================================================================
SET @add_platform_admin = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `platform_admin` char(1) NOT NULL DEFAULT ''0'' COMMENT ''平台管理员：1是；0否'' AFTER `type`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'platform_admin'
);
PREPARE stmt FROM @add_platform_admin; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 二、平台管理员唯一化：system 置 1，其余账号一律 0
-- ============================================================================
UPDATE `sys_user` SET `platform_admin` = '0'
WHERE `platform_admin` IS NULL OR `platform_admin` NOT IN ('0', '1');

UPDATE `sys_user` SET `platform_admin` = '0'
WHERE `platform_admin` = '1' AND `id` <> @platform_admin_user_id;

UPDATE `sys_user`
SET `platform_admin` = '1', `update_time` = NOW(), `update_by` = 'system'
WHERE `id` = @platform_admin_user_id AND `del_flag` = '0';

-- ============================================================================
-- 三、平台管理员专属角色：持有全部可见菜单（不参与租户套餐）
-- ============================================================================
INSERT INTO `sys_role`
	(`id`, `role_name`, `role_code`, `role_desc`, `create_time`, `update_time`, `del_flag`,
	 `tenant_id`, `create_by`, `update_by`)
SELECT @platform_role_id, '平台管理员', 'ROLE_PLATFORM_ADMIN',
	   '平台管理员：可切换租户并维护平台级配置', NOW(), NOW(), '0',
	   @target_tenant_id, 'system', 'system'
FROM DUAL
WHERE EXISTS (SELECT 1 FROM `sys_tenant` WHERE `id` = @target_tenant_id AND `del_flag` = '0')
  AND NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `id` = @platform_role_id);

-- 角色已存在时纠正名称/编码/归属（幂等，避免被改坏后无法自愈）
UPDATE `sys_role`
SET `role_name` = '平台管理员',
    `role_code` = 'ROLE_PLATFORM_ADMIN',
    `tenant_id` = @target_tenant_id,
    `del_flag` = '0',
    `update_time` = NOW(),
    `update_by` = 'system'
WHERE `id` = @platform_role_id
  AND EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_tenant`
                   WHERE `id` = @target_tenant_id AND `del_flag` = '0') t
  );

-- 授予全部可见菜单（幂等）
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT REPLACE(UUID(), '-', ''), @platform_role_id, m.`id`, NOW(), @target_tenant_id
FROM `sys_menu` m
WHERE m.`del_flag` = '0'
  AND EXISTS (SELECT 1 FROM `sys_role` r WHERE r.`id` = @platform_role_id AND r.`del_flag` = '0')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm
    WHERE rm.`role_id` = @platform_role_id AND rm.`menu_id` = m.`id`
  );

-- ============================================================================
-- 四、system 账号只保留平台管理员角色
-- ============================================================================
DELETE FROM `sys_user_role`
WHERE `user_id` = @platform_admin_user_id
  AND `role_id` <> @platform_role_id;

INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `create_time`, `tenant_id`, `del_flag`)
SELECT REPLACE(UUID(), '-', ''), @platform_admin_user_id, @platform_role_id, NOW(), @target_tenant_id, '0'
FROM DUAL
WHERE EXISTS (SELECT 1 FROM `sys_user` u
              WHERE u.`id` = @platform_admin_user_id
                AND u.`tenant_id` = @target_tenant_id
                AND u.`del_flag` = '0')
  AND EXISTS (SELECT 1 FROM `sys_role` r WHERE r.`id` = @platform_role_id AND r.`del_flag` = '0')
  AND NOT EXISTS (SELECT 1 FROM `sys_user_role` ur
                  WHERE ur.`user_id` = @platform_admin_user_id
                    AND ur.`role_id` = @platform_role_id
                    AND ur.`del_flag` = '0');

-- ============================================================================
-- 五、还原租户管理员角色到套餐范围
--     201 曾给平台租户 ROLE_ADMIN 补授全部菜单；租户管理员只应持有租户套餐菜单
-- ============================================================================
DELETE rm FROM `sys_role_menu` rm
JOIN `sys_role` r ON r.`id` = rm.`role_id`
WHERE r.`tenant_id` = @target_tenant_id
  AND r.`role_code` = 'ROLE_ADMIN'
  AND r.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_tenant_menu` tm
    WHERE tm.`tenant_id` = @target_tenant_id AND tm.`menu_id` = rm.`menu_id`
  );

-- ============================================================================
-- 六、自检
-- ============================================================================
-- 唯一平台管理员（期望 1 行：system）
SELECT `id`, `username`, `tenant_id`, `platform_admin`
FROM `sys_user`
WHERE `platform_admin` = '1';

-- 平台管理员角色与菜单数（期望：角色存在、menu_count = 可见菜单总数）
SELECT r.`id`, r.`role_code`, r.`role_name`, COUNT(rm.`id`) AS menu_count
FROM `sys_role` r
LEFT JOIN `sys_role_menu` rm ON rm.`role_id` = r.`id`
WHERE r.`id` = @platform_role_id
GROUP BY r.`id`, r.`role_code`, r.`role_name`;

-- 租户管理员角色与套餐菜单差异（期望 0 行）
SELECT rm.`menu_id`
FROM `sys_role_menu` rm
JOIN `sys_role` r ON r.`id` = rm.`role_id`
WHERE r.`tenant_id` = @target_tenant_id
  AND r.`role_code` = 'ROLE_ADMIN'
  AND r.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_tenant_menu` tm
    WHERE tm.`tenant_id` = @target_tenant_id AND tm.`menu_id` = rm.`menu_id`
  );

SET FOREIGN_KEY_CHECKS = 1;
