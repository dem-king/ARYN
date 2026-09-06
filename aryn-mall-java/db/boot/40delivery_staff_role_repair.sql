-- 配送员受保护角色修复与遗留角色下线增量迁移（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：
--   1) 29 号脚本按租户种子 delivery_staff（配送员）角色时，若该角色事后在角色管理中被
--      逻辑删除，重跑 29 号会因主键 MD5('delivery_staff_role_'+tenant_id) 冲突被
--      INSERT IGNORE 静默跳过，角色无法自愈；配送员管理开通/停用资格即报
--      “未找到编码为 delivery_staff 的角色，请先在角色管理中创建”。
--      本脚本幂等恢复被逻辑删除的受保护角色，并补挂配送执行权限菜单
--      （delivery:execute，菜单 2100000000000000017）。
--   2) 老版配送模块遗留角色 ROLE_DELIVERY_STAFF（与受保护角色同名“配送员”）在现行代码中
--      已无任何引用，同名并存曾在角色管理中造成误删受保护角色，按逻辑删除下线；
--      其用户绑定与角色菜单关联保留作历史，权限计算只关联有效角色，不受影响。
--      不迁移其用户绑定：配送资格在新体系下只能经配送员管理开通（fail-closed），
--      自动改绑等价于越权开通。
--   3) 配套后端防护：角色管理删除受保护角色、或修改其角色编码会被拒绝
--      （SysRoleController，CommonConstants.PROTECTED_DELIVERY_ROLE_CODE）。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不物理删除存量数据。
-- 执行：mysql -u root -p aryn_boot < 40delivery_staff_role_repair.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 预检查 1：受保护配送员角色现状（每租户应恰一行有效；del_flag=1 即待修复对象）
SELECT tenant_id, id, role_name, del_flag, update_time, update_by
FROM sys_role
WHERE role_code = 'delivery_staff'
ORDER BY tenant_id, del_flag;

-- 2) 预检查 2：遗留同名配送员角色及其有效用户绑定台账（下线前留档，应只有历史意义）
SELECT sr.tenant_id, sr.id AS role_id, sr.del_flag, COUNT(sur.id) AS bound_users
FROM sys_role sr
LEFT JOIN sys_user_role sur ON sur.role_id = sr.id AND sur.del_flag = '0'
WHERE sr.role_code = 'ROLE_DELIVERY_STAFF'
GROUP BY sr.tenant_id, sr.id, sr.del_flag;

-- 3) 幂等恢复被逻辑删除的 delivery_staff：仅当该租户不存在有效 delivery_staff 角色时，
--    每租户恢复一行（取 MAX(id)，避免多行删除数据触发有效编码唯一键冲突）；
--    保留原角色ID，历史绑定与资格操作日志的 role 引用不断裂
UPDATE sys_role sr
JOIN (
    SELECT MAX(r.id) AS id
    FROM sys_role r
    WHERE r.role_code = 'delivery_staff'
      AND r.del_flag = '1'
      AND NOT EXISTS (
          SELECT 1 FROM sys_role ok
          WHERE ok.tenant_id = r.tenant_id
            AND ok.role_code = 'delivery_staff'
            AND ok.del_flag = '0')
    GROUP BY r.tenant_id
) todo ON todo.id = sr.id
SET sr.del_flag = '0', sr.update_time = NOW(), sr.update_by = 'system';

-- 4) 幂等补齐从未有过 delivery_staff 的租户（与 29 号同构：角色ID = MD5('delivery_staff_role_'+tenant_id)；
--    覆盖 sys_tenant 登记租户与 sys_user 实际租户；NOT EXISTS 不过滤 del_flag，
--    与步骤 3 的恢复逻辑互补，任何存量行（含已删除）都不会重复插入）
INSERT INTO sys_role (id, role_name, role_code, role_desc, create_time, del_flag, tenant_id, create_by)
SELECT MD5(CONCAT('delivery_staff_role_', t.tenant_id)), '配送员', 'delivery_staff',
       '移动端配送工作台执行角色，仅包含配送执行权限', NOW(), '0', t.tenant_id, 'system'
FROM (
    SELECT id AS tenant_id FROM sys_tenant WHERE del_flag = '0'
    UNION
    SELECT DISTINCT tenant_id FROM sys_user WHERE del_flag = '0' AND tenant_id IS NOT NULL
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role r
    WHERE r.tenant_id = t.tenant_id
      AND r.role_code = 'delivery_staff'
);

-- 5) 幂等补挂配送执行权限（delivery:execute，菜单 2100000000000000017）：
--    角色管理删除角色时会连带清空 sys_role_menu，恢复角色后必须补回
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time, tenant_id)
SELECT MD5(CONCAT(r.id, ':menu:2100000000000000017')), r.id, '2100000000000000017', NOW(), r.tenant_id
FROM sys_role r
WHERE r.role_code = 'delivery_staff' AND r.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm
    WHERE rm.role_id = r.id AND rm.menu_id = '2100000000000000017');

-- 6) 下线遗留同名配送员角色 ROLE_DELIVERY_STAFF（逻辑删除；现行代码零引用）
UPDATE sys_role
SET del_flag = '1', update_time = NOW(), update_by = 'system'
WHERE role_code = 'ROLE_DELIVERY_STAFF' AND del_flag = '0';

-- 7) 结果核验：每租户应恰一行有效 delivery_staff 且已挂配送执行菜单；ROLE_DELIVERY_STAFF 应全部 del_flag=1
SELECT sr.tenant_id, sr.role_code, sr.id, sr.del_flag,
       (SELECT COUNT(*) FROM sys_role_menu rm
         WHERE rm.role_id = sr.id AND rm.menu_id = '2100000000000000017') AS has_execute_menu
FROM sys_role sr
WHERE sr.role_code IN ('delivery_staff', 'ROLE_DELIVERY_STAFF')
ORDER BY sr.role_code, sr.tenant_id;

SET FOREIGN_KEY_CHECKS = 1;
