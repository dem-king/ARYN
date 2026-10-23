-- 会员等级成长体系种子数据（Boot 单体 模式）
--
-- 目标库：aryn_boot（单体模式所有表同库）
-- 背景：管理端「会员管理 > 会员等级」初始为空（共 0 个等级），C 端会员成长体系
--       （/app/member/levels）与自动升级（MemberLevelServiceImpl#recalculateLevel）
--       均无档位可用。参考生鲜/商超会员成长体系补齐 5 档等级。
-- 口径：本批统一采用「累计消费金额」（condition_type='1'）单一成长口径。
--       升级取满足阈值的最高档：recalculateLevel 按 sort_order 升序遍历启用等级，
--       total_consume >= condition_value 即命中，sort_order 越大等级越高。
--       最低档阈值为 0.00，保证新注册会员（累计消费为 0）也能匹配到基础等级。
--       注意：condition_type='1'（累计消费金额）与 '2'（累计积分）不可混排比较，
--       如需积分成长线，应另行独立规划，不要与本批金额档交错设置 sort_order。
-- 特性：可重复执行；仅按 2120 固定前缀清理本脚本自身数据，不触碰存量业务等级；
--       逻辑删除字段 del_flag，禁止物理删除存量。等级图标 level_icon 留空，
--       可在管理端「会员等级」编辑时上传。
-- 依赖：4aryn_boot_member.sql（member_level 建表）。
--
-- 执行：mysql -u root -p aryn_boot < 147member_level_seed.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 0. 幂等清理（仅本脚本 2120 前缀，不触碰存量等级） ----------
DELETE FROM `member_level` WHERE `id` LIKE '2120000000000000%' AND `tenant_id` = '1590229800633634816';

-- ---------- 1. 五档会员等级（统一累计消费金额，单位：元；sort_order 越大等级越高） ----------
INSERT INTO `member_level`
  (`id`, `level_name`, `level_icon`, `condition_type`, `condition_value`,
   `sort_order`, `status`, `create_by`, `create_time`, `update_time`, `del_flag`, `tenant_id`)
VALUES
  ('2120000000000000001', '新客会员', NULL, '1',    0.00, 1, '0', 'seed', NOW(), NULL, '0', '1590229800633634816'),
  ('2120000000000000002', '银卡会员', NULL, '1',  100.00, 2, '0', 'seed', NOW(), NULL, '0', '1590229800633634816'),
  ('2120000000000000003', '金卡会员', NULL, '1',  500.00, 3, '0', 'seed', NOW(), NULL, '0', '1590229800633634816'),
  ('2120000000000000004', '铂金会员', NULL, '1', 2000.00, 4, '0', 'seed', NOW(), NULL, '0', '1590229800633634816'),
  ('2120000000000000005', '钻石会员', NULL, '1', 5000.00, 5, '0', 'seed', NOW(), NULL, '0', '1590229800633634816');

-- ---------- 2.（可选）存量会员按新档位重算 ----------
-- 种子只新增等级配置，不改写存量 user_info.member_level_id。已有会员会在下次下单
-- 触发成长计算时自动升级；如需立即为存量会员重算，可调用重算接口或人工执行：
-- UPDATE `user_info` u JOIN (
--   SELECT `id`, `condition_value` FROM `member_level`
--   WHERE `status` = '0' AND `condition_type` = '1' AND `tenant_id` = '1590229800633634816'
-- ) lv ON u.`total_consume` >= lv.`condition_value`
-- SET u.`member_level_id` = lv.`id`, u.`update_time` = NOW()
-- WHERE u.`del_flag` = '0' AND u.`tenant_id` = '1590229800633634816';
-- （上述 JOIN 取满足阈值的最高档需配合排序，建议走系统重算逻辑，勿直接批量 UPDATE 兜底。）

-- 验证：
-- SELECT level_name, condition_type, condition_value, sort_order, status
--   FROM member_level WHERE tenant_id='1590229800633634816' AND del_flag='0' ORDER BY sort_order;

SET FOREIGN_KEY_CHECKS = 1;
