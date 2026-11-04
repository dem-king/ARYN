<script setup lang="ts">
import type { NavigationScrollAppearance, NavigationSurfaceAppearance } from '@/components/diy/schema/navigation-scroll'
import type { NavigationAppearance } from '@/components/diy/schema/navigation-style'
import type { NavigationScrollEffect } from '@/components/diy/schema/types'

import { computed, inject, onMounted, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'

import {
  FROSTED_SCROLL_EFFECT,
  FROSTED_SCROLL_SURFACE,
  FROSTED_SCROLL_SURFACE_DARK,
  resolveNavigationScrollAppearance,
} from '@/components/diy/schema/navigation-scroll'
import { DIY_NAVBAR_SCROLL_KEY } from '@/composables/useDiyNavbarScroll'
import { FROSTED_NAVBAR_KEY } from '@/composables/useFrostedNavbar'
import { useMallThemeStore } from '@/store/mallThemeStore'
import { canGoBackFromCurrentPage } from '@/utils/navigation-lock'
import { toNativeNavigationBarHexColor } from '@/utils/navigation-bar-color'

interface Props {
  title?: string
  bordered?: boolean
  leftArrow?: boolean
  /**
   * 导航栏底色：装修页传入页面设置的 navigation.backgroundColor。
   * 缺省不注入样式，维持 wot 默认白底（其余调用方行为不变）。
   */
  backgroundColor?: string
  /** 底色非白时的前景色（标题/图标），如品牌色页头配白字 */
  textColor?: string
  /**
   * 导航栏背景图/渐变：装修页「导航栏背景跟随首个区块」在左右渐变时
   * 整段复用首区块的 linear-gradient，接缝两侧逐列同色。
   * 缺省不注入，其余调用方行为不变。
   */
  backgroundImage?: string
  /**
   * 沉浸式导航（滚动后一步切换目标底色）配置：仅装修页 DiyPage 传入。
   * 缺省/`mode='none'` 时走旧逻辑 —— 30+ 业务页的渲染结果必须逐像素不变。
   */
  scrollEffect?: NavigationScrollEffect | null
  /**
   * 滚动目标底色外观（静态解析结果）：由 DiyPage 用 resolveNavigationScrollSurface
   * 算好再下传，导航组件自己拿不到 PageSettings/主题，不做重复解析。
   */
  scrollSurface?: NavigationSurfaceAppearance | null
  /**
   * 是否渲染底边细线（仅 fade 模式有效）。
   *
   * 页面把搜索框设为「滚动常驻」时，搜索栏会钉在导航栏正下方并共用同一套材质，
   * 导航栏自己的底边细线会把一整块头部从中间切开（导航白条 + 细线 + 搜索白条），
   * 此时由 DiyPage 传 false 关掉；其余页面缺省 true，行为不变。
   */
  hairline?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  title: '',
  bordered: true,
  leftArrow: true,
  backgroundColor: '',
  textColor: '',
  backgroundImage: '',
  scrollEffect: null,
  scrollSurface: null,
  hairline: true,
})

const isCanBack = ref(true)
const router = useRouter()

const tinted = computed(() => !!props.backgroundColor || !!props.backgroundImage)
const navbarStyle = computed(() => {
  const style: string[] = []
  if (props.backgroundColor) {
    style.push(`background-color:${props.backgroundColor};`)
  }
  if (props.backgroundImage) {
    // 100% 100% 让渐变正好铺满导航条；左右渐变方向不受拉伸影响
    style.push(`background-image:${props.backgroundImage};`)
    style.push('background-repeat:no-repeat;')
    style.push('background-size:100% 100%;')
  }
  return style.join('')
})
const foreground = computed(() => (tinted.value ? props.textColor || '#ffffff' : ''))

/** 右侧插槽避让微信胶囊。 */
const rightInset = ref(0)
const rightInsetStyle = computed(() =>
  rightInset.value ? `margin-right:${rightInset.value}px;` : '',
)

/**
 * 页面滚动状态：由宿主页 useDiyNavbarScroll() / useFrostedNavbar() provide。
 *
 * 完全没有滚动源接入的页面（纯表单页、登录页）inject 拿到 null，视为未滚动
 * （起始外观），行为与改造前一致；只 inject 引用、在 computed 里读值，一次
 * 滚动只翻转两次。
 */
const scrolled = inject(DIY_NAVBAR_SCROLL_KEY, null)

/**
 * 业务页磨砂开关：由宿主页 useFrostedNavbar() provide。
 *
 * 与装修页的配置驱动区分开：装修页传 scrollEffect/scrollSurface（可能显式关闭），
 * 业务页不传配置、只开开关 —— 材质取同一份磨砂白常量，避免两处数值漂移。
 * 未开开关且未传配置时（登录页等只需静态顶栏的页面）完全不进入 fade 分支。
 */
const frostedScroll = inject(FROSTED_NAVBAR_KEY, false)

/**
 * 生效的滚动配置：显式下传优先，其次业务页磨砂开关；都没有则不接管。
 *
 * 业务页磨砂面的底色随系统深浅切换（深色玻璃同 86% 材质）；装修页显式
 * 下传的 surface 是商家配置的浅色内容，保持原样不参与深色切换。
 */
const mallThemeStore = useMallThemeStore()
const scrollEffect = computed(
  () =>
    props.scrollEffect
    ?? (frostedScroll ? FROSTED_SCROLL_EFFECT : null),
)
const scrollSurface = computed(
  () =>
    props.scrollSurface
    ?? (frostedScroll
      ? (mallThemeStore.mode === 'dark'
          ? FROSTED_SCROLL_SURFACE_DARK
          : FROSTED_SCROLL_SURFACE)
      : null),
)

/**
 * 是否启用沉浸式导航（滚动切换）。
 *
 * 两个条件缺一不可：配置 mode='fade' 且拿到目标底色外观。背景层与
 * 「wd-navbar 根透明」是同一件事的两面 —— 少了背景层还会把根设成透明，
 * 就会出现导航整条透空、页面内容直接顶到状态栏的视觉事故，故一并降级。
 */
const fadeEnabled = computed(
  () => scrollEffect.value?.mode === 'fade' && scrollSurface.value != null,
)

/** 起始外观：与旧渲染同源（装修页传入的底色/前景色） */
const baseAppearance = computed<NavigationAppearance>(() => ({
  backgroundColor: props.backgroundColor || '#FFFFFF',
  textColor: tinted.value ? props.textColor || '#FFFFFF' : '#222222',
}))

/**
 * 传给微信 setNavigationBarColor 的 backgroundColor。
 *
 * 微信把它定为**必填**色值：只传 frontColor 会抛
 * `parameter.backgroundColor should be String instead of Undefined`，
 * 传 rgb() / CSS 变量则抛 `should be HexColor`。沉浸式导航的页面都是
 * `navigationStyle: custom`，原生导航条不可见，这里只需把起始底色归一化成
 * 合法 hex 占位；读不出色值（如个人中心的 `var(--theme-color-primary)`）回退白色。
 */
const nativeNavigationBarColor = computed(() =>
  toNativeNavigationBarHexColor(baseAppearance.value.backgroundColor),
)

/** 目标底色：fade 时必由 DiyPage 传入或由业务页开关提供；兜底值不参与渲染，只为类型收敛 */
const fadeSurface = computed<NavigationSurfaceAppearance>(() =>
  scrollSurface.value ?? {
    color: baseAppearance.value.backgroundColor,
    alpha: 1,
    blur: false,
    textColor: baseAppearance.value.textColor,
  },
)

/** 滚动状态 → 外观：与编辑画布、草稿预览共用同一纯函数（schema/navigation-scroll.ts） */
const fadeAppearance = computed<NavigationScrollAppearance>(() =>
  resolveNavigationScrollAppearance(
    baseAppearance.value,
    fadeSurface.value,
    scrollEffect.value ?? undefined,
    scrolled?.value ? 1 : 0,
  ),
)

/**
 * 沉浸式导航背景层高度 = 状态栏 + 44px 导航条内容区。
 * 与 wd-navbar 的 fixed 根同口径（padding-top: statusBarHeight + 44px 内容区）。
 */
const { statusBarHeight = 0 } = uni.getWindowInfo()
const navbarBgHeight = statusBarHeight + 44

/** base 层：静态外观（纯色 / 左右渐变背景图），已滚动时整体隐藏 */
const baseLayerStyle = computed(
  () => `${navbarStyle.value}opacity:${fadeAppearance.value.baseOpacity};`,
)

/**
 * 目标底色 + 不透明度 → CSS rgba()。
 *
 * 底色可能来自主题（hex）、自定义色（归一化后 hex/rgb）或磨砂白；直接拼
 * `surfaceColor + alpha` 在小程序里不成立，这里统一拆通道再拼 rgba。
 */
function toSurfaceRgba(color: string, alpha: number): string {
  const value = color.trim()
  const hex = /^#([\da-f]{3}|[\da-f]{6}|[\da-f]{8})$/i.exec(value)
  if (hex) {
    const raw = hex[1] ?? ''
    const base = raw.length === 3
      ? raw.split('').map(char => char + char).join('')
      : raw.slice(0, 6)
    const channels = [0, 2, 4].map(index => Number.parseInt(base.slice(index, index + 2), 16))
    return `rgba(${channels[0]}, ${channels[1]}, ${channels[2]}, ${alpha})`
  }
  const rgb = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/i.exec(value)
  if (!rgb)
    return `rgba(255, 255, 255, ${alpha})`
  return `rgba(${Math.round(Number(rgb[1]))}, ${Math.round(Number(rgb[2]))}, ${Math.round(Number(rgb[3]))}, ${alpha})`
}

/** surface 层：目标底色 + 可选毛玻璃，已滚动时整体显示 */
const surfaceLayerStyle = computed(() => {
  const appearance = fadeAppearance.value
  return `background-color:${toSurfaceRgba(appearance.surfaceColor, appearance.surfaceAlpha)};opacity:${appearance.surfaceOpacity};`
})

/**
 * 标题 / 图标色：fade 模式在起始色与目标色之间随滚动状态一步翻转。
 * 不加颜色过渡：背景已是一步切换，颜色若慢 160ms 会在磨砂白底上留下
 * 「白字淡出」的空窗；与状态栏同帧翻转才一致。
 */
const titleStyle = computed(() => {
  if (fadeEnabled.value)
    return `color:${fadeAppearance.value.textColor};`
  return tinted.value ? `color:${foreground.value};` : ''
})
const leftIconColor = computed(() => {
  if (fadeEnabled.value)
    return fadeAppearance.value.textColor
  return tinted.value ? foreground.value : undefined
})

/** fade 模式把 wd-navbar 根设为透明，让下层的背景层负责全部底色 */
const navbarCustomStyle = computed(() =>
  fadeEnabled.value ? 'background:transparent;' : navbarStyle.value,
)

/**
 * 状态栏前景色同步（fade 模式）。
 *
 * 微信 setNavigationBarColor 的 frontColor 语义包含状态栏，且只接受
 * #000000 / #ffffff：按滚动状态翻转，值不变时不重复调用，一次滚动最多
 * 切换一次。H5 / App 无系统状态栏或端上不支持时静默（try/catch），
 * 导航自绘的背景与文字色不受影响。
 *
 * backgroundColor 是微信的**必填**参数（漏传即抛 MiniProgramError），虽然
 * fade 模式下原生导航条不可见，也必须带一个合法 hex，见 nativeNavigationBarColor。
 */
let lastFrontColor: '#000000' | '#ffffff' | null = null
watch(
  () => (fadeEnabled.value ? fadeAppearance.value.statusBarFrontColor : null),
  (frontColor) => {
    // fade 关闭时把去重状态复位：再次开启时（如切换装修配置）要重新写一次
    if (!frontColor) {
      lastFrontColor = null
      return
    }
    if (frontColor === lastFrontColor)
      return
    lastFrontColor = frontColor
    try {
      uni.setNavigationBarColor({
        backgroundColor: nativeNavigationBarColor.value,
        frontColor,
      })
    }
    catch {
      // 忽略：状态栏属于增强项，不影响导航内容可读性
    }
  },
  { immediate: true },
)

/**
 * 能否返回：每次点击现算，不再只用挂载时的快照。
 *
 * 旧实现在 onMounted 里 `getCurrentPages()` 拍一次；登录页 `replaceAll` 回跳、
 * `redirectTo` 顶替当前页、分享卡片直达等都会让页面栈在挂载后变化。快照失真后
 * 返回键会在首屏调 `navigateBack`（必然失败、开发者工具报 Page route 错误），
 * 或在可返回时误退回首页。图标在挂载/每次 onShow 同步，点击时再实时判断一次。
 */
function syncBackIcon() {
  isCanBack.value = canGoBackFromCurrentPage()
}

onShow(syncBackIcon)

onMounted(() => {
  syncBackIcon()

  // #ifdef MP-WEIXIN
  const sys = uni.getWindowInfo()
  const capsule = uni.getMenuButtonBoundingClientRect()
  rightInset.value = Math.max(sys.windowWidth - capsule.left, 0) + 8
  // #endif
})

function handleLeftClick() {
  if (canGoBackFromCurrentPage()) {
    // 连点由 router 实例上的 navigation lock 兜底（返回键 600ms 内只执行一次），
    // 避免第二次 navigateBack 把上一层页面也弹掉、留下无主的 routeDone。
    router.back()
  }
  else {
    router.pushTab({
      name: 'home',
    })
  }
}
</script>

<template>
  <!--
    沉浸式导航背景层：只有 fade 模式才渲染，位于 wd-navbar（z-index 500）之下、
    页面内容之上（z-index 499）。pointer-events:none 保证不挡页面点击；
    高度 = 状态栏 + 44px，与 wd-navbar 的 fixed 根同口径。
    起始外观与目标外观一步切换（不做距离插值），无 scrollEffect 的其余
    业务页不会渲染这个节点，模板契约与改造前一致。
  -->
  <view
    v-if="fadeEnabled"
    class="hr-navbar-bg"
    :style="{ height: `${navbarBgHeight}px` }"
  >
    <!-- base 层：起始外观（纯色 / 左右渐变背景图），已滚动时整体隐藏 -->
    <view class="hr-navbar-bg__base" :style="baseLayerStyle" />
    <!-- surface 层：目标底色 + 可选毛玻璃，已滚动时整体显示 -->
    <view
      class="hr-navbar-bg__surface"
      :class="{ 'is-blur': fadeAppearance.blur }"
      :style="surfaceLayerStyle"
    />
    <!-- 底部细线：浅色目标底色与内容的分界，随滚动状态出现；常驻搜索栏接管下沿时渲染者变为搜索栏 -->
    <view
      v-if="hairline"
      class="hr-navbar-bg__hairline"
      :style="{ opacity: `${fadeAppearance.hairlineOpacity}` }"
    />
  </view>
  <wd-navbar
    placeholder
    safe-area-inset-top
    fixed
    :bordered="bordered"
    :custom-style="navbarCustomStyle"
    :title="title"
  >
    <template #left>
      <wd-icon
        v-if="leftArrow"
        :name="isCanBack ? 'arrow-left' : 'home1'"
        size="22px"
        :color="leftIconColor"
        @click="handleLeftClick"
      />
    </template>
    <!--
      标题一律由本组件渲染，不再交给 wd-navbar 的 title 分支。

      这里曾经写成 `<template v-if="tinted" #title>`：小程序编译期会把命名插槽
      静态登记进 u-s（`u-s="{{['left','title','right']}}"`），与运行时条件无关，
      于是 wd-navbar 的 `!$slots.title` 恒为 false，永远不再渲染自己的 title 文案；
      而 tinted 为 false 时插槽内容又是空的 —— 标题就此整条消失。
      现在改为常驻插槽，tinted 只决定是否注入前景色，未传底色时留空字符串，
      由 wd-navbar 自身的 .wd-navbar__title 颜色接管。
    -->
    <template #title>
      <text :style="titleStyle">
        {{ title }}
      </text>
    </template>
    <template #right>
      <view :style="rightInsetStyle">
        <slot name="right" />
      </view>
    </template>
  </wd-navbar>
</template>

<style lang="scss" scoped>
.hr-navbar-bg {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 499;
  pointer-events: none;
  overflow: hidden;

  &__base,
  &__surface,
  &__hairline {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
  }

  /* 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的写法） */
  &__surface.is-blur {
    -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
    backdrop-filter: blur(18px) saturate(160%);
  }

  &__hairline {
    top: auto;
    height: 1px;
    background-color: rgba(15, 23, 42, 0.08);
  }
}
</style>
