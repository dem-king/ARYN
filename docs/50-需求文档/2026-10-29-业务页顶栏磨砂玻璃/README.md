# 业务页顶栏磨砂玻璃（滚动切换）落地记录

> 日期：2026-10-29｜范围：C 端移动端（H5 / 小程序）｜**零后端 / 零 DB 变更**

## 需求

首页（装修页）滚动后顶栏是一层「半透明白 + 背景模糊」的磨砂玻璃。要求把同一材质推广到
移动端其他自定义导航页；用户明确选择「全部自定义导航页」并同意分类页头部改覆盖层。

## 结论

玻璃效果成立的前提是**内容真的从顶栏下方穿过**。因此分三类落地：

| 类型 | 做法 | 页数 |
|------|------|:--:|
| 页面级滚动 | 宿主页 `onPageScroll(handlePageScroll)` 字面量（小程序按页面源码生成滚动位标记） | 40 |
| 内部列表滚动（z-paging） | 导航栏从 `#top` 槽上移为页面级 fixed 覆盖层；列表 `@scroll` 驱动；内容区首行放 `hr-navbar-space` 等高占位组件（高度取运行时实测状态栏，见文末追加） | 11 |
| 内部独立滚动区（scroll-view / fixed 布局） | 保持原结构：顶栏在上、滚动区在下，内容不穿过顶栏 | 12 |

**保持原状的 12 页**（不是遗漏，是结构上不存在可模糊的内容）：
`promotion/seckill`、`promotion/seckill-rule`、`promotion/discount`、`message/chat`、
`order/shared-cart/detail`、`order/shared-cart/category`、`product/goods-list`、
`product/ship-supply`、`order/order-list`、`delivery/task-list`、`promotion/coupon/coupon-user`
（后 5 个的 `#top` 还挂着 Tab / 筛选条，整块上移风险高）；`vessel/port-vehicles` 用原生导航栏。
后续若要做，需把 Tab / 筛选条一起纳入覆盖层（参照分类页方案）。

## 实现要点

- **材质同源**：`schema/navigation-scroll.ts` 新增 `FROSTED_SCROLL_EFFECT` /
  `FROSTED_SCROLL_SURFACE`（白 86% + `blur(18px) saturate(160%)`），与装修页磨砂白共用同一组常量与解析函数。
- **状态同源**：新增 `useFrostedNavbar()`，内部复用 `useDiyNavbarScroll()` 的「顶部 ↔ 已滚动」
  布尔状态与切回本页的视口校准，只额外 `provide(FROSTED_NAVBAR_KEY, true)`。
- **组件自动套用**：`hr-navbar` / `hr-search-navbar` 注入开关后，未收到装修配置时自动取磨砂常量；
  装修页显式下传的 `scrollEffect`（含 `mode=none`）仍然优先。商详自定义顶栏单独处理。
- **一步切换**：任意正滚动立即切目标态，回到顶部立即还原（与首页一致，无过渡动画）。
- **分类页覆盖层**：搜索栏 + 一级图标条固定在内容之上，右栏商品流上探到页面顶部并留出
  头部等高滚动占位；图标条高度实测回填（语言切换 / 文案换行会改变高度）。
  吸顶排序条（综合推荐 / 销量 / 价格 / 新品）使用**同一套磨砂材质**（白 86% + blur 18px +
  同色细线）：它吸顶后紧贴搜索栏与图标条下沿，纯白底会在接缝处露出一条材质分界。

## 两个必须守住的细节

1. **小程序页面滚动必须在页面 SFC 写 `onPageScroll(...)` 字面量**：编译期 runtimeHooks 按页面
   源码字符串生成位标记，包进 composable 会静默收不到事件（H5 正常）。契约测试见
   `src/components/diy/frosted-navbar.contract.test.ts`。
2. **起始底色可能是 CSS 变量**（个人中心 / 会员页的 `var(--theme-color-primary)`）：解析器只认
   hex/rgb，读不出会按白底把白字推成深色。`resolveNavigationScrollAppearance` 在 `progress=0`
   时直接沿用调用方的起始文字色（已补单测）。

## 验证

| 项 | 方式 | 结果 |
|----|------|------|
| 类型 | `pnpm type-check` | 通过 |
| 单测 | `pnpm test:unit` | 126 文件 / 1353 项通过（新增 13 项：磨砂常量、CSS 变量反色、组件与页面接线契约） |
| 小程序编译 | `pnpm build:mp-weixin` | DONE |
| H5 实测（本地 boot） | 分类页滚动、个人中心滚动、商详滚动、商品评价列表滚动 | 头部均为 `rgba(255,255,255,.86)` + `backdrop-filter: blur(18px)`，标题/图标反色，内容从栏下穿过 |

## 附带

- `src/pages.json` 由 uni-pages 重新生成（补上 `sub-pages/user/login-devices/index`），
  `navigation-contract.test.ts` 的「跳转目标必须已注册」恢复通过。

## 追加：底部 tabBar 玻璃材质（2026-10-29）

原生 tabBar 只支持纯色底色、没有模糊能力，因此按平台分两种实现；**两端都是悬浮胶囊**：
居中、宽 `calc(100% - 40px)`（上限 480px）、高 52px、圆角 26px、贴底 16px、白色 86%（与顶栏同一数值）+
`blur(18px)`、浮起阴影 `0 8px 24px rgba(15,23,42,.16)`；**占位高度 68px = 52 + 16**，
tab 页的底部让位（分类页高度、购物车结算条与占位、首页/个人中心滚动流末尾）统一按
`--app-tabbar-height` 计算，两端同口径。底边距取固定 16px 而不是「安全区 + 间距」：
胶囊仍落在 iPhone home indicator 上方，但不会悬在半空。

| 平台 | 做法 |
|------|------|
| H5 | uni-app 内置 tabBar 是真实 DOM：全局 CSS 改成悬浮胶囊并叠磨砂材质（行内样式需 `!important`），隐藏原生 0.5px 分隔线；深色模式跟 `prefers-color-scheme` |
| 微信小程序 | `pages.config.ts` 的 `tabBar` **无条件**写 `tabBar.custom = true`：小程序**从根上不渲染原生 tabBar**，冷启动/重新编译不会先闪出原生栏；`src/custom-tab-bar/` 提供页面首帧的原生实现，真正的视觉由 Vue 的 `glass-tabbar` 在 tab 页布局里绘制（层级 998、选中态、角标、多语言）。**不能按 `UNI_PLATFORM` 条件写入**，原因见文末「原生底部 tabBar 又出现」一节 |

> 为什么不是只调 `uni.hideTabBar()`：隐藏是**事后**动作，冷启动与「重新编译」时原生栏已经画出来了，会先闪一下；`custom: true` 才是官方的不渲染方案。`hideTabBar` 仍保留作为老基础库（不支持 custom）的兜底，失败静默。

**必须守住的写法（2026-10-29 修复）**：`-webkit-backdrop-filter` 会被构建链的 autoprefixer
当「过时前缀」删掉，编译产物里只剩无前缀的 `backdrop-filter`；iOS 18 以下的 WKWebView 不认
无前缀写法 → 小程序里毛玻璃退化成「半透明白 + 内容直接透出来」。CSS 属性名大小写不敏感，
项目统一写成 **`-WEBKIT-backdrop-filter`**（`category-all-sheet` 的既有做法）即可绕过清理，
`hr-navbar` / `diy-search-bar` / `diy-goods-tabs` / `hr-search-navbar` / 分类页头部 /
商详顶栏 / `glass-tabbar` 与 H5 底栏均已改为此写法，契约测试断言也同步到该形式。

- 复用的 tab 清单在 `components/glass-tabbar/tabs.ts`，与 `pages.config.ts` 的 `tabBar.list` 一一对应；
  选中项按当前页面路由解析，`switchTab` 切换，`onShow` 重新隐藏原生 bar。
- 未验证：**真机/开发者工具下的小程序自绘底栏**（本机只有 H5 浏览器）。H5 端曾临时渲染同一组件
  验证材质、选中态与 `switchTab`，页面底部让位是 `#ifdef MP-WEIXIN` 分支，需在开发者工具复核。

### 追加：首次切换 tab 底栏闪烁——能做到与做不到（2026-10-29）

**问题**：首次切到某个 tab 时，底部玻璃胶囊会短暂消失再出现。原因不是视觉样式，而是
「小程序切 tab 本质是换页」：新页面的 Vue 根还没挂载前，页面里自绘的 `glass-tabbar`
还不存在，底部先空一段；再切回已访问过的 tab（页面已缓存）就不复现。

**逐帧复现（开发者工具 dev 构建 + 屏幕录帧，约 110ms/帧）**：
冷切分类页时先出现**一整帧纯白**（连底栏也没有），约 120–240ms 后才到「分类页骨架 + 底栏」
的首帧。结论：`custom-tab-bar` 与页面在**同一个 webview** 里，页面首帧渲染前它也不存在；
页面内的任何自绘/原生占位（包括本节的 custom-tab-bar 实现）**盖不住这段纯白**。
这是微信 WebView 模式下自定义 tabBar 的固有限制（官方社区也有同样反馈；原生 tabBar
不受影响，因为它在窗口层，不随页面 webview 重建）。

**当前采用的折中**：`src/custom-tab-bar/` 从「`display:none` 占位」改成一份**同款原生底栏**，
由框架随页面首帧渲染，Vue 版挂载后立刻接管。它覆盖的是「页面首帧已出（骨架屏）→ Vue 挂载」
这段空窗（实测约 300–500ms），**不覆盖首帧之前的纯白帧**。

| 时机 | 谁在画底栏 |
|------|-----------|
| 新页面 webview 首帧之前（纯白） | 无人（页面内方案无解） |
| 页面首帧（骨架屏）→ Vue 挂载 | 原生 `custom-tab-bar`（静态数据，不依赖 Vue） |
| 页面 Vue 挂载后 | Vue `components/glass-tabbar`（选中态/角标/多语言/浮层级全部回到这里） |

**要彻底消除纯白帧，只有两条路**（2026-10-29 与用户确认后暂不做）：
1. **改回原生 tabBar**（`custom:false`）：底栏由窗口层渲染，切页永不消失；代价是失去
   悬浮胶囊/毛玻璃/浮起阴影，变成全宽白色实心条；改动小、风险低。
2. **单页承载 4 个 tab**（同一页面内 v-show/v-if 切换）：底栏常驻、彻底无 page 切换白屏；
   代价是把 4 个页面重构为组件，牵涉路由/深链/页面生命周期/滚动与磨砂顶栏，工作量大。

- **隐藏方式**：`custom: true` 下 `wx.hideTabBar` 不生效，必须取当前页的组件实例
  `getTabBar().setData({ hidden: true })`；`uni.hideTabBar` 仍保留给不支持 custom 的老基础库。
  Vue 版第一次的 `onShow` 可能早于挂载，要等 `onMounted` 之后再接管，否则会提前藏掉占位栏。
- **切页保护**：按路由（`props.active` 对应的页面）在页面栈里找**本页**的底栏实例再隐藏，
  不取栈顶——切页瞬间本页可能已经不是栈顶，用栈顶会误伤新页面的占位栏。
- **选中项**：原生组件 `attached` 时页面栈仍是空的（实测 `getCurrentPages() === []`），
  所以首帧先保持「不亮任何 tab」的中性态，`setTimeout(0)` 等页面入栈后再按真实路由点亮；
  这样不会先亮错 tab 再跳一下。
- **交接不跳色**：原生组件读不到 Vue store，Vue 版每次状态变化把「文案/主题色/深色/角标」写进
  `getApp().__glassTabBarSeed`，下一个页面的首帧直接取用；语言缺省时读 `ARYN_LOCALE` 存储。
- **视觉同源**：原生 `index.wxss` 与 Vue 版逐值一致（52px 胶囊 / 26px 圆角 / 贴底 16px /
  86% 白 + blur 18px / 图标 26 / 文案 11 / 角标 15），`tabbar.contract.test.ts` 会比对两份关键数值与
  tab 清单，改一侧忘另一侧会红灯。
- **为什么不做成纯原生**：原生 tabBar 在页面 DOM 之外，页面里的浮层（全部分类 999、会话失效 5100、
  租户阻断 9999 等）压不住它；保留 Vue 版作为常态实现，可以把既有的层级关系原样保住。

验证：`pnpm type-check` / `pnpm test:unit`（127 文件 / 1379 项）/ `pnpm build:mp-weixin` 通过。

**开发者工具端到端验证（dev 构建 + automation + 屏幕录帧）**：
- 首页 Vue 底栏已接管：页面实例的 `getTabBar().data.hidden === true`、`selected === 0`。
- 冷切「我的」轮询：`t≈825ms` 新页面入栈、`hidden:false`；`t≈1260ms` `hidden:true`（Vue 接管）。
- 冷切「分类」录帧：`t≈1520ms` 仍是首页 → `t≈1637ms` 整屏纯白（无底栏）→ `t≈1756ms`
  分类骨架 + 原生底栏 → 之后 Vue 版接管。**纯白帧在页面内无解，需按上面的两条路线之一根治。**
- 原生胶囊几何、选中态、玻璃材质在真实工程里截图确认；`setData({hidden:true})` 能把原生栏隐藏。

- 仍未验证：**真机（iOS/Android）上的首切观感**与真实模糊/安全区表现。
- 原生底栏的隐藏依赖 `getTabBar()`（微信 2.5.0+ 自定义 tabBar 的官方接口）。若在极老基础库上
  `getTabBar` 不存在，占位栏会留在最上层（视觉与 Vue 版一致，点击仍是 switchTab），但页面浮层
  无法再压住它——只在 `custom` 本身可用而 `getTabBar` 缺失的异常组合下出现。
- 工具备忘：完整工程的 **prod 构建**（`dist/build/mp-weixin`）在本机开发者工具里会
  `simulator launch failed`；换成 dev 构建（`dist/dev/mp-weixin`）即可正常打开并用
  `miniprogram-automator`（`cli auto --auto-port`）验证。

### 追加：微信控制台 `parameter.backgroundColor should be String instead of Undefined`（2026-10-29 修复）

**现象**：进入个人中心、首页等自定义导航页，微信开发者工具每次加载都刷一条红色
`Error: MiniProgramError`：
`{"errno":1001,"errMsg":"setNavigationBarColor:fail parameter error:
parameter.backgroundColor should be String instead of Undefined"}`。

**根因**：`hr-navbar` 的「状态栏前景色同步」watch 只传了 `frontColor`。微信
`wx.setNavigationBarColor` 把 `backgroundColor` 定为**必填**（有效值仅十六进制色），缺参数
即拒绝调用；参数校验由小程序框架异步报错，外层 `try/catch` 接不住。装修页的
`useDecorationPage` 同理：直接透传页面配置原值，空串 / 非 hex 会换成另一种参数错误。

**修复**：

- 新增 `src/utils/navigation-bar-color.ts` 的 `toNativeNavigationBarHexColor()`：
  hex（`#RGB` / `#RGBA` / `#RRGGBB` / `#RRGGBBAA`）与 `rgb()/rgba()` 统一归一成 `#rrggbb`；
  CSS 变量（个人中心/会员页品牌色 `var(--theme-color-primary…)`）、空值、脏数据回退白色。
- `hr-navbar` 的状态栏 watch 与 `useDecorationPage` 都传入归一化后的 `backgroundColor`。
  两处页面均为 `navigationStyle: custom`，原生导航条不可见，该字段只为满足参数校验，
  不改变任何渲染结果。
- 契约测试 `navbar-scroll.contract.test.ts` 增加「必须带 `backgroundColor` 且必须经归一化」
  断言；单测 `src/utils/navigation-bar-color.test.ts` 覆盖 hex / rgb / CSS 变量 / 空值 / 脏数据。

**验证**：`pnpm type-check` 通过；`pnpm test:unit` 128 文件 / 1387 项通过；dev 构建产物
`dist/dev/mp-weixin/components/hr-navbar/index.js` 已重新生成，调用变为
`{ backgroundColor: nativeNavigationBarColor.value, frontColor }`。未在开发者工具重新目检
控制台（参数已合法，dev 构建需在工具里重新编译后生效）。

### 追加：原生底部 tabBar「又出现了」—— pages.json 跨平台共用被 H5 覆盖（2026-10-29 修复）

**现象**：微信开发者工具（工程为 dev 构建 `dist/dev/mp-weixin`）底部忽然变回**全宽白色原生 tabBar**，
悬浮玻璃胶囊消失；`dist/dev/mp-weixin/app.json` 的 `tabBar` 里没有 `custom: true`，
而 `dist/build/mp-weixin/app.json`（prod 构建）里是有的。

**根因**：`tabBar.custom` 当时按编译平台条件写入（只在 mp-weixin 打开），但 `src/pages.json` 是
H5 / 小程序 / App **共用的同一份生成文件**：`vite.config.ts` 的 pages 预生成与 uni-pages 插件
每次 dev/build 启动都会重写它。只要期间跑过任意一次 H5 的 dev/build（哪怕启动几十秒就退出），
它就会把 pages.json 重写成「tabBar 不带 custom」；正在运行的 mp-weixin dev 监听到 pages.json 变化后
重新生成 app.json，`custom` 随之丢失 → 微信回退原生 tabBar。时间线佐证：17:59 prod 构建产物带 custom，
17:59:32 dev 的 app.json 被 H5 进程覆盖后重写成不带 custom；此后每次 H5 dev 启动都会再覆盖一次
（19:16、19:36 各一次；即使该 H5 进程随后失败退出，pages 预生成已经先落盘）。

**修复**：`pages.config.ts` 的 `tabBar` 无条件写 `custom: true`（`const customTabBar = { custom: true }`，
不再读 `UNI_PLATFORM`）。H5 / App 运行时不认识该字段，按微信 schema 原样透传即可：H5 内置 tabBar
仍走全局 CSS 磨砂（`uni-h5` 产物里没有任何 `tabBar.custom` 判断）；小程序端无论哪一端最后写 pages.json，
app.json 都保留 `custom`。

**验证**：
- 用与 `vite.config.ts` 相同的参数分别以 `UNI_PLATFORM=mp-weixin` / `h5` 跑 pages 预生成：
  两次生成的 `src/pages.json` 都含 `"custom": true`（修复前 h5 那次会丢），diff 仅多这一行。
- 运行中的 mp-weixin dev 随即重写 `dist/dev/mp-weixin/app.json`，`tabBar.custom === true`；
  开发者工具重新编译后恢复悬浮玻璃底栏。
- `pnpm type-check` 通过；`src/components/glass-tabbar/tabbar.contract.test.ts` 25 项通过，
  其中守门断言改为「无条件写入 + 不得出现 `UNI_PLATFORM` / `useCustomTabBar` 条件分支」。
- `pnpm build:h5` 通过（H5 构建不受 custom 字段影响）。

**教训**：uni-pages 生成的 `pages.json` 是跨平台单一产物，**平台无关字段绝不能按 `UNI_PLATFORM`
条件写入**；只有插件原生支持的 `#ifdef` 平台块（pages / subPackages 列表）可以按平台合并差异。

### 追加：列表页顶栏占位在真机上短一截，首屏卡片压到导航栏上（2026-11-01 修复）

**现象**：收货地址列表的首张地址卡片顶端与导航栏标题「收货地址」齐平，卡片压在导航栏上
（同样影响收藏、足迹、积分明细、余额明细等共 11 个 z-paging 列表页）；H5 端一切正常。

**根因**：占位高度写成了 `calc(var(--status-bar-height, 0px) + 44px)`。小程序端 uni-app 在
`app.wxss` 里注入的是 `page{--status-bar-height:25px}`（见 `@dcloudio/uni-mp-vite` 的
`cssVars`），**写死 25px、与设备无关**；而 `hr-navbar` 内部的 `wd-navbar` 用
`uni.getWindowInfo().statusBarHeight`（刘海屏 / 灵动岛 44~59px）做 `padding-top`。两边差
20~35px，占位偏短 → 首屏内容整体上移、压在导航栏上。H5 端该变量为 0，恰好等于真实值
（H5 没有系统状态栏），所以浏览器里看不出来，只有小程序真机/开发者工具能复现。

**修复**：

- 新增 `src/utils/navbar-space.ts`：`resolveNavbarSpaceHeight(statusBarHeight)` =
  `max(实测状态栏高度, 0) + 44`，与 `wd-navbar` 的 placeholder 同口径；单测覆盖刘海屏、
  H5（0）、脏数据（负数 / `NaN` / `Infinity` / `undefined`）。
- 新增 `src/components/hr-navbar-space/index.vue`：首行渲染一条等高占位，高度在 `setup`
  里用 `uni.getWindowInfo().statusBarHeight` 实测后内联。11 个列表页的
  `<view class="hr-navbar-scroll-space" />` 统一换成 `<hr-navbar-space />`。
- 删除 `App.vue` 的旧全局占位类（CSS 变量方案）；`frosted-navbar.contract.test.ts` 改为守
  「占位高度必须实测 + 全站不得再出现 CSS 变量占位」，内部列表滚动页清单从 4 页补全到 11 页。

**验证**：`pnpm type-check` 通过；`pnpm test:unit` 137 文件 / 1502 项通过（新增
`src/utils/navbar-space.test.ts` 3 项）；`pnpm build:mp-weixin` DONE，产物
`dist/build/mp-weixin/components/hr-navbar-space/index-vendor.js` 为
`getWindowInfo()` + `resolveNavbarSpaceHeight(...)`，页面 `index.json` 已注册
`hr-navbar-space`；`pnpm build:h5` DONE；`dist/dev/mp-weixin`（开发者工具用的 dev 构建）
也已刷新，`app.wxss` 里旧占位类已消失。真机/开发者工具观感需在重新编译后目检（用户提供的
截图即修复前的现象）。

**覆盖审计**（确认只改这一类页面，不误改其他顶栏结构）：

| 顶栏结构 | 页数 | 顶栏占位方式 | 是否需要本修复 |
|------|:--:|------|:--:|
| `hr-navbar` + z-paging（导航栏上移为覆盖层） | 11 | `<hr-navbar-space />`（本次新增，实测状态栏） | 是（已改） |
| `hr-navbar` + z-paging，但导航栏在 `#top` 槽内 | 4 | wd-navbar 的 `placeholder`（内部按实测状态栏占位，在滚动区内正常参与布局） | 否 |
| 页面滚动页（`onPageScroll`） | 40 | 同上，wd-navbar placeholder 在正常文档流中 | 否 |
| `hr-search-navbar` | 4 | 组件自带的实测占位 `<view :style="{height: placeholderHeight}">` | 否 |

4 个 `#top` 页：我的任务、订单列表、船舶供料、我的优惠券；4 个搜索顶栏页里两个需要避让的
（分类浏览、共享购物车分类）已经读 `placeholderHeight`。审计脚本规则：含 `<hr-navbar` 与
`<z-paging` 的页面必须有 `<hr-navbar-space />` 或 `#top`，当前 15 页全覆盖、无缺口。

### 追加：dev 产物缺组件注册的「假性回归」（2026-11-01 排障）

**现象**：上面的修复落地后，用户在微信开发者工具里看拼团活动列表页，首屏第一张卡又压在
「拼团活动」标题上（修复前后截图见 `artifacts/mp-navbar-fix-2026-11-01/`，该目录为本地生成物、
不进 Git）。同一时刻 H5 同页正常，源码 `pnpm type-check` 0 error、`pnpm test:unit` 137 文件 /
1502 项全绿。

**根因是 dev 产物，不是源码**：`hr-navbar-space` 组件目录 17:30 才新建，而
`pnpm dev:mp-weixin` 的监听进程 16:21 就已启动。增量编译只重写变化的页面，**早先编译过的页面
`index.json` 的 `usingComponents` 不会补上新组件**；`<hr-navbar-space>` 于是成了未注册标签，
小程序把它渲染成 0 高 —— 等价于「占位整条消失」，首屏内容从 y=0 开始，导航栏又变成标题压卡片。
实测证据：改前 `dist/dev/mp-weixin/sub-pages/promotion/group-buy/group-buy-list/index.json` 的
`usingComponents` 只有 `group-buy-card / hr-navbar / z-paging`；而 17:31 全量构建的
`dist/build/mp-weixin` 同一文件已含 `hr-navbar-space`。wxml 里标签存在、文档流高度却是 0，
就是这一特征。

**判定方法**（下次再遇到「H5 对、小程序错」先用这两条排除）：

```bash
# 1) dev 产物的页面 json 是否注册了占位组件（正常应输出 "hr-navbar-space"）
grep -o '"hr-navbar-space"' dist/dev/mp-weixin/sub-pages/promotion/group-buy/group-buy-list/index.json
# 2) 组件是否已被自动注册进类型文件（正常应输出 1）
grep -c HrNavbarSpace src/components.d.ts
```

**处置**：

1. 停掉旧的 mp dev 监听，挪走/删掉 `dist/dev/mp-weixin` 后**重新跑 `pnpm dev:mp-weixin`**
   （全量重编译，不要等增量补），产物里 11 个列表页的 `index.json` 全部补上注册；
2. 目录里的 `project.private.config.json`（开发者工具本地设置 `urlCheck:false`）会随删除一起
   消失，重建后要**恢复**，否则小程序里接口全被域名校验拦截、列表静默空态（本次已恢复）；
3. 开发者工具里重新编译（或在模拟器里重进页面）。

**验证（开发者工具自动化实测，非目测）**：用 `miniprogram-automator` 连开发者工具，
`reLaunch` 到 `sub-pages/promotion/group-buy/group-buy-list/index`，读到
`statusBarHeight=54`、`.group-buy-list` 的 `offset.top=98`，即 **98 = 54 + 44**，与 `wd-navbar`
固定层底沿完全对齐；修复前同批数据下首卡 top≈12。截图
`artifacts/mp-navbar-fix-2026-11-01/after-mp-devtools.png`、左右对比
`before-after.png`。

**结论/经验**：源码（新增组件 + 契约测试）本身已正确，这类「占位突然消失」先查 dev 产物页面
json 的 `usingComponents`，不要先怀疑样式；**新增组件后要重启 mp dev 监听**（或组件落地后重新
全量构建一次），清理 `dist/dev` 时记得保留 `project.private.config.json`。
