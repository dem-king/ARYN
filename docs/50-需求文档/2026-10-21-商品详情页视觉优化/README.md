# 商品详情页视觉优化（对标盒马 / 小象超市）

> 日期：2026-10-21 ｜ 状态：**已实施并部署到本地 boot 环境（三端自动化验证 + 详情接口冒烟通过；小程序待真机目检）**
> 分支：`codex/newbie-price-activity` ｜ 工作目录：仓库根
> 原始请求（用户原文）：「这是盒马和小象超市的商品详情页，第三张图是当前小程序的商品详情页，
> 你结合前两者分析一下，设计一下咱们当前这个，比如评价，我看他们下边都有一个去逛逛，
> 点击就跳转到分类页了，你分析一下，看如何提升一下视觉和美感」。
> 第二轮追加：「如果从这个点击去逛逛按钮跳转动分类页，应该在左上角有一个返回按钮，
> 返回到商品详情页才对」；第三轮追加（截图问属性标签条）：「这一块是怎么实现的」→ 选择方案 B（结构化字段）。

## 一、目标与非目标

**目标**：保留现有商详导航、装修、加购链路，只做增量优化。

1. 评价区从「数量 + 两行文字」补齐为可决策的评价摘要（好评率、标签、图评、空态）。
2. 补「去逛逛」分类回流入口，且返回栈必须真实（左上角返回回原商详，不重建、不丢滚动位置）。
3. 首屏信息重新分层（轮播比例/页码、价格/标题/副标题、区块标题、底栏）。
4. 运营可配置商详首屏「属性标签卡条」（对齐参考图的 地标/冷藏/上榜 横滑卡），数据落库、后台可编辑、C 端渲染。

**非目标（本轮未做，需用户确认后再动）**：

- 不把「猜你喜欢」上移到详情之前（会改现有 tab 顺序）。
- 不加「正品保障 / 慢必赔」等服务承诺（缺少真实配置，不写死假承诺）。
- 属性标签不做商品级差异化图标上传器（后台只给 8 个预置图标 key；http(s) 图标地址后端仍接收，保证不丢历史数据）。

## 二、影响端

| 端 | 改动 |
|----|------|
| 小程序（C 端） | 商详首屏、评价区、去逛逛压栈分类页、属性标签卡条 |
| 管理后台 | 商品编辑表单新增「商品属性标签」编辑器 + 提交前校验 |
| 后端（boot/cloud 双模式） | `goods_spu.attr_tags` JSON 字段、实体/TypeHandler/Mapper/归一化校验 |
| 数据库 | boot `142` / cloud `142` 增量脚本（幂等），`aryn_boot_full.sql` 已重新生成 |

无新增菜单、无新增权限点、不涉及认证与租户口径变化（`attr_tags` 跟随 `goods_spu` 既有租户拦截器）。

## 三、改动明细

### 1. 评价区升级（`GoodsComment.vue`）

- 好评率 + 2×2 统计标签（好评/中评/差评/有图），点标签直接带 `rateType` 进评价列表。
- 评价条目补星级、头像兜底、图片预览、三行截断；≥3 张图显示剩余数量。
- 0 评价展示空态（「还没有评价 / 购买后可以在这里分享真实体验」），底部「查看全部 N 条评价」。
- 评价异步加载完成后通知页面重新量测锚点，避免顶部「评价/详情/推荐」跳转偏移。
- 修断链：评价列表页此前不读 `rateType`，点「好评/有图」实际仍打开全部评价。

### 2. 「去逛逛」分类回流（真实返回栈）

- 新增分类卡 `GoodsCategoryEntry.vue`（同分类两张缩略图 + 分类名 + 主题色按钮），挂在评价卡下方。
- 关键取舍：分类页是 **tabBar 根页面**，`reLaunch` 会销毁商详、`switchTab` 不接收 query。
  因此把分类页 UI 抽成共享组件 `components/category-browser`：
  - tab 分类页 `pages/product/category/index.vue` 继续复用、**无返回箭头**；
  - 新增压栈页 `sub-pages/product/category-browse/index.vue`，`navigateTo` 打开、**左上角返回箭头**，
    `router.back()` 回到原商详实例（页面栈与滚动位置保留）。
- 分类定位仍走 `categoryLocateStore`（tab 页无法带 query 的既有约定）。
- 跨端契约同步：管理端 `canvas-page-shell-contract.test.ts` 原本直接读 tab 分类页源码校验
  「rail 176rpx/排序条 88rpx 与画布 88px/44px 对齐」「装修挂载点在右栏 #banner」；UI 抽成共享组件后
  改为读取 `components/category-browser/index.vue`，并额外断言 tab 页与压栈页都复用该组件
  （否则共享口径会与页面壳漂移）。
- 代价说明：压栈分类页不显示 native tabBar（tabBar 只属于根页面）；若强行保留 tabBar 只能做「假返回」，商详会重建、滚动位置丢失，故不采用。

### 3. 首屏与其他视觉

- 轮播改 1:1（750rpx），右下角 `1/5` 页码；指示点上移避免被价格区盖住。
- 价格 44rpx、标题 32rpx，副标题/包邮/库存已售重新分层；优惠/规格/配送三行分隔线 + 按压反馈。
- 「商品详情」「你可能还会喜欢」统一为主题色竖条 + 标题；底栏轻阴影、主按钮 76rpx + 按压态。

### 4. 商品属性标签（方案 B，结构化字段）

#### 数据契约 `GoodsSpu.attrTags`（最多 6 个）

```json
[
  {
    "key": "attr-1",
    "label": "系列",
    "value": "地标",
    "icon": "location",
    "tone": "green",
    "linkType": "popup",
    "popupTitle": "地标",
    "popupContent": "产地为地理标志产品保护区",
    "linkUrl": null
  }
]
```

| 字段 | 规则 |
|------|------|
| `label` | 必填，≤12 字符（管理端展示为属性名小字） |
| `value` | 必填，≤24 字符（卡片主文案） |
| `icon` | 可空；预置 key `location/snowflake/trophy/package/security/time/shopping-bag/star-filled`，或 http(s) 图片地址 ≤255 |
| `tone` | `green/blue/gray/orange`，未知降级 `gray` |
| `linkType` | `none/popup/page`，未知降级 `none` |
| `popupTitle/popupContent` | `popup` 时正文必填 ≤500，标题 ≤30、缺省取 `value` |
| `linkUrl` | `page` 时必填，仅允许 `/pages/` 或 `/sub-pages/` 开头的站内路径 ≤500 |
| `key` | 服务端按展示顺序重新生成 `attr-1..attr-6`，前端不提交 |

空行（label/value 都空）服务端静默丢弃；`null` 表示不修改（局部更新不会清空已有标签），`[]` 表示清空。

#### 数据库（boot / cloud 双模式）

- `db/boot/142goods_attr_tags_incremental.sql`、`db/cloud/142goods_attr_tags_incremental.sql`：
  `goods_spu.attr_tags JSON NULL`，以 `information_schema` 守卫实现可重复执行，不 DROP/TRUNCATE 存量数据；两份除 `USE`/库名注释外内容一致。
- 已登记 `db/boot/build-full-sql.mjs` 的 `sections`，`aryn_boot_full.sql` 重新生成（21950 行），
  `node db/boot/verify-full-sql.mjs` 静态校验通过（含 `ADD COLUMN \`attr_tags\`` 断言）。

#### 后端

- `GoodsAttrTag`（aryn-product-api）：JSON 元素实体，`Serializable`。
- `GoodsAttrTagListTypeHandler`：fastjson2 序列化 `List<GoodsAttrTag>`；**刻意不标 `@MappedTypes(List.class)`**，
  避免与同模块 `SpecsListTypeHandler`（`List<GoodsSku.Specs>`）全局抢占，只在字段与 XML resultMap 显式指定。
- `GoodsSpu.attrTags`：`@TableField(typeHandler = GoodsAttrTagListTypeHandler.class, jdbcType = JdbcType.VARCHAR)`；
  `@TableName` 已是 `autoResultMap = true`，MP 内置 insert/update/selectById 走同一 handler。
- `GoodsAttrTagNormalizer`（aryn-product-biz）：系统边界归一化与校验（数量/长度/枚举白名单/弹窗必填/站内路径），
  在 `saveGoods`、`updateGoods` 入口调用；非法输入抛 `ArynBusinessException`。
- `GoodsSpuMapper.xml`：`selectSpuById`（管理端详情）与 `selectApiSpuById`（C 端详情）补 `goods_spu.attr_tags`，
  `goodsSpuMap` / `resultMap2` 显式挂 TypeHandler；列表/分页查询不选该列，保持列表出参不变。
- 局部更新（上下架、改状态、逻辑删除）不携带该字段 → MyBatis-Plus 忽略 null，不会清空既有配置。

#### 管理后台（`views/product/goods-spu/form.vue`）

- 「商品子标题」下新增「商品属性标签」编辑器：属性值/属性名/图标下拉/色调/点击行为/删除，
  `popup` 展开弹窗标题+正文，`page` 展开站内路径输入 + 前缀提示；最多 6 行，达上限禁用添加。
- 回显 null 安全：`attrTags` 为 null/缺字段时归一化为 `[]`（icon→none、tone→gray、linkType→none）。
- 纯函数 `validateGoodsAttrTags` 覆盖 >6、名/值同填、长度、popup 正文必填、page 路径前缀；
  在 `submitForm` 中紧随 `validateGoodsSkus` 调用，失败 `ElMessage.warning` 后阻断提交。
- 提交直接带 `state.form.attrTags`（不提交 `key`，由后端重建）。

#### 小程序（`sub-pages/product/goods-detail/`）

- 数据契约与白名单常量：`src/api/product/attr-tags.ts`（tone/linkType/icon 白名单、MAX=6、payload 接口）。
- 纯函数：`components/goods-attr-tags.ts` 的 `normalizeGoodsAttrTags(raw: unknown)` + `isAllowedInternalPagePath()`；
  防御旧数据/脏数据：非数组→`[]`、脏项丢弃、>6 截断、未知枚举降级、危险协议/外链/`..` 越界→`none` 或去图标。
- 组件：`components/GoodsAttrTags.vue`，`scroll-view scroll-x` + 176rpx 卡宽 + 12rpx 间距（一屏露 3~3.5 张），
  tone 四色低饱和底 + 暗色 `dark:`/`--wot-dark-*`；8 个预置图标映射项目既有 `i-carbon:*`（类名写死字面量，UnoCSS 可静态扫描）；
  popup 用 `wd-popup`（`OVERLAY_Z_INDEX.sheet=901`），page 双重校验后 `uni.navigateTo`，none 不显示 chevron 且不响应。
- 接入 `GoodsInfo.vue` 标题卡：标题/副标题/包邮标签之后、库存已售 meta 之前；空数据整块不渲染，
  后端未部署/旧数据时首屏与改动前完全一致。价格、加购、收藏分享逻辑零改动。

## 四、兼容策略

- 后端先上、前端后上：C 端 `attrTags` 缺失整块不渲染；管理端编辑旧商品默认 `[]`，未编辑不会写坏数据。
- 前端先上、后端后上：字段不返回 → 同上降级；提交 `attrTags` 到旧后端会被忽略（Jackson 默认忽略未知字段需确认版本行为；
  若严格模式报错，需后端同步上线，见「剩余风险」）。
- 存量数据零迁移：JSON 列可空，历史商品 `attr_tags = NULL`。

## 五、验收命令与实际结果

### 后端（实际执行）

```bash
cd aryn-mall-java
mvn test -pl aryn-product/aryn-product-biz -am        # 104 用例，0 失败，BUILD SUCCESS
mvn clean compile -pl aryn-boot -am                   # 全聚合 boot 模式编译，BUILD SUCCESS
node db/boot/verify-full-sql.mjs                      # 全量 SQL 静态校验通过（21950 行）
```

新增/修正用例：

- `GoodsAttrTagNormalizerTest`（9 个用例）：断言改为仓库既有约定 `.satisfies(e -> getMsg())`
  （`ArynBusinessException` 不覆写 `getMessage()`，原测试用 `hasMessageContaining` 恒失败）。
- `GoodsSpuServiceImplTest` 新增 2 例：`saveGoods` 超 6 个标签抛业务异常、保存前归一化
  （去空白/丢空行/未知 tone→gray/key 重排）。

### 管理后台（实际执行）

```bash
cd aryn-mall-ui
pnpm -C apps/web-ele run typecheck                    # exit 0
pnpm test:unit apps/web-ele/src/views/product/goods-spu/goods-spu-form-validation.test.ts  # 14/14 通过
pnpm test:unit apps/web-ele/src/views/promotion/page-designer/canvas-page-shell-contract.test.ts  # 6/6 通过
```

`pnpm test:unit` 全仓：104 个测试文件 / 833 用例，832 通过、**1 个既有失败**
（`mobile-retail-normalizers.test.ts` 的 `desc` 期望 `sales_volume`、实际 `sales_volume,id`，
为工作区其他未提交改动，本次未触碰；前一轮记录的另外 2 个 `canvas-page-shell-contract` 失败
是本功能「分类页 UI 抽共享组件」引起，已按新源码位置修正契约并全绿）。

### 小程序（实际执行）

```bash
cd aryn-mall-uniapp
pnpm type-check                                       # exit 0
pnpm test:unit                                        # 80 文件 / 852 用例全过（基线 78/828，新增 24 例）
pnpm build:mp-weixin                                  # exit 0，DONE Build complete
```

构建产物核实：`GoodsInfo.json` 已注册 `goods-attr-tags` 组件；`GoodsAttrTags.wxss` 含 8 个 carbon 图标类、
4 个 tone 类与 popup/暗色样式。构建日志中 `[unocss] failed to load icon "carbon:truck"` 为未改动文件的历史告警。

### 新增测试

| 测试 | 覆盖 |
|------|------|
| `GoodsAttrTagNormalizerTest`（后端 9） | 边界归一化/降级/校验 |
| `GoodsSpuServiceImplTest`（后端 +2） | 保存入口接线、归一化生效 |
| `goods-spu-form-validation.test.ts`（后台 +9） | 编辑器校验各失败分支 |
| `goods-attr-tags.test.ts`（小程序 16） | 脏数据/旧数据全降级分支 |
| `goods-attr-tags.contract.test.ts`（小程序 8） | 挂载位置、横滑属性、图标字面量、popup/navigateTo 守卫 |
| `detail-category-entry.contract.test.ts`（小程序，前轮） | 去逛逛压栈、返回箭头、好评率、评价筛选参数 |

## 六、剩余风险与真机目检清单

- **后端已部署冒烟，前端未人工目检**：boot 已重新打包部署并跑通管理端/C 端详情接口（见第七节）；
  管理后台编辑器与小程序首屏仍待人工目检。
- 小程序真机重点看：① 横滑手感、第 4 张裁切比例、滚动条隐藏、长值省略；② carbon mask 图标在各基础库的显示；
  ③ popup 层级/长正文（500 字）换行与底部安全距离；④ page 卡落到运营配置的已注册页面、未注册时 toast；
  ⑤ 与秒杀条/新人价条/包邮标签/库存 meta 的纵向间距；⑥ 暗色模式。
- 前后端上线顺序：建议后端先上（字段 + 迁移），管理端再上（否则旧后端需容忍未知 JSON 字段），C 端最后上。
- 后台图标下拉仅 8 个预置 key：历史/接口直接写入的 http(s) 图标会以原始 URL 显示在下拉中，不改动则原样回传、不丢数据。
- 属性标签为空时没有任何新布局，属于「后端未部署即与现状一致」的降级底线，已由小程序单测锁定。

## 七、部署执行记录（2026-10-21 21:29–21:36，本地 boot 环境）

- **打包**：`cd aryn-mall-java && mvn -Pboot -DskipTests clean package -pl aryn-boot -am` →
  `BUILD SUCCESS`（33.6s），产物 `aryn-boot/target/aryn-boot.jar`（269MB，Spring Boot 3.5.16，Start-Class `ArynBootApplication`）。
- **数据**：先备份 `aryn_boot`（`mysqldump`，4.0MB，`/tmp/aryn_boot_before_attr_tags.sql`）→
  在 `aryn-boot-mysql` 容器执行 `db/boot/142goods_attr_tags_incremental.sql`，
  **连续执行两次均幂等通过**；确认 `goods_spu.attr_tags json NULL`、中文注释编码正确，存量 394 条商品未受影响。
- **部署**：`docker compose -f docker-compose-boot.yml build aryn-boot` +
  `up -d --no-deps --force-recreate aryn-boot`；容器重建后 24.5s 启动完成，
  `/boot/actuator/health` → `{"status":"UP"}`，容器内 `/app/app.jar` 为 21:29 构建产物。
- **冒烟（真实链路）**：临时给商品 `9740000000000000001` 写入 3 条属性标签（含中文/popup/tone）→
  管理端 `GET /boot/goodsspu/{id}` 与 C 端 `GET /boot/app/goodsspu/{id}` 均返回解析后的 `attrTags` 数组
  （MySQL JSON 列 → `GoodsAttrTagListTypeHandler` → Jackson 全链路通过）→ 测试数据已还原为 `NULL`；
  C 端冒烟产生的 1 条浏览足迹已删除。启动日志无 ERROR。
- **已知既有告警（与本次无关）**：`ClassPathMapperScanner` 报 `fulfillmentExceptionMapper` Bean 重复注册 WARN。
