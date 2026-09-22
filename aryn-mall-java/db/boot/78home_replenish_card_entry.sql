-- 悦航购首页补给单卡片装修迁移（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 特性：幂等；只**插入一个组件**，不删除、不覆盖既有楼层与运营配置。
--
-- 背景（2026-09-22）：
--   补给单卡片（replenish-card）此前已注册但只存在于组件库，运营需手工拖入；
--   本脚本让**存量租户**的首页默认就能看到它。
--
--   客户端读取的是**已发布版本快照**（page_design.published_version_id →
--   page_design_version），不是草稿（page_design.page_content）。因此两处都要迁移，
--   否则线上首页不会出现该组件。
--
--   插入位置：**金刚区（tab-nav）之前**。与移动端改造原型一致
--   （状态条 → 搜索 → 今日补给单卡 → 金刚区），也让卡片紧邻购物入口。
--   ⚠️ 不能用 `$.sections[0].components[0]` 硬编码下标：
--      · 船舶工作台（69 号脚本）已插在首位，硬编码会把它挤到卡片后面；
--      · 金刚区实测在 [1]，但那是数据状态、不是约束。
--   这里用同一套 JSON_SEARCH 定位 tab-nav 再插到它前面，位置稳定。
--
--   同时兼容两种历史结构：
--     · v3：sections[].components[]，组件用 props
--     · v2：根级 components[]，组件用 formData（读取侧 migratePageContent 会自动迁移）
--   注意 v2/v3 的 JSON_SEARCH 路径不同（`$.components[*].type` vs
--   `$.sections[*].components[*].type`），必须分开处理。
--
--   ⚠️ JSON_SEARCH 返回的是**指向 `.type` 值**的路径（如
--     "$.sections[0].components[1].type"）。JSON_ARRAY_INSERT 要的是**数组下标路径**，
--     因此必须先 LEFT(..., CHAR_LENGTH(...) - 5) 裁掉结尾的 '.type'，
--     直接传原路径会插到 `.type` 上的非法位置、静默不生效。
--
--   ⚠️ 缓存：客户端缓存键含 versionId
--   （page_design_cache:{tenant}:{pageId}:{versionId}，TTL 24h）。
--   本脚本原地更新 version 行、不产生新 versionId，因此**执行后必须清缓存**：
--     redis-cli --scan --pattern 'page_design_cache:*' | xargs -r redis-cli del
--   否则最长 24 小时看不到新组件。69 号脚本同样受此影响。
--
--   可见性无需按租户筛数据：组件自身按 `shipSupplyEnabled`（船供能力）判断，
--   纯零售租户与未登录用户都不会渲染，因此所有首页都可以安全插入。
--
-- 执行：mysql -u root -p aryn_boot < 78home_replenish_card_entry.sql
-- 随后：清 page_design_cache:*（见上）

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 草稿（page_design.page_content）
-- ---------------------------------------------------------------------------
-- v3（sections[]）
UPDATE `page_design`
SET `page_content` = JSON_ARRAY_INSERT(
      `page_content`,
      LEFT(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
           CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5),
      JSON_OBJECT('id', CONCAT('rp-', `id`), 'type', 'replenish-card', 'version', 1, 'props', JSON_OBJECT(
        'commonStyle', JSON_OBJECT(
          'bgColorDirection', 'to right',
          'bgEndColor', '',
          'bgPicUrl', '',
          'bgStartColor', '#ffffff',
          'styleBottomMargin', 10,
          'styleBottomPadding', 12,
          'styleLbRadius', 0,
          'styleLeftMargin', 10,
          'styleLeftPadding', 12,
          'styleLtRadius', 0,
          'styleRbRadius', 0,
          'styleRightMargin', 10,
          'styleRightPadding', 12,
          'styleRtRadius', 0,
          'styleTopMargin', 10,
          'styleTopPadding', 12
        ),
        'count', 1,
        'dataSource', JSON_OBJECT('mode', 'current-tenant'),
        'emptyStrategy', 'hide',
        'invalidStrategy', 'hide',
        'showBatchAdd', TRUE,
        'showPreview', TRUE,
        'title', '今日补给单'
      )
      )
    ),
    `update_time` = NOW(),
    `update_by` = 'system'
WHERE `page_type` = '1'
  AND `del_flag` = '0'
  AND JSON_VALID(`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(`page_content`, '$.sections')) = 'ARRAY'
  AND JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type') IS NOT NULL
  -- 幂等护栏：按 type 判断（结构字段），不用 title —— 运营改标题不会让护栏失效。
  -- 同时这也是单例组件：重复插入会在发布时被服务端拦下（COMPONENT_DUPLICATED）。
  AND JSON_SEARCH(`page_content`, 'one', 'replenish-card', NULL, '$.sections[*].components[*].type') IS NULL;

-- v2（根级 components[]，组件字段为 formData）
UPDATE `page_design`
SET `page_content` = JSON_ARRAY_INSERT(
      `page_content`,
      LEFT(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.components[*].type')),
           CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.components[*].type'))) - 5),
      JSON_OBJECT('id', CONCAT('rp-', `id`), 'title', '今日补给单', 'type', 'replenish-card', 'formData', JSON_OBJECT(
        'commonStyle', JSON_OBJECT(
          'bgColorDirection', 'to right',
          'bgEndColor', '',
          'bgPicUrl', '',
          'bgStartColor', '#ffffff',
          'styleBottomMargin', 10,
          'styleBottomPadding', 12,
          'styleLbRadius', 0,
          'styleLeftMargin', 10,
          'styleLeftPadding', 12,
          'styleLtRadius', 0,
          'styleRbRadius', 0,
          'styleRightMargin', 10,
          'styleRightPadding', 12,
          'styleRtRadius', 0,
          'styleTopMargin', 10,
          'styleTopPadding', 12
        ),
        'count', 1,
        'dataSource', JSON_OBJECT('mode', 'current-tenant'),
        'emptyStrategy', 'hide',
        'invalidStrategy', 'hide',
        'showBatchAdd', TRUE,
        'showPreview', TRUE,
        'title', '今日补给单'
      )
      )
    ),
    `update_time` = NOW(),
    `update_by` = 'system'
WHERE `page_type` = '1'
  AND `del_flag` = '0'
  AND JSON_VALID(`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(`page_content`, '$.components')) = 'ARRAY'
  AND JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.components[*].type') IS NOT NULL
  AND JSON_SEARCH(`page_content`, 'one', 'replenish-card', NULL, '$.components[*].type') IS NULL;

-- ---------------------------------------------------------------------------
-- 2. 已发布版本快照（含灰度版本）
-- ---------------------------------------------------------------------------
-- v3（sections[]）
UPDATE `page_design_version` v
JOIN `page_design` p ON p.`id` = v.`page_design_id`
SET v.`page_content` = JSON_ARRAY_INSERT(
      v.`page_content`,
      LEFT(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type')),
           CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type'))) - 5),
      JSON_OBJECT('id', CONCAT('rp-', v.`id`), 'type', 'replenish-card', 'version', 1, 'props', JSON_OBJECT(
        'commonStyle', JSON_OBJECT(
          'bgColorDirection', 'to right',
          'bgEndColor', '',
          'bgPicUrl', '',
          'bgStartColor', '#ffffff',
          'styleBottomMargin', 10,
          'styleBottomPadding', 12,
          'styleLbRadius', 0,
          'styleLeftMargin', 10,
          'styleLeftPadding', 12,
          'styleLtRadius', 0,
          'styleRbRadius', 0,
          'styleRightMargin', 10,
          'styleRightPadding', 12,
          'styleRtRadius', 0,
          'styleTopMargin', 10,
          'styleTopPadding', 12
        ),
        'count', 1,
        'dataSource', JSON_OBJECT('mode', 'current-tenant'),
        'emptyStrategy', 'hide',
        'invalidStrategy', 'hide',
        'showBatchAdd', TRUE,
        'showPreview', TRUE,
        'title', '今日补给单'
      )
      )
    )
WHERE p.`page_type` = '1'
  AND p.`del_flag` = '0'
  AND v.`del_flag` = '0'
  AND JSON_VALID(v.`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(v.`page_content`, '$.sections')) = 'ARRAY'
  AND JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[*].components[*].type') IS NOT NULL
  AND JSON_SEARCH(v.`page_content`, 'one', 'replenish-card', NULL, '$.sections[*].components[*].type') IS NULL;

-- v2（根级 components[]）
UPDATE `page_design_version` v
JOIN `page_design` p ON p.`id` = v.`page_design_id`
SET v.`page_content` = JSON_ARRAY_INSERT(
      v.`page_content`,
      LEFT(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.components[*].type')),
           CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.components[*].type'))) - 5),
      JSON_OBJECT('id', CONCAT('rp-', v.`id`), 'title', '今日补给单', 'type', 'replenish-card', 'formData', JSON_OBJECT(
        'commonStyle', JSON_OBJECT(
          'bgColorDirection', 'to right',
          'bgEndColor', '',
          'bgPicUrl', '',
          'bgStartColor', '#ffffff',
          'styleBottomMargin', 10,
          'styleBottomPadding', 12,
          'styleLbRadius', 0,
          'styleLeftMargin', 10,
          'styleLeftPadding', 12,
          'styleLtRadius', 0,
          'styleRbRadius', 0,
          'styleRightMargin', 10,
          'styleRightPadding', 12,
          'styleRtRadius', 0,
          'styleTopMargin', 10,
          'styleTopPadding', 12
        ),
        'count', 1,
        'dataSource', JSON_OBJECT('mode', 'current-tenant'),
        'emptyStrategy', 'hide',
        'invalidStrategy', 'hide',
        'showBatchAdd', TRUE,
        'showPreview', TRUE,
        'title', '今日补给单'
      )
      )
    )
WHERE p.`page_type` = '1'
  AND p.`del_flag` = '0'
  AND v.`del_flag` = '0'
  AND JSON_VALID(v.`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(v.`page_content`, '$.components')) = 'ARRAY'
  AND JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.components[*].type') IS NOT NULL
  AND JSON_SEARCH(v.`page_content`, 'one', 'replenish-card', NULL, '$.components[*].type') IS NULL;

-- ---------------------------------------------------------------------------
-- 3. 自检
-- ---------------------------------------------------------------------------
-- 期望：
--   · draft_has_card / published_has_card 均为 1（当前发布版本已含补给单卡片）；
--   · card_before_kingkong 为 1（补给单卡在金刚区之前，即原型顺序）；
--   · floors_before / floors_after 应相差 1，等于原楼层数 +1；
--     若变小说明脚本误删了楼层，必须回滚重来。
SELECT
  p.`id` AS page_id,
  JSON_LENGTH(JSON_EXTRACT(p.`page_content`, '$.sections[0].components')) AS draft_floors,
  JSON_SEARCH(p.`page_content`, 'one', 'replenish-card', NULL, '$.sections[*].components[*].type') IS NOT NULL AS draft_has_card,
  JSON_SEARCH(v.`page_content`, 'one', 'replenish-card', NULL, '$.sections[*].components[*].type') IS NOT NULL AS published_has_card,
  -- 0 表示补给单卡排在金刚区之后（顺序不符原型）
  LOCATE('replenish-card', p.`page_content`) > LOCATE('tab-nav', p.`page_content`) AS card_after_kingkong
FROM `page_design` p
LEFT JOIN `page_design_version` v ON v.`id` = p.`published_version_id`
WHERE p.`page_type` = '1' AND p.`del_flag` = '0';
