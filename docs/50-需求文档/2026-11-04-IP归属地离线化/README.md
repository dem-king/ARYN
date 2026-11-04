# IP 归属地离线化（ip2region）与数据热更新

> 2026-11-04。把登录日志 / 在线会话 / 操作日志的「登录地点」从第三方 HTTP 接口
> （`whois.pconline.com.cn`，无商用授权、无 SLA、并发即 503）切换到本地 ip2region 离线库，
> 并补齐 xdb 数据的校验式热更新链路。

## 1. 需求来源

- 运营与客服在排查「账号在其他设备登录」时需要看到登录地点；现有实现依赖太平洋网络 whois
  接口，存在三个硬伤：
  1. **授权不明**：接口页面只自称「免费 IP 查询 Web 接口」，没有任何服务条款/商用授权/SLA；站点
     版权声明为「未经授权禁止转载、摘编、复制或建立镜像」——作为 SaaS 售卖功能有合规风险；
  2. **可用性差**：境内可访问但限流明显（实测 4 并发 8 次请求 5 次 503），失败降级为「未知」；
  3. **同步链路**：登录/操作日志是同步调用，最坏多等 1.5s（现有超时保护）。
- 结论：保留 HTTP 实现作为可选兜底，主链路改为本地 ip2region 离线库（微秒级、不联网、可热更新）。

## 2. 目标 / 非目标

**目标**

- 业务调用方零改动：`IpUtils.getWhoisAddress(ip)` 仍是唯一入口，契约（「省 市」/「内网IP」/「未知」）不变。
- 离线库优先、HTTP 兜底可配置；离线库/数据文件不可用时**不能**让登录链路失败。
- xdb 数据「不定期」更新：提供校验式热更新（下载 → 结构+样本校验 → 原子替换 → 重建查询服务 → 失败回滚），
  用 XXL-JOB 每日低峰触发。
- boot 单体 + cloud 微服务双模式配置齐全。

**非目标**

- 不改登录日志/在线会话/操作日志的表结构与接口；
- 不做 IPv6 离线库（v6 xdb 37MB，登录地点 IPv6 场景少，继续走 HTTP 兜底）；
- 不做经纬度/区县/ISP 展示（数据里有，但当前产品口径只要城市文本）；
- 不接高德/腾讯/百度等在线 IP 定位 API（同样需要商务授权，且引入外部依赖）。

## 3. 方案

### 3.1 分层与被依赖关系

```
aryn-common-core（L0）
  ├─ IpLocationResolver        ← SPI 接口（新增）
  ├─ HttpIpLocationResolver    ← 第三方 HTTP 实现（从 IpUtils 抽出，行为不变）
  └─ IpUtils                   ← 统一入口 + 静态注册（registerAddressResolver/resetAddressResolver）
aryn-common-ip（L1，新增模块）
  ├─ provider/Ip2RegionLocationProvider   ← ip2region 离线实现（含 HTTP 兜底）
  ├─ provider/IpRegionTextFormatter       ← region 文本 → 展示地点
  ├─ updater/IpRegionDataUpdater          ← 校验式热更新（下载/校验/原子替换/回滚/文件锁）
  ├─ job/IpRegionDataRefreshJobHandler    ← XXL-JOB handler（可选依赖 xxl-job-core）
  └─ config/IpLocationAutoConfiguration、IpRegionRefreshJobAutoConfiguration
aryn-common-log（L1）
  └─ 新增依赖 aryn-common-ip：日志模块是所有产生登录/操作日志的服务都会引入的基础设施，
     随它分发离线库；未引入本模块的服务继续使用 HTTP 实现（IpUtils 默认解析器）。
```

- 沿用项目已有的 L1→L1 依赖实践（`common-log` 原本就依赖 `common-mybatis`/`upms-api`），
  不新增 biz 间直接依赖。
- `IpUtils` 通过静态注册切换实现，登录日志、在线记录、操作日志、登录设备页 4 处调用点**零改动**。

### 3.2 数据加载与查询

- 数据来源优先级：
  1. 外部可写目录 `aryn.ip.ip2region.data-dir`（默认 `~/.aryn/ip2region`）下已存在的
     `ip2region_v4.xdb`（热更新落点，多副本可挂共享卷）；
  2. classpath 内置 `ip2region/ip2region_v4.xdb`（随包发布，11.1MB）。
- 缓存策略：外部文件默认 `vindex`（512KB 索引 + 随机读）；classpath 因 binding 约束只能用
  `buffer`（11MB 全量进内存，登录/日志低频调用可接受）。可配置 `cache-policy`/`searchers`。
- 启动校验：外部文件先 `Searcher.verifyFromFile` + 样本 IP 查询，失败自动回退 classpath；
  两者都失败也不阻塞启动，归属地走 HTTP 兜底并打 WARN 日志。
- 展示口径：`中国|浙江省|杭州市|阿里|CN` → `浙江省 杭州市`；直辖市省==市去重（`北京市`）；
  海外只展示国家（`United States`，与旧 HTTP 的 `美国` 同粒度）；`Reserved`/空值 → `未知`。
- 内网 IP 由 `IpUtils`/provider 统一返回 `内网IP`（本地开发不误判）。

### 3.3 数据热更新（核心）

`IpRegionDataUpdater#refresh(force)`：

1. 计算目标文件 `{data-dir}/ip2region_v4.xdb`，对 `{data-dir}/.ip2region_v4.lock` 抢文件锁
   （同 JVM 撞车捕 `OverlappingFileLockException`）——抢不到返回 `SKIPPED`，避免分片广播时重复下载 11MB；
2. 读 `.ip2region_v4.meta` 里的 ETag，HTTP 条件请求 `If-None-Match`；304 → `UP_TO_DATE`；
3. 下载到 `.download` 临时文件，校验：体积下限（默认 5MB）、`Searcher.verifyFromFile`、
   样本 IP（默认 `223.5.5.5`/`114.114.114.114`）必须能解析出 region；
4. 旧文件改名 `.bak` 备份 → 原子 move 临时文件到目标 → `provider.reload(target)` 重建查询服务
   （binding 无 reload API，只能新建实例后切换引用）；
5. 任一步失败：删除新文件、恢复 `.bak`，provider 保持旧数据；`.bak` 成功时也保留，便于人工回滚；
6. 成功后写 meta（url/etag/lastModified/sha256/数据构建时间），并返回 `UPDATED` 及来源描述。

**XXL-JOB 注册**（每日 03:30，`CRON 0 30 3 * * ?`，路由 `SHARDING_BROADCAST`，失败重试 1 次）：

- boot：`db/boot/200ip_region_refresh_job.sql` → `aryn_boot_job.xxl_job_info`，job_group=1；
- cloud：`db/cloud/203ip_region_refresh_job.sql` → `aryn_job.xxl_job_info`，沿用 job_group=1（aryn-order 执行器）；
- 任务参数留空按 ETag 判断，传 `force` 强制重新下载。

### 3.4 配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `aryn.ip.enabled` | `true` | 总开关；false 时不注册离线解析器（沿用 HTTP，等同改造前） |
| `aryn.ip.fallback-http` | `true` | 离线库查不到时回退第三方 HTTP（IPv6/新网段兜底） |
| `aryn.ip.ip2region.enabled` | `true` | 仅离线库开关 |
| `aryn.ip.ip2region.data-dir` | `""`（= `~/.aryn/ip2region`） | 外部可写数据目录（热更新落点） |
| `aryn.ip.ip2region.cache-policy` | `vindex` | `vindex` / `buffer` / `nocache` |
| `aryn.ip.ip2region.searchers` | `20` | 查询器池大小 |
| `aryn.ip.ip2region.classpath-location` | `classpath:ip2region/ip2region_v4.xdb` | 内置数据位置 |
| `aryn.ip.refresh.enabled` | `true` | 热更新 + Job handler 开关 |
| `aryn.ip.refresh.source-url` | ip2region master raw | 下载地址（境内可换 Gitee 镜像 / 商用数据） |
| `aryn.ip.refresh.timeout-ms` | `60000` | 下载超时 |
| `aryn.ip.refresh.min-bytes` | `5242880` | 文件体积下限 |
| `aryn.ip.refresh.sample-ips` | `223.5.5.5,114.114.114.114` | 更新后抽样校验 IP |

对应环境变量：`ARYN_IP_ENABLED`、`ARYN_IP_FALLBACK_HTTP`、`ARYN_IP2REGION_DATA_DIR`、
`ARYN_IP_REFRESH_ENABLED`、`ARYN_IP2REGION_XDB_URL`、`ARYN_IP2REGION_TIMEOUT_MS`。

boot 配置在 `aryn-boot/src/main/resources/application.yml`；cloud 模式在 9 个服务（auth/upms/user/order/pay/product/promotion/message/vessel）
的 `application.yml` 各一份（Nacos 同名配置可覆盖）。

### 3.5 数据与许可（必须知晓）

- **代码**：`org.lionsoul:ip2region:3.3.7`，Apache-2.0 OR MIT，可商用。
- **数据**：本次内置 `ip2region_v4.xdb` 取自官方 master（下载于 2026-11-04，
  sha256 `46249ad1b33bcaa4cc37475b93746cec2b00acffc3316cc4f56167ac821ea2ad`，header 数据构建时间
  2026-10-28）。官方说明自带数据由 geofeed + 社区报告合并、**不定期更新**，仓库未给出数据商用许可承诺；
  README 明确「精度和更新频率要求高的场景建议购买商用离线数据」。
- **商用建议**：上线前按需购买 [ip2region.net 商用离线数据](https://ip2region.net/products/offline)
  （V4 基础版 ¥499/年，承诺每月 ≥1 更新），把拿到的 XDB 替换内置文件，或用 TXT 经官方 maker 生成
  xdb；`aryn.ip.refresh.source-url` 指向自有分发地址。第三方 whois 接口仅作兜底，不落库统计依赖。

## 4. 改动清单

| 端 | 文件 |
|---|---|
| 核心 | `aryn-common-core/.../util/IpLocationResolver.java`（新）、`HttpIpLocationResolver.java`（新，抽自 IpUtils）、`IpUtils.java`（改：SPI 注册 + 委派） |
| 新模块 | `aryn-common/aryn-common-ip/`：pom、`ip2region/ip2region_v4.xdb`、provider/formatter/updater/job/config 共 11 个类 + 自动配置 imports + 5 个测试类 |
| 依赖 | 根 `pom.xml`（ip2region 版本 + depMgmt）、`aryn-common/pom.xml`（模块）、`aryn-common-log/pom.xml`（依赖）、`aryn-common-core/pom.xml`（test 依赖） |
| 配置 | boot `application.yml` + 9 个 cloud 服务 `application.yml` |
| 数据库 | 无表结构变更；Job 注册脚本 boot `200` / cloud `203`；boot 全量 SQL 已登记 `200ip_region_refresh_job.sql` 并重建校验 |

## 5. 验证

```bash
# 新增模块 + 核心 SPI 单测（26 例）
cd aryn-mall-java && mvn -B -o -pl aryn-common/aryn-common-ip -am test

# boot 全量编译/测试
cd aryn-mall-java && mvn clean test -pl aryn-boot -am

# boot 全量 SQL 重建与静态校验
cd aryn-mall-java && node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs
```

结果：

- `aryn-common-core` 4 例 + `aryn-common-ip` 26 例全绿（格式化、真实 xdb 查询、热重载、更新成功/304/
  校验失败保留旧数据/下载异常/文件锁跳过/自动配置注册与关闭/Job 参数与结果回写）。
- boot 全量测试：`mvn -B test -pl aryn-boot -am` **BUILD SUCCESS**（44 个 reactor 模块全绿，
  boot 108 例、新增 common-core 4 例 + common-ip 26 例，约 57s）；`mvn -B clean test -pl aryn-boot -am`
  再次全绿（约 1.5 分钟，验证无脏增量依赖）。
- 打包验证：`aryn-common-ip.jar` 4.3MB（含 xdb，压缩后），jar 内 `ip2region/ip2region_v4.xdb`
  与源文件 sha256 一致；`aryn-boot` 可执行包包含 `BOOT-INF/lib/aryn-common-ip-3.2.1.jar`。
- 全量 SQL：29343 行、3284081 字节，`verify-full-sql.mjs` 通过。

## 6. 部署与运维注意

- **包体积/内存**：每个引入 common-log 的服务 jar 增加约 4.3MB（xdb 压缩后；运行期需读取 11MB 数据）；
  classpath 模式常驻内存约 11MB，外部文件 + `vindex` 模式内存约 512KB。
- **多副本热更新**：xdb 是本地文件。boot 单 JVM 部署天然一致；cloud 多服务/多副本需要
  「同一 `data-dir` 共享卷」或按执行器分组分别注册刷新任务（分片广播），文件锁保证只有一个实例真正下载。
- **首次刷新**：安装后第一次跑 Job 会在 `data-dir` 落一份 xdb 并切换到外部文件，之后走 ETag 增量判断。
- **回滚**：`aryn.ip.enabled=false` 即回到旧 HTTP 链路；数据回滚用同目录 `.bak` 覆盖回
  `ip2region_v4.xdb`（下次启动/热更新生效）；代码回滚按版本发布回退 jar 即可（无 DDL）。
- **时延**：离线查询微秒级；仅当离线库未命中且开启 fallback 时才会有 1.5s 上限的 HTTP 调用。

## 7. 剩余风险

- 自带数据无明确商用许可承诺（见 3.5），对外售卖前需替换为已购商用数据；
- IPv6 与极新网段仍依赖 HTTP 兜底（可用性/准确性与第三方一致）；
- 海外 IP 展示英文国家名（与旧实现中文国家名存在文案差异，属预期口径）；
- cloud 各服务「各自一份磁盘数据」时，非执行器服务只能随发版更新（见 6）；
- Job 依赖调度中心配置：若调度中心未执行 `200`/`203` 脚本，任务不会自动运行（数据仍可用，只是不更新）。

## 8. 参考

- [ip2region README（xdb 更新章节）](https://github.com/lionsoul2014/ip2region/blob/master/README_zh.md)
- [ip2region Java 查询客户端（Ip2Region/verifyFromFile）](https://github.com/lionsoul2014/ip2region/blob/master/binding/java/README_zh.md)
- [ip2region data 目录说明（源文件与 xdb）](https://github.com/lionsoul2014/ip2region/blob/master/data/README_zh.md)
- [ip2region 商用离线数据](https://ip2region.net/products/offline)
- 关联风险条目：`docs/40-接口与风险/README.md#ip-归属地数据源与许可2026-11-04`
