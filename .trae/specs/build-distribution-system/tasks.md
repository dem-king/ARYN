# Tasks

- [x] Task 1: 梳理并冻结分销领域接口契约（管理端、移动端、后端）
  - [x] SubTask 1.1: 基于现有项目约定输出分销实体与状态机（分销用户、分销订单、佣金、提现单）
  - [x] SubTask 1.2: 定义管理端与移动端所需 API 清单、入参、出参与错误码
  - [x] SubTask 1.3: 明确权限点、菜单路由、按钮级权限与字典项

- [x] Task 2: 完成后端分销核心能力（haorong-mall-java）
  - [x] SubTask 2.1: 新增分销配置、分销用户、分销订单、分销提现数据模型与持久层
  - [x] SubTask 2.2: 实现分销关系绑定、订单归因、佣金计算与结算服务
  - [x] SubTask 2.3: 实现提现申请、审核通过/驳回流程与审计日志
  - [x] SubTask 2.4: 完成接口、权限注解、参数校验、异常处理与幂等保护
  - [x] SubTask 2.5: 补充数据库 SQL 与必要初始化数据

- [x] Task 3: 完成管理端分销模块（haorong-mall-ui）
  - [x] SubTask 3.1: 新增菜单、路由、页面目录与 API 调用层
  - [x] SubTask 3.2: 实现分销配置页面（查询、编辑、保存）
  - [x] SubTask 3.3: 实现分销用户页面（列表、筛选、详情）
  - [x] SubTask 3.4: 实现分销订单页面（列表、筛选、状态展示）
  - [x] SubTask 3.5: 实现分销提现页面（待审核、通过、驳回）

- [x] Task 4: 完成移动端分销能力（haorong-mall-uniapp）
  - [x] SubTask 4.1: 新增分销中心入口与页面路由
  - [x] SubTask 4.2: 实现推广信息、佣金统计、佣金明细、提现记录展示
  - [x] SubTask 4.3: 实现提现申请流程与表单校验
  - [x] SubTask 4.4: 接入分享参数解析与分销关系绑定链路

- [ ] Task 5: 联调与验证
  - [x] SubTask 5.1: 完成三端联调（配置生效、订单归因、佣金结算、提现审核）
  - [ ] SubTask 5.2: 补充关键自动化测试（后端服务与接口）与前端核心交互自测
  - [x] SubTask 5.3: 修复联调问题并更新文档中的验收结果

# Task Dependencies

- Task 2 depends on Task 1
- Task 3 depends on Task 1, Task 2
- Task 4 depends on Task 1, Task 2
- Task 5 depends on Task 2, Task 3, Task 4
