# aryn-user-biz 测试兼容性修复实施计划

**状态：** 已完成（2026-07-17）

**目标：** 修复 7 个用户域测试类与 MyBatis-Plus 3.5.15、Mockito 的编译兼容性，使 Boot 聚合测试能够越过 `aryn-user-biz` 测试编译阶段。

**方案：** 仅调整测试代码和测试依赖，不改变生产 Service 或 Mapper API。测试通过私有 Service 子类设置继承的 `baseMapper`；Mockito 对单实体和集合重载使用显式参数类型，批量 Mapper 桩使用 `List<BatchResult>` 返回契约。

**技术栈：** Java 17、JUnit 5、Mockito、MyBatis-Plus 3.5.15、Maven

---

### 任务 1：保留失败基线

**文件：**
- 检查：`aryn-mall-java/aryn-user/aryn-user-biz/pom.xml`
- 检查：`aryn-mall-java/aryn-user/aryn-user-biz/src/test/java/com/aryn/cloud/user/service/impl/*Test.java`

1. 运行 `mvn test-compile -pl aryn-user/aryn-user-biz -am -DskipTests`。
2. 确认失败来自 `baseMapper` 受保护访问、Mapper 重载歧义和批量返回值类型不符。

### 任务 2：修复 Service 测试夹具

**文件：**
- 修改：`aryn-mall-java/aryn-user/aryn-user-biz/src/test/java/com/aryn/cloud/user/service/impl/BalanceRecordServiceImplTest.java`
- 修改：`aryn-mall-java/aryn-user/aryn-user-biz/src/test/java/com/aryn/cloud/user/service/impl/MemberBenefitServiceImplTest.java`
- 修改：`aryn-mall-java/aryn-user/aryn-user-biz/src/test/java/com/aryn/cloud/user/service/impl/MemberLevelServiceImplTest.java`
- 修改：`aryn-mall-java/aryn-user/aryn-user-biz/src/test/java/com/aryn/cloud/user/service/impl/MemberTagServiceImplTest.java`
- 修改：`aryn-mall-java/aryn-user/aryn-user-biz/src/test/java/com/aryn/cloud/user/service/impl/PointsRecordServiceImplTest.java`
- 修改：`aryn-mall-java/aryn-user/aryn-user-biz/src/test/java/com/aryn/cloud/user/service/impl/RechargeOrderServiceImplTest.java`
- 修改：`aryn-mall-java/aryn-user/aryn-user-biz/src/test/java/com/aryn/cloud/user/service/impl/SignInRecordServiceImplTest.java`

1. 将 `@InjectMocks` 字段类型改为对应的私有测试子类。
2. 在测试子类内部提供包内不可见的 Mapper 设置方法，由子类合法访问 `protected baseMapper`。
3. 在 `setUp()` 中通过设置方法注入 Mapper。
4. 运行 `test-compile`，确认 7 个受保护访问错误消失。

### 任务 3：消除 Mapper 重载歧义

**文件：** 同任务 2。

1. 为 `argThat` 的 lambda 参数声明具体实体类型。
2. 将单实体 `any()` 改为 `any(Entity.class)`。
3. 为集合匹配器声明元素或集合类型，确保选择 `insert(Collection<T>)`。
4. 将批量插入桩返回值从整数改为 `List<BatchResult>`。
5. 运行 `mvn test -pl aryn-user/aryn-user-biz -am`，确认 7 个测试类能够编译并执行。

### 任务 4：聚合验证与记录

**文件：**
- 更新：`docs/90-记录归档/需求记录.md`（仅在结论稳定后）

1. 运行 `mvn test -pl aryn-boot -am`。
2. 检查测试数量、失败数和 Maven Reactor Summary。
3. 运行 `git diff --check` 并审查只涉及计划、用户测试及必要文档。
4. 若 Boot 在后续模块失败，记录新的独立阻断点，不将其误报为本次修复失败。
