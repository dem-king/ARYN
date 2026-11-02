# 领券中心 + 满减券阶梯视觉重构（C 端券体系 · 方向 A 现代简约）

> 日期：2026-11-01 ｜ 范围：C 端移动端（`aryn-mall-uniapp`）券体系四处券面 + 领券中心页 + 首页满减券阶梯 + 管理端装修预览对齐
> 用户请求：给出「领券中心页」与「首页满减券阶梯组件」两张小程序截图，要求「UI/UX 重新设计一下，要主流、现代」
> 用户拍板：视觉方向 **A. 现代简约 · 品牌红点缀**；改造范围 **A. 全局统一升级**（领券中心 / 我的券包 / 结算页选券 / 首页领券卡一次统一）
> 状态：设计规格已冻结，实施中（本文档为实施契约，任何数值以本文为准）

---

## 1. 背景与现状诊断

基线：当前工作树（`ship-chandler`），H5 真实渲染截图与计算样式见
`outputs/coupon-center-redesign/before/`（采集脚本 `capture.mjs`，390×844@2x，真实 demo 数据）。

| # | 现状问题（用户可感知） | 证据 |
|---|--------------------------|------|
| D1 | 券卡「三层重色」：渐变描边外框 6rpx + 浅色票面 + 整条红色状态带，红橙面积占卡片 ~40%，三张卡压满首屏 | before 截图；`.coupon-ticket` 高 116.3px，面值/状态带同时高饱和 |
| D2 | 状态带把「有效期」做成整条红底白字，信息层级错位：最重要的「面额 / 门槛 / 能否领」和次要素「截止时间」抢同一视觉权重 | `.coupon-ticket__band` 高 52rpx 全宽主色渐变 |
| D3 | 领券中心页只有一张列表：无页头、无筛选、无空态、无「我的券包」入口；整页除卡片外全是空白（首屏利用率 ~25%） | before 截图：3 张卡 + 「没有更多了」+ 大片留白 |
| D4 | 首页领券卡（`diy-coupon-receive` 列表样式）单卡占 50% 行宽，3 张券只露出 2.5 张，横滑暗示弱；票面同样重色 | before `home-coupon-zone.png`；`couponCardLayout(3,10)` → 50% |
| D5 | 满减券阶梯：hero 与白卡是两块平铺（无「上浮卡」收口）；三档小卡挤在一行、门槛/面额/按钮三层信息等高；「怎么参与」三步只是三个圆点；CTA 与 hero 同款渐变，主次不分 | before `home-ladder-element.png`；`ladder-hero` 与 `ladder-card` 之间 20rpx 空隙 |
| D6 | 券体系配色写死但分散：券卡 SCSS 变量 + 装修组件内联样式 + 管理端预览 px 三处各抄一遍，改一边就漂移 | `coupon-ticket.scss`、`diy-coupon-receive/index.vue`、管理端 `coupon-receive/index.vue` |

**结论**：不是「某个页面写丑了」，而是券体系停留在「渐变描边 + 红带」的旧票券语言，与 10-23 之后的
「白卡 + 轻投影 + 中性灰 + 品牌色点状强调」全站规范脱节。本次把券体系整体切到新语言。

## 2. 设计方向（A · 现代简约）

- **形**：白底卡 + 左侧浅色金额区 + 竖向虚线撕口（含票根缺口）+ 轻投影；不再用渐变描边外框、不再用整条红带。
- **色**：红橙只给「钱」——面额、主 CTA、选中态、阶梯最高档；其余全部中性灰（`#1f2329/#5b616e/#8a8f99/#f1f2f4`）。
- **层级**：券卡三层信息——① 面额 + 门槛（左值块）② 券名 + 适用范围 + 类型/截止（右信息区）③ 动作（按钮）。
- **页**：领券中心 = 品牌渐变页头（含「我的券包」入口）+ 分段筛选 + 券卡列表 + 空态/骨架；首屏利用率目标 ≥ 60%。
- **阶梯**：hero 与白卡做「上浮卡收口」（白卡上移压住 hero 底 32rpx）；档位做成**上楼梯**（越省越高），最高档实心品牌渐变 +「最划算」角标；「怎么参与」用虚线串联的三步。
- **多租户**：券体系配色是**固定促销色**（券 = 让利，红橙为行业通用语言；与旧券卡口径一致，不随主题换肤）；正文/背景/分隔线仍用全站中性色。页面 hero 同用该促销色，保证换肤后不出现「红配蓝」。

参考稿（静态 HTML 渲染，非最终实现）：`outputs/coupon-center-redesign/mockup.html` +
`mock-coupon-center.png` / `mock-home-zone.png` / `mock-coupon-user.png`。

## 3. 设计令牌（唯一来源：`aryn-mall-uniapp/src/styles/coupon-ticket.scss`）

> 换算：小程序屏宽恒 750rpx，管理端预览画布 375px ⇒ **1rpx = 0.5px**。

| 令牌 | 值 | 用途 |
|------|-----|------|
| `$coupon-brand` | `#ff5500` | 面额、主 CTA、选中态、最高档 |
| `$coupon-brand-light` | `#ff7a1f` | 渐变浅端 |
| `$coupon-brand-mid` | `#ff4a10` | hero 渐变中段 |
| `$coupon-brand-deep` | `#ff2d2d` | 渐变深端 |
| `$coupon-brand-ink` | `#c94300` | 浅橙底上的品牌小字（对 `$coupon-tint` ≈5:1） |
| `$coupon-tint` | `#fff6f1` | 票面/shì块浅底（金额区渐变起点） |
| `$coupon-tint-strong` | `#ffece2` | 券类型胶囊底 |
| `$coupon-line` | `#ffe3d6` | 虚线撕口、浅描边 |
| `$coupon-text` / `$coupon-text-2` | `#1f2329` / `#5b616e` | 券名 / 次文案 |
| `$coupon-desc` | `#8a8f99` | 门槛、截止、说明（辅助文本） |
| `$coupon-surface` | `#ffffff` | 卡面 |
| `$coupon-hairline` | `#f1f2f4` | 分隔线 |
| `$coupon-muted-brand/tint/line` | `#b6b9c0` / `#fafbfc` / `#eef0f3` | 已使用/已过期/不可用 |
| `$coupon-frozen-brand/tint/line/ink` | `#e8842a` / `#fffaf3` / `#ffe6c7` / `#b26a12` | 冻结中 |
| `$coupon-card-radius` | `24rpx` | 券卡圆角（12px） |
| `$coupon-card-shadow` | `0 2rpx 6rpx rgba(15,23,42,.03), 0 12rpx 32rpx rgba(15,23,42,.05)` | 券卡投影 |
| `$coupon-divider-width` | `2rpx` | 虚线宽 |
| `$coupon-notch-size` | `20rpx` | 票根缺口直径（10px） |
| `$coupon-value-width` | `176rpx` | 券卡左值块宽度（88px） |
| `$coupon-action-height` | `64rpx` | 券卡 CTA 高度（32px） |
| mixin `coupon-hero-bg` | `linear-gradient(118deg, light 0%, mid 56%, deep 100%)` | 领券中心页头 / 阶梯页头 |

> 旧券卡变量（`$coupon-rim-*` / `$coupon-band-*` / `coupon-rim()` 等）在迁移期保留在文件底部
> `compat` 段，`coupon-card` 与 `diy-coupon-receive` 迁移完成后由 ticket-dev 整段删除。

---

## 4. 分模块规格

### 4.1 券卡 `src/components/coupon-card/index.vue`（ticket-dev）

结构（保持 props/emits 契约不变；新增可选 prop）：

```
<view coupon-ticket [--tone] [--selector] [--selected]>
  <view coupon-ticket__value>        ← 左值块（176rpx，浅底渐变）
      <view coupon-ticket__amount>   ¥ 56rpx/800 + symbol 28rpx/700
      <view coupon-ticket__threshold> 20rpx $coupon-desc
  <view coupon-ticket__info>         ← 右信息区
      <view coupon-ticket__headline> 券名 28rpx/600 $coupon-text（1 行省略）
      <view coupon-ticket__desc>     适用范围 21rpx $coupon-desc（1 行省略）
      <view coupon-ticket__meta>
          <view coupon-ticket__pill> 类型 18rpx/600 $coupon-brand-ink on $coupon-tint-strong, r8rpx
          <view coupon-ticket__band> 状态/截止 20rpx $coupon-desc + ⏱ time-filled 20rpx
  <view coupon-ticket__action>       ← 主 CTA（primary/ghost）或单选图标
  <view coupon-ticket__notch--top / --bottom> ← 票根缺口，left: $coupon-value-width；top/bottom: -10rpx
```

| 项 | 规格 |
|----|------|
| 根卡 | `display:flex; background:$coupon-surface; border-radius:$coupon-card-radius; box-shadow:$coupon-card-shadow; overflow:hidden; position:relative` |
| 左值块 | `width:$coupon-value-width; padding:24rpx 8rpx; 纵向居中; background:linear-gradient(180deg,$coupon-tint 0%, #fff 82%)` |
| 撕口虚线 | 值块右缘 `.coupon-ticket__value::after`：`border-left:$coupon-divider-width dashed $coupon-line; top:22rpx; bottom:22rpx` |
| 票根缺口 | 直径 `$coupon-notch-size`；圆心落在值块右缘（撕口处）与卡片上下边缘；`background: var(--coupon-notch-bg, var(--wot-color-theme-background, #f5f5f5))` |
| CTA primary | `height:$coupon-action-height; padding:0 28rpx; border-radius:999rpx; 24rpx/600 #fff; background:$coupon-brand; box-shadow:0 8rpx 20rpx rgba(255,85,0,.20)` |
| CTA ghost | `background:#fff; border:2rpx solid #ffc9ad; color:$coupon-brand; 无投影`（已领取的「去使用」用 ghost） |
| 卡片高度 | 由内容撑开，目标 ≈176rpx（88px）；H5@390 实测 94.7px（≈182rpx，内容 4 行信息决定，≤100px 达标） |
| tone=muted | 面额 `$coupon-muted-brand`；值块底 `$coupon-muted-tint→#fff`；虚线 `$coupon-muted-line`；胶囊灰底 `#f1f2f4` + `#8a8f99` |
| tone=frozen | 面额 `$coupon-frozen-brand`；值块底 `$coupon-frozen-tint`；虚线 `$coupon-frozen-line`；胶囊 `$coupon-frozen-tint/$coupon-frozen-ink` |
| selector 模式 | 右侧 44rpx 单选图标（选中 `check-circle-filled` 品牌色 / 未选 `circle1` #c8c8c8）；选中态根卡 `inset 0 0 0 2rpx $coupon-brand` 描边 + 值块浅底 |
| 新增 prop | `actionVariant?: 'primary' \| 'ghost'`（默认 `primary`）；`notchColor?: string`（默认 `var(--wot-color-theme-background, #f5f5f5)`，结算页传 `#ffffff`） |
| 交互 | 点击热区 = 整卡（保持现有 `handleAction`/`handleSelect`）；CTA 只做视觉不单独绑事件（避免小程序事件透传重复触发） |

改动后 `coupon-user/index.vue` 的券卡传 `action-variant="ghost"`（券包内全是已领券，动作降级为次级）；
`CouponSelector.vue` 传 `notch-color="#ffffff"`（弹层白底，缺口不显脏）。

### 4.2 领券中心页 `src/sub-pages/promotion/coupon/coupon-list/index.vue`（center-dev）

页面骨架（根节点 class `cc-page`，供 H5 采集断言）：

1. `hr-navbar`（保持现有 title，不加右侧按钮）
2. `z-paging`（保持 `:auto="false"` + `onLoad` reload；保留 `hr-navbar-scroll-space`）
3. `.cc-body`（`padding: 24rpx 24rpx 0`）
   - `.cc-hero`：`coupon-hero-bg` 渐变卡，`border-radius:32rpx; padding:28rpx 32rpx; box-shadow:0 16rpx 40rpx rgba(255,85,0,.18); overflow:hidden`；伪元素两枚半透明白圆（`::before` 168rpx 右上 / `::after` 96rpx 右下，白色 13%/9%）
     - `.cc-hero__badge`：`promotion.coupon.list.badge`（"限时领券"），20rpx/600 白字，白 22% 底，h 40rpx 圆角 999
     - `.cc-hero__wallet`：`promotion.coupon.list.wallet`（"我的券包"）+ `chevron-right` 20rpx；白 18% 底 + 1rpx 白 26% 描边；点击 `router.push({ name: 'coupon-user' })`
     - `.cc-hero__title`：`heroTitle` 40rpx/800 白字，`letter-spacing:1rpx`，margin-top 18rpx
     - `.cc-hero__sub`：`heroSubtitle`（`{amount}` = 当前列表满减券最大面额，取不到时用 `heroSubtitleFallback`）22rpx rgba(255,255,255,.9)
   - `.cc-segments`：`margin-top:24rpx; padding:6rpx; background:#fff; border-radius:999rpx; box-shadow:$coupon-card-shadow; display:flex; gap:4rpx`
     - `.cc-segment`：flex 1；height 64rpx；24rpx；色 `$coupon-text-2`；active 白字 600 + `$coupon-brand` 实底 + `0 8rpx 20rpx rgba(255,85,0,.22)`
     - Tab：全部（`couponType: ''`）/ 满减券（`'1'`）/ 折扣券（`'2'`）；文案：`promotion.coupon.list.tabs.all` + `checkout.coupon.typeAmount` + `checkout.coupon.typeDiscount`
     - 切换：`state.activeType = key` 后 `pagingRef.value?.reload()`；`queryList` 把 `couponType` 传入接口（后端 `CouponInfo` 实体已有该字段，无需后端改动）
   - `.cc-list`：`margin-top:24rpx; display:flex; flex-direction:column; gap:20rpx`；每项 `<coupon-card>`（`band` = `formatCouponDeadline(receiveEndedAt)`；已领取 actionText=去使用 + `action-variant="ghost"`）
   - 骨架：`loading && !list.length` 时渲染 3 张 `.cc-skeleton` 卡（高 176rpx、白底、圆角/投影同券卡、`@keyframes cc-pulse` 透明度 0.55↔1）；`queryList` 首行置 `loading=true`，`finally` 置 false
   - 空态：z-paging `#empty` 插槽 → `hall-state`（`art="discount"`，`emptyTitle`/`emptyDesc`，`actionText = promotion.common.goHome`，`@action` → `router.pushTab({ name: 'home' })`；**注意 home 是 tabBar 页，`router.push` 会静默失败**）
4. 领取逻辑保持现有：`addObj` 成功后本地 `userReceiveCount += 1`（卡片即时切「去使用」），失败 toast 兜底；`bandText` 仍用 `formatCouponDeadline`。
5. 首屏加载去掉全屏 `globalLoading` 遮罩（会盖住骨架屏），错误提示仍由请求层 toast 负责；Tab 快速连点用请求序号兜底，只让最新一次 `complete()` 生效（避免旧筛选响应污染列表）。

### 4.3 首页领券组件 `src/components/diy/diy-coupon-receive/index.vue`（ticket-dev）

- `showStyle=1`（列表）：紧凑竖版券卡一排最多 **3 张**、可横滑（`overflow-x:auto` + 隐藏滚动条）。
  - `.coupon-item`：`flex:0 0 auto; width` 由 `couponCardLayout()` 内联给出；白底、`$coupon-card-radius`、`$coupon-card-shadow`、overflow hidden。
  - `.coupon-face`（上）：`padding:20rpx 12rpx 14rpx; text-align:center; background:linear-gradient(180deg,$coupon-tint 0%,#fff 100%)`
    - `.coupon-pill` 18rpx/600 `$coupon-brand-ink` on `$coupon-tint-strong`；`.amount-row`（symbol 22rpx/700 + value 48rpx/800 + unit 22rpx/700，`$coupon-brand`）；`.coupon-threshold` 18rpx `$coupon-desc`；`.coupon-name` 18rpx `$coupon-desc` 单行省略
  - `.coupon-band`（下）：`margin-top:14rpx; padding:14rpx 16rpx 16rpx; border-top:$coupon-divider-width dashed $coupon-line`
    - `.coupon-btn`：`width:100%; height:52rpx; 圆角 999; 22rpx/600 #fff; background:$coupon-brand`；已领取 `#f4f5f7` 底 + `#8a8f99` 字
- `showStyle=2`（卡片）：一卡一行横版票——`.coupon-face` 变左值块（宽 `$coupon-value-width`，浅底渐变，胶囊+面额+门槛），新增 `.coupon-info`（券名 26rpx/600 1 行 + 适用范围 20rpx `$coupon-desc` 1 行），`.coupon-band` 变右侧动作区（`.coupon-btn` 宽 128rpx、h 56rpx、实底品牌）。
- **布局函数不改**：复核基线渲染后确认 `couponCardLayout()` 现有逻辑在演示数据（3 张券 / showNum=10）下已经是每张 1/3 行宽、一排正好 3 张（实测 117.8px）；券数更多时靠 `minWidth:220rpx` + 横滑，同样约 3 张/屏。改 perRow 上限无视觉收益且要动已有测试，因此 `coupon-card-layout.ts` 与 `coupon-card-layout.test.ts` **保持原样、零改动**。

### 4.4 满减券阶梯 `src/components/diy/diy-coupon-ladder/index.vue`（ticket-dev 之外由 ladder-dev / 由同一 verifier 验收）

结构（数据源/领取逻辑/空数据不渲染规则保持不变）：

```
<view class="ladder-floor">
  <view class="ladder-hero">        ← coupon-hero-bg 渐变，圆角 32rpx 32rpx 0 0，padding 30rpx 32rpx 60rpx
      <view class="ladder-badge">   配置 badgeText（无则不渲染）
      <view class="ladder-title">   配置 title，48rpx/800 白
      <view class="ladder-subtitle"> 配置 subtitle，22rpx rgba(255,255,255,.9)
  <view class="ladder-card">        ← margin-top:-32rpx; background:#fff; radius 32rpx; padding 28rpx; $coupon-card-shadow; z-index 2
      <view class="ladder-card-head">  「阶梯满减」28rpx/700 + 「买得越多，省得越多」20rpx $coupon-desc
      <view class="ladder-tiers">      ← display:flex; align-items:flex-end; gap:12rpx
          .ladder-tier（每档，flex:1，min-width:0，纵向居中，圆角 20rpx，浅底渐变，2rpx $coupon-line 描边）
              nth-child(1..4) min-height: 212 / 236 / 260 / 284rpx（上楼梯；值必须大于档位内容高 ≈198rpx，否则会被内容撑平、2 档看不出台阶）
              .ladder-tier-threshold 20rpx $coupon-desc（i18n threshold）
              .ladder-tier-amount  ¥ 22rpx/700 + 数 50rpx/800 $coupon-brand
              .ladder-tier-claim  44rpx 高、20rpx/600、$coupon-brand 字、白底 + 2rpx #ffc9ad 描边圆角 999
          .ladder-tier--active（最后一档 = 最高档）
              coupon-hero-bg 渐变实底 + 去描边 + `0 12rpx 28rpx rgba(255,85,0,.22)`；门槛/金额白字；claim 白底品牌字
              .ladder-tier-flag 「最划算」（i18n bestValue），绝对定位右上，16rpx/700、白底 `#fff`、红字 `#e01b2e`（压渐变档位比半透黑底清晰）
          .ladder-tier--done（已领取）claim 灰底灰字；金额降为 $coupon-muted-brand
      <view class="ladder-divider">    2rpx $coupon-hairline，margin 28rpx 0 24rpx
      <view class="ladder-section-title"> 「怎么参与」（现有 i18n，加品牌色竖条）
      <view class="ladder-steps">       三步：虚线连接 + 52rpx 圆点序号 + 20rpx 文案
      <view class="ladder-note">        配置 noteText（有则渲染，浅灰底 16rpx 圆角）
      <view class="ladder-cta">         配置 ctaText/ctaLink（有则渲染）：84rpx 高、999 圆角、28rpx/600 白、$coupon-brand 实底 + 投影
```

- 档位卡整体可点（`@click.stop` 领取；`showClaimBtn=false` 时不可点），保证 tap 热区 ≥88rpx。
- 仅保留 hero/card 两层的「上浮卡」关系；**不新增**「活动规则」「全部券」等配置外入口。
- 数据源、`normalizeCouponLadderTiers`、`mergeReceivedCouponIds`、游客登录拦截逻辑一律不动。

### 4.5 管理端装修预览（designer-dev）

`preview-parity.test.ts` 明确要求「编辑器预览 = 小程序实机」逐值一致（1rpx = 0.5px），因此：

1. `aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/components/coupon-receive/index.vue`
   - 模板与样式按 4.3 新结构重写（列表样式紧凑 110px 卡 + 卡片样式横版票），px 值 = rpx/2；
   - 保留手选空态「未选择优惠券」、自动模式按 `showNum` 画上限卡数；
   - 与 C 端同名的结构 class：`coupon-item / coupon-face / coupon-pill / amount-row / coupon-value / coupon-threshold / coupon-name / coupon-info / coupon-desc / coupon-band / coupon-btn`。
2. `.../page-design/components/extension/coupon-ladder/index.vue`
   - 按 4.4 重写（hero + 上浮卡 + 上楼梯 + 步骤 + CTA + 画布提示），保留下方「画布为示例档位」提示；
   - 新增画布示例档 4 档（10/30/50/80 对 99/199/299/399 的既有占位保持不变）。
3. `.../page-designer/preview-parity.test.ts` 的「领券」两条用例改为新契约：
   - 结构标记：两端都含 `coupon-face / coupon-pill / coupon-threshold / coupon-band / coupon-btn`（去掉了旧 `coupon-notch` 必选）；
   - 数值锁：`.coupon-value` 字号 24px ↔ 48rpx；`.coupon-face` padding 两端同值；`.coupon-band` 的 `border-top` 虚线宽度 1px ↔ 2rpx；`.coupon-btn` 高度 26px ↔ 52rpx；
   - 配色锁：管理端 `.coupon-pill` 背景色 === `coupon-ticket.scss` 里 `$coupon-tint-strong` 的值；
   - 变量锁：`$coupon-card-radius: 24rpx`、`$coupon-notch-size: 20rpx`、`$coupon-value-width: 176rpx` 存在且取值一致。

---

## 5. 文件清单 / Owner / 写作用域

| Owner | 文件 | 内容 |
|-------|------|------|
| lead（已完成） | `src/styles/coupon-ticket.scss`（新令牌段） | 设计令牌与 mixin |
| lead（已完成） | `src/locales/modules/{promotion,diy}.{zh-CN,en-US}.ts` | 新增 i18n 词条 |
| ticket-dev | `src/styles/coupon-ticket.scss`（删除底部 compat 段） | 迁移收尾 |
| ticket-dev | `src/components/coupon-card/index.vue` | 4.1 券卡重写 |
| ticket-dev | `src/components/diy/diy-coupon-receive/index.vue` | 4.3 首页领券卡重写 |
| ticket-dev | `src/sub-pages/order/order-confirm/components/CouponSelector.vue` | 传 `notch-color` |
| center-dev | `src/sub-pages/promotion/coupon/coupon-list/index.vue` | 4.2 领券中心页重写 |
| center-dev | `src/sub-pages/promotion/coupon/coupon-user/index.vue` | 传 `action-variant="ghost"` |
| ladder-dev | `src/components/diy/diy-coupon-ladder/index.vue` | 4.4 阶梯重写 |
| designer-dev | 管理端 `coupon-receive/index.vue`、`extension/coupon-ladder/index.vue`、`preview-parity.test.ts` | 4.5 |
| verifier | 只读 + `outputs/coupon-center-redesign/`、验证报告 | 独立验收 |

**零后端 / 零接口 / 零 DB 改动**（筛选走已有 `couponType` 查询字段）。

## 6. i18n 词条（已落库）

- `promotion.coupon.list.*` 新增：`badge` / `heroTitle` / `heroSubtitle`（`{amount}`）/ `heroSubtitleFallback` / `wallet` / `tabs.all` / `emptyTitle` / `emptyDesc`；
- `diy.couponLadder.*` 新增：`tierTitle` / `tierHint` / `bestValue`；`stepClaim` 改为「领取优惠券」、`stepCart` 改为「加购物车」；
- 复用：`checkout.coupon.typeAmount/typeDiscount/noThreshold/threshold/scopeAll/scopePartial`、`promotion.common.goHome`、`promotion.coupon.user.use`、`diy.coupon.claim/claimed/receiveSuccess/receiveFailed`。
- 中英必须同步（`locale.test.ts` 校验 key 集合一致、英文包不得含中文）。

## 7. 验证方案（verifier 独立执行）

| 项 | 方法 | 通过标准 |
|----|------|----------|
| 类型 | `cd aryn-mall-uniapp && pnpm type-check` | 0 error |
| C 端单测 | `pnpm test:unit` | 全绿（`coupon-card-layout.test.ts` 不动、应保持通过） |
| 管理端单测 | `cd aryn-mall-ui && pnpm --filter @vben/web-ele test:unit`（或项目既有命令） | 全绿（含更新后的 `preview-parity.test.ts`） |
| 小程序构建 | `cd aryn-mall-uniapp && pnpm build:mp-weixin` | `DONE Build complete`；`pages/promotion/coupon/coupon-list` 分包 wxss 含 `cc-hero`/`cc-segment`，`coupon-ticket` wxss 含新样式 |
| H5 真实渲染（before/after 同口径） | `node outputs/coupon-center-redesign/capture.mjs after`（390×844@2x，真实 demo 数据） | 券卡高度 ≤ 100px；渐变描边/整条状态带消失；首页 3 张券卡同屏不截断；阶梯最高档为渐变实底 + 「最划算」；无横向溢出（`scrollWidth == clientWidth`） |
| 回归面 | 首页券区 / 领券中心 / 我的券包 / 结算页选券弹层（H5 截图） | 四处券卡视觉一致，无布局破版；结算弹层白底下缺口不显脏 |
| 空态 | 领券中心切「折扣券」Tab（demo 无折扣券） | HallState 空态正常，不出现空白页 |
| i18n | 切换到 en-US 后看两处界面 | 无中文残留、无未翻译 key 直出 |

## 8. 边界与风险

- **不动**：券接口/领取链路/登录拦截/分页参数语义/装修组件数据结构/管理端配置项；`z-paging` 与 `hr-navbar` 用法不变。
- **风险 1（中）**：`preview-parity.test.ts` 是字符串级契约，C 端与管理端必须同一批数值落地；实施时两端以本文 §4.1/§4.3/§4.5 的数字为准，不要互抄对方代码。
- **风险 2（低）**：券卡在结算页 `wd-action-sheet` 内、白底上，票根缺口用 prop 传 `#ffffff` 兜底；若漏传会出现灰点（验收项已列）。
- **风险 3（低）**：`couponType=2`（折扣券）在 demo 无数据，Tab 空态需人工确认；线上折扣券币种文案复用现有 `couponValueParts`，不新增换算。
- **风险 4（低）**：小程序不支持 `gap` 的旧基础库场景与现网一致（仓库已大量使用 gap），如构建/真机发现异常按 flex margin 兜底（不影响 H5 验收）。
- **回滚**：按 §5 文件清单单独还原；无数据/接口/DB 影响。

## 9. 验证记录（2026-11-01 18:45 回写）

> 独立验收人：verifier（只读源码，自行复跑，不采信实现者结论）。完整报告：
> `outputs/coupon-center-redesign/verification.md`；取证截图/脚本/命令日志在 `outputs/coupon-center-redesign/verify/`；
> before/after 截图与计算样式在 `outputs/coupon-center-redesign/{before,after}/` 与 `metrics-*.json`。

### 9.1 结论：PASS（P0=0 / P1=0；P2 9 条均不阻塞，已按下表处理）

| 项 | 实测结果 |
|----|----------|
| C 端类型检查 | `cd aryn-mall-uniapp && pnpm type-check` → EXIT 0，0 error |
| C 端单测 | `pnpm test:unit` → 137 files / **1502 tests 全绿**（含未改动的 coupon-card-layout 6 条） |
| 小程序构建 | `pnpm build:mp-weixin` → `DONE Build complete`；coupon-list/coupon-user 在 sub-pages 分包；产物 wxss 新样式齐全、**全 dist 无旧 coupon-ticket__face/rim 残留** |
| 管理端单测 | `cd aryn-mall-ui && pnpm test:unit` → 1270 passed / 2 failed；两条失败经独立取证为 **pre-existing**（用户未提交改动：category-browser 宽度 176→192rpx、两端 navigation-scroll.ts 在 HEAD 完全一致而工作区漂移），与本次无关 |
| 管理端类型检查 | `cd aryn-mall-ui/apps/web-ele && pnpm run typecheck` → EXIT 0（`pnpm -F` 形式因 PATH 找不到 vue-tsc，非代码问题） |
| 预览一致性契约 | `preview-parity.test.ts -t "领券"` → 2 passed；该文件用户原有的其它用例改动原样保留 |
| H5 真实渲染 | 390×844@2x、真实 demo 数据、0 console error；before/after 同口径 |

### 9.2 before → after 关键数值（H5 实测，1rpx≈0.52px@390）

| 指标 | before | after |
|------|--------|-------|
| 领券中心券卡高度 | 116.3px（面 75.8 + 红带 41.6） | **94.7px**（白卡，无红带） |
| 券卡外观 | 渐变描边外框 + 浅票面 + 52rpx 全宽主色状态带 | 白底 + 12.48px 圆角 + 双层轻投影 + 2rpx 竖向虚线撕口 + 20rpx 票根缺口 + 左值块 91.5px |
| 面额字号 | 29.12px/700 | 29.12px/800（¥ 14.56px/700） |
| 主 CTA | 68.6×24.3px 渐变胶囊 | 79×33.3px 品牌实底胶囊（ghost 白底描边） |
| 领券中心页首屏 | 只有列表 + 大量留白 | 页头 365×115.7px 渐变卡 + 33.3px 分段筛选 + 3 张卡 + 底部「没有更多了」，首屏利用率显著提升 |
| 首页领券卡 | 117.8×110.1px 重色票卡 | 117.8×**149.1px** 白卡（一排 3 张，x=10/136.1/262.2，间距 8.3/8.3，全部在 390 视口内） |
| 阶梯 hero → 白卡 | 两块平铺（hero 137.4px 卡片另行起） | 白卡**上浮压住 hero 16.6px**；hero 137.5px |
| 阶梯档位 | 2 档等高（163.3×91.9px） | **上楼梯** 2 档高 110.2 / 122.7px（台阶 12.5px），最高档渐变实底 + 白底红字「最划算」 |
| 横向溢出 | `scrollWidth==clientWidth` | `scrollWidth==clientWidth`（390/390，四处券面 + 阶梯全部通过） |

### 9.3 P2 处理

- P2-1/2（文档与实现数值不一致：档位 min-height、角标配色）→ **已回写本 README §4.4**，以实机可见效果为准；
- P2-3/4（死代码）：`coupon-cta-bg` mixin、`diy.couponLadder.amount` 词条已删除；
- P2-5/6（管理端画布占位 / 110px 近似）：属画布辅助占位与既有口径，保留并在 §4.5 说明；
- P2-7（券卡 94.7px 略超 176rpx 目标）：内容驱动，≤100px 达标，保留；
- P2-8（demo `coupon_user` 因采集脚本反复领取产生重复行）：**已清理**该测试账号的 15 行重复领取记录并回补库存/分配数（现 3 张券各 1 行）；`capture.mjs` 增加 `SKIP_CLAIM=1` 开关，复验不再重复领取；after 截图已用干净数据重跑；
- P2-9（工作区 concurrent 改动 `vite.config.ts`、`notice/*`、`activity-strip/marquee-text.vue` 于本时段被改）：**不属于本需求写作用域**，为工作区其它并行工作/用户改动，本次未触碰，相关单测全绿；`src/pages.json` 是 uni-app dev/build 依据用户 11:28 未提交 `pages.config.ts` 自动重新生成的产物，无 coupon 相关行，亦非本次手改。

### 9.4 已知未覆盖（低风险，发版前建议）

- 小程序真机/微信开发者工具目检（本机仅 H5 + MP 构建产物）：**唯一中风险项**，建议发版前在开发者工具复看领券中心与阶梯；
- `showStyle=2` 横版票、阶梯 `showClaimBtn=false`、结算「muted+selected」组合：demo 配置无法构造，已用代码审查 + 管理端对齐 + parity 契约替代覆盖；
- 骨架屏运行态、Tab 连点竞态未做慢网压测（纯逻辑已审查）。

## 10. 交付物

- 代码：见 §5 文件清单（11 个文件改动 + 2 个目录新增，零后端/零接口/零 DB）。
- 设计规格：本文档（含令牌表、逐组件数值、before/after、验证记录）。
- 视觉参考：`outputs/coupon-center-redesign/mockup.html` 与 `mock-*.png`。
- 验收报告与证据：`outputs/coupon-center-redesign/verification.md`、`verify/`、`before/`、`after/`（本地生成物，不进 Git）。
