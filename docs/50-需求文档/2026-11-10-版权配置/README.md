# 版权配置可配置化

> 状态：已实施（2026-11-10）；后端单测/契约测试、管理端与移动端类型检查通过；真实库与真机验收见「六、待验收」。

## 一、原始请求

运营视角指出：系统里的版权信息目前全部写死在代码里，要求做成像图里那样可配置的
「版权配置」：分用户端 / 商户端 / 平台端三个 Tab，可维护 logo、名称、版权信息、版权链接、
ICP 备案号、ICP 网站链接（平台端另有登录页标题 / 副标题 / 图标）。

## 二、改造前现状

| 端 | 位置 | 内容 |
|---|---|---|
| 管理端 · 登录后页脚 | `packages/preferences/src/index.ts` | 公司名 `Aryn Mall`、官网、年份写死 `2025` |
| 管理端 · 登录页 | `apps/web-ele/src/layouts/auth.vue` + `locales/langs/*/page.json` | `© 年份 悦航购 Aetheryn Mall`，logo 取静态 `/static/logo.png` |
| C 端 · 登录页 | `src/pages/login/index.vue` | 内联 `copyright` ref 写死 logo，名称回落语言包；**从未请求服务端** |
| C 端 · 版权行 | `src/locales/zh-CN.ts` | `天启雨数版权所有` |
| C 端 · 配送登录 | `src/pages/delivery/login.vue` | 模板里写死 `悦航购配送` |
| C 端 · H5 外壳 | `index.html` | 陈旧品牌 `Hx Mall` |
| 后端 | — | 无任何版权/站点配置表；`copyright`/`icp` 仅命中源码文件头注释 |

## 三、设计口径

| 项 | 口径 |
|---|---|
| 存储 | 新表 `sys_copyright_config`，一行一端（`client_type` = `app` / `merchant` / `platform`），唯一键 `(tenant_id, client_type)` |
| 权限 | 平台管两端、租户管用户端：`merchant`/`platform` 需平台管理员（`upms:copyright:platform:*` + `PlatformTenantGuard`）；`app` 由各租户维护（`upms:copyright:*`） |
| 品牌单源 | 名称 / Logo 留空 = 跟随 `sys_tenant.name` / `logoUrl`，避免后台顶栏与页脚两套品牌；服务端在下发值里完成回落，管理端读取的是原始值（如实反映「留空」） |
| 登录前取数 | 登录页没有会话也没有 `tenant-id` 头，平台端配置固定取平台租户（`tenant_type='1'`，数据驱动、不写死租户 ID），读取走 `@InterceptorIgnore` + 显式 `tenant_id`；写侧同样固定写平台租户那一条（平台管理员可能处于切换后的租户上下文，写入前临时切上下文并在 finally 还原） |
| 年份 | 不入库；页脚渲染时取当前年，避免再出现写死 `2025` 跨年过期 |
| 链接安全 | 只允许 `http/https` 或站内相对路径，拒绝 `javascript:` 等协议（值会直接绑 `href`）；C 端再过滤一次 |
| 缓存 | 下发值进 Redis（用户端/商户端 `copyright:config:{tenantId}:{clientType}`；平台端全局 `copyright:config:platform`，所有租户登录页共用），TTL 5 分钟；保存按端精确失效（不用 `keys()` 通配扫描）；租户改名/换 Logo 后由租户修改链路清该租户两端 + 平台端 key |
| 菜单拆分 | 两个页面入口共用一个组件：`/platform/copyright-config`（菜单名「平台版权」，商户端 + 平台端，`sys_key` 平台专属应用）与 `/system/copyright-config`（菜单名「版权配置」，用户端，`app_base`）。平台应用不随套餐下发，租户天然看不到平台入口。**两个菜单的 `name` 必须不同**：vben 用菜单 `name` 作为路由名，同名路由会被后注册的覆盖，表现为其中一个入口点击后 404 |
| 页脚 | 商户端配置渲染在布局页脚（框架默认 `footer.enable=false`）。除 app 级默认值外，`main.ts` 做一次代码级迁移（与 `loginExpiredMode` 同口径），否则老用户本地偏好缓存里的 false 会让商户端配置无处显示 |
| 边界 | 小程序 `manifest.json` 应用名、`pages.json` 导航标题是编译期产物，服务端配置改不了；本次只覆盖登录页/页脚等运行时渲染点 |

## 四、影响端与文件

| 端 | 主要改动 |
|---|---|
| 后端（aryn-upms） | 新增 `SysCopyrightConfig` 实体、`CopyrightConfigDTO`、`CopyrightConfigVO`、`AppCopyrightConfigVO`、`SysCopyrightConfigMapper`、`ISysCopyrightConfigService` / `Impl`、`SysCopyrightConfigController`、`AppCopyrightConfigController`；`SysTenantServiceImpl` 在租户修改后提交事务时失效版权缓存；`CacheConstants` 增加缓存前缀 |
| 管理端（web-ele） | 新增 `api/upms/copyright-config.ts`、`views/upms/copyright-config/index.vue`、`composables/use-public-copyright.ts`；`layouts/auth.vue`（平台端品牌/版权/ICP）、`views/_core/authentication/login.vue`（标题/副标题/图标）、`layouts/basic.vue`（商户端页脚）接线；`packages/preferences` 默认年份改当前年并关闭本地版权编辑区 |
| C 端（uniapp） | 新增 `api/upms/copyright.ts`、`store/copyrightStore.ts`（缓存先行防闪、失败保持、租户后缀 key）+ 单测；`pages/login/index.vue`、`pages/delivery/login.vue` 接线；`store/persist.ts` 排除新 store；`authStore` 登出重置；`App.vue` 回前台节流刷新；`index.html` 修正陈旧品牌 |
| 数据库 | boot `db/boot/219copyright_config_incremental.sql`、`220copyright_config_menu.sql`；cloud `db/cloud/229...`、`230...`、`231copyright_config_nacos.sql`；`build-full-sql.mjs` 登记并重新生成 `aryn_boot_full.sql` |
| 双模式 | boot `application.yml`：`secure.ignore.urls` 加 `/app/copyright-config`、`hx.tenant.tables` 加 `sys_copyright_config`；cloud：`3aryn_nacos.sql` 基线（upms 租户表 + 网关双前缀白名单 + 精确 boot 兼容路由，md5 已同步）+ 231 增量脚本；`PublicEndpointWhitelistContractTest` 增补公开路径、私有路径与新增用例 |

## 五、验证记录（2026-11-10，本机）

| 项 | 命令 | 结果 |
|---|---|---|
| 后端编译 | `mvn -o compile -pl aryn-boot -am`（JDK 21） | 通过 |
| 后端单测 | `mvn -o test -pl aryn-upms/aryn-upms-biz -am -Dtest=SysCopyrightConfigServiceImplTest,SysTenantServiceImplTest` | 26 项通过（版权 19 + 租户 7） |
| 租户/白名单契约 | `mvn -o test -pl aryn-boot -am -Dtest=TenantConfigurationConsistencyTest,PublicEndpointWhitelistContractTest` | 10 项通过 |
| 全量 SQL | `node db/boot/build-full-sql.mjs && node db/boot/verify-full-sql.mjs` | 静态校验通过（31951 行） |
| 管理端类型 | `cd aryn-mall-ui && pnpm check:type` | 通过 |
| 移动端类型 | `cd aryn-mall-uniapp && pnpm type-check` | 通过 |
| 移动端单测 | `npx vitest run src/store/copyrightStore.test.ts` | 8 项通过 |

本机 boot 库已执行 219/220（自检：platform_menu_rows=3、tenant_menu_rows=3、platform_seed_rows=2、app_seed_rows=2）。

本机 boot 重建镜像后的 E2E（2026-11-10）：

| 项 | 结果 |
|---|---|
| 免登 `/boot/app/copyright-config`（platform / app + tenant-id） | 均 200，名称/Logo 回落租户品牌，登录页三项仅平台端下发 |
| 平台管理员登录后 GET/PUT `/boot/copyright-config` | 保存后免登接口即时生效（缓存失效正确） |
| 非法链接保存 | 返回「版权链接只支持 http/https 链接或站内相对路径」 |
| 租户管理员改用户端 | 成功；改平台端返回 403「仅平台管理员可操作租户配置」 |
| 多租户隔离 | 租户 1 与租户 2 各自返回自己的名称，互不串读 |
| 管理端登录页（Playwright 实拍） | 品牌「悦航购」、Logo、`© 2026 悦航购 Aetheryn Mall`、登录标题/副标题均来自服务端配置 |
| 「平台版权」页（Playwright 实拍） | 商户端/平台端两个 Tab、字段回填正确；页面内保存提示「保存成功」且免登接口同步变化 |
| 后台页脚 | 渲染 `Copyright © 2026 悦航购 Aetheryn Mall` |
| C 端请求路径 | H5 实测发出 `/api/boot/app/copyright-config?clientType=app` 且 200 |

未覆盖：cloud 库增量脚本执行、小程序真机品牌渲染（H5 登录页路由被首页拦截，仅验证到请求与数据层）。

## 六、待验收（部署后）

1. boot 库执行 219/220，cloud 库执行 229/230/231，检查脚本末尾自检计数；
2. 清理 Redis `menu_cache::*` 并重新登录，确认「平台管理 → 版权配置」「系统设置 → 版权配置」两个入口可见；
3. 平台端改登录页标题/副标题/图标后退出登录，确认登录页立即生效；
4. 租户端改用户端版权后重开小程序，确认登录页品牌与版权行更新（5 分钟内生效，缓存 TTL 兜底）；
5. 修改租户名称/Logo，确认页脚跟随更新（缓存已失效）；
6. 填入 ICP 备案号与链接，确认管理端页脚与 C 端登录页展示且可点击；
7. 存量浏览器如仍看不到后台页脚，检查本地偏好缓存（`preferences`）或等待 `main.ts` 迁移生效。
