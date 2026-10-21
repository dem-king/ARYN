-- 悦航购新人专享楼层网关白名单增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据。
--
-- 背景：C 端新人专享楼层（`/promotion/app/newbie/current`）对所有人展示新人价
-- 做营销吸引（行业惯例：浏览浅判、结算硬判），前端以免登方式（skipToken）请求，
-- 接口不在网关白名单时网关返回 401，前端把 401 一律当作登录态失效 → 清 token 跳登录页。
-- 与 boot 模式 aryn-boot/application.yml 的 secure.ignore 逐条对应。
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

-- 锚点用 discount 行（88 号脚本已插入且位置稳定）；护栏判据=已登记则跳过。
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_content LIKE '%    - /promotion/app/newbie/current\n%',
  @gateway_content,
  REPLACE(
    @gateway_content,
    '    - /promotion/app/discount/activities/**\n',
    '    - /promotion/app/discount/activities/**\n    - /promotion/app/newbie/current\n'
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

-- 自检：应输出 1（新人专享楼层接口已登记）。
-- 历史教训（81 号脚本）：锚点不匹配时脚本会「执行成功但内容没改」，
-- 所以这里必须打印实际命中数，不能只看 UPDATE 无报错。
SELECT (
  (SELECT `content` LIKE '%    - /promotion/app/newbie/current\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS registered_newbie_public_count;
