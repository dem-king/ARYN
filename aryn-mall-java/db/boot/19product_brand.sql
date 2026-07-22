USE aryn_boot;

CREATE TABLE IF NOT EXISTS goods_brand (
  id varchar(32) NOT NULL COMMENT '主键',
  name varchar(50) NOT NULL COMMENT '品牌名称',
  logo_url varchar(500) DEFAULT NULL COMMENT '品牌 Logo',
  description varchar(255) DEFAULT NULL COMMENT '品牌描述',
  status char(1) NOT NULL DEFAULT '0' COMMENT '状态：0.启用；1.停用',
  sort int NOT NULL DEFAULT 0 COMMENT '排序序号',
  tenant_id varchar(32) NOT NULL COMMENT '租户 ID',
  create_by varchar(60) DEFAULT NULL COMMENT '创建人',
  update_by varchar(60) DEFAULT NULL COMMENT '修改人',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_time datetime DEFAULT NULL COMMENT '修改时间',
  del_flag char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.正常；1.删除',
  PRIMARY KEY (id),
  KEY idx_goods_brand_tenant_status (tenant_id, status, del_flag),
  KEY idx_goods_brand_tenant_name (tenant_id, name, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品品牌';

ALTER TABLE goods_brand
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

SET @brand_column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'goods_spu' AND column_name = 'brand_id'
);
SET @brand_column_sql = IF(
  @brand_column_exists = 0,
  'ALTER TABLE goods_spu ADD COLUMN brand_id varchar(32) DEFAULT NULL COMMENT ''品牌 ID'' AFTER category_second_id, ADD KEY idx_goods_spu_brand (tenant_id, brand_id)',
  'SELECT 1'
);
PREPARE brand_column_stmt FROM @brand_column_sql;
EXECUTE brand_column_stmt;
DEALLOCATE PREPARE brand_column_stmt;

INSERT INTO sys_menu
  (id, name, permission, path, redirect, parent_id, icon, component, sort, type,
   create_time, update_time, outer_status, del_flag, application_key, create_by, update_by)
VALUES
  ('2060000000000000001', '商品品牌', NULL, '/product/goods-brand', NULL, '1779386487675092994', 'carbon:tag-group', 'product/goods-brand/index', 6, '0', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2060000000000000002', '商品品牌列表', 'product:goodsbrand:page', NULL, NULL, '2060000000000000001', NULL, NULL, 1, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2060000000000000003', '商品品牌查询', 'product:goodsbrand:get', NULL, NULL, '2060000000000000001', NULL, NULL, 2, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2060000000000000004', '商品品牌新增', 'product:goodsbrand:add', NULL, NULL, '2060000000000000001', NULL, NULL, 3, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2060000000000000005', '商品品牌修改', 'product:goodsbrand:edit', NULL, NULL, '2060000000000000001', NULL, NULL, 4, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2060000000000000006', '商品品牌删除', 'product:goodsbrand:del', NULL, NULL, '2060000000000000001', NULL, NULL, 5, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), permission = VALUES(permission), path = VALUES(path),
  parent_id = VALUES(parent_id), icon = VALUES(icon), component = VALUES(component),
  sort = VALUES(sort), type = VALUES(type), del_flag = '0';

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time, tenant_id)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW(), role.tenant_id
FROM sys_role role
JOIN sys_menu menu ON menu.id BETWEEN '2060000000000000001' AND '2060000000000000006'
WHERE role.del_flag = '0' AND role.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu role_menu
    WHERE role_menu.role_id = role.id AND role_menu.menu_id = menu.id
      AND role_menu.tenant_id = role.tenant_id
  );

INSERT INTO sys_tenant_menu (id, tenant_id, menu_id, create_time, create_by)
SELECT REPLACE(UUID(), '-', ''), tenant.id, menu.id, NOW(), 'system'
FROM sys_tenant tenant
JOIN sys_menu menu ON menu.id BETWEEN '2060000000000000001' AND '2060000000000000006'
WHERE tenant.del_flag = '0' AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1 FROM sys_tenant_menu tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id AND tenant_menu.menu_id = menu.id
  );
