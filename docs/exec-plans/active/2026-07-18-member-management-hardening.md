# 会员管理隐患修复实施计划

> 执行时按本计划逐项完成测试、实现和验证；禁止创建或切换 Git worktree。

**状态：** 已完成（2026-07-18）

**目标：** 修复会员资料越权、密码算法不一致、敏感字段泄露、账变并发、等级成长失效、权益不生效和关系数据不完整问题。

**架构：** HTTP 接口使用显式 DTO 限制可写字段；积分和余额通过带余额约束的原子 SQL 更新；订单完成事件驱动用户域幂等累计消费和积分。订单创建通过用户域远程接口读取当前等级权益，按“会员折扣 -> 优惠券 -> 运费/免邮”计算并记录会员优惠金额；等级变化后通过幂等来源键发放专属券。

**技术栈：** Spring Boot 3、MyBatis-Plus、Dubbo、RocketMQ、MySQL 8、Vue 3、UniApp、Vitest/JUnit 5/Mockito。

---

### 任务 1：封闭会员资料与密码安全边界

**文件：**
- 修改 `aryn-mall-java/aryn-user/aryn-user-biz/.../controller/admin/UserInfoController.java`
- 修改 `aryn-mall-java/aryn-user/aryn-user-biz/.../controller/app/AppUserInfoController.java`
- 新增 `aryn-mall-java/aryn-user/aryn-user-api/.../dto/*User*DTO.java`
- 修改 `UserRespVO.java`、`UserInfoMapper.xml`、`UserInfoServiceImpl.java`
- 新增 Controller 权限与资料所有权测试

**步骤：**
1. 先写测试，覆盖 C 端只能修改当前用户、不能写积分/余额/等级、密码使用 BCrypt、后台所有端点显式鉴权、HTTP VO 不含密码。
2. 运行 user-biz 测试确认失败。
3. 引入显式 DTO 和字段映射，手机号验证码成功后原子消费，密码更新校验确认密码并统一 BCrypt。
4. 删除 HTTP 响应和管理端分页 SQL 中的密码字段，补齐查询权限。
5. 重新运行目标测试。

### 任务 2：保证余额、积分和等级成长一致

**文件：**
- 修改 `UserInfo.java`、`UserInfoMapper.java`
- 修改 `PointsRecordServiceImpl.java`、`BalanceRecordServiceImpl.java`、`MemberLevelServiceImpl.java`
- 修改对应 Service 测试

**步骤：**
1. 写原子账变、余额不足、并发更新语义、累计积分不因消费下降、等级按 `sortOrder` 选择的测试。
2. Mapper 增加带租户条件的原子增减 SQL，Service 校验变动类型和数值。
3. 新增 `totalPoint`，等级累计积分读取该字段；等级重算失败向上抛出，保证主账和等级事务一致。
4. 删除等级时同步清理权益关系。
5. 运行 user-biz 测试。

### 任务 3：落地会员权益和订单成长

**文件：**
- 新增用户域权益聚合 VO、远程接口和 App Controller
- 修改 `OrderPriceComputeService.java`、`OrderInfoServiceImpl.java`
- 修改订单 Entity/DTO 和订单完成事件
- 新增用户域订单完成监听器及成长记录 Entity/Mapper/Service
- 扩展优惠券远程服务，支持按会员权益来源幂等发券

**步骤：**
1. 写权益聚合、价格顺序、免邮、最优折扣/倍率、订单完成幂等和专属券幂等测试。
2. 用户域返回当前等级启用权益；C 端仅能读取启用等级及权益。
3. 订单项先计算会员折扣，再分摊优惠券；免邮清零运费，并保存 `memberDiscountPrice`。
4. 订单完成事件携带实际商品支付金额；用户域按订单唯一键累计消费、发放基础积分并重算等级。
5. 等级首次进入后按 `(user, benefit)` 唯一来源键发放专属券。
6. 运行 user/order/promotion 目标测试。

### 任务 4：约束关系数据与修复跨端契约

**文件：**
- 修改会员等级、权益、标签 Service 和 DTO/VO
- 修改 Cloud/Boot SQL 与租户表配置
- 修改管理端权益表单和 UniApp 等级权益页/API

**步骤：**
1. 关系写入前校验主体和目标存在，空集合安全处理。
2. 为用户标签、等级权益、订单成长、会员券来源增加租户内唯一约束和迁移去重。
3. 管理端按权益类型提供受约束输入，并将等级绑定合并到权益保存事务。
4. UniApp 改用 `/app/member/**`，按后端数字枚举和真实等级字段展示，移除密码状态字段。
5. 更新知识库接口、数据模型与需求记录。

### 任务 5：全量验证

1. `cd aryn-mall-java && mvn test -pl aryn-boot -am`
2. `cd aryn-mall-ui && pnpm check:type && pnpm test:unit && pnpm lint`
3. `cd aryn-mall-uniapp && pnpm type-check && pnpm build:mp-weixin`
4. 检查 `git diff --check`，确认没有覆盖用户现有修改。

### 完成记录

- 后端 `mvn test -pl aryn-boot -am` 全部通过，38 个 Reactor 模块成功；会员域 69 个、订单域 11 个、营销域 90 个、Boot 租户门禁 10 个测试通过。
- 管理端单元测试 51 个文件、411 个用例通过，会员权益表单通过 Prettier 且不在类型错误列表中；全量 typecheck/lint 仍被既有分销、页面装修、公共 UI 类型和历史格式问题阻断。
- UniApp 会员相关文件不在现有 type-check/build 错误列表中；全量命令仍被既有全局类型、商品详情页和 UnoCSS 构建冲突阻断。
- Boot/Cloud SQL 一致性静态检查与 `git diff --check` 通过；真实 MySQL 8 存量迁移演练仍是部署前置条件。
