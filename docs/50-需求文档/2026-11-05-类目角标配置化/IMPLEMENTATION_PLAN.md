# 类目角标配置化 — 实施契约（IMPLEMENTATION_PLAN）

> 状态：已实施（2026-11-05）。本机 boot 环境已部署验收，结果见 [交付说明](交付说明.md)；
> 与初版契约的两处实施期调整：①管理端/App 树改为双口径（§2.3）；②「热」seed 配色保持
> 现网 `#FF4D2D`；③boot 租户白名单补在 `aryn-boot/application.yml`（§1.4/§5 的 cloud 侧不变）。
> 关联现状：`goods_category.badge_type` 硬编码 `0 无 / 1 荐 / 2 热`，样式写死在
> `aryn-mall-ui/apps/web-ele/src/views/product/goods-category/form.vue` 的 `BADGE_OPTIONS`、
> `aryn-mall-ui/apps/web-ele/src/views/product/goods-category/index.vue` 的 `CATEGORY_BADGES`、
> `aryn-mall-uniapp/src/utils/category-badge.ts` 的 `CATEGORY_BADGES`。
> 目标：运营在管理后台「商品管理 → 类目角标」自由新增/编辑文字、底色、字色、排序、启停，
> C 端分类页左栏按配置渲染，新增角标不再需要改代码发版。

## 0. 非目标

- 不做角标图片上传（本期只支持文字 + 双色）。
- 不复用到商品/活动等其他域（只服务类目）。
- 不做角标文案的多语言：运营配置的 `badge_text` 原样下发；`zh-CN`/`en-US` 的
  `labels.categoryBadge*` 仅作旧数据兜底。
- 不删除 `goods_category.badge_type` 列（改宽后继续作为 `badge_key` 引用）。

## 1. 数据模型

### 1.1 新表 `goods_category_badge`（租户级）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | varchar(32) PK | `@TableId(type = IdType.ASSIGN_ID)` |
| `badge_key` | varchar(32) NOT NULL | 稳定键，`goods_category.badge_type` 存此值；新建时由后端生成（初值=雪花 id 字符串），创建后不可改 |
| `badge_text` | varchar(8) NOT NULL | 角标文字，1–2 个字符（后端与前端都限制 ≤2） |
| `bg_color` | varchar(16) NOT NULL DEFAULT '#00B578' | 底色 `#RRGGBB` |
| `text_color` | varchar(16) NOT NULL DEFAULT '#FFFFFF' | 字色 `#RRGGBB` |
| `sort` | int NOT NULL DEFAULT 0 | 升序 |
| `status` | char(2) NOT NULL DEFAULT '0' | 0.启用；1.停用 |
| `del_flag` | char(2) NOT NULL DEFAULT '0' | `@TableLogic` |
| `tenant_id` | varchar(32) NOT NULL | 租户拦截器自动注入 |
| `create_time` / `update_time` | datetime | 自动填充 |
| `create_by` / `update_by` | varchar(60) | 自动填充 |

索引：`PRIMARY KEY(id)`、`UNIQUE KEY uk_badge_tenant_key(tenant_id, badge_key)`、
`KEY idx_badge_tenant_sort(tenant_id, sort)`。

> 逻辑删除后 `badge_key` 仍占用唯一键：新建角标的 key 由雪花 id 生成，
> 不会复用，因此不需要为删除场景做特殊处理；已删除角标引用的类目在树接口里解析不到 → 不渲染。

### 1.2 存量列改宽（幂等）

```sql
ALTER TABLE `goods_category`
  MODIFY COLUMN `badge_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
  NOT NULL DEFAULT '0' COMMENT '类目角标：0.无；其它值为 goods_category_badge.badge_key';
```

### 1.3 存量数据兼容 seed（幂等）

- 试点租户 `1590229800633634816` 固定写入两行，`id`/`badge_key` 用 `'1'`/`'2'`
  保持与既有 5 个「荐」/4 个「热」类目引用完全兼容，样式沿用现网绿/红。
- 其它租户若也存在 `badge_type IN ('1','2')` 的存量引用，按
  `(tenant_id, badge_type)` 去重生成配置行（`id=REPLACE(UUID(),'-','')`，key 仍为 `'1'`/`'2'`），
  保证引用不悬空。
- 旧脚本 `db/boot/89goods_category_badge.sql`、`db/cloud/90goods_category_badge.sql`
  的「存量归一」段必须同步修正：删除 `NOT IN ('0','1','2')` 白名单（改为仅 `IS NULL` 或 `= ''`），
  否则重跑旧脚本会把运营新建的角标 key 全部刷回 `'0'`。

## 2. 后端契约（Java）

### 2.1 新增文件

- `aryn-product-api/.../api/entity/GoodsCategoryBadge.java`（仿 `GoodsBrand`）
- `aryn-product-biz/.../mapper/GoodsCategoryBadgeMapper.java`（`BaseMapper`）
- `aryn-product-biz/.../service/IGoodsCategoryBadgeService.java`
- `aryn-product-biz/.../service/impl/GoodsCategoryBadgeServiceImpl.java`
- `aryn-product-biz/.../controller/admin/GoodsCategoryBadgeController.java`
- 单测：`GoodsCategoryBadgeServiceImplTest`（删除保护）＋扩展 `GoodsCategoryServiceImplTest`（树透出 badge）

### 2.2 管理端接口（`/product/goodscategorybadge`，boot 自动改写为 `/boot/goodscategorybadge`）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/page?current&size&status&badgeText` | `product:goodscategorybadge:page` | 分页；`badgeText` like，`status` eq，按 `sort asc, create_time desc` |
| GET | `/list` | `product:goodscategorybadge:get` | 全部未删除（含停用），`sort asc`；类目表单/列表回显用 |
| GET | `/{id}` | `product:goodscategorybadge:get` | 详情 |
| POST | `` | `product:goodscategorybadge:add` | 新增；`badgeKey` 由后端生成，忽略入参 |
| PUT | `` | `product:goodscategorybadge:edit` | 修改；忽略入参 `badgeKey` |
| DELETE | `/{id}` | `product:goodscategorybadge:del` | 被类目引用时返回 `Result.fail("角标已被类目使用，请先解除后再删除")` |

校验规则（Controller 或 Service 内显式校验，项目未用 jakarta validation 注解）：
`badgeText` 去空格后非空且 `length ≤ 2`；`bgColor`/`textColor` 匹配 `^#[0-9a-fA-F]{6}$`；
`sort` 为空默认 0；`status` 为空默认 `'0'`。失败返回 `Result.fail("...")`。

### 2.3 类目树接口字段契约（管理端 `/goodscategory/tree` 与 App `/app/goodscategory/tree` 分口径）

两种口径都先在 `extra` 保留原有键（`categoryPic/description/status/sort`），先查当前租户
`status='0'` 的角标构造 `badgeKey → badge` 映射，再按消费方归一：

- **管理端口径**（`GoodsCategoryController.tree`，供管理端类目列表/表单父级树）：
  - `extra.badgeType` = 数据库原始值（未配置保持 `null`，**不归一**）—— 管理端列表要能回显
    「引用了停用角标」的类目，停用/未知 key 的展示交给管理端自己的 `key → 样式` 映射兜底；
  - `extra.badge` = 命中启用角标时为 `{ "text": "荐", "bgColor": "#00B578", "color": "#FFFFFF" }`，否则 `null`。
- **App 口径**（`AppGoodsCategoryController.tree`，C 端分类页）：
  - 命中启用角标：`extra.badgeType` = 原始 key（如 `'1'`、`'3f2...'`），`extra.badge` = 同上对象；
  - 未命中（未配置 / 已停用 / 已删除 / key 非法）：`extra.badgeType` = `'0'`，`extra.badge` = `null`。
- 两种口径都**必须始终写入 `badge` 键**（值可为 null）：C 端据此区分「新后端明确无角标」与
  「旧后端未下发」。boot 模式 Jackson 为 `default-property-inclusion: always`，null 会保留。
- 实现上服务层只保留一份树构建逻辑，用参数/两个入口区分是否归一，避免复制。

### 2.4 测试要求

- `GoodsCategoryServiceImplTest`：已有 2 个用例更新为新的 `badgeType='0'` 语义，并新增
  「启用角标透出 badge 对象」「停用角标透出 badgeType='0' + badge=null」。
- 新增 `GoodsCategoryBadgeServiceImplTest`：删除被引用角标返回失败、未被引用可删。

## 3. 管理端契约（`aryn-mall-ui/apps/web-ele`）

### 3.1 新增

- `src/api/product/goods-category-badge.ts`：`getPage/getList/getById/addObj/editObj/delObj`
  （路径首段 `product`，禁止硬编码 `/boot`）。
- `src/views/product/goods-category-badge/index.vue`：与 `views/product/goods-brand/index.vue`
  同骨架（PageFilterPanel + PageToolbar + ElTable）。列：角标预览（色块）、文字、底色、字色、
  排序、状态、操作。工具栏「新增」；行内「编辑/删除」；
  `v-access:code` 控制按钮权限（`product:goodscategorybadge:*`）。
- `src/views/product/goods-category-badge/form.vue`：`badgeText`（输入 ≤2）、底色/字色
  `ElColorPicker`、排序 `ElInputNumber`、状态 Radio；弹窗内实时预览。颜色默认
  `#00B578 / #FFFFFF`，可点选常用色快捷值（绿/红/橙/蓝）。

### 3.2 改造

- `views/product/goods-category/form.vue`：
  - 删除写死的 `BADGE_OPTIONS`；打开弹窗时 `getList()` 拉角标；
  - 选项 = `[{label:'无角标', value:'0'}, ...list.map(b => ({ label: 角标文字 + (b.status==='1' ? '（已停用）' : ''), value: b.badgeKey }))]`；
  - 编辑存量类目时若其 `badgeType` 不在返回列表中（历史脏值），追加一个
    `已失效角标（key）` 选项并保持可保存，避免静默清零；
  - 表单 `badgeType` 默认 `'0'`。
- `views/product/goods-category/index.vue`：
  - 删除写死的 `CATEGORY_BADGES` 与本地 `resolveCategoryBadge`；
  - `onMounted`/`load()` 时 `getList()` 构建 `key → {text,bgColor,color}` 映射；
  - 列表角标列按映射渲染（保留旧 key `'1'/'2'` 的本地兜底只用于接口失败场景，可省）；
  - 空/未知 key 显示占位横线。

> 新页面路由由菜单 SQL 注册（component=`product/goods-category-badge/index`），
> 无需改前端静态路由；菜单名「类目角标」，挂在「商品管理」下。

## 4. C 端契约（`aryn-mall-uniapp`）

- `src/utils/category-badge.ts`：
  - `resolveCategoryBadge(badgeType, serverBadge?)`：`serverBadge` 形如
    `{text,bgColor,color}` 且字段合法时直接返回；否则回退旧 `CATEGORY_BADGES` 映射
    （兼容旧后端与未迁移数据）；`badgeType='0'`/空 → null。
  - 保留 `CATEGORY_BADGES`（menu 里只展示语义的两项兜底）与 i18n 词条。
- `src/components/category-browser/index.vue`：`badge: resolveCategoryBadge(item.badgeType, item.badge)`
  （注意展开顺序：先取 `item.badge` 再覆盖本地字段）。
- `src/sub-pages/order/shared-cart/category.vue`：同上。
- 角标方块样式保持现有 `category-rail-item__badge`（宽度随 padding 自适应 1–2 字）；
  2 字时类目名可省略号截断，属可接受。
- `src/utils/category-badge.test.ts`：新增用例——服务端 badge 优先、服务端 badge 非法回退、
  `badgeType='0'` + badge=null → null、旧数据 `'1'/'2'` 仍能回退渲染。

## 5. SQL / 数据脚本清单

| 模式 | 文件 | 内容 |
|---|---|---|
| boot | `db/boot/205goods_category_badge_config.sql` | 建表 + 改宽 `badge_type` + 存量 seed + 自检 SELECT |
| boot | `db/boot/206goods_category_badge_menu.sql` | 菜单/按钮 + 租户/角色授权（INSERT IGNORE） |
| cloud | `db/cloud/210goods_category_badge_config.sql` | 同 boot，目标库 `aryn_product` |
| cloud | `db/cloud/211goods_category_badge_menu.sql` | 同 boot，目标库 `aryn_upms` |
| cloud | `db/cloud/212goods_category_badge_nacos_tenant_tables.sql` | 向 `aryn_nacos.config_info` 的 `aryn-product-biz-dev.yml` 的 `hx.tenant.tables` 追加 `goods_category_badge` |

菜单固定 ID（先在库/脚本中确认不冲突）：

| 用途 | ID | 权限 |
|---|---|---|
| 类目角标（页面） | `2140000000000000100` | `product:goodscategorybadge:page` |
| 列表按钮 | `...101` | `product:goodscategorybadge:page` |
| 查询按钮 | `...102` | `product:goodscategorybadge:get` |
| 新增按钮 | `...103` | `product:goodscategorybadge:add` |
| 修改按钮 | `...104` | `product:goodscategorybadge:edit` |
| 删除按钮 | `...105` | `product:goodscategorybadge:del` |

页面参数：`path=/product/goods-category-badge`、`component=product/goods-category-badge/index`、
`parent_id=1779386487675092994`（商品管理）、`sort=6`、`icon=carbon:tag`、`application_key=app_base`、`type=0`。
授权：显式试点租户 `1590229800633634816` + 超管角色 1；再按「已拥有商品类目菜单
`1496327483721789441`」推导补授角色/租户；同时补齐祖先容器 `1779386487675092994`（商品管理）。
boot 后需清理 Redis `menu_cache::*` 并重新登录才见新菜单（写进交付说明）。

`db/boot/build-full-sql.mjs` 追加两个 section（`205`、`206`），重新生成 `aryn_boot_full.sql`；
`verify-full-sql.mjs` 的 `required` 追加：`CREATE TABLE ` + "`goods_category_badge`"、
`'product:goodscategorybadge:page'`、`'/product/goods-category-badge'`、`MODIFY COLUMN ` + "`badge_type`"。

## 6. 验收命令

```bash
# 后端（先 SQL 后代码；本地无 mysql 客户端时用 docker exec）
cd aryn-mall-java && mvn clean compile -pl aryn-boot -am
cd aryn-mall-java && mvn test -pl aryn-product/aryn-product-biz -am
cd aryn-mall-java/db/boot && node build-full-sql.mjs && node verify-full-sql.mjs

# 管理端
cd aryn-mall-ui && pnpm -F @vben/web-ele run typecheck
cd aryn-mall-ui && pnpm test:unit -- --run   # 或 pnpm test:unit
cd aryn-mall-ui && pnpm lint

# C 端
cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit
```

## 7. 本机 boot 环境验收（Lead 收口，teammate 不要做）

- 备份：`artifacts/db-backup/2026-11-05-category-badge/`（`mysqldump` 相关表）。
- 执行 `db/boot/205`、`db/boot/206`（`docker exec -i aryn-boot-mysql mysql -uroot -pwqy123456`）。
- 重建后端：`mvn clean package -DskipTests -pl aryn-boot -am` →
  `docker compose -f docker-compose-boot.yml build aryn-boot` → `up -d --no-deps --force-recreate aryn-boot`。
- 运行态校验：`/boot/actuator/health`；`/boot/app/goodscategory/tree`（带 `tenant-id` 头）
  节点含 `badge` 对象；`/boot/v3/api-docs` 含 `/goodscategorybadge`；DB 菜单/角标行数自检；
  Redis `menu_cache::*` 清理；管理端（Vite dev http://127.0.0.1:5777）刷新/重登可见「类目角标」。

## 8. 写作用域（并行约束）

| 任务 | 只允许改 |
|---|---|
| 后端+SQL | `aryn-mall-java/aryn-product/**`、`aryn-mall-java/db/**` |
| 管理端 | `aryn-mall-ui/apps/web-ele/src/api/product/**`、`aryn-mall-ui/apps/web-ele/src/views/product/**` |
| C 端 | `aryn-mall-uniapp/src/utils/category-badge.ts`、`aryn-mall-uniapp/src/utils/category-badge.test.ts`、`aryn-mall-uniapp/src/components/category-browser/index.vue`、`aryn-mall-uniapp/src/sub-pages/order/shared-cart/category.vue` |

文档（`docs/**`）、部署、live DB 操作全部由 Lead 负责；teammate 不得修改工作区里与本任务无关的
未提交改动，也不得执行 `git checkout/restore/reset`。

---

## 追加需求（2026-11-05，交付后用户追加）：图片角标与尺寸/圆角自定义

> 初版 §0 非目标「不做角标图片上传（本期只支持文字 + 双色）」由用户追加放开，其余契约不变。

### 数据模型（增量脚本 boot 208 / cloud 213）

| 列 | 类型 | 说明 |
|---|---|---|
| `icon_url` | `varchar(512) NOT NULL DEFAULT ''` | 图片角标地址；空串=文字角标（图片优先于文字） |
| `badge_height` | `int NOT NULL DEFAULT 28` | 高度 rpx，合法 16–96；文字角标字号/内边距由它推导 |
| `border_radius` | `int NOT NULL DEFAULT 4` | 圆角 rpx，合法 0–48 |

旧行加列后自动落到默认值，C 端观感与改造前一致（28rpx 高、19rpx 字号、5rpx 内边距、4rpx 圆角）。

### 接口与校验

- 管理端提交/返回字段：`iconUrl`、`badgeHeight`、`borderRadius`（其余字段不变）。
- 文字与图片二选一：配置图片后文字可留空；未配置图片时文字仍必填 1–2 字（按码点计数）。
- 图片地址只接受 `http(s)://` 或 `/` 开头，长度 ≤512；`javascript:`/`data:` 等一律拒绝。
- 高度/圆角越界返回明确错误（不做静默 clamp）；缺省取 28/4。
- 类目树 `extra.badge` 追加 `iconUrl`（无图为 `""`）、`height`、`borderRadius`，旧行兜底 `""/28/4`。

### 渲染口径

- 图片角标：按 `height` 正方形渲染（宽=高），`borderRadius` 圆角，`aspectFit` 等比缩放。
- 文字角标：`fontSize = clamp(round(height*0.68), 10, 48)`、`paddingX = clamp(round(height*0.18), 3, 16)`，
  高度/行高/圆角/配色全部内联下发；旧后端/旧数据回退默认值。
- 管理端预览按 750rpx 设计稿等比缩放（1rpx = 0.5px）；列表预览支持图片并显示「高 × 圆角」。
- 微信小程序宽屏（MP-WEIXIN ≥600px）按平板密度固定 18px/11px，避免 rpx 在展开态放大约 1.8 倍；
  折叠态（<600px）完全按运营配置渲染。

### 验证

- 后端：`mvn test -pl aryn-product/aryn-product-biz -am`（216 例全过）、boot 打包 + 容器重建、health=UP；
  App 树实测图片角标下发 `iconUrl/height/borderRadius`，旧角标下发 `""/28/4`。
- SQL：208 执行自检 `badge_style_columns_ready=3`；全量脚本重新生成并通过 `verify-full-sql.mjs`。
- 管理端：`typecheck` 通过、`logic.test.ts` 23 例通过、改动文件 Prettier 通过。
- C 端：`pnpm type-check` 通过、`pnpm test:unit` 1716 例通过（角标 32 例）、`pnpm build:mp-weixin` 成功。
