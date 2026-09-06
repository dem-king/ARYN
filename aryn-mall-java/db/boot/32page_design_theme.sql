-- 商城装修主题增量迁移（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 32page_design_theme.sql

USE `aryn_boot`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 页面装修主题表
-- 说明：草稿经 v3 文档 themeRef 引用主题；发布时服务端把主题内容固化为
-- themeSnapshot 写入发布快照，主题后续修改不影响已发布历史版本。
CREATE TABLE IF NOT EXISTS `page_design_theme` (
  `id` varchar(32) NOT NULL COMMENT '主键（同时作为 v3 文档 themeRef 令牌）',
  `theme_name` varchar(100) NOT NULL COMMENT '主题名称',
  `primary_color` varchar(16) NULL COMMENT '品牌主色',
  `page_background_color` varchar(16) NULL COMMENT '页面背景色',
  `navigation_color` varchar(16) NULL COMMENT '导航栏背景色',
  `navigation_text_color` varchar(16) NULL COMMENT '导航栏文字色',
  `radius` int NULL DEFAULT 8 COMMENT '全局圆角（px）',
  `system_flag` char(2) NOT NULL DEFAULT '0' COMMENT '系统主题：0.否；1.是；',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `update_by` varchar(60) NULL COMMENT '修改人',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_time` datetime NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_page_design_theme_tenant` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面装修主题表';

-- 主题管理按钮权限（IGNORE 幂等）
INSERT IGNORE INTO `sys_menu`
  (`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
   `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
  ('2026090509000000004', '装修主题管理', 'promotion:pagedesign:theme', NULL, NULL,
   '1600477837933047810', NULL, NULL, 7, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL);

-- 临时表：收集本次新增的菜单 ID，用于批量授权
CREATE TEMPORARY TABLE tmp_menu_page_design_theme (
  menu_id varchar(32) NOT NULL,
  PRIMARY KEY (menu_id)
) ENGINE = MEMORY DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

INSERT INTO tmp_menu_page_design_theme (menu_id)
VALUES ('2026090509000000004');

-- 为所有租户的 ROLE_ADMIN 角色授权
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT REPLACE(UUID(), '-', ''), role.id, seed.menu_id, NOW(), role.tenant_id
FROM sys_role AS role
CROSS JOIN tmp_menu_page_design_theme AS seed
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
INSERT INTO `sys_tenant_menu` (`id`, `tenant_id`, `menu_id`, `create_time`, `create_by`)
SELECT REPLACE(UUID(), '-', ''), tenant.id, seed.menu_id, NOW(), 'system'
FROM sys_tenant AS tenant
CROSS JOIN tmp_menu_page_design_theme AS seed
WHERE tenant.del_flag = '0'
  AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_tenant_menu AS tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id
      AND tenant_menu.menu_id = seed.menu_id
  );

DROP TEMPORARY TABLE tmp_menu_page_design_theme;

SET FOREIGN_KEY_CHECKS = 1;
