<script setup lang="ts">
import { computed } from 'vue'

import { useAuthStore } from '@/store/authStore'

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
import { migratePageContent } from './schema/migrate'
import type { DecorationSection } from './schema/types'
import UnknownComponent from './unknown-component.vue'

const props = defineProps<{
  pageContentData: unknown
  pageName?: string
}>()

const authStore = useAuthStore()
const document = computed(() => migratePageContent(props.pageContentData))
const sections = computed(() =>
  document.value.sections.filter((section) => sectionVisible(section)),
)
const title = computed(
  () => document.value.page.navigation.title || props.pageName || '',
)
const pageStyle = computed(() => ({
  backgroundColor: document.value.page.backgroundColor,
  backgroundImage: document.value.page.backgroundImage
    ? `url(${document.value.page.backgroundImage})`
    : undefined,
}))

function sectionVisible(section: DecorationSection) {
  if (section.style.condition === 'login')
    return authStore.isLoggedIn
  if (section.style.condition === 'guest')
    return !authStore.isLoggedIn
  return true
}

function sectionStyle(section: DecorationSection) {
  const style: Record<string, string | number | undefined> = {
    backgroundColor: section.style.backgroundColor || undefined,
    backgroundImage: section.style.backgroundImage
      ? `url(${section.style.backgroundImage})`
      : undefined,
    paddingBottom: `${section.style.paddingY}rpx`,
    paddingTop: `${section.style.paddingY}rpx`,
  }
  if (section.style.sticky) {
    style.position = 'sticky'
    style.top = '0'
    style.zIndex = '10'
  }
  return style
}
</script>

<template>
  <view class="diy-page" :style="pageStyle">
    <hr-navbar
      v-if="document.page.navigation.visible"
      :title="title"
      :left-arrow="false"
    />
    <view class="diy-components">
      <view
        v-for="section in sections"
        :key="section.id"
        class="diy-section"
        :class="{ 'diy-section-scroll': section.style.horizontalScroll }"
        :style="sectionStyle(section)"
      >
        <view
          v-for="item in section.components"
          :key="item.id"
          class="diy-item"
          :class="{ 'diy-item-scroll': section.style.horizontalScroll }"
        >
        <DiyCategoryNav
          v-if="item.type === 'category-nav'"
          :show-data="item.props"
        />
        <DiyCouponReceive
          v-else-if="item.type === 'coupon-receive'"
          :show-data="item.props"
        />
        <DiyGap v-else-if="item.type === 'gap'" :show-data="item.props" />
        <DiyGoods v-else-if="item.type === 'goods'" :show-data="item.props" />
        <DiyImage
          v-else-if="item.type === 'image-ad'"
          :show-data="item.props"
        />
        <DiyNotice
          v-else-if="item.type === 'notice'"
          :show-data="item.props"
        />
        <DiyRichText
          v-else-if="item.type === 'rich-text'"
          :show-data="item.props"
        />
        <DiySearchBar
          v-else-if="item.type === 'search-bar'"
          :show-data="item.props"
        />
        <DiySwiperBanner
          v-else-if="item.type === 'swiper-banner'"
          :show-data="item.props"
        />
        <DiyTabnav
          v-else-if="item.type === 'tab-nav'"
          :show-data="item.props"
        />
        <DiyTitleText
          v-else-if="item.type === 'title-text'"
          :show-data="item.props"
        />
        <DiyGoodsGroup
          v-else-if="item.type === 'goods-group'"
          :show-data="item.props"
        />
        <DiyGoodsRanking
          v-else-if="item.type === 'goods-ranking'"
          :show-data="item.props"
        />
        <DiyLimitedActivity
          v-else-if="item.type === 'limited-activity'"
          :show-data="item.props"
        />
        <DiyCountdown
          v-else-if="item.type === 'countdown'"
          :show-data="item.props"
        />
        <DiyMarketingEntry
          v-else-if="item.type === 'marketing-entry'"
          :show-data="item.props"
        />
        <DiyShopInfo
          v-else-if="item.type === 'shop-info'"
          :show-data="item.props"
        />
        <DiyGoodsWaterfall
          v-else-if="item.type === 'goods-waterfall'"
          :show-data="item.props"
        />
        <DiyCouponCombo
          v-else-if="item.type === 'coupon-combo'"
          :show-data="item.props"
        />
        <DiyMemberBenefits
          v-else-if="item.type === 'member-benefits'"
          :show-data="item.props"
        />
        <DiyServicePromise
          v-else-if="item.type === 'service-promise'"
          :show-data="item.props"
        />
        <DiyBottomNav
          v-else-if="item.type === 'bottom-nav'"
          :show-data="item.props"
        />
        <DiyVideoLive
          v-else-if="item.type === 'video-live'"
          :show-data="item.props"
        />
        <UnknownComponent
          v-else
          :component-type="item.type"
          :show-data="item.props"
        />
        </view>
      </view>
    </view>
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.diy-page {
  min-height: 100vh;
  background-position: top center;
  background-repeat: no-repeat;
  background-size: 100% auto;
}

.diy-section-scroll {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
}

.diy-item-scroll {
  flex: 0 0 580rpx;
  width: 580rpx;
}
</style>
