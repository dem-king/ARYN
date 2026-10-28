# 小程序中英双语切换（英文界面）实施说明

> 日期：2026-10-26 ｜ 状态：**前端已实施；真机反馈的占位符裸露已修复，待真机复检** ｜ 范围：`aryn-mall-uniapp` C 端全部页面与应用级提示；**零后端 / 零 DB 变更**
> 关联：多租户（语言默认值可按构建配置）、双模式（纯前端，无接口路径变更）
> 结论入口：本文件 §0 与 `docs/90-记录归档/需求记录.md` 同日条目

---

## 0. 结论先行

1. **界面文案可 100% 中英切换**：`vue-i18n@9` 已接入（此前只是装了依赖、零使用），全 C 端页面/组件/工具层文案抽成 16 个词条模块，个人设置页可运行时切换并持久化，切完不重启即可生效（tabBar、自定义导航栏、页面内文案同步刷新）。
2. **后端业务数据仍为中文**：商品名/副标题、分类名、品牌名、活动名、公告正文、会员权益名、装修 JSON 内容、收货地址等均由后端单语字段下发，前端不做机翻。这是本次方案的**明确边界**，不是缺陷（见 §4）。
3. **交付工作台不英文化**：`src/pages/delivery/**` 与 `src/api/delivery.ts` 是内部配送员使用的页面，按既定口径排除；买家侧的物流进度组件（`components/delivery/delivery-progress.vue`）已英文化。
4. 微信原生 UI（tabBar 首次渲染间隙、微信胶囊菜单、分享面板、系统权限弹窗）与图片内文字无法由前端英文化。

三条彻底英文化路线（后端双语字段 / 机翻缓存 / 英文租户）的代价对比见 §7，本次未采用。

---

## 1. 背景与目标

客户有外国朋友使用小程序，而小程序此前只有中文。原始诉求：「里面所有显示的中文都变成英文」。

拆成三层看：

| 层 | 能否前端解决 | 本次处理 |
|---|---|---|
| 界面文案（按钮/标题/空态/toast/校验/导航） | 能 | **全部英文化** |
| 后端业务内容（商品/分类/活动/公告/装修/权益名） | 不能 | 保持中文，作为明确边界 |
| 微信原生 UI、图片、用户输入/地址 | 不能 | 不处理 |

目标：外国用户把系统语言切到 English 后，**所有前端可控制的界面文案**都显示英文，业务数据保持原文（避免机翻食品/规格/单位造成误购）。

---

## 2. 方案选型

| 方案 | 结论 |
|---|---|
| A. 前端 i18n + 词条包（**采用**） | 界面文案切换彻底、可回退、无接口成本；业务数据保持中文 |
| B. 读接口按语言机翻 + 缓存 | 不采用：需后端/翻译服务改造，食品与规格误翻风险高 |
| C. 复制一套英文租户数据 | 不采用：技术最省但要长期双份运营维护 |

语言策略：

- 支持 `zh-CN`（默认）与 `en-US`；
- 初始语言优先级：**本地存储 `ARYN_LOCALE` → 构建变量 `VITE_DEFAULT_LOCALE` → 系统语言**（`uni.getLocale()`，en 开头按英文处理）；
- 切换入口（三处，切换即持久化并对当前页面生效）：
  1. **登录页导航栏右上角**：显示可切换到的另一种语言（English / 简体中文），外国朋友在读不懂界面时也能一键切英文；
  2. **个人中心页头右侧**：与设置齿轮并排的玻璃胶囊，同款一键切换。个人中心的导航栏右上角被「居中标题 + 微信胶囊」夹住（375pt 屏只剩约 37px），放不下带文字的按钮，故收进页头右侧；
  3. **个人中心 → 个人信息 → 「语言 / Language」**：标准 action sheet 选择（带「语言已切换」提示）。
  入口逻辑收敛在 `src/composables/useLocaleSwitch.ts`，页面不再各写一套。

---

## 3. 实现架构

### 3.1 i18n 接入

| 文件 | 作用 |
|---|---|
| `src/locales/index.ts` | `createI18n({ legacy: false, globalInjection: true, fallbackLocale: 'zh-CN' })`；导出 `getLocale / setStoredLocale / resolveInitialLocale / translate / normalizeLocale` |
| `src/locales/compile-messages.ts` | 启动时把字符串词条预编译成 message 函数：小程序端 `vue-i18n` 是 runtime-only 构建（无 message 编译器），不预编译则 `{count}` 等占位符会裸露（见 §5.2） |
| `src/locales/zh-CN.ts` / `en-US.ts` | 聚合入口：公共词条（common/tabbar/login/userCenter/userSetting）+ 按模块 spread |
| `src/locales/modules/*.zh-CN.ts` / `*.en-US.ts` | 按业务域拆分的词条模块，中英 key 一一对应 |
| `src/store/localeStore.ts` | 语言状态 + tabBar 文案同步；`localeLabel` 按自身语言展示（简体中文 / English） |
| `src/composables/useTabBarLocale.ts` | tab 页 `onShow` 兜底同步 tabBar 文案 |
| `src/composables/useLocaleSwitch.ts` | 语言切换入口统一逻辑（登录页/个人中心一键互切 + 设置页 action sheet） |
| `src/App.vue` | `onLaunch` 初始化语言（含 tabBar 同步） |

词条模块（共 17 组，模块内再按页面分组）：

| 模块 | 覆盖 |
|---|---|
| `common` / `tabbar` / `login` / `userCenter` / `userSetting`（聚合入口内） | 全局通用、登录、个人中心、设置 |
| `catalog` / `product` / `search` / `cart` / `checkout` | 分类、商详、搜索、购物车、结算 |
| `order` / `afterSales` / `sharedCart` | 订单、退款售后、共享购物车（船供协作采购） |
| `accountMember` / `accountSettings` / `message` | 会员/积分/储值/分销、地址与资料、消息与客服 |
| `promotion` / `diy` | 拼团/秒杀/折扣/优惠券、首页装修楼层 |
| `vessel` / `app` / `shared` / `labels` | 船舶靠港、应用级错误与租户校验、跨页共享组件、工具层标签 |

> 共 17 个域模块；`common` / `tabbar` / `login` / `userCenter` / `userSetting` 五组公共词条直接写在聚合入口内。
> `zh-CN.ts` / `en-US.ts` 只做聚合（import + spread），**禁止**在页面里直接 import 单语言词条文件。

### 3.2 运行时行为

- **tabBar**：`pages.json`/`pages.config.ts` 里是静态中文，`localeStore.syncTabBar()` 用 `uni.setTabBarItem` 逐项覆盖；非 tab 页调用可能受基础库限制失败，tab 页 `onShow` 再同步一次。
- **导航栏标题**：C 端页面统一用自定义导航栏 `hr-navbar`，标题改为 `:title="t('...')"` 后随语言切换实时刷新；`definePage` 里的静态 `navigationBarTitleText` 仅作兜底保留。`vessel/port-vehicles` 是唯一天然导航栏页面，`onShow` 内 `uni.setNavigationBarTitle` 按当前语言设置。
- **非组件代码**：`import { translate } from '@/locales'` 在函数调用时求值（禁止模块顶层常量直接翻译，否则语言被固化）；工具函数返回展示文案时同理。
- **响应式陷阱**：`setup` 阶段拼装的 label 数组/对象一律改成 `computed`，否则切语言后页面不刷新。
- **请求头**：`src/api/core/instance.ts` 在 beforeRequest 注入 `Accept-Language: getLocale()`；后端目前仅 auth 验证码使用了 i18n 过滤器，其余接口不依赖该头，属于为后续后端英文化预留。
- **判定码与展示文案分离**：`utils/coupon-usability.ts` 的 `reason`、订单状态码等保留为非翻译判定码，由页面映射成当前语言词条；倒计时预警等改为结构化状态（如 `vessel-call-time` 返回 `{ text, urgent }`），**禁止对翻译后的文本做字符串比较或正则匹配**。

### 3.3 默认语言配置

`import.meta.env.VITE_DEFAULT_LOCALE`（`zh-CN` / `en-US`）可按租户构建定制默认语言；不配置则跟随系统语言。`.env` 内已留注释示例。

---

## 4. 覆盖范围与刻意不翻译清单

**已英文化**：登录、首页与装修楼层、分类、搜索、商品列表/详情、购物车、结算下单、订单列表/详情/物流/支付结果、退款售后、共享购物车全流程、消息与在线客服、个人中心、会员/积分/储值/签到/等级权益、地址与个人资料、分销与提现、足迹/收藏/评价、拼团/秒杀/限时折扣/优惠券、船舶绑定与靠港车辆、隐私授权弹窗、租户阻断页、全局请求错误与登录态提示。

**刻意保留中文（前端无法或不应处理）**：

| 内容 | 原因 |
|---|---|
| 商品名/副标题/详情、分类、品牌、活动名、公告正文、会员权益名、装修 JSON 文案、物流轨迹、司机/任务信息 | 后端单语业务数据；机翻食品、规格、单位会造成误购 |
| 字典标签（`src/sub-pages/utils/dict.ts`，如订单状态、支付方式） | 后端字典下发中文；页面按状态码映射到当前语言，未知状态保留后端原文 |
| 交付工作台 `src/pages/delivery/**`、`src/api/delivery.ts` | 内部配送员页面，用户为员工 |
| 微信原生 UI、分享卡片、图片内文字、用户输入（地址/昵称） | 前端不可控 |
| 代码注释、`console.*`、内部异常文案、API 路径与状态码 | 非用户可见 |

---

## 5. 验证

| 项 | 命令/方式 | 结果 |
|---|---|---|
| 类型检查 | `cd aryn-mall-uniapp && pnpm type-check` | 见 §5.1 |
| 单测（全量） | `pnpm exec vitest run` | 见 §5.1 |
| 词条一致性测试 | `src/locales/locale.test.ts`：中英 key 对齐、en-US 无中文（`common.chinese` 除外）、zh-CN 无空值、运行时切换生效 | 通过 |
| 小程序构建 | `pnpm build:mp-weixin` | 见 §5.1 |
| 剩余中文扫描 | 自研脚本只统计「模板文本节点 + 属性值 + 脚本字符串字面量（剔除注释/console/业务数据）」 | 见 §5.1 |
| 人工口径 | 语言入口切换后检查首页/分类/商详/购物车/结算/订单/我的/消息/营销页 | 待真机目检 |

### 5.1 实测结果

### 5.1 实测结果（2026-10-26 13:22）

| 项 | 结果 |
|---|---|
| 类型检查 | `pnpm type-check`（vue-tsc）**exit 0，0 error** |
| 单测（全量） | `npx vitest run`：**108 文件 / 1142 用例全绿**（基线 107 文件 / 1133 用例，新增 5 条 locale 用例（含 message 编译与字面竖线）+ 4 条入口契约用例） |
| 小程序构建 | `pnpm build:mp-weixin`：**DONE Build complete，exit 0**（11/11 task，产物 `dist/build/mp-weixin`） |
| 词条规模 | 17 个域模块 + 聚合入口（common/tabbar/login/userCenter/userSetting），**约 1842 条词条 × 2 语言**，`locale.test.ts` 校验 key 一一对应、en-US 无中文（`common.chinese` 除外）、zh-CN 无空值、运行时 `setStoredLocale` 生效 |
| 剩余中文精确扫描 | 自研扫描器（模板文本节点 + 属性值 + 脚本字符串字面量，剔除注释/console/业务数据）：**候选从 584 行 / 85 文件降到 34 行 / 18 文件**，逐条确认全部为刻意保留（见下） |
| 人工/真机 | **未做**：本次为代码级验证，需在小程序开发者工具/真机做中英切换目检（见 §6 与遗留风险） |

剩余 34 行候选均为「非用户可见」或「刻意保留」：语言自称（`简体中文`）、
逻辑判定码（`coupon-usability` 券不可用 reason、`vessel-bind-apply` 后端「待审核/等待运营」
匹配、`chain-import` 后端匹配结果截取）、后端规格哨兵（`默认`）、常量源表
（`CART_ACTION_TEXT`、`CATEGORY_BADGES`、`MEMBER_PRICE_BADGE_TEXT` 等，展示层另行
`translate()` 求值）、内部异常文案（`组件实例 proxy 为 null`、`查询支付结果超时`）。

### 5.2 真机反馈修复：小程序端词条占位符裸露（2026-10-26 14:2x）

**现象**：真机上首页与购物车等页面出现 `{groupNum}人拼团`、`¥{amount}`、`{value}折`、
`共 {count} 件商品`、`去结算({count})`、`注册{registerDays}天内新人 · 每人每款限{limit}件`
等未替换的占位符，中英各约 260 条含占位符词条受影响；页面其它文案正常。

**根因**：uni-app 在 mp 平台（`@dcloudio/uni-mp-vite` 的 `config().resolve.alias`）把
`vue-i18n` 别名到 runtime-only 构建
`@dcloudio/uni-cli-shared/lib/vue-i18n/dist/vue-i18n.runtime.esm-bundler.js`——微信小程序
禁用 `new Function`，官方 message 编译器无法在端上运行。该构建的 `messageCompiler` 是直通桩：

```js
messageCompiler: source => ctx => ctx.normalize([source])
```

字符串词条因此被原样返回，完全不做插值。§5.1 的代码级验证发现不了：vitest 在 Node 下用
完整构建（插值正常），`build:mp-weixin` 只打包不执行运行时逻辑。

**修复**：新增 `src/locales/compile-messages.ts`，在 `src/locales/index.ts` 构造 `messages` 前
把中英词条递归预编译成 message 函数（vue-i18n 对函数词条跳过编译器，`isMessageFunction`
分支直接求值），支持 `{name}` 命名插值与 `{'literal'}` 字面量转义，不修改原始词条模块。
H5 / 小程序 / App 三端因此行为一致，且词条插值可在单测里覆盖。

**验证**：

| 项 | 结果 |
|---|---|
| 类型检查 | `pnpm type-check` exit 0 |
| 单测全量 | `npx vitest run`：**110 文件 / 1153 用例全绿**（+2 文件 / +11 用例） |
| 新增契约测试 | `src/locales/mp-runtime-contract.test.ts` 直接加载 uni 的 runtime-only 构建：字符串词条不插值（钉住根因）、预编译后正常插值（钉住修复），覆盖截图中的 `cart.itemCount` / `cart.checkoutWithCount` / `diy.activityStrip.groupBuyTitle` / `diy.newbieFloor.perks` / `userSetting.pointsBalance` |
| 语法守卫 | `compile-messages.test.ts` 扫描全部词条，钉住只支持 `{name}` 与 `{'literal'}`；出现复数 / 链接消息 / 位置参数会直接失败，提示扩展编译器 |
| 小程序构建 | `pnpm build:mp-weixin`：**DONE Build complete，exit 0**；产物 `locales/index.js` 在启动时调用 `compileMessages`，`locales/compile-messages.js` 含纯 JS 编译器 |
| 产物自检 | 用 Node 加载构建产物 `locales/*.js` + `compile-messages.js`：中英各 **260 条含占位符词条 0 残留**，截图里的 8 处文案逐条断言通过 |
| 真机 | **待复检**：需在小程序开发者工具/真机确认首页、购物车等页面占位符已被数值替换 |

---

## 6. 已知限制

- 首次启动的 tabBar 文案存在极短的中文兜底间隙（pages.json 静态配置先渲染，`onLaunch` 同步覆盖）；英文默认构建可用 `VITE_DEFAULT_LOCALE=en-US` 缩小影响，彻底消除需按语言出两套 `app.json`。
- 装修楼层里「取数归一化阶段烘焙」的固定文案（活动入口条标题、领券楼层券面文案等）在首页/微页面监听 `locale` 变化后**重新拉取装修 JSON**刷新（已实现，不闪 loading）；其它自行缓存文案的页面如后续新增，需遵循同一口径。
- 语言切换即时生效依赖 `useI18n` 的响应式 `t`；仍有少量后端下发的静态文案不受控（见 §4）。
- 英文比中文长 20–40%，窄屏按钮/标签已按英文长度校对，但极端小屏与长单词（如 "Withdrawable"）建议真机目检。
- 后端错误 message 仍为中文：前端 toast 优先展示后端原文，仅前端兜底文案已英文化。

---

## 7. 后续可选路线（彻底英文化业务数据）

| 路线 | 做法 | 代价 |
|---|---|---|
| A 双语字段 | 商品/分类/活动/公告/装修 JSON 增加 `name_en` 等字段，管理后台维护，缺失回退中文 | 3–5 周开发 + 持续运营填写 |
| B 机翻 + 缓存 | 读接口按 `Accept-Language` 调翻译服务并缓存 | 1–2 周开发 + API 成本，食品/规格误翻风险 |
| C 英文租户 | 复制一套英文装修与商品数据 | 技术最省，长期维护两份数据 |

本次前端已把 `Accept-Language` 透传到后端，后续采用 A/B 路线时无需再改请求层。
