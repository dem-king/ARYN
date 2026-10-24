# 积分 P1：会员等级差异化抵扣 + 积分兑换（兑券）

> 日期：2026-10-24 ｜ 依据：`docs/积分方案调研-竞品对标.md` 第 5 节 P1 项（#6/#7），承接同日 P0 止血四件套
> 范围：user/promotion 双域 + 管理后台 + 小程序 + 增量 SQL（boot 161 / cloud 161）

## 1. 背景

P0 已把积分的「负债与套利口子」堵住；P1 按调研节奏把积分从「第二货币」转回「营销工具」：

- **#6 会员等级差异化抵扣**：等级越高单笔抵扣上限越高（京东京享值分档 50%→90% 口径），与既有积分倍率发放体系对称，也是高价值用户的忠诚度钩子。
- **#7 积分兑换（兑券）**：小象积分商城 / 叮咚朴朴「兑券再抵现」/ 盒马兑权益的行业共识——兑出的券可定向品类、设门槛，补贴比无条件抵现更可控。

## 2. #6 等级差异化抵扣：零新表零新页面

复用 158 号建成的**会员权益体系**（`member_benefit` + `member_benefit_level_rel`）：

- 新增权益类型 **5 = 积分抵扣上限**，`benefitValue` 为百分数（20 = 20%，校验 (0,100]）。
- `MemberBenefitsVO` 新增 `deductMaxRatio`（null=未配置），多档配置取最大（等级越高抵得越多）。
- `PointsGrantServiceImpl.getDeductContext` 在全局配置之后做等级覆盖：**等级权益 > 全局配置 > 代码常量 20%**；等级服务异常降级全局，非法区间不覆盖（订单侧已有兜底校验）。
- **订单域零改动**：`PointsDeductContextVO.deductMaxRatio` 本就是 P0 下发的唯一比例来源。
- 管理端：会员权益表单加「积分抵扣上限」类型与百分数输入；关联等级复用既有多选。

## 3. #7 积分兑换：积分兑优惠券

### 3.1 数据模型

- **`points_exchange`（兑换项）**：标题/图标、`points_cost`（积分价）、`coupon_id`（券模板）、`per_user_limit`（0=不限）、上下架、排序。
- **`points_exchange_record`（兑换记录）**：一次兑换 = 一条记录 = 扣一次积分 + 发一张券；**记录 ID 同时是扣积分幂等键**（`POINTS_EXCHANGE:{记录ID}`，新场景 `POINTS_EXCHANGE`）**与发券来源单号**（`sourceType=POINTS_EXCHANGE, sourceId=记录ID`），任一侧重放都只落到同一条业务事实。

### 3.2 兑换链路（user 域 `PointsExchangeServiceImpl`）

顺序固定「**落记录 → 扣积分 → 发券**」，全部在本地事务内：

1. 校验兑换项在架、积分价合法、未达每人限兑（超限拒绝在扣积分之前）；
2. 落记录（标题/积分/券模板快照）；
3. `pointsGrantService.consume`（新通用场景消耗方法，走 P0 的 FIFO 批次扣减，余额不足抛错）；
4. 发券：promotion 域新原语 `grantPointsExchangeCoupon`（与会员券发放同构：来源单号幂等 + `allocateOne` 券库存扣减，不足抛错）。

发券是**最后一步**远程调用，其失败连带扣积分与记录整体回滚——最坏情形是用户什么都没得到、可重试，不会出现「券已发但积分没扣」的反向超发（cloud 跨库残余风险与既有会员券发放同构，见 P0 文档边界说明）。

### 3.3 C 端与管理端

- C 端（积分明细页）：新增「积分兑换」区块（总览卡下方），展示券名/面额描述（满减/折扣/无门槛 + 限兑与已兑次数）、积分价；不可兑置灰并展示原因（**已被抢完 > 已达限兑次数 > 积分不足**）；确认弹窗后兑换，成功同步刷新余额/兑换列表/明细。接口：`GET /app/points/exchanges`、`POST /app/points/exchanges/{id}/redeem`。
- 管理端（积分配置页第 4 个 Tab「积分兑换」）：兑换项 CRUD，券模板复用启用的 `coupon_info` 列表（与会员权益选券同源），保存时远程校验券模板存在；复用 `user:pointsconfig:*` 权限，零菜单 SQL。

## 4. 双模式适配

- 新表 `points_exchange` / `points_exchange_record` 双模式 161 号脚本；租户白名单三处同步：boot `application.yml`、cloud Nacos 基线 `3aryn_nacos.sql`（content+md5）、cloud 161 脚本内幂等追加段（锚点 = 160 号追加后的 `points_exclude` 行）。
- `aryn_boot_full.sql` 重建并登记（sections + resetTables + verify tokens）。
- promotion-api 新增 `grantPointsExchangeCoupon` 为纯增量（Dubbo 原语签名），`CouponUserServiceImpl` 抽公共 `grantSourceCoupon` 避免复制。

## 5. 验证

- `mvn clean test -pl aryn-boot -am` 全量绿；新增用例：等级比例覆盖/非法忽略/异常降级 3 个、权益类型 5 聚合与校验 3 个、兑换服务 6 个（顺序/限兑/下架/发券失败回滚/不可兑原因/配置校验）。
- web-ele `check:type / test:unit(879) / lint` 绿；uniapp `type-check` 绿；`build/verify-full-sql` 通过。

## 6. 边界与后续

- 兑换不支持「积分+现金」混合支付（调研 #7 的完整形态），如需再评估；无总库存字段（券模板自身 `remainNum` 即硬库存）。
- P2 项（财务递延计提、付费会员联动）未实施；游戏化任务矩阵依调研结论不做。
- 部署：boot 执行 161 后 clean 重新打包；cloud 重启 user 服务（Nacos 白名单）；首次上线需在管理端配置兑换项，C 端无兑换项时整块隐藏。
