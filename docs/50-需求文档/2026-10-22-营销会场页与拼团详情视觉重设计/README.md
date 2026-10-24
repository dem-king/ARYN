# 营销会场页与拼团详情视觉重设计（秒杀会场 / 折扣会场 / 拼团详情 / 拼团列表）

> 日期：2026-10-22 ｜ 状态：**已完成（三轮：C 端 4 页 + 入口条 → 管理端注册 + 后端联调 + 双主题/状态矩阵验收 → 秒杀/折扣会场按原型逐项对齐）。验证证据见 §十二 / §十三 / §十四**
> 拍板记录：D1 秒杀 CTA=「抢」（保留加购动线）✅｜D2 拼团头像后端**本期一起做** ✅｜D4 详情显示「已拼 N 件」✅
> 关联前置：[`../2026-10-22-装修营销楼层视觉重设计/`](../2026-10-22-装修营销楼层视觉重设计/README.md)（首页三楼层已实施并通过验证，本轮只做其"非目标"缺口）
> 原始请求：「这是当前系统的秒杀、拼团、折扣组件实现，目前 UI/UX 比较 low，看着不好看；调研同类竞品（小象超市、盒马等生鲜商城）怎么做，给一份完整方案」

---

## 〇、一页结论

| 项 | 结论 |
|----|------|
| **先说清边界** | 首页三个**装修楼层**（秒杀/拼团/折扣）今天已完成重设计并通过自动化验证，本轮**不重做楼层**，只沿用它建立的设计语言；用户截图里仍显旧橙，是楼层旧版录屏/未刷新，与本轮缺口无关 |
| **真正 low 的是 4 个没改版的页面** | ① 秒杀会场页 `pages/promotion/seckill.vue`；② 折扣会场页 `pages/promotion/discount.vue`；③ **拼团详情页（最严重，直接显示团长裸 ID）**；④ 拼团列表卡 `components/group-buy-card/index.vue`（无商品图、无省额、无成团进度） |
| **竞品参照** | 盒马「限时疯抢」（大促头+场次节奏+立省角标+已抢进度+马上抢）、小象秒杀会场（回购/榜单信任词+到手价）、拼多多 2 人团（头像进度环+还差 1 人+双价对比+参与拼单）、盒马/叮咚折扣（到手价+折角标+省额+库存紧张） |
| **设计主线** | 复用首页楼层的「促销强度三级体系」：秒杀=强（渐变大促头+大倒计时+已抢进度+实心抢）、拼团=中（暖珊瑚+头像环+双价+去拼单）、折扣=轻（白底+折扣大牌+省额+弱按钮），不铺满大红 |
| **首页入口（D6 已拍板 A）** | 主流首页把秒杀/拼团/折扣当**入口**不当**楼层**：金刚区下一排等宽活动小卡（盒马式，新增 `activity-strip`，自动取进行中各一、点进会场）；首页不再让三个促销楼层通栏堆叠，主体留给商品流（§5.5） |
| **数据原则** | 只用现有真实字段造氛围（立省额、已抢%、剩 N 件、省 ¥X、已拼 N 件）；**不伪造**在抢人数/回购数/好评数（无字段，上一轮已定调） |
| **后端依赖** | 拼团详情「团长头像+成员进度环」**本期一起做**：复用 user-api 已有 `RemoteMallUserService.getUserByIds` 批量取昵称头像（无需新建远程接口、不改表），promotion-biz 在 record 列表装配 |
| **工作量** | 前端约 3 人日（含首页活动入口条）+ 拼团头像后端 0.5 人日（复用现成批量接口）+ 验收 0.5 人日，合计约 **4 人日**（详见 §8） |
| **已拍板** | D1 秒杀「抢」✅｜D2 头像本期一起做 ✅｜D4 显示已拼 N 件 ✅；D3/D5 按建议默认（见 §10） |

**视觉原型**（AI 示意稿；§5 规格已在 2026-10-23 第三轮按原型逐项对齐，见 §十四；首页楼层见前置目录）：

- 拼团详情改造：[prototypes/p2-groupbuy-detail.jpg](./prototypes/p2-groupbuy-detail.jpg)
- 秒杀会场改造：[prototypes/p5-seckill-hall.jpg](./prototypes/p5-seckill-hall.jpg)
- 折扣会场改造：[prototypes/p6-discount-hall.jpg](./prototypes/p6-discount-hall.jpg)
- 首页入口重构（金刚区+活动小卡条+商品流）：[prototypes/p7-home-entry-strip.jpg](./prototypes/p7-home-entry-strip.jpg)

**实施后实机截图**（H5 渲染 + 本地 boot 后端真实数据，同名区块可直接对照 §5 规格）：

| 页面 | 截图 | 关键验证点 |
|------|------|-----------|
| 秒杀会场（原型对齐版） | [screenshots/seckill-hall.png](./screenshots/seckill-hall.png) | 56rpx 大促头 / 白色面板 +「正在抢」下划线 Tab / 深色分格倒计时 / 浅主色条「已抢90% · 仅剩2件」/「立省¥10.00」实心角标 / 90×64rpx 方块「抢」/ 售罄整卡置灰 |
| 折扣会场（原型对齐版） | [screenshots/discount-hall.png](./screenshots/discount-hall.png) | 描边活动卡 + 实心「9.5折/8折」大牌 + 「剩」分格倒计时 + 去逛逛 / **双列网格商品卡** + 折角标 +「到手价」label + 省¥胶囊 + 右下快捷加购 |
| 拼团列表 | [screenshots/group-buy-list.png](./screenshots/group-buy-list.png) | 商品图/N 人成团徽标/双价/省额/已拼 N 件/去拼团 |
| 拼团详情 | [screenshots/group-buy-detail.png](./screenshots/group-buy-detail.png) | 商品名+商品图（未登录亦可）/双价/标签组/成团倒计时/空态引导/底部双 CTA |
| 首页活动入口条 | [screenshots/activity-strip.png](./screenshots/activity-strip.png) | 三类卡等宽、真实图、角标「抢/省¥7/9.5折」、钩子、跳会场 |
| 秒杀状态矩阵 | [screenshots/seckill-states-soldout.png](./screenshots/seckill-states-soldout.png) | 售罄整卡置灰+「已抢光」/库存紧张「仅剩 2 件」/未开始「即将开始」灰 CTA |
| 拼团详情（登录态） | [screenshots/group-buy-detail-logged-in.png](./screenshots/group-buy-detail-logged-in.png) | 真实后端头像昵称/头像环空位虚线问号/「还差1人拼成」/「已满员待成团」 |
| 拼团团态矩阵 | [screenshots/group-buy-actions.png](./screenshots/group-buy-actions.png) | 参与拼单 / 我参团中 / 去支付 / 去下单 / 已拼成（灰态）五分支同屏 |
| 天空蓝主题 | [秒杀](./screenshots/seckill-hall-blue-theme.png) · [折扣](./screenshots/discount-hall-blue-theme.png) · [拼团列表](./screenshots/group-buy-list-blue-theme.png) | 真实租户主题 `#4D7FFF`（DB `page_design_theme`「天空蓝」切为商城默认后实拍），全站换肤无写死色 |

---

## 一、背景与边界

### 1.1 上一轮已经做完什么（本轮不再重复）

`2026-10-22-装修营销楼层视觉重设计` 已完成并验证通过的是**首页装修器楼层**：

- `components/diy/diy-seckill/index.vue`（场次 Tab + 横滑卡 + 双态进度 + 马上抢）
- `components/diy/diy-limited-activity/index.vue`（单列横排/双列 + N 人成团徽标 + 双价 + 省额）
- `components/diy/diy-discount/index.vue`（折扣大牌 + 全场权益卡 + 双列商品 + 会场跳转）
- 配套：类型/归一化/管理端预览/parity 契约测试，后端零改动，902 + 869 用例全绿。

该方案 §1.2「非目标」明确写了：**不做 C 端秒杀/折扣会场页改版、不做拼团详情**。本轮正是补齐这条线。

### 1.2 本轮范围

| # | 对象 | 路径 | 现状评级 |
|---|------|------|---------|
| 1 | 秒杀会场页 | `src/pages/promotion/seckill.vue`（376 行） | 中：有场次/倒计时/进度，但进度方向反、无立省、氛围弱 |
| 2 | 折扣会场页 | `src/pages/promotion/discount.vue`（316 行） | 中：活动头浓烈但力度没放大、全场活动灰字、无省额 |
| 3 | **拼团详情页** | `src/sub-pages/promotion/group-buy/group-buy-detail/index.vue`（225 行） | **差：显示裸 leaderUserId、无商品信息、无头像环、无双价、无分享** |
| 4 | 拼团列表卡/页 | `src/components/group-buy-card/index.vue` + `group-buy-list/index.vue` | 差：无商品图、无省额、无成团进度、样式独立于楼层 |
| 5 | **首页营销入口层（新增方案）** | 新增 `diy-activity-strip` 活动小卡条；首页编排建议见 §5.5 | 现首页把三楼层通栏堆叠，割裂、不主流 |

### 1.3 非目标（本轮不做）

- 不改首页三楼层已实施的组件样式（`diy-seckill/diy-limited-activity/diy-discount` 保留可用）；但**建议首页编排不再把它们通栏堆叠**，改走 §5.5 入口条。
- 不改后端下单/成团业务逻辑；不新增秒杀「在抢人数」「回购/好评」等无字段社会证明。
- 不做搜索、分类筛选、会场装修配置（属另一期）；不做暗色模式（全站未上线）。

---

## 二、竞品调研（小象超市 / 盒马 / 拼多多 / 叮咚）

> 方法：抓取竞品真实 App 界面截图做布局拆解，结合长期稳定的模块形态；置信度标 `[实]`（本次看到截图）/`[知]`（行业通识）。

### 2.1 秒杀：盒马「限时疯抢」+ 小象秒杀会场 `[实]`

| 竞品元素 | 具体做法 | 我们可复用 |
|---------|---------|-----------|
| 大促头 | 橘红渐变大标题「限时疯抢」，场次名 + 黑底分格倒计时，右侧「24.2 万人疯抢中」头像堆叠 | 大促头+分格倒计时照做；**在抢人数无字段，本期省略** |
| 场次节奏 | 早 7 点抢 / 午 1 点抢 / 晚 9 点抢横滑 Tab，未开始标「即将开抢」 | 场次 Tab 嵌入头部，进行中高亮、未开始/已结束置灰 |
| 商品角标 | 图上红角标「立省 ¥20」「买 1 赠 1」「赠品」 | 图左上「立省 ¥X」红角标（用 original−seckill 真实价差） |
| 价格 | 特价大字 + 划线原价；右侧实心橙「抢」/「马上抢」 | 到手价 42rpx/800，划线灰，实心胶囊 CTA |
| 紧迫 | 已抢进度条 / 仅剩 N 件；售罄置灰「已抢光」 | 已抢% 双态进度（≥80% 切「仅剩 N 件」），售罄灰态 |
| 信任词 | 小象会场：「3 万人回购」「9 万人好评」「叶菜热卖榜第 1」 | **无字段，本期不做**，留待后端补销量/评价后加 |

### 2.2 拼团：拼多多 2 人团 `[实]`

| 竞品元素 | 具体做法 | 我们可复用 |
|---------|---------|-----------|
| 销量社会证明 | 商品头「已拼 107003 件 · 2 人拼单」 | 用活动已有 `virtualNum` 显示「已拼 N 件」（前端直接用，非伪造） |
| 成团进度环 | 团长头像 + 空位头像（问号）+「还差 1 人拼成」（1 人红色强调） | 成员头像环 `currentNum/groupNum`，空位虚线问号，「还差 X 人」红强调 |
| 成团倒计时 | 「23:59:19 后结束」 | 拼团记录 `expireAt` 倒计时 |
| 双价对比 | 底部「单独购买 ¥39.9」灰线 vs「发起拼单 ¥26.8」红底 | 详情底部双 CTA：单独购买（白底描边）/ 发起拼单（渐变实心） |
| 参与他人团 | 每条进行中的团一个「去拼单」按钮 + 成团倒计时 | 「正在拼的团」列表每条「参与拼单」 |

### 2.3 折扣：盒马到手价 / 叮咚晚间市集 `[实]`

| 竞品元素 | 具体做法 | 我们可复用 |
|---------|---------|-----------|
| 折扣即主视觉 | 红「9.9 折」小标 +「到手价 ¥7.79」红大字 + 划线 ¥18.79 | 活动头折扣大牌 + 商品到手价 + 划线 |
| 省额 | 浅底「省 ¥X」徽章 | 商品行「省 ¥X」浅橙徽章（original−discount） |
| 库存紧张 | 叮咚「仅剩 1 件」红标 | 折扣无库存字段，本期省略，保留倒计时 |
| 轻促销克制 | 折扣不铺满大红，橙色只给标签/价格，大量留白 | 会场页改白底，弱按钮「去逛逛」，与秒杀强红拉开层级 |

### 2.4 四条通用规律（跨竞品）

1. **信息锤不同**：秒杀表达「抢」（时间+库存+价差）、拼团表达「拼」（人数+双价+好友）、折扣表达「省」（力度+到手价）。
2. **价格永远是第一视觉**：到手价字号最大最红，原价灰小划线，省额独立徽章。
3. **紧迫三件套**：倒计时、进度/仅剩、限量/限购。
4. **CTA 分强弱**：秒杀实心「抢」、拼团实心「去拼单」、折扣描边「去逛逛」。

### 2.5 首页入口范式：营销活动为什么不该在首页铺楼层 `[实]`

> 针对「首页三个促销楼层纵向堆叠、显得不和谐、不主流」的问题，专门调研主流 App 首页如何安放营销入口。

| App | 首页怎么安放秒杀/拼团/折扣 | 证据 |
|-----|--------------------------|------|
| **盒马** | 金刚区（3×5 彩色图标）下方，**一排 3 个等宽活动小卡并排**：时令新鲜 / 尝新盒子 / **限时疯抢（63.7万+在抢）**，每卡=缩略图+标题+副标+起售价；点小卡进会场。首页主体是推荐双列商品流 | 盒马首页截图（豌豆荚版） |
| **小象超市** | 金刚区（品类图标）+ 一个**横向小条/卡位**放限时秒杀，不铺通栏；秒杀红标直接轻打在普通商品卡上（「秒杀」角标+立即抢） | 小象首页/会场截图 |
| **拼多多** | 金刚区两行图标宫格，**「限时秒杀」只是第一个带「抢」角标的 icon**，另有多人团、9块9特卖、断码清仓等并列；秒杀/百亿补贴都不在首页铺商品楼层，点 icon 进频道 | 拼多多首页改版截图 |

**三条结论**：

1. **首页 = 搜索 + 金刚区（品类/频道图标）+ 一排紧凑活动入口 + 商品推荐流**。营销活动在首页只承担「钩子」，完整氛围/倒计时/抢购进度全部收进**频道会场页**承接。
2. **秒杀/拼团/折扣在首页是「入口」，不是「楼层」**：轻量做法是金刚区带角标 icon（拼多多）；中等做法是金刚区下一排 2-4 个等宽活动小卡（盒马）；绝少让三个活动各自占一个通栏大卡纵向堆叠。
3. **首页主体永远是商品流**，促销色只出现在小角标、价格、小按钮上，大面积留白；通栏大红渐变楼层只在大促主会场使用，不进日常首页。

> 对照本项目：现有 `category-nav` 已是金刚区、`marketing-entry` 是纯图标宫格（无图卡/无钩子/无起售价），**缺的正是「盒马式活动小卡入口条」**；这也是 §5.5 要补的东西。

---

## 三、现状诊断（代码取证）

### 3.1 秒杀会场页 `seckill.vue`

| # | 问题 | 证据 |
|---|------|------|
| S1 | **进度条方向反**：`stockPercent = remaining/seckillStock`，画的是「剩余比例」，竞品表达「已抢%」 | `seckill.vue:162-168` |
| S2 | 无「立省 ¥X」角标与省额，价差只靠划线价，不突出 | 模板 `:294-306` |
| S3 | 场次条（渐变）与倒计时条（白底）分离，选中态仅 `rgba(255,255,255,.25)`，弱 | `:219-260` |
| S4 | CTA 文案「加入购物车」削弱秒杀心智（动线仍是进详情加购，可保留行为、改文案） | `:176-187` |
| S5 | 售罄/未开始只有灰按钮，无整卡置灰与氛围；空态仅一行灰字 | `:316-331` |

### 3.2 折扣会场页 `discount.vue`

| # | 问题 | 证据 |
|---|------|------|
| D1 | **0.95 被 `Math.round(value*10)` 渲染成「10 折」**（首页楼层已修，会场页漏修） | `:180-191` |
| D2 | 活动头整段橙渐变，折扣力度被压成 22rpx 小标签，没放大 | `:369-429` |
| D3 | 全场活动（无商品清单）只显示灰字「全场商品参与，进入商品详情查看折扣价」，像坏数据 | `:271-276` |
| D4 | 商品行无省额、无 CTA，折扣小标在品名下重复一次 | `:277-309` |

### 3.3 拼团详情页 `group-buy-detail/index.vue`（重点）

| # | 问题 | 证据 |
|---|------|------|
| G1 | **标题直接渲染 `record.leaderUserId` 裸用户 ID** | `:137` |
| G2 | 整页没有商品图/名/拼团价/单独购买价，用户不知道买什么 | 模板仅 record 列表 |
| G3 | 无成员头像、无成团进度环、无「还差 X 人」、无成团倒计时 | 仅 `currentNum/groupNum` 文字 `:151` |
| G4 | 底部只有「发起拼团」，无单独购买对比、无参与他人团、无分享 | `:207-214` |
| G5 | 空态「暂无拼团记录」冷，无发起引导 | `:196-199` |

### 3.4 拼团列表卡 `group-buy-card/index.vue`

| # | 问题 |
|---|------|
| C1 | 无商品图，纯文字卡，与首页楼层/竞品货架差距大 |
| C2 | 无省额、无「已拼 N 件」（`virtualNum` 已下发未用）、无成团进度 |
| C3 | 样式自成一套（16rpx 圆角、独立渐变），与首页拼团楼层不统一 |

---

## 四、设计总纲（沿用首页楼层规范，不另起炉灶）

### 4.1 促销强度三级（与首页同屏一致）

| 级别 | 页面 | 楼层头 | 信息锤 | CTA | 主色占比 |
|------|------|--------|--------|-----|---------|
| **强** | 秒杀会场 | 品牌渐变 + 暗化层 + 深色分格倒计时 | 场次节奏 + 已抢进度 + 立省 | 抢（实心） | 高 |
| **中** | 拼团详情/列表 | 浅底 + 珊瑚图标 | 头像环 + 双价 + 省额 + 已拼件数 | 去拼单（实心） | 中 |
| **轻** | 折扣会场 | 白底 + 折扣大牌 | 折扣力度 + 到手价 + 省额 | 去逛逛（描边弱按钮） | 低 |

### 4.2 色彩 token（禁止写死色值，跟随租户主题）

| 角色 | 取值 | 用途 |
|------|------|------|
| accent | `var(--wot-color-theme-primary)` | 到手价、折扣大牌、CTA、进度条、省额 |
| accent-2 | `var(--wot-color-theme-secondary)` | 渐变第二端、角标 |
| promo-soft | `var(--wot-color-theme-background)` | 浅底头、省额徽章底 |
| dark-block | 固定 `#1F2329` | 秒杀倒计时分格块（不随主题） |
| ink/ink-2/muted | `#1F2329/#5B616E/#8A8F99` | 标题/正文/辅助 |
| strike | `#B6B9C0` | 划线原价 |
| line | `#F1F2F4` | 发丝分隔 |

### 4.3 字号/圆角/间距（750rpx 设计稿，与首页楼层逐值同源）

| 元素 | 规格 |
|------|------|
| 页面大标题 | 38rpx/800（大促头内白字） |
| 商品名 | 25rpx/500，2 行截断 |
| 到手价 | **42rpx/800**（¥ 符号 20rpx/700），全卡最大 |
| 原价 | 20rpx/400 划线 |
| 省额徽章 | 19rpx/600，高 34rpx，浅橙底 |
| 倒计时数字 | 深色块 23rpx/700，`tabular-nums` |
| CTA | 24rpx/700，高 56rpx，pill 圆角 999 |
| 圆角 | 卡片 20rpx、商品图 16rpx、大牌 20rpx |
| 卡阴影 | `0 8rpx 24rpx rgba(0,0,0,.05)` |
| 触控 | 视觉按钮 56rpx，热区 ≥88rpx |

### 4.4 两端与双模式

- 本轮改的是 **C 端独立页面**（非装修器画布），无管理端预览，因此不涉及 px↔rpx parity 契约；但配色/字号/token 必须与首页楼层同源，避免两套促销语言。
- boot/cloud：页面纯前端取数，不新增 API 路径；拼团头像后端改动走 `*-api` VO + Dubbo，两模式同享。无数据库变更。

---

## 五、分模块方案

### 5.1 秒杀会场页（强）

> 2026-10-23 第三轮按用户提供的原型 `prototypes/p5-seckill-hall.jpg` 重排：大促头放大、场次 Tab 改为「白色面板 + 主色下划线」、已抢进度改为「浅主色条 + 组合文案」、CTA 改圆角方块、立省角标改实心主色块。以下为落地后的最终规格。

#### 结构

```
┌ 大促头（品牌渐变 + 12% 暗化层 + 装饰圆环） ────────────┐
│ (⚡)  限时秒杀 56rpx/800                               │
│      整点开抢 · 限量特价 · 手慢无                      │
└────────────────────────────────────────────────────┘
┌ 白色面板（上圆角 28rpx，上移 28rpx 叠压头图）──────────┐
│ 正在抢  10:00场  14:00场  20:00场     ← 选中=深字+下划线 │
│ 距本场结束 [02][:][54][:][16]        ← 深色分格倒计时    │
├────────────────────────────────────────────────────┤
│ ┌商品行卡（左图右文）──────────────────────────────┐ │
│ │[图｜立省¥9.9] 乐事薯片70g*10包                    │ │
│ │              ¥22.90 ¥32.80                       │ │
│ │              [已抢87% · 仅剩3件]（浅主色条） [抢]  │ │
│ └──────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────┘
```

#### 规格与数据映射

| 展示 | 来源/规则 |
|------|----------|
| 大促头 | 品牌渐变（`--wot-color-theme-secondary/primary`）+ 12% 暗化层 + 双装饰圆环；72rpx「秒」图标 + 标题 56rpx/800 + 副标 24rpx（白 82%） |
| 场次 Tab | `sessionList`，顺序：进行中→未开始；**进行中显示「正在抢」**，未开始显示起始整点「HH:00场」（`startTime` 解析失败回落 `sessionName`）；已结束不进 Tab。样式：选中 `#1F2329` 30rpx/700 + 40×6rpx primary 圆角下划线，未选中 `#8A8F99` 28rpx/500；Tab 高 88rpx 满足热区 |
| 倒计时 | 进行中取 `endTime`、未开始取 `startTime`；`#1F2329` 分格块 23rpx/700；倒计时结束自动刷新场次 |
| 立省角标 | `originalPrice − seckillPrice`，>0 才显示「立省¥X」；实心 primary 方块（圆角 8rpx、高 34rpx、20rpx/700） |
| 已抢进度（**修复 S1**） | `sold = seckillStock − remainingStock`，`pct = round(sold/seckillStock*100)`；文案「已抢 {pct}% · 仅剩 {remaining}件」（售罄「已抢光」）；容器为浅主色条（primary 10% 透明叠层，确保任意租户主题下都是品牌浅色，而非中性灰）+ 高 36rpx；库存未知隐藏整块 |
| CTA | 90×64rpx 圆角 20rpx 实心方块「抢」（保留 88rpx 热区），行为仍为进详情加购（**不改下单动线**）；未开始「即将开始」/已结束/售罄为灰底灰字；售罄整卡置灰 |
| 空/加载 | 头部 + 面板 + 2 行商品骨架；空态「暂无进行中的秒杀场次」 |
| 明确不做 | 头部「N万人在抢」头像堆叠、商品「N万人回购」信任词（后端无字段，不伪造，见 §4 数据原则） |

### 5.2 折扣会场页（轻）

> 2026-10-23 第三轮按用户提供的原型 `prototypes/p6-discount-hall.jpg` 重排：商品区由「纵向列表」改为「双列网格 + 卡片加购按钮」（D3 决策随之更新，见 §10），活动卡改为「白色描边卡 + 实心大牌 + 主色分格倒计时」。以下为落地后的最终规格。

#### 结构（活动卡 + 双列商品网格）

```
┌ 活动卡（白底 + 2rpx primary 描边，圆角 20rpx）──────────┐
│ [8折]  零食酒水狂欢8折专区           [去逛逛]（描边胶囊） │
│        全场商品参与 · 下单立减20%                       │
│        剩 [23][:][15][:][42]（primary 实心分格）        │
├─────────────────────────────────────────────────────┤
│ ┌商品卡──────────┐ ┌商品卡──────────┐                 │
│ │[8折] 方图       │ │[8折] 方图       │                 │
│ │ 老李卤豆干500g  │ │ 善味阁卤鸭脖400g│                 │
│ │ 到手价          │ │ 到手价          │                 │
│ │ ¥23.92 ¥29.90   │ │ ¥31.92 ¥39.90   │                 │
│ │ [省¥5.98]   (🛒) │ │ [省¥7.98]   (🛒) │                 │
│ └────────────────┘ └────────────────┘                 │
└─────────────────────────────────────────────────────┘
```

#### 规格与数据映射

| 展示 | 来源/规则 |
|------|----------|
| 折扣大牌 | `discountType`：1 打折 `{round(value*100)/10}折`（**修复 D1：0.95→9.5折**，不再 *10）；2 减价「减¥X」；3 一口价。实心 primary 方块 128×128rpx（白字 44rpx/800，3–4 字文案自动降档防裁切） |
| 活动卡 | 白底 + 2rpx primary 描边 + 圆角 20rpx；活动名 32rpx/700 单行；副行 24rpx `#8A8F99`（全场型「全场商品参与 · 下单立减 X%」，商品型「{N}件商品参与」）；状态标签保留在倒计时行尾 |
| 全场型（`goodsList` 空） | 权益卡沿用同一套活动卡视觉，**页面不出现灰字占位** |
| 商品卡 | 双列网格（gap 16rpx / 两侧 24rpx）：方图 + 左上 primary 折角标「{力度}折」+ 品名 26rpx/500 两行 + 「到手价」label + 到手价 `discountPrice` 42rpx/800 + 划线 `originalPrice` +「省¥X」浅橙胶囊（仅 diff>0）+ **右下 QuickCartButton 快捷加购**（登录拦截/多规格唤起 SKU 弹层/加购去向确认走既有全局链路，外层 `@click.stop` 防冒泡）；整卡仍可进详情 |
| 状态/倒计时 | 进行中/即将开始/已结束标签；倒计时用后端 `countdown` 秒数本地递减（沿用现有 `countdownMap`，避免 iOS 空格日期 NaN），呈现为「剩」+ 三个 primary 实心分格 |
| 空/加载 | 双列卡骨架；空态「暂无折扣活动」 |

### 5.3 拼团详情页（中，重点）

#### 结构

```
┌ 商品信息卡 ─────────────────────────────────────┐
│ [图]  天然饮用水整箱24瓶 550ml                   │
│       ¥25.90(拼团)  ¥29.90(单独购买划线)        │
│       [2人成团] [限购2件] [24小时内拼成] 已拼N件  │
│       距成团 23:15:42                            │
└──────────────────────────────────────────────┘
┌ 正在拼的团 ────────────────────────────────────┐
│ [团长头像] 爱**  (头像环 ● ○) 还差1人拼成      │
│   剩余23:15:42                      [参与拼单]    │
└──────────────────────────────────────────────┘
┌ 底部固定双CTA ────────────────────────────────┐
│ [单独购买 ¥29.90]（白底橙描边） [¥25.90 发起拼单] │
└──────────────────────────────────────────────┘
```

#### 规格与数据映射

| 展示 | 来源/规则 |
|------|----------|
| 商品卡 | `getActivityById(id)`：图（spu 图，需补 spuImage 或复用记录 sku 图）、`activityName`、`groupPrice`、`originalPrice` |
| 标签组 | `groupNum`人成团、`limitNum`限购、`groupExpireHours`小时成团、`virtualNum`已拼 N 件 |
| 成团倒计时 | 当前进行中团 `expireAt`；无进行中团则用活动 `endedAt` |
| 成团进度环 | 团长头像 + `currentNum/groupNum` 个位置，空位虚线问号；「还差 {groupNum−currentNum} 人拼成」（差 1 人红色强调） |
| 正在拼的团列表 | `listGroupBuyRecordByActivity`：每条团长头像+脱敏昵称、进度环、剩余倒计时、「参与拼单」；`isJoined` 高亮「我参团中」；`groupStatus=1` 显示「已拼成」灰态 |
| 底部双 CTA | 左「单独购买 ¥原价」→ 商品详情直接购买；右「¥拼团价 发起拼单」→ 现有发起流程 |
| 分享 | 右上角转发按钮，引导好友参团（`onShareAppMessage` 带 activityId） |
| 空态 | 无进行中团：插画式引导「还没人开团，来发起第一团」，主按钮发起 |

#### 头像/昵称：本期后端装配（§6.1）

本期一起做：后端在 record 列表批量回填团长/成员昵称头像（复用现成 `getUserByIds`）；前端头像缺失时仍用默认灰圆 + 脱敏名兜底，保证接口异常不白屏。

### 5.4 拼团列表卡 + 列表页

- 改为左图右文货架卡（与首页拼团楼层同构）：商品图 176×176、`N人成团`徽标、双价、省额、`virtualNum`「已拼 N 件」、状态标签、「去拼团」实心小按钮。
- `group-buy-list` 仅换卡组件，分页/下拉逻辑不变。

### 5.5 首页营销入口层重构（回应「首页铺楼层不和谐」）

> 现状问题：运营把 `seckill / limited-activity / discount` 三个楼层都拖进首页，各自通栏、各自渐变头，自上而下堆叠，割裂且抢商品流注意力。主流首页不这么做（§2.5）。
>
> **D6 已拍板：采用方案 A（盒马式活动小卡条）。** B（角标 icon）/C（仅留精选卡位）不再采用。

#### 5.5.1 首页编排（确定）

`search-bar → swiper-banner → category-nav(金刚区) → diy-activity-strip(本条) → goods-recommend / goods-waterfall(商品流)`。

- **不再把 `diy-seckill / diy-limited-activity / diy-discount` 三个通栏楼层拖上首页**；三个组件保留，仍可用于活动聚合页/专题页。
- 本条位于金刚区正下方、商品流之上，只占一屏内一条，完整促销氛围由各会场页承接。

#### 5.5.2 新增组件 `diy-activity-strip`（活动入口条）

不复用纯图标宫格的 `marketing-entry`（它无图卡/无钩子/无价格）。新建轻量组件，注册进 `DiyPage` 的 `activity-strip` 分支。

```ts
// retail-types.ts 新增
export interface ActivityStripEntry {
  key: 'seckill' | 'group-buy' | 'discount' | 'custom'
  title: string          // 活动名，如「限时秒杀」
  hook: string           // 钩子文案，如「14:00 整点开抢」，空则用默认
  badge: string          // 角标文字，如「抢」「省¥4」「8折」
  imageUrl: string       // 缩略图，空走首字色块兜底
  link: DecorationLink   // custom 时使用；内置三类用固定路由
}
export interface ActivityStripProps extends Omit<RetailBaseProps, 'count'> {
  dataSource: { mode: 'automatic' } | { mode: 'manual'; entries: ActivityStripEntry[] }
}
// 不设「每行列数 / 展示数量」：自动模式最多三类活动各一张，
// 手配模式的卡片数就是运营添加的数量，渲染侧按实际卡数自动排布。
```

#### 5.5.3 三类内置默认入口（自动模式）

并行复用现有 loader 各取 1 条进行中活动，组装成卡；某类无进行中活动则该卡隐藏，剩余卡自动等宽重排。

| 卡 | 默认名 / 钩子 / 角标 | 自动取数（复用现有 loader，count=1） | 固定跳转 |
|----|--------------------|--------------------------------------|---------|
| 秒杀 | 名「限时秒杀」/ 钩子「{sessionName}·整点开抢」/ 红角标「抢」 | `loadSeckillSessions` 取进行中首场，图取首件 `goodsList[0].goodsImage` | `/pages/promotion/seckill` |
| 2人拼团 | 名「2人拼团」/ 钩子「{groupNum}人成团立省」/ 橙角标「省¥{originalPrice-groupPrice}」 | `loadLimitedActivities({mode:'automatic',count:1})` 取进行中首条，图取合并后 `goodsImage` | `/sub-pages/promotion/group-buy/group-buy-list/index` |
| 限时折扣 | 名「限时折扣」/ 钩子「{折扣力度}专区」/ 橙角标「{折扣}折」 | `loadDiscounts({mode:'automatic',count:1})` 取进行中首条，图取首件图 | `/pages/promotion/discount` |

- 入口始终指向**会场聚合页**（不是单场/单活动），活动轮换后入口不失效；钩子与图随取到的活动实时刷新。
- 省额/折扣只由真实字段计算（§4 数据原则），无进行中活动的卡直接隐藏，不显示「暂无」占位。
- 折扣角标力度口径与会场页统一：`Math.round(discountValue*100)/10`（0.95→9.5折，见 §3.2 / R4），不出现「10折」。

#### 5.5.4 手动模式（可选）

`mode:'manual'` 时由运营在装修器按 `ActivityStripEntry[]` 配置卡片（名/钩子/角标/图/link），不实时拉活动，可放领券中心、自定义页等任意 `followDecorationLink` 支持的链接。本期默认走 automatic，手动配置面板在管理端后置（见 §5.5.7）。

#### 5.5.5 视觉规格（活动小卡）

| 元素 | 规格 |
|------|------|
| 卡片 | 等宽 1/卡数（一行最多三张，手配超出三张自动换行），白底，浅描边 `#F1F2F4`，圆角 16rpx，内边距 12rpx |
| 首行 | 缩略图 64×64rpx 圆角 12rpx + 活动名；指定商品活动取首件商品图，全场折扣没有商品清单时取「全店销量 Top1」真实商品图作代表（该商品确实参与全场折扣，不伪造数据），仍缺图才回落首字色块（沿用 `resolveCategoryFallback` 色板） |
| 活动名 | 24rpx/700 ink，与缩略图同一行，单行省略 |
| 钩子 | 20rpx/500 muted，独占整卡宽的第二行，单行省略（三张卡时也能完整显示「午间场·整点开抢」） |
| 角标 | 18rpx/700 小胶囊贴卡角：秒杀红「抢」、拼团橙「省¥X」、折扣橙「X折」，色值走 §4.2 token |
| 交互 | 整卡可点（`followDecorationLink`），右侧淡箭头与首行活动名垂直居中；无独立按钮、无大促渐变头 |

#### 5.5.6 空态与降级

- 三类活动都无进行中：整条不渲染（`invalidStrategy:'hide'`），不留空白占位。
- 单个 loader 失败：该卡隐藏，其余两卡照常；不报错、不阻断页面。
- 全场折扣无商品清单 → 缩略图取数层补「全店销量 Top1」真实商品图（`loadDiscounts` 的 `coverImage`），
  不退回「限」字色块；代表图查询失败/店内无商品时再回落首字色块，不阻断入口条。
  实测见 [activity-strip-discount-cover.png](./screenshots/activity-strip-discount-cover.png)（2026-10-23 H5 真实数据）。
- 远程图/头像加载失败走首字色块/默认灰圆兜底。

#### 5.5.7 管理端与本期边界

- **本期只做 C 端组件 + 默认 automatic 配置**；管理端装修器的「活动入口条」拖拽、手动选卡配置面板可排到下一期（届时补组件库注册与配置表单）。
- 管理端已补注册与配置面板：只配「数据来源（自动/手配）」与手配卡片，**不提供每行列数/展示数量**——卡片按实际数量自动等宽排布，一行最多三张，超出换行。
- 首页落地动作由运营在装修器：移除三个通栏促销楼层、拖入 `activity-strip`；无需改 `pages/home/index.vue`（纯装修驱动）。

> 原型：[prototypes/p7-home-entry-strip.jpg](./prototypes/p7-home-entry-strip.jpg)（金刚区 + 活动小卡条 + 推荐商品流）。

---

## 六、数据与后端依赖

### 6.1 拼团头像/昵称装配（本期一起做，复用现成接口）

**好消息：无需新建远程接口。** user-api 已有 `RemoteMallUserService.getUserByIds(List<String> userIds)`，返回 `UserInfoVO{id, nickname, avatarUrl, phone}`，promotion-biz 直接 `@DubboReference` 注入调用即可。

#### VO 增补（`GroupBuyRecordVO`，app 列表/详情共用）

| 字段 | 类型 | 说明 |
|------|------|------|
| `leaderNickname` | String | 团长昵称（原样展示，不下发手机号/密码） |
| `leaderAvatar` | String | 团长头像 URL |
| `memberList` | `List<GroupBuyMemberVO>` | 本团成员：`userId/nickname/avatarUrl/isLeader/memberStatus`（复用实体已有非表字段 `memberList`，新增一个轻量出参内嵌 VO） |

#### 装配点：`GroupBuyRecordServiceImpl.getPageByActivityId`

现状逐条 convert、未查成员/团长资料。改为：

1. 先 `selectPageByActivityId` 取本页 record；
2. 批量收集本页所有 `leaderUserId` + 各 record 的成员 userId（`groupBuyMemberService` 按 `recordId in (...)` 一次查出）；
3. **一次** `remoteMallUserService.getUserByIds(allUserIds)` 批量取资料，建 `Map<userId, UserInfoVO>`（避免 N+1 远程调用）；
4. 回填每条 VO 的 `leaderNickname/leaderAvatar` 与 `memberList`（团长置首、`isLeader=1`）；
5. 查不到的用户：头像置空走前端默认灰圆、昵称回落「拼友」，不抛异常。

#### 隐私与边界

- 只下发 `nickname/avatarUrl`，**不下发 phone、password、userId 明文以外的敏感字段**；不做昵称二次脱敏（与竞品一致，昵称即公开展示名）。
- 不改表、不落库头像昵称；boot 聚合与 cloud 拆分同享（走 `*-api` VO + Dubbo，符合 biz 间不直连规则）。

### 6.2 前端直接可用的现有字段（无需后端）

- 秒杀：`seckillPrice/originalPrice/seckillStock/remainingStock/sessionName/status/startTime/endTime`
- 折扣：`discountPrice/originalPrice/discountType/discountValue/activityName/countdown/status/goodsList`
- 拼团：`groupPrice/originalPrice/groupNum/limitNum/virtualNum/groupExpireHours/startedAt/endedAt`；record `currentNum/groupNum/expireAt/groupStatus/isJoined/myOrderId`

---

## 七、双模式（boot / cloud）核对

| 项 | 结论 |
|----|------|
| 前端页面 | 纯展示取数，无新增请求路径，`parseOpenBoot/rewriteBootUrl` 不涉及新域，两模式一致 |
| 后端补字段 | 走 `*-api` VO + Dubbo RPC，不跨 biz 直连 import，cloud 可拆分、boot 聚合均生效 |
| 数据库 | 本期零变更，无增量 SQL、不动全量脚本 |
| 主题色 | 全部走 token，多租户（暖橙/天空蓝/抹茶绿）自适应，不写死 `#FF2237` |

---

## 八、文件清单与工作量

### 8.1 文件清单

| 端 | 文件 | 改动 |
|----|------|------|
| C 端 | `pages/promotion/seckill.vue` | 大促头/场次 Tab/已抢进度修复/立省角标/售罄态/骨架 |
| C 端 | `pages/promotion/discount.vue` | 折扣大牌/全场权益卡/省额/修 9.5 折/弱按钮 |
| C 端 | `sub-pages/promotion/group-buy/group-buy-detail/index.vue` | 商品卡+双价+头像环+参与团列表+双 CTA+分享+空态 |
| C 端 | `components/group-buy-card/index.vue` | 左图右文货架卡+省额+已拼件数，对齐楼层 |
| C 端 | 新增 `components/diy/diy-activity-strip/index.vue` + `retail-types.ts` 增 `ActivityStripProps`，注册到 `DiyPage` | 盒马式活动小卡入口条（§5.5）；自动模式复用 `loadSeckillSessions/loadLimitedActivities/loadDiscounts` 各取 1 条，固定路由进会场；管理端配置面板本期后置 |
| C 端 | `api/promotion/groupBuyActivity.ts`、`sub-pages/api/promotion/groupBuyRecord.ts` | 补 VO 类型：`leaderNickname/leaderAvatar/memberList[]` |
| 后端 | `GroupBuyRecordVO` + 新增 `GroupBuyMemberVO`（轻量出参） | 增 `leaderNickname/leaderAvatar/memberList` 字段 |
| 后端 | `GroupBuyRecordServiceImpl.getPageByActivityId` | 注入 `RemoteMallUserService`，批量 `getUserByIds` 一次回填，避免 N+1（§6.1） |
| 文档 | 本 README；完成后登记 `docs/90-记录归档/需求记录.md` | |

### 8.2 工作量

| 工作包 | 内容 | 估时 |
|--------|------|------|
| A | 秒杀会场 + 折扣会场前端 | 1.25 人日 |
| B | 拼团详情前端（头像环+参与团+双CTA）+ 列表卡 | 1.25 人日 |
| C | 拼团头像后端装配（复用 `getUserByIds`，无新远程接口） | 0.5 人日 |
| E | 首页活动入口条 `diy-activity-strip`（C 端；管理端预览可后置） | 0.5 人日 |
| D | 验收/真机目检/文档归档 | 0.5 人日 |
| **合计** | | **约 4 人日**（A/B/E 可并行；后端 C 与前端 B 解耦，可随批） |

### 8.3 验证命令

```bash
# 小程序（前端全部改动）
cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit && pnpm build:mp-weixin
# 后端（本期 §6.1 头像装配）
cd aryn-mall-java && mvn clean compile -pl aryn-boot -am && mvn test -pl aryn-boot -am
```

视觉验收：秒杀场次切换/倒计时跳动/已抢双态/售罄；折扣 9.5 折口径/全场权益卡；拼团详情双价/头像环/参与团/双 CTA；暖橙+天空蓝主题各一组截图。

---

## 九、风险

| # | 风险 | 缓解 |
|---|------|------|
| R1 | user-api 远程调用超时/部分用户查不到 | 批量查失败不阻断主流程：头像置空走默认灰圆、昵称回落「拼友」；前端同样兜底 |
| R2 | 拼团详情需商品图，record/activity 无图字段 | 前端按 `spuId` 调商品接口取主图，缺图走占位；不为此给活动表加图字段 |
| R3 | 秒杀「抢」文案与加购动线 | 已拍板：行为不变（进详情加购、服务端按秒杀价结算），仅 CTA 文案改「抢」 |
| R4 | 0.95 折修复只改会场页，避免与首页楼层口径分叉 | 两处统一 `Math.round(value*100)/10` |
| R5 | 真机头像环/双列密度 | 微信开发者工具目检 2 人团与多人团两种密度 |
| R6 | 自动模式某类无进行中活动 | 该卡隐藏、剩余等宽重排；三类全无整条不渲染，不留空白占位；单 loader 失败不阻断其余（§5.5.6） |
| R7 | 管理端配置面板本期后置 | 本期 C 端只支持默认 automatic；手动选卡/自定义链接待下一期补装修器表单，不在本期估时 |

---

## 十、决策点（拍板记录）

| # | 决策 | 结论 | 状态 |
|---|------|------|------|
| D1 | 秒杀 CTA 文案 | **「抢」**（保留进详情加购行为，服务端按秒杀价结算，仅改文案） | ✅ 已拍板 |
| D2 | 拼团头像后端 | **本期一起做**，复用 `RemoteMallUserService.getUserByIds`，无新远程接口、不改表 | ✅ 已拍板 |
| D3 | 折扣会场商品布局 | ~~纵向列表~~ → **双列网格 + 卡片右下快捷加购**（2026-10-23 第三轮按用户提供的原型 p6 调整；原「纵向列表」为建议默认，现已被原型覆盖） | ✅ 已按原型更新 |
| D4 | 拼团详情「已拼 N 件」 | **显示**（用活动已下发的 `virtualNum`，非伪造） | ✅ 已拍板 |
| D5 | 实施节奏 | **秒杀/折扣先上，拼团详情+头像跟进**（A/B 解耦，可分批发版） | 按建议默认，可改 |
| D6 | 首页活动组织方式 | **A 盒马式活动小卡条**（§5.5，已拍板）：金刚区下一排等宽小卡，自动取进行中秒杀/拼团/折扣各一，点进会场；首页不再三楼层通栏堆叠 | ✅ 已拍板 |

---

## 十一、验收清单

- [x] 秒杀：场次 Tab 切换、倒计时按场跳动、已抢%/仅剩 N 件双态、立省角标、售罄置灰 —— 口径与首页 `diy-seckill` 对齐（`pct<=0 →「限量 N 件」`、`remaining<=0 →「已抢光」`）；售罄/仅剩/未开始三态另用真实接口数据改造截图验证（§13.3）
- [x] 折扣：9.5 折口径、全场权益卡（无灰字）、到手价+划线+省额、弱按钮
- [x] 拼团详情：商品卡双价、头像环+还差 X 人、参与团列表、双 CTA、分享、空态引导 —— 头像环/参与团/五种团按钮分支已在重建后的真实后端 + 登录态 H5 下截图验证（§13.2、§13.3）
- [x] 拼团头像：record 列表返回 `leaderNickname/leaderAvatar/memberList`；批量一次查询无 N+1；远程失败/查无用户走灰圆兜底，不报错 —— 已重建 boot 容器真实联调，44 项断言通过（§13.2）
- [x] 拼团列表卡：商品图、N 人成团、双价、省额、已拼 N 件
- [x] 首页入口条 `activity-strip`：自动模式并行取进行中秒杀/拼团/折扣各一；三类卡等宽、角标/钩子/图正确，分别跳进对应会场页；某类无活动隐藏该卡、三类全无整条隐藏；单类 loader 失败不阻断其余 —— C 端已实现，**管理端装修器注册（拖拽 + 属性面板 + 编辑器预览）也已补齐**（§13.1），运营可自助配置自动/手动两种数据源
- [x] 多租户主题下颜色走 token，无写死促销色 —— 真实租户主题「天空蓝 `#4D7FFF`」切为商城默认后实拍三页（§13.4）
- [x] `pnpm type-check/test:unit/build:mp-weixin`（前端）全绿；后端改动 `mvn compile/test` 通过 —— 另含管理端 `pnpm test:unit`（110 文件 / 909 用例）与 `pnpm check:type`
- [x] boot/cloud 行为一致，无 DB 变更 —— 本轮联调只临时插入/删除 `9900` 前缀测试行并已回滚复核为 0；主题切换已还原为原「测试」主题
- [x] 文档回写并登记需求记录

---

## 十二、实施记录（2026-10-22 第一轮：C 端 + 后端）

### 12.1 改动文件

| 端 | 文件 | 结果 |
|----|------|------|
| C 端 | `src/pages/promotion/seckill.vue` | 大促头 + 内嵌场次 Tab + 深色分格倒计时 + 立省角标 + S1 已抢进度修复 + 售罄/未开始灰态 + 骨架/空态 |
| C 端 | `src/pages/promotion/discount.vue` | 白底轻促销活动卡 + 折扣大牌 + D1 口径修复 + 全场权益卡（替换灰字）+ 省额徽章 + 弱按钮 + 骨架/空态 |
| C 端 | `src/sub-pages/promotion/group-buy/group-buy-detail/index.vue` | 商品卡双价/标签组/已拼 N 件 + 成团倒计时 + 团长头像昵称/头像进度环/还差 X 人 + 参与团列表（四分支动线原样保留）+ 双 CTA + 分享 + 空态 + 骨架 |
| C 端 | `src/components/group-buy-card/index.vue` + `group-buy-list/index.vue` | 左图右文货架卡（176×176 图、N 人成团、双价、省额、已拼 N 件、去拼团）；列表页一次 `getByIds` 批量补图无 N+1 |
| C 端 | `src/sub-pages/api/promotion/groupBuyRecord.ts` | `GroupBuyRecord` 补 `leaderNickname/leaderAvatar/memberList`；新增 `GroupBuyMemberVO` 出参类型（保留实体类型） |
| C 端 | 新增 `src/components/diy/diy-activity-strip/{index.vue,activity-strip.ts,activity-strip.test.ts}`、`retail-types.ts`、`diy/index.vue`、`registry.ts` | 活动入口条组件 + 纯逻辑 + 29 个回归用例 + 三处注册 |
| 后端 | `GroupBuyRecordVO`（+3 字段）、新增 `GroupBuyMemberVO`、`GroupBuyRecord`（删除无人使用的非表字段 `memberList`，避免父子同签名 getter 冲突） | record 列表/详情出参 |
| 后端 | `GroupBuyRecordServiceImpl#getPageByActivityId`、`IGroupBuyMemberService(+Impl)#getByRecordIds` | 一次批量成员查询 + 一次 `remoteMallUserService.getUserByIds` 回填，固定 2 SQL + 1 RPC，无 N+1 |
| 后端 | `PageDesignComponentTypes` | 新增 `ACTIVITY_STRIP` 并加入 `KNOWN_TYPES`（`automatic` 模式，不入 `DATA_DRIVEN_TYPES`/`SINGLETON_TYPES`） |
| 后端 | `GroupBuyRecordServiceImplTest` | 构造函数补 mock，新增 3 个用例（批量装配成功 / 远程异常降级 / 查无用户逐人兜底），17/17 全绿 |
| 文档 | 本 README + `docs/90-记录归档/需求记录.md` + `docs/50-需求文档/README.md` | 回写与索引 |

### 12.2 线上口径修复（本轮顺手修掉的真实缺陷）

1. **S1 已抢进度方向反**：`sold = seckillStock − remainingStock`；口径与首页楼层统一为「已抢光 / 仅剩 N 件 / 限量 N 件 / 已抢 N%」四态。
2. **D1 折扣口径**：`Math.round(value*100)/10`（0.95→9.5 折、0.8→8 折），力度 ≥10 折退化为中性「折扣」，全链路（会场页 + 入口条角标）不再出现「10折」。
3. **商品字段名错配（真机验收发现）**：商品接口真实响应是 `{ id, name, spuUrls[] }`，旧代码读 `spuName/picUrl/sliderPicUrls` → 拼团列表/详情的商品图永远是占位、详情页标题回落成活动名。统一改用既有 `normalizeRetailGoods`（`firstImage` 已兼容 `spuUrls[0]`）。
4. **拼团详情未登录 401**：`getById` 必须带登录态（写商品足迹），分享出去的拼团页访客多数未登录 → 商品信息整块空白。改走免登的 `getByIds([spuId])`。
5. **H5 横向溢出**：`scroll-view` 在 uni-h5 基座自带 `width:100%`，content-box 下 padding 撑出视口（实测 415px > 390px），秒杀进度文案与 CTA 被裁；补 `box-sizing: border-box`。

### 12.3 验证（2026-10-22 真实执行）

| 项 | 命令/方式 | 结果 |
|----|-----------|------|
| 小程序静态+单测 | `cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit` | type-check exit 0；83 文件 / **898 用例全绿**（基线 869 + 入口条 29） |
| 小程序构建 | `pnpm build:mp-weixin` | DONE Build complete；`src/pages.json`、`components.d.ts`、`auto-imports.d.ts`、`manifest.json` 构建后无 diff |
| 后端单测 | `mvn test -pl aryn-promotion/aryn-promotion-biz -am` | BUILD SUCCESS；**289 用例**，其中 `GroupBuyRecordServiceImplTest` 17/17 |
| boot 聚合编译 | `mvn -q compile -pl aryn-boot -am -DskipTests` | exit 0 |
| H5 真机渲染 | 8888 dev server + 本地 boot 9999 真实数据，Playwright（无头 Chrome，390×844，未登录上下文） | 5 张截图见上方清单；0 console error、0 个 4xx/5xx（含 401/403），秒杀/折扣/拼团列表/详情全页面无横向溢出 |
| 入口条渲染 | 临时 DIY 页面注入 `activity-strip` 节点渲染后删除（未入库） | 三卡等宽、真实图、角标「抢 / 省¥7 / 9.5折」、钩子、跳会场正确 |

### 12.4 边界（第一轮结束时；其中「头像联调 / 第二套主题」已在 §十三 闭环）

- ~~拼团头像未做重启联调~~ → 已在 §13.2 闭环（重建 boot 容器 + 真实 API 44 项断言 + 登录态截图）。
- ~~未补第二套主题截图~~ → 已在 §13.4 闭环（真实租户「天空蓝」主题切换实拍）。
- **头像环仅覆盖 2–4 人**：`groupNum>4` 折叠成 `+N`，避免一行挤爆；若产品要求逐位置全量绘制，调 `MAX_RING_SLOTS` 即可。
- **拼团失败记录不进「正在拼的团」栏**：该栏只放进行中 + 已拼成；失败记录在「我的拼团」查看（原页面会把三态混在一栏，语义更差）。
- **未提交 git**：工作区含用户既有未提交修改，两轮都只落文件、不 commit、不切分支。

### 12.5 下一步（按 R7 顺延）→ 已在 §十三 完成

~~活动入口条目前只有 C 端能力，还差管理端装修器注册。~~ 已在第二轮补齐（§13.1）：管理端组件面板可拖拽、属性面板支持自动/手动两种数据源、编辑器预览复用真实 loader。剩余可选后续项见 §13.6。

---

## 十三、第二轮补强记录（2026-10-22 收口）

> 目标：把第一轮登记的三条「代码就绪但入口未开 / 未联调 / 未目检」边界全部闭环，并补齐状态矩阵验收。

### 13.1 管理端装修器注册：`activity-strip` 可被运营拖入首页

| 端 | 文件 | 结果 |
|----|------|------|
| 管理端 | 新增 `apps/web-ele/src/views/promotion/page-design/components/activity-strip/{types.ts,index.vue,setting.vue}` | 类型与 C 端 `ActivityStrip*` 严格同形；编辑器**真实数据预览**（并行复用三个既有 loader，单类失败只隐藏该卡、剩余等宽重排、三类全无走 frame empty、缺图首字灰块兜底）；属性面板支持列数 2/3/4、展示数量、自动/手动数据源、卡片增删（≤10，含类型/标题/钩子/角标/图/`LinkUrl`）、空态与失效策略 |
| 管理端 | `page-designer/registry/component-registry.ts` + `component-registry.test.ts` | 注册「活动入口条 / 营销活动」，组件面板「营销活动」分组可拖拽；retail 组件数 12→13 |
| 管理端 | `page-designer/preview-parity.test.ts` / `mobile-renderer-contract.test.ts` | 新增两端契约用例：类型同形、固定会场路由、折扣口径（`×100/10`、≥10 退化）、省额守卫、`Promise.allSettled` 隔离、默认策略；并纳入既有 renderer/props 契约 |

与 C 端逐值同源（1rpx = 0.5px）：卡片圆角/内边距 8px、缩略图 32px（圆角 6px）、活动名 12px/700、钩子 10px、角标 9px/高 14px 胶囊、发丝描边 1px、右侧箭头 5px；促销色只走主题 token。

**验证**：`cd aryn-mall-ui && pnpm test:unit` → **110 文件 / 909 用例全绿**（基线 902 + 新增 7）；`pnpm check:type` → exit 0（turbo，37 packages）。
> 未对管理端做浏览器截图：管理端登录依赖滑动拼图验证码（`blockPuzzle`），无头环境无法稳定通过；组件面板/画布/属性面板的能力由上述契约测试 + turbo typecheck 保障，建议运营侧首次使用时目检一次。

### 13.2 后端重建联调：拼团头像真实出参

- `mvn package -DskipTests -pl aryn-boot -am` BUILD SUCCESS → `docker compose -f docker-compose-boot.yml build aryn-boot` + `up -d --no-deps --force-recreate aryn-boot`；容器内 `/app/app.jar` 与本地 `target/aryn-boot.jar` md5 一致（`20377e71…`），`/boot/actuator/health` → `{"status":"UP"}`；mysql/redis/rocketmq/xxl-job 未动。
- 登录（`POST /boot/toc-token/password/login`，**需 `platform-type: H5` 头**）后调 `GET /boot/app/groupbuy/record/page`：**44/44 断言通过** —— 两条记录的 `leaderNickname` 非空、`leaderAvatar` 透传、`memberList` 团长置首（`isLeader="1"`、nickname/memberStatus 齐全）、2/2 人团 `memberList.length=2`、登录用户 `isJoined=true`；**未登录 HTTP 200 + `isJoined=false`（无 401）**；响应不含 phone/password。
- 登录态 H5（注入 uni storage 登录态）：**20/20 DOM 断言通过** —— `.record-item`×2、`.record-ring`×2、`.ring-slot`×4、`.ring-avatar--empty`×1（还差 1 人）、`.ring-avatar--text`×1（头像缺失→首字灰圆）、头像图 `naturalWidth=64` 真实加载；文本含「还差1人拼成」「已满员待成团」「我参团中」。
- 测试数据：2 个 `9900` 前缀测试用户 + 2 条记录 + 3 条成员行，**用完已删除并复核为 0**；既有用户/活动记录未受影响。
- 踩坑记录（供后续联调复用）：MySQL BIGINT 上限 9.22e18，19 位 `99…` ID 会 out of range（用 18 位）；C 端登录接口必须带 `platform-type` 头；H5 登录态存储是 uni 的 `{type:"object",data:{...}}` 包装格式。

### 13.3 状态矩阵验收（H5 真实渲染）

| 状态 | 方式 | 结果 |
|------|------|------|
| 秒杀 售罄 / 库存紧张 / 未开始 | Playwright 路由拦截真实接口响应后改造 `remainingStock`（不改 DB、不改代码） | 售罄卡整卡置灰 + 红色「已抢光」+ 100% 进度 + 灰色「已抢光」按钮；紧张卡「仅剩 2 件」+ 实心「抢」；未开始「即将开始」灰 CTA + 「距本场开始」 |
| 拼团 五分支按钮 | 路由拦截 `record/page` 返回 5 条不同态记录 | 参与拼单 / 我参团中 / 去支付 / 去下单 / 已拼成（灰态且不显示参团按钮）同屏正确；5 个团、10 个环位、3 个空位 |
| 拼团 空态 | 真实未登录数据 | 「还没人开团，来发起第一团」+ 发起按钮 |

### 13.4 多租户主题验收（真实主题切换，非模拟）

把 DB `page_design_theme` 中「天空蓝」行（`primary_color=#4D7FFF`、`page_background_color=#F1F5FF`）临时置为商城默认主题（`mall_default_flag=1`），`/boot/app/pagedesign/mall-theme` 即返回 `#4D7FFF`；H5 四页实测 CSS 变量 `--wot-color-theme-primary=#4D7FFF`、无 console error、无横向溢出 → 截图三张（见上方截图表）；随后**已还原**为原「测试」主题（`#ff5500`）并复核接口返回值。

### 13.5 第二轮验证汇总

| 项 | 命令/方式 | 结果 |
|----|-----------|------|
| 管理端单测 | `cd aryn-mall-ui && pnpm test:unit` | 110 文件 / **909 用例全绿** |
| 管理端类型 | `pnpm check:type` | exit 0（turbo 37 packages） |
| 后端重建 | `mvn package` + docker rebuild/restart | jar md5 一致、health UP、仅重启 aryn-boot |
| 真实 API | 44 项断言（快照 `/tmp/avatar-e2e/api-assertions.json` 留档） | 44/44 |
| 登录态 H5 | 20 项 DOM 断言 | 20/20 |
| 真实主题 | CSS 变量 + 三页截图 | `#4D7FFF` 全站生效 |
| 数据清理 | `cleanup.sql` + SELECT 复核 | 测试行 0，既有数据完好 |
| 工作区 | 生成物 diff 审计 | `pages.json`/`components.d.ts`/`auto-imports.d.ts`/`manifest.json`/`uni-pages.d.ts` 无 diff；未提交 git |

### 13.6 仍存的边界（不影响验收）

- **远程用户服务异常降级分支**（昵称回落「拼友」/头像空）在 boot 单体下是同进程 Dubbo 调用，真机无法在不改代码的情况下制造远程异常；该分支仅由单测覆盖（`GroupBuyRecordServiceImplTest` 3 个降级用例）。
- **`avatar_url` 为相对路径时**（如 `/static/default-avatar.png`）H5 可解析，微信小程序端可能无法直接加载；接口原样透传 DB 值，属既有数据规范问题，前端已做首字灰圆兜底。
- **`activity-strip` 未进商详/分类/个人中心页白名单**：与 `marketing-entry` 同口径，语义上是首页营销入口；若后续要在分类页复用，需另开白名单（两端 + 后端白名单三处同步）。
- **管理端未跑 `pnpm lint`**：本轮只对新增 3 个文件做了 prettier 对齐；若 CI 有 stylelint/eslint 门禁，合并前补跑一次。
- **小程序真机目检**：本轮用 H5 渲染验证（含主题/状态矩阵），微信开发者工具内的环位密度与头像加载仍建议上线前目检一次（R5）。

---

## 十四、第三轮：秒杀/折扣会场按原型逐项对齐（2026-10-23）

> 触发：用户对照 `prototypes/p5-seckill-hall.jpg` / `prototypes/p6-discount-hall.jpg` 质询「这两个页面是按图这样改造的吗」。逐项比对后确认**方向一致但未逐像素复刻**（README 原定「原型为 AI 示意稿，落地以 §5 规格为准」），用户拍板 **全面向原型靠拢**。

### 14.1 改动（仅两个页面文件，无后端/管理端改动）

| 页面 | 文件 | 变化 |
|------|------|------|
| 秒杀会场 | `aryn-mall-uniapp/src/pages/promotion/seckill.vue` | 大促头放大（标题 38→56rpx/800、图标 72rpx 圆形、副标独立一行）；场次 Tab 由「渐变头内胶囊」改为「白色圆角面板 + 深字 + primary 下划线」，进行中显示「正在抢」、未开始显示「HH:00场」；已抢进度由「进度轨道 + 单状态」改为「浅主色条 + 组合文案『已抢 X% · 仅剩 N件』」；CTA 由 pill 改为 90×64rpx 圆角方块；立省角标由渐变胶囊改为实心 primary 方块；深色分格倒计时保留 |
| 折扣会场 | `aryn-mall-uniapp/src/pages/promotion/discount.vue` | 商品区由「纵向列表」改为**双列网格**（方图 + 左上折角标 + 到手价 label + 省¥胶囊 + **右下快捷加购按钮**）；活动卡改白底 + 2rpx primary 描边 + 实心 primary 大牌（128rpx）+ 「剩」+ primary 实心分格倒计时 + 描边「去逛逛」；全场权益卡沿用同一套活动卡视觉；骨架改双列；分页/倒计时/口径/状态标签/整卡进详情全部保留 |

两处实现要点：
1. **快捷加购**复用既有 `components/quick-cart-button`（登录拦截 / 多规格唤起 SKU 弹层 / 加购去向确认 / 弹层全局挂载），公共组件零改动；外层 `@click.stop` 防冒泡，整卡仍可进商详。
2. **浅主色条**不直接用 `--wot-color-theme-background`：租户 `pageBackgroundColor` 可能是中性灰（默认主题 `#f5f5f5`），会把「浅粉条」洗成灰条。改为 primary 10% 透明度叠层，暖橙主题=浅粉、天空蓝主题=浅蓝，任何租户都保持品牌浅色。

### 14.2 决策更新

- **D3 折扣商品布局**：由「纵向列表（建议默认）」改为「双列网格 + 快捷加购」（按原型，见 §10 已更新）。
- **明确仍不做**：头部「N万人在抢」头像堆叠、商品「N万人回购」信任词 —— 后端无字段，按 §4 数据原则不伪造（原型有，属竞品的估算氛围，不落地）。

### 14.3 验证（2026-10-23 真实执行）

| 项 | 命令/方式 | 结果 |
|----|-----------|------|
| 小程序静态+单测 | `pnpm type-check && pnpm test:unit` | type-check exit 0；**83 文件 / 898 用例全绿** |
| 小程序构建 | `pnpm build:mp-weixin` | DONE Build complete；跟踪生成物（`pages.json` 等）无 diff |
| H5 真机（暖橙） | 8888 dev server + 本地 boot 9999 真实数据，Playwright 390×844 无头 Chrome | 秒杀：大促头/「正在抢」下划线 Tab/深色分格倒计时/「已抢90% · 仅剩2件」浅粉条/方块「抢」/售罄灰态；折扣：描边活动卡/实心大牌/双列商品卡/折角标/到手价 label/省¥胶囊/右下加购；0 console error、无横向溢出 |
| H5 真机（天空蓝） | 拦截 `mall-theme` 返回真实「天空蓝」`#4D7FFF` 配色 | 两页全部换肤生效（大促头/下划线/角标/CTA/浅色条），无写死促销色 |
| 状态覆盖 | 路由拦截真实响应改造 `remainingStock`（不改 DB/代码） | 进行中「正在抢」、未开始「即将开始」灰 CTA、售罄整卡置灰 +「已抢光」、库存紧张「仅剩 2 件」四态正确 |

截图已归档并替换旧版：[seckill-hall.png](./screenshots/seckill-hall.png)、[discount-hall.png](./screenshots/discount-hall.png)、[天空蓝·秒杀](./screenshots/seckill-hall-blue-theme.png)、[天空蓝·折扣](./screenshots/discount-hall-blue-theme.png)。

### 14.4 与原型仍存的差异（有意保留 / 可一行调整）

1. 场次 Tab 未做原型「浅粉底带 + 选中项白色凸起片」，按本轮拍板描述落成「白色面板 + 主色下划线」。
2. CTA 保留 secondary→primary 渐变（原型接近实心红）；立省角标已改实心。
3. 活动卡保留「进行中」状态标签（原型无）—— 运营需要状态可见。
4. 页面底色沿用 App 全局 `#f5f6f8`（原型是白 canvas），未改全局底色以免影响其它页面。
5. 商品图 `aspectFill` 方图裁切（列表图源比例不一）；商品型活动副行为「N件商品参与」（真实字段），原型文案为「全场商品参与 · 下单立减20%」。
6. 售罄卡 CTA 仍显示灰字「已抢光」（原型是灰「抢」）—— 沿用既有 `ctaText` 语义，未改业务文案。
7. 多活动分组渲染（真实接口分页），原型为单活动示例页。

> 以上 1/2/3/4 若要继续贴近原型，均为单文件样式级改动（Tab 底带、CTA 实心化、隐藏状态标签、页面白底），可按需再调。
