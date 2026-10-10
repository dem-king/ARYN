# 配送工作台多趟次与按顺序装车送货

> 需求日期：2026-10-10
> 范围：后端（order-biz / order-api）+ C 端配送员工作台（uniapp `pages/delivery/*`）+ 双模式增量 SQL
> 目标：一个司机一辆车一张出车单，把当天所有订单按自己的顺序装车、按顺序送。

---

## 零、第二轮：出车单粒度重构（本文件的核心）

第一轮按「趟次」组织工作台并加了排序，但用户实测仍报两个问题：
**「两个配送中的订单只显示一个」** 和 **「没法调整送货顺序」**。

根因是**建模错位**：`delivery_trip`（出车单）由「派单动作」创建 —— 每派一批新建一张。
所以同一司机的货被拆到多张单上，每张单各自配货、各自出发；排序的作用域是「一趟车」，
跨单的两个订单天然没法排。

**用户给的口径**：*「一个司机一辆车可以送多个订单，订单派给他，他把这些货按顺序配好装车，
然后去送货」* —— 出车单应当是「司机的一次出行」，不是「一次派单批次」。

### 重构后的模型（B）

| 项 | 旧 | 新 |
|----|----|----|
| 出车单边界 | 一次派单一批 | **一个司机一次出行一张** |
| 派单并入范围 | 待配货/配货中 | **待配货/配货中/配送中**（车开出也能加货） |
| 任务落点 | 待取货 / 配货中 | 按趟次进度三态：待配货→待取货、配货中→配货中、**配送中→待送达** |
| 并入已出发趟次的订单 | — | **立即转「待收货」**（货已在车上，与「出发即待收货」同口径） |
| 出发 | 一次性（2→3） | **可重复调用**：首次改趟次状态，之后只带新并入的单上路 |
| 加单可拉性 | 只允许未出车 | 允许在途（含配送中）；**只有已收车**才拒绝 |

**关键发现：数据库层面从来允许「一司机多张在途单」**（`delivery_trip` 只有
`uk_delivery_trip_no` 唯一键，`staff_id` 是普通索引），所以**本次重构零 DDL 变更**——
「一司机一车」纯粹是应用逻辑（`resolveAssignTrip` / `getActiveTrip`）。

### 订单状态联动的风险点（最高风险）

`depart` 会调 `OrderDeliveryStateService.markShippedOnPickUp`，把**订单**推到「待收货」
并触发微信发货上报。配送中并入的订单必须立即触发同一联动，靠「订单已是待收货即幂等返回」
防重复上报（该幂等逻辑原本就有）。已实测：拉单后订单 `status` 2→3、`deliver_time` 写入。

### 顺带修掉的既有 bug

`cancel` / `close` / `returnConfirm` 三条任务关闭路径原先只调 `completeIfAllTasksSettled`，
**没有重算 `delivery_trip.task_count`** —— 管理端出车单列表的「订单数」在订单取消后就是错的
（非本次引入）。已抽出 `settleTripAfterTaskClosed`（先重算、再判收车）统一处置。

---

## 一、需求来源

司机端反馈：**「如果有很多订单派过来，这个工作台只能看到一个」**——今天要送 5 家货 5 个订单，
需要先把这 5 单的货都配好装车，再按顺序送；而工作台只显示一张出车单，只能在两个快捷入口里
来回点，配货时看不到全部任务。同时要求**支持司机手动排序，先送哪个后送哪个**。

## 二、根因（两个叠加的结构问题）

### 1. 每次派单都新建一支出车单，工作台却只取最新一趟

- `DeliveryTaskServiceImpl.assignTasks` 原实现无条件 `new DeliveryTrip()`，
  每次派单（含管理端「按订单派单」`assignByOrderId`，它内部就是单任务批量派单）都新建一趟车。
- `DeliveryTripServiceImpl.getActiveTrip` 用 `.last("LIMIT 1")` 只取最新一趟。
- 结果：管理端分 5 次把 5 个订单派给同一司机 → 5 张出车单 → 工作台只显示第 5 趟，
  **前 4 单在首页等于消失**（能在「我的任务」列表里看到，但主页完全不可见）。
-
  即截图症状：顶部「待处理任务 2」，下面「当前出车单」只有一张，总单数 0/1。

### 2. 配货清单按订单分组，货要跑 5 遍

`trip-detail` 的配货视图是「订单 → 分类 → 商品」三层，同一件货出现在 5 个订单里就列 5 行、
要跑 5 遍货架。司机的真实动作是「这趟车一共拿多少货」，而不是「每个订单各拿多少」。

## 三、口径定案

- **同一趟车 = 司机一次装车出发要送的全部订单**。司机尚未出车（出车单状态 1 待配货 / 2 配货中）
  时，新派的任务**并入同一趟**；车一旦开出（3 配送中）就不再并单——货不在车上。
- **并入已开始配货的趟次时，任务直接落「配货中」**：`depart` 只把「配货中」的任务推进到
  「待送达」，留在「待取货」的新单会永远送不出去（`depart` 的状态筛选是硬编码的）。
- **改派要连带换趟次**：原实现只改 `staffId` 而把 `tripId` 留在原司机车上，这单在新司机的
  工作台不会出现，原司机车上又多一单。改派后两趟的单数都要重算，原趟次做一次收车判定。
- **工作台 = 全部在途趟次**（状态 1/2/3），不是最新一趟。每趟卡里直接列出这趟的站点顺序。
- **配货清单 = 整趟车按「分类 + 商品 + 规格」合并**的合计（分类≈供应商批次，按批次走仓库），
  逐行确认取货时一次改掉该货在整趟车里的全部明细。
- **送货顺序 = 司机的路线**，整趟任务的一个排列；提交必须是**完整排列**（部分列表会被拒绝，
  未提交的任务会留在原序号上造成路线静默错乱）。
- **「今日已完成」按送达时间当天判定**：原实现把 `status=5/6` 的总数当今日完成，
  实际统计的是历史累计。

## 四、接口与数据变更

### 新增接口

| 方法 | 路径（cloud 段） | 说明 |
|------|------------------|------|
| GET | `/mall-order/app/delivery/trip/workbench` | 工作台首页数据：`pendingTaskCount` + `todayDoneCount` + `trips[]`（每趟含 `taskList[]` 站点摘要） |
| POST | `/mall-order/app/delivery/trip/{id}/items/batch-pick` | 批量确认/取消取货，body `{ itemIds, picked }` |
| GET | `/mall-order/app/delivery/trip/{id}/pull-candidates` | 可拉进本趟的候选订单；`source`=MINE 我的未完成 / UNASSIGNED 未派送 / 空=合并，`keyword` 模糊匹配 |
| POST | `/mall-order/app/delivery/trip/{id}/pull-orders` | 把订单拉进本趟，body `{ orderIds }`，返回实际拉入数 |

四个接口都在 `AppDeliveryTripController`（路径首段 `/mall-order` 为微服务域，boot 模式由
`contextPath: /boot` 加前缀，前端走 `rewriteBootUrl` 改写，**无硬编码 `/boot`**）。

### 变更接口（行为，非签名）

- `POST /app/delivery/trip/{id}/sort`：新增**完整排列校验**，提交的 taskIds 必须等于该趟
  全部任务 ID 的集合，否则抛「送货顺序与当前趟次不一致，请刷新后重试」。
- `assignTasks` / `assignByOrderId` / `reassign`：派单目标趟次改为 `resolveAssignTrip`
  （并入该司机在途趟次，含配送中；没有才新建）；任务落点由 `planTaskAttach` 按趟次进度三态决定；
  并入已出发趟次后调 `markAttachedTasksShipped` 把订单转待收货。
- `POST /app/delivery/trip/{id}/depart`：改为**可重复调用**。首次从 2→3 记 `departTime`
  并按整趟校验取货；已出发时不再改趟次状态，只把新并入的「配货中」任务推入「待送达」
  （逐个校验其明细，用新增的 `allPickedByTask`）。
- `delivery_task` 关闭类路径（`cancel` / `cancelByOrderId` / `close` / `returnConfirm`）：
  统一改走 `settleTripAfterTaskClosed`（先重算 `task_count` 再判收车），修掉既有计数漂移。

### 加单（拉单并入）口径

配货页三个入口在数据上收敛为**同一个动作：让订单的配送任务归属本趟车**。

| 入口 | 候选来源 | 口径 |
|------|----------|------|
| 今日已有未完成任务 | `MINE` | 已派给当前司机（`staff_id`）、状态在待取货/配货中/待送达，且不在本趟的订单；含挂在别的趟次上的 |
| 未派送订单 | `UNASSIGNED` | 本租户 `order_info.status=2待发货` + `delivery_way in (3,4)`；已有任务的 **排除别人已接走的**（司机自助拉单不是抢单） |
| 临时新增 | 空（两类合并） | 客户临时加单/管理端补单后，从同一面板把它拉上车 |

> **「未派送」是配送语义，不是付款语义**：候选条件**不能**加 `pay_status=1`。
> 货到付款单的 `pay_status` 恒为 0（钱在送达时才收），而那恰恰是司机必须上门的一类单；
> 实测库里的待发货单 2/2 全是 COD，加了付款过滤会让「未派送订单」整个空掉。
> 未付款的预付单本来就进不到「待发货」状态，所以按状态筛已经足够。
> 前端对 `paymentType=3` 的单标「货到付款」，提醒司机送达时收款。

- 已在本趟的订单不出现在候选里（司机看的是「还能拉什么」）。
- 订单**没有配送任务**时（历史单可能缺任务）按订单快照补建任务与取货明细后再拉入。
- 订单已有任务时**改属本趟**：状态对齐到本趟进度（见 `planTaskAttach` 三态），
  并从原趟次移出（重算原趟次单数 + 判收车）。
- **只有已收车（status=4）的趟次拒绝加单**；在途（含配送中）都可加。
- 已完成/已取消的订单不允许重新上车。
- 商品、收货人、地址全部来自订单本身，**司机不手填任何字段**；不动表结构
  （`delivery_task.order_id` 与 `delivery_task_item.order_item_id` 仍是 NOT NULL，
  这也是「临时新增」必须依托真实订单的原因）。

### 新增 VO / DTO（均在 `aryn-order-api`，无需 Dubbo 序列化）

- `DeliveryWorkbenchVO`、`DeliveryTripBriefVO`、`DeliveryTripTaskBriefVO`
- `DeliveryCandidateOrderVO`、`DeliveryCandidateItemVO`
- `DeliveryBatchPickDTO`、`DeliveryPullOrdersDTO`

### 数据库

**无结构变更**：`delivery_task.sort_no`、`delivery_trip.task_count` 均早已存在，
本次只改读写口径，代码层重构不需要 DDL。

**新增一份存量归并脚本**（双模式各一份，可重复执行）：

| 模式 | 文件 |
|------|------|
| boot | `db/boot/119delivery_trip_merge_incremental.sql` |
| cloud | `db/cloud/120delivery_trip_merge_incremental.sql` |

把同一租户+同一司机的多张在途出车单（状态 1/2/3）归并到创建时间最早的那张：
其余趟次的订单改属目标单并重排 `sort_no`（已出发的订单排前面，再按原趟次与 `sort_no`），
被拉空的趟次置 `4已完成` 并补 `complete_time`，目标趟次回写实际 `task_count`。
只 `UPDATE trip_id / sort_no`，不动任务状态、金额、收货信息；脚本开头有预览 SELECT 便于核对。
已更新 `db/boot/build-full-sql.mjs` 的 `sections` 并重新生成 `aryn_boot_full.sql`（`verify-full-sql.mjs` 通过）。

> `delivery_task` 上的 `UNIQUE KEY uk_delivery_task_order(tenant_id, order_id)` 保证一单一任务，
> 并单不会出现同一订单两条任务；归并脚本也只改 `trip_id`，不新增任务行。
> 数据库**没有**「一司机一在途单」的唯一约束（也不加：历史多单需要平滑归并）。

## 五、前端交互

### 工作台 `pages/delivery/index.vue`

- 顶部统计保留（待处理/今日已完成），新增一行「待送 N 单 / 待取 N 件」。
- 主体改为**趟次卡片列表**：每趟一张卡，头部是状态 + 单数件数 + 该干什么
  （没开始配货 / 还差 N 件 / 还剩 N 单 / 已收车）。正常只有一张（一车一张单），
  标签显示「当前出车单」；历史遗留多张时才显示「第 N 趟」并额外给一条橙色提示，
  引导司机进详情用「加单」拉合。
- 卡内直接列出**送货顺序**（序号 + 收货人 + 目的地 + 电话/送达时间），底部一个「配货 / 出发」
  （配送中显示「继续送货」）按钮直接进详情。

### 出车单详情 `pages/delivery/trip-detail.vue`

- **配货视图**：合并后的汇总行，按分类分节；一行显示「共 N 件（M 个订单）」，
  点一下整行确认取货（走批量接口），部分确认时显示 `已确认/总数`。
- **加单区**（3 个入口：已有任务 / 未派送订单 / 临时新增），**配货视图与送货视图都有**：
  送货视图（配送中）里叫「再加一单」，因为「车开出去了才临时加货」才是这条路径的主场景。
  点开是同一个选单面板（多选 + 搜索），确认后一次性拉进本趟；面板里显示每单的
  收货人、地址、件数、原挂靠趟次，货到付款单带「货到付款」标记（司机送达时要收款）。
- **送货视图**：新增「调整顺序」面板，按住右侧手柄拖动，松手即保存；保留上移/下移按钮作为
  精确兜底；失败回滚本地顺序，避免界面与库里的路线不一致。
  **排序作用域 = 这张单的全部未完成订单**，所以出车单合并后跨订单排序自然可用。
- 排序拖拽用 `touchstart/touchmove/touchend` + 固定行高换算落点（`utils/delivery-pick.ts` 的
  `resolveTargetIndex` / `sortRowOffset`），不用 `movable-view`（wot-design-uni 无排序组件，
  仓内也无先例）。
- 前端已删除 `getActiveTrip` 封装（无调用方）；后端 `/trip/active` 保留，供未升级的旧版本小程序。

### 新增纯函数模块

`src/utils/delivery-pick.ts`：`buildPickRows`（合并）、`groupPickRows`（按分类分组）、
`isPickRowDone`、`applyRouteOrder`（按顺序排列，缺项追加末尾）、`moveItem`、
`resolveTargetIndex`、`sortRowOffset`。全部有单测（18 例）。

## 六、验收命令与实际结果

```
# 后端：workbench / 并单 / 排列校验 / 批量取货 / 拉单 / 重复出发
mvn test -pl aryn-order/aryn-order-biz -am -Dtest='DeliveryTaskServiceImplTest,DeliveryTripServiceImplTest,DeliveryTaskItemServiceImplTest,OrderInfoDeliverAssignTest,ArynOrderPayDeliveryIdempotencyTest'
→ 全绿（DeliveryTaskServiceImplTest 35 例、DeliveryTripServiceImplTest 15 例）

# 后端：全模块（order-biz）
mvn test -pl aryn-order/aryn-order-biz -am
→ 381 例全绿

# 后端：单体模式全量
mvn test -pl aryn-boot -am
→ BUILD SUCCESS

# C 端
npx vue-tsc --noEmit              → 通过
npx vitest run --config vitest.config.ts → 61 文件 / 629 例全绿
npx eslint src/pages/delivery/index.vue src/pages/delivery/trip-detail.vue src/api/delivery.ts → 干净

# 全量 SQL
node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs → 通过（20785 行）
```

### boot 线上实测（clean 重打包部署，jar md5 双端一致 + health UP）

1. **存量归并脚本**：跑出「1 个司机 2 张在途单（TR...857b / TR...2051）」，执行后合并为一张
   （目标单 `task_count=2`，另一张置 4已完成/`task_count=0`，两单 `sort_no` 1/2），
   二次执行无变更（幂等）。
2. **跨订单排序**（原截图做不到的动作）：`PUT /trip/{id}/sort` 提交两站互换 → `data:true`，
   库中 `sort_no` 已对调；随后还原原顺序。
3. **配送中加单**：`pull-candidates` 返回 2 个未派送单；`pull-orders` 拉入一单 →
   任务落「待送达」(4)、`sort_no` 追加为 3，**订单同步转「待收货」(3) 且写入 `deliver_time`**。
4. 工作台 `/workbench` 返回 **1 张在途单 / 2 单 14 件 / 两站按 sort_no 排列**（修复前只显示一站）。
5. 测试期间改动的数据（任务归属/状态、订单状态、排序、日志行）已全部还原。

契约守卫：
- `src/pages/delivery/delivery-workbench-contract.test.ts`（10 例，新）：
  派单并单、工作台返回全部在途趟次、今日完成按送达时间、前端用聚合接口、
  配货合并 + 批量确认、排序排列校验 + 失败回滚、三个加单入口走拉单接口、
  后端拉单的趟次/抢单守卫、双模式路径。其中「不抢别人已接的单」一条
  已人为移除守卫验证过会失败。
- `src/pages/delivery/delivery-arrive-contract.test.ts`：原有 10 例保持通过
  （凭证校验先于写状态那条断言收窄到 `markArrived` 方法体内——文件里其他地方
  也会提到「已送达」，按全文首个匹配会误判）。

## 七、剩余风险

- **已开出的车不并单，但可以加单**：派单时车已开出会新建下一趟；配货页的「加单」只对
  「待配货/配货中」开放。司机配送中若想临时加单，需要先回到待配货状态（当前不支持）——
  这是刻意的，避免把不在车上的货塞进已在路上的趟次。
- **并单后司机看不到「这单是新加进来的」**：新任务排在路线末尾，司机若已配齐可能漏掉。
  当前靠工作台的「还差 N 件」提示兜底，后续可加新单高亮/角标。
- **拖拽排序未在真机验证**：仅 type-check + 单测覆盖落点换算，`touchmove` 手感需真机确认；
  已保留上移/下移按钮兜底。
- **工作台无「趟次」概念的空态提示**：无在途趟次时只提示「暂无进行中的出车单」，
  未区分「今天没活」与「活都在别的状态」。
- **未派送候选无分页**：按租户一次返回全部待发货订单，订单量大的租户需要加分页或
  按配送区域/时间窗收敛；当前有搜索框兜底。
- **「未派送」候选不区分区域**：同租户不同配送员看到的是同一批未派送单，可能出现
  两个司机同时想拉同一单（后端已保证不会重复接走，但提示语是「已被别人接走」）。
  后续可按配送区域/港口过滤候选。
- **未派送查询的防回归手段是静态断言**：Mapper 是 mock，单测无法真的执行 SQL 过滤，
  所以「查询条件里不得含 pay_status」这条用读源码断言钉住（已人为验证会失败）。
  真正要验证过滤语义需集成测试或线上实测。
- **归并脚本只处理「同租户同司机的在途多单」**：不跨司机、不跨租户、不碰状态 4 的趟次。
  若生产存在「同一司机一出发一未出发」的混合情况，脚本会一并归并到最早那张
  （口径上取「同为在途即视为同一辆车」）；如果业务上认为未出发的货不该并进已开出的车，
  需按 `status` 再收窄条件。
- **没有加数据库唯一约束**：`delivery_trip` 未加 `(tenant_id, staff_id, status)` 唯一键，
  因为历史多单需要平滑归并、且并发派单下加约束会把正常请求变成报错。改由应用层
  `resolveAssignTrip` 保证「取最新一张在途单」，并发极端场景可能短暂出现两张
  （下次派单会并入最新那张，归并脚本可兜底）。
- **重复出发的取货校验按「新并入的单」而非整趟**：已出发趟次再点出发时，只校验
  仍处于「配货中」的任务；早已在路上的单不再重复校验。这是刻意的（否则一单未取货会
  把「捎带一单」这个动作整体阻断）。
- **cloud 模式未部署**：本次新增 `db/cloud/120delivery_trip_merge_incremental.sql`，
  cloud 侧需重新构建 order 服务并执行该脚本。
