# 空态插画配置化（wd-status-tip 裂图修复）

> 状态：已实施（2026-11-13，含同日补充的购物车/订单/秒杀三张会场空态与死资源清理）；boot 单机库已执行 230/231/232，jar/镜像已重建并重启，免登接口与静态图实测通过；cloud 库、Nacos、管理端菜单与真机空态出图仍待验收，见「六、待验收」。

## 一、原始请求

微信开发者工具报错：

```text
[渲染层网络层错误] Failed to load image https://registry.npmmirror.com/wot-design-uni-assets/1.0.4/files/message.png
```

用户要求空态图「跟商品图片类似、通过后端提供/查询」，并在两个方案中明确选择配置化版本（管理后台可换图 + 免登接口下发）。

## 二、根因

`wd-status-tip` 的 7 个预设位默认拼 `https://registry.npmmirror.com/wot-design-uni-assets/*/files/{preset}.png`，
而 npmmirror 的 unpkg 文件服务不服务 `wot-design-uni-assets` 包，实测返回 HTTP 451 + JSON：

```json
{"error":"[FORBIDDEN] \"wot-design-uni-assets\" is not allow to unpkg files, ..."}
```

请求本身完成（`net::OK`），但响应体不是 PNG，渲染层解码失败。替代 CDN（jsDelivr/unpkg）可用，但依赖第三方可用性；
项目需要的是可运营、可换图的后端方案。

## 三、设计口径

| 项 | 口径 |
|---|---|
| 内置兜底 | 7 张 wd-status-tip 预设图随 `aryn-upms-biz` 静态资源发布：`static/status-tip/{search,network,content,collect,comment,halo,message}.png`，cloud `/upms/status-tip/`、boot `/boot/status-tip/`，与商品图同源，小程序域名不用新增；购物车/订单/秒杀 3 张会场插画仍随小程序包内置（`static/promotion/empty/*.png`） |
| 租户覆盖 | 新表 `sys_status_tip_config`，一行一租户；10 个 URL 列（7 个 wd-status-tip 预设位 + 3 张会场插画）可单独留空，留空=回落内置图 |
| 存储 | 复用商品图上传链路（`/upms/file/upload`，`type=1`），存完整 URL；兼容对象存储公网 URL 与本地存储相对路径 |
| 下发 | 免登 `GET /upms/app/status-tip-config`，租户由 `tenant-id` 头决定；空串表示未配置 |
| 管理端 | `GET/PUT /upms/status-tip-config`，权限 `upms:statustip:get/edit`；菜单挂「系统设置」，组件 `upms/status-tip-config/index` |
| 安全 | URL 仅允许 `http/https` 或站内相对路径，≤500 字符；前端二次过滤，防止 `javascript:/data:` 注入 |
| C 端解析 | 配置值优先；相对路径补后端域名（boot 下 `/upms/`→`/boot/`），保证小程序 `<image>` 拿到完整 URL；7 个预设位未配置回落内置前缀，3 张会场插画未配置回落随包 PNG |
| 缓存 | C 端 storage 缓存（key 带租户后缀，缓存先行防闪）+ 启动预加载 + 回前台 5 分钟节流刷新；服务端不做 Redis 缓存（空态低频，省掉「改完等缓存」的解释成本） |

## 四、影响端与文件

| 端 | 主要改动 |
|---|---|
| 后端（aryn-upms） | 新增 `SysStatusTipConfig`、`StatusTipConfigDTO`、`StatusTipConfigVO`、`SysStatusTipConfigMapper`、`ISysStatusTipConfigService` / `Impl`、`SysStatusTipConfigController`、`AppStatusTipConfigController` |
| 管理端（web-ele） | 新增 `api/upms/status-tip-config.ts`、`views/upms/status-tip-config/index.vue`（10 个图片位上传/粘贴/清除/预览） |
| C 端（uniapp） | `utils/status-tip.ts` + 单测；新增 `api/upms/status-tip.ts`、`store/statusTipStore.ts`；`App.vue` 启动预加载 + `onShow` 节流刷新；6 处 `wd-status-tip` 改为 `statusTipStore.imageFor(preset)`；购物车/订单列表改用 `empty-cart`/`empty-order` 配置位，秒杀 `hall-state` 改用 `empty-seckill` 配置位 |
| 数据库 | boot `230status_tip_config_incremental.sql`、`231status_tip_config_menu.sql`、`232status_tip_config_empty_illustrations.sql`；cloud `244status_tip_config_incremental.sql`、`245status_tip_config_menu.sql`、`246status_tip_config_nacos.sql`、`247status_tip_config_empty_illustrations.sql`；`build-full-sql.mjs` 登记并重建 `aryn_boot_full.sql` |
| 双模式 | boot `application.yml`：`secure.ignore.urls` 加 `/app/status-tip-config`、`/status-tip/**`，`hx.tenant.tables` 加 `sys_status_tip_config`；cloud：`3aryn_nacos.sql` 基线（租户表 + 网关双前缀白名单 + 精确 boot 兼容路由，md5 已同步）+ 246 增量；`PublicEndpointWhitelistContractTest` 增补 |

## 五、验证记录（2026-11-13，本机）

| 项 | 命令 | 结果 |
|---|---|---|
| 后端编译 | `mvn -o compile -pl aryn-upms/aryn-upms-biz -am -DskipTests` | BUILD SUCCESS |
| 白名单契约 | `mvn -o -pl aryn-boot org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test -Dtest=PublicEndpointWhitelistContractTest` | 7/7 通过（含新增 status-tip 用例） |
| 全量 SQL | `node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs` | 静态校验通过（33243 行） |
| 管理端类型 | `pnpm --filter @vben/web-ele typecheck` | 通过 |
| 管理端 lint | `pnpm exec eslint`（新增/修改文件） | 0 error |
| 移动端类型 | `pnpm type-check` | 通过 |
| 移动端单测 | `pnpm test:unit src/utils/status-tip.test.ts`、`pnpm test:unit src/store/statusTipStore.test.ts` | 18/18、7/7 通过 |
| 静态引用 | 全量 `/static/...` 引用存在性校验 | 生产代码无缺失（唯一 `MISSING: /static/a.png` 是测试夹具） |
| 死资源清理 | 删除无引用文件 | `static/tab-nav/` 8 张 PNG、`static/mobile.svg`、`static/pwd.svg` 共 10 个；删除后全量引用校验仍通过 |
| 小程序构建 | `pnpm build:mp-weixin` | Build complete，产物在 `dist/build/mp-weixin` |

> 小程序包体门禁另有一项独立问题：`pnpm check:mp-package` 报主包 2124.7 KB，超过微信 2048 KB 上限。本次改动只删除了 10 个静态资源、未新增图片，主包超限需在发布前通过分包/瘦身另行处理，不计入空态配置功能本身。

> 注：仓库默认 surefire 2.12.4 不识别 JUnit 5，直接 `mvn test` 会显示 `Tests run: 0`；本次用 3.2.5 显式执行契约测试，报告见 `aryn-boot/target/surefire-reports/`。

## 五之二、运行态执行记录（2026-11-13，boot）

| 项 | 实际执行 | 结果 |
|---|---|---|
| 目标环境 | Docker `aryn-boot` + `aryn-boot-mysql`，库 `aryn_boot` | 容器运行中 |
| 增量 SQL | `230status_tip_config_incremental.sql`、`231status_tip_config_menu.sql`、`232status_tip_config_empty_illustrations.sql` | 执行成功；表 10 个图片位列、3 条菜单、角色/租户授权均已落库 |
| 后端构建 | `mvn -o clean package -pl aryn-boot -am -Dmaven.test.skip=true` | BUILD SUCCESS，45 个模块全部 SUCCESS |
| 镜像/重启 | `docker compose -f docker-compose-boot.yml up -d --build aryn-boot` | 镜像重建，容器 Recreated/Started；日志 `Started ArynBootApplication in 25.384 seconds` |
| 免登接口 | `GET http://localhost:9999/boot/app/status-tip-config`（`tenant-id: 1590229800633634816`） | HTTP 200，返回 10 个字段且均为空串 |
| 静态兜底 | `GET /boot/status-tip/{search,network,content,collect,comment,halo,message}.png` 共 7 张 | 全部 HTTP 200，`image/png`；`message.png` 21132 bytes，1024×1024 |

> 构建注意：首次使用 `-DskipTests` 时，`aryn-promotion-biz` 的测试源码因调用 JDK 21 的 `List.getFirst()` 在 JDK 17 下 testCompile 失败，属既有问题；本次按 `-Dmaven.test.skip=true` 跳过测试编译完成打包，不影响主代码。

## 六、待验收（部署后）

1. boot 已完成（见「五之二」）；cloud upms 库执行 `244/245/247`，cloud 执行 `246` 并刷新 Nacos 网关配置，cloud 服务重启。
2. 管理端重新登录后确认「系统设置 → 空态插画配置」可见、保存按钮受 `upms:statustip:edit` 控制；菜单 SQL 已落库，当前 Redis 未发现 `menu_cache*` 键，若仍不可见再清 `menu_cache::*` 并重登。
3. 未配置时确认 7 张内置静态图可访问（`/status-tip/*.png` / `/upms/status-tip/*.png`），购物车/订单/秒杀三处仍显示随包 PNG，小程序不再请求 npmmirror。
4. 管理端上传一张图后保存，C 端对应空态使用新图；免登接口 boot 已实测 200，cloud 待测。
5. 小程序真机验证空态/失败态出图（含登录前页面），重点检查购物车、订单列表、秒杀会场三处。

## 七、边界

- 已覆盖 `wd-status-tip` 的 7 个预设位与购物车/订单/秒杀 3 张会场插画；TabBar、支付渠道图标、登录微信图标、品牌渐变图标等品牌/平台固定素材仍随包，不走后台配置。
- 管理端上传仍走租户存储配置；未配置存储时上传失败并提示，与商品图一致。
- 图片域名沿用商品图域名，若商品图本身配置了私有读/签名 URL，需要另行评估空态图是否也要签名。
- 本次删除了 10 个无引用静态资源（`static/tab-nav/` 8 张 PNG、`static/mobile.svg`、`static/pwd.svg`），删除前已确认全仓无引用；后续若要从历史版本恢复，需从 Git 取回。
