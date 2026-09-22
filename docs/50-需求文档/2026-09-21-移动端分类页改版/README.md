# 移动端分类页改版（一级图标条 + 二级侧栏 + 内嵌商品流）

> 日期：2026-09-21
> 状态：**已实施并验证（P0+P1）；浮层几何已按参考图二次校准**
> 关联：[移动端商品快捷加购](../../2026-09-21-移动端商品快捷加购/README.md)、[共享采购与商品统一](../../2026-09-20-共享采购与商品统一/README.md)、[船供化交互调研与竞品对标](../../2026-09-19-船供化交互调研与竞品对标/README.md)

---

## 1. 原始请求

> 移动端分类按这个样式进行修改，你看一下这两个图片，给我一个改造方案。

参考图（小象超市风格）两张：

- **图 1 主态**：顶部搜索框（左返回 + 搜索按钮 + 购物车角标）→ 一级分类**圆形图标横滑条**（选中项文字变主题色 + 圆形描边，右端固定「展开」）→ 下方左右分栏：**左栏二级分类竖排**（选中加粗变色、无卡片、无左侧竖条）、**右栏商品流**（排序/筛选 chips + 横向商品卡 + 加购按钮）。
- **图 2 展开态**：点击「展开」弹**全屏「全部分类」浮层**，一级分类 5 列圆形宫格，选中项文字加主题色胶囊底；底部「点击收起⌃」收起。

## 2. 目标与非目标

### 目标

1. 分类 Tab 页视觉与信息结构改为「一级图标条 + 二级侧栏 + 商品流」，对齐参考图。
2. 一级分类**全部可浏览**：默认只看横滑条前几项，通过「展开」浮层可看到全部。
3. 顶部补齐搜索入口与购物车入口（含数量角标）。
4. 保留并复用已有的快捷加购能力，不在分类页重复实现加购规则。
5. 不新增接口、不改数据库表结构；boot / cloud 双模式行为一致。

### 非目标（本期明确不做）

| 项 | 原因 |
|---|---|
| 「会员折扣 / 有机安心 / 严选 / 省心价」等**营销虚拟分类** | 参考图左栏混有营销标签，我们的 `goods_category` 只有真实两级类目；要做需要新表或类目标签字段，属独立需求 |
| 榜单 / N% 人回购 / 近 14 天最低价 / 秒杀价等**营销角标** | 当前 `goods_spu` 与秒杀折扣域没有对应的 C 端聚合字段，需另行设计 |
| 领券浮层、新人券倒计时（图 1 底部） | 与分类页结构无关，另有投放位 |
| 三端（管理后台）改造 | 类目结构未变，管理端 `views/product/goods-category` 无需改动 |

## 3. 参考图 → 现有能力映射

| 参考图元素 | 现有基础 | 结论 |
|---|---|---|
| 搜索框 + 购物车角标 | `components/hr-search-navbar/index.vue`（含 `#right` 插槽）、`store/shoppingCartStore.ts#fetchCartCount`、`components/quick-cart-button` 已用 `wd-badge` | **可直接复用**，只需组合 |
| 一级圆形图标条 | `GET /product/app/goodscategory/tree` 返回一级节点 + `categoryPic` | 结构够用，**但图片数据为空**（见下） |
| 「全部分类」展开浮层 | 无 | **新增组件** |
| 左侧二级竖排 | 现有 `wd-sidebar`（104px 宽、白底、选中态带 4px 左侧竖条），与参考图不符 | **改为自绘 rail** |
| 右侧商品流 | `api/product/spu.ts#getPage` + `sub-pages/product/goods-list` + `quick-cart-button` | 逻辑已有，**需要抽成可复用面板** |
| 一级分类过滤商品 | `GoodsSpuMapper.xml` 的 `selectApiPage` **已支持 `categoryFirstId`**；`AppGoodsSpuController#page` 用实体接收 query | **后端无需改动** |
| 营销角标 | 无数据来源 | 本期不做 |

### 3.1 阻断项：类目图片全为空

统计当前 SQL（boot 与 cloud 语义一致）：

| 来源 | 一级 | 二级 | 有图 |
|---|---|---|---|
| `db/*/2aryn_boot.sql` / `8aryn_product.sql`（遗留 家用电器/手机数码） | 2 | 7 | 9 |
| `db/*/41grocery_catalog_seed.sql`（商超品类） | 12 | 51 | **0** |
| `db/*/62ship_supply_seed_acceptance.sql`（船舶物料） | 1 | 6 | **0** |
| **合计** | **15** | **64** | **共 79 行仅 9 行有图** |

结论：**新版式的骨架是圆形图标，而当前 70/79 行 `category_pic` 为 `NULL`**。
若直接改版，首屏会是一片空白圆形。必须在实现前确定兜底策略（见 §4.6）。

## 4. 改造方案

### 4.1 目标结构

```
┌───────────────────────────────────────────┐
│ ←  [ 🔍 搜索商品        ] [搜索]  🛒 ⑫      │  hr-search-navbar + #right 购物车
├───────────────────────────────────────────┤
│ (◯水果)(◯蔬菜)(◯肉禽蛋)(◯海鲜)… │ 展开 ▸   │  一级横滑条（选中：主题色文字+描边）
├──────────┬────────────────────────────────┤
│ 叶菜类   │  综合 销量 价格            ▦   │  二级 rail + 商品流
│ 根茎类   │  ┌────┐ 商品名                   │
│ 茄果瓜类 │  │图片│ ¥3.90 已售470  ⊕          │
│ …        │  └────┘                         │
└──────────┴────────────────────────────────┘
        ↑ 点击「展开」→ 全屏全部分类浮层（图 2）
```

层级映射：**一级分类 = 横滑条/浮层**，**二级分类 = 左侧 rail**，**商品 = 右侧列表**（与 `goods_spu.category_first_id / category_second_id` 的数据模型天然对齐）。

### 4.2 页面骨架

- 保留 `definePage({ name:'category', layout:'tabbar', navigationStyle:'custom' })` 不变，`pages.json` 里该页已注册为 tabBar 页，**不需要改动路由**。
- 顶栏 `hr-navbar` 换为 `hr-search-navbar`（`disabled` + `left-arrow=false`，点击/回车都跳 `goods-search`），`#right` 插槽放购物车图标 + `wd-badge`；`onShow` 调 `fetchCartCount()`。
- **删除现有高度魔法值**：现在 `.category-wraper` 用
  `height: calc(100vh - var(--window-top) - 44px - env(safe-area-inset-bottom) - 20rpx)`
  并按 MP/H5 写了两份。换成 `hr-search-navbar` 后顶栏实际高度是 `statusBarHeight + 44`（组件自带 `navPlaceholder` 占位），再按 44px 手算必然错位。
  **改为 flex 纵向布局**：页面 `height:100%` → 图标条 `flex:none` → 内容区 `flex:1; min-height:0;`，内部各自滚动。

### 4.3 一级分类横滑条（图 1 上部）

- 结构：`scroll-view scroll-x` + `flex` 子项，每项「圆形图标 96rpx + 名称 24rpx」；选中项：文字主题色 + 圆形描边 2rpx。
- 右端「展开」按钮固定定位，配一条白色渐变遮罩，避免与滚动内容重叠。
- 数据源即 `state.category`（一级节点），无需额外请求。
- 点击行为：切换 `activeFirst` → 左栏 rail 换成该一级的 `children` → 右栏商品流回到「全部」并重新加载。
- 节点数可控（当前 15 个一级），仍建议浮层用 `v-if` 懒挂载。

### 4.4 「全部分类」浮层（图 2）

新增组件 `src/components/category-all-sheet/index.vue`：

- 遮罩层 `z-index:999`：高于 H5 端 uni tabBar（`998`，见 `@dcloudio/uni-h5/style/framework/tabBar.css`）与 SKU 弹层（`990`），**低于搜索导航栏（`1000`）**。
  该层级是关键：参考图中搜索框仍是纯白 `(255,255,255)`、购物车角标仍是满饱和红 `(255,18,52)`，说明导航栏渲染在遮罩之上、始终保持点亮可点。
- **顶边从导航栏下沿开始**，不遮挡搜索框；具体值由 `hr-search-navbar` 暴露的 `placeholderHeight`（状态栏 + 导航栏）透传，页面不再按平台手算高度。
- **底边不贴屏幕底**：参考图面板底边在屏高 86.5% 处，下方保留遮罩带露出被压暗的页面内容；底角带圆角，形成「自上滑出的卡片」观感（实测本机 989px 视口下底边 855px = 86.5%，与参考图一致）。
- 内容：标题「全部分类」+ 5 列宫格（圆形图标 100rpx + 名称 24rpx）+ 底部「点击收起⌃」。
- 选中态：名称加主题色胶囊底（对齐参考图），由父组件传入 `activeFirst`。
- 交互：点击类目 → `emit('select', index)` → 父组件切换并关闭；点击「点击收起」、右上角关闭图标或蒙层均可关闭。

### 4.5 左侧二级 rail（图 1 左栏）

- **替换 `wd-sidebar`**（当前是 `src` 中唯一使用处，移除无外溢影响）。
  原因：`wd-sidebar` 是 104px 固定宽 + 白底 + 选中态左侧 4px 竖条 + `wd-sidebar__padding` 占位块（见 `node_modules/wot-design-uni/components/common/abstracts/variable.scss:844-861`），与参考图「无卡片、无竖条、加粗变色」不一致，样式覆盖成本高于自绘。
- 自绘：`width:168rpx; font-size:26rpx; padding:24rpx 12rpx; text-align:center`；选中 `color: var(--theme-color-primary, var(--wot-color-theme-primary)); font-weight:600`。
- 若一级下 `children` 为空，rail 渲染一个不可点的「暂无子类目」并让右栏按一级拉全部商品。

### 4.6 图标兜底策略（阻断项解法）

优先级从高到低：

1. **运营补图（首选）**：管理端 `views/product/goods-category/form.vue` 已支持上传、且 `categoryPic` 是必填校验，运营补 15 个一级图即可。C 端无需改动。
2. **前端兜底（必须做）**：`categoryPic` 为空时渲染「首字色块圆」——取类目名首字，背景色由 `id` 哈希到一组低饱和色，`color:#fff`。
   - 服务端返回的 `Tree` extra 中 `null` 值会被 hutool **直接丢弃**（已实测），前端只用 `v-if="item.categoryPic"` 判断即可，不要写 `item.categoryPic === null`。
3. 不做「拿一级下第一个商品的图当类目图」——商品图是方图、类目图需要主体居中，混淆后运营无法分辨。

> 若要脚本化给 12 个商超一级类目补默认图，必须同时新建 `db/boot/` 与 `db/cloud/` 两份同编号增量脚本并重新生成 `aryn_boot_full.sql`。本期**建议不做**，避免把外部图片 URL 固化进种子数据。

### 4.7 右侧商品流

- 新增 `src/components/goods-list-panel/index.vue`，抽出「查询 + 排序 + 分页 + 卡片 + 快捷加购」，供分类页与 `sub-pages/product/goods-list` 复用，避免两处各写一份分页/错误处理。
  - Props：`categoryFirstId?`、`categorySecondId?`、`keyword?`、`layout?`
  - 内部：`api/product/spu.ts#getPage` + `components/quick-cart-button`（单规格直加、多规格弹层，规则由 `useQuickCart` 统一）
- 排序 chips：复用现有 `goods-list` 的「综合推荐 / 销量 / 价格 / 新品」口径（后端 `PageArgumentResolver` 解析 `asc`/`desc` 参数）。参考图的「折扣」无后端排序字段，本期不做。
- 分页：分类页内容区用 `scroll-view` + `@scrolltolower` 触发下一页；首屏加载失败要有重试态，空类目要有空态。
- 切换二级分类时：重置 `current=1`、清空列表、内容区回滚到顶部。
- 商品卡片点击 → `goods-detail`（保持现有跳转）。

### 4.8 需要删除的旧逻辑

`src/pages/product/category/index.vue` 现状是为「锚点定位式」分栏服务的，改版后全部失效：

| 现状代码 | 处理 |
|---|---|
| `measureCategoryBoxes()`（L33-54，`getRect` 量测 + 两次 `nextTick` 兜底） | **删除** |
| `handleChange()`（L56-69，重复点击同一分类的 scroll-top 偏移技巧） | **删除** |
| `onScroll()`（L70-81，滚动反查激活项） | **删除** |
| `itemScrollTop` / `scrollTop` / `getRect, isArray` 导入（L2、L17-18） | **删除** |
| `toGoodsList(categoryId)` 传 `categoryId`（L84-88） | 保留跳转能力，但参数语义改为 `categoryFirstId` / `categorySecondId`（见 §5.2） |
| 右侧一级 banner 图 + 白卡宫格（L102-118） | 由新结构替换；`item.categoryPic` 的用途从「区块 banner」变为「圆形图标」 |

改版后左栏与右栏是**筛选关系**而非**锚点跳转关系**，这段量测逻辑没有替代品，直接删除即可。

## 5. 接口与数据

### 5.1 不新增接口

| 用途 | 接口 | 变化 |
|---|---|---|
| 类目树 | `GET /product/app/goodscategory/tree` | 不变（返回 `id/name/children` + 内联 `categoryPic/description/status/sort`） |
| 商品分页 | `GET /product/app/goodsspu/page` | 不变，新增传参 `categoryFirstId`（SQL 已支持） |
| 购物车数量 | `GET /mall-order/app/shopping-cart/count` | 复用 `shoppingCartStore.fetchCartCount()` |
| 加购 | `POST /mall-order/app/shopping-cart` | 复用 `useQuickCart` |

后端 `aryn-product`、`aryn-order` **一行不用改**。

### 5.2 参数语义收敛（要注意的兼容点）

`sub-pages/product/goods-list/index.vue:64` 目前是 `state.queryParams.categorySecondId = options?.categoryId`，即 `categoryId` 实际承载的是**二级**类目。
`components/diy/link-resolver.ts:112` 也按 `?categoryId=${targetId}` 生成链接。

建议：goods-list 同时接受 `categoryFirstId` / `categorySecondId`，**保留 `categoryId` 作为二级的兼容别名**，避免装修链接、优惠券跳转（`coupon-card`、`coupon-user`、`shopping-cart` 均跳到 `goods-list`）失效。

### 5.3 双模式与多租户

- 前端沿用 `/product/...` 首段，由 `api/core/instance.ts` 的 `rewriteBootUrl` 在 boot 下改写为 `/boot/...`；**禁止**在页面里判断 `VITE_OPEN_BOOT` 或硬编码 `/boot`。
- 类目树查询走 MyBatis 租户拦截器，按当前 `tenant-id` 隔离，不需要额外传参。
- 无表结构改动 → **不需要新增 SQL 增量脚本**，也不涉及 biz 模块间 import。

## 6. 改动文件清单

| 类型 | 文件 | 说明 |
|---|---|---|
| 改 | `aryn-mall-uniapp/src/pages/product/category/index.vue` | 整体重构（顶栏、图标条、rail、商品流容器） |
| 新 | `aryn-mall-uniapp/src/components/category-all-sheet/index.vue` | 全部分类浮层 |
| 新 | `aryn-mall-uniapp/src/components/category-icon-strip/index.vue` | 一级图标横滑条（含「展开」） |
| 新 | `aryn-mall-uniapp/src/components/goods-list-panel/index.vue` | 可复用商品流（分页/排序/加购） |
| 改 | `aryn-mall-uniapp/src/sub-pages/product/goods-list/index.vue` | 改为复用 `goods-list-panel`；新增 `categoryFirstId/categorySecondId` 入参 |
| 改 | `aryn-mall-uniapp/src/code-review-contract.test.ts` | 增加改版契约（图标兜底、浮层懒挂载、双参数兼容） |
| 可选 | `aryn-mall-uniapp/src/components/goods-list-panel/contract.test.ts` | 新增单测：一级过滤参数拼装、分页重置 |

商城端为纯前端改动，**后端与 SQL 无文件变更**。

## 7. 分阶段实施

| 阶段 | 内容 | 说明 |
|---|---|---|
| **P0 骨架** | 顶栏替换（搜索 + 购物车角标）、一级图标条、全部分类浮层、二级 rail 自绘、删除旧量测逻辑 | 不改商品流，右栏暂留「二级宫格 + 点击跳 goods-list」 |
| **P1 商品流** | 抽 `goods-list-panel`、分类页内嵌商品流、一级/二级过滤参数、分页与空态 | 与参考图完全一致；goods-list 页同步收敛为复用 |
| **P2 打磨** | 图标兜底色板、加载骨架屏、浮层动效、排序 chips、H5 安全区微调 | 视觉收口 |

> P0 结束时页面结构已对齐参考图，可作为一次可独立验收的交付；P1 才引入商品流。

## 8. 验收清单

| # | 场景 | 预期 |
|---|---|---|
| 1 | 进入分类 Tab | 顶栏为搜索框 + 购物车角标；下方 15 个一级分类圆形图标可横滑 |
| 2 | 未补图的类目 | 显示「首字色块圆」，不出现空白/破图 |
| 3 | 点击「展开」 | 浮层 5 列宫格；**搜索框保持可点**、面板底边在 86.5% 屏高处、下方露出压暗内容 |
| 4 | 浮层选中某类 | 该类目文字加主题色胶囊底；点击后浮层关闭、图标条选中项同步 |
| 5 | 切换一级 | 左栏 rail 换成该一级的二级类目，右栏商品回到第一页 |
| 6 | 切换二级 | 商品列表按 `categorySecondId` 过滤，列表滚动回顶部 |
| 7 | 商品卡片加购 | 单规格直加、多规格弹规格层（不触发卡片跳转） |
| 8 | 列表分页 | 滚动到底加载下一页，无重复/丢页 |
| 9 | 购物车角标 | 加购后数量 +1；重新进入页面 `onShow` 刷新 |
| 10 | boot 模式 | 请求为 `/boot/app/goodscategory/tree`、`/boot/app/goodsspu/page` |
| 11 | cloud 模式 | 请求为 `/product/app/...`，网关可路由 |
| 12 | 合规 | 源码中不出现 `VITE_OPEN_BOOT === 'true'` 或硬编码 `/boot` |

验证命令：

```bash
cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit
cd aryn-mall-uniapp && pnpm build:mp-weixin
cd aryn-mall-uniapp && pnpm dev:h5          # H5 手工核对
cd aryn-mall-uniapp && pnpm dev:mp-weixin   # 微信开发者工具核对
```

## 9. 风险

| 风险 | 影响 | 对策 |
|---|---|---|
| 类目无图（70/79 行） | 新版式失去视觉骨架 | 前端首字色块兜底 + 运营补图清单（§4.6） |
| 一级下商品量大 | 首屏慢、翻页多 | 默认选中二级首个类目；一级「全部」走 `categoryFirstId`；分页 10 条 |
| 浮层 z-index 低于 H5 tabBar | 浮层被 tabBar 切掉 | 显式 `z-index:1000+`，并在 H5 实测 |
| `categoryId` 语义迁移 | 装修位/优惠券跳转失效 | 保留兼容别名（§5.2） |
| 左右分栏与页面滚动耦合 | 小程序双滚动条、卡顿 | 顶栏+图标条固定，仅内容区 `scroll-view`；不再使用页面级滚动 |
| 自绘 rail 无键盘/读屏语义 | 无障碍退化 | 保留 `role`/`aria-label`（H5），触控热区 ≥ 88rpx |

## 10. 已确认的决策

1. **右栏内嵌商品流**（P1 已做），与参考图一致。
2. **类目图标**：前端首字色块兜底 + 运营逐步补图，不做种子 SQL 补外部 URL。
3. **营销虚拟分类**（会员折扣/有机安心/严选）本期不做，如需要请单独立项。

## 11. 实施结果（2026-09-21）

### 11.1 变更文件

| 类型 | 文件 | 说明 |
|---|---|---|
| 新 | `src/components/category-icon-strip/index.vue` | 一级圆形图标横滑条 + 「展开」 |
| 新 | `src/components/category-all-sheet/index.vue` | 全屏「全部分类」浮层（`z-index: 1200`） |
| 新 | `src/components/goods-list-panel/index.vue` | 可复用商品流（排序/分页/卡片/快捷加购） |
| 新 | `src/utils/category-icon.ts` | 首字色块兜底（纯函数） |
| 新 | `src/components/category-nav-contract.test.ts` | 结构守门（11 例） |
| 新 | `src/utils/category-icon.test.ts` | 兜底规则单测（6 例） |
| 改 | `src/pages/product/category/index.vue` | 整体重构，删除锚点量测逻辑 |
| 改 | `src/sub-pages/product/goods-list/index.vue` | 改为复用 `goods-list-panel` |
| 改 | `src/components/quick-cart-button/contract.test.ts` | 加购入口断言随重构迁移到面板 |

后端、SQL、管理端**零改动**（后端 `selectApiPage` 已支持 `categoryFirstId`）。

### 11.2 实施中发现并修复的三个真实缺陷

| # | 缺陷 | 后果 | 修复 |
|---|---|---|---|
| 1 | 分类页在路由白名单内（访客可浏览），但新增的购物车角标在 `onShow` 无条件调 `fetchCartCount()` | 访客进入分类页即触发 401，被统一错误处理**直接踢到登录页** | 加 `authStore.isLoggedIn` 守卫（与购物车页一致），未登录角标按 0 处理 |
| 2 | 存量类目存在**已填 `category_pic` 但域名不可达**（种子数据指向示例域名） | 只判断字段是否为空会渲染出**空白圆**，比无图更糟 | 图标条与浮层都增加 `@error` 兜底：加载失败同样走首字色块 |
| 3 | P1 初版没有待类目树返回就渲染商品流 | 先按「全部分类」白查一次商品，随后立刻被真实筛选覆盖 | `treeLoaded` 置位后再挂载面板 |

### 11.3 实测结论

浏览器（H5 dev，825×989 视口）逐项验证：

| # | 场景 | 结果 |
|---|---|---|
| 1 | 分类 Tab 结构 | 图标条 15 个类目、二级 rail、商品流、购物车角标全部渲染 ✅ |
| 2 | 无图类目 | 「蔬菜/水果/肉禽蛋…」显示首字色块 ✅ |
| 3 | 已填图但 URL 不可达 | 「家用电器/手机数码」同样回落首字色块，无空白圆 ✅ |
| 4 | 一级筛选 | 点「蔬菜」→ rail 换为 6 个二级类目，商品流加载 3 条叶菜 ✅ |
| 5 | 二级筛选 | 点「根茎类」→ 商品流换为土豆/洋葱/胡萝卜 ✅ |
| 6 | 展开浮层 | 15 个类目 5 列宫格，`position:fixed` / `z-index:1200`，当前项「蔬菜」高亮 ✅ |
| 7 | 布局 | 内容区 698px = 视口 989 − tabBar 50；排序条 + 滚动区正好填满，无溢出、无双滚动条 ✅ |
| 8 | 卡片 → 详情 → 返回 | 返回后 rail 选中项与商品列表状态保持 ✅ |
| 9 | 搜索跳转 | 分类页 → 搜索页 → 搜索结果 → 商品列表页链路正常 ✅ |
| 10 | 类目树接口 | `total=15` 一级类目（含船舶物料）✅ |
| 11 | 一级过滤接口 | `categoryFirstId` → total=15 ✅ |
| 12 | 二级过滤接口 | `categorySecondId` → total=3 ✅ |
| 13 | 双模式路径 | `/product/app/goodscategory/tree` → boot 下改写为 `/boot/app/goodscategory/tree`；商品分页、购物车数量同理 ✅ |

命令验证：

```bash
cd aryn-mall-uniapp
pnpm type-check      # ✅ 0 error
pnpm test:unit       # ✅ 20 files / 137 tests passed
pnpm build:mp-weixin # ✅ Build complete
pnpm build:h5        # ✅ Build complete
```

### 11.4 浮层几何二次校准（2026-09-21 追加）

用户对比参考图后指出两个偏差，均已按参考图**实测像素**修正：

| # | 问题 | 参考图实测证据 | 修正 |
|---|---|---|---|
| 1 | 浮层把搜索框整个盖住了 | 参考图搜索框 `(255,255,255)` 纯白、购物车角标 `(255,18,52)` 满饱和 —— 未被任何遮罩压暗；而面板下方页面内容是 `(100,100,100)` 灰，确实被压暗 | 遮罩 `z-index` 由 `1200` 降到 `999`（**低于**导航栏 `1000`），面板 `top` 改为导航栏真实占位高度 |
| 2 | 浮层铺满到屏幕最底部 | 参考图面板底边在 832/953 = **87.3%**（屏幕含安全区）屏高处，下方有约 121pt 的压暗内容带 | 面板改为 `bottom: 13.5%`，并加底角圆角 |

补充发现并修复的第三个问题：

| # | 问题 | 后果 | 修正 |
|---|---|---|---|
| 3 | 宫格密度过大（圆 128rpx、行距 222rpx） | 15 个类目共 3 行，第三行被面板底边**裁掉半个圆** | 按参考图实测（圆 ~100rpx、行距 ~167rpx）收紧间距，3 行全部完整可见 |

**导航栏高度改为单一事实来源**：`hr-search-navbar` 新增 `defineExpose({ placeholderHeight })`，页面读取后透传给浮层，避免各页面再按平台手算导航栏高度（旧版分类页正是栽在 `44px/84px` 手算上）。

本机实测（989px 视口）：

| 指标 | 实测 | 参考图 |
|---|---|---|
| 导航栏下沿 / 面板顶边 | 45 / 44 px（无缝衔接，差 1px 为边框） | 面板紧贴搜索栏下沿 |
| 面板底边占屏高 | 855 / 989 = **86.5%** | 87.3% |
| 面板底角圆角 | 26.88px | 有圆角 |
| 面板打开时点搜索框 | 成功跳转 `goods-search` | 搜索框可点 |
| 三行类目 | 全部 `fully: true` | 全部可见 |

### 11.5 剩余风险与未做项

| 项 | 说明 |
|---|---|
| P2 未做 | 加载骨架屏、浮层动效、H5 安全区微调 |
| Boot 运行态未实测 | 本机只起了 cloud 容器栈，`/boot` 路径为静态改写核对 + 后端 mapper 口径一致，未跑真实 boot 实例 |
| 微信小程序真机未实测 | 仅完成 `build:mp-weixin` 构建与产物结构核对 |
| 运营补图 | 15 个一级类目仍需在管理后台 `views/product/goods-category` 补图，兜底不替代正式配图 |
| 商品图 | 商超种子商品的 `spu_urls` 亦为空，商品卡片暂无图 |
