-- ============================================================================
-- 版权配置 — 数据模型（Cloud 微服务模式）
--
-- 目标库：aryn_upms
-- 背景（2026-11-10 需求「版权配置可配置化」）：
--   三端版权/品牌/备案信息此前全部写死在前端：管理端 packages/preferences 的
--   copyright（公司名/官网/年份写死 2025）、登录页语言包 copyrightName、C 端登录页
--   内联 copyright ref 与 locales 的「天启雨数版权所有」。改一次版权要发版，且
--   ICP 备案号无处可填。本次收敛为租户级配置表，一行一端：
--     app      用户端（C 端商城小程序/H5 登录页）
--     merchant 商户端（管理后台登录后页脚）
--     platform 平台端（管理后台登录页，登录前无会话，固定取平台租户配置）
--   名称与 Logo 留空表示跟随租户品牌（sys_tenant.name/logoUrl），避免顶栏与页脚
--   出现两套品牌；版权信息/链接/ICP 是本次新增的独立信息。
--
-- 权限口径：app 由各租户自己维护；merchant/platform 属平台级配置，仅平台管理员
--   可读写（Controller 侧 PlatformTenantGuard 校验，菜单侧拆两个页面权限）。
--
-- 特性：可重复执行；CREATE TABLE IF NOT EXISTS，不 DROP/TRUNCATE 存量数据。
-- 与 boot 版一致性：本脚本与 db/boot/219copyright_config_incremental.sql 内容一致，仅目标库不同。
-- 配套：菜单与种子见 db/cloud/230copyright_config_menu.sql；
--       租户表白名单见 db/cloud/231copyright_config_nacos.sql（aryn-upms-biz-dev.yml）。
-- 执行：mysql -u root -p aryn_upms < 229copyright_config_incremental.sql
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、sys_copyright_config 版权配置表（租户级，一行一端）
--
-- 唯一键 (tenant_id, client_type)：同一租户同一端只有一条配置，保存走 upsert。
-- 本表不做逻辑删除入口（配置只改不删），del_flag 仅为与全库口径一致而保留。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_copyright_config` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `client_type` varchar(16) NOT NULL COMMENT '端类型：app 用户端；merchant 商户端；platform 平台端',
  `logo_url` varchar(500) DEFAULT NULL COMMENT 'Logo（留空跟随租户品牌 sys_tenant.logo_url）',
  `name` varchar(30) DEFAULT NULL COMMENT '名称（留空跟随租户品牌 sys_tenant.name）',
  `copyright_text` varchar(100) DEFAULT NULL COMMENT '版权信息',
  `copyright_link` varchar(500) DEFAULT NULL COMMENT '版权链接（http/https 或站内相对路径）',
  `icp` varchar(100) DEFAULT NULL COMMENT 'ICP备案号',
  `icp_link` varchar(500) DEFAULT NULL COMMENT 'ICP网站链接（http/https 或站内相对路径）',
  `login_title` varchar(60) DEFAULT NULL COMMENT '登录页标题（仅平台端）',
  `login_subtitle` varchar(128) DEFAULT NULL COMMENT '登录页副标题（仅平台端）',
  `login_icon` varchar(500) DEFAULT NULL COMMENT '登录页图标（仅平台端）',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.正常；1.删除',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户 ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_copyright_tenant_client` (`tenant_id`, `client_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '版权配置' ROW_FORMAT = DYNAMIC;

-- 自检：表存在且唯一键就位（期望 table_rows=1、unique_key_rows=1）
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_copyright_config') AS table_rows,
  (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_copyright_config'
      AND index_name = 'uk_copyright_tenant_client') AS unique_key_rows;

SET FOREIGN_KEY_CHECKS = 1;