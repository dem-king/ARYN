-- 悦航购首页船舶工作台装修化（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 特性：幂等；不删除、不覆盖既有组件与运营配置；仅做 JSON 结构插入。
--
-- 背景（2026-09-21）：
--   船舶工作台原先硬编码在首页装修页的 below-navbar 插槽中，运营既不能调整位置也不能隐藏。
--   改造后成为装修组件 ship-workbench，可自由排序/删除，服务端对「同页重复」发布阻断。
--
--   客户端读取的是**已发布版本快照**（page_design.published_version_id → page_design_version），
--   不是草稿（page_design.page_content）。因此存量首页必须同时迁移两处，
--   否则改造上线后存量租户首页的船舶工作台会直接消失。
--
--   同时兼容两种历史结构：
--     · v3：sections[].components[]，组件用 props
--     · v2：根级 components[]，组件用 formData（读取侧 migratePageContent 会自动迁移）
--   插入位置为组件数组**首位**，与改造前「导航栏下方、所有 DIY 组件之上」的视觉位置一致。
--
--   注意：必须用 JSON_ARRAY_INSERT(..., '$[0]', ...) 在数组头部插入；
--   JSON_INSERT 在路径已存在时会静默不生效。
--
-- 执行：mysql -u root -p aryn_boot < 69ship_workbench_component_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ===========================================================================
-- ===========================================================================
-- 1. 草稿（page_design.page_content）
-- v3（sections）：插入首区块组件数组首位
UPDATE `page_design`
SET `page_content` = JSON_REPLACE(
      `page_content`,
      '$.sections[0].components',
      JSON_ARRAY_INSERT(
        JSON_EXTRACT(`page_content`, '$.sections[0].components'),
        '$[0]',
        JSON_OBJECT('id', CONCAT('sb-', `id`), 'type', 'ship-workbench', 'version', 1, 'props', JSON_OBJECT(
            'commonStyle', JSON_OBJECT(
              'bgColorDirection', 'to right',
              'bgEndColor', '',
              'bgPicUrl', '',
              'bgStartColor', '#ffffff',
              'styleBottomMargin', 10,
              'styleBottomPadding', 0,
              'styleLbRadius', 16,
              'styleLeftMargin', 10,
              'styleLeftPadding', 0,
              'styleLtRadius', 16,
              'styleRbRadius', 16,
              'styleRightMargin', 10,
              'styleRightPadding', 0,
              'styleRtRadius', 16,
              'styleTopMargin', 10,
              'styleTopPadding', 0
            ),
            'count', 1,
            'dataSource', JSON_OBJECT('mode', 'current-tenant'),
            'emptyStrategy', 'hide',
            'invalidStrategy', 'hide',
            'showFrequent', TRUE
          ))
      )
    )
WHERE `page_type` = '1'
  AND `del_flag` = '0'
  AND JSON_VALID(`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(`page_content`, '$.sections[0].components')) = 'ARRAY'
  AND `page_content` NOT LIKE '%"ship-workbench"%'
  AND JSON_SEARCH(`page_content`, 'one', 'ship-workbench', NULL, '$.sections[*].components[*].type') IS NULL;

-- v2（components + formData）：同一位置，写入旧格式字段以兼容存量结构
--   读取侧 migratePageContent 会把 formData 迁到 props，无需额外处理。
UPDATE `page_design`
SET `page_content` = JSON_REPLACE(
      `page_content`,
      '$.components',
      JSON_ARRAY_INSERT(
        JSON_EXTRACT(`page_content`, '$.components'),
        '$[0]',
        JSON_OBJECT('id', CONCAT('sb-', `id`), 'title', '船舶工作台', 'type', 'ship-workbench',
                    'formData', JSON_OBJECT(
            'commonStyle', JSON_OBJECT(
              'bgColorDirection', 'to right',
              'bgEndColor', '',
              'bgPicUrl', '',
              'bgStartColor', '#ffffff',
              'styleBottomMargin', 10,
              'styleBottomPadding', 0,
              'styleLbRadius', 16,
              'styleLeftMargin', 10,
              'styleLeftPadding', 0,
              'styleLtRadius', 16,
              'styleRbRadius', 16,
              'styleRightMargin', 10,
              'styleRightPadding', 0,
              'styleRtRadius', 16,
              'styleTopMargin', 10,
              'styleTopPadding', 0
            ),
            'count', 1,
            'dataSource', JSON_OBJECT('mode', 'current-tenant'),
            'emptyStrategy', 'hide',
            'invalidStrategy', 'hide',
            'showFrequent', TRUE
          ))
      )
    )
WHERE `page_type` = '1'
  AND `del_flag` = '0'
  AND JSON_VALID(`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(`page_content`, '$.components')) = 'ARRAY'
  AND `page_content` NOT LIKE '%"ship-workbench"%'
  AND JSON_SEARCH(`page_content`, 'one', 'ship-workbench', NULL, '$.components[*].type') IS NULL;

-- 2. 已发布版本（page_design_version.page_content，含灰度版本）
-- v3（sections）：插入首区块组件数组首位
UPDATE `page_design_version` v
JOIN `page_design` p ON p.`id` = v.`page_design_id`
SET v.`page_content` = JSON_REPLACE(
      v.`page_content`,
      '$.sections[0].components',
      JSON_ARRAY_INSERT(
        JSON_EXTRACT(v.`page_content`, '$.sections[0].components'),
        '$[0]',
        JSON_OBJECT('id', CONCAT('sb-', v.`id`), 'type', 'ship-workbench', 'version', 1, 'props', JSON_OBJECT(
            'commonStyle', JSON_OBJECT(
              'bgColorDirection', 'to right',
              'bgEndColor', '',
              'bgPicUrl', '',
              'bgStartColor', '#ffffff',
              'styleBottomMargin', 10,
              'styleBottomPadding', 0,
              'styleLbRadius', 16,
              'styleLeftMargin', 10,
              'styleLeftPadding', 0,
              'styleLtRadius', 16,
              'styleRbRadius', 16,
              'styleRightMargin', 10,
              'styleRightPadding', 0,
              'styleRtRadius', 16,
              'styleTopMargin', 10,
              'styleTopPadding', 0
            ),
            'count', 1,
            'dataSource', JSON_OBJECT('mode', 'current-tenant'),
            'emptyStrategy', 'hide',
            'invalidStrategy', 'hide',
            'showFrequent', TRUE
          ))
      )
    )
WHERE p.`page_type` = '1'
  AND v.`del_flag` = '0'
  AND JSON_VALID(v.`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(v.`page_content`, '$.sections[0].components')) = 'ARRAY'
  AND v.`page_content` NOT LIKE '%"ship-workbench"%'
  AND JSON_SEARCH(v.`page_content`, 'one', 'ship-workbench', NULL, '$.sections[*].components[*].type') IS NULL;

-- v2（components + formData）：同一位置，写入旧格式字段以兼容存量结构
--   读取侧 migratePageContent 会把 formData 迁到 props，无需额外处理。
UPDATE `page_design_version` v
JOIN `page_design` p ON p.`id` = v.`page_design_id`
SET v.`page_content` = JSON_REPLACE(
      v.`page_content`,
      '$.components',
      JSON_ARRAY_INSERT(
        JSON_EXTRACT(v.`page_content`, '$.components'),
        '$[0]',
        JSON_OBJECT('id', CONCAT('sb-', v.`id`), 'title', '船舶工作台', 'type', 'ship-workbench',
                    'formData', JSON_OBJECT(
            'commonStyle', JSON_OBJECT(
              'bgColorDirection', 'to right',
              'bgEndColor', '',
              'bgPicUrl', '',
              'bgStartColor', '#ffffff',
              'styleBottomMargin', 10,
              'styleBottomPadding', 0,
              'styleLbRadius', 16,
              'styleLeftMargin', 10,
              'styleLeftPadding', 0,
              'styleLtRadius', 16,
              'styleRbRadius', 16,
              'styleRightMargin', 10,
              'styleRightPadding', 0,
              'styleRtRadius', 16,
              'styleTopMargin', 10,
              'styleTopPadding', 0
            ),
            'count', 1,
            'dataSource', JSON_OBJECT('mode', 'current-tenant'),
            'emptyStrategy', 'hide',
            'invalidStrategy', 'hide',
            'showFrequent', TRUE
          ))
      )
    )
WHERE p.`page_type` = '1'
  AND v.`del_flag` = '0'
  AND JSON_VALID(v.`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(v.`page_content`, '$.components')) = 'ARRAY'
  AND v.`page_content` NOT LIKE '%"ship-workbench"%'
  AND JSON_SEARCH(v.`page_content`, 'one', 'ship-workbench', NULL, '$.components[*].type') IS NULL;

-- ===========================================================================
-- 自检：以下四行均应为 0，否则说明首页草稿或已发布版本仍缺少该组件
-- ===========================================================================
SELECT '草稿-v3缺少' AS check_item, COUNT(*) AS remaining
FROM `page_design`
WHERE `page_type` = '1' AND `del_flag` = '0' AND JSON_VALID(`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(`page_content`, '$.sections[0].components')) = 'ARRAY'
  AND JSON_SEARCH(`page_content`, 'one', 'ship-workbench', NULL, '$.sections[*].components[*].type') IS NULL
UNION ALL
SELECT '草稿-v2缺少', COUNT(*)
FROM `page_design`
WHERE `page_type` = '1' AND `del_flag` = '0' AND JSON_VALID(`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(`page_content`, '$.components')) = 'ARRAY'
  AND JSON_SEARCH(`page_content`, 'one', 'ship-workbench', NULL, '$.components[*].type') IS NULL
UNION ALL
SELECT '版本-v3缺少', COUNT(*)
FROM `page_design_version` dv JOIN `page_design` p ON p.`id` = dv.`page_design_id`
WHERE p.`page_type` = '1' AND dv.`del_flag` = '0' AND JSON_VALID(dv.`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(dv.`page_content`, '$.sections[0].components')) = 'ARRAY'
  AND JSON_SEARCH(dv.`page_content`, 'one', 'ship-workbench', NULL, '$.sections[*].components[*].type') IS NULL
UNION ALL
SELECT '版本-v2缺少', COUNT(*)
FROM `page_design_version` dv JOIN `page_design` p ON p.`id` = dv.`page_design_id`
WHERE p.`page_type` = '1' AND dv.`del_flag` = '0' AND JSON_VALID(dv.`page_content`)
  AND JSON_TYPE(JSON_EXTRACT(dv.`page_content`, '$.components')) = 'ARRAY'
  AND JSON_SEARCH(dv.`page_content`, 'one', 'ship-workbench', NULL, '$.components[*].type') IS NULL;
