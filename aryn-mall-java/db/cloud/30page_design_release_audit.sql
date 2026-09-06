-- 商城装修发布治理与审计增量迁移（Cloud 微服务模式：表进 aryn_promotion，菜单进 aryn_upms）
-- 目标库：aryn_promotion / aryn_upms
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有业务数据。
-- 执行：mysql -u root -p aryn_promotion <；菜单段自动切换至 aryn_upms 30page_design_release_audit.sql

USE `aryn_promotion`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 页面装修发布申请表
-- 说明：发布流程为 草稿保存 -> 服务端校验 -> 创建 release ->（可配置跳过）审批
-- -> 基于快照生成不可变版本 -> 更新线上指针 -> 记录审计。
-- 申请内容为提交时的页面快照，审批通过后按快照发布，保证审阅内容与上线内容一致。
CREATE TABLE IF NOT EXISTS `page_design_release` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `page_design_id` varchar(32) NOT NULL COMMENT '页面ID（page_design.id）',
  `release_no` int NOT NULL COMMENT '页面内申请序号，从1递增',
  `release_strategy` char(2) NOT NULL DEFAULT '0' COMMENT '发布策略：0.立即；1.定时（预留）；2.灰度（预留）；',
  `release_status` char(2) NOT NULL DEFAULT '0' COMMENT '申请状态：0.待审核；1.已发布；2.已拒绝；3.已取消；',
  `draft_revision` bigint NOT NULL COMMENT '申请时草稿修订号',
  `schema_version` int NOT NULL DEFAULT 2 COMMENT '装修协议版本',
  `page_name` varchar(50) NOT NULL COMMENT '申请时页面名称',
  `page_content` longtext NOT NULL COMMENT '申请内容快照',
  `publish_remark` varchar(255) NULL COMMENT '发布备注',
  `audit_remark` varchar(255) NULL COMMENT '审批意见',
  `submit_by` varchar(60) NULL COMMENT '提交人',
  `submit_at` datetime NULL COMMENT '提交时间',
  `audit_by` varchar(60) NULL COMMENT '审批人',
  `audit_at` datetime NULL COMMENT '审批时间',
  `release_version_id` varchar(32) NULL COMMENT '审批通过后生成的发布版本ID（page_design_version.id）',
  `fail_reason` varchar(255) NULL COMMENT '失败原因（预留）',
  `plan_publish_at` datetime NULL COMMENT '计划发布时间（定时策略预留）',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `update_by` varchar(60) NULL COMMENT '修改人',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_time` datetime NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_page_design_release_no` (`tenant_id`, `page_design_id`, `release_no`),
  KEY `idx_page_design_release_status` (`page_design_id`, `release_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面装修发布申请表';

-- 页面装修审计日志表（追加写，不提供业务更新）
-- 说明：记录草稿保存、发布、下线、回滚与发布申请全流程，
-- 保证任何线上版本可回溯到租户、操作者、release 与前后版本。
CREATE TABLE IF NOT EXISTS `page_design_audit_log` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `page_design_id` varchar(32) NOT NULL COMMENT '页面ID（page_design.id）',
  `release_id` varchar(32) NULL COMMENT '关联发布申请ID（page_design_release.id）',
  `action` varchar(32) NOT NULL COMMENT '操作类型：SAVE_DRAFT/PUBLISH/UNPUBLISH/ROLLBACK/RELEASE_SUBMIT/RELEASE_APPROVE/RELEASE_REJECT/RELEASE_CANCEL',
  `operator` varchar(60) NULL COMMENT '操作人',
  `operator_ip` varchar(64) NULL COMMENT '操作者IP',
  `before_version_id` varchar(32) NULL COMMENT '操作前发布版本ID',
  `after_version_id` varchar(32) NULL COMMENT '操作后发布版本ID',
  `before_revision` bigint NULL COMMENT '操作前草稿修订号',
  `after_revision` bigint NULL COMMENT '操作后草稿修订号',
  `result` char(2) NOT NULL DEFAULT '0' COMMENT '操作结果：0.成功；1.失败；',
  `remark` varchar(255) NULL COMMENT '备注',
  `create_by` varchar(60) NULL COMMENT '创建人',
  `create_time` datetime NULL COMMENT '创建时间',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_page_design_audit_page` (`tenant_id`, `page_design_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='页面装修审计日志表';

-- 菜单库切换：cloud 模式菜单权限位于 aryn_upms
USE `aryn_upms`;

-- 装修发布治理按钮权限（IGNORE 幂等）
INSERT IGNORE INTO `sys_menu`
  (`id`, `name`, `permission`, `path`, `redirect`, `parent_id`, `icon`, `component`, `sort`, `type`,
   `create_time`, `update_time`, `outer_status`, `del_flag`, `application_key`, `create_by`, `update_by`)
VALUES
  ('2026090509000000001', '提交发布申请', 'promotion:pagedesign:submit', NULL, NULL,
   '1600477837933047810', NULL, NULL, 4, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2026090509000000002', '发布审批', 'promotion:pagedesign:approve', NULL, NULL,
   '1600477837933047810', NULL, NULL, 5, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL),
  ('2026090509000000003', '装修审计日志', 'promotion:pagedesign:audit', NULL, NULL,
   '1600477837933047810', NULL, NULL, 6, '1', NOW(), NULL, '0', '0', 'app_base', 'system', NULL);

-- 临时表：收集本次新增的菜单 ID，用于批量授权
CREATE TEMPORARY TABLE tmp_menu_page_design_governance (
  menu_id varchar(32) NOT NULL,
  PRIMARY KEY (menu_id)
) ENGINE = MEMORY DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

INSERT INTO tmp_menu_page_design_governance (menu_id)
VALUES
  ('2026090509000000001'),
  ('2026090509000000002'),
  ('2026090509000000003');

-- 为所有租户的 ROLE_ADMIN 角色授权
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `tenant_id`)
SELECT REPLACE(UUID(), '-', ''), role.id, seed.menu_id, NOW(), role.tenant_id
FROM sys_role AS role
CROSS JOIN tmp_menu_page_design_governance AS seed
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
CROSS JOIN tmp_menu_page_design_governance AS seed
WHERE tenant.del_flag = '0'
  AND tenant.id <> '1881232176465358849'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_tenant_menu AS tenant_menu
    WHERE tenant_menu.tenant_id = tenant.id
      AND tenant_menu.menu_id = seed.menu_id
  );

DROP TEMPORARY TABLE tmp_menu_page_design_governance;

SET FOREIGN_KEY_CHECKS = 1;
