-- 商城装修定时/灰度发布与指标增量迁移（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_boot < 33page_design_phase4.sql

USE `aryn_boot`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 灰度发布版本指针
-- 说明：幂等守卫兼容已有列的存量库；information_schema 判断在 MySQL 8 可重复执行。
SET @gray_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'page_design'
    AND COLUMN_NAME = 'gray_version_id'
);
SET @gray_ddl = IF(
  @gray_column_exists = 0,
  'ALTER TABLE `page_design` ADD COLUMN `gray_version_id` varchar(32) NULL COMMENT ''灰度发布版本ID'' AFTER `published_version_id`',
  'SELECT 1'
);
PREPARE gray_stmt FROM @gray_ddl;
EXECUTE gray_stmt;
DEALLOCATE PREPARE gray_stmt;

-- 行业模板标签
SET @industry_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'page_design_template'
    AND COLUMN_NAME = 'industry_tag'
);
SET @industry_ddl = IF(
  @industry_column_exists = 0,
  'ALTER TABLE `page_design_template` ADD COLUMN `industry_tag` varchar(32) NULL COMMENT ''行业标签（行业模板筛选用，通用为空）'' AFTER `system_flag`',
  'SELECT 1'
);
PREPARE industry_stmt FROM @industry_ddl;
EXECUTE industry_stmt;
DEALLOCATE PREPARE industry_stmt;

-- 灰度发布目标表
CREATE TABLE IF NOT EXISTS `page_design_release_target` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `release_id` varchar(32) NOT NULL COMMENT '发布申请ID（page_design_release.id）',
  `page_design_id` varchar(32) NOT NULL COMMENT '页面ID（page_design.id）',
  `target_tenant_id` varchar(32) NOT NULL COMMENT '灰度目标租户ID',
  `terminal` varchar(16) NOT NULL DEFAULT 'all' COMMENT '灰度目标终端：all/h5/weapp',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `create_time` datetime NULL COMMENT '创建时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID（申请归属租户）',
  PRIMARY KEY (`id`),
  KEY `idx_page_design_target_release` (`release_id`, `target_tenant_id`),
  KEY `idx_page_design_target_page` (`page_design_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面装修灰度发布目标表';

-- 页面装修每日聚合指标表
-- 说明：本表不纳入租户拦截器白名单（原生 upsert 与拦截器改写冲突），
-- 租户维度由服务端显式写入并在查询时显式过滤。
CREATE TABLE IF NOT EXISTS `page_design_metric_daily` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `page_design_id` varchar(32) NOT NULL COMMENT '页面ID',
  `version_id` varchar(32) NOT NULL DEFAULT '-' COMMENT '发布版本ID，未知为 -',
  `metric_date` date NOT NULL COMMENT '指标日期',
  `component_type` varchar(32) NOT NULL DEFAULT '-' COMMENT '组件类型，页面级为 -',
  `view_count` bigint NOT NULL DEFAULT 0 COMMENT '页面访问数',
  `click_count` bigint NOT NULL DEFAULT 0 COMMENT '组件点击数',
  `error_count` bigint NOT NULL DEFAULT 0 COMMENT '渲染错误数',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `create_time` datetime NULL COMMENT '创建时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_page_design_metric_daily` (`tenant_id`, `page_design_id`, `version_id`, `metric_date`, `component_type`),
  KEY `idx_page_design_metric_page` (`page_design_id`, `metric_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面装修每日聚合指标表';

-- 指标查看按钮权限（IGNORE 幂等）
INSERT IGNORE INTO `sys_menu`
  (`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
   `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
  ('2026090609000000001', '装修数据看板', 'promotion:pagedesign:metrics', NULL, NULL,
   '1600477837933047810', NULL, NULL, 8, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL);

-- 临时表：收集本次新增的菜单 ID，用于批量授权
CREATE TEMPORARY TABLE tmp_menu_page_design_metrics (
  menu_id varchar(32) NOT NULL,
  PRIMARY KEY (menu_id)
) ENGINE = MEMORY DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

INSERT INTO tmp_menu_page_design_metrics (menu_id)
VALUES ('2026090609000000001');

-- 为所有租户的 ROLE_ADMIN 角色授权
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT REPLACE(UUID(), '-', ''), role.id, seed.menu_id, NOW(), role.tenant_id
FROM sys_role AS role
CROSS JOIN tmp_menu_page_design_metrics AS seed
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
CROSS JOIN tmp_menu_page_design_metrics AS seed
WHERE tenant.del_flag = '0'
  AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_tenant_menu AS tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id
      AND tenant_menu.menu_id = seed.menu_id
  );

DROP TEMPORARY TABLE tmp_menu_page_design_metrics;

SET FOREIGN_KEY_CHECKS = 1;
