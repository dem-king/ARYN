-- ============================================================================
-- 首单口径一致性修复（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-12 需求「新人首单双轨与采购主体改造」审查修复，2026-11-13）：
--   1. 船供提交权限口径统一为 vessel_member.can_confirm 列：发起人(1)/采购确认人(3)/
--      业务员(4) 默认具备（与共享车 VESSEL_MANAGER_ROLES、成员管理 MEMBER_ADMIN_ROLES
--      同口径），普通船员(2)不具备。历史行按角色回填，新写入由代码默认值保证。
--   2. order_info 增加下单时主体审核状态快照 buyer_org_audit_status：
--      企业首单消耗只统计「下单时主体已审核通过(快照=1)」的已支付单；
--      待审核期间下的单即使支付也不烧掉工厂首单（否则「待审核可下单」与
--      「审核通过后享首单」两条已确认口径互相打架）。
--
-- 兼容：
--   · 历史企业单 buyer_org_id 为 NULL，本就不参与首单统计，快照列保持 NULL；
--   · P0 上线到本脚本之间产生的企业单快照为 NULL，不消耗首单（宁宽勿烧，
--     该窗口仅存在于未升级环境，运营可按需人工核对）；
--   · 回填只把管理角色的 can_confirm 置 1，不降级任何已有授权。
--
-- 幂等：information_schema 判断 + PREPARE 动态执行；UPDATE 带值守卫，可重复执行。
-- 执行：mysql -u root -p aryn_boot < 233first_order_consistency_fix_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;

-- 1. 船舶成员确认权回填：发起人/业务员默认具备采购确认权
UPDATE `vessel_member`
SET `can_confirm` = '1', `update_time` = NOW()
WHERE `member_role` IN ('1', '4')
  AND (`can_confirm` IS NULL OR `can_confirm` <> '1');

-- 自检：期望管理角色无 can_confirm<>1 的行
SELECT COUNT(*) AS manager_without_confirm FROM `vessel_member`
WHERE `member_role` IN ('1', '4') AND (`can_confirm` IS NULL OR `can_confirm` <> '1');

-- 2. 订单主体审核状态快照列
SET @add_order_org_audit_status = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `order_info` ADD COLUMN `buyer_org_audit_status` char(1) NULL COMMENT ''下单时采购主体审核状态快照：0待审核 1已通过 2已驳回 3已停用；首单消耗只统计已通过'' AFTER `buyer_org_credit_code`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'buyer_org_audit_status'
);
PREPARE stmt FROM @add_order_org_audit_status; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：order_info 存在快照列
SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'buyer_org_audit_status';

SET FOREIGN_KEY_CHECKS = 1;