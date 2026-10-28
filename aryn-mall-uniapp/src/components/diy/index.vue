<script setup lang="ts">
import type { DecorationSection } from './schema/types'

import { computed } from 'vue'
import { useAuthStore } from '@/store/authStore'
import { useMallThemeStore } from '@/store/mallThemeStore'

import { useUserStore } from '@/store/userStore'
import DiyActivityStrip from './diy-activity-strip/index.vue'
import DiyCategoryNav from './diy-category-nav/index.vue'
import DiyCountdown from './diy-countdown/index.vue'
import DiyCouponReceive from './diy-coupon-receive/index.vue'
import DiyCustomHtml from './diy-custom-html/index.vue'
import DiyGap from './diy-gap/index.vue'
import DiyGoodsGroup from './diy-goods-group/index.vue'
import DiyGoodsRanking from './diy-goods-ranking/index.vue'
import DiyGoodsRecommend from './diy-goods-recommend/index.vue'
import DiyGoodsScroll from './diy-goods-scroll/index.vue'
import DiyGoodsTabs from './diy-goods-tabs/index.vue'
import DiyGoodsWaterfall from './diy-goods-waterfall/index.vue'
import DiyGoods from './diy-goods/index.vue'
import DiyImageCube from './diy-image-cube/index.vue'
import DiyImage from './diy-image/index.vue'
import DiyMarketingEntry from './diy-marketing-entry/index.vue'
import DiyMemberBenefits from './diy-member-benefits/index.vue'
import DiyNewbieFloor from './diy-newbie-floor/index.vue'
import DiyNotice from './diy-notice/index.vue'
import DiyReplenishCard from './diy-replenish-card/index.vue'
import DiyRichText from './diy-rich-text/index.vue'
import DiySearchBar from './diy-search-bar/index.vue'
import DiyServicePromise from './diy-service-promise/index.vue'
import DiyShipWorkbench from './diy-ship-workbench/index.vue'
import DiyShopInfo from './diy-shop-info/index.vue'
import DiySwiperBanner from './diy-swiper-banner/index.vue'
import DiyTabnav from './diy-tabnav/index.vue'
import DiyTitleText from './diy-titletext/index.vue'
import DiyVideoLive from './diy-video-live/index.vue'
import { evaluateCondition } from './schema/condition'
import { resolveEffectivePageTheme } from './schema/effective-theme'
import { migratePageContent } from './schema/migrate'
import { resolveNavigationScrollSurface } from './schema/navigation-scroll'
import { resolveNavigationAppearance } from './schema/navigation-style'
import { buildSectionGradient, getSectionGradientAnchorColor } from './schema/section-gradient'
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
const mallThemeStore = useMallThemeStore()
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
// 页面有效主题：页面引用过主题（快照已被 migrate 合并进 page 配置）时保持
// 页面自身颜色；未引用主题的页面跟随商城默认主题——模板/存量页面写死的
// 品牌色（如种子模板的蓝色导航）不再遮住管理端的全局换肤。
// 「页面设置」关闭跟随（followMallTheme=false）同样走页面自存色，在解析器内处理
const effectiveTheme = computed(() =>
  resolveEffectivePageTheme(
    document.value.page,
    // 页面引用过主题：快照颜色已合并进 page 配置，不再叠加商城默认主题
    document.value.themePrimaryColor
      ? undefined
      : {
          navigationColor: mallThemeStore.navigationColor,
          navigationTextColor: mallThemeStore.navigationTextColor,
          pageBackgroundColor: mallThemeStore.pageBackgroundColor,
        },
  ),
)
// 导航栏外观：页面设置可选「跟随首个区块」，把首区块背景（渐变顶边起色 /
// 纯色）直接用作导航背景，运营不必再手动对齐导航色和区块渐变。
// 与编辑器画布、草稿预览共用同一解析函数（schema/navigation-style.ts）。
// 注意传入未过滤的 document.sections：导航取首个有内容的区块，不随登录态/
// 会员条件变化，避免同一页面不同用户看到不同导航色。
const navigationAppearance = computed(() =>
  resolveNavigationAppearance(
    document.value.page,
    effectiveTheme.value,
    document.value.sections,
  ),
)
// 滚动渐显（沉浸式导航）的静态配置：目标底色外观只随页面配置 / 主题变化。
// 这里**只算静态部分、不读滚动值**：滚动进度由 hr-navbar 自己 inject 的
// scrollTop 计算，DiyPage 不消费滚动状态，滚动时全部装修楼层都不重渲染。
const navigationScrollEffect = computed(
  () => document.value.page.navigation.scrollEffect,
)
const navigationScrollSurface = computed(() =>
  resolveNavigationScrollSurface(
    document.value.page,
    effectiveTheme.value,
    navigationAppearance.value,
  ),
)
const stickyTop = computed(() => {
  if (props.embedded || !document.value.page.navigation.visible)
    return 0
  const { statusBarHeight = 0 } = uni.getWindowInfo()
  return statusBarHeight + 44
})

/** 搜索框行高（px）：与 diy-search-bar 的固定 CSS 同口径（行内上下 padding 6px*2 + 输入条 36px） */
const SEARCH_BAR_ROW_HEIGHT = 48

function normalizeDiyLength(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

/**
 * 常驻搜索框：整页 DIY + 首个渲染区块的第一个组件是 search-bar + 组件配置 sticky=true。
 *
 * 只认这个位置：搜索框必须位于页面内容的最顶端，才能用「导航栏高度 + 首区块上边距 /
 * 上内边距 + 搜索框自身外边距」静态算出常驻层的顶边（不做异步测距）。其余位置
 * （区块中段 / 非首个区块 / 内嵌装修块）一律回退为原来的随页滚动行为，现有页面零变化。
 */
const stickySearch = computed(() => {
  if (props.embedded)
    return null
  const first = sections.value[0]
  if (!first || first.style.horizontalScroll)
    return null
  const component = first.components[0]
  if (!component || component.type !== 'search-bar')
    return null
  const showData = component.props as Record<string, unknown> | undefined
  if (!showData || showData.sticky !== true)
    return null
  const common = (showData.commonStyle ?? {}) as Record<string, unknown>
  const paddingTop = normalizeDiyLength(common.styleTopPadding)
  const paddingBottom = normalizeDiyLength(common.styleBottomPadding)
  return {
    id: component.id,
    props: showData,
    /** 常驻层顶边（视口坐标）：导航栏占位高 + 首区块上外边距 + 上内边距 */
    top: stickyTop.value + first.style.marginY + first.style.paddingY,
    /** 常驻层左右避让：首区块左右外边距 + 左右内边距 */
    insetX: first.style.marginX + first.style.paddingX,
    hostBackground: sectionAmbientColor(first),
    /**
     * 常驻层占用的纵向高度（相对导航栏下沿）：首区块上边距/上内边距 + 搜索框上外边距
     * + 上内边距 + 行高 + 下内边距。吸顶 Tab 栏 / 吸顶区块按它下移，不会被搜索框盖住。
     */
    occupiedHeight: first.style.marginY
      + first.style.paddingY
      + normalizeDiyLength(common.styleTopMargin)
      + paddingTop
      + SEARCH_BAR_ROW_HEIGHT
      + paddingBottom,
  }
})

const stickySearchId = computed(() => stickySearch.value?.id ?? '')

/**
 * 吸顶基准线：有固定导航栏时让出导航栏；常驻搜索框出现后再让出一条搜索栏高度，
 * 吸顶 Tab 栏与吸顶区块都停在搜索框下沿。
 */
const stickyOffsetTop = computed(
  () => stickyTop.value + (stickySearch.value?.occupiedHeight ?? 0),
)
// 主题色浅色面：主色追加 9% 透明（hex 8 位），服务承诺圆底等 tint 消费。
// CSS 无法从 var() 派生透明度（小程序不支持 color-mix），只能两端同口径预计算；
// 非 6 位 hex 不下发，组件侧走自己的 rgba 兜底。管理端画布在
// phone-canvas / preview-canvas 挂同名变量（theme-presets.withAlpha 同口径）。
function primarySoft(primaryColor: string | undefined): string | undefined {
  return primaryColor && /^#[\dA-F]{6}$/i.test(primaryColor)
    ? `${primaryColor}17`
    : undefined
}

/**
 * 沉浸式导航是否生效：整页 DIY + 导航可见 + 页面开了滚动渐显。
 *
 * 只用于「把导航的目标底色材质同步给吸顶 Tab 栏」这类同层 UI，
 * 不参与滚动进度计算（进度仍由 hr-navbar 自己 inject）。
 */
const immersiveNavEnabled = computed(
  () =>
    !props.embedded
    && document.value.page.navigation.visible
    && document.value.page.navigation.scrollEffect?.mode === 'fade',
)

/**
 * 目标底色 + 目标态 alpha → CSS rgba()。
 *
 * 吸顶 Tab 栏与 hr-navbar 共用同一材质：导航的磨砂面是 rgba(.86) + blur，
 * 这里必须拼出同口径颜色，否则两块半透明层叠在一起会一深一浅。
 */
function toSurfaceCssColor(color: string, alpha: number): string {
  const value = color.trim()
  const hex = /^#([\da-f]{3}|[\da-f]{6})$/i.exec(value)
  if (hex) {
    const raw = hex[1] ?? ''
    const base = raw.length === 3
      ? raw.split('').map(char => char + char).join('')
      : raw
    const channels = [0, 2, 4].map(index => Number.parseInt(base.slice(index, index + 2), 16))
    return `rgba(${channels[0]}, ${channels[1]}, ${channels[2]}, ${alpha})`
  }
  const rgb = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/i.exec(value)
  if (!rgb)
    return `rgba(255, 255, 255, ${alpha})`
  return `rgba(${rgb[1]}, ${rgb[2]}, ${rgb[3]}, ${alpha})`
}

const pageStyle = computed(() => ({
  'backgroundColor': effectiveTheme.value.backgroundColor,
  'backgroundImage': document.value.page.backgroundImage
    ? `url(${document.value.page.backgroundImage})`
    : undefined,
  // Tab 商品流的吸顶偏移：整页装修有固定导航栏，sticky 需要让出状态栏 + 导航高度；
  // 首区块搜索框开了「滚动常驻」时还要让出常驻搜索栏那一条；内嵌页/隐藏导航栏时为 0
  '--diy-sticky-top': `${stickyOffsetTop.value}px`,
  // 沉浸式导航的目标底色材质（磨砂白 / 主题色 / 自定义色）：吸顶 Tab 栏与
  // 顶栏共用，避免「顶栏磨砂白、Tab 栏纯白」出现一条材质分界。
  ...(immersiveNavEnabled.value
    ? {
        '--diy-nav-surface-bg': toSurfaceCssColor(
          navigationScrollSurface.value.color,
          navigationScrollSurface.value.alpha,
        ),
        '--diy-nav-surface-blur': navigationScrollSurface.value.blur
          ? 'blur(18px) saturate(160%)'
          : 'none',
        '--diy-nav-hairline': 'rgba(15, 23, 42, 0.08)',
      }
    : {}),
  // 有效主色 = 页面固化主题 > 商城默认主题，与 ConfigProvider 下发的
  // --wot-color-theme-primary 同源，保证浅色面跟随实际生效的主题色
  '--wot-color-theme-primary-soft': primarySoft(
    document.value.themePrimaryColor || mallThemeStore.primaryColor,
  ),
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

/**
 * 区块内组件脚下的「环境底色」：搜索栏铃铛/扫码这类直接坐在区块背景上的
 * 图标需要按底色自动取可读色。渐变取顶边起色（与导航栏「跟随首个区块」
 * 同口径）、纯色取背景色；都没有时回落页面有效背景色。
 */
function sectionAmbientColor(section: DecorationSection): string {
  const gradient = section.style.backgroundImage
    ? undefined
    : section.style.backgroundGradient
  const anchor
    = getSectionGradientAnchorColor(gradient) || section.style.backgroundColor
  if (anchor && anchor !== 'transparent')
    return anchor
  return effectiveTheme.value.backgroundColor || '#ffffff'
}

function sectionStyle(section: DecorationSection) {
  const hasBackgroundImage = Boolean(section.style.backgroundImage)
  // 背景图优先；其次渐变；最后纯色。渐变塞进 backgroundImage 而不是
  // background 简写，保留 backgroundColor 作为半透明渐变的垫底色。
  const gradient = hasBackgroundImage
    ? undefined
    : buildSectionGradient(section.style.backgroundGradient)
  const style: Record<string, string | number | undefined> = {
    backgroundColor: section.style.backgroundColor || undefined,
    backgroundImage: hasBackgroundImage
      ? `url(${section.style.backgroundImage})`
      : gradient,
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
    // 横滑区块靠 overflow-x: auto 滚动，裁切会禁掉横滑，故跳过；
    // 含吸顶 Tab 的区块同样跳过：overflow 会创建滚动容器，position: sticky 失效
    const hasStickyTabs = section.components.some(
      item =>
        item.type === 'goods-tabs'
        && Boolean((item.props as Record<string, unknown> | undefined)?.stickyTabs),
    )
    if (!hasStickyTabs && !section.style.horizontalScroll)
      style.overflow = 'hidden'
  }
  // 区块背景图可能自带圆角/留白，补齐平铺属性，否则会按默认 tile 重复
  if (hasBackgroundImage) {
    style.backgroundPosition = 'top center'
    style.backgroundRepeat = 'no-repeat'
    style.backgroundSize = '100% auto'
  }
  if (section.style.sticky) {
    style.position = 'sticky'
    // 让出固定导航栏：整页 DIY 的 wd-navbar 是 fixed 覆盖层（状态栏 + 44px），
    // top:0 会把区块吸到导航栏底下、顶部内容（如品牌区搜索栏）被永久遮住；
    // 首区块搜索框开了「滚动常驻」时，还要再让出常驻搜索栏那一条；
    // 内嵌页/隐藏导航栏时为 0，行为与之前一致。
    style.top = `${stickyOffsetTop.value}px`
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
      :bordered="false"
      :background-color="navigationAppearance.backgroundColor"
      :background-image="navigationAppearance.backgroundImage"
      :text-color="navigationAppearance.textColor"
      :scroll-effect="navigationScrollEffect"
      :scroll-surface="navigationScrollSurface"
      :hairline="!stickySearch"
    />
    <!--
      常驻搜索栏：首区块第一个搜索框开启「滚动常驻」后，由页面级渲染一份固定层
      （滚动进度只在该组件内部消费），区块里那份退化为只占高度的占位实例。
    -->
    <DiySearchBar
      v-if="stickySearch"
      pinned
      :show-data="stickySearch.props"
      :host-background="stickySearch.hostBackground"
      :pinned-top="stickySearch.top"
      :pinned-inset-x="stickySearch.insetX"
      :nav-appearance="navigationAppearance"
      :scroll-effect="navigationScrollEffect"
      :scroll-surface="navigationScrollSurface"
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
            :host-background="sectionAmbientColor(section)"
            :sticky-placeholder="stickySearchId === item.id"
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
          <DiyCountdown
            v-else-if="item.type === 'countdown'"
            :show-data="item.props"
          />
          <DiyNewbieFloor
            v-else-if="item.type === 'newbie-floor'"
            :show-data="item.props"
          />
          <DiyMarketingEntry
            v-else-if="item.type === 'marketing-entry'"
            :show-data="item.props"
          />
          <DiyActivityStrip
            v-else-if="item.type === 'activity-strip'"
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
          <DiyGoodsTabs
            v-else-if="item.type === 'goods-tabs'"
            :show-data="item.props"
            :immersive-nav="immersiveNavEnabled"
          />
          <DiyMemberBenefits
            v-else-if="item.type === 'member-benefits'"
            :show-data="item.props"
          />
          <DiyServicePromise
            v-else-if="item.type === 'service-promise'"
            :show-data="item.props"
          />
          <DiyVideoLive
            v-else-if="item.type === 'video-live'"
            :show-data="item.props"
          />
          <DiyGoodsRecommend
            v-else-if="item.type === 'goods-recommend'"
            :show-data="item.props"
            :goods-id="goodsId"
          />
          <DiyImageCube
            v-else-if="item.type === 'image-cube'"
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
