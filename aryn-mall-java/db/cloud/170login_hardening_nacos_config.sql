-- 登录安全加固 Cloud Nacos 配置增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行（已达标则跳过），不删除或重建 Nacos 配置数据。
--
-- 背景（登录代码安全审查 2026-10-28）：
--   1) Sa-Token 默认监听器在 is-log=true 时，登录/续期等事件会打印完整 token；
--      能读日志即可冒用尚未过期的会话。
--   2) UPMS / USER 的 Mapper 日志开启 DEBUG 时，新增/改密的 SQL 参数会记录 BCrypt hash。
--   3) 管理端密码登录没有账号/IP 维度的失败计数与锁定；管理端（TOB）会话有效期与
--      C 端共用 30 天，token 泄露后可用窗口过长。
--
-- 变更（仅配置，无表结构变更）：
--   1) application-dev.yml：sa-token.is-log true -> false（关闭 Sa-Token 令牌日志）
--   2) aryn-upms-biz-dev.yml：com.aryn.cloud.upms.mapper debug -> info
--   3) aryn-user-biz-dev.yml：com.aryn.cloud.user.mapper debug -> info
--   4) aryn-auth-dev.yml：追加 security.login（登录失败限流参数 + TOB 会话 12h 绝对有效期）
--
-- 对应 boot：aryn-boot/src/main/resources/application.yml 的 sa-token.is-log、
--           security.login.*、logging.level.com.aryn.cloud.{upms,user}.mapper。
-- 生效：Sa-Token 与 logging 配置为启动期读取；security.login 使用 @RefreshScope，
--       Nacos 正常刷新即可，但建议执行后滚动重启 auth / upms / user 服务确保一次到位；
--       boot 模式需重新打包重启。
-- 执行：mysql -u root -p aryn_nacos < 170login_hardening_nacos_config.sql

USE `aryn_nacos`;

-- ===== 1. 关闭 Sa-Token 完整 token 日志（共享配置 application-dev.yml） =====
SET @app_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'application-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

-- 幂等：已是 is-log: false（或配置缺失）时原样保留，只有仍为 true 才替换。
SET @app_content = IF(
  @app_content IS NULL
    OR @app_content LIKE '%is-log: false%'
    OR @app_content NOT LIKE '%is-log: true%',
  @app_content,
  REPLACE(@app_content, 'is-log: true', 'is-log: false')
);

UPDATE `config_info`
SET `content` = @app_content,
    `md5` = MD5(@app_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'application-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @app_content IS NOT NULL
  AND (`content` <> @app_content OR `md5` <> MD5(@app_content));

-- ===== 2. UPMS Mapper 日志降级（改密 SQL 参数含 BCrypt hash） =====
SET @upms_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

SET @upms_content = IF(
  @upms_content IS NULL
    OR @upms_content NOT LIKE '%com.aryn.cloud.upms.mapper: debug%',
  @upms_content,
  REPLACE(@upms_content, 'com.aryn.cloud.upms.mapper: debug', 'com.aryn.cloud.upms.mapper: info')
);

UPDATE `config_info`
SET `content` = @upms_content,
    `md5` = MD5(@upms_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-upms-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @upms_content IS NOT NULL
  AND (`content` <> @upms_content OR `md5` <> MD5(@upms_content));

-- ===== 3. USER Mapper 日志降级（注册/改密 SQL 参数含 BCrypt hash） =====
SET @user_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-user-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

SET @user_content = IF(
  @user_content IS NULL
    OR @user_content NOT LIKE '%com.aryn.cloud.user.mapper: debug%',
  @user_content,
  REPLACE(@user_content, 'com.aryn.cloud.user.mapper: debug', 'com.aryn.cloud.user.mapper: info')
);

UPDATE `config_info`
SET `content` = @user_content,
    `md5` = MD5(@user_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-user-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @user_content IS NOT NULL
  AND (`content` <> @user_content OR `md5` <> MD5(@user_content));

-- ===== 4. 登录防护配置追加到 aryn-auth-dev.yml（登录限流 + TOB 12h） =====
SET @auth_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-auth-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

-- 幂等：已有 security.login 配置（或配置缺失）时原样保留，仅在缺失时追加一次。
SET @auth_content = IF(
  @auth_content IS NULL
    OR @auth_content LIKE '%tob-timeout-seconds%',
  @auth_content,
  CONCAT(@auth_content,
    '\n\n\n# 登录安全加固（2026-10-28）：密码登录失败限流 + 管理端会话有效期\n',
    'security:\n',
    '  login:\n',
    '    enabled: true\n',
    '    max-account-failures: 5\n',
    '    account-lock-seconds: 900\n',
    '    max-ip-failures: 30\n',
    '    ip-lock-seconds: 900\n',
    '    tob-timeout-seconds: 43200\n')
);

UPDATE `config_info`
SET `content` = @auth_content,
    `md5` = MD5(@auth_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-auth-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @auth_content IS NOT NULL
  AND (`content` <> @auth_content OR `md5` <> MD5(@auth_content));

-- ===== 自检：锚点不匹配时脚本会「执行成功但内容没改」，必须看下面这些数 =====
-- token_log_disabled / upms_mapper_info / user_mapper_info / login_guard_registered 应为 1；
-- legacy_* 应为 0；config_md5_mismatch 应为 0。
SELECT
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'application-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%is-log: false%') AS token_log_disabled,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'application-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%is-log: true%') AS legacy_token_log_left,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%com.aryn.cloud.upms.mapper: info%') AS upms_mapper_info,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%com.aryn.cloud.upms.mapper: debug%') AS legacy_upms_debug_left,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'aryn-user-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%com.aryn.cloud.user.mapper: info%') AS user_mapper_info,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'aryn-user-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%com.aryn.cloud.user.mapper: debug%') AS legacy_user_debug_left,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'aryn-auth-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%tob-timeout-seconds%') AS login_guard_registered,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` IN ('application-dev.yml', 'aryn-upms-biz-dev.yml', 'aryn-user-biz-dev.yml', 'aryn-auth-dev.yml')
     AND `group_id` = 'DEFAULT_GROUP'
     AND (`md5` <> MD5(`content`) OR `md5` IS NULL)) AS config_md5_mismatch;
