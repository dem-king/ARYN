# 发布缺口与商品品牌补齐实施计划

> **执行约束：** 在当前工作目录逐项实施；禁止修改明文配置和生产 API 地址，禁止新增独立仓储/WMS，禁止覆盖用户已有未提交改动。

**目标：** 修复微信小程序构建、租户上下文泄漏风险、测试与 CI 空壳、XXL-JOB 版本错配，并在商品域内补齐品牌管理与商品关联。

**架构：** 品牌属于 `aryn-product`，沿用 product-api/product-biz 分层，不新增服务。数据库变更同时维护 Boot/Cloud，租户表同步加入白名单；管理端提供 CRUD 与商品表单选择，C 端只读启用品牌。发布验证采用 Maven、Vitest、Playwright、UniApp 构建和可用时的 MySQL 8 临时库。

**技术栈：** Spring Boot 3、MyBatis-Plus、MySQL 8、Vue 3、Vite、Vitest、Playwright、UniApp、pnpm、GitHub Actions。

---

## 任务 1：UniApp 构建与测试基线

1. 固定复现 `pnpm build:mp-weixin` 的 UnoCSS/bundle optimizer 冲突。
2. 以最小平台条件调整插件启用范围或顺序，避免重复转换。
3. 清理 H5 的无效 UnoCSS 图标类，达到构建零告警。
4. 增加 Vitest 配置、纯逻辑测试和 `test:unit` 脚本。
5. 运行类型检查、单测、H5 与微信构建。

## 任务 2：租户上下文生命周期

1. 为 `ArynTenantContextFilter` 编写正常链路和异常链路清理测试。
2. 为 `ArynDubboRequestFilter` 编写 provider 正常/异常清理和 consumer 透传测试。
3. 在过滤器中使用 `try/finally` 清理上下文。
4. 支付回调保持由 HTTP 过滤器统一清理，并增加回调租户边界测试。
5. 运行 common-mybatis、common-dubbo、pay 与 Boot 聚合测试。

## 任务 3：商品品牌完整切片

1. 增加 `goods_brand` 和 `goods_spu.brand_id` 的 Boot/Cloud 幂等迁移，维护完整 SQL、租户白名单和一致性校验。
2. 在 product-api 增加品牌实体/DTO/VO，在 product-biz 增加 Mapper、Service、管理端 CRUD 和 App 启用品牌列表。
3. 商品管理查询与保存支持品牌，商品分页支持 `brandId` 条件。
4. 管理端增加品牌 API、页面、菜单权限和商品表单品牌选择。
5. UniApp 增加只读品牌 API，并在商品列表筛选中接入品牌参数。
6. 增加后端契约测试、前端 API/表单测试和 SQL 一致性测试。

## 任务 4：E2E 与 CI

1. 在管理端应用包增加 Playwright 配置和可独立运行的页面装修主链路测试，API 使用明确的路由 mock，覆盖草稿、预览、发布和租户拒绝。
2. 将根 `test:e2e` 从空 Turbo 任务改为实际 Playwright 命令。
3. 增加主仓 GitHub Actions，分别验证后端、管理端、UniApp 和 SQL。
4. 为支付、认证、网关关键边界补聚焦测试，优先支付回调幂等和租户上下文。

## 任务 5：XXL-JOB 与发布演练

1. 将 Executor 依赖与仓库内 Admin 版本统一，并运行 Maven 聚合测试。
2. 检查 Docker Compose 和文档中的版本/端口一致性。
3. 若本机 Docker 可用，启动临时 MySQL 8，按 Boot/Cloud 顺序演练迁移并校验索引、行数和回滚前置条件。
4. 执行全量命令和 `git diff --check`，更新缺失模块清单、Gate 3 状态与需求归档；无法运行的外部联调必须明确保留为未验证。
