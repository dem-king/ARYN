-- 悦航购船供化试点种子数据（Boot 单体模式）
-- 目标库：aryn_boot
-- 特性：幂等插入（不存在才插入），不覆盖已有业务数据。
-- 内容：一个试点船舶、两个上海港靠港计划（近未来），供端到端验收使用。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 试点船舶：悦航1号
INSERT INTO `vessel_info` (`id`, `vessel_name`, `vessel_name_en`, `imo_code`, `vessel_type`, `status`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9610000000000000001', '悦航1号', 'AETHELYRN NO.1', '9800001', '1', '1', '1590229800633634816', 'system', NOW(), '0'
WHERE NOT EXISTS (
  SELECT 1 FROM `vessel_info` WHERE `id` = '9610000000000000001' AND `tenant_id` = '1590229800633634816'
);

-- 靠港计划1：+3天到港，停留12小时，配送时间窗 09:00-16:00
INSERT INTO `vessel_call` (`id`, `vessel_id`, `port_code`, `port_name`, `berth`, `eta`, `etd`, `delivery_window_start`, `delivery_window_end`, `status`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9610000000000000002', '9610000000000000001', 'CNSHA', '上海港', '3号泊位',
       DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 12 HOUR,
       DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 1 HOUR, DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 8 HOUR,
       '1', '1590229800633634816', 'system', NOW(), '0'
WHERE NOT EXISTS (
  SELECT 1 FROM `vessel_call` WHERE `id` = '9610000000000000002' AND `tenant_id` = '1590229800633634816'
);

-- 靠港计划2：+10天到港
INSERT INTO `vessel_call` (`id`, `vessel_id`, `port_code`, `port_name`, `berth`, `eta`, `etd`, `delivery_window_start`, `delivery_window_end`, `status`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT '9610000000000000003', '9610000000000000001', 'CNSHA', '上海港', '5号泊位',
       DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY) + INTERVAL 12 HOUR,
       DATE_ADD(NOW(), INTERVAL 10 DAY) + INTERVAL 1 HOUR, DATE_ADD(NOW(), INTERVAL 10 DAY) + INTERVAL 8 HOUR,
       '1', '1590229800633634816', 'system', NOW(), '0'
WHERE NOT EXISTS (
  SELECT 1 FROM `vessel_call` WHERE `id` = '9610000000000000003' AND `tenant_id` = '1590229800633634816'
);

SET FOREIGN_KEY_CHECKS = 1;
