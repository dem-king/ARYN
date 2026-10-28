/**
 * 应用级提示：请求错误、租户校验、登录态与导航兜底。
 *
 * 只放本模块负责页面/组件的用户可见文案；中文词条保持原有文案不变。
 */
export default {
  app: {
    request: {
      featureUnavailable: '该功能暂不可用，请稍后重试',
      forbidden: '暂无权限访问该功能',
    },
    auth: {
      loggingIn: '正在登录中，请稍候...',
      wxCredentialFailed: '获取微信登录凭证失败',
      tokenMissing: '登录响应缺少有效凭证',
      unsupportedScene: '不支持的购买场景：{scene}',
    },
    nav: {
      noPermission: '暂无访问权限',
    },
    deliveryNav: {
      noAddress: '暂无收货地址',
      openMapFailed: '打开地图失败',
      addressCopiedTitle: '地址已复制',
      addressCopiedContent: '打开手机上的导航软件，把地址粘贴到搜索框即可开始导航',
      gotIt: '知道了',
      copyFailed: '复制地址失败',
    },
    countdown: {
      finished: '已结束',
      days: '{count}天',
    },
    tenantBlocked: {
      title: '应用配置暂不可用',
      desc: '请联系管理员确认小程序与租户配置，稍后重试',
      retry: '重试',
    },
    tenantGuard: {
      moduleMissing: '租户身份模块缺失',
      tenantIdMismatch: '包内租户标识与环境注入不一致',
      appIdMismatch: '包内微信 AppID 与环境注入不一致',
      openBootMismatch: '包内部署模式与环境注入不一致',
      apiBaseUrlMismatch: '包内 API 地址与环境注入不一致',
      deviceMismatch: '运行容器 AppID 与包内期望不一致（real={real}）',
      bindingMismatch: '服务端租户映射与包内身份不一致',
      bindingCheckFailed: '租户绑定校验失败',
      bindingChanged: '服务端租户映射已变更，请重新打开应用',
      sessionMismatch: '会话租户与包内身份不一致',
    },
    tenantPreflight: {
      httpFailed: '租户预检请求失败（HTTP {status}）',
      abnormalResponse: '租户预检返回异常 code={code}',
      missingData: '租户预检响应缺少数据',
      networkFailed: '租户预检网络失败',
    },
    file: {
      deliveryIdentityFailed: '配送身份校验未通过',
      uploadFailed: '凭证上传失败',
      uploadResponseInvalid: '凭证上传响应无效',
    },
  },
}
