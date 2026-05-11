# aryn-mall Boot 模式详细部署方案

## 一、架构概览

Boot 模式下，所有业务模块（auth、upms、user、order、pay、product、promotion）**打成一个可执行 JAR**，由 `aryn-boot` 模块统一启动。核心简化：

| 项目 | Cloud 模式 | Boot 模式 |
|------|-----------|-----------|
| 部署单元 | 8个独立服务 + Gateway | **1个单体 JAR** |
| 服务发现 | Nacos | **禁用** |
| 配置中心 | Nacos | **禁用**（本地配置文件） |
| RPC调用 | Dubbo + Nacos | **Dubbo injvm（进程内）** |
| 分布式事务 | Seata | **禁用** |
| 限流降级 | Sentinel | 可选保留（本地限流） |
| 数据库 | 7个独立库 | **1个 aryn_boot 库** |
| 端口 | 9999/5227/5327/7527/6300/6400/6500/6900 | **9999** |
| 上下文路径 | / | **/boot** |

## 二、环境要求

| 组件 | 最低版本 | 说明 |
|------|---------|------|
| **JDK** | 17 | 推荐 Eclipse Temurin 17 |
| **MySQL** | 8.0+ | 推荐 8.0.x |
| **Redis** | 7.0+ | 推荐 7.x |
| **RocketMQ** | 5.4.0+ | NameServer + Broker |
| **Maven** | 3.8+ | 构建工具 |
| **XXL-JOB Admin** | 2.4+ | 定时任务调度中心（可选） |

## 三、技术栈版本

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.8 |
| Spring Cloud | 2025.0.0 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Java | 17 |
| Dubbo | 3.3.6 |
| MyBatis-Plus | 3.5.15 |
| Sa-Token | 1.45.0 |
| Druid | 1.2.24 |
| RocketMQ Spring | 2.3.5 |
| Redisson | 3.52.0 |
| XXL-JOB | 3.3.1 |
| Knife4j | 4.6.0 |
| Spring Boot Admin | 3.5.6 |
| Hutool | 5.8.42 |
| MySQL Connector | 9.2.0 |
| Fastjson2 | 2.0.60 |
| 动态数据源 | 4.3.1 |
| 微信Java SDK | 4.7.9.B |
| 支付宝SDK | 4.39.259.ALL |

## 四、部署步骤

### 步骤1：初始化数据库

```bash
# 登录 MySQL
mysql -u root -p

# 1. 创建库和表（按顺序执行）
source /path/to/aryn-mall-java/db/boot/1schema.sql
source /path/to/aryn-mall-java/db/boot/2aryn_boot.sql
source /path/to/aryn-mall-java/db/boot/3aryn_boot_job.sql
source /path/to/aryn-mall-java/db/boot/4aryn_boot_member.sql
```

> **关键**：必须按数字顺序执行，`1schema.sql` 建库，后续脚本建表和导入数据。

### 步骤2：安装并启动 Redis

```bash
# Linux
sudo apt install redis-server
sudo systemctl start redis

# 或 Docker 方式
docker run -d --name aryn-redis \
  -p 6379:6379 \
  redis:7-alpine \
  redis-server --requirepass redis --appendonly yes
```

### 步骤3：安装并启动 RocketMQ

```bash
# Docker 方式（推荐）
# NameServer
docker run -d --name rmq-namesrv \
  -p 9876:9876 \
  apache/rocketmq:5.4.0 \
  sh mqnamesrv

# Broker
docker run -d --name rmq-broker \
  -p 10911:10911 -p 10909:10909 \
  --link rmq-namesrv:namesrv \
  -e "NAMESRV_ADDR=namesrv:9876" \
  apache/rocketmq:5.4.0 \
  sh mqbroker -c /home/rocketmq/rocketmq-5.4.0/conf/broker.conf
```

### 步骤4：安装并启动 XXL-JOB Admin（可选）

```bash
docker run -d --name xxl-job-admin \
  -p 8081:8081 \
  -e PARAMS="--spring.datasource.url=jdbc:mysql://localhost:3306/aryn_boot_job?useUnicode=true&characterEncoding=UTF-8 --spring.datasource.username=root --spring.datasource.password=你的密码" \
  xuxueli/xxl-job-admin:2.4.0
```

### 步骤5：修改配置文件

编辑 `aryn-boot/src/main/resources/application-dev.yml`：

```yaml
spring:
  data:
    redis:
      host: 你的Redis地址       # 默认 192.168.1.40
      port: 6379
      password: 你的Redis密码    # 如有
      database: 1
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://你的MySQL地址:3306/aryn_boot?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
          username: root
          password: 你的MySQL密码
          driver-class-name: com.mysql.cj.jdbc.Driver
          type: com.alibaba.druid.pool.DruidDataSource

rocketmq:
  name-server: 你的RocketMQ地址:9876
  producer:
    group: producer_group

xxl:
  job:
    adminAddresses: http://你的XXL-JOB地址:8081/xxl-job-admin
```

### 步骤6：构建项目

```bash
cd /path/to/aryn-mall-java

# Boot 模式构建（默认激活，无需指定 -P boot）
mvn clean package -DskipTests

# 产物位置
ls aryn-boot/target/aryn-boot.jar
```

> **注意**：Maven 默认激活 `boot` profile，只编译 `aryn-boot` 模块。如果之前激活过 `cloud`，需显式指定 `-P boot`。

### 步骤7：启动应用

```bash
# 开发环境
java -jar aryn-boot/target/aryn-boot.jar

# 生产环境
java -jar aryn-boot/target/aryn-boot.jar \
  -Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError \
  --spring.profiles.active=prod
```

启动成功后访问：
- **应用入口**：`http://localhost:9999/boot`
- **API 文档**：`http://localhost:9999/boot/doc.html`（Knife4j）

## 五、Docker Compose 一键部署（推荐生产使用）

项目已提供 `docker-compose-boot.yml`，一键启动所有依赖 + 应用：

```bash
cd /path/to/aryn-mall-java

# 1. 先构建 JAR
mvn clean package -DskipTests

# 2. 一键启动
docker-compose -f docker-compose-boot.yml up -d
```

该编排包含：

| 容器 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| aryn-boot-mysql | mysql:8.0 | 3306 | 自动初始化 `db/boot/` 下SQL |
| aryn-boot-redis | redis:7-alpine | 6379 | AOF持久化，密码redis |
| aryn-boot-rocketmq-namesrv | apache/rocketmq:5.4.0 | 9876 | NameServer |
| aryn-boot-rocketmq-broker | apache/rocketmq:5.4.0 | 10911/10909 | Broker |
| aryn-boot | 本地构建 | 9999 | 业务应用 |

如需自定义配置，可通过环境变量覆盖（`docker-compose-boot.yml` 中已配置）。

## 六、生产环境配置清单

编辑 `application-prod.yml`，确保以下配置指向生产环境：

```yaml
spring:
  data:
    redis:
      host: 生产Redis地址
      port: 6379
      password: 生产Redis密码
      database: 1
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://生产MySQL地址:3306/aryn_boot?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
          username: 生产用户
          password: 生产密码
          driver-class-name: com.mysql.cj.jdbc.Driver
          type: com.alibaba.druid.pool.DruidDataSource

rocketmq:
  name-server: 生产RocketMQ地址:9876
  producer:
    group: producer_group

xxl:
  job:
    adminAddresses: http://生产XXL-JOB地址:8081/xxl-job-admin
```

## 七、JVM 参数建议

| 场景 | 堆内存 | 其他参数 |
|------|--------|---------|
| 开发/测试 | `-Xms256m -Xmx512m` | `-XX:+ExitOnOutOfMemoryError` |
| 生产（4G机器） | `-Xms1g -Xmx2g` | `-XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -XX:MaxGCPauseMillis=200` |
| 生产（8G+机器） | `-Xms2g -Xmx4g` | `-XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/logs/` |

## 八、健康检查与监控

Boot 模式下已集成 Spring Boot Actuator：

```bash
# 健康检查
curl http://localhost:9999/boot/actuator/health

# 如需 Spring Boot Admin 监控，启动 aryn-monitor 模块
java -jar aryn-visual/aryn-monitor/target/aryn-monitor.jar
# 访问 http://localhost:7001
```

## 九、常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 启动报 Nacos 连接失败 | 未完全禁用Nacos | 确认使用 `-P boot` 构建，检查 application.yml 中 `nacos.discovery.enabled=false` 和 `nacos.config.enabled=false` |
| 启动报数据库连接失败 | MySQL未启动或密码错误 | 检查 MySQL 连接和 `application-dev.yml` 中数据源配置 |
| 启动报 Redis 连接失败 | Redis未启动 | 启动 Redis 服务 |
| RocketMQ 相关 Bean 创建失败 | RocketMQ未启动 | 启动 RocketMQ 或排除 RocketMQ 自动配置 |
| Dubbo 报注册中心错误 | 应使用 injvm 协议 | 确认 `dubbo.protocol.name=injvm` 和 `dubbo.registry.address=N/A` |
| 访问接口404 | 未加上下文路径 | Boot 模式上下文路径为 `/boot`，完整路径如 `/boot/auth/login` |
| SQL脚本执行报错 | 未按顺序执行 | 按 `1schema → 2aryn_boot → 3aryn_boot_job → 4aryn_boot_member` 顺序执行 |

## 十、部署架构图

```
                    ┌──────────────────────────┐
                    │       客户端/前端          │
                    └────────────┬─────────────┘
                                 │
                          :9999/boot
                                 │
                    ┌────────────▼─────────────┐
                    │      aryn-boot (JAR)      │
                    │  ┌──────────────────────┐│
                    │  │ aryn-auth (认证授权)   ││
                    │  │ aryn-upms  (权限管理)  ││
                    │  │ aryn-user  (用户中心)  ││
                    │  │ aryn-product (商品)    ││
                    │  │ aryn-order  (订单)     ││
                    │  │ aryn-pay    (支付)     ││
                    │  │ aryn-promotion (营销)  ││
                    │  │ aryn-generator (代码生成)││
                    │  └──────────────────────┘│
                    │  Dubbo: injvm (进程内调用) │
                    └──┬───────┬───────┬────────┘
                       │       │       │
                ┌──────▼──┐ ┌──▼───┐ ┌▼────────┐
                │ MySQL   │ │Redis │ │RocketMQ │
                │:3306    │ │:6379 │ │:9876    │
                │aryn_boot│ │      │ │         │
                └─────────┘ └──────┘ └─────────┘
```

## 十一、核心配置文件说明

### application.yml（主配置）

Boot 模式核心配置项：

```yaml
server:
  port: 9999                    # 服务端口
  servlet:
    context-path: /boot         # 上下文路径

spring:
  cloud:
    nacos:
      config:
        enabled: false          # 禁用Nacos配置中心
      discovery:
        enabled: false          # 禁用Nacos服务发现
  main:
    allow-bean-definition-overriding: true  # 允许Bean定义覆盖

sa-token:
  token-name: satoken           # Token名称
  timeout: 2592000              # Token有效期（30天）
  is-concurrent: true           # 允许并发登录
  is-share: false               # 不共享Token
  is-read-cookie: false         # 不从Cookie读取
  token-style: uuid             # Token风格

mybatis-plus:
  mapper-locations: classpath*:/mapper/*Mapper.xml
  global-config:
    db-config:
      id-type: auto             # 主键自增

dubbo:
  scan:
    base-packages: com.aryn.cloud
  protocol:
    name: injvm                 # 进程内调用
    port: -1
  registry:
    address: N/A                # 无注册中心

seata:
  enabled: false                # 禁用分布式事务
```

### application-dev.yml（开发环境）

```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: 192.168.1.40
      port: 6379
      database: 1
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/aryn_boot?characterEncoding=utf8&...
          username: root
          password: wqy123456
          driver-class-name: com.mysql.cj.jdbc.Driver
          type: com.alibaba.druid.pool.DruidDataSource

rocketmq:
  name-server: localhost:9876
  producer:
    group: producer_group

aj:
  captcha:
    cache-type: redis           # 验证码缓存类型

xxl:
  job:
    adminAddresses: http://localhost:8081/xxl-job-admin
```

### application-prod.yml（生产环境）

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    url: jdbc:mysql://127.0.0.1:3306/Aetheryn_boot?...
    username: root
    password: 123456

rocketmq:
  name-server: 127.0.0.1:9876
```

## 十二、SQL 初始化脚本说明

Boot 模式 SQL 脚本位于 `db/boot/` 目录：

| 文件 | 说明 | 必须性 |
|------|------|--------|
| `1schema.sql` | 创建 `aryn_boot` 和 `aryn_boot_job` 数据库 | **必须** |
| `2aryn_boot.sql` | 所有业务表结构和初始数据（UPMS/用户/商品/订单/支付/营销） | **必须** |
| `3aryn_boot_job.sql` | XXL-JOB 定时任务表结构 | 使用XXL-JOB时必须 |
| `4aryn_boot_member.sql` | 会员相关表 | 依赖会员功能时必须 |

## 十三、启动类说明

Boot 模式启动类：`com.aryn.cloud.ArynBootApplication`

```java
@SpringBootApplication
@ArynEnableXxlJob    // 启用 XXL-JOB 定时任务
public class ArynBootApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArynBootApplication.class, args);
    }
}
```

该启动类通过 `@SpringBootApplication` 的包扫描机制自动加载所有子模块的 Spring Bean，Dubbo 使用 `injvm` 协议在进程内完成服务调用，无需网络通信。

## 十四、Boot 模式 vs Cloud 模式选择建议

| 维度 | Boot 模式适用 | Cloud 模式适用 |
|------|-------------|---------------|
| 团队规模 | 小团队（1-5人） | 大团队（5+人） |
| 日活量 | < 10万 | > 10万 |
| 部署资源 | 1-2台服务器 | 5+台服务器/K8s集群 |
| 运维能力 | 初中级 | 高级 |
| 扩展需求 | 整体扩展 | 按模块独立扩展 |
| 开发效率 | 高（调试简单） | 中（需启动多服务） |
