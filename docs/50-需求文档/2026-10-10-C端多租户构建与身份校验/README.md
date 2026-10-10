# C 端多租户构建与身份校验落地记录

> 日期：2026-10-10
> 分支：codex/mobile-replenish-ui（多租户改动与其余在途修改并行工作树，互不触碰）
> 上游方案：`docs/plans/2026-10-10-c-end-multi-tenant-build-design.md`（V2.1 实施方案）+ 独立可行性评审

## 落地范围（P0-A + P0-B 代码层）

本轮完成方案 Step 1–6 的全部代码与测试；Step 7（两租户×双模式联调）与 Step 8（灰度/上传自动化）按方案要求阻塞在真实域名与第二租户 AppID，不在本轮范围。

### P0-A 租户构建体系（aryn-mall-uniapp）

- **配置契约**：`tenants/<key>.json`（真实身份+空 origin 阻断）+ `_template.json`（下划线开头不参与 all）。校验规则：key 正则+与文件名一致、tenantId 必须字符串（雪花 ID 防精度丢失）、wxAppId `wx[0-9a-f]{16}`、apiBaseUrl 必须 HTTPS origin 且拒绝 localhost/回环/示例域名/带 path。全量预检拒绝 tenantId/AppID 重复。
- **manifest 唯一工厂**：`build/manifest-factory.mjs` 承载完整静态对象（含 helper 默认值的 `mp-harmony` 段对齐）；`manifest.config.ts` 变薄包装。**工厂直写副本 manifest 绕开 uni CLI 早读 `parseManifestJsonOnce` 的 once 缓存错位**；默认 dev 由 `scripts/run-uni.mjs` 包装器预写（内容不变不回写，避免 mtime 抖动）。
- **独立副本**：`.tenant-build/<key>/<profile>/<deployment>/<buildId>/`，白名单复制（排除 .env*/测试/tenants/私钥/project.private.config.json），SHA-256 快照核对（复制后立即核对——生成物写入发生在核对之后，否则 manifest/身份模块会触发误报）；源码 symlink 拒绝；`src/project.wx.json`/`src/project.config.json` 存在即预检失败。
- **受控 env**：新建 env 对象 + 封闭清单（VITE_TENANT_*/VITE_OPEN_BOOT/VITE_API_BASE_URL/VITE_ENV_NAME/VITE_BUILD_ID/UNI_INPUT_DIR/UNI_OUTPUT_DIR 等），父进程 VITE_/UNI_ 残留无效。
- **pages 预生成屏障**：`vite.config.ts` 在创建 Uni()/UniKuRoot() 前 `new PageContext(共享参数).updatePagesJSON()`；参数抽在 `build/pages-options.mjs` 与插件同源。
- **产物校验**：`artifact-contract-plugin.mjs`（configResolved 固化 env 与规范化身份逐字段比对；generateBundle 从入口 chunk 走 imports/dynamicImports 验证 `src/generated/tenant-build.ts` 可达且身份字面量内联——树摇掉的未消费 identity 直接失败）+ `artifact-verifier.mjs`（project.config/app.json 悬空路由/tabBar 资源/theme.json/禁止文件/摘要）。
- **不可变发布**：attempt → rename 到 `dist/tenants/<key>/<profile>/<deployment>/<buildId>/`，目标已存在必须失败；包外元数据 build-meta/verification/checksums；ownership 状态机 CREATED→COPIED→COMPILED→VERIFIED→PUBLISHED（失败记 FAILED）；`.tenant-build/build.lock` 互斥锁。
- **身份进运行时**：副本生成 `src/generated/tenant-build.ts`（冻结身份对象）；默认源码同路径放 null 版本（快照白名单排除该文件）。
- **命令**：`pnpm build:tenant <key>|all [--profile --deployment] [--dry-run]`、`pnpm verify:tenant --artifact <dir>`、`pnpm test:build`。全部 uni dev/build 脚本改走 `scripts/run-uni.mjs` 包装（命令名不变）。
- **gitignore**：根 `build/` 规则会吞掉 `aryn-mall-uniapp/build/`，已加 `!aryn-mall-uniapp/build/` 白名单；新增 `.tenant-build/`、`tenants/.private/` 忽略。

### P0-B 后端身份校验（aryn-auth / aryn-user）

- **权威解析** `MiniAppBindingResolver`：`selectValidWxMaByAppId`（@InterceptorIgnore + 参数绑定 + List 返回）每次直查库；0 条/多条/租户不一致/secret 缺失一律 403 fail-closed，绝不自动纠正为另一租户；校验成功刷新本实例 `WxMiniAppConfigCache`（独立副本），消费端用该快照 `WxMiniAppConfiguration.createMaService(account)` 构造 SDK。
- **登录顺序** `TocLoginService` 四入口重构：maLogin/maPhoneLogin 固定 WX_MA 平台校验 → guard 权威绑定（`LoginTenantGuard` @DubboReference user-api 的 `RemoteMiniAppTenantService.requireBinding`）→ 已验证租户进 ThreadLocal → 手机号解密/查用户/socialLogin → socialUser/userInfo 租户断言 → 绑定（`SocialUserBindDTO.expectedTenantId` 核对跨租户反查）→ 用已验证租户签发 → finally 恢复进入前快照。smsLogin/passwordLogin 对 WX_MA 走 guard、H5/APP 保留既有分支、微信形 AppID+非微信平台的矛盾请求拒绝。
- **请求字段来源**：`TocTokenController.applyRequestIdentity` 每入口无条件从原始请求头覆盖 `requestTenantId/appId/platformType`（body 不可信，ThreadLocal 不可用——有 token 时过滤器改用会话租户）。
- **两个前端可用端点**：`GET /toc-token/tenant-binding`（匿名，复用权威解析，公开返回仅 appId/tenantId/ready）；`GET /toc-token/tenant-session?scope=mall|delivery`（白名单路径下 Controller 手工校验登录：无 token→401，设备错配/会话租户≠原始 tenant-id 头→403 且不清 token）。二者被 `/toc-token/**` 白名单覆盖，无需扩大配置。
- **后台维护补强**：`SocialAccountServiceImpl.save` 补 `@CacheEvict`（原来没有）；新增/更新做全局 AppID 重复预检（`countValidWxMaByAppIdExcluding`）；tenantId 与当前上下文不一致拒绝写入。`WxMiniAppConfigLoader` 改 try/finally 恢复租户上下文、只加载 WX_MA、重复 AppID 不覆盖并记 error。
- **审计登记**：两个新 @InterceptorIgnore 方法登记进 `TenantInterceptorBypassAuditTest` 白名单（该测试扫描全仓所有绕过注解，漏登即红）。

### P0-B 前端运行时守卫（aryn-mall-uniapp）

- `src/api/core/tenant-preflight.ts`：仅两个固定端点的原始 uni.request，不 import alova/store（防 instance→guard→API→instance 循环）；错误类型化（network/unauthorized/forbidden/bad-payload）。
- `src/api/core/tenant-identity.ts`：idle→local-check→binding-check→ready|blocked 状态机；generated identity 与 import.meta.env 逐字段断言 + MP-WEIXIN 真机 AppID 比对（vitest 下 #ifdef 不剥离，`typeof uni` 防御）；shared Promise 去重、blocked 不自动重试、`retryTenantBinding()` 新建 Promise；`ensureSessionTenant(scope, token)` 按 scope+token 缓存、失败不缓存、401 带 checkedToken。
- **覆盖入口**：`instance.ts`（async beforeRequest：await binding + 携 token 请求 await session，401 仅清对应 scope 且比对该 token 未被换掉）、`file.ts`（uploadFile/uploadDeliveryEvidence 上传前守卫；配送 401 才清登录态、403 保留 token）、`messageStore.connect`（async 化、守卫失败不建 socket）、`sub-pages/utils/pay.ts`（requestPayment 前守卫）、`sharedCartImport`（旁路上传守卫）、`authStore` 四登录（token 先过 session 校验再 setLoginState；fetchUserInfoAfterLogin 不吞身份错误）、配送两入口（delivery/login.vue 与 user-center 的探活分类：401 才清理重换、403/守卫错误保留 token 不自动换取、不导航）、App.ku.vue（blocked 全屏 UI + buildId 追溯 + 重试 + onShow 5 分钟节流刷新）。

## 完成度复核补强（2026-10-11）

对照方案 §12 测试设计复核出 4 个缺口并已补齐：

1. **默认 dev 链路真实运行验证**：`pnpm dev:mp-weixin` 经 run-uni.mjs 包装器完整跑通
   （"Build complete. Watching"），默认 manifest/产物 project.config 均为默认身份
   （aryn-mall-uniapp + 默认 AppID），无任何租户污染；验证后 watcher 已全部清杀。
2. **回前台刷新接线修正**：App 级 onShow 只在 `src/App.vue`（真正的 App 组件）触发，
   从 App.ku.vue 移过去；App.ku.vue 改为订阅 `onTenantGuardStateChange` 驱动阻断 UI。
3. **构建故障流程 E2E**（方案 §12.1 条 7/10）：`tests/build/build-tenant.e2e.test.mjs`
   5 例——fake uni（成功/编译失败/产物无效三行为）+ fixture 项目：
   成功发布 mp-weixin/+元数据且 ownership=PUBLISHED；编译失败与产物无效均不发布、
   ownership=FAILED、非零退出；锁独占拒绝且不建工作目录。接缝：
   `TENANT_BUILD_PROJECT_ROOT`/`TENANT_TEST_UNI_BIN`（fake 行为编码进脚本文件——
   buildChildEnv 封闭清单会剥掉测试 env，行为不能走环境变量）。
4. **Controller 契约测试**（方案 §12.3）：`TocTokenControllerTest` 7 例——header 无条件
   覆盖 body 伪造身份、binding 端点仅返回 appId/tenantId/ready、缺头 403 不触 guard、
   session 白名单路径下无登录显式 401、设备错配 403（requireDevice 走真实现）、
   会话租户错配 403、成功仅返回 tenantId。

## 验证结果（2026-10-10）

| 验证 | 结果 |
|---|---|
| `pnpm test:build`（tests/build，node:test） | 31/31 通过（26 单元 + 5 故障流程 E2E） |
| `pnpm type-check`（vue-tsc） | 通过 |
| `pnpm test:unit`（vitest 全量） | 641/641 通过（62 文件；tenant-identity 新增 9 例；3 处既有契约测试锚点适配：urlCheck 契约迁移到唯一工厂、requireTokenValue 计数、sharedCartImport async 锚点） |
| `mvn test -pl aryn-boot -am` | 全量通过（唯一失败为绕过审计白名单漏登，已登记；auth 5 例、user-biz resolver 6 例新增全绿） |
| 真实 uni 租户构建 E2E | 见下节 |

## E2E 真实构建验证

临时给 `tenants/aetheryn.json` 的 staging/boot 填 LAN IP origin（`https://192.168.3.14:9999`，校验器允许 IP、禁止 localhost/示例域名），执行 `build:tenant aetheryn --profile staging --deployment boot` 走完全链路：副本快照 → manifest 工厂直写 → 身份模块生成 → uni build（attempt 目录）→ 契约插件/env 固化/身份可达校验 → verifier → 不可变发布。验证后临时 origin 已清空回 `""`（未配置组合必须阻断，联调需部署负责人提供真实域名）。

## 阻塞项（须负责人提供，按方案 §17）

1. A/B 每个目标 profile/deployment 的真实 HTTPS origin。
2. 第二业务租户真实 tenantId/AppID 与后台开通记录。
3. 每租户微信平台/支付/回调/业务初始化配置。
4. 两租户×双模式联调矩阵（方案 §12.4）与灰度回滚演练。

## 关键教训

- 根 `.gitignore` 的 `build/` 全局规则会静默吞掉子项目 `build/` 目录，新增构建模块目录必须加白名单并用 `git check-ignore` 验证。
- uni CLI 在 Vite 配置加载前 `parseManifestJsonOnce` 且 once 缓存，任何"Vite 插件阶段再生成 manifest"的方案都晚于早读；只有 CLI 启动前确定性直写能保证各阶段同源。
- `@uni-helper/vite-plugin-uni-pages` 的 `VitePluginUniPages` 在创建时同步 `checkPagesJsonFileSync`，因此 pages 预生成必须发生在插件创建之前（configResolved 里的生成对首帧已晚）。
- vitest（node 环境）不剥离 `#ifdef MP-WEIXIN` 注释块，引用 `uni.*` 全局要用 `typeof uni !== 'undefined'` 防御。
- `ArynBusinessException(Integer, String)` 构造器不写 `getMessage()`（msg 在 `getMsg()`），测试断言必须用 `extracting(e -> ((ArynBusinessException) e).getMsg())`。
- `mvn test -pl aryn-auth` 漏 `-am` 会用 ~/.m2 旧 user-api jar 编译测试（找不到新方法符号），跨模块新增字段/方法后必须带 `-am`。
- pinia state 在 await 后被 TS 控制流窄化成初始字面量类型，比较其它枚举值会报"无重叠"——用 `as` 断言回全集。
