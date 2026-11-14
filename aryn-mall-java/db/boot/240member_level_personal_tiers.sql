-- 个人会员等级档位面向海员个人客群重配（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-14 船供 B2B 活动与会员体系重构 · P1）：
--   原 147 号种子按通用零售客单价给了五档（0/100/500/2000/5000，累计消费口径）。
--   平台客群转为海员个人购买（小额高频、随船送达），原 100 元即升银卡、
--   500 元金卡对个人客群过密；本脚本按方案建议值重配为三档小额档位并收敛后两档：
--     新客会员 0 / 常购会员 500 / 资深会员 2000（累计消费口径不变）；
--     铂金/钻石两档禁用（保留配置行，运营后续可按需恢复或改门槛）。
--
-- 幂等：UPDATE 按固定 ID 定位（147 号种子的 2120 段），重复执行结果一致；
--       不删任何配置行；仅默认租户（与 147 种子同租户）。
--
-- ⚠️ 执行前必读：boot 演示库的档位已被运营手工调整过（2026-11-14 实测为
--       0.01/1.5万/3万/6万/10万，与本脚本建议值不同）——盲目执行会覆盖运营口径！
--       本脚本仅作为「按方案建议值重配」的现成工具，执行前先与运营对齐档位；
--       full SQL 空库初始化场景不受影响（初始化即按新客群口径）。
-- 关联：cloud 模式对应 256member_level_personal_tiers.sql（同 ID 同数据）。

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 三档小额档位（门槛重配，名称同步改叫法）
UPDATE `member_level`
SET `level_name` = '新客会员', `condition_value` = 0.00, `update_by` = 'system', `update_time` = NOW()
WHERE `id` = '2120000000000000001' AND `del_flag` = '0';

UPDATE `member_level`
SET `level_name` = '常购会员', `condition_value` = 500.00, `update_by` = 'system', `update_time` = NOW()
WHERE `id` = '2120000000000000002' AND `del_flag` = '0';

UPDATE `member_level`
SET `level_name` = '资深会员', `condition_value` = 2000.00, `update_by` = 'system', `update_time` = NOW()
WHERE `id` = '2120000000000000003' AND `del_flag` = '0';

-- 2. 收敛后两档（禁用不删；等级重算取「启用中」最高匹配档，禁用档自动退出）
UPDATE `member_level`
SET `status` = '1', `update_by` = 'system', `update_time` = NOW()
WHERE `id` IN ('2120000000000000004', '2120000000000000005') AND `del_flag` = '0';

-- 3. 存量会员等级随下次订单完成事件自动重算（recalculateLevel 读启用档），
--    无需在此回写 user_info.member_level_id。

-- 自检（执行后人工核对）
-- SELECT id, level_name, condition_value, status FROM member_level ORDER BY sort_order;

SET FOREIGN_KEY_CHECKS = 1;
