# ARYN 微信／支付宝支付改造：官方证据摘要

检索日期：2026-11-06；检索词均带 2026。范围为普通商户，不混用服务商接口。仅研究公开资料，未调用商户接口、未交易、未修改业务代码或配置。

**证据等级**：微信下列支付文档已读取正文；支付宝文档中心本次抓取大多只有 JavaScript 壳，下文相关网页结论均标为 **S（仅官方搜索摘要，未核实正文）**；支付宝官方发布 Java SDK **4.40.1067.ALL** 的源码包已读取，标为 **C（源码核实）**。不引用第三方博客。下述“建议”是实施设计，不等同于平台承诺。

## 1. 管理页材料与微信双模式

两模式共有：`mchid`、各终端 `appid`、商户 API 证书对应的私钥 `apiclient_key.pem`、商户证书序列号、APIv3 密钥。商户私钥用于请求及调起支付签名；商户证书不是微信验签证书。APIv3 密钥用于 AES-256-GCM 解密通知／下载的平台证书，不是 RSA 签名私钥。[1][3][7]

- **微信支付公钥模式**：另收微信支付公钥 PEM 与 `PUB_KEY_ID_…`；用于应答／通知验签及指定敏感字段加密。公钥无固定到期时间，但不能等同于永不轮换。无需把平台证书当必填材料。[1][2]
- **平台证书模式**：另维护微信支付平台证书及其序列号、有效期；平台证书有效期为五年，须平滑更新。不要把商户 API 证书、公钥 ID、平台证书序列号混成一个字段。[1][2]
- **轮换建议**：凭证版本化，按 `Wechatpay-Serial` 选择可信公钥／证书，未知 ID 拒绝验签；新旧材料受控并存、切换及回退。官方“平台证书转公钥”有七天通知灰度，必须兼容两种验签；这不是“公钥每七天自动更新”规则，也不能据此承诺 SDK 自动下载新公钥。[2]

建议证书上传后解析序列号、检查私钥匹配；若 SDK 只需私钥及序列号，不应让商户误以为运行时必须读取商户证书文件。凭证加密存储、按租户隔离、不回显／写日志。开户签约、AppID 绑定和支付域名仍须商户在官方平台完成；“后台上传即可”以这些前置条件成立为限。[1][3][6]

## 2. 微信终端路由不能合并

| 场景 | 服务端及客户端契约 |
|---|---|
| 公众号内网页 | `POST /v3/pay/transactions/jsapi`，传该公众号 AppID 下的 `payer.openid`；[JSAPI 调起](https://pay.weixin.qq.com/doc/v3/merchant/4012791857)传 `appId/timeStamp/nonceStr/package/signType/paySign`，`package=prepay_id=…`、`signType=RSA`。[3] |
| 微信小程序 | 同 JSAPI 下单接口，但使用小程序 AppID、该 AppID 下 OpenID；`wx.requestPayment` 接收 `timeStamp/nonceStr/package/signType/paySign`。签名 AppID、下单 AppID、实际调起小程序必须一致。[3][4] |
| 原生 App | [APP 下单](https://pay.weixin.qq.com/doc/v3/merchant/4013070347)：`POST /v3/pay/transactions/app`；OpenSDK 参数含 `appId/partnerId/prepayId/nonceStr/timeStamp/sign`，Android `packageValue`（iOS `package`）为 `Sign=WXPay`。使用开放平台移动应用 AppID，不挪用公众号 ID。[5] |
| 微信外手机浏览器 | `POST /v3/pay/transactions/h5`，跳转返回的 `h5_url`，不是 JSAPI 参数包；链接有效期五分钟。[6] |

微信 App 还受开放平台登记的包名／应用签名等约束，见[官方 Android 接入](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)。建议身份按租户＋渠道＋AppID 存储，不能把不同端 OpenID 或 UnionID 直接互换。

H5 必填 `scene_info.payer_client_ip` 与 `scene_info.h5_info.type`（`Wap/iOS/Android`）；`app_name/app_url`在当前接口表为选填，**支付域名不是另一个必填 JSON 字段**。实际发起页域名须在商户平台配置，跳转保留正确 Referer；IP 应为真实用户终端 IP，建议只信任受控代理头。公众号内走 JSAPI／支付授权目录，外部 H5 走 H5 产品／域名，不能只因页面技术是 H5 就统一使用 H5 支付。[6][H5 接入易错点](https://pay.weixin.qq.com/doc/v3/merchant/4054257691)

## 3. 支付宝证书模式与 APP／H5

材料：AppID、应用私钥、应用公钥证书、支付宝公钥证书、支付宝根证书；显式设 `signType=RSA2`。CSR 用于向平台申请证书，不是运行时签名私钥；三类证书不能互换。[9-S][10-C]

**证书模式支持已由官方源码确认，不是依赖页面猜测**：`DefaultAlipayClient.java:32–42`接收三类证书配置与私钥；`AbstractAlipayClient.java:629–676,807–814`证明：

- APP：`AlipayTradeAppPayRequest` 对应 `alipay.trade.app.pay`，`sdkExecute(request).getBody()`返回签名订单串。
- H5：`AlipayTradeWapPayRequest` 对应 `alipay.trade.wap.pay`，`pageExecute(request,"GET").getBody()`返回 URL；默认 POST 返回表单，不能当 URL 使用。
- 两方法均传入 `appCertSN`，公共签名过程加入 `app_cert_sn`／`alipay_root_cert_sn`，因此支持证书模式。它们本身只本地组装签名数据，不证明网关受理、签约或支付成功。此结论限该官方通用 Java SDK，不能套到所有 Easy SDK 版本。[10-C]

## 4. 支付宝小程序：新老身份须分流

服务端 `alipay.trade.create` 创建交易，前端 `my.tradePay({tradeNO: trade_no})` 拉起收银台。[11-S][my.tradePay-S](https://opendocs.alipay.com/mini/api/openapi-pay)

最新官方摘要写明：新商户建议 `buyer_open_id`，存量可继续 `buyer_id`；使用前需确认应用的 openid 配置启用。另有“新增 APPID 灰度”说明，**不能推导所有新应用在固定日期后一律同规则，也不能硬编码永远传 2088 开头 buyer_id**。[11-S][12-S] SDK 同时存在 `buyerId/buyerOpenId`，但字段存在不证明所有应用均可使用。[10-C]

身份链路：`my.getAuthCode` → 后端 `alipay.system.oauth.token` → 按该应用实际身份模式使用 `user_id` 或 `open_id`，分别映射到买家字段。[13-S][10-C] 建议后台显式记录身份模式，校验身份与应用归属；未拿到匹配身份就阻止下单。两字段同传优先级及新应用切换的完整规则，本次未读到正文，**不猜测**。`my.tradePay` 的 success／9000 不能独立认定已支付，官方搜索摘要明确如此。

## 5. 回调、查单、关单与退款闭环

微信保留原始报文，用可信公钥／证书验 `Wechatpay-*` 签名，再用 APIv3 密钥解密。成功 ACK 为 HTTP **200 或 204、无包体**；失败用 4xx／5xx及错误说明。官方要求重入处理及查询补偿。建议验签后先可靠落库／入队再 ACK，异步核对租户、商户、AppID、订单、金额币种并幂等推进。[7]

支付宝证书验签使用 `AlipaySignature.rsaCertCheckV1(...,"RSA2")`，成功接收返回纯文本 **`success`**，不是 JSON；失败不回 success。建议同样核对 `app_id/seller_id/out_trade_no/total_amount`。[10-C][14-S] 订单状态以验签服务端通知或查单为准，不采用前端 success、跳转页面或 SDK `isSuccess()`作为已付款依据。

- 微信：[查单 GET](https://pay.weixin.qq.com/doc/v3/merchant/4012791838) `/v3/pay/transactions/out-trade-no/{out_trade_no}`；[关单 POST](https://pay.weixin.qq.com/doc/v3/merchant/4012791839) 同路径加 `/close`；[退款 POST](https://pay.weixin.qq.com/doc/v3/merchant/4012810597) `/v3/refund/domestic/refunds`；[退款查询 GET](https://pay.weixin.qq.com/doc/v3/merchant/4012810601) 同路径加 `/{out_refund_no}`。退款受理不等于到账；重试沿用原商户退款单号。
- 支付宝：`alipay.trade.query/close/refund/fastpay.refund.query`；源码确认相应方法及 `trade_status`、`refund_status`。`TRADE_SUCCESS/TRADE_FINISHED` 与支付有关；退款最终核对 `REFUND_SUCCESS`，不能仅凭接口通用成功码。[10-C][退款查询-S](https://opendocs.alipay.com/open/8c776df6_alipay.trade.fastpay.refund.query)

## 6. 沙箱与无商户阶段验收

微信官方明确：“APIv3暂未提供独立的沙箱测试环境和测试参数”，生产不支持压测。[8] 因此当前仅做本地假密钥／合成报文测试、验签失败／金额不符／重复回调／查单补偿测试；不是微信官方沙箱验收，禁止自动发起真实小额交易。

支付宝有独立沙箱，但产品及功能范围有限。官方摘要列 APP、手机网站支付，并有“当面付不支持小程序应用类型”“APP SDK 暂不支持鸿蒙”等限制；不能扩展成所有小程序／证书模式都支持或都不支持。[15-S] [APP 沙箱指引-S](https://opendocs.alipay.com/support/01rftu)要求沙箱客户端。具体租户应用、证书模式及平台组合仍待登录官方控制台复核；沙箱通过不证明生产签约或域名就绪。本次未运行沙箱。

## 7. 配置诊断的证明边界

建议分为“离线有效／远端查询可用／场景待验收”，不要只有一个绿色开关。

1. **立即可做**：格式、证书有效期／序列号、私钥公钥匹配、本地签名验签、客户端参数结构；只能证明材料内部一致，不能证明平台已登记或 APIv3 密钥与平台一致。[1][10-C]
2. **以后明确授权才做**：查询指定既有订单／退款；平台证书模式可按[平台证书文档](https://pay.weixin.qq.com/doc/v3/merchant/4012068814)获取证书并验签解密。不得把证书下载当公钥模式通用连通测试。合法查询成功只证明本次凭证／请求／响应链路，`ORDER_NOT_EXIST`也不是全渠道开通证明。
3. **仍需人工及场景验收**：签约权限、AppID 绑定、客户端包签名、H5／JSAPI 域名、真实通知入站。所谓[“查询商户号绑定的APPID”](https://pay.weixin.qq.com/doc/v3/merchant/4013287174)正文是商户平台页面操作，并非可调用查询 API。自检 URL 能访问不证明支付平台真实通知可达。诊断不得顺带下单、关单或退款。

## 主要官方来源（15 项）

1. [微信证书密钥概览](https://pay.weixin.qq.com/doc/v3/merchant/4024350132)
2. [平台证书切换微信支付公钥](https://pay.weixin.qq.com/doc/v3/merchant/4012154180)
3. [微信 JSAPI 下单](https://pay.weixin.qq.com/doc/v3/merchant/4012791856)
4. [微信小程序调起支付](https://pay.weixin.qq.com/doc/v3/merchant/4012791898)
5. [微信 APP 调起支付](https://pay.weixin.qq.com/doc/v3/merchant/4013070351)
6. [微信 H5 下单](https://pay.weixin.qq.com/doc/v3/merchant/4012791834)
7. [微信支付成功通知](https://pay.weixin.qq.com/doc/v3/merchant/4012791836)
8. [微信最佳安全实践／APIv3 沙箱边界](https://pay.weixin.qq.com/doc/v3/merchant/4012073699)
9. [支付宝设置密钥加签方式（S）](https://opendocs.alipay.com/common/02kdnc)
10. [支付宝官方 Java SDK 4.40.1067.ALL 源码包（C）](https://repo.maven.apache.org/maven2/com/alipay/sdk/alipay-sdk-java/4.40.1067.ALL/alipay-sdk-java-4.40.1067.ALL-sources.jar)；同版本 POM 已核对 `com.alipay.sdk`、开发者 antopen、官网 open.alipay.com。
11. [支付宝小程序交易创建（S）](https://opendocs.alipay.com/mini/6039ed0c_alipay.trade.create?pathHash=779dc517&scene=de4d6a1e0c6e423b9eefa7c3a6dcb7a5&ref=api)
12. [支付宝 openid 简介（S）](https://opendocs.alipay.com/mini/0ai2i6?pathHash=13dd5946)
13. [支付宝 openid 开发流程（S）](https://opendocs.alipay.com/mini/0ai5vq)
14. [支付宝异步通知说明（S）](https://opendocs.alipay.com/open/04ij9t?pathHash=8b971aad&ref=api)
15. [支付宝沙箱支持产品范围（S）](https://opendocs.alipay.com/common/097jyi)
