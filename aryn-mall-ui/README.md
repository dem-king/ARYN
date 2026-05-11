<p align="center">
	<img height="150" width="150" alt="logo" src="https://docs-open.aryn.co/images/logo.png">
</p>
<h1 align="center" style="margin: 20px 0 20px 0px; font-weight: bold;">悦航购</h1>
<p align="center">
	<a href="https://gitee.com/aetheryn/aryn-mall/stargazers"><img src="https://gitee.com/aetheryn/aryn-mall/badge/star.svg?theme=dark"></a>
	<a href="https://gitee.com/aetheryn/aryn-mall"><img src="https://img.shields.io/badge/version-v3.1.0-blue.svg"></a>
	<a href="https://gitee.com/aetheryn/aryn-mall/blob/master/LICENSE"><img src="https://img.shields.io/github/license/mashape/apistatus.svg"></a>
</p>

## 🚀 项目介绍

**悦航购（Aryn Mall）** 是一套基于 **Java 17 + Spring Boot 3 + Spring Cloud & Alibaba + Sa-Token + Dubbo** 构建的微服务电商系统。系统创新性支持 **单体与微服务架构** 自由切换，可灵活适配从初创团队到中小型企业的不同业务规模与部署场景。前端采用 **Vue3 + TypeScript + UniApp** 技术栈，兼具高扩展性、易维护性与模块解耦特性，不仅适用于企业级生产环境部署，更能满足个人学习研究、毕业设计及二次开发等多样化需求。

---

## 🏗 项目架构

<p align="center">
	<img  alt="core_business"  src="https://docs-open.aryn.co/images/hxcloud.png">
</p>

## 🧩 系统模块

```
📦 aryn
├─ 📦 aryn-auth                  # 授权服务 [端口: 5227]
├─ 📦 aryn-boot                  # 单体启动模块 [端口:9999]
├─ 📦 aryn-common                # 系统公共模块
│  ├─ 📁 aryn-common-core        # 公共核心包
│  ├─ 📁 aryn-common-dubbo       # Dubbo 扩展封装
│  ├─ 📁 aryn-common-job         # XXL-Job 封装
│  ├─ 📁 aryn-common-log         # 日志模块封装
│  ├─ 📁 aryn-common-logistics   # 快递物流相关封装
│  ├─ 📁 aryn-common-mybatis     # MyBatis 扩展封装
│  ├─ 📁 aryn-common-redis        # redis缓存工具模块
│  ├─ 📁 aryn-common-seata       # 分布式事务模块
│  ├─ 📁 aryn-common-security    # 安全认证封装（含 Sa-Token）
│  ├─ 📁 aryn-common-sentinel    # sentinel扩展封装
│  ├─ 📁 aryn-common-sms         # 短信模块封装
│  ├─ 📁 aryn-common-storage     # 文件存储封装（本地 / 阿里云 / 腾讯云等）
│  └─ 📁 aryn-common-swagger     # swagger文档模块
├─ 📦 aryn-gateway               # 网关服务 [端口: 9999]
├─ 📦 aryn-miniapp               # 小程序服务
│  ├─ 📁 aryn-miniapp-api        # 小程序 API 公共模块
│  └─ 📁 aryn-miniapp-biz        # 小程序业务处理模块
├─ 📦 aryn-order                 # 订单模块
│  ├─ 📁 aryn-order-api          # 订单 API 公共模块
│  └─ 📁 aryn-order-biz          # 订单业务处理模块
├─ 📦 aryn-pay                   # 支付模块
│  ├─ 📁 aryn-pay-api            # 支付 API 公共模块
│  └─ 📁 aryn-pay-biz            # 支付业务处理模块
├─ 📦 aryn-product               # 商品模块
│  ├─ 📁 aryn-product-api        # 商品 API 公共模块
│  └─ 📁 aryn-product-biz        # 商品业务处理模块
├─ 📦 aryn-promotion             # 营销模块
│  ├─ 📁 aryn-promotion-api      # 营销 API 公共模块
│  └─ 📁 aryn-promotion-biz      # 营销业务处理模块
├─ 📦 aryn-upms                  # 用户权限管理模块（后台）
│  ├─ 📁 aryn-upms-api           # 用户权限 API 公共模块
│  └─ 📁 aryn-upms-biz           # 用户权限业务处理模块
├─ 📦 aryn-user                  # 商城用户模块（C端用户）
│  ├─ 📁 aryn-user-api           # 商城用户 API 公共模块
│  └─ 📁 aryn-user-biz           # 商城用户业务处理模块
├─ 📦 aryn-visual                # 系统可视化与运维模块
│  ├─ 📁 aryn-generator          # 代码生成器模块
│  └─ 📁 aryn-monitor            # Spring Boot Admin 服务监控 [端口: 7001]

```

## 🔧 后端技术栈

| 技术组件               | 说明                                                                                       |
|------------------------|--------------------------------------------------------------------------------------------|
| **Spring Boot 3** | 现代化 Java 应用开发框架，提供自动配置和快速启动 |
| **Spring Cloud Alibaba** | 基于 Spring Cloud 的阿里巴巴微服务组件集合，集成服务注册与发现（Nacos）、配置管理、负载均衡、链路追踪，同时支持 RocketMQ 消息队列、Seata 分布式事务等丰富功能，全面增强微服务生态能力。 |
| **Apache Dubbo 3**     | 高性能 RPC 框架，支持多协议、多注册中心和 Triple 协议，实现服务间高效通信                         |
| **Nacos**              | 服务注册与配置中心，支持服务发现与动态配置管理，兼容 Dubbo 和 Spring Cloud                      |
| **MyBatis & MyBatis-Plus** | 数据访问层框架，MyBatis 实现 ORM 映射，Plus 提供分页、Lambda 表达式、自动 CRUD 等功能增强          |
| **Druid**              | 高性能数据库连接池，支持 SQL 监控、防火墙、慢查询日志等                                       |
| **Redis & Redisson**   | Redis 用作缓存、分布式锁、限流等，Redisson 提供分布式锁及高级客户端支持                         |
| **RocketMQ**           | 分布式消息中间件，支持高吞吐、低延迟、顺序消息和事务消息等，服务间异步解耦和事件驱动             |
| **Seata**              | 分布式事务框架，保障多服务、多数据源操作一致性，集成于 Spring Cloud Alibaba                      |
| **Sentinel**           | 流量防卫利器，支持服务限流、熔断降级、系统负载保护和动态规则配置                               |
| **XXL-JOB**            | 轻量级分布式任务调度平台，支持定时任务管理和执行                                           |
| **Sa-Token**           | 简洁轻量的权限认证框架，支持单点登录、权限校验、会话管理等                                   |
| **Spring Boot Admin**  | 应用监控管理平台，提供实时健康检查、日志查看、线程监控等                                   |
| **Knife4j**            | Swagger UI 增强工具，用于自动生成在线接口文档，支持接口分组和权限注解                          |

---

### 🖥 前端技术栈

| 平台         | 技术栈                             | 说明                           |
|--------------|-----------------------------------|--------------------------------|
| 管理后台     | Vue3 + Vite + Pinia + TS（vben模板） | 响应式后台管理系统               |
| 移动端商城   | UniApp + Vue3 + Pinia + TS + Wot-Ui          | 支持 H5 / 小程序 / App，多端统一开发 |
| 移动端商户（商业版） | UniApp + Pinia + Vue3 + TS + Wot-Ui          | 商业版移动端商户端，支持 H5 / 小程序 / App |
| PC 商城（商业版，规划中） | Vue3 + Pinia + Element Plus + TS           | 商业版 PC 端商城，正在规划中  |
---

## 📦 项目模块划分

系统按微服务拆分，典型模块包括：

| 服务名           | 功能说明                                   |
|------------------|--------------------------------------------|
| **网关服务**     | 统一入口，路由转发，权限拦截等               |
| **认证服务**     | 登录、注册、Token 鉴权等      |
| **用户服务**     | 用户中心、收货地址、账户信息等               |
| **商品服务**     | 商品 SPU/SKU 管理、分类等         |
| **订单服务**     | 下单、支付、发货、退款、订单拆分             |
| **支付服务**     | 支持余额、微信、支付宝等多种支付方式         |
| **营销服务**     | 优惠券、积分抵扣、会员价、限时秒杀、多人拼团等     |


