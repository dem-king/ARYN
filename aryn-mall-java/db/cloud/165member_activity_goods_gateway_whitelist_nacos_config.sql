-- 悦航购会员专区自动数据源接口网关白名单 + boot 兼容路由增量（Cloud 微服务模式）
-- 目标库：aryn_nacos
-- 特性：幂等执行，不删除或重建 Nacos 配置数据；锚点不匹配时本次不改动，由末尾自检计数暴露。
--
-- 背景（2026-10-25）：会员专区 Tab 改为「自动读取会员价活动里的商品」（装修多 Tab 新增
-- dataSource.mode="activity"），C 端不再手选商品，活动加减商品即同步。新增免登接口
-- `GET /promotion/app/member/activity-goods?activityId=`（boot 模式实际路径
-- `/boot/app/member/activity-goods`，由 context-path 统一加前缀，控制器不硬编码 /boot），
-- 返回活动元信息与按活动 SKU 配置顺序去重保序的 SPU 列表；未登录也照常返回（只含活动/
-- 商品 ID，无用户维度数据）。接口不在网关白名单时网关返回 401，前端把 401 一律当作
-- 登录态失效 → 清 token 跳登录页（与 /app/member/prices、/app/newbie/prices 同一踩坑）。
--
-- 双模式说明（务必分清职责，不要重复落 DB 脚本）：
-- * boot 模式：由 `aryn-boot/src/main/resources/application.yml` 的
--   secure.ignore `- /app/member/activity-goods` 承担（本次已加，与 `- /app/member/prices`
--   相邻登记），boot 侧无 DB 脚本；
-- * cloud 模式：由本脚本登记 Nacos 网关配置（data_id='aryn-gateway-dev.yml'），
--   同时补齐 boot 兼容前缀与 boot 兼容路由，三处缺一不可：
--   1) 白名单 cloud 直连前缀 `- /promotion/app/member/activity-goods`（免登放行，路由由既有
--      `/promotion/**` → aryn-promotion-biz 承担）；
--   2) 白名单 boot 兼容前缀 `- /boot/app/member/activity-goods`；
--   3) 路由 `aryn-promotion-biz-boot-compat-member-activity-goods`：
--      Path=**/boot/app/member/activity-goods（精确路径）** + StripPrefix=1
--      → 转发到 promotion-biz 的 `/app/member/activity-goods`。
--      光有白名单不够：前端以 boot 模式构建（VITE_OPEN_BOOT=true，请求路径被改写成
--      `/boot/app/member/activity-goods`）却指向 cloud 网关时，路由表里没有该路径会直接 404，
--      网关根本没把请求转给 aryn-promotion-biz，白名单还没来得及生效（131/164 号的教训）。
--
-- ⚠ 路由 Path 必须精确写 `/boot/app/member/activity-goods`，**绝不能写 `/boot/app/member/**`**：
--   user 域已占用 `/boot/app/member/current-info`、`/boot/app/member/benefits`
--   （Gateway StripPrefix 后即 aryn-user-biz 的 `/app/member/...`），一旦用通配前缀，
--   这两个请求会被抢到 aryn-promotion-biz 上 404。164 号已登记精确的 prices 路由，本脚本
--   继续按精确路径登记，绝不扩大成前缀通配。
--
-- 幂等判据的写法（与 88 / 131 / 156 / 164 号一致，129 号的坑）：每步判重都在 `@gateway_probe`
-- （当前 content 末尾补一个换行）上做 `LIKE '%行\n%'`，这样即使该行正好是 content 的
-- 最后一行（YAML 文本末尾没有换行符）也能命中；REPLACE 的锚点不带尾部换行，锚点位于
-- 文本末尾时仍能追加。探针每步重算（而不是算一次复用），否则「上一步刚插入的锚点」
-- 在下一步看不到，插入链会断。
--
-- 锚点与兜底（环境差异较大，必须多锚点）：
-- * 既有环境（已按增量升级）：164 号已插入精确的 `aryn-promotion-biz-boot-compat-member-price`
--   路由与 `/promotion/app/member/prices`、`/boot/app/member/prices` 两条白名单，本脚本优先
--   锚在这些行之后，保持同域条目相邻、便于人工核对；
-- * 全新基线环境：`3aryn_nacos.sql` 基线的网关配置目前只有 pagedesign / groupbuy / couponinfo
--   三条 boot 兼容路由，没有 newbie/member-price 的 boot 路由与相关白名单（131/164 号注释里
--   已登记该基线同步欠账）。故每一步都留了兜底锚点：路由兜底锚 newbie 路由 Path 行、再兜底
--   couponinfo 路由 Path 行；cloud 白名单兜底锚 `/promotion/app/newbie/prices`、
--   `/promotion/app/discount/prices`；boot 白名单兜底锚 `/boot/app/newbie/prices`、
--   `/boot/app/newbie/current`，再兜底上一步刚插入的 `/promotion/app/member/activity-goods`。
--   主锚点与兜底锚点都缺失时本次不改动，末尾 anchor_* 与实际计数会暴露，不会静默成功。
--
-- 注意：按前缀逐条列出，不用 `/promotion/app/**` 一把放开（写接口必须保持鉴权）。
--
-- 基线同步说明：为不干扰他人未提交内容，本脚本只在增量脚本内补齐，不改
-- `3aryn_nacos.sql` 基线单行 INSERT；待基线下次整体重建时，把本脚本的
-- 「1 条精确路由 + 2 条白名单」一并落进去即可（末尾自检可直接作为比对目标：
-- 白名单 2 条、路由 1 条，且不得出现 `/boot/app/member/**` 通配）。

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
-- 一、boot 兼容路由：/boot/app/member/activity-goods（精确路径，不能通配）
-- 主锚点：164 号插入的 member-price 精确路由 Path 行（既有环境有）；
-- 兜底锚点：newbie 路由 Path 行（131 号插入）、再兜底 couponinfo 路由 Path 行
-- （基线即有，位置稳定，129 号先例）。
-- 护栏判据=路由 id 已存在则跳过；锚点都不在则本次不改动路由
-- （自检 registered_activity_goods_route_count 会输出 0，不会静默成功）。
-- ---------------------------------------------------------------------------
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%id: aryn-promotion-biz-boot-compat-member-activity-goods\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%                - Path=/boot/app/member/prices\n%',
    REPLACE(
      @gateway_content,
      '                - Path=/boot/app/member/prices',
      '                - Path=/boot/app/member/prices\n                \n            - id: aryn-promotion-biz-boot-compat-member-activity-goods\n              uri: lb://aryn-promotion-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/member/activity-goods'
    ),
    IF(
      @gateway_probe LIKE '%                - Path=/boot/app/newbie/**\n%',
      REPLACE(
        @gateway_content,
        '                - Path=/boot/app/newbie/**',
        '                - Path=/boot/app/newbie/**\n                \n            - id: aryn-promotion-biz-boot-compat-member-activity-goods\n              uri: lb://aryn-promotion-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/member/activity-goods'
      ),
      IF(
        @gateway_probe LIKE '%                - Path=/boot/app/couponinfo/**\n%',
        REPLACE(
          @gateway_content,
          '                - Path=/boot/app/couponinfo/**',
          '                - Path=/boot/app/couponinfo/**\n                \n            - id: aryn-promotion-biz-boot-compat-member-activity-goods\n              uri: lb://aryn-promotion-biz\n              filters:\n                - StripPrefix=1\n              predicates:\n                - Path=/boot/app/member/activity-goods'
        ),
        @gateway_content
      )
    )
  )
);

-- ---------------------------------------------------------------------------
-- 二、白名单两条（缺哪条补哪条；boot 兼容前缀锚在上一步刚插入的行之后，形成稳定插入链）
--
-- 每条都是「已存在则跳过」+「锚点不存在则不改动」的双重护栏：
-- 锚点链断掉时不会静默成功——后续条目无处可插，末尾 registered_activity_goods_public_count < 2
-- 即为证据。
-- ---------------------------------------------------------------------------

-- 2.1 cloud 直连前缀：/promotion/app/member/activity-goods（本次新增）
-- 主锚点 `/promotion/app/member/prices`（164 号插入，既有环境有）；
-- 兜底锚点 `/promotion/app/newbie/prices`（131 号）、再兜底 `/promotion/app/discount/prices`
-- （88/156 号插入，基线即有，位置稳定）。
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /promotion/app/member/activity-goods\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /promotion/app/member/prices\n%',
    REPLACE(
      @gateway_content,
      '    - /promotion/app/member/prices',
      '    - /promotion/app/member/prices\n    - /promotion/app/member/activity-goods'
    ),
    IF(
      @gateway_probe LIKE '%    - /promotion/app/newbie/prices\n%',
      REPLACE(
        @gateway_content,
        '    - /promotion/app/newbie/prices',
        '    - /promotion/app/newbie/prices\n    - /promotion/app/member/activity-goods'
      ),
      IF(
        @gateway_probe LIKE '%    - /promotion/app/discount/prices\n%',
        REPLACE(
          @gateway_content,
          '    - /promotion/app/discount/prices',
          '    - /promotion/app/discount/prices\n    - /promotion/app/member/activity-goods'
        ),
        @gateway_content
      )
    )
  )
);

-- 2.2 boot 兼容前缀：/boot/app/member/activity-goods（本次新增，与 1 号精确路由配套）
-- 主锚点 `/boot/app/member/prices`（164 号）；兜底 `/boot/app/newbie/prices`、
-- `/boot/app/newbie/current`；再兜底锚上一步刚插入的 cloud 直连行
-- `/promotion/app/member/activity-goods`（探针已重算，能看见）。
SET @gateway_probe = CONCAT(IFNULL(@gateway_content, ''), '\n');
SET @gateway_content = IF(
  @gateway_content IS NULL
    OR @gateway_probe LIKE '%    - /boot/app/member/activity-goods\n%',
  @gateway_content,
  IF(
    @gateway_probe LIKE '%    - /boot/app/member/prices\n%',
    REPLACE(
      @gateway_content,
      '    - /boot/app/member/prices',
      '    - /boot/app/member/prices\n    - /boot/app/member/activity-goods'
    ),
    IF(
      @gateway_probe LIKE '%    - /boot/app/newbie/prices\n%',
      REPLACE(
        @gateway_content,
        '    - /boot/app/newbie/prices',
        '    - /boot/app/newbie/prices\n    - /boot/app/member/activity-goods'
      ),
      IF(
        @gateway_probe LIKE '%    - /boot/app/newbie/current\n%',
        REPLACE(
          @gateway_content,
          '    - /boot/app/newbie/current',
          '    - /boot/app/newbie/current\n    - /boot/app/member/activity-goods'
        ),
        IF(
          @gateway_probe LIKE '%    - /promotion/app/member/activity-goods\n%',
          REPLACE(
            @gateway_content,
            '    - /promotion/app/member/activity-goods',
            '    - /promotion/app/member/activity-goods\n    - /boot/app/member/activity-goods'
          ),
          @gateway_content
        )
      )
    )
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

-- 自检（必须打印实际命中数，不能只看 UPDATE 无报错，81 号历史教训）：
-- registered_activity_goods_public_count 应输出 2（cloud 直连前缀 + boot 兼容前缀各 1 条）；
-- registered_activity_goods_route_count 应输出 1，且要求路由 id 与 Path 精确行同时存在
--   （只插了一半不算成功；Path 必须精确，不接受通配）；
-- wildcard_activity_goods_route_present 应输出 0：确认没有把 `/boot/app/member/**` 通配写进路由，
--   否则会抢走 user 域 `/boot/app/member/current-info`、`/boot/app/member/benefits`。
-- 四个 anchor_* 用于定位失败原因：
--   anchor_member_price_route_present / anchor_member_price_public_present 是主锚点；
--   anchor_newbie_route_present / anchor_newbie_price_public_present 是兜底锚点。
--   计数不足时看对应锚点：仅主锚点 0 而计数正常 = 走了兜底分支（全新基线环境属正常）；
--   主锚点与兜底锚点都为 0 = 环境里的锚点行已被改动，需人工按注释口径修正后重跑。
-- 判重同样走 CONCAT(content, '\n') 探针，兼容「目标行是最后一行」的场景。
SELECT (
  (SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/member/activity-goods\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  + (SELECT CONCAT(`content`, '\n') LIKE '%    - /boot/app/member/activity-goods\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS registered_activity_goods_public_count,
(
  (SELECT CONCAT(`content`, '\n') LIKE '%            - id: aryn-promotion-biz-boot-compat-member-activity-goods\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
  AND (SELECT CONCAT(`content`, '\n') LIKE '%                - Path=/boot/app/member/activity-goods\n%' FROM `config_info`
    WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1)
) AS registered_activity_goods_route_count,
(
  SELECT CONCAT(`content`, '\n') LIKE '%                - Path=/boot/app/member/**\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS wildcard_activity_goods_route_present,
(
  SELECT CONCAT(`content`, '\n') LIKE '%            - id: aryn-promotion-biz-boot-compat-member-price\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_member_price_route_present,
(
  SELECT CONCAT(`content`, '\n') LIKE '%                - Path=/boot/app/newbie/**\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_newbie_route_present,
(
  SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/member/prices\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_member_price_public_present,
(
  SELECT CONCAT(`content`, '\n') LIKE '%    - /promotion/app/newbie/prices\n%' FROM `config_info`
  WHERE `data_id` = 'aryn-gateway-dev.yml' AND `group_id` = 'DEFAULT_GROUP' LIMIT 1
) AS anchor_newbie_price_public_present;
