# 悦航购 Aetheryn Mall — 架构文档

## 1. 系统定位

悦航购是一套面向中小型电商的 SaaS 商城系统，支持 **单体模式（Boot）** 与 **微服务模式（Cloud）** 双形态运行。

- **Base Package**: `com.aryn.cloud`
- **版本**: 3.2.1
- **JDK**: 17

## 2. 部署架构

```
用户请求 → Nginx
              ├→ aryn-mall-ui (Vue 3 SPA)           # 管理后台 :port
              ├→ aryn-mall-uniapp (UniApp)           # C端商城
              └→ aryn-gateway :9999                  # API 网关
                   ├→ aryn-auth :5227               # 认证中心
                   ├→ aryn-upms-biz                 # 权限管理 (RPC)
                   ├→ aryn-user-biz                 # 用户服务 (RPC)
                   ├→ aryn-order-biz                # 订单服务 (RPC)
                   ├→ aryn-pay-biz                  # 支付服务 (RPC)
                   ├→ aryn-product-biz              # 商品服务 (RPC)
                   └→ aryn-promotion-biz            # 营销服务 (RPC)

中间件层:
  Nacos (注册/配置) | MySQL 8.x | Redis | RocketMQ | Sentinel | Seata
```

## 3. Java 后端模块依赖矩阵

### 3.1 完整模块清单

```
aryn (根 POM)
├── aryn-common/                     # 公共层
│   ├── aryn-common-core             # 核心工具、基础类 (R, BeanUtils, 异常)
│   ├── aryn-common-log              # 操作日志注解 + AOP
│   ├── aryn-common-redis            # Redis 工具封装
│   ├── aryn-common-mybatis          # MyBatis 扩展 (分页、租户拦截)
│   ├── aryn-common-storage          # 文件存储 (本地/OSS)
│   ├── aryn-common-sms              # 短信 (阿里/腾讯)
│   ├── aryn-common-swagger          # Swagger/Knife4j 文档
│   ├── aryn-common-job              # XXL-JOB 任务封装
│   ├── aryn-common-dubbo            # Dubbo RPC 封装
│   ├── aryn-common-security         # 安全认证 (Sa-Token + OAuth)
│   ├── aryn-common-sentinel         # Sentinel 流控降级
│   ├── aryn-common-seata            # Seata 分布式事务
│   ├── aryn-common-logistics        # 快递物流封装
│   └── aryn-common-datasource       # 动态数据源
├── aryn-gateway                     # Spring Cloud Gateway (L5)
├── aryn-auth                        # 认证中心 (L5)
├── aryn-upms/
│   ├── aryn-upms-api                # 权限接口 + DTO (L3)
│   └── aryn-upms-biz                # 权限业务实现 (L4)
├── aryn-user/
│   ├── aryn-user-api                # 用户接口 + DTO (L3)
│   └── aryn-user-biz                # 用户业务实现 (L4)
├── aryn-order/
│   ├── aryn-order-api               # 订单接口 + DTO (L3)
│   └── aryn-order-biz               # 订单业务实现 (L4)
├── aryn-pay/
│   ├── aryn-pay-api                 # 支付接口 + DTO (L3)
│   └── aryn-pay-biz                 # 支付业务实现 (L4)
├── aryn-product/
│   ├── aryn-product-api             # 商品接口 + DTO (L3)
│   └── aryn-product-biz             # 商品业务实现 (L4)
├── aryn-promotion/
│   ├── aryn-promotion-api           # 营销接口 + DTO (L3)
│   └── aryn-promotion-biz           # 营销业务实现 (L4)
├── aryn-boot                        # 单体启动器 (L5, 聚合所有 biz)
└── aryn-visual/
    ├── aryn-monitor                 # Spring Boot Admin (L5)
    └── aryn-generator               # 代码生成器 (L5)
```

### 3.2 层级依赖矩阵

```
            L0  L1  L2  L3  L4  L5
L0 core     ✓   —   —   —   —   —    ← 纯基础，无内部依赖
L1 log/...  ✓   —   —   —   —   —    ← 只能 import L0
L2 sec/...  ✓   ✓   —   —   —   —    ← 只能 import L0-L1
L3 *-api    ✓   ✓   ✓   —   —   —    ← 只能 import L0-L2
L4 *-biz    ✓   ✓   ✓   ✓   —   —    ← 只能 import L0-L3
L5 gw/...   ✓   ✓   ✓   ✓   ✓   —    ← 可依赖所有低层
```

### 3.3 关键依赖路径 (来自 pom.xml)

| 模块 | 依赖的内部模块 |
|------|--------------|
| `aryn-upms-biz` | upms-api, common-storage, common-log, common-sms, common-security, common-redis, common-sentinel, common-dubbo |
| `aryn-user-biz` | user-api, common-security, common-log, common-sentinel, common-dubbo |
| `aryn-order-biz` | order-api, common-logistics, common-security, common-log, common-job, common-sentinel, common-seata, common-dubbo |
| `aryn-pay-biz` | pay-api, common-security, common-sentinel, common-seata, common-dubbo |
| `aryn-product-biz` | product-api, common-log, common-security, common-sentinel, common-seata, common-dubbo |
| `aryn-promotion-biz` | promotion-api, common-security, common-log, common-job, common-dubbo |

### 3.4 包名 → 模块映射

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

## 4. 管理后台 (aryn-mall-ui) 包依赖

### 4.1 包命名空间

| 命名空间 | 目录 | 定位 |
|----------|------|------|
| `@vben-core/*` | `packages/@core/` | 核心基础 (无界面业务耦合) |
| `@vben/*` | `packages/` | 业务公共包 |
| `@vben/web-ele` | `apps/web-ele/` | Element Plus 管理后台应用 |

### 4.2 层级依赖树

```
L0 (类型):    @vben-core/typings → @vben/types
                 ↑___________________↑ (无内部依赖)

L1 (工具):    @vben-core/shared, @vben/utils, @vben/constants,
              @vben/icons, @vben/locales
                 (仅依赖 L0)

L2 (状态):    @vben-core/preferences, @vben/stores, @vben/preferences
                 (依赖 L0-L1 + pinia)

L3 (效果):    @vben/access, @vben/hooks, @vben/request,
              @vben/plugins, @vben-core/composables
                 (依赖 L0-L2)

L4 (UI):      @vben/layouts, @vben/styles,
              @vben-core/*-ui (form-ui, layout-ui, menu-ui…)
                 (依赖 L0-L3 + Element Plus)

L5 (应用):    @vben/web-ele
                 (依赖所有低层)
```

## 5. 移动端 (aryn-mall-uniapp) 架构

```
应用层:   App.vue → layouts/default.vue, layouts/tabbar.vue
路由层:   pages/ → uni-mini-router (文件路由)
状态层:   store/ (Pinia + persist)
API 层:   api/core/ (Alova instance → middleware → handlers)
          api/{domain}/ (按业务域组织)
```

- **网络层**: Alova (类 Axios) + UniApp adapter
- **文件路由**: `uni-mini-router`，页面在 `src/pages/`
- **跨平台条件编译**: `#ifdef MP-WEIXIN` / `#ifdef H5` 等

## 6. 数据架构

| 数据库 | 用途 |
|--------|------|
| `aryn_boot` | 单体模式业务库 |
| `aryn_upms` | 权限管理库 |
| `aryn_user` | C 端用户库 |
| `aryn_order` | 订单库 |
| `aryn_pay` | 支付库 |
| `aryn_product` | 商品库 |
| `aryn_promotion` | 营销库 |
| `aryn_nacos` | Nacos 配置库 |

所有业务表含：
- `id` (雪花算法主键)
- `tenant_id` (多租户隔离)
- `create_time` / `update_time` (自动填充)
- `del_flag` (逻辑删除: 0=正常 1=删除)

## 7. 通信模式

| 场景 | 方式 |
|------|------|
| 单体模式 | Spring DI 直接注入 |
| 微服务模式 | Apache Dubbo Triple 协议 RPC |
| 异步解耦 | Apache RocketMQ |
| 定时任务 | XXL-JOB |
| 服务保护 | Alibaba Sentinel |
