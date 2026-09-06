-- 员工账号与配送员管理重构增量迁移（Cloud 微服务模式）
-- 目标库：aryn_upms（预检查 2 需切换 aryn_order，与 27delivery_fulfillment_incremental.sql 相同的多库执行方式）
-- 背景：
--   1) delivery_staff（配送员）为受保护角色，只能由配送员管理（资格接口/向导）授予或回收，
--      通用“新增/修改员工账号”接口不再接受该角色；为避免管理员被迫选择超级管理员，
--      幂等补齐每个租户的最低权限员工角色“普通员工”（ROLE_STAFF）。
--   2) 系统设置菜单“用户管理”更名为“员工账号”，路由 /system/user 与权限编码保持不变。
--   3) 输出重复角色与配送资格历史异常数据的预检查结果，供管理员确认后人工迁移；
--      仅在不存在重复角色编码时补齐 (tenant_id, role_code) 唯一约束。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不物理删除存量数据。
-- 执行：mysql -u root -p < 37employee_role_scope.sql

USE `aryn_upms`;
SET NAMES utf8mb4;

-- 1) 预检查 1：同一租户同一角色编码的重复有效角色（应返回空结果集；
--    如有返回，请人工保留一条稳定角色ID，其余角色的菜单/用户关联逻辑迁移后置 del_flag='1'，
--    禁止脚本自动删除；清理完成后重跑本脚本即可补齐唯一约束）
SELECT tenant_id, role_code, COUNT(*) AS cnt
FROM sys_role
WHERE del_flag = '0'
GROUP BY tenant_id, role_code
HAVING COUNT(*) > 1;

-- 2) 预检查 3（与 Boot 对应）：迁移台账（受影响租户与角色统计，供上线记录留存）
SELECT sr.tenant_id, sr.role_code, sr.role_name, COUNT(sur.id) AS user_count
FROM sys_role sr
LEFT JOIN sys_user_role sur
       ON sur.role_id = sr.id
      AND sur.del_flag = '0'
WHERE sr.del_flag = '0'
  AND sr.role_code IN ('delivery_staff', 'ROLE_STAFF')
GROUP BY sr.tenant_id, sr.role_code, sr.role_name;

-- 3) 幂等补齐每个租户的最低权限员工角色“普通员工”（ROLE_STAFF）：
--    覆盖 sys_tenant 登记租户与 sys_user 中实际存在的租户，已存在不重复插入
INSERT INTO sys_role (id, role_name, role_code, role_desc, create_time, del_flag, tenant_id, create_by)
SELECT REPLACE(UUID(), '-', ''), '普通员工', 'ROLE_STAFF',
       '最低权限员工角色：满足员工账号基础角色要求，不含配送资格与管理菜单',
       NOW(), '0', t.tenant_id, 'system'
FROM (
    SELECT id AS tenant_id FROM sys_tenant WHERE del_flag = '0'
    UNION
    SELECT DISTINCT tenant_id FROM sys_user WHERE del_flag = '0' AND tenant_id IS NOT NULL
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role r
    WHERE r.tenant_id = t.tenant_id
      AND r.role_code = 'ROLE_STAFF'
      AND r.del_flag = '0'
);

-- 4) 幂等菜单更名：用户管理 -> 员工账号（路由与权限编码不变）
UPDATE sys_menu
SET name = '员工账号', update_time = NOW(), update_by = 'system'
WHERE id = '1491752531735490561'
  AND name = '用户管理';

-- 5) 幂等补齐有效角色编码生成列（逻辑删除行不占用唯一键）
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_role'
      AND COLUMN_NAME = 'active_role_code') = 0,
  'ALTER TABLE `sys_role` ADD COLUMN `active_role_code` varchar(50) GENERATED ALWAYS AS (CASE WHEN `del_flag` = ''0'' THEN `role_code` ELSE NULL END) STORED COMMENT ''有效角色编码（生成列，供唯一键使用）''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) 仅在当前不存在重复角色编码时补齐唯一约束；存在重复（预检查 1 有结果）时跳过并提示，
--    待人工迁移完成后重跑本脚本
SET @dup_count = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, role_code
    FROM sys_role
    WHERE del_flag = '0'
    GROUP BY tenant_id, role_code
    HAVING COUNT(*) > 1
  ) d);
SET @ddl = IF(
  @dup_count = 0
  AND (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_role'
      AND INDEX_NAME = 'uk_sys_role_tenant_code') = 0,
  'ALTER TABLE `sys_role` ADD UNIQUE KEY `uk_sys_role_tenant_code` (`tenant_id`, `active_role_code`)',
  'SELECT ''存在重复角色编码或约束已存在，跳过唯一约束创建（见预检查 1）'' AS migrate_notice');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7) 预检查 2：拥有配送员角色但没有配送员资料的员工账号（跨库：aryn_order.delivery_staff，
--    应返回空结果集；如有返回，请在配送员管理中补建配送资料，或确认后回收配送资格。
--    如环境中 aryn_upms 与 aryn_order 不在同一 MySQL 实例，请在各自库手工执行等价查询）
USE `aryn_order`;
SELECT sur.tenant_id, sur.user_id, su.username, su.nickname
FROM aryn_upms.sys_user_role sur
INNER JOIN aryn_upms.sys_role sr
        ON sr.id = sur.role_id
       AND sr.del_flag = '0'
       AND sr.role_code = 'delivery_staff'
INNER JOIN aryn_upms.sys_user su
        ON su.id = sur.user_id
       AND su.del_flag = '0'
WHERE sur.del_flag = '0'
  AND NOT EXISTS (SELECT 1
                  FROM delivery_staff ds
                  WHERE ds.user_id = sur.user_id
                    AND ds.del_flag = '0');

SET FOREIGN_KEY_CHECKS = 1;
