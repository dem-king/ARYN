USE aryn_boot;

SET NAMES utf8mb4;

START TRANSACTION;

INSERT IGNORE INTO sys_dict_value
  (id, dict_id, dict_label, dict_value, dict_type, status, remarks, sort, del_flag,
   create_time, update_time, create_by, update_by, show_class)
VALUES
  ('2080000000000000090', '1825787265549987842', '商城配送', '3', 'delivery_way', '0',
   '商城员工配送', 3, '0', NOW(), NULL, 'system', NULL, 'primary');

INSERT IGNORE INTO sys_menu
  (id, name, permission, path, redirect, parent_id, icon, component, sort, type,
   create_time, update_time, outer_status, del_flag, application_key, create_by, update_by)
VALUES
  ('2080000000000000000', '商城配送', NULL, '/order/delivery', '/order/delivery-task',
   '1521496866882236418', 'carbon:delivery', '', 30, '0', NOW(), NULL, '0', '0', 'app_base', 'system', 'system'),
  ('2080000000000000001', '配送任务', NULL, '/order/delivery-task', NULL,
   '2080000000000000000', 'carbon:task', 'order/delivery-task/index', 1, '0', NOW(), NULL, '0', '0', 'app_base', 'system', 'system'),
  ('2080000000000000002', '配送任务分页', 'order:delivery:page', NULL, NULL,
   '2080000000000000001', NULL, NULL, 1, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2080000000000000003', '配送任务查询', 'order:delivery:get', NULL, NULL,
   '2080000000000000001', NULL, NULL, 2, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2080000000000000004', '配送任务派单', 'order:delivery:assign', NULL, NULL,
   '2080000000000000001', NULL, NULL, 3, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2080000000000000005', '配送任务改派', 'order:delivery:reassign', NULL, NULL,
   '2080000000000000001', NULL, NULL, 4, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2080000000000000006', '配送异常处理', 'order:delivery:exception', NULL, NULL,
   '2080000000000000001', NULL, NULL, 5, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2080000000000000007', '配送退回处理', 'order:delivery:return', NULL, NULL,
   '2080000000000000001', NULL, NULL, 6, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2080000000000000008', '配送员履约', 'order:delivery:execute', NULL, NULL,
   '2080000000000000001', NULL, NULL, 7, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2080000000000000020', '配送范围', NULL, '/order/delivery-area', NULL,
   '2080000000000000000', 'carbon:map-boundary', 'order/delivery-area/index', 2, '0', NOW(), NULL, '0', '0', 'app_base', 'system', 'system'),
  ('2080000000000000021', '配送范围管理', 'order:delivery:area', NULL, NULL,
   '2080000000000000020', NULL, NULL, 1, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time, tenant_id)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW(), role.tenant_id
FROM sys_role AS role
JOIN sys_menu AS menu ON menu.id BETWEEN '2080000000000000000' AND '2080000000000000021'
WHERE role.del_flag = '0'
  AND role.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu AS role_menu
    WHERE role_menu.role_id = role.id
      AND role_menu.menu_id = menu.id
      AND role_menu.tenant_id = role.tenant_id
  );

INSERT INTO sys_tenant_menu (id, tenant_id, menu_id, create_time, create_by)
SELECT REPLACE(UUID(), '-', ''), tenant.id, menu.id, NOW(), 'system'
FROM sys_tenant AS tenant
JOIN sys_menu AS menu ON menu.id BETWEEN '2080000000000000000' AND '2080000000000000021'
WHERE tenant.del_flag = '0'
  AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1 FROM sys_tenant_menu AS tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id
      AND tenant_menu.menu_id = menu.id
  );

COMMIT;
