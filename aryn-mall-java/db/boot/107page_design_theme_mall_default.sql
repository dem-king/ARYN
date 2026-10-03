-- 商城默认主题换肤能力（Boot 单体模式）
--
-- 目标库：aryn_boot
-- 背景（2026-10-02）：
--   page_design_theme 此前只能被单个装修页面通过 themeRef 引用，主题改动
--   「存了但全商城不生效」。本次为表增加 mall_default_flag 列，支持把一个
--   主题设为「商城默认主题」，C 端经免登接口 /app/pagedesign/mall-theme
--   拉取后对整个小程序换肤（wot 组件、tabBar 选中色等）。
--   租户内只允许一行 mall_default_flag='1'，由服务层在事务内互斥维护，
--   不加唯一索引（绝大多数行都是 '0'，索引无法表达「至多一个 '1'」）。
-- 特性：幂等（information_schema 判列存在）；不改动任何存量数据，全部
--       主题行的默认标记保持 '0'，C 端未设置默认主题时回落内置配色。
-- 配套：代码侧新增 setDefaultTheme / getDefaultTheme 与管理端「设为默认」。
--
-- 执行：mysql -u root -p aryn_boot < 107page_design_theme_mall_default.sql

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. page_design_theme 增加商城默认主题标记
-- ---------------------------------------------------------------------------
SET @add_mall_default_flag = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `page_design_theme` ADD COLUMN `mall_default_flag` char(2) NOT NULL DEFAULT ''0'' COMMENT ''商城默认主题：0.否；1.是；'' AFTER `status`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'page_design_theme' AND COLUMN_NAME = 'mall_default_flag'
);
PREPARE stmt FROM @add_mall_default_flag; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 自检：应返回 mall_default_flag 列且默认值为 '0'
-- ---------------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'page_design_theme' AND COLUMN_NAME = 'mall_default_flag';

-- 自检：默认主题标记为 '1' 的行数（执行后预期 0，设置默认主题后预期 1）
SELECT COUNT(*) AS mall_default_rows FROM `page_design_theme` WHERE `mall_default_flag` = '1';

-- 全量脚本以本脚本收尾（build-full-sql sections 末位），按约定恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;
