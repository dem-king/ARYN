-- 全站自动动效总开关（Cloud 微服务模式）
-- 目标库：aryn_promotion
-- 背景（2026-10-16）：
--   小程序端有多处自动播放/自动前进的动效（新人专享分页前进、商品横滑条、
--   轮播图、图片组件、搜索栏热词），此前只能逐个组件关闭。对「系统开启了
--   减弱动效」「运营希望整站安静」两类诉求缺少一个总开关。
--   WCAG 2.2 SC 2.2.2 要求自动移动的内容必须可暂停；H5 可用媒体查询
--   prefers-reduced-motion 自动感知，但小程序（微信）没有等价能力，
--   因此需要一个运营可配的总开关作为兜底。
--
--   开关挂在「商城默认主题」行上而非新建配置表：
--   C 端启动时已有一趟免登主题请求（/app/pagedesign/mall-theme）且带本地
--   缓存防闪色，开关随主题一起下发即可在首帧生效，不新增请求、不新增菜单。
--
-- 特性：可重复执行，不执行 DROP/TRUNCATE；存量主题行默认 '1'（保留现状：
--       自动动效继续开启），运营显式关闭后才变 '0'。
-- 关联：boot 模式同名增量 127page_design_theme_auto_motion.sql 内容一致。

USE `aryn_promotion`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- page_design_theme 增加自动动效总开关
-- ---------------------------------------------------------------------------
SET @add_auto_motion = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `page_design_theme` ADD COLUMN `auto_motion_enabled` char(2) NOT NULL DEFAULT ''1'' COMMENT ''自动动效总开关：1.开启（默认，含自动轮播/前进）；0.关闭（尊重减弱动效偏好）'' AFTER `radius`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'page_design_theme'
    AND COLUMN_NAME = 'auto_motion_enabled'
);
PREPARE stmt FROM @add_auto_motion; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检：列已就位（应输出 1）
SELECT COUNT(*) AS auto_motion_column_ready
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'page_design_theme'
  AND COLUMN_NAME = 'auto_motion_enabled';
