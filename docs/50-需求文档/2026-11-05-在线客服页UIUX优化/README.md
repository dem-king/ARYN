# 在线客服页 UI/UX 优化（现代 IM 会话结构）

> 日期：2026-11-05 ｜ 状态：**已实施；独立审查 + H5 视觉/行为验收通过（159/159），待微信真机目检**
> 分支：`ship-chandler` ｜ 工作目录：仓库根
> 原始请求（用户原文）：「这个页面UI/ux优化一下」（附在线客服页 390×844 真机截图）
> 页面：`aryn-mall-uniapp/src/sub-pages/message/chat/index.vue`（699 → 1323 行）
> 设计规格：[artifacts/chat-ux-2026-11-05/DESIGN.md](../../artifacts/chat-ux-2026-11-05/DESIGN.md)
> 视觉/行为证据：[artifacts/chat-ux-2026-11-05/](../../artifacts/chat-ux-2026-11-05/)（capture.mjs / screenshots / metrics.json / REPORT.md）

## 一、背景与现状诊断

用户截图里的表现（与源码逐条对应）：

| # | 问题 | 源码/截图证据 |
|---|------|--------------|
| 1 | 标题栏右侧「结束会话」胶囊紧贴居中标题（`在线客服结束会话` 视觉粘连），与微信胶囊争同一区域 | 旧 `hr-navbar #right` + `.chat-navbar-action` |
| 2 | 会话状态条 20rpx（≈10px）、`--app-text-3` 低对比；右侧「客服：昵称」与标题栏信息重复 | 旧 `.chat-status` |
| 3 | 时间戳嵌在每条气泡内（低透明度白字压品牌色）；同一分钟多条各挂一个时间，噪音大 | 旧 `ChatBubble .message-state` |
| 4 | 无头像、无消息分组；商品卡是「橙色大色块 + 白色内卡 + 类型条」的套娃，无明确 CTA | 旧 `BusinessCard` |
| 5 | 输入区：图片入口 42rpx（≈21px）、提示 20rpx、输入框带下划线像表单、发送按钮 130×72rpx（65×36px）均低于 44pt；空输入仍能点发送 | 旧 `.chat-composer` |
| 6 | 加载态只有一行文字；无空态/排队态/回到最新，无失败重试的明确视觉 | 旧 `.loading-copy` |
| 7 | 白字压品牌橙（dev 主题 `#ff5500`）对比度仅 3.16:1；深色模式靠变量但也缺少层级 | 旧气泡/发送按钮 |

## 二、目标与非目标

**目标**：把「客服会话」做得像现代 IM——打开先看清「对方是谁、是否在线、怎么结束」；消息有分组和时间分隔；输入区一眼可用；空/排队/加载/关闭都有明确状态；主流程（发送、重试、上传、结束、重开）行为一个不变。

**非目标（明确不做）**：
- 零后端、零接口、零 DB、零路由、零权限变更（不改 Controller/DTO/SQL）。
- 不新增产品能力：不加表情/语音/快捷话术/客服正在输入（后者需要后端 typing 事件）。
- 不重做消息中心列表页（仅复用其主题与时间格式口径）。
- 不引入新 UI 库/新图标体系（继续 wot-design-uni + `--app-*` 语义变量）。

## 三、信息架构（新）

```
hr-navbar（仅返回 + 居中「在线客服」）
chat-session        会话头：头像 + 昵称 + 状态行（在线/排队/关闭/连接中） + 「结束会话」
chat-timeline-wrap  position:relative
 └ chat-timeline    scroll-view
     ├ 加载更早消息（胶囊，带 loading 文案）
     ├ 骨架屏（仅首屏无消息）｜空态/排队态（无消息时）
     ├ 时间分隔（首条 / ≥5min / 跨天）
     ├ 气泡列表（客服组首头像 + 白卡；我方品牌气泡；系统居中胶囊；业务卡片）
     └ …
 └ chat-jump        回到最新消息（上滑离开底部出现；新消息到达带品牌色小圆点）
chat-composer      图片按钮 88rpx + 胶囊输入框 + 发送按钮 128×88rpx（空内容禁用）
chat-closed        CLOSED 只读卡：锁图标 + 「会话已关闭」+ 重开时限 + 「重新咨询」
```

- 会话头固定不随消息滚动；消息区域独立滚动；输入区固定在底部并让出 safe-area。
- 信息层级：身份/状态（会话头）→ 上下文（时间/分组）→ 内容（气泡/卡片）→ 行动（输入区/结束/重开）。

## 四、关键设计决策

1. **结束会话从标题栏移到会话头**：标题栏只留返回 + 居中标题，解决胶囊贴字与争空间；结束入口是 88rpx 命中区的纯文字按钮（中性 `--app-text-2`，对比度 5.74；低频、克制、防误触）。排队中也允许「结束会话」（取消排队），语义沿用 `canEndConversation`。
2. **时间从气泡里拿出来做分隔**：新增纯函数 `buildChatTimeline(messages, { formatTime })`（`src/utils/message.ts`）——首条必插、相邻 ≥5 分钟或跨自然日插、同发送方同段分组；时间文案复用 `formatNoticeTime`（今天 HH:mm / 昨天 HH:mm / MM-DD HH:mm）。脏时间不渲染、不插分隔；系统消息自成一组。**同一分钟连发多条只出现 1 个时间**（截图里旧版 4 条消息挂 4 个时间）。
3. **气泡体系**：客服消息 72rpx 头像列 + 白卡气泡（组内用等宽占位保持左对齐）；我方品牌色气泡 + 黑色渐变遮罩（`rgba(0,0,0,.20)→.30`）把白字对比度从 3.16 提到 **4.78**；系统消息居中胶囊；发送中/失败状态行移到气泡下方（转圈 / warning 图标 + 文案），失败点击复用原 `clientMessageId` 重发（服务端幂等）。气泡内不再放时间戳。
4. **业务卡片去套娃**：`BusinessCard` 改为「112rpx 媒体块（图或类型图标）+ 类型小胶囊 + 两行标题 + 一行摘要 + 箭头」的单层白卡；props 不变，站内信详情页复用无回归。
5. **输入区现代 IM**：图片按钮 88rpx 圆形（aria-label「发送图片」）+ 胶囊输入框（去 wot 默认下划线与内边距，auto-height，`confirm-type=send` 键盘发送）+ 发送按钮 128×88rpx 品牌渐变；空内容 `disabled` + 降透明度（显式覆盖各端原生 `button[disabled]` 灰底，实测仍是品牌色 + .45 透明度）。
6. **状态全覆盖**：骨架屏（仅 `loading && !messages.length`，不与真实气泡同屏）→ 空态（品牌图标 + 「有什么可以帮您？」+ 能力提示）→ 排队态（会话头「正在为你接入客服」+ 橙点 + 排队文案）→ 关闭态（只读卡 + 24h 重开时限 + 重新咨询）。加载中会话头显示「正在连接客服…」而非提前显示「在线」。
7. **滚动体验**：自己发消息/首屏/点击回底一定滚到底；WS 推送补拉时若用户已上滑（距底 >64px）**不强行拉底**，显示「回到最新消息」并带新消息小圆点；`loadOlder` 有并发锁与「加载中…」。
8. **可访问性/主题/兼容**：品牌色统一三层 fallback `var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237))`；中性色走 `--app-*`，深色模式自动跟随（无死白块）；可读次要文字（状态行/时间分隔/空态）用 `--app-text-2` 保证 ≥4.5:1；所有主操作 ≥88rpx 命中区；`aria-role/aria-label` 覆盖结束、图片、回底、失败重试、业务卡片；动画只动 transform/opacity 并尊重 `prefers-reduced-motion`；不使用 `color-mix/:has/aspect-ratio`。
9. **i18n**：中英成对新增 8 个 key（`onlineNow / agentFallbackName / queueTitle / emptyTitle / emptyHint / loadingEarlier / jumpToLatest / sendImageAria`），不删既有 key。

## 五、改动文件

| 文件 | 说明 |
|------|------|
| `aryn-mall-uniapp/src/sub-pages/message/chat/index.vue` | 页面重写：会话头/时间线/骨架/空态/回底/输入区/关闭卡；新增 `onTimelineScroll`、`measureTimeline`、`sessionName/sessionStatus/timeline/canSubmitDraft` 等派生；业务逻辑与生命周期原样保留 |
| `aryn-mall-uniapp/src/components/message/ChatBubble.vue` | 气泡重写：分组头像列、品牌遮罩气泡、发送状态行、系统胶囊、业务卡片宿主；新增 props `groupStart/staffAvatar/staffName` 与 `retry` emit |
| `aryn-mall-uniapp/src/components/message/BusinessCard.vue` | 业务卡片重设计（媒体块/类型胶囊/两行标题/摘要/箭头）；props 与路由优先级不变 |
| `aryn-mall-uniapp/src/utils/message.ts` | 新增 `buildChatTimeline`、`formatMessageLocalDateTime`（乐观消息本地时间格式，修 ISO 时区差） |
| `aryn-mall-uniapp/src/utils/message.test.ts` | 新增 10 条单测（首条/5 分钟阈值/跨天/分组/系统消息/脏时间/空会话/稳定 key/本地时间格式） |
| `aryn-mall-uniapp/src/locales/modules/message.zh-CN.ts`、`message.en-US.ts` | 新增 8 个中英词条 |
| `artifacts/chat-ux-2026-11-05/`（新增） | 设计规格 DESIGN.md、可复跑验收脚本 capture.mjs、截图、metrics.json、REPORT.md |

统计：7 个源码文件，+1578 / −291 行；无后端/DB/路由/权限文件。

## 六、验证

| 项 | 方式 | 结果 |
|----|------|------|
| 类型检查 | `pnpm type-check`（vue-tsc） | exit 0 |
| 单元测试 | `pnpm test:unit` | 153 文件 / **1736 用例全绿**（本次新增 10 条时间线/本地时间单测） |
| ESLint | 7 个改动文件 | 0 error（仅 `refreshConversation` 基线已有的 jsdoc @param warning） |
| 契约测试 | `require-login-contract.test.ts` + locales 全量 | 全绿（登录守卫字面量、中英 key 对齐、编译语法） |
| H5 生产构建 | `pnpm build:h5` | exit 0 |
| 微信小程序构建 | `pnpm build:mp-weixin` | **exit 0**（DONE Build complete，产物 `dist/build/mp-weixin`；仅有两条既有 unocss 图标告警） |
| H5 视觉/行为验收 | `node artifacts/chat-ux-2026-11-05/capture.mjs --strict`（Playwright + 全量 mock API/WS，390×844@2x） | **191/191 断言通过，0 失败**（最终收口复验 bundle `index-CF-iJXKk.js` / 22:07）；console error 0；无横向溢出（390/320）；WS 全部 `open` |
| 触控尺寸（390） | 实测 | 结束 64×46、图片 46×46、发送 67×46、重新咨询 112×46、回底 48×48 —— 全 ≥44 |
| 对比度（WCAG，最不利像素） | 实测 | 我方气泡 4.78、客服气泡 12.63、会话头昵称 12.63、状态行 5.74、时间分隔 5.36、空态 5.36、关闭提示/重开按钮 4.78、可用态发送 4.78；深色 4.78–15.39 |
| 时间/分组 | DOM 断言 | 首屏 3 条时间分隔（首条 + ≥5min×2），同分钟连发只 1 条；加载更早后 +1 条「昨天 HH:mm」；9 条消息只渲染 3 个组首头像 |
| 行为流 | Playwright 端到端 | 16/16：空内容 disabled→输入 enabled→发送 POST→气泡+1；失败→重试复用同一 `clientMessageId`；结束→只读卡；重新咨询→恢复可写；图片选择/上传/发 IMAGE；加载更早；上滑回底；WS 推送补拉不强制拉底 + 小圆点 |
| 深色模式 | H5 真代码路径（`__uniConfig.darkmode` → `.wot-theme-dark`） | 页面底 #131313 / 卡片 #1b1b1b，无死白块；关键文字 4.78–15.39 |
| 独立只读审查 | qa-reviewer（type-check/全量单测/eslint + 逐文件 diff 审查） | 无 P0；1 个 P1（回底按钮 72rpx<44pt）+ 13 个 P2 已全部修复/采纳，结论「可合并」 |
| 验收回修 | visual-verifier 三轮复验 | P2（无坐席头像兜底方形）、P3-1（加载态误报在线）、P3-2（结束会话对比度 3.21→5.74）已修并复验；系统消息胶囊浅色不可见、骨架屏占位对比度低两处收口修复；三轮共修复 5 项，最终剩余 P0/P1/P2 = 0 |

证据与过程：`artifacts/chat-ux-2026-11-05/REPORT.md`（接口 mock 清单、storage seed、WS mock、逐场景断言）、`screenshots/`（before/after 各状态）。

## 六点五、最终收口（2026-11-05 22:08）

- 视觉验收两轮回修：P2 无坐席头像兜底由方形改圆形；加载态会话头由「在线 · 消息实时同步」改「正在连接客服…」+ 灰点；结束会话文字由品牌橙改 `--app-text-2`（对比度 3.21→5.74）。
- 收口自查再修两处：系统消息胶囊在浅色下与页面底同色（card-2 ↔ bg）几乎不可见，改白卡 + 1rpx 描边 + `--app-text-2`（对比度 5.74）；骨架屏占位色 `--app-skeleton` 与页面底过近，改 `--app-skeleton-band`（#e9ebef）。
- 最终复验：H5 生产构建 exit 0（bundle `index-CF-iJXKk.js`，22:07:29）；`capture.mjs --strict` **191/191 通过**；`pnpm build:mp-weixin` exit 0；`pnpm type-check` exit 0；`pnpm test:unit` 153 文件 / 1736 用例全绿；7 改动文件 eslint 0 error（仅基线已有 jsdoc warning）。剩余 P0/P1/P2 = 0。

## 七、边界与遗留

1. **未真机目检**：本机 h5 验收通过；微信 iOS/Android 真机需确认 ① wd-textarea 深度样式（去下划线/胶囊内边距）② `uni.createSelectorQuery()` 回底判定（H5 验证过，MP 未 `.in()` 组件作用域）③ 骨架 pulse/气泡入场动画 ④ `env(safe-area-inset-bottom)` 与键盘弹起时输入区位置 ⑤ 深色模式（小程序 darkmode）。
2. **H5 深色验收手段**：H5 生产产物默认 `darkmode=false`（manifest 只在 mp-weixin 段开启），harness 在页面脚本前劫持 `window.__uniConfig.darkmode=true` 走真实 `getTheme → matchMedia → mallThemeStore → .wot-theme-dark` 路径；不代表 H5 默认开启深色。
3. **加载更早消息不保持滚动位置**：prepend 历史消息后视口会随内容增长偏移（旧实现同样）；如需优化，需在插入前后记录 `scrollHeight` 差值回写 `scroll-top`。
4. **无「客服正在输入」/表情/语音/快捷问句**：需要后端 typing 事件或产品定义，本次未做。
5. **资产限制**：客服头像/昵称来自会话详情快照（`assignedStaffAvatar/Name`），排队阶段为空时用品牌色 service 图标兜底，不伪造坐席。
6. **rpx 机制**：320 宽下 88rpx ≈ 37.5px（等比缩放固有行为），DESIGN 只要求 320 不横向溢出；390/375 主流宽度 88rpx=44-45px。
7. **回滚**：纯展示层，回滚本节 §五 7 个源码文件即可；`artifacts/chat-ux-2026-11-05/` 为新增验收产物，可保留复跑。
