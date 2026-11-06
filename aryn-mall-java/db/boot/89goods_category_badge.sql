-- 类目角标（Boot 单体模式）—— 历史脚本（角标已升级为配置化）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 特性：可重复执行；不 DROP/TRUNCATE；不覆盖已有业务数据；仅加列与回填角标。
--
-- 背景（2026-09-24 · 分类页对标小象超市）：
--   参考图左栏二级类目在名称前有一个小方块图标 ——「荐」（时令推荐）、
--   「热」（热卖）。这类角标是**类目**的运营属性，与商品标签无关，
--   现有 goods_category 没有承载它的字段。
--
-- 现状（2026-11-05 需求「类目角标配置化」，主入口已迁移到 205/206 号脚本）：
--   goods_category.badge_type 由「写死枚举」改为「goods_category_badge.badge_key 引用」，
--   文字/底色/字色/排序/启停由运营在管理后台配置：
--     · 0 = 无角标（默认）；
--     · 1 / 2 = 试点租户历史键（配置行由 205goods_category_badge_config.sql seed）；
--     · 其它值 = 运营新建角标的雪花 id 键。
--   本脚本仅保留「加列 + 归一 + 演示回填」能力；新库直接执行 205/206 即可。
--   ⚠️ 归一白名单已按需求修正：原 `NOT IN ('0','1','2')` 会把运营新建的角标 key
--   全部刷回 '0'，现只清理 NULL / 空串。
--
-- ⚠️ 部署顺序：**先执行本脚本（或 205goods_category_badge_config.sql），再部署后端代码**。
--    GoodsCategory 实体已加 badgeType 字段，而 MyBatis-Plus 走显式列清单，
--    列不存在时所有类目查询都会 Unknown column 报错（含 C 端类目树）。
--
-- 执行：mysql -u root -p aryn_boot < 89goods_category_badge.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------- 1. 加列（幂等：列已存在则跳过；宽度按配置化需求给到 varchar(32)） ----------
SET @badge_column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'goods_category' AND column_name = 'badge_type'
);
SET @badge_column_sql = IF(
  @badge_column_exists = 0,
  'ALTER TABLE goods_category ADD COLUMN badge_type varchar(32) NOT NULL DEFAULT ''0'' COMMENT ''类目角标：0.无；其它值为 goods_category_badge.badge_key'' AFTER sort',
  'SELECT 1'
);
PREPARE badge_column_stmt FROM @badge_column_sql;
EXECUTE badge_column_stmt;
DEALLOCATE PREPARE badge_column_stmt;

-- ---------- 2. 存量数据归一（只清理 NULL/空串，不动运营新建的角标 key） ----------
UPDATE `goods_category`
   SET `badge_type` = '0'
 WHERE `badge_type` IS NULL OR `badge_type` = '';

-- ---------- 3. 演示角标回填（商超 951/952 段，与 41 号种子同一批类目） ----------
-- 只回填 3 个存量类目作为可运营的示例，其余保持「无角标」：
--   根茎类 → 推荐（时令推荐位）、坚果炒货 → 热卖、食用菌菇 → 热卖。
-- '1'/'2' 是试点租户的历史角标键，对应 205goods_category_badge_config.sql seed 的配置行。
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
