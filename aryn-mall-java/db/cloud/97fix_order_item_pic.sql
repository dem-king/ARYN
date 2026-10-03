-- 回填订单明细商品图片（Cloud 微服务模式）
-- 目标库：aryn_order（订单明细），关联 aryn_product（商品/规格）
-- 背景：船供共享购物车按成员拆分下单的请求行不携带 picUrl，
--       OrderPriceComputeService.generateOrderItems 曾用请求里的 null
--       覆盖已兜底的 SPU 主图，导致 order_item.pic_url 入库即 NULL：
--         · C 端订单列表/订单详情商品缩略图全部不显示；
--         · 管理端「商城订单」订单信息列商品无图。
--       代码侧已改为「请求快照 → SKU 图 → SPU 主图首张」三级兜底，
--       本脚本负责回填存量明细，并归一化 spu_urls 的字面量 "[]" 脏数据。
-- 特性：可重复执行，不执行 DROP/TRUNCATE，不覆盖已有非空业务数据。
-- 执行：mysql -u root -p < 97fix_order_item_pic.sql（需与 aryn_product 同实例）

-- ============ aryn_product：归一化字面量 "[]" 主图脏数据 ============
-- JsonArrayStringTypeHandler 按逗号切分，"[]" 会被解析成单元素 ["[]"] 参与
-- 前端渲染（当作图片地址加载失败）；置空即视为无主图，与空数组语义一致。

USE `aryn_product`;
SET NAMES utf8mb4;

UPDATE `goods_spu`
SET `spu_urls` = '', `update_time` = NOW()
WHERE `del_flag` = 0
  AND `spu_urls` = '[]';

-- ============ aryn_order：回填为空的商品图片 ============
-- 兜底顺序与代码一致：SKU 图优先，其次 SPU 主图首张（spu_urls 为裸 URL 逗号串）；
-- 明细已非空的不覆盖；商品行无论是否逻辑删除均可回填。

USE `aryn_order`;

UPDATE `order_item` oi
JOIN `aryn_product`.`goods_sku` gs ON gs.`id` = oi.`sku_id`
LEFT JOIN `aryn_product`.`goods_spu` sp ON sp.`id` = gs.`spu_id`
SET oi.`pic_url` = CASE
        WHEN gs.`pic_url` IS NOT NULL AND gs.`pic_url` <> '' THEN gs.`pic_url`
        WHEN sp.`spu_urls` IS NOT NULL AND sp.`spu_urls` <> '' AND sp.`spu_urls` <> '[]'
            THEN TRIM(SUBSTRING_INDEX(sp.`spu_urls`, ',', 1))
        ELSE oi.`pic_url`
    END
WHERE oi.`del_flag` = 0
  AND (oi.`pic_url` IS NULL OR oi.`pic_url` = '' OR oi.`pic_url` = '[]');

-- 验证：SELECT COUNT(*) FROM order_item WHERE del_flag=0 AND (pic_url IS NULL OR pic_url='');

SET FOREIGN_KEY_CHECKS = 1;
