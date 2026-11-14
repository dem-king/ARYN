-- 新人专享首单加品：蛋奶肉果菜 8 款（Cloud 微服务模式）
-- 目标库：aryn_promotion
-- 背景：原活动只有 8 个 1 元级零食 SKU，撑不起「新客首单」；本次回填 8 个高频刚需 SKU 并排到列表最前，
--       价格均低于当前售价（也低于会员价），并保持 rules.skus 与 scope_value 一一对应。
-- 特性：可重复执行；以「鸡蛋 SKU 是否已在规则里」为幂等条件，不覆盖运营后续新增的其它 SKU。
-- 关联：boot 模式同名增量 223newbie_staples_backfill_incremental.sql 内容一致。

USE `aryn_promotion`;
SET NAMES utf8mb4;

UPDATE `promotion_activity`
SET `rules` = '{"limitPerUser":1,"longTerm":true,"registerWithinDays":30,"skus":[{"skuId":"9750000000000000021","spuId":"9740000000000000021","price":5.9,"skuName":"单规格","spuName":"鲜鸡蛋 10枚/盒"},{"skuId":"9750000000000000042","spuId":"9740000000000000042","price":6.9,"skuName":"单规格","spuName":"巴氏鲜牛奶 1L/瓶"},{"skuId":"9750000000000000017","spuId":"9740000000000000017","price":9.9,"skuName":"单规格","spuName":"猪五花肉片 300g/盒"},{"skuId":"9750000000000000001","spuId":"9740000000000000001","price":4.9,"skuName":"单规格","spuName":"烟台红富士苹果 4个装 约1.2kg"},{"skuId":"9750000000000000005","spuId":"9740000000000000005","price":2.9,"skuName":"单规格","spuName":"国产香蕉 约1kg/把"},{"skuId":"10740000000000000206","spuId":"9740000000000000206","price":1.9,"skuName":"单规格","spuName":"上海青小青菜 约300g/份"},{"skuId":"9750000000000000010","spuId":"9740000000000000010","price":1.9,"skuName":"单规格","spuName":"黄心土豆 约500g/份"},{"skuId":"9750000000000000145","spuId":"9740000000000000145","price":6.9,"skuName":"单规格","spuName":"全麦面包 400g/袋"},{"price":1,"skuId":"10740000000000000346","skuName":"单规格","spuName":"优乐美袋装奶茶22g"},{"price":1,"skuId":"10740000000000000308","skuName":"单规格","spuName":"黄豆芽500g"},{"price":1.9,"skuId":"10740000000000000309","skuName":"单规格","spuName":"内酯豆腐400g"},{"price":3.9,"skuId":"10740000000000000373","skuName":"单规格","spuName":"冰糖银耳羹杯装15g"},{"price":2.9,"skuId":"10740000000000000315","skuName":"单规格","spuName":"达利园八宝粥360g"},{"price":9.9,"skuId":"10740000000000000369","skuName":"单规格","spuName":"五芳斋绿豆糕100g"},{"price":9.9,"skuId":"10740000000000000322","skuName":"单规格","spuName":"奶黄包12只装"},{"price":3.9,"skuId":"10740000000000000366","skuName":"单规格","spuName":"农心鲜虾条80g"}]}',
    `scope_value` = '9750000000000000021,9750000000000000042,9750000000000000017,9750000000000000001,9750000000000000005,10740000000000000206,9750000000000000010,9750000000000000145,10740000000000000346,10740000000000000308,10740000000000000309,10740000000000000373,10740000000000000315,10740000000000000369,10740000000000000322,10740000000000000366',
    `remark` = '航海双11新人礼包：16 款高感知 SKU 一口价（蛋奶肉果菜在前），每人每款限 1 件',
    `end_time` = '2099-12-31 23:59:59',
    `update_time` = NOW()
WHERE `id` = '3170000000000000501'
  AND `activity_type` = '9'
  AND `del_flag` = '0'
  AND `rules` NOT LIKE '%9750000000000000021%';

SELECT `id`, JSON_LENGTH(`rules`, '$.skus') AS `sku_count`
FROM `promotion_activity`
WHERE `id` = '3170000000000000501';

SET FOREIGN_KEY_CHECKS = 1;
