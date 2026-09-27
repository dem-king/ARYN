-- 悦航购 C 端公开读接口网关白名单增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
--
-- 背景：小程序登录后点首页金刚区分类，被提示「登录已过期」并跳回登录页。
-- 金刚区落地页（sub-pages/product/goods-list）会以**免登**方式（前端 skipToken、
-- 不带 satoken 头）请求商品品牌列表等读接口；接口不在网关白名单时网关返回 401，
-- 前端把 401 一律当作登录态失效 → 清 token 跳登录页。
--
-- 本脚本补 cloud 网关 `aryn-gateway-dev.yml` 的免登白名单。product 域已被
-- `/product/app/**` 覆盖（品牌 / 类目 / 商品列表都在其下），缺的是 promotion 域的
-- 秒杀与折扣**读**接口 —— 金刚区「限时秒杀」「限时折扣」入口直接打这两个接口。
-- 与 boot 模式 aryn-boot/application.yml 的 secure.ignore 逐条对应。
--
-- 注意：带写语义的 `/promotion/app/seckill/order`（下单）、
-- `/promotion/app/discount/goods/{skuId}`（控制器上有 @SaCheckLogin）**不得**放进
-- 白名单，因此这里按前缀逐条列出，不用 `/promotion/app/**` 一把放开。
--
-- 匹配语义：Spring 环境下 Sa-Token 走 SaPathMatcherHolder → AntPathMatcher，
-- `/a/b/**` 同时匹配 `/a/b` 与 `/a/b/...`（已用 ant matcher 实测），故不需要再单列裸路径。

USE `aryn_nacos`;

SET @gateway_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml'
    AND `group_id` = 'DEFAULT_GROUP'
    AND `tenant_id` = 'public'
  LIMIT 1
);

-- 锚点用 pagedesign 行（3aryn_nacos.sql 基线与运行环境都有，
-- 且各增量脚本都往它后面插，位置稳定）。护栏用**结构性条目**判据，已登记则跳过。
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_content LIKE '%    - /promotion/app/seckill/sessions/**\n%',
  @gateway_content,
  REPLACE(
    @gateway_content,
    '    - /promotion/app/pagedesign/**\n',
    '    - /promotion/app/pagedesign/**\n    - /promotion/app/seckill/sessions/**\n    - /promotion/app/seckill/goods/**\n    - /promotion/app/discount/activities/**\n'
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

-- 自检：应输出 3（3 条 promotion 公开读接口都已登记）。
-- 历史教训（81 号脚本）：锚点不匹配时脚本会「执行成功但内容没改」，
-- 所以这里必须打印实际命中数，不能只看 UPDATE 无报错。
SELECT (
  (SELECT `content` LIKE '%    - /promotion/app/seckill/sessions/**\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /promotion/app/seckill/goods/**\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /promotion/app/discount/activities/**\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS registered_public_read_count;
