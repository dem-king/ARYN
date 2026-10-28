-- 首页金刚区图标放大（Boot 单体模式）
-- 目标库：aryn_boot
--
-- 背景（2026-10-27）：
--   首页金刚区（tab-nav 图文导航，5 列 × 3 行分页）图标为 44px，实机观感偏小，
--   与分类图标的展示权重不匹配。本次把图标放大到 56px，并把原来的 22px 圆角
--   同步到 28px（保持正圆），条目、链接、列数、分页行数与其它楼层一律不动。
--
-- 组件侧同步改动（本脚本只负责存量页面数据）：
--   · 移动端 diy-tabnav：文字 13px、图文间距 6px、行间距 8px，高度公式同步；
--   · 管理端装修画布 tab-nav 与组件默认值同步，新增组件默认 imgSize = 56。
--
-- 特性：幂等；只处理首页（page_type='1'）的 tab-nav：
--   · imgSize 只把小于 56 的提升到 56——运营手动调得更大的页面不会被压回；
--   · imgRadius 只在「图标已是 56 且圆角仍是 22」时改成 28，不动其它圆角。
--
--   ⚠️ 客户端读取的是**已发布版本快照**（page_design.published_version_id →
--   page_design_version），不是草稿，因此草稿与当前已发布版本都要改。
--
--   ⚠️ JSON_SEARCH 返回的是**指向 `.type` 值**的路径，例如
--     "$.sections[1].components[0].type"
--   直接拼 '.props.imgSize' 会得到非法路径 ...type.props.imgSize，JSON_SET 静默
--   不生效。必须先去掉结尾的 '.type'（5 个字符）再拼 '.props.imgSize'。
--
--   ⚠️ 缓存：客户端缓存键含 versionId
--   （page_design_cache:{tenant}:{pageId}:{versionId}，TTL 24h）。
--   本脚本原地更新 version 行、不产生新 versionId，因此**执行后必须清缓存**：
--     redis-cli -n <db> --scan --pattern 'page_design_cache:*' | xargs -r redis-cli -n <db> del
--   库号取 spring.data.redis.database（本地 dev 为 1，线上以实际配置为准），
--   否则最长 24 小时看不到新尺寸。
--
-- 执行：mysql -u root -p aryn_boot < 164home_tabnav_icon_size_incremental.sql
-- 随后：清 page_design_cache:*（见上）

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 草稿（page_design.page_content）
-- ---------------------------------------------------------------------------
UPDATE `page_design` p
JOIN (
  SELECT
    `id`,
    CONCAT(
      LEFT(
        JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
        CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
      ),
      '.props.imgSize'
    ) AS `img_path`
  FROM `page_design`
  WHERE `page_type` = '1'
    AND `del_flag` = '0'
    AND JSON_VALID(`page_content`)
    AND JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type') IS NOT NULL
) t ON t.`id` = p.`id`
SET p.`page_content` = JSON_SET(p.`page_content`, t.`img_path`, 56),
    p.`update_time` = NOW(),
    p.`update_by` = 'system'
WHERE CAST(JSON_UNQUOTE(JSON_EXTRACT(p.`page_content`, t.`img_path`)) AS UNSIGNED) < 56;

-- ---------------------------------------------------------------------------
-- 2. 草稿：圆角 22 → 28（图标已放大到 56 的才处理，保持正圆观感）
-- ---------------------------------------------------------------------------
UPDATE `page_design` p
JOIN (
  SELECT
    `id`,
    CONCAT(
      LEFT(
        JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
        CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
      ),
      '.props.imgRadius'
    ) AS `radius_path`,
    CONCAT(
      LEFT(
        JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
        CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
      ),
      '.props.imgSize'
    ) AS `img_path`
  FROM `page_design`
  WHERE `page_type` = '1'
    AND `del_flag` = '0'
    AND JSON_VALID(`page_content`)
    AND JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type') IS NOT NULL
) t ON t.`id` = p.`id`
SET p.`page_content` = JSON_SET(p.`page_content`, t.`radius_path`, 28),
    p.`update_time` = NOW(),
    p.`update_by` = 'system'
WHERE CAST(JSON_UNQUOTE(JSON_EXTRACT(p.`page_content`, t.`img_path`)) AS UNSIGNED) = 56
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(p.`page_content`, t.`radius_path`)) AS UNSIGNED) = 22;

-- ---------------------------------------------------------------------------
-- 3. 当前已发布版本（page_design_version.page_content）：图标 44 → 56
--    只改 published_version_id 指向的那一版，历史快照保持原样。
-- ---------------------------------------------------------------------------
UPDATE `page_design_version` v
JOIN `page_design` p ON p.`published_version_id` = v.`id`
JOIN (
  SELECT
    `id`,
    CONCAT(
      LEFT(
        JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
        CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
      ),
      '.props.imgSize'
    ) AS `img_path`
  FROM `page_design_version`
  WHERE `del_flag` = '0'
    AND JSON_VALID(`page_content`)
    AND JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type') IS NOT NULL
) t ON t.`id` = v.`id`
SET v.`page_content` = JSON_SET(v.`page_content`, t.`img_path`, 56)
WHERE p.`page_type` = '1'
  AND p.`del_flag` = '0'
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(v.`page_content`, t.`img_path`)) AS UNSIGNED) < 56;

-- ---------------------------------------------------------------------------
-- 4. 当前已发布版本：圆角 22 → 28
-- ---------------------------------------------------------------------------
UPDATE `page_design_version` v
JOIN `page_design` p ON p.`published_version_id` = v.`id`
JOIN (
  SELECT
    `id`,
    CONCAT(
      LEFT(
        JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
        CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
      ),
      '.props.imgRadius'
    ) AS `radius_path`,
    CONCAT(
      LEFT(
        JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
        CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
      ),
      '.props.imgSize'
    ) AS `img_path`
  FROM `page_design_version`
  WHERE `del_flag` = '0'
    AND JSON_VALID(`page_content`)
    AND JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type') IS NOT NULL
) t ON t.`id` = v.`id`
SET v.`page_content` = JSON_SET(v.`page_content`, t.`radius_path`, 28)
WHERE p.`page_type` = '1'
  AND p.`del_flag` = '0'
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(v.`page_content`, t.`img_path`)) AS UNSIGNED) = 56
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(v.`page_content`, t.`radius_path`)) AS UNSIGNED) = 22;

-- ---------------------------------------------------------------------------
-- 5. 自检
-- ---------------------------------------------------------------------------
SELECT
  p.`id` AS page_id,
  (SELECT COALESCE(SUM(JSON_LENGTH(jt.`components`)), 0)
     FROM JSON_TABLE(p.`page_content`, '$.sections[*]'
            COLUMNS (`components` JSON PATH '$.components')) jt) AS draft_component_count,
  CAST(
    JSON_UNQUOTE(JSON_EXTRACT(
      p.`page_content`,
      CONCAT(
        LEFT(
          JSON_UNQUOTE(JSON_SEARCH(p.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
          CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(p.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
        ),
        '.props.imgSize'
      )
    ))
    AS UNSIGNED
  ) AS draft_img_size,
  CAST(
    JSON_UNQUOTE(JSON_EXTRACT(
      p.`page_content`,
      CONCAT(
        LEFT(
          JSON_UNQUOTE(JSON_SEARCH(p.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
          CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(p.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
        ),
        '.props.imgRadius'
      )
    ))
    AS UNSIGNED
  ) AS draft_img_radius,
  CAST(
    JSON_UNQUOTE(JSON_EXTRACT(
      v.`page_content`,
      CONCAT(
        LEFT(
          JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
          CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
        ),
        '.props.imgSize'
      )
    ))
    AS UNSIGNED
  ) AS published_img_size,
  CAST(
    JSON_UNQUOTE(JSON_EXTRACT(
      v.`page_content`,
      CONCAT(
        LEFT(
          JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
          CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5
        ),
        '.props.imgRadius'
      )
    ))
    AS UNSIGNED
  ) AS published_img_radius
FROM `page_design` p
LEFT JOIN `page_design_version` v ON v.`id` = p.`published_version_id`
WHERE p.`page_type` = '1' AND p.`del_flag` = '0';

-- 自检口径：
--   · draft_img_size / published_img_size 应为 56（原本已大于 56 的保持原值）；
--   · draft_img_radius / published_img_radius 应为 28（原本不是 22 的保持原值）；
--   · draft_component_count 不变，说明没有误删楼层；
--   · published_* 为 NULL 表示该租户没发布过版本，草稿正确即可。
-- 执行后务必清 page_design_cache:*。

-- 与全量脚本约定保持一致：末尾恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;
