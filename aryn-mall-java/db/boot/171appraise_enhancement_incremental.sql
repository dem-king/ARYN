-- 商品评价增强：部分评价 / 匿名 / 评分分层 / 追评改删（Boot 单体模式）
-- 目标库：aryn_boot
--
-- 背景：一单多品评价由「每品必填文字 + 全量提交 + 自动 5 星」升级为行业口径：
--       文字选填、允许部分评价、物流/服务维度、匿名评价、系统默认评价不打分、
--       追评/修改/删除（发布后 30 天窗口、追评与修改各一次、删除为逻辑删除）。
--
-- 涉及结构：
--   goods_appraise 新增 appraise_type / is_anonymous / tags / advance_content /
--     advance_pic_urls / advance_time / modify_time；
--   order_info.appraise_status 口径改为「全部订单项都有有效评价才置 1」，
--     部分评价保持 0（含部分评价，仍计入待评价数量）。
--
-- 特性：幂等；不删除、不覆盖业务数据；无 DROP/TRUNCATE。
-- 执行：mysql -u root -p aryn_boot < 171appraise_enhancement_incremental.sql

USE `aryn_boot`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、goods_appraise 新增评价能力列（information_schema 判列存在，可重复执行）
-- ============================================================================
SET @add_appraise_type = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `goods_appraise` ADD COLUMN `appraise_type` tinyint NOT NULL DEFAULT 0 COMMENT ''评价类型：0.用户主动评价；1.系统默认评价（不打分、不计入好评率）'' AFTER `content`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_appraise' AND COLUMN_NAME = 'appraise_type'
);
PREPARE stmt FROM @add_appraise_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_is_anonymous = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `goods_appraise` ADD COLUMN `is_anonymous` tinyint NOT NULL DEFAULT 0 COMMENT ''是否匿名：0.否；1.是（C 端脱敏展示，后台仍可追溯真实用户）'' AFTER `appraise_type`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_appraise' AND COLUMN_NAME = 'is_anonymous'
);
PREPARE stmt FROM @add_is_anonymous; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_tags = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `goods_appraise` ADD COLUMN `tags` varchar(255) NULL DEFAULT NULL COMMENT ''评价标签（逗号分隔，最多 5 个）'' AFTER `is_anonymous`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_appraise' AND COLUMN_NAME = 'tags'
);
PREPARE stmt FROM @add_tags; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_advance_content = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `goods_appraise` ADD COLUMN `advance_content` varchar(255) NULL DEFAULT NULL COMMENT ''追评内容'' AFTER `tags`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_appraise' AND COLUMN_NAME = 'advance_content'
);
PREPARE stmt FROM @add_advance_content; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_advance_pic_urls = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `goods_appraise` ADD COLUMN `advance_pic_urls` varchar(1024) NULL DEFAULT NULL COMMENT ''追评图片（逗号分隔）'' AFTER `advance_content`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_appraise' AND COLUMN_NAME = 'advance_pic_urls'
);
PREPARE stmt FROM @add_advance_pic_urls; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_advance_time = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `goods_appraise` ADD COLUMN `advance_time` datetime NULL DEFAULT NULL COMMENT ''追评时间（非空表示已追评，仅允许追评一次）'' AFTER `advance_pic_urls`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_appraise' AND COLUMN_NAME = 'advance_time'
);
PREPARE stmt FROM @add_advance_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_modify_time = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `goods_appraise` ADD COLUMN `modify_time` datetime NULL DEFAULT NULL COMMENT ''评价修改时间（非空表示已修改过，仅允许修改一次）'' AFTER `advance_time`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods_appraise' AND COLUMN_NAME = 'modify_time'
);
PREPARE stmt FROM @add_modify_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 二、存量系统默认评价回填（幂等）
--     旧逻辑写入 content='系统默认评价' 且 5 星；回填 appraise_type=1 后
--     不再计入好评率分子/分母，也不再出现在 C 端商详评价列表。
-- ============================================================================
UPDATE `goods_appraise` SET `appraise_type` = 1
WHERE `appraise_type` = 0 AND `content` = '系统默认评价';

-- ============================================================================
-- 三、订单评价状态口径注释（幂等）
--     0.待评价（含部分商品已评）；1.全部订单项已评价。
-- ============================================================================
SET @modify_appraise_status_comment = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `order_info` MODIFY COLUMN `appraise_status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''评价状态：0.待评价（含部分评价）；1.全部订单项已评价''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_info' AND COLUMN_NAME = 'appraise_status'
    AND COLUMN_COMMENT NOT LIKE '%部分评价%'
);
PREPARE stmt FROM @modify_appraise_status_comment; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
