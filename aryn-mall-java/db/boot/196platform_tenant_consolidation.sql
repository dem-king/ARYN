-- ============================================================================
-- 平台租户合并：「悦航购」接管平台租户身份，物理删除「系统租户」（Boot 单体模式）
-- 目标库：aryn_boot
--
-- 背景：
--   初始化种子里有两条租户：业务租户「悦航购」1590229800633634816（承载全部业务数据）
--   与平台租户「系统租户」1881232176465358849（仅 system 管理员、角色、部门、存储配置）。
--   平台租户身份此前写死在代码常量里，运维想只保留一个租户时无处下手。
--   本次改为「租户类型数据驱动」后，把悦航购提升为平台租户，并把系统租户下的
--   system 管理员迁入悦航购，最后物理删除系统租户及其余下数据。
--
-- 内容：
--   1. 悦航购：tenant_type=1（平台租户）、auth_end_time=NULL（长期有效）；
--   2. system 管理员：租户迁到悦航购、部门改挂悦航购根部门；
--   3. 存储配置：悦航购没有启用配置时，继承系统租户的默认本机配置；
--   4. 物理删除系统租户在 sys_user/sys_role/sys_user_role/sys_role_menu/sys_dept/
--      sys_tenant_menu/sys_storage_config/sys_login_log/sys_log 等表的全部数据及租户行；
--   5. 兜底自检：列出仍残留系统租户 tenant_id 的表（正常应为空）。
--
-- 边界：
--   本脚本只做「租户合并」，不区分平台管理员与租户管理员。system 账号的平台管理员角色与
--   全部菜单授权、以及悦航购租户管理员角色还原到套餐范围，由后续 197 脚本完成。
--
-- 安全：
--   * 仅当「悦航购」租户行存在时才执行任何写入/删除，避免在非种子环境误删平台租户；
--   * 全部语句幂等：系统租户已删除后重复执行均为空操作；
--   * 本脚本含物理删除，执行前请备份数据库；无 DROP TABLE / TRUNCATE。
--
-- 执行：mysql -u root -p aryn_boot < 196platform_tenant_consolidation.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
SET FOREIGN_KEY_CHECKS = 0;

-- 固定种子 ID（与 2aryn_boot.sql 基线一致）
SET @target_tenant_id = '1590229800633634816'; -- 悦航购
SET @legacy_tenant_id = '1881232176465358849'; -- 系统租户（待删除）
SET @legacy_system_user_id = '1881232178902249473'; -- 系统租户下的 system 管理员

-- 目标租户存在才执行合并；否则所有语句退化为空操作（非种子环境不会误删平台租户）
SET @target_exists = (
  SELECT COUNT(*) FROM `sys_tenant` WHERE `id` = @target_tenant_id AND `del_flag` = '0'
);

-- ============================================================================
-- 一、悦航购提升为平台租户：长期有效、不参与授权期管理
-- ============================================================================
UPDATE `sys_tenant`
SET `tenant_type` = '1',
    `auth_end_time` = NULL,
    `update_time` = NOW(),
    `update_by` = 'system'
WHERE `id` = @target_tenant_id
  AND `del_flag` = '0'
  AND @target_exists = 1;

-- ============================================================================
-- 二、system 管理员迁入悦航购：部门改挂目标租户根部门，账号本身保留
-- ============================================================================
SET @target_root_dept_id = (
  SELECT `id` FROM `sys_dept`
  WHERE `tenant_id` = @target_tenant_id AND `parent_id` = '0' AND `del_flag` = '0'
  ORDER BY `sort`, `id`
  LIMIT 1
);

UPDATE `sys_user`
SET `tenant_id` = @target_tenant_id,
    `dept_id` = COALESCE(@target_root_dept_id, `dept_id`),
    `update_time` = NOW(),
    `update_by` = 'system'
WHERE `id` = @legacy_system_user_id
  AND `tenant_id` = @legacy_tenant_id
  AND `del_flag` = '0'
  AND @target_exists = 1;

-- 角色关系：先删除旧租户下的角色关联；system 的平台管理员角色由 197 脚本授予
DELETE FROM `sys_user_role`
WHERE `user_id` = @legacy_system_user_id
  AND `tenant_id` = @legacy_tenant_id;

-- ============================================================================
-- 三、存储配置：目标租户没有启用配置时，继承系统租户的默认本机配置
-- ============================================================================
UPDATE `sys_storage_config` sc
JOIN (
  SELECT COUNT(*) AS cnt FROM `sys_storage_config`
  WHERE `tenant_id` = @target_tenant_id AND `del_flag` = '0' AND `status` = '0'
) target_cfg
SET sc.`tenant_id` = @target_tenant_id,
    sc.`update_time` = NOW(),
    sc.`update_by` = 'system'
WHERE sc.`tenant_id` = @legacy_tenant_id
  AND sc.`del_flag` = '0'
  AND sc.`status` = '0'
  AND target_cfg.cnt = 0
  AND @target_exists = 1;

-- ============================================================================
-- 四、物理删除系统租户及其余下数据
--     显式列出种子模型下平台租户会产生的表；顺序为先关联表、后主表、最后租户行
-- ============================================================================
DELETE FROM `sys_role_menu` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_tenant_menu` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_user_role` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_role` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_dept` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_storage_config` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_login_log` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_log` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_material` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_material_group` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `sys_user_wechat_binding` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
-- 基线种子在系统租户下遗留的商品规格测试行（goods_specs / goods_specs_value）
DELETE FROM `goods_specs_value` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
DELETE FROM `goods_specs` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;
-- 系统租户下的其它账号一并删除（system 已迁出，此处只兜底清理遗留账号）
DELETE FROM `sys_user` WHERE `tenant_id` = @legacy_tenant_id AND @target_exists = 1;

DELETE FROM `sys_tenant`
WHERE `id` = @legacy_tenant_id
  AND @target_exists = 1;

-- ============================================================================
-- 五、兜底自检：列出仍残留系统租户 tenant_id 的表（正常应为空结果集）
--     如出现残留（例如运维在系统租户下创建过业务数据），请按表名人工确认后再清理
-- ============================================================================
SET SESSION group_concat_max_len = 1048576;
SET @leftover_sql = (
  SELECT CONCAT(
    'SELECT * FROM (',
    GROUP_CONCAT(
      CONCAT('SELECT ''', TABLE_NAME, ''' AS table_name, COUNT(*) AS cnt FROM `',
             TABLE_NAME, '` WHERE `tenant_id` = ''', @legacy_tenant_id, '''')
      SEPARATOR ' UNION ALL '
    ),
    ') leftover WHERE cnt > 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND COLUMN_NAME = 'tenant_id'
);
PREPARE stmt FROM @leftover_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自检：悦航购应为唯一平台租户，且长期有效
SELECT `id`, `name`, `tenant_type`, `auth_end_time`
FROM `sys_tenant`
WHERE `tenant_type` = '1' AND `del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
