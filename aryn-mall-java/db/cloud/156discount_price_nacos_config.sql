-- 悦航购限时折扣批量查价接口网关白名单增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据；锚点不匹配时本次不改动，由末尾自检计数暴露。
--
-- 背景（2026-10-24）：C 端「限时折扣」会场按活动展示折后价
-- （SKU 10740000000000000355 售价 29.90，8 折价 23.92），但商详 / 购物车 / 加购确认弹层
-- 只拿得到 SKU 售价，展示 29.90，前后不一致。后端新增免登批量查价接口
-- `GET /promotion/app/discount/prices?skuIds=a,b`（boot 模式实际路径
-- `/boot/app/discount/prices`，由 context-path 统一加前缀，控制器不硬编码 /boot）。
--
-- 商详 / 购物车需要一次标注几十行价格，故新接口是**公开读接口**（折扣价是活动配置的
-- 公开价格，不含任何用户维度数据），未登录也要拿到与会场一致的折后价；
-- 不在网关白名单时网关返回 401，前端把 401 一律当作登录态失效 → 清 token 跳登录页。
-- 与 boot 模式 aryn-boot/src/main/resources/application.yml 的 secure.ignore
-- （`- /app/discount/prices`）逐条对应。
--
-- 注意：带 @SaCheckLogin 的单 SKU 接口 `/promotion/app/discount/goods/{skuId}`
-- 语义不变、不得放开，因此按前缀逐条列出，不用 `/promotion/app/**` 一把放开。
--
-- 幂等写法（与 88 / 131 号一致）：先取 content，用「content 末尾补一个换行」的探针做
-- `LIKE '%行\n%'` 判重（目标行恰好是 content 最后一行时也能命中）；锚点用 88 号脚本
-- 插入的 discount 行（基线与运行环境都有，位置稳定）；UPDATE 时同步重算 md5。

USE `aryn_nacos`;

SET @gateway_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml'
    AND `group_id` = 'DEFAULT_GROUP'
    AND `tenant_id` = 'public'
  LIMIT 1
);

-- 护栏：已登记 `/promotion/app/discount/prices` 则跳过；锚点不存在则本次不改动
-- （末尾自检 registered_discount_price_public_count 输出 0，不会静默成功）。
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /promotion/app/discount/prices\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /promotion/app/discount/activities/**\n%',
    REPLACE(
      @gateway_content,
      '    - /promotion/app/discount/activities/**',
      '    - /promotion/app/discount/activities/**\n    - /promotion/app/discount/prices'
    ),
    @gateway_content
  )
);

UPDATE `config_info`
SET `content` = @gateway_content,
    `md5` = MD5(@gateway_content),
    `gmt_modified` = NOW()
WHERE `data_id` = 'aryn-gateway-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `tenant_id` = 'public'
  AND @gateway_content IS NOT NULL
  AND `content` <> @gateway_content;

-- 自检：registered_discount_price_public_count 应输出 1（折扣批量查价接口已登记）。
-- 若为 0 且 anchor_whitelist_present = 0，说明环境里的 discount/activities 锚点已被改动，
-- 需人工按注释里的锚点口径修正后重跑。
-- 历史教训（81 号脚本）：锚点不匹配时脚本会「执行成功但内容没改」，
-- 所以这里必须打印实际命中数，不能只看 UPDATE 无报错。
SELECT (
  SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/discount/prices\n%'
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS registered_discount_price_public_count,
(
  SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/discount/activities/**\n%'
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_whitelist_present;
