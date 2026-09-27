-- 商品卡片划线原价开关开启（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 特性：可重复执行；不 DROP/TRUNCATE；只翻转装修配置开关，不动任何商品/订单数据。
--
-- 背景（2026-09-24 · 首页商品卡对标小象超市）：
--   小象超市首页商品卡在售价旁展示划线原价（￥3.8 划掉 ￥23.8），
--   用以突出折扣力度。本项目的字段早已具备：
--     · 商品数据：goods_spu.sales_price / original_price（原价 ≥ 售价）
--     · 接口下发：/product/app/goodsspu/page 与 /list/{ids} 均已返回 originalPrice
--     · 管理端配置：装修「商品」组件早有「商品原价」勾选框
--   但 C 端与预览端此前都**没有实现渲染**，导致该开关是一条死链路
--   （能勾、能存、无效果），存量装修数据里它被固化成 false。
--
-- 本次改动（前端已实现渲染，无需改后端）：
--   · 小程序：diy-goods / diy-goods-group / diy-goods-ranking /
--             diy-goods-scroll / diy-goods-waterfall 五个商品楼层
--   · 管理端预览：同名组件同步，保证编辑器所见即实机
--
-- 本脚本做的唯一一件事：把存量装修配置里的 showOriginalPrice 由 false 翻成 true。
--   判定口径（前端统一实现，见 price-display.ts）：
--   **仅当 original_price 严格大于 sales_price 时才划线**，
--   因此原价未填（0）或与原价持平的商品不会划出「￥0」或无效划线。
--   商超/船供种子的原价普遍高于售价（如 3.9 → 4.9、4.9 → 5.9），改动后即刻可见。
--
-- 幂等与安全：
--   · 只匹配 `"showOriginalPrice":false` 的字面量，已为 true 的配置不受影响；
--   · 用 REPLACE 而非重写 JSON，不触碰同一 JSON 里的其它字段；
--   · 已发布快照（page_design_version）同样处理，避免线上快照仍渲染旧效果。
--   · 若运营已在后台手动调整过开关，本脚本不会把 true 改回 false（单向）。
--
-- 注：装修配置存于 page_design.page_content / page_design_version，为 longtext JSON。
--     若运营希望关闭划线价，直接在装修器里取消勾选「商品原价」并重新发布即可。
--
-- 执行：mysql -u root -p aryn_boot < 90goods_card_original_price.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------- 1. 首页/页面装修配置：开启商品原价 --------
UPDATE `page_design`
   SET `page_content` = REPLACE(`page_content`, '"showOriginalPrice":false', '"showOriginalPrice":true'),
       `update_time`  = NOW(),
       `update_by`    = 'system'
 WHERE `page_content` LIKE '%"showOriginalPrice":false%';

-- ---------- 2. 已发布快照：同步开启，避免线上仍渲染旧效果 --------
-- page_design_version 只在存在该表时处理（早期环境可能尚未建表）
SET @version_table_exists = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'page_design_version'
);
SET @version_sql = IF(
  @version_table_exists = 0,
  'SELECT 1',
  'UPDATE `page_design_version`
      SET `page_content` = REPLACE(`page_content`, ''"showOriginalPrice":false'', ''"showOriginalPrice":true'')
    WHERE `page_content` LIKE ''%"showOriginalPrice":false%'''
);
PREPARE version_stmt FROM @version_sql;
EXECUTE version_stmt;
DEALLOCATE PREPARE version_stmt;

-- ---------- 3. 装修模板市场：模板同样开启，保证新页面套用后即带原价 --------
-- 模板内容列是 template_content（不是 page_content）
SET @template_table_exists = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'page_design_template'
);
SET @template_sql = IF(
  @template_table_exists = 0,
  'SELECT 1',
  'UPDATE `page_design_template`
      SET `template_content` = REPLACE(`template_content`, ''"showOriginalPrice":false'', ''"showOriginalPrice":true'')
    WHERE `template_content` LIKE ''%"showOriginalPrice":false%'''
);
PREPARE template_stmt FROM @template_sql;
EXECUTE template_stmt;
DEALLOCATE PREPARE template_stmt;
