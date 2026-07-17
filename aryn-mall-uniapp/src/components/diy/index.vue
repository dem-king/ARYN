<script setup lang="ts">
import { computed } from 'vue'

import DiyCategoryNav from './diy-category-nav/index.vue'
import DiyCountdown from './diy-countdown/index.vue'
import DiyCouponReceive from './diy-coupon-receive/index.vue'
import DiyGap from './diy-gap/index.vue'
import DiyGoods from './diy-goods/index.vue'
import DiyGoodsGroup from './diy-goods-group/index.vue'
import DiyGoodsRanking from './diy-goods-ranking/index.vue'
import DiyImage from './diy-image/index.vue'
import DiyLimitedActivity from './diy-limited-activity/index.vue'
import DiyMarketingEntry from './diy-marketing-entry/index.vue'
import DiyNotice from './diy-notice/index.vue'
import DiyRichText from './diy-rich-text/index.vue'
import DiySearchBar from './diy-search-bar/index.vue'
import DiyShopInfo from './diy-shop-info/index.vue'
import DiySwiperBanner from './diy-swiper-banner/index.vue'
import DiyTabnav from './diy-tabnav/index.vue'
import DiyTitleText from './diy-titletext/index.vue'
import { migratePageContent } from './schema/migrate'
import UnknownComponent from './unknown-component.vue'

const props = defineProps<{
  pageContentData: unknown
  pageName?: string
}>()

const document = computed(() => migratePageContent(props.pageContentData))
const components = computed(() => document.value.components)
const title = computed(
  () => document.value.page.navigation.title || props.pageName || '',
)
const pageStyle = computed(() => ({
  backgroundColor: document.value.page.backgroundColor,
  backgroundImage: document.value.page.backgroundImage
    ? `url(${document.value.page.backgroundImage})`
    : undefined,
}))
</script>

<template>
  <view class="diy-page" :style="pageStyle">
    <hr-navbar
      v-if="document.page.navigation.visible"
      :title="title"
      :left-arrow="false"
    />
    <view class="diy-components">
      <view v-for="item in components" :key="item.id">
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
        <UnknownComponent
          v-else
          :component-type="item.type"
          :show-data="item.props"
        />
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
</style>
