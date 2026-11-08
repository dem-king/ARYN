# 支付管理后台自助配置与交易闭环完善方案

**状态：** 待评审；本轮仅研究与制订方案，不修改业务代码、不操作真实商户、不执行数据库迁移。

**目标：** 先完善支付代码和安全边界；商户后续通过管理后台填写信息、上传证书、检查配置和启停渠道，无需再改 Java 源码或填写服务器文件路径。

**架构：** 保留现有 pay-api/pay-biz、Dubbo、MyBatis 租户隔离、管理端页面结构和 UniApp。支付域统一管理加密凭据、支付能力、渠道参数、查单/回调落账和关单；订单与充值只通过 pay-api 调用，不直接依赖 pay-biz。

**技术栈：** Spring Boot 3、Java 21、MyBatis-Plus、Sa-Token、RocketMQ、MySQL、Vue 3/Element Plus、UniApp；现有支付依赖为 weixin-java-pay 4.7.9.B、alipay-sdk-java 4.39.259.ALL，不预设升级 SDK。

**代码基线：** 2026-11-06，当前分支 ship-chandler，调研开始时 HEAD=03d1e565，工作树无修改。实施前重新核对工作树、SQL 编号及并行修改。

---

## 一、范围与交付边界

### 1.1 推荐路线

选择“保留现有支付域，完善后台配置与渠道协议”路线，不重写商城、不引入新的聚合支付平台。

| 方案 | 优点 | 代价/判断 |
|---|---|---|
| 仅修改表单和证书上传 | 改动最少 | 不能修复 H5/App 协议、查单及取消竞态，不满足可上线目标 |
| 完善现有支付域与后台自助配置 | 复用已有回调验签、租户隔离、退款代码 | 本方案推荐；必须同时修改三端契约与资金状态处理 |
| 引入新聚合支付系统或服务商模式 | 可获得更多通道能力 | 商户体系、运维及迁移成本明显增加，本轮不做 |

### 1.2 支付场景

| 场景 | 本轮处理 |
|---|---|
| 微信小程序微信支付 | 核心交付；完善资料诊断、OpenID/AppID 一致性、回调与查单 |
| 微信 App 支付（Android/iOS） | 核心交付；修复参数对象与大小写映射、完善原生模块构建要求 |
| 外部浏览器微信 H5 支付 | 核心交付；新增 H5 Handler，补实际客户端 IP、场景信息和跳转 |
| 支付宝 App 支付 | 核心交付；改为 App 支付订单串，不能复用小程序交易创建协议 |
| 支付宝 H5 支付 | 核心交付；改为官方页面支付流程，统一返回跳转 URL |
| 支付宝小程序支付 | 独立扩展阶段；必须连同支付宝身份授权、应用绑定、构建配置完整接通，不只增加一个按钮 |
| 微信内网页的公众号 JSAPI | 本轮不开放；不能将外部 H5 支付当作公众号 JSAPI |
| PC 网站支付、服务商/子商户、分账、代收 | 本轮不做，后台不能展示成已经支持 |
| 货到付款、余额、零元单 | 不改业务规则，纳入回归，不能被在线渠道开关误伤 |

不承诺在微信小程序中直接增加支付宝收银台。本轮也不支持同一业务订单自由切换已经发起的支付渠道：首次发起后锁定方式；同方式可幂等重试，换方式需先确认原渠道关闭并重新下业务订单。若后续必须原订单切换，需要独立设计 payment attempt，不能覆盖旧支付单。

### 1.3 后台可配置与不可热更新的边界

后台负责商户信息、证书/密钥、支付产品启停、回调地址维护入口、配置诊断、验证记录。

以下仍属于一次性外部前置条件：商户开户、产品签约、AppID/商户绑定、平台域名配置、服务器公网 HTTPS、部署主密钥及可用数据库/MQ。原生 App 的支付模块、AppID、包名/签名、iOS Universal Links 和小程序构建身份需要正确打包/发布；后台改配置不能热更新已经安装的原生包。

---

## 二、现有代码事实与复用边界

| 事实 | 证据（仓库相对路径） | 处理 |
|---|---|---|
| 保存配置只校验基础字段与重复 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/service/impl/PayConfigServiceImpl.java:35` | 增加按渠道/场景的服务端校验，保留草稿与启用校验区别 |
| 证书上传返回服务器绝对路径 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/controller/PayConfigController.java:113` | 改为不透明 materialId，不允许手填路径或远程文件 URL |
| 微信已使用 API v3 公钥模式 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/config/WxPayConfiguration.java:37` | 保持该模式，去掉与本轮 v3 流程无关的 v2/P12 必填暗示 |
| 支付宝已使用证书模式/RSA2 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/config/AliPayConfiguration.java:24` | 保持证书模式，分清应用私钥与平台公钥证书 |
| 订单和充值回调基础地址取订单配置 | `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java:1199`；`aryn-mall-java/aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/service/impl/RechargeOrderServiceImpl.java:124` | 本轮保留单一来源，在支付配置页面增加联动维护入口，不新增第二套通知地址 |
| 微信用户身份链路已存在 | `aryn-mall-java/aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/dubbo/RemoteSocialUserServiceImpl.java:49`；`aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/service/TocLoginService.java:195` | 复用微信登录，不宣称 OpenID 链路缺失；支付宝需独立实现 |
| 回调已有验签、金额/商户检查及条件更新 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/service/impl/PayNotifyRecordServiceImpl.java:76`、`:215`、`:349` | 保留并抽出回调/查单共用落账逻辑，不降低校验 |
| 主动支付查单仅支持微信 | 同上 `:150` | 增加支付宝支付查询，不误称支付宝退款查询也缺失 |
| 支付宝退款和待确认退款查单已实现 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/handler/impl/AlipayRefundHandler.java:47`、`:107`、`:150` | 复用并补测试，不重写整套退款 |
| App/H5 与小程序支付宝协议混用 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/handler/impl/AlipayAppPayHandler.java:51`；`AlipayH5PayHandler.java:53` | 按对应终端官方接口改造 |
| 微信 App 的 orderInfo 当前统一 JSON.stringify | `aryn-mall-uniapp/src/sub-pages/utils/pay.ts:103` | Android/iOS 改为对象；不混用 HarmonyOS 的字符串规则 |
| 同一订单支付方式被一致性校验锁定 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/handler/AbstractPayOrderHandler.java:78` | 明确前后端行为与文案，不静默覆写旧渠道 |
| 当前取消会先改业务状态并返还库存 | `aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java:475` | 增加支付关闭/未知状态门禁，保留已有补偿链路并检查持久重试 |
| 支付成功消息在落账事务内同步发送 | `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/service/impl/PayNotifyRecordServiceImpl.java:455` | 引入支付专用 outbox，避免数据库回滚但业务已收到成功消息 |
| 普通私有上传不等于密钥保险箱 | `aryn-mall-java/aryn-common/aryn-common-storage/src/main/java/com/aryn/cloud/common/storage/handler/ArynUploadFileHandler.java:24` | 默认实现仍可走普通上传，不能直接复用于支付私钥 |
| 支付 Mapper 当前 DEBUG | `aryn-mall-java/aryn-boot/src/main/resources/application.yml:97` | 双模式收紧日志，避免 SQL 参数/异常暴露密钥 |
| 已有 SQL 全量构建、唯一索引与租户治理 | `aryn-mall-java/db/boot/build-full-sql.mjs:6`；`db/boot/168payment_hardening_incremental.sql:18`；boot `application.yml` 登记 `pay_config` | 复用，不把 SQL 未显式 tenant_id 错报为无隔离 |

本地已通过 `javap` 核实当前 SDK 支持内存材料：微信 `setPrivateKeyContent`、`setPrivateCertContent`、`setPublicKeyContent`；支付宝 `CertAlipayRequest.setCertContent`、`setAlipayPublicCertContent`、`setRootCertContent`。因此无需为每个支付实例维护公开路径或共享明文证书目录。

---

## 三、后台改造规格

### 3.1 保留原页面结构，按渠道显示资料

继续使用“支付配置列表 + 编辑弹窗”，不大改导航和视觉风格。默认未选择支付类型时不显示任一渠道证书，修正目前空选项也显示支付宝证书标签的歧义。

列表增加：支付渠道、终端、应用、商户、启用状态、资料检查结果、最近验证时间。操作为“配置 / 检查 / 启用或停用 / 查看变更记录”；已关联交易的配置不允许随意删除。

编辑区分为：基本信息、密钥与证书、关联配置检查、启用与验证。保存草稿不要求当前已经有商户资料；启用时必须满足对应场景必填项及本地安全校验。

### 3.2 微信支付资料

- 支付端、AppID、微信商户号。
- API v3 密钥：敏感输入，后续只显示“已设置”，不回显掩码供原样提交。
- 商户 API 私钥：上传 `apiclient_key.pem`。
- 商户 API 证书：上传 `apiclient_cert.pem`，提取序列号、有效期和公钥，检查与私钥配对。
- 微信支付公钥：上传公钥 PEM，填写对应 `PUB_KEY_ID_...`。
- 微信 H5：检查产品开通/域名配置，服务端生成支付所需 H5 场景参数，客户端 IP 从可信代理链获取。
- 关联显示当前小程序登录 AppID 与支付 AppID 是否一致，允许跳转或打开现有三方账号维护表单；不另存一份 AppSecret。

界面必须区分“商户公钥证书”和“微信支付公钥”：前者属于商户身份/签名配置，后者用于验证微信响应/通知。公钥文件可能没有 X.509 证书有效期，不得强行显示不存在的到期日期。公钥 ID 与文件在平台上的对应关系不能仅靠本地解析证明。

本轮不提供微信 API v2 密钥/P12 作为 v3 支付必填项；也不新增没有交付验证的“平台证书模式”开关。

### 3.3 支付宝资料

- 支付端、支付宝应用 AppID、收款方 PID（用于对应 `seller_id` 校验，不能与微信商户号混淆）。
- 应用私钥：粘贴或上传一种方式即可，服务端规范化后只加密保存；不是“支付宝平台私钥”。
- 应用公钥证书、支付宝公钥证书、支付宝根证书。
- 展示并核对证书有效期、指纹、应用证书与应用私钥的配对关系；序列号由 SDK/服务端处理。
- 生产/沙箱隔离，平台网关为后端白名单，不允许租户任意填写网关地址。沙箱仅出现在部署允许的测试环境，不能将沙箱到账写入真实商城订单或余额。

证书解析和私钥配对属于本地检查；不把证书存在等价成产品已经签约。

### 3.4 上传反馈

每份材料显示名称、用途、大小、指纹、可适用的有效期、解析结果、上传时间；私钥只显示配置状态和配对结果。支持替换，不提供私钥下载/明文回显。

上传校验包括：权限、当前租户、材料用途、尺寸、PEM/DER 内容、算法/密钥长度、证书结构、签名链可验证项；不要仅靠文件后缀。禁止 ZIP 解压、服务器路径输入和任意远程 URL 拉取。格式、配对或证书链错误给清晰中文原因，不显示原文密钥或完整 SDK 请求。

### 3.5 三层诊断，不做假“接入成功”

| 层级 | 检查内容 | 能证明什么 |
|---|---|---|
| 本地资料校验 | 字段、证书、私钥配对、关联 AppID、回调 URL 规则、资料权限、可解密 | 本系统能够构造正确格式的请求 |
| 渠道通信验证 | 显式授权的无资金变更请求、响应验签、返回码分析 | 某次调用的网络和鉴权结果，不保证所有支付产品已开通 |
| 真实闭环验证 | 经授权的小额支付、渠道通知、业务落账、退款与退款确认 | 指定终端和环境真实链路已验证 |

不将“查无此订单”普遍解释为支付产品可用；不同渠道返回码分别处理。没有官方通用鉴权探针时显示“待真实联调”，不虚构接口。纯前端探活、服务器自访或 HTTP 200 都不能证明微信/支付宝能向公网回调。

启用状态与验证状态分开：允许有权限的管理员在本地检查通过、确认平台前置条件后启用待联调渠道；界面仍标记“未实付验证”，不循环要求先支付后才能开启。真实验证记录由有效回调/查单事实生成，不允许手工勾选伪造。

---

## 四、安全存储、身份和回调设计

### 4.1 支付域内加密，不复用公开素材库

推荐将体积受限的密钥及证书材料加密入库。AES-GCM 使用每次写入独立随机 nonce，AAD 绑定租户、材料 ID、用途和结构版本，保存 keyId。部署主密钥通过环境变量/Secret 注入，所有实例使用一致密钥集；不得存入 Git、普通配置表、前端或 Nacos SQL 明文种子。

主密钥缺失时阻止相关配置启用和支付签名，不回退为明文存储。设计备份/恢复与主密钥轮换，防止数据库可恢复但密钥遗失导致历史交易无法退款。SDK 仅在服务端受控内存中消费解密内容；审计和异常中禁止记录内容。

### 4.2 最小数据模型

1. `pay_config` 保留稳定路由 ID 与租户/渠道/终端唯一约束，增加启用状态、活动身份版本和并发版本号；不向客户端暴露密钥实体。
2. 新增 `pay_config_revision`：不可变的渠道、商户、应用、环境身份及材料引用，记录校验结果、创建者和时间。换商户是新身份版本，不修改历史版本。
3. 新增 `pay_config_material`：材料用途、密文、keyId/nonce、公开指纹和可适用的证书元数据；材料归属及引用必须同租户。
4. `pay_trade_order` 绑定身份版本及后续状态字段，`pay_refund_order` 通过原支付单获得身份；不得运行时按当前终端配置切换到新商户。
5. 新增 `pay_event_outbox`：事件 ID、业务键、事件类型、非敏感负载、投递状态/次数/下次时间。范围仅限支付/退款事件，不建通用事件平台。

新增表全部包含 tenant_id、逻辑删除和审计字段，并登记 boot/cloud 租户治理。保留现有索引治理；不默认允许同一租户同一支付端激活多个商户路由。

### 4.3 换证与历史交易

- 商户身份冻结与签名凭据有效期分开：老订单仍属于原商户/AppID/环境；同一身份的证书轮换后，主动查询/退款/关单使用该身份当前有效签名凭据，不能永远强制使用已吊销私钥。
- 微信平台公钥按通知 serial/keyId 定位并保留必要过渡版本；API v3 密钥变化另设有限的解密过渡，不在验签失败时放宽校验。
- 停用仅禁止新发起，不禁止历史回调、查询、退款和补偿。
- 新通知路由携带可在解密前定位身份的版本标识，例如 `.../notify/v2/wx/{tenantId}/{identityRevisionId}`，避免和旧路由同结构冲突；路径值仅用于定位，仍必须验签并检查真实商户/金额/订单。
- 旧回调入口仅为已经发出的存量支付保留明确迁移窗口；历史支付未闭环前不能删除。不是长期保留两套业务实现。

### 4.4 回调地址单一来源

本轮继续以 OrderConfig.notifyUrl 为基础地址，支付页面通过现有订单配置 API 打开联动维护入口，遵守原权限。统一的 PayCallbackUrlBuilder 生成各渠道支付/退款地址，展示 boot/cloud 最终结果。

基础地址只允许可接受的 HTTPS 公网地址，生产禁止 localhost、内网、userinfo、任意 query/fragment；反向代理路径可显式配置但不能重复拼接 boot/pay。前端 returnUrl/quitUrl 改由可信商城站点和固定路由构造，不接受任意站外跳转。

不做任意 URL 的服务器主动探测，避免诊断功能引入 SSRF；必须进行回调验证时使用限定地址和明确挑战流程，结果仍与渠道真实通知区分。

---

## 五、支付协议与交易状态完善

### 5.1 各端返回统一自有契约

不直接把 Java SDK Response 序列化到 C 端。预支付结果明确 `channel`、`terminal`、`invokeType`、`orderNo`、`params`：

- 微信小程序：`WX_MINI`，返回 timeStamp、nonceStr、package、signType=RSA、paySign。
- 微信 Android/iOS App：`WX_APP`，返回 appid、partnerid、prepayid、package、noncestr、timestamp、sign 对象。
- 微信 H5：`REDIRECT`，返回经过白名单检查的支付 URL。
- 支付宝 App：`ALI_APP`，返回已签名订单字符串。
- 支付宝 H5：`REDIRECT`，后端生成 GET 跳转 URL，不将任意 HTML 注入页面，也不把 HTML 表单字符串当 URL。
- 支付宝小程序：`ALI_MINI`，返回 tradeNo，再映射至 uni.requestPayment 的 orderInfo。
- 已支付：独立 `ALREADY_PAID` 结果，C 端查询业务状态，不误当支付参数再次拉起。

`paymentType + tradeType + 运行终端 + 当前租户可用配置` 必须一致；服务端做校验，不信任前端隐藏按钮。付款金额仍来自订单/充值单，不来自客户端展示值。

### 5.2 前后端渠道能力一致

增加支付能力读取接口，返回当前租户指定终端已经实现、已启用且本地材料合格的渠道，不返回商户密钥/文件路径。购物支付和余额充值共用；没有渠道时显示具体原因，不出现可点击但必然失败的占位入口。

微信 H5 仅面向外部浏览器。微信内网页在本轮不提供尚未实现的公众号 JSAPI，给明确提示或合法的已有入口，不循环跳转。App 支付模块未安装或运行 AppID 不符时，客户端提前提示并在后端保留安全校验。

### 5.3 支付成功以服务端为准

支付宝 App 使用 `AlipayTradeAppPayRequest + sdkExecute(request).getBody()`；支付宝 H5 使用 `AlipayTradeWapPayRequest + pageExecute(request, "GET").getBody()`。两者是本地签名/组装操作，不沿用当前远程 `certificateExecute` 后的 `isSuccess()` 判断，不把生成订单串或 URL 当作渠道受理成功；真实支付状态以通知/查单为准。当前锁定的 4.39.259.ALL 源码已确认两者会传入 appCertSN，支持证书模式。

支付宝增加支付查询，与微信共用“验签/验真 → 核对商户、订单、金额、币种 → 原子落账 → outbox”业务路径。微信支付及退款通知成功返回 HTTP 204 无包体，验签/业务校验失败返回相应 4xx/5xx，不能失败仍 HTTP 200；支付宝成功返回纯文本 `success`，不用商城 Result 包装。重复有效通知仍成功确认但不重复落账，处理失败保留渠道重试机会。

支付结果页和充值结果页显示“确认中/已支付/未支付/已关闭”，有限次数轮询；原生 success 或 H5 returnUrl 不直接视为成功。

渠道超时、响应不明、查单暂时失败保留 UNKNOWN/处理中，不当作失败重新收款。保留渠道错误码，区分用户取消与配置错误，不把所有错误统一提示“取消支付”。

### 5.4 关单与并发保护

补充 SUBMITTING/PENDING/CLOSING/CLOSED/UNKNOWN/PAID 等必要生命周期，旧 payStatus 如被业务广泛引用，仅作为明确映射的兼容投影，不形成独立可冲突状态源。

取消与预支付需要同一持久化门禁：先短事务记录提交或关单意图，再事务外调用渠道，最后短事务推进结果。覆盖“取消时渠道下单仍在途”“本地未建单时并发发起支付”“已付款但回调晚到”等情况。

只有渠道明确未支付且已关闭，或经提交门禁确认从未发起支付，才能最终取消并释放库存/优惠。未知、网络失败、暂时查无单不能直接释放。已经支付则先落账并提示转售后；存量异常晚付继续走既有补偿策略，补充持久重试和人工异常队列，不能静默吞掉资金。

支付/退款落账与 outbox 同事务提交，提交后重投；消费者按业务状态/事件 ID 幂等。不能只把当前 syncSend 改成 afterCommit 回调就声称解决进程崩溃丢消息。

---

## 六、可执行任务拆分

每项按“写失败测试 → 验证失败原因 → 最小实现 → 定向回归 → 阶段审查”执行；不自动提交 Git、不创建 worktree。以下新文件是计划新增，不表示本轮已经创建。

### 任务 1：冻结场景契约与回归基线

**修改：**
- `aryn-mall-java/aryn-pay/aryn-pay-api/src/main/java/com/aryn/cloud/pay/api/enums/PayTradeTypeEnum.java`
- `aryn-mall-java/aryn-pay/aryn-pay-api/src/main/java/com/aryn/cloud/pay/api/remote/RemotePayService.java`
- `aryn-mall-uniapp/src/sub-pages/utils/pay.ts`

**测试：** 现有 `PaymentHandlerValidationTest`、`PayNotifyRecordServiceImplTest`、`PayOrderQuerySettlementTest`；新增支付渠道契约测试覆盖各终端参数、已支付返回和关闭后重试限制。

**完成条件：** 支持矩阵、DTO、身份字段和错误码先对齐；现有微信/退款/零元单用例建立可重复基线。

### 任务 2：加密材料及双模式数据迁移

**新增：** pay-api 的 `PayConfigMaterial`/`PayConfigRevision` 实体，pay-biz 的 `PayCredentialService`、材料/版本 Mapper、密文处理测试。

**修改：** `PayConfig.java`、`PayConfigServiceImpl.java`、`PayConfigMapper.xml`、boot 配置及 cloud Nacos 配置；双模式增量 SQL 和初始化基线。

**测试先行：** 同内容重复加密密文不同；AAD/租户/材料用途不匹配解密失败；主密钥缺失拒绝；列表详情/日志不包含密文解密值；并发发布版本只有一个生效。

**迁移：** 导入旧明文数据库字段与允许目录下现存证书，生成材料和身份版本，逐条验证并绑定历史交易。仅有当前配置无法证明旧订单商户身份时阻断自动回填，交管理员核对。导入必须可重入、可审计，不通过 SQL 拼接密钥。成功后按批准的发布流程清理旧明文字段/文件，不永久双读；缺失材料由后台重新上传。

### 任务 3：资料 API、上传与配置诊断

**修改：**
- `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/controller/PayConfigController.java`
- `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/service/impl/PayConfigServiceImpl.java`
- `aryn-mall-java/aryn-pay/aryn-pay-biz/src/test/java/com/aryn/cloud/pay/controller/PayConfigControllerTest.java`

**新增：** 分渠道写 DTO、安全读 VO、`PayConfigCheckService` 及对应测试。上传返回 materialId/元数据；启用和诊断有独立权限与操作审计；未修改的敏感字段通过“未提交=保持”表达，不回传星号字符串。

**测试先行：** 错文件/错用途/超限/过期/不配对/跨租户/无权限/未配置主密钥全部被拒绝；草稿可缺资料但不能启用；任何本地诊断不能自动标记真实支付通过。

### 任务 4：SDK 初始化与身份固定

**修改：**
- `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/config/WxPayConfiguration.java`
- `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/config/AliPayConfiguration.java`
- `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/service/impl/PayNotifyRecordServiceImpl.java`
- `aryn-mall-java/aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/controller/PayNotifyRecordController.java`

**新增：** `PayCallbackUrlBuilder`、按身份和材料版本解析 SDK 的支付域内部服务。SDK 初始化与验签都改为内容模式，不能只改下单而保留回调依赖旧绝对路径。必要缓存以租户+身份+材料版本为键，更新可失效并关闭被淘汰的 HTTP client。

**测试先行：** 更换当前商户不改变旧订单；同商户换证后历史订单可使用有效凭据退款；停用后旧回调仍能处理；错误签名/错误版本/过渡期之外的材料均拒绝。

### 任务 5：微信 H5、App 与支付宝 App/H5 协议

**修改：** `WechatAppPayHandler.java`、`WechatJsApiPayHandler.java`、`AlipayAppPayHandler.java`、`AlipayH5PayHandler.java`、`AbstractPayOrderHandler.java`、`CreateOrderReqDTO.java`；新增 `WechatH5PayHandler.java`。

**前端修改：**
- `aryn-mall-uniapp/src/sub-pages/utils/pay.ts`
- `aryn-mall-uniapp/src/sub-pages/order/order-pay/index.vue`
- `aryn-mall-uniapp/build/manifest-factory.mjs`

**测试先行：** 不同终端不串协议；Android/iOS 微信 App 为对象；支付宝 App 为签名字符串；H5 为受信 URL；错误 buyer 身份、伪造客户端 IP、任意 returnUrl、非法金额/渠道组合被阻断。原生打包信息不硬编码真实密钥。

### 任务 6：后台页面与能力下发

**修改：**
- `aryn-mall-ui/apps/web-ele/src/views/pay/config/index.vue`
- `aryn-mall-ui/apps/web-ele/src/views/pay/config/form.vue`
- `aryn-mall-ui/apps/web-ele/src/components/cert-upload/index.vue`
- `aryn-mall-ui/apps/web-ele/src/api/pay/config.ts`
- `aryn-mall-ui/apps/web-ele/src/views/order/config/form.vue`
- `aryn-mall-ui/apps/web-ele/src/views/user/social-account/form.vue`

**新增：** 支付域能力接口及 C 端类型/API。复用既有样式与表单，不引入大型新页面架构。联动订单配置/三方账号时保留各自权限，不以支付编辑权限绕过。

**测试先行：** 切换渠道不会串填证书；读取配置不带私钥；上传失败不能显示成功；禁用或资料错误渠道前后端均不可发起；无渠道/无权限/证书到期/小屏/暗色主题有正确状态。

### 任务 7：查单、关单、可靠事件与退款回归

**修改：** `RemotePayService.java`、`RemotePayServiceImpl.java`、`PayNotifyRecordServiceImpl.java`、`PayTradeOrder.java`、支付 Mapper；订单 `OrderInfoServiceImpl.java`、`OrderCancelListener.java`、`OrderJobHandler.java`；充值 `RechargeOrderServiceImpl.java`。

**新增：** 支付专用 outbox Mapper/投递任务、支付宝支付查单、两渠道关单/未知态补偿。旧支付和退款监听器继续使用业务幂等更新；支付宝退款查单复用已有 Handler/任务。

**测试先行：** 重复/乱序回调、回调与主动查询并发、MQ 不可用、提交后进程退出、取消与支付并发、已关闭单重试、余额充值重复入账、部分退款/重复退款。验证异常晚付不会同时“订单取消返库存”与“正常已付发货”。

### 任务 8：支付宝小程序独立补全

**修改：** `TocLoginService.java`、用户三方身份 API/实现、管理端三方账号表单、UniApp 平台身份请求头/登录与支付；复用 `AlipayJsApiPayHandler.java`。

**要求：** 按实际支付宝应用授权能力实现 code 换身份，区分用户在本系统的 userId 与支付宝 buyer 身份；不得拿微信 OpenID 填支付宝 buyer_id。新旧应用身份字段、OAuth 权限以官方接口文档及实际应用权限确定并分别测试。未完整完成前能力接口不下发该渠道。

**验收：** 支付宝小程序独立构建、授权、登录租户绑定、创建交易、my.tradePay/uni.requestPayment、回调及查单全部通过；不能靠 H5/App 成功推定小程序可用。

### 任务 9：发布、操作说明与双模式验收

资料上传、草稿、启用、轮换、停用和故障定位均需要简短操作说明。记录“本地通过/沙箱通过/真实通过”的实际证据，未做的不得标绿。

阶段顺序：P0=任务1–4的安全配置底座；P1=任务5–7的核心渠道闭环；P2=任务8的支付宝小程序；发布验收贯穿每阶段。每阶段先审查再进入下一阶段，P0完成不等于可以真实收款。

---

## 七、双模式数据库与配置约束

- 截至调研时 boot 普通增量最高 208、cloud 普通增量最高 213；cloud 的 `999aryn_job.sql` 是独立调度基线，不按普通业务增量取号。实施时重新查重并分配一致的新脚本名，禁止覆盖已有脚本。
- boot/cloud 同名增量需可重复执行；先检查重复/不完整身份数据，禁止 DROP/TRUNCATE 存量支付记录，不以自动删除解决冲突。
- boot 登记 `db/boot/build-full-sql.mjs`，重新生成 `aryn_boot_full.sql` 并运行校验；cloud 同步 `6aryn_pay.sql` 等受影响服务基线、Nacos 租户白名单与同名增量。
- 新表需要双模式租户治理；新增权限、字典与补偿任务需要双模式种子，cloud 如涉及 auth/user/order 同步对应配置。
- 新增主密钥配置只给环境变量占位与操作要求，绝不把实际值写入 SQL/YAML/文档。
- C 端/管理端 URL 首段保持服务域；复用 parseOpenBoot/rewriteBootUrl，boot/cloud 两种模式均检查能力、上传、预支付、查询以及新旧回调路由。
- 旧配置停机迁移和旧回调保留窗口属于正式迁移策略，不是随意保留两套实现；迁移前备份，经授权后执行，结果逐条核对。

## 八、验证清单与运行命令

以下为实施阶段的待执行命令，本轮没有运行，也没有宣称通过。后端使用 JDK 21，定向测试必须带 -am；不使用工作树中已删除的历史 harness 脚本。

```bash
# 在 aryn-mall-java 目录执行
mvn test -pl aryn-pay/aryn-pay-biz -am -Dtest=PayConfigControllerTest,PaymentHandlerValidationTest,PayNotifyRecordServiceImplTest,PayOrderQuerySettlementTest -Dsurefire.failIfNoSpecifiedTests=false
mvn clean compile -pl aryn-boot -am
mvn test -pl aryn-boot -am
node db/boot/build-full-sql.mjs
node db/boot/verify-full-sql.mjs

# 在 aryn-mall-ui 目录执行
pnpm check:type
pnpm test:unit
pnpm lint
pnpm dev:ele

# 在 aryn-mall-uniapp 目录执行
pnpm type-check
pnpm test:unit
pnpm dev:h5
```

新测试类加入定向测试列表；修改后的代码还需检查跨 biz import 及公共依赖层级。若 Graphify 在实施环境不可用，记录限制并用引用搜索替代影响分析。

### 无商户资料阶段可以完成

- 合成测试证书的解析/配对/过期校验、加密及租户测试。
- Mock SDK 与真实签名算法生成的测试通知验证，不绕过验签接口。
- boot/cloud 路由、页面交互、禁用渠道、无资料草稿、未知态/MQ失败/并发状态机测试。
- MySQL 上迁移可重入、唯一索引并发约束和 outbox 崩溃恢复测试。
- 后台及 H5 启动开发服务并用浏览器验证；小程序/原生端需对应工具和真机，不能用网页截图代替。

### 后续资料齐备后才能完成

- 每个拟上线终端独立授权小额实付与退款，核对渠道账单、支付单、业务订单和余额变化。
- 渠道真实通知可达，重复通知不重复入账，通知延迟时主动查单可收敛。
- 关单与实付竞态、证书轮换后历史退款、全额/部分退款、停用后历史回调。
- 沙箱成功仅标记沙箱；未获得商户资料时，只能交付“代码及模拟验证完成”，不能写“生产支付已接通”。

## 九、官方依据与证据管理

平台协议以官方文档和当前 SDK 为准，不以社区博客或页面选项作能力证明。完整核验记录见 [官方证据摘要](2026-11-06-payment-official-research.md)。微信资料已读取正文；支付宝部分文档中心只返回 JavaScript 页面壳，相关身份/沙箱规则只获得官方搜索摘要，研究附录已标注证据等级，不冒充全文核实。

支付宝 App/H5 的关键结论有官方 SDK 源码证明：研究附录核实官方发布的 4.40.1067.ALL，主代理进一步读取项目当前锁定的 4.39.259.ALL sources.jar，确认 `AbstractAlipayClient.java:628–675` 中 `pageExecute(GET)` 返回 URL、`sdkExecute` 返回签名订单串，且均带 appCertSN；不需要为了这一能力盲目升级依赖。

| 官方依据 | 对本方案的直接约束 |
|---|---|
| [微信证书密钥概览](https://pay.weixin.qq.com/doc/v3/merchant/4024350132) | 商户签名私钥、商户证书与微信验签公钥不得混用 |
| [平台证书切换微信支付公钥](https://pay.weixin.qq.com/doc/v3/merchant/4012154180) | 平台证书转公钥存在过渡；新接入固定公钥模式，若存量正在切换则需受控兼容，不擅自承诺自动换公钥 |
| [微信 JSAPI 下单](https://pay.weixin.qq.com/doc/v3/merchant/4012791856)、[小程序调起](https://pay.weixin.qq.com/doc/v3/merchant/4012791898) | 下单 AppID、OpenID 所属应用及实际小程序一致，v3 用 RSA |
| [微信 H5 下单](https://pay.weixin.qq.com/doc/v3/merchant/4012791834) | 返回 h5_url；scene_info 的 payer_client_ip、h5_info.type 必需；域名配置是平台侧要求，不虚构必填 JSON domain 字段 |
| [微信成功通知](https://pay.weixin.qq.com/doc/v3/merchant/4012791836) | 保留原始请求验签/解密；成功 HTTP 200/204 无包体，本方案选择 204；失败非 2xx；幂等与主动查询补偿 |
| [微信 API v3 安全实践](https://pay.weixin.qq.com/doc/v3/merchant/4012073699) | API v3 暂无独立沙箱及测试参数；本地模拟不等于官方沙箱，未经授权不实付 |
| [支付宝官方 SDK 源码](https://repo.maven.apache.org/maven2/com/alipay/sdk/alipay-sdk-java/4.40.1067.ALL/alipay-sdk-java-4.40.1067.ALL-sources.jar) | App 用 sdkExecute；H5 GET pageExecute 返回 URL，默认 POST 返回表单；两者并非渠道支付成功 |
| [支付宝 openid 简介](https://opendocs.alipay.com/mini/0ai2i6?pathHash=13dd5946)、[开发流程](https://opendocs.alipay.com/mini/0ai5vq) | 本次为官方摘要证据；支付宝小程序须核对应用实际身份模式，分别使用 user_id/buyer_id 或 open_id/buyer_open_id，不硬编码一套 |
| [支付宝沙箱范围](https://opendocs.alipay.com/common/097jyi) | 本次为官方摘要证据；不能承诺每种应用/证书组合均支持沙箱，P2 开始前须复核应用权限与官方正文 |
| [uni.requestPayment](https://uniapp.dcloud.net.cn/api/plugins/payment) | 微信小程序参数、支付宝小程序 tradeNO、支付宝 App 字符串、Android/iOS 微信 App 对象；HarmonyOS 规则不混用 |
| [App 微信支付](https://uniapp.dcloud.net.cn/tutorial/app-payment-weixin.html) | 原生 Payment 模块、AppID、iOS Universal Links 和重新打包/自定义基座要求 |

实施前仍需核对商户实际签约产品和应用权限，不能把文档能力等同于当前商户能力。新旧支付宝买家身份同传优先级、应用迁移细则未完成正文核验，禁止猜测后直接实施；这不阻碍先完成公用安全底座及已经核实的 App/H5 协议。
