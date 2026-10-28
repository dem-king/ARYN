-- 充值套餐档位上移（Boot 单体模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 背景：158 号脚本种的三档 100/200/500 元面向个人小额储值，对当前储值场景偏低，
--       按运营要求整体上移至 5000 元起步；赠送比例沿用原三档 5% / 7.5% / 10% 的阶梯。
-- 内容：
--   recharge_config：100/200/500（赠 5/15/50）→ 5000/10000/20000（赠 250/750/2000）。
-- 特性：可重复执行；仅刷新本种子固定前缀（2170000000000000%）的行，ID 保持不变，
--       不影响引用配置 ID 的存量充值订单，也不触碰其他租户或运营自建的档位行。
-- 依赖：158member_management_incremental.sql（recharge_config 表与初始种子）。
-- 口径：种子只绑定默认租户 1590229800633634816；其他租户请把 @tenant_id 改成目标租户后重跑。
--
-- 执行：mysql -u root -p aryn_boot < 163recharge_config_tiers_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

SET @tenant_id = _utf8mb4'1590229800633634816' COLLATE utf8mb4_general_ci;

SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 充值套餐档位上移 ----------
DELETE FROM `recharge_config`
WHERE `id` LIKE '2170000000000000%' AND `tenant_id` = @tenant_id;

INSERT INTO `recharge_config`
  (`id`, `recharge_amount`, `gift_amount`, `gift_point`, `sort_order`, `status`,
   `create_by`, `create_time`, `update_time`, `del_flag`, `tenant_id`)
VALUES
  ('2170000000000000001', 5000.00,  250.00,  0, 1, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2170000000000000002', 10000.00, 750.00,  0, 2, '0', 'seed', NOW(), NULL, '0', @tenant_id),
  ('2170000000000000003', 20000.00, 2000.00, 0, 3, '0', 'seed', NOW(), NULL, '0', @tenant_id);

-- 自检：应返回 5000/10000/20000 三行（赠 250/750/2000）
SELECT `id`, `recharge_amount`, `gift_amount`, `gift_point`, `sort_order`
FROM `recharge_config`
WHERE `tenant_id` = @tenant_id AND `del_flag` = '0'
ORDER BY `sort_order`;

SET FOREIGN_KEY_CHECKS = 1;
