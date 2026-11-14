-- ============================================================================
-- 船供/企业首单活动种子 + 首单主体唯一约束（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-11-12 需求「新人首单双轨与采购主体改造」P1）：
--   type=7 整船优惠活动通过 rules.firstOrderOnly + subjectType 参与首单引擎。
--   P1 引擎已实现按船/按采购主体判定资格；本脚本只负责：
--     1) promotion_lock 增加主体活跃作用域生成列 + 唯一索引，防并发双单重复享首单；
--     2) 按租户克隆船供/企业首单活动，避免只对某个租户生效。
--
-- 说明：
--   - 活动 ID 用 MD5(tenant + 业务前缀) 固定生成，可重复执行不重复插入；
--   - 规则里的 subjectType：1=船，2=采购主体；
--   - 种子默认草稿（status=1）：首单立减直接面向全部租户发布会有预算风险，
--     由运营在管理端按租户确认档位/预算后手动发布；已执行过旧版脚本的环境
--     活动保持原状态，本脚本不回改（INSERT IGNORE 不覆盖运营调整）。
--   - 不插入个人新人活动（个人场景由 227 修复现有活动）。
--
-- 执行：mysql -u root -p aryn_boot < 228first_order_activity_incremental.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 首单主体活跃作用域：只对 LOCKED/CONFIRMED 生成非空值，释放后自动变 NULL 允许重试
SET @has_subject_scope := (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = 'aryn_boot' AND `table_name` = 'promotion_lock'
    AND `column_name` = 'subject_active_scope'
);
SET @ddl := IF(@has_subject_scope = 0,
  'ALTER TABLE `promotion_lock` ADD COLUMN `subject_active_scope` VARCHAR(160) '
  'GENERATED ALWAYS AS (CASE WHEN `subject_id` IS NOT NULL AND `subject_type` IS NOT NULL '
  'AND `status` IN (''1'',''2'') THEN CONCAT(`tenant_id`, '':'', `activity_id`, '':'', '
  '`subject_type`, '':'', `subject_id`) ELSE NULL END) STORED COMMENT ''首单主体活跃作用域（释放后为空）''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_subject_index := (
  SELECT COUNT(*) FROM `information_schema`.`statistics`
  WHERE `table_schema` = 'aryn_boot' AND `table_name` = 'promotion_lock'
    AND `index_name` = 'uk_promotion_lock_subject_active'
);
SET @ddl := IF(@has_subject_index = 0,
  'ALTER TABLE `promotion_lock` ADD UNIQUE KEY `uk_promotion_lock_subject_active` (`subject_active_scope`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 船供首单：满 1000 减 100 / 满 3000 减 400 / 满 5000 减 800，单笔封顶 800
INSERT IGNORE INTO `promotion_activity`
(`id`, `activity_name`, `activity_type`, `activity_code`, `scope_type`, `scope_value`, `purchase_scene`,
 `rules`, `priority`, `total_budget`, `consumed_budget`, `per_user_limit`, `gift_stock`,
 `stackable`, `coupon_stackable`, `points_stackable`, `start_time`, `end_time`, `status`, `publish_time`,
 `remark`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT MD5(CONCAT('ship-first-', t.`id`)),
       '船供首单立减',
       '7',
       'SYS_SHIP_FIRST_ORDER',
       '4',
       '2',
       '2',
       '{"tiers":[{"minAmount":1000,"discountAmount":100},{"minAmount":3000,"discountAmount":400},{"minAmount":5000,"discountAmount":800}],"firstOrderOnly":true,"subjectType":"1","monthlyBudget":50000,"maxDiscountAmount":800}',
       100,
       0,
       0,
       0,
       0,
       '0',
       '1',
       '1',
       NOW(),
       '2027-12-31 23:59:59',
       '1',
       NULL,
       'P1 首单引擎：按船判定，已支付即消耗；全额退款不恢复',
       t.`id`,
       'system',
       NOW(),
       '0'
FROM `sys_tenant` t
WHERE t.`del_flag` = '0';

-- 3. 企业首单：同档位，按采购主体判定
INSERT IGNORE INTO `promotion_activity`
(`id`, `activity_name`, `activity_type`, `activity_code`, `scope_type`, `scope_value`, `purchase_scene`,
 `rules`, `priority`, `total_budget`, `consumed_budget`, `per_user_limit`, `gift_stock`,
 `stackable`, `coupon_stackable`, `points_stackable`, `start_time`, `end_time`, `status`, `publish_time`,
 `remark`, `tenant_id`, `create_by`, `create_time`, `del_flag`)
SELECT MD5(CONCAT('org-first-', t.`id`)),
       '企业首单立减',
       '7',
       'SYS_ORG_FIRST_ORDER',
       '4',
       '3',
       '3',
       '{"tiers":[{"minAmount":1000,"discountAmount":100},{"minAmount":3000,"discountAmount":400},{"minAmount":5000,"discountAmount":800}],"firstOrderOnly":true,"subjectType":"2","monthlyBudget":100000,"maxDiscountAmount":800}',
       100,
       0,
       0,
       0,
       0,
       '0',
       '1',
       '1',
       NOW(),
       '2027-12-31 23:59:59',
       '1',
       NULL,
       'P1 首单引擎：按采购主体判定，不按成员账号重复；待审核主体不享',
       t.`id`,
       'system',
       NOW(),
       '0'
FROM `sys_tenant` t
WHERE t.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
