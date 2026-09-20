# 悦航购船供化改造 · 交互合理性与竞品对标调研报告

> 日期：2026-09-19
> 调研对象：悦航购（Aetheryn Mall）船供化改造一期~三期已落地实现
> 方法：**以实际代码为唯一事实来源**（逐文件核对前端页面、API 层、后端 Controller/Service/Mapper/SQL），叠加国内外同类平台竞品对标
> 结论强度：本报告所有「现状」结论均标注文件与行号，可逐条复核

---

## 0. 摘要

船供化改造在**领域建模、后端能力、双模式适配、权限与 SQL 治理**上完成度很高：三端角色矩阵清晰，`purchase_scene / sale_scope / delivery_way=4 / vessel_call` 四个核心概念自洽，后端共享购物车、履约波次、营销引擎、ETA 变更通知均有完整实现与单测覆盖。

但**交互层与业务设计层存在系统性偏差**，可归纳为三条主线：

| 主线 | 本质 | 代表问题 |
|---|---|---|
| **A. 关系前置化** | 把「船舶成员关系」当成进店门槛，而非订单属性 | 新用户被「请联系客户管理员配置」挡住，无任何自助路径 |
| **B. 核心链路未闭环** | 关键能力后端已建成，前端未接通或接通错误 | 共享购物车 C 端零实现；`way=4` 不在配送选项里；搜索实际不可用 |
| **C. 改造未做端到端验收** | 单测/编译/类型检查全绿，但无人走过真实流程 | `way=4` 在三个界面分别显示为「普通快递 / 无需配送 / 等待提货」 |

其中 **A 是与竞品范式的根本性偏离**（详见 §5、§6），也是用户最初提出质疑的那一点；而 **B、C 是可直接修复的工程缺陷**（详见 §4）。

---

## 1. 调研范围与方法

### 1.1 代码范围

| 端 | 路径 | 核对内容 |
|---|---|---|
| 小程序 | `aryn-mall-uniapp/src/` | 全部 `pages/`、`sub-pages/` 页面、`components/ship-workbench`、`api/` 层、`store/shipContextStore` |
| 后端 | `aryn-mall-java/aryn-{vessel,product,order,promotion}/` | Controller、Service、Mapper XML、SQL 脚本 |
| 管理后台 | `aryn-mall-ui/apps/web-ele/src/views/` | 船舶档案、船供目录、营销中心、履约、共享购物车 |
| 文档 | `docs/50-需求文档/2026-09-11-*`、`docs/plans/2026-09-1*` | 方案、功能总说明、试点上线清单 |

### 1.2 竞品范围

ShipServ（Marcura）、Marine Online 海运在线、区域 ship chandler 自建线上目录（Emdad）、船用采购网 ckmarine、京东企业购、震坤行。

### 1.3 方法说明

本报告刻意**不采信文档描述作为事实**。原因见 §4.0：文档与代码已出现多处不一致，且不一致之处恰好是问题集中区。凡文档与代码冲突，一律以代码为准并标注冲突点。

---

## 2. 项目现状还原

### 2.1 改造全景

三期累计落地内容：

- **一期**：领域契约、双模式 SQL、`aryn-vessel` 船舶域、船供商品资料与 Excel 四步导入、订单配送上下文（`delivery_way=4`）、支付幂等、共享购物车、履约波次、Web 工作台、小程序船舶上下文与船供目录
- **二期**：统一营销引擎（阶梯价 / 整船优惠 / 锁定释放）、订单营销快照、常购清单与再购预览、靠港日历、港口配送看板
- **三期**：零成本 ETA 自动提醒、规则冲突检查、购物车防串船、买赠、常购专属价、照片留证

### 2.2 核心领域模型（自洽，设计合理）

| 概念 | 取值 | 层级 |
|---|---|---|
| `purchase_scene` 购买场景 | 1 海员个人 / 2 船供采购 | 订单级快照 |
| `sale_scope` 销售范围 | 1 仅个人 / 2 仅船供 / 3 两者均可 | 商品级 |
| `delivery_way` 配送方式 | 1 普通快递 / 2 上门自提 / 3 商城配送 / **4 公司港口船舶内部配送** | 订单级 |
| `vessel_call` 靠港计划 | ETA / ETD / 泊位 / 配送时间窗 | 履约时间基准 |

强制规则（`船供化改造功能总说明.md` §2）：
- `purchase_scene=2` 必须使用 `delivery_way=4`
- 船供可见商品必须提供 IMPA/ISSA/内部编码之一，且每 SKU 必须有采购单位、MOQ≥1、步长>0，MOQ 为步长整数倍
- 内部配送结算时服务端校验：下单用户必须是该船**在船成员**、靠港未离港、靠港与船舶匹配

**评价**：领域模型是本次改造最扎实的部分。问题不在模型，而在模型与用户可达路径之间的落差。

### 2.3 角色与端矩阵

| 角色 | 端 | 关键交互 |
|---|---|---|
| 运营/商品 | Web | 船供资料维护、Excel 导入、营销活动配置 |
| 调度/管理员 | Web | 船舶档案、**成员绑定**、靠港计划、ETA 修改 |
| 仓库 | Web | 拣货波次、扫码、短装、异常 |
| 海员/采购确认人 | 小程序 | 船舶上下文、个人/船供采购、共享购物车、常购、订单跟踪 |
| 司机 | 小程序 | 今日任务、取货、到港、签收拍照、异常上报 |

---

## 3. 用户旅程还原（按代码实际行为）

### 3.1 新用户首次进入（未登录）

```
打开小程序 → 首页(diy装修页) → 插入 <ShipWorkbench />
  → authStore.isLoggedIn === false
  → visible = true，status = 'guest'
  → 文案：「登录后可使用船供采购和船舶配送」
  → 三个按钮均可见；点「船供采购」→ 跳登录页
```

代码：`components/ship-workbench/index.vue:26-30, 111-113`；`pages/home/index.vue:82`

### 3.2 新用户登录后（无船舶成员关系）

```
onShow → loadWorkbench()
  → isLoggedIn === true → visible = true, status 先置 'unbound'
  → getMyVessels() 返回 []
  → shipContextStore.reset()，status = 'unbound'
  → 文案：「当前账号尚未绑定船舶，请联系客户管理员配置」
  → 「船供采购」按钮仍可点（仅校验登录）→ 可进船供目录、可加购
```

代码：`ship-workbench/index.vue:31-40, 65-72, 114-116`

**关键事实**：此处**不是硬阻断**。用户可进目录、可加购、可结算（结算时因无船舶上下文，`deliveryWay` 回落为 `'1'` 普通快递）。真实伤害是**误导性文案让用户以为自己不能用**。

### 3.3 已绑定用户

```
loadWorkbench()
  → getMyVessels() 返回列表
  → vessel = vessels.find(id === store.vesselId) ?? vessels[0]   ← 自动取第一艘
  → setVesselContext(...)
  → getVesselContext(vessel.id)
      → 有 vesselCallId → status='ready'，显示「下一靠港：港口 泊位（时间 起）」+「已选靠港」绿标
      → 无 vesselCallId → status='unbound' ← 有船无靠港，却提示「尚未绑定船舶」
```

代码：`ship-workbench/index.vue:41-57`

### 3.4 船供采购

```
点「船供采购」→ setPurchaseScene('2') → /sub-pages/product/ship-supply/index?scene=2
  → fetchPage()：GET /product/app/goodsspu/ship/page
      params: { current, size, impaCode: keyword, nameEn: keyword }
  → 列表：名称/英文名/IMPA/编码/箱规/单位/起订/步长/售价 + 数量输入 + 加入购物车
  → 本地校验 MOQ 与步长 → addShoppingCart（自动注入 vesselId/vesselCallId/purchaseScene）
```

代码：`sub-pages/product/ship-supply/index.vue:31-49, 59-84`；`api/order/shoppingCart.ts:12-20`

### 3.5 结算

```
order-confirm initData()
  → hasVesselContext ? deliveryWay='4' + 注入上下文 : deliveryWay='1'
  → 模板：way=4 显示船舶/港口/泊位/时间窗卡片 +「公司司机按靠港计划送达港口/船舶」
  → ShopOrderItem 内提供配送方式 picker（1普通快递/2上门自提/3商城配送）
  → toPay()：way=4 且无上下文 → 提示「请先在首页选择船舶和靠港计划」
```

代码：`sub-pages/order/order-confirm/index.vue:108-114, 216-219, 273-297`

---

## 4. 实证问题清单

### 4.0 前置说明：文档与代码已出现系统性不一致

| 文档要求 | 代码实际 | 出处 |
|---|---|---|
| 「未登录或**无船舶成员关系时不显示工作台**（走普通零售路径）」 | 只要登录就显示，并弹出未绑定警告 | `船供化改造功能总说明.md` §4.1 vs `ship-workbench/index.vue:27,31` |
| 验收点：「结算默认 way=4，页面显示船舶卡片，**不出现普通快递选项**」 | picker 里恰好只有普通快递/自提/商城配送，**没有 way=4** | `试点上线清单.md` §3.1 vs `ShopOrderItem.vue:61-67` |
| 验收链路：「首页工作台**选择船舶**（自动加载下一靠港）」 | 无任何船舶/靠港选择 UI | `试点上线清单.md` §3.1 |
| 验收点：「**共享购物车**：发起人创建 → 成员加购 → 确认人提交 → 整船订单」 | 小程序端零实现 | `试点上线清单.md` §3.2 |
| 验收点：「非船供场景（无船舶成员关系）**不出现内部配送选项**」 | 未绑定用户仍可进入船供目录并加购 | `试点上线清单.md` §4 |

**这组不一致解释了问题为何能存活至今**：`试点上线清单.md` §5 验证记录中，「真机/浏览器端到端三链路验收」一栏状态是 **「待试点执行」**。也就是说，后端单测（约 90 项）、`mvn compile`、`pnpm type-check`、`pnpm test:unit`（小程序 45 项）全部通过，但**没有任何人真实走过一遍用户流程**。下文 P0-1、P0-2 这类「一用就发现」的缺陷，正是这样漏过去的。

---

### P0-1　配送方式选项缺失 `way=4`，内部配送不可逆

**文件**：`aryn-mall-uniapp/src/sub-pages/order/order-confirm/components/ShopOrderItem.vue:59-67`

```js
// 将 deliveryMethod 转换为 wd-select-picker 需要的格式
// deliveryWay: 1普通快递 2上门自提 3商城配送      ← 注释本身就漏了 4
const deliveryMethodColumns = computed(() => {
  return [
    { value: '1', name: '普通快递' },
    { value: '2', name: '上门自提' },
    { value: '3', name: '商城配送' },
  ]
})
```

**后果**（三重）：
1. 内部配送只能靠 `initData()` 的默认值进入，**用户一旦在 picker 里选了任何其他方式，就永远无法切回内部配送**
2. picker 当前值为 `'4'` 时，选项列表中不存在该值 → **显示为空**
3. 直接违反验收标准「不出现普通快递选项」——实际出现的恰恰是普通快递

**这是船供链路的核心缺陷**：`delivery_way=4` 是本次改造的旗舰能力，却在结算页不可选。

---

### P0-2　`way=4` 在三个界面被显示为三种错误文案

| 界面 | 文件:行 | 显示为 | 应为 |
|---|---|---|---|
| 结算页配送方式 | `ShopOrderItem.vue:182` | **普通快递** | 公司港口/船舶内部配送 |
| 订单详情配送方式 | `order-detail/index.vue:387` | **无需配送** | 公司港口/船舶内部配送 |
| 订单详情状态文案 | `order-detail/index.vue:40` | **等待提货** | 司机配送中 / 待送达 |
| 订单详情状态副文案 | `order-detail/index.vue:67` | **商家已备货，等待提货中** | 司机已取货，配送中 |

四处三元表达式均只覆盖 `1/2/3`，`else` 分支承担了 `4`。其中「无需配送」尤其严重——船供订单的核心价值就是**送到船边**，订单详情却告诉用户「无需配送」。

---

### P0-3　船供目录搜索实际不可用（SQL 条件被 AND 而非 OR）

**前端**：`sub-pages/product/ship-supply/index.vue:36-39`
```js
params: {
  current: state.current,
  size: state.size,
  impaCode: keyword.value || undefined,
  nameEn: keyword.value || undefined,     // ← 同一个关键词同时传给两个参数
}
```

**后端**：`aryn-product-biz/src/main/resources/mapper/ShipGoodsProfileMapper.xml:58-68`
```xml
<if test="query.impaCode != null and query.impaCode != ''">
    AND (profile.impa_code LIKE CONCAT('%', #{query.impaCode}, '%')
         OR profile.issa_code LIKE ...
         OR profile.internal_item_code LIKE ...
         OR profile.barcode LIKE ...)
</if>
<if test="query.nameEn != null and query.nameEn != ''">
    AND (profile.name_en LIKE CONCAT('%', #{query.nameEn}, '%')
         OR spu.name LIKE ...
         OR profile.search_aliases LIKE ...)
</if>
```

两个 `<if>` 是**顺序 AND**。因此最终 SQL 语义为：

```
AND (编码组命中) AND (名称组命中)
```

**实测后果**：
- 按 IMPA 码搜索（如 `0301010`，商品名中不含该串）→ 名称组不命中 → **0 条**
- 按中文品名搜索（如「洗衣粉」，编码字段不含该串）→ 编码组不命中 → **0 条**
- 仅当关键词同时出现在编码字段与名称字段时才可能命中

而页面 placeholder 明确承诺：「搜索 IMPA / ISSA / 条码 / 中英文名」。

**同一缺陷复制到了第二个接口**：`AppGoodsSpuController.java:71-72`
```java
query.setNameEn(keyword);
query.setImpaCode(keyword);
```
`GET /product/app/goodsspu/search` 有完全相同的 AND 问题。且该接口**小程序端从未调用**（`api/` 下无引用），属客户端死代码。

---

### P0-4　船供目录无分页，永远只能看到第一页

**文件**：`sub-pages/product/ship-supply/index.vue`

`state` 中定义了 `current / size(20) / total`（第 21-26 行），`fetchPage` 也读取了 `res.total`（第 44 行），但：
- 没有 `onReachBottom` 上拉加载
- 没有「加载更多」按钮
- 模板中**从未使用 `state.total`**
- 没有任何翻页控件

**后果**：船供目录永远只渲染第 1 页 20 条。IMPA 目录动辄上千条 SKU，该页面作为「采购目录」实际不可用。

---

### P0-5　共享购物车：后端完整，C 端零实现

**后端已建成**：
- `aryn-order-biz/.../controller/app/AppSharedCartController.java`（C 端全套接口）
- `ISharedCartService` / `SharedCartServiceImpl`（创建 / 邀请成员 / 增删改明细 / 核定数量 / 提交生成整船订单 / 关闭）
- `SharedCartServiceTest`（12 项单测）
- 管理端只读视图 `SharedCartAdminController` + 菜单 `2110000000000000121`

**小程序端**：`src/api/` 下**没有 `sharedCart.ts`**，`src/pages/` 与 `src/sub-pages/` 下**没有任何共享购物车页面**。

**后果**：船供场景中最有价值的协作能力（多人合并采购、同 SKU 数量自动合并、确认人核定数量、生成一个整船订单）**终端用户完全无法使用**。这是一项「账面上已完成、实际上不可达」的功能。

---

### P0-6　`sale_scope` 在主链路完全未生效，两个列表各错一半

**问题一：仅船供商品泄漏到零售链路**

`GoodsSpuMapper.xml:105-132` 的 `selectApiPage`（C 端通用商品列表）过滤条件为：
```xml
AND goods_spu.`del_flag` = '0'
AND goods_category.`del_flag` = '0'
AND goods_spu.`status` = '1'
```
**没有任何 `sale_scope` 过滤**（该查询甚至未 JOIN `ship_goods_profile`）。

而 `/product/app/goodsspu/page` 是以下入口的共同数据源：
- `api/product/spu.ts:5` → 分类页、商品搜索页、商品列表页
- 首页装修的商品组件
- `getTop10HotSearchGoods`（热销榜，仅按 `status='1'` 过滤）

**后果**：`sale_scope=2`（仅船供采购）的商品会出现在普通零售列表中，个人用户可加购、可下单。销售范围约束形同虚设。

**问题二：下架商品出现在船供目录**

反方向同样出错。`ShipGoodsProfileMapper.xml:40-82` 的 `selectShipSummaryPage` **没有 `status` 过滤**（仅在 `query.status` 被显式传入时才加，而前端从不传）。

**后果**：已下架/未上架商品会出现在船供目录中。

> 两个列表各错一半：零售列表漏了 `sale_scope`，船供目录漏了 `status`。

---

### P0-7　未绑定提示与设计文档矛盾，且无任何自助出路

**文档明确写了不该这么做**：
- `船供化改造功能总说明.md` §4.1：「未登录或**无船舶成员关系时不显示工作台**（走普通零售路径）」
- `船供化改造功能总说明.md` §3.2.1：「成员管理：输入商城用户 ID + 角色绑定……**绑定必须由后台操作，不开放用户自助搜索**」
- `试点上线清单.md` §2.2：「管理端'船舶档案'绑定 2~3 名海员商城用户为船舶成员（**后台邀请授权，不开放自助搜索绑定**）」

**代码实际**：`ship-workbench/index.vue:27,31` 只要登录就 `visible = true`，并渲染 `:115`：
> 「当前账号尚未绑定船舶，请联系客户管理员配置」

**两个问题叠加**：
1. **不该显示时显示了** —— 文档要求隐藏，代码显示
2. **显示了却没有出路** —— 文案要求用户「联系客户管理员」，但新用户根本不存在这样一个角色（没有既有客户关系，哪来的客户管理员？），且系统不提供任何自助绑定入口（后端 `VesselAppController` 仅 3 个 GET，绑定动作 `vessel:member:save` 只存在于管理后台）

**这正是用户最初质疑的点，且代码证实了质疑成立。**

---

### P0-8　状态语义合并：把「有船无靠港」误报为「未绑定船舶」

`ship-workbench/index.vue:36-57` 中，`status = 'unbound'` 有两个来源：

```js
if (!vessels || vessels.length === 0) {
  status.value = 'unbound'          // ① 真的没有成员关系
  return
}
...
if (context?.vesselCallId) { status.value = 'ready' }
else { status.value = 'unbound' }   // ② 有成员关系，但该船无在营靠港计划
```

两种情况共用同一句文案「当前账号尚未绑定船舶，请联系客户管理员配置」。第 ② 种情况下，**用户明明已经绑定了船舶，系统却告诉他没绑定**——这是事实性错误提示，也是最容易演变成客诉的一类缺陷。正确文案应区分：「『{船名}』暂无靠港计划，到港后可下单」。

---

### P1-1　船舶与靠港均无选择入口，但系统处处引导用户去「切换/选择」

**证据（死代码）**：
- `store/shipContextStore.ts:96` `switchVessel()` —— 仅在 `shipContext.test.ts:51,62` 被调用，**无任何页面调用**
- `api/vessel.ts:13` `getVesselCalls()`（可用靠港计划列表）—— **无任何页面调用**
- `getMyVessels()` 仅被 `ship-workbench/index.vue:35` 调用，且结果只用于 `vessels[0]` 自动选中

**但界面反复要求用户执行这些动作**：

| 位置 | 文案 | 用户能否执行 |
|---|---|---|
| `order-confirm/index.vue:218` | 「请先在首页选择船舶和靠港计划」 | ❌ 首页无选择入口 |
| `pages/user/shopping-cart/index.vue:350` | 红字「（结算前请切换船舶）」 | ❌ 无切换入口 |
| `sub-pages/product/ship-supply/index.vue:100` | 「请在首页选择船舶和靠港计划」 | ❌ 首页无选择入口 |

**连带后果**：
- 多船用户（如管理多条船的采购人员）**无法切换船舶**，系统强制使用 `vessels[0]`
- 一艘船有多个靠港计划时**无法选择**，强制使用 `getVesselContext` 返回的「下一靠港」
- 验收标准 §3.1「首页工作台选择船舶」与 §9.6「船 A 加购 → 切船 B → 结算被拦截」**在物理上无法执行**，防串船能力无法验证

---

### P1-2　【本项已撤销 —— 见附录 D 勘误】

初稿曾判定「个人购买不清空船舶上下文」为缺陷。复核 `试点上线清单.md` §3.1 后确认这是**设计要求，不是缺陷**：

> **个人购买链路**：登录 → 首页工作台选择船舶（自动加载下一靠港）→ **个人购买** → 下单支付 → 仓库配货 → **派司机 → 到港 → 签收拍照**。
> 验收点：结算默认 `delivery_way=4`，页面显示船舶/港口/时间窗卡片。

即 `purchase_scene=1`（海员个人购买）与 `2`（船供采购）区分的是**谁采购、谁付钱**，而不是**送到哪里**——两者都由公司司机按靠港计划送到船上。因此 `goPersonal()` 不清空船舶上下文、结算默认 `way=4`、`deliveryContextParams` 携带 `purchaseScene` 均为正确行为，代码无需修改。

原判定的错误在于：把「场景」误读为「配送目的地」，而它实际是「采购主体」。

---

### P1-3　常购「再来一份」绕过数量规则，且无价格与数量确认

**文件**：`sub-pages/product/frequent/index.vue:30-41`

```js
function handleAddToCart(item: any) {
  const quantity = item.totalQuantity && item.totalQuantity > 0 ? item.totalQuantity : 1
  addShoppingCart({ skuId: item.skuId, quantity, addType: '2' })
}
```

按历史数量一键加购本身**符合文档 §4.3**（「『再来一份』按历史数量一键加购」），问题出在实现细节：

| 问题 | 说明 |
|---|---|
| 绕过 MOQ/步长 | 船供商品强制 MOQ 与步长（船供页 `ship-supply/index.vue:65-74` 有本地校验），本页**无任何校验**。且 `totalQuantity` 是近 90 天**累计件数**，几乎必然不是 `stepQty` 的整数倍 → 加购成功、结算才被拦 |
| 无价格展示 | 用户看不到当前价格即完成加购（文档 §4.7 承诺的「现价/现库存」在本页缺失） |
| 不可调整 | 无数量编辑、无步进器、不可跳转详情，用户无法在加购前修正数量 |

---

### P1-4　`business_mode` 字段未落地

`db/boot/45ship_supply_menu_permission.sql:16-23` 为 `sys_tenant` 增加了 `business_mode`（1 综合并存 / 2 纯零售）。

但全仓检索显示该字段**仅出现在 SQL 与文档中**，`aryn-mall-uniapp/src/`、`aryn-mall-ui/`、`aryn-*-biz/` 中**无任何代码读取**。

而方案文档要求它生效：`海员个人与船供配送改造方案.md:999`「普通零售租户仍需兼容的页面不要物理删除，按租户 `business_mode` 或能力开关隐藏」。

**后果**：纯零售租户（`business_mode=2`）的 C 端用户，首页仍会渲染船舶工作台。

---

### P2　移动端 UI 与视觉一致性问题

| # | 位置 | 问题 |
|---|---|---|
| 1 | `ship-supply/index.vue:123-165` | 船供列表**无商品图片**，纯文字堆叠；无分类筛选（仅关键词）；无购物车角标/入口；数量靠手输（`type="number"`），**无步进器**，移动端输入易错且不体现 `stepQty` |
| 2 | `ship-supply/index.vue:167-169` | 请求失败与「暂无商品」共用同一空态文案「暂无船供商品」→ **加载失败被误报为无数据**；无骨架屏、无重试 |
| 3 | `ShipProfileCard.vue:36-41` | `firstSkuField()` 取「第一个非空 SKU 值」，多 SKU 商品切换规格后**卡片不更新** → 显示的 MOQ/步长可能是别的规格的 |
| 4 | `ShipProfileCard.vue:23-34` | `load()` 在 setup 中直接调用且**不 watch `props.spuId`**；`.catch(() => {})` 静默吞掉错误；无 loading/error 态，`v-if="profile"` 导致布局跳动 |
| 5 | `ShipProfileCard.vue:62-64` | 储存条件（冷藏/冷冻/**危险品**）仅以纯文本呈现，无徽标/色标区分 —— 安全相关信息未做视觉强化 |
| 6 | `order-confirm/index.vue:276-346` | 结算页混用**内联 `style` + 硬编码色值**（`#fff` `#909399` `#10b981` `#e67e22`）与 Tailwind 工具类，与其余页面的 `text-gray-500` / `bg-white` 体系不一致，换肤与暗色模式会失效 |
| 7 | `ShopOrderItem.vue` | **配送方式选择器被放在「订单商品卡片」内部** —— 配送方式是订单级决策，不应挂在商品条目组件上，信息层级错位 |
| 8 | `ship-workbench/index.vue:124-145` | 三个按钮 `船供采购 / 个人购买 / 常购` 视觉权重均等（蓝/灰/黄），但业务上「船供采购」是主路径。且未绑定时「船供采购」仍可点，与提示语义冲突 |
| 9 | `ship-workbench/index.vue:96-101` | 「已选靠港」绿标与船舶名同行，但**无任何可点击暗示**，用户不会知道这里本应能切换 |

---

## 5. 竞品对标

### 5.1 对标矩阵

| 平台 | 类型 | 用户准入模型 | 船舶/港口信息位置 | 交易方式 | 账期 |
|---|---|---|---|---|---|
| **ShipServ**（Marcura） | 全球船供交易平台 | 买方/供应商**企业账号**注册验证；注册目的是取 RFQ/Quote/PO 文档与报表 | **RFQ 的字段** | RFQ → Quote → PO → PO Confirmation 文档流 | 平台外结算 |
| **Marine Online 海运在线** | 一站式海运 B2B | 船东注册后**即可交易**，无船舶前置 | **订单字段** | 标准化服务「加入购物车即可订购」；非标「在线询价」 | **授信 30–60 天**，先消费后买单 |
| **Emdad** 等区域 chandler 自建目录 | 供应商自有渠道 | **开放浏览完整目录**，24/7 实时价格 | **订单字段** | 目录下单 + 历史再购 + **自动补货建议** | 数字化发票与对账 |
| **船用采购网 ckmarine** | 信息展示站 | 无需注册 | — | 询价 / 电话邮件联系 | — |
| **京东企业购** | MRO B2B | **个人账号可正常购买**；企业账号是**可选升级**（需营业执照+对公账户，审核 1–3 工作日） | 收货地址 / 成本中心 = **订单字段** | 直接下单；采购清单模板 | 企业账期 |
| **震坤行** | 工业品 MRO | 企业客户为主 | **订单字段** | 目录直采 + API 对接 | 账期 |

### 5.2 行业共识（四条）

1. **船舶 / 港口 / ETA 是订单属性，不是进店门槛。** 六个对标对象中，没有任何一个要求用户「先绑定船舶才能浏览或购买」。
2. **关系的建立方式有三种，都不是「等管理员配置」**：
   - 开放获取（Marine Online：注册即可交易）
   - 可选升级（京东：先买后认证，认证解锁能力）
   - 以 RFQ 承载（ShipServ：关系在询价流程中自然建立）
3. **成熟平台把「关系」做成能力，而不是门槛**：账期、增值税专票、多角色审批、集中采购报表、自动补货建议。京东的三级角色（管理员/采购员/审批员）+ 限额 + 审批流，与悦航购的共享购物车（发起人/成员/确认人）在设计意图上高度一致——但京东的角色体系是**认证后解锁的能力**，而非使用前提。
4. **船供特有的协作采购，国际对标是「船队级集中采购 + 自动补货」**（Emdad：按消耗速率与历史数据推荐再购；船队跨港集中支出报表）。悦航购的共享购物车方向正确，但 C 端未接通（P0-5）。

### 5.3 行业痛点与悦航购的机会点

传统船供的公认痛点（`海运在线` 行业访谈）：船东找供应商要「挨个发邮件或打电话谈价格」，效率低、质量无保障；中小船东航线不固定，在不熟悉的港口难以快速找到可信供应商。

悦航购的差异化机会恰在于此——**自有仓库 + 自有司机 + 靠港计划驱动的确定性交付**，这是纯信息平台（ckmarine）和 marketplace（ShipServ）都不具备的。但当前交互把这份优势**藏在了「先绑定船舶」的门槛之后**，等于用 B2B 大客户直销的交互，去承接本该开放获取的港口自然流量。

---

## 6. 交互合理性评估

评估基准：Nielsen 十大启发式 + 竞品范式 + 船供业务特性。

### 6.1 逐项评估

| 环节 | 合理性 | 判定 | 依据 |
|---|---|---|---|
| 领域模型（场景/范围/配送方式/靠港） | ✅ 合理 | — | 四概念正交，快照原则正确，双模式适配规范 |
| 首页工作台「船供采购/个人购买/常购」三入口 | ⚠️ 基本合理 | P1 | 方向正确（场景显式化），但视觉权重均等、未绑定时按钮仍可点 |
| **新用户准入（绑定船舶）** | ❌ **不合理** | **P0** | 与竞品范式相反（§5.2）；与自家文档矛盾（§4.0）；无自助出路（P0-7） |
| 未绑定提示文案 | ❌ 不合理 | P0 | 事实性错误（P0-8）+ 指令无法执行（P0-7） |
| 船供目录检索 | ❌ 不可用 | P0 | 搜索 SQL 条件错误（P0-3）+ 无分页（P0-4）+ 下架商品可见（P0-6） |
| 加购数量规则（MOQ/步长） | ⚠️ 部分合理 | P1 | 船供页有本地校验（好），但常购页绕过（P1-3），且无步进器 |
| 购物车防串船分组 | ⚠️ 设计合理、执行断裂 | P1 | 分组逻辑正确，但提示用户「切换船舶」而系统无切换入口（P1-1） |
| 结算内部配送上下文卡片 | ✅ 合理 | — | 船舶/港口/泊位/时间窗齐备，符合船供预期 |
| **结算配送方式选择** | ❌ **严重不合理** | **P0** | `way=4` 不在选项中且不可逆（P0-1） |
| 订单详情配送信息 | ❌ 不合理 | P0 | 四处错误文案（P0-2），「无需配送」自相矛盾 |
| 常购与再购 | ⚠️ 部分合理 | P1 | 再购预览（详情页）设计良好；常购页「再来一份」语义错误（P1-3） |
| 共享购物车 | ❌ 不可达 | P0 | 后端完整、C 端零实现（P0-5） |
| 营销优惠展示 | ✅ 合理 | — | 阶梯价/整船优惠明细 + 快照，语义清晰 |
| 司机端配送上下文 | ✅ 合理 | — | 快照驱动，不依赖实时档案，符合现场作业 |
| ETA 变更提醒 | ✅ 合理 | — | 影响面预览 + 站内信 + fail-open，设计成熟 |
| 多租户能力开关 | ❌ 未落地 | P1 | `business_mode` 无代码读取（P1-4） |

### 6.2 三条主线的深层成因

**A. 关系前置化（P0-7、P0-8）**

设计文档自身就写明了「绑定必须由后台操作，不开放用户自助搜索」——这是一个**有意识的架构决策**，而非疏漏。它隐含的业务假设是：

> 船东与我方签约 → 客户管理员把船舶与船员录入系统 → 船员才来下单

这是**「官方指定供应商」的闭环模型**。它与悦航购实际要承接的流量不匹配：船供公司的自然获客来自港口、船上、船员之间的口碑（扫码、小程序搜到），**先有购买意图，再有船舶关系**。把关系设成前置门槛，等于把最贵的自然流量挡在门外，并要求它去找一个尚不存在的「客户管理员」。

**B. 核心链路未闭环（P0-1~P0-6、P1-1~P1-3）**

特征是「后端建成、前端未接通或接通错误」：共享购物车、靠港选择、船舶切换、`way=4` 选项、搜索参数。共同点是**这些能力都没有被真实用户路径覆盖过**。

**C. 端到端验收缺失（贯穿全部 P0）**

`试点上线清单.md` §5 的「真机/浏览器端到端三链路验收」状态为**「待试点执行」**。单测与类型检查无法发现「picker 里少了一个选项」「订单详情显示无需配送」这类问题——它们只在人走流程时才暴露。**这是所有 P0 缺陷的共同放行口。**

---

## 7. 改造建议

### 7.1 立即修复（P0，建议 1 周内）

| # | 动作 | 文件 | 验收方式 |
|---|---|---|---|
| 1 | picker 增加 `{ value: '4', name: '公司港口/船舶内部配送' }`，并按 `hasVesselContext` 决定是否展示 | `ShopOrderItem.vue:61-67` | 已绑定用户可在 4 与 1/2/3 之间双向切换 |
| 2 | 统一配送方式文案映射，抽成单一 `const DELIVERY_WAY_LABEL = {1,2,3,4}` 供三处复用 | `ShopOrderItem.vue:182`、`order-detail/index.vue:40,67,387` | 订单详情显示「公司港口/船舶内部配送」 |
| 3 | 搜索改为**单一 keyword 参数 + 后端 OR 组合**；或前端只传一个参数、后端合并为一组 OR | `ship-supply/index.vue:36-39`、`ShipGoodsProfileMapper.xml:58-68`、`AppGoodsSpuController.java:71-72` | IMPA 码、中文名、英文名、条码、内部编码**各自单独搜索均可命中** |
| 4 | 船供目录补分页：`onReachBottom` 加载下一页 + 展示 `total` | `ship-supply/index.vue` | 滚动可加载至全部结果 |
| 5 | `selectShipSummaryPage` 补 `AND spu.status = '1'`；`selectApiPage` 补 `sale_scope` 排除 `2` | 两个 Mapper XML | 下架商品不出现在船供目录；仅船供商品不出现在零售列表 |
| 6 | 未绑定态**改为不显示工作台**（回归文档 §4.1），或改为中性引导卡片 | `ship-workbench/index.vue:26-33` | 新用户首页不再出现红字警告 |
| 7 | `unbound` 拆分为 `noVessel` / `noCall` 两态，分别给文案 | `ship-workbench/index.vue:36-57,114-116` | 有船无靠港时提示「『{船名}』暂无靠港计划」 |
| 8 | 清除误导性指引：移除「请切换船舶」「请在首页选择船舶」等无法执行的文案，改为跳转入口 | `order-confirm:218`、`shopping-cart:350`、`ship-supply:100` | 文案与实际可执行动作一致 |

### 7.2 补齐闭环（P1，建议 2–3 周）

| # | 动作 | 说明 |
|---|---|---|
| 9 | **打通共享购物车 C 端** | 新建 `api/order/sharedCart.ts` + 列表/详情/成员/明细/确认页；后端已就绪，纯前端工作量 |
| 10 | **补船舶与靠港选择入口** | 让 `switchVessel` 与 `getVesselCalls` 真正被调用：工作台船舶名可点 → 船舶/靠港选择弹层。这是 P1-1 全部误导文案的解药，也是防串船可验证的前提 |
| 11 | **常购页对齐数量规则** | 复用 MOQ/步长校验；展示当前价格与本次将加购的数量；提供数量编辑 |
| 12 | **落地 `business_mode`** | C 端首页按租户模式决定是否渲染 `ShipWorkbench`；后端结算侧同步校验 |
| 13 | 数量输入改步进器 | 船供页与常购页统一，按 `stepQty` 步进，避免手输错误 |

### 7.3 准入模型重构（战略级，建议与业务共同立项）

这是本次调研的核心建议。**建议把「船舶绑定」从进店门槛降级为订单属性，并保留三条互补路径：**

| 路径 | 说明 | 优先级 |
|---|---|---|
| **① 订单驱动绑定** | 允许未绑定用户直接下单；结算时填写「船名 + 港口 + 预计靠泊」；服务端自动创建「待认领船舶」档案，运营后台认领合并。**不阻断任何成交，同时把船舶库从 0 填起来** | 建议先做 |
| **② 船舶邀请码 / 二维码** | 船东或已在船用户生成船舶邀请码，船员扫码即绑定。适配「船上一个人先用、全船跟着用」的裂变路径，运营成本最低 | 建议同步做 |
| **③ 申请绑定 + 运营审核** | 用户搜索船名提交申请，运营审核通过。作为 ①② 的兜底 | 建议同步做 |
| **④ 保留管理员配置** | 作为大客户（船东签约）的批量导入通道，**但不再是唯一路径** | 保留 |

配套：`business_mode` 生效；未绑定用户的首页工作台改为「服务入口」而非「门槛提示」——有船则展示靠港与配送，无船则展示个人购买与常用清单，船舶入口收进「我的」页面。**首页第一屏不应出现任何警告性文案。**

### 7.4 流程改进（防止同类问题复发）

| # | 动作 |
|---|---|
| 15 | **把端到端验收从「待执行」变为「上线前置条件」**：`试点上线清单.md` §3 的四条链路（个人购买/船供采购/履约/司机）必须真机走通并留证，否则不予发布 |
| 16 | 建立**文案映射单一来源**：`delivery_way`、`purchase_scene`、`sale_scope`、靠港状态等枚举，前端统一走一份常量表，禁止各处三元表达式各自实现 |
| 17 | 引入**契约测试**：前端页面调用的 API 参数集，与后端 Mapper 的过滤条件做断言（可直接拦截 P0-3、P0-6 这类问题） |
| 18 | 文档与代码不一致时，**以代码为准并回写文档**：当前 `船供化改造功能总说明.md` §4.1 与实现相反，会持续误导后续开发 |

---

## 8. 结论

1. **用户最初的质疑成立，且代码证据比预期更充分。** 「当前账号尚未绑定船舶，请联系客户管理员配置」不仅是文案问题——它同时是**与自家设计文档相矛盾的实现**（P0-7）、**事实性错误的提示**（P0-8）、**没有出路的死胡同**（无自助绑定路径）。

2. **这不是一个「官方指定供应商」该有的交互，而设计上恰恰是照那个模型做的。** 文档白纸黑字写着「绑定必须由后台操作，不开放用户自助搜索」——这是一个有意识的架构决策。行业对标显示，六个同类平台无一采用此范式；通行做法是把船舶/港口作为**订单属性**，把关系做成**可选升级的能力**（账期、专票、审批流）。

3. **但改造本身的工程质量是高的。** 领域模型自洽、双模式适配规范、SQL 幂等、后端单测覆盖充分。问题集中在**交互层与业务设计层**，且大部分是可快速修复的工程缺陷（§7.1 的 8 项，一周内可完成）。

4. **最值得警惕的不是任何单个缺陷，而是「端到端验收未执行」这件事。** `way=4` 在结算页不可选、在订单详情显示为「无需配送」——这类问题任何一个人走一遍流程都会立刻发现，却在 90+ 后端单测、`mvn compile`、`pnpm type-check`、45 项小程序单测全绿的情况下留存至今。**测试覆盖率不能替代真实用户路径验证**，这是本次调研最应沉淀的教训。

5. **优先级建议**：§7.1（8 项 P0，一周）→ §7.3（准入模型重构，战略级，决定获客天花板）→ §7.2（补齐闭环，2–3 周）。其中 **§7.3 的「订单驱动绑定」是投入产出比最高的一项**：它既解除了对新用户的阻断，又能把船舶主数据从 0 积累起来，直接服务于后续的靠港配送与集中采购能力。

---

## 附录 A　问题清单速查

| 编号 | 级别 | 问题 | 位置 |
|---|---|---|---|
| P0-1 | 🔴 | 配送方式 picker 缺 `way=4`，内部配送不可逆 | `ShopOrderItem.vue:61-67` |
| P0-2 | 🔴 | `way=4` 在 4 处显示为错误文案（普通快递/无需配送/等待提货） | `ShopOrderItem.vue:182`、`order-detail:40,67,387` |
| P0-3 | 🔴 | 船供搜索 SQL 条件被 AND，按编码或名称单独搜索均返回 0 条 | `ShipGoodsProfileMapper.xml:58-68`、`AppGoodsSpuController.java:71-72` |
| P0-4 | 🔴 | 船供目录无分页，只能看到第 1 页 20 条 | `ship-supply/index.vue` |
| P0-5 | 🔴 | 共享购物车后端完整、C 端零实现 | `api/order/`、`pages/` 无对应文件 |
| P0-6 | 🔴 | `sale_scope` 在零售列表未生效；`status` 在船供目录未生效 | `GoodsSpuMapper.xml:117`、`ShipGoodsProfileMapper.xml:40-82` |
| P0-7 | 🔴 | 未绑定提示与文档矛盾且无自助出路 | `ship-workbench/index.vue:27,31,115` |
| P0-8 | 🔴 | 「有船无靠港」被误报为「未绑定船舶」 | `ship-workbench/index.vue:36-57` |
| P1-1 | 🟠 | 船舶/靠港无选择入口（`switchVessel`/`getVesselCalls` 为死代码），但界面引导用户去切换 | `shipContextStore.ts:96`、`api/vessel.ts:13` |
| ~~P1-2~~ | — | 【已撤销】个人购买走内部配送是设计要求，非缺陷（见附录 D） | — |
| P1-3 | 🟠 | 常购「再来一份」按 90 天累计量加购且绕过 MOQ/步长 | `frequent/index.vue:30-41` |
| P1-4 | 🟠 | `business_mode` 无代码读取，纯零售租户仍见船舶工作台 | 全仓无引用 |
| P2-1~9 | 🟡 | UI 与视觉一致性（无分页/无图/无步进器/静默失败/内联样式/组件层级错位等） | 见 §4 P2 表 |

## 附录 B　复核指引

本报告所有结论均可用以下方式独立复核：

```bash
# P0-1 / P0-2：配送方式选项与文案映射
grep -n "deliveryMethodColumns" -A 8 aryn-mall-uniapp/src/sub-pages/order/order-confirm/components/ShopOrderItem.vue
grep -rn "无需配送" aryn-mall-uniapp/src

# P0-3：搜索参数与 SQL 条件
grep -n "impaCode" -A 3 aryn-mall-uniapp/src/sub-pages/product/ship-supply/index.vue
grep -n "query.impaCode" -A 12 aryn-mall-java/aryn-product/aryn-product-biz/src/main/resources/mapper/ShipGoodsProfileMapper.xml

# P0-4：分页能力
grep -n "onReachBottom\|state.total" aryn-mall-uniapp/src/sub-pages/product/ship-supply/index.vue

# P0-5：共享购物车 C 端实现
ls aryn-mall-uniapp/src/api/order/ | grep -i shared   # 无输出

# P0-6：sale_scope / status 过滤
grep -n "status" aryn-mall-java/aryn-product/aryn-product-biz/src/main/resources/mapper/GoodsSpuMapper.xml
grep -rn "saleScope\|sale_scope" aryn-mall-java/aryn-product/aryn-product-biz/src/main/resources/mapper/GoodsSpuMapper.xml  # 无输出

# P1-1：死代码确认
grep -rn "switchVessel\|getVesselCalls" aryn-mall-uniapp/src --include=*.vue  # 仅 d.ts 与定义

# P1-4：business_mode 是否被读取
grep -rn "business_mode\|businessMode" aryn-mall-java aryn-mall-ui aryn-mall-uniapp --include=*.java --include=*.ts --include=*.vue  # 仅 SQL 与 docs
```

## 附录 C　参考资料

- `docs/50-需求文档/2026-09-11-悦航购船供化彻底改造/船供化改造功能总说明.md`
- `docs/50-需求文档/2026-09-11-悦航购船供化彻底改造/试点上线清单.md`
- `docs/50-需求文档/2026-09-11-海员个人与船供配送改造/海员个人与船供配送改造方案.md`
- `docs/plans/2026-09-12-船供化二期营销与效率.md`
- ShipServ 帮助中心：<https://help.shipserv.com/en/articles/10130133-how-to-get-started-with-shipserv-platform>
- 海运在线（Marine Online）海事服务线上化模式：<https://zhuanlan.zhihu.com/p/405269270>
- Emdad, *Digitalization in Ship Supply*：<https://emdad-shipsupplier.com/digitalization-ship-supply/>
- 京东企业采购账号注册与权限模型：<https://diantuoyi.com/article/18123.html>

---

## 附录 D　勘误与修订记录

### D.1 勘误：P1-2 已撤销（2026-09-19）

初稿将「个人购买不清空船舶上下文」判为缺陷，**该判定错误**。

`试点上线清单.md` §3.1 明确规定个人购买链路同样走「仓库配货 → 派司机 → 到港 → 签收拍照」，验收点为「结算默认 `delivery_way=4`」。可见 `purchase_scene` 区分的是**采购主体**（海员个人 vs 船舶），而非**配送目的地**；两者都由公司司机送到船上。

因此 `goPersonal()` 不清空船舶上下文、结算默认 `way=4` 均为**正确实现**，§4 P1-2 与 §7.2 对应整改项已作废。教训：判定「场景污染」前，应先确认该字段的业务语义边界。

### D.2 已实施的 P0 修复（2026-09-19）

| 编号 | 修复内容 | 涉及文件 |
|---|---|---|
| P0-1 | 新增 `@/utils/delivery-way` 作为配送方式文案与选项的**单一来源**；结算页 picker 补入 `way=4`（已绑定船舶+靠港时可选，并排在首位） | `utils/delivery-way.ts`（新增）、`order-confirm/components/ShopOrderItem.vue` |
| P0-1+ | 顺带修正 picker 交互：拆分「弹窗草稿值」与「已确认值」，取消弹窗不再污染展示与提交；补 `props.deliveryWay` 同步 | 同上 |
| P0-2 | 三处错误文案统一改走 `deliveryWayLabel()`；订单详情状态与副标题为 `way=4` 增加显式分支（「配送至船舶」「公司司机正在配送至船舶」），不再依赖 `else` 兜底 | `order-detail/index.vue` |
| P0-3 | 新增 `keyword` 统一关键词字段，SQL 在「编码组」与「名称组」之间取 **OR**；C 端船供页与 `/app/goodsspu/search` 改为传 `keyword` | `ShipProductSummaryVO.java`、`ShipGoodsProfileMapper.xml`、`AppGoodsSpuController.java`、`ship-supply/index.vue` |
| P0-4 | 船供目录补 `onReachBottom` 上拉分页、加载更多状态、总数展示，并区分「加载中 / 加载失败 / 暂无数据」三态 | `ship-supply/index.vue` |
| P0-6a | C 端船供目录强制 `status='1'`（在 Controller 层强制，管理端复用同一查询仍可见下架商品） | `AppGoodsSpuController.java` |
| P0-6b | 零售列表 `selectApiPage` 增加 `ship_goods_profile` 左连接，排除 `sale_scope='2'`（仅船供商品不再泄漏到零售链路） | `GoodsSpuMapper.xml` |
| P0-7/8 | 工作台状态由 `unbound` 拆分为 `noVessel` / `noCall`；移除「请联系客户管理员配置」死胡同文案，改为中性引导；未就绪时点击「船供采购」就地说明原因而非静默进入目录 | `components/ship-workbench/index.vue` |

**验证结果**：

| 验证项 | 结果 |
|---|---|
| `vue-tsc --noEmit`（小程序类型检查） | 通过，0 错误 |
| `vitest run`（小程序单测，含 9 项守门契约测试） | 48/48 通过 |
| 两个 Mapper XML 格式良好性校验 | 通过 |
| `mvn compile -pl aryn-boot -am` | 通过 |

> **尚未验证**：MyBatis 映射在**运行期**的解析与 SQL 执行（需启动应用与数据库）。`selectApiPage` 新增的 `LEFT JOIN ship_goods_profile` 已在 ON 子句显式声明 `tenant_id` 与 `del_flag`，以规避多租户拦截器行为不确定性，但仍建议在试点环境按 §7.4 的端到端清单实跑一次零售列表与船供目录。

### D.3 待业务确认的开放项

**验收标准「不出现普通快递选项」与本次修复的差异**

`试点上线清单.md` §3.1 验收点写明：结算默认 `delivery_way=4`，**不出现普通快递选项**。

本次修复采取的是**「补上 `way=4` 并保留 1/2/3 可切换」**，而非「有船舶上下文时锁定为 `way=4`、隐藏其他选项」。原因是：

- 锁定后，已绑定船舶的用户若想「上门自提」或「寄回家」将无路可走
- 但按验收标准字面要求，绑定用户不应看到「普通快递」

两者不可兼得，需业务方确认。若确认应锁定，改动很小：`deliveryWayOptions()` 在 `withVesselInternal` 为真时只返回内部配送一项即可。

**另需确认**：`switchVessel` / `getVesselCalls` 为死代码（P1-1），意味着多船用户被强制使用 `vessels[0]`、多靠港时被强制使用「下一靠港」。若试点租户确有多船/多靠港场景，此项应提升优先级。

### D.4 共享购物车 C 端打通（2026-09-19）

针对 §4 **P0-5「后端完整、C 端零实现」**，本次补齐闭环。

**后端补缺**

| 改动 | 说明 |
|---|---|
| 新增 `SharedCartVO` | 在 `shared_cart` 原始字段外补充两类信息：① **船舶/靠港展示字段**（船舶名、港口、泊位、配送时间窗），由 `RemoteVesselService.getVesselCallContext` 补齐，远程失败或靠港失效时**降级为仅返回 ID**，不影响列表可用；② **查看者视角权限标记**（`viewerRole` / `viewerCanEdit` / `viewerCanConfirm` / `viewerIsOwner`），前端不再自行推断权限 |
| `ISharedCartService` | 新增 `listMyCarts` 与 `getCartDetail`；实现中成员与明细计数**各查一次后内存分组**，避免逐车 N+1 |
| `AppSharedCartController` | 新增 `GET /app/shared-cart/my`；`GET /{id}` 改为返回 `SharedCartVO`（原返回裸实体，C 端此前无任何调用方，无兼容风险） |

**前端实现**

- 新增 `src/api/order/sharedCart.ts`（10 个接口）与 `src/utils/shared-cart.ts`（状态/角色文案单一来源，沿用 `delivery-way.ts` 的约定）
- 新增列表页 `sub-pages/order/shared-cart/list.vue`：我参与的购物车、状态标签、船舶/港口、明细与成员计数、收集截止；创建表单（收集窗口 24h / 48h / 不限期 + 备注），**仅在已绑定船舶+靠港时可发起**（购物车创建即绑定且不可变更）
- 新增详情页 `sub-pages/order/shared-cart/detail.vue`：成员列表与邀请（发起人）、明细增删改（**仅限自己的明细**）、核定数量后提交整船订单、关闭购物车；**权限全部由后端 `viewer*` 标记驱动**
- 船供目录页新增 `sharedCartId` **选货模式**：携带该参数时加购写入共享购物车而非个人购物车，并展示购物车**自身**的船舶/靠港——避免在错误的船下选货
- 入口：购物车页与船供采购页各加一处「共享购物车」入口；`pages.json` 注册两个页面

**验证结果**

| 项 | 结果 |
|---|---|
| `vue-tsc --noEmit` | 通过，0 错误 |
| `vitest run` | **74/74 通过**（新增 26 项：`delivery-way` 6 + `shared-cart` 8 + 前后端契约 12） |
| `mvn test -pl aryn-order/aryn-order-biz -am` | **117/117 通过**（`SharedCartServiceTest` 由 12 项增至 18 项） |
| `mvn compile -pl aryn-boot -am` | 通过 |

**新增契约守门**：`sub-pages/order/shared-cart/contract.test.ts` 断言 pages.json 已注册两个页面、列表页可进详情、详情页可进选货模式、前端 API 路径与后端 `@RequestMapping` / `@PostMapping` 一一对应。**「页面忘了注册」「前后端路由漂移」这两类问题类型检查抓不到**，只能靠这类守门。

**过程中暴露并修复的自身缺陷**

1. `detail.vue` 把 computed 命名为 `readonly`，与 Vue 自动导入的 `readonly()` 冲突，模板中被解析为函数导致条件**恒真**（`vue-tsc` TS2774 报出）。已改名 `cartReadonly`，并在契约测试中加断言防止复发。
2. `list.vue` 的 `payload` 声明为 `Record<string, any>` 丢失类型约束；`maxlength="120"` 传字符串而非数字。均已修正。

> **仍未验证**：上述均为编译期与单测验证。`SharedCartVO` 依赖 `RemoteVesselService` 的 Dubbo 调用，其降级路径（远程不可用）已由单测覆盖，但**真实跨服务调用**需在试点环境验证。

### D.5 船舶与靠港选择入口（2026-09-19）

针对 §4 **P1-1「船舶/靠港无选择入口」**——`switchVessel` 与 `getVesselCalls` 是死代码，而界面却反复引导用户执行做不到的动作。本项是共享购物车创建与防串船共同的前置。

**新增 `components/ship-context-picker/index.vue`（底部弹层）**

- 加载「我作为在船成员的在营船舶」；多船时以横向 chips 切换
- 选择船舶后加载其**可用**靠港计划（服务端已按 ETA 升序并排除已离港/已完成/已取消）
- 选中靠港后写入 `shipContextStore`；**换船时先 `switchVessel`（其内部清空靠港上下文）再 `setVesselCall`**，防止把 A 船的靠港带到 B 船
- 展示 ETA/ETD 与配送时间窗，并标记当前已选靠港
- 覆盖「加载中 / 加载失败可重试 / 无船舶 / 无可用靠港」四态

**接入四处入口，同时消除死指引**

| 位置 | 原状态 | 现状 |
|---|---|---|
| 首页工作台 | 船舶名不可点 | 船舶名可点，带「切换 >」提示 |
| 购物车跨靠港分组 | 红字「（结算前请切换船舶）」纯文案 | 「（结算前请点此切换船舶）」可点击打开选择器 |
| 船供采购页 | 「请在首页选择船舶和靠港计划」 | 「选择船舶和靠港计划 >」可点击 |
| 共享购物车创建 | toast「需先关联船舶并选择靠港计划」 | 直接打开选择器 |
| 结算页 | 「请先在首页选择船舶和靠港计划」 | 「请先选择船舶和靠港计划」 |

**顺带修复一处本次改动引入的逻辑缺陷（自查发现）**

工作台原先用 `getVesselContext`（返回**最早**的可用靠港）刷新上下文。接入选择器后会产生冲突：**用户显式选了第 2 个靠港，工作台一刷新又被改回第 1 个**。

改为用 `getVesselCalls` 取完整可用列表，**仅当选中的靠港已不可用（过期 / 已取消 / 换船被清空）时才回落到最早的可用靠港**。

> 副作用：`api/vessel.ts` 的 `getVesselContext` 因此不再被调用。本次**保留**该导出（它 1:1 对应仍在线的后端接口 `GET /vessel/app/context`，删除可能与其他在途改动冲突），已记入待清理项。

**验证结果**

| 项 | 结果 |
|---|---|
| `vue-tsc --noEmit` | 通过，0 错误 |
| `vitest run` | **83/83 通过**（新增 9 项选择器契约测试） |

**新增契约守门**：`components/ship-context-picker/contract.test.ts` 断言 ①选择器确实调用 `getVesselCalls` / `switchVessel` / `setVesselCall`；②`switchVessel` 必须出现在 `setVesselCall` **之前**（防串船顺序）；③四个相关页面都渲染了选择器；④`switchVessel` 与 `getVesselCalls` 至少被一个 `.vue` 页面调用——**这一条直接守住「死代码复活」**；⑤各处旧死指引文案已不存在。

### D.6 至此的进度与剩余项

| 报告编号 | 问题 | 状态 |
|---|---|---|
| P0-1 ~ P0-8 | 配送选项缺失 / 文案错误 / 搜索不可用 / 无分页 / 共享车 C 端 / sale_scope 与 status / 未绑定提示 / 状态语义合并 | ✅ 已修（D.2、D.4） |
| P1-1 | 船舶与靠港无选择入口 | ✅ 已修（D.5） |
| P1-3 | 常购页绕过 MOQ/步长、无价格 | ⬜ 未做 |
| P1-4 | `business_mode` 未落地 | ⬜ 未做 |
| P2-1~9 | UI 与视觉一致性（列表无图/无步进器、卡片静默失败、内联样式混用等） | ⬜ 未做 |
| §7.3 | 准入模型重构（订单驱动绑定 / 船舶邀请码 / 申请绑定审核） | ⬜ 未立项，**投入产出比最高** |
| §7.4 | 流程改进（E2E 验收前置、枚举文案单一来源、契约测试） | 🟡 部分完成：文案单一来源与契约测试已落地；**E2E 验收仍未执行** |

> **最需要强调的仍是 §7.4 的最后一条**：截至目前所有修复都只经过编译期与单测验证，**真实端到端链路依然没有走过一遍**。D.2、D.4、D.5 中列出的「未验证」项（MyBatis 新 JOIN 的实际 SQL、Dubbo 跨服务调用、选择器在真机上的弹层交互）都必须在试点环境按 §7.4 的清单实测。

### D.7 端到端验收交付物（2026-09-19）

为把《试点上线清单》§5 长期处于「待试点执行」的验收落到实处，本次交付两份可执行材料。

**1. `E2E验收清单.md`（91 个用例）**

按链路拆分，每条标注「修复前表现」，便于区分「新功能验收」与「旧缺陷回归」：

| 链路 | 用例数 | 覆盖重点 |
|---|---|---|
| 自动化冒烟 S1–S10 | 10 | 接口层可断言部分 |
| A 个人购买 | 11 | **P0-1 / P0-2** 配送方式选项与文案 |
| B 船供采购 | 13 | **P0-3** 搜索、**P0-4** 分页、**P0-6a** 下架过滤 |
| C 共享购物车 | 22 | 原清单要求、但此前无从验收 |
| D 船舶靠港切换 | 13 | 原清单要求、但此前无此入口 |
| E 履约 | 12 | 沿用原清单 §3.3 |
| F 司机 | 5 | 沿用原清单 §3.4 |
| R 零售兼容 | 5 | 含 R5「预期失败」项（`business_mode` 未落地） |

通过标准中明确：**任一 P0 用例（A5/A6/A9/A10、B2~B8、C16/C18、D10/D12）失败即不得上线**。

**2. `dev-tools/smoke-ship-supply.sh`（接口冒烟脚本）**

```bash
./dev-tools/smoke-ship-supply.sh              # Cloud 模式
MODE=boot ./dev-tools/smoke-ship-supply.sh    # Boot 单体模式
TOKEN=<satoken> ./dev-tools/smoke-ship-supply.sh
```

- 支持 Cloud / Boot 双模式：boot 下把 `/域/路径` 改写为 `/boot/路径`，与前端 `rewriteBootUrl` 一致（已用前端 `boot-url.test.ts` 的期望值交叉验证）
- 仅依赖 `curl` + `python3`，**不依赖 jq**；JSON 取值助手已对「字段不存在 / 非数组 / 非法 JSON」三类边界做过验证，均安全返回空而不崩溃
- 关键断言：**S4/S5/S6 分别按 IMPA / 中文名 / 内部编码搜索并断言 `total > 0`**——这三条在修复前都会返回 0，是 P0-3 最直接的回归探测
- 创建共享购物车后会**自动关闭清理**，不在试点环境留脏数据
- 退出码：全通过 0，有失败 1（可直接接 CI）

**脚本自身的一个 bug（已修）**：路径改写最初写作 `${path#/[!/]*}`，但 `#` 是非贪婪匹配、`*` 会匹配空串，结果只吃掉首段的一个字符（`/vessel/...` → `/bootessel/...`）。已改用正则 `^/[^/]+(.*)$`，并逐条复验 boot/cloud 两种模式下的四条真实路径。

**仍未完成**：脚本只覆盖接口层，**UI 交互、跨页面状态、真机表现必须人工执行**——清单第 3~9 节即为此准备。建议执行完毕后，把《试点上线清单》§5 的「真机/浏览器端到端三链路验收」一栏更新为**已执行**并附结果记录表。

### D.8 服务重启与运行期验证（2026-09-19 20:27）

#### D.8.1 重启范围

按「`*-api` 变更 → Provider 及其所有 Consumer」的规则，先确认了改动类的引用范围：

| 改动类 | 引用方 | 结论 |
|---|---|---|
| `ShipProductSummaryVO`（product-api） | 仅 `aryn-product` 内部 | 只重启 product |
| `SharedCartVO`（order-api） | 仅 `aryn-order` 内部 | 只重启 order |

其余改动均为 `aryn-product-biz` / `aryn-order-biz` 内部实现与 Mapper 资源。**结论：只需重启 `product` 与 `order` 两个服务。**

前端无需重启：H5 dev server 的日志显示 HMR 已接住全部改动，且新页面路由（`SubPagesOrderSharedCartList` / `SharedCartDetail`）已由 pages.json 变更自动生成。

#### D.8.2 重启结果

| 服务 | 结果 |
|---|---|
| `aryn-product-biz` | 容器已替换，16 秒恢复健康；Nacos 服务注册正常 |
| `aryn-order-biz` | 容器已替换，37 秒恢复健康 |

容器内 `/app/app.jar` 时间戳（12:27:52 / 12:28:18 UTC）与本次 Maven 构建产物时间**完全一致**，确认运行的是新代码。

> **环境坑**：沙箱环境下 `docker build` 会被拦截（BuildKit 需要写 `~/.docker/buildx/activity/`，在工作区之外）。解决办法是改用旧版构建器：`DOCKER_BUILDKIT=0 docker compose build <service>`。代价是没有 buildx 缓存，首次构建约 5 分钟。

#### D.8.3 运行期验证（此前只能静态校验的部分）

D.2 与 D.7 曾声明「MyBatis 新 JOIN 的实际 SQL 需在试点环境验证」。本次借服务已启动 + 数据库可访问，**把这三条补验了**：

| # | 验证项 | 方法 | 结果 |
|---|---|---|---|
| V1 | `selectApiPage` 新增 `LEFT JOIN ship_goods_profile` 是否误伤零售商品 | 直接对 `aryn_product` 跑改造前/后两条等价 SQL 计数 | **111 = 111**，零误伤 ✓ |
| V2 | 零售列表接口在新 JOIN 下是否正常返回 | `GET /product/app/goodsspu/page`（带 tenant-id） | `code:0`，正常返回真实商品 ✓ |
| V3 | 船供搜索新增的 `query.keyword` OGNL 分支是否可用 | `GET /product/app/goodsspu/ship/page?keyword=0301010` | `code:0`，无 OGNL 异常 ✓ |
| V4 | 船供目录 `status='1'` 过滤是否生效 | `GET /product/app/goodsspu/ship/page` | `code:0`，查询正常执行 ✓ |
| V5 | **P0-3 的搜索逻辑修复是否真的有效** | 构造三条样本行（IMPA / 内部编码 / 中英文名各一），分别按修复前与修复后的 WHERE 条件计数 | **修复后 1 条 / 修复前 0 条** ✓ |

V5 是本次最有价值的证据：它用构造数据**复现了原缺陷**（按 IMPA 码单独搜索返回 0 条）并**证明修复生效**。

> 注意：接口 HTTP 状态恒为 200，业务结果在响应体的 `code` 字段（`0` 成功、`401` 未登录）。用 HTTP 状态码判断成败会误判。

#### D.8.4 试点数据现状（重要，影响验收可执行性）

查库发现试点数据尚未就绪：

| 表 | 行数 | 含义 |
|---|---|---|
| `vessel_info` | 1 | 试点船舶（悦航1号）已就位 |
| `vessel_call` | 2 | 两个靠港计划已就位 |
| `vessel_member` | **0** | ⚠️ **没有任何用户绑定到船舶** |
| `ship_goods_profile` | **0** | ⚠️ **船供商品资料未导入** |
| `ship_sku_profile` | **0** | ⚠️ 同上 |
| `goods_spu` | 111 | 普通零售商品正常 |

**后果**：
- 船供目录现在**必然是空的**（这也解释了 V3/V4 返回 0 条是正确行为，不是缺陷）
- 任何人打开首页工作台都会落到 `noVessel` 状态，**船舶/靠港选择器会显示「暂未关联船舶」**
- **共享购物车无法创建**（创建要求船舶+靠港上下文）
- 因此 E2E 清单的 **B、C、D 三条链路当前全部无法执行**

**执行验收前必须先完成**（对应《试点上线清单》§2.2 / §2.3）：
1. 管理端「船舶档案 → 成员管理」绑定 2~3 名商城用户为悦航1号成员，并指定一名采购确认人
2. 通过商品列表「批量导入」上传船供商品 Excel（含采购单位、箱规、MOQ、步长、IMPA/内部编码）
3. 为试点商品设置销售范围（2 仅船供 / 3 两者均可）与库存

> 另需注意：**权限在登录时快照进 token session**，完成上述 SQL/权限操作后必须重新登录才生效。

### D.9 试点验收数据一键准备（2026-09-19）

针对 D.8.4 发现的「试点数据未就绪，B/C/D 三条链路无法执行」，本次交付一键准备脚本，并**已在当前环境实跑验证**。

**交付物**

| 文件 | 说明 |
|---|---|
| `db/cloud/62ship_supply_seed_acceptance.sql` | Cloud 微服务模式种子数据 |
| `db/boot/62ship_supply_seed_acceptance.sql` | Boot 单体模式（由 cloud 版机械转换，仅去跨库前缀，0 处残留） |
| `dev-tools/seed-acceptance-data.sh` | 一键铺设 / 清理 / 只读校验 |

```bash
./dev-tools/seed-acceptance-data.sh            # 自动探测 cloud/boot 并铺设
./dev-tools/seed-acceptance-data.sh --verify   # 只读校验
./dev-tools/seed-acceptance-data.sh --clean    # 移除本脚本铺的数据
```

**铺了什么**（全部挂 96x 人工编排前缀，幂等，不触碰存量业务数据）

| 内容 | 数量 | 用途 |
|---|---|---|
| 船舶成员 | 3 | 发起人 / 采购确认人 / 普通船员，绑定悦航1号 |
| 靠港计划 | 2 | 保证验收时**至少 2 个可用靠港**（D 链路切换验证的前提） |
| 船舶物料类目 | 1 一级 + 6 二级 | 电缆/救生衣等工业品在原有商超分类里无处安放 |
| 船供商品 | 28 | 见下 |

28 个商品是按验收清单**刻意设计**的，不是随便凑数：

- 27 个上架（26 个 `sale_scope=3` + 1 个 `sale_scope=2`）→ **超过单页 20 条**，可验证分页
- 1 个下架 → 验证船供目录不得出现下架商品
- 编码覆盖 IMPA / ISSA / 内部编码 / 条码 / 英文名 / 搜索别名 → 验证各条搜索路径
- MOQ 与步长组合多样，且**全部满足「MOQ 是步长整数倍」**→ 验证数量规则
- 储存条件覆盖 常温 / 冷藏 / 冷冻 / 危险品 → 验证详情页储存条件展示

**实跑验证结果（真实数据，非构造）**

| 验证项 | 结果 |
|---|---|
| 船供目录第 1 / 2 页 | 20 条 + 7 条，`total=27`、`pages=2` ✓ |
| 搜 IMPA 码 `0301010` | 命中「船用洗手液」 ✓ |
| 搜中文名 `救生衣` | 命中 ✓ |
| 搜内部编码 `INT-ELE-001` | 命中「船用电缆 3x2.5」 ✓ |
| 搜英文名 `Gasket` | 命中「密封垫片」 ✓ |
| 搜条码 `6901234500135` | 命中「柴油滤芯」 ✓ |
| 搜搜索别名 `泡面` | 命中「方便面」 ✓ |
| **P0-6a** 搜下架商品 | **0 条**（船供目录正确排除） ✓ |
| **P0-6b** 零售列表搜仅船供商品「医用酒精」 | **0 条** ✓ |
| **P0-6b 对照** 船供目录搜「医用酒精」 | **1 条** ✓ |
| `sale_scope=3` 商品「救生衣」 | 零售与船供**两处均可见** ✓ |
| 幂等性 | 重复执行数量不变；`--clean` 后归零、可重建 ✓ |

> 上面 6 条搜索在修复前**全部返回 0 条**。D.8.3 的 V5 用构造数据证明了这一点，现在用真实数据再次确认——**P0-3 至此有了端到端的实证**。

**过程中发现并修掉的两个自身缺陷**

1. 脚本自检的预期值我一开始算错了（写 26，实际应为 27：26 个 scope=3 + 1 个 scope=2 都上架）。已修正。
2. `seed-acceptance-data.sh` 的 `clean()` 漏删靠港计划，导致清理后仍有残留。已补上并复验。

**另一个必须知道的坑**：46 号脚本的靠港计划用的是 `NOW()+3天 / NOW()+10天` **相对日期，会随时间自然过期**。本次检查时其中一条已经离港（eta 09-17，今天 09-19）。所以 62 号脚本用独立前缀新建 2 个靠港，保证验收时始终可用。

**当前状态：环境已就绪。** 用绑定过的账号重新登录后，即可按 `E2E验收清单.md` 执行 B/C/D 三条链路：

| 账号 | 用户ID | 角色 |
|---|---|---|
| 微信用户6798 | `2040654277629796353` | 发起人（可邀请成员、可提交、可关闭） |
| 176****2320 | `2040656345832747009` | 采购确认人（可核定数量并提交整船订单） |
| 宇宙第一帅 | `2096466699352522754` | 普通船员（只能维护自己的明细） |
