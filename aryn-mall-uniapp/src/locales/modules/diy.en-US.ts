/**
 * English messages for the diy module (Home page DIY floor components).
 * Keys must mirror diy.zh-CN.ts.
 */
export default {
  diy: {
    common: {
      product: 'Item',
      outOfStock: 'Out of stock',
      noProducts: 'No items yet',
      grab: 'Buy',
      noContent: 'No content',
      loadError: 'Unable to load',
      loadFailed: 'Failed to load',
      saveAmount: 'Save ¥{amount}',
    },

    // Home activity entry strip (diy-activity-strip)
    activityStrip: {
      seckillBadge: 'GO',
      seckillTitle: 'Flash Sale',
      seckillHookWithSession: '{name} · Hourly',
      seckillHook: 'Hourly drop',
      groupBuyTitle: '{groupNum}-Person Group',
      groupBuyTitleFallback: '2-Person Group',
      groupBuyHook: 'Save more with {groupNum} people',
      groupBuyHookFallback: 'Group up to save',
      groupBuyBadge: 'Group',
      discountHook: '{value} Zone',
      discountTitle: 'Limited Offer',
    },

    // Member banner on the multi-tab goods feed (diy-goods-tabs / member-banner)
    memberBanner: {
      memberCopy: 'You are {levelName}. Enjoy member-only prices here.',
      guestCopy: 'Join for member prices and exclusive coupons',
      memberFallbackName: 'Member',
    },

    // Retail floor load states (retail-state.vue)
    retailState: {
      empty: 'No content',
      invalid: 'Unable to load',
    },

    // Countdown floor (diy-countdown)
    countdown: {
      title: 'Ends in',
      completedText: 'Event ended',
    },

    // Video / live floor (diy-video-live)
    videoLive: {
      noLive: 'No live stream available',
      comingSoon: 'Live streaming is coming soon',
      coverFallback: 'Live',
      liveTag: 'LIVE',
      emptyHint: 'Set a video URL or live entry in the page builder',
    },

    // Goods group floor (diy-goods-group)
    goodsGroup: {
      defaultTitle: 'Best Sellers',
    },

    // Goods ranking floor (diy-goods-ranking)
    goodsRanking: {
      defaultTitle: 'Top Sellers',
    },

    // Goods horizontal scroll floor (diy-goods-scroll)
    goodsScroll: {
      defaultTitle: 'Easy Lunch',
    },

    // Multi-tab goods feed (diy-goods-tabs)
    goodsTabs: {
      tabRecommend: 'For You',
      tabBargain: 'Bargains',
      tabMember: 'Member Zone',
      memberTag: 'Member',
      login: 'Sign In',
      learnMember: 'Learn More',
      regularPrice: 'Regular',
      bargainPrice: 'Clearance',
    },

    // New user floor (diy-newbie-floor)
    newbieFloor: {
      defaultTitle: 'New User Perks',
      tag: 'NEW',
      subtitle: 'First-order price',
      countdownEndedPrefix: 'Event',
      countdownEndedText: 'Ended',
      countdownPrefix: 'Ends in',
      perks: 'New users within {registerDays} days · Limit {limit} per item',
      priceBadge: 'New User Price',
      fallbackGoodsName: 'New User Item',
      limitToast: 'New User Price: limit {limit} per customer',
      selectSpecsToast: 'Select Specs',
    },

    // Today's supply list floor (diy-replenish-card)
    replenishCard: {
      defaultTitle: 'Today\'s Supply List',
      addressPending: 'Address syncing',
      itemCount: '{count} items',
      memberCount: '{count} joined',
      estimate: 'Est. ¥{amount}',
      batchAdd: 'Add from List',
      emptyTitle: 'No active supply list',
      emptyDesc: 'Start a supply list to pool orders with your crew, or import an Excel list to order in one go.',
      createAction: 'Start a List',
      importAction: 'Import List',
    },

    // Vessel workbench floor (diy-ship-workbench)
    shipWorkbench: {
      loading: 'Loading…',
      noCall: 'No port call. Tap to declare.',
      incomplete: 'Port info incomplete',
      frequent: 'Buy Again',
    },

    // Coupons (retail-normalizers / diy-coupon-receive)
    coupon: {
      fallbackTitle: 'Coupon',
      typeDiscount: 'Discount',
      typeAmount: 'Amount Off',
      discountValue: '{percentOff}% OFF',
      thresholdRequired: 'Min. spend ¥{threshold}',
      noThreshold: 'No minimum',
      claimed: 'Claimed',
      claim: 'Claim',
      receiveSuccess: 'Coupon claimed',
      receiveFailed: 'Failed to claim',
    },

    // Search bar floor (diy-search-bar)
    searchBar: {
      placeholder: 'Search products',
    },

    // Shop info floor (diy-shop-info)
    shopInfo: {
      contact: 'Contact',
    },

    // Marketing entry floor (diy-marketing-entry)
    marketingEntry: {
      empty: 'No entries',
    },

    // Member benefits floor (diy-member-benefits)
    memberBenefits: {
      empty: 'No member benefits configured',
      fallbackIcon: 'Gift',
    },

    // Service promise floor (diy-service-promise)
    servicePromise: {
      empty: 'No service promises configured',
    },

    // Custom HTML floor (diy-custom-html)
    customHtml: {
      empty: 'No content',
    },
  },
}
