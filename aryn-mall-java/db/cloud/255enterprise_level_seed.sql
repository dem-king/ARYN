-- 企业会员等级档位种子（Cloud 微服务模式）
--
-- 目标库：aryn_user
-- 背景（2026-11-14 船供 B2B 活动与会员体系重构 · P1）：
--   企业轨等级按「近 12 个月主体已支付采购额」分档（enterprise_level.condition_value），
--   主体维度为岸上企业（buyer_org）/ 船舶（vessel）。本脚本提供四档种子，
--   运营可按实际客单价调整门槛或增删档位（门槛须随 sort_order 递增，等级重算取最高匹配档）。
--
-- 档位（建议值，运营可调）：
--   基础客户 0（注册即得，承载通用权益锚点）
--   合作客户 30,000（月均 2-3 千的稳定采购主体）
--   核心客户 100,000（月均近万的工厂/餐厅主力）
--   战略客户 300,000（船公司/连锁工厂级年度大户）
--
-- 幂等：按 ID INSERT IGNORE；已手工调整过的租户不会被覆盖。
-- 关联：对应 boot 239enterprise_level_seed.sql（同 ID 同数据）。

USE `aryn_user`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `enterprise_level`
  (`id`, `level_name`, `sort_order`, `condition_value`, `status`, `tenant_id`,
   `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('2180000000000001000', '基础客户', 1, 0, '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001001', '合作客户', 2, 30000, '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001002', '核心客户', 3, 100000, '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001003', '战略客户', 4, 300000, '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0');

-- 示例权益（可选启用）：合作/核心/战略客户折扣率递进；免邮挂核心档起。
-- 说明：协议价政策池/返利率/账期为预留权益位，本期不生效，运营按需在「企业权益」页配置。
INSERT IGNORE INTO `enterprise_benefit`
  (`id`, `benefit_name`, `benefit_type`, `benefit_value`, `description`, `status`, `tenant_id`,
   `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('2180000000000001100', '合作客户 98 折', '1', '0.9800', '企业折扣率权益（与活动价取优不叠加）', '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001101', '核心客户 96 折', '1', '0.9600', '企业折扣率权益（与活动价取优不叠加）', '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001102', '战略客户 95 折', '1', '0.9500', '企业折扣率权益（与活动价取优不叠加）', '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001110', '核心客户免邮', '2', '1', '岸上公司配送（scene=3）整单免邮；到船配送恒免邮与此无关', '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001120', '开票资质', '4', '1', '可申请企业发票（证件资料按需补收）', '0', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0');

-- 权益挂载等级（多对多）
INSERT IGNORE INTO `enterprise_benefit_level_rel`
  (`id`, `benefit_id`, `level_id`, `tenant_id`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('2180000000000001200', '2180000000000001100', '2180000000000001001', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001201', '2180000000000001101', '2180000000000001002', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001202', '2180000000000001102', '2180000000000001003', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001210', '2180000000000001110', '2180000000000001002', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001211', '2180000000000001110', '2180000000000001003', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001220', '2180000000000001120', '2180000000000001001', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001221', '2180000000000001120', '2180000000000001002', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0'),
  ('2180000000000001222', '2180000000000001120', '2180000000000001003', '1590229800633634816', 'system', NOW(), 'system', NOW(), '0');

-- 自检（执行后人工核对）：四档等级与示例权益应存在
-- SELECT id, level_name, sort_order, condition_value FROM enterprise_level ORDER BY sort_order;
-- SELECT id, benefit_name, benefit_type, benefit_value FROM enterprise_benefit;

SET FOREIGN_KEY_CHECKS = 1;
