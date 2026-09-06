-- 悦航购配送员商城账号绑定增量迁移（Cloud 微服务模式）
-- 订单库：aryn_order；菜单/权限库：aryn_upms；配置库：aryn_nacos
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p < 29delivery_account_binding.sql

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 配送员与商城用户绑定关系表
-- 说明：
-- 1. 同一商城用户在租户内全历史仅一行（唯一键 tenant_id+mall_user_id），
--    解绑通过 status='0' 保留记录，重新绑定时复用该行更新归属。
-- 2. active_sys_user_id 为生成列：仅有效绑定（status='1' 且未删除）取 sys_user_id，
--    配合唯一键 uk_delivery_binding_active_sys_user 在数据库层保证
--    同一员工账号在租户内最多一个有效绑定（解绑行为 NULL，不占用唯一键）。
CREATE TABLE IF NOT EXISTS `delivery_account_binding` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `mall_user_id` varchar(32) NOT NULL COMMENT '商城用户ID（user_info.id）',
  `sys_user_id` varchar(32) NOT NULL COMMENT '员工账号ID（sys_user.id）',
  `delivery_staff_id` varchar(32) NOT NULL COMMENT '配送员资料ID（delivery_staff.id）',
  `status` varchar(2) NOT NULL DEFAULT '1' COMMENT '绑定状态：1有效 0解绑',
  `active_sys_user_id` varchar(32) GENERATED ALWAYS AS (
    CASE WHEN `status` = '1' AND `del_flag` = '0' THEN `sys_user_id` ELSE NULL END
  ) STORED COMMENT '有效绑定员工账号（生成列，供唯一键使用）',
  `bind_time` datetime DEFAULT NULL COMMENT '绑定时间',
  `unbind_time` datetime DEFAULT NULL COMMENT '解绑时间',
  `bind_by` varchar(60) DEFAULT NULL COMMENT '绑定操作人',
  `unbind_by` varchar(60) DEFAULT NULL COMMENT '解绑操作人',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_binding_mall_user` (`tenant_id`, `mall_user_id`),
  UNIQUE KEY `uk_delivery_binding_active_sys_user` (`tenant_id`, `active_sys_user_id`),
  KEY `idx_delivery_binding_sys_user` (`tenant_id`, `sys_user_id`),
  KEY `idx_delivery_binding_staff` (`tenant_id`, `delivery_staff_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='配送员商城账号绑定';

USE `aryn_upms`;

-- 配送员管理新增按钮权限（平台级定义，IGNORE 幂等）
INSERT IGNORE INTO `sys_menu` (`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`, `create_time`, `outer_status`, `del_flag`, `application_key`, `create_by`) VALUES
('2100000000000000060', '绑定商城账号', 'delivery:staff:bind', NULL, NULL, '2100000000000000003', '', NULL, 8, '1', NOW(), '0', '0', 'app_base', 'system'),
('2100000000000000061', '配送资格管理', 'delivery:staff:qualification', NULL, NULL, '2100000000000000003', '', NULL, 9, '1', NOW(), '0', '0', 'app_base', 'system'),
('2100000000000000062', '接单状态管理', 'delivery:staff:availability', NULL, NULL, '2100000000000000003', '', NULL, 10, '1', NOW(), '0', '0', 'app_base', 'system');

-- 配送员执行角色按租户初始化（平台定义 + 存量租户幂等补齐，不写死租户ID）
-- 角色ID = MD5('delivery_staff_role_' + tenant_id)，稳定且租户内唯一；
-- 移动端免重复登录依赖该角色携带 delivery:execute（菜单 2100000000000000017）。
INSERT IGNORE INTO `sys_role` (`id`, `role_name`, `role_code`, `role_desc`, `create_time`, `update_time`, `del_flag`, `tenant_id`, `create_by`)
SELECT MD5(CONCAT('delivery_staff_role_', t.`id`)), '配送员', 'delivery_staff',
       '移动端配送工作台执行角色，仅包含配送执行权限', NOW(), NULL, '0', t.`id`, 'system'
FROM `sys_tenant` t
WHERE t.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role` r
    WHERE r.`role_code` = 'delivery_staff' AND r.`tenant_id` = t.`id` AND r.`del_flag` = '0'
  );

-- 配送员角色授予配送执行权限（按角色逐条幂等补齐）
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(r.`id`, ':menu:2100000000000000017')), r.`id`, '2100000000000000017', NOW(), r.`tenant_id`
FROM `sys_role` r
WHERE r.`role_code` = 'delivery_staff' AND r.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm
    WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = '2100000000000000017'
  );

-- 新增按钮分配给各租户管理员角色（role_code=ROLE_ADMIN，按租户幂等补齐，不写死租户ID）
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT MD5(CONCAT(r.`id`, ':menu:', m.`menu_id`)), r.`id`, m.`menu_id`, NOW(), r.`tenant_id`
FROM `sys_role` r
JOIN (
  SELECT '2100000000000000060' AS menu_id
  UNION ALL SELECT '2100000000000000061'
  UNION ALL SELECT '2100000000000000062'
) m ON 1=1
WHERE r.`role_code` = 'ROLE_ADMIN' AND r.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm
    WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`menu_id`
  );

-- 新增按钮按租户初始化租户菜单（不写死租户ID）
INSERT IGNORE INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT MD5(CONCAT(t.`id`, ':menu:', m.`menu_id`)), t.`id`, m.`menu_id`, NOW(), 'system'
FROM `sys_tenant` t
JOIN (
  SELECT '2100000000000000060' AS menu_id
  UNION ALL SELECT '2100000000000000061'
  UNION ALL SELECT '2100000000000000062'
) m ON 1=1
WHERE t.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_tenant_menu` tm
    WHERE tm.`tenant_id` = t.`id` AND tm.`menu_id` = m.`menu_id`
  );

-- Cloud 订单服务租户表清单（Nacos config_info）。重复执行不会重复追加；
-- content 变更时同步 md5 与 gmt_modified，保证 Nacos 客户端一致性校验通过。
USE `aryn_nacos`;
UPDATE `config_info`
SET `content` = REPLACE(`content`, '      - delivery_area',
  '      - delivery_area\n      - delivery_account_binding'),
    `md5` = MD5(`content`),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  AND `content` LIKE '%      - delivery_area%'
  AND `content` NOT LIKE '%      - delivery_account_binding%';

SET FOREIGN_KEY_CHECKS = 1;
