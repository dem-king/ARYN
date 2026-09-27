-- 悦航购营销装修组件：拼团改名 + 秒杀/折扣楼层（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：幂等；可重复执行；不执行 DROP/TRUNCATE；不覆盖运营自定义文案。
--
-- 背景（2026-09-24）：
--   营销管理有拼团/秒杀/折扣/优惠券四个模块，但装修组件库此前只认得出优惠券：
--     · 拼团：其实**已有**数据驱动组件，但类型叫 `limited-activity`、标签叫「限时活动」，
--       数据源指向 group_buy_activity，运营按名字根本找不到 → 三端改名「拼团」；
--     · 秒杀：无组件，只能靠「营销入口」写静态路径跳 /pages/promotion/seckill；
--     · 折扣：无组件，同上。
--   本轮新增 `seckill`、`discount` 两个数据驱动组件（三端已登记）。
--
--   客户端读取的是**已发布版本快照**（page_design.published_version_id →
--   page_design_version），不是草稿（page_design.page_content）。因此存量页面两处都要迁移，
--   否则已发布页仍然渲染旧标题「限时活动」。
--
--   ⚠️ 本脚本**只改标题文案**，不新增/删除任何组件：
--     秒杀与折扣楼层需要运营按自己诉求摆放（放哪个页面、放在第几层），
--     自动插入会打乱既有页面结构，属于越权改版。运营在装修后台拖入即可。
--
--   ⚠️ 幂等护栏：仅当 props.title 恰为旧默认值「限时活动」时才改写。
--     运营若已改成「拼团秒杀」「今日必拼」等自定义文案，**不会被覆盖**。
--     同时要求组件 type 为 limited-activity，避免误伤同名字段的其它组件。
--
--   ⚠️ 路径写法：JSON_SEARCH 指定 '$....props.title' 作为搜索域时，
--     返回的路径**已经指向 title 本身**（例如 $.sections[0].components[1].props.title），
--     因此直接用 JSON_SET(doc, 该路径, '拼团') 即可，**不要**再截掉结尾的 '.title' ——
--     截断后路径变成 ...props，会把整个 props 对象替换成字符串，组件配置全毁。
--     （已在 MySQL 8.0 实机验证：改写后 count / showCountdown / type 等兄弟字段保持不变。）
--
-- 执行：mysql -u root -p aryn_boot < 88marketing_seckill_discount_components.sql
-- 随后：清客户端装修缓存（键含 versionId，TTL 24h），否则最长 24 小时看不到新标题：
--   redis-cli --scan --pattern 'page_design_cache:*' | xargs -r redis-cli del

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 草稿（page_design.page_content）
--    组件可能挂在任意区块（sections[*].components[*]），故用通配定位。
-- ---------------------------------------------------------------------------
UPDATE `page_design`
SET `page_content` = JSON_SET(
      `page_content`,
      JSON_UNQUOTE(JSON_SEARCH(
        `page_content`, 'one', '限时活动', NULL, '$.sections[*].components[*].props.title')),
      '拼团')
WHERE `del_flag` = '0'
  AND JSON_SEARCH(`page_content`, 'one', 'limited-activity', NULL, '$.sections[*].components[*].type') IS NOT NULL
  AND JSON_SEARCH(`page_content`, 'one', '限时活动', NULL, '$.sections[*].components[*].props.title') IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. 已发布版本快照（page_design_version.page_content）
--    同上口径；只改旧默认文案，运营自定义文案保持不变。
-- ---------------------------------------------------------------------------
UPDATE `page_design_version`
SET `page_content` = JSON_SET(
      `page_content`,
      JSON_UNQUOTE(JSON_SEARCH(
        `page_content`, 'one', '限时活动', NULL, '$.sections[*].components[*].props.title')),
      '拼团')
WHERE `del_flag` = '0'
  AND JSON_SEARCH(`page_content`, 'one', 'limited-activity', NULL, '$.sections[*].components[*].type') IS NOT NULL
  AND JSON_SEARCH(`page_content`, 'one', '限时活动', NULL, '$.sections[*].components[*].props.title') IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. 自检：两项均应返回 0
-- ---------------------------------------------------------------------------
SELECT 'legacy_title_left_in_draft' AS check_name, COUNT(*) AS remaining, 0 AS expected
FROM `page_design`
WHERE `del_flag` = '0'
  AND JSON_SEARCH(`page_content`, 'one', '限时活动', NULL, '$.sections[*].components[*].props.title') IS NOT NULL
UNION ALL
SELECT 'legacy_title_left_in_version', COUNT(*), 0
FROM `page_design_version`
WHERE `del_flag` = '0'
  AND JSON_SEARCH(`page_content`, 'one', '限时活动', NULL, '$.sections[*].components[*].props.title') IS NOT NULL;
