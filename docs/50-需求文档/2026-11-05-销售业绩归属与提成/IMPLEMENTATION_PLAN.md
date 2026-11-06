# 销售业绩归属与提成 — 实施计划与接口契约

> 依据：[README.md](README.md)（2026-11-05 需求文档）
> 本文是开发期的**唯一共享契约**：后端、管理端、小程序均按此实现，字段名/路径不得私自变更；确需变更先改本文件并通知 Lead。
> 阶段：P0 归属打通 → P1 提成账本 → P2 分销改券 → P3 获客与报表。

---

## 1. 总览与分工

| 工作流 | 写作用域（互斥） | 内容 |
|---|---|---|
| Lead | `aryn-promotion-api/**`、`aryn-order/**`、`aryn-boot/src/main/resources/application.yml`、`docs/**` | 契约（实体/DTO/枚举/Dubbo）、订单域改造、集成审查、文档回写 |
| `sql-db` | `aryn-mall-java/db/**` | boot/cloud 增量 SQL、菜单/字典/租户表/定时任务注册、全量 SQL 重建校验 |
| `be-sales` | `aryn-promotion-biz/**`（除券相关新文件）、`AppDistributionController.shareBind` 变更 | P0/P1 销售域：销售员、客户归属、认领转交、业绩单、退款、台账、报表、与分销互斥 |
| `be-coupon` | `aryn-promotion-biz/**` 券相关新文件、`DistributionWithdraw*` 改造、`aryn-user-biz/**` 推广码 | P2 券包/兑换/提现下线；P3 推广小程序码 |
| `fe-admin` | `aryn-mall-ui/**` | 管理端：销售管理 6 页 + 分销 2 页 + 提现只读 + 订单归属列/筛选/导出 |
| `fe-uniapp` | `aryn-mall-uniapp/**` | 分销中心改券兑换、销售工作台、推广码展示、分享绑定上报 |

**依赖顺序**：Lead 先落 `aryn-promotion-api` 契约 → 后端并行；前端按本文契约先行。SQL 独立先行。
**每日集成点**：Lead 在每阶段末跑编译/测试并回写本文「进度」小节。

---

## 2. 数据模型（SQL 由 `sql-db` 落地，字段名必须一致）

新表全部在 promotion 域（boot：`aryn_boot`；cloud：`aryn_promotion`）。统一字段：
`id varchar(32) PK`、`create_by/create_time/update_by/update_time`、`del_flag char(2) default '0'`、`tenant_id varchar(32) not null`、`version int default 0`。
金额 `decimal(10,2)`，比例 `decimal(10,4)`，时间 `datetime`。**禁止物理删除**。

### 2.1 `sales_staff` 销售员

| 列 | 类型 | 说明 |
|---|---|---|
| sys_user_id | varchar(32) NOT NULL | 后台员工账号（RemoteSysUserService 校验存在） |
| staff_name / phone / dept_id / dept_name | varchar(64)/(32)/(32)/(64) | 员工快照 |
| member_user_id | varchar(32) NOT NULL | 销售本人的小程序会员 ID；**租户内唯一**（生成分享参数用） |
| commission_rate | decimal(10,4) NULL | 个人提成比例 0–1；NULL 取全局默认 |
| status | char(2) NOT NULL default '0' | 0 在职 / 1 离职 |
| leave_time | datetime NULL | 离职时间 |
| remark | varchar(255) | |
唯一键：`uk_sales_staff_member (tenant_id, member_user_id)`；普通键：`idx_sales_staff_sys_user (tenant_id, sys_user_id)`、`idx_sales_staff_status (tenant_id, status)`。

### 2.2 `sales_customer_bind` 客户归属

| 列 | 说明 |
|---|---|
| customer_user_id | varchar(32) NOT NULL 客户会员 ID |
| sales_staff_id | varchar(32) NOT NULL 归属销售 |
| bind_source | varchar(16) NOT NULL：`SHARE`/`QRCODE`/`ADMIN`/`TRANSFER`/`CLAIM` |
| bind_time | datetime NOT NULL |
唯一键：`uk_sales_customer_bind (tenant_id, customer_user_id)` —— 一个客户同时只归属一个销售。
普通键：`idx_sales_customer_bind_staff (tenant_id, sales_staff_id)`。

### 2.3 `sales_customer_bind_log` 换绑日志（只追加）

`customer_user_id`、`old_sales_staff_id`（可空）、`old_sales_staff_name`、`new_sales_staff_id`、`new_sales_staff_name`、`bind_source`、`reason varchar(255)`、`operator_id`、`operator_name`。
普通键：`idx_sales_bind_log_customer (tenant_id, customer_user_id, create_time)`。

### 2.4 `sales_customer_claim` 待转交认领

| 列 | 说明 |
|---|---|
| customer_user_id | varchar(32) NOT NULL |
| sales_staff_id | varchar(32) NOT NULL |
| source | varchar(16) NOT NULL：`SHARE`/`QRCODE` |
| status | char(2) NOT NULL default '0'：0 待首单 / 1 已转交 / 2 已失效 |
| claim_time | datetime NOT NULL |
| expire_time | datetime NOT NULL（默认 claim_time + `sales_config.claim_expire_days`，默认 90 天） |
唯一键：`uk_sales_claim_customer (tenant_id, customer_user_id)` —— 同一客户只保留最新一条有效认领（重复点击=更新销售/来源/时间，沿用同一条）。
普通键：`idx_sales_claim_staff (tenant_id, sales_staff_id, status)`。

### 2.5 `sales_transfer_reward` 客户转交奖励

`customer_user_id`、`distributor_user_id`（原分销员会员 ID）、`sales_staff_id`（接收销售）、`biz_order_id`（触发订单）、`first_order_amount decimal(10,2)`（首单实付不含运费）、`reward_rate decimal(10,4)`、`reward_amount decimal(10,2)`、`status char(2)`：0 待结算 / 1 已入账 / 2 已作废、`settle_time`。
唯一键：`uk_sales_transfer_reward (tenant_id, customer_user_id, biz_order_id)` —— 幂等。

### 2.6 `sales_order` 销售业绩单

| 列 | 说明 |
|---|---|
| biz_order_id | varchar(32) NOT NULL 业务订单 ID |
| order_no | varchar(64) |
| buyer_user_id | varchar(32) NOT NULL |
| sales_staff_id / staff_name / dept_id | 订单快照 |
| payment_amount / freight_amount | 实付（含运费）/ 运费 |
| commission_base | 提成基数（实付不含运费） |
| commission_rate | 支付时比例快照 |
| commission_amount | 提成金额 |
| refunded_base / refunded_commission | 累计退款基数 / 累计扣回提成 |
| status | char(2) NOT NULL：0 待确认 / 1 可发放 / 2 已发放 / 3 已全额退款 |
| pay_time / settle_at / settle_time | 支付时间 / 计划可发放（风险观察期止）/ 实际可发放 |
| payout_id | varchar(32) NULL 所属发放批次 |
| remark | varchar(255) |
唯一键：`uk_sales_order_biz (tenant_id, biz_order_id)`；普通键：`idx_sales_order_staff (tenant_id, sales_staff_id, pay_time)`、`idx_sales_order_status (tenant_id, status, settle_at)`、`idx_sales_order_payout (tenant_id, payout_id)`。

### 2.7 `sales_payout` 月度发放台账

`sales_staff_id`、`staff_name`、`dept_id`、`payout_month varchar(7)`（`yyyy-MM`，生成批次所属月=上月）、`order_count int`、`trade_amount`（成交额=Σ(基数-已退基数)）、`commission_amount`（应发=Σ(提成-已扣回)）、`refund_deduct_amount`（上期退款冲减为正数，实发=应发-冲减）、`payable_amount`、`status char(2)`：0 待确认 / 1 已发放、`confirm_by`、`confirm_time`、`remark varchar(500)`。
唯一键：`uk_sales_payout_staff_month (tenant_id, sales_staff_id, payout_month)`。

### 2.8 `sales_payout_adjustment` 已发放后退款的负数结转

`sales_staff_id`、`staff_name`、`biz_order_id`、`refund_no varchar(64)`、`amount decimal(10,2)`（负数）、`status char(2)`：0 待结转 / 1 已结转、`payout_id`、`remark`。
唯一键：`uk_sales_payout_adjustment (tenant_id, refund_no)`；普通键：`idx_sales_adjustment_staff (tenant_id, sales_staff_id, status)`。

### 2.9 `sales_config` 全局配置（每租户单行）

| 列 | 默认 | 说明 |
|---|---|---|
| default_commission_rate | 0.0500 | 全局默认提成比例 |
| referral_reward_rate | 0.0000 | 转交奖励比例（0=不启用） |
| monthly_cutoff_day | 1 | 每月台账生成日（1–28） |
| refund_risk_days | 7 | 退款风险观察期（`settle_at = pay_time + days`） |
| claim_expire_days | 90 | 认领有效期 |
| status | '0' | 0 启用 |
唯一键：`uk_sales_config_active (tenant_id, active_config_key)`（生成列，同 distribution_config 风格），保证单租户单启用配置。

### 2.10 `distribution_coupon_pack` 券包配置

`pack_name varchar(64)`、`face_amount decimal(10,2)`、`threshold_amount decimal(10,2)`（使用门槛）、`valid_days int`、`required_commission decimal(10,2)`（消耗佣金）、`stock int NULL`（NULL=不限）、`status char(2)`：0 上架 / 1 下架、`sort int`、`remark varchar(255)`。

### 2.11 `distribution_coupon_pack_item` 券包→券模板

`pack_id varchar(32)`、`coupon_info_id varchar(32)`、`quantity int default 1`。
唯一键：`uk_distribution_pack_item (tenant_id, pack_id, coupon_info_id)`。

### 2.12 `distribution_coupon_exchange` 兑换记录

`user_id`、`nickname`、`pack_id`、`pack_name`、`face_amount`、`commission_amount`（消耗佣金）、`coupon_user_ids varchar(500)`（发放的 coupon_user 主键，逗号分隔）、`status char(2)`：0 发放中 / 1 已发放 / 2 发放失败、`exchange_time`、`remark`。
普通键：`idx_distribution_exchange_user (tenant_id, user_id, create_time)`、`idx_distribution_exchange_pack (tenant_id, pack_id, create_time)`。

### 2.13 `order_info` 新增列（order 域）

```sql
ALTER TABLE `order_info`
  ADD COLUMN `sales_staff_id` varchar(32) NULL COMMENT '归属销售ID快照（下单时写入，换绑不影响）' AFTER `group_buy_record_id`,
  ADD COLUMN `sales_staff_name` varchar(64) NULL COMMENT '归属销售姓名快照' AFTER `sales_staff_id`;
```
**不加索引**（按销售查询走 `sales_order`）。可重复执行（先 `information_schema` 判存在）。

---

## 3. 契约 — 后端 API

### 3.1 Dubbo（`aryn-promotion-api`）

```java
// com.aryn.cloud.promotion.api.remote.RemoteSalesAttributionService
SalesAttributionDTO resolve(String tenantId, String userId);
```
`SalesAttributionDTO`：`salesStaffId`、`salesStaffName`（无归属返回 null 字段的 DTO 或 null）。
判定顺序：`sales_customer_bind` 且销售在职 → 未过期 `sales_customer_claim(status=0)` 且销售在职 → 无。
**只返回在职销售**。order 侧调用方：`OrderInfoServiceImpl.createOrder`（失败只告警不阻断）。

### 3.2 管理端 REST（promotion-biz，首段为微服务域 `promotion`）

前端请求前缀 `/promotion`，即 UI 调用 `/promotion/sales/staff/page`；boot 模式 `rewriteBootUrl` 改写为 `/boot/sales/staff/page`。

| 方法 | 路径 | 权限 | 入参/返回 |
|---|---|---|---|
| GET | `/sales/staff/page` | `promotion:salesstaff:page` | params: current,size,staffName,phone,deptId,status；返回 `IPage<SalesStaffVO>` |
| GET | `/sales/staff/{id}` | `promotion:salesstaff:get` | `Result<SalesStaffVO>` |
| POST | `/sales/staff/add` | `promotion:salesstaff:add` | body `SalesStaffSaveDTO` |
| PUT | `/sales/staff/edit` | `promotion:salesstaff:edit` | body `SalesStaffSaveDTO`（必须带 id） |
| POST | `/sales/staff/resign` | `promotion:salesstaff:edit` | body `SalesStaffResignDTO`（staffId, receiveStaffId, leaveTime, reason） |
| POST | `/sales/staff/status` | `promotion:salesstaff:edit` | body `{id, status}`（0/1） |
| GET | `/sales/staff/options` | `promotion:salescustomer:page` | params keyword；返回在职销售 `List<SalesStaffVO>`（下拉） |
| GET | `/sales/customer/page` | `promotion:salescustomer:page` | params current,size,customerUserId,salesStaffId,bindSource；返回 `IPage<SalesCustomerVO>`（含 pendingClaimStaffId/Name、claimStatus） |
| POST | `/sales/customer/assign` | `promotion:salescustomer:edit` | body `SalesCustomerAssignDTO`（customerUserId, salesStaffId, reason）手动指派/改归属 |
| GET | `/sales/customer/bind-log/page` | `promotion:salescustomer:page` | params customerUserId；返回 `IPage<SalesCustomerBindLog>` |
| GET | `/sales/claim/page` | `promotion:salescustomer:page` | params status,salesStaffId；返回 `IPage<SalesCustomerClaimVO>` |
| GET | `/sales/order/page` | `promotion:salesorder:page` | params current,size,salesStaffId,deptId,status,orderNo,buyerUserId,payTimeStart,payTimeEnd；返回 `IPage<SalesOrderVO>` |
| GET | `/sales/order/export` | `promotion:salesorder:export` | 同上筛选，EasyExcel 输出 |
| GET | `/sales/order/statistics` | `promotion:salesorder:page` | 汇总卡片：orderCount,tradeAmount,commissionAmount,refundAmount |
| GET | `/sales/payout/page` | `promotion:salespayout:page` | params current,size,payoutMonth,salesStaffId,status |
| GET | `/sales/payout/{id}` | `promotion:salespayout:page` | `Result<SalesPayoutVO>` |
| GET | `/sales/payout/{id}/order/page` | `promotion:salespayout:page` | 该批次业绩单分页 |
| POST | `/sales/payout/generate` | `promotion:salespayout:generate` | body `SalesPayoutGenerateDTO`（payoutMonth 可空=上月）返回生成的批次数 |
| POST | `/sales/payout/confirm` | `promotion:salespayout:confirm` | body `SalesPayoutConfirmDTO`（id, remark） |
| GET | `/sales/payout/export` | `promotion:salespayout:export` | |
| GET | `/sales/report/summary` | `promotion:salesreport:page` | params payoutMonth,salesStaffId,deptId；返回 `List<SalesReportVO>` |
| GET | `/sales/report/export` | `promotion:salesreport:export` | |
| GET | `/sales/report/trend` | `promotion:salesreport:page` | params salesStaffId,months=6；返回 `List<SalesTrendVO>`（month,orderCount,tradeAmount,commissionAmount） |
| GET | `/sales/config` | `promotion:salesconfig:page` | `Result<SalesConfig>`（不存在时返回默认值对象） |
| PUT | `/sales/config` | `promotion:salesconfig:edit` | body `SalesConfig` |
| GET | `/sales/transfer-reward/page` | `promotion:salescustomer:page` | 转交奖励查询（customerUserId/salesStaffId/status） |
| GET | `/distribution/coupon/pack/page` | `promotion:distributioncouponpack:page` | params packName,status |
| GET | `/distribution/coupon/pack/list` | `promotion:distributioncouponpack:page` | 全部券包（下拉/选择券模板用） |
| GET | `/distribution/coupon/pack/{id}` | `promotion:distributioncouponpack:page` | |
| POST | `/distribution/coupon/pack/add` | `promotion:distributioncouponpack:add` | body `DistributionCouponPackSaveDTO`（含 items: [{couponInfoId, quantity}]） |
| PUT | `/distribution/coupon/pack/edit` | `promotion:distributioncouponpack:edit` | |
| POST | `/distribution/coupon/pack/status` | `promotion:distributioncouponpack:edit` | body `{id, status}` |
| DELETE | `/distribution/coupon/pack/{id}` | `promotion:distributioncouponpack:del` | 逻辑删除 |
| GET | `/distribution/coupon/exchange/page` | `promotion:distributioncouponexchange:page` | params userId,packId,status,createTimeStart,createTimeEnd |
| GET | `/distribution/coupon/exchange/statistics` | `promotion:distributioncouponexchange:page` | 券成本汇总（faceAmount/commissionAmount/count） |

**提现审核降级（P2）**：
- `POST /distribution/withdraw/audit` → 直接抛 `ArynBusinessException("提现功能已下线，仅保留历史查询")`；
- `POST /distribution/withdraw/reencrypt-legacy-accounts` 保留；
- `GET /distribution/withdraw/page`、`/{id}` 保留只读。
- 小程序 `POST /app/distribution/withdraw/apply` → 同错误文案。

### 3.3 小程序 REST

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/app/distribution/share/bind` | 原接口，**扩展**：body 增加可选 `bindSource`（`SHARE`/`QRCODE`），默认 `SHARE`。邀请人为在职销售时走销售绑定分流 |
| GET | `/app/distribution/coupon/pack/list` | 上架券包列表（id,packName,faceAmount,thresholdAmount,validDays,requiredCommission,stock） |
| POST | `/app/distribution/coupon/exchange` | body `{packId}`；返回 `Result<Boolean>` |
| GET | `/app/distribution/coupon/exchange/page` | 我的兑换记录 |
| GET | `/app/sales/workbench` | 销售工作台汇总 `AppSalesWorkbenchVO`：isSales、staffId、staffName、memberUserId、customerCount、monthOrderCount、monthTradeAmount、monthCommission、totalCommission、qrcodeUrl（可为 null） |
| GET | `/app/sales/customer/page` | 我的客户（customerUserId 脱敏昵称/头像、bindTime） |
| GET | `/app/sales/order/page` | 我的业绩明细（orderNo,payTime,commissionBase,commissionAmount,status） |
| GET | `/app/sales/transfer-reward/page` | 我的转交奖励（分销员视角，可选） |
| GET | `/mall-user/app/sales/qrcode` | **user 域**；返回 `Result<SalesQrcodeVO>`：`qrcodeUrl`（data URI 或对象存储 URL）、`page`、`scene`。仅在职销售本人可调；Redis 缓存 7 天 |

小程序 RequestMapping：promotion 侧 `@RequestMapping("/app/sales")`、`@RequestMapping("/app/distribution/coupon")`；user 侧 `@RequestMapping("/app/sales")` + `@GetMapping("/qrcode")`。

### 3.4 VO/DTO 字段清单（promotion-api 已实现，前端按此写 TS 类型）

- `SalesStaffVO`：id, sysUserId, staffName, phone, deptId, deptName, memberUserId, commissionRate, status, leaveTime, remark, createTime, customerCount（列表可选）
- `SalesCustomerVO`：id, customerUserId, nickname, avatar, salesStaffId, salesStaffName, bindSource, bindTime, pendingClaimStaffId, pendingClaimStaffName, claimStatus, claimExpireTime
- `SalesOrderVO`：= `SalesOrder` + buyerNickname
- `SalesPayoutVO`：= `SalesPayout` + orderCount 等（entity 自带）
- `SalesReportVO`：salesStaffId, staffName, deptName, orderCount, tradeAmount, refundAmount, commissionAmount, paidAmount
- `SalesTrendVO`：month, orderCount, tradeAmount, commissionAmount
- `SalesConfig`：见 2.9
- `AppSalesWorkbenchVO`：见 3.3
- `DistributionCouponPackVO`：= entity + couponInfos（[{couponInfoId, name, faceAmount, quantity}]）
- `DistributionCouponExchangeVO`：= entity + nickname
- `SalesQrcodeVO`：qrcodeUrl, page, scene

---

## 4. 核心业务规则（实现清单）

> 2026-11-05 上线前复审后，4.1/4.3/4.4/4.5 的若干口径已按复审结论更新（详见交付说明 §8），本文件已同步为最终实现。

### 4.1 分享/扫码绑定分流（`shareBind`，be-sales）
1. 取邀请人 `memberUserId` → 查在职 `sales_staff`。
2. 邀请人不是销售 → 先查客户 `sales_customer_bind`：**已归属销售则忽略（不再建立新的分销上级关系，需求 §4.2）**；未归属才走原 `distributionUserService.register(dto)`。
3. 邀请人是销售：
   - 邀请人 == 当前用户 → 忽略（自购/自点）。
   - 客户已有 `sales_customer_bind`：同销售→幂等返回；不同销售→**不改归属**，返回成功（不报错）。
   - 客户无销售归属且不是分销用户（无 `distribution_user.inviter_user_id`）：写 `sales_customer_bind`（source=SHARE/QRCODE）+ 日志（old=nil）。
   - 客户无销售归属但已是分销员下级（有 inviter）：**写/替换 `sales_customer_claim`（待首单，status=0）**，不改归属、不建新的分销关系。
4. 任一情况都不得把销售会员账号写成客户的分销上级。

### 4.2 下单归属快照（Lead / aryn-order）
- `createOrder` 保存前调 `RemoteSalesAttributionService.resolve(tenantId, userId)`：
  - 成功且非空 → 写 `order_info.sales_staff_id/name`；
  - 异常/超时 → `log.warn`，不阻断下单（该单无业绩）。
- `OrderPaySuccessEvent` 增加 `salesStaffId`、`actualPayPrice`（`BeanUtils.copyProperties` 自动带出）。
- 管理端订单列表：返回体已含字段；筛选条件 `salesStaffId`、`salesStaffName`（LIKE）；导出「订单明细」表加「归属销售」列（`DETAIL_HEAD` 在「购买场景」后插入）。

### 4.3 支付后（`SalesPayHandler implements PromotionPayEventHandler`，be-sales）
1. `event.salesStaffId` 为空 → 跳过。
2. 销售不存在/已离职 → 跳过（不再产生新业绩）。
3. 自购：`event.userId == staff.memberUserId` → 跳过。
3. 若存在该客户、该销售的待转交认领（status=0 且未过期）→ 先执行**转交**：
   - `sales_customer_bind` upsert 为该销售（source=CLAIM），写换绑日志（原归属或分销员分销关系 → 销售，reason「首单转交」），认领置 1；
   - **复审后以订单销售快照为准**：客户尚无正式归属时，即使下单后认领被改到另一位销售或已过期，支付仍按快照完成转交（被替换/过期的其他待认领统一作废），避免"原销售拿提成但未转交、奖励丢失"；
   - `referral_reward_rate > 0` 时生成 `sales_transfer_reward`（金额=`首单实付不含运费 × 比例`，2 位向下取整），状态 `0 待结算`，给原分销员 `pending_commission += 奖励` + 写 `distribution_commission_flow`（类型 INCOME，备注「客户转交奖励」）；按 (customer,biz_order_id) 幂等。奖励由 `distributionSettleJobHandler` 按 `distribution_config.settle_cycle_days` 释放为 available（status=1、settle_time、累计佣金）；首单全额退款时自动扣回（status=2、EXPENSE 流水、pending/available/debt 三态、幂等）。
5. 生成 `sales_order`：
   - 基数（**已按本仓库代码口径修正**：`order_info.payment_price`/`actual_pay_price` 均为含运费口径）：
     - 在线支付：`max(0, event.paymentPrice - event.freightPrice)`
     - 货到付款：`max(0, event.actualPayPrice - event.freightPrice)`，`actualPayPrice` 为空回退 `paymentPrice - freightPrice`
     - 基数为 0 时不生成业绩单；金额 2 位、RoundingMode.DOWN；
   - 比例：`staff.commissionRate` 为空取 `sales_config.default_commission_rate`；
   - 金额 2 位向下取整；**`status=1`（支付即「可发放」，`settle_at=pay_time+refund_risk_days` 仅作退款风险观察期标记，不再是放行门槛）**、`payout_id=null`；
   - `biz_order_id` 唯一冲突 → 幂等跳过。
6. 与分销互斥：`DistributionPayHandler` 在 `event.salesStaffId` 非空时直接 return；`DistributionSettlementServiceImpl.settleOrder` 在买家已归属在职销售时 return（防转交后旧订单继续计佣）。

### 4.4 退款（`SalesRefundHandler implements PromotionRefundEventHandler`，be-sales）
- 幂等：按 `refundNo` 记录（复用/新建 promotion 域幂等表或 `sales_payout_adjustment`+独立退款记录表；建议新增 `sales_refund_record`，字段同 `distribution_refund_record`：refund_no 唯一、biz_order_id、refund_amount、refund_base_amount、applied、applied_time）。
- 定位 `sales_order` by `biz_order_id`；按 `refundBaseAmount / commission_base` 比例计算应扣回，2 位向下取整，累计不回退超 `commission_amount`。
- `status in (0,1)`：直接冲减 `refunded_commission`；全额退款 → status=3。
- `status=2`（已发放）：生成/累加 `sales_payout_adjustment`（负数），不改已发放台账；全额退款同样只生成负数调整。
- **复审新增**：退款冲减成功后，若业绩单已入「待确认」台账（`payout_id` 非空），调用 `recalcPendingPayout(payoutId)` 按批次净额重算台账应发/实发，避免财务按旧金额发放；`confirm()` 前还会再重算一次兜底。
- **复审新增**：全额退款联动 `clawbackReward(bizOrderId)` 扣回原分销员转交奖励（按触发订单幂等，客户归属不回退）。
- 退款发生在支付事件前 → 记录待应用，支付处理时补应用（参考 `DistributionSettlementServiceImpl.applyRecordedRefunds`，可简化：若 sales_order 不存在则留待 job 重放或支付时补）；若业绩单延迟创建（>7 天，退款记录已被放弃），支付生成业绩单后先重开该订单被放弃的记录再补应用，避免漏扣。

### 4.5 月度发放台账（`SalesPayoutJobHandler`，be-sales）
- 每日跑：① 认领过期置 status=2；② 重放未应用的退款记录。
- 到达 `sales_config.monthly_cutoff_day`（默认 1 日，**仅在生成日执行**）生成「上月」台账 `sales_payout`；已过生成日但仍有未入批的上月业绩单（漏跑）时做一次幂等补跑：
  - 业绩单支付创建即 `status=1 可发放`（不再有 `settle_at` 到期才放行的步骤）；
  - 按销售聚合 `sales_order where status=1 and payout_id is null and pay_time < 台账月次月1日0点`：order_count、trade_amount=Σ(base-refunded_base)、commission_amount=Σ(amount-refunded_commission)；历史未入批单顺延，台账月之后的订单不纳入；
  - 追加该销售 `sales_payout_adjustment where status=0` 的负数合计为 refund_deduct（取正数记 `refund_deduct_amount`），并将调整置 1、绑定 payout_id；
  - **复审新增**：只有待结转负数、没有可发放业绩单的销售（含离职）也要生成台账，`payable_amount = 0 - refund_deduct_amount`（允许负数）；
  - `payable_amount = commission_amount - refund_deduct_amount`；status=0 待确认；unique(tenant,staff,month) 幂等；
  - 生成后把纳入的 `sales_order.payout_id` 指向该批次。
- 管理端「生成台账」= 同逻辑手动触发（可指定月份，重复生成幂等跳过已存在批次）。
- 财务确认发放：先 `recalcPendingPayout` 重算 → 校验批次内 `status=1` 单数与台账 `order_count` 一致（不一致抛业务异常回滚）→ `sales_payout.status=1, confirm_by/time`；该批次 `sales_order.status=2, settle_time`（批量更新同时推进 `version`，防并发退款把已发放订单状态回写）。
- 定时任务需按租户遍历（`RemoteTenantService.list`），参考 `DistributionSettleJobHandler`。

### 4.6 离职与换绑（`ISalesStaffService`，be-sales）
- 标记离职必须传 `receiveStaffId`（在职、非本人）：批量把名下 `sales_customer_bind` 改为接收人（source=TRANSFER）+ 逐条日志；名下未完成认领作废（status=2）。
- 单人改归属 `assign`：写 bind upsert + 日志（reason 必填），未完成认领作废。
- 离职销售历史业绩单/台账不动；后台不展示「提交提现」入口（本来就没有）。

### 4.7 分销佣金改券兑换（be-coupon）
- `AppDistributionController.apply` / `DistributionWithdrawService.apply` → 抛业务异常「提现功能已下线，佣金可兑换优惠券」。
- 兑换流程（单事务）：校验证券包上架/库存（stock NULL 不限；>0 扣减）→ 校验 `available_commission >= required_commission` → 扣 `available_commission`、`total_commission` 不变、写 `distribution_commission_flow` EXPENSE（备注「兑换优惠券-券包名」）→ 调优惠券发放接口为该用户发券（每个 pack_item × quantity）→ 券发放成功才提交；失败回滚佣金扣减（抛异常回滚事务即可）。
- 发券实现：优先复用 `RemoteCouponInfoService`/`CouponUserService` 现有领取/发放方法；若现有接口只支持用户自领，则在 promotion 内直接构造 `CouponUser`（参考 `CouponUserServiceImpl` 的发券逻辑）并校验模板状态与库存。
- 后台：券包 CRUD、兑换记录查询与券成本汇总。
- 小程序：分销中心「申请提现」改成「兑换优惠券」入口，券包列表/兑换确认/兑换记录页。

### 4.8 推广码 + 销售工作台（P3）
- user 域 `GET /app/sales/qrcode`：当前登录会员 → `social_user` 反查 appId → `MiniAppBindingResolver.requireBinding(appId, tenantId)` → 校验该会员是**在职销售**（调 promotion Dubbo `RemoteSalesAttributionService`? 不行，方向反了；promotion 不依赖 user。改为 user-biz 通过 Dubbo 调 promotion 提供的 `RemoteSalesStaffService.isActiveSales(memberUserId)`，该接口放 promotion-api，由 be-sales 实现）。
  - `WxMaService.getQrcodeService().createWxaCodeUnlimitBytes("sid="+memberUserId, "pages/home/index", 430, false, null, true, "release")`；`scene` 32 字符内（`sid=` + 19 位 = 23）。
  - 返回 data URI（`data:image/png;base64,...`），Redis 缓存 key `sales:qrcode:{tenantId}:{memberUserId}`，7 天。
  - 未发布小程序/体验版可用 `env_version` 配置（读 `wx.miniapp.env-version`，默认 release，失败降级 `trial`→`develop` 重试一次）。
- 小程序销售工作台 `/sub-pages/user/sales/index`：入口在个人中心/分销中心，展示推广码图片、我的客户、本月明细、本月预估提成；只读。
- 业绩报表：后台按销售/部门/月份汇总与导出。

---

## 5. 前端页面清单

### 5.1 管理端（`aryn-mall-ui/apps/web-ele`）

| 菜单/路由 | 组件 | API 文件 |
|---|---|---|
| `/promotion/sales/staff` 销售员 | `views/promotion/sales/staff/index.vue` | `api/promotion/sales-staff.ts` |
| `/promotion/sales/customer` 客户归属 | `views/promotion/sales/customer/index.vue`（弹窗：改归属、换绑日志、转交奖励、待认领） | `api/promotion/sales-customer.ts` |
| `/promotion/sales/order` 业绩单 | `views/promotion/sales/order/index.vue` | `api/promotion/sales-order.ts` |
| `/promotion/sales/payout` 发放台账 | `views/promotion/sales/payout/index.vue` | `api/promotion/sales-payout.ts` |
| `/promotion/sales/report` 业绩报表 | `views/promotion/sales/report/index.vue` | `api/promotion/sales-report.ts` |
| `/promotion/sales/config` 全局配置 | `views/promotion/sales/config/index.vue` | `api/promotion/sales-config.ts` |
| `/promotion/distribution/coupon-pack` 券包配置 | `views/promotion/distribution-coupon-pack/index.vue` | `api/promotion/distribution-coupon-pack.ts` |
| `/promotion/distribution/coupon-exchange` 兑换记录 | `views/promotion/distribution-coupon-exchange/index.vue` | `api/promotion/distribution-coupon-exchange.ts` |
| 提现审核（改只读） | `views/promotion/distribution-withdraw/index.vue`：删除审核/打款按钮，保留查询 | 现有 `distribution-withdraw.ts` 保留 getPage/getById |
| 订单管理（加列） | `views/order/order-info/index.vue`：列表加「归属销售」列；筛选加归属销售输入/下拉；详情显示归属销售 | `api/order/order-info.ts` 复用 getPage |
| 路由/菜单 | 后端 `sys_menu` 驱动（`component` 字段=组件路径），无需前端静态路由 | — |

UI 规范：沿用现有 Element Plus + Vben 风格；表格列 `show-overflow-tooltip`；金额 `formatPrice`/`toFixed(2)`；日期 `formatDateTime`；分页 `current/size`；导出用 `download`/blob 方式，参照现有导出页。
**双模式**：API 路径统一写微服务域（`/promotion/...`、`/mall-order/...`），由 requestClient 自动 `rewriteBootUrl`，禁止手拼 `/boot`。

### 5.2 小程序（`aryn-mall-uniapp`）

| 页面 | 路径 | 内容 |
|---|---|---|
| 分销中心改造 | `sub-pages/user/distribution/index.vue` | 去掉「申请提现」；新增「兑换优惠券」入口与可兑换佣金展示 |
| 券包列表/兑换 | `sub-pages/user/distribution/coupon-exchange.vue`（新建） | 券包卡片、所需佣金、我的可提现佣金、兑换确认 |
| 兑换记录 | `sub-pages/user/distribution/coupon-exchange-record.vue`（新建） | 分页记录 |
| 销售工作台 | `sub-pages/user/sales/index.vue`（新建，只对已登记销售展示入口） | 推广码、我的客户、业绩明细、本月预估提成 |
| 推广码展示 | 工作台内弹窗/区块 | `<image :src="qrcodeUrl">`，保存到相册用 `uni.saveImageToPhotosAlbum`（base64 先写临时文件） |
| API | `api/distribution/entity.ts` 扩展券相关；新建 `api/sales/entity.ts` | 路径首段微服务域 |
| 分享绑定 | `useDistributionShare.ts` 增补 `bindSource: 'QRCODE'` 场景（扫码进入携带 scene 时） | 保持现有 shareUserId/sid 参数 |

---

## 6. 数据库脚本清单（sql-db）

| 模式 | 文件 | 内容 |
|---|---|---|
| boot | `db/boot/202sales_performance_incremental.sql` | 2.1–2.13 全部表 + `order_info` 加列 + 字典（`sales_staff_status`、`sales_bind_source`、`sales_order_status`、`sales_payout_status`、`sales_claim_status`、`sales_transfer_reward_status`、`coupon_pack_status`、`coupon_exchange_status`）+ `sales_config` 默认行按租户补齐 |
| boot | `db/boot/203sales_performance_menu.sql` | `sys_menu`、`sys_tenant_menu`、`sys_role_menu`（见 6.1） |
| boot | `db/boot/204sales_performance_job.sql` | `aryn_boot_job.xxl_job_info` 注册 `salesPayoutJobHandler`（每日 01:10） |
| cloud | `db/cloud/205sales_performance_incremental.sql` | 表 DDL 与 boot 一致（目标库 `aryn_promotion` + `aryn_order` 加列分开 USE） |
| cloud | `db/cloud/206sales_performance_menu.sql` | 菜单/字典（`aryn_upms` 或 boot 同库按现有 cloud 菜单脚本惯例） |
| cloud | `db/cloud/207sales_performance_nacos_tenant_tables.sql` | `hx.tenant.tables` 增量（新表 11 张） |
| cloud | `db/cloud/208sales_performance_job.sql` | 同 204（目标 `aryn_job`/`xxl_job` 按现有 cloud 任务注册惯例） |
| boot | `db/boot/build-full-sql.mjs` | sections 追加 202/203/204；重新生成 `aryn_boot_full.sql` 并跑 `verify-full-sql.mjs` |
| cloud | `db/cloud/9aryn_promotion.sql` 等 | 不必改基线（增量脚本自足）；如现有惯例要求基线同步，参照 `187clearance_zone_gateway_whitelist_nacos_config.sql` 风格只加增量 |

> boot/cloud 表结构必须一致；cloud 脚本用 `CREATE TABLE IF NOT EXISTS` + `information_schema` 判重加列，可重复执行，**禁止 DROP/TRUNCATE 存量数据**（新建表可 `CREATE TABLE IF NOT EXISTS`）。

### 6.1 菜单 ID 规划（固定 ID + INSERT IGNORE 幂等）

父菜单「营销管理」= `1779386604402573314`；「分销管理」= `1991000000000000001`。

| ID | 名称 | path | component | permission |
|---|---|---|---|---|
| 2130000000000000100 | 销售管理 | `/promotion/sales` | ''（目录） | — |
| 2130000000000000110 | 销售员 | `/promotion/sales/staff` | `promotion/sales/staff/index` | `promotion:salesstaff:page` |
| 2130000000000000111..115 | 销售员按钮 | — | — | add/edit/get/resign?（add,edit,del） |
| 2130000000000000120 | 客户归属 | `/promotion/sales/customer` | `promotion/sales/customer/index` | `promotion:salescustomer:page` |
| 2130000000000000121..123 | 客户归属按钮 | — | — | edit,export?（edit） |
| 2130000000000000130 | 业绩单 | `/promotion/sales/order` | `promotion/sales/order/index` | `promotion:salesorder:page` |
| 2130000000000000131/132 | 业绩单按钮 | — | — | export,get |
| 2130000000000000140 | 发放台账 | `/promotion/sales/payout` | `promotion/sales/payout/index` | `promotion:salespayout:page` |
| 2130000000000000141..143 | 台账按钮 | — | — | generate,confirm,export |
| 2130000000000000150 | 业绩报表 | `/promotion/sales/report` | `promotion/sales/report/index` | `promotion:salesreport:page` |
| 2130000000000000151 | 报表导出 | — | — | `promotion:salesreport:export` |
| 2130000000000000160 | 全局配置 | `/promotion/sales/config` | `promotion/sales/config/index` | `promotion:salesconfig:page` |
| 2130000000000000161 | 配置编辑 | — | — | `promotion:salesconfig:edit` |
| 2130000000000000170 | 券包配置（父=分销管理） | `/promotion/distribution/coupon-pack` | `promotion/distribution-coupon-pack/index` | `promotion:distributioncouponpack:page` |
| 2130000000000000171..173 | 券包按钮 | — | — | add,edit,del |
| 2130000000000000180 | 兑换记录（父=分销管理） | `/promotion/distribution/coupon-exchange` | `promotion/distribution-coupon-exchange/index` | `promotion:distributioncouponexchange:page` |

授权策略：沿用 `199zone_tag_batch_menu.sql` 的推导式补授（拥有「分销管理」`1991000000000000001` 菜单的角色/租户获得销售管理+券包/兑换菜单；拥有「订单管理」菜单的角色获得订单归属列所需权限无新增权限点则跳过）。

### 6.2 租户表登记

- boot：`aryn-boot/src/main/resources/application.yml` 的 `hx.tenant.tables` 增加 11 张新表（Lead 负责）。
- cloud：`db/cloud/207sales_performance_nacos_tenant_tables.sql`（写 Nacos `application-dev.yml` 增量说明/UPDATE 语句，参照 `202zone_tag_batch_nacos_tenant_tables.sql`）。

### 6.3 字典编码（`sys_dict`/`sys_dict_value`）

`sales_staff_status`(0在职/1离职)、`sales_bind_source`(SHARE分享/QRCODE推广码/ADMIN后台指派/TRANSFER离职交接/CLAIM首单转交)、`sales_order_status`(0待确认/1可发放/2已发放/3已全额退款)、`sales_payout_status`(0待确认/1已发放)、`sales_claim_status`(0待首单/1已转交/2已失效)、`sales_transfer_reward_status`(0待结算/1已入账/2已作废)、`distribution_coupon_pack_status`(0上架/1下架)、`distribution_coupon_exchange_status`(0发放中/1已发放/2发放失败)。

---

## 7. 双模式适配清单

1. biz 间禁止 import：order→promotion 走 `@DubboReference RemoteSalesAttributionService`；user→promotion 走 `@DubboReference RemoteSalesStaffService`；promotion→upms 用现有 `RemoteSysUserService`。
2. 新增配置项无需改动（sales_config 落库）；推广码 `env_version` 用 `@Value` 默认值，boot/cloud 均可。
3. Controller 路径不得硬编码 `/boot`；前端请求首段必须是微服务域（见 3.2/3.3）。
4. `hx.tenant.tables` boot 直改 yml；cloud 走 Nacos 脚本。
5. 新增 Dubbo 接口必须在 `*-api` 模块 + 实现类 `@DubboService`（cloud）且在 boot 下可注入（沿用现有 `@DubboReference` 用法）。

---

## 8. 验证

```bash
# 后端（每阶段）
cd aryn-mall-java && mvn -q clean compile -pl aryn-boot -am
cd aryn-mall-java && mvn test -pl aryn-boot -am
# 管理端
cd aryn-mall-ui && pnpm check:type && pnpm test:unit && pnpm lint
# 小程序
cd aryn-mall-uniapp && pnpm type-check && pnpm test:unit
# SQL
cd aryn-mall-java/db/boot && node build-full-sql.mjs && node verify-full-sql.mjs
```

单测覆盖（后端）：
- 绑定分流：邀请人为销售/非销售/已归属分销员/已归属其他销售/自点
- 转交奖励幂等（重复支付事件）、首单归属
- 提成计算：在线支付、货到付款实收、比例为 NULL 取默认、向下取整
- 退款：待确认/可发放直接冲减、已发放生成负数调整、全额退款、按 refundNo 幂等
- 互斥：带 salesStaffId 跳过分销；客户归属销售后分销结算跳过
- 券兑换：佣金足够→扣减发券、余额不足/发券失败→不扣佣金、库存扣减、重复兑换
- 台账：生成幂等、跨月顺延、调整结转、确认发放状态联动

---

## 9. 取舍与风险备注

- 新增 `sales_payout_adjustment`、`sales_refund_record`、`distribution_coupon_pack_item` 三张辅助表（需求文档未列全，用于幂等与多模板券包，属必要落地细节）。
- `sales_order.payout_id` 在同一订单只归属一个批次（不重复计入）。
- 分销提现历史保留，审核/打款接口返回下线业务错误，避免误操作。
- 推广码需已发布小程序；开发期 A 调用会失败，接口返回明确错误文案并缓存短 TTL 失败结果，前端降级为「复制分享链接」。
- 订单表仅加两个可空列、不加索引；按销售统计一律查 `sales_order`。

---

## 10. 进度

| 阶段 | 后端 | 管理端 | 小程序 | SQL | 状态 |
|---|---|---|---|---|---|
| P0 | ✅ 销售员/客户归属/绑定分流/快照/Dubbo | ✅ 销售员、客户归属、订单归属列与筛选/详情/导出 | —（沿用分享入口） | ✅ 202/203/205/206/207 | **已完成** |
| P1 | ✅ SalesPayHandler/SalesRefundHandler/转交奖励/台账任务/互斥 | ✅ 业绩单、发放台账、业绩报表、全局配置 | — | ✅ 202/204/208 | **已完成** |
| P2 | ✅ 券包/兑换/提现下线 | ✅ 券包配置、兑换记录、提现只读 | ✅ 分销中心改券兑换 + 兑换记录 | ✅ 202/203/205/206 | **已完成** |
| P3 | ✅ 推广码（user 域）/工作台接口/报表 | ✅ 报表导出 | ✅ 销售工作台/推广码展示 | ✅ 204/208 | **已完成** |

> 交付结果、验证命令与已知限制见 [交付说明.md](交付说明.md)。2026-11-05 后端全量 `mvn test -pl aryn-boot -am` = 2158 tests / 0 fail；管理端 type-check + 1453 tests + 改动文件 lint 通过；小程序 type-check + 1691 tests 通过；boot 全量 SQL verify 通过。
