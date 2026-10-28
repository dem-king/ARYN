/**
 * 跨页面共享组件的用户可见文案（隐私弹窗、SKU 弹层、配送进度、类目兜底等）。
 *
 * 只放本模块负责组件的文案；中文词条保持原有文案不变。
 */
export default {
  shared: {
    privacy: {
      title: '用户隐私保护提示',
      desc: '感谢您使用本应用，您使用本应用的服务之前请仔细阅读并同意',
      subDesc: '。当您点击同意并开始时用产品服务时，即表示你已理解并同意该条款内容，该条款将对您产生法律约束力。如您拒绝，将无法使用相应服务。',
      protocol: '《用户隐私保护指引》',
      disagree: '拒绝',
      agree: '同意',
    },
    sku: {
      requesting: '请求中...',
      invalidActionFunction: 'custom-action必须是function',
      invalidActionPromise: 'custom-action必须返回一个Promise',
    },
    deliveryProgress: {
      noPhone: '暂无电话号码',
      callFailed: '拨打电话失败',
      staffRole: '商城专属配送员',
      tripNo: '出车单号',
      progressTitle: '配送进度',
      evidence: '送达凭证',
      empty: '暂无配送进度信息',
    },
    categoryIcon: {
      fallbackText: '类',
    },
    deliveryPick: {
      uncategorized: '未分类',
    },
  },
}
