# Java 后端

> 详细架构信息参见 [ARCHITECTURE.md](../ARCHITECTURE.md)，功能索引参见 [功能到代码索引](../20-业务与数据/功能到代码索引.md)。

## 1. 模块总览

```
aryn (根 POM)
├── aryn-common/                     # 公共层 (13 个模块)
│   ├── aryn-common-core             # L0: 核心工具、基础类 (R, BeanUtils, 异常)
│   ├── aryn-common-log              # L1: 操作日志注解 + AOP
│   ├── aryn-common-redis            # L1: Redis 工具封装
│   ├── aryn-common-mybatis          # L1: MyBatis 扩展 (分页、租户拦截)
│   ├── aryn-common-storage          # L1: 文件存储 (本地/OSS)
│   ├── aryn-common-sms              # L1: 短信 (阿里/腾讯)
│   ├── aryn-common-swagger          # L1: Swagger/Knife4j 文档
│   ├── aryn-common-job              # L1: XXL-JOB 任务封装
│   ├── aryn-common-dubbo            # L1: Dubbo RPC 封装
│   ├── aryn-common-datasource       # L1: 动态数据源
│   ├── aryn-common-security         # L2: 安全认证 (Sa-Token + OAuth)
│   ├── aryn-common-sentinel         # L2: Sentinel 流控降级
│   ├── aryn-common-seata            # L2: Seata 分布式事务
│   └── aryn-common-logistics        # L2: 快递物流封装
├── aryn-gateway                     # L5: Spring Cloud Gateway
├── aryn-auth                        # L5: 认证中心
├── aryn-upms/                       # 权限管理
│   ├── aryn-upms-api                # L3: 权限接口 + DTO
│   └── aryn-upms-biz                # L4: 权限业务实现 (19 Controller)
├── aryn-user/                       # C端用户
│   ├── aryn-user-api                # L3: 用户接口 + DTO
│   └── aryn-user-biz                # L4: 用户业务实现 (20 Controller)
├── aryn-order/                      # 订单
│   ├── aryn-order-api               # L3: 订单接口 + DTO
│   └── aryn-order-biz               # L4: 订单业务实现 (8 Controller)
├── aryn-pay/                        # 支付
│   ├── aryn-pay-api                 # L3: 支付接口 + DTO
│   └── aryn-pay-biz                 # L4: 支付业务实现 (5 Controller)
├── aryn-product/                    # 商品
│   ├── aryn-product-api             # L3: 商品接口 + DTO
│   └── aryn-product-biz             # L4: 商品业务实现 (11 Controller)
├── aryn-promotion/                  # 营销
│   ├── aryn-promotion-api           # L3: 营销接口 + DTO
│   └── aryn-promotion-biz           # L4: 营销业务实现 (13 Controller)
├── aryn-boot                        # L5: 单体启动器 (聚合所有 biz)
└── aryn-visual/
    ├── aryn-monitor                 # L5: Spring Boot Admin
    └── aryn-generator               # L5: 代码生成器
```

## 2. 层级依赖矩阵

```
            L0  L1  L2  L3  L4  L5
L0 core     ✓   —   —   —   —   —    ← 纯基础，无内部依赖
L1 log/...  ✓   —   —   —   —   —    ← 只能 import L0
L2 sec/...  ✓   ✓   —   —   —   —    ← 只能 import L0-L1
L3 *-api    ✓   ✓   ✓   —   —   —    ← 只能 import L0-L2
L4 *-biz    ✓   ✓   ✓   ✓   —   —    ← 只能 import L0-L3
L5 gw/...   ✓   ✓   ✓   ✓   ✓   —    ← 可依赖所有低层
```

> ⚠️ **核心约束**：高层可依赖低层，反向禁止。不同 biz 模块之间**不可直接 import**（通过 Dubbo RPC 通信）。

## 3. 包名 → 模块映射

| Java Package | Maven Artifact | Layer |
|-------------|---------------|:--:|
| `com.aryn.cloud.common.core` | aryn-common-core | L0 |
| `com.aryn.cloud.common.log` | aryn-common-log | L1 |
| `com.aryn.cloud.common.redis` | aryn-common-redis | L1 |
| `com.aryn.cloud.common.mybatis` | aryn-common-mybatis | L1 |
| `com.aryn.cloud.common.storage` | aryn-common-storage | L1 |
| `com.aryn.cloud.common.sms` | aryn-common-sms | L1 |
| `com.aryn.cloud.common.swagger` | aryn-common-swagger | L1 |
| `com.aryn.cloud.common.job` | aryn-common-job | L1 |
| `com.aryn.cloud.common.dubbo` | aryn-common-dubbo | L1 |
| `com.aryn.cloud.common.ds` | aryn-common-datasource | L1 |
| `com.aryn.cloud.common.security` | aryn-common-security | L2 |
| `com.aryn.cloud.common.sentinel` | aryn-common-sentinel | L2 |
| `com.aryn.cloud.common.seata` | aryn-common-seata | L2 |
| `com.aryn.cloud.common.logistics` | aryn-common-logistics | L2 |
| `com.aryn.cloud.upms.api` | aryn-upms-api | L3 |
| `com.aryn.cloud.user.api` | aryn-user-api | L3 |
| `com.aryn.cloud.order.api` | aryn-order-api | L3 |
| `com.aryn.cloud.pay.api` | aryn-pay-api | L3 |
| `com.aryn.cloud.product.api` | aryn-product-api | L3 |
| `com.aryn.cloud.promotion.api` | aryn-promotion-api | L3 |
| `com.aryn.cloud.upms` | aryn-upms-biz | L4 |
| `com.aryn.cloud.user` | aryn-user-biz | L4 |
| `com.aryn.cloud.order` | aryn-order-biz | L4 |
| `com.aryn.cloud.pay` | aryn-pay-biz | L4 |
| `com.aryn.cloud.product` | aryn-product-biz | L4 |
| `com.aryn.cloud.promotion` | aryn-promotion-biz | L4 |
| `com.aryn.cloud.gateway` | aryn-gateway | L5 |
| `com.aryn.cloud.auth` | aryn-auth | L5 |
| `com.aryn.cloud.boot` | aryn-boot | L5 |
| `com.aryn.cloud.monitor` | aryn-monitor | L5 |
| `com.aryn.cloud.generator` | aryn-generator | L5 |

## 4. 业务模块详情

### 4.1 aryn-upms-biz（权限管理，19 Controller）

| Controller | 路由前缀 | 功能 |
|-----------|----------|------|
| SysUserController | `/admin/upms/user` | 用户 CRUD |
| SysRoleController | `/admin/upms/role` | 角色 CRUD |
| SysMenuController | `/admin/upms/menu` | 菜单/权限 CRUD |
| SysDeptController | `/admin/upms/dept` | 部门 CRUD |
| SysDictController | `/admin/upms/dict` | 字典 CRUD |
| SysLogController | `/admin/upms/log` | 操作日志查询 |
| TenantController | `/admin/upms/tenant` | 租户 CRUD |
| SysFileController | `/admin/upms/file` | 文件上传/管理 |
| LogisticsCompanyController | `/admin/upms/logistics` | 物流公司 CRUD |

**关键依赖**: upms-api, common-storage, common-log, common-sms, common-security, common-redis, common-sentinel, common-dubbo

### 4.2 aryn-user-biz（C端用户，20 Controller）

| Controller | 路由前缀 | 功能 |
|-----------|----------|------|
| MallUserController | `/app/user` | 用户注册/登录/信息 |
| SocialUserController | `/app/user/social` | 社交登录 |
| UserAddressController | `/app/user/address` | 收货地址 CRUD |
| MemberLevelController | `/admin/user/member-level` | 会员等级管理 |
| PointsRecordController | `/app/user/points` | 积分查询/使用 |
| SignInController | `/app/user/sign-in` | 每日签到 |
| BalanceController | `/app/user/balance` | 余额查询 |
| RechargeController | `/app/user/recharge` | 充值 |
| UserStatisticsController | `/admin/user/statistics` | 用户统计 |

**关键依赖**: user-api, common-security, common-log, common-sentinel, common-dubbo

### 4.3 aryn-order-biz（订单，8 Controller）

| Controller | 路由前缀 | 功能 |
|-----------|----------|------|
| OrderInfoController | `/app/order` / `/admin/order` | 订单 CRUD + 状态流转 |
| ShoppingCartController | `/app/cart` | 购物车 CRUD |
| OrderRefundController | `/app/refund` / `/admin/refund` | 退款申请/审核 |
| OrderLogisticsController | `/app/logistics` / `/admin/logistics` | 物流信息 |
| OrderStatisticsController | `/admin/order/statistics` | 订单统计 |

**关键依赖**: order-api, common-logistics, common-security, common-log, common-job, common-sentinel, common-seata, common-dubbo

### 4.4 aryn-pay-biz（支付，5 Controller）

| Controller | 路由前缀 | 功能 |
|-----------|----------|------|
| PayTradeOrderController | `/app/pay` / `/admin/pay` | 支付下单/查询 |
| PayRefundController | `/admin/pay/refund` | 退款管理 |
| PayConfigController | `/admin/pay/config` | 支付配置 |
| PayCallbackController | `/pay/callback` | 支付回调(无需登录) |

**关键依赖**: pay-api, common-security, common-sentinel, common-seata, common-dubbo

### 4.5 aryn-product-biz（商品，11 Controller）

| Controller | 路由前缀 | 功能 |
|-----------|----------|------|
| GoodsSpuController | `/admin/product/spu` / `/app/product/spu` | SPU 管理 |
| GoodsSkuController | `/admin/product/sku` / `/app/product/sku` | SKU 管理 |
| GoodsCategoryController | `/admin/product/category` | 分类管理 |
| GoodsSpecController | `/admin/product/spec` | 规格管理 |
| GoodsAppraiseController | `/app/product/appraise` | 评价管理 |
| GoodsFavoriteController | `/app/product/favorite` | 收藏 |
| GoodsFootprintController | `/app/product/footprint` | 浏览足迹 |
| GoodsStatisticsController | `/admin/product/statistics` | 商品统计 |

**关键依赖**: product-api, common-log, common-security, common-sentinel, common-seata, common-dubbo

### 4.6 aryn-promotion-biz（营销，13 Controller）

| Controller | 路由前缀 | 功能 |
|-----------|----------|------|
| CouponInfoController | `/admin/promotion/coupon` | 优惠券定义 |
| CouponUserController | `/app/promotion/coupon` / `/admin/promotion/coupon-user` | 用户优惠券 |
| GroupBuyController | `/admin/promotion/group-buy` | 拼团活动 |
| GroupBuyRecordController | `/app/promotion/group-buy` | 拼团参与 |
| DistributionController | `/admin/promotion/distribution` | 分销管理 |
| PageDesignController | `/admin/promotion/page-design` | 页面装修 |

**关键依赖**: promotion-api, common-security, common-log, common-job, common-dubbo

## 5. Dubbo RPC 接口

| 接口 | 所属 API 模块 | 提供者 | 消费者 |
|------|-------------|--------|--------|
| RemoteSysUserService | upms-api | upms-biz | user-biz, order-biz, pay-biz, product-biz, promotion-biz |
| RemoteTenantService | upms-api | upms-biz | auth, gateway |
| RemoteSysLogService | upms-api | upms-biz | 各 biz 模块 |
| RemoteMallUserService | user-api | user-biz | order-biz, promotion-biz |
| RemoteSocialUserService | user-api | user-biz | auth |
| RemoteUserAddressService | user-api | user-biz | order-biz |
| RemoteSecOrderService | user-api | user-biz | order-biz |
| RemoteUserStatisticsService | user-api | user-biz | — |
| RemotePayService | pay-api | pay-biz | order-biz |
| RemoteRefundService | pay-api | pay-biz | order-biz |
| RemoteGoodsSkuService | product-api | product-biz | order-biz, promotion-biz |
| RemoteGoodsSpuService | product-api | product-biz | promotion-biz |
| RemoteGoodsAppraiseService | product-api | product-biz | — |
| RemoteCouponUserService | promotion-api | promotion-biz | order-biz |

## 6. 通信模式

| 场景 | 方式 | 说明 |
|------|------|------|
| 单体模式 | Spring DI 直接注入 | `aryn-boot` 聚合所有 biz |
| 微服务模式 | Apache Dubbo Triple 协议 RPC | 各 biz 独立部署 |
| 异步解耦 | Apache RocketMQ | 跨服务异步通知 |
| 定时任务 | XXL-JOB | 订单超时、拼团过期等 |
| 服务保护 | Alibaba Sentinel | 流控降级 |
| 分布式事务 | Seata | 跨服务事务一致性 |