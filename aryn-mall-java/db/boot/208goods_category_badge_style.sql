-- ============================================================================
-- 类目角标样式扩展 — 图片角标 + 可调高度/圆角（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-05 需求「类目角标配置化」追加需求，见
--       docs/50-需求文档/2026-11-05-类目角标配置化/IMPLEMENTATION_PLAN.md 追加需求章节）：
--   角标原本只支持「文字 1–2 字 + 底色/字色」，运营希望进一步支持图片角标与
--   自定义高度/圆角。本脚本给 goods_category_badge 增加三列：
--     icon_url      varchar(512) NOT NULL DEFAULT ''  —— 图片地址，空串=文字角标
--     badge_height  int NOT NULL DEFAULT 28           —— 高度 rpx（允许 16–96）
--     border_radius int NOT NULL DEFAULT 4            —— 圆角 rpx（允许 0–48）
--   C 端按 height 正方形渲染图片、按 borderRadius 裁圆角；文字角标字号/左右内边距
--   由 height 推导（fontSize=round(height*0.68)、paddingX=round(height*0.18)），
--   旧数据落到默认值后观感与改动前完全一致（28rpx 高、19rpx 字号、4rpx 圆角）。
--
-- 特性：可重复执行；不 DROP / TRUNCATE 存量；列已存在时跳过（按 information_schema 判定）。
--
-- 与 cloud 版一致性：本脚本与 db/cloud/213goods_category_badge_style.sql 语义一致，仅目标库不同。
-- 执行：mysql -u root -p aryn_boot < 208goods_category_badge_style.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. icon_url：图片角标地址（空串=文字角标） ----------
SET @icon_url_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'goods_category_badge' AND column_name = 'icon_url'
);
SET @icon_url_sql = CASE
  WHEN @icon_url_exists = 0 THEN
    'ALTER TABLE `goods_category_badge` ADD COLUMN `icon_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '''' COMMENT ''角标图片地址（空串=文字角标；配置后 C 端优先渲染图片）'' AFTER `text_color`'
  ELSE 'SELECT 1'
END;
PREPARE icon_url_stmt FROM @icon_url_sql;
EXECUTE icon_url_stmt;
DEALLOCATE PREPARE icon_url_stmt;

-- ---------- 2. badge_height：角标高度（rpx，默认 28） ----------
SET @badge_height_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'goods_category_badge' AND column_name = 'badge_height'
);
SET @badge_height_sql = CASE
  WHEN @badge_height_exists = 0 THEN
    'ALTER TABLE `goods_category_badge` ADD COLUMN `badge_height` int NOT NULL DEFAULT 28 COMMENT ''角标高度（rpx，16-96；文字角标字号/内边距由它推导）'' AFTER `icon_url`'
  ELSE 'SELECT 1'
END;
PREPARE badge_height_stmt FROM @badge_height_sql;
EXECUTE badge_height_stmt;
DEALLOCATE PREPARE badge_height_stmt;

-- ---------- 3. border_radius：角标圆角（rpx，默认 4） ----------
SET @border_radius_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'goods_category_badge' AND column_name = 'border_radius'
);
SET @border_radius_sql = CASE
  WHEN @border_radius_exists = 0 THEN
    'ALTER TABLE `goods_category_badge` ADD COLUMN `border_radius` int NOT NULL DEFAULT 4 COMMENT ''角标圆角（rpx，0-48）'' AFTER `badge_height`'
  ELSE 'SELECT 1'
END;
PREPARE border_radius_stmt FROM @border_radius_sql;
EXECUTE border_radius_stmt;
DEALLOCATE PREPARE border_radius_stmt;

-- ---------- 4. 自检：三列就绪（期望 badge_style_columns_ready=3） ----------
SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'goods_category_badge'
      AND column_name IN ('icon_url', 'badge_height', 'border_radius')) AS badge_style_columns_ready,
  (SELECT COUNT(*) FROM `goods_category_badge`) AS badge_rows;

SET FOREIGN_KEY_CHECKS = 1;
