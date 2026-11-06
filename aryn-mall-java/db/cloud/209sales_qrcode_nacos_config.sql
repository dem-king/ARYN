-- 销售推广码小程序版本配置增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 作用域：只修改 public 命名空间（config_info.tenant_id = 'public'）下的 aryn-user-biz-dev.yml。
--         config_info 唯一键为 (data_id, group_id, tenant_id)，Nacos 用 tenant_id 区分命名空间；
--         其他命名空间（如独立 dev/test/prod namespace）需按各自的 aryn-user-biz-{profile}.yml
--         单独配置 wx.miniapp.env-version，本脚本不跨命名空间复制。
-- 特性：幂等执行（见下方锚点规则），不删除或重建 Nacos 配置数据。
--
-- 背景（2026-11-05 需求「销售业绩归属与提成」，见
--       docs/50-需求文档/2026-11-05-销售业绩归属与提成/IMPLEMENTATION_PLAN.md 4.5）：
--   推广码生成服务 SalesQrcodeServiceImpl 通过 @Value("${wx.miniapp.env-version:release}") 读取
--   小程序版本（release 正式版 / trial 体验版 / develop 开发版），生成失败时按 trial → develop
--   降级重试。代码默认 release，体验版/开发版联调必须显式配置，否则正式版码在未发布时生成失败。
--
-- 变更（仅配置，无表结构变更）：
--   1) public 命名空间 aryn-user-biz-dev.yml：追加 wx.miniapp.env-version（默认 release，可用环境变量覆盖）
--
-- 幂等锚点：仅当 public 下该 data_id 的 content 既不包含根级 `wx:` 也不包含 `env-version` 时追加一次；
--           若已存在 `wx:`（无论是否已有 env-version）则原样保留、不追加，避免根级 wx: 出现重复
--           YAML key（重复 key 会导致 Nacos 配置解析失败/覆盖语义不明确）。
--
-- 对应 boot：aryn-boot/src/main/resources/application.yml 的 wx.miniapp.env-version；
--           无 Nacos 时的本地兜底见 aryn-user/aryn-user-biz/src/main/resources/application.yml。
-- 生效：@Value 为启动期读取，执行后需重启 aryn-user-biz（cloud）/重新打包重启 boot。
-- 执行：mysql -u root -p aryn_nacos < 209sales_qrcode_nacos_config.sql

USE `aryn_nacos`;

SET NAMES utf8mb4;

-- ===== 追加推广码小程序版本配置到 public 命名空间的 aryn-user-biz-dev.yml =====
SET @user_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-user-biz-dev.yml'
    AND `group_id` = 'DEFAULT_GROUP'
    AND `tenant_id` = 'public'
  LIMIT 1
);

-- 幂等：content 缺失、已有根级 wx:（哪怕还没有 env-version）或已有 env-version 时都不追加。
SET @needs_env_version = (
  @user_content IS NOT NULL
    AND @user_content NOT LIKE '%wx:%'
    AND @user_content NOT LIKE '%env-version%'
);
SET @user_content = IF(
  @needs_env_version,
  CONCAT(@user_content,
    '\n\n\n# 推广码小程序版本（2026-11-05 销售业绩归属与提成）：release 正式版 / trial 体验版 / develop 开发版\n',
    'wx:\n',
    '  miniapp:\n',
    '    env-version: ${WX_MINIAPP_ENV_VERSION:release}\n'),
  @user_content
);

-- 更新 content 并按 content 重算 md5；条件中显式 `OR md5 IS NULL`：
-- MySQL 里 `md5 <> ...` 在 md5 为空时结果为 NULL（不成立），若不单列 md5 IS NULL，
-- 「content 已含 env-version 但 md5 为空」的行不会被修正，而下方自检会把 md5 IS NULL 计为不一致。
UPDATE `config_info`
SET `content` = @user_content,
    `md5` = MD5(@user_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-user-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `tenant_id` = 'public'
  AND (@user_content IS NOT NULL)
  AND (`content` <> @user_content OR `md5` <> MD5(@user_content) OR `md5` IS NULL);

-- ===== 自检（public 命名空间）：已配置数应为 1，md5 不一致数应为 0 =====
SELECT
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'aryn-user-biz-dev.yml'
     AND `group_id` = 'DEFAULT_GROUP'
     AND `tenant_id` = 'public'
     AND `content` LIKE '%env-version:%') AS qrcode_env_version_configured,
  (SELECT COUNT(*)
   FROM `config_info`
   WHERE `data_id` = 'aryn-user-biz-dev.yml'
     AND `group_id` = 'DEFAULT_GROUP'
     AND `tenant_id` = 'public'
     AND (`md5` <> MD5(`content`) OR `md5` IS NULL)) AS config_md5_mismatch;
