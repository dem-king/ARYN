-- 悦航购微信小程序凭证切换（Cloud 微服务模式）
-- 目标库：aryn_user
-- 背景：小程序前端 AppId 已切换为 wx0a8242ea59f3e6b4，后端 social_account 仍停留在旧的
--       wxe150c73d0376f899，导致 socialLogin() 查不到三方账号配置，
--       ma/login 首次登录在 bindUserId() 处报「用户不存在」。
-- 特性：可重复执行；仅更新 social_account 中 WX_MA 类型记录的 app_id/app_secret，
--       不执行 DROP/TRUNCATE，不触碰存量业务数据。
-- 注意：app_secret 为开发库明文种子值，生产环境必须外置到配置中心/环境变量。
-- 执行：mysql -u root -p aryn_user < 87wechat_miniapp_credentials.sql

USE `aryn_user`;
SET NAMES utf8mb4;

-- 按 app_id 对齐旧记录（含逻辑删除记录，避免唯一索引 uk_social_account_app_id 冲突）
UPDATE `social_account`
SET `app_id`     = 'wx0a8242ea59f3e6b4',
    `app_secret` = '2556ce9a864c8e0ff8f4fb4f0f94fc89',
    `update_time` = NOW(),
    `update_by`   = 'system'
WHERE `type` = 'WX_MA'
  AND `app_id` <> 'wx0a8242ea59f3e6b4';

-- 兜底：旧记录已被逻辑删除或缺失时，补齐一条可用的 WX_MA 三方账号配置
INSERT INTO `social_account` (
  `id`, `type`, `app_id`, `app_secret`,
  `create_time`, `update_time`, `del_flag`, `create_by`, `update_by`, `tenant_id`
)
VALUES (
  '1', 'WX_MA', 'wx0a8242ea59f3e6b4', '2556ce9a864c8e0ff8f4fb4f0f94fc89',
  NOW(), NOW(), '0', 'system', 'system', '1590229800633634816'
)
ON DUPLICATE KEY UPDATE
  `app_secret`  = VALUES(`app_secret`),
  `del_flag`    = '0',
  `update_time` = NOW(),
  `update_by`   = 'system';
