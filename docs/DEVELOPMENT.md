# 悦航购 Aetheryn Mall — 开发指南

## 1. 环境要求

| 组件 | 版本 | 备注 |
|------|------|------|
| JDK | 17+ | OpenJDK 17 |
| Node.js | ≥ 22.x (UI) / ≥ 18 (UniApp) | |
| Maven | 3.6+ | |
| MySQL | 8.x | |
| Redis | 6.x+ | |
| Nacos | 2.x | 仅微服务模式需要 |
| RocketMQ | 5.x | 仅微服务模式需要 |

## 2. 项目初始化

```bash
# 克隆后，在项目根目录执行：

# === 后端 ===
cd aryn-mall-java

# 1. 初始化数据库 (二选一)
#    单体模式: 导入 db/boot/ 下的 SQL 文件
mysql -u root -p < db/boot/1schema.sql
mysql -u root -p < db/boot/2aryn_boot.sql
#    微服务模式: 导入 db/cloud/ 下的 SQL 文件

# 2. 修改配置
#    编辑 aryn-boot/src/main/resources/application-dev.yml
#    修改 MySQL/Redis 连接信息

# 3. 编译
mvn clean compile -pl aryn-boot -am

# 4. 运行 (IDEA 直接运行 ArynBootApplication.java)
#    单体模式启动端口: 9999
#    接口文档: http://localhost:9999/boot/doc.html

# === 管理后台 ===
cd aryn-mall-ui
pnpm install        # 安装依赖 (首次)
pnpm dev            # 启动开发服务器

# === 移动端 ===
cd aryn-mall-uniapp
pnpm install        # 安装依赖
pnpm dev:mp-weixin  # 启动微信小程序开发
pnpm dev:h5         # 启动 H5 开发
```

## 3. 常用命令速查

### 3.1 Java 后端 (`aryn-mall-java/`)

```bash
# 编译 (指定模块 + 依赖)
mvn clean compile -pl aryn-boot -am

# 测试
mvn clean test -pl aryn-boot -am

# 打包 (跳过测试)
mvn clean package -DskipTests -pl aryn-boot -am

# 安装到本地仓库
mvn clean install -DskipTests

# 检查依赖层级 (Harness)
python3 ../scripts/lint-deps.py

# 代码质量检查
python3 ../scripts/lint-quality.py

# Docker 构建
docker build -t aryn-boot ./aryn-boot
```

### 3.2 管理后台 (`aryn-mall-ui/`)

```bash
pnpm dev                   # 启动 web-ele 开发
pnpm build                 # 生产构建
pnpm lint                  # ESLint 检查
pnpm format                # Prettier 格式化
pnpm test                  # Vitest 单元测试
pnpm typecheck             # TypeScript 类型检查
pnpm turbo-run             # Turbo 构建

# 依赖检查 (monorepo)
node scripts/lint-deps.mjs
```

### 3.3 移动端 (`aryn-mall-uniapp/`)

```bash
pnpm dev:mp-weixin         # 微信小程序
pnpm dev:h5                # H5 开发
pnpm build:mp-weixin       # 微信小程序构建
pnpm build:h5              # H5 构建
pnpm lint                  # ESLint
pnpm type-check            # TypeScript 类型检查
pnpm alova-gen             # 从后端 Swagger 生成 API 类型
```

## 4. 开发规范

### 4.1 Java 后端

**目录约定**:
```
aryn-*-biz/src/main/java/com/aryn/cloud/{domain}/
├── controller/         # HTTP 接口 (@RestController)
├── service/
│   ├── impl/           # 业务实现
│   └── xxxService.java # 接口定义
├── mapper/             # MyBatis Mapper
├── entity/             # 数据库实体 (BaseEntity)
├── dto/                # 数据传输对象
└── config/             # 模块配置
```

**关键注解约定**:
- Controller: `@RestController` + `@RequestMapping("/{domain}")`
- Service: `@Service` + `@Transactional(rollbackFor = Exception.class)`
- Mapper: `@Mapper` (或启动类 `@MapperScan`)
- Entity: `@Data` + `@TableName("{table}")` + 继承 `BaseEntity`
- 权限: `@SaCheckPermission("user:xxx:yyy")`
- 防重: `@RepeatSubmit`

**代码风格**:
- 使用 Lombok (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`)
- 统一返回: `Result.success(data)` / `Result.failed(msg)`
- 分页查询: `IPage<T>` 参数 + `Result<PageResult<T>>` 返回
- 参数校验: `@Valid` + `@NotNull` / `@NotBlank`
- 禁止: `System.out.println()`, `printStackTrace()`, 硬编码密码/密钥

### 4.2 Vue 3 (管理后台)

**约定**:
- Composition API (`<script setup lang="ts">`)
- 文件命名: `kebab-case.vue`
- 表单组件: `<FormModal>` 模式 (弹窗表单)
- 权限指令: `v-action="'user:xxx:yyy'"`
- 使用 `@vben/*` 公共包而非自己写重复逻辑

### 4.3 UniApp (移动端)

**约定**:
- Composition API + `<script setup lang="ts">`
- 页面文件路由 (`uni-mini-router`)
- API 层使用 Alova (不要直接用 `uni.request`)
- 条件编译: `#ifdef MP-WEIXIN` / `#ifdef H5`

## 5. 数据库规范

### 命名
- 表名: `sys_` 前缀 (系统), 业务表用 `{domain}_` 前缀
- 字段名: `snake_case`, 布尔字段 `is_` 前缀
- 索引: `idx_{table}_{field}`

### 必需字段
- `id` BIGINT (雪花算法, 主键)
- `tenant_id` BIGINT (租户 ID)
- `create_by` VARCHAR (创建人)
- `create_time` DATETIME
- `update_by` VARCHAR
- `update_time` DATETIME
- `del_flag` CHAR(1) (0=正常, 1=删除)

> 所有删除操作使用 `del_flag` 逻辑删除，**严禁** SQL `DELETE` 语句。

## 6. Git 工作流

- `main` — 生产分支 (受保护)
- `dev` — 开发集成分支
- `feature/xxx` — 功能开发分支
- `fix/xxx` — 修复分支

Commit Message: `type(scope): description`
- `feat(user): add user profile page`
- `fix(order): correct order status transition`

## 7. Harness 验证管道

```bash
# 完整验证流程
python3 scripts/validate.py --backend

# 等价于依次执行:
mvn clean compile -pl aryn-boot -am    # build
python3 scripts/lint-deps.py            # lint-arch
python3 scripts/lint-quality.py         # lint-quality
mvn clean test -pl aryn-boot -am       # test
python3 scripts/verify/run.py           # e2e verify

# 操作前预验证
python3 scripts/verify_action.py --action "create file aryn-order-biz/.../XxxService.java"
python3 scripts/verify_action.py --action "import com.aryn.cloud.upms from com.aryn.cloud.order"
```

## 8. 常见问题

### Q: 单体与微服务如何切换？
A: 单体使用 `aryn-boot` 聚合模块；拆分为微服务后排除 `aryn-boot`，各 biz 独立部署。

### Q: 跨模块调用怎么做？
A: 单体直接注入 Service；微服务通过 Dubbo `@DubboReference` 调用 Remote Service（定义在 `*-api` 中）。

### Q: 多租户如何实现？
A: `aryn-common-mybatis` 中的 `ArynTenantLineHandler` 解析请求头 `TENANT-ID`，自动为 SQL 添加 `tenant_id` 过滤。
