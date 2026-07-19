USE aryn_upms;

SET NAMES utf8mb4;

START TRANSACTION;

-- Cloud 初始化曾遗漏拼团菜单；IGNORE 仅用于兼容已手工补齐的存量库。
INSERT IGNORE INTO sys_menu
  (id, name, permission, path, redirect, parent_id, icon, component, sort, type,
   create_time, update_time, outer_status, del_flag, application_key, create_by, update_by)
VALUES
  ('1991000000000000050', '拼团管理', NULL, '/promotion/groupbuy', '/promotion/groupbuy/activity',
   '1779386604402573314', 'carbon:group', '', 21, '0', '2026-04-22 10:00:00', NULL, '0', '0',
   'app_market', 'system', 'system'),
  ('1991000000000000051', '拼团活动', NULL, '/promotion/groupbuy/activity', NULL,
   '1991000000000000050', 'carbon:flash', 'promotion/group-buy-activity/index', 1, '0',
   '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system'),
  ('1991000000000000052', '拼团活动分页', 'promotion:groupbuy:page', NULL, NULL,
   '1991000000000000051', NULL, NULL, 1, '1', '2026-04-22 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000053', '拼团活动查询', 'promotion:groupbuy:get', NULL, NULL,
   '1991000000000000051', NULL, NULL, 2, '1', '2026-04-22 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000054', '拼团活动新增', 'promotion:groupbuy:add', NULL, NULL,
   '1991000000000000051', NULL, NULL, 3, '1', '2026-04-22 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000055', '拼团活动修改', 'promotion:groupbuy:edit', NULL, NULL,
   '1991000000000000051', NULL, NULL, 4, '1', '2026-04-22 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000056', '拼团活动删除', 'promotion:groupbuy:del', NULL, NULL,
   '1991000000000000051', NULL, NULL, 5, '1', '2026-04-22 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000060', '拼团记录', NULL, '/promotion/groupbuy/record', NULL,
   '1991000000000000050', 'carbon:document', 'promotion/group-buy-record/index', 2, '0',
   '2026-04-22 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system'),
  ('1991000000000000061', '拼团记录分页', 'promotion:groupbuyrecord:page', NULL, NULL,
   '1991000000000000060', NULL, NULL, 1, '1', '2026-04-22 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL);

CREATE TEMPORARY TABLE tmp_menu_seed_repair (
  menu_id varchar(32) NOT NULL,
  menu_name varchar(60) NOT NULL,
  PRIMARY KEY (menu_id)
) ENGINE = MEMORY DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

INSERT INTO tmp_menu_seed_repair (menu_id, menu_name)
VALUES
  ('2050000000000000001', '会员等级'),
  ('2050000000000000002', '等级列表'),
  ('2050000000000000003', '等级查询'),
  ('2050000000000000004', '等级新增'),
  ('2050000000000000005', '等级编辑'),
  ('2050000000000000006', '等级删除'),
  ('2050000000000000010', '积分管理'),
  ('2050000000000000011', '积分配置'),
  ('2050000000000000012', '积分配置列表'),
  ('2050000000000000013', '积分配置查询'),
  ('2050000000000000014', '积分配置新增'),
  ('2050000000000000015', '积分配置编辑'),
  ('2050000000000000016', '积分配置删除'),
  ('2050000000000000021', '积分记录'),
  ('2050000000000000022', '积分记录查询'),
  ('2050000000000000030', '签到管理'),
  ('2050000000000000031', '签到配置'),
  ('2050000000000000032', '签到配置列表'),
  ('2050000000000000033', '签到配置查询'),
  ('2050000000000000034', '签到配置新增'),
  ('2050000000000000035', '签到配置编辑'),
  ('2050000000000000036', '签到配置删除'),
  ('2050000000000000041', '签到记录'),
  ('2050000000000000042', '签到记录查询'),
  ('2050000000000000051', '储值配置'),
  ('2050000000000000052', '储值配置列表'),
  ('2050000000000000053', '储值配置查询'),
  ('2050000000000000054', '储值配置新增'),
  ('2050000000000000055', '储值配置编辑'),
  ('2050000000000000056', '储值配置删除'),
  ('2050000000000000100', '余额记录'),
  ('2050000000000000101', '余额记录查询'),
  ('2050000000000000110', '充值订单'),
  ('2050000000000000111', '充值订单列表'),
  ('2050000000000000112', '充值订单查询'),
  ('2050000000000000120', '会员标签'),
  ('2050000000000000121', '标签列表'),
  ('2050000000000000122', '标签查询'),
  ('2050000000000000123', '标签新增'),
  ('2050000000000000124', '标签编辑'),
  ('2050000000000000125', '标签删除'),
  ('2050000000000000130', '会员权益'),
  ('2050000000000000131', '权益列表'),
  ('2050000000000000132', '权益查询'),
  ('2050000000000000133', '权益新增'),
  ('2050000000000000134', '权益编辑'),
  ('2050000000000000135', '权益删除'),
  ('1991000000000000050', '拼团管理'),
  ('1991000000000000051', '拼团活动'),
  ('1991000000000000052', '拼团活动分页'),
  ('1991000000000000053', '拼团活动查询'),
  ('1991000000000000054', '拼团活动新增'),
  ('1991000000000000055', '拼团活动修改'),
  ('1991000000000000056', '拼团活动删除'),
  ('1991000000000000060', '拼团记录'),
  ('1991000000000000061', '拼团记录分页');

UPDATE sys_menu AS menu
JOIN tmp_menu_seed_repair AS seed ON seed.menu_id = menu.id
SET menu.name = seed.menu_name;

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time, tenant_id)
SELECT REPLACE(UUID(), '-', ''), role.id, seed.menu_id, NOW(), role.tenant_id
FROM sys_role AS role
CROSS JOIN tmp_menu_seed_repair AS seed
WHERE role.del_flag = '0'
  AND role.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu AS role_menu
    WHERE role_menu.role_id = role.id
      AND role_menu.menu_id = seed.menu_id
      AND role_menu.tenant_id = role.tenant_id
  );

INSERT INTO sys_tenant_menu (id, tenant_id, menu_id, create_time, create_by)
SELECT REPLACE(UUID(), '-', ''), tenant.id, seed.menu_id, NOW(), 'system'
FROM sys_tenant AS tenant
CROSS JOIN tmp_menu_seed_repair AS seed
WHERE tenant.del_flag = '0'
  AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_tenant_menu AS tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id
      AND tenant_menu.menu_id = seed.menu_id
  );

DROP TEMPORARY TABLE tmp_menu_seed_repair;

COMMIT;
