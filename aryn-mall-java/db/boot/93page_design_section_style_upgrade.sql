-- 悦航购装修区块样式能力升级与首页品牌区拆分（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 特性：幂等；只重组**区块划分**并补齐区块样式字段，不删除、不覆盖任何组件配置。
--
-- 背景（2026-09-25）：
--   区块（section）此前只有「背景色 / 背景图 / 上下内边距」三个样式能力，
--   且渲染时恒定**通栏全宽、无圆角、无左右留白、区块之间无间距**。
--   运营给首页唯一区块选了 `rgb(255, 109, 109)` 这类饱和背景色后，
--   效果是一整条贯穿全屏的色带——搜索栏、轮播、金刚区、优惠券、公告、
--   商品楼层全部浮在同一片饱和色上，视觉生硬。
--
--   本次为区块样式扩展四个字段（长度单位统一 px，与组件级 commonStyle、
--   管理端 375px 画布同口径）：
--     · marginX / marginY：左右/上下外边距，让区块脱离通栏成为独立卡片
--     · paddingX：左右内边距，让内部组件不贴边、不盖住圆角
--     · radius：圆角
--   旧文档缺这些字段时缺省为 0，渲染与改造前完全一致，历史页面不会被动画改样。
--
--   本脚本处理「**品牌页头与内容混在同一个上色区块**」这一畸形结构：
--   把开头的品牌组件（搜索栏 / 轮播图 / 图片广告）拆成独立的 `section-brand`，
--   让饱和品牌色只铺在页头；其余组件归入 `section-content`，回到页面底色之上。
--   这正是美团自营 / 小象超市等成熟商城的形态（彩色页头 + 浅底内容区）。
--
--   拆分同时修复两处既有隐患：
--     · `condition` 归一为 `always`：存量区块挂着
--       `{logic:'and',rules:[{type:'memberLevel',memberLevelIds:[]}]}` 空规则，
--       C 端 fail-open 能正常显示，但会被发布校验
--      （DefaultPageDesignDocumentValidator CONDITION_RULE_EMPTY）拦下，
--       导致该页面**无法再次发布**。
--     · `sticky` 归一为 false：原先挂在整页高的区块上形同虚设，
--       拆出的页头仅约 200px 高，保留吸顶会长期占屏。运营可按需在装修页打开。
--
--   守卫条件（缺一即跳过）：
--     · 单区块页面（多区块页面由运营在装修页逐块判断，脚本无法替他决策）
--     · 区块背景色非空（无色带就无需拆分，保持通栏直角）
--     · 开头确有品牌组件，且其后仍有内容组件（否则拆了会更糟）
--
--   客户端读取的是**已发布版本快照**（page_design.published_version_id →
--   page_design_version），不是草稿。因此两处都要迁移，否则线上首页不生效。
--
--   ⚠️ 缓存：客户端缓存键含 versionId
--   （page_design_cache:{tenant}:{pageId}:{versionId}，TTL 24h）。
--   本脚本原地更新 version 行、不产生新 versionId，因此**执行后必须清缓存**：
--     redis-cli -a <pwd> -n 1 --scan --pattern 'page_design_cache:*' \
--       | xargs -r redis-cli -a <pwd> -n 1 del
--   否则最长 24 小时看不到新样式。
--
-- 执行：mysql -u root -p aryn_boot < 93page_design_section_style_upgrade.sql
-- 随后：清 page_design_cache:*（见上）

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 拆分用的品牌组件类型（与前端 page-designer 的页头类组件一致）
-- ---------------------------------------------------------------------------
-- 计算每页「品牌前缀」长度：开头连续属于品牌类的组件个数。
-- JSON_TABLE 展开为行后，取第一个非品牌组件的序号减一即为前缀长度；
-- 全为品牌组件时无匹配行，MIN 返回 NULL，由 COALESCE 兜底为组件总数
-- （此时前置条件 brand_count < total_count 不成立，该页被跳过）。
DROP TEMPORARY TABLE IF EXISTS `tmp_section_split`;
-- 显式声明排序规则：MySQL 8 默认 utf8mb4_0900_ai_ci，而业务列是 utf8mb4_general_ci，
-- 二者在 JOIN 比较 `page_id` 时会报 "Illegal mix of collations"。
CREATE TEMPORARY TABLE `tmp_section_split` (
  `page_id` VARCHAR(64) COLLATE utf8mb4_general_ci NOT NULL,
  `brand_count` INT NOT NULL,
  `total_count` INT NOT NULL,
  PRIMARY KEY (`page_id`)
);

INSERT INTO `tmp_section_split` (`page_id`, `brand_count`, `total_count`)
SELECT
  `pd`.`id`,
  COALESCE(
    MIN(`jt`.`ord`) - 1,
    JSON_LENGTH(JSON_EXTRACT(`pd`.`page_content`, '$.sections[0].components'))
  ) AS `brand_count`,
  JSON_LENGTH(JSON_EXTRACT(`pd`.`page_content`, '$.sections[0].components')) AS `total_count`
FROM `page_design` AS `pd`
LEFT JOIN JSON_TABLE(
       JSON_EXTRACT(`pd`.`page_content`, '$.sections[0].components'),
       '$[*]' COLUMNS (`ord` FOR ORDINALITY, `ctype` VARCHAR(64) PATH '$.type')
       -- LEFT JOIN 的 ON 作为过滤条件：非品牌组件的行不参与 MIN，但左表行保留
       -- COLLATE 显式对齐：JSON_TABLE 产出的字符串默认跟随库排序规则
     ) AS `jt` ON `jt`.`ctype` COLLATE utf8mb4_general_ci
       NOT IN ('search-bar', 'swiper-banner', 'image-ad')
WHERE `pd`.`del_flag` = '0'
  AND JSON_LENGTH(JSON_EXTRACT(`pd`.`page_content`, '$.sections')) = 1
  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`pd`.`page_content`, '$.sections[0].style.backgroundColor')), '') <> ''
GROUP BY `pd`.`id`
HAVING `brand_count` > 0 AND `brand_count` < `total_count`;

-- ---------------------------------------------------------------------------
-- 1. 草稿（page_design.page_content）
-- ---------------------------------------------------------------------------
UPDATE `page_design` AS `pd`
JOIN `tmp_section_split` AS `k` ON `k`.`page_id` = `pd`.`id`
SET `pd`.`page_content` = JSON_SET(
      `pd`.`page_content`,
      '$.sections',
      JSON_ARRAY(
        JSON_OBJECT(
          'components', JSON_EXTRACT(`pd`.`page_content`,
              CONCAT('$.sections[0].components[0 to ', `k`.`brand_count` - 1, ']')),
          'id', 'section-brand',
          'name', '品牌区',
          'style', JSON_OBJECT(
            'backgroundColor', JSON_UNQUOTE(JSON_EXTRACT(`pd`.`page_content`, '$.sections[0].style.backgroundColor')),
            'backgroundImage', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`pd`.`page_content`, '$.sections[0].style.backgroundImage')), ''),
            'condition', 'always',
            'horizontalScroll', FALSE,
            'marginX', 0,
            'marginY', 0,
            'paddingX', 0,
            'paddingY', 0,
            'radius', 0,
            'sticky', FALSE
          ),
          'type', 'default'
        ),
        JSON_OBJECT(
          'components', JSON_EXTRACT(`pd`.`page_content`,
              CONCAT('$.sections[0].components[', `k`.`brand_count`, ' to last]')),
          'id', 'section-content',
          'name', '内容区',
          'style', JSON_OBJECT(
            'backgroundColor', '',
            'backgroundImage', '',
            'condition', 'always',
            'horizontalScroll', FALSE,
            'marginX', 0,
            'marginY', 0,
            'paddingX', 0,
            'paddingY', 0,
            'radius', 0,
            'sticky', FALSE
          ),
          'type', 'default'
        )
      )
    );

-- ---------------------------------------------------------------------------
-- 2. 已发布版本快照（page_design_version.page_content）
--    同上口径；只处理**当前发布版本**（published_version_id），
--    历史版本保持原样以便回溯对比。
-- ---------------------------------------------------------------------------
UPDATE `page_design_version` AS `pdv`
JOIN `page_design` AS `pd` ON `pd`.`published_version_id` = `pdv`.`id`
JOIN `tmp_section_split` AS `k` ON `k`.`page_id` = `pd`.`id`
SET `pdv`.`page_content` = JSON_SET(
      `pdv`.`page_content`,
      '$.sections',
      JSON_ARRAY(
        JSON_OBJECT(
          'components', JSON_EXTRACT(`pdv`.`page_content`,
              CONCAT('$.sections[0].components[0 to ', `k`.`brand_count` - 1, ']')),
          'id', 'section-brand',
          'name', '品牌区',
          'style', JSON_OBJECT(
            'backgroundColor', JSON_UNQUOTE(JSON_EXTRACT(`pdv`.`page_content`, '$.sections[0].style.backgroundColor')),
            'backgroundImage', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`pdv`.`page_content`, '$.sections[0].style.backgroundImage')), ''),
            'condition', 'always',
            'horizontalScroll', FALSE,
            'marginX', 0,
            'marginY', 0,
            'paddingX', 0,
            'paddingY', 0,
            'radius', 0,
            'sticky', FALSE
          ),
          'type', 'default'
        ),
        JSON_OBJECT(
          'components', JSON_EXTRACT(`pdv`.`page_content`,
              CONCAT('$.sections[0].components[', `k`.`brand_count`, ' to last]')),
          'id', 'section-content',
          'name', '内容区',
          'style', JSON_OBJECT(
            'backgroundColor', '',
            'backgroundImage', '',
            'condition', 'always',
            'horizontalScroll', FALSE,
            'marginX', 0,
            'marginY', 0,
            'paddingX', 0,
            'paddingY', 0,
            'radius', 0,
            'sticky', FALSE
          ),
          'type', 'default'
        )
      )
    );

DROP TEMPORARY TABLE IF EXISTS `tmp_section_split`;

-- ---------------------------------------------------------------------------
-- 3. 品牌区搜索框：灰底改白
--    品牌色是饱和红粉时，原灰底(#e3e3e3)会显出脏感；白胶囊才是页头标准形态
--   （与轮播图一致）。只改这条历史默认值，运营自定义过的颜色保持不变。
-- ---------------------------------------------------------------------------
UPDATE `page_design`
SET `page_content` = JSON_SET(
      `page_content`,
      '$.sections[0].components[0].props.bgColor', '#ffffff')
WHERE `del_flag` = '0'
  AND JSON_UNQUOTE(JSON_EXTRACT(`page_content`, '$.sections[0].components[0].type')) = 'search-bar'
  AND JSON_UNQUOTE(JSON_EXTRACT(`page_content`, '$.sections[0].components[0].props.bgColor'))
      IN ('rgb(227, 227, 227)', '#e3e3e3');

UPDATE `page_design_version` AS `pdv`
JOIN `page_design` AS `pd` ON `pd`.`published_version_id` = `pdv`.`id`
SET `pdv`.`page_content` = JSON_SET(
      `pdv`.`page_content`,
      '$.sections[0].components[0].props.bgColor', '#ffffff')
WHERE `pdv`.`del_flag` = '0'
  AND JSON_UNQUOTE(JSON_EXTRACT(`pdv`.`page_content`, '$.sections[0].components[0].type')) = 'search-bar'
  AND JSON_UNQUOTE(JSON_EXTRACT(`pdv`.`page_content`, '$.sections[0].components[0].props.bgColor'))
      IN ('rgb(227, 227, 227)', '#e3e3e3');

-- ---------------------------------------------------------------------------
-- 4. 自检
--    期望：brand_split_left = 0（无待拆分页面）
--         split_done 为已拆分的页面数（便于确认命中范围）
-- ---------------------------------------------------------------------------
SELECT 'brand_split_left' AS `check_name`,
       COUNT(*) AS `value`,
       0 AS `expected`
FROM `page_design`
WHERE `del_flag` = '0'
  AND JSON_LENGTH(JSON_EXTRACT(`page_content`, '$.sections')) = 1
  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`page_content`, '$.sections[0].style.backgroundColor')), '') <> ''
  AND JSON_UNQUOTE(JSON_EXTRACT(`page_content`, '$.sections[0].components[0].type'))
      IN ('search-bar', 'swiper-banner', 'image-ad')
UNION ALL
SELECT 'split_done', COUNT(*), NULL
FROM `page_design`
WHERE `del_flag` = '0'
  AND JSON_SEARCH(`page_content`, 'one', 'section-brand', NULL, '$.sections[*].id') IS NOT NULL;
