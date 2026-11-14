-- 运营回填①：权益补齐 + 专区补价 + 恢复定向发券入口（Cloud 微服务模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-15 运营方案确认执行）：
--   1) 企业专属客服权益（type=5）挂战略客户——个人权益经查运营已配 12 条
--      （2140 段：新客 9.8 折 → 钻石 8.5 折+3 倍积分+免邮，挂载完整），不再补配；
--   2) 会员专区 30 个日耗品补 0.95 折扣率（标了但缺价的行，幂等条件 UPDATE，
--      已配价 18 个不动；大件低频不进会员价，差异化交给档期活动）；
--   3) 恢复「优惠券管理」管理端菜单（P0 收口时隐藏）——新体系保留企业满减券
--      定向发放能力，领券中心入口仍无券可领，不冲突。
--
-- 幂等：INSERT IGNORE / 条件 UPDATE；不删任何数据。
-- 注意：会员价配后由标签同步进 SYS_MEMBER_ZONE（管理端专区设置页「立即同步」
--       或每日 mallZoneSyncJobHandler 兜底），同步前 C 端暂不展示身份价。
-- 关联：对应 boot 241ops_benefits_zone_seed.sql（分库分段同数据）。

USE `aryn_user`;
SET NAMES utf8mb4;

-- （权益段：enterprise_benefit 在 aryn_user 库）
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 企业专属客服权益（type=5）+ 挂战略客户
INSERT IGNORE INTO `enterprise_benefit`
  (`id`, `benefit_name`, `benefit_type`, `benefit_value`, `description`, `status`, `tenant_id`,
   `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('2180000000000001130', '战略客户专属客服', '5', '1',
   '专属客服/业务员一对一服务', '0', '1590229800633634816', 'ops', NOW(), 'ops', NOW(), '0');
INSERT IGNORE INTO `enterprise_benefit_level_rel`
  (`id`, `benefit_id`, `level_id`, `tenant_id`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
  ('2180000000000001230', '2180000000000001130', '2180000000000001003', '1590229800633634816', 'ops', NOW(), 'ops', NOW(), '0');

-- 2. 会员专区：30 个标了但缺价的日耗生鲜补 0.95 折扣率（只命中缺价行）
UPDATE `goods_spu`
SET `member_discount_rate` = 0.95, `update_by` = 'ops', `update_time` = NOW()
WHERE `del_flag` = '0' AND `is_member_item` = '1'
  AND `member_discount_rate` IS NULL AND `member_price_fixed` IS NULL
  AND `tenant_id` = '1590229800633634816';

-- 3. 恢复「优惠券管理」菜单（新体系保留定向发券；领券中心 C 端无券不受影响）
UPDATE `sys_menu`
SET `del_flag` = '0', `update_by` = 'ops', `update_time` = NOW()
WHERE `id` = '1585191363078467586' AND `del_flag` = '1';

-- 自检（执行后人工核对）
-- SELECT COUNT(*) AS member_no_price FROM goods_spu
--  WHERE del_flag='0' AND is_member_item='1'
--    AND member_discount_rate IS NULL AND member_price_fixed IS NULL
--    AND tenant_id='1590229800633634816';  -- 期望 0
-- SELECT id, benefit_name FROM enterprise_benefit WHERE id='2180000000000001130';
-- SELECT del_flag FROM sys_menu WHERE id='1585191363078467586';  -- 期望 0

SET FOREIGN_KEY_CHECKS = 1;
