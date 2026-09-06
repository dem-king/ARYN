import type { Component } from 'vue'

import DiyBottomNav from './diy-bottom-nav/index.vue'
import DiyCategoryNav from './diy-category-nav/index.vue'
import DiyCountdown from './diy-countdown/index.vue'
import DiyCouponCombo from './diy-coupon-combo/index.vue'
import DiyCouponReceive from './diy-coupon-receive/index.vue'
import DiyGap from './diy-gap/index.vue'
import DiyGoods from './diy-goods/index.vue'
import DiyGoodsGroup from './diy-goods-group/index.vue'
import DiyGoodsRanking from './diy-goods-ranking/index.vue'
import DiyGoodsWaterfall from './diy-goods-waterfall/index.vue'
import DiyImage from './diy-image/index.vue'
import DiyLimitedActivity from './diy-limited-activity/index.vue'
import DiyMarketingEntry from './diy-marketing-entry/index.vue'
import DiyMemberBenefits from './diy-member-benefits/index.vue'
import DiyNotice from './diy-notice/index.vue'
import DiyRichText from './diy-rich-text/index.vue'
import DiySearchBar from './diy-search-bar/index.vue'
import DiyServicePromise from './diy-service-promise/index.vue'
import DiyShopInfo from './diy-shop-info/index.vue'
import DiySwiperBanner from './diy-swiper-banner/index.vue'
import DiyTabnav from './diy-tabnav/index.vue'
import DiyTitleText from './diy-titletext/index.vue'
import DiyVideoLive from './diy-video-live/index.vue'
import UnknownComponent from './unknown-component.vue'

export const mobileComponentTypes = [
  'category-nav',
  'coupon-receive',
  'gap',
  'goods',
  'image-ad',
  'notice',
  'rich-text',
  'search-bar',
  'swiper-banner',
  'tab-nav',
  'title-text',
  'goods-group',
  'goods-ranking',
  'limited-activity',
  'countdown',
  'marketing-entry',
  'shop-info',
  'goods-waterfall',
  'coupon-combo',
  'member-benefits',
  'service-promise',
  'bottom-nav',
  'video-live',
] as const

export type MobileComponentType = typeof mobileComponentTypes[number]

const componentRegistry: Record<MobileComponentType, Component> = {
  'category-nav': DiyCategoryNav,
  'coupon-receive': DiyCouponReceive,
  'gap': DiyGap,
  'goods': DiyGoods,
  'image-ad': DiyImage,
  'notice': DiyNotice,
  'rich-text': DiyRichText,
  'search-bar': DiySearchBar,
  'swiper-banner': DiySwiperBanner,
  'tab-nav': DiyTabnav,
  'title-text': DiyTitleText,
  'goods-group': DiyGoodsGroup,
  'goods-ranking': DiyGoodsRanking,
  'limited-activity': DiyLimitedActivity,
  'countdown': DiyCountdown,
  'marketing-entry': DiyMarketingEntry,
  'shop-info': DiyShopInfo,
  'goods-waterfall': DiyGoodsWaterfall,
  'coupon-combo': DiyCouponCombo,
  'member-benefits': DiyMemberBenefits,
  'service-promise': DiyServicePromise,
  'bottom-nav': DiyBottomNav,
  'video-live': DiyVideoLive,
}

export function getDiyComponent(type: string): Component | undefined {
  return componentRegistry[type as MobileComponentType]
}
