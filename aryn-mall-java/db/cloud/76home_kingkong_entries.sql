-- 悦航购首页金刚区改为 8 项（Cloud 微服务模式）
--
-- 目标库：aryn_promotion（页面装修库）
-- 特性：幂等；**只替换金刚区那一个组件**，不动轮播/商品/秒杀等其它楼层。
--
-- 背景（2026-09-22）：
--   现有首页金刚区是 tab-nav 的 4 项（全部商品/浏览记录/浏览记录/领券中心），
--   与移动端改造方案 B 版原型的 8 项不一致，且"浏览记录"重复占了 2 格。
--
--   客户端读取的是**已发布版本快照**（page_design.published_version_id →
--   page_design_version），不是草稿，因此必须同时迁移两处。
--
--   实现要点：用 JSON_SEARCH 定位 type='tab-nav'，再用 JSON_SET 只替换该组件的
--   props —— 绝不能整体重写 components 数组，否则会连带删掉轮播/商品/秒杀楼层
--   （这是本脚本第一版的真实缺陷，自检里的 component_count 就是为此加的护栏）。
--
--   ⚠️ JSON_SEARCH 返回的是**指向 `.type` 值**的路径，例如
--     "$.sections[0].components[1].type"
--   直接拼 '.props' 会得到非法路径 ...type.props，JSON_SET 静默不生效。
--   必须先 LEFT(..., CHAR_LENGTH(...) - 5) 去掉结尾的 '.type' 再拼 '.props'。
--   （已在 MySQL 8.0 实机验证：去掉后 JSON_TYPE(...'props') 为 OBJECT。）
--
--   ⚠️ 缓存：客户端缓存键含 versionId
--   （page_design_cache:{tenant}:{pageId}:{versionId}，TTL 24h）。
--   本脚本原地更新 version 行、不产生新 versionId，因此**执行后必须清缓存**：
--     redis-cli --scan --pattern 'page_design_cache:*' | xargs -r redis-cli del
--   否则最长 24 小时看不到新金刚区。
--
-- 执行：mysql -u root -p aryn_promotion < 76home_kingkong_entries.sql
-- 随后：清 page_design_cache:*（见上）

USE `aryn_promotion`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 草稿（page_design.page_content）
-- ---------------------------------------------------------------------------
UPDATE `page_design`
SET `page_content` = JSON_SET(
      `page_content`,
      CONCAT(LEFT(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type')), CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type'))) - 5), '.props'),
      JSON_OBJECT(
              'type', '3',
              'fontColor', 'rgba(0, 0, 0, 1)',
              'showNum', 4,
              'imgSize', 30,
              'imgRadius', 0,
              'scrollShow', false,
              'commonStyle', JSON_OBJECT(
                'styleTopMargin', 10, 'styleBottomMargin', 10,
                'styleLeftMargin', 10, 'styleRightMargin', 10,
                'styleTopPadding', 10, 'styleBottomPadding', 10,
                'styleLeftPadding', 10, 'styleRightPadding', 10,
                'styleLtRadius', 10, 'styleRtRadius', 10,
                'styleLbRadius', 10, 'styleRbRadius', 10,
                'bgColorDirection', 'to right',
                'bgStartColor', 'rgba(255, 255, 255, 1)',
                'bgEndColor', '', 'bgPicUrl', ''
              ),
              'navList', JSON_ARRAY(
                JSON_OBJECT('title', '我的补给单', 'url', '',
                  'link', JSON_OBJECT('name', '共享购物车列表', 'url', '/sub-pages/order/shared-cart/list')),
                JSON_OBJECT('title', '常购清单', 'url', '',
                  'link', JSON_OBJECT('name', '常购清单', 'url', '/sub-pages/product/frequent/index')),
                JSON_OBJECT('title', '限时折扣', 'url', '',
                  'link', JSON_OBJECT('name', '限时折扣', 'url', '/pages/promotion/discount')),
                JSON_OBJECT('title', '领券中心', 'url', '',
                  'link', JSON_OBJECT('name', '优惠券列表', 'url', '/sub-pages/promotion/coupon/coupon-list/index')),
                JSON_OBJECT('title', '每日签到', 'url', '',
                  'link', JSON_OBJECT('name', '每日签到', 'url', '/sub-pages/user/member/sign-in')),
                JSON_OBJECT('title', '多人拼团', 'url', '',
                  'link', JSON_OBJECT('name', '拼团列表', 'url', '/sub-pages/promotion/group-buy/group-buy-list/index')),
                JSON_OBJECT('title', '船供专区', 'url', '',
                  'link', JSON_OBJECT('name', '船供采购', 'url', '/sub-pages/product/ship-supply/index')),
                JSON_OBJECT('title', '联系客服', 'url', '',
                  'link', JSON_OBJECT('name', '客服会话', 'url', '/sub-pages/message/chat/index'))
              ),
              'scrollShw', true
      )
    ),
    `update_time` = NOW(),
    `update_by` = 'system'
WHERE `page_type` = '1'
  AND `del_flag` = '0'
  AND JSON_VALID(`page_content`)
  -- 只处理含 tab-nav 的首页
  AND JSON_SEARCH(`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type') IS NOT NULL
  -- 幂等护栏：已升级过则跳过
  AND `page_content` NOT LIKE '%kk-replenish-%';

-- ---------------------------------------------------------------------------
-- 2. 已发布版本（page_design_version.page_content，含灰度版本）
-- ---------------------------------------------------------------------------
UPDATE `page_design_version` v
JOIN `page_design` p ON p.`id` = v.`page_design_id`
SET v.`page_content` = JSON_SET(
      v.`page_content`,
      CONCAT(LEFT(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type')), CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type'))) - 5), '.props'),
      JSON_OBJECT(
              'type', '3',
              'fontColor', 'rgba(0, 0, 0, 1)',
              'showNum', 4,
              'imgSize', 30,
              'imgRadius', 0,
              'scrollShow', false,
              'commonStyle', JSON_OBJECT(
                'styleTopMargin', 10, 'styleBottomMargin', 10,
                'styleLeftMargin', 10, 'styleRightMargin', 10,
                'styleTopPadding', 10, 'styleBottomPadding', 10,
                'styleLeftPadding', 10, 'styleRightPadding', 10,
                'styleLtRadius', 10, 'styleRtRadius', 10,
                'styleLbRadius', 10, 'styleRbRadius', 10,
                'bgColorDirection', 'to right',
                'bgStartColor', 'rgba(255, 255, 255, 1)',
                'bgEndColor', '', 'bgPicUrl', ''
              ),
              'navList', JSON_ARRAY(
                JSON_OBJECT('title', '我的补给单', 'url', '',
                  'link', JSON_OBJECT('name', '共享购物车列表', 'url', '/sub-pages/order/shared-cart/list')),
                JSON_OBJECT('title', '常购清单', 'url', '',
                  'link', JSON_OBJECT('name', '常购清单', 'url', '/sub-pages/product/frequent/index')),
                JSON_OBJECT('title', '限时折扣', 'url', '',
                  'link', JSON_OBJECT('name', '限时折扣', 'url', '/pages/promotion/discount')),
                JSON_OBJECT('title', '领券中心', 'url', '',
                  'link', JSON_OBJECT('name', '优惠券列表', 'url', '/sub-pages/promotion/coupon/coupon-list/index')),
                JSON_OBJECT('title', '每日签到', 'url', '',
                  'link', JSON_OBJECT('name', '每日签到', 'url', '/sub-pages/user/member/sign-in')),
                JSON_OBJECT('title', '多人拼团', 'url', '',
                  'link', JSON_OBJECT('name', '拼团列表', 'url', '/sub-pages/promotion/group-buy/group-buy-list/index')),
                JSON_OBJECT('title', '船供专区', 'url', '',
                  'link', JSON_OBJECT('name', '船供采购', 'url', '/sub-pages/product/ship-supply/index')),
                JSON_OBJECT('title', '联系客服', 'url', '',
                  'link', JSON_OBJECT('name', '客服会话', 'url', '/sub-pages/message/chat/index'))
              ),
              'scrollShw', true
      )
    )
WHERE p.`page_type` = '1'
  AND v.`del_flag` = '0'
  AND JSON_VALID(v.`page_content`)
  AND JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type') IS NOT NULL
  AND v.`page_content` NOT LIKE '%kk-replenish-%';

-- ---------------------------------------------------------------------------
-- 3. 自检
-- ---------------------------------------------------------------------------
-- 注意：金刚区不一定在第 0 个组件（实测种子数据里它在 [1]），
-- 因此这里用同一套 JSON_SEARCH 定位，不能写死 components[0]。
SELECT
  p.`id` AS page_id,
  JSON_LENGTH(JSON_EXTRACT(
    p.`page_content`,
    CONCAT(LEFT(JSON_UNQUOTE(JSON_SEARCH(p.`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type')),
                CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(p.`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type'))) - 5),
           '.props.navList')
  )) AS draft_nav_count,
  JSON_LENGTH(JSON_EXTRACT(
    v.`page_content`,
    CONCAT(LEFT(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type')),
                CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(v.`page_content`, 'one', 'tab-nav', NULL, '$.sections[0].components[*].type'))) - 5),
           '.props.navList')
  )) AS published_nav_count,
  JSON_LENGTH(JSON_EXTRACT(p.`page_content`, '$.sections[0].components')) AS component_count
FROM `page_design` p
LEFT JOIN `page_design_version` v ON v.`id` = p.`published_version_id`
WHERE p.`page_type` = '1' AND p.`del_flag` = '0';

-- 自检口径：
--   · draft_nav_count / published_nav_count 应为 8；
--   · component_count 应保持原值（当前种子为 6：轮播/金刚区/轮播/商品/商品/秒杀）。
--     若变小说明脚本误删了楼层，必须回滚重来。
--   · published_nav_count 为 NULL 表示该租户没发布过版本，草稿正确即可。
-- 执行后务必清 page_design_cache:*。
