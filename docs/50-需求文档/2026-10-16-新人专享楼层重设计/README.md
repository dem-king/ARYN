# 新人专享楼层：交互与视觉重设计方案

> 日期：2026-10-16 ｜ 状态：**已实施**（价格色统一为全站价格口径，经运营确认）
> 起因：运营反馈「这样滚动交互体验没问题吗？符合移动端交互吗？整层一个颜色设计没问题吗？」

## 〇、实施结果（2026-10-16）

按下方方案落地，运营确认「价格色统一成暖色」。实现要点与差异：

| 方案项 | 实施结果 |
|--------|---------|
| 连续跑马灯 → 分页式自动前进 | ✅ 两端改为 `scrollLeft/scroll-left` 每步一张卡，默认 3000ms，末尾先落到底再回卷首张 |
| 触摸即暂停、松手 5 秒恢复 | ✅ 管理端 `mousedown`/`touchstart` + `mouseup`/`touchend`；C 端 `@touchstart`/`@touchend` |
| 手动滑永久可用 | ✅ 移除 `scroll-x="!autoScroll"` 与 `overflow:hidden` 互斥 |
| 价格色统一暖色 | ✅ **按全站价格口径：`--wot-color-theme-primary`**（与 `.gs-price` 同 token，本租户解析为暖橙 `#ff5500`），而非方案初稿里另立的 `--nf-accent` |
| 抢购按钮 | ✅ 与商品横滑条 `.gs-btn` 同款渐变 `linear-gradient(90deg, secondary, primary)` + 同色投影 |
| 角标 / 省¥ / 倒计时 | ✅ 全部落到 `--wot-color-theme-primary`；省¥ 胶囊底色 `#fff1f0`（同 `.gs-subtitle`） |
| 横幅主色 | ✅ 保留运营配置的 `--nf-main`，只作用于横幅这一处 Surface |
| `prefers-reduced-motion` | ⚠️ 管理端预览已实现；C 端小程序无媒体查询能力，改为不自动前进需经配置（未强制） |
| 字段迁移 | ✅ 复用 `autoScroll` 字段名（仅改语义），已发布页面数据零迁移 |

**实施中额外修掉的边界缺陷**：平滑滚动在无动画帧环境（旧 WebView / 系统减弱动效）
不会落位，加 480ms 兜底直接落位；兜底同时判断「用户是否已按住」，
避免从拖拽中的用户手里把位置抢回去。

### 验证

- web-ele `vitest` 621 条全绿、`vue-tsc` 通过；uniapp `vitest` 659 条全绿、`vite-tsc` 通过。
- 契约测试改写：跑马灯相关的 3 条断言替换为「分页步进 + 触摸暂停 + 手动并存」
  与「三层色彩角色」，并断言跑马灯遗留物（`@keyframes nf-marquee` / `nf-track-wrap` /
  `is-auto`）必须为 0。
- 小程序编译产物核对：wxss 中跑马灯残留 0、价格/角标/省¥/倒计时均取
  `--wot-color-theme-primary`；wxml 中 `scroll-x` / `scroll-left` / `bindscroll` /
  `bindtouchstart` / `bindtouchend` 全部就位。
- **浏览器实测（装修器画布，非 mock）**：自动前进循环
  `0 → 138 → 213 → 回卷 0`；按住 5 秒位置纹丝不动；按住期间拖到 50px 不被抢占；
  松手约 5 秒后恢复自动前进；三层配色计算值
  价格/角标/省¥/倒计时 `rgb(255,85,0)`、横幅 `rgb(43,109,232)`、
  图片垫底 `rgb(243,244,246)`。

## 一、现状问题的量化诊断

### 1.1 交互：连续跑马灯（marquee）是移动端反模式

当前实现是**无限连续位移的跑马灯**（`animation: nf-marquee linear infinite`），
商品卡片永不停歇地从右向左漂移。这个交互有三重问题：

| 问题 | 说明 | 依据 |
|------|------|------|
| **无法阅读** | 卡片持续移动，用户想看清某个商品时它已滑走；低识字率/非母语用户更读不完 | NN/g《Auto-Forwarding Carousels and Accordions Annoy Users and Reduce Visibility》：*"I didn't have time to read it. It keeps flashing too quickly."* |
| **失去控制感** | 用户无法暂停、无法让内容停下；想点「抢」时目标还在动 | 同上：*"It's just plain annoying for users to lose control of the UI when things move around of their own accord."* |
| **无障碍不达标** | 自动移动且持续 >5 秒、又与其他内容并存，**必须**提供暂停/停止/隐藏机制 | WCAG 2.2 SC 2.2.2 Pause, Stop, Hide（Level A）：*"For any moving, blinking or scrolling information that starts automatically, lasts more than five seconds, and is presented in parallel with other content, there is a mechanism for the user to pause, stop, or hide it."* |
| **移动端尤甚** | NN/g 明确建议：**移动端不要自动播放**——页面短、用户快速滚动，轮播变化时用户往往已在下方看不到 | NN/g《Designing Effective Carousels》Auto-Forwarding Tips：*"Do not auto-forward on mobile devices."* |

另外还有一个**我们自己的实现缺陷**：自动模式下手动滑被完全禁掉
（C 端 `:scroll-x="!autoScroll"`、管理端 `overflow: hidden`）。
「开了自动就不能手动」→ 用户唯一能改变内容的方式是等它自己走完一圈。

### 1.2 配色：单一主色铺满整层，没有色彩层级

审计当前渲染的计算样式（主色 #2b6de8）：

| 元素 | 当前取值 | 问题 |
|------|---------|------|
| 横幅底 | `#2b6de8` | 主色 |
| 「新人价」角标 | `#2b6de8` | **与横幅同色** |
| 价格数字 | `#2b6de8` | **与横幅同色** |
| 「抢」按钮 | `#2b6de8` | **与横幅同色** |
| 「省¥」胶囊文字 | `#2b6de8` | **与横幅同色** |
| 倒计时文字 | `#2b6de8` | **与横幅同色** |

六个元素、一个颜色值。没有「背景 / 容器 / 强调」的层级区分，全靠明度差（白卡 vs 彩底）
勉强拉开。这带来两个具体后果：

1. **价格失去了促销语义**。电商场景里价格是需要「跳出来」的信息，
   同页的「商品横滑条」价格是暖色 `#ff5500`（`--wot-color-theme-secondary`
   派生的橙红渐变），新人专享的价格却是蓝色——**同一屏两组价格两种颜色**，
   用户对「价格长什么样」的认知被打断。
2. **缺乏设计体系支撑**。Material 3 的色彩角色体系把颜色分为
   Primary（主操作）/ Secondary（次要）/ Tertiary（**badge、通知等需特别强调的小元素**）
   与 Surface（背景）等角色，并强调「按预设配对使用」；Ant Design 亦是以
   「基础色板 → 经过明度阶梯衍生出 10 级色阶」的方式避免一色到底。
   当前实现是「一个色值打天下」，既没有 Surface/Accent 分工，也没有色阶。

### 1.3 其它已发现但本轮不动的观感问题

- 卡片与彩色横幅之间已有淡投影（上一轮已修）。
- 倒计时、角标、按钮三处都用了饱和度很高的纯色，视觉噪音偏多（本轮一并收敛）。

## 二、重设计方案

### 2.1 交互方案：自动前进（auto-advance）+ 触摸即暂停 + 永久可手动滑

放弃「连续位移的跑马灯」，改为**一次一整卡的分页式自动前进**：

| 规则 | 取值 | 依据 |
|------|------|------|
| 前进粒度 | 一次一步（一张卡片宽度），不是连续漂移 | 分页式前进保留「有更多内容」的暗示，又不至于让内容无法阅读（NN/g 推荐用露出下一张的方式暗示更多内容） |
| 前进间隔 | 默认 3000ms，可调 1500–10000ms | 沿用组件既有 `interval` 配置；NN/g 以「每秒 3 个词」估算阅读时间——卡片信息量小，3 秒足够 |
| **触摸即暂停** | `pointerdown`/`touchstart` 立即暂停；松手 5 秒后恢复 | 直接回应「用户想读时内容在动」；与轮播组件「按住暂停」的移动端惯例一致 |
| **手动滑永远可用** | 自动前进不再禁用 `scroll-x` / `overflow-x` | 修复「开了自动就滑不动」的实现缺陷 |
| 到末尾回卷 | 最后一步回到起点 | 对应 WCAG 中 auto-updating 内容的暂停语义（继续循环而非停住） |
| 尊重系统偏好 | `prefers-reduced-motion: reduce` 时不自动前进 | WCAG SC 2.3.3 / 2.2.2 精神；H5 与小程序可分别用媒体查询/开关处理 |
| 触控目标 | 抢购按钮 22×22px 偏小，实施时把可点击区域撑到 ≥24×24（整卡可点已满足） | WCAG SC 2.5.8 Target Size (Minimum)：24×24 CSS px |

**为什么不是「干脆去掉自动」**：自动前进仍有营销价值（首屏没滑动的用户也能看到更多商品）。
本方案在保留自动的同时，把「用户能随时接管」这条底线补齐——这是 WCAG 明确的
可访问性要求，不只是体验偏好。

### 2.2 配色方案：三层色彩角色

不再让主色铺满，改为**角色分工**：

| 角色 | 用途 | 取色 |
|------|------|------|
| **Surface（主色）** | 横幅底——大面积、低信息密度的品牌面 | `--nf-main`（运营可配，默认跟随页面主题主色） |
| **Accent（促销/价格色）** | 价格数字、抢购按钮、新人价角标、省¥胶囊、倒计时——所有传达「便宜、要买」的元素 | `--wot-color-theme-primary`（**全站价格同一 token**，与 `.gs-price` 一致；本租户解析为暖橙 `#ff5500`） |
| **Neutral（中性）** | 商品图垫底 `#f3f4f6`、划线原价 `#a8abb2`、商品名 `#303133` | 固定中性色，不随主题变化 |

关键点：

- **价格/按钮/角标与同页商品横滑条同 token**，全站「价格长什么样」恢复统一认知；
- 横幅仍保留运营配置的主色，品牌感不丢；
- 「省¥」胶囊底色 `#fff1f0`（与 `.gs-subtitle` 同口径），与价格同族；
- 倒计时胶囊白底 + 价格色文字，成为横幅上最亮的点（上一轮样式优化已改底色，本轮统一文字色）；
- 抢购按钮与 `.gs-btn` 同款 `linear-gradient(90deg, secondary, primary)` + 同色投影。

### 2.3 状态与默认值

```jsonc
// newbie-floor props 增量
{
  "autoAdvance": true,      // 替代 autoScroll（旧字段保留迁移）
  "interval": 3000,         // 沿用，现为「每步间隔」
  "pauseResumeDelay": 5000  // 新增，触摸后恢复延迟
}
```

## 三、方案对比

对比图（真实商品数据：即食豆腐脑 ¥0.01/¥5.90、葱油拌面、速冻小笼包、韭菜盒子；
主色实测 #2b6de8 为当前配置）：

1. **现状（对照）**——连续跑马灯 + 单色铺满
2. **新规格 · 蓝主色**——自动前进 + 三层色彩（保留运营当前配置的蓝）
3. **新规格 · 暖主色**——同一套规则换暖色主色的效果

## 四、实施范围（评审通过后）

| 端 | 文件 | 改动 |
|----|------|------|
| C 端 | `diy-newbie-floor/index.vue` | 跑马灯 → 分步前进 + 触摸暂停；`--nf-accent` 变量与角色取色 |
| C 端 | `retail-types.ts` | `NewbieFloorProps` 增加 `autoAdvance` / `pauseResumeDelay` |
| 管理端 | `newbie-floor/index.vue` | 同上（画布预览与实机逐值对齐） |
| 管理端 | `newbie-floor/types.ts` `setting.vue` | 字段与设置项（「自动轮播」→「自动前进」文案） |
| 契约测试 | `preview-parity.test.ts` | 锁住三层色角色与触摸暂停实现 |
| 文档 | 本文件 | 评审记录 |

> 旧草稿兼容：读取时 `autoScroll === true` 映射为 `autoAdvance: true`，
> 保证已发布页面不因字段改名失效。

## 五、参考依据

- Nielsen Norman Group, *Auto-Forwarding Carousels and Accordions Annoy Users and Reduce Visibility*（2013）
- Nielsen Norman Group, *Carousel Usability: Designing an Effective UI for Websites with Content Overload*（含 Auto-Forwarding Tips）
- W3C, *Understanding Success Criterion 2.2.2: Pause, Stop, Hide*（WCAG 2.2 Level A）
- W3C, *Understanding Success Criterion 2.5.8: Target Size (Minimum)*（24×24 CSS px 触控目标下限）
- Material Design 3, *Color roles*（Primary / Secondary / Tertiary / Surface 的角色化用色）
- Ant Design, *色彩*（基础色板 + 明度阶梯派生色阶）
- 站内一致性证据：同页 `goods-scroll` 价格 `#ff5500`、渐变抢购按钮
  `linear-gradient(90deg, #ff9159, #ff5500)`、副标题胶囊 `#fff1f0`

---

## 六、二轮优化（2026-10-18）：小程序横滑不完整 + 视觉精修

> 状态：**已实施** ｜ 起因：实机反馈「新人专享组件在小程序不能完整的滚动，而且样式还是不好看」。

### 6.1 「不能完整滚动」的三条根因（都是实机才暴露的实现缺陷）

| # | 根因 | 说明 | 修复 |
|---|------|------|------|
| 1 | 轨道宽度没撑开 | `.nf-track` 是块级 flex，宽度默认只有 `scroll-view` 那么宽；小程序原生滚动区按子节点尺寸计算，量不到溢出内容，最后一张卡永远滑不全。管理端画布早已写了 `width: max-content`，两端因此不一致（画布正常、实机不正常） | 轨道显式给宽度（`len × 260rpx + (len−1) × 16rpx`，行内样式用 px 下发），并保留 `width: max-content` 兜底 |
| 2 | 自动前进的滚动上限是硬编码 | 旧实现 `BANNER_INNER_RPX = 750 − 40`，漏掉了装修区块的 `paddingX = 12px/边`（24rpx × 2 = 48rpx，当前页面用的是「卡片区块」预设）以及横幅内边距，把可视宽多算了 56rpx（实际 654rpx）。自动前进「到末尾」时最后一张卡仍被裁掉约 28px | 用 `uni.createSelectorQuery()` 实测 `.nf-scroll` 与 `.nf-track` 宽度，滚动上限 = 轨道宽 − 可视宽；首帧量不到时补测 3 次 |
| 3 | 手势位置回写绑定值 | 旧实现把 `@scroll` 的位置回写进 `:scroll-left`，小程序上等于和手指抢滚动位置（滑到一半被拽回去） | 手势位置只记在非响应式变量 `liveScrollLeft`，`scroll-left` 只承载程序化步进；同值时先错开 1px 再落回 |

### 6.2 视觉精修（三层色彩角色不变）

| 元素 | 一轮 | 二轮 |
|------|------|------|
| 横幅面 | 实心主色 + 三个硬边白圆斑（像渲染瑕疵） | 主色渐变面（白色高光 + 暗部 + 主色三层叠加），角落两道极淡同心光环；圆角 24→32rpx |
| 头部 | 标题 + 半透明白胶囊副标 + 整块亮白倒计时胶囊（抢标题） | 副标改细描边胶囊；倒计时拆「灰色前缀 + 价格色时间」；标题 34rpx/800，间距 12→14rpx |
| 卡片 | 8px 圆角、贴边切角角标、1 行省略名称、浅粉「省¥」胶囊、22px 圆形按钮 | 12px 圆角；角标改浮起渐变胶囊；名称两行 clamp（固定两行高，同排价格行齐平）；省¥ 改价格色纯文字；按钮 27px；图片 `contain → cover`（260×252rpx，不再露灰边） |
| 阴影/层级 | 0 2px 8px | 0 8px 20px（白卡浮在彩底上更立体） |

商品图从 `aspectFit` 改 `aspectFill`：旧图在卡片里会露出灰底竖带，视觉上像「图片没加载完」。

### 6.3 两端一致 & 契约

- 管理端 `newbie-floor/index.vue` 同步为 `px = rpx / 2`，与管理端 `preview-parity.test.ts` 逐值断言；
- parity 新增「小程序横滑完整性」契约：管理端/C 端轨道都必须 `width: max-content`、C 端模板必须有 `:style="trackStyle"`、C 端必须有 `createSelectorQuery` + `maxScrollLeft` 且不得再出现 `BANNER_INNER_RPX`（防止修复回退）。

### 6.4 验证

- uniapp：`pnpm type-check` 通过；`pnpm test:unit` 全绿（含 `auto-motion-switch.contract.test.ts` 的开关接线）；
- `pnpm build:mp-weixin` 通过；产物核对：wxml 含 `scroll-x` / `scroll-left` / `bindscroll` / `bindtouchstart` / `bindtouchend`，轨道 `style="{{n}}"`；wxss 含 `width:max-content`、`white-space:normal`、`border-radius:24rpx`；js 内含 `createSelectorQuery` 实测逻辑、无 `BANNER_INNER_RPX`；
- 375px 渲染对比（1rpx = 0.5px，真实商品数据）：`outputs/newbie-floor-optimization/final-states.png` —— 初始态 2.4 张 + 下一张露出，滑到末尾最后一张完整贴齐可视区右边缘，无裁切。

> 注意：C 端需重新出小程序包生效；管理端刷新即见。
>
> 残余风险：本环境无法自动化微信真机/开发者工具（原生 `scroll-view` 测量与
> `createSelectorQuery` 只有编译产物 + 静态评审 + 浏览器同构渲染证据），
> **发版前建议真机人工验收一次**：最后一张能否完整滑出、自动前进到末尾是否回卷、
> 手指横滑是否发涩。

