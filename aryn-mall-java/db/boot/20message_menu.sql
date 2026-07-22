USE aryn_boot;

SET NAMES utf8mb4;

START TRANSACTION;

INSERT IGNORE INTO sys_menu
  (id, name, permission, path, redirect, parent_id, icon, component, sort, type,
   create_time, update_time, outer_status, del_flag, application_key, create_by, update_by)
VALUES
  ('2070000000000000000', '消息中心', NULL, '/message', '/message/inbox', '0',
   'carbon:email', '', 65, '0', NOW(), NULL, '0', '0', 'app_base', 'system', 'system'),
  ('2070000000000000001', '通知收件箱', NULL, '/message/inbox', NULL, '2070000000000000000',
   'carbon:notification', 'message/inbox/index', 1, '0', NOW(), NULL, '0', '0', 'app_base', 'system', 'system'),
  ('2070000000000000010', '通知管理', NULL, '/message/notice', NULL, '2070000000000000000',
   'carbon:bullhorn', 'message/notice/index', 2, '0', NOW(), NULL, '0', '0', 'app_base', 'system', 'system'),
  ('2070000000000000011', '通知分页', 'message:notice:page', NULL, NULL, '2070000000000000010',
   NULL, NULL, 1, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000012', '通知查询', 'message:notice:get', NULL, NULL, '2070000000000000010',
   NULL, NULL, 2, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000013', '通知新增', 'message:notice:add', NULL, NULL, '2070000000000000010',
   NULL, NULL, 3, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000014', '通知修改', 'message:notice:edit', NULL, NULL, '2070000000000000010',
   NULL, NULL, 4, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000015', '通知发布', 'message:notice:publish', NULL, NULL, '2070000000000000010',
   NULL, NULL, 5, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000016', '通知撤回', 'message:notice:revoke', NULL, NULL, '2070000000000000010',
   NULL, NULL, 6, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000020', '客服工作台', NULL, '/message/service', NULL, '2070000000000000000',
   'carbon:headset', 'message/service/index', 3, '0', NOW(), NULL, '0', '0', 'app_base', 'system', 'system'),
  ('2070000000000000021', '客服坐席配置', 'message:service:agent', NULL, NULL, '2070000000000000020',
   NULL, NULL, 1, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000022', '客服主管操作', 'message:service:supervisor', NULL, NULL, '2070000000000000020',
   NULL, NULL, 2, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000023', '主动联系会员', 'message:conversation:initiate', NULL, NULL, '2070000000000000020',
   NULL, NULL, 3, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2070000000000000030', '工作人员私信', NULL, '/message/direct', NULL, '2070000000000000000',
   'carbon:chat', 'message/direct/index', 4, '0', NOW(), NULL, '0', '0', 'app_base', 'system', 'system'),
  ('2070000000000000031', '发起工作人员私信', 'message:staff:direct', NULL, NULL, '2070000000000000030',
   NULL, NULL, 1, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL);

CREATE TEMPORARY TABLE tmp_message_common_menu (
  menu_id varchar(32) NOT NULL,
  PRIMARY KEY (menu_id)
) ENGINE = MEMORY DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

INSERT INTO tmp_message_common_menu (menu_id)
VALUES
  ('2070000000000000000'),
  ('2070000000000000001'),
  ('2070000000000000030'),
  ('2070000000000000031');

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time, tenant_id)
SELECT REPLACE(UUID(), '-', ''), role.id, common.menu_id, NOW(), role.tenant_id
FROM sys_role AS role
CROSS JOIN tmp_message_common_menu AS common
WHERE role.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu AS role_menu
    WHERE role_menu.role_id = role.id
      AND role_menu.menu_id = common.menu_id
      AND role_menu.tenant_id = role.tenant_id
  );

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time, tenant_id)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW(), role.tenant_id
FROM sys_role AS role
JOIN sys_menu AS menu ON menu.id BETWEEN '2070000000000000000' AND '2070000000000000031'
WHERE role.del_flag = '0'
  AND role.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu AS role_menu
    WHERE role_menu.role_id = role.id
      AND role_menu.menu_id = menu.id
      AND role_menu.tenant_id = role.tenant_id
  );

INSERT INTO sys_tenant_menu (id, tenant_id, menu_id, create_time, create_by)
SELECT REPLACE(UUID(), '-', ''), tenant.id, menu.id, NOW(), 'system'
FROM sys_tenant AS tenant
JOIN sys_menu AS menu ON menu.id BETWEEN '2070000000000000000' AND '2070000000000000031'
WHERE tenant.del_flag = '0'
  AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_tenant_menu AS tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id
      AND tenant_menu.menu_id = menu.id
  );

DROP TEMPORARY TABLE tmp_message_common_menu;

COMMIT;
