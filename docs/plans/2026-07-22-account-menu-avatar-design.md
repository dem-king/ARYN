# 账户菜单与头像展示调整设计

## 背景

管理后台右上角账户菜单当前包含“文档”“GitHub”“问题 & 帮助”三个外部入口，并在用户未上传头像时使用 `preferences.app.defaultAvatar` 的远程链接作为兜底头像。共享头像组件还会在图片缺失时显示用户名末两位字符。

本次调整要求删除上述三个外部入口，并确保头像只在用户存在真实上传地址时展示。没有头像时仍需保留账户菜单入口，以便用户继续使用锁屏和退出登录功能。

## 设计方案

采用局部调整方案，不修改全局 `VbenAvatar` 的通用兜底行为：

1. 在管理后台基础布局中删除三个外部菜单及相关常量、图标、窗口跳转依赖。
2. 头像计算值只读取用户信息中的头像地址；空字符串、纯空白或缺失值统一视为无头像，不再回退到默认头像链接。
3. `UserDropdown` 在有头像时保持现有头像按钮；无头像时使用用户名文字作为下拉菜单触发入口。
4. 账户下拉信息区、锁屏页面、锁屏弹窗和登录过期弹窗仅在头像地址有效时渲染头像。
5. 保留共享头像组件本身的兜底能力，避免改变通知、工作台等其他业务中的既有表现。

## 组件边界

- `apps/web-ele/src/layouts/basic.vue`：负责提供真实头像地址和账户菜单配置。
- `packages/effects/layouts/src/widgets/user-dropdown/user-dropdown.vue`：负责有头像与无头像两种账户入口及下拉头部布局。
- `packages/effects/layouts/src/widgets/lock-screen/lock-screen.vue`：负责锁屏解锁页面的条件头像展示。
- `packages/effects/layouts/src/widgets/lock-screen/lock-screen-modal.vue`：负责锁屏设置弹窗的条件头像展示。
- `packages/effects/common-ui/src/ui/authentication/login-expired-modal.vue`：负责登录过期弹窗的条件头像展示。

## 数据流与异常处理

用户信息仍由 `useUserStore` 提供。基础布局将头像字符串规范化后向下传递；子组件仅根据头像值是否存在决定是否渲染，不发起额外请求，也不生成替代链接。头像文件加载失败时不增加新的远程兜底地址。

## 验证

- 类型检查确保组件属性和模板分支合法。
- 单元测试或组件测试覆盖有头像、无头像时的账户入口与头像可见性。
- 静态检查确认三个外部菜单和默认头像回退已从基础布局移除。
