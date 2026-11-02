/**
 * 会话失效弹窗文案（被顶下线/登录过期/被踢下线细分）。
 *
 * 口径见 docs/50-需求文档/2026-10-28-被顶下线弹窗提示/一期实现契约.md §4（reason 文案）
 * 与二期实现契约.md §2（subReason 文案 + 单按钮规则），中文逐字冻结；
 * 英文语义一致即可。占位符只使用 `{name}` 命名插值（小程序端自研编译器只支持这一种）。
 */
export default {
  sessionInvalid: {
    title: {
      beReplaced: '账号已在其他设备登录',
      beReplacedDelivery: '配送账号已在其他设备登录',
      tokenExpired: '登录已过期',
      invalid: '登录状态已失效',
      // 二期：KICK_OUT + subReason 细分标题
      adminKick: '已被管理员下线',
      passwordChanged: '密码已修改',
      accountDisabled: '账号已被停用',
      accountDeleted: '账号已被删除',
      tenantDisabled: '所属租户已停用',
      memberDisabled: '账号已被禁用',
      deliveryRevoked: '配送资格已变更',
    },
    content: {
      beReplaced: '当前账号于 {time} 在另一台设备登录，你已被迫下线。',
      beReplacedNoTime: '当前账号已在其他设备登录，请重新登录后继续使用。',
      beReplacedTail: '重新登录会使那台设备下线。',
      tokenExpired: '登录状态已过期，请重新登录。',
      invalid: '登录状态已失效，请重新登录。',
      // 二期：KICK_OUT + subReason 细分正文（移动端口径，逐字）
      adminKick: '管理员已将你下线。',
      passwordChanged: '密码已修改，请使用新密码重新登录。',
      accountDisabled: '该账号已被停用，请联系管理员。',
      accountDeleted: '该账号已被删除。',
      tenantDisabled: '账号所属店铺已停用。',
      memberDisabled: '该账号已被禁用，请联系客服。',
      deliveryRevoked: '你的配送资格已变更，请重新登录配送端。',
    },
    deviceLine: '设备：{device}',
    relogin: '重新登录',
    exit: '退出',
    // 二期：不可重新登录时的唯一按钮文案（与「退出」底层动作相同）
    backToLogin: '返回登录',
  },
}
