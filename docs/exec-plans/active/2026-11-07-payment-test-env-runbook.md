# 支付测试环境联调手册

**状态：** 2026-11-07 准备完成；等待测试环境、真实商户资料与真机。本文不包含任何真实密钥。

**关联：** [支付管理后台自助配置完善方案](2026-11-06-payment-self-service-plan.md) · [官方证据摘要](2026-11-06-payment-official-research.md)

**最终目标：** 在测试环境完成微信/支付宝各终端的真实小额支付、回调、查单、关单和退款验证；未完成前不得把渠道标记为生产可用。

---

## 一、当前已完成与未完成

### 本地已验证

- 支付 P0/P1/P1.1/P2 代码已落地，支付域 **144 tests** 通过。
- 支付宝远程调用已全部使用 `certificateExecute`，支付域主代码无 `AlipayClient.execute`。
- 本地 `aryn_boot` 已应用 `209/210/211/212`，并确认以下结构存在：
  - `pay_config.enabled/active_revision_id/config_version`
  - `pay_config_revision.buyer_identity_mode`
  - `pay_config_material`、`pay_event_outbox`
  - `pay_trade_order.trade_status/prepay_time/close_time/config_revision_id`
  - `order_info.pay_submitting/pay_submit_time`
  - `social_account.identity_mode`、`social_user.platform_type/identity_mode`
- 管理端支付配置页、三方账号、身份模式已完成 mock 浏览器验证。
- UniApp 微信/支付宝小程序构建与类型检查已通过（mock 级）。

### 未验证，必须在本手册中完成

- 真实微信商户、支付宝应用/证书、真实授权码与真机。
- 真实支付、回调、查单、关单、退款、取消竞态。
- Cloud 模式 SQL 与 Nacos 配置。
- 生产级主密钥备份/轮换、Seata 全局事务、outbox 死信处理。

---

## 二、联调前必须准备

### 2.1 平台侧

| 项目 | 要求 |
|---|---|
| 微信商户 | 商户号、API v3 密钥、商户 API 证书/私钥、微信支付公钥及公钥 ID；对应支付产品已开通 |
| 微信小程序 | 小程序 AppID、AppSecret；与支付 AppID、商户号绑定关系一致 |
| 微信 App | 开放平台移动应用 AppID、包名/签名、iOS Universal Links；原生 Payment 模块已打包 |
| 支付宝应用 | AppID、应用私钥、应用公钥证书、支付宝公钥证书、支付宝根证书；证书模式 RSA2 |
| 支付宝小程序 | 独立小程序 AppID、授权范围、买家身份模式（user_id 或 open_id）已确认 |
| 回调域名 | 公网 HTTPS 域名，能转发到支付服务；微信 H5 支付域名/Referer 已按平台要求配置 |
| 商城回跳域名 | 支付完成/退出返回的商城域名，需加入 `ARYN_PAY_ALLOWED_RETURN_HOSTS` |

### 2.2 部署 Secret

以下值只放部署 Secret/环境变量，禁止写入 Git、SQL、Nacos 明文或聊天：

```text
ARYN_PAY_CREDENTIAL_MASTER_KEY=<Base64 编码的 32 字节随机密钥>
ARYN_PAY_CREDENTIAL_KEY_ID=primary
ARYN_PAY_CREDENTIAL_PREVIOUS_KEYS=<keyId=Base64,keyId=Base64，可空>
ARYN_PAY_ALLOWED_RETURN_HOSTS=<商城域名，逗号分隔>
ARYN_PAY_TRUSTED_PROXY=<true/false，只有受控反向代理才为 true>
ARYN_PAY_CALLBACK_ALLOW_HTTP=false
ARYN_PAY_CALLBACK_ALLOW_PRIVATE=false
ARYN_PAY_OUTBOX_RELAY_INTERVAL_MS=5000
ARYN_PAY_OUTBOX_BATCH_SIZE=100
ARYN_PAY_OUTBOX_MAX_ATTEMPTS=20
ARYN_PAY_ALIPAY_GATEWAY_PROD=https://openapi.alipay.com/gateway.do
ARYN_PAY_ALIPAY_GATEWAY_SANDBOX=https://openapi-sandbox.dl.alipaydev.com/gateway.do
```

要求：

- 主密钥必须是 32 字节随机值的 Base64，不能用短口令。
- 主密钥必须备份；丢失后历史材料无法解密，历史订单无法退款/查单。
- 轮换时新密钥设为 `key-id`，旧密钥放 `previous-keys`，确认旧材料可解密后再决定清理。
- 测试环境若必须用 HTTP/内网地址，只允许临时打开 `ARYN_PAY_CALLBACK_ALLOW_HTTP/ALLOW_PRIVATE`，验证完立即恢复 `false`。

---

## 三、SQL 执行顺序

### 3.1 Boot 单体模式（目标库 `aryn_boot`）

按顺序执行，每一步先备份：

```text
1. 209pay_credential_vault_incremental.sql
2. 210payment_closure_incremental.sql
3. 211order_pay_submit_gate_incremental.sql
4. 212alipay_mini_identity_incremental.sql
```

执行后：

```bash
cd aryn-mall-java
node db/boot/build-full-sql.mjs
node db/boot/verify-full-sql.mjs
```

说明：

- 209–212 均可重复执行；发现重复支付配置或身份冲突时会中止，需人工核对，禁止删除资金记录。
- 212 会把历史 `social_user` 回填为 `WX_MA + open_id`；这是存量微信支付继续可用的前提。
- 本地 `aryn_boot` 已执行过 209–212，测试环境仍需按正式流程重新执行并留痕。

### 3.2 Cloud 微服务模式

按顺序执行，目标库见每份脚本头部：

```text
1. 214pay_credential_vault_incremental.sql   → aryn_pay + aryn_upms（菜单权限）
2. 215pay_credential_vault_nacos_tenant_tables.sql → aryn_nacos
3. 216payment_closure_incremental.sql        → aryn_pay
4. 217payment_closure_nacos_tenant_tables.sql → aryn_nacos
5. 218order_pay_submit_gate_incremental.sql  → aryn_order
6. 219alipay_mini_identity_incremental.sql   → aryn_user + aryn_pay
```

注意：

- 215/217 只改写 `aryn-pay-biz-dev.yml`；生产/测试的 `-prod` 等同名 Nacos 配置需同步相同键。
- 219 同时改 `aryn_user` 与 `aryn_pay`，不要只跑一半。
- 订单、充值、用户、支付域的增量必须一起执行；缺少 `social_user.platform_type` 时微信小程序支付会 fail-closed。
- 执行顺序建议：SQL → Nacos 发布 → 主密钥 Secret → 应用发布。

---

## 四、回调地址与路由

「订单配置 → 通知地址」只填**基础地址**，不要带 `/boot`、`/pay` 或完整回调路径。

代码会拼成：

```text
Boot：  {基础地址}/boot/notify/v2/wx/{tenantId}/{revisionId}
        {基础地址}/boot/notify/v2/refunds/wx/{tenantId}/{revisionId}
        {基础地址}/boot/notify/v2/alipay/{tenantId}/{revisionId}

Cloud： {基础地址}/pay/notify/v2/wx/{tenantId}/{revisionId}
        {基础地址}/pay/notify/v2/refunds/wx/{tenantId}/{revisionId}
        {基础地址}/pay/notify/v2/alipay/{tenantId}/{revisionId}
```

要求：

- 基础地址公网可达、HTTPS、无 userinfo/query/fragment。
- 网关必须放行上述回调路径，不能被登录态、租户头或 CSRF 拦截。
- 路径中的租户与身份版本只用于定位；验签、商户、应用、金额、订单校验仍必须通过。
- 不要用 `localhost` 或内网地址冒充公网回调；纯前端探活不能证明微信/支付宝能回调成功。

---

## 五、管理端配置步骤

### 5.1 支付路由

1. 登录管理端，进入「支付管理 → 支付配置」。
2. 新增支付路由：支付渠道（微信/支付宝）+ 终端（小程序/App/H5）。
3. 保存后进入配置弹窗，不要在这里填写私钥路径。

### 5.2 上传材料

按用途上传：

| 渠道 | 材料 |
|---|---|
| 微信 | 商户 API 私钥 `apiclient_key.pem`、商户 API 证书 `apiclient_cert.pem`、微信支付公钥、API v3 密钥（按界面要求） |
| 支付宝 | 应用私钥、应用公钥证书、支付宝公钥证书、支付宝根证书 |

上传后逐项确认：用途、指纹、有效期、解析结果、私钥/证书配对结果。私钥不回显属正常。

### 5.3 创建并启用身份版本

1. 创建身份版本：填写 AppID、商户号/收款方 PID、环境。
2. 支付宝小程序必须显式选择买家身份模式：
   - `user_id` → 下单使用 `buyer_id`
   - `open_id` → 下单使用 `buyer_open_id`
   - 不选择则能力接口不下发，预支付拒绝。
3. 执行「本地检查」，只代表材料内部一致，不代表平台已签约。
4. 启用版本；列表状态应显示启用/材料齐备/检查结果。

### 5.4 关联配置

- 三方账号：
  - 微信小程序：`WX_MA`，AppID/AppSecret。
  - 支付宝小程序：`ALI_MINI`，AppID/身份模式；私钥/证书仍在支付材料中，不重复填写。
- 订单配置：填写回调基础地址。
- 小程序构建：微信/支付宝 AppID 通过租户构建配置注入，不要写死在源码。

### 5.5 旧配置迁移

- 使用「旧配置迁移」入口迁移历史明文配置；迁移可重入、冲突阻断。
- 只有能从历史字段/文件证明商户身份的旧单才自动绑定；无法证明的旧单会阻断取消/退款，需人工核对。
- 迁移成功前不要清理旧字段/旧证书目录；清理必须单独审批。

---

## 六、客户端发布顺序

**必须先发客户端，再发后端**，因为小程序预支付现在会校验 `platform-type`：

1. 发布带 `platform-type` 的新微信小程序版本，确认审核/灰度可用。
2. 发布 UniApp H5/App 新版本（如涉及）。
3. 再发布后端支付代码。
4. 若必须先后端上线，需临时允许旧版本不带平台头；该过渡必须有明确截止时间和监控，不能长期保留。

平台字段只是路由/体验约束，不是资金安全边界；真正的安全仍依赖买家身份归属、验签、商户/租户/金额校验。

---

## 七、联调测试矩阵

### 7.1 正向

| 场景 | 预期 |
|---|---|
| 微信小程序微信支付 | 下单成功、调起微信、回调落账、订单已支付 |
| 微信 App 微信支付 | orderInfo 为对象，Android/iOS 真机可调起 |
| 外部浏览器微信 H5 | 返回受信跳转 URL，支付后回跳，回调落账 |
| 支付宝 App | 返回签名订单串，调起支付宝，回调落账 |
| 支付宝 H5 | 返回 GET 跳转 URL，支付后回跳，回调落账 |
| 支付宝小程序 | `my.getAuthCode` 登录，`my.tradePay` 调起，回调落账 |
| 微信小程序内 | **不展示支付宝**；跨平台调用被 403 拒绝 |
| 支付宝小程序内 | **不展示微信支付**；跨平台调用被 403 拒绝 |
| 余额充值 | 支付成功只入账一次；取消后不重复入账 |
| 零元单/货到付款 | 不误走在线支付，原有规则不变 |

### 7.2 反向与资金安全

| 场景 | 预期 |
|---|---|
| 证书过期/私钥不配对/用途错误 | 本地检查失败，不能启用 |
| 身份模式未配置 | 能力不下发，预支付拒绝 |
| 微信 OpenID 填支付宝字段 | 拒绝，不能创建交易 |
| 回调验签失败/金额或商户不符 | 不落账，微信非 2xx，支付宝不返回 success |
| 重复回调/重复查单 | 幂等，只落账一次、只发一次业务事件 |
| 取消时渠道已支付 | 取消被拒绝，转退款/售后 |
| 取消时渠道查单超时/未知 | 不释放库存/优惠/积分，返回“确认中” |
| 取消与预支付并发 | 取消锁/支付提交锁互斥，不出现库存已释放但用户仍付款 |
| MQ 不可用 | 落账与 outbox 同事务成功，后台重投；不丢事件 |
| outbox 发送后标记失败 | 允许重复投递，消费端按业务键幂等 |
| 关单后再次支付 | 明确拒绝或走新业务订单，不复用已关闭支付单 |

### 7.3 必看数据

每笔测试记录：

- 业务订单号、支付单号、渠道交易号
- 支付配置路由 ID、身份版本 ID
- 回调记录 `pay_notify_record`
- 事件表 `pay_event_outbox` 状态与重投次数
- 退款单号、退款状态、退款查询结果
- 操作人、时间、环境、租户

---

## 八、验收门槛

### 可进入测试环境

- SQL 全部执行成功，Nacos 配置生效。
- 主密钥 Secret 已注入，应用启动无“主密钥未配置”。
- 管理端可创建路由、上传材料、创建身份版本、本地检查、启停。
- 能力接口按终端/平台返回正确渠道，未配置渠道不下发。

### 可标记“测试环境联调通过”

- 每个拟上线终端至少完成一笔真实小额支付 + 一笔退款。
- 回调与主动查单都能收敛到已支付，重复通知不重复入账。
- 取消/关单竞态、未知状态、MQ 重投至少各验证一次。
- 支付宝小程序真机完成授权、登录、支付、回调、查单、退款。
- 所有证据可追溯到订单、支付单、回调记录和操作人。

### 仍不能标记“生产可用”

- 未做真实商户对账、退款到账核对、异常晚付处理、证书轮换演练。
- 未确认 Cloud Seata 全局事务与 outbox 死信处理。
- 未完成主密钥备份/恢复演练。

---

## 九、回滚与运维

- 应用回滚：支付代码回滚后，新增表/列可保留；旧代码不读取新表，但不能回滚已启用的新身份版本。
- 渠道回滚：管理端停用渠道即可阻止新支付；历史回调/查单/退款仍应继续处理。
- 主密钥故障：禁止重新生成后直接覆盖；先用 `previous-keys` 恢复旧材料解密，再评估轮换。
- outbox 死信：当前无后台重放页，需要查库定位并按业务键人工重投；大量死信时先停渠道再处理。
- 取消锁：`pay_submitting=2` 最长 2 分钟阻塞重试，属有意保守策略；不要手工清锁，除非确认无在途支付。

---

## 十、本地已验证事实（2026-11-07）

- 支付域全量 **144 tests，0 失败**。
- 支付域主代码无 `AlipayClient.execute`，支付宝远程调用均为 `certificateExecute`。
- 本地 `aryn_boot` 已应用 209–212，结构检查通过，`pay_event_outbox` 存在。
- 本地 `aryn_boot` 增量脚本重复执行验证通过。
- Cloud 214–219 尚未在本地执行，必须在测试环境按顺序执行。

---

## 十一、待你确认/提供

- [ ] 测试环境地址与部署方式（boot 或 cloud）
- [ ] 真实微信商户/小程序/App 资料是否齐备
- [ ] 真实支付宝应用/证书是否齐备
- [ ] 是否确实要上线支付宝小程序；若否，P2 可保持不上线
- [ ] 支付宝小程序买家身份模式（user_id / open_id）
- [ ] 回调公网域名与商城回跳域名
- [ ] 是否允许进行小额实付与退款验证
- [ ] 旧微信小程序版本是否仍在线上，需不需要过渡期

以上确认后再执行真实联调；在此之前只能做代码、模拟和结构验证。
