-- 悦航购共享购物车生命周期治理增量迁移（Cloud 微服务模式）
--
-- 目标库：aryn_order（订单库）
-- 租户白名单：本脚本仅对已有表 shared_cart 增加列/索引，不新增表，无需改动 Nacos 租户白名单。
-- 特性：幂等执行，不删除或重建数据；不修改既有列类型，不覆盖业务数据。
--
-- 背景（业务规则，2026-09-20 确认）：
--   1. 同一艘船同时只允许一个「收集中」的共享购物车。重复发起时前端应被引导去加入已有购物车，
--      而不是各发各的，否则同船同一靠港会重复下单。
--   2. 收集有效期 24 小时，自创建时刻起算，由服务端计算。
--   3. 过期由定时任务置为「已关闭」，避免列表长期显示误导性的「收集中」。
--   4. 订单签收后购物车置为「已完成」，让船员看到「本次采购已送达」。
--
-- 执行：mysql -u root -p < 66shared_cart_lifecycle_incremental.sql

USE `aryn_order`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 1. 收集截止时间兜底
--    历史数据由前端传 expiresAt，可能为空（永不过期）。这里仅补空值，
--    不覆盖运营已设置的有效期；新建由服务端强制写成 created + 24h。
-- ===========================================================================
UPDATE `shared_cart`
SET `expires_at` = DATE_ADD(`create_time`, INTERVAL 24 HOUR)
WHERE `expires_at` IS NULL
  AND `create_time` IS NOT NULL
  AND `status` IN ('2', '3');

-- ===========================================================================
-- 2. 送达归档时间
-- ===========================================================================
SET @add_shared_cart_completed_time = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `completed_time` datetime DEFAULT NULL COMMENT ''送达归档时间（订单签收后写入）'' AFTER `submitted_time`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'completed_time'
);
PREPARE stmt FROM @add_shared_cart_completed_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 3. 同一船舶「收集中」唯一性 —— 生成列 + 唯一索引
--    应用层先查后插无法防并发（两个请求可能同时通过校验），
--    因此用数据库兜底：仅当 status='2'（收集中）且未逻辑删除时生成列取 1，其余取 NULL。
--    MySQL 唯一索引允许多个 NULL，故「已提交/已完成/已关闭/已删除」可并存，
--    只有「同一租户 + 同一船舶 + 收集中且未删除」互斥。
-- ===========================================================================
SET @add_shared_cart_active_flag = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `status` = ''2'' AND `del_flag` = ''0'' THEN 1 ELSE NULL END) STORED COMMENT ''进行中标记：仅收集中且未删除为1，其余为NULL以允许多行''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'active_flag'
);
PREPARE stmt FROM @add_shared_cart_active_flag; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 建索引前先清理存量重复：同一船舶只保留最早的一条为收集中，其余置为已关闭。
-- （不删除数据，仅改状态；重复的收集中购物车本身即为历史缺陷产物。）
UPDATE `shared_cart` c
JOIN (
  SELECT `tenant_id`, `vessel_id`, MIN(`id`) AS keep_id
  FROM `shared_cart`
  WHERE `status` = '2' AND `del_flag` = '0'
  GROUP BY `tenant_id`, `vessel_id`
  HAVING COUNT(*) > 1
) d ON c.`tenant_id` = d.`tenant_id` AND c.`vessel_id` = d.`vessel_id`
SET c.`status` = '5'
WHERE c.`status` = '2' AND c.`del_flag` = '0' AND c.`id` <> d.keep_id;

SET @add_shared_cart_active_uk = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD UNIQUE KEY `uk_shared_cart_active` (`tenant_id`, `vessel_id`, `active_flag`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND INDEX_NAME = 'uk_shared_cart_active'
);
PREPARE stmt FROM @add_shared_cart_active_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- 4. 过期扫描索引（Job 按 status + expires_at 扫描）
-- ===========================================================================
SET @add_shared_cart_expire_idx = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD INDEX `idx_shared_cart_expire` (`status`, `expires_at`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND INDEX_NAME = 'idx_shared_cart_expire'
);
PREPARE stmt FROM @add_shared_cart_expire_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- 自检：新列与唯一索引应存在
SELECT `COLUMN_NAME`, `COLUMN_TYPE`, `COLUMN_COMMENT`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart'
  AND `COLUMN_NAME` IN ('completed_time', 'active_flag')
ORDER BY `COLUMN_NAME`;

SELECT `INDEX_NAME`, `NON_UNIQUE`, GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart'
  AND `INDEX_NAME` IN ('uk_shared_cart_active', 'idx_shared_cart_expire')
GROUP BY `INDEX_NAME`, `NON_UNIQUE`;

-- ===========================================================================
-- 5. 分享令牌（微信群分享自助加入）
--    背景：原邀请方式要求发起人先知道对方商城用户 ID，而群里的人尚未进入系统，
--    不可能预先获取。改为卡片携带随机令牌，点击即凭令牌加入。
--    令牌随购物车 24 小时过期，且加入时校验船舶成员关系（见 Service 层），
--    降低链接被转发到外部群后的滥用风险。
-- ===========================================================================
SET @add_shared_cart_share_token = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD COLUMN `share_token` varchar(64) DEFAULT NULL COMMENT ''分享令牌（转发卡片携带，随购物车过期失效）'' AFTER `active_flag`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND COLUMN_NAME = 'share_token'
);
PREPARE stmt FROM @add_shared_cart_share_token; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_shared_cart_share_token_uk = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `shared_cart` ADD UNIQUE KEY `uk_shared_cart_share_token` (`share_token`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shared_cart' AND INDEX_NAME = 'uk_shared_cart_share_token'
);
PREPARE stmt FROM @add_shared_cart_share_token_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;
