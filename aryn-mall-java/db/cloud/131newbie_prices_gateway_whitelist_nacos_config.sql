-- 悦航购新人价批量查询接口网关白名单 + boot 兼容路由增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据；锚点不匹配时本次不改动，由末尾自检计数暴露。
--
-- 背景（2026-10-18）：C 端商详 / 购物车新增批量查价接口
-- `/promotion/app/newbie/prices?skuIds=...`，页面以免登方式可访问（未登录只返回活动价、
-- 资格字段为 null；带登录态时额外返回当前用户的资格判定）。接口不在网关白名单时网关返回 401，
-- 前端把 401 一律当作登录态失效 → 清 token 跳登录页。与 boot 模式
-- aryn-boot/application.yml 的 secure.ignore 逐条对应（boot 侧已加 `/app/newbie/prices`）。
--
-- 为什么本脚本比 127 号多做了两件事：
-- 1) 127 号只登记了 cloud 直连前缀 `/promotion/app/newbie/current`，没有登记 boot 兼容前缀。
--    本脚本补齐 `/boot/app/newbie/current` 与 `/boot/app/newbie/prices`（缺哪条补哪条）。
-- 2) 光有白名单不够：如果前端以 boot 模式构建（VITE_OPEN_BOOT=true，请求路径被改写成
--    `/boot/app/newbie/...`）却指向 cloud 网关，路由表里没有 `/boot/app/newbie/**`
--    会直接 404 —— 网关根本没把请求转给 aryn-promotion-biz，白名单还没来得及生效。
--    因此这里按 129 号 couponinfo 的既有做法，同时登记「路由 + 白名单」两件事：
--    新增路由 `aryn-promotion-biz-boot-compat-newbie`（Path=/boot/app/newbie/**，
--    StripPrefix=1 → 转发到 promotion-biz 的 `/app/newbie/**`）。
--
-- 注意：按前缀逐条列出，不用 `/promotion/app/**` 一把放开（写接口必须保持鉴权）。
--
-- 匹配语义：Spring 环境下 Sa-Token 走 SaPathMatcherHolder → AntPathMatcher，
-- `/a/b/**` 同时匹配 `/a/b` 与 `/a/b/...`。
--
-- 幂等判据的写法（重要，129 号的坑）：每步判重都在 `@gateway_probe`（当前 content 末尾补一个
-- 换行）上做 `LIKE '%行\n%'`，这样即使该行正好是 content 的最后一行（YAML 文本末尾没有换行符）
-- 也能命中；129 号直接对 content 判 `LIKE '%行\n%'`，目标行恰好是最后一行时判不出来、
-- 会重复插入同一行。同理 REPLACE 的锚点也不带尾部换行，锚点位于文本末尾时仍能正常追加。
-- 探针每步重算（而不是算一次复用），否则「上一步刚插入的锚点」在下一步看不到，
-- 白名单的插入链会断在第二环。
--
-- 基线同步说明：`3aryn_nacos.sql` 基线里的网关行历史上多次随增量同步（88 号随 2a9022c
-- 一并更新过），但 127 号（新人楼层白名单）未同步；129 号对基线的同步目前仍是工作区
-- 未提交改动。为不干扰他人未提交内容，本脚本只在增量脚本内补齐，不改基线单行 INSERT；
-- 待基线下次整体重建时，把 `/boot/app/newbie/**` 路由与 4 条 newbie 白名单一并落进去即可
-- （本脚本末尾自检可直接作为比对目标：白名单 4 条、路由 1 条）。

USE `aryn_nacos`;

SET @gateway_content = (
  SELECT `content`
  FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml'
    AND `group_id` = 'DEFAULT_GROUP'
    AND `tenant_id` = 'public'
  LIMIT 1
);

-- ---------------------------------------------------------------------------
-- 一、boot 兼容路由：/boot/app/newbie/**
-- 锚点用 couponinfo 的 boot 兼容路由（基线与运行环境都有，位置稳定）；
-- 护栏判据=路由 id 已存在则跳过。锚点不匹配则本次不改动路由
-- （自检 registered_newbie_route_count 会输出 0，不会静默成功）。
-- ---------------------------------------------------------------------------
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%id: aryn-promotion-biz-boot-compat-newbie\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%            - id: aryn-promotion-biz-boot-compat-couponinfo\n%',
    REPLACE(
      @gateway_content,
      '            - id: aryn-promotion-biz-boot-compat-couponinfo',
      '            - id: aryn-promotion-biz-boot-compat-newbie\n              uri: lb://aryn-promotion-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/newbie/**\n                \n            - id: aryn-promotion-biz-boot-compat-couponinfo'
    ),
    @gateway_content
  )
);

-- ---------------------------------------------------------------------------
-- 二、白名单四条（缺哪条补哪条；逐条锚在上一条之后，形成稳定插入链）
--
-- 每条都是「已存在则跳过」+「锚点不存在则不改动」的双重护栏：
-- 锚点链断掉时不会静默成功——后续条目无处可插，末尾 registered_newbie_public_count < 4 即为证据。
-- ---------------------------------------------------------------------------

-- 2.1 cloud 直连前缀：/promotion/app/newbie/current（127 号已插则跳过）
-- 锚点用 discount 行（88 号脚本插入且位置稳定，与 127 号同一锚点）。
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /promotion/app/newbie/current\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /promotion/app/discount/activities/**\n%',
    REPLACE(
      @gateway_content,
      '    - /promotion/app/discount/activities/**',
      '    - /promotion/app/discount/activities/**\n    - /promotion/app/newbie/current'
    ),
    @gateway_content
  )
);

-- 2.2 cloud 直连前缀：/promotion/app/newbie/prices（本次新增）
-- 锚定在上一条之后，保证同域条目相邻、便于人工核对。
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /promotion/app/newbie/prices\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /promotion/app/newbie/current\n%',
    REPLACE(
      @gateway_content,
      '    - /promotion/app/newbie/current',
      '    - /promotion/app/newbie/current\n    - /promotion/app/newbie/prices'
    ),
    @gateway_content
  )
);

-- 2.3 boot 兼容前缀：/boot/app/newbie/current（127 号的历史缺口，一并补上）
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /boot/app/newbie/current\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /promotion/app/newbie/prices\n%',
    REPLACE(
      @gateway_content,
      '    - /promotion/app/newbie/prices',
      '    - /promotion/app/newbie/prices\n    - /boot/app/newbie/current'
    ),
    @gateway_content
  )
);

-- 2.4 boot 兼容前缀：/boot/app/newbie/prices（本次新增）
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /boot/app/newbie/prices\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /boot/app/newbie/current\n%',
    REPLACE(
      @gateway_content,
      '    - /boot/app/newbie/current',
      '    - /boot/app/newbie/current\n    - /boot/app/newbie/prices'
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

-- 自检：registered_newbie_public_count 应输出 4（cloud 直连前缀与 boot 兼容前缀各 2 条）。
-- 历史教训（81 号脚本）：锚点不匹配时脚本会「执行成功但内容没改」，
-- 所以这里必须打印实际命中数，不能只看 UPDATE 无报错。
-- registered_newbie_route_count 应输出 1，且要求路由 id 与 Path 同时存在（只插了一半不算成功）。
-- anchor_* 两个字段用于定位失败原因：计数不足且对应锚点为 0 时，说明环境里的锚点行已被改动，
-- 需要人工按注释里的锚点口径修正后重跑。
-- 判重同样走 CONCAT(content, '\n') 探针，兼容「目标行是最后一行」的场景。
SELECT (
  (SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/newbie/current\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/newbie/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT CONCAT(`content`, '\n') LIKE '%    - /boot/app/newbie/current\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT CONCAT(`content`, '\n') LIKE '%    - /boot/app/newbie/prices\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS registered_newbie_public_count,
(
  (SELECT CONCAT(`content`, '\n') LIKE '%id: aryn-promotion-biz-boot-compat-newbie\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AND (SELECT CONCAT(`content`, '\n') LIKE '%                - Path=/boot/app/newbie/**\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS registered_newbie_route_count,
(
  SELECT CONCAT(`content`, '\n') LIKE '%            - id: aryn-promotion-biz-boot-compat-couponinfo\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_route_present,
(
  SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/discount/activities/**\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_whitelist_present;
