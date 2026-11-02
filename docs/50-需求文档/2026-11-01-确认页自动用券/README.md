# 确认页自动选最优券（兑现「领券后下单自动抵扣」）

> 日期：2026-11-01 ｜ 范围：C 端 `aryn-mall-uniapp` 确认订单页（前端）＋券可用性纯函数 ｜ 后端零改动
> 触发：用户问「提交订单下单成功，结算的时候能否按这个阶梯优惠券结算，下单功能有没有问题」。实测结论：券能按面额正确抵扣，但确认页默认不选券、用户不手动选就原价成单，与阶梯楼层「下单自动抵扣 / 下单自动减」文案不一致。用户在三个方案中拍板 **A. 实现确认页自动选最优券**。

---

## 1. 实测诊断（真实后端 + demo 数据）

| 场景（樱桃 29.90，95 折后 28.405） | 修复前行为 |
|---|---|
| 结算不传券 ×8 | 券额 0、应付 227.20（用户券包里有满减券也不自动用） |
| 结算带满199减40 ×8 | 券额 40、应付 187.20（选券后金额正确） |
| 带券提交 / 取消 | 订单 `coupon_price=40`、`payment_price=187.20`；券先冻结，取消后回滚可用 —— 下单链路本身无问题 |
| 门槛不足 / 篡改应付 | 服务端拒绝（60060 / 订单金额已变动）—— 风控口径正常 |

根因：`orderSettlement` / `orderCreate` 只在请求带 `couponUserId` 时才走 `orderCouponHandler`；确认页初始 `orderParams.couponUserId` 为空，券行只显示「选择优惠券」，用户不点就没有抵扣。主流电商（淘宝/京东/有赞）确认页默认勾选最优券，本系统缺这一步。

## 2. 方案（A：前端自动选最优券）

1. **自动选券时机**：首轮结算成功且券列表加载完成后（可用性判定同时需要结算下发的 `couponEligibleAmount` 与券列表，缺一不可）。
2. **选哪张**：`pickBestCoupon()` 从可用券里选「本单可抵扣金额最大」的一张：
   - 满减券取面额；折扣券按 `可叠金额 × (10 − 折扣) / 10` 估算（与服务端 `couponCompute` 同口径，仅用于排序）；
   - 排除不可用券（门槛 / 指定商品范围 / 活动禁叠）与「状态未使用但有效期已过」的券；
   - 抵扣金额相同优先快到期的，再按 id 稳定排序。
3. **只自动选一次**：`couponAutoApplied` 保证不反复改写；用户手动确认过（选券 / 换券 / 不使用）后 `couponUserTouched=true`，此后不再自动覆盖。
4. **金额以服务端为准**：选中后立即重新结算，页面展示与提交都用服务端返回的 `couponPrice` / `paymentPrice`；自动选券失败（极少数竞态被服务端判不可用）回退为「不使用券」并重算，保证页面仍可提交。
5. **可取消**：券弹层新增「不使用优惠券」显式出口；并且修复历史缺陷——弹层打开时回显当前已选券（此前重开弹层直接点「确认」会把已选券清掉）。

## 3. 改动文件

| 文件 | 改动 |
|------|------|
| `src/utils/coupon-usability.ts` | 新增 `estimateCouponDiscount()` / `pickBestCoupon()` 纯函数（复用现有 `getCouponUnavailableInfo` 可用性口径 + `daysUntil` 有效期判断） |
| `src/utils/coupon-usability.test.ts` | 新增 7 条自动选券单测（最大抵扣、门槛排除、禁叠排除、过期排除、同额先到期、折扣券对比、空列表） |
| `src/sub-pages/order/order-confirm/index.vue` | 首轮结算后自动选券 + 二次结算 + 用户操作保护；券弹层下发当前已选券；手动确认标记 touched |
| `src/sub-pages/order/order-confirm/components/CouponSelector.vue` | 新增 `couponUserId` prop（打开即回显）；新增「不使用优惠券」行；未选中回传空串清空 |
| `src/sub-pages/order/coupon-auto-select.contract.test.ts` | 契约测试：自动选券触发/只一次/用户优先/弹层回显/不使用出口/纯函数口径 |
| `src/locales/modules/checkout.{zh-CN,en-US}.ts` | 新增 `checkout.coupon.noCoupon`（不使用优惠券 / No coupon） |

**零后端 / 零接口 / 零 DB**：结算、下单、券冻结/回滚逻辑完全复用；`expectedPaymentPrice` 防篡改、门槛/叠加校验均在服务端。

## 4. 验证（真实后端 demo，390×844 H5）

| 场景 | 结果 |
|---|---|
| qty=1（28.40）进确认页 | 自动选中无门槛5元券：券行 `-￥5`、合计 **23.40**、已优惠 6.5 |
| 打开券弹层不动选择直接「确认」 | 已选券**不丢**（修复历史缺陷） |
| 选「不使用优惠券」→ 确认 | 合计回 28.40、券行回「选择优惠券」 |
| 再手动选券 → 确认 | 合计回 23.40（手动选择不被自动覆盖） |
| qty=8（227.20）进确认页 | 自动选中满199减40：券行 `-￥40`、合计 **187.20** |
| 提交订单 | 成单成功，订单详情实付 187.20；API 查订单 `coupon_price=40`、`payment_price=187.20`；取消后券状态回未使用 |
| 自动化 | `pnpm type-check` 0 error；`pnpm test:unit` **138 文件 / 1518 用例全绿**；MP 构建与独立验收见下方回写 |
| 全程 | 0 console error |

## 5. 边界与风险

- 自动选券只影响确认页默认值，**不改任何金额计算规则**；服务端仍是唯一权威（提交时 `expectedPaymentPrice` 校验、券状态/门槛/叠加/最低实付兜底不变）。
- 拼团/秒杀等禁叠活动商品：结算下发的 `couponStackable`/`couponEligibleAmount` 会让 `pickBestCoupon` 返回 null（或只剩可叠部分可用券），不会突破叠加规则。
- 用户主动选择永远优先：一旦在手选弹层点过「确认」，后续结算（切积分/配送方式）都不会再自动改券。
- 券列表接口失败时保持「未选券」，不阻断确认页金额。
- 未覆盖：小程序真机未目检（H5 + MP 构建产物）；自动选券在多券叠加场景下的「组合最优」（当前一次只选一张，与系统单券模型一致）。
- 回滚：按上表 6 个文件还原即可。

## 6. 验收状态（2026-11-01 19:40 回写）

**独立验收：PASS（0 P0 / 0 P1 / 7 条 P2，均不阻塞）**。报告：
`outputs/coupon-center-redesign/verification-auto-coupon.md`；证据：
`outputs/coupon-center-redesign/verify/auto-coupon-verify/`（自写 E2E 脚本、16 张截图、metrics、命令日志）。

- 自动化（验收人在最终工作树复跑）：`pnpm type-check` 0 error；`pnpm test:unit` **138 files / 1520 tests 全绿**；`pnpm build:mp-weixin` DONE，产物含 `pickBestCoupon` / `coupon-scroll__none`。
- H5 独立 E2E（8 场景全 PASS）：qty1 自动选 5 元（23.40）、首屏恰 2 次结算不循环；弹层回显不丢券；不使用→28.40、再手选→23.40；qty8 自动选 40 元（187.20）→ 提交成单实付 187.20 → 取消后订单 11、券 3→0；手选后切积分/配送不覆盖；en-US 正常；mock 禁叠不自动选；券列表接口失败安全。
- 环境噪音：验收期间 demo 账号被外部会话登录顶号 3 次，已用「换 token 重试 + 分窗口取证」规避并在报告记录。
- 已知 P2（后续可选优化，不影响交付）：`couponState.couponUserId` 与 `orderParams.couponUserId` 双口径（前者只写不读）；每次结算都重拉券列表；首屏两次全屏 loading（二次结算可静默）；自动重算窗口内点提交会被服务端以「价格变动」拒绝（不会错扣）；`couponUserTouched` 分支属防御性冗余。
- 说明：`checkout.footer.total/saved` 中英词条是**用户未提交的并发改动**（PaymentFooter 同步改），非本次新增；本次只新增 `checkout.coupon.noCoupon`。
