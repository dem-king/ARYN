# 船供活动治理：问题清单与修复方案

> 日期：2026-10-13
> 状态：**三期（P0/P1/P2）已全部实施**
> 类型：现状盘点 + 设计缺陷定位 + 修复方案
> 触发：管理端「船供活动 → 新建活动」表单可用性质疑（SKU ID 手填、"指定场景"手填原始码），
> 继而对营销引擎、订单计价链、营销锁定、管理端表单做全链路梳理。

---

## 1. 结论

船供活动（`promotion_activity`，类型 4 阶梯价 / 5 买赠 / 7 整船优惠 / 9 新人专享价）
**当前不具备上线条件**。问题不在体验层而在模型层：

1. **阶梯价规则模型不成立**：档位只有 `{minQty, unitPrice}` 没有 SKU 维度，
   "满 20 件单价 8 元" + 适用范围全场 = 全场任何 SKU 买满 20 件都变 8 元。
2. **配置即静默失效**：范围值自由文本 + 匹配 fail-closed，填错永不命中且无任何反馈。
3. **买赠链路三个硬伤**：结算页不可见、同车赠品静默丢弃、赠品库存伪造。
4. **无熔断**：活动无预算/总量/每人限领，配错价无法止损。

骨架是对的（锁定幂等、fail-closed 降级、订单营销快照、共享购物车按 SKU 聚合口径、
发布治理校验结构），修复时保留；要修的是规则模型与配置闭环。

## 2. 现状代码地图

| 环节 | 位置 |
|------|------|
| 引擎试算 | `promotion-biz` `PromotionEngineServiceImpl`（preview：范围匹配 → 同类型取最优 → 跨类型叠加） |
| 规则校验 / 发布治理 | `ActivityPublishGovernanceService`、`ShipActivityController` |
| 营销锁定 | `PromotionReservationServiceImpl`（reserve/confirm/release，表 `promotion_lock`） |
| 订单计价 | `order-biz` `OrderPriceComputeService`（applyPromotionLadder / orderPromotionPriceHandler / applyShipWholeDiscount）、`OrderInfoServiceImpl`（提交时追加赠品、营销快照） |
| 管理端 | `web-ele` `views/promotion/ship-supply/index.vue`、`api/promotion/ship-activity.ts` |
| C 端 | `uniapp` `sub-pages/order/order-confirm`（仅展示阶梯/整船明细，无赠品） |

## 3. 问题清单

### P0 上线阻断

1. **阶梯价规则无 SKU 维度**。`rules.ladders=[{minQty,unitPrice}]`，引擎对范围内每个
   SKU 逐档覆盖单价（`PromotionEngineServiceImpl.java:156-181`）。模型只对"单品批量采购"
   成立，但表单允许选全场/多 SKU。对照新人价 `rules.skus[{skuId,price}]` 是逐 SKU 定价的，
   阶梯价属半成品模型。
2. **无熔断**。活动表无总预算、赠品总量、每人限领字段（新人价限购 1 为硬编码）。
   买赠发布后赠品无限量；阶梯价配错一位小数会被薅且只能靠暂停止损。
3. **叠加不可控**。`stackable`/`priority` 为死字段（`PromotionActivity.java:74-77`），
   引擎硬编码"同类型取最优、跨类型全叠加"；阶梯价改完基价后限时折扣再乘上去
   （`OrderPriceComputeService.java:129-135`），再叠会员折扣、券、整船优惠，
   只有单明细封 0、没有整单封顶。"是否可叠加"既不可配也解释不清。
4. **买赠结算页不可见**。结算接口只返回阶梯/整船明细，赠品在提交订单 reserve 时才算
   （`OrderInfoServiceImpl.java:781-783`）——用户付款后才知道有赠品，确认页与提交两套口径。
5. **同车赠品静默丢弃**。赠品 SKU 已在购物车时整条赠品被 filter 掉
   （`appendGiftItems` :1140），"买 A 赠 A"永远不成立。
6. **赠品库存伪造**。构造 `stock=赠品数量` 的假 GoodsSku 绕过真实库存校验
   （`OrderInfoServiceImpl.java:1170-1174`）；真实库存不足时 `reduceStock` 失败，
   整单报"库存不足"——用户被一个自己没选的赠品挡下单。
7. **reserve 幂等重放结果不一致**。`rebuildFromLock` 只重建单条锁（LIMIT 1）、
   `ladderOverrides` 为空、赠品丢失（`PromotionReservationServiceImpl.java:166-180`），
   下单重放时阶梯价与赠品会丢。

### P1 配置闭环

8. scopeValue 自由文本 + 匹配 fail-closed：ID 写错→永不命中，无校验、无告警、
   列表只回显码值，错误暴露方式是客户投诉。
9. 指定船舶/指定靠港占位符仍为"SKU ID 逗号分隔"（`index.vue:358-363`），
   实际匹配 `vesselId`/`vesselCallId` 主键（引擎 :256-257），按提示填必然错。
10. scopeType=4"指定场景"与"限定场景"下拉是同一判定（引擎 :254-255 与 :244-246），纯冗余。
11. 无 dry-run：不能输入模拟购物车验证命中与金额，运营只能发布后祈祷。
12. 阶梯价高于售价时发布校验不拦（校验不查商品库），引擎静默跳过（引擎 :167），
    活动"已发布"但永远不生效。
13. SKU/赠品手填，未复用既有 `select-goods` 组件（秒杀/拼团/折扣表单均在用）；
    船舶/靠港/港口无选择器。
14. 编辑已发布活动必报错：后端禁止改规则/范围（`ShipActivityController.java:125-131`），
    前端只禁用类型与 scopeType 两个控件，scopeValue 与档位仍可编辑。
15. `handleSave` 的 `.catch(() => {})`（`index.vue:156`）吞掉保存失败提示。
16. 新人价（type 9）无管理入口：后端校验、引擎、C 端 `AppNewbieController` 均齐备，
    管理端表单类型仅 4/5/7，type 9 只能裸调 API 创建，列表类型列显示原始码"9"。
17. "满 N 件"口径未披露：按整单同 SKU 合计数量判定（`OrderPriceComputeService.java:583-596`），
    表单与文案均无说明，运营会理解成整单件数。

### P2 治理与运维

18. 锁释放失败只 `log.warn("等待补偿")`（`OrderInfoServiceImpl.java:457-460`），无补偿任务：
    新人价在途锁泄漏后用户新人身份被长期冻住。
19. 整船优惠分摊以最后一条明细兜底、分摊基数不含券（`OrderPriceComputeService.java:529-560`），
    叠加顺序无文档，财务对账解释不清 promoPrice。
20. 发布冲突只查"同类型+时间重叠"，跨类型叠加把价格打到零无人治理。
21. 买赠多活动取"最优"=赠品总件数最大（引擎 :337-338）：送 10 件低值品赢过送 1 件高值品。
22. 引擎每次试算全表扫 DB、规则 JSON 逐单解析，无缓存、无"命中/解析失败"监控指标（仅 log.warn）。

## 4. 修复方案

### 4.1 阶梯价模型（P0-1，待决策）

- **方案 A（推荐）：逐 SKU 阶梯价**。`rules.skuLadders=[{skuId, ladders:[{minQty,unitPrice}]}]`；
  type 4 的 scope 限定"指定SKU"（参照 `validateNewbiePrice` 对 scopeType 的强校验，
  `ActivityPublishGovernanceService.java:147`）；发布时校验每档 unitPrice < 该 SKU 当前售价
  （经 product-api Dubbo 查询）。表单用 `select-goods` 选品后逐品配档。
  契合船供"按物资谈合同价"的业务形态。
- **方案 B：档位改折扣语义**（满 N 件打 X 折 / 减 Y 元），scope 可全场；
  适合品类普促，但与船供合同价场景不符，且与整船优惠（type 7）语义重叠。
- 存量兼容：功能未上线、无生产数据，不做规则迁移；测试数据用一次性清理 SQL 处理。
- 口径披露：表单文案明确"满 N 件 = 单内同一 SKU 合计数量"。

### 4.2 熔断与叠加（P0-2、P0-3）

- `promotion_activity` 增字段：`total_budget`（预算金额，0=不限）、`per_user_limit`
  （0=不限）、`gift_stock`（买赠赠品总量，0=不限）；reserve 时按 `promotion_lock`
  聚合扣减，超限拦截。增量 SQL 按约定 boot/cloud 双份。
- 叠加关系固化为矩阵并写进引擎注释与文档：单价类（阶梯/新人）同 SKU 互斥取更低
  （已有 `mergeNewbieOverrides`）；减免类（整船）可与单价类叠加；买赠独立。
  `stackable` 字段标注废弃、表单不暴露；`priority` 仅保留新人价列表排序用途。
- 整单封顶：新增租户级配置"营销折后底价比例"（应付不得低于订单基价 × 比例，默认 0
  即维持现状单明细封 0），在 `computeItemPayPrice`/`applyShipWholeDiscount` 收口。

### 4.3 买赠三件套（P0-4/5/6）

- 结算接口返回赠品：结算与提交共用同一试算（幂等锁保证两屏一致），确认页赠品带"赠"标。
- 同车赠品不再丢弃：去掉 `appendGiftItems` 的 noneMatch filter，赠品独立成行
  （订单明细已支持同 SKU 多行——共享购物车按成员拆行先例；库存按 SKU 聚合校验已具备）。
- 真实库存：赠品 SKU 查真实 GoodsSku 并入 `goodsSkuList` 参与校验；真实库存不足时
  **不赠该品并在确认页提示**（推荐，不阻断付费商品），备选为整单阻断——待决策。

### 4.4 锁重放一致（P0-7）

`rebuildFromLock` 改为按订单全部锁（`findLocks`）重建：ladderOverrides 与 gifts 从
锁的 `ruleSnapshot`（已存规则 JSON）恢复，保证重放与首次结果一致。

### 4.5 配置闭环（P1 全部）

- 表单：SKU/赠品复用 `select-goods`；删除 scopeType=4；船舶/靠港/港口改可搜索下拉
  （船舶档案 `api/vessel/vessel.ts`、靠港日历、港口字典数据源现成）；
  已发布态禁用 scopeValue 与规则控件；保存失败补错误提示。
- 列表范围列回显名称（批量查询 SKU/船舶/港口名）。
- 新增 dry-run 端点 `POST /promotion/ship-activity/dry-run`：入参
  `{skuIds+数量, userId, vesselId, portCode, purchaseScene, 时间}`，返回命中活动与金额；
  管理端对话框加"试算"按钮。
- 发布校验补"阶梯价 < 售价"（查商品库）。

### 4.6 P2 批次

- xxl-job 锁释放补偿：扫描已取消/超时关单但 `promotion_lock` 仍 LOCKED 的记录补 release
  （release 本身幂等）。
- 整船分摊口径写入订单域文档。
- 新人价管理入口：表单补 type 9（逐 SKU 定价复用新人价调研文档的方案）。
- 买赠"最优"改按赠品价值（售价 × 数量）最大。
- 引擎试算加租户级短缓存与命中/失败监控指标（可选）。

## 5. 分期与验收

| 阶段 | 内容 | 验收 |
|------|------|------|
| 一（P0） | 4.1–4.4 | `PromotionEngineTest`、`OrderPriceComputeServiceTest` 逐条补用例：全场阶梯价不再串价、赠品同车合并、赠品真实库存、重放一致、预算超限拦截 |
| 二（P1） | 4.5 | 管理端配置全流程走通（选品→配档→dry-run→发布→列表回显）；boot/cloud 双模式路径验证 |
| 三（P2） | 4.6 | 补偿任务演练、对账口径文档评审 |

双模式适配：新增端点首段为微服务域 `promotion`；增量 SQL 落 `db/boot/` 与 `db/cloud/`
双份并重新生成 `aryn_boot_full.sql`；跨模块查询走 `*-api` Dubbo 接口。

## 6. 决策记录（2026-10-13 评审）

1. 阶梯价模型：**方案 A 逐 SKU 阶梯价**（`rules.skuLadders`，scope 限定指定SKU）。
2. 赠品真实库存不足：**不赠 + 确认页提示**，不阻断付费商品。
3. 整单折后底价比例：**暂不引入**，维持单明细封 0，留待后续治理批次。

## 7. 一期实施记录（2026-10-13）

- **阶梯价逐 SKU 模型**：引擎 `applyBestLadderPrice` 改读 `rules.skuLadders`
  （未配档 SKU 不改价）；发布校验 `validateSkuLadders` 限定 scope 为指定SKU/常购用户
  且 skuLadders 与 scopeValue 一一对应；管理端表单用 `select-goods` 选品后逐 SKU 配档
  （规则快照带 spuName/skuName 供回显，编辑态无需反查商品库）；适用范围对 type 4
  不再手填，由活动商品自动带出。
- **买赠三件套**：结算页返回 `orderInfo.giftItems`（`availableGifts` 按真实库存过滤并
  回填名称/图片，查询失败 fail-closed 按无赠品）；提交同口径过滤后追加赠品明细；
  同车赠品不再丢弃（去掉 noneMatch filter，赠品独立成行，库存按 SKU 聚合校验已具备）；
  C 端确认页新增赠品区块。
- **锁重放一致**：`rebuildFromLocks` 按订单全部锁重建明细与金额，赠品从锁 ruleSnapshot
  恢复（历史单锁 LIMIT 1 重建丢赠品与其余明细）。
- **熔断**：`promotion_activity` 增 `total_budget`/`per_user_limit`/`gift_stock`
  （增量 SQL boot 124 / cloud 125，全量 SQL 已重新生成并校验）；reserve 按存量锁
  （LOCKED+CONFIRMED）聚合扣减，超限整单拦截；管理端表单补三个配置项。
- **验证**：promotion + order 模块测试全绿（PromotionEngineTest 33 条，含逐 SKU 阶梯价、
  校验拒绝全场范围、重放恢复赠品、限享/预算熔断新用例）；web-ele 与 uniapp type-check
  通过；改动文件 eslint/prettier 干净。
- **遗留**：二期 P1（配置选择器化、删 scopeType=4、dry-run、发布校验查售价、新人价
  管理入口等）与三期 P2 未启动。

## 8. 二三期实施记录（2026-10-14）

- **二期（P1）**：
  - dry-run：`POST /promotion/ship-activity/dry-run`（引擎 preview，不落库）+
    管理端「试算」对话框（模拟购物车/场景/靠港，展示命中活动、阶梯价覆盖与赠品）。
  - 发布校验查售价：阶梯价每档 unitPrice 必须低于 SKU 当前售价，商品库不可用阻断发布
    （fail-closed），消除「已发布但永不生效」。
  - 配置选择器化：赠品与范围 SKU 复用 `select-goods`；船舶/靠港/港口改可搜索下拉
    （船舶档案与已申报靠港数据源）；删除 scopeType=4 伪选项；已发布态禁用规则与范围
    控件；列表范围列回显名称/数量；表单文案披露「满件 = 单内同 SKU 合计」。
  - 保存失败提示由请求层全局拦截器承担，未重复加 toast。
- **三期（P2）**：
  - 锁补偿：`promotionLockCompensateJobHandler` 扫近 7 天已取消订单幂等补释放
    （注册 SQL boot 126 / cloud 128，全量 SQL 已重新生成校验）。
  - 买赠最优改赠品价值（售价 × 数量），售价查询失败降级按总数量。
  - 新人价（type 9）管理端入口：逐 SKU 新人价 + 注册时限，scope 由商品自动带出，
    校验复用 `validateNewbiePrice`。
  - 整船分摊口径写入 `docs/20-业务与数据/业务流程.md`「船供营销计价与分摊口径」。
- **验证**：promotion + order 模块测试全绿（PromotionEngineTest 35 条含买赠价值最优
  新用例；ShipActivityControllerTest 12 条含档价查售价与试算用例）；web-ele 与 uniapp
  type-check 通过；改动文件 eslint/prettier 干净。
- **备注**：实施期间并行任务的新人价 SQL 占用 boot 125 / cloud 126-127 编号，本治理的
  补偿任务 SQL 避让为 boot 126 / cloud 128；引擎试算缓存与监控指标为文档可选项，未实施。
