-- 短信发送频控 Cloud Nacos 配置增量
-- 目标库：aryn_nacos
-- 特性：幂等执行（content 已含该配置则跳过），不删除或重建 Nacos 配置数据。
-- 变更内容：upms 服务信任网关代理头（X-Forwarded-For 取最后一跳），
--           用于短信发送接口 IP 维度频控；boot 单体在 application.yml 中保持 false。

USE `aryn_nacos`;

SET @upms_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-upms-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

SET @upms_content = IF(
  @upms_content IS NULL OR @upms_content LIKE '%trust-forwarded-headers%',
  @upms_content,
  CONCAT(@upms_content, '\n\nsms:\n  rate-limit:\n    trust-forwarded-headers: true\n')
);

UPDATE `config_info`
SET `content` = @upms_content,
    `md5` = MD5(@upms_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-upms-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @upms_content IS NOT NULL
  AND `content` <> @upms_content;
