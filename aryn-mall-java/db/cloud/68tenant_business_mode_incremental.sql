-- 悦航购租户业务模式能力启用（Cloud 微服务模式）
-- 目标库：aryn_upms（菜单 / 权限库）
-- 背景：45ship_supply_menu_permission.sql 已为 sys_tenant 增加 business_mode
--       （1 综合 = 个人 + 船供并存；2 纯零售），但该字段此前**无任何代码读取**，
--       纯零售租户的 C 端首页仍会渲染船舶工作台。
-- 本次改动让能力真正生效，并补齐运营侧的配置入口与枚举字典。
-- 特性：幂等；不删除、不覆盖已有业务数据；无 DROP/TRUNCATE。

USE `aryn_upms`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、字典：business_mode，供管理端租户表单下拉选择
-- ============================================================================
INSERT IGNORE INTO `sys_dict`
(`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`update_time`,`create_by`,`update_by`)
SELECT '2130000000000000001', 'business_mode', '租户业务模式', '0', '1综合（个人+船供并存） 2纯零售', '0', NOW(), NULL, 'system', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `type` = 'business_mode');

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`)
SELECT '2130000000000000011', d.`id`, '综合（个人+船供并存）', '1', 'business_mode', '0', '展示船供入口', 1, '0', NOW(), 'system'
FROM `sys_dict` d WHERE d.`type` = 'business_mode' LIMIT 1;

INSERT IGNORE INTO `sys_dict_value`
(`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`remarks`,`sort`,`del_flag`,`create_time`,`create_by`)
SELECT '2130000000000000012', d.`id`, '纯零售', '2', 'business_mode', '0', '不展示船供入口', 2, '0', NOW(), 'system'
FROM `sys_dict` d WHERE d.`type` = 'business_mode' LIMIT 1;

-- ============================================================================
-- 二、存量兜底：历史上以 NULL / 空串存在的租户按综合模式处理
-- 仅回填空值，不覆盖运营已显式配置的取值。
-- ============================================================================
UPDATE `sys_tenant` SET `business_mode` = '1' WHERE `business_mode` IS NULL OR `business_mode` = '';

SET FOREIGN_KEY_CHECKS = 1;
