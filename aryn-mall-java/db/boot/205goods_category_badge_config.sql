-- ============================================================================
-- 类目角标配置化 — 数据模型与存量兼容（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 背景（2026-11-05 需求「类目角标配置化」，见
--       docs/50-需求文档/2026-11-05-类目角标配置化/IMPLEMENTATION_PLAN.md 第 1、5 节）：
--   历史上 goods_category.badge_type 是写死枚举（0.无 1.推荐 2.热卖），「荐/热」的文字与
--   绿/红配色写死在 C 端代码里；现在改为租户级配置表 goods_category_badge：运营在
--   管理后台自由配置文字、底色、字色、排序与启停，goods_category.badge_type 改为存
--   配置的稳定键 badge_key，新增角标不再需要改代码发版。
--
-- 特性：可重复执行；不 DROP / TRUNCATE 存量；只建表、改宽列、补 seed，
--       不覆盖运营在后台调整过的角标文字/颜色/状态。
-- 幂等：
--   1) 新表 CREATE TABLE IF NOT EXISTS；
--   2) 列宽按 information_schema 判定：列不存在则按新定义 ADD，列存在但未达
--      varchar(32) 才 MODIFY（已是目标结构时重跑为空操作，避免无谓的锁表重建）；
--   3) seed 依赖唯一键 (tenant_id, badge_key)，INSERT IGNORE 保证反复执行不重复、不覆盖。
--
-- 与 cloud 版一致性：本脚本与 db/cloud/210goods_category_badge_config.sql 内容一致，仅目标库不同。
-- 配套：菜单/授权见 db/boot/206goods_category_badge_menu.sql；
--       旧脚本 db/boot/89goods_category_badge.sql 的「存量归一」白名单已同步修正
--       （否则重跑旧脚本会把运营新建的角标 key 全部刷回 '0'）。
-- 执行：mysql -u root -p aryn_boot < 205goods_category_badge_config.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、goods_category_badge 类目角标配置表（租户级）
--
-- 唯一键 (tenant_id, badge_key)：同一租户内一个稳定键只对应一条配置。
-- 逻辑删除后 badge_key 仍占用唯一键，但新建角标的 key 由后端用雪花 id 生成、
-- 不会复用，因此删除场景不需要做 key 回收。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `goods_category_badge` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `badge_key` varchar(32) NOT NULL COMMENT '角标稳定键（goods_category.badge_type 存此值，创建后不可修改）',
  `badge_text` varchar(8) NOT NULL COMMENT '角标文字（1-2 个字符）',
  `bg_color` varchar(16) NOT NULL DEFAULT '#00B578' COMMENT '底色（#RRGGBB）',
  `text_color` varchar(16) NOT NULL DEFAULT '#FFFFFF' COMMENT '字色（#RRGGBB）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序序号（升序）',
  `status` char(2) NOT NULL DEFAULT '0' COMMENT '状态：0.启用；1.停用',
  `del_flag` char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.正常；1.删除',
  `tenant_id` varchar(32) NOT NULL COMMENT '租户 ID',
  `create_by` varchar(60) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(60) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_badge_tenant_key` (`tenant_id`, `badge_key`) USING BTREE,
  KEY `idx_badge_tenant_sort` (`tenant_id`, `sort`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '类目角标配置' ROW_FORMAT = DYNAMIC;

-- ============================================================================
-- 二、goods_category.badge_type 改宽（char(2) → varchar(32)，幂等）
--
-- 学科说明：badge_type 从「枚举值」变成「badge_key 引用」，宽度必须覆盖雪花 id（19 位），
-- 原 char(2) 装不下运营新建的角标 key。
-- ============================================================================
SET @badge_type_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'goods_category' AND column_name = 'badge_type'
);
SET @badge_type_needs_widen = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'goods_category' AND column_name = 'badge_type'
    AND (data_type <> 'varchar' OR character_maximum_length IS NULL OR character_maximum_length < 32)
);
SET @badge_type_sql = CASE
  WHEN @badge_type_exists = 0 THEN
    'ALTER TABLE `goods_category` ADD COLUMN `badge_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''0'' COMMENT ''类目角标：0.无；其它值为 goods_category_badge.badge_key'' AFTER `sort`'
  WHEN @badge_type_needs_widen > 0 THEN
    'ALTER TABLE `goods_category` MODIFY COLUMN `badge_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''0'' COMMENT ''类目角标：0.无；其它值为 goods_category_badge.badge_key'''
  ELSE
    'SELECT 1'
END;
PREPARE badge_type_stmt FROM @badge_type_sql;
EXECUTE badge_type_stmt;
DEALLOCATE PREPARE badge_type_stmt;

-- ============================================================================
-- 三、存量 seed：历史 badge_type='1'/'2' 的引用不能悬空
-- ============================================================================

-- 3.1 试点租户固定两行：id/badge_key 用 '1'/'2' 与存量类目引用完全兼容，
--     样式沿用现网「荐」绿、「热」红。INSERT IGNORE：运营改过的文字/颜色不会被重跑覆盖。
INSERT IGNORE INTO `goods_category_badge`
(`id`,`badge_key`,`badge_text`,`bg_color`,`text_color`,`sort`,`status`,`del_flag`,`tenant_id`,`create_by`,`create_time`)
VALUES
('1','1','荐','#00B578','#FFFFFF',1,'0','0','1590229800633634816','system',NOW()),
('2','2','热','#FF4D2D','#FFFFFF',2,'0','0','1590229800633634816','system',NOW());

-- 3.2 其它租户若也存在 badge_type IN ('1','2') 的存量引用，按 (tenant_id, badge_type) 去重补配置行，
--     保证引用不悬空。id 用 UUID（去掉 '-'）而不是雪花 id：seed 不是业务新增，
--     运营后续新建的角标仍由后端生成雪花 id，两者不混用。
INSERT IGNORE INTO `goods_category_badge`
(`id`,`badge_key`,`badge_text`,`bg_color`,`text_color`,`sort`,`status`,`del_flag`,`tenant_id`,`create_by`,`create_time`)
SELECT REPLACE(UUID(), '-', ''), legacy.`badge_type`,
       CASE legacy.`badge_type` WHEN '1' THEN '荐' ELSE '热' END,
       CASE legacy.`badge_type` WHEN '1' THEN '#00B578' ELSE '#FF4D2D' END,
       '#FFFFFF',
       CASE legacy.`badge_type` WHEN '1' THEN 1 ELSE 2 END,
       '0', '0', legacy.`tenant_id`, 'system', NOW()
FROM (
  SELECT DISTINCT `tenant_id`, `badge_type`
  FROM `goods_category`
  WHERE `badge_type` IN ('1', '2') AND `tenant_id` <> '1590229800633634816'
) legacy;

-- ============================================================================
-- 四、自检：表/列/试点 seed 就绪，且不存在悬空角标引用
--   期望：badge_table_ready=1、badge_type_column_ready=1、pilot_badge_rows=2、
--         dangling_badge_refs=0（非 0 说明仍有类目引用未 seed 的历史 key，需排查）
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'goods_category_badge') AS badge_table_ready,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'goods_category'
      AND column_name = 'badge_type') AS badge_type_column_ready,
  (SELECT COUNT(*) FROM `goods_category_badge`
    WHERE `tenant_id` = '1590229800633634816' AND `badge_key` IN ('1', '2')
      AND `del_flag` = '0') AS pilot_badge_rows,
  (SELECT COUNT(*) FROM `goods_category` c
    LEFT JOIN `goods_category_badge` b
      ON b.`tenant_id` = c.`tenant_id` AND b.`badge_key` = c.`badge_type` AND b.`del_flag` = '0'
    WHERE c.`del_flag` = '0' AND c.`badge_type` IS NOT NULL AND c.`badge_type` <> '0'
      AND b.`id` IS NULL) AS dangling_badge_refs;

SET FOREIGN_KEY_CHECKS = 1;
