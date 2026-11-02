-- 秒杀每日排期：Cloud 模式租户表与订阅消息配置（增量、可重复执行）
--
-- 目标库：aryn_nacos
-- 内容：
--   1) aryn-promotion-biz-dev.yml 的 hx.tenant.tables 追加 3 张秒杀排期表；
--   2) aryn-promotion-biz-dev.yml 追加秒杀提醒配置（template-id 需运营在 Nacos 填写）；
--   3) aryn-message-biz-dev.yml 追加微信订阅模板映射 SECKILL_SESSION_START。
-- 特性：幂等（按表名/标记判重），不删除或重建 Nacos 配置数据。
--
-- 执行：mysql -u root -p aryn_nacos < 177seckill_tenant_config_nacos.sql

USE `aryn_nacos`;

-- 1) 租户表白名单
SET @promotion_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
);
SET @promotion_content = REPLACE(
  @promotion_content,
  '      - seckill_order\n',
  '      - seckill_order\n      - seckill_session_template\n      - seckill_goods_template\n      - seckill_subscribe\n'
);
UPDATE `config_info`
SET `content` = @promotion_content,
    `md5` = MD5(@promotion_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-promotion-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `content` NOT LIKE '%seckill_session_template%';

-- 2) 秒杀提醒配置（template-id 为空时 C 端隐藏提醒按钮，后台只落库不发送）
SET @promotion_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
);
SET @promotion_content = CONCAT(
  @promotion_content,
  '\n\n# seckill-daily-reminder\naryn:\n  seckill:\n    reminder:\n      enabled: ${SECKILL_REMINDER_ENABLED:true}\n      template-id: ${WECHAT_SUBSCRIBE_TEMPLATE_SECKILL_START:}\n      offset-minutes: ${SECKILL_REMINDER_OFFSET_MINUTES:10}\n'
);
UPDATE `config_info`
SET `content` = @promotion_content,
    `md5` = MD5(@promotion_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-promotion-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `content` NOT LIKE '%seckill-daily-reminder%';

-- 3) 微信订阅模板映射（与 Boot 模式 wechat.subscribe.template-map 对齐）
SET @message_content = (
  SELECT `content` FROM `config_info`
  WHERE `data_id` = 'aryn-message-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
);
SET @message_content = CONCAT(
  @message_content,
  '\n# seckill-subscribe-template\nwechat:\n  subscribe:\n    app-id: ${WECHAT_SUBSCRIBE_APP_ID:}\n    template-map:\n      ORDER_DELIVERY_TASK: ${WECHAT_SUBSCRIBE_TEMPLATE_DELIVERY:}\n      SECKILL_SESSION_START: ${WECHAT_SUBSCRIBE_TEMPLATE_SECKILL_START:}\n'
);
UPDATE `config_info`
SET `content` = @message_content,
    `md5` = MD5(@message_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-message-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `content` NOT LIKE '%seckill-subscribe-template%';

SET FOREIGN_KEY_CHECKS = 1;
