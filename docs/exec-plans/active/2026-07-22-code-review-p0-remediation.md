# Code Review P0 Remediation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复经当前代码核验成立的代码审查 P0 问题，并记录重复项、已修复项和误报。

**Architecture:** 保持三项目既有分层，不引入跨 biz 直接依赖。安全校验放在各自边界：上传输入在 UPMS 控制器前校验，CORS 策略位于共享安全属性并由 Boot/Gateway 消费，前端协议修复使用各项目内的纯函数与现有 store/API 层。

**Tech Stack:** Spring Boot 3、Sa-Token、JUnit 5、Mockito、Vue 3、Pinia、TypeScript、Vitest、UniApp、Alova。

**Status:** 已完成。完整后端 Reactor 已通过，详见 Task 6。

---

### Task 1: 修复系统用户密码与管理端登录传输

**Files:**
- Create: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/controller/SysUserControllerPasswordTest.java`
- Create: `aryn-mall-java/aryn-auth/src/test/java/com/aryn/cloud/auth/controller/TobTokenControllerTest.java`
- Modify: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/controller/SysUserController.java`
- Modify: `aryn-mall-java/aryn-auth/src/main/java/com/aryn/cloud/auth/controller/TobTokenController.java`
- Modify: `aryn-mall-java/aryn-boot/src/main/resources/application-dev.yml`
- Modify: `aryn-mall-java/aryn-boot/src/main/resources/application-prod.yml`
- Create: `aryn-mall-ui/apps/web-ele/src/views/_core/authentication/login-security.test.ts`
- Modify: `aryn-mall-ui/apps/web-ele/src/store/auth.ts`
- Modify: `aryn-mall-ui/apps/web-ele/.env`
- Modify: `aryn-mall-ui/apps/web-ele/.env.production`
- Delete: `aryn-mall-ui/apps/web-ele/src/utils/aes.ts`

**Steps:**
1. 写失败测试，证明旧密码参数顺序错误、新密码未写入，以及 Tob 登录仍要求固定 AES 解密。
2. 运行两个 Java 定向测试，确认按预期失败。
3. 修复 BCrypt 调用；Tob 登录直接把 HTTPS 请求中的密码交给现有 `LoginService`。
4. 写并运行管理端安全契约测试，禁止测试账号、自动填充和登录 AES 密钥回归。
5. 删除管理端登录 AES 调用、公开密钥配置和未使用工具。
6. 运行 Java 与 Vitest 定向测试确认通过。

### Task 2: 加固文件上传与本地预览

**Files:**
- Create: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/support/UploadFileValidator.java`
- Create: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/support/LocalFilePathResolver.java`
- Create: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/support/UploadFileValidatorTest.java`
- Create: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/support/LocalFilePathResolverTest.java`
- Modify: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/controller/SysUploadController.java`

**Steps:**
1. 写失败测试覆盖空文件、超限、伪造扩展名、SVG/HTML、合法 PNG/JPEG 和路径穿越。
2. 运行 UPMS 定向测试确认失败。
3. 实现 10MB 图片文件头白名单与规范化路径解析。
4. 控制器接入校验，管理端上传增加 `upms:material:add`，预览恢复原租户上下文并统一业务异常。
5. 运行 UPMS 定向测试确认通过。

### Task 3: CORS 显式白名单

**Files:**
- Create: `aryn-mall-java/aryn-common/aryn-common-security/src/main/java/com/aryn/cloud/common/security/properties/CorsProperties.java`
- Create: `aryn-mall-java/aryn-common/aryn-common-security/src/test/java/com/aryn/cloud/common/security/properties/CorsPropertiesTest.java`
- Modify: `aryn-mall-java/aryn-boot/src/main/java/com/aryn/cloud/config/SaTokenConfigure.java`
- Modify: `aryn-mall-java/aryn-gateway/src/main/java/com/aryn/cloud/gateway/config/SaTokenConfigure.java`
- Modify: `aryn-mall-java/aryn-gateway/pom.xml`
- Modify: `aryn-mall-java/aryn-boot/src/main/resources/application.yml`

**Steps:**
1. 写失败测试覆盖精确 Origin 匹配、空 Origin 和通配符拒绝。
2. 实现 `hx.cors.allowed-origins` 属性。
3. Boot/Gateway 仅对匹配 Origin 回显 CORS 头，限制方法并添加 `Vary: Origin`。
4. 运行 common-security、boot、gateway 相关测试。

### Task 4: 修复管理后台字典、URL 与样式污染

**Files:**
- Create: `aryn-mall-ui/apps/web-ele/src/store/dict.test.ts`
- Create: `aryn-mall-ui/apps/web-ele/src/api/boot-url.ts`
- Create: `aryn-mall-ui/apps/web-ele/src/api/boot-url.test.ts`
- Modify: `aryn-mall-ui/apps/web-ele/src/views/_core/authentication/login.vue`
- Modify: `aryn-mall-ui/apps/web-ele/src/store/dict.ts`
- Modify: `aryn-mall-ui/apps/web-ele/src/components/dict-tag/index.vue`
- Modify: `aryn-mall-ui/apps/web-ele/src/api/request.ts`
- Modify: `aryn-mall-ui/apps/web-ele/src/components/verifition/index.vue`

**Steps:**
1. 写失败测试覆盖空 key、删除结果、重复字典更新和 Boot URL 重写。
2. 运行定向 Vitest 确认失败。
3. 实现最小修复，使用类型化 props/store，并为验证码样式添加 scoped。
4. 运行定向 Vitest、管理端类型检查和 lint。

### Task 5: 修复 UniApp 登录态、响应、持久化与事件清理

**Files:**
- Create: `aryn-mall-uniapp/src/store/auth-token.ts`
- Create: `aryn-mall-uniapp/src/store/auth-token.test.ts`
- Create: `aryn-mall-uniapp/src/store/persist.test.ts`
- Create: `aryn-mall-uniapp/src/api/core/handlers.test.ts`
- Create: `aryn-mall-uniapp/src/api/core/boot-url.ts`
- Create: `aryn-mall-uniapp/src/api/core/boot-url.test.ts`
- Modify: `aryn-mall-uniapp/src/store/authStore.ts`
- Modify: `aryn-mall-uniapp/src/store/persist.ts`
- Modify: `aryn-mall-uniapp/src/api/core/handlers.ts`
- Modify: `aryn-mall-uniapp/src/api/core/instance.ts`
- Modify: `aryn-mall-uniapp/src/router/index.ts`
- Modify: `aryn-mall-uniapp/src/sub-pages/order/order-confirm/index.vue`
- Modify: `aryn-mall-uniapp/src/sub-pages/order/order-list/index.vue`
- Modify: `aryn-mall-uniapp/manifest.config.ts`

**Steps:**
1. 写失败测试覆盖缺失 token、瞬时 store 排除、字符串响应 401 和 Boot URL 重写。
2. 运行 UniApp 定向 Vitest 确认失败。
3. 实现 token 守卫、安全响应解析、持久化排除和 URL 工具。
4. 清理路由敏感日志，改用 `onUnload` + 具名监听器解绑，开启 `urlCheck`。
5. 运行定向测试、UniApp 全量单测和 `pnpm type-check`。

### Task 6: 集成验证与审查结论归档

**Files:**
- Modify: `docs/50-需求文档/2026-07-22-代码审查P0修复/设计.md`
- Modify: `docs/90-记录归档/需求记录.md`

**Steps:**
1. 运行 `mvn test -pl aryn-boot -am`。
2. 运行管理端 `pnpm check:type && pnpm test:unit && pnpm lint && pnpm build:ele`。
3. 运行 UniApp `pnpm test:unit && pnpm type-check`。
4. 运行 `git diff --check`、检查 Git 状态和全部变更，确认未触碰用户未跟踪文件。
5. 在归档中记录已修复项、误报项、验证结果和剩余部署配置要求。

**Result:** 管理端类型检查、完整单测、生产构建及变更文件 Lint 通过；UniApp 全量测试与生产源码类型检查通过；Gateway 聚合编译和全部后端定向测试通过。未跟踪的 `aryn-common-log/event/ArynLogEventListener.java` getter 名称错误已在本地更正，但该未注册且重复现有处理逻辑的实验代码仍排除在提交之外；非沙箱环境运行完整 `mvn test -pl aryn-boot -am` 通过，41 个 Reactor 模块全部成功。完整管理端 Lint 仍被 19 个历史无关文件阻断，UniApp 原生类型检查受 Node/Vitest 本地依赖环境阻断。
