-- 悦航购船舶域租户白名单补丁（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 背景：56 号脚本为 aryn_vessel 新增了 vessel_call_change_log（靠港变更日志），
--       但 47 号脚本写入的 aryn-vessel-biz-dev.yml 租户白名单仍只有
--       vessel_info / vessel_member / vessel_call 三张表。
--       TenantSchemaValidator 对“库中带 tenant_id 的表”与白名单做双向严格校验，
--       漏配会直接抛 IllegalStateException("多租户 schema 校验失败: 未配置租户表: [vessel_call_change_log]")，
--       导致 aryn-vessel-biz 启动失败并无限重启。
-- 特性：幂等执行，不删除或重建 Nacos 配置数据；重复执行无副作用。
-- 执行：mysql -u root -p < 60vessel_tenant_whitelist_patch.sql

USE `aryn_nacos`;

SET @vessel_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-vessel-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

SET @vessel_content = IF(
  @vessel_content IS NULL OR @vessel_content LIKE '%- vessel_call_change_log%',
  @vessel_content,
  REPLACE(
    @vessel_content,
    '      - vessel_call\n',
    '      - vessel_call\n      - vessel_call_change_log\n'
  )
);

UPDATE `config_info`
SET `content` = @vessel_content,
    `md5` = MD5(@vessel_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-vessel-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @vessel_content IS NOT NULL
  AND `content` <> @vessel_content;

-- 验证：白名单应包含 vessel_info / vessel_member / vessel_call / vessel_call_change_log
SELECT `data_id`, `group_id`
FROM `config_info`
WHERE `data_id` = 'aryn-vessel-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `content` LIKE '%- vessel_call_change_log%';
