# 装修预览一致性修复（微页面首页缩略图 / 商城主题页）

> 日期：2026-10-28 ｜ 状态：**已实施（Playwright 截图对比 + 契约测试）** ｜ 范围：管理端装修预览 + 商城主题页 ｜ 零后端 / 零 DB 变更
> 关联：[沉浸式导航栏](../2026-10-27-小程序沉浸式导航栏/README.md)、[搜索框滚动常驻](../2026-10-27-搜索框滚动常驻/README.md)、`preview-parity` 跨端同源守卫
> 截图：`outputs/page-preview-parity/`（修复前后、C 端对照）

---

## 0. 结论先行

两个用户可见问题，**同一个根因 + 两处图片口径漂移**：

1. **「微页面」列表里首页缩略图与微信实机不一致**：搜索框不见了、整块区域被蓝色铺满、banner 被裁切。
2. **「商城主题」页整屏变蓝**：左侧「主题库」面板被蓝色盖住，右侧预览错位。

根因不是数据，而是**预览画布（`preview-canvas.vue`）的导航层没有定位包含块**：沉浸式导航的当前/目标底色画在
`.preview-navigation__base` / `__surface` 两层绝对定位元素上（`position: absolute; inset: 0`），
而 `.preview-navigation` 自身不是定位元素 —— 绝对定位层一路上溯到最近的定位祖先：

| 出现位置 | 包含块（修复前） | 现象 |
|----------|------------------|------|
| 生效卡片缩略图 | `.page-thumb-canvas` 的 `transform`（`scale(0.501)`） | 不溢出手机框，但蓝色铺满整个画布 —— 盖住未定位的搜索框等静态内容 |
| 商城主题页 | 管理端内容区（画布外层只有 `zoom`，不建立包含块） | 蓝色直接逃逸到画布外，铺满整屏、盖住左侧主题库面板 |

编辑器画布 `phone-canvas.vue` 的 `.phone-navigation` 一直是 `position: relative`，预览画布漏了这条，
于是出现「编辑器里正常、列表缩略图/换肤预览不正常」——修复就是两处渲染面对齐同一包含块约定。

另外两处**图片缩放口径**与 C 端不一致（`object-fit: cover` vs 小程序 `scaleToFill`）：banner / 图片广告会被裁掉两侧，
文案与按钮位置相对实机偏移，运营据此调图等于白调；顺带补齐轮播指示点颜色接线（运营可配 `indicatorColor` /
`indicatorActiveColor`，管理端此前是 Element 默认色）。

---

## 1. 改动文件

| 文件 | 内容 |
|------|------|
| `apps/web-ele/src/views/promotion/page-preview/components/preview-canvas.vue` | `.preview-navigation` 增加 `position: relative` 建立包含块（sticky 分支同样满足），注释写明两层绝对定位底色的依赖 |
| `apps/web-ele/src/views/promotion/page-design/components/swiper-banner/index.vue` | 图片 `object-fit: cover → fill`（对齐 C 端 `mode="scaleToFill"`）；指示点改为 8px 圆点并消费 `indicatorColor` / `indicatorActiveColor`（CSS 变量覆盖 ElCarousel 内部按钮） |
| `apps/web-ele/src/views/promotion/page-design/components/image-ad/index.vue` | ElImage `fit="fill"`（内层 `<img>` 的 object-fit 不受外层 CSS 影响）+ 指示点颜色接线 |
| `apps/web-ele/src/views/promotion/page-design/components/common/retail-preview/retail-preview-frame.vue` | 楼层外框 `border` → `box-shadow: inset`：C 端零售楼层根节点无边框，border 会多吃 2px 宽高，与「1rpx = 0.5px」逐值对齐口径冲突 |
| `apps/web-ele/src/views/promotion/mall-theme/index.vue`、新增 `mall-theme/preview-theme.ts` | 预览底栏补齐 4 项（与 C 端 `pages.json` 同序）；导航标题改为首页页名（`page.navigation.title \|\| pageName`，不再硬编码「商城首页」）；预览主题/标注/取色抽纯函数；舞台改 container query + `min(100%, 100cqh × 375/812)`，修 1920×1080 溢出与比例失真 |
| `apps/web-ele/src/views/promotion/page-preview/preview-containment.contract.test.ts` | 新增回归契约：导航层包含块、两端画布约定一致、图片缩放口径、指示点颜色接线 |
| `apps/web-ele/src/views/promotion/mall-theme/mall-theme.contract.test.ts` | 新增商城主题页契约（tabbar 逐项读 C 端 `pages.json` 比对、预览主题绑定、导航标题、缩放/滚动、提示条） |

> 与并行的「沉浸式导航一步切换」需求（同日在改 `navigation-scroll*` / `hr-navbar` / `diy-search-bar`）**互不改同一行为**：
> 本需求只补预览画布的包含块与图片口径，滚动切换逻辑仍归该需求。

---

## 2. 验证

| 项 | 方式 | 结果 |
|----|------|------|
| 修复前复现 | Playwright 打开 `#/promotion/page-design`、`#/promotion/mall-theme` | 缩略图整块蓝、搜索框不可见；商城主题页整屏蓝、主题库面板被盖住 |
| 修复后目检 | 同上（等待图片加载后截图） | 搜索框恢复；商城主题页左侧主题库 + 右侧手机壳预览正常；banner 不裁切；导航标题「悦航购 · 商城首页」 |
| 与 C 端对照 | uniapp H5 dev server（375×812，DPR2）截首页，与预览同宽归一后逐组件比对 | 逐组件数值见独立审计报告 [outputs/page-preview-parity/audit-report.md](../../../outputs/page-preview-parity/audit-report.md)：9 个楼层无整块错位；`escaped=[]`；banner 两端同为拉伸铺满 |
| 草稿预览页 | 生成 preview-token 打开 `#/page-preview/<token>` | 导航 sticky、`__base` 尺寸 = 375×84（不再整屏），页面与 C 端一致 |
| 布局矩阵 | 商城主题页 1266×828 / 1440×900 / 1920×1080 / 1280×720 / 1266×700 五档 | 无溢出/遮挡；手机壳恒等比 375:812；左主题库未被覆盖；屏体内可滚动 |
| 定向单测 | `pnpm exec vitest run --dom apps/web-ele/src/views/promotion` | **51 文件 / 434 用例全绿**（含新增 6+15 条契约） |
| 全仓单测 | `pnpm test:unit` | 123 文件 / 1016 用例全绿 |
| 类型 | `pnpm -F @vben/web-ele run typecheck`、`pnpm check:type` | 均通过（exit 0） |
| Lint | `pnpm lint` | 本任务 7 个文件 eslint/stylelint/prettier 全通过；全仓剩余失败仅并行改动(`api/request.ts`、`session-invalid*.ts`、`login.vue`)与存量基线(`layouts/auth.vue`) |

---

## 3. 边界与遗留

- **预览画布中的动态楼层数据与实机不同**（活动条 `省¥4` vs `省¥7`、优惠券占位券、商品流无限滚动页脚）：管理端预览取管理端接口/占位数据，C 端取当前用户可见数据，属**预期差异**；如需更贴近可后续按「当前生效活动」取数。
- **`replenish-card` 占位卡 vs C 端匿名隐藏**：实为登录态+租户业务模式的触发条件差异（登录 + 综合租户 + 无进行中购物车 + `emptyStrategy=placeholder` 时 C 端渲染同款占位卡），非渲染缺陷；建议后续在预览里标注触发条件或提供「匿名/登录」预览态切换。
- **预览不渲染 C 端「常驻搜索栏」**（P1，审计报告 #1）：C 端整页 DIY 且首区块首个组件是搜索框且开 `sticky` 时，`DiyPage` 会渲染页面级 `position: fixed` 常驻层；管理端预览仍随内容滚动。这是 [搜索框滚动常驻](../2026-10-27-搜索框滚动常驻/README.md) 已注明的「管理端画布/草稿预览暂不渲染常驻层（静态滚动面）」限制，本次沿用，建议后续给 PreviewCanvas 补固定层。
- **goods-tabs 加购按钮图标**（P2）：管理端预览是购物车图标，C 端是圆底「+」的 `quick-cart-button`（含加载/已加态）；属组件级美化差异，未在本次修（不影响布局），建议后续按 C 端组件对齐。
- **tab-nav 分页指示点几何**（P3）：管理端 6px/贴底、H5 采样 8px/距底 10px；微信原生 swiper 指示点尺寸可能与 H5 不同，建议真机目检后再定。
- **商城主题「自动动效」关闭时管理端预览仍自动轮播**（P3）：预览画布未消费主题的 `autoMotionEnabled`，需给 PreviewCanvas 增加动效开关 prop。
- **商城主题页模拟 tabBar 用 16px 圆点占位**：结构/顺序/选中色已与 C 端一致，图标为装饰性占位（编辑器预览惯例）。
- **管理端预览不模拟微信状态栏/胶囊按钮**：导航高度统一 84px（≈ 状态栏 40 + 44，画布与预览同口径），与真机 `statusBarHeight + 44` 有几像素差异，属既有设计取舍。
- 本次不改 C 端渲染（C 端是基准），不改后端/数据库，不涉及双模式配置。

