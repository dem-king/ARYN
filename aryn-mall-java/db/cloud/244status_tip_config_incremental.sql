-- ============================================================================
-- 空态插画配置 — 数据模型（Cloud 微服务模式）
--
-- 目标库：aryn_upms
-- 背景（2026-11-13）：
--   wot-design-uni 的 wd-status-tip 默认图源 registry.npmmirror.com 已被 npmmirror
--   的 unpkg 白名单拒绝（HTTP 451），C 端空态/失败态全部裂图。7 张预设图先随
--   aryn-upms 静态资源发布（/status-tip/{search|network|content|collect|comment|halo|message}.png），
--   购物车/订单/秒杀 3 张会场插画仍随小程序包内置（/static/promotion/empty/*.png）。
--   本表提供租户级覆盖：运营在管理后台按图片位上传自己的图，未配置的位回落内置图。
--
--   与商品图同一套素材上传链路，存的是完整 URL（本地存储/MinIO/OSS 均可）。
--
-- 特性：可重复执行；CREATE TABLE IF NOT EXISTS，不 DROP/TRUNCATE 存量数据。
-- 与 boot 版一致性：本脚本与 db/boot/230status_tip_config_incremental.sql 内容一致，仅目标库不同。
-- 配套：菜单与权限见 db/cloud/245status_tip_config_menu.sql；
--       租户表白名单见 db/cloud/246status_tip_config_nacos.sql（Nacos aryn-upms-biz 配置）。
-- 执行：mysql -u root -p aryn_upms < 244status_tip_config_incremental.sql
-- ============================================================================

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、sys_status_tip_config 空态插画配置表（租户级，一行一租户）
--
-- 唯一键 tenant_id：同一租户只有一条配置，保存走 upsert。
-- 10 个 URL 列（7 个 wd-status-tip 预设位 + 3 张会场空态插画）可单独留空，
-- 留空即回落内置图；本表不做逻辑删除入口（配置只改不删），del_flag 仅为与全库口径一致而保留。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_status_tip_config` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `search_url` varchar(500) DEFAULT NULL COMMENT '搜索无结果图 URL',
  `network_url` varchar(500) DEFAULT NULL COMMENT '网络异常图 URL',
  `content_url` varchar(500) DEFAULT NULL COMMENT '内容为空图 URL',
  `collect_url` varchar(500) DEFAULT NULL COMMENT '收藏为空图 URL',
  `comment_url` varchar(500) DEFAULT NULL COMMENT '评论为空图 URL',
  `halo_url` varchar(500) DEFAULT NULL COMMENT '券/资产为空图 URL',
  `message_url` varchar(500) DEFAULT NULL COMMENT '消息为空图 URL',
  `empty_cart_url` varchar(500) DEFAULT NULL COMMENT '购物车空态图 URL',
  `empty_order_url` varchar(500) DEFAULT NULL COMMENT '订单列表空态图 URL',
  `empty_seckill_url` varchar(500) DEFAULT NULL COMMENT '秒杀会场空态图 URL',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.正常；1.删除',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户 ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_status_tip_tenant` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '空态插画配置' ROW_FORMAT = DYNAMIC;

-- 自检：表存在、唯一键就位，且 10 个图片位列齐全（期望 table_rows=1、unique_key_rows=1、url_columns=10）
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_status_tip_config') AS table_rows,
  (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_status_tip_config'
      AND index_name = 'uk_status_tip_tenant') AS unique_key_rows,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_status_tip_config'
      AND column_name IN ('search_url', 'network_url', 'content_url', 'collect_url',
        'comment_url', 'halo_url', 'message_url', 'empty_cart_url',
        'empty_order_url', 'empty_seckill_url')) AS url_columns;

SET FOREIGN_KEY_CHECKS = 1;
