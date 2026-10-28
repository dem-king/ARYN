-- 全局单点登录 Cloud Nacos 配置增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行（已是 false 则跳过），不删除或重建 Nacos 配置数据。
--
-- 背景：Sa-Token 默认 is-concurrent=true，同一账号可在多台设备/多个浏览器同时在线。
--      业务要求「同一账号任意时刻只保留一个有效会话，新登录顶掉旧登录」：
--      管理端 Web 与配送端登录同为 TOB 设备类型，会互顶；C 端（TOC）同样单设备。
--
-- 变更：cloud 所有服务（含 gateway）通过 spring.config.import 引入的共享配置
--      application-dev.yml 中 sa-token.is-concurrent 由 true 改为 false。
--      （替换范围由 Sa-Token replacedRange 默认 CURR_DEVICE_TYPE 决定，只顶同设备类型）
-- 对应 boot：aryn-boot/src/main/resources/application.yml 的 sa-token.is-concurrent=false。
-- 生效：Nacos 配置不会热刷新 Sa-Token 全局配置，执行后需重启各微服务（boot 模式重打包重启）。
--
-- 执行：mysql -u root -p aryn_nacos < 166global_single_session_nacos_config.sql

USE `aryn_nacos`;

SET @app_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'application-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

-- 幂等：已是单点配置（或配置缺失）时原样保留，只有仍为 true 才替换。
SET @app_content = IF(
  @app_content IS NULL
    OR @app_content LIKE '%is-concurrent: false%'
    OR @app_content NOT LIKE '%is-concurrent: true%',
  @app_content,
  REPLACE(@app_content, 'is-concurrent: true', 'is-concurrent: false')
);

-- content 未变但 md5 与内容不一致时也一并校准，避免历史漂移一直报错。
UPDATE `config_info`
SET `content` = @app_content,
    `md5` = MD5(@app_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'application-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @app_content IS NOT NULL
  AND (`content` <> @app_content OR `md5` <> MD5(@app_content));

-- 自检：single_session_registered 应为 1，legacy_multi_session_left 应为 0，
-- config_md5_mismatch 应为 0。锚点不匹配时脚本会「执行成功但内容没改」，
-- 必须看这三个数，不能只看 UPDATE 无报错。
SELECT
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'application-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%is-concurrent: false%') AS single_session_registered,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'application-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND `content` LIKE '%is-concurrent: true%') AS legacy_multi_session_left,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'application-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
     AND (`md5` <> MD5(`content`) OR `md5` IS NULL)) AS config_md5_mismatch;
