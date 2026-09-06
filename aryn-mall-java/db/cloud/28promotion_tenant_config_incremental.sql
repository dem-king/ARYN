-- promotion 服务租户白名单与 XXL-JOB 端口增量配置（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。

USE `aryn_nacos`;

SET @promotion_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

SET @promotion_content = REPLACE(
  @promotion_content,
  '\n\nhx:\n',
  '\n\nxxl:\n  job:\n    port: 9010\n\nhx:\n'
);
SET @promotion_content = REPLACE(
  @promotion_content,
  '      - page_design_template\n',
  '      - page_design_template\n      - seckill_activity\n      - seckill_session\n      - seckill_goods\n      - seckill_order\n      - discount_activity\n      - discount_goods\n'
);

UPDATE `config_info`
SET `content` = @promotion_content,
    `md5` = MD5(@promotion_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-promotion-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `content` <> @promotion_content;
