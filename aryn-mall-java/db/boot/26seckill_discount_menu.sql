USE aryn_boot;

SET NAMES utf8mb4;

START TRANSACTION;

-- 秒杀活动与折扣活动菜单种子；IGNORE 兼容早期或不完整的存量库。
-- 编辑页路由已在前端 core.ts 静态注册（hideInMenu），此处仅配置列表页与按钮权限。
INSERT IGNORE INTO sys_menu
  (id, name, permission, path, redirect, parent_id, icon, component, sort, type,
   create_time, update_time, outer_status, del_flag, application_key, create_by, update_by)
VALUES
  -- 秒杀管理（目录）
  ('1991000000000000070', '秒杀管理', NULL, '/promotion/seckill-activity', '/promotion/seckill-activity/index',
   '1779386604402573314', 'carbon:flash', '', 22, '0', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', 'system'),
  -- 秒杀活动列表
  ('1991000000000000071', '秒杀活动', NULL, '/promotion/seckill-activity/index', NULL,
   '1991000000000000070', 'carbon:flash', 'promotion/seckill-activity/index', 1, '0',
   '2026-08-02 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system'),
  -- 秒杀活动按钮权限
  ('1991000000000000072', '秒杀活动分页', 'promotion:seckill:page', NULL, NULL,
   '1991000000000000071', NULL, NULL, 1, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000073', '秒杀活动查询', 'promotion:seckill:get', NULL, NULL,
   '1991000000000000071', NULL, NULL, 2, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000074', '秒杀活动新增', 'promotion:seckill:add', NULL, NULL,
   '1991000000000000071', NULL, NULL, 3, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000075', '秒杀活动修改', 'promotion:seckill:edit', NULL, NULL,
   '1991000000000000071', NULL, NULL, 4, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000076', '秒杀活动删除', 'promotion:seckill:del', NULL, NULL,
   '1991000000000000071', NULL, NULL, 5, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000077', '秒杀活动状态', 'promotion:seckill:status', NULL, NULL,
   '1991000000000000071', NULL, NULL, 6, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),

  -- 折扣管理（目录）
  ('1991000000000000080', '折扣管理', NULL, '/promotion/discount-activity', '/promotion/discount-activity/index',
   '1779386604402573314', 'carbon:percentage', '', 23, '0', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', 'system'),
  -- 折扣活动列表
  ('1991000000000000081', '折扣活动', NULL, '/promotion/discount-activity/index', NULL,
   '1991000000000000080', 'carbon:percentage', 'promotion/discount-activity/index', 1, '0',
   '2026-08-02 10:00:00', NULL, '0', '0', 'app_market', 'system', 'system'),
  -- 折扣活动按钮权限
  ('1991000000000000082', '折扣活动分页', 'promotion:discount:page', NULL, NULL,
   '1991000000000000081', NULL, NULL, 1, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000083', '折扣活动查询', 'promotion:discount:get', NULL, NULL,
   '1991000000000000081', NULL, NULL, 2, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000084', '折扣活动新增', 'promotion:discount:add', NULL, NULL,
   '1991000000000000081', NULL, NULL, 3, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000085', '折扣活动修改', 'promotion:discount:edit', NULL, NULL,
   '1991000000000000081', NULL, NULL, 4, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000086', '折扣活动删除', 'promotion:discount:del', NULL, NULL,
   '1991000000000000081', NULL, NULL, 5, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL),
  ('1991000000000000087', '折扣活动状态', 'promotion:discount:status', NULL, NULL,
   '1991000000000000081', NULL, NULL, 6, '1', '2026-08-02 10:00:00', NULL, '0', '0',
   'app_market', 'system', NULL);

-- 临时表：收集本次新增的菜单 ID，用于批量授权
CREATE TEMPORARY TABLE tmp_menu_seckill_discount (
  menu_id varchar(32) NOT NULL,
  PRIMARY KEY (menu_id)
) ENGINE = MEMORY DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

INSERT INTO tmp_menu_seckill_discount (menu_id)
VALUES
  ('1991000000000000070'),
  ('1991000000000000071'),
  ('1991000000000000072'),
  ('1991000000000000073'),
  ('1991000000000000074'),
  ('1991000000000000075'),
  ('1991000000000000076'),
  ('1991000000000000077'),
  ('1991000000000000080'),
  ('1991000000000000081'),
  ('1991000000000000082'),
  ('1991000000000000083'),
  ('1991000000000000084'),
  ('1991000000000000085'),
  ('1991000000000000086'),
  ('1991000000000000087');

-- 为所有租户的 ROLE_ADMIN 角色授权
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time, tenant_id)
SELECT REPLACE(UUID(), '-', ''), role.id, seed.menu_id, NOW(), role.tenant_id
FROM sys_role AS role
CROSS JOIN tmp_menu_seckill_discount AS seed
WHERE role.del_flag = '0'
  AND role.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu AS role_menu
    WHERE role_menu.role_id = role.id
      AND role_menu.menu_id = seed.menu_id
      AND role_menu.tenant_id = role.tenant_id
  );

-- 为所有租户授权菜单（平台租户除外，平台租户不过滤菜单）
INSERT INTO sys_tenant_menu (id, tenant_id, menu_id, create_time, create_by)
SELECT REPLACE(UUID(), '-', ''), tenant.id, seed.menu_id, NOW(), 'system'
FROM sys_tenant AS tenant
CROSS JOIN tmp_menu_seckill_discount AS seed
WHERE tenant.del_flag = '0'
  AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_tenant_menu AS tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id
      AND tenant_menu.menu_id = seed.menu_id
  );

DROP TEMPORARY TABLE tmp_menu_seckill_discount;

COMMIT;