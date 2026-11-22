# 船供 B2B 活动与会员体系重构方案

> 日期：2026-11-14　状态：P0 已执行（boot）、P1 全部落地含缺口补齐与端到端实测（分支 `codex/b2b-activity-member`，20 个提交）
>
> **实施状态（2026-11-14 四续，与代码对齐）**：
> - ✅ P0 停量：boot 234/235 + cloud 249/250/251 增量 SQL（结束存量活动、停秒杀排期模板、隐藏运营菜单、停三个调度 Job）。**boot 库已执行并自检通过**（秒杀 0 进行/模板 0 启用/菜单隐藏/三调度停用/专区保留 2 条）；**cloud 三脚本待 cloud 环境启用时补跑**
> - ✅ P1 数据层：boot 236-239 + cloud 252-255 + 个人档位工具 240/256（协议价/档期/企业等级六张表+绑定表、账期预留、菜单 2180 段、对账 Job 注册、企业等级四档+示例权益种子）；租户白名单（boot yml + Nacos 种子 md5 同步）与 full SQL verify 通过
> - ✅ P1 后端：营销引擎协议价段（编码 8）/档期过滤/档期特供 type=12（限量熔断 quotaUnits + 发布治理校验）、订单计价链接入（enginePricedSkuIds/stackPriority/企业权益分轨 orderEnterpriseBenefitHandler/免邮分轨）、企业会员双轨（等级重算+成长值累计+RemoteEnterpriseUserService+月度对账 Job）
> - ✅ 管理端：价格政策/档期管理/企业等级/企业权益四页面 + 船供活动页增强（档期特供 type=12 表单区、阶梯价归属档期字段、满量优惠「满 N 件」件数维度）
> - ✅ C 端：协议价/档期 API 层、商品卡协议价角标（zone-price 契约+useZonePrices 补价，船上下文默认）、档期特供楼层 diy-trade-calendar（双端注册+预览 parity，registry 契约计数 11）、商详协议价/档期特供主价与角标、确认页活动标签（policy/calendarSpecial 双语言）
> - ✅ 企业会员展示与数据层：GET /vessel/app/vessels/{id}/enterprise-benefits（船成员校验+绑定表解析）+ 个人中心页头企业等级徽章
> - ✅ P1.5 清零（2026-11-15）：岸上企业权益端点（`GET /order/app/buyer-org/{id}/enterprise-benefits`，requireUsableForCart 校验）、个人中心企业权益弹层（徽章点击展开折扣/免邮/开票/专属客服，预留灰显）、协议价 dry-run（`/price-policy/{id}/dry-run` 五值预检 + 管理端「预检」弹窗）、会场页空态核查（四页 hall-state/z-paging 空态分支 + boot 接口空数据联验）
> - ✅ 审查修复（2026-11-15）：双轴审查（规范+规格）发现全部修复——协议价下单必失败（reserve 误扣预算）、企业权益回退个人轨套利、企业折扣叠阶梯价、特供名额多扣、企业成长值幂等/退款回扣/period 口径/月度汇总 Job、协议价越权查价净化、minQty 成交门槛、政策池消费、特供每人限购件数口径、退款回补限量（幂等列）、靠港档期 eta~etd 派生、免登四件套（含新增阶梯价查询接口）、P0 优惠券菜单恢复+两调度补停（boot 243/244、cloud 259/260/261）、管理端保存失败/编辑丢明细/批量导入/对象选择器、C 端价格五处同源+阶梯价全链+档期楼层修复+i18n/ESLint。详见需求记录「2026-11-15 审查修复」条目
> - ✅ 运营示例与手册（2026-11-15）：boot 246 / cloud 263 价格政策示例数据（船队年度框架价 / 单船酒水专项 / 快消 94 折补充协议 / 年度续签草稿，共 4 政策 31 明细，已执行进 boot 库并通过幂等与倒挂/成本校验）；配套 [价格政策操作手册](价格政策操作手册.md)
> - ⏳ 待运营/P2：cloud 三脚本补跑；企业等级门槛调整；个人档位 240 脚本执行前先与运营对齐（boot 库档位已被运营手工调整为 0.01/1.5万/3万/6万/10万，勿盲目覆盖）；协议价审批流、账期实施、企业返利生效、个人等级滚动年、提前购分级（均为 P2）
>
> **与方案正文的实现口径差异（已按此实现，非缺口）**：
> 1. §8 的「activity-strip 改造」以新建 `diy-trade-calendar` 楼层承接档期曝光替代；activity-strip 依赖的秒杀/拼团/折扣数据停运后整条自动隐藏，功能等价。
> 2. §8 的「个人中心双轨会员卡」落地为页头企业等级徽章（描边玻璃变体），权益明细展示后续按需扩展。
> 3. §5.1 的「个人档位小额重配」产出为可选工具脚本（240/256）而非直接执行——boot 库档位已被运营手工调整，覆盖需先对齐（见 ⏳ 待运营）。
> 4. 新人专享楼层组件定义仍在已发布装修页中（活动停运后接口返空整楼自动隐藏），不做数据清理。
>
> **验证基线**：全仓 Java 2725 例 0 失败 0 错误（审查修复后，含引擎/双轨/成员校验回归 13 例）；uniapp type-check 干净、1949 例全绿、改动文件 ESLint 干净；管理端 typecheck 通过、test:unit 1655/1657（2 例 preview-parity 为存量失败）、改动文件 ESLint 干净；full SQL verify 通过；**boot 环境端到端实测通过**（协议价真实下单成功+取消释放、特供限量扣减/限购拦截/取消与退款回补+MQ 幂等、越权查价净化、游客免登接口——详见需求记录「2026-11-15 审查修复」条目）
> 需求：停掉全部存量活动，面向「船供采购 / 海员个人 / 工厂餐厅采购」三类 B2B 客群重新设计活动体系与会员权益。不做小象/盒马式零售限时抢购形态。
>
> 已确认的四项决策：
> 1. **存量处置**：仅结束存量数据（保留代码与表，结束在跑活动、暂停运营入口），待新体系验证后再统一清理。
> 2. **会员双轨制**：海员个人走「等级+积分」消费型会员；船公司/工厂/餐厅按采购主体走「企业等级+协议权益」采购型会员。
> 3. **账期/授信**：本期设计预留（数据模型+权益位），二阶段实施。
> 4. **协议价**：纳入一期（按企业/船舶/客群的专属合同价）。

---

## 1. 背景与目标

当前营销体系沿用了零售电商玩法（整点秒杀、拼团裂变、领券中心、新人获客价），与真实客群的采购行为错位：

- 船舶补给按**靠港窗口**采购，一次买够一个航次（15-45 天），批量大、整箱整件、决策链是管事/大厨/确认人；
- 工厂餐厅按**周/月节奏**周期性复购，原材料大批量，重视价格稳定、对账开票；
- 海员个人购买量小但价格敏感，随船补给送达，适合轻量的个人会员与积分。

目标：把「营销」从**限时情绪刺激**重构为**分层价格体系 + 档期化经营节奏 + 双轨会员权益**，让价格能力（协议价/阶梯价/满量优惠）成为 B2B 转化的主引擎。

## 2. 现状盘点（重构地基）

### 2.1 活动资产（aryn-promotion 模块）

| 类型 | 表 | 形态 | 重构判定 |
|---|---|---|---|
| 秒杀 | seckill_activity/session/session_template/goods/order/subscribe | 场次+每日排期模板+Redis Lua 预扣+整点开抢提醒 | **停运**。能力由「档期特供」替代（DB 原子计数限量即可，无需 Redis 预扣） |
| 限时折扣 | discount_activity/goods/stock_lock | 打折/减价/固定价 | **停运**，被阶梯价/协议价/档期特供覆盖 |
| 拼团 | group_buy_activity/record/member | 开团/参团/虚拟成团 | **停运**，B2B 不适用 |
| 优惠券 | coupon_info/goods/user（满减券/折扣券） | 券范围∩可叠明细、最低实付兜底 | **保留改造**：下线领券中心，保留定向发放（企业满减券、个人权益券） |
| 新人价 | promotion_activity type=9（首件混价 discountedQuantity） | 个人/船供/企业三轨首单 | **停运**。首单资格机制（promotion_lock 主体作用域）保留代码，P2 评估「企业开户礼包」 |
| 会员价 | promotion_activity type=10 SYS_MEMBER_ZONE + goods_sku.is_member_item | 身份专区价，成本兜底 | **保留**，归入双轨会员的身份价体系 |
| 出清价 | promotion_activity type=11 SYS_CLEARANCE_ZONE + clearance_rule（距到期 ≤N 小时分层再折） | 临期时间分层 | **保留**，食品供应链的临期处理刚需 |
| 阶梯价 | promotion_activity type=4 rules.skuLadders | 按 minQty 分档单价 | **保留增强**：归属档期、C 端展示、SQL 预算熔断复用 |
| 买赠 | promotion_activity type=5 + order_item.gift_flag | 门槛档位赠品 0 元成行 | **保留**（买 10 箱送 1 箱） |
| 整船满减 | promotion_activity type=7 rules.tiers | minAmount→discountAmount 整单分摊 | **保留泛化**为「满量/满额优惠」（增加 minQty 维度） |
| 价格快照 | goods_sku_price_daily（每日 01:30 快照）+ 近14天最低价标签 | MIN(标价，出清，秒杀) 口径 | **保留**，秒杀价来源随秒杀停运自然消失 |

统一引擎：`PromotionEngineServiceImpl.preview()` 单入口试算、`promotion_lock` 锁流水（preview→reserve→confirm→release）、`promotion_snapshot` 订单快照、`promotionLockCompensateJobHandler` 补偿。**新体系全部落在这套引擎上，不另起炉灶。**

### 2.2 会员资产（aryn-user 模块）

| 能力 | 现状 | 位置 |
|---|---|---|
| 个人等级 | member_level 5 档种子（新客0/银卡100/金卡500/铂金2000/钻石5000，累计消费口径），事件驱动重算 | `MemberLevelServiceImpl#recalculateLevel` |
| 个人权益 | member_benefit 5 类：折扣(取min)/免邮/专属券/积分倍率/抵扣上限 | `MemberBenefitServiceImpl#getUserBenefits` |
| 积分 | 注册/下单返/抵现(仅 scene=1、20% 上限)/兑券/过期 FIFO/签到 | `PointsGrantServiceImpl`、`orderPointsDeductHandler` |
| 储值 | 充值套餐+赠送，**余额只进不出（未接订单支付）** | `RechargeOrderServiceImpl`、`BalanceRecordServiceImpl` |
| 标签 | member_tag + user_tag_rel | `MemberTagServiceImpl` |
| 岸上企业 | buyer_org（orgType 1企业/2工厂/3园区餐厅）+成员角色+审核+邀请码 | `BuyerOrgServiceImpl` |
| 企业首单 | 按 buyer_org/vessel 双主体资格 + promotion_lock 防双享 | `RemoteBuyerProfileServiceImpl` |
| 分销/销售提成 | 分销（社交裂变佣金）与销售提成互斥 | promotion 模块 sales*/distribution* |

### 2.3 计价链（OrderPriceComputeService，2003 行）

成交基价链：**拼团(4) > 秒杀(3) > 会员/出清价(2) > 新人价(1) > 限时折扣(0)**，随后减法链：会员折扣（会员价取优+成本兜底）→ 优惠券 → 整单优惠分摊 → 运费（场景化 FreightRule，到船 way=4 恒免邮）→ 积分抵现（仅 scene=1）。下单 `expectedPaymentPrice` 一致性校验 + 营销 reserve + `promotion_snapshot` 落库。

关键可复用机制：`hitAppliesToRow` 行级判定、新人价拆行（applyLadderOverrides）、`stackPriority` 优先级表、整单分摊尾差补齐、专区价批量补价 `useZonePrices`。

### 2.4 场景基建

`PurchaseSceneEnum`：1 海员个人 / 2 船供采购（强制 way=4 到船）/ 3 企业采购（强制 way=3 岸上公司配送）。订单与明细全链路携带场景快照。**这是新体系分轨生效的天然挂点。**

## 3. 客群画像与经营诉求

| 客群 | 场景 | 采购特征 | 核心诉求 |
|---|---|---|---|
| 船舶补给 | scene=2，挂靠港窗口 | 整箱整件大批量、一次买够一个航次、管事/大厨决策、业务员代下单 | 价格稳定+量价阶梯、按船期档期备货、整船优惠、对账 |
| 海员个人 | scene=1 | 中小批量、价格敏感、随船送达 | 个人价、积分、免邮门槛 |
| 工厂/餐厅 | scene=3，buyer_org | 周期性复购、原材料大批量、采购员下单 | 协议价、阶梯价、满额配送、开票、月结（预留） |

经营节奏从「整点秒杀」改为**档期制**：以周度档期 + 靠港档期组织选品与价格，运营按档期上架特供商品。

## 4. 新价格与活动体系

### 4.1 设计原则

1. **单一成交价**：每个 SKU 的成交基价只来自一个价格来源，优先级取高，不做基价叠加；订单级让利（满量优惠、券）可叠在基价之上。
2. **档期化经营**：活动归属采购档期，替代整点场次。
3. **场景分轨**：活动可按 purchaseScene 圈定生效客群（引擎 scopeType=4 已支持）。
4. **展示价=可成交价**：预算/限量熔断前置剔除（引擎已有），避免展示价与结算价漂移；`expectedPaymentPrice` 校验兜底。

### 4.2 成交价体系与优先级

| 优先级 | 价格层 | 说明 | 承载 |
|:--:|---|---|---|
| 9 | **协议价**（新增） | 指定企业/船舶/客群的专属合同价，长期有效 | price_policy（新表） |
| 8 | **档期特供价**（替代秒杀） | 档期内的限量特惠 SKU，活动级限量+每人限购 | promotion_activity type=12（档期特供） |
| 7 | **阶梯价** | 按数量分档单价（买得越多单价越低） | promotion_activity type=4（增强） |
| 6 | **身份价**（会员价/出清价） | 等级身份价 + 临期出清价，逐 SKU 取低 | promotion_activity type=10/11（保留） |
| — | 售价 salesPrice | 兜底 | goods_sku |

订单级让利（不改动单价，按现有减法链）：**满量/满额优惠**（type=7 泛化）→ 优惠券（定向发放）→ 运费规则 → 积分抵现（仅个人）。买赠独立判定走赠品行。

历史类型（拼团/秒杀/折扣/新人价）代码保留、无数据，计价优先级表中自然失效，`stackPriority` 调整为上表映射。

### 4.3 活动类型定义（一期运营可用的 5 类）

| 类型 | 规则要点 | 管理入口 |
|---|---|---|
| 协议价 | applyType：1 指定 buyer_org / 2 指定 vessel / 3 客群标签；priceMode：固定价或折扣率；有效期+起订量提示；同 SKU 多政策冲突按 priority 取大、同级取低价 | 价格政策页（新建） |
| 档期特供 | 归属档期、活动级限量（件数 quota + 原子计数）、perUserLimit、按场景圈定；无整点倒计时，展示「档期供应·余量 N」 | 档期管理页（新建） |
| 阶梯价 | skuLadders 分档单价；归属档期可选；C 端展示「买 N 件单价 X」阶梯表 | 船供活动页 type=4 区增强 |
| 满量/满额优惠 | tiers 支持 minAmount→discountAmount（现状）+ minQty→discountAmount（新增件数维度）；按场景/船舶/主体圈定 | 船供活动页 type=7 区增强 |
| 买赠 | 门槛档位赠品（买 N 送 X），赠品库存 fail-closed | 船供活动页 type=5 区 |

### 4.4 采购档期模型（promo_calendar）

- `anchorType`：1 周度档期（周一到周日）/ 2 靠港档期（挂 vessel_call，窗口=eta~etd）/ 3 自然月 / 4 自定义时间窗。
- 活动挂 `calendarId`；档期未开始/已结束则其下活动不参与试算（引擎前置过滤）。
- C 端档期楼层按档期聚合展示（「本周船供特供」「靠港 xx 港备货档期」）。

### 4.5 互斥叠加规则（简化现有链）

- 协议价客户与档期特供冲突：取协议价（合同稳定性优先）；协议价 SKU 不参与档期特供限量占用。
- 阶梯价与身份价：逐 SKU 取低（引擎 applyBestLadderPrice 与 applyBestIdentityPrice 现有取低逻辑沿用）。
- 满量优惠门槛基数排除已享活动价 SKU（`wholeDiscountEligibleRow` 口径沿用）。
- 个人场景叠加积分抵现，企业场景禁积分（现有规则）；企业满减券开放 scene=3。
- 所有命中仍走 promotion_lock preview→reserve→confirm→release 与 promotion_snapshot 落库，订单快照闭环不变。

## 5. 新会员体系（双轨）

### 5.1 个人会员（海员，scene=1）

- **等级**：沿用 member_level 机制，P1 重新配置面向个人的小额档位（如 新客 0 / 常购 500 / 资深 2000，累计消费口径）；P2 升级为「近 12 个月滚动消费」口径（member_level.conditionType 增 3=滚动年，月度快照 Job）。
- **权益**（member_benefit 保留）：个人会员价专区、积分倍率、免邮门槛下调、权益券；**新增**：档期特供提前购（提前 N 小时可见，P2 可选）。
- **积分**：闭环保留（注册/下单返/抵现 20% 上限/兑券）；签到降为可选开关。

### 5.2 企业会员（船公司/工厂/餐厅，scene=2/3）

主体挂点复用现有两套：岸上=buyer_org，船=vessel（船公司维度 P2 在船舶档案补 company 字段后聚合）。

- **企业等级** enterprise_level（新表）：按「近 12 个月主体采购额」分档（种子：注册客户 0 / 合作客户 3 万 / 核心客户 10 万 / 战略客户 30 万，具体档位运营定）。
- **升级计算**：月度 Job 汇总 order_info（scene=3 by buyer_org_id、scene=2 by vessel_id，pay_status=1）到 enterprise_growth 月度聚合表 → 滚动 12 个月求和 → 重算等级（参照 `MemberLevelServiceImpl#recalculateLevel` 模式）+ enterprise_level_record 流水。主体表各加 `level_id` 冗余列。
- **企业权益** enterprise_benefit（新表，模式复制 member_benefit）：
  | benefitType | 权益 | 计价消费点 |
  |---|---|---|
  | 1 | 企业折扣率 | 与身份价/阶梯价取优（复用 resolveMemberPriceTakeBetter 模式） |
  | 2 | 免邮/邮费门槛减免 | freight handler 按场景分轨（scene=2 到船恒免邮不变；scene=3 企业免邮门槛） |
  | 3 | 协议价政策池 | 价格政策按等级自动套用 |
  | 4 | 开票资质 | 对账/开票资料（P2 联动） |
  | 5 | 专属客服/业务员 | 展示与 IM（现有客服链路） |
  | 6 | 返利率（预留） | 二期：季度返点入预付款账户 |
  | 7 | 账期天数（预留） | 二期：授信额度+月结 |
- **权益分轨消费**：订单计价按 purchaseScene 取权益——scene=1 读 member_benefits（现状），scene=2/3 读 enterprise benefits（主体维度），不交叉取优，避免个人等级与企业等级套利。

### 5.3 积分/储值/返利

- 企业客户不做积分抵现（现状规则保留）；企业让利走协议价/满量/返利。
- 储值余额现状只进不出：一期不动；二期与账期合并为「企业资金账户」（预付款支付 + 授信账期 + 月度对账单）。
- 分销（社交裂变）停运入口（菜单隐藏）；销售提成（船供 CRM）保留。

### 5.4 账期/授信预留设计（二期实施）

数据模型一期建表不启用：`enterprise_credit_account`（subject 维度授信额度/已用/冻结/状态）、`enterprise_statement`（月度对账单：期初/出账/回款/结转）、还款核销流水。订单侧预留：createOrder 增 paymentType=4 账期（仅授信主体 + 额度校验 +风控开关），收款走对账核销。一期只做：建表种子、管理端只读视图、权益位「账期天数」配置不生效。

## 6. 数据模型（boot/cloud 双模式增量 SQL）

新增（编号从当前最大递增，参照 27delivery_fulfillment 增量风格，可重复执行）：

```sql
-- 1) 价格政策（协议价）
price_policy        (id, tenant_id, policy_name, apply_type 1企业/2船舶/3客群标签, apply_value,
                     price_mode 1固定价/2折扣率, priority, start_time, end_time,
                     status 0草稿/1生效/2停用, 审计字段, del_flag)
price_policy_item   (policy_id, sku_id, spu_id, policy_price, policy_rate, min_qty 起订提示,
                     uk(tenant_id, policy_id, sku_id))

-- 2) 企业等级双轨
enterprise_level            (id, tenant_id, level_name, sort_order, condition_value 近12月采购额, status)
enterprise_benefit          (id, tenant_id, benefit_name, benefit_type 1..7, benefit_value, status)
enterprise_benefit_level_rel(benefit_id, level_id)
enterprise_level_record     (subject_type 1岸上企业/2船舶, subject_id, old_level_id, new_level_id, change_reason)
enterprise_growth           (subject_type, subject_id, period yyyy-MM, consume_amount,
                             uk(subject_type, subject_id, period))
ALTER buyer_org    ADD level_id BIGINT NULL;
ALTER vessel_info  ADD level_id BIGINT NULL;

-- 3) 采购档期 + 引擎增强
promo_calendar      (id, tenant_id, calendar_name, anchor_type 1周度/2靠港/3自然月/4自定义,
                     anchor_ref vessel_call_id 等, start_time, end_time, status, remark)
ALTER promotion_activity ADD calendar_id BIGINT NULL COMMENT '归属档期';
ALTER promotion_activity ADD total_quota INT NULL COMMENT '档期特供活动级限量(件)';
ALTER promotion_activity ADD consumed_quota INT NOT NULL DEFAULT 0;

-- 4) 账期预留（只建表，不启用）
enterprise_credit_account / enterprise_statement / statement_item

-- 5) 快照编码扩展
promotion_snapshot.activityType 注释新增：8 协议价（规则快照存 policyId）
```

同步动作：boot 脚本登记进 `db/boot/build-full-sql.mjs` sections 并重新生成 `aryn_boot_full.sql`（`verify-full-sql.mjs` 校验）；cloud 同名脚本 + 菜单脚本双份；`promotion_activity` 新增类型/表进租户拦截表白名单核对。

## 7. 计价链改造点

| 改造 | 位置 | 要点 |
|---|---|---|
| 协议价试算段 | `PromotionEngineServiceImpl` 新增 applyBestPolicyPrice（最优先段） | 命中集合=用户上下文（userId→buyer_org/vessel/标签）命中的生效政策；快照走 promotion_lock 同口径 |
| 档期特供 type=12 | 引擎新增类型处理 | 限量走 total_quota 原子计数（复用 144/145 预算原子化模式）；档期窗口前置过滤 |
| 阶梯价增强 | type=4 处理段 | calendarId 过滤、与身份价取低沿用 |
| 满量优惠件数维度 | type=7 applyShipWholeDiscount | tiers 增 minQty 判定（明细聚合件数） |
| stackPriority 重排 | `OrderPriceComputeService` L332 | 协议价(9) > 档期特供(8) > 阶梯(7) > 会员/出清(6) > 历史(拼团4/秒杀3/新人1/折扣0) |
| 企业权益消费 | `orderMemberBenefitHandler` 旁新增 enterprise 分轨 | scene=2/3 读 enterprise benefits；企业折扣率与活动价取优不叠加；免邮走 freight handler 场景分轨 |
| 结算展示 | fillStackPolicyDisplay / 商品卡 | 行级 activityType 增 policy/ladder/seckill-supply 标签；阶梯价进度提示（「再买 N 件单价降至 X」） |

## 8. 三端改造清单

### 管理端（apps/web-ele）

- 新建：`views/promotion/price-policy`（价格政策列表+编辑器：对象圈定+SKU 明细批量导入）、`views/promotion/activity-calendar`（档期管理）、`views/user/enterprise-level`（企业等级+权益，模式复制 member-level/benefit 页）。
- 增强：船供活动页（ship-supply）补档期特供 type=12 表单区、阶梯价/满量优惠增强字段（档期/件数维度）。
- 菜单：新增「价格政策/档期管理/企业会员」；隐藏「秒杀管理/折扣管理/拼团/领券中心/新人专享/分销管理」（逻辑隐藏不删菜单行）。
- 装修器：营销组件库移除新人专享入口默认项；goods-tabs 专区语义保留 member/clearance；新增「档期特供」楼层组件（后述）。

### C 端（aryn-mall-uniapp）

- 楼层：`diy-activity-strip` 秒杀/拼团/折扣卡改为「档期特供/阶梯价专区」入口（无活动自动隐藏机制沿用）；`diy-newbie-floor` 下线（发布页面清理）；新增 `diy-trade-calendar`（档期楼层：档期名+倒计时窗+特供商品横滑）。
- 商品卡：`zone-price.ts` badgeKind 扩展 `policy`（协议价）/`ladder`（阶梯价）；商详新增阶梯价表格（数量-单价）与协议价标识、档期特供余量展示。
- 个人中心：会员卡双轨——scene 上下文判断展示「个人等级卡」或「企业等级卡」（buyer_org/vessel 上下文），企业卡展示企业权益与专属客服；积分/余额资产条仅个人态展示。
- 结算/购物车：阶梯价进度提示、协议价行标签、企业场景隐藏积分入口（现有规则）。

### 后端（aryn-mall-java）

- promotion-biz：PolicyPriceService、TradeCalendarService、引擎扩展、AppPolicyController（协议价批量查价）、AppCalendarController（档期+特供列表）。
- user-biz：EnterpriseLevelService（重算+Job）、EnterpriseBenefitService、RemoteEnterpriseUserService（Dubbo 接口入 *-api）。
- order-biz：计价链改造如 §7；order_info 快照不变（promotion_snapshot 承载）。
- xxl-job：enterpriseLevelRecalcJobHandler（月度 1 日重算）、promoCalendarStatusJobHandler（可选，档期状态派生）。

## 9. 存量处置（P0 立即执行清单，纯数据+配置动作）

> 用户已确认「仅结束存量数据，保留代码」。以下按可重复执行 SQL + 调度中心操作准备，执行前先在 boot 库演练。

1. **活动数据收口**（boot/cloud 各一份增量 SQL）：
   - `seckill_activity.status→2 已结束`；`seckill_session_template.status→禁用`（**关键：不停模板则 seckillScheduleJobHandler 每 60s 继续生成新场次**）；未开始场次置结束。
   - `discount_activity.status→3 已暂停`；`group_buy_activity.activity_status→2 已结束`。
   - `coupon_info.status→1 禁用`（停发；存量未使用券自然过期，不强制作废）。
   - `promotion_activity` 中手工运营活动（type 4/5/7/9，status∈{1,2}）→3 已暂停；**保留 SYS_MEMBER_ZONE / SYS_CLEARANCE_ZONE 系统托管活动**（专区体系新方案继续沿用，会员价待企业等级上线后重挂门槛）。
2. **XXL-Job 停用**（调度中心操作，不改代码）：seckillScheduleJobHandler、seckillStatusJobHandler、seckillOrderExpireJobHandler、discountStatusJobHandler、groupBuyExpireJobHandler；保留 promotionLockCompensateJobHandler（泄漏释放）、skuPriceDailySnapshotJobHandler、mallZoneSyncJobHandler。
3. **管理端菜单隐藏**：秒杀管理、折扣管理、拼团、领券中心、新人专享、分销管理（sys_menu visible 置隐藏，含清 menu_cache）。
4. **装修页面清理**：排查已发布首页/微页面中含 activity-strip/newbie-floor 的版本——activity-strip 无进行中活动自动隐藏、newbie-floor 活动结束后接口返空整楼隐藏（验证空态行为后，运营替换为占位楼层或重新发布）。
5. **C 端验证**：活动会场页（seckill/discount/groupbuy/newbie）空数据态显示正常空态而非报错；`/app/seckill/sessions` 等接口返空不阻塞首页。

2026-11-20 修复商品详情页残留的公开领券链路。商详加载时停止调用 `/promotion/app/couponinfo/page`，并移除公开领券入口与弹层。定向权益券按 §2 的范围发放，适用券在结算页使用。

## 10. 分期实施计划

| 期 | 内容 | 验收 |
|---|---|---|
| **P0 停量**（1-2 天） | §9 全部动作 | 线上无零售活动形态可运营；C 端无活动入口残留；补偿 Job 正常 |
| **P1 新体系一期** | ①价格政策全链路（表+引擎最优先段+管理端+C 端协议价角标）②档期模型+档期特供 type=12（表+引擎+档期管理页+档期楼层）③阶梯价增强（档期归属+C 端阶梯表+进度提示）④满量优惠件数维度 ⑤企业等级+企业权益（buyer_org/vessel 双挂点+月度重算 Job+计价分轨消费+企业会员卡）⑥双模式增量 SQL+full SQL 重生成 ⑦管理端菜单重组 | `mvn test -pl aryn-boot -am` 全绿；`pnpm check:type && test:unit && lint` 三工程全绿；boot/cloud 双库增量 SQL 可重复执行；双模式请求路径验证；协议价/档期特供试算 dry-run 用例 |
| **P2 资金与深度** | 账期授信（额度校验+月结对账+还款核销+paymentType=4）、企业返利入预付款、储值余额支付打通、个人等级滚动年口径、企业首单开户礼包评估、船公司主体聚合（船舶档案 company 字段）、协议价审批流 | 另行立项评审 |

依赖关系：P1 内 ①②③ 可并行开发；⑤ 依赖 ① 的权益位（协议价池）；SQL 统一一批出，避免编号碎片。

## 11. 风险与验证要点

1. **秒杀排期模板必须先停**，否则调度 Job 持续生成新场次（P0 第一刀）。
2. **会员价过渡**：企业等级上线前，企业客户当前可能持有个人 member_level——双轨权益按场景分轨消费，个人等级保留不动，避免权益套利与体验回退。
3. **协议价与阶梯价冲突**：发布前用 dry-run 试算（复用 ShipActivityController 冲突检索/dry-run 机制），同 SKU 多政策给出命中解释。
4. **价格快照口径**：协议价/档期特供是否入 goods_sku_price_daily？协议价是主体 scoped（同会员价口径不入）；档期特供价是全局价，**应入**（快照 Job 增拉取来源），否则「近14天最低价」合规标签漏算。
5. **expectedPaymentPrice**：档期特供限量耗尽时试算前置剔除保证展示价=可成交价（引擎已有机制），下单二次校验兜底。
6. **存量退款/锁释放**：引擎 release 与补偿 Job 不动；结束的活动如仍有 LOCKED 锁由 promotionLockCompensateJobHandler 收口。
7. **双模式合规**：所有新表/新接口按 AGENTS.md 双模式规则出双份 SQL、Controller 首段为微服务域、C 端免登接口（协议价批量查价/档期列表）登记双端白名单。
8. **验证基线**：后端 `mvn test -pl aryn-boot -am`；UI `pnpm check:type && pnpm test:unit && pnpm lint`；uniapp `pnpm type-check && pnpm test:unit`；涉及装修组件改动跑 preview-parity 契约测试。

## 12. 待定项（实施前需运营口径）

- 企业等级四档的具体门槛金额与名称（方案给种子建议值，运营可调）。
- 档期特供是否需要「提前购」分级（战略客户提前 N 小时）。
- 协议价是否需要审批流（P1 先做直发，审批流列 P2）。
- 优惠券企业满减券的发放形式（定向发放即可，还是需要企业券包）。

---

## 13. P2 评估（2026-11-15）

> 评估口径：**业务价值**（对三类客群的转化/留存/风控意义）× **实现成本**（S≈天级 / M≈周内 / L≈多周）× **依赖前置** × **风险**。P2 不是「P1 的尾巴」，而是四个相对独立的专题包，建议按包立项而非逐项排期。

### A 包：资金域（P2a，建议整体立项，串行依赖）

| 项 | 价值 | 依赖 | 成本 | 风险 | 评估结论 |
|---|---|---|---|---|---|
| A1 储值余额支付打通 | **高**——现状余额只进不出，充值是「死钱」，用户侧投诉风险真实存在（充值套餐 5000/10000/20000 档已在跑） | pay 域加余额渠道；`BalanceRecord`「2-消费」链路已就位（changeBalance 带非负守卫） | **M**：支付域渠道路由 + 余额扣减原子性 + 退款回补 | 中：组合支付边界先定（建议一期纯余额全额抵扣，不做混合） | **建议 P2a 第一步**。替代方案是下线充值入口——但已有充值用户在库，伤害更大 |
| A2 账期授信（额度校验+订单占用） | **高**——B2B 大客户转化最强杠杆（船公司/工厂月结是行业惯例）；表已预留（enterprise_credit_account，frozen/used 结构齐） | A1 资金账户口径先行；createOrder 加 paymentType=4 校验 `used+frozen+本单 ≤ limit`；发货后转应收 | **M-L**：订单占用/释放（frozen 原子增减）+ COD 式状态机 + 逾期停用闸门 | **高**——赊销=应收账款风控，必须线下授信合同背书 | **核心项**。分两步：先额度校验+占用（可控），后对账 |
| A3 月结对账+还款核销 | 高（A2 的运营闭环） | A2；对账单生成 Job（enterprise_statement 已预留）+ 回款录入核销 | **M**：按 period 汇总出账 + 管理端对账页 + 核销回写 | 中：出账口径（确认收货 or 发货）需财务定 | A2 上线一个对账周期后做 |
| A4 企业返利生效 | 中-高（大客户留存强杠杆，季度返点到预付款账户） | **依赖 A1/A2 资金账户**（返点入预付款）+ enterprise_benefit type=6 已预留（C 端弹层已留「即将上线」位） | **M**：季度汇总 Job（enterprise_growth 已按月累计）+ 返点入账 + 有效期 | 中：返点有效期/清零口径要定 | A2 落地后做 |

### B 包：主体与客群成熟项（P2b，按客户信号触发）

| 项 | 价值 | 依赖 | 成本 | 风险 | 评估结论 |
|---|---|---|---|---|---|
| B1 岸上企业上下文 store | 中——解锁岸上展示挂点统一（个人中心岸上企业徽章、协议价上下文自动带 buyerOrgId、共享车页简化）；类比 shipContextStore 模式 | 无（buyer_org_member 数据已齐） | **S**：持久化 store + 登录/切换失效 + 2-3 个挂点接入 | 低 | **P2b 第一步**，性价比最高；岸上权益端点（P1.5 已做）等它挂 UI |
| B2 船公司主体聚合 | 高（真实船供业务常见多船经营人集中采购——等级/协议价按公司统一而非单船） | 船舶档案补 company 字段；enterprise_level_binding 增 subject_type=3（船公司）；协议价 apply_type 增船公司维度；growth 聚合口径改 | **L**：主体聚合口径波及引擎/订单快照/结算 | 中：单船→公司聚合的采购额归属口径要财务与运营共同定 | **有真实多船客户时优先立项**；单船演示阶段不启动 |
| B3 企业首单开户礼包 | 中（新企业首购转化；firstOrder 机制代码已在——subject 唯一锁已建） | 运营策略定（试用价/满减券/权益体验期） | **S-M**：复用 firstOrderOnly + subjectType=2/3 现成机制 | 低（防双享已由 promotion_lock 兜住） | 运营策略项，策略定了随时可做 |

### C 包：运营工具与策略（P2c，低优先，按信号触发）

| 项 | 价值 | 成本 | 评估结论 |
|---|---|---|---|
| C1 个人等级滚动年口径 | 中低——现状累计消费重算即可降；海员小额高频客群对滚动年敏感度有限；且 boot 库档位已被运营手工调整为 0.01/1.5万/3万/6万/10万 五档口径 | **M**（消费按 period 化 + 档位重配）；**前置：先与运营对齐 240 号脚本口径** | **暂缓**——运营已自调档位，等流失客激活诉求再做 |
| C2 协议价审批流 | 低（当前租户小团队直发 + dry-run 预检已够；多角色提报场景才需要） | **M**（审批状态机+通知） | **暂缓**——dry-run（P1.5 已做）覆盖主要配置风险 |
| C3 提前购分级（战略客户提前 N 小时看档期特供） | 低-中（会员尊享感） | **S-M**（楼层/接口按等级过滤可见性） | 可做可不做；建议与 B1 一起顺带 |
| C4 MOQ/箱规回归 | 中（整箱起订是 B2B 真实约束，防碎片订单；order_item 快照字段在但无写入方——2026-09-29 随船供包装资料下线） | **M**（SKU 轻量 moq/step 字段 + 加购校验 + C 端提示；不做大包资料回归） | **运营收集诉求后做**；阶梯价本身已在引导批量 |
| C5 E2E 数据清理 | 卫生项 | **S**（DELETE 9000000000000000{201,202,210,211,220,230,001}） | 演示库保留无害（人工验收用得着）；正式生产初始化时清理 |

### 建议排期

```
P2a 资金域（一个专题包，串行）：A1 余额支付 → A2 账期额度+占用 → A3 对账核销 → A4 返利
P2b 客群成熟项：B1 岸上上下文（随时可插队，S）→ B2 船公司聚合（有客户信号再启动）→ B3 开户礼包
P2c 工具与策略：全部按运营信号触发，不主动排期
```

### 触发信号（什么出现就启动什么）

- 用户问「充了的钱怎么用」→ A1
- 有船公司/工厂谈月结 → A2（先签授信合同）
- 运营提「多船统一价格」→ B2
- 出现配置错误事故且 dry-run 未覆盖 → C2
- 运营提出整箱起订 → C4

### 明确不建议做的

- **组合支付（余额+在线混合）**：边界复杂，一期纯余额全额抵扣足够
- **自动授信提额**：授信额度必须线下合同背书，系统不做自动提额
- **签到/充值策略调整**：与 B2B 客群无关，维持现状
