# 搜索框滚动常驻（首页头部常驻搜索栏）

> 日期：2026-10-27 ｜ 状态：**已实施（自动化 + H5 端到端验证通过；MP 产物构建通过，未真机目检）**
> 范围：C 端小程序（整页装修）+ 管理端装修器 ｜ **零后端接口 / 零 DB 结构变更**（只扩装修 JSON 的组件 props；存量页面开关默认关）
> 关联：[小程序沉浸式导航栏](../2026-10-27-小程序沉浸式导航栏/README.md)（本需求的材质/滚动接线直接复用该能力）

---

## 0. 结论先行

**可以，已落地。** 首页搜索框是装修组件 `search-bar`，原先属于首区块内流内容，滚动时随区块一起滚走
（只剩导航栏）。现在给它加了运营可配的「**滚动常驻**」开关：

1. **整页常驻**：开启后搜索框固定在导航栏下方，滚到页面任何位置都可见、可点，不会随内容滚走。
2. **与导航栏同一块头部**：常驻栏复用沉浸式导航的滚动渐显材质（起始底色 → 磨砂白 / 主题色 / 自定义色，
   双层 + 毛玻璃 + 底边细线），滚动中导航栏与搜索栏始终是同一块连续表面；导航栏自己的底边细线在此时
   让给搜索栏底边，避免头部被切成两段。材质**跟随逐帧原始滚动值、不加 CSS 时间过渡**，变色与内容
   1:1 跟手（2026-10-28 按用户「慢一步/不够丝滑」反馈优化，见 §9）。
3. **静置零变化**：材质层在 `scrollTop=0` 时完全透明，首屏仍是设计稿的「搜索框直接坐在区块渐变上」；
   H5 实测 header 区域与改造前**像素级一致**（最大通道差 1，>2 的像素 0 个）。
4. **位置约束（重要）**：只有「首个渲染区块的第一个组件」是搜索框时才生效——常驻顶边由「导航栏高度 +
   首区块外边距/内边距 + 搜索框自身外边距」**静态计算**，不做异步测距；其余位置安静回退为随页滚动。
5. **存量零回归**：开关默认 `false`；未开启的页面渲染路径、模板结构与改造前完全一致（H5 基线断言覆盖）。

| 改造前（滚动 300px） | 改造后（滚动 300px） |
| --- | --- |
| ![改造前](screenshots/01-before-scrolled300.png) | ![改造后](screenshots/04-after-scrolled300.png) |

（左：搜索框已随首区块滚走、只剩磨砂导航条；右：磨砂白头部 = 导航栏 + 常驻搜索栏，往下滚整页都在。）

---

## 1. 用户诉求与决策

- **原始诉求**：用户提供首页截图（搜索框在首屏区块内），问「滚动时，这个搜索框能否修改成一直常驻」。
- **澄清后选择**：
  - 形态 = **整页常驻**（不是「只在首屏区块内吸顶、区块滚完即消失」，也不是「收进导航栏」）。
  - 开关 = **装修后台加开关**（默认关，首页在装修后台打开，其它页面不受影响）——与仓库「C 端行为由装修
    JSON 驱动」的既有纪律一致，不写死。

## 2. 现状与关键约束

首页发布版本的真实配置（本地 boot 库）：

```jsonc
{
  "navigation": { "backgroundMode": "followFirstSection",
                  "scrollEffect": { "mode": "fade", "surface": "frosted", "distance": 140, "blur": true } },
  "sections[0]": {
    "style": { "backgroundGradient": { "to bottom", "#3678FA → "#EEF3FB" }, "marginY": 0, "paddingY": 0, "radius": 0 },
    "components": ["search-bar", "swiper-banner", "notice"]   // 搜索框是首区块第一个组件
  }
}
```

- 渲染链路：`pages/home` → `useDiyNavbarScroll()`（页面 `provide` 8px 量化的 scrollTop）→ `DiyPage` →
  `hr-navbar`（fixed + 占位，`inject` 滚动值做渐显）+ 区块流。
- **既有纪律**：`DiyPage` 不消费滚动状态（`navbar-scroll.contract.test.ts` 守门），滚动时只允许导航子树
  重渲染——本需求的常驻层同样是「叶子组件自己 `inject`」，`DiyPage` 只下发静态外观。
- 首页搜索框是首区块第一个组件，其自然位置恰好紧贴导航栏下沿，因此「常驻位置 = 静置位置」，
  不需要测距/动画迁移，只需要把实例换成页面级固定层。

## 3. 方案

### 3.1 两层实例：页面级固定层 + 区块内占位层

`DiyPage` 识别到「首区块第一个组件 = `search-bar` 且 `props.sticky === true`」后：

- 在 `hr-navbar` 之后渲染一份 `pinned` 实例（`position: fixed`；`top = 导航高度 + 首区块上边距/内边距`；
  `left/right = 首区块左右边距/内边距`；`z-index: 480`，低于 `wd-navbar` 的 500；去掉根节点 `overflow`
  让材质铺满整条头部）。
- 区块内原实例加 `sticky-placeholder`：`visibility: hidden; pointer-events: none`，**只保留布局高度**，
  保证首屏内容位置与改造前一致、且不会出现两条可见搜索框。
- 常驻实例唯一，故点击/铃铛角标/热词轮播等交互始终落在可见的那一条上；占位实例不跑热词定时器。

eligibility 计算在 `DiyPage`：

```ts
// 内嵌装修（商详/分类/个人中心的 DIY 段）永不常驻；横滑区块不参与
if (props.embedded) return null
const first = sections.value[0]                       // 首个「渲染出来」的区块
if (!first || first.style.horizontalScroll) return null
const component = first.components[0]                 // 必须是第一个组件，才能静态算顶边
if (component?.type !== 'search-bar') return null
if (showData?.sticky !== true) return null
```

### 3.2 材质与导航栏同源：`materialize` 成形

常驻层直接复用 `resolveNavigationScrollAppearance()`（与 `hr-navbar` 同一纯函数）算出：
起始底色层不透明度 `baseOpacity`、目标底色层不透明度 `surfaceOpacity`、`surfaceColor/alpha`、`blur`、
`textColor`、`hairlineOpacity`，并叠加一个「成形」系数：

```
materialize = min(1, rawScrollTop / 8)    // 一个量化步内把底色补上
层不透明度   = 渐显进度 × materialize
```

- `scrollTop = 0` ⇒ `materialize = 0` ⇒ 材质全透明，静置观感 100% 还原设计稿（首屏不糊上导航底色）。
- **滚动值用逐帧原始值**：`useDiyNavbarScroll()` 同时 provide 两份 ref —— 8px 量化的 `scrollTop` 给
  导航栏渐显（控制整条导航的重渲染次数），逐帧原始值 `rawScrollTop` 给常驻搜索栏材质（大面积纯色块，
  量化台阶可见且会与导航栏「差档」）。材质层**不加 CSS transition**（时间过渡会额外滞后 ~180ms）。
- 材质变量 `--diy-nav-surface-bg / --diy-nav-surface-blur / --diy-nav-hairline` 由 `DiyPage` 挂在
  `.diy-page` 根节点继承（与吸顶 Tab 栏同一条链路），因此**导航栏 / 搜索栏 / 吸顶 Tab 栏三处材质同源**。
- 滚动值只在本组件子树（`diy-search-bar`）内 `inject` 消费；`DiyPage` 与装修楼层不重渲染。

### 3.3 细线归属、图标反色、白条轮廓

- **导航栏底边细线让位**：`hr-navbar` 新增 `hairline` prop（默认 `true`）；`DiyPage` 在存在常驻搜索框时
  传 `false`，改由搜索栏底部细线做头部与内容的分界——否则「导航白条 + 1px 细线 + 搜索白条」会在头部
  中间露一条缝。
- **图标反色**：常驻材质出现前按宿主区块底色取可读色（与改造前一致）；材质出现后跟随导航解析出的
  `textColor`，滚动到磨砂白时铃铛/扫码自动翻成深色，与导航标题同一翻转时机。
- **白条轮廓**：材质出现后给内层输入条补 `inset 0 0 0 1px rgba(15,23,42,.08)`，避免配置的白色输入条
  融进磨砂白头部。

### 3.4 吸顶基准线让位

`DiyPage` 的 `--diy-sticky-top`（吸顶 Tab 栏 / 吸顶区块用的基准线）从「导航高度」改为
`stickyOffsetTop = 导航高度 + 常驻搜索栏占用高度`。占用高度 = 首区块上边距/上内边距 + 搜索框上外边距
+ 上内边距 + 行高 48px + 下内边距（与 `diy-search-bar` 固定 CSS 同口径）。这样「猜你喜欢 / 低价捡漏 /
会员专区」吸顶时停在搜索栏下沿，不会被常驻栏盖住。

### 3.5 管理端开关

- `page-design/components/search-bar/setting.vue`：「滚动常驻」`ElSwitch`（`v-model="form.sticky"`，
  默认 `false`），提示「…仅当搜索框位于页面首个区块的第一个组件时生效」。
- `page-designer/registry/component-registry.ts`：search-bar legacy 默认值补 `sticky: false`（新增组件
  默认不常驻）。
- C 端读取 `showData.sticky === true`；存量数据没有该字段 → `false`，零迁移。

## 4. 改动文件

| 文件 | 内容 |
| --- | --- |
| `aryn-mall-uniapp/src/components/diy/diy-search-bar/index.vue` | 新增 `pinned / stickyPlaceholder / pinnedTop / pinnedInsetX / navAppearance / scrollEffect / scrollSurface` props；`inject(DIY_NAVBAR_SCROLL_KEY)` + `resolveNavigationScrollAppearance` 生成两层材质；固定层/占位层根样式；图标反色、白条细边 |
| `aryn-mall-uniapp/src/components/diy/index.vue` | 新增 `stickySearch / stickySearchId / stickyOffsetTop`；页面级渲染 `pinned` 实例并静态下发导航外观；区块内实例加 `:sticky-placeholder`；`--diy-sticky-top` 与区块吸顶 top 改用 `stickyOffsetTop`；`hr-navbar` 传 `:hairline="!stickySearch"` |
| `aryn-mall-uniapp/src/components/hr-navbar/index.vue` | 新增 `hairline` prop（默认 `true`），常驻搜索栏接管头部下沿时关闭自身细线 |
| `aryn-mall-uniapp/src/composables/useDiyNavbarScroll.ts` | 新增 `DIY_NAVBAR_RAW_SCROLL_KEY`：逐帧原始滚动值（导航栏仍用 8px 量化 ref，搜索栏材质用原始值），`onShow` 同步两份 ref |
| `aryn-mall-uniapp/src/components/diy/sticky-search.contract.test.ts`（新增） | 13 条源码契约：位置约束、页面级渲染、占位层、DiyPage 不消费滚动、吸顶让位、材质同源、固定层级、静置透明、细线归属、**逐帧原始值/无时间过渡** |
| `aryn-mall-ui/.../page-design/components/search-bar/setting.vue` | 「滚动常驻」开关 + 默认 `false` + 生效位置提示 |
| `aryn-mall-ui/.../page-designer/registry/component-registry.ts` | search-bar 默认值补 `sticky: false` |
| `aryn-mall-ui/.../page-designer/search-bar-sticky.contract.test.ts`（新增） | 5 条契约：开关绑定/默认值/提示文案/registry 默认值/不放进样式面板 |
| `aryn-mall-ui/.../page-designer/preview-parity.test.ts` | 吸顶偏移断言更新为 `stickyOffsetTop`（C 端多让一条常驻搜索栏；管理端静态预览仍停画布顶部），并注明偏差出处 |
| `docs/.../2026-10-27-搜索框滚动常驻/README.md`（本文） | 需求、方案、验证、偏差、风险与开关步骤 |

> 端到端验证脚本与产物在本机 `outputs/sticky-search/`（`verify.mjs`、`results.json`、`REPORT.md`、
> 截图、DB 备份），该目录为本地验证产物、不入库。

## 5. 验证结果（2026-10-27 本机实跑）

| 项 | 方式 | 结果 |
| --- | --- | --- |
| C 端类型检查 | `cd aryn-mall-uniapp && pnpm type-check` | ✅ exit 0 |
| C 端单测 | `pnpm test:unit` | ✅ **115 文件 / 1207 用例全绿**（含新增 13 条契约） |
| H5 端到端（Playwright + 真实 boot 后端） | `pnpm build:h5:demo` 后 `node outputs/sticky-search/verify.mjs`（A 段显式关、B 段显式开、C 段不拦截接口走真实装修配置、D 段跟手性） | ✅ **25/25 断言通过**：静置时固定层与占位层几何完全重合且材质 opacity=0；`scrollTop=0` header 区域与改造前像素 diff 最大通道 1、>2 像素 0 个；滚动 60/300/1200px 固定层恒在 `top=44px`；滚动 300px 目标底色 `rgba(255,255,255,.86)` + `blur` 与导航栏一致；导航栏细线关闭、改由搜索栏底边承担；回到顶部材质归零；真实装修配置路径同样通过；**跳变时材质与导航栏同一帧到位（≤48ms 采样分辨率）**、连续滚动逐帧滞后 0 帧且逐帧色差 ≤0.02；0 console error |
| MP-WEIXIN 产物 | `pnpm dev:mp-weixin` 全量重建 `dist/dev/mp-weixin` | ✅ 构建成功：`diy-search-bar` 产物含 `search-bar-material__base/__surface/__hairline`、`diy` 产物含 `pinned` 搜索栏节点、`hr-navbar` 细线 `wx:if` 生效、`pages/home` 仍 `__runtimeHooks = 1` |
| 管理端定向 vitest | `npx vitest run --dom <新增契约 / registry / migrate / preview-parity>` | ✅ 新契约 5/5、registry+migrate 回归 66 用例通过；`preview-parity` 搜索栏两条用例通过；剩 2 条失败为**改造前既有**（补给单空态、活动入口条省额，涉及工作区未提交的 C 端 WIP） |
| 管理端 lint | 改动文件 eslint / prettier --check | ✅ 0 error / 全部符合 |
| 材质跟手性探针（2026-10-28 优化前后对照） | `node outputs/sticky-search/smoothness-probe.mjs`（跳变到位时延 + 连续滚动逐帧滞后帧数） | ✅ 优化后：下行 17ms / 上行 17ms 到位（优化前 187ms / 185ms）；连续滚动滞后 **0 帧**（优化前 5 / 9 帧）、逐帧色差 0.0039 / 0.0040（优化前 0.0266 / 0.0255）；静置 header 像素 diff 仍为最大通道 1、>2 像素 0 |
| 本地开发库开关 | 首页草稿 + 已发布版本 `page_content` 置 `props.sticky=true`（14109 字符，中文校验通过），清 `page_design_cache` | ✅ API `/app/pagedesign?pageApp=MOBILE` 返回 `sticky: true` 且中文正常 |

### 5.1 截图

| 静置（=设计稿首屏） | 滚动 60px（材质成形中） |
| --- | --- |
| ![静置](screenshots/02-after-top.png) | ![过渡](screenshots/03-after-scroll60-transition.png) |

| 滚动 1200px（仍常驻） | 真实装修配置（滚动 420px） |
| --- | --- |
| ![深滚](screenshots/05-after-scrolled1200.png) | ![真实配置](screenshots/06-real-config-scrolled420.png) |

头部连续过渡条带（自上而下 = scrollTop 0 → 4 → 8 → 12 → 16 → 24 → 40 → 60 → 80 → 100 → 120 → 140 → 200 → 300px；
导航栏与常驻搜索栏逐行同色递进，见 `smoothness-before/after.md` 与 §9）：

![滚动条带](screenshots/07-scroll-filmstrip.png)

## 6. 与设计稿的偏差 / 已知取舍

1. **管理端画布与草稿预览不渲染常驻层**：两处是静态滚动面（画布不滚动、草稿预览的搜索栏仍是流内预览），
   常驻效果只在 C 端实机出现。`preview-parity` 相应断言更新为 `stickyOffsetTop` 并注明原因。
2. **仅「首个渲染区块的第一个组件」生效**：常驻顶边由静态布局算出，不做异步测距；搜索框若放在区块中段、
   非首个区块或内嵌装修段，开关不生效（回退随页滚动）。管理端提示文案已写明。
3. **过渡带（scrollTop 0→140px）会与导航栏一起透出少量内容**：这是「与导航栏同一套材质」的必然结果
   （目标底色 `alpha .86` + 毛玻璃），首屏与深滚两端均干净；中途急停时可见轻微透底，与导航栏自身表现一致。
4. **占位实例仍在区块内**（`visibility:hidden`）：用于零成本保留首屏布局高度；不可见、不可点、不跑热词定时器。

## 7. 剩余风险 / 待办

- **真机目检未做**：微信开发者工具 + iOS/Android 各一台，重点看 ①`position: fixed` 常驻层与 `wd-navbar`
  的层级与状态栏安全区；②`backdrop-filter` 在不同微信版本的兼容（不支持时仍是 `rgba .86` 半透明兜底）；
  ③Tab 切走/返回时的滚动补同步（`useDiyNavbarScroll` 的 `onShow` 已补一次 viewport 偏移）；④下拉刷新回弹时
  固定层表现。
- **逐帧刷新的真机开销**：常驻搜索栏材质改为消费逐帧原始滚动值后，该叶子子树在滚动时每帧重渲染一次
  （导航栏仍是 8px 量化）。H5 实测丝滑且无掉帧，但 MP 真机 `setData` 频率随之提高，需在低端机观察；
  若真机出现掉帧，可退回「4px 量化 ref」折中（探针脚本可直接复测滞后帧数）。
- **多页面开启**：落地页（`sub-pages/promotion/diy-page`）同样支持；运营在其它页面开启时须遵守「首个区块
  第一个组件」约束，否则无效果。
- **管理端预览二期**：若要草稿预览也演示常驻效果，需要在 `page-preview` 用现有 `scrollTop` 驱动一个
  搜索栏 `pinned` 预览态（本次未做，见 §6.1）。

## 8. 如何开启 / 回滚

**开启（运营）**：管理端 → 首页搭建器 → 选中首区块的「搜索框」→ 内容设置打开「滚动常驻」→ 保存草稿 →
草稿预览确认 → 发布。无需改后端、无需执行 SQL。

**本机已代为开启**（等价运营操作）：首页草稿 `page_design.page_content` 与已发布版本
`page_design_version.page_content` 均写入 `props.sticky = true`，并删除 Redis `page_design_cache:*` 对应 key。
备份与回滚物料在本机 `outputs/sticky-search/db-backup/`：

- `published-2115037628451352577-original.json`：开启前的内容（不含 `sticky`）；
- `enable-sticky-hex.sql`：本次开启用的 SQL（HEX + `CONVERT(... USING utf8mb4)`，规避终端字符集问题）；
- 回滚：用 `original.json` 的 HEX 覆盖两处 `page_content` 并清同名缓存 key 即可。

---

## 9. 顺滑度优化（2026-10-28 用户反馈）

**反馈**：往下滚动时搜索框背景变色「比其他慢一步」；往上滚动时背景颜色变化「不够丝滑」。

**根因（探针量化，非主观判断）**：

| 叠加因素 | 现象 |
| --- | --- |
| 材质层挂了 `transition: opacity 0.18s linear` | 跳变到位比导航栏晚 **~170ms**（187ms vs 18ms）；连续滚动滞后 5–9 帧，上行尾巴更长 |
| 「成形」距离 24px + 8px 量化 | 起步 24px 内材质只有导航栏的 1/3–2/3，起步阶段显得「慢半拍」 |
| 导航栏用 8px 量化值、搜索栏也用它 | 大面积纯色块一档一档跳，不像导航栏那么不容易被注意到 |

**修复**：

1. `useDiyNavbarScroll()` 增加 `DIY_NAVBAR_RAW_SCROLL_KEY`（逐帧原始滚动值，负值按 0 收敛），
   导航栏继续用 8px 量化 ref；consume 方仍只有叶子组件（`hr-navbar` / `diy-search-bar`），
   `DiyPage` 与装修楼层不参与。
2. 常驻搜索栏材质改用原始值，**删除 CSS `transition`**——颜色与内容 1:1 跟手。
3. 成形距离 24px → **8px（一个量化步）**：起步不再有明显滞后；同时保留「静置全透明」的首屏保真。
4. 新增 D 段端到端断言（跳变到位时延 / 连续滚动滞后帧数 / 逐帧色差）与
   `outputs/sticky-search/smoothness-probe.mjs` 探针，防「再加时间过渡」回归。

**优化前后（H5 实测，同一台机器 / 同一后端 / 真实装修配置）**：

| 指标 | 优化前 | 优化后 |
| --- | --- | --- |
| 下行跳变：导航栏 / 搜索栏 surface 到位 | 18ms / **187ms** | 17ms / **17ms** |
| 上行跳变：导航栏 / 搜索栏 surface 还原 | 18ms / **185ms** | 17ms / **17ms** |
| 连续滚动滞后帧数（下行 / 上行） | 5 帧 / 9 帧 | **0 帧 / 0 帧** |
| 连续滚动逐帧色差（下行 / 上行） | 0.0266 / 0.0255 | **0.0039 / 0.0040** |
| 静置 header 与改造前像素 diff | 最大通道 1、>2 像素 0 | 最大通道 1、>2 像素 0（无回退） |

原始探针输出：`smoothness-before.md` / `smoothness-after.md`；头部过渡条带：`screenshots/07-scroll-filmstrip.png`。

**边界**：逐帧刷新只发生在常驻搜索栏这一棵子树；若 MP 低端机实测掉帧，可把原始值 ref 换成 4px 量化
（滞后帧数会回到 1–2 帧，仍远好于时间过渡方案），探针脚本可直接复测。
