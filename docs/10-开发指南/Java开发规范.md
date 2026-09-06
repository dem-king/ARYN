# Java 后端开发规范（通用版）

> 本文档基于悦航购（Aryn Mall）项目的真实编码实践提炼，整理为一份**可复用于后续新项目**的通用 Java 开发规范。
> 覆盖工程结构、命名、分层依赖、代码风格、数据访问、事务、日志、安全等维度。
> 使用时按新项目的实际情况替换示例中的包名与业务名即可。

---

## 1. 技术栈基线

新项目建立技术基线时建议采用以下版本组合（与项目实践一致）：

| 技术 | 版本建议 | 说明 |
|------|----------|------|
| JDK | 17+ | 语言与构建基线 |
| Spring Boot | 3.x | 现代化应用框架 |
| Maven | 3.6+ | 构建与多模块管理 |
| MyBatis-Plus | 3.5.x | 数据访问，简化 CRUD |
| 权限框架 | Sa-Token | 轻量认证与鉴权 |
| 参数校验 | jakarta.validation | `@Valid` + 注解校验 |
| Swagger 文档 | springdoc（Knife4j 增强） | `@Tag` / `@Operation` / `@Schema` |
| 日志 | Lombok `@Slf4j` | 结构化日志 |
| 其他 | Lombok、Hutool、MySQL 8.x、Redis | 常用工具与中间件 |

> 若采用微服务形态，再加 Spring Cloud Alibaba + Dubbo + Nacos（详见 §4 依赖规则）。

---

## 2. 工程结构与模块划分

Maven 多模块，基础包名统一，例如 `com.<company>.cloud`。业务按"域"拆分：

```text
├─ <project>-common-core   # 纯基础类型/工具，禁止依赖任何内部业务包
├─ <project>-common-xxx    # 基础设施封装：log / redis / mybatis / security ...
├─ <project>-<domain>-api  # 接口 + DTO / VO / Entity（可被其他域依赖）
├─ <project>-<domain>-biz  # 业务实现：Controller / Service / Mapper / Dubbo 实现
├─ <project>-boot          # 单体启动模块（聚合所有 biz）
└─ <project>-gateway       # 网关（微服务形态）
```

**核心原则**：

- 每个业务域分成 `*-api`（对外契约）与 `*-biz`（内部实现）两个模块。
- `*-api` 模块**不依赖**任何 `*-biz` 模块，只依赖 `common` 层。
- 实体（Entity）、DTO、VO 统一放在 `*-api`，保证跨模块可复用。

---

## 3. 命名规范

| 类别 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase，名词 | `UserInfoController` |
| 接口名 | PascalCase，业务接口以 `I` 前缀，远程接口以 `Remote` 前缀 | `IUserInfoService` / `RemoteUserService` |
| 方法 | camelCase，动词开头 | `getUserById` / `saveUser` |
| 变量/字段 | camelCase | `createTime` |
| 常量 | 全大写 + 下划线 | `CommonConstants.SUCCESS` |
| 包名 | 全小写 | `com.aryn.cloud.user.controller.admin` |
| 模块名 | 小写，`-` 分隔 | `aryn-order-biz` |
| 传输对象 | `XxxDTO` / `XxxVO` / `XxxQuery` / `XxxRequest` | `UserCreateDTO` / `UserRespVO` |
| Controller | `XxxController` | `UserInfoController` |
| Service | `IXxxService` / `XxxServiceImpl` | `IUserInfoService` / `UserInfoServiceImpl` |
| Mapper | `XxxMapper` | `UserInfoMapper` |

---

## 4. 分层与依赖规则（强制）

### 4.1 分层

```text
L0 common-core（纯基础类型）
L1 common-log/redis/mybatis/storage/sms/swagger/job ...
L2 common-security/sentinel/seata ...
L3 *-api（Remote 接口、DTO、VO、Entity）
L4 *-biz（Controller、Service、Mapper、Dubbo 实现）
L5 gateway / auth / boot（入口）
```

### 4.2 依赖方向

- **高层可依赖低层，反向禁止**：`*-biz` 可依赖 `*-api` 与 `common`，`*-api` 不得依赖 `*-biz`。
- **不同 biz 之间禁止直接 import 实现类**，跨模块调用一律走 `*-api` 定义的 `Remote` 接口 + Dubbo RPC。
- 底层模块（`common-core`）**禁止 import 任何内部业务包**。

### 4.3 跨模块调用约定

在 `*-api` 中定义远程接口，业务端统一返回 DTO/VO，不返回 Map：

```java
// <domain>-api/.../remote/RemoteUserService.java
public interface RemoteUserService {
    UserInfoVO getUserById(String userId);
    List<UserInfoVO> getUserByIds(List<String> userIds);
}
```

在 `*-biz` 中用 `@DubboReference` 注入使用；单体模式下由 Spring 容器/Dubbo injvm 聚合，无需改动业务代码。

---

## 5. 代码风格

### 5.1 通用

- 使用 Lombok 简化样板代码：类用 `@Data`，依赖注入用 `@RequiredArgsConstructor` + `private final`。
- 禁止手工编写 getter/setter、无参/全参构造器。
- 统一使用 tab 或统一缩进（建议与项目现有风格保持一致），并通过 IDE 格式化。
- 避免魔法数字，常量收敛到常量类或枚举。

### 5.2 日志（强制）

- 所有需要日志的类使用 `@Slf4j`。
- **禁止** `System.out.println()`、`printStackTrace()`。
- 异常日志用 `log.error("业务说明 ex={}", e.getMessage(), e)`，保留堆栈。
- 关键业务操作（增删改、金额变动）记录结构化日志。

### 5.3 注释

- 类、公共方法建议保留 Javadoc，注明作者、用途。
- 复杂逻辑必须写注释说明"为什么"，而非翻译代码。

---

## 6. 统一返回与异常处理

### 6.1 统一响应体

所有 Controller 返回统一封装 `Result<T>`（可放 `common-core`）：

```java
@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {
    private int code;   // 0=成功，非 0=失败
    private String msg;
    private T data;

    public static <T> Result<T> success() { /* code=0 */ }
    public static <T> Result<T> success(T data) { /* ... */ }
    public static <T> Result<T> fail(String msg) { /* code=1 */ }
    public static <T> Result<T> fail(int code, String msg) { /* ... */ }
}
```

- Controller 方法统一返回 `Result` / `Result<T>`，禁止直接返回裸对象。
- 业务方法返回 Boolean、分页 `IPage` 等真实数据，由 Controller 包一层 `Result.success(...)`。

### 6.2 全局异常处理

提供全局 `@RestControllerAdvice`，集中处理：

| 异常 | 处理 |
|------|------|
| 业务异常 `XxxBusinessException` | 返回自定义 `code + msg` |
| 参数校验 `BindException` | 提取字段校验 message 返回 |
| 权限异常 `NotPermissionException` 等 | 返回 403 语义 |
| 兜底 `Exception` | 记录 `log.error`，返回统一错误 |

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(XxxBusinessException.class)
    public Result handleBusiness(XxxBusinessException e) {
        log.error("自定义异常信息 ex={}", e.getMsg(), e);
        return Result.fail(e.getCode(), e.getMsg());
    }
    // ... 其余按需实现
}
```

### 6.3 业务异常

- 定义统一业务异常类（如 `XxxBusinessException`），包含 `code` 与 `msg`。
- Service 中业务校验失败时 `throw new XxxBusinessException("xxx不能为空")`，禁止吞异常后返回 null。

---

## 7. Controller 层规范

```java
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/userinfo")
@Tag(name = "商城用户")
public class UserInfoController {

    private final IUserInfoService userInfoService;

    @Operation(summary = "分页列表")
    @SaCheckPermission("user:userinfo:page")
    @GetMapping("/page")
    public Result page(Page page, UserInfo userInfo) {
        return Result.success(userInfoService.getPage(page, userInfo));
    }

    @SysLog("新增用户")
    @Operation(summary = "新增")
    @SaCheckPermission("user:userinfo:add")
    @PostMapping
    public Result<Boolean> add(@RequestBody @Valid UserCreateDTO request) {
        return Result.success(userInfoService.saveUser(request));
    }

    @Operation(summary = "删除")
    @SaCheckPermission("user:userinfo:del")
    @DeleteMapping("/{id}")
    public Result<Boolean> del(@PathVariable("id") String id) {
        return Result.success(userInfoService.deleteUser(id));
    }
}
```

**约定**：

- 类注解：`@Slf4j` + 注入方式 + `@RestController` + `@RequestMapping` + `@Tag`。
- 方法注解顺序建议：`@Operation(summary=...)` → 权限/操作日志注解 → HTTP 方法注解。
- REST 语义：GET 查询、POST 新增、PUT 修改、DELETE 删除。
- 分页：方法入参直接用 `Page page` + 查询对象，由 MyBatis-Plus 分页插件处理。
- 写操作使用 `@RequestBody @Valid XxxDTO` 接收参数并做参数校验。
- 增删改等敏感操作加 `@SysLog("操作说明")` 记录操作日志。
- 每个方法必须有 Swagger `@Operation` 说明，参数用 `@Schema` 描述。
- Controller 只做参数接收与结果封装，业务逻辑全部下沉到 Service。

---

## 8. Service 层规范

- 接口 + 实现分离：`IXxxService` 接口 + `XxxServiceImpl` 实现。
- 继承 MyBatis-Plus 基类减少样板：

```java
public interface IUserInfoService extends IService<UserInfo> {
    IPage<UserRespVO> getPage(Page page, UserInfo userInfo);
}

@Slf4j
@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl
        extends ServiceImpl<UserInfoMapper, UserInfo>
        implements IUserInfoService {

    private final IXxxService xxxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveUser(UserCreateDTO request) {
        // 1. 参数/业务校验，不通过 throw 业务异常
        // 2. 多表写入时用事务保证一致性
        return save(entity);
    }
}
```

**约定**：

- 构造器注入（`@RequiredArgsConstructor` + `private final`），禁止 `@Autowired` 字段注入。
- 多表写入必须加 `@Transactional(rollbackFor = Exception.class)`。
- 业务校验失败抛业务异常，不返回失败标记然后吞掉。

---

## 9. Mapper / 数据访问层规范

```java
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    IPage<UserRespVO> selectAdminPage(Page page, @Param("query") UserInfo userInfo);

    // 复杂 SQL 可用注解或 XML
    @Update("""
            UPDATE user_info
            SET point = COALESCE(point, 0) + #{changePoint}
            WHERE id = #{userId} AND del_flag = '0'
            """)
    int acquirePoints(@Param("userId") String userId, @Param("changePoint") Integer changePoint);
}
```

**约定**：

- Mapper 接口继承 `BaseMapper<T>`，获得基础 CRUD。
- 复杂 SQL 写在 `resources/mapper/` 下的 XML；简单更新可用 `@Update` 文本块。
- 参数统一用 `@Param` 命名，禁用 `0`/`1` 位置参数。
- 分页方法返回 `IPage<VO>`，配合分页插件。
- 涉及金额/积分等增减操作，SQL 内做条件保护（如 `COALESCE(...,0) >= 变动值`），避免并发下为负。

---

## 10. 实体与 DTO / VO 规范

### 10.1 Entity

```java
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "user_info")
public class UserInfo extends Model<UserInfo> {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String nickname;

    // 审计字段（自动填充）
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    // 逻辑删除
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private String delFlag;
}
```

**约定**：

- 主键 `@TableId(type = IdType.ASSIGN_ID)`，使用雪花 ID（字符串类型避免精度问题）。
- 实体上使用 `@Schema` 描述字段，供 Swagger 生成文档。
- 审计字段统一为 `create_by` / `update_by` / `create_time` / `update_time`，通过 `@TableField(fill = ...)` + MetaObjectHandler 自动填充，不手工赋值。
- 逻辑删除字段统一 `del_flag`（`0` 显示 / `1` 隐藏），加 `@TableLogic`。

### 10.2 DTO / VO

- 放在 `*-api` 模块的 `dto/` 与 `vo/` 包，实现 `Serializable`。
- **禁止** Entity 直接暴露给接口/前端：对外入参用 `XxxDTO`/`XxxRequest`，出参用 `XxxVO`。
- 字段用 `@Schema` 描述；需要校验的字段加 `@NotNull` / `@NotBlank` 等。
- 出参 VO 只包含需要返回的字段，不返回密码等敏感信息。

---

## 11. 数据库规范

### 11.1 表设计

| 项 | 规范 |
|----|------|
| 表名 | 小写下划线：`user_info`、`order_detail` |
| 字段名 | 小写下划线：`del_flag`、`create_time` |
| 主键 | 雪花 ID（`bigint`/`varchar`） |
| 逻辑删除 | 所有表含 `del_flag`（`0` 显示 / `1` 删除），**禁止物理删除** |
| 审计字段 | `create_by` / `update_by` / `create_time` / `update_time` |
| 多租户 | 需隔离的表必须含 `tenant_id`（见 §12） |

### 11.2 数据操作

- 删除一律走逻辑删除，通过 `@TableLogic` 或 SQL 中 `del_flag = '0'` 条件实现。
- 变更语句必须带 `del_flag = '0'` 条件，避免误操作已删除数据。
- 金额、积分用 `BigDecimal`/整数分存储，禁止浮点类型。
- 时间统一用 `LocalDateTime` + `datetime`。

---

## 12. 多租户规范

若系统需要 SaaS 多租户：

- 需隔离的表含 `tenant_id`，通过 MyBatis 拦截器自动注入查询条件（基于"租户表清单"配置）。
- 含 `tenant_id` 的表在 Mapper `JOIN` 中**必须声明表别名**，避免拦截器注入未限定的 `tenant_id`。
- 启动时校验实际表结构与租户表清单一致，防止漂移。
- 跨服务调用时通过 RPC Filter 传播 `tenantId`，保证链路级隔离。

---

## 13. 事务与一致性规范

- 多表写入统一使用 `@Transactional(rollbackFor = Exception.class)`（默认只回滚 RuntimeException，必须显式指定）。
- 支付回调、定时任务、MQ 消费等场景需业务**幂等**：先查后写、唯一索引、幂等表/状态机。
- 分布式场景（跨库/微服务）明确 Seata 等事务方案，或通过"本地事务 + 消息 + 补偿"保证最终一致性。
- 金额变动类 SQL 做原子条件更新，避免先查后改的并发竞态。

---

## 14. 安全规范

- 认证鉴权统一走权限框架（本项目为 Sa-Token），后台接口按 `域:资源:动作` 声明权限码，如 `user:userinfo:page`。
- 公开接口（登录、验证码、支付回调）需显式放行，并审阅越权风险。
- 密码等敏感信息加密存储（本项目用 BCrypt），禁止明文。
- 手机号等敏感字段在返回前做脱敏处理（如 `@Desensitization`）。
- 接口出参 VO 不返回密码、token 等敏感字段。

---

## 15. 双模式适配（单体 / 微服务，强制）

若系统需要同时支持单体与微服务两种部署形态：

- **路径**：Controller 路径首段必须是业务域（`/order/xxx`），单体模式由 `context-path` 统一加 `/boot` 前缀，**禁止在 Controller 硬编码 `/boot`**。
- **跨模块调用**：一律走 `*-api` Remote + Dubbo，保证微服务可拆分，单体模式下不改代码即可聚合。
- **配置**：新增配置项必须同时提供单体（`application*.yml`）与微服务（各服务 + 配置中心）两份配置。
- **数据库脚本**：表结构/脚本变更必须同步维护单体和微服务两套 SQL，防止表结构漂移。
- **验证**：修改后分别验证两种模式下的请求路径与依赖正确。

---

## 16. 测试规范

- 核心业务（金额变动、状态流转、幂等、权限）必须有单元/集成测试。
- 分层测试：Mapper 契约测试 → Service 单测 → 接口集成测试。
- 测试不依赖真实环境：数据库用 H2/Testcontainers，外部服务 mock。
- 修改公共模块时，扩大到所有消费者模块验证（编译 + 相关测试）。

---

## 17. Git 与文档规范

- 提交信息使用中文，标题概括变更，正文说明"为什么"。
- 新需求在 `docs/50-需求文档/YYYY-MM-DD-需求名/` 建立需求包，完成后登记归档记录。
- 稳定结论、业务规则、接口变更需同步更新对应层文档。
- 涉及共享代码、租户、认证、实体或公共 API 的改动，先评估调用/依赖影响范围。

---

## 18. 检查清单（提交流前自查）

- [ ] 无 `System.out.println` / `printStackTrace`
- [ ] 日志使用 `@Slf4j`，异常带堆栈
- [ ] 接口返回统一 `Result`，出参用 VO，不暴露 Entity
- [ ] 敏感字段已脱敏，不返回密码
- [ ] 删除走逻辑删除 `del_flag`
- [ ] 多表写入有事务，回调/任务有幂等
- [ ] 多租户表 JOIN 有表别名
- [ ] Controller 方法有 `@Operation`，写操作有权限码
- [ ] 跨模块调用走 `*-api` Remote，无 biz 间直接 import
- [ ] 双模式（若适用）下路径与配置均正确
- [ ] 相关测试已补充并通过
