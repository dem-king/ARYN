-- 商品卖点文案回填（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景：为现有商品补一段商详首屏卖点，按商品名匹配，文案为运营口径示例。
-- 特性：可重复执行；仅回填空值（introduction IS NULL OR ''），不覆盖后台已编辑过的文案。
-- 匹配：按商品名精确匹配；若目标环境商品目录不同（如旧全量脚本初始化的旧目录），语句安全跳过、不改数据。
-- 配套：goods_spu.introduction（172/173 增量）与 GoodsIntroductionSanitizer 白名单。
--
-- 执行：mysql -u root -p aryn_boot < 173goods_introduction_seed.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

UPDATE `goods_spu` SET `introduction` = '<p>牙齿的<span style="color:#2458c9;font-weight:bold">私人医生</span>，早晚各上岗一次，牙龈表示很安心。</p>'
WHERE `name` = '云南白药牙膏' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>夏天的<span style="color:#1a9c5b;font-weight:bold">续命水</span>：蚊子绕道走，汗味也不敢造次。</p>'
WHERE `name` = '六神经典花露水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>那几天的<span style="color:#d9467a;font-weight:bold">隐形队友</span>：棉柔贴身，走路带风也不慌。</p>'
WHERE `name` = '卫生巾日用' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>下班第一件事：抽一张，把妆和班味一起<span style="color:#2458c9;font-weight:bold">卸干净</span>。</p>'
WHERE `name` = '卸妆湿巾25片' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>灶台油污一抽就服软，比刷手机还<span style="color:#e8541e;font-weight:bold">解压</span>。</p>'
WHERE `name` = '厨房湿巾' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>吸油吸水两开花，炸物垫一张，灶台像<span style="color:#b45309;font-weight:bold">刚装修好</span>。</p>'
WHERE `name` = '厨房纸巾' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>温和抑菌不刺激，日常护理里的<span style="color:#d9467a;font-weight:bold">小透明</span>，默默把舒适感拉满。</p>'
WHERE `name` = '妇炎洁洗液' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>能擦手、能擦嘴、能救急，带娃出门的<span style="color:#2458c9;font-weight:bold">万能橡皮擦</span>。</p>'
WHERE `name` = '婴儿手口湿巾80抽' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>手口可用、湿而不凉，换尿布那几秒<span style="color:#2458c9;font-weight:bold">少一点兵荒马乱</span>。</p>'
WHERE `name` = '婴儿湿巾' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>柔韧亲肤不掉屑，纸巾界的<span style="color:#b45309;font-weight:bold">老实人</span>，一抽就安心。</p>'
WHERE `name` = '心相印卷纸' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>客厅茶几的<span style="color:#b45309;font-weight:bold">门面担当</span>，原生木浆，抽着不心疼。</p>'
WHERE `name` = '心相印盒抽纸' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>洗完像做了<span style="color:#8a5a1c;font-weight:bold">半次护理</span>，头发顺到梳子都想偷懒。</p>'
WHERE `name` = '施华蔻精油洗发水600ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>加厚有芯，厨房卫生间都能打，囤它就像囤<span style="color:#b45309;font-weight:bold">安全感</span>。</p>'
WHERE `name` = '有芯卷纸' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>竹浆本色不加戏，朴素但<span style="color:#1a9c5b;font-weight:bold">耐造</span>，一家人用着踏实。</p>'
WHERE `name` = '本色卫生纸' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>洗完不紧绷，脸皮（褒义）被温柔对待的<span style="color:#2458c9;font-weight:bold">洗脸自由</span>。</p>'
WHERE `name` = '氨基酸洗面奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>秋冬手部干纹的<span style="color:#b45309;font-weight:bold">急救包</span>，一抹回软，敲键盘都顺滑。</p>'
WHERE `name` = '洋甘菊护手霜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>头屑说再见，深色衣服终于敢<span style="color:#2458c9;font-weight:bold">正大光明</span>地穿了。</p>'
WHERE `name` = '海飞丝去屑洗发水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>毛躁发丝一把按住，洗完头摸自己头发会<span style="color:#2458c9;font-weight:bold">多摸两下</span>。</p>'
WHERE `name` = '海飞丝护发素' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>无硅油配方，头皮清爽得像给毛囊<span style="color:#1a9c5b;font-weight:bold">开了扇窗</span>。</p>'
WHERE `name` = '滋源无硅油洗发水535ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>三分钟拯救炸毛，赶时间的早上也能<span style="color:#2458c9;font-weight:bold">体面出门</span>。</p>'
WHERE `name` = '潘婷3分钟护发素180ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>整箱到家，抽纸自由一次到位，客厅卧室<span style="color:#b45309;font-weight:bold">随手有纸</span>。</p>'
WHERE `name` = '纸巾抽纸整箱' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>三层加厚，擦汗擦嘴擦桌子，性价比界的<span style="color:#b45309;font-weight:bold">六边形战士</span>。</p>'
WHERE `name` = '维达抽纸' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>纯白清香，洗完是干净肥皂味，闻着像<span style="color:#1a9c5b;font-weight:bold">小时候的夏天</span>。</p>'
WHERE `name` = '舒肤佳沐浴露' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>脚痒别硬扛，家里药箱的<span style="color:#2458c9;font-weight:bold">常驻嘉宾</span>，对症才安心。</p>'
WHERE `name` = '达克宁软膏' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>75%酒精，出门擦一擦，手不碰脏东西心里<span style="color:#1a9c5b;font-weight:bold">有底</span>。</p>'
WHERE `name` = '酒精消毒湿巾便携装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>洗完顺到能一梳到底，平价大碗的<span style="color:#2458c9;font-weight:bold">柔顺担当</span>。</p>'
WHERE `name` = '飘柔洗发露300ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>防蛀牙膏里的<span style="color:#2458c9;font-weight:bold">老熟人</span>，一支搞定早晚刷牙的安心。</p>'
WHERE `name` = '高露洁牙膏' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>咖啡好搭档，0乳糖不闹肚子，倒进杯里就<span style="color:#b45309;font-weight:bold">很时髦</span>。</p>'
WHERE `name` = 'OATLY燕麦奶1L' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>早餐桌上的<span style="color:#2458c9;font-weight:bold">保留节目</span>，一箱管十天，喝的就是踏实。</p>'
WHERE `name` = '伊利纯牛奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>冷链鲜奶，奶香厚实，早上来一杯，胃先<span style="color:#b45309;font-weight:bold">醒过来</span>。</p>'
WHERE `name` = '光明优倍鲜牛奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>非浓缩还原，酸甜都来自真橙子，喝一口像<span style="color:#e8541e;font-weight:bold">剥了个橙</span>。</p>'
WHERE `name` = '农夫NFC橙汁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>维C小炸弹，冰一下更爽，工位上的<span style="color:#e8541e;font-weight:bold">下午茶救星</span>。</p>'
WHERE `name` = '味全每日C橙汁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>希腊风味够浓稠，勺子立得住，早餐和加班都<span style="color:#b45309;font-weight:bold">顶得住</span>。</p>'
WHERE `name` = '安慕希酸奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>椰香直球，从小喝到大的味道，冰镇后<span style="color:#1a9c5b;font-weight:bold">一口回海岛</span>。</p>'
WHERE `name` = '椰树椰汁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>小瓶装椰香，随手一罐，解腻的程度<span style="color:#1a9c5b;font-weight:bold">刚刚好</span>。</p>'
WHERE `name` = '椰牛椰汁245ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>100%果汁，酸甜不加水，喝完感觉皮肤都<span style="color:#e8541e;font-weight:bold">亮了一点</span>。</p>'
WHERE `name` = '汇源100%橙汁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>高端奶源，口感醇厚，送礼自喝都不掉价的<span style="color:#2458c9;font-weight:bold">体面奶</span>。</p>'
WHERE `name` = '特仑苏纯牛奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>配料表干净得可爱，无添加，酸得温和、喝得<span style="color:#b45309;font-weight:bold">放心</span>。</p>'
WHERE `name` = '简爱原味酸奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>豆香浓郁，早餐一根吸管解决，赶地铁也能<span style="color:#1a9c5b;font-weight:bold">喝得像样</span>。</p>'
WHERE `name` = '维他奶豆奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>整箱囤的日常奶，便宜大碗，全家早餐的<span style="color:#2458c9;font-weight:bold">基础装备</span>。</p>'
WHERE `name` = '蒙牛纯牛奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>花生奶香一口入魂，甜而不腻，是小时候<span style="color:#b45309;font-weight:bold">小卖部</span>的味道。</p>'
WHERE `name` = '银鹭花生牛奶450ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>核桃醇香，罐装的营养感，送长辈和自己都<span style="color:#8a5a1c;font-weight:bold">很合适</span>。</p>'
WHERE `name` = '露露核桃露240ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>冷藏鲜榨，清爽不齁，下午三点犯困时来一杯<span style="color:#1a9c5b;font-weight:bold">很提神</span>。</p>'
WHERE `name` = '鲜榨苹果汁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>热水一冲，办公室秒变奶茶店，周杰伦的<span style="color:#b45309;font-weight:bold">快乐</span>你也可以有。</p>'
WHERE `name` = '优乐美杯装奶茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>小袋装随手冲，甜香顺滑，下午犯困时的<span style="color:#b45309;font-weight:bold">小甜头</span>。</p>'
WHERE `name` = '优乐美袋装奶茶22g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>香浓一冲就开，熬夜后的胃和头发都表示<span style="color:#8a5a1c;font-weight:bold">谢谢</span>。</p>'
WHERE `name` = '南方黑芝麻糊' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>清香回甘，泡开满屋兰花香，待客自饮都<span style="color:#1a9c5b;font-weight:bold">有面子</span>。</p>'
WHERE `name` = '安溪铁观音' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>早餐冲一碗，细滑暖胃，打工人的<span style="color:#b45309;font-weight:bold">随便但体面</span>。</p>'
WHERE `name` = '山药葛根粉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>枸杞遇上蜂蜜，温水一冲，养生局里最<span style="color:#e8541e;font-weight:bold">好喝的那杯</span>。</p>'
WHERE `name` = '心之源枸杞蜂蜜236g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>热牛奶一泡就吃，膳食纤维管饱，早餐不再<span style="color:#b45309;font-weight:bold">靠意志力</span>。</p>'
WHERE `name` = '桂格即食燕麦片' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>天然成熟蜜，甜得干净，冲水涂抹都<span style="color:#e8541e;font-weight:bold">很省心</span>。</p>'
WHERE `name` = '洋槐蜂蜜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>大罐装枣花香，温水一勺，秋冬的喉咙<span style="color:#b45309;font-weight:bold">被温柔抱住</span>。</p>'
WHERE `name` = '百花枣花蜂蜜1750g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>100 包管够，热水一泡就是下午茶，加奶更<span style="color:#b45309;font-weight:bold">顺滑</span>。</p>'
WHERE `name` = '立顿红茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>窨制花香扑鼻，泡开像站在花田边，解腻又<span style="color:#1a9c5b;font-weight:bold">解压</span>。</p>'
WHERE `name` = '茉莉花茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>明前绿茶，豆香清雅，一杯下去春天就<span style="color:#1a9c5b;font-weight:bold">住进杯子</span>。</p>'
WHERE `name` = '西湖龙井' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>独立小包，热水一冲豆香满屋，早餐效率<span style="color:#b45309;font-weight:bold">拉满</span>。</p>'
WHERE `name` = '豆奶粉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>豆香浓郁不齁甜，早餐桌上的<span style="color:#1a9c5b;font-weight:bold">温柔担当</span>。</p>'
WHERE `name` = '豆本豆豆奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>三合一浓郁提神，困意来袭时一包续命，办公室<span style="color:#8a5a1c;font-weight:bold">硬通货</span>。</p>'
WHERE `name` = '越南G7咖啡50包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>麦芽可可香，热牛奶一冲，冬天手里有它<span style="color:#8a5a1c;font-weight:bold">暖一半</span>。</p>'
WHERE `name` = '阿华田可可粉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>甜蜜滋补小零嘴，嘴馋和养生居然能在<span style="color:#b91c1c;font-weight:bold">同一颗枣</span>上和解。</p>'
WHERE `name` = '阿胶蜜枣' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>特浓三合一，一条下去眼神都变亮，90 条够撑一个<span style="color:#8a5a1c;font-weight:bold">季度</span>。</p>'
WHERE `name` = '雀巢1+2特浓90条' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>经典三合一，热水一冲就是熟悉的香，加班夜里的<span style="color:#8a5a1c;font-weight:bold">老伙计</span>。</p>'
WHERE `name` = '雀巢咖啡1+2' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>奶香浓郁，给咖啡加一点顺滑，苦味都变得<span style="color:#b45309;font-weight:bold">有礼貌</span>。</p>'
WHERE `name` = '雀巢咖啡伴侣400g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>杯装经典，热水一冲香甜顺滑，冬天捧在手心<span style="color:#b45309;font-weight:bold">就很治愈</span>。</p>'
WHERE `name` = '香飘飘奶茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>15 杯一盒口味不重样，办公室一分享，人情世故<span style="color:#b45309;font-weight:bold">一下顺了</span>。</p>'
WHERE `name` = '香飘飘奶茶礼盒15杯' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>原味速溶，醇香不酸，早上和开会前都得<span style="color:#8a5a1c;font-weight:bold">来一条</span>。</p>'
WHERE `name` = '麦斯威尔原味30条' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>芝麻核桃双拼香，冲一碗浓稠顺滑，养生也可以<span style="color:#8a5a1c;font-weight:bold">很好喝</span>。</p>'
WHERE `name` = '黑芝麻核桃粉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>一咬就酥到掉渣，喜糖盒里的<span style="color:#e8541e;font-weight:bold">气氛组组长</span>。</p>'
WHERE `name` = '徐福记酥心糖' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>丝滑到会在嘴里滑滑梯，心情不好时来一块，<span style="color:#8a5a1c;font-weight:bold">快乐立刻续费</span>。</p>'
WHERE `name` = '德芙巧克力' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>大粒葡萄干，甜得像晒足了太阳，抓一把就是<span style="color:#e8541e;font-weight:bold">追剧标配</span>。</p>'
WHERE `name` = '新疆蓝宝石葡萄干500g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>咸香酥脆，咔嚓一口回到小时候，家庭装的意思就是<span style="color:#e8541e;font-weight:bold">放开吃</span>。</p>'
WHERE `name` = '旺旺仙贝' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>蓬松香脆，甜咸刚好，办公室抽屉里的<span style="color:#e8541e;font-weight:bold">快乐储备</span>。</p>'
WHERE `name` = '旺旺雪饼' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>30 小包一天一袋，坚果配果干，忙起来也能<span style="color:#b45309;font-weight:bold">有点讲究</span>。</p>'
WHERE `name` = '每日坚果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>五香入味，嗑起来就停不下，聊天局的<span style="color:#e8541e;font-weight:bold">气氛发动机</span>。</p>'
WHERE `name` = '洽洽香瓜子' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酸甜开胃，果干有嚼劲，嘴馋又怕胖时的<span style="color:#d9467a;font-weight:bold">折中选择</span>。</p>'
WHERE `name` = '浙梅蔓越莓干508g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酸得清醒，甜得及时，犯困时来一颗，<span style="color:#1a9c5b;font-weight:bold">瞬间精神</span>。</p>'
WHERE `name` = '溜溜梅脆青梅' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>糖衣裹着核桃香，甜脆不涩，补脑这件事终于<span style="color:#b45309;font-weight:bold">不苦了</span>。</p>'
WHERE `name` = '琥珀核桃仁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>奶油香浓，自带开果器，吃坚果终于不用<span style="color:#b45309;font-weight:bold">和壳较劲</span>。</p>'
WHERE `name` = '百草味夏威夷果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>奶香酥脆，剥好的果仁直接入口，懒人也能<span style="color:#b45309;font-weight:bold">优雅补脑</span>。</p>'
WHERE `name` = '碧根果仁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>蜜饯果脯，芒果味浓，嚼着像把夏天<span style="color:#e8541e;font-weight:bold">含在嘴里</span>。</p>'
WHERE `name` = '芒果干' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>冻干草莓，酥脆酸甜，下午三点的<span style="color:#d9467a;font-weight:bold">粉色救兵</span>。</p>'
WHERE `name` = '薛记草莓干88g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>麻辣下酒，花生脆得响，小酌时没它总觉得<span style="color:#b91c1c;font-weight:bold">少了点什么</span>。</p>'
WHERE `name` = '酒鬼花生' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>带壳红皮，铁锅炒香，剥一颗满手花生香，<span style="color:#b45309;font-weight:bold">越剥越上瘾</span>。</p>'
WHERE `name` = '铁锅炒花生' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>奶油味碧根果，大颗饱满，追剧时剥壳的手<span style="color:#b45309;font-weight:bold">根本停不下</span>。</p>'
WHERE `name` = '三只松鼠碧根果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>蜜汁猪肉脯，甜咸有嚼劲，加班到深夜的<span style="color:#b91c1c;font-weight:bold">一口安慰</span>。</p>'
WHERE `name` = '三只松鼠肉脯' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>菠萝果干，香甜耐嚼，酸酸甜甜像<span style="color:#e8541e;font-weight:bold">热带小太阳</span>。</p>'
WHERE `name` = '三只松鼠菠萝干106g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>洋葱圈酥脆，咸香上瘾，一包根本<span style="color:#e8541e;font-weight:bold">不够看一集</span>。</p>'
WHERE `name` = '上好佳洋葱圈80g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>玉米卷香脆，小小一包刚刚好，解馋又不<span style="color:#e8541e;font-weight:bold">太有负担</span>。</p>'
WHERE `name` = '上好佳玉米卷40g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>开口松子，仁香饱满，剥起来顺手，吃起来<span style="color:#b45309;font-weight:bold">很有成就感</span>。</p>'
WHERE `name` = '东北开口松子' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>原味番茄双拼，薄脆带响，快乐水的<span style="color:#e8541e;font-weight:bold">最佳拍档</span>。</p>'
WHERE `name` = '乐事薯片' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>10 包多口味，随手一包，独处或来客都能<span style="color:#e8541e;font-weight:bold">咔嚓一下</span>。</p>'
WHERE `name` = '乐事薯片70g*10包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>鲜虾风味，酥脆鲜香，一开袋手就<span style="color:#e8541e;font-weight:bold">自己伸进去</span>。</p>'
WHERE `name` = '农心鲜虾条80g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>儿时味道，辣得带劲，办公室下午的<span style="color:#b91c1c;font-weight:bold">快乐源泉</span>。</p>'
WHERE `name` = '卫龙大面筋辣条' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>原味南瓜子，饱满香脆，嗑着嗑着一下午就<span style="color:#b45309;font-weight:bold">过去了</span>。</p>'
WHERE `name` = '原味南瓜子' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>薯片香脆，追剧零食，边看边咔嚓，剧情都<span style="color:#e8541e;font-weight:bold">更带劲</span>。</p>'
WHERE `name` = '可比克薯片' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>花生夹心，能量补充，饿到发慌时来一条，<span style="color:#8a5a1c;font-weight:bold">立刻回血</span>。</p>'
WHERE `name` = '士力架花生夹心' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>原味夹心，扭一扭泡一泡，大人的快乐也可以<span style="color:#8a5a1c;font-weight:bold">很简单</span>。</p>'
WHERE `name` = '奥利奥夹心饼干' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>原味坚果，香脆不燥，每天一小把，健康零食<span style="color:#b45309;font-weight:bold">不算偷吃</span>。</p>'
WHERE `name` = '巴旦木仁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>原色无漂白，果仁饱满，剥开就笑，<span style="color:#1a9c5b;font-weight:bold">名字就很讨喜</span>。</p>'
WHERE `name` = '开心果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>工地搬砖也要<span style="color:#d97706;font-weight:bold">头等大事</span>：抗砸耐戴，命比工期贵。</p>'
WHERE `name` = 'ABS安全帽' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>带呼吸阀，粉尘环境里的<span style="color:#2458c9;font-weight:bold">第一道防线</span>，喘气还顺畅。</p>'
WHERE `name` = 'KN95防尘口罩' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>高空作业的<span style="color:#d97706;font-weight:bold">保命绳</span>，五点受力，家里那盏灯等你回去。</p>'
WHERE `name` = '五点式安全带' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>小病小伤不慌张，家里车里各放一个，<span style="color:#b91c1c;font-weight:bold">关键时刻很顶用</span>。</p>'
WHERE `name` = '便携家庭急救包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>棉纱防滑，搬货干活不打滑，一打慢慢用，<span style="color:#2458c9;font-weight:bold">手先保住</span>。</p>'
WHERE `name` = '劳保手套' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>加厚牛津布，风雨里干活不狼狈，收起来<span style="color:#2458c9;font-weight:bold">就一小包</span>。</p>'
WHERE `name` = '单人雨衣' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>高亮反光，夜里干活多一层<span style="color:#d97706;font-weight:bold">被看见的安全感</span>。</p>'
WHERE `name` = '反光安全背心' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>耐磨涤棉，耐脏耐造，干活的人穿得<span style="color:#4b5563;font-weight:bold">踏实才体面</span>。</p>'
WHERE `name` = '春秋工作服套装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>火灾逃生的<span style="color:#b91c1c;font-weight:bold">几分钟底气</span>，希望永远用不上，但不能没有。</p>'
WHERE `name` = '消防过滤式自救呼吸器' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>家庭应急的<span style="color:#b91c1c;font-weight:bold">定心丸</span>，放厨房门口，小范围起火不求人。</p>'
WHERE `name` = '淮海干粉灭火器2kg' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>绝缘底，电流面前不逞强，电工师傅的<span style="color:#2458c9;font-weight:bold">脚下功夫</span>。</p>'
WHERE `name` = '电工绝缘鞋' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>强光一开，黑夜退散，停电、检修、夜路的<span style="color:#d97706;font-weight:bold">随身太阳</span>。</p>'
WHERE `name` = '神火强光应急手电筒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>电工防护，手上多一层<span style="color:#2458c9;font-weight:bold">绝缘底气</span>，带电作业才敢讲规范。</p>'
WHERE `name` = '绝缘手套' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>连体防尘，粉尘环境里把自己<span style="color:#4b5563;font-weight:bold">打包好</span>，干活才安心。</p>'
WHERE `name` = '连体防尘服' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>防砸防刺穿，一脚踩下去稳当，重活人的<span style="color:#4b5563;font-weight:bold">铁脚板</span>。</p>'
WHERE `name` = '钢头劳保鞋' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>防飞溅，切割打磨不眯眼，眼睛只有一双，<span style="color:#2458c9;font-weight:bold">别省这一步</span>。</p>'
WHERE `name` = '防护护目镜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>拖地擦台面来一点，家里细菌<span style="color:#1a9c5b;font-weight:bold">集体下班</span>。</p>'
WHERE `name` = '84消毒液' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>食品级材质，装菜装水果装剩饭，冰箱收纳的<span style="color:#2458c9;font-weight:bold">万金油</span>。</p>'
WHERE `name` = 'PE保鲜袋' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>免钉上墙，承重牢靠，租房党终于可以<span style="color:#d97706;font-weight:bold">放心往墙上贴</span>。</p>'
WHERE `name` = '免钉粘胶挂钩10个' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>点断式加厚，提起来不心虚，倒垃圾这件小事<span style="color:#4b5563;font-weight:bold">不再翻车</span>。</p>'
WHERE `name` = '加厚垃圾袋' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>里外一块吸，玻璃一擦透亮，擦窗终于不用<span style="color:#2458c9;font-weight:bold">半个身子探出去</span>。</p>'
WHERE `name` = '双面擦窗器' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>大容量一装，杂物秒消失，房间整洁得<span style="color:#d97706;font-weight:bold">像换了套装修</span>。</p>'
WHERE `name` = '塑料收纳箱大号' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>免打孔上墙，衣服挂起来，地面空出来的那一刻<span style="color:#d97706;font-weight:bold">很治愈</span>。</p>'
WHERE `name` = '壁挂衣架收纳系统' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>免手洗，一踩一转就甩干，拖地这件事终于<span style="color:#2458c9;font-weight:bold">不脏手</span>。</p>'
WHERE `name` = '旋转拖把' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>倒一圈静置一会，马桶亮得<span style="color:#1a9c5b;font-weight:bold">能反光</span>，刷厕所也有成就感。</p>'
WHERE `name` = '洁厕灵' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>抑菌又滋润，洗完不干手，家里那瓶<span style="color:#1a9c5b;font-weight:bold">用得最快</span>的就是它。</p>'
WHERE `name` = '洗手液' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>一夜安眠，蚊子在角落<span style="color:#1a9c5b;font-weight:bold">集体沉默</span>，夏天的老朋友。</p>'
WHERE `name` = '盘式蚊香' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>换季被子一压就扁，衣柜凭空<span style="color:#d97706;font-weight:bold">多出一半空间</span>。</p>'
WHERE `name` = '真空压缩袋套装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>去油不伤手，一搓就起泡，洗完的盘子亮得<span style="color:#1a9c5b;font-weight:bold">能照人</span>。</p>'
WHERE `name` = '立白洗洁精' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>深层洁净，一件外套一泵，换季大扫除的<span style="color:#2458c9;font-weight:bold">主力军</span>。</p>'
WHERE `name` = '蓝月亮洗衣液' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>无痕防滑，真丝衬衫也挂得稳，衣柜里的<span style="color:#4b5563;font-weight:bold">秩序感</span>全靠它。</p>'
WHERE `name` = '防滑衣架' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>柠檬去油，便宜大碗，厨房台面那瓶<span style="color:#1a9c5b;font-weight:bold">永远不嫌多</span>。</p>'
WHERE `name` = '雕牌洗洁精' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>编织线身，快充不断片，办公室和包里各一根，<span style="color:#2458c9;font-weight:bold">电量焦虑减半</span>。</p>'
WHERE `name` = 'Type-C数据线' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>一壶水几分钟就开，泡面冲茶冲咖啡，出租屋的<span style="color:#2458c9;font-weight:bold">万能热水站</span>。</p>'
WHERE `name` = '不锈钢电水壶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>30W 大功率，上车就回血，导航和电量<span style="color:#2458c9;font-weight:bold">都不迷路</span>。</p>'
WHERE `name` = '倍思车载快充30W' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>双向快充，一天两充不慌，出门带它等于带了个<span style="color:#2458c9;font-weight:bold">移动插座</span>。</p>'
WHERE `name` = '充电宝20000mAh' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>便携静音，通勤地铁里的<span style="color:#1a9c5b;font-weight:bold">随身空调</span>，吹着就到站了。</p>'
WHERE `name` = '手持小风扇' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>桌面折叠，追剧刷课都解放双手，颈椎表示<span style="color:#d97706;font-weight:bold">谢谢</span>。</p>'
WHERE `name` = '手机支架' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>国A级照度，写作业不揉眼，晚上加班也有<span style="color:#d97706;font-weight:bold">像样的光</span>。</p>'
WHERE `name` = '护眼台灯' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>6 位 1.8 米，电脑台灯充电器一起上，桌面<span style="color:#2458c9;font-weight:bold">不再抢插座</span>。</p>'
WHERE `name` = '插线板6位' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>无线数显，胎压看得见，半路漏气不再<span style="color:#d97706;font-weight:bold">干着急</span>。</p>'
WHERE `name` = '无线数显车载充气泵' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>办公静音，点起来不吵同事，开会记笔记<span style="color:#2458c9;font-weight:bold">顺手很多</span>。</p>'
WHERE `name` = '无线鼠标' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>TWS 降噪，地铁上一戴，世界安静，<span style="color:#2458c9;font-weight:bold">只剩你的歌单</span>。</p>'
WHERE `name` = '真无线蓝牙耳机' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>软壳防摔，手感亲肤，手机掉地上那一秒<span style="color:#d97706;font-weight:bold">先谢它</span>。</p>'
WHERE `name` = '硅胶手机壳' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>耳机线材一包收，包里不再<span style="color:#4b5563;font-weight:bold">乱成一团</span>。</p>'
WHERE `name` = '耳机收纳包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>卡在出风口，导航抬头就看，开车时少低头<span style="color:#2458c9;font-weight:bold">多安全</span>。</p>'
WHERE `name` = '车载出风口手机支架' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>出风口一夹，车里瞬间<span style="color:#1a9c5b;font-weight:bold">换了个味道</span>，堵车也没那么烦。</p>'
WHERE `name` = '车载出风口香薰' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>铝合金稳固可调，平板立起来，追剧办公都<span style="color:#2458c9;font-weight:bold">很有样子</span>。</p>'
WHERE `name` = '铝合金平板支架' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>轴承顺滑不缠绳，跳起来像开了倍速，燃脂也能<span style="color:#1a9c5b;font-weight:bold">很上头</span>。</p>'
WHERE `name` = '361度轴承跳绳' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>中考训练专用，手感扎实，垫球也要垫出<span style="color:#2458c9;font-weight:bold">满分气势</span>。</p>'
WHERE `name` = '5号排球' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>70g 加厚不卡纸，打印复印都顺，办公室的<span style="color:#4b5563;font-weight:bold">隐形刚需</span>。</p>'
WHERE `name` = 'A4复印纸' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>加厚内页，5 本一包，开会记笔记再也不<span style="color:#d97706;font-weight:bold">东拼西凑</span>。</p>'
WHERE `name` = 'B5笔记本' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>175g 竞技款，飞得稳接得爽，公园里最<span style="color:#1a9c5b;font-weight:bold">拉风的那个圈</span>。</p>'
WHERE `name` = '专业飞盘175g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>0.5mm 顺滑出墨，签字不晕染，一盒 12 支够<span style="color:#4b5563;font-weight:bold">写很久</span>。</p>'
WHERE `name` = '中性笔黑色' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>双拍一套，学生训练刚刚好，课间十分钟也能<span style="color:#e8541e;font-weight:bold">杀两局</span>。</p>'
WHERE `name` = '乒乓球拍双拍' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>多规格随手记，贴电脑贴冰箱，灵感来了<span style="color:#d97706;font-weight:bold">先抓住</span>。</p>'
WHERE `name` = '便利贴套装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>软体轻巧，好飞不摔，周末带娃放风筝，<span style="color:#2458c9;font-weight:bold">娃跑累了你歇着</span>。</p>'
WHERE `name` = '儿童软体风筝' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>棋具齐全，落子有声，陪娃下棋顺便<span style="color:#4b5563;font-weight:bold">找找当年的自己</span>。</p>'
WHERE `name` = '围棋棋盘套装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>居家健身，拉几下胸口就热，健身房年卡<span style="color:#1a9c5b;font-weight:bold">又能往后拖</span>。</p>'
WHERE `name` = '木柄扩胸拉力器' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>室内外通用，手感扎实，周末球场上的<span style="color:#e8541e;font-weight:bold">主力球</span>。</p>'
WHERE `name` = '李宁7号篮球' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>红色批注醒目，改稿划重点一绝，办公桌的<span style="color:#b91c1c;font-weight:bold">第二支笔</span>。</p>'
WHERE `name` = '红色中性笔' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>入门套装，双拍即开打，约人打球不用再<span style="color:#1a9c5b;font-weight:bold">借拍子</span>。</p>'
WHERE `name` = '羽毛球拍双拍' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>耐打耐抽，训练不心疼，一筒能扛<span style="color:#1a9c5b;font-weight:bold">好几次约球</span>。</p>'
WHERE `name` = '耐打羽毛球' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>自动计数，燃脂有数，跳完看一眼数字<span style="color:#1a9c5b;font-weight:bold">很有成就感</span>。</p>'
WHERE `name` = '计数跳绳' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>绿叶鲜嫩，清炒一盘就上桌，家常菜里的<span style="color:#1a9c5b;font-weight:bold">常驻绿</span>。</p>'
WHERE `name` = '上海青小青菜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>细腻嫩滑，入口即化，拌点酱油就是<span style="color:#1a9c5b;font-weight:bold">加班救急菜</span>。</p>'
WHERE `name` = '内酯豆腐400g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>嫩滑豆香，麻婆、清蒸、煮汤都行，冰箱里的<span style="color:#1a9c5b;font-weight:bold">百搭选手</span>。</p>'
WHERE `name` = '嫩豆腐' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>绿叶鲜嫩，蒜蓉一炒就香，配面条配米饭都<span style="color:#1a9c5b;font-weight:bold">很对味</span>。</p>'
WHERE `name` = '小油菜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>菇香浓郁，泡发后煲汤提鲜，厨房里的<span style="color:#8a5a1c;font-weight:bold">鲜味作弊器</span>。</p>'
WHERE `name` = '干香菇250g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>肉质肥厚，煎炒烤都像在吃肉，素食党的<span style="color:#1a9c5b;font-weight:bold">口感外挂</span>。</p>'
WHERE `name` = '新鲜杏鲍菇' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>清脆爽口，凉拌清炒皆宜，咬下去<span style="color:#1a9c5b;font-weight:bold">咔嚓作响</span>。</p>'
WHERE `name` = '新鲜莴笋' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>花球紧实，低脂又管饱，健身餐里的<span style="color:#1a9c5b;font-weight:bold">绿色担当</span>。</p>'
WHERE `name` = '新鲜西兰花' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>辛香鲜嫩，包饺子炒鸡蛋都香，下锅那一下<span style="color:#1a9c5b;font-weight:bold">满屋都是春天</span>。</p>'
WHERE `name` = '新鲜韭菜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>肉厚鲜香，炒青菜煮汤都提味，厨房常备的<span style="color:#8a5a1c;font-weight:bold">鲜味小帮手</span>。</p>'
WHERE `name` = '新鲜香菇' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>沙瓤多汁，生吃凉拌都行，切开那一下<span style="color:#e8541e;font-weight:bold">汁水会说话</span>。</p>'
WHERE `name` = '普罗旺斯西红柿' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>脆嫩清香，现摘直送，拍黄瓜的快乐<span style="color:#1a9c5b;font-weight:bold">不用等</span>。</p>'
WHERE `name` = '本地黄瓜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>鲜嫩脆爽，火锅清炒皆宜，涮三秒就能<span style="color:#1a9c5b;font-weight:bold">捞起来吃</span>。</p>'
WHERE `name` = '油麦菜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>脆甜新鲜，炒丝炖汤都出色，冰箱里的<span style="color:#e8541e;font-weight:bold">橙色底牌</span>。</p>'
WHERE `name` = '胡萝卜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>肉质脆嫩，凉拌炒菜皆宜，口感脆得<span style="color:#4b5563;font-weight:bold">很有存在感</span>。</p>'
WHERE `name` = '鲜木耳300g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>粉糯香甜，炖着吃炒着吃都稳，怎么做都<span style="color:#d97706;font-weight:bold">很难翻车</span>。</p>'
WHERE `name` = '黄心土豆' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>鲜嫩爽脆，炒菜凉拌皆宜，便宜的快乐<span style="color:#1a9c5b;font-weight:bold">一大袋</span>。</p>'
WHERE `name` = '黄豆芽500g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>一碗拉面几分钟出锅，汤头够味，深夜的胃<span style="color:#d97706;font-weight:bold">被妥帖安放</span>。</p>'
WHERE `name` = '兰州牛肉面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>冲泡即饮，清润甜糯，熬夜之后来一杯，<span style="color:#b45309;font-weight:bold">喉咙很领情</span>。</p>'
WHERE `name` = '冰糖银耳羹杯装15g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>经典中的经典，热水一泡满屋香，5 连包管<span style="color:#b91c1c;font-weight:bold">好几顿</span>。</p>'
WHERE `name` = '康师傅红烧牛肉面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>藤椒麻香，酸辣开胃，没胃口的时候<span style="color:#1a9c5b;font-weight:bold">一碗见底</span>。</p>'
WHERE `name` = '康师傅藤椒牛肉面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>闻着上头，吃着更上头，3 袋装够你<span style="color:#b91c1c;font-weight:bold">连嗦三顿</span>。</p>'
WHERE `name` = '柳州螺蛳粉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>多口味混装 12 桶，办公室抽屉一塞，<span style="color:#d97706;font-weight:bold">加班不慌</span>。</p>'
WHERE `name` = '桶装方便面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>芝麻酱一拌，武汉街头味道到家，<span style="color:#b45309;font-weight:bold">嗦一口就懂</span>。</p>'
WHERE `name` = '武汉热干面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>豚骨高汤，汤浓味鲜，泡面也能吃出<span style="color:#b45309;font-weight:bold">认真感</span>。</p>'
WHERE `name` = '汤达人豚骨拉面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酸爽够味，一口下去瞌睡都醒了，5 连包<span style="color:#1a9c5b;font-weight:bold">很经吃</span>。</p>'
WHERE `name` = '统一老坛酸菜面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>不用开火不用锅，15 分钟吃上火锅，宿舍的<span style="color:#b91c1c;font-weight:bold">快乐天花板</span>。</p>'
WHERE `name` = '自热小火锅' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>自热米饭，15 分钟开饭，不用洗碗的<span style="color:#d97706;font-weight:bold">正经一餐</span>。</p>'
WHERE `name` = '自热煲仔饭' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>户外速食，有热水没有都能吃，露营时的<span style="color:#d97706;font-weight:bold">热乎饭</span>。</p>'
WHERE `name` = '自热米饭' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>热水一冲就是一碗汤，配泡面的<span style="color:#1a9c5b;font-weight:bold">灵魂搭档</span>。</p>'
WHERE `name` = '苏伯紫菜蛋花汤杯装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>香辣暖胃，早上来一碗，河南人的<span style="color:#b91c1c;font-weight:bold">早餐乡愁</span>。</p>'
WHERE `name` = '逍遥镇胡辣汤393g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>红薯粉弹牙，酸辣过瘾，3 桶囤着<span style="color:#b91c1c;font-weight:bold">随时开嗦</span>。</p>'
WHERE `name` = '酸辣粉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>奶香浓郁，现摘现发，一颗入口像<span style="color:#d9467a;font-weight:bold">吃了一小口春天</span>。</p>'
WHERE `name` = '丹东99草莓' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>自然熟，绵甜软糯，剥开就是<span style="color:#d97706;font-weight:bold">早餐搭子</span>。</p>'
WHERE `name` = '国产香蕉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>果香浓郁，酸甜多汁，泡水喝像<span style="color:#e8541e;font-weight:bold">自带热带滤镜</span>。</p>'
WHERE `name` = '广西百香果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>脆甜爽口，咬下去咔嚓响，追剧时一颗一颗<span style="color:#1a9c5b;font-weight:bold">停不下来</span>。</p>'
WHERE `name` = '新鲜冬枣' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>2J 大果，脆甜爆汁，5 斤装实现<span style="color:#b91c1c;font-weight:bold">车厘子自由</span>。</p>'
WHERE `name` = '智利车厘子2J 5斤' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>蒜瓣果肉，香甜软糯，剥开那一下<span style="color:#8a5a1c;font-weight:bold">很治愈</span>。</p>'
WHERE `name` = '泰国山竹1kg' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>清甜椰子水，配开椰器，插上吸管就<span style="color:#1a9c5b;font-weight:bold">瞬移海边</span>。</p>'
WHERE `name` = '泰国椰青' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>香甜软糯，核小肉厚，吃一口<span style="color:#e8541e;font-weight:bold">满手都是甜</span>。</p>'
WHERE `name` = '海南贵妃芒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>脆甜多汁，果味浓郁，这个季节<span style="color:#b91c1c;font-weight:bold">不尝尝说不过去</span>。</p>'
WHERE `name` = '烟台大樱桃' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>脆甜多汁，产地直采，苹果界的<span style="color:#e8541e;font-weight:bold">老实实力派</span>。</p>'
WHERE `name` = '烟台红富士苹果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>进口蓝莓，果粉新鲜，一把倒进酸奶里<span style="color:#2458c9;font-weight:bold">瞬间高级</span>。</p>'
WHERE `name` = '秘鲁蓝莓125g/盒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>红心果肉，清甜润肠，切开颜色<span style="color:#d9467a;font-weight:bold">就很上镜</span>。</p>'
WHERE `name` = '红心火龙果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>红心果肉，香甜细腻，勺子一挖<span style="color:#1a9c5b;font-weight:bold">像吃果酱</span>。</p>'
WHERE `name` = '蒲江红心猕猴桃' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酸甜适口，维C丰富，剥一个满手<span style="color:#e8541e;font-weight:bold">橙子香</span>。</p>'
WHERE `name` = '赣南脐橙' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>金枕品种，香甜绵密，爱的人<span style="color:#d97706;font-weight:bold">闻着就沦陷</span>。</p>'
WHERE `name` = '金枕头榴莲1个' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>清香脆甜，无籽，一颗接一颗<span style="color:#1a9c5b;font-weight:bold">根本停不下</span>。</p>'
WHERE `name` = '阳光玫瑰葡萄' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>沙瓤多汁，夏日必备，勺子挖中间那口<span style="color:#e8541e;font-weight:bold">最甜</span>。</p>'
WHERE `name` = '麒麟西瓜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>0 添加，清甜解渴，运动完来一瓶，<span style="color:#1a9c5b;font-weight:bold">比功能饮料温柔</span>。</p>'
WHERE `name` = '100%椰子水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>早餐新宠，嚼劲十足，夹蛋夹芝士都<span style="color:#b45309;font-weight:bold">很上镜</span>。</p>'
WHERE `name` = '全麦贝果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>冷萃冻干，3 秒即溶，冷水一冲就化开，夏天的<span style="color:#8a5a1c;font-weight:bold">续命冰咖啡</span>。</p>'
WHERE `name` = '冷萃冻干咖啡' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>新品低糖，冷萃顺口不酸涩，冰箱里常备一瓶，<span style="color:#8a5a1c;font-weight:bold">随拿随喝</span>。</p>'
WHERE `name` = '冷萃咖啡' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>开袋即食，轻食党的<span style="color:#1a9c5b;font-weight:bold">体面午餐</span>，酱一拌就是一顿。</p>'
WHERE `name` = '即食蔬菜沙拉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>秋梨熬制，温水一冲润喉，换季嗓子干的时候<span style="color:#b45309;font-weight:bold">很贴心</span>。</p>'
WHERE `name` = '同仁堂秋梨膏180g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>当日鲜配，奶香醇厚，早餐桌上这瓶<span style="color:#2458c9;font-weight:bold">带着新鲜感</span>。</p>'
WHERE `name` = '巴氏鲜牛奶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>手打虾滑，下锅一滚就弹，火锅局里的<span style="color:#b91c1c;font-weight:bold">抢手货</span>。</p>'
WHERE `name` = '手打鲜虾滑' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>本周上新，限量尝鲜，脆甜带玫瑰香，<span style="color:#1a9c5b;font-weight:bold">吃过的都懂</span>。</p>'
WHERE `name` = '晴王阳光玫瑰' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>有机认证，花球紧实，给健身餐和娃的饭碗<span style="color:#1a9c5b;font-weight:bold">加点安心</span>。</p>'
WHERE `name` = '有机西兰花' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>进口即食，绵密细腻，抹面包做沙拉都<span style="color:#1a9c5b;font-weight:bold">很会搭</span>。</p>'
WHERE `name` = '牛油果' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>桂花清香，软糯清甜，配一杯热茶就是<span style="color:#b45309;font-weight:bold">江南下午茶</span>。</p>'
WHERE `name` = '白糖桂花糕300g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>当季新品，籽软能嚼，剥一碗边追剧边吃，<span style="color:#b91c1c;font-weight:bold">红得很喜庆</span>。</p>'
WHERE `name` = '突尼斯软籽石榴' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>红心果肉，香甜多汁，挖着吃像在吃<span style="color:#1a9c5b;font-weight:bold">天然果酱</span>。</p>'
WHERE `name` = '红心猕猴桃' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>迁西板栗，香甜软糯，剥开一个接一个，<span style="color:#8a5a1c;font-weight:bold">秋天就这口味</span>。</p>'
WHERE `name` = '迁西糖炒栗子500g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>膏满黄肥，礼盒装体面，自吃是<span style="color:#d97706;font-weight:bold">一年一度的仪式感</span>。</p>'
WHERE `name` = '阳澄湖大闸蟹礼盒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>无糖 0 脂，茶香清爽解腻，火锅烤肉的<span style="color:#1a9c5b;font-weight:bold">最佳搭档</span>。</p>'
WHERE `name` = '三得利乌龙茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>茉莉香乌龙，清爽回甘，冰镇后喝一口，<span style="color:#1a9c5b;font-weight:bold">烦心事都淡了</span>。</p>'
WHERE `name` = '三得利茉莉乌龙' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>原叶萃取，茶味干净，不想喝甜的时候<span style="color:#1a9c5b;font-weight:bold">就靠它</span>。</p>'
WHERE `name` = '东方树叶绿茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>提神抗疲劳，加班开车来一瓶，<span style="color:#d97706;font-weight:bold">困意当场退散</span>。</p>'
WHERE `name` = '东鹏特饮' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>补充能量，提神醒脑，忙到脚不沾地时的<span style="color:#d97706;font-weight:bold">外挂</span>。</p>'
WHERE `name` = '中沃体质能量' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>氨基酸牛磺酸，困了累了来一罐，<span style="color:#d97706;font-weight:bold">状态拉回来</span>。</p>'
WHERE `name` = '乐虎功能饮料' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>冰爽红茶，柠檬清香，配炸鸡配烧烤都<span style="color:#e8541e;font-weight:bold">很解腻</span>。</p>'
WHERE `name` = '今麦郎冰红茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>橙蜜风味，经典国货，喝一口是<span style="color:#e8541e;font-weight:bold">满满的回忆杀</span>。</p>'
WHERE `name` = '健力宝橙蜜味' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>0 糖 0 脂，气泡够足，想喝汽水又怕胖时的<span style="color:#1a9c5b;font-weight:bold">正解</span>。</p>'
WHERE `name` = '元气森林气泡水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>整箱囤货，家里办公室各一箱，<span style="color:#2458c9;font-weight:bold">喝水这件事不将就</span>。</p>'
WHERE `name` = '农夫山泉天然水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>低矿淡钠，母婴适用，冲奶泡粉都<span style="color:#2458c9;font-weight:bold">更安心</span>。</p>'
WHERE `name` = '农夫山泉婴儿水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>果味茶饮，茶香果香都有，<span style="color:#8a5a1c;font-weight:bold">选择困难症的救星</span>。</p>'
WHERE `name` = '农夫山泉茶π' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>西安味道，橙味汽水，小小一瓶，<span style="color:#e8541e;font-weight:bold">配泡馍最地道</span>。</p>'
WHERE `name` = '冰峰橙味汽水200ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>经典原味，冰镇后第一口<span style="color:#b91c1c;font-weight:bold">值一块钱</span>，12 瓶管够。</p>'
WHERE `name` = '可口可乐' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>0 糖可乐，快乐不减，想喝又怕糖时的<span style="color:#b91c1c;font-weight:bold">妥协艺术</span>。</p>'
WHERE `name` = '可口可乐零度' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>电解质补充，运动后一瓶，<span style="color:#2458c9;font-weight:bold">出汗也不虚</span>。</p>'
WHERE `name` = '外星人电解质水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>葡萄汁饮，清爽甜润，1.25L 大瓶，聚餐时<span style="color:#8a5a1c;font-weight:bold">一倒就有排面</span>。</p>'
WHERE `name` = '多上葡萄汁1.25L' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>无糖原味，气泡细密，调酒或者单喝都<span style="color:#2458c9;font-weight:bold">很清口</span>。</p>'
WHERE `name` = '屈臣氏苏打水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>柠檬红茶，冰爽解腻，加班来一瓶，<span style="color:#e8541e;font-weight:bold">甜得刚刚好</span>。</p>'
WHERE `name` = '康师傅冰红茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>绿茶清香，解腻爽口，大餐之后的<span style="color:#1a9c5b;font-weight:bold">清爽收尾</span>。</p>'
WHERE `name` = '康师傅绿茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>日常饮用，整箱更划算，家里<span style="color:#2458c9;font-weight:bold">永远缺一瓶水</span>。</p>'
WHERE `name` = '怡宝纯净水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>多种维 B，能量补给，熬夜后的<span style="color:#d97706;font-weight:bold">体面续命</span>。</p>'
WHERE `name` = '日加满功能饮料' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>星选拿铁，开盖即饮，早八人的<span style="color:#8a5a1c;font-weight:bold">移动咖啡店</span>。</p>'
WHERE `name` = '星巴克即饮咖啡' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>咖啡香浓，冰爽顺滑，夏天下午的<span style="color:#8a5a1c;font-weight:bold">小放纵</span>。</p>'
WHERE `name` = '星巴克星冰乐' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>经典可乐，气泡够劲，冰镇配烧烤，<span style="color:#2458c9;font-weight:bold">快乐加倍</span>。</p>'
WHERE `name` = '百事可乐' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>轻怡低卡，清爽解渴，控糖期的<span style="color:#2458c9;font-weight:bold">快乐替代</span>。</p>'
WHERE `name` = '百事轻怡可乐' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>累了困了喝红牛，这句话从小听到大，<span style="color:#b91c1c;font-weight:bold">还真管用</span>。</p>'
WHERE `name` = '红牛维生素功能饮料' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>清爽低糖，茶味自然，油腻之后的<span style="color:#1a9c5b;font-weight:bold">一口清新</span>。</p>'
WHERE `name` = '统一绿茶' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>橙汁汽水，家庭分享装，聚餐桌上<span style="color:#e8541e;font-weight:bold">老少通吃</span>。</p>'
WHERE `name` = '统一鲜橙多2L' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>补充能量，600ml 大瓶，干完活来一口<span style="color:#d97706;font-weight:bold">很得劲</span>。</p>'
WHERE `name` = '维生素功能饮料' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>果味汽水，橙香扑鼻，童年的<span style="color:#e8541e;font-weight:bold">快乐水</span>还那个味。</p>'
WHERE `name` = '美年达橙味' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>青柠口味，清爽回甘，运动出汗后的<span style="color:#1a9c5b;font-weight:bold">第一口最爽</span>。</p>'
WHERE `name` = '脉动维生素饮料' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>橙味汽水，气泡带劲，配炸物<span style="color:#e8541e;font-weight:bold">快乐翻倍</span>。</p>'
WHERE `name` = '芬达橙味' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>丝滑拿铁，开盖就喝，通勤路上<span style="color:#8a5a1c;font-weight:bold">先把自己叫醒</span>。</p>'
WHERE `name` = '雀巢即饮咖啡' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>柠檬清爽，气泡够足，油腻之后<span style="color:#1a9c5b;font-weight:bold">来一口透心凉</span>。</p>'
WHERE `name` = '雪碧' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>4L 家庭装，煮饭泡茶都方便，<span style="color:#2458c9;font-weight:bold">一桶能用好几天</span>。</p>'
WHERE `name` = '饮用纯净水' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>丝滑拿铁，即开即饮，会议前来一瓶，<span style="color:#8a5a1c;font-weight:bold">发言都更有底气</span>。</p>'
WHERE `name` = '麦斯威尔拿铁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>0 糖 0 脂，苦得清醒，减脂期的<span style="color:#8a5a1c;font-weight:bold">精神支柱</span>。</p>'
WHERE `name` = '黑咖啡饮料' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>刺身级，冷链直达，切一片蘸酱油，<span style="color:#d97706;font-weight:bold">在家吃出日料店</span>。</p>'
WHERE `name` = '冰鲜三文鱼柳' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>海捕带鱼，段装方便，煎到两面金黄，<span style="color:#2458c9;font-weight:bold">香得邻居敲门</span>。</p>'
WHERE `name` = '冷冻带鱼段' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>去线虾仁，Q 弹，炒蛋滑蛋都省事，<span style="color:#b91c1c;font-weight:bold">懒人也有海鲜吃</span>。</p>'
WHERE `name` = '冷冻青虾仁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>吐沙干净，爆炒花甲，配一罐冰啤，<span style="color:#2458c9;font-weight:bold">夏夜就齐了</span>。</p>'
WHERE `name` = '鲜活花蛤' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>烧烤配菜里的<span style="color:#d97706;font-weight:bold">万能搭子</span>，烤到边缘焦脆，撒点辣椒面就封神。</p>'
WHERE `name` = '土豆片' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>牛油浓香，麻辣过瘾，一包下锅，<span style="color:#b91c1c;font-weight:bold">家里秒变火锅店</span>。</p>'
WHERE `name` = '德庄牛油火锅底料300g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>手工牛油，香辣浓郁，煮开那一下<span style="color:#b91c1c;font-weight:bold">全楼都知道你在吃火锅</span>。</p>'
WHERE `name` = '手工牛油火锅底料500g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>鲜嫩爽滑，火锅绝配，涮十秒就熟，<span style="color:#1a9c5b;font-weight:bold">吸满汤底最香</span>。</p>'
WHERE `name` = '新鲜金针菇' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>一蘸提味，麻酱香浓，在家吃火锅也有<span style="color:#b45309;font-weight:bold">店里的味道</span>。</p>'
WHERE `name` = '海底捞火锅蘸料120g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>浓香番茄，酸甜开胃，不吃辣星人的<span style="color:#e8541e;font-weight:bold">火锅自由</span>。</p>'
WHERE `name` = '海底捞番茄火锅底料' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>台式烤肠，焦香弹嫩，空气炸锅一转，<span style="color:#b91c1c;font-weight:bold">香到邻居来敲门</span>。</p>'
WHERE `name` = '火山石烤肠' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>十种丸子一袋齐，选择困难症的<span style="color:#b91c1c;font-weight:bold">火锅答案</span>。</p>'
WHERE `name` = '火锅丸子组合' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>原切牛肉，涮三秒就熟，火锅桌上的<span style="color:#b91c1c;font-weight:bold">硬通货</span>。</p>'
WHERE `name` = '火锅肥牛卷' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>北方麻酱，香浓醇厚，蘸毛肚蘸白菜都<span style="color:#b45309;font-weight:bold">很地道</span>。</p>'
WHERE `name` = '火锅蘸料麻酱' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>烧烤水煮都行，甜糯爆汁，<span style="color:#d97706;font-weight:bold">啃起来最解压</span>。</p>'
WHERE `name` = '甜玉米' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>内蒙羊肉，鲜嫩不膻，涮完蘸麻酱，<span style="color:#b91c1c;font-weight:bold">冬天就等这一口</span>。</p>'
WHERE `name` = '羔羊肉卷' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>腌制入味，烤制即食，阳台支个炉子就是<span style="color:#b91c1c;font-weight:bold">深夜食堂</span>。</p>'
WHERE `name` = '腌制牛肉串' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>烧烤半成品，腌得刚好，撒孜然那一下<span style="color:#b91c1c;font-weight:bold">香味直冲天灵盖</span>。</p>'
WHERE `name` = '腌制羊肉串' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>北方麻酱，香浓醇厚，火锅蘸料界的<span style="color:#b45309;font-weight:bold">老字号</span>。</p>'
WHERE `name` = '草原红太阳麻酱蘸料' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>谷饲肥牛，火锅必备，肥瘦相间，<span style="color:#b91c1c;font-weight:bold">涮一片香一片</span>。</p>'
WHERE `name` = '谷饲肥牛片' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>五香入味，即食卤蛋，泡面加一个，<span style="color:#8a5a1c;font-weight:bold">立刻升级成套餐</span>。</p>'
WHERE `name` = '五香卤蛋' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>开袋即食，酱香入味，切片下酒，<span style="color:#b91c1c;font-weight:bold">朋友来了有面子</span>。</p>'
WHERE `name` = '五香酱牛肉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>火锅三明治都行，切片一煎，<span style="color:#b91c1c;font-weight:bold">香得很有年代感</span>。</p>'
WHERE `name` = '午餐肉罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>特产卤味，咸香鲜嫩，半只切好，<span style="color:#d97706;font-weight:bold">配饭配酒都合适</span>。</p>'
WHERE `name` = '南京盐水鸭' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>卤香入味，酱香浓郁，啃起来<span style="color:#b91c1c;font-weight:bold">越啃越上头</span>。</p>'
WHERE `name` = '卤酱鸭头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酱香微辣，软糯脱骨，追剧时啃一袋，<span style="color:#b91c1c;font-weight:bold">剧情都变好看了</span>。</p>'
WHERE `name` = '卤鸡爪' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>脆爽卤藕，独立小包，办公室解馋<span style="color:#1a9c5b;font-weight:bold">不脏手</span>。</p>'
WHERE `name` = '口口巧卤藕片20包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>麻辣卤香，啃到停不下来，追剧零食的<span style="color:#b91c1c;font-weight:bold">头号选手</span>。</p>'
WHERE `name` = '善味阁卤鸭脖400g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>脱骨入味，一撕就开，带一只回家，<span style="color:#d97706;font-weight:bold">晚饭就有着落了</span>。</p>'
WHERE `name` = '德州扒鸡' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>溏心皮蛋，切瓣拌豆腐，<span style="color:#4b5563;font-weight:bold">下酒下粥都稳</span>。</p>'
WHERE `name` = '松花皮蛋' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>下饭小菜，脆爽咸鲜，白粥配它，<span style="color:#1a9c5b;font-weight:bold">简单但很满足</span>。</p>'
WHERE `name` = '涪陵榨菜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酸爽开胃，下饭神器，炒肉末一拌，<span style="color:#1a9c5b;font-weight:bold">米饭得多煮一碗</span>。</p>'
WHERE `name` = '湖南酸豆角' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>卤香鸭翅，越啃越香，周末窝沙发，<span style="color:#b91c1c;font-weight:bold">一袋正好刷两集</span>。</p>'
WHERE `name` = '牧童卤鸭翅散装500g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>糖醋爽脆，解腻开胃，吃饺子火锅时的<span style="color:#b45309;font-weight:bold">隐藏彩蛋</span>。</p>'
WHERE `name` = '糖醋糖蒜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>卤香豆干，独立小包，嘴馋时来一包，<span style="color:#b45309;font-weight:bold">素的也能很香</span>。</p>'
WHERE `name` = '老李卤豆干小包装500g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>开胃酱菜，酸甜爽脆，油腻之后来两片，<span style="color:#1a9c5b;font-weight:bold">胃口又回来了</span>。</p>'
WHERE `name` = '酸甜脆萝卜' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酸甜开胃，冰镇后更爽，吃撑了来一碗，<span style="color:#b91c1c;font-weight:bold">胃表示舒服了</span>。</p>'
WHERE `name` = '山楂罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>意式番茄，烹饪百搭，煮面炖肉都<span style="color:#e8541e;font-weight:bold">很出味</span>。</p>'
WHERE `name` = '意大利番茄罐头400g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>杨梅酸甜，开胃解腻，夏天冰一碗，<span style="color:#b91c1c;font-weight:bold">酸得刚刚好</span>。</p>'
WHERE `name` = '杨梅罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>椰果清甜，口感 Q 弹，配甜品配酸奶都<span style="color:#1a9c5b;font-weight:bold">很加分</span>。</p>'
WHERE `name` = '椰果罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>沙拉三明治都行，开罐即食，<span style="color:#2458c9;font-weight:bold">健身餐的蛋白质搭子</span>。</p>'
WHERE `name` = '油浸金枪鱼罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>即食玉米，粒粒饱满，拌沙拉做浓汤都<span style="color:#d97706;font-weight:bold">很省事</span>。</p>'
WHERE `name` = '甜玉米粒罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>甜玉米粒，即食方便，一罐在手，<span style="color:#d97706;font-weight:bold">早餐配菜都不慌</span>。</p>'
WHERE `name` = '甜玉米罐头425g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>果肉饱满，糖水清甜，冰镇后吃，<span style="color:#d97706;font-weight:bold">像回到小时候</span>。</p>'
WHERE `name` = '砀山黄桃罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>童年味道，一瓣一瓣甜，冬天来一碗，<span style="color:#e8541e;font-weight:bold">暖胃又暖心</span>。</p>'
WHERE `name` = '糖水橘子罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>糖水菠萝，果肉饱满，酸甜清爽，<span style="color:#d97706;font-weight:bold">做甜品很省事</span>。</p>'
WHERE `name` = '糖水菠萝罐头450g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>红烧猪肉，即开即食，加热浇饭，<span style="color:#b91c1c;font-weight:bold">一个人也吃得像样</span>。</p>'
WHERE `name` = '红烧猪肉罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>热带果香，酸甜多汁，配酸奶或者直接吃，<span style="color:#d97706;font-weight:bold">都很夏天</span>。</p>'
WHERE `name` = '菠萝罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>下饭菜，豆豉咸香，配白粥能多吃<span style="color:#b91c1c;font-weight:bold">两碗</span>。</p>'
WHERE `name` = '豆豉鲮鱼罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>鹰嘴豆粉糯，低脂健康，拌沙拉做泥都<span style="color:#1a9c5b;font-weight:bold">很洋气</span>。</p>'
WHERE `name` = '鹰嘴豆罐头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>整只冷冻，煲汤红烧都行，周末炖一锅，<span style="color:#d97706;font-weight:bold">满屋都是家的味道</span>。</p>'
WHERE `name` = '三黄鸡' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>五香入味，豆香浓郁，切片凉拌或者炒，<span style="color:#b45309;font-weight:bold">便宜又下饭</span>。</p>'
WHERE `name` = '五香豆干200g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>火锅首选，鲜嫩不膻，一涮就打卷，<span style="color:#b91c1c;font-weight:bold">冬天就该这么吃</span>。</p>'
WHERE `name` = '内蒙羔羊肉卷' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>翅中肥美，烤炸卤皆宜，空气炸锅一丢，<span style="color:#b91c1c;font-weight:bold">追剧夜宵有了</span>。</p>'
WHERE `name` = '冷冻鸡翅中' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>薄韧豆香，凉拌炒菜皆宜，卷点黄瓜丝，<span style="color:#b45309;font-weight:bold">清爽又顶饱</span>。</p>'
WHERE `name` = '千张豆腐皮200g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>卤香入味，一口一个，泡面火锅都<span style="color:#8a5a1c;font-weight:bold">很配</span>。</p>'
WHERE `name` = '卤鹌鹑蛋30枚' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>精修牛腩，炖煮软烂，一锅番茄牛腩，<span style="color:#b91c1c;font-weight:bold">米饭都显得不够</span>。</p>'
WHERE `name` = '原切牛腩块' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>筋多肉嫩，卤炖首选，切片凉拌，<span style="color:#b91c1c;font-weight:bold">下酒很体面</span>。</p>'
WHERE `name` = '原切牛腱子肉' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>肉质紧实，红烧炖汤都行，一块大排，<span style="color:#b91c1c;font-weight:bold">午饭就稳了</span>。</p>'
WHERE `name` = '新鲜猪大排' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>肉质细嫩，烤制炖汤皆宜，撒把孜然，<span style="color:#b91c1c;font-weight:bold">香味藏不住</span>。</p>'
WHERE `name` = '新鲜羊排' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>蛋黄流油起沙，配白粥一绝，<span style="color:#d97706;font-weight:bold">咸香到舔壳</span>。</p>'
WHERE `name` = '流油咸鸭蛋8枚' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>肥瘦相间，烧烤红烧，煎到微焦，<span style="color:#b91c1c;font-weight:bold">肥而不腻最香</span>。</p>'
WHERE `name` = '猪五花肉片' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>谷物喂养，新鲜直达，煎炒蒸煮都行，<span style="color:#d97706;font-weight:bold">冰箱里的底气</span>。</p>'
WHERE `name` = '鲜鸡蛋' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>空气炸锅同款，200 度 15 分钟，<span style="color:#d97706;font-weight:bold">在家薯条自由</span>。</p>'
WHERE `name` = '冷冻粗薯条' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>冷冻锁鲜，解冻即用，炒饭炒蛋都<span style="color:#2458c9;font-weight:bold">很出彩</span>。</p>'
WHERE `name` = '冷冻虾仁' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>海捕鲅鱼，肉质紧实，红烧或者包饺子，<span style="color:#2458c9;font-weight:bold">鲜得很实在</span>。</p>'
WHERE `name` = '冻鲅鱼段' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>南瓜清甜，软糯可口，煎一煎当早餐，<span style="color:#d97706;font-weight:bold">甜得很温柔</span>。</p>'
WHERE `name` = '南瓜饼360g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>火山石烤肠，焦香弹嫩，切花刀一烤，<span style="color:#b91c1c;font-weight:bold">夜市就开在你家</span>。</p>'
WHERE `name` = '台式烤肠' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>奥尔良风味，外酥里嫩，微波两分钟，<span style="color:#b91c1c;font-weight:bold">比外卖快</span>。</p>'
WHERE `name` = '奥尔良鸡肉卷' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>流沙奶黄，香甜绵软，蒸一笼当早餐，<span style="color:#d97706;font-weight:bold">全家都爱吃</span>。</p>'
WHERE `name` = '奶黄包12只装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>葱香松软，蒸热即食，配粥配豆浆都<span style="color:#b45309;font-weight:bold">很妥帖</span>。</p>'
WHERE `name` = '安井葱油花卷1kg' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>皮薄馅大，鲜肉多汁，早上蒸几个，<span style="color:#b91c1c;font-weight:bold">比楼下早餐店省事</span>。</p>'
WHERE `name` = '安井鲜肉包720g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酥脆素馅，炸到金黄，过年过节或者<span style="color:#d97706;font-weight:bold">嘴馋都能炸</span>。</p>'
WHERE `name` = '春卷' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>火锅食材，肥瘦均匀，涮几秒就打卷，<span style="color:#b91c1c;font-weight:bold">一盒不够吃</span>。</p>'
WHERE `name` = '牛肉卷' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>外酥里糯，芝麻香甜，炸一锅，<span style="color:#b45309;font-weight:bold">童年味道回来了</span>。</p>'
WHERE `name` = '芝麻麻团' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>家常猪肉白菜，一袋 1kg，懒得做饭时<span style="color:#d97706;font-weight:bold">煮一锅就解决</span>。</p>'
WHERE `name` = '速冻水饺' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>无骨嫩滑，煎几分钟就香，做汉堡做盖饭<span style="color:#b91c1c;font-weight:bold">都很像样</span>。</p>'
WHERE `name` = '香煎鸡排' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>香芋绵甜，外脆里糯，炸一盘追剧<span style="color:#8a5a1c;font-weight:bold">越吃越上头</span>。</p>'
WHERE `name` = '香芋地瓜丸' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>Q 弹火锅丸子，煮汤炒菜都行，<span style="color:#2458c9;font-weight:bold">一口一个很治愈</span>。</p>'
WHERE `name` = '鱼豆腐' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>劲道醇和，经典保健酒，老友小聚，<span style="color:#b91c1c;font-weight:bold">慢慢喝慢慢聊</span>。</p>'
WHERE `name` = '中国劲酒680ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>浓香经典，入口绵柔，重要饭局上，<span style="color:#b91c1c;font-weight:bold">它一上桌就有分量</span>。</p>'
WHERE `name` = '五粮液浓香型' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>传统黄酒，温润醇厚，温一壶配蟹，<span style="color:#8a5a1c;font-weight:bold">秋天就有了仪式感</span>。</p>'
WHERE `name` = '古越龙山黄酒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>绍兴黄酒，温润醇厚，小酌一杯，<span style="color:#8a5a1c;font-weight:bold">暖意从胃里散开</span>。</p>'
WHERE `name` = '女儿红绍兴黄酒500ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>贺兰山产区，果香醇厚，配牛排配奶酪，<span style="color:#b91c1c;font-weight:bold">在家也有西餐感</span>。</p>'
WHERE `name` = '宁夏干红葡萄酒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>餐酒优选，果香顺口，聚餐开一瓶，<span style="color:#b91c1c;font-weight:bold">气氛立刻不一样</span>。</p>'
WHERE `name` = '张裕干红葡萄酒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>滋补养生酒，经典配方，送长辈，<span style="color:#8a5a1c;font-weight:bold">体面又贴心</span>。</p>'
WHERE `name` = '椰岛鹿龟酒轻奢版' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>清香小酒，100ml 一口一瓶，小聚微醺<span style="color:#2458c9;font-weight:bold">刚刚好</span>。</p>'
WHERE `name` = '江小白' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>清香白酒，绵甜爽净，入口干净，<span style="color:#2458c9;font-weight:bold">喝完很舒服</span>。</p>'
WHERE `name` = '汾酒青花20' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>三年陈酿，口感柔和，晚饭温一杯，<span style="color:#8a5a1c;font-weight:bold">日子都慢下来</span>。</p>'
WHERE `name` = '沙洲优黄三年陈500ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>绵柔浓香，纯粮酿造，朋友聚餐，<span style="color:#2458c9;font-weight:bold">好喝不上头</span>。</p>'
WHERE `name` = '洋河大曲' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>美式拉格，冰镇够爽，看球撸串，<span style="color:#d97706;font-weight:bold">一箱正好</span>。</p>'
WHERE `name` = '百威啤酒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>陈年黄酒，香气醇厚，做菜或者小酌，<span style="color:#8a5a1c;font-weight:bold">都很够味</span>。</p>'
WHERE `name` = '绍兴花雕酒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>酱香白酒，入口醇厚，重要时刻开一瓶，<span style="color:#b91c1c;font-weight:bold">面子情谊都在</span>。</p>'
WHERE `name` = '茅台迎宾酒' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>清爽口感，冰镇一口，烧烤摊的灵魂<span style="color:#d97706;font-weight:bold">就是它</span>。</p>'
WHERE `name` = '雪花啤酒勇闯天涯' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>冰爽经典，泡沫细腻，配海鲜配烧烤，<span style="color:#1a9c5b;font-weight:bold">夏天才算完整</span>。</p>'
WHERE `name` = '青岛啤酒经典' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>起酥层次，奶香浓郁，配咖啡当早餐，<span style="color:#b45309;font-weight:bold">像在面包店上班</span>。</p>'
WHERE `name` = '丹麦起酥面包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>绿豆清凉，细腻绵密，下午茶来两块，<span style="color:#1a9c5b;font-weight:bold">甜得刚刚好</span>。</p>'
WHERE `name` = '五芳斋绿豆糕100g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>低脂代餐，麦香扎实，夹蛋夹菜，<span style="color:#b45309;font-weight:bold">减脂餐也能好吃</span>。</p>'
WHERE `name` = '全麦面包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>天津麻花，酥脆香甜，掰着吃更香，<span style="color:#b45309;font-weight:bold">办公室分享很受欢迎</span>。</p>'
WHERE `name` = '天津大麻花500g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>乳酸菌味夹心，松软香甜，早上抓一包，<span style="color:#b45309;font-weight:bold">路上就能吃</span>。</p>'
WHERE `name` = '夹心吐司面包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>外皮酥香，馅料软糯，配茶当点心，<span style="color:#b45309;font-weight:bold">一口一个满足</span>。</p>'
WHERE `name` = '桃李老婆饼袋装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>传统桃酥，酥松掉渣，泡杯茶，<span style="color:#8a5a1c;font-weight:bold">长辈的快乐很简单</span>。</p>'
WHERE `name` = '稻香村桃酥300g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>肉松夹心，咸香松软，早餐来两片，<span style="color:#d97706;font-weight:bold">咸口党的胜利</span>。</p>'
WHERE `name` = '肉松吐司' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>肉松咸香，蛋糕松软，一口咸一口甜，<span style="color:#d97706;font-weight:bold">追剧停不下来</span>。</p>'
WHERE `name` = '肉松戚风蛋糕' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>咸味代餐，酥脆不腻，办公室抽屉常备，<span style="color:#b45309;font-weight:bold">饿了随时垫一口</span>。</p>'
WHERE `name` = '苏打饼干' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>奶香蒸蛋糕，松软不上火，早餐配牛奶，<span style="color:#b45309;font-weight:bold">简单又体面</span>。</p>'
WHERE `name` = '蒸蛋糕' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>注心蛋糕，一口爆浆，小时候的<span style="color:#d97706;font-weight:bold">课间快乐</span>又回来了。</p>'
WHERE `name` = '蛋黄派' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>豆沙香甜，松软可口，掰开吃更有<span style="color:#b45309;font-weight:bold">层次感</span>。</p>'
WHERE `name` = '豆沙面包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>巧克力豆曲奇，酥脆香甜，牛奶一配，<span style="color:#8a5a1c;font-weight:bold">下午茶就齐了</span>。</p>'
WHERE `name` = '趣多多曲奇' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>Q 弹黑芝麻，越嚼越香，4 个一袋，<span style="color:#4b5563;font-weight:bold">解馋刚刚好</span>。</p>'
WHERE `name` = '麻薯面包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>908g 礼盒装，黄油香浓，送人自吃都<span style="color:#b45309;font-weight:bold">很有排面</span>。</p>'
WHERE `name` = '黄油曲奇饼干' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>嫩滑豆香，即开即食，咸甜自己定，<span style="color:#b45309;font-weight:bold">早餐不再将就</span>。</p>'
WHERE `name` = '即食豆腐脑' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>小米养胃，即开即食，加班回来喝一杯，<span style="color:#d97706;font-weight:bold">胃先被哄好</span>。</p>'
WHERE `name` = '小米粥杯装350g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>爽滑耐煮，煮一碗清汤面，加个蛋，<span style="color:#b45309;font-weight:bold">深夜也很治愈</span>。</p>'
WHERE `name` = '手工挂面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>筋道爽滑，拌酱浇卤都好吃，<span style="color:#b45309;font-weight:bold">一碗就有满足感</span>。</p>'
WHERE `name` = '手擀面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>皮蛋瘦肉，咸香绵稠，即开即食，<span style="color:#4b5563;font-weight:bold">熬夜党的温柔</span>。</p>'
WHERE `name` = '皮蛋瘦肉粥杯装' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>手工老面，劲道有嚼头，夹菜夹肉，<span style="color:#b45309;font-weight:bold">北方人的踏实感</span>。</p>'
WHERE `name` = '老面馒头' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>葱香松软，蒸一蒸就上桌，配粥配豆浆都<span style="color:#1a9c5b;font-weight:bold">很顺口</span>。</p>'
WHERE `name` = '花卷' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>葱油酱香，拌一拌就开吃，懒人也能<span style="color:#b45309;font-weight:bold">十分钟开饭</span>。</p>'
WHERE `name` = '葱油拌面' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>外酥里嫩，葱香扑鼻，平底锅一煎，<span style="color:#b45309;font-weight:bold">早餐就有了</span>。</p>'
WHERE `name` = '葱油饼' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>香浓豆浆，早餐必备，配包子配油条都<span style="color:#d97706;font-weight:bold">很中式</span>。</p>'
WHERE `name` = '袋装豆浆300ml' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>五谷八宝，香甜绵软，赶时间拎一罐，<span style="color:#8a5a1c;font-weight:bold">早餐也认真</span>。</p>'
WHERE `name` = '达利园八宝粥360g' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>皮薄馅足，蒸后爆汁，小心烫嘴，<span style="color:#b91c1c;font-weight:bold">一笼不够吃</span>。</p>'
WHERE `name` = '速冻小笼包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>韭菜鲜香，外皮酥脆，煎到两面金黄，<span style="color:#1a9c5b;font-weight:bold">比路边摊干净</span>。</p>'
WHERE `name` = '韭菜盒子' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>皮薄馅大，早餐首选，蒸八分钟，<span style="color:#b91c1c;font-weight:bold">家里就有包子铺</span>。</p>'
WHERE `name` = '鲜肉包' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>现包现发，馅香皮薄，煮一锅，<span style="color:#b91c1c;font-weight:bold">蘸醋才是灵魂</span>。</p>'
WHERE `name` = '鲜肉水饺' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');
UPDATE `goods_spu` SET `introduction` = '<p>整颗虾仁，皮滑汤鲜，20 只一盒，<span style="color:#2458c9;font-weight:bold">早餐宵夜都能打</span>。</p>'
WHERE `name` = '鲜虾云吞' AND `del_flag` = '0' AND (`introduction` IS NULL OR `introduction` = '');

SET FOREIGN_KEY_CHECKS = 1;
