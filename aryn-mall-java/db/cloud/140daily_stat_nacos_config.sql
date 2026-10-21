-- 用户日订单汇总 Cloud Nacos 配置增量
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
-- 变更内容：order 服务租户白名单登记 stat_daily_user_order。

USE `aryn_nacos`;

SET @order_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-order-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);
SET @order_content = IF(
  @order_content IS NULL OR @order_content LIKE '%- stat_daily_user_order\n%',
  @order_content,
  REPLACE(
    @order_content,
    '      - dashboard_target\n',
    '      - dashboard_target\n      - stat_daily_user_order\n'
  )
);
UPDATE `config_info`
SET `content` = @order_content,
    `md5` = MD5(@order_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-order-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND @order_content IS NOT NULL
  AND `content` <> @order_content;
