-- 商城装修发布治理 Nacos 配置增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
-- 变更内容：
--   1) promotion 服务租户拦截表白名单登记 page_design_release / page_design_audit_log；
--   2) 增加装修发布审批开关 decoration.release.approval-required（默认 false，关闭时提交申请即发布）。

USE `aryn_nacos`;

SET @promotion_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-promotion-biz-dev.yml' AND `group_id` = 'DEFAULT_GROUP'
  LIMIT 1
);

-- 1) 租户拦截表白名单：以 page_design_template 为锚点追加两张治理表
SET @promotion_content = IF(
  @promotion_content LIKE '%- page_design_release\n%',
  @promotion_content,
  REPLACE(
    @promotion_content,
    '      - page_design_template\n',
    '      - page_design_template\n      - page_design_release\n      - page_design_audit_log\n'
  )
);

-- 2) 装修发布审批开关：插入在 hx: 配置段之前
SET @promotion_content = IF(
  @promotion_content LIKE '%approval-required%',
  @promotion_content,
  REPLACE(
    @promotion_content,
    '\n\nhx:\n',
    '\n\ndecoration:\n  release:\n    approval-required: false\n\nhx:\n'
  )
);

UPDATE `config_info`
SET `content` = @promotion_content,
    `md5` = MD5(@promotion_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-promotion-biz-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `content` <> @promotion_content;
