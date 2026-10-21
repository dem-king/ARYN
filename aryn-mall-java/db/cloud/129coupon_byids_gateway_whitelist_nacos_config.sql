-- 悦航购优惠券按ID查询接口网关白名单增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
--
-- 背景：装修「优惠券组合」楼层的手选模式改为按配置的券 ID 取数
-- （`/promotion/app/couponinfo/byIds/{ids}`），不再拉第一页再过滤 ——
-- 被选中的券排在列表第一页之外时，过滤式实现会让整个楼层漏渲染。
-- C 端以免登方式（skipToken）请求该接口；接口不在网关白名单时网关返回 401，
-- 前端把 401 一律当作登录态失效 → 清 token 跳登录页。
-- 与 boot 模式 aryn-boot/application.yml 的 secure.ignore 逐条对应
-- （boot 侧新增 `/app/couponinfo/byIds/**`）。
--
-- 注意：按前缀逐条列出，不用 `/promotion/app/**` 一把放开（写接口必须保持鉴权）。
--
-- 匹配语义：Spring 环境下 Sa-Token 走 SaPathMatcherHolder → AntPathMatcher，
-- `/a/b/**` 同时匹配 `/a/b` 与 `/a/b/...`。

USE `aryn_nacos`;

SET @gateway_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml'
    AND `group_id` = 'DEFAULT_GROUP'
    AND `tenant_id` = 'public'
  LIMIT 1
);

-- 锚点用 groupbuy record 行（根种子 3aryn_nacos.sql 与 88 号脚本后一直存在，
-- 且其后紧跟 couponinfo/page 行，位置稳定）；护栏判据=已登记则跳过。
-- 两条新增一起插：cloud 直连前缀与 boot 兼容前缀各一条。
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_content LIKE '%    - /promotion/app/couponinfo/byIds/**\n%',
  @gateway_content,
  REPLACE(
    @gateway_content,
    '    - /promotion/app/groupbuy/record/page\n',
    '    - /promotion/app/groupbuy/record/page\n    - /promotion/app/couponinfo/byIds/**\n    - /boot/app/couponinfo/byIds/**\n'
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

-- 自检：应输出 2（promotion 前缀与 boot 兼容前缀的 byIds 接口都已登记）。
-- 历史教训（81 号脚本）：锚点不匹配时脚本会「执行成功但内容没改」，
-- 所以这里必须打印实际命中数，不能只看 UPDATE 无报错。
SELECT (
  (SELECT `content` LIKE '%    - /promotion/app/couponinfo/byIds/**\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT `content` LIKE '%    - /boot/app/couponinfo/byIds/**\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS registered_coupon_byids_public_count;
