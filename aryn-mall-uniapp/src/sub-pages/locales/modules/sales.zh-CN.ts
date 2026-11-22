/**
 * 销售工作台（公司销售）文案。
 *
 * 口径：销售提成随基本工资发放，小程序内**只读、不提供提现**；
 * 未登记为销售（`isSales=false`）时整页只展示「非销售账号」提示。
 */
export default {
  sales: {
    workbench: {
      navTitle: '销售工作台',
      nonSalesTitle: '非销售账号',
      nonSalesHint: '当前账号未登记为销售，暂无法查看销售工作台',
      nonSalesBack: '返回',
      monthCommission: '本月预估提成(元)',
      monthTradeAmount: '本月成交额(元)',
      monthOrderCount: '本月成交单数',
      totalCommission: '累计提成(元)',
      customerCount: '名下客户(人)',
      qrcodeTitle: '我的推广码',
      qrcodeHint: '客户扫码进入小程序后自动绑定为你的客户',
      qrcodeLoading: '推广码生成中...',
      qrcodeUnavailable: '推广码暂不可用',
      qrcodeRetry: '重新获取',
      saveToAlbum: '保存到相册',
      saveSuccess: '已保存到相册',
      saveFailed: '保存失败，请长按图片保存',
      longPressHint: '长按图片亦可保存',
      customersTitle: '我的客户',
      customersEmpty: '暂无客户',
      bindSourceLabel: '来源：{source}',
      bindTimeLabel: '绑定时间：{time}',
      ordersTitle: '业绩明细',
      ordersEmpty: '暂无业绩明细',
      orderCommissionBase: '提成基数{amount}元',
      orderCommissionAmount: '提成{amount}元',
      orderPayTime: '支付时间：{time}',
      loadMore: '加载更多',
      status: {
        pending: '待确认',
        payable: '可发放',
        paid: '已发放',
        refunded: '已全额退款',
        unknown: '未知',
      },
      bindSource: {
        share: '分享链接',
        qrcode: '推广码',
        admin: '后台指派',
        transfer: '离职交接',
        claim: '首单转交',
        unknown: '其他',
      },
    },
  },
}
