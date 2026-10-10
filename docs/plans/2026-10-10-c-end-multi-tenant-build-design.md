# C 端多租户构建与上线实施方案

> 版本：V2.1（代码评审与实施一致性复核后修订）  
> 日期：2026-10-10  
> 状态：可进入实施；生产域名、第二租户真实 AppID 与支付配置尚须由部署负责人提供  
> 范围：`aryn-mall-uniapp` 构建/运行时、`aryn-auth` 登录边界、`aryn-user-api/biz` 微信配置解析；管理后台复用现有开通能力  
> 本文是实施设计，不表示代码已经实现或验收通过。命令中新增脚本须完成对应步骤后才能执行。

## 0. 执行摘要与强制决策

采用“**一套源码 + 配置驱动 + 独立构建副本 + 每租户一个微信小程序**”。不做一个小程序运行时切换租户。

以下决策不留给实施阶段自由选择：

1. `VITE_TENANT_ID` 保持原名，普通请求、文件流、消息、主题缓存继续使用它。
2. 租户构建不修改原工作目录的 `src/manifest.json`、`src/pages.json` 或 `.env`。在独立普通目录内复制源码，不创建/使用 Git worktree。
3. uni 子进程启动**之前**完成 manifest 生成；副本中禁用 manifest helper 的运行时 import，彻底消除迟生成与缓存错位。
4. 环境模式 `production/staging` 与部署模式 `boot/cloud` 分开；域名按两维组合明确配置，不依赖 `.env.production` 的默认地址。
5. 输出先落独占 attempt，验证成功才发布为不可变版本目录；任何失败均不得标记为可上传。
6. 正式多租户上线必须包含后端登录前 AppID↔tenantId 校验，以及前端真实 AppID/会话租户检查。`shop-info` 不承担映射校验。
7. 不自动部署、不自动提审、不自动发布、不操作真实库。上传为后续独立阶段，先 dry-run 再人工确认。
8. 新增租户“只加配置”指前端构建接入；后台租户开通、业务数据、微信/支付配置仍是必要前置条件。

### 0.1 范围与验收门槛

| 阶段 | 内容 | 可宣称结果 |
|---|---|---|
| P0-A | 配置、隔离构建、默认开发兼容、产物校验 | 可以安全生成多租户候选包，不能直接宣称可生产上线 |
| P0-B | 后端登录身份校验、前端启动与会话校验、微信配置读取硬化 | 具备生产上线的技术前提 |
| P0-C | 两租户×双模式、登录/交易联调、灰度和回滚演练 | 通过后才允许上线 |
| P1 | 完整品牌外壳、更多配置管理体验、监控优化 | 产品体验完善，不替代 P0 身份边界 |
| P2 | miniprogram-ci 上传与 CI 构建流水线 | 自动化交付；平台提审/发布仍逐应用按实际权限处理 |

**P0-A、P0-B、P0-C 均为本期正式上线必需项，不把身份校验降为可选告警。**

## 1. 已核实的代码事实

### 1.1 构建事实

- `manifest.config.ts` 是源配置，`src/manifest.json` 是生成物；实际安装 manifest helper 为 **0.2.12**，`package.json` 的 `^0.2.8` 只是声明范围。
- helper 在 `dist/index.mjs:91–108` 通过 c12 加载配置并写文件，`121–127` 调用 `ctx.setup()` 但没有 await。
- uni CLI `@dcloudio/vite-plugin-uni/dist/cli/utils.js:169–176` 在 Vite 配置加载前调用 `parseManifestJsonOnce`；`uni-cli-shared/dist/json/manifest.js:23` 缓存首次读取结果。
- `UNI_OUTPUT_DIR` 已明确支持，见 CLI `utils.js:125–135`；必须在子进程启动前设置，使用绝对路径。
- Vite `loadEnv` 先读文件后用外部 `process.env.VITE_*` 覆盖；无需额外 define 才实现环境优先级。
- `vite.config.ts:25–49` 的 `fixProjectConfig()` 写死默认 dev/build 目录，需修改为只处理当前 outDir。
- uni-pages 的生成在异步 `configResolved`；`UniKuRoot()` 等插件创建时可提前读取 pages。副本必须先生成 pages，再创建依赖它的插件。
- `src/project.wx.json` / `src/project.config.json` 一旦存在，可覆盖 manifest 派生的工程配置；`project.private.config.json` 也可能被复制到产物。本方案明确拒绝这些输入。

### 1.2 租户与认证事实

- `src/api/core/instance.ts` 每次发送包内 tenant-id，微信小程序 AppID 来自 `uni.getAccountInfoSync()`，不是 Vite 环境变量。
- `getCurrentShop()` 已接 `/upms/app/tenant/shop-info`，带 `skipToken:true`；服务端返回标识为 `data.id`，不存在时为 `data:null`。
- `ArynTenantContextFilter` 匿名取请求头租户，有登录会话时取会话租户；不比较 app-id 和 tenant-id。
- 微信登录按 `social_account.app_id` 反查租户；手机号/短信/密码流程存在先查用户、后恢复微信租户的顺序，需要调整。
- 三方账号配置是进程内 ConcurrentHashMap。后台维护刷新当前实例，直接 SQL 与其他实例不自动同步；不能用“本实例缓存命中”证明权威配置正确。
- 独立营业还需要 `pay_config`、订单配置/回调及租户业务数据，不是只新增 `sys_tenant` 和 `social_account`。

## 2. 配置契约

### 2.1 文件组织

```text
aryn-mall-uniapp/
  tenants/
    aetheryn.json
    _template.json
    .private/                 # 仅后续上传私钥；不进入构建副本
  build/
    manifest-factory.mjs      # 唯一 manifest 对象工厂，纯模块
    tenant-config.mjs         # 解析、校验、规范化配置
    snapshot.mjs              # 复制清单、摘要、路径安全
    build-context.mjs         # 环境清理和本次路径契约
    pages-options.mjs         # 预生成/正常插件共享参数
    artifact-verifier.mjs     # 候选包校验与输出证据
    artifact-contract-plugin.mjs
  scripts/
    build-tenant.mjs
    run-uni.mjs               # 默认 dev/build 包装器
    verify-tenant.mjs
  tests/build/*.test.mjs      # Node 原生 test，不混进 src 的 Vitest 范围
  .tenant-build/              # 本机构建副本/日志/缓存，Git 忽略
  dist/tenants/               # 成功的不可变产物，已有 dist 忽略规则
```

### 2.2 配置示例：真实身份 + 未提供的部署地址

`tenants/aetheryn.json` 初始内容：

```json
{
  "schemaVersion": 1,
  "key": "aetheryn",
  "name": "悦航购",
  "tenantId": "1590229800633634816",
  "wxAppId": "wx0a8242ea59f3e6b4",
  "defaultProfile": "production",
  "defaultDeployment": "boot",
  "profiles": {
    "production": {
      "boot": { "apiBaseUrl": "" },
      "cloud": { "apiBaseUrl": "" }
    },
    "staging": {
      "boot": { "apiBaseUrl": "" },
      "cloud": { "apiBaseUrl": "" }
    }
  }
}
```

**空地址是明确的未配置状态，构建必须立即失败。不能将示例域名或当前 localhost 当作可发布配置。** 部署负责人填写实际地址后，该组合才可构建。一个暂未部署的组合可以保持空，但其双模式联调验收不能被标记为通过。

`_template.json` 身份字段也为空，结构同上；`all` 必须排除所有 `_` 开头文件，不能构建模板。

本期不再使用 `envName` 与 `openBoot` 的独立可编辑字段：

- `VITE_ENV_NAME = profile`，防止 env 标签与 mode 不一致。
- `VITE_OPEN_BOOT = String(deployment === 'boot')`，防止配置里出现 cloud 却 openBoot=true。
- 仍由业务代码 `parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)` 解析，不引入其它解析器。

### 2.3 校验规则

| 字段/输入 | 规则 | 失败行为 |
|---|---|---|
| schemaVersion | 必须等于 1，未知字段也拒绝 | 配置错误，非零退出 |
| key | `^[a-z][a-z0-9-]{0,31}$`，必须等于文件名 | 拒绝路径穿越、大小写漂移 |
| name | 去首尾空白后非空，作为工程名称；不是微信审核名称 | 拒绝空名称 |
| tenantId | 必须是十进制正整数字符串，不能是 JSON number | 拒绝雪花 ID 精度损失 |
| wxAppId | `^wx[0-9a-f]{16}$`，禁止游客 AppID | 拒绝无效身份 |
| profile | production 或 staging | 不接受随意拼出的 `.env.<name>` |
| deployment | boot 或 cloud | 不推测部署模式 |
| apiBaseUrl | 绝对 HTTPS URL，无账号密码、query、fragment；本期统一为 origin，path 仅空或 `/` | 禁止重复 `/boot` 和服务域拼接 |
| 禁止地址 | localhost、回环/链路本地地址、`.invalid`、example/yourapp 等占位域名 | staging 也不能使用示例地址 |
| 配置冲突 | 不同 key 不能重复 tenantId 或 AppID；本期一租户一小程序 | `all` 在首个编译前检查全量 |
| 本机环境 | Node≥22，依赖已安装；记录实际版本 | 不自动全局装依赖 |

此策略针对可供微信体验/生产联调的包。本地 HTTP 调试仍走原 `dev:mp-weixin`，不放宽租户发布脚本。

### 2.4 命令契约

以下命令是本方案实施后提供的接口：

```bash
pnpm build:tenant aetheryn
pnpm build:tenant aetheryn --profile staging --deployment cloud
pnpm build:tenant all --profile production --deployment boot
pnpm build:tenant aetheryn --profile staging --deployment boot --dry-run
pnpm verify:tenant --artifact /absolute/path/to/release-directory
pnpm test:build
```

- 不传参数时按租户 defaultProfile/defaultDeployment；生产 CI 必须显式传两维参数。
- `all` 对全量配置预检，随后按 key 排序串行构建；任何失败停止，整体非零退出。此前已成功版本保留，不伪装整体成功。
- `--dry-run` 只展示解析后的无秘密配置、计划路径、工具版本，不生成副本、不调用 API、不编译。
- 不提供 `--force` 跳过校验；不接受租户发布 `--watch`；不将 `--mode` 与 profile 混用。

## 3. 独立构建副本与路径安全

### 3.1 目录布局

```text
.tenant-build/<key>/<profile>/<deployment>/<buildId>/
  ownership.json              # 所属项目、UUID、源根、创建时间、状态
  app/                        # 独立源码副本，真正的 Vite root/cwd
    node_modules -> <原项目 node_modules 的绝对路径>
    src/
    build/
    package.json
    vite.config.ts
    ...
  attempt/
    mp-weixin/                # 仅本次 uni 写入的输出
  cache/vite/
  cache/uni/
  logs/build.log
  source-inventory.json
  normalized-config.json
  verification.json
```

成功发布：

```text
dist/tenants/<key>/<profile>/<deployment>/<buildId>/
  mp-weixin/                  # 微信工具/上传使用此目录
  build-meta.json             # 元数据放包外，不增加小程序体积
  checksums.json              # 产物摘要
  verification.json
```

buildId 用 `crypto.randomUUID()`，不靠时间戳防冲突。记录时间用 `new Date().toISOString()`；不手算 Unix timestamp。

### 3.2 副本复制白名单

必须复制：

- `src/`，包括静态素材、`theme.json`、布局、业务源码和当前生成的 pages。
- `build/` 的实现模块，不复制测试与密钥。
- `package.json`、`pnpm-lock.yaml`、`tsconfig.json`、`index.html`。
- `vite.config.ts`、`manifest.config.ts`、`pages.config.ts`、`uno.config.ts`。
- `async-import.d.ts`、`async-component.d.ts`；src 内已有声明按正常源码复制。

不复制：

- 原项目 `node_modules`、dist、.tenant-build、.git、.workbuddy-ai、编辑器目录。
- 任意 `.env*`，包括 `.env.local`；租户构建由受控 env 完整注入，不允许上级 `.env` 回退。
- `*.test.*`、`*.spec.*`、`__tests__`、测试截图和测试结果；若运行时误引用测试，编译失败，不自动补拷贝。
- tenants 目录及 `.private`、私钥/证书；构建只使用已经规范化的无秘密配置。
- `src/project.private.config.json`。

对 `src/project.wx.json` / `src/project.config.json` 不悄悄丢弃：**检测到即预检失败，提示先删除冲突或完成显式受控迁移**，避免隐藏团队新加配置。

副本源文件不允许 symlink。除了新建的 node_modules 链接，其余目录均为真实复制。对文件使用 lstat，不跟随任意源码 symlink；路径的 realpath 必须在原项目允许根内。

### 3.3 快照一致性与依赖边界

- 对白名单文件记录相对路径、大小、SHA-256；复制后核对副本摘要，再核对原目录文件集/摘要。
- 发现复制期间用户修改源码或 dev 正在回写生成物，则停止本次尝试，提示重试。无需强制关闭 dev，但不能接收混合时间点快照。
- 原源码保持未提交修改，不使用 `git archive HEAD` 丢掉用户当前修改。
- 记录源码摘要、锁文件摘要、实际工具版本；本地 dirty=true 可生成候选包，正式发布建议 CI 使用明确提交且 dirty=false。
- 共用 node_modules 只用于读依赖。Vite/uni 缓存、自动声明、pages/manifest 输出都指向副本/attempt，不写原 node_modules/.vite。
- 同时 `pnpm install` 与构建不受支持；建立构建/依赖变更互斥，构建前后核对依赖清单。不能只看锁文件没变就保证实际安装没变。
- P0 `all` 串行，独立 buildId 允许互不覆盖；原目录默认 dev 可继续运行，快照稳定性检查可能要求重试。未来并行须验证所有缓存均隔离后才开放。

### 3.4 环境清理

对子进程创建新 env 对象，保留必需系统项（PATH、HOME、临时目录、编码等），但清除所有继承的 `VITE_*`、`UNI_*`、HBuilderX 参数和未知 `NODE_OPTIONS`，再设置白名单。

关键注入：

```text
VITE_TENANT_BUILD=true
VITE_TENANT_KEY=<key>
VITE_TENANT_ID=<tenantId>
VITE_TENANT_WX_APPID=<wxAppId>
VITE_TENANT_NAME=<name>
VITE_OPEN_BOOT=<true|false 字符串>
VITE_API_BASE_URL=<实际 origin>
VITE_ENV_NAME=<profile>
VITE_BUILD_ID=<buildId>
VITE_PORT=8888
CI=1
VITE_ROOT_DIR=<副本 app 绝对路径>
UNI_INPUT_DIR=<副本 app/src 绝对路径>
UNI_OUTPUT_DIR=<本次 attempt/mp-weixin 绝对路径>
UNI_APP_X_CACHE_DIR=<本次 cache/uni 绝对路径>
```

其它现有 VITE 项必须列入实现中的封闭清单，明确默认值，不从父进程任意透传。例如 `VITE_CUSTOMER_SERVICE_MODE` 需先核对代码接受值再显式决定；不能在复制后悄悄读取原 `.env`。代理/H5 dev 特殊变量不属于微信发布输入。

子进程 `cwd=副本 app`，参数 `--config <副本 vite.config.ts> --mode <profile>`。`NODE_ENV` 由普通 uni build 固定 production，staging 是构建配置模式，不是开发/watch 编译。

## 4. manifest 单一来源与生成屏障

### 4.1 抽取工厂

新增 `build/manifest-factory.mjs`。迁移当前 `manifest.config.ts` 中的完整静态对象，不删 App/H5/其它平台配置；返回新对象，只替换租户 name 与 mp-weixin.appid。

工厂契约：

```js
export const DEFAULT_WX_APP_ID = "wx0a8242ea59f3e6b4";
export const DEFAULT_APP_NAME = "aryn-mall-uniapp";

export function createManifest(identity) {
  // 内部完整保留当前所有 manifest 配置。
  // 每次返回独立对象，不能复用被其它调用改写的共享对象。
  const manifest = createOriginalManifest();
  manifest.name = identity.name;
  manifest["mp-weixin"].appid = identity.wxAppId;
  return manifest;
}
```

`createOriginalManifest()` 必须包含当前完整对象，不是只生成上面两个字段。工厂不 import uni-manifest，不读文件，不调用 watch，不依赖 process.env；输入显式传入。

根 `manifest.config.ts` 变为薄包装：

```ts
import { createManifest, DEFAULT_APP_NAME, DEFAULT_WX_APP_ID } from './build/manifest-factory.mjs'

const tenantBuild = process.env.VITE_TENANT_BUILD === 'true'
if (tenantBuild && (!process.env.VITE_TENANT_NAME || !process.env.VITE_TENANT_WX_APPID)) {
  throw new Error('租户构建缺少名称或微信 AppID')
}

export default createManifest({
  name: tenantBuild ? process.env.VITE_TENANT_NAME! : DEFAULT_APP_NAME,
  wxAppId: tenantBuild ? process.env.VITE_TENANT_WX_APPID! : DEFAULT_WX_APP_ID,
})
```

默认模式不能读取残留租户环境变量；包装器清理这些变量，继续使用 `.env` 默认 tenant-id 和开发地址。

### 4.2 租户预生成

build 脚本从**副本内工厂**读取对象，并在调用 CLI 前写 `app/src/manifest.json`，写入后 JSON.parse 检查 name/appid。不能使用原目录工厂解析后再假称副本自洽。

副本 Vite 配置满足：

- 当 `VITE_TENANT_BUILD=true`，不 import / 不执行 uni-manifest helper。
- 默认 dev 时，条件动态 import helper 并显式 cwd 指向当前根。
- 禁止顶部静态 import helper，否则其模块级副作用仍可运行。

`await UniHelperManifest()` 不足以等待其内部 setup，此方案不依赖该行为。

### 4.3 默认命令包装器

`pnpm dev:mp-weixin` 命令名保持不变，内部改成 `node scripts/run-uni.mjs dev -p mp-weixin`；默认 `build:mp-weixin` 同样包一层。

包装器顺序：

1. 解析 dev/build 和平台，拒绝把租户发布参数混入默认开发入口。
2. 清除租户专属控制变量及用户残留 UNI_INPUT/OUTPUT_DIR；保持普通开发 `.env` 行为。
3. 用同工厂写当前根默认 manifest；生成失败不得启动 CLI。
4. 通过同 Node 可执行文件启动 uni CLI，cwd 原项目；转发退出码和 SIGINT/SIGTERM。

其它 uni dev/build 脚本也应统一包装，保留原命令名/参数；避免 H5/App 直接 CLI 又遇到旧 manifest 早读。`release-*` 等非 uni 脚本不改。

团队可以继续跟踪默认 manifest；租户构建不污染该文件。不在生成 JSON 顶部手写注释，而在工厂和方案中说明来源。

## 5. pages、Vite 与插件改造

### 5.1 pages 预生成屏障

将现有 UniHelperPages 参数抽到 `build/pages-options.mjs`，预生成与正常插件使用同一份参数：dirs、subPackages、exclude、outDir、dts、绝对 configSource。

Vite async 配置在创建 `UniKuRoot()`、`Uni()` 前：

```ts
import path from 'node:path'
import UniHelperPages, { PageContext } from '@uni-helper/vite-plugin-uni-pages'
import { createPagesOptions } from './build/pages-options.mjs'

const root = process.env.VITE_ROOT_DIR || process.cwd()
const pagesOptions = createPagesOptions(root)
const context = new PageContext(pagesOptions, root)
await context.updatePagesJSON()
// 此后校验 pages.json 可解析，所有目标页面源码存在。
// 然后创建 UniKuRoot() / Uni()，并保留 UniHelperPages(pagesOptions)。
```

正常 uni-pages 插件仍负责 definePage/route block transform 和 virtual:uni-pages。预生成只在配置阶段建立输入屏障，不替代正常插件。

- `configSource` 指定副本的绝对 pages.config 文件，不靠向父目录查找。
- Uno 配置也显式指定副本文件，保持原 presets/theme/blocklist。
- 预生成后校验目标平台 pages、分包、tabBar 对应源码与图片存在；若旧 pages 合并留下陈旧路径，应报错修正源配置，不悄悄剔除业务路由。
- 实施时为 PageContext 的导出和方法建立契约测试，并锁定实际依赖版本；未来升级不能跳过构建矩阵。

### 5.2 fixProjectConfig 改造

只读取本次 `UNI_OUTPUT_DIR/project.config.json`。默认非微信平台不运行补丁。

建议在 manifest 工厂的 mp-weixin 内显式设置 `miniprogramRoot: './'`，补丁作为兼容层；若已有值指向其它目录，校验失败，不默默覆盖。

- build 的 writeBundle 可补 `miniprogramRoot`，但**最终权威校验在子进程 close 后进行**。
- 租户构建 JSON 解析或写入失败应抛错，不继续 catch 空操作。
- 默认 dev 输出按实际 UNI_OUTPUT_DIR；保留当前可用的 watcher 时机但不扫描旧 build 目录。

### 5.3 env 与缓存

- 继续 `loadEnv(mode.mode, root)`；外部 VITE 变量自然优先，不将全部 env 回填到全局 process.env。
- 关键字段按规范化配置一次注入；禁止仅 tenant-id 走 define，其它字段各走不同来源。
- `cacheDir` 在 tenant 模式显式指向本次 `cache/vite`，避免 node_modules symlink 落到原根 `.vite`。
- 校验 UNI 派生 .tsc/.uvue/hx/cache 仍在 attempt 所属目录；不接受用户指定任意 outDir。
- 保留 uni 自带 `@` alias/customResolver；不追加会抢先命中的通用 alias。

## 6. 构建脚本流程与故障处理

### 6.1 固定执行顺序

```text
解析参数 → 全量配置预检 → 检查工具/冲突文件
  → 为本次创建 UUID 独占目录与 ownership 标记
  → 复制源码并验证稳定摘要
  → 建立受控依赖链接、环境和路径
  → 生成 manifest 和 src/generated/tenant-build.ts
  → spawn uni build（独立子进程）
  → 等待 close、exitCode=0 且无 signal
  → 校验 manifest / project / app.json / emitted bundle / 资源
  → 生成 build-meta、verification、checksums
  → 目标不存在且同文件系统时，发布到不可变目录
  → 输出准确的 release 路径
```

核心子进程契约示例：

```js
const child = spawn(process.execPath, [
  resolvedUniBin,
  "build",
  "-p", "mp-weixin",
  "--mode", profile,
  "--config", path.join(snapshotRoot, "vite.config.ts"),
], {
  cwd: snapshotRoot,
  env: childEnv,
  shell: false,
  stdio: ["inherit", "pipe", "pipe"],
});
```

`resolvedUniBin` 从原项目的实际 package bin 元数据解析绝对路径，不能凭 cwd 拼裸 `uni` 或调用全局安装；shell=false，不拼接不受控字符串。

### 6.2 成功发布与锁

- 目标路径固定 `dist/tenants/key/profile/deployment/buildId`，不接受任意用户输入目录。
- attempt 与 dist 放同一项目文件系统；目标已存在必须失败。
- 发布用独占锁保护“检查目标不存在→rename”操作，避免目录 merge/覆盖；不维护会被默认改写的 latest symlink。
- 发布父目录以及相对路径逐级检查没有 symlink，防止绕出项目根。
- 同一个 key 可有多个不可变版本；上传必须显式选 buildId，不用“最后目录”猜版本。
- ownership 状态仅允许 CREATED→COPIED→COMPILED→VERIFIED→PUBLISHED，失败记录 FAILED；不能在未验证时生成 PUBLISHED。

### 6.3 中断与残留

- SIGINT/SIGTERM 转发子进程，等待退出，记录失败，释放自身持有的锁。
- 不覆盖旧成功包，不修改原 `.env` 或恢复用户业务文件。
- 构建日志保留在本次目录；默认不自动递归清理任何源目录。
- `.tenant-build` 可以保留失败副本以诊断。清理不是本期必需功能；后续如增加 clean，必须只处理有项目 ownership、非运行状态、无 symlink 的具体候选目录，先列表确认，不触碰 `.workbuddy-ai` 或发布版本。
- 依赖模块出现 native binding/版本不兼容时停止，按受控 Node/pnpm 环境修复；不自动全局安装或改用户工具链。

## 7. 产物身份与验证

### 7.1 真实运行时代码中的构建身份

构建脚本在副本新增 `src/generated/tenant-build.ts`，内容来自唯一规范化对象：

```ts
export const tenantBuildIdentity = Object.freeze({
  schemaVersion: 1,
  key: 'aetheryn',
  tenantId: '1590229800633634816',
  wxAppId: 'wx0a8242ea59f3e6b4',
  name: '悦航购',
  profile: 'production',
  deployment: 'boot',
  openBoot: true,
  apiBaseUrl: '<本次已校验的实际 origin>',
  buildId: '<本次 UUID>',
})
```

示例占位只能出现在本文，实际生成代码必须写真实已校验值。默认源码提供同文件的 `null` 版本，默认开发不依赖租户副本注入。

运行时 identity 模块必须被实际启动/请求守卫 import，不能只是打一个未使用 JSON 进输出再宣称 bundle 配置正确。入口执行关键字段一致性断言：identity 与 import.meta.env 的 tenantId、wxAppId、API、profile、openBoot 均相同。

新增独立 `src/api/core/tenant-build-types.ts`，完整定义 TenantBuildIdentity。默认和生成文件都显式导出 `Readonly<TenantBuildIdentity> | null`，默认赋 null，生成版本赋冻结对象；避免 strict 模式把默认分支推断为 never。消费者先判空再读字段。默认源码和生成副本分别做类型检查，不能用 any 绕过。

```ts
import type { TenantBuildIdentity } from '../api/core/tenant-build-types'
export const tenantBuildIdentity: Readonly<TenantBuildIdentity> | null = null
```

可新增 VITE 字段的显式类型声明提高质量，但不是解决上述默认 null 类型的替代方案。

### 7.2 构建证据插件

`artifact-contract-plugin.mjs` 在 Vite configResolved 固化有效 env，在 generateBundle 建立证据：

- 确认有效 env 等于规范化配置，manifest 与配置一致。
- 从入口及分包入口沿 imports/dynamicImports 建立 emitted chunk 可达关系。模块 ID/renderedLength/renderedExports 仅作为来源辅助，不单独证明代码存活。
- 插件保留本次规范化身份与 configResolved 的 env 校验结果；在最终 emitted JS AST 中验证身份对象或内联常量被启动/发送守卫实际消费。允许模块合并、变量改名、常量内联；不要求保留原变量名。拒绝未使用对象、注释和孤立 buildId 字符串作为证明。
- 记录消费关系、相关 chunk 与最终摘要；verifier 复核文件存在、摘要和规范化身份。若变换导致消费关系无法解析，必须失败并修 verifier 的语法适配，不能退化为字符串命中放行。
- 测试包括“元数据正确、env 错误”“identity 未被 import”“import 后未使用被 tree-shaking”“主/分包含旧注入”反例，以及“常量内联但守卫有效”正例。
- 此证据是构建契约检查，不是数字签名，不能替代真实设备请求验收。P2 CI 对元数据签名可后续加入。

### 7.3 最终 verifier 清单

| 项目 | 必须满足 |
|---|---|
| manifest | 副本 name、mp-weixin.appid/miniprogramRoot 等于规范化值 |
| project.config.json | appid、projectname 正确；compileType 为小程序；miniprogramRoot=`./` |
| app.json | 主包/分包页面存在，无悬空路由；tabBar 所需资源存在 |
| bundle | 编译身份真正可达；有效 env 等于本次配置，无旧租户注入 |
| 资源 | theme.json、静态/组件资源完整；遵守平台包体要求，超限停止上传 |
| 禁止文件 | 私钥、证书、.env、project.private.config.json、源码 symlink 不存在 |
| 追溯 | buildId、key、两种 mode、源码摘要、dirty 状态、lock 摘要、实际工具版本完整 |
| 完成状态 | 子进程非 watch 已 close，退出码 0，无终止信号 |

主包大小等阈值从实际目标平台规则/所用上传工具获取并纳入 CI，不在文档猜一个固定数值。

## 8. 后端登录身份校验（P0-B）

### 8.1 目标和最小接口

不修改 `ArynTenantContextFilter` 的平台管理员语义，不在 common-mybatis 引入 user-biz；新增能力走现有 API/Dubbo 边界。

新增文件与修改点：

```text
aryn-user/aryn-user-api/.../dto/MiniAppTenantBindingDTO.java
aryn-user/aryn-user-api/.../remote/RemoteMiniAppTenantService.java
aryn-user/aryn-user-api/.../dto/UserLoginReqDTO.java           # 加 requestTenantId
aryn-user/aryn-user-biz/.../dubbo/RemoteMiniAppTenantServiceImpl.java
aryn-user/aryn-user-biz/.../service/MiniAppBindingResolver.java
aryn-user/aryn-user-biz/.../mapper/SocialAccountMapper.java + xml
aryn-user/aryn-user-biz/.../service/impl/SocialAccountServiceImpl.java
aryn-user/aryn-user-biz/.../dubbo/RemoteSocialUserServiceImpl.java
aryn-auth/.../service/LoginTenantGuard.java
aryn-auth/.../service/TocLoginService.java
aryn-auth/.../controller/TocTokenController.java
```

RPC 契约：

```java
MiniAppTenantBindingDTO requireBinding(String appId, String expectedTenantId);
```

DTO 为 Serializable，仅返回 appId、tenantId、accountId、配置 updateTime/指纹标识、ready=true；不返回 secret、手机号、微信凭证。内部配置指纹用于一致性，不暴露秘钥衍生摘要到公开 API。

### 8.2 权威解析与跨实例配置

`MiniAppBindingResolver` 使用新增、不带 Spring @Cacheable 的权威 SQL：

```sql
SELECT sa.*
FROM social_account AS sa
WHERE sa.app_id = #{appId}
  AND sa.type = 'WX_MA'
  AND sa.del_flag = '0'
```

mapper 方法仅这一受控查询使用 `@InterceptorIgnore(tenantLine = "true")`，参数绑定，返回 List：

- 0 条：拒绝不存在配置；2 条或以上：拒绝重复映射，不选第一条。
- 1 条：tenantId 必须严格等于 expectedTenantId；secret 非空。
- 不自动纠正请求头为另一个租户。错配直接抛 `ArynBusinessException(403, "小程序与租户配置不一致")`。

为了不把多实例广播变成本期不可落地前置项，**本期选择每次绑定/微信登录权威查库，并同步刷新当前消费实例配置**，而不是信任旧缓存：

1. 权威记录校验成功后，将完整记录更新到当前 WxMiniAppConfigCache；缓存保存独立对象，避免外部原地改字段。
2. 在 `RemoteSocialUserServiceImpl.socialLogin/getPhoneNumberInfo` 内再次执行同解析，消费的是该次解析得到的配置快照。
3. 新增 `WxMiniAppConfiguration.createMaService(SocialAccount validatedConfig)` 或等价工厂，用该快照构造 SDK；guard 与实际微信 RPC 不依赖路由到同一 user 实例。
4. 对 accountId、tenantId、AppID、secret/updateTime 做内部比较并刷新，secret 仅在内存比较，绝不日志/返回。
5. 配置在 guard 后变更时，消费端重查并拒绝不一致；不保证跨远程微信调用和 DB 更新的全局原子性，因此禁止营业期间直接迁移 AppID 所属租户。

这样直接 SQL 修改也能在下一次正式解析后被看到；依然要求运维通过后台维护，避免绕过校验。已有 AppID 查询缓存不能用于此安全判定。

后台账号维护补强：

- 保存/修改只允许当前合法租户上下文，不能信任 body.tenantId 跨租户写。
- 新增也执行 Spring 缓存失效；更新/删除维持失效。
- 事务成功提交后刷新/移除本实例 SDK 缓存，事务回滚不得遗留“已更新”缓存。
- 账号维护做全局 AppID 重复预检；并发竞争的最终保障如需数据库唯一约束，作为独立双模式迁移，详见第 10 节。
- 即使未加索引，Resolver 也在重复情况下 fail-closed，不因唯一性假设失效而错选租户。
- WxMiniAppConfigLoader 改为 try/finally 恢复线程上下文，只加载 WX_MA、有效记录，重复 AppID 不相互覆盖，启动记录明确错误。

本期接口的 ready 仅代表“权威映射和本实例 SDK 输入齐备”，**不代表微信 secret 已远程验证或支付已开通**。

### 8.3 请求字段来源

UserLoginReqDTO 新增 `requestTenantId`。Controller 在每个登录入口无条件覆盖：

```java
userLoginReqDTO.setRequestTenantId(request.getHeader(CommonConstants.TENANT_ID));
userLoginReqDTO.setAppId(request.getHeader(MallCommonConstants.HEADER_APP_ID));
```

禁止拿当前 ThreadLocal 值当原始请求头，因为有 token 时过滤器会改用会话租户。禁止 body 提供的 requestTenantId 绕过校验。

- `/ma/login`、`/ma/phone/login` 固定要求 WX_MA 语义，平台缺失/错误直接拒绝。
- `/sms/login`、`/password/login` 对合法 WX_MA 执行 guard；H5/APP 保留既有租户登录分支，平台非法拒绝。
- AppID 看起来是微信但平台声称 H5/APP 的矛盾请求拒绝；不能因为省略 header 就绕过 WX_MA 分支。
- 不把 platform header 本身当不可伪造身份；真正微信登录仍必须验证 jsCode/手机号授权凭证。

### 8.4 guard 和流程顺序

`LoginTenantGuard` 通过 `@DubboReference RemoteMiniAppTenantService` 取得最小绑定 DTO。只依赖 user-api，不引用 user-biz。

所有微信登录流程统一：

```text
格式/平台校验
 → 权威绑定校验（tenantId 严格一致）
 → 快照进入前 ThreadLocal，设置已验证 tenantId
 → 解密手机号 / 查用户 / socialLogin 等原业务
 → 检查返回 socialUser、userInfo 的租户与已验证 tenantId 一致
 → 绑定商城用户与三方用户
 → 使用已验证 tenantId 签发 token
 → finally 还原进入前 ThreadLocal
```

特别修改 maPhoneLogin、smsLogin、passwordLogin：**任何查商城用户、创建用户、调用手机号解密之前，先验证绑定。** 不能在查到 A 的用户后再切换为 B 的租户。

socialLogin/getPhoneNumberInfo 也要求 requestTenantId，不再“按 AppID 查到什么租户就切什么租户”。maLogin 的返回租户需与 guard 一致。

`bindUserId` 增强校验：socialUser 和待绑定 mallUser 属于同一已验证租户；上下文正确时不得为解决历史问题无条件跨租户绑定。受控跨租户主键反查如仍保留，必须校验返回记录与 expectedTenantId 一致。

ThreadLocal 恢复适配 boot injvm/cloud RPC，保持现有 ArynDubboRequestFilter 的调用方上下文恢复语义；不复制旧注释中“所有调用都会清空”的历史结论。

### 8.5 新增两个前端可用端点

1. `GET /auth/toc-token/tenant-binding`（前端 cloud 路径）
   - Controller 实际 `@GetMapping("/tenant-binding")`，boot 为 `/boot/toc-token/tenant-binding`。
   - 匿名，读取 app-id、tenant-id、platform-type，复用权威绑定解析。
   - 公开返回仅 `{ appId, tenantId, ready: true }`，不暴露 accountId/updateTime/secret。
   - 反滥用限流复用现有网关/安全设施；RPC/DB 超时失败，不把异常当 ready。

2. `GET /auth/toc-token/tenant-session?scope=mall|delivery`
   - scope 必填、仅接受 mall/delivery；Controller 显式检查登录，因为 `/toc-token/**` 目前在免登白名单中。
   - preflight 显式传本次待验证 token，不从共享默认 header 猜 token。
   - 使用现有 SecurityUtils.requireUser/requireDevice 核对实际会话：mall 必须 TOC，delivery 必须 TOB；读取会话租户严格比对原始 tenant-id 头。设备或租户错配返回业务码 403。
   - 返回 `{ tenantId }`，无 userId 等内部字段；session 缓存按 scope+当前 token 分开。
   - 无 token、过期 token、会话用户缺失在本端点显式转换为 ArynBusinessException(401, ...)，不能依赖当前 NotLoginException 默认处理器（其默认失败码不保证是401）。
   - TOB 一致不等于配送资格。管理端 TOB 不因通过 session 而获配送权限；既有 DeliveryAccessGuard 保留。不新增平台管理员在 C 端切租户例外。

保持业务码 403 与登录态 401 分开：403 只提示配置错误，不自动清 token 或跳登录。RPC 超时按服务暂不可用处理，不回落到错误租户。

白名单检查：boot application.yml、cloud auth/gateway Nacos 均核实公开 binding 可用；**session 即使路径被白名单放行，Controller 也必须手工 requireLogin**。不为两个接口扩大其它路径公开范围。

## 9. 前端运行时身份守卫（P0-B）

### 9.1 文件与职责

```text
src/generated/tenant-build.ts          # 默认 null；租户副本生成真实对象
src/api/core/tenant-build-types.ts      # 默认/生成文件共享严格类型
src/api/core/tenant-identity.ts         # 本地纯校验、共享 Promise、绑定/会话协议
src/api/core/tenant-preflight.ts        # 仅原始 preflight 传输，不 import alova/store
src/api/core/instance.ts               # 所有业务请求 await 启动守卫
src/main.ts / src/App.vue              # 最早启动检查，错误 UI/重试
src/api/upms/file.ts                   # uni.uploadFile 前守卫
src/sub-pages/api/order/sharedCartImport.ts
src/sub-pages/order/order-appraise/index.vue
src/store/messageStore.ts             # async 建连前守卫
src/store/authStore.ts                 # 存 token 后验证会话，再拉用户/购物车
src/api/delivery.ts                    # 配送登录 token 保存前后对应验证
src/pages/delivery/login.vue           # 配送进入工作台前会话确认
src/pages/user/user-center/index.vue    # 商城换配送身份的第二入口，不可漏
src/sub-pages/utils/pay.ts             # requestPayment 前守卫
```

业务页面不改租户变量名；有修改的是身份入口和旁路传输，不再声称“所有业务代码零改动”。其它直接 uni.request/下载/支付入口通过定向搜索核对，第三方非商城服务不无差别添加 tenant-id。

### 9.2 无循环依赖的启动流程

租户模式由 generated identity 非 null 判定；正式租户包不能通过可编辑 VITE 开关关闭守卫。

`tenant-identity` 不 import alova、Pinia 或业务 API，避免 instance→guard→API→instance 循环。preflight 使用单独 `uni.request` adapter：

- URL 复用 `buildApiUrl` 与 `rewriteBootUrl`，禁止手拼 `/boot`。
- binding 不携带 token；原始 adapter 只支持两个固定 preflight 路径，不暴露任意“跳过守卫”开关。
- 检查 HTTP 状态、平台 Result 成功码 0、payload 字段和 appId/tenantId，超时/格式错误拒绝。
- 错误只返回类型化错误给启动界面，避免直接复用会清 token 的全局 handler。
- shared Promise 去重，失败后的用户重试必须创建新 Promise；没有持久化永久 ready=true。

启动本地检查同步执行：

1. generated identity 与 import.meta.env 完全一致。
2. `MP-WEIXIN` 下读取真实 miniProgram.appId，等于包内 wxAppId。
3. 成功后发 binding preflight，映射相同、ready=true 才放行业务请求。
4. 若有持久化商城/配送 token，在其首次使用前发 tenant-session，确认会话租户。

`main.ts`/App 初始化会触发 theme、cart 等并发请求，因此安全边界必须在 request/send/上传/WebSocket 入口，不能只在异步 onLaunch 做一次检查然后假设顺序可靠。

### 9.3 状态机和失败行为

```text
idle → local-check → binding-check → ready
                ↘ blocked
ready → session-check（每个当前 token 首次使用）→ session-ready
                             ↘ blocked
blocked → 用户重试 → local-check
session-check → 401 → scope-auth-required（仅对应旧 token 失效，可重新登录）
```

- 配置 blocked：停止登录、商城数据请求、上传、WebSocket、支付，显示“应用配置暂不可用，请联系管理员”，附 buildId/错误分类；不显示 secret 或内部账号信息。
- 网络故障重试，不缓存为永久错误，不伪装游客放行。
- preflight 错误带 endpoint、scope、校验 token 和分类。binding 的401/403不清任何 token；session 的401仅由认证边界清理对应 scope 的旧失效态和缓存、中止原带 token 请求。binding 已通过则允许重新登录，不进入永久配置 blocked。
- 清失效态前比较当前 token 仍等于本次校验 token，防止迟到401删除新登录态。商城/配送清理独立，不能使用当前 clearAuthData 一次清两边代替 scope 清理。
- 配置/设备/会话租户错配403保留 token 但阻断使用，不自动换取另一 token；网络/服务不可用保留 token 并允许重试。用户明确选择退出后按既有局部登出流程清理。
- 登录成功先取得 token，验证其会话 tenantId，再设可用登录状态及拉用户/购物车。`fetchUserInfoAfterLogin()` 的原吞错逻辑不得吞掉身份校验错误。
- token 更新/退出使 session-check 缓存失效；商城与配送分开缓存，不能把 A token 的通过结果用于 B token。
- 回前台刷新 binding 采用共享节流（建议 5 分钟）和重新检验当前会话；前台阶段锁住业务发送直到需要的校验完成，避免撤销配置后仍一直使用旧 ready。
- 默认 `dev:*` generated=null，不强制调用新 preflight，保持现有开发链路；可通过专门集成测试入口模拟身份。H5/App 的多租户发布不是本期范围，不伪装已完成。

### 9.4 旁路传输与配送入口兼容契约

- `uploadFile` 保持 `Promise<UploadResponse>`，`uploadDeliveryEvidence` 保持 `Promise<string>`；真正 uni.uploadFile 之前 await 守卫，失败 reject，不能挂起 Promise。
- `uploadImg` 保持返回 Alova Method，其守卫在支持 async 的 beforeRequest 中执行；不得改成 Promise<Method> 或自动提前发送。
- 配送上传当前401/403都清登录态，实施时改为仅401清配送scope；403保留token、拒绝上传，不跳登录。普通403与身份配置403的错误类别分别保留。
- messageStore.connect 改为异步并调整调用方为正确 await/void；避免心跳/重连创建未处理拒绝，守卫失败不得创建 socket。
- 同时覆盖独立配送登录页与商城个人中心 `saveDeliveryAuthAndEnter`、`exchangeAndEnter`、`enterDeliveryWorkspace`。候选token先delivery session校验，再获取资料/进入；错误不得被空catch降级为保存空资料并导航。
- 旧配送token探活先分类，仅401清理/重新换取；403不清、不自动换取；网络或配置错误保留token并停止进入。
- 原await/then/Method调用方式保持，新增“守卫失败零传输、零导航、无登录成功提示”测试。

前端校验防误配，**不属于不可绕过的服务端安全边界**；匿名店铺信息原本公开，客户端头部可伪造。token 与业务权限隔离仍由服务端负责。

## 10. 数据库、配置与后台接入

### 10.1 本期必需数据库变更

本期构建/登录硬化不强制新增表或字段：复用 social_account、现有 tenant/用户字段，不虚构 version/status 字段。新增 mapper SQL 不是数据库结构迁移。

新增公开端点如当前白名单已覆盖，无需扩大配置；若 cloud Nacos 需实际更新，提供可执行、幂等的配置增量，并确保 boot 同等行为。

### 10.2 可选 AppID 唯一约束

本期 Resolver 已在重复时拒绝，后台做重复预检。若要数据库彻底阻止竞争：

- 先确认生产 MySQL 版本、social_account 当前索引和逻辑删除历史；不要直接套 `(app_id, del_flag)` 唯一索引限制多条历史删除记录。
- 可选使用“仅有效 WX_MA 返回 AppID，其他返回 NULL”的生成列唯一索引，前提是实际 DB 支持。
- 先执行重复预检，发现重复由人工确认修复；禁止 DROP/TRUNCATE/物理删除存量记录。
- boot/cloud 分别新增编号递增的幂等 SQL；编号在实施当天按目录最大编号确定，不用本文件日期推测。
- boot 登记 build-full-sql sections、重新生成 aryn_boot_full.sql 并 verify；cloud 同步基线与增量。
- 这一项是独立迁移，未执行不得声称数据库唯一性已保证。guard 的 fail-closed 不能删除。

### 10.3 新租户开通清单

| 顺序 | 执行者 | 操作 | 验收证据 |
|---|---|---|---|
| 1 | 平台运营 | 后台租户开通，不直接只插 sys_tenant | 管理员、角色、菜单、套餐/有效期正常 |
| 2 | 租户管理员 | 配 WX_MA 三方账号 | tenantId/AppID 正确且不重复 |
| 3 | 部署负责人 | boot/cloud 目标 API 与白名单、证书、域名 | binding 两种部署路径可达 |
| 4 | 微信管理员 | 应用主体/体验成员/实际域名配置 | 真机请求与登录通过 |
| 5 | 支付管理员 | pay_config、商户/AppID 关联、证书路径与回调 | 测试订单、查询、回调及退款链路正确 |
| 6 | 租户管理员 | 订单、商品、库存、装修、履约/消息等 | 首屏、加购、下单及目标业务可用 |
| 7 | 前端维护者 | 增加 tenants/<key>.json，填真实 origin | dry-run + 四组合验收记录 |
| 8 | 发布负责人 | 选择 VERIFIED buildId 并灰度 | 版本和租户追溯完整 |

AppSecret、支付私钥绝不写进 tenants JSON、generated identity、build-meta 或日志。

## 11. 文件级改动总表

### 11.1 C 端

| 文件/范围 | 动作 | 完成定义 |
|---|---|---|
| tenants/aetheryn.json、_template.json | 新增 | 规范结构、真实身份；未配置 origin 明确阻断 |
| build/manifest-factory.mjs | 新增 | 完整静态配置迁移、纯工厂、无 helper 副作用 |
| manifest.config.ts | 修改 | 薄包装，同工厂；严格区分默认/租户 |
| build/tenant-config.mjs、build-context.mjs | 新增 | 强校验、封闭 env、唯一绝对路径 |
| build/snapshot.mjs | 新增 | 白名单复制、摘要一致、无源码 symlink |
| build/pages-options.mjs | 新增 | 预生成与插件同参数、固定配置根 |
| vite.config.ts | 修改 | pages 屏障、条件 helper import、当前 outDir 补丁、缓存隔离 |
| build/artifact-contract-plugin.mjs、artifact-verifier.mjs | 新增 | 有效 env 与真实 bundle/资源校验 |
| scripts/build-tenant.mjs、run-uni.mjs、verify-tenant.mjs | 新增 | 流程编排、参数、状态/中断、默认命令兼容 |
| package.json | 修改 | 新脚本；旧 dev/build 名称保留、包装器覆盖 uni 入口 |
| src/generated/tenant-build.ts | 新增 | 默认 null，副本生成真实身份 |
| src/api/core/tenant-build-types.ts、tenant-identity.ts、tenant-preflight.ts | 新增 | 严格类型、无循环依赖，local/binding/session 守卫 |
| request/upload/import/appraise/socket/login/pay 入口 | 修改 | 旁路无绕过、返回契约保持、身份错误不被吞掉 |
| src/pages/user/user-center/index.vue | 修改 | 商城换配送身份的session确认、分类catch、阻止错误导航 |
| src/main.ts、App.vue | 修改 | 最早初始化与失败/重试界面 |
| src/api/core/*.test.ts、认证/旁路测试 | 新增/修改 | 状态机、双模式、错配拒绝、401/403 分离 |
| tests/build/*.test.mjs | 新增 | 不依赖业务 Vitest include；原生 node:test |
| 根 .gitignore | 修改 | 增加 aryn-mall-uniapp/.tenant-build/、tenants/.private/；dist 已忽略 |
| pnpm-lock.yaml | 条件修改 | 仅有实际依赖调整时更新；不混入无关 install 漂移 |

后续纯品牌任务可涉及 pages 全局标题、登录 logo/版权、配送品牌名称，不混进 P0 登录隔离补丁。

### 11.2 后端

| 模块/类 | 完成定义 |
|---|---|
| user-api 的 MiniAppTenantBindingDTO/RemoteMiniAppTenantService | 最小序列化 RPC 契约 |
| UserLoginReqDTO | 加 requestTenantId，不采信客户端 body |
| user-biz 的 MiniAppBindingResolver/RemoteMiniAppTenantServiceImpl | 权威查询、重复拒绝、实例配置刷新 |
| SocialAccountMapper + XML | 全局受控参数化查询，明确别名，最小绕过范围 |
| WxMiniAppConfiguration/ConfigCache/ConfigLoader | 权威快照创建 SDK、有效类型过滤、上下文恢复 |
| SocialAccountServiceImpl | 后台租户字段约束、重复预检、提交后缓存一致 |
| RemoteSocialUserServiceImpl | 微信消费前重查；绑定双方租户一致 |
| auth 的 LoginTenantGuard/TocLoginService | 四登录入口先 guard 后用户查询，finally 恢复 |
| TocTokenController | 原 header 覆盖、平台校验、binding/session 端点 |
| auth/user/boot 测试 | 顺序、身份、RPC/injvm、免登与会话契约 |
| boot/cloud 配置和 SQL | 仅实际需要时修改，遵守双模式增量规则 |

## 12. 测试设计

### 12.1 构建单测：`pnpm test:build`

package 脚本建议 `node --test tests/build/*.test.mjs`，测试至少包括：

1. JSON number tenantId、空字段、非法 AppID、未知字段、模板/路径穿越拒绝。
2. all 重复 tenantId/AppID 在任何构建前失败；排序、首失败停止。
3. profile/deployment 组合确定 env，`false` 不变成 truthy；父进程 VITE/UNI 残留无效。
4. 副本只有白名单、源码 symlink/冲突 project 文件拒绝、源编辑期间失败。
5. 工厂保留完整原 manifest，A→B→A 不串值；uni spawn 前文件已完成。
6. 原根 manifest/pages/env/node_modules 缓存不因租户构建而变更。
7. 子进程失败/信号/日志流失败、VERIFY 失败、目标已存在均不发布。
8. identity 未被 import、有效 env 错误、project AppID 错误、私钥误入均不通过。
9. 默认 wrapper 保留命令参数、主动默认 manifest 预生成。
10. 输出路径/缓存路径不得越界，锁释放与独占发布正确。

用 fake CLI 在测试专属临时目录验证故障流程；不替代下面真实 uni 构建。

### 12.2 前端单测：`pnpm test:unit`

- identity 对 env/AppID 比对，错误本地立即拒绝，不先打业务 API。
- binding 成功/错配/未知/网络失败/错误 Result code，以及重试去重。
- 并发 theme/cart 在 bootstrap 期间不能提前发出；preflight 不递归进入 alova。
- session 错租户阻断，token 变化重新校验，配送与商城隔离。
- 登录 guard 错误不能被 fetchUserInfoAfterLogin 吞掉；商城换配送的空catch不允许吞错后导航。
- session(scope=mall|delivery)设备与token明确分离；迟到401不得删除新的token。
- 上传、下载、消息、评价上传、支付入口全部 await 守卫；上传原Promise/Alova Method接口保持。
- 401 走既有失效语义；403 不循环清 token 跳登录，但请求仍阻断。
- default generated=null 保持原开发流程；H5/App 不误调用微信 getAccountInfo。
- boot/cloud 路径覆盖 `/auth/toc-token/tenant-binding`、session、shop-info、文件、消息。

### 12.3 后端单测与契约测试

- 错配/重复/未知 AppID 时 verifyNoInteractions：手机号解密、商城用户查询/创建、绑定、签发 token 均未发生。
- Controller 强制覆盖 requestTenantId/appId/platform；body 不能绕过。
- maPhone/sms/password/maLogin 四流程一致；H5/APP 既有分支保留。
- RPC 返回租户与请求相同，social/mallUser 任一返回错租户时拒绝绑定/token。
- binding 权威查询不读 Spring 查询缓存；实际消费实例 stale cache 能刷新。
- 配置删除/改 secret/重复时明确行为；事务回滚不更新缓存。
- guard finally 恢复原 ThreadLocal，成功/异常，boot injvm/cloud provider 均覆盖。
- tenant-session 被免登白名单覆盖时仍手工校验登录，无 token 401、错租户403。
- 公共端点白名单与 C 端权限注解守门测试通过，不新增管理端权限给 C 端。
- 不跨 biz import；mapper 租户别名审计与现有隔离守门测试不回退。

### 12.4 两租户×环境模式×部署模式矩阵

测试身份 A/B 必须 tenantId、AppID、name 不同。单元测试可用 `.invalid`/模拟数据，但**实际联调矩阵必须使用真实配置**；B 未提供时写“阻塞”，不伪装通过。

| 租户 | profile | 部署 | 验证要求 |
|---|---|---|---|
| A | staging | boot | 真实构建、bundle注入、完整请求/登录/上传/消息/支付联调 |
| A | staging | cloud | 同上，网关白名单/Dubbo/服务域正确 |
| B | staging | boot | 同上，与A身份/数据严格不同 |
| B | staging | cloud | 同上，不复用A AppID或支付配置 |
| A | production | boot | 最终VERIFIED包、真实origin，binding/session与非破坏性冒烟 |
| A | production | cloud | 同上，实际生产Nacos/网关/RPC核对 |
| B | production | boot | 同上，按B真实身份确认 |
| B | production | cloud | 同上，按B真实配置确认 |

- staging做完整业务联调；每个拟上线production组合必须用最终包和真实origin验收，不能拿staging证据替代。
- 未部署组合标为“非本次上线范围”，不能写“通过”；计划上线但缺配置的组合标为“阻塞”。系统双模式能力至少由两租户staging的四组合证明。
- cloud记录实际namespace、group、data_id与生效内容；仓库dev基线不等于生产配置已覆盖。前端profile和后端Nacos profile分别记录。
- production支付冒烟需授权、隔离测试商品/账户，禁止自动批量真实扣款；无法安全验收则保留阻塞。

另外执行 A→B→A 和构建期间默认 dev：原目录生成物未被租户编译改写；默认 dev 仍用 `.env`，无上一租户名称/AppID。

### 12.5 实施后的验证命令

下列前端命令从 `aryn-mall-uniapp` 执行：

```bash
pnpm test:build
pnpm type-check
pnpm test:unit
pnpm build:tenant aetheryn --profile staging --deployment boot
pnpm build:tenant aetheryn --profile staging --deployment cloud
pnpm build:tenant aetheryn --profile production --deployment boot
pnpm build:tenant aetheryn --profile production --deployment cloud
pnpm dev:mp-weixin
```

production命令仅在对应组合真实配置已填写且在本次上线范围时执行；第二租户按其真实key重复对应验收。

后端从 `aryn-mall-java` 执行：

```bash
mvn clean compile -pl aryn-boot -am
mvn test -pl aryn-boot -am
```

cloud 另做实际 auth/user 服务编译部署与 RPC 联调，boot 编译不代表 cloud 环境已验收。当前工作树/基线可能已有失败；必须区分既有失败与新增回归，不为赶验收删除测试/跳过钩子。

执行环境采用项目受控 Node≥22、pnpm 9.x 与锁文件；Agent 在本机调用 Node 时使用可用受控绝对路径，不改全局工具环境。

## 13. 逐步实施与完成定义

### Step 1：基线与范围确认

- 保存当前 git status 和目标文件差异，识别用户正在修改 App/auth/request 的内容；不覆盖无关修改。
- 读取项目指南，租户/认证/共享请求变更优先影响图谱；工具不可用时定向源码搜索，记录限制。
- 先跑可靠基线测试，保存失败列表；确认 A/B 真实身份与四组合域名的负责人。
- 完成定义：有基线证据、文件范围明确，缺少真实配置被单独标记。

### Step 2：配置与纯工厂

- 实现 schema 校验、命令参数，抽取完整 manifest 工厂和默认薄包装。
- 添加模板与 A 配置；未知生产地址空值拒绝。
- 完成定义：配置/factory 单测通过，原所有平台 manifest 静态字段无误删。

### Step 3：副本、预生成、CLI 与缓存

- 实现白名单复制、摘要、受控 env、manifest/identity 生成、pages 屏障、Vite 条件插件与缓存。
- 实现默认 wrapper，旧命令名不改。
- 完成定义：模拟故障测试通过；非默认身份真实 uni 编译能正确产物，原工作区无租户构建污染。

### Step 4：产物校验与不可变发布

- 实现 emitted 契约证据、verifier、状态/摘要、原子发布。
- 完成定义：任何输入/产物错配无法 PUBLISHED，A/B 候选包互不覆盖。

### Step 5：后端权威解析与登录顺序

- user-api RPC、user-biz 权威 resolver、SDK 快照、提交后缓存维护。
- auth guard、四登录流程、两 preflight 端点与白名单契约。
- 完成定义：错误绑定无用户副作用，已有 H5/App/配送行为无回归，boot 编译测试和 cloud RPC 都有证据。

### Step 6：前端身份守卫与旁路覆盖

- local/binding/session 状态机、登录后身份确认、文件/消息/支付入口。
- 完成定义：bootstrap 前无业务请求泄漏，网络失败可重试，403 无登录循环。

### Step 7：联调与上线前验收

- 完成四组合、四登录分支、商品/订单/上传/消息与支付回调。
- 演练 A 包配置 B AppID、A 头+B token、B token+其它用户 ID 等错配拒绝。
- 完成定义：矩阵全绿，有可追溯 buildId；未完成项不发布。

### Step 8：灰度与后续上传自动化

- 后端能力先部署，再生成新候选包；选择 A 体验版，再 B 体验版，确认后分租户发布。
- 先手工选不可变产物与正确 AppID，P2 再接上传自动化。
- 完成定义：回滚演练通过，监控能按 tenant/buildId 定位，不自动修改生产数据。

## 14. 发布、监控与回滚

### 14.1 发布顺序

1. 先上线兼容旧正确配置客户端的后端 guard/RPC/endpoint。旧包只要 tenantId/AppID 正确，仍能登录。
2. 先部署 user-api/user-biz，再部署 auth；boot 同包升级。若存在混合版本，旧 provider 缺 RPC 方法时新 auth 必须失败关闭，不能 fallback。
3. 后端冒烟 binding/session 和旧 A 包登录。
4. 构建 VERIFIED A、B 候选包，体验版分别联调，再人工决定正式发布。
5. 若通过 P2 上传，上传前再 verifier；上传目标 AppID 必须等于 build-meta 与 project.config。

### 14.2 监控

日志/事件字段：tenantKey（前端）、tenantId、appId、buildId、profile、deployment、失败类型、traceId；不记录 token/密码/jsCode/secret/支付密钥。

重点监控：绑定错配、未知/重复 AppID、SDK 配置缺失、session 错配、bootstrap 失败率、登录失败率、上传/消息异常、支付回调归属错误。告警阈值由正式环境基线制定，不在文档伪造数值。

前端错误页可以显示 buildId，用户上报时可追溯具体包；记录 sourceHash/configHash 的映射在包外构建元数据。

### 14.3 回滚

- 小程序回滚到**同租户、同 AppID** 的上一 VERIFIED/PUBLISHED 版本，绝不借用其它租户包。
- 后端 guard 不为了排障关闭错配检查。RPC/配置故障优先回滚兼容的 auth/user 整体版本或修复配置，不能单独留下新 auth 调旧 provider。
- 若旧后端不具备必需身份防护，则停止新租户发布/暂停受影响登录，不能宣称安全回滚完成。
- 配置/secret 回滚后，权威 resolver 在实际消费实例重新加载；核对所有实例和微信回调。
- 不在回滚中物理删除订单、用户、social_user 或迁移 AppID 所属租户。错误身份绑定若已发生，单独审计与人工修复，保留交易记录。
- 候选产物失败不会破坏上一版本；日志与副本保留以诊断。

## 15. P1 品牌与 P2 自动上传

### 15.1 P1 品牌

name 仅用于工程/编译身份，不自动修改微信平台审核名称。登录静态 logo、版权、配送“悦航购配送”、pages 全局 Aryn Mall 标题仍需产品决定是否租户化。

如需要独立品牌，在 schemaVersion 2 中增加受控 brand 配置/本地资源白名单，并扩展快照与资源验证；不把远程任意资源 URL 当作能保证包内品牌的配置。后台装修/主题复用已有租户接口。

### 15.2 P2 上传接口契约

```bash
pnpm upload:tenant --artifact /absolute/release/path --dry-run
pnpm upload:tenant --artifact /absolute/release/path
```

- 工具版本从官方说明确认后锁定；实际上传参数以该版本 API 为准，本方案不预设未经验证的 SDK 调用。
- 私钥统一 `tenants/.private/<key>.key`；根 gitignore 精确覆盖。CI 通过 secret 创建临时密钥、最小权限，不进入副本/产物/日志。
- 先验证 release 的 appId/key/version，再读取同 key 私钥；不按最新文件猜租户。
- 上传前确认目标应用和版本，支持 dry-run；实际上传、提审、发布分别记录结果，不把上传成功当已发布。
- 平台各 AppID 的审核/发布流程按实际账号权限和最新规则执行，不用“批量上传”掩盖独立应用流程。
- 凭证管理和平台授权由负责人完成；不能把 AppSecret 当上传私钥。

## 16. 最终上线检查单

- [ ] 原 uniapp 开发命令名和 `.env` 默认租户行为保留。
- [ ] 租户构建完全在独立副本，无原 manifest/pages/env/缓存写入。
- [ ] manifest 在 uni CLI 启动前生成；pages 在依赖它的插件创建前生成。
- [ ] 配置错误、工具失败、信号中断、产物错误均不发布。
- [ ] build identity 实际进入运行时代码；env/project/资源与元数据一致。
- [ ] production/staging、boot/cloud 两维没有被混用。
- [ ] 四微信登录入口在任何用户副作用前校验 AppID/租户。
- [ ] 权威查库+实际消费实例 SDK 快照，不依赖缓存路由碰巧一致。
- [ ] binding 匿名正确，session 即使路径免登也强制登录。
- [ ] 前端 local/binding/session 与旁路请求无绕过，无 Promise 循环。
- [ ] 401/403 区分；身份校验失败不被登录后吞错逻辑吃掉。
- [ ] 两个真实租户staging各自boot/cloud联调通过；拟上线production组合用最终VERIFIED包和实际配置验收，未部署组合不声称通过。
- [ ] session的401仅清对应旧scope，403/网络故障不清token；商城换配送入口不能吞守卫错误后继续导航。
- [ ] 支付/订单/回调/基础业务配置按租户完成，正确包不等于正确营业。
- [ ] 私钥、secret、token 不入配置/包/日志/Git。
- [ ] 后端先升级、小程序按租户灰度、回滚演练有证据。

## 17. 交付边界与仍需提供的信息

本文已确定代码实现路线、文件职责、参数契约、错误处理与验收标准，不需要实施者重新选择核心架构。

目前无法从仓库可信获得、须负责人提供的仅是部署和业务事实：

1. A/B 每个目标 profile/deployment 的真实 HTTPS origin。
2. 第二业务租户的真实 tenantId、AppID 与后台开通记录（不能拿系统租户代替真实业务租户）。
3. 每租户微信平台、商户/支付、回调与业务初始化配置。
4. CI 的实际运行环境、源码提交、后续上传权限/secret 管理。

这些缺失不会阻碍构建/认证逻辑实现和单测，但会阻塞真实联调与正式上线。不得为“看起来能跑”填假生产地址、共享旧 AppID 或跳过测试。
