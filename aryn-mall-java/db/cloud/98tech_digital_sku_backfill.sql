-- 家电数码演示商品补齐 SKU 数据（Cloud 微服务 模式）
--
-- 目标库：aryn_product
-- 背景：75ship_and_tech_product_images.sql 插入的 14 个家电数码演示商品（1930 段 SPU）
--       只写了 goods_spu，没有配套的 goods_sku。由此产生三类可见缺陷：
--         1) C 端「共享购物车选货页」价格整列显示 ￥-：列表 SQL 取 sku.sales_price，
--            LEFT JOIN 后 SKU 缺失 → NULL → 前端回落成 "-"；
--         2) 点「加入共享车 / 加入购物车」传的 skuId 为空：列表接口的 sku_id 列
--            为 NULL，加购落库成 sku_id 为空的脏明细；
--         3) 商品详情/下单链路的 selectListByIds 以 goods_sku 为入口（status='0'），
--            无 SKU 行的商品无法进入下单流程。
-- 特性：可重复执行；仅按 1931/1932 前缀清理自身数据，不触碰存量业务数据；
--       逻辑删除字段 del_flag，禁止物理删除存量。
-- 依赖：先执行 cloud/75ship_and_tech_product_images.sql（本脚本按 SPU id 关联，不新建 SPU）。
--
-- 执行：mysql -u root -p aryn_product < 98tech_digital_sku_backfill.sql

USE `aryn_product`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 0. 幂等清理（仅本脚本 1931/1932 前缀） ----------
-- 注意：主键为 19 位（17 位前缀 + 2 位序号），LIKE 前缀必须写足 17 位，
-- 少写会漏删序号 ≥10 的行，重跑时撞主键。
DELETE FROM `goods_sku` WHERE `id` LIKE '19310000000000000%';

-- ===========================================================================
-- 1. 商品规格表（唯一数据源，两张业务表都由它派生，避免多处维护）
--    moq/step_qty 取值 1：家电数码为整件采购，无批量步进约束
--    价格/库存与 goods_spu 保持一致，避免列表价与结算价口径打架
-- ===========================================================================
DROP TEMPORARY TABLE IF EXISTS `tmp_tech_digital_sku`;
CREATE TEMPORARY TABLE `tmp_tech_digital_sku` (
  `seq` int NOT NULL,
  `spu_id` varchar(32) NOT NULL,
  `name` varchar(100) NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `stock` int NOT NULL,
  `weight` decimal(10,2) NOT NULL,
  `purchase_unit` varchar(32) NOT NULL,
  `package_spec` varchar(128) NOT NULL,
  PRIMARY KEY (`seq`)
);

-- 重量为演示用估重（kg），仅用于运费试算，非实测值
INSERT INTO `tmp_tech_digital_sku` (`seq`, `spu_id`, `name`, `price`, `stock`, `weight`, `purchase_unit`, `package_spec`) VALUES
( 1, '1930000000000000001', '小米电视 65英寸 4K超清',   2999.00, 500, 18.00, '台', '1台/箱'),
( 2, '1930000000000000002', '海信电视 55英寸 4K全面屏', 2199.00, 500, 13.50, '台', '1台/箱'),
( 3, '1930000000000000003', '格力1.5匹变频壁挂空调',    2599.00, 500, 10.50, '台', '1台/箱'),
( 4, '1930000000000000004', '美的大1匹冷暖挂机',        1899.00, 500,  9.50, '台', '1台/箱'),
( 5, '1930000000000000005', '海尔十字对开门冰箱',       3999.00, 500, 78.00, '台', '1台/箱'),
( 6, '1930000000000000006', '容声双门冰箱 252L',        1599.00, 500, 52.00, '台', '1台/箱'),
( 7, '1930000000000000007', 'vivo X300 Pro 5G',       4299.00, 500,  0.22, '台', '1台/盒'),
( 8, '1930000000000000008', '小米15 16GB+512GB',       3999.00, 500,  0.22, '台', '1台/盒'),
( 9, '1930000000000000009', '智能手表 运动版',            899.00, 500,  0.06, '块', '1块/盒'),
(10, '1930000000000000010', '小米小爱智能音箱',           249.00, 500,  0.55, '台', '1台/盒'),
(11, '1930000000000000011', '大疆 Mini 4 Pro',          4788.00, 500,  0.25, '台', '1台/盒'),
(12, '1930000000000000012', '大疆 Air 3 无人机',         6988.00, 500,  0.72, '台', '1台/盒'),
(13, '1930000000000000013', '联想小新Pro16',            5499.00, 500,  1.90, '台', '1台/盒'),
(14, '1930000000000000014', 'MacBook Air 13 M3',       8999.00, 500,  1.24, '台', '1台/盒');

-- ===========================================================================
-- 2. SKU 明细（价格/库存/重量与 SPU 对齐；status='0' 表示可售）
--    goods_spu.status='1' 为上架，goods_sku.status='0' 为可售 —— 两者语义相反，勿混用
-- ===========================================================================
INSERT INTO `goods_sku`
  (`id`, `spu_id`, `sales_price`, `original_price`, `cost_price`, `stock`, `weight`, `volume`,
   `create_time`, `update_time`, `del_flag`, `version`, `tenant_id`, `create_by`, `update_by`,
   `specs_json`, `status`, `specs_arr`, `pic_url`)
SELECT CONCAT('19310000000000000', LPAD(t.`seq`, 2, '0')),
       t.`spu_id`,
       t.`price`,
       COALESCE(sp.`original_price`, t.`price`),
       COALESCE(sp.`cost_price`, t.`price`),
       t.`stock`, t.`weight`, NULL,
       NOW(), NULL, '0', 0, '1590229800633634816', 'seed', NULL,
       NULL, '0', NULL, sp.`spu_urls`
FROM `tmp_tech_digital_sku` t
JOIN `goods_spu` sp ON sp.`id` = t.`spu_id` AND sp.`tenant_id` = '1590229800633634816';

DROP TEMPORARY TABLE IF EXISTS `tmp_tech_digital_sku`;

-- 验证：
-- SELECT s.name, k.sales_price, k.stock FROM goods_spu s JOIN goods_sku k ON k.spu_id = s.id
--   WHERE s.id LIKE '1930000000000000%' ORDER BY s.id;                        -- 期望 14 行非空价格

SET FOREIGN_KEY_CHECKS = 1;
