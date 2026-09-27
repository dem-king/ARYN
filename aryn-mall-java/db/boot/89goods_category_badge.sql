-- 类目角标（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 特性：可重复执行；不 DROP/TRUNCATE；不覆盖已有业务数据；仅加列与回填角标。
--
-- 背景（2026-09-24 · 分类页对标小象超市）：
--   参考图左栏二级类目在名称前有一个小方块图标 ——「荐」（时令推荐）、
--   「热」（热卖）。这类角标是**类目**的运营属性，与商品标签无关，
--   现有 goods_category 没有承载它的字段。
--
-- 语义约定（务必与代码注释保持一致）：
--   badge_type  类目角标。0 = 无（默认）；1 = 推荐；2 = 热卖。
--               只存枚举值、不存图片地址：样式由 C 端内置，
--               运营只能选语义，避免同一语义在不同租户下配色漂移。
--               存量数据与新建类目一律默认 '0'，即不渲染角标。
--
-- ⚠️ 部署顺序：**先执行本脚本，再部署后端代码**。
--    GoodsCategory 实体已加 badgeType 字段，而 MyBatis-Plus 走显式列清单，
--    列不存在时所有类目查询都会 Unknown column 报错（含 C 端类目树）。
--
-- 执行：mysql -u root -p aryn_boot < 89goods_category_badge.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------- 1. 加列（幂等：列已存在则跳过） ----------
SET @badge_column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'goods_category' AND column_name = 'badge_type'
);
SET @badge_column_sql = IF(
  @badge_column_exists = 0,
  'ALTER TABLE goods_category ADD COLUMN badge_type char(2) NOT NULL DEFAULT ''0'' COMMENT ''类目角标：0.无 1.推荐 2.热卖'' AFTER sort',
  'SELECT 1'
);
PREPARE badge_column_stmt FROM @badge_column_sql;
EXECUTE badge_column_stmt;
DEALLOCATE PREPARE badge_column_stmt;

-- ---------- 2. 存量数据归一（NULL/非法值一律落 '0'，保证 C 端判定简单） ----------
UPDATE `goods_category`
   SET `badge_type` = '0'
 WHERE `badge_type` IS NULL OR `badge_type` NOT IN ('0', '1', '2');

-- ---------- 3. 演示角标回填（商超 951/952 段，与 41 号种子同一批类目） ----------
-- 只回填 3 个存量类目作为可运营的示例，其余保持「无角标」：
--   根茎类 → 推荐（时令推荐位）、坚果炒货 → 热卖、食用菌菇 → 热卖。
-- 脚本可重复执行，不会覆盖运营在后台自行调整过的角标。
UPDATE `goods_category`
   SET `badge_type` = '1', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000002' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0' AND `badge_type` = '0';
UPDATE `goods_category`
   SET `badge_type` = '2', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000033' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0' AND `badge_type` = '0';
UPDATE `goods_category`
   SET `badge_type` = '2', `update_time` = NOW(), `update_by` = 'system'
 WHERE `id` = '9520000000000000005' AND `tenant_id` = '1590229800633634816' AND `del_flag` = '0' AND `badge_type` = '0';
