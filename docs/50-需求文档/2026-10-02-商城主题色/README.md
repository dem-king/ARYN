# 商城主题色一键换肤（商城默认主题）

> 需求日期：2026-10-02
> 范围：三端（promotion 后端 / 管理端装修器 / C 端小程序）。
> 目标：商家在管理端把一套配色设为「商城默认主题」后，整个小程序商城
> （wot 组件、价格、按钮、标签、tabBar 选中色与图标）一键换肤。
> 本文档如实记录调研、设计与落地结果；未做与边界一律标注。

---

## 一、竞品调研摘要

| 产品 | 入口 | 覆盖面 | 机制 |
|------|------|--------|------|
| 有赞 | 店铺装修 → 全局风格 | 购物车按钮、价格、营销标签、导航；tabBar 图标另做「自定义品牌色」 | 店铺级配置，全店即时生效 |
| 微盟 | 店铺装修 → 风格管理 | 顶部色系（导航栏）+ 页面色系（品牌色） | 8 套预设 + 自定义；组件可选「跟随全局 / 自定义」；组件库基于 Design Tokens |
| Shopify | 主题编辑器 → Color Schemes | 最多 21 套配色，全站生效 | 主题把颜色暴露为 CSS 变量，选方案即换变量值 |

四条共识：

1. 主题色是**店铺级（租户级）**配置而非页面级，页面/组件保留「跟随全局 vs 自定义覆盖」两级优先级；
2. **预设色板 + 自定义色**并举；
3. 只染**品牌强调元素**（按钮/价格/标签/导航/tabBar），不是全页染色；
4. 技术上都是 **CSS 变量 / Design Token 下发**，原生外壳走各端 API
   （小程序 `wx.setTabBarStyle` / `wx.setNavigationBarColor`）。
   原生 tabBar 图标是 PNG、不吃 CSS 变量，有赞为此单独做了图标品牌色功能——本需求用
   canvas 染色解决（见 4.4）。

来源：[有赞新版店铺装修说明](https://help.youzan.com/displaylist/detail_4_4-2-78334)、
[有赞底部导航品牌色](https://help.youzan.com/displaylist/detail_4_4-2-48624)、
[微盟店铺装修拆解（优设）](https://www.uisdc.com/weimob)、
[微盟风格管理 8 套预设](https://www.hfbangfu.com/pages/help_wm/3417.php)、
[微盟 Titian 组件库 Design Tokens 实践](https://zhuanlan.zhihu.com/p/674998388)、
[Shopify 配色设置](https://help.shopify.com/en/manual/online-store/themes/customizing-themes/theme-editor/color-settings)、
[Shopify 动态配色最佳实践](https://www.shopify.com/partners/blog/creating-dynamic-color-schemes-with-theme-options-and-presets)、
[微信小程序动态换肤方案（腾讯云）](https://cloud.tencent.com/developer/article/1841937)。

## 二、现状（改造前）

- `page_design_theme` 表已有配色模型（主色/页面底色/导航色/导航文字色/圆角 +
  system_flag），但只能被单个装修页经 `themeRef` 引用，发布固化为 `themeSnapshot`；
- 管理端「主题」按钮已是配色 CRUD；C 端对 `themeRef/themeSnapshot` **零渲染消费**；
- C 端唯一换肤点 `App.ku.vue` 静态写死 `#FF2237`；
- 31 个文件散落多种硬编码红（#e5484d×19、#ff4500×19、#ff2237×11、#ff6b35×10 等）；
  `uno.config.ts` fallback 是陈旧蓝 `#4D7FFF`、`theme.json` 是陈旧蓝 `#0165FF`；
- 结论：**管道已建一半，水没通**。

## 三、设计

### 3.1 数据与接口（两级主题结构，对标微盟「跟随全局/自定义」）

- `page_design_theme` 新增 `mall_default_flag char(2)`（'0'/'1'），租户内至多一行 '1'，
  由服务层事务内互斥维护（先清旧标记再打新标记）；不加唯一索引（绝大多数行是 '0'）。
- 管理端 `POST /promotion/pagedesign/themes/{id}/default`（权限沿用
  `promotion:pagedesign:theme`）。
- C 端免登 `GET /app/pagedesign/mall-theme`：**自动落进现有**
  `/app/pagedesign/**`（boot yml）与 `/promotion/app/pagedesign/**`（cloud 网关）白名单，
  零白名单改动；未设置默认主题时 `data=null`。
- 辅色不下库：后端 `ThemeColorUtils.deriveSecondary`（主色混白 35%）衍生，
  发布快照 `themeSnapshot` 与 mall-theme VO 同口径下发。
- 优先级：**页面级 themeSnapshot > 商城默认主题 > 内置默认红**。

### 3.2 C 端换肤通道

- `mallThemeStore`：初始化**同步**读 storage 缓存（防闪色），`ensureLoaded` 异步刷新；
  逐字段 hex 校验防脏值；persist 插件排除（自带缓存 + resolved 标记不可持久化）；
  登出/切账号随 `authStore.clearAuthData` 重置（主题按租户配置）。
- `App.ku.vue`：`wd-config-provider :theme-vars` 从 store computed 读取——
  `@uni-ku/root` 每页注入，wot 组件全量生效；已消费 `var(--wot-color-theme-primary)`
  的既有文件自动跟随。
- `useMallThemeSync`（模块级单例守卫）：拉取 + `uni.setTabBarStyle(selectedColor)` +
  图标染色；`$subscribe` 挂 store 实例，不随页面卸载失效；非 tab 页调用失败静默
  （tab 页会再触发）。
- 页面级覆盖：`diy/index.vue` 在 `.diy-page` 根内联下发
  `--wot-color-theme-primary/secondary`（来自 themeSnapshot，hex 校验后透传），
  区块内组件读到的即页面主题。

### 3.3 硬编码红收敛（口径）

- 品牌强调色（价格/按钮/标签/排名/秒杀进度条等）→
  `var(--wot-color-theme-primary, #ff2237)`；
  双色渐变 → 辅色→主色对（`var(--wot-color-theme-secondary, <原浅色>), var(--wot-color-theme-primary, <原深色>)`）；
  浅红底/边 → `var(--wot-color-theme-background, <原色>)`。
- **语义色保留**：共享购物车/补给单的橙色采购进度渐变（`#FFB25C→#F2741D`）、
  待处理警示橙标签、order-pay 危险红 `#ff4d4f`、优惠券冻结态橙、
  商家可控 props 色（diy-goods 的 salesPriceColor 等、bottom-nav activeColor）。
- 管理端画布组件（page-design/components 8 个）同步替换，**与 C 端字符串逐字一致**
  （preview-parity.test.ts 契约）；画布根（phone-canvas）按
  页面主题 > 商城默认主题 > 内置默认红内联下发同名变量，所见即所得。
- `uno.config.ts` fallback 修正 `#4D7FFF→#FF2237`（fallback = 内置默认配色，
  改默认值需三处同步：mallThemeStore.DEFAULT_MALL_THEME / uno.config / theme.json）；
  `theme.json` 两段 `tabSelectedColor` 陈旧蓝 `#0165FF` 修正。
- 商家可控默认值不主题化：diy-bottom-nav 的 `#ff5000` 兜底保留字面量
  （管理端 extension/types.ts 默认值同口径，避免数据语义混乱）。

### 3.4 三期：tabBar 图标染色 + 深色模式

- **图标染色** `useTabBarIconTint`：仅 MP-WEIXIN。`wx.createOffscreenCanvas({type:'2d'})`
  读选中图标 PNG → `source-in` 把非透明像素刷成主题色 → `canvasToTempFilePath` →
  `setTabBarItem` 换图；同色一次会话只染一次；默认红 `#FF2237` 跳过（内置图标即此色）；
  任一步失败静默保持原图标（文字选中色仍由 setTabBarStyle 同步）。
- **深色模式**：manifest `darkmode: true` + `themeLocation: theme.json`（仅 mp-weixin）。
  原生导航/tabBar 由 theme.json dark 段自动跟随系统；内容层由系统主题 →
  `mallThemeStore.mode` → `App.ku.vue :theme="mode"` 加 `wot-theme-dark` class
  （App.vue 已有该 class 的页面底色适配）。`uni.getAppBaseInfo().theme` +
  `uni.onThemeChange` 维护 mode。
- **已知边界（诚实列）**：页面级深色视觉适配未全量做——自定义页面背景/卡片仍是浅色，
  仅 wot 组件层、page-wrapper 底色与原生外壳跟随系统；已带 `dark:` 变体的 13 个文件
  自动受益。页面逐个适配列为后续按需推进项。

### 3.5 数据库增量（双模式）

- boot：`db/boot/107page_design_theme_mall_default.sql`（ Ary_boot 库，
  information_schema 判列幂等加列 + 自检 SELECT + `SET FOREIGN_KEY_CHECKS=1` 收尾）；
- cloud：`db/cloud/108page_design_theme_mall_default.sql`（仅目标库不同，内容一致）；
- `build-full-sql.mjs` sections 末位登记第 82 项，`aryn_boot_full.sql` 已重新生成并通过
  `verify-full-sql.mjs`（20084 行 / 1643106 字节）。
- 不涉及菜单/权限种子（沿用 `promotion:pagedesign:theme`）、不涉及租户表白名单变更
  （page_design_theme 已登记）。

### 3.6 入口分层修正（2026-10-02 追加）

**问题**：主题能力最初整体挂在装修器顶栏弹窗里，而「设为商城默认」改的是**租户级全局
配置**——放在单页编辑器里作用域误导（运营调单页主题时可能顺带换掉全商城品牌色）、
发现性差（改品牌色要先进某个页面的编辑器）。竞品（有赞「全局风格」、微盟「风格管理」）
均把店铺级配色放在装修区域的**一级入口**。

**拆法**（不是简单搬运，而是按作用域分层）：

| 能力 | 作用域 | 落位 |
|------|--------|------|
| 主题库 CRUD、预设色板、设为商城默认、效果预览 | 租户级全局 | 新菜单「商城装修 → 商城主题」 |
| 页面引用哪个主题（跟随商城默认 / 指定主题） | 单页 | 装修器「本页主题」选择器 |

- 新页面 `views/promotion/mall-theme/index.vue`：左侧主题库（卡片列表 + 预设色板 +
  「商城默认」标记 + 设为默认/编辑/删除），右侧效果预览（导航栏 / 标签 / 价格 / 按钮 /
  tabBar 选中态，用当前预览主题的衍生色实时渲染）。选主色时自动重算页面底色。
- 「设为默认」加了二次确认弹窗，明示「整个小程序都会换色、用户下次打开生效」。
- 装修器 `theme-dialog.vue` 降级为「本页主题」：只列「跟随商城默认 + 各主题」，
  选中即写 `themeRef`（空串表示跟随），文案引导到独立菜单；顶栏按钮同步改名。
- 增量 SQL：boot `108mall_theme_menu.sql` / cloud `109mall_theme_menu.sql`
  （菜单 ID `2110000000000000205`，sort=15 位于微页面与模板市场之间；向拥有「微页面」
  的角色/租户推导式补授，与 84 号脚本同口径）。**菜单权限登录时快照，需重登生效。**
- 顺带收益：`promotion:pagedesign:theme` 由「微页面下的按钮权限」变为独立菜单权限，
  可单独授予「只管配色、不能编辑页面」的角色。

## 四、验证记录

- 后端：`PageDesignThemeServiceImplTest` 15/15（新增默认主题互斥/停用拒绝/空默认 5 例）、
  `ThemeColorUtilsTest` 2/2（钉死混白 35% 口径）；白名单契约测试补
  `/app/pagedesign/mall-theme`；`mvn test -pl aryn-boot -am` **BUILD SUCCESS**。
- 管理端：`check:type` 通过；`test:unit` 79 文件 571 用例全绿（含 theme-presets 新 5 例、
  preview-parity 17 例）；改动文件 prettier/eslint 干净。
- C 端：`pnpm type-check` 通过；vitest 582 例中 581 绿——唯一失败
  `replenish-contract.test.ts`（断言 Java 文件含「最小起订量」）为**既有失败**，
  归属船供 MOQ 下线（2026-09-29）与本分支在途改动，与主题无关；
  eslint 仅既有违规文件（vk-data-goods-sku-popup 等）报错，本次 diff 无新增违规。
- parity：管理端画布与 C 端 diy 的主题变量字符串逐字一致，preview-parity 全绿。
- 双模式：boot 白名单 `/app/pagedesign/**`、cloud 网关 `/promotion/app/pagedesign/**`
  均已覆盖新接口；promotion 模块内聚、无 biz 间 import。
- **菜单入口端到端实测（boot 实机，2026-10-02）**：`/boot/menu` 已下发「商城主题」
  （component `promotion/mall-theme/index`，父子层级正确）；打开页面主题库列表、
  预设色板、效果预览均正常渲染，「天空蓝」正确显示「商城默认」标记；装修器「本页主题」
  正确回显草稿既有 `themeRef` 为「当前」；切换「跟随商城默认」后草稿 revision 2→3、
  `themeRef` 置空、提示语正确，再切回「天空蓝」恢复（revision→4）。增量 SQL 重复执行
  幂等（菜单 1 行、租户授权 1 行）。

## 五、遗留与后续

1. **生效时效**：C 端换肤在冷启动（或下次 ensureLoaded）生效，与页面级快照随发布走
   一致；未做运行时推送即时刷新。
2. **深色模式页面级适配**按需推进（见 3.4 边界）。
3. 预设色板目前 8 套（活力红/暖橙/琥珀金/抹茶绿/青碧/天空蓝/优雅紫/玫粉），
   底色由主色统一衍生；后续可按行业扩充。
4. 自定义主色过浅时按钮文字对比度兜底（亮度自动切黑/白字）尚未实现——
   当前依赖预设的可控性与商家自检，列为增强项。
