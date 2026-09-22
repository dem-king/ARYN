-- 悦航购首页金刚区改为 8 项（Cloud 微服务模式）
--
-- 目标库：aryn_promotion（页面装修库）
-- 特性：幂等；**只替换金刚区那一个组件**，不动轮播/商品/秒杀等其它楼层。
--
-- 背景（2026-09-22）：
--   现有首页金刚区是 tab-nav 的 4 项（全部商品/浏览记录/浏览记录/领券中心），
--   与移动端改造方案 B 版原型的 8 项不一致，且"浏览记录"重复占了 2 格。
--
--   ⚠️ **不含「船供专区」**（2026-09-22 裁决）：
--   与 2026-09-20 决策 D1「船供不是商品分类，是采购场景，C 端不区分展示」冲突
--   ——船供商品统一走商品分类浏览，不再设专区入口。原型第 2 格的「船供专区」
--   由「限时秒杀」补位（该入口在 A 版原型里本来就有，后被补给单挤出）。
--   注意：`ship-supply` 页面**仍然保留**，它是共享购物车的选货模式入口
--   （`?scene=2&sharedCartId=...`），删除的是金刚区的专区入口，不是这个页面。
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
                JSON_OBJECT('title', '限时秒杀', 'url', '',
                  'link', JSON_OBJECT('name', '限时秒杀', 'url', '/pages/promotion/seckill')),
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
  -- 幂等护栏：金刚区的条目里已有指向秒杀页的链接则跳过。
  --
  -- ⚠️ 原护栏写的是 `NOT LIKE '%kk-replenish-%'`，但脚本从未把该串写进 JSON，
  --   属**死代码**：重跑会重新覆盖整个 props，把运营在装修后台的改动静默抹掉。
  --   2026-09-22 用真实 page_design 数据在临时表实测确认（改一项后重跑被还原）。
  --
  -- ⚠️ 也不能拿 title 当判据：「我的补给单」是运营可见文案，改成「我的补给清单」
  --   之类以后护栏就失效，重跑又会覆盖整组（实测已复现）。改用 link.url 判断 ——
  --   URL 是入口指向，运营改标题/换图都不会动它。
  --
  --   另：旧版 76 号（含「船供专区」）的产物不含秒杀链接，因此**不会被本护栏跳过**，
  --   会被下面这条 UPDATE 整体升级为新的 8 项，无需额外的修补脚本。
  AND JSON_SEARCH(`page_content`, 'one', '/pages/promotion/seckill', NULL,
                  '$.sections[0].components[*].props.navList[*].link.url') IS NULL;

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
                JSON_OBJECT('title', '限时秒杀', 'url', '',
                  'link', JSON_OBJECT('name', '限时秒杀', 'url', '/pages/promotion/seckill')),
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
  AND JSON_SEARCH(v.`page_content`, 'one', '/pages/promotion/seckill', NULL,
                  '$.sections[0].components[*].props.navList[*].link.url') IS NULL;

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
