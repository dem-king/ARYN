<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { DIY_NAVBAR_SCROLL_KEY } from '@/composables/useDiyNavbarScroll'
import { FROSTED_NAVBAR_KEY } from '@/composables/useFrostedNavbar'
import { canGoBackFromCurrentPage } from '@/utils/navigation-lock'

interface Props {
  modelValue: string
  placeholder?: string
  placeholderLeft?: boolean
  bordered?: boolean
  leftArrow?: boolean
  transparent?: boolean
  scrollTop?: number
  navPlaceholder?: boolean
  disabled?: boolean
  focus?: boolean
  bgColor?: string
  fontColor?: string
  primary?: boolean
  gradient?: boolean
  searchBtn?: boolean
  /**
   * 强制磨砂玻璃态：浮层展开时顶栏与浮层要用同一块玻璃。
   * 只靠滚动状态的话，页面停在顶部时顶栏还是实心白，会和玻璃浮层分家。
   */
  forceFrosted?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '',
  placeholderLeft: false,
  bordered: true,
  leftArrow: true,
  transparent: false,
  scrollTop: 0,
  navPlaceholder: true,
  disabled: false,
  focus: false,
  bgColor: '#ffffff',
  fontColor: '#000000',
  primary: false,
  gradient: true,
  searchBtn: true,
  forceFrosted: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'search', value: string): void
  (e: 'focus'): void
}>()

const router = useRouter()
const { t } = useI18n()
const searchValue = ref(props.modelValue)
/** 未传 placeholder 时回落到与当前语言一致的默认文案 */
const placeholderText = computed(() => props.placeholder || t('search.inputPlaceholder'))
const isCanBack = ref(true)
const capsulePaddingRight = ref(0)
const statusBarHeight = ref(0)
const navBarHeight = ref(44)
const capsuleCenterOffset = ref(0)
const maxScroll = 100

/** 页面滚动状态 / 业务页磨砂开关：均由宿主页的 useFrostedNavbar() provide */
const pageScrolled = inject(DIY_NAVBAR_SCROLL_KEY, null)
const frostedScroll = inject(FROSTED_NAVBAR_KEY, false)

/**
 * 磨砂玻璃态：业务页开着磨砂开关且页面已滚动；浮层展开时由 forceFrosted 强制开启。
 *
 * transparent（按滚动距离渐显白底）与 primary（品牌色底）是两条既定材质，
 * 这里不接管，避免同一根栏上叠加两套底色逻辑。
 */
const frosted = computed(
  () =>
    !props.transparent
    && !props.primary
    && (props.forceFrosted || (frostedScroll && pageScrolled?.value === true)),
)

/**
 * 滚动后磨砂整条生效：输入框融进同一块玻璃，不再单独画一层胶囊。
 *
 * 只认「页面滚动」这条路径：forceFrosted（浮层展开）时页面可能仍在顶部，
 * 搜索框需要保留独立轮廓维持可点识别。
 */
const blended = computed(() => frosted.value && pageScrolled?.value === true)
watch(() => props.modelValue, (val) => {
  searchValue.value = val
})

/**
 * 能否返回：每次点击现算，不再只用挂载时的快照。
 *
 * 与 hr-navbar 同口径：页面栈可能在挂载后被 replaceAll / redirectTo 改写，
 * 快照失真会让返回键在首屏调 navigateBack（必然失败）或误退回首页。
 */
function syncBackIcon() {
  isCanBack.value = canGoBackFromCurrentPage()
}

onShow(syncBackIcon)

onMounted(() => {
  syncBackIcon()
  const sys = uni.getWindowInfo()
  statusBarHeight.value = sys.statusBarHeight || 0
  // #ifdef MP-WEIXIN
  const rect = uni.getMenuButtonBoundingClientRect()
  const rightSpace = sys.windowWidth - rect.right
  capsulePaddingRight.value = rightSpace + rect.width
  navBarHeight.value = 44
  const barHeight = rect.bottom - rect.top
  const capsuleCenter = rect.top + barHeight / 2
  capsuleCenterOffset.value = capsuleCenter - (statusBarHeight.value + navBarHeight.value / 2)
  // #endif
  // #ifndef MP-WEIXIN
  navBarHeight.value = 44
  capsuleCenterOffset.value = 0
  // #endif
})

/**
 * 导航栏占位高度（状态栏 + 44px 内容区）。
 *
 * 只用于页面内等高占位。**浮层贴边不要用它**：它不含小程序胶囊的纵向偏移，
 * 也不含底部分隔线，按它贴边会在导航栏下沿漏出一条透出遮罩的缝。
 */
const placeholderHeight = computed(() => statusBarHeight.value + navBarHeight.value)

/**
 * 导航栏**实际渲染**高度（含胶囊纵向偏移与底部分隔线）。
 *
 * 供「需要从导航栏下沿开始渲染」的浮层使用（如分类页的全部分类面板）。
 * 微信里 capsuleCenterOffset 可能为负，导航栏实际高度会比「状态栏 + 44」略矮，
 * 浮层若按占位高度贴边，两块之间就会留下一根深色细线（遮罩从缝里透出来）。
 * 底部分隔线由 bordered 控制：面板展开时导航栏会关掉分隔线，高度同步减 1px。
 */
const visualHeight = computed(
  () => placeholderHeight.value + capsuleCenterOffset.value + (props.bordered ? 1 : 0),
)

function updateValue(val: string) {
  searchValue.value = val
  emit('update:modelValue', val)
}
function handleSearch(val: string) {
  emit('search', val)
}
function handleFocus() {
  emit('focus')
}
function handleDisabledClick() {
  if (props.disabled)
    handleSearch(searchValue.value)
}
function handleLeftClick() {
  if (canGoBackFromCurrentPage()) {
    // 连点由 router 实例上的 navigation lock 兜底，避免连退两层
    router.back()
  }
  else {
    router.pushTab({ name: 'home' })
  }
}

const dynamicStyle = computed(() => {
  let style = ''
  if (frosted.value) {
    // 磨砂玻璃：与 hr-navbar 的磨砂白目标态同一组数值（86% 白 + blur 18px）
    style += 'background-color: rgba(255, 255, 255, 0.86);'
    style += 'color: #222222;'
  }
  else if (props.transparent) {
    const opacity = Math.min(props.scrollTop / maxScroll, 1)
    style += `background-color: rgba(255, 255, 255, ${opacity});`
    style += `color: ${opacity > 0.5 ? '#000' : '#fff'};`
  }
  else if (props.primary && props.gradient) {
    style += 'background: linear-gradient(to right, var(--wot-color-theme-primary), var(--wot-color-theme-secondary));'
    style += 'color: #fff;'
  }
  else if (props.primary) {
    style += 'background-color: var(--wot-color-theme-primary);'
    style += 'color: #fff;'
  }
  else if (props.bgColor) {
    style += `background-color: ${props.bgColor};`
    style += `color: ${props.fontColor || ''};`
  }
  else {
    style += `color: ${props.fontColor || ''};`
  }
  return style
})

const titleColor = computed(() => {
  if (frosted.value)
    return '#222222'
  if (props.transparent) {
    const opacity = Math.min(props.scrollTop / maxScroll, 1)
    return opacity > 0.5 ? '#000000' : '#ffffff'
  }
  if (props.primary)
    return '#ffffff'
  return props.fontColor || '#000000'
})

defineExpose({ placeholderHeight, visualHeight })
</script>

<template>
  <view
    class="fixed left-0 right-0 top-0 z-1000"
    :class="{ 'hr-search-navbar--frosted': frosted }"
    :style="dynamicStyle"
  >
    <view :style="{ paddingTop: `${statusBarHeight + capsuleCenterOffset}px`, height: `${navBarHeight}px`, paddingRight: `${capsulePaddingRight}px` }" class="flex items-center justify-between px-12rpx">
      <view v-if="leftArrow" class="w-68rpx flex items-center justify-center">
        <wd-icon
          :color="titleColor"
          :name="isCanBack ? 'arrow-left' : 'home1'"
          size="22px"
          @click="handleLeftClick"
        />
      </view>

      <view class="mx-10rpx min-w-0 flex-1">
        <view
          class="search-navbar-field box-border h-36px min-w-0 flex items-center rounded-20rpx py-4rpx pl-20rpx pr-8rpx"
          :class="{ 'search-navbar-field--blended': blended }"
          @click="handleDisabledClick"
        >
          <text class="i-carbon:search mr-8rpx text-16px text-gray-500" />
          <input
            v-if="!disabled"
            class="flex-1 rounded-16rpx bg-transparent px-8rpx py-4rpx text-28rpx text-[#333]"
            type="text"
            :value="searchValue"
            :placeholder="placeholderText"
            :focus="focus"
            confirm-type="search"
            @input="(e:any) => updateValue(e.detail?.value ?? e.target?.value)"
            @confirm="handleSearch(searchValue)"
            @focus="handleFocus"
          >
          <view v-else class="min-w-0 flex flex-1">
            <view
              class="min-w-0 py-6rpx"
              :class="searchValue ? 'text-[#333]' : 'text-gray-400'"
            >
              <view v-if="searchValue" class="min-w-0 flex items-center rounded-6rpx bg-gray-100 px-20rpx py-4rpx text-26rpx">
                <view class="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap">
                  {{ searchValue }}
                </view>
                <wd-icon name="close" size="20rpx" custom-class="ml-4rpx flex-none" color="#6b7280" />
              </view>
              <view v-else class="text-28rpx">
                {{ placeholderText }}
              </view>
            </view>
          </view>

          <view
            v-if="searchBtn"
            class="ml-8rpx h-28px flex flex-none items-center rounded-14rpx bg-primary px-24rpx text-26rpx text-white"
            @click.stop="handleSearch(searchValue)"
          >
            {{ t('search.searchButton') }}
          </view>
        </view>
      </view>

      <view class="flex items-center">
        <slot name="right" />
      </view>
    </view>
    <view
      v-if="bordered"
      class="h-1px bg-[#eaeaea]"
      :style="{ backgroundColor: frosted ? 'rgba(15, 23, 42, 0.08)' : '' }"
    />
  </view>
  <view v-if="navPlaceholder" :style="{ height: `${placeholderHeight}px` }" />
</template>

<style lang="scss" scoped>
/**
 * 搜索输入框：默认保持与改造前一致的浅灰实心胶囊。
 *
 * 页面滚动、整条顶栏切磨砂后由 --blended 撤掉底色与描边，
 * 输入框融进同一块玻璃，整块头部不再出现「磨砂白头上贴一层胶囊」的分层。
 */
.search-navbar-field {
  border: 1px solid #ececf0;
  background-color: #f5f6f8;
}

/**
 * 滚动后磨砂态：输入框融进整条磨砂顶栏，不再单独画一层胶囊。
 * 透明描边保留 1px，状态切换时内容不跳位。
 */
.search-navbar-field--blended {
  border-color: transparent;
  background-color: transparent;
}

/**
 * 磨砂玻璃态：与 hr-navbar 的磨砂白目标态共用 blur 数值。
 * 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的
 * backdrop-filter）；不支持 backdrop-filter 的内核退化为 86% 白底，图标/搜索框仍可读。
 */
.hr-search-navbar--frosted {
  -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%);
}
</style>
