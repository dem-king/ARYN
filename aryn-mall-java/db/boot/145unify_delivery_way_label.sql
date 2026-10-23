-- 悦航购统一公司配送方式对外名称（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：delivery_way=3/4 均由公司自有运力送达，但对外名称分别是「商城配送」与
--       「公司港口/船舶内部配送」，容易被理解成两套配送体系。
-- 语义：仅更新字典展示文案，枚举值、订单与配送任务数据均不变：
--       3 商城配送 -> 公司配送（岸上）：公司配送员送到收货地址（限配送范围）
--       4 公司港口/船舶内部配送 -> 公司配送（到船）：公司司机按靠港计划送到港口或船舶
-- 特性：可重复执行，不执行 DROP/TRUNCATE。
-- 注意：字典有缓存，执行后需刷新字典缓存或重新登录管理端。
-- 关联：cloud 模式对应增量 146unify_delivery_way_label.sql，内容一致。

USE `aryn_boot`;
SET NAMES utf8mb4;

UPDATE `sys_dict_value`
SET `dict_label` = '公司配送（岸上）',
    `remarks` = '公司配送员送到收货地址（限配送范围）'
WHERE `dict_type` = 'delivery_way' AND `dict_value` = '3' AND `del_flag` = '0';

UPDATE `sys_dict_value`
SET `dict_label` = '公司配送（到船）',
    `remarks` = '公司司机按靠港计划送到港口或船舶'
WHERE `dict_type` = 'delivery_way' AND `dict_value` = '4' AND `del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
