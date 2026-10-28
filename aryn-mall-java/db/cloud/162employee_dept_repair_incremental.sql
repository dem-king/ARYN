-- 员工账号悬挂部门修复增量迁移（Cloud 微服务模式）
-- 目标库：aryn_upms（UPMS 基线 2aryn_upms.sql 已含 sys_user/sys_dept 表）
-- 背景：历史种子数据里 sys_user.dept_id 可能指向已不存在（或已逻辑删除）的部门，
--       例如 admin 的 dept_id='2' 而 sys_dept 只剩根部门 id='1'；
--       导致员工账号页按部门筛选时该员工被过滤掉、列表「部门」列也无法回显。
-- 修复口径：dept_id 指向同租户下不存在/已删除部门时，回填为同租户根部门
--       （parent_id='0' 且 del_flag='0'，多根时取 id 最小者）；
--       dept_id 为空或同租户没有根部门的账号保持原值，不做破坏性变更。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有合法业务数据。
-- 执行：mysql -u root -p aryn_upms < 162employee_dept_repair_incremental.sql

USE `aryn_upms`;
SET NAMES utf8mb4;

UPDATE `sys_user` u
JOIN (
    SELECT d.tenant_id, MIN(d.id) AS id
    FROM `sys_dept` d
    WHERE d.parent_id = '0' AND d.del_flag = '0'
    GROUP BY d.tenant_id
) root ON root.tenant_id = u.tenant_id
SET u.dept_id = root.id
WHERE u.del_flag = '0'
  AND u.dept_id IS NOT NULL
  AND u.dept_id <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM `sys_dept` d2
      WHERE d2.id = u.dept_id
        AND d2.tenant_id = u.tenant_id
        AND d2.del_flag = '0'
  );

-- 与全量脚本约定保持一致：末尾恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;
