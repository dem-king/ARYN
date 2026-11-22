/**
 * 登录设备页（三期契约 §2.1/§2.2）文案。
 *
 * 单点策略说明必须如实描述当前策略（「同一账号仅允许一处登录」），不得暗示支持多端并存。
 */
export default {
  loginDevices: {
    title: '登录设备',
    entry: '登录设备',
    currentDevice: '当前设备',
    currentTag: '当前设备',
    currentDeviceCannotKick: '当前设备不支持在此下线，请使用底部「退出登录」。',
    otherDevices: '其他登录会话',
    noOthers: '暂无其他登录会话',
    empty: '暂无登录设备信息',
    emptyHint: '登录后可在个人设置中查看当前账号的登录设备',
    loadFailed: '登录设备加载失败，请稍后重试',
    retry: '重试',
    kick: '下线',
    kickTitle: '下线设备',
    kickConfirm: '确定要下线该设备吗？下线后该设备需要重新登录。',
    kickSuccess: '已下线该设备',
    kickFailed: '下线失败，请稍后重试',
    unknownDevice: '未知设备',
    location: '地点：{location}',
    loginTime: '登录时间：{time}',
    policyTip: '同一账号仅允许一处登录，其他设备登录会自动顶掉本设备。',
  },
}
