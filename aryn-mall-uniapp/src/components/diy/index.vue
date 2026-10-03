<script setup lang="ts">
import type { DecorationSection } from './schema/types'

import { computed } from 'vue'
import { useAuthStore } from '@/store/authStore'

import { useUserStore } from '@/store/userStore'
import DiyBottomNav from './diy-bottom-nav/index.vue'
import DiyCategoryNav from './diy-category-nav/index.vue'
import DiyCountdown from './diy-countdown/index.vue'
import DiyCouponCombo from './diy-coupon-combo/index.vue'
import DiyCouponReceive from './diy-coupon-receive/index.vue'
import DiyCustomHtml from './diy-custom-html/index.vue'
import DiyDiscount from './diy-discount/index.vue'
import DiyGap from './diy-gap/index.vue'
import DiyGoodsGroup from './diy-goods-group/index.vue'
import DiyGoodsRanking from './diy-goods-ranking/index.vue'
import DiyGoodsScroll from './diy-goods-scroll/index.vue'
import DiyGoodsWaterfall from './diy-goods-waterfall/index.vue'
import DiyGoods from './diy-goods/index.vue'
import DiyImage from './diy-image/index.vue'
import DiyLimitedActivity from './diy-limited-activity/index.vue'
import DiyMarketingEntry from './diy-marketing-entry/index.vue'
import DiyMemberBenefits from './diy-member-benefits/index.vue'
import DiyNotice from './diy-notice/index.vue'
import DiyReplenishCard from './diy-replenish-card/index.vue'
import DiyRichText from './diy-rich-text/index.vue'
import DiySearchBar from './diy-search-bar/index.vue'
import DiySeckill from './diy-seckill/index.vue'
import DiyServicePromise from './diy-service-promise/index.vue'
import DiyShipWorkbench from './diy-ship-workbench/index.vue'
import DiyShopInfo from './diy-shop-info/index.vue'
import DiySwiperBanner from './diy-swiper-banner/index.vue'
import DiyTabnav from './diy-tabnav/index.vue'
import DiyTitleText from './diy-titletext/index.vue'
import DiyVideoLive from './diy-video-live/index.vue'
import { evaluateCondition } from './schema/condition'
import { migratePageContent } from './schema/migrate'
import UnknownComponent from './unknown-component.vue'

const props = defineProps<{
  pageContentData: unknown
  pageName?: string
  /** 当前商品 ID（商详页装修场景透传，供商品类组件感知上下文） */
  goodsId?: string
  /**
   * 内嵌模式：装修块作为页面中间的一段内容使用（分类页 / 商详页 / 个人中心页），
   * 而非整页 DIY。
   *
   * 两处差别都由本开关控制，缺一不可：
   *   · **不渲染 hr-navbar**：装修 Schema 的 `navigation.visible` 默认 true 且存量
   *     数据全是 true，内嵌场景原样渲染会在页面中间横插一条导航栏；
   *   · **不撑满 100vh**：整页 DIY 需要 `min-height: 100vh` 保证背景铺满，
   *     内嵌到商品流里会撑出一屏空白，把后面的商品挤到屏幕外。
   */
  embedded?: boolean
}>()

const authStore = useAuthStore()
const userStore = useUserStore()
const document = computed(() => migratePageContent(props.pageContentData))
// 空区块（没有组件）不渲染：删空组件的区块只剩一张空样式卡（背景/圆角/内边距），
// 实机会出现一条莫名的灰条占位；与管理端画布、预览画布同口径。
const sections = computed(() =>
  document.value.sections.filter(
    section =>
      section.components.length > 0 && sectionVisible(section),
  ),
)
const title = computed(
  () => document.value.page.navigation.title || props.pageName || '',
)
const pageStyle = computed(() => ({
  backgroundColor: document.value.page.backgroundColor,
  backgroundImage: document.value.page.backgroundImage
    ? `url(${document.value.page.backgroundImage})`
    : undefined,
  // 页面级主题（发布固化的 themeSnapshot）在 diy 内容区覆盖全局商城主题：
  // 内联 CSS 变量沿节点树继承，区块内组件的 var(--wot-color-theme-*) 会读到页面值；
  // 未引用主题时不下发，回落 App.ku.vue 的全局商城默认主题
  ...(document.value.themePrimaryColor
    ? {
        '--wot-color-theme-primary': document.value.themePrimaryColor,
        '--wot-color-theme-secondary': document.value.themeSecondaryColor
          ?? document.value.themePrimaryColor,
      }
    : {}),
}))

function sectionVisible(section: DecorationSection) {
  return evaluateCondition(section.style.condition, {
    isLoggedIn: authStore.isLoggedIn,
    memberLevelId: userStore.getLevelId,
    userTags: userStore.getUserTags,
  })
}

function sectionStyle(section: DecorationSection) {
  const style: Record<string, string | number | undefined> = {
    backgroundColor: section.style.backgroundColor || undefined,
    backgroundImage: section.style.backgroundImage
      ? `url(${section.style.backgroundImage})`
      : undefined,
    // 长度字段口径为 px（见 SectionStyle 注释），与管理端画布一致
    marginBottom: `${section.style.marginY}px`,
    marginLeft: `${section.style.marginX}px`,
    marginRight: `${section.style.marginX}px`,
    marginTop: `${section.style.marginY}px`,
    paddingBottom: `${section.style.paddingY}px`,
    paddingLeft: `${section.style.paddingX}px`,
    paddingRight: `${section.style.paddingX}px`,
    paddingTop: `${section.style.paddingY}px`,
  }
  if (section.style.radius > 0) {
    style.borderRadius = `${section.style.radius}px`
    // 圆角需要裁掉子元素溢出的直角背景（轮播图/图片类组件自带白底）；
    // 横滑区块靠 overflow-x: auto 滚动，裁切会禁掉横滑，故跳过。
    if (!section.style.horizontalScroll)
      style.overflow = 'hidden'
  }
  // 区块背景图可能自带圆角/留白，补齐平铺属性，否则会按默认 tile 重复
  if (section.style.backgroundImage) {
    style.backgroundPosition = 'top center'
    style.backgroundRepeat = 'no-repeat'
    style.backgroundSize = '100% auto'
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
  <view class="diy-page" :class="{ 'diy-page--embedded': embedded }" :style="pageStyle">
    <hr-navbar
      v-if="!embedded && document.page.navigation.visible"
      :title="title"
      :left-arrow="false"
      :background-color="document.page.navigation.backgroundColor"
      :text-color="document.page.navigation.textColor"
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
          <DiyCustomHtml
            v-else-if="item.type === 'custom-html'"
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
          <DiyGoodsScroll
            v-else-if="item.type === 'goods-scroll'"
            :show-data="item.props"
          />
          <DiyLimitedActivity
            v-else-if="item.type === 'limited-activity'"
            :show-data="item.props"
          />
          <DiySeckill
            v-else-if="item.type === 'seckill'"
            :show-data="item.props"
          />
          <DiyCountdown
            v-else-if="item.type === 'countdown'"
            :show-data="item.props"
          />
          <DiyDiscount
            v-else-if="item.type === 'discount'"
            :show-data="item.props"
          />
          <DiyMarketingEntry
            v-else-if="item.type === 'marketing-entry'"
            :show-data="item.props"
          />
          <DiyShipWorkbench
            v-else-if="item.type === 'ship-workbench'"
            :show-data="item.props"
          />
          <DiyReplenishCard
            v-else-if="item.type === 'replenish-card'"
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

  /* 内嵌模式不撑满可视区：装修块只是页面中的一段内容（见 embedded 属性说明） */
  &--embedded {
    min-height: 0;
  }
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
