-- 悦航购船供营销二期 Cloud Nacos 配置增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
-- 变更内容：
--   promotion 服务租户白名单登记 promotion_activity / promotion_lock。

USE `aryn_nacos`;

SET @promotion_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);
SET @promotion_content = IF(
  @promotion_content IS NULL OR @promotion_content LIKE '%- promotion_activity\n%',
  @promotion_content,
  REPLACE(
    @promotion_content,
    '      - page_design_template\n',
    '      - page_design_template\n      - promotion_activity\n      - promotion_lock\n'
  )
);
UPDATE `config_info`
SET `content` = @promotion_content,
    `md5` = MD5(@promotion_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-promotion-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @promotion_content IS NOT NULL
  AND `content` <> @promotion_content;
