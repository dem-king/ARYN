-- 悦航购船供化验收种子数据（Cloud 微服务模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 特性：可重复执行；仅按 96x 人工编排前缀清理本脚本自身的种子数据，不触碰存量业务数据
-- 内容：
--   1) 船舶成员：把 3 个商城用户绑定到悦航1号（发起人 / 采购确认人 / 普通船员）
--   2) 靠港计划：保证至少有 2 个可用靠港（46 号脚本用的是相对日期，会随时间自然过期）
--   3) 船舶物料类目：1 个一级 + 6 个二级
--   4) 28 个船供商品（SPU + SKU + ship_goods_profile + ship_sku_profile）
--
-- 这 28 个商品是按验收清单的需要刻意设计的：
--   · 26 个上架（25 个 sale_scope=3 个人+船供、1 个 sale_scope=2 仅船供）→ 超过 20 条，可验证分页
--   · 1 个下架（status=0）→ 验证船供目录不得出现下架商品
--   · 编码覆盖 IMPA / ISSA / 内部编码 / 条码 / 英文名 / 搜索别名 → 验证搜索各自可命中
--   · MOQ 与步长组合多样，且全部满足「MOQ 是步长整数倍」→ 验证数量规则
--   · 储存条件覆盖 常温/冷藏/冷冻/危险品 → 验证详情页储存条件展示
--
-- 执行：mysql -u root -p aryn_boot < 62ship_supply_seed_acceptance.sql
-- 清理：见文末「清理本脚本数据」段落，或使用 dev-tools/seed-acceptance-data.sh --clean

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 0. 清理本脚本历史种子（幂等）
-- ===========================================================================
DELETE FROM `ship_sku_profile` WHERE `id` LIKE '967%';
DELETE FROM `ship_goods_profile` WHERE `id` LIKE '966%';
DELETE FROM `goods_sku` WHERE `id` LIKE '965%';
DELETE FROM `goods_spu` WHERE `id` LIKE '964%';
DELETE FROM `goods_category` WHERE `id` LIKE '963%';
DELETE FROM `vessel_bind_apply` WHERE `id` LIKE '969%';
DELETE FROM `user_info` WHERE `id` LIKE '97%';
DELETE FROM `vessel_member` WHERE `id` LIKE '962%';
DELETE FROM `vessel_call` WHERE `id` LIKE '968%';

-- ===========================================================================
-- 1. 船舶成员：绑定到悦航1号（9610000000000000001）
--    仅当该用户在 user_info 中真实存在时才插入，避免产生孤儿成员行。
--    若试点账号不同，改下面三个 user_id 即可。
-- ===========================================================================
INSERT INTO `vessel_member`
  (`id`, `vessel_id`, `user_id`, `member_role`, `can_edit`, `can_confirm`, `status`, `join_time`, `remark`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9620000000000000001', '9610000000000000001', u.`id`, '1', '1', '1', '1', NOW(), '试点发起人（可邀请成员、可提交、可关闭）', '1590229800633634816', 'seed', NOW(), '0'
FROM `user_info` u WHERE u.`id` = '2040654277629796353' AND u.`del_flag` = '0';

INSERT INTO `vessel_member`
  (`id`, `vessel_id`, `user_id`, `member_role`, `can_edit`, `can_confirm`, `status`, `join_time`, `remark`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9620000000000000002', '9610000000000000001', u.`id`, '3', '1', '1', '1', NOW(), '采购确认人（可核定数量并提交整船订单）', '1590229800633634816', 'seed', NOW(), '0'
FROM `user_info` u WHERE u.`id` = '2040656345832747009' AND u.`del_flag` = '0';

INSERT INTO `vessel_member`
  (`id`, `vessel_id`, `user_id`, `member_role`, `can_edit`, `can_confirm`, `status`, `join_time`, `remark`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9620000000000000003', '9610000000000000001', u.`id`, '2', '1', '0', '1', NOW(), '普通船员（只能维护自己的明细）', '1590229800633634816', 'seed', NOW(), '0'
FROM `user_info` u WHERE u.`id` = '2096466699352522754' AND u.`del_flag` = '0';

-- ===========================================================================
-- 1c. 绑定申请：留 1 条待审核申请，供验收直接测试管理端审核
--
--     申请人必须**不是任何在营船舶的成员**，否则服务端会拒绝重复申请，
--     而且审核通过也只剩「同步成员」这一步，测不到真正的绑定路径。
--     三个试点账号都已在船，所以这里专门建一个验收用申请人（97 前缀），
--     不依赖既有账号的成员状态，保证本段一定有数据。
--
--     审核通过时若船名匹配不到在营船舶，管理端会走「新建船舶后绑定」分支 ——
--     这正是地推场景（船还没录入系统）需要人工验证的路径。
--     验收时建议走「新建船舶后绑定」，避免污染试点船舶。
--
--     该申请人的用途仅限验收：用手机号 13800009701 + 短信码登录 App，
--     即可走「申请 → 运营审核 → 绑定成功」完整链路。
-- ===========================================================================
-- 验收用申请人（非任何船舶成员）。仅补必要字段，其余走表默认值。
INSERT IGNORE INTO `user_info`
  (`id`, `nickname`, `phone`, `tenant_id`, `del_flag`, `create_time`, `user_source`, `sex`)
VALUES
  ('9700000000000000001', '验收申请人', '13800009701', '1590229800633634816', '0', NOW(), '1', '0');

INSERT INTO `vessel_bind_apply`
  (`id`, `apply_no`, `user_id`, `apply_role`, `apply_vessel_name`, `apply_vessel_imo`, `apply_port_name`,
   `real_name`, `phone`, `position`, `remark`, `status`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9690000000000000001', 'VBSMOKE0001', u.`id`, '4', '悦航验收测试船', 'IMO9800999', '上海港',
       '验收业务员', '13800009999', 'YG-VERIFY', '验收种子：待审核的业务员认领申请', '1',
       '1590229800633634816', 'seed', NOW(), '0'
FROM `user_info` u
WHERE u.`id` = '9700000000000000001'
  AND u.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `vessel_member` m
    WHERE m.`user_id` = u.`id` AND m.`del_flag` = '0' AND m.`status` = '1'
  );

-- ===========================================================================
-- 1b. 靠港计划：保证验收时有 2 个可用靠港
--     46 号脚本用的是 NOW()+3天 / NOW()+10天 的相对日期，几天后就会过期；
--     D 链路（船舶靠港切换）需要至少 2 个可用靠港才能验证。
--     这里用独立的 968 前缀新建，不动 46 号脚本的数据。
-- ===========================================================================
INSERT INTO `vessel_call`
  (`id`, `vessel_id`, `port_code`, `port_name`, `berth`, `eta`, `etd`,
   `delivery_window_start`, `delivery_window_end`, `status`, `tenant_id`, `create_by`, `create_time`, `del_flag`) VALUES
('9680000000000000001', '9610000000000000001', 'CNSHA', '上海港', '3号泊位',
 DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY) + INTERVAL 12 HOUR,
 DATE_ADD(NOW(), INTERVAL 2 DAY) + INTERVAL 1 HOUR, DATE_ADD(NOW(), INTERVAL 2 DAY) + INTERVAL 8 HOUR,
 '1', '1590229800633634816', 'seed', NOW(), '0'),
('9680000000000000002', '9610000000000000001', 'CNSHA', '上海港', '5号泊位',
 DATE_ADD(NOW(), INTERVAL 9 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY) + INTERVAL 12 HOUR,
 DATE_ADD(NOW(), INTERVAL 9 DAY) + INTERVAL 1 HOUR, DATE_ADD(NOW(), INTERVAL 9 DAY) + INTERVAL 8 HOUR,
 '1', '1590229800633634816', 'seed', NOW(), '0');

-- ===========================================================================
-- 2. 船舶物料类目（1 个一级 + 6 个二级）
-- ===========================================================================
INSERT INTO `goods_category`
  (`id`, `name`, `parent_id`, `category_pic`, `description`, `status`, `create_time`, `update_time`, `del_flag`, `sort`, `tenant_id`, `create_by`, `update_by`) VALUES
('9630000000000000001', '船舶物料', '0',    NULL, '船供采购专用类目，按船舶物料惯例组织', '0', NULL, NULL, '0', 90, '1590229800633634816', 'seed', NULL),
('9630000000000000002', '清洁用品', '9630000000000000001', NULL, '船舶物料·清洁用品', '0', NULL, NULL, '0', 1, '1590229800633634816', 'seed', NULL),
('9630000000000000003', '安全防护', '9630000000000000001', NULL, '船舶物料·安全防护', '0', NULL, NULL, '0', 2, '1590229800633634816', 'seed', NULL),
('9630000000000000004', '甲板索具', '9630000000000000001', NULL, '船舶物料·甲板索具', '0', NULL, NULL, '0', 3, '1590229800633634816', 'seed', NULL),
('9630000000000000005', '轮机备件', '9630000000000000001', NULL, '船舶物料·轮机备件', '0', NULL, NULL, '0', 4, '1590229800633634816', 'seed', NULL),
('9630000000000000006', '电工照明', '9630000000000000001', NULL, '船舶物料·电工照明', '0', NULL, NULL, '0', 5, '1590229800633634816', 'seed', NULL),
('9630000000000000007', '船用食品', '9630000000000000001', NULL, '船舶物料·船用食品', '0', NULL, NULL, '0', 6, '1590229800633634816', 'seed', NULL);

-- ===========================================================================
-- 3. 商品规格表（唯一的数据源，四张业务表都由它派生，避免多处维护）
--    cat: 二级类目序号(2-7)  storage: 1常温 2冷藏 3冷冻 4危险品 5其他
--    scope: sale_scope  status: 商品上下架  moq 必须是 step_qty 的整数倍
-- ===========================================================================
DROP TEMPORARY TABLE IF EXISTS `tmp_ship_seed`;
CREATE TEMPORARY TABLE `tmp_ship_seed` (
  `seq` int NOT NULL,
  `name` varchar(100) NOT NULL,
  `name_en` varchar(255) NOT NULL,
  `cat` int NOT NULL,
  `impa` varchar(32) DEFAULT NULL,
  `issa` varchar(32) DEFAULT NULL,
  `internal_code` varchar(64) DEFAULT NULL,
  `barcode` varchar(64) DEFAULT NULL,
  `aliases` varchar(500) DEFAULT NULL,
  `storage` char(2) NOT NULL,
  `purchase_unit` varchar(32) NOT NULL,
  `package_spec` varchar(128) NOT NULL,
  `moq` int NOT NULL,
  `step_qty` int NOT NULL,
  `scope` char(2) NOT NULL,
  `status` char(2) NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `stock` int NOT NULL
);

INSERT INTO `tmp_ship_seed`
  (`seq`, `name`, `name_en`, `cat`, `impa`, `issa`, `internal_code`, `barcode`, `aliases`, `storage`, `purchase_unit`, `package_spec`, `moq`, `step_qty`, `scope`, `status`, `price`, `stock`) VALUES
( 1, '船用洗手液',       'Marine Hand Soap',       2, '0301010', '30.01.01', 'INT-CLN-001', '6901234500011', '洗手液,洗洁精,hand soap',    '1', '箱', '24瓶/箱',      5,   1, '3', '1',   128.00,  800),
( 2, '甲板清洁剂',       'Deck Cleaner',           2, '0302020', NULL,       'INT-CLN-002', '6901234500028', '除油剂,甲板清洗,cleaner',    '1', '箱', '12桶/箱',      6,   3, '3', '1',   396.00,  400),
( 3, '医用酒精 75%',     'Medical Alcohol 75%',    2, '0305010', NULL,       'INT-CLN-003', NULL,            '酒精,消毒液,alcohol',        '4', '箱', '24瓶/箱',      6,   6, '2', '1',   216.00,  300),
( 4, '救生衣',           'Life Jacket',            3, '0902010', '09.02.01', 'INT-SAF-001', '6901234500042', '救生服,life jacket',         '1', '件', '10件/箱',     10,   5, '3', '1',   185.00,  500),
( 5, '救生圈',           'Life Buoy',              3, '0902020', NULL,       'INT-SAF-002', NULL,            '救生浮圈,life buoy',         '1', '个', '4个/箱',       4,   2, '3', '1',    96.00,  300),
( 6, '消防水带 65mm',    'Fire Hose 65mm',         3, '0903010', NULL,       'INT-SAF-003', NULL,            '水带,消防,fire hose',        '1', '条', '20米/条',      2,   1, '3', '1',   268.00,  200),
( 7, '安全带',           'Safety Harness',         3, '0902030', NULL,       'INT-SAF-004', NULL,            '高空安全带,harness',         '1', '件', '5件/箱',       5,   1, '3', '1',   156.00,  250),
( 8, '工作手套',         'Work Gloves',            3, '0904010', NULL,       'INT-SAF-005', NULL,            '劳保手套,gloves',            '1', '双', '120双/箱',    60,  12, '3', '1',   360.00,  600),
( 9, '船用雨衣',         'Marine Raincoat',        3, '0904020', NULL,       'INT-SAF-006', NULL,            '雨披,raincoat',              '1', '件', '20件/箱',     20,  10, '3', '1',   180.00,  300),
(10, '尼龙缆绳 24mm',    'Nylon Rope 24mm',        4, '0701020', '07.01.02', 'INT-DEK-001', NULL,            '缆绳,rope',                  '1', '卷', '200米/卷',     1,   1, '3', '1',  1450.00,  120),
(11, '钢丝绳 16mm',      'Steel Wire Rope 16mm',   4, '0701030', NULL,       'INT-DEK-002', NULL,            '钢索,wire rope',             '1', '米', '100米/卷',   100,  50, '3', '1',    18.50, 5000),
(12, '船用卸扣',         'Marine Shackle',         4, '0701040', NULL,       'INT-DEK-003', NULL,            '卡扣,shackle',               '1', '个', '20个/箱',     20,   5, '3', '1',    42.00,  800),
(13, '柴油滤芯',         'Diesel Filter',          5, '0504020', '05.04.02', 'INT-ENG-001', '6901234500135', '燃油滤,filter',              '1', '个', '12个/箱',     12,   6, '3', '1',    88.00,  900),
(14, '机油滤芯',         'Oil Filter',             5, '0504030', NULL,       'INT-ENG-002', NULL,            '润滑油滤,oil filter',        '1', '个', '12个/箱',     12,  12, '3', '1',    76.00,  900),
(15, '液压油 46#',       'Hydraulic Oil 46',       5, '0401010', NULL,       'INT-OIL-001', NULL,            '液压油,hydraulic oil',       '1', '桶', '200L/桶',      1,   1, '3', '1',  2380.00,   60),
(16, '密封垫片',         'Gasket',                 5, '0602030', NULL,       'INT-FAS-002', NULL,            '垫片,gasket',                '1', '片', '50片/盒',     50,  25, '3', '1',    12.00, 2000),
(17, '不锈钢螺栓 M16',   'SS Bolt M16',            5, '0601010', '06.01.01', 'INT-FAS-001', NULL,            '螺栓,bolt',                  '1', '盒', '100只/盒',     5,   5, '3', '1',   165.00,  400),
(18, '船用电缆 3x2.5',   'Marine Cable 3x2.5',     6, '0703020', NULL,       'INT-ELE-001', NULL,            '电缆,cable',                 '1', '卷', '100米/卷',     2,   2, '3', '1',  1280.00,  150),
(19, '船用灯泡 220V',    'Marine Bulb 220V',       6, '0705010', NULL,       'INT-ELE-002', NULL,            '灯泡,bulb',                  '1', '只', '100只/箱',    20,  10, '3', '1',     8.50, 3000),
(20, 'LED 投光灯 100W',  'LED Floodlight 100W',    6, '0705020', '07.05.02', 'INT-ELE-003', NULL,            '投光灯,floodlight',          '1', '只', '6只/箱',       6,   6, '3', '1',   320.00,  240),
(21, '绝缘胶带',         'Insulation Tape',        6, '0705030', NULL,       'INT-ELE-004', NULL,            '电工胶带,tape',              '1', '卷', '200卷/箱',    40,  20, '3', '1',     6.00, 4000),
(22, '饮用水 5L',        'Drinking Water 5L',      7, '0101010', NULL,       'INT-FOD-001', NULL,            '矿泉水,water',               '1', '箱', '4桶/箱',       4,   4, '3', '1',    56.00, 1200),
(23, '速溶咖啡',         'Instant Coffee',         7, '0102010', NULL,       'INT-FOD-002', NULL,            '咖啡,coffee',                '1', '盒', '24袋/盒',     24,  12, '3', '1',   168.00,  600),
(24, '冷冻牛肉',         'Frozen Beef',            7, '0103020', NULL,       'INT-FOD-003', NULL,            '牛肉,beef,冷冻',             '3', '箱', '10kg/箱',      2,   1, '3', '1',   680.00,  200),
(25, '新鲜蔬菜',         'Fresh Vegetables',       7, '0104010', NULL,       'INT-FOD-004', NULL,            '蔬菜,vegetables,冷藏',       '2', '箱', '5kg/箱',       2,   1, '3', '1',   120.00,  300),
(26, '方便面',           'Instant Noodles',        7, '0105010', NULL,       'INT-FOD-005', NULL,            '泡面,noodles',               '1', '箱', '24包/箱',     24,  24, '3', '1',    96.00,  800),
(27, '午餐肉罐头',       'Luncheon Meat Can',      7, '0105020', NULL,       'INT-FOD-006', NULL,            '罐头,can',                   '1', '箱', '24罐/箱',     24,  12, '3', '1',   288.00,  500),
-- 第 28 条专门用于验证「船供目录不得出现下架商品」
(28, '【下架测试】停用备件', 'Discontinued Spare Part', 5, '0509000', NULL,    'INT-ENG-099', NULL,            '下架测试,discontinued',      '1', '个', '1个/箱',       1,   1, '3', '0',    10.00,    0);

-- ===========================================================================
-- 4. SPU（挂到「船舶物料」一级 + 对应二级类目；分类必须有值，
--    否则零售列表的 goods_category.del_flag 过滤会把商品排除掉）
-- ===========================================================================
INSERT INTO `goods_spu`
  (`id`, `name`, `sub_title`, `spu_urls`, `status`, `sales_volume`, `category_first_id`, `category_second_id`,
   `create_time`, `update_time`, `del_flag`, `description`, `enable_specs`, `tenant_id`, `create_by`, `stock`,
   `freight_type`, `sales_price`, `original_price`, `cost_price`)
SELECT CONCAT('96400000000000000', LPAD(`seq`, 2, '0')),
       `name`,
       CONCAT(`name_en`, ' / ', `package_spec`),
       '[]',
       `status`,
       0,
       '9630000000000000001',
       CONCAT('963000000000000000', `cat`),
       NOW(), NOW(), '0',
       CONCAT(`name`, '（船供验收种子数据，图片请由运营在管理端补充）'),
       '0',
       '1590229800633634816', 'seed',
       `stock`,
       '0',
       `price`, ROUND(`price` * 1.15, 2), ROUND(`price` * 0.70, 2)
FROM `tmp_ship_seed`;

-- ===========================================================================
-- 5. SKU（每个 SPU 一个默认 SKU，与 SPU 同价同库存）
-- ===========================================================================
INSERT INTO `goods_sku`
  (`id`, `spu_id`, `sales_price`, `original_price`, `cost_price`, `stock`, `create_time`, `update_time`,
   `del_flag`, `version`, `tenant_id`, `create_by`, `status`, `specs_json`)
SELECT CONCAT('96500000000000000', LPAD(`seq`, 2, '0')),
       CONCAT('96400000000000000', LPAD(`seq`, 2, '0')),
       `price`, ROUND(`price` * 1.15, 2), ROUND(`price` * 0.70, 2),
       `stock`, NOW(), NOW(), '0', 0,
       '1590229800633634816', 'seed', '1',
       '[]'
FROM `tmp_ship_seed`;

-- ===========================================================================
-- 6. SPU 船供资料
-- ===========================================================================
INSERT INTO `ship_goods_profile`
  (`id`, `spu_id`, `sale_scope`, `impa_code`, `issa_code`, `internal_item_code`, `barcode`, `name_en`,
   `search_aliases`, `storage_type`, `shelf_life_days`, `ship_supply_remark`, `publish_completeness`,
   `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT CONCAT('96600000000000000', LPAD(`seq`, 2, '0')),
       CONCAT('96400000000000000', LPAD(`seq`, 2, '0')),
       `scope`, `impa`, `issa`, `internal_code`, `barcode`, `name_en`, `aliases`,
       `storage`, 365, '船供验收种子数据',
       100,
       '1590229800633634816', 'seed', NOW(), '0'
FROM `tmp_ship_seed`;

-- ===========================================================================
-- 7. SKU 包装资料（采购单位 / 箱规 / MOQ / 步长）
-- ===========================================================================
INSERT INTO `ship_sku_profile`
  (`id`, `spu_id`, `sku_id`, `base_unit`, `purchase_unit`, `conversion_rate`, `package_spec`,
   `package_spec_en`, `moq`, `step_qty`, `stock_warning_line`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT CONCAT('96700000000000000', LPAD(`seq`, 2, '0')),
       CONCAT('96400000000000000', LPAD(seq, 2, '0')),
       CONCAT('96500000000000000', LPAD(`seq`, 2, '0')),
       '件', `purchase_unit`, 1.0000, `package_spec`,
       NULL, `moq`, `step_qty`, 10,
       '1590229800633634816', 'seed', NOW(), '0'
FROM `tmp_ship_seed`;

DROP TEMPORARY TABLE IF EXISTS `tmp_ship_seed`;

SET FOREIGN_KEY_CHECKS = 1;

-- ===========================================================================
-- 自检：应输出 上架船供商品=27、仅船供=1、下架=1、成员=3、可用靠港>=2
-- （27 条 > 单页 20 条，足以验证分页）
-- ===========================================================================
SELECT '上架船供商品(应 27)' AS item, COUNT(*) AS cnt
FROM `ship_goods_profile` p JOIN `goods_spu` s ON s.`id` = p.`spu_id`
WHERE p.`del_flag` = '0' AND p.`sale_scope` IN ('2', '3') AND s.`status` = '1' AND s.`del_flag` = '0'
UNION ALL
SELECT '仅船供(sale_scope=2，应 1)', COUNT(*)
FROM `ship_goods_profile` WHERE `del_flag` = '0' AND `sale_scope` = '2'
UNION ALL
SELECT '下架商品(应 1)', COUNT(*)
FROM `ship_goods_profile` p JOIN `goods_spu` s ON s.`id` = p.`spu_id`
WHERE p.`del_flag` = '0' AND s.`status` <> '1'
UNION ALL
SELECT '船舶成员(应 3)', COUNT(*) FROM `vessel_member` WHERE `del_flag` = '0' AND `id` LIKE '962%'
UNION ALL
SELECT '待审核绑定申请(应 >=0)', COUNT(*) FROM `vessel_bind_apply`
WHERE `del_flag` = '0' AND `id` LIKE '969%' AND `status` = '1'
UNION ALL
SELECT '可用靠港计划(应 >=2)', COUNT(*)
FROM `vessel_call`
WHERE `del_flag` = '0' AND `status` IN ('1', '2') AND `etd` > NOW();

-- ===========================================================================
-- 清理本脚本数据（需要时手工执行）
-- ===========================================================================
-- DELETE FROM `ship_sku_profile`   WHERE `id` LIKE '967%';
-- DELETE FROM `ship_goods_profile` WHERE `id` LIKE '966%';
-- DELETE FROM `goods_sku`          WHERE `id` LIKE '965%';
-- DELETE FROM `goods_spu`          WHERE `id` LIKE '964%';
-- DELETE FROM `goods_category`     WHERE `id` LIKE '963%';
-- DELETE FROM `vessel_member` WHERE `id` LIKE '962%';
-- DELETE FROM `vessel_call`   WHERE `id` LIKE '968%';
-- DELETE FROM `vessel_bind_apply` WHERE `id` LIKE '969%';
