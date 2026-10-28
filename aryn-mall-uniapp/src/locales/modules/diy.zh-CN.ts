/**
 * 首页装修楼层组件。
 *
 * 只放本模块负责页面/组件的用户可见文案；中文词条保持原有文案不变。
 * 装修 JSON 下发的商品名、活动名、分类名、权益名、金额、时间等业务内容不在此维护。
 */
export default {
  diy: {
    common: {
      product: '商品',
      outOfStock: '暂时缺货',
      noProducts: '暂无商品',
      grab: '抢',
      noContent: '暂无内容',
      loadError: '暂时无法加载',
      loadFailed: '加载失败',
      saveAmount: '省¥{amount}',
    },

    // 首页活动入口条（diy-activity-strip）
    activityStrip: {
      seckillBadge: '抢',
      seckillTitle: '限时秒杀',
      seckillHookWithSession: '{name}·整点开抢',
      seckillHook: '整点开抢',
      groupBuyTitle: '{groupNum}人拼团',
      groupBuyTitleFallback: '2人拼团',
      groupBuyHook: '{groupNum}人成团立省',
      groupBuyHookFallback: '多人成团',
      groupBuyBadge: '拼',
      discountHook: '{value}专区',
      discountTitle: '限时折扣',
    },

    // 多 Tab 商品流会员权益条（diy-goods-tabs / member-banner）
    memberBanner: {
      memberCopy: '您已是 {levelName}，本区享会员专享价',
      guestCopy: '入会享会员价与专属券',
      memberFallbackName: '会员',
    },

    // 零售楼层加载态（retail-state.vue）
    retailState: {
      empty: '暂无内容',
      invalid: '暂时无法加载',
    },

    // 倒计时楼层（diy-countdown）
    countdown: {
      title: '距活动结束',
      completedText: '活动已结束',
    },

    // 视频/直播楼层（diy-video-live）
    videoLive: {
      noLive: '暂无可观看的直播',
      comingSoon: '直播功能即将开放',
      coverFallback: '直播入口',
      liveTag: '直播',
      emptyHint: '请在装修中配置视频地址或直播入口',
    },

    // 商品分组楼层（diy-goods-group）
    goodsGroup: {
      defaultTitle: '热卖商品',
    },

    // 商品排行楼层（diy-goods-ranking）
    goodsRanking: {
      defaultTitle: '畅销排行',
    },

    // 商品横滑楼层（diy-goods-scroll）
    goodsScroll: {
      defaultTitle: '省心午餐',
    },

    // 多 Tab 商品流（diy-goods-tabs）
    goodsTabs: {
      tabRecommend: '猜你喜欢',
      tabBargain: '低价捡漏',
      tabMember: '会员专区',
      memberTag: '会员',
      login: '去登录',
      learnMember: '了解会员',
      regularPrice: '日常价',
      bargainPrice: '捡漏价',
    },

    // 新人专享楼层（diy-newbie-floor）
    newbieFloor: {
      defaultTitle: '新人专享',
      tag: '新',
      subtitle: '首单专享价',
      countdownEndedPrefix: '活动',
      countdownEndedText: '已结束',
      countdownPrefix: '距结束',
      perks: '注册{registerDays}天内新人 · 每人每款限{limit}件',
      priceBadge: '新人价',
      fallbackGoodsName: '新人专享商品',
      limitToast: '新人专享价每人限购 {limit} 件',
      selectSpecsToast: '请选择规格',
    },

    // 今日补给单楼层（diy-replenish-card）
    replenishCard: {
      defaultTitle: '今日补给单',
      addressPending: '收货地址待同步',
      itemCount: '{count} 项',
      memberCount: '{count} 人参与',
      estimate: '预估 ¥{amount}',
      batchAdd: '按单加购',
      emptyTitle: '还没有进行中的补给单',
      emptyDesc: '发起补给单与同船成员合并采购，或直接导入 Excel 清单一键下单',
      createAction: '发起补给单',
      importAction: '导入清单下单',
    },

    // 船舶工作台楼层（diy-ship-workbench）
    shipWorkbench: {
      loading: '正在加载…',
      noCall: '暂无靠港计划，点击申报',
      incomplete: '靠港信息待完善',
      frequent: '常购',
    },

    // 优惠券相关（retail-normalizers / diy-coupon-receive）
    coupon: {
      fallbackTitle: '优惠券',
      typeDiscount: '折扣券',
      typeAmount: '满减券',
      discountValue: '{value}折',
      thresholdRequired: '满{threshold}可用',
      noThreshold: '无门槛',
      claimed: '已领取',
      claim: '领取',
      receiveSuccess: '领取成功',
      receiveFailed: '领取失败',
    },

    // 搜索栏楼层（diy-search-bar）
    searchBar: {
      placeholder: '搜索商品',
    },

    // 店铺信息楼层（diy-shop-info）
    shopInfo: {
      contact: '联系',
    },

    // 营销入口楼层（diy-marketing-entry）
    marketingEntry: {
      empty: '暂无入口',
    },

    // 会员权益楼层（diy-member-benefits）
    memberBenefits: {
      empty: '暂无会员权益，请在装修中配置',
      fallbackIcon: '礼',
    },

    // 服务承诺楼层（diy-service-promise）
    servicePromise: {
      empty: '暂无服务承诺，请在装修中配置',
    },

    // 自定义 HTML 楼层（diy-custom-html）
    customHtml: {
      empty: '暂无内容',
    },
  },
}
