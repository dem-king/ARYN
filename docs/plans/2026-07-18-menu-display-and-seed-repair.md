# 菜单展示与初始化数据修复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. 本仓库禁止创建或切换 Git worktree，必须在当前工作目录执行。

**Goal:** 修复菜单管理空白、Cloud 拼团菜单缺失、会员菜单乱码，以及新增会员/营销菜单未授权的问题。

**Architecture:** 管理端将树形数组显式包装成 VXE Table 代理约定的 `list` 响应。数据库同时维护 Boot/Cloud 初始化种子和可重复执行的存量迁移：用确定的中文名称覆盖已损坏文本，补齐 Cloud 拼团菜单，并按现有管理员角色和非平台租户幂等补授权。

**Tech Stack:** Vue 3、TypeScript、VXE Table、Spring Boot 3、JUnit 5、MySQL 8

---

### Task 1: 建立菜单种子契约测试

**Files:**
- Create: `aryn-mall-java/aryn-upms/aryn-upms-biz/src/test/java/com/aryn/cloud/upms/menu/MenuSeedContractTest.java`

1. 断言 Boot/Cloud 会员菜单脚本均包含 `SET NAMES utf8mb4`。
2. 断言 Cloud 与 Boot 均包含完整拼团菜单 ID。
3. 断言存量修复迁移包含会员中文名称覆盖、角色授权和租户授权。
4. 运行定向测试，确认当前实现失败。

### Task 2: 修复菜单管理表格响应适配

**Files:**
- Modify: `aryn-mall-ui/apps/web-ele/src/views/upms/menu/index.vue`

1. 将 `/upms/menu/tree` 返回的树数组包装为 `{ list }`，并配置非分页 `response.list` 映射。
2. 关闭封装的早期自动加载，在页面挂载后显式查询，确保首次进入即可显示。
3. 保持分页关闭和树节点结构不变。
4. 运行管理端类型检查、格式检查和浏览器切页验收。

### Task 3: 修复初始化 SQL 与存量迁移

**Files:**
- Modify: `aryn-mall-java/db/boot/4aryn_boot_member.sql`
- Modify: `aryn-mall-java/db/cloud/4aryn_user_menu.sql`
- Modify: `aryn-mall-java/db/cloud/2aryn_upms.sql`
- Create: `aryn-mall-java/db/boot/15menu_seed_repair.sql`
- Create: `aryn-mall-java/db/cloud/15menu_seed_repair.sql`
- Regenerate: `aryn-mall-java/db/boot/aryn_boot_full.sql`

1. 为独立会员菜单脚本声明 UTF-8 会话字符集。
2. 将 Boot 的 9 条拼团菜单同步到 Cloud UPMS 初始化 SQL。
3. 用显式 `UPDATE ... CASE` 恢复 47 条会员菜单中文名称，避免依赖损坏文本反解。
4. 对管理员角色和非平台租户用 `NOT EXISTS` 幂等补齐会员及拼团菜单授权。
5. 更新 Boot 全量 SQL 生成清单并重新生成、验证。

### Task 4: 修复当前 Cloud 运行库

1. 先查询受影响菜单数量和授权数量作为迁移前基线。
2. 执行 `db/cloud/15menu_seed_repair.sql`。
3. 验证会员菜单名称字节、拼团菜单、角色授权和租户授权。
4. 清理菜单缓存并重新登录，使前端重新生成动态路由。

### Task 5: 验证与知识回写

**Files:**
- Modify: `docs/40-接口与风险/缺失模块清单.md`
- Modify: `docs/90-记录归档/需求记录.md`

1. 运行 UPMS 菜单种子契约测试。
2. 运行 Boot 聚合测试及管理端类型检查、单元测试和 lint。
3. 运行 `git diff --check` 并确认未覆盖用户已有修改。
4. 记录实际通过项和剩余风险。
