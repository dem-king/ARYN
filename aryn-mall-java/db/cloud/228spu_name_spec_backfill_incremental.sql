-- 商品名称规格化回填（Cloud 微服务模式）
--
-- 目标库：aryn_product（商品）+ aryn_order（购物车快照）
-- 特性：可重复执行（每条 UPDATE 带 name=旧名 条件，二次执行 0 行变更；不会误改
--       名称已被人工调整过的商品）；不 DROP/TRUNCATE；不覆盖价格等业务数据。
--
-- 背景（2026-11-08 · 商品规格调研回填）：
--   974 段前 302 个商品（9740000000000000001 ~ 9740000000000000302）为早期种子，
--   名称普遍不带规格（如「烟台红富士苹果」「维达抽纸」「可口可乐」），用户无法
--   判断买的是多少量；而后批 974 段（303~394）与 977 段（97700000000000000xxx）
--   种子已带规格（如「泰国山竹1kg」「夏黑无籽葡萄 约500g/盒」「挂耳咖啡 10片装」）。
--   本脚本对齐后批种子风格，为名称追加规格后缀（「名称 空格 规格」），共 275 条；
--   价格不变（规格量级按既有售价设计，与价格自洽）。
--
--   另有 27 个商品刻意不改：名称已含规格语义或单件无歧义——
--   李宁7号篮球 / 羽毛球拍双拍 / 计数跳绳 / 乒乓球拍双拍 / 5号排球 /
--   真无线蓝牙耳机 / 充电宝20000mAh / 手机支架 / 护眼台灯 / 插线板6位 /
--   手持小风扇 / 无线鼠标 / 春秋工作服套装 / ABS安全帽 / 钢头劳保鞋 /
--   单人雨衣 / 防护护目镜 / 绝缘手套 / 双面擦窗器 / 围棋棋盘套装 /
--   耳机收纳包 / 硅胶手机壳 / 铝合金平板支架 / 反光安全背心 / 五点式安全带 /
--   连体防尘服 / 电工绝缘鞋。
--
--   随带同步刷新购物车在途行的 spu_name 快照（仅名称不一致的在途行）；
--   订单明细 order_item 是下单时点快照，按惯例不动。
--
-- 执行：mysql -u root -p < 228spu_name_spec_backfill_incremental.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 1. 商品名称追加规格后缀（275 条，幂等）
-- ===========================================================================
UPDATE `aryn_product`.`goods_spu` SET `name` = '烟台红富士苹果 4个装 约1.2kg' WHERE `id` = '9740000000000000001' AND `name` = '烟台红富士苹果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '海南贵妃芒 约750g/盒' WHERE `id` = '9740000000000000002' AND `name` = '海南贵妃芒' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '麒麟西瓜 1个 约4kg' WHERE `id` = '9740000000000000003' AND `name` = '麒麟西瓜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '阳光玫瑰葡萄 约500g/串' WHERE `id` = '9740000000000000004' AND `name` = '阳光玫瑰葡萄' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '国产香蕉 约1kg/把' WHERE `id` = '9740000000000000005' AND `name` = '国产香蕉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '赣南脐橙 5个装 约1.2kg' WHERE `id` = '9740000000000000006' AND `name` = '赣南脐橙' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '丹东99草莓 约300g/盒' WHERE `id` = '9740000000000000007' AND `name` = '丹东99草莓' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '泰国椰青 1个 约900g' WHERE `id` = '9740000000000000008' AND `name` = '泰国椰青' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '油麦菜 约350g/份' WHERE `id` = '9740000000000000009' AND `name` = '油麦菜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '黄心土豆 约500g/份' WHERE `id` = '9740000000000000010' AND `name` = '黄心土豆' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '普罗旺斯西红柿 4个装 约600g' WHERE `id` = '9740000000000000011' AND `name` = '普罗旺斯西红柿' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '本地黄瓜 2根装 约500g' WHERE `id` = '9740000000000000012' AND `name` = '本地黄瓜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '小油菜 约300g/份' WHERE `id` = '9740000000000000013' AND `name` = '小油菜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜香菇 约200g/盒' WHERE `id` = '9740000000000000014' AND `name` = '新鲜香菇' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '嫩豆腐 400g/盒' WHERE `id` = '9740000000000000015' AND `name` = '嫩豆腐' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '胡萝卜 约500g/份' WHERE `id` = '9740000000000000016' AND `name` = '胡萝卜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '猪五花肉片 300g/盒' WHERE `id` = '9740000000000000017' AND `name` = '猪五花肉片' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '原切牛腩块 500g/盒' WHERE `id` = '9740000000000000018' AND `name` = '原切牛腩块' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '内蒙羔羊肉卷 250g/袋' WHERE `id` = '9740000000000000019' AND `name` = '内蒙羔羊肉卷' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '三黄鸡 1只 约1kg' WHERE `id` = '9740000000000000020' AND `name` = '三黄鸡' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '鲜鸡蛋 10枚/盒' WHERE `id` = '9740000000000000021' AND `name` = '鲜鸡蛋' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冷冻青虾仁 250g/袋' WHERE `id` = '9740000000000000022' AND `name` = '冷冻青虾仁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冰鲜三文鱼柳 200g/份' WHERE `id` = '9740000000000000023' AND `name` = '冰鲜三文鱼柳' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '鲜活花蛤 约500g/份' WHERE `id` = '9740000000000000024' AND `name` = '鲜活花蛤' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '老面馒头 4个装 约320g' WHERE `id` = '9740000000000000025' AND `name` = '老面馒头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '鲜肉包 4个装 约360g' WHERE `id` = '9740000000000000026' AND `name` = '鲜肉包' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '手工挂面 500g/把' WHERE `id` = '9740000000000000027' AND `name` = '手工挂面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '鲜虾云吞 12只装 约240g' WHERE `id` = '9740000000000000028' AND `name` = '鲜虾云吞' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '葱油饼 5片装 约450g' WHERE `id` = '9740000000000000029' AND `name` = '葱油饼' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '鲜肉水饺 500g/袋' WHERE `id` = '9740000000000000030' AND `name` = '鲜肉水饺' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '花卷 4个装 约320g' WHERE `id` = '9740000000000000031' AND `name` = '花卷' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '手擀面 400g/份' WHERE `id` = '9740000000000000032' AND `name` = '手擀面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冷冻粗薯条 400g/袋' WHERE `id` = '9740000000000000033' AND `name` = '冷冻粗薯条' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '牛肉卷 250g/袋' WHERE `id` = '9740000000000000034' AND `name` = '牛肉卷' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '香煎鸡排 400g/袋' WHERE `id` = '9740000000000000035' AND `name` = '香煎鸡排' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '速冻水饺 450g/袋' WHERE `id` = '9740000000000000036' AND `name` = '速冻水饺' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '鱼豆腐 250g/袋' WHERE `id` = '9740000000000000037' AND `name` = '鱼豆腐' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冷冻虾仁 250g/袋' WHERE `id` = '9740000000000000038' AND `name` = '冷冻虾仁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '春卷 300g/袋' WHERE `id` = '9740000000000000039' AND `name` = '春卷' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '台式烤肠 250g/袋 约5根' WHERE `id` = '9740000000000000040' AND `name` = '台式烤肠' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '晴王阳光玫瑰 约500g/串' WHERE `id` = '9740000000000000041' AND `name` = '晴王阳光玫瑰' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '巴氏鲜牛奶 1L/瓶' WHERE `id` = '9740000000000000042' AND `name` = '巴氏鲜牛奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '牛油果 2个装 约300g' WHERE `id` = '9740000000000000043' AND `name` = '牛油果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '有机西兰花 约400g/颗' WHERE `id` = '9740000000000000044' AND `name` = '有机西兰花' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冷萃咖啡 250ml/瓶' WHERE `id` = '9740000000000000045' AND `name` = '冷萃咖啡' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '全麦贝果 2个装 约220g' WHERE `id` = '9740000000000000046' AND `name` = '全麦贝果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '手打鲜虾滑 150g/袋' WHERE `id` = '9740000000000000047' AND `name` = '手打鲜虾滑' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '突尼斯软籽石榴 3个装 约600g' WHERE `id` = '9740000000000000048' AND `name` = '突尼斯软籽石榴' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '农夫山泉天然水 550ml×24瓶' WHERE `id` = '9740000000000000049' AND `name` = '农夫山泉天然水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '怡宝纯净水 555ml×24瓶' WHERE `id` = '9740000000000000050' AND `name` = '怡宝纯净水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '饮用纯净水 1.5L×12瓶' WHERE `id` = '9740000000000000051' AND `name` = '饮用纯净水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '维达抽纸 3层100抽×12包' WHERE `id` = '9740000000000000052' AND `name` = '维达抽纸' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '心相印卷纸 4层140g×10卷' WHERE `id` = '9740000000000000053' AND `name` = '心相印卷纸' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '厨房纸巾 2卷装' WHERE `id` = '9740000000000000054' AND `name` = '厨房纸巾' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '婴儿湿巾 80抽/包' WHERE `id` = '9740000000000000055' AND `name` = '婴儿湿巾' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '本色卫生纸 4层180g×8卷' WHERE `id` = '9740000000000000056' AND `name` = '本色卫生纸' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '伊利纯牛奶 250ml×24盒' WHERE `id` = '9740000000000000057' AND `name` = '伊利纯牛奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '特仑苏纯牛奶 250ml×24盒' WHERE `id` = '9740000000000000058' AND `name` = '特仑苏纯牛奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '蒙牛纯牛奶 250ml×24盒' WHERE `id` = '9740000000000000059' AND `name` = '蒙牛纯牛奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '简爱原味酸奶 1kg/桶' WHERE `id` = '9740000000000000060' AND `name` = '简爱原味酸奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '农夫NFC橙汁 300ml×4瓶' WHERE `id` = '9740000000000000061' AND `name` = '农夫NFC橙汁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '安慕希酸奶 205g×16盒' WHERE `id` = '9740000000000000062' AND `name` = '安慕希酸奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '椰树椰汁 245ml×24罐' WHERE `id` = '9740000000000000063' AND `name` = '椰树椰汁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '鲜榨苹果汁 1L/瓶' WHERE `id` = '9740000000000000064' AND `name` = '鲜榨苹果汁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '可口可乐 500ml×24瓶' WHERE `id` = '9740000000000000065' AND `name` = '可口可乐' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '雪碧 500ml×24瓶' WHERE `id` = '9740000000000000066' AND `name` = '雪碧' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '百事可乐 500ml×24瓶' WHERE `id` = '9740000000000000067' AND `name` = '百事可乐' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '芬达橙味 500ml×24瓶' WHERE `id` = '9740000000000000068' AND `name` = '芬达橙味' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '元气森林气泡水 480ml×12瓶' WHERE `id` = '9740000000000000069' AND `name` = '元气森林气泡水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '屈臣氏苏打水 330ml×24瓶' WHERE `id` = '9740000000000000070' AND `name` = '屈臣氏苏打水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '可口可乐零度 500ml×24瓶' WHERE `id` = '9740000000000000071' AND `name` = '可口可乐零度' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '美年达橙味 500ml×24瓶' WHERE `id` = '9740000000000000072' AND `name` = '美年达橙味' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '红牛维生素功能饮料 250ml×12罐' WHERE `id` = '9740000000000000073' AND `name` = '红牛维生素功能饮料' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '东鹏特饮 500ml×12瓶' WHERE `id` = '9740000000000000074' AND `name` = '东鹏特饮' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '乐虎功能饮料 380ml×12罐' WHERE `id` = '9740000000000000075' AND `name` = '乐虎功能饮料' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '星巴克即饮咖啡 281ml×3瓶' WHERE `id` = '9740000000000000076' AND `name` = '星巴克即饮咖啡' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '雀巢即饮咖啡 268ml×6瓶' WHERE `id` = '9740000000000000077' AND `name` = '雀巢即饮咖啡' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '脉动维生素饮料 600ml×15瓶' WHERE `id` = '9740000000000000078' AND `name` = '脉动维生素饮料' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '黑咖啡饮料 250ml/瓶' WHERE `id` = '9740000000000000079' AND `name` = '黑咖啡饮料' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '维生素功能饮料 250ml×12罐' WHERE `id` = '9740000000000000080' AND `name` = '维生素功能饮料' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '青岛啤酒经典 500ml×12罐' WHERE `id` = '9740000000000000081' AND `name` = '青岛啤酒经典' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '雪花啤酒勇闯天涯 500ml×12罐' WHERE `id` = '9740000000000000082' AND `name` = '雪花啤酒勇闯天涯' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '百威啤酒 500ml×18罐' WHERE `id` = '9740000000000000083' AND `name` = '百威啤酒' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '茅台迎宾酒 500ml/瓶 53度' WHERE `id` = '9740000000000000084' AND `name` = '茅台迎宾酒' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '五粮液浓香型 500ml/瓶 52度' WHERE `id` = '9740000000000000085' AND `name` = '五粮液浓香型' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '张裕干红葡萄酒 750ml/瓶' WHERE `id` = '9740000000000000086' AND `name` = '张裕干红葡萄酒' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '绍兴花雕酒 500ml/瓶' WHERE `id` = '9740000000000000087' AND `name` = '绍兴花雕酒' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '江小白 100ml×12瓶' WHERE `id` = '9740000000000000088' AND `name` = '江小白' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '三得利乌龙茶 500ml×24瓶' WHERE `id` = '9740000000000000089' AND `name` = '三得利乌龙茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '东方树叶绿茶 500ml×24瓶' WHERE `id` = '9740000000000000090' AND `name` = '东方树叶绿茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '康师傅冰红茶 500ml×24瓶' WHERE `id` = '9740000000000000091' AND `name` = '康师傅冰红茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '统一绿茶 500ml×24瓶' WHERE `id` = '9740000000000000092' AND `name` = '统一绿茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '西湖龙井 100g/罐' WHERE `id` = '9740000000000000093' AND `name` = '西湖龙井' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '安溪铁观音 100g/罐' WHERE `id` = '9740000000000000094' AND `name` = '安溪铁观音' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '茉莉花茶 200g/袋' WHERE `id` = '9740000000000000095' AND `name` = '茉莉花茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '立顿红茶 2g×100包' WHERE `id` = '9740000000000000096' AND `name` = '立顿红茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '香飘飘奶茶 80g×12杯' WHERE `id` = '9740000000000000097' AND `name` = '香飘飘奶茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '桂格即食燕麦片 700g/袋' WHERE `id` = '9740000000000000098' AND `name` = '桂格即食燕麦片' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '阿华田可可粉 400g/罐' WHERE `id` = '9740000000000000099' AND `name` = '阿华田可可粉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '南方黑芝麻糊 40g×10袋' WHERE `id` = '9740000000000000100' AND `name` = '南方黑芝麻糊' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '雀巢咖啡1+2 13g×50条' WHERE `id` = '9740000000000000101' AND `name` = '雀巢咖啡1+2' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '豆奶粉 560g/袋' WHERE `id` = '9740000000000000102' AND `name` = '豆奶粉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '洋槐蜂蜜 500g/瓶' WHERE `id` = '9740000000000000103' AND `name` = '洋槐蜂蜜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '山药葛根粉 300g/罐' WHERE `id` = '9740000000000000104' AND `name` = '山药葛根粉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '火锅肥牛卷 250g/盒' WHERE `id` = '9740000000000000105' AND `name` = '火锅肥牛卷' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '羔羊肉卷 250g/盒' WHERE `id` = '9740000000000000106' AND `name` = '羔羊肉卷' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '火锅丸子组合 500g/袋' WHERE `id` = '9740000000000000107' AND `name` = '火锅丸子组合' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '海底捞番茄火锅底料 200g/袋' WHERE `id` = '9740000000000000108' AND `name` = '海底捞番茄火锅底料' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '腌制羊肉串 400g/袋 约10串' WHERE `id` = '9740000000000000109' AND `name` = '腌制羊肉串' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '甜玉米 2根装 约700g' WHERE `id` = '9740000000000000110' AND `name` = '甜玉米' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '土豆片 约300g/份' WHERE `id` = '9740000000000000111' AND `name` = '土豆片' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '火锅蘸料麻酱 200g/盒' WHERE `id` = '9740000000000000112' AND `name` = '火锅蘸料麻酱' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '五香酱牛肉 200g/袋' WHERE `id` = '9740000000000000113' AND `name` = '五香酱牛肉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '南京盐水鸭 半只 约500g' WHERE `id` = '9740000000000000114' AND `name` = '南京盐水鸭' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '德州扒鸡 1只 约550g' WHERE `id` = '9740000000000000115' AND `name` = '德州扒鸡' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '卤鸡爪 200g/袋' WHERE `id` = '9740000000000000116' AND `name` = '卤鸡爪' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '涪陵榨菜 80g×10袋' WHERE `id` = '9740000000000000117' AND `name` = '涪陵榨菜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '酸甜脆萝卜 300g/袋' WHERE `id` = '9740000000000000118' AND `name` = '酸甜脆萝卜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '松花皮蛋 6枚装' WHERE `id` = '9740000000000000119' AND `name` = '松花皮蛋' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '午餐肉罐头 340g/罐' WHERE `id` = '9740000000000000120' AND `name` = '午餐肉罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '砀山黄桃罐头 425g/罐' WHERE `id` = '9740000000000000121' AND `name` = '砀山黄桃罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '糖水橘子罐头 425g/罐' WHERE `id` = '9740000000000000122' AND `name` = '糖水橘子罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '菠萝罐头 425g/罐' WHERE `id` = '9740000000000000123' AND `name` = '菠萝罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '山楂罐头 425g/罐' WHERE `id` = '9740000000000000124' AND `name` = '山楂罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '午餐肉罐头 198g/罐' WHERE `id` = '9740000000000000125' AND `name` = '午餐肉罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '油浸金枪鱼罐头 150g/罐' WHERE `id` = '9740000000000000126' AND `name` = '油浸金枪鱼罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '甜玉米粒罐头 340g/罐' WHERE `id` = '9740000000000000127' AND `name` = '甜玉米粒罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '豆豉鲮鱼罐头 227g/罐' WHERE `id` = '9740000000000000128' AND `name` = '豆豉鲮鱼罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '洽洽香瓜子 260g/袋' WHERE `id` = '9740000000000000129' AND `name` = '洽洽香瓜子' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '铁锅炒花生 250g/袋' WHERE `id` = '9740000000000000130' AND `name` = '铁锅炒花生' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '酒鬼花生 150g/袋' WHERE `id` = '9740000000000000131' AND `name` = '酒鬼花生' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '三只松鼠碧根果 500g/袋' WHERE `id` = '9740000000000000132' AND `name` = '三只松鼠碧根果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '每日坚果 25g×30包' WHERE `id` = '9740000000000000133' AND `name` = '每日坚果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '百草味夏威夷果 200g/袋' WHERE `id` = '9740000000000000134' AND `name` = '百草味夏威夷果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '开心果 250g/袋' WHERE `id` = '9740000000000000135' AND `name` = '开心果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '巴旦木仁 250g/袋' WHERE `id` = '9740000000000000136' AND `name` = '巴旦木仁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '乐事薯片 70g×3包' WHERE `id` = '9740000000000000137' AND `name` = '乐事薯片' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '卫龙大面筋辣条 106g×3包' WHERE `id` = '9740000000000000138' AND `name` = '卫龙大面筋辣条' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '三只松鼠肉脯 200g/袋' WHERE `id` = '9740000000000000139' AND `name` = '三只松鼠肉脯' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '旺旺仙贝 400g/袋' WHERE `id` = '9740000000000000140' AND `name` = '旺旺仙贝' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '德芙巧克力 252g/盒' WHERE `id` = '9740000000000000141' AND `name` = '德芙巧克力' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '徐福记酥心糖 350g/袋' WHERE `id` = '9740000000000000142' AND `name` = '徐福记酥心糖' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '芒果干 200g/袋' WHERE `id` = '9740000000000000143' AND `name` = '芒果干' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '奥利奥夹心饼干 97g×3盒' WHERE `id` = '9740000000000000144' AND `name` = '奥利奥夹心饼干' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '全麦面包 400g/袋' WHERE `id` = '9740000000000000145' AND `name` = '全麦面包' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '夹心吐司面包 320g/袋' WHERE `id` = '9740000000000000146' AND `name` = '夹心吐司面包' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '麻薯面包 240g/袋' WHERE `id` = '9740000000000000147' AND `name` = '麻薯面包' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '肉松吐司 300g/袋' WHERE `id` = '9740000000000000148' AND `name` = '肉松吐司' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '黄油曲奇饼干 454g/盒' WHERE `id` = '9740000000000000149' AND `name` = '黄油曲奇饼干' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '蛋黄派 480g/袋' WHERE `id` = '9740000000000000150' AND `name` = '蛋黄派' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '蒸蛋糕 400g/袋' WHERE `id` = '9740000000000000151' AND `name` = '蒸蛋糕' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '苏打饼干 400g/盒' WHERE `id` = '9740000000000000152' AND `name` = '苏打饼干' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '康师傅红烧牛肉面 105g×5包' WHERE `id` = '9740000000000000153' AND `name` = '康师傅红烧牛肉面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '统一老坛酸菜面 120g×5包' WHERE `id` = '9740000000000000154' AND `name` = '统一老坛酸菜面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '桶装方便面 128g×10桶' WHERE `id` = '9740000000000000155' AND `name` = '桶装方便面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '自热小火锅 400g/盒' WHERE `id` = '9740000000000000156' AND `name` = '自热小火锅' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '自热米饭 440g/盒' WHERE `id` = '9740000000000000157' AND `name` = '自热米饭' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '柳州螺蛳粉 335g×3袋' WHERE `id` = '9740000000000000158' AND `name` = '柳州螺蛳粉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '兰州牛肉面 220g/袋' WHERE `id` = '9740000000000000159' AND `name` = '兰州牛肉面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '酸辣粉 135g×5袋' WHERE `id` = '9740000000000000160' AND `name` = '酸辣粉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '海飞丝去屑洗发水 500ml/瓶' WHERE `id` = '9740000000000000161' AND `name` = '海飞丝去屑洗发水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '舒肤佳沐浴露 400ml/瓶' WHERE `id` = '9740000000000000162' AND `name` = '舒肤佳沐浴露' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '氨基酸洗面奶 100g/支' WHERE `id` = '9740000000000000163' AND `name` = '氨基酸洗面奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '洋甘菊护手霜 50g×3支' WHERE `id` = '9740000000000000164' AND `name` = '洋甘菊护手霜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '云南白药牙膏 100g/支' WHERE `id` = '9740000000000000165' AND `name` = '云南白药牙膏' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '高露洁牙膏 120g/支' WHERE `id` = '9740000000000000166' AND `name` = '高露洁牙膏' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '卫生巾日用 8片/包' WHERE `id` = '9740000000000000167' AND `name` = '卫生巾日用' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '纸巾抽纸整箱 3层100抽×24包' WHERE `id` = '9740000000000000168' AND `name` = '纸巾抽纸整箱' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '蓝月亮洗衣液 1kg/瓶' WHERE `id` = '9740000000000000169' AND `name` = '蓝月亮洗衣液' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '立白洗洁精 500g/瓶' WHERE `id` = '9740000000000000170' AND `name` = '立白洗洁精' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '雕牌洗洁精 500g/瓶' WHERE `id` = '9740000000000000171' AND `name` = '雕牌洗洁精' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '洗手液 500ml/瓶' WHERE `id` = '9740000000000000172' AND `name` = '洗手液' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '加厚垃圾袋 30只×5卷' WHERE `id` = '9740000000000000173' AND `name` = '加厚垃圾袋' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = 'PE保鲜袋 200只/卷' WHERE `id` = '9740000000000000174' AND `name` = 'PE保鲜袋' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '旋转拖把 1套 带桶' WHERE `id` = '9740000000000000175' AND `name` = '旋转拖把' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '防滑衣架 10只装' WHERE `id` = '9740000000000000176' AND `name` = '防滑衣架' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '中性笔黑色 0.5mm 12支/盒' WHERE `id` = '9740000000000000177' AND `name` = '中性笔黑色' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = 'B5笔记本 40页×2本' WHERE `id` = '9740000000000000178' AND `name` = 'B5笔记本' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = 'A4复印纸 70g 500张/包' WHERE `id` = '9740000000000000179' AND `name` = 'A4复印纸' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = 'Type-C数据线 1m' WHERE `id` = '9740000000000000187' AND `name` = 'Type-C数据线' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '劳保手套 12副/包' WHERE `id` = '9740000000000000194' AND `name` = '劳保手套' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = 'KN95防尘口罩 50只/盒' WHERE `id` = '9740000000000000196' AND `name` = 'KN95防尘口罩' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '烟台大樱桃 约500g/盒' WHERE `id` = '9740000000000000201' AND `name` = '烟台大樱桃' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '蒲江红心猕猴桃 6个装 约750g' WHERE `id` = '9740000000000000202' AND `name` = '蒲江红心猕猴桃' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '广西百香果 8个装 约700g' WHERE `id` = '9740000000000000203' AND `name` = '广西百香果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜冬枣 约500g/盒' WHERE `id` = '9740000000000000204' AND `name` = '新鲜冬枣' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '红心火龙果 1个 约500g' WHERE `id` = '9740000000000000205' AND `name` = '红心火龙果' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '上海青小青菜 约300g/份' WHERE `id` = '9740000000000000206' AND `name` = '上海青小青菜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜韭菜 约350g/份' WHERE `id` = '9740000000000000207' AND `name` = '新鲜韭菜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜莴笋 2根装 约800g' WHERE `id` = '9740000000000000208' AND `name` = '新鲜莴笋' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜西兰花 约400g/颗' WHERE `id` = '9740000000000000209' AND `name` = '新鲜西兰花' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜杏鲍菇 约400g/份' WHERE `id` = '9740000000000000210' AND `name` = '新鲜杏鲍菇' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜猪大排 400g/份' WHERE `id` = '9740000000000000211' AND `name` = '新鲜猪大排' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '原切牛腱子肉 500g/份' WHERE `id` = '9740000000000000212' AND `name` = '原切牛腱子肉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜羊排 600g/份' WHERE `id` = '9740000000000000213' AND `name` = '新鲜羊排' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冷冻鸡翅中 500g/袋' WHERE `id` = '9740000000000000214' AND `name` = '冷冻鸡翅中' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冷冻带鱼段 500g/袋' WHERE `id` = '9740000000000000215' AND `name` = '冷冻带鱼段' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '速冻小笼包 12只装 约300g' WHERE `id` = '9740000000000000216' AND `name` = '速冻小笼包' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '韭菜盒子 4个装 约400g' WHERE `id` = '9740000000000000217' AND `name` = '韭菜盒子' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '葱油拌面 2人份 约450g' WHERE `id` = '9740000000000000218' AND `name` = '葱油拌面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '即食豆腐脑 220g×3盒' WHERE `id` = '9740000000000000219' AND `name` = '即食豆腐脑' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冻鲅鱼段 600g/袋' WHERE `id` = '9740000000000000220' AND `name` = '冻鲅鱼段' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '奥尔良鸡肉卷 500g/袋' WHERE `id` = '9740000000000000221' AND `name` = '奥尔良鸡肉卷' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '香芋地瓜丸 300g/袋' WHERE `id` = '9740000000000000222' AND `name` = '香芋地瓜丸' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '芝麻麻团 300g/袋' WHERE `id` = '9740000000000000223' AND `name` = '芝麻麻团' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '红心猕猴桃 8个装 约1kg' WHERE `id` = '9740000000000000224' AND `name` = '红心猕猴桃' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '100%椰子水 1L/盒' WHERE `id` = '9740000000000000225' AND `name` = '100%椰子水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '即食蔬菜沙拉 250g/盒' WHERE `id` = '9740000000000000226' AND `name` = '即食蔬菜沙拉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '冷萃冻干咖啡 2g×20条' WHERE `id` = '9740000000000000227' AND `name` = '冷萃冻干咖啡' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '农夫山泉婴儿水 1L×12瓶' WHERE `id` = '9740000000000000228' AND `name` = '农夫山泉婴儿水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '心相印盒抽纸 3层100抽×8盒' WHERE `id` = '9740000000000000229' AND `name` = '心相印盒抽纸' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '厨房湿巾 25片×3包' WHERE `id` = '9740000000000000230' AND `name` = '厨房湿巾' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '有芯卷纸 4层180g×12卷' WHERE `id` = '9740000000000000231' AND `name` = '有芯卷纸' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '光明优倍鲜牛奶 950ml/瓶' WHERE `id` = '9740000000000000232' AND `name` = '光明优倍鲜牛奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '维他奶豆奶 250ml×24盒' WHERE `id` = '9740000000000000233' AND `name` = '维他奶豆奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '味全每日C橙汁 300ml×2瓶' WHERE `id` = '9740000000000000234' AND `name` = '味全每日C橙汁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '汇源100%橙汁 1L/盒' WHERE `id` = '9740000000000000235' AND `name` = '汇源100%橙汁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '健力宝橙蜜味 330ml/罐' WHERE `id` = '9740000000000000236' AND `name` = '健力宝橙蜜味' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '百事轻怡可乐 330ml/罐' WHERE `id` = '9740000000000000237' AND `name` = '百事轻怡可乐' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '外星人电解质水 600ml/瓶' WHERE `id` = '9740000000000000238' AND `name` = '外星人电解质水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '星巴克星冰乐 281ml/瓶' WHERE `id` = '9740000000000000239' AND `name` = '星巴克星冰乐' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '麦斯威尔拿铁 250ml/瓶' WHERE `id` = '9740000000000000240' AND `name` = '麦斯威尔拿铁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '中沃体质能量 600ml/瓶' WHERE `id` = '9740000000000000241' AND `name` = '中沃体质能量' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '日加满功能饮料 250ml/罐' WHERE `id` = '9740000000000000242' AND `name` = '日加满功能饮料' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '汾酒青花20 500ml/瓶' WHERE `id` = '9740000000000000243' AND `name` = '汾酒青花20' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '洋河大曲 500ml/瓶' WHERE `id` = '9740000000000000244' AND `name` = '洋河大曲' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '宁夏干红葡萄酒 750ml/瓶' WHERE `id` = '9740000000000000245' AND `name` = '宁夏干红葡萄酒' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '古越龙山黄酒 500ml/瓶' WHERE `id` = '9740000000000000246' AND `name` = '古越龙山黄酒' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '康师傅绿茶 500ml/瓶' WHERE `id` = '9740000000000000247' AND `name` = '康师傅绿茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '今麦郎冰红茶 500ml/瓶' WHERE `id` = '9740000000000000248' AND `name` = '今麦郎冰红茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '三得利茉莉乌龙 500ml/瓶' WHERE `id` = '9740000000000000249' AND `name` = '三得利茉莉乌龙' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '农夫山泉茶π 500ml/瓶' WHERE `id` = '9740000000000000250' AND `name` = '农夫山泉茶π' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '优乐美杯装奶茶 80g/杯' WHERE `id` = '9740000000000000251' AND `name` = '优乐美杯装奶茶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '豆本豆豆奶 250ml/盒' WHERE `id` = '9740000000000000252' AND `name` = '豆本豆豆奶' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '黑芝麻核桃粉 500g/罐' WHERE `id` = '9740000000000000253' AND `name` = '黑芝麻核桃粉' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '阿胶蜜枣 250g/袋' WHERE `id` = '9740000000000000254' AND `name` = '阿胶蜜枣' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '谷饲肥牛片 300g/盒' WHERE `id` = '9740000000000000255' AND `name` = '谷饲肥牛片' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '新鲜金针菇 150g/袋' WHERE `id` = '9740000000000000256' AND `name` = '新鲜金针菇' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '腌制牛肉串 400g/袋 约8串' WHERE `id` = '9740000000000000257' AND `name` = '腌制牛肉串' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '火山石烤肠 400g/袋 约6根' WHERE `id` = '9740000000000000258' AND `name` = '火山石烤肠' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '卤酱鸭头 200g/袋' WHERE `id` = '9740000000000000259' AND `name` = '卤酱鸭头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '湖南酸豆角 300g/袋' WHERE `id` = '9740000000000000260' AND `name` = '湖南酸豆角' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '糖醋糖蒜 350g/瓶' WHERE `id` = '9740000000000000261' AND `name` = '糖醋糖蒜' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '五香卤蛋 30g/枚' WHERE `id` = '9740000000000000262' AND `name` = '五香卤蛋' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '椰果罐头 200g/罐' WHERE `id` = '9740000000000000263' AND `name` = '椰果罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '杨梅罐头 245g/罐' WHERE `id` = '9740000000000000264' AND `name` = '杨梅罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '红烧猪肉罐头 340g/罐' WHERE `id` = '9740000000000000265' AND `name` = '红烧猪肉罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '鹰嘴豆罐头 400g/罐' WHERE `id` = '9740000000000000266' AND `name` = '鹰嘴豆罐头' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '原味南瓜子 250g/袋' WHERE `id` = '9740000000000000267' AND `name` = '原味南瓜子' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '琥珀核桃仁 200g/袋' WHERE `id` = '9740000000000000268' AND `name` = '琥珀核桃仁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '东北开口松子 250g/袋' WHERE `id` = '9740000000000000269' AND `name` = '东北开口松子' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '碧根果仁 250g/袋' WHERE `id` = '9740000000000000270' AND `name` = '碧根果仁' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '可比克薯片 105g/袋' WHERE `id` = '9740000000000000271' AND `name` = '可比克薯片' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '旺旺雪饼 150g/袋' WHERE `id` = '9740000000000000272' AND `name` = '旺旺雪饼' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '士力架花生夹心 51g/条' WHERE `id` = '9740000000000000273' AND `name` = '士力架花生夹心' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '溜溜梅脆青梅 80g/袋' WHERE `id` = '9740000000000000274' AND `name` = '溜溜梅脆青梅' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '豆沙面包 220g/袋' WHERE `id` = '9740000000000000275' AND `name` = '豆沙面包' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '丹麦起酥面包 192g/袋' WHERE `id` = '9740000000000000276' AND `name` = '丹麦起酥面包' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '肉松戚风蛋糕 300g/盒' WHERE `id` = '9740000000000000277' AND `name` = '肉松戚风蛋糕' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '趣多多曲奇 180g/盒' WHERE `id` = '9740000000000000278' AND `name` = '趣多多曲奇' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '汤达人豚骨拉面 128g/桶' WHERE `id` = '9740000000000000279' AND `name` = '汤达人豚骨拉面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '康师傅藤椒牛肉面 103g/袋' WHERE `id` = '9740000000000000280' AND `name` = '康师傅藤椒牛肉面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '武汉热干面 250g/袋' WHERE `id` = '9740000000000000281' AND `name` = '武汉热干面' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '自热煲仔饭 360g/盒' WHERE `id` = '9740000000000000282' AND `name` = '自热煲仔饭' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '海飞丝护发素 400ml/瓶' WHERE `id` = '9740000000000000283' AND `name` = '海飞丝护发素' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '六神经典花露水 195ml/瓶' WHERE `id` = '9740000000000000284' AND `name` = '六神经典花露水' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '达克宁软膏 20g/支' WHERE `id` = '9740000000000000285' AND `name` = '达克宁软膏' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '妇炎洁洗液 380ml/瓶' WHERE `id` = '9740000000000000286' AND `name` = '妇炎洁洗液' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '84消毒液 1L/瓶' WHERE `id` = '9740000000000000287' AND `name` = '84消毒液' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '洁厕灵 500g/瓶' WHERE `id` = '9740000000000000288' AND `name` = '洁厕灵' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '盘式蚊香 10盘装' WHERE `id` = '9740000000000000289' AND `name` = '盘式蚊香' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '红色中性笔 0.5mm 12支/盒' WHERE `id` = '9740000000000000291' AND `name` = '红色中性笔' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '便利贴套装 4本装' WHERE `id` = '9740000000000000292' AND `name` = '便利贴套装' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '耐打羽毛球 12只/筒' WHERE `id` = '9740000000000000293' AND `name` = '耐打羽毛球' AND `del_flag` = '0';
UPDATE `aryn_product`.`goods_spu` SET `name` = '不锈钢电水壶 1.7L' WHERE `id` = '9740000000000000298' AND `name` = '不锈钢电水壶' AND `del_flag` = '0';

-- ===========================================================================
-- 2. 同步刷新购物车在途行名称快照（跨库：购物车在 aryn_order）
-- ===========================================================================
UPDATE `aryn_order`.`shopping_cart` sc
JOIN `aryn_product`.`goods_spu` s ON s.`id` = sc.`spu_id`
SET sc.`spu_name` = s.`name`
WHERE sc.`del_flag` = '0' AND sc.`spu_name` <> s.`name`;

-- ===========================================================================
-- 3. 自检
-- ===========================================================================
SELECT COUNT(*) AS unchanged_should_be_0 FROM `aryn_product`.`goods_spu` WHERE (`id` = '9740000000000000001' AND `name` = '烟台红富士苹果') OR (`id` = '9740000000000000002' AND `name` = '海南贵妃芒') OR (`id` = '9740000000000000003' AND `name` = '麒麟西瓜') OR (`id` = '9740000000000000004' AND `name` = '阳光玫瑰葡萄') OR (`id` = '9740000000000000005' AND `name` = '国产香蕉') OR (`id` = '9740000000000000006' AND `name` = '赣南脐橙') OR (`id` = '9740000000000000007' AND `name` = '丹东99草莓') OR (`id` = '9740000000000000008' AND `name` = '泰国椰青') OR (`id` = '9740000000000000009' AND `name` = '油麦菜') OR (`id` = '9740000000000000010' AND `name` = '黄心土豆') OR (`id` = '9740000000000000011' AND `name` = '普罗旺斯西红柿') OR (`id` = '9740000000000000012' AND `name` = '本地黄瓜') OR (`id` = '9740000000000000013' AND `name` = '小油菜') OR (`id` = '9740000000000000014' AND `name` = '新鲜香菇') OR (`id` = '9740000000000000015' AND `name` = '嫩豆腐') OR (`id` = '9740000000000000016' AND `name` = '胡萝卜') OR (`id` = '9740000000000000017' AND `name` = '猪五花肉片') OR (`id` = '9740000000000000018' AND `name` = '原切牛腩块') OR (`id` = '9740000000000000019' AND `name` = '内蒙羔羊肉卷') OR (`id` = '9740000000000000020' AND `name` = '三黄鸡') OR (`id` = '9740000000000000021' AND `name` = '鲜鸡蛋') OR (`id` = '9740000000000000022' AND `name` = '冷冻青虾仁') OR (`id` = '9740000000000000023' AND `name` = '冰鲜三文鱼柳') OR (`id` = '9740000000000000024' AND `name` = '鲜活花蛤') OR (`id` = '9740000000000000025' AND `name` = '老面馒头') OR (`id` = '9740000000000000026' AND `name` = '鲜肉包') OR (`id` = '9740000000000000027' AND `name` = '手工挂面') OR (`id` = '9740000000000000028' AND `name` = '鲜虾云吞') OR (`id` = '9740000000000000029' AND `name` = '葱油饼') OR (`id` = '9740000000000000030' AND `name` = '鲜肉水饺') OR (`id` = '9740000000000000031' AND `name` = '花卷') OR (`id` = '9740000000000000032' AND `name` = '手擀面') OR (`id` = '9740000000000000033' AND `name` = '冷冻粗薯条') OR (`id` = '9740000000000000034' AND `name` = '牛肉卷') OR (`id` = '9740000000000000035' AND `name` = '香煎鸡排') OR (`id` = '9740000000000000036' AND `name` = '速冻水饺') OR (`id` = '9740000000000000037' AND `name` = '鱼豆腐') OR (`id` = '9740000000000000038' AND `name` = '冷冻虾仁') OR (`id` = '9740000000000000039' AND `name` = '春卷') OR (`id` = '9740000000000000040' AND `name` = '台式烤肠') OR (`id` = '9740000000000000041' AND `name` = '晴王阳光玫瑰') OR (`id` = '9740000000000000042' AND `name` = '巴氏鲜牛奶') OR (`id` = '9740000000000000043' AND `name` = '牛油果') OR (`id` = '9740000000000000044' AND `name` = '有机西兰花') OR (`id` = '9740000000000000045' AND `name` = '冷萃咖啡') OR (`id` = '9740000000000000046' AND `name` = '全麦贝果') OR (`id` = '9740000000000000047' AND `name` = '手打鲜虾滑') OR (`id` = '9740000000000000048' AND `name` = '突尼斯软籽石榴') OR (`id` = '9740000000000000049' AND `name` = '农夫山泉天然水') OR (`id` = '9740000000000000050' AND `name` = '怡宝纯净水') OR (`id` = '9740000000000000051' AND `name` = '饮用纯净水') OR (`id` = '9740000000000000052' AND `name` = '维达抽纸') OR (`id` = '9740000000000000053' AND `name` = '心相印卷纸') OR (`id` = '9740000000000000054' AND `name` = '厨房纸巾') OR (`id` = '9740000000000000055' AND `name` = '婴儿湿巾') OR (`id` = '9740000000000000056' AND `name` = '本色卫生纸') OR (`id` = '9740000000000000057' AND `name` = '伊利纯牛奶') OR (`id` = '9740000000000000058' AND `name` = '特仑苏纯牛奶') OR (`id` = '9740000000000000059' AND `name` = '蒙牛纯牛奶') OR (`id` = '9740000000000000060' AND `name` = '简爱原味酸奶') OR (`id` = '9740000000000000061' AND `name` = '农夫NFC橙汁') OR (`id` = '9740000000000000062' AND `name` = '安慕希酸奶') OR (`id` = '9740000000000000063' AND `name` = '椰树椰汁') OR (`id` = '9740000000000000064' AND `name` = '鲜榨苹果汁') OR (`id` = '9740000000000000065' AND `name` = '可口可乐') OR (`id` = '9740000000000000066' AND `name` = '雪碧') OR (`id` = '9740000000000000067' AND `name` = '百事可乐') OR (`id` = '9740000000000000068' AND `name` = '芬达橙味') OR (`id` = '9740000000000000069' AND `name` = '元气森林气泡水') OR (`id` = '9740000000000000070' AND `name` = '屈臣氏苏打水') OR (`id` = '9740000000000000071' AND `name` = '可口可乐零度') OR (`id` = '9740000000000000072' AND `name` = '美年达橙味') OR (`id` = '9740000000000000073' AND `name` = '红牛维生素功能饮料') OR (`id` = '9740000000000000074' AND `name` = '东鹏特饮') OR (`id` = '9740000000000000075' AND `name` = '乐虎功能饮料') OR (`id` = '9740000000000000076' AND `name` = '星巴克即饮咖啡') OR (`id` = '9740000000000000077' AND `name` = '雀巢即饮咖啡') OR (`id` = '9740000000000000078' AND `name` = '脉动维生素饮料') OR (`id` = '9740000000000000079' AND `name` = '黑咖啡饮料') OR (`id` = '9740000000000000080' AND `name` = '维生素功能饮料') OR (`id` = '9740000000000000081' AND `name` = '青岛啤酒经典') OR (`id` = '9740000000000000082' AND `name` = '雪花啤酒勇闯天涯') OR (`id` = '9740000000000000083' AND `name` = '百威啤酒') OR (`id` = '9740000000000000084' AND `name` = '茅台迎宾酒') OR (`id` = '9740000000000000085' AND `name` = '五粮液浓香型') OR (`id` = '9740000000000000086' AND `name` = '张裕干红葡萄酒') OR (`id` = '9740000000000000087' AND `name` = '绍兴花雕酒') OR (`id` = '9740000000000000088' AND `name` = '江小白') OR (`id` = '9740000000000000089' AND `name` = '三得利乌龙茶') OR (`id` = '9740000000000000090' AND `name` = '东方树叶绿茶') OR (`id` = '9740000000000000091' AND `name` = '康师傅冰红茶') OR (`id` = '9740000000000000092' AND `name` = '统一绿茶') OR (`id` = '9740000000000000093' AND `name` = '西湖龙井') OR (`id` = '9740000000000000094' AND `name` = '安溪铁观音') OR (`id` = '9740000000000000095' AND `name` = '茉莉花茶') OR (`id` = '9740000000000000096' AND `name` = '立顿红茶') OR (`id` = '9740000000000000097' AND `name` = '香飘飘奶茶') OR (`id` = '9740000000000000098' AND `name` = '桂格即食燕麦片') OR (`id` = '9740000000000000099' AND `name` = '阿华田可可粉') OR (`id` = '9740000000000000100' AND `name` = '南方黑芝麻糊') OR (`id` = '9740000000000000101' AND `name` = '雀巢咖啡1+2') OR (`id` = '9740000000000000102' AND `name` = '豆奶粉') OR (`id` = '9740000000000000103' AND `name` = '洋槐蜂蜜') OR (`id` = '9740000000000000104' AND `name` = '山药葛根粉') OR (`id` = '9740000000000000105' AND `name` = '火锅肥牛卷') OR (`id` = '9740000000000000106' AND `name` = '羔羊肉卷') OR (`id` = '9740000000000000107' AND `name` = '火锅丸子组合') OR (`id` = '9740000000000000108' AND `name` = '海底捞番茄火锅底料') OR (`id` = '9740000000000000109' AND `name` = '腌制羊肉串') OR (`id` = '9740000000000000110' AND `name` = '甜玉米') OR (`id` = '9740000000000000111' AND `name` = '土豆片') OR (`id` = '9740000000000000112' AND `name` = '火锅蘸料麻酱') OR (`id` = '9740000000000000113' AND `name` = '五香酱牛肉') OR (`id` = '9740000000000000114' AND `name` = '南京盐水鸭') OR (`id` = '9740000000000000115' AND `name` = '德州扒鸡') OR (`id` = '9740000000000000116' AND `name` = '卤鸡爪') OR (`id` = '9740000000000000117' AND `name` = '涪陵榨菜') OR (`id` = '9740000000000000118' AND `name` = '酸甜脆萝卜') OR (`id` = '9740000000000000119' AND `name` = '松花皮蛋') OR (`id` = '9740000000000000120' AND `name` = '午餐肉罐头') OR (`id` = '9740000000000000121' AND `name` = '砀山黄桃罐头') OR (`id` = '9740000000000000122' AND `name` = '糖水橘子罐头') OR (`id` = '9740000000000000123' AND `name` = '菠萝罐头') OR (`id` = '9740000000000000124' AND `name` = '山楂罐头') OR (`id` = '9740000000000000125' AND `name` = '午餐肉罐头') OR (`id` = '9740000000000000126' AND `name` = '油浸金枪鱼罐头') OR (`id` = '9740000000000000127' AND `name` = '甜玉米粒罐头') OR (`id` = '9740000000000000128' AND `name` = '豆豉鲮鱼罐头') OR (`id` = '9740000000000000129' AND `name` = '洽洽香瓜子') OR (`id` = '9740000000000000130' AND `name` = '铁锅炒花生') OR (`id` = '9740000000000000131' AND `name` = '酒鬼花生') OR (`id` = '9740000000000000132' AND `name` = '三只松鼠碧根果') OR (`id` = '9740000000000000133' AND `name` = '每日坚果') OR (`id` = '9740000000000000134' AND `name` = '百草味夏威夷果') OR (`id` = '9740000000000000135' AND `name` = '开心果') OR (`id` = '9740000000000000136' AND `name` = '巴旦木仁') OR (`id` = '9740000000000000137' AND `name` = '乐事薯片') OR (`id` = '9740000000000000138' AND `name` = '卫龙大面筋辣条') OR (`id` = '9740000000000000139' AND `name` = '三只松鼠肉脯') OR (`id` = '9740000000000000140' AND `name` = '旺旺仙贝') OR (`id` = '9740000000000000141' AND `name` = '德芙巧克力') OR (`id` = '9740000000000000142' AND `name` = '徐福记酥心糖') OR (`id` = '9740000000000000143' AND `name` = '芒果干') OR (`id` = '9740000000000000144' AND `name` = '奥利奥夹心饼干') OR (`id` = '9740000000000000145' AND `name` = '全麦面包') OR (`id` = '9740000000000000146' AND `name` = '夹心吐司面包') OR (`id` = '9740000000000000147' AND `name` = '麻薯面包') OR (`id` = '9740000000000000148' AND `name` = '肉松吐司') OR (`id` = '9740000000000000149' AND `name` = '黄油曲奇饼干') OR (`id` = '9740000000000000150' AND `name` = '蛋黄派') OR (`id` = '9740000000000000151' AND `name` = '蒸蛋糕') OR (`id` = '9740000000000000152' AND `name` = '苏打饼干') OR (`id` = '9740000000000000153' AND `name` = '康师傅红烧牛肉面') OR (`id` = '9740000000000000154' AND `name` = '统一老坛酸菜面') OR (`id` = '9740000000000000155' AND `name` = '桶装方便面') OR (`id` = '9740000000000000156' AND `name` = '自热小火锅') OR (`id` = '9740000000000000157' AND `name` = '自热米饭') OR (`id` = '9740000000000000158' AND `name` = '柳州螺蛳粉') OR (`id` = '9740000000000000159' AND `name` = '兰州牛肉面') OR (`id` = '9740000000000000160' AND `name` = '酸辣粉') OR (`id` = '9740000000000000161' AND `name` = '海飞丝去屑洗发水') OR (`id` = '9740000000000000162' AND `name` = '舒肤佳沐浴露') OR (`id` = '9740000000000000163' AND `name` = '氨基酸洗面奶') OR (`id` = '9740000000000000164' AND `name` = '洋甘菊护手霜') OR (`id` = '9740000000000000165' AND `name` = '云南白药牙膏') OR (`id` = '9740000000000000166' AND `name` = '高露洁牙膏') OR (`id` = '9740000000000000167' AND `name` = '卫生巾日用') OR (`id` = '9740000000000000168' AND `name` = '纸巾抽纸整箱') OR (`id` = '9740000000000000169' AND `name` = '蓝月亮洗衣液') OR (`id` = '9740000000000000170' AND `name` = '立白洗洁精') OR (`id` = '9740000000000000171' AND `name` = '雕牌洗洁精') OR (`id` = '9740000000000000172' AND `name` = '洗手液') OR (`id` = '9740000000000000173' AND `name` = '加厚垃圾袋') OR (`id` = '9740000000000000174' AND `name` = 'PE保鲜袋') OR (`id` = '9740000000000000175' AND `name` = '旋转拖把') OR (`id` = '9740000000000000176' AND `name` = '防滑衣架') OR (`id` = '9740000000000000177' AND `name` = '中性笔黑色') OR (`id` = '9740000000000000178' AND `name` = 'B5笔记本') OR (`id` = '9740000000000000179' AND `name` = 'A4复印纸') OR (`id` = '9740000000000000187' AND `name` = 'Type-C数据线') OR (`id` = '9740000000000000194' AND `name` = '劳保手套') OR (`id` = '9740000000000000196' AND `name` = 'KN95防尘口罩') OR (`id` = '9740000000000000201' AND `name` = '烟台大樱桃') OR (`id` = '9740000000000000202' AND `name` = '蒲江红心猕猴桃') OR (`id` = '9740000000000000203' AND `name` = '广西百香果') OR (`id` = '9740000000000000204' AND `name` = '新鲜冬枣') OR (`id` = '9740000000000000205' AND `name` = '红心火龙果') OR (`id` = '9740000000000000206' AND `name` = '上海青小青菜') OR (`id` = '9740000000000000207' AND `name` = '新鲜韭菜') OR (`id` = '9740000000000000208' AND `name` = '新鲜莴笋') OR (`id` = '9740000000000000209' AND `name` = '新鲜西兰花') OR (`id` = '9740000000000000210' AND `name` = '新鲜杏鲍菇') OR (`id` = '9740000000000000211' AND `name` = '新鲜猪大排') OR (`id` = '9740000000000000212' AND `name` = '原切牛腱子肉') OR (`id` = '9740000000000000213' AND `name` = '新鲜羊排') OR (`id` = '9740000000000000214' AND `name` = '冷冻鸡翅中') OR (`id` = '9740000000000000215' AND `name` = '冷冻带鱼段') OR (`id` = '9740000000000000216' AND `name` = '速冻小笼包') OR (`id` = '9740000000000000217' AND `name` = '韭菜盒子') OR (`id` = '9740000000000000218' AND `name` = '葱油拌面') OR (`id` = '9740000000000000219' AND `name` = '即食豆腐脑') OR (`id` = '9740000000000000220' AND `name` = '冻鲅鱼段') OR (`id` = '9740000000000000221' AND `name` = '奥尔良鸡肉卷') OR (`id` = '9740000000000000222' AND `name` = '香芋地瓜丸') OR (`id` = '9740000000000000223' AND `name` = '芝麻麻团') OR (`id` = '9740000000000000224' AND `name` = '红心猕猴桃') OR (`id` = '9740000000000000225' AND `name` = '100%椰子水') OR (`id` = '9740000000000000226' AND `name` = '即食蔬菜沙拉') OR (`id` = '9740000000000000227' AND `name` = '冷萃冻干咖啡') OR (`id` = '9740000000000000228' AND `name` = '农夫山泉婴儿水') OR (`id` = '9740000000000000229' AND `name` = '心相印盒抽纸') OR (`id` = '9740000000000000230' AND `name` = '厨房湿巾') OR (`id` = '9740000000000000231' AND `name` = '有芯卷纸') OR (`id` = '9740000000000000232' AND `name` = '光明优倍鲜牛奶') OR (`id` = '9740000000000000233' AND `name` = '维他奶豆奶') OR (`id` = '9740000000000000234' AND `name` = '味全每日C橙汁') OR (`id` = '9740000000000000235' AND `name` = '汇源100%橙汁') OR (`id` = '9740000000000000236' AND `name` = '健力宝橙蜜味') OR (`id` = '9740000000000000237' AND `name` = '百事轻怡可乐') OR (`id` = '9740000000000000238' AND `name` = '外星人电解质水') OR (`id` = '9740000000000000239' AND `name` = '星巴克星冰乐') OR (`id` = '9740000000000000240' AND `name` = '麦斯威尔拿铁') OR (`id` = '9740000000000000241' AND `name` = '中沃体质能量') OR (`id` = '9740000000000000242' AND `name` = '日加满功能饮料') OR (`id` = '9740000000000000243' AND `name` = '汾酒青花20') OR (`id` = '9740000000000000244' AND `name` = '洋河大曲') OR (`id` = '9740000000000000245' AND `name` = '宁夏干红葡萄酒') OR (`id` = '9740000000000000246' AND `name` = '古越龙山黄酒') OR (`id` = '9740000000000000247' AND `name` = '康师傅绿茶') OR (`id` = '9740000000000000248' AND `name` = '今麦郎冰红茶') OR (`id` = '9740000000000000249' AND `name` = '三得利茉莉乌龙') OR (`id` = '9740000000000000250' AND `name` = '农夫山泉茶π') OR (`id` = '9740000000000000251' AND `name` = '优乐美杯装奶茶') OR (`id` = '9740000000000000252' AND `name` = '豆本豆豆奶') OR (`id` = '9740000000000000253' AND `name` = '黑芝麻核桃粉') OR (`id` = '9740000000000000254' AND `name` = '阿胶蜜枣') OR (`id` = '9740000000000000255' AND `name` = '谷饲肥牛片') OR (`id` = '9740000000000000256' AND `name` = '新鲜金针菇') OR (`id` = '9740000000000000257' AND `name` = '腌制牛肉串') OR (`id` = '9740000000000000258' AND `name` = '火山石烤肠') OR (`id` = '9740000000000000259' AND `name` = '卤酱鸭头') OR (`id` = '9740000000000000260' AND `name` = '湖南酸豆角') OR (`id` = '9740000000000000261' AND `name` = '糖醋糖蒜') OR (`id` = '9740000000000000262' AND `name` = '五香卤蛋') OR (`id` = '9740000000000000263' AND `name` = '椰果罐头') OR (`id` = '9740000000000000264' AND `name` = '杨梅罐头') OR (`id` = '9740000000000000265' AND `name` = '红烧猪肉罐头') OR (`id` = '9740000000000000266' AND `name` = '鹰嘴豆罐头') OR (`id` = '9740000000000000267' AND `name` = '原味南瓜子') OR (`id` = '9740000000000000268' AND `name` = '琥珀核桃仁') OR (`id` = '9740000000000000269' AND `name` = '东北开口松子') OR (`id` = '9740000000000000270' AND `name` = '碧根果仁') OR (`id` = '9740000000000000271' AND `name` = '可比克薯片') OR (`id` = '9740000000000000272' AND `name` = '旺旺雪饼') OR (`id` = '9740000000000000273' AND `name` = '士力架花生夹心') OR (`id` = '9740000000000000274' AND `name` = '溜溜梅脆青梅') OR (`id` = '9740000000000000275' AND `name` = '豆沙面包') OR (`id` = '9740000000000000276' AND `name` = '丹麦起酥面包') OR (`id` = '9740000000000000277' AND `name` = '肉松戚风蛋糕') OR (`id` = '9740000000000000278' AND `name` = '趣多多曲奇') OR (`id` = '9740000000000000279' AND `name` = '汤达人豚骨拉面') OR (`id` = '9740000000000000280' AND `name` = '康师傅藤椒牛肉面') OR (`id` = '9740000000000000281' AND `name` = '武汉热干面') OR (`id` = '9740000000000000282' AND `name` = '自热煲仔饭') OR (`id` = '9740000000000000283' AND `name` = '海飞丝护发素') OR (`id` = '9740000000000000284' AND `name` = '六神经典花露水') OR (`id` = '9740000000000000285' AND `name` = '达克宁软膏') OR (`id` = '9740000000000000286' AND `name` = '妇炎洁洗液') OR (`id` = '9740000000000000287' AND `name` = '84消毒液') OR (`id` = '9740000000000000288' AND `name` = '洁厕灵') OR (`id` = '9740000000000000289' AND `name` = '盘式蚊香') OR (`id` = '9740000000000000291' AND `name` = '红色中性笔') OR (`id` = '9740000000000000292' AND `name` = '便利贴套装') OR (`id` = '9740000000000000293' AND `name` = '耐打羽毛球') OR (`id` = '9740000000000000298' AND `name` = '不锈钢电水壶');
SELECT `id`, `name` FROM `aryn_product`.`goods_spu`
WHERE `id` BETWEEN '9740000000000000001' AND '9740000000000000302' AND `del_flag` = '0'
  AND `name` NOT REGEXP '[0-9]+(g|kg|ml|L|只|枚|个|盒|袋|包|份|条|罐|瓶|卷|支|根|串|颗|片|桶|杯|筒|副|盘|本|张|抽|度|m)|装|套|卷';

SET FOREIGN_KEY_CHECKS = 1;
