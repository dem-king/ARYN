-- ============================================================================
-- 船供/企业首单活动种子 + 首单主体唯一约束（Cloud 微服务模式）
--
-- 目标库：aryn_promotion（租户名单读 aryn_upms.sys_tenant）
-- 前置：237（promotion_lock.subject_type/subject_id）、145（total/consumed_budget）、
--       157（coupon/points_stackable）、185（activity_code）。正常按编号顺序执行即可。
-- 背景与规则同 boot 228first_order_activity_incremental.sql。
-- 种子默认草稿（status=1），由运营按租户确认档位/预算后手动发布；
-- 已执行过旧版脚本的环境活动保持原状态，本脚本不回改。
--
-- 执行：mysql -u root -p < 240first_order_activity_incremental.sql
-- ============================================================================

USE `aryn_promotion`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 首单主体活跃作用域 + 唯一索引（幂等）
SET @has_subject_scope := (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = 'aryn_promotion' AND `table_name` = 'promotion_lock'
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
  WHERE `table_schema` = 'aryn_promotion' AND `table_name` = 'promotion_lock'
    AND `index_name` = 'uk_promotion_lock_subject_active'
);
SET @ddl := IF(@has_subject_index = 0,
  'ALTER TABLE `promotion_lock` ADD UNIQUE KEY `uk_promotion_lock_subject_active` (`subject_active_scope`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 船供首单（按租户克隆）
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
FROM `aryn_upms`.`sys_tenant` t
WHERE t.`del_flag` = '0';

-- 3. 企业首单（按租户克隆）
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
FROM `aryn_upms`.`sys_tenant` t
WHERE t.`del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
