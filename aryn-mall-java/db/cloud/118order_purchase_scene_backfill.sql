-- ============================================================================
-- 订单购买场景口径修复：存量空场景一次性回填（Cloud 微服务模式）
-- 目标库：aryn_order（order_info / order_item / delivery_task 三表同库）
-- 背景：购买场景是订单固有属性（1 海员个人购买 / 2 船供采购），但历史实现只在
--       公司港口/船舶内部配送（delivery_way=4）时才由 C 端携带该字段，走商城配送（3）
--       与普通快递（1）的订单 purchase_scene 恒为空，导致管理端订单列表「购买场景」
--       显示「—」、导出留空、按场景筛选也筛不出这些单。
-- 口径：代码侧已修正为「下单未声明场景时归一为 1 个人购买」。本脚本回填存量空值：
--         - order_info：delivery_way <> '4' 的空场景 → '1'；
--           delivery_way = '4' 且场景为空的行不猜（内部配送既可能是船供采购、也可能是
--           个人到船订单，船供采购必须显式声明），保留空值由管理端按个人购买展示与筛选；
--         - order_item / delivery_task：场景跟随所属订单，订单为 1 的同步回填为 1，
--           避免明细与订单、配送任务三处口径不一致。
-- 特性：可重复执行（回填后不再满足 WHERE）；不执行 DROP/TRUNCATE；
--       只写 purchase_scene 与 update_time，不触碰金额、状态等业务列。
-- 执行：mysql -u root -p aryn_order < 118order_purchase_scene_backfill.sql
-- 与 db/boot/117order_purchase_scene_backfill.sql 内容保持一致，仅库名不同。
-- ============================================================================

USE `aryn_order`;

SET NAMES utf8mb4;

-- ============ order_info：非内部配送的存量空场景回填为个人购买 ============
-- 仅回填 delivery_way <> '4'：内部配送订单的场景缺失属于「未声明」，语义上无法
-- 从配送方式反推（船供采购与个人到船都走内部配送），须保持空值而不是猜成个人购买。
UPDATE `order_info`
SET `purchase_scene` = '1',
    `update_time` = NOW()
WHERE `del_flag` = '0'
  AND `delivery_way` <> '4'
  AND (`purchase_scene` IS NULL OR `purchase_scene` = '');

-- ============ order_item：明细场景跟随所属订单 ============
UPDATE `order_item` oi
JOIN `order_info` o ON o.`id` = oi.`order_id`
SET oi.`purchase_scene` = o.`purchase_scene`
WHERE oi.`del_flag` = '0'
  AND o.`del_flag` = '0'
  AND (oi.`purchase_scene` IS NULL OR oi.`purchase_scene` = '')
  AND o.`purchase_scene` IS NOT NULL;

-- ============ delivery_task：配送任务场景跟随所属订单 ============
UPDATE `delivery_task` t
JOIN `order_info` o ON o.`id` = t.`order_id`
SET t.`purchase_scene` = o.`purchase_scene`,
    t.`update_time` = NOW()
WHERE t.`del_flag` = '0'
  AND o.`del_flag` = '0'
  AND (t.`purchase_scene` IS NULL OR t.`purchase_scene` = '')
  AND o.`purchase_scene` IS NOT NULL;

-- 自检 1：非内部配送订单不应再有空场景（期望 0 行）
SELECT COUNT(*) AS `non_internal_blank_scene`
FROM `order_info`
WHERE `del_flag` = '0'
  AND `delivery_way` <> '4'
  AND (`purchase_scene` IS NULL OR `purchase_scene` = '');

-- 自检 2：内部配送订单中场景仍为空的行数（属预期存量，管理端按个人购买展示）
SELECT COUNT(*) AS `internal_blank_scene`
FROM `order_info`
WHERE `del_flag` = '0'
  AND `delivery_way` = '4'
  AND (`purchase_scene` IS NULL OR `purchase_scene` = '');

-- 自检 3：场景分布（1 个人购买 / 2 船供采购 / 空）
SELECT IFNULL(NULLIF(`purchase_scene`, ''), 'NULL') AS `scene`, COUNT(*) AS `cnt`
FROM `order_info`
WHERE `del_flag` = '0'
GROUP BY `scene`;
