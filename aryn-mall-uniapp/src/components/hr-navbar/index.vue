<script setup lang="ts">
import type { NavigationScrollAppearance, NavigationSurfaceAppearance } from '@/components/diy/schema/navigation-scroll'
import type { NavigationAppearance } from '@/components/diy/schema/navigation-style'
import type { NavigationScrollEffect } from '@/components/diy/schema/types'

import { computed, inject, onMounted, ref, watch } from 'vue'

import { resolveNavigationScrollAppearance } from '@/components/diy/schema/navigation-scroll'
import { DIY_NAVBAR_SCROLL_KEY } from '@/composables/useDiyNavbarScroll'

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
   * 滚动渐显（沉浸式导航）配置：仅装修页 DiyPage 传入。
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
 * 滚动渐显的页面滚动距离：由宿主页 useDiyNavbarScroll() provide。
 *
 * 其余 30+ 业务页不 provide，inject 拿到 null 视为 scrollTop=0，行为与改造前一致。
 * 只 inject 引用、在 computed 里读值，滚动时仅导航子树重渲染。
 */
const scrollTop = inject(DIY_NAVBAR_SCROLL_KEY, null)

/**
 * 是否启用滚动渐显。
 *
 * 两个条件缺一不可：配置 mode='fade' 且拿到目标底色外观。背景层与
 * 「wd-navbar 根透明」是同一件事的两面 —— 少了背景层还会把根设成透明，
 * 就会出现导航整条透空、页面内容直接顶到状态栏的视觉事故，故一并降级。
 */
const fadeEnabled = computed(
  () => props.scrollEffect?.mode === 'fade' && props.scrollSurface != null,
)

/** 起始外观：与旧渲染同源（装修页传入的底色/前景色） */
const baseAppearance = computed<NavigationAppearance>(() => ({
  backgroundColor: props.backgroundColor || '#FFFFFF',
  textColor: tinted.value ? props.textColor || '#FFFFFF' : '#222222',
}))

/** 目标底色：fade 时必由 DiyPage 传入；兜底值不参与渲染，只为类型收敛 */
const fadeSurface = computed<NavigationSurfaceAppearance>(() =>
  props.scrollSurface ?? {
    color: baseAppearance.value.backgroundColor,
    alpha: 1,
    blur: false,
    textColor: baseAppearance.value.textColor,
  },
)

/** 滚动进度 → 外观：与编辑画布、草稿预览共用同一纯函数（schema/navigation-scroll.ts） */
const fadeAppearance = computed<NavigationScrollAppearance>(() =>
  resolveNavigationScrollAppearance(
    baseAppearance.value,
    fadeSurface.value,
    props.scrollEffect ?? undefined,
    scrollTop?.value ?? 0,
  ),
)

/**
 * 滚动渐显背景层高度 = 状态栏 + 44px 导航条内容区。
 * 与 wd-navbar 的 fixed 根同口径（padding-top: statusBarHeight + 44px 内容区）。
 */
const { statusBarHeight = 0 } = uni.getWindowInfo()
const navbarBgHeight = statusBarHeight + 44

/** base 层：静态外观（纯色 / 左右渐变背景图），随进度整体淡出 */
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

/** surface 层：目标底色 + 可选毛玻璃，随进度淡入 */
const surfaceLayerStyle = computed(() => {
  const appearance = fadeAppearance.value
  return `background-color:${toSurfaceRgba(appearance.surfaceColor, appearance.surfaceAlpha)};opacity:${appearance.surfaceOpacity};`
})

/**
 * 标题 / 图标色：fade 模式按滚动进度在起始色与目标色之间翻转。
 * 切换点带 160ms 颜色过渡，避免合成背景亮度跨过阈值时文字「啪」地跳色。
 */
const titleStyle = computed(() => {
  if (fadeEnabled.value)
    return `color:${fadeAppearance.value.textColor};transition:color .16s linear;`
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
 * #000000 / #ffffff：按滚动进度翻转，值不变时不重复调用，一次滚动最多
 * 切换一次。H5 / App 无系统状态栏或端上不支持时静默（try/catch），
 * 导航自绘的背景与文字色不受影响。
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
      uni.setNavigationBarColor({ frontColor })
    }
    catch {
      // 忽略：状态栏属于增强项，不影响导航内容可读性
    }
  },
  { immediate: true },
)

onMounted(() => {
  const pages = getCurrentPages()
  /**
   * 判断是否能返回
   */
  if (pages.length <= 1 || pages[pages.length - 1].route === 'pages/login/index') {
    isCanBack.value = false
  }
  else {
    isCanBack.value = true
  }

  // #ifdef MP-WEIXIN
  const sys = uni.getWindowInfo()
  const capsule = uni.getMenuButtonBoundingClientRect()
  rightInset.value = Math.max(sys.windowWidth - capsule.left, 0) + 8
  // #endif
})

function handleLeftClick() {
  if (isCanBack.value) {
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
    滚动渐显背景层：只有 fade 模式才渲染，位于 wd-navbar（z-index 500）之下、
    页面内容之上（z-index 499）。pointer-events:none 保证不挡页面点击；
    高度 = 状态栏 + 44px，与 wd-navbar 的 fixed 根同口径。
    无 scrollEffect 的其余业务页不会渲染这个节点，模板契约与改造前一致。
  -->
  <view
    v-if="fadeEnabled"
    class="hr-navbar-bg"
    :style="{ height: `${navbarBgHeight}px` }"
  >
    <!-- base 层：起始外观（纯色 / 左右渐变背景图），随进度淡出 -->
    <view class="hr-navbar-bg__base" :style="baseLayerStyle" />
    <!-- surface 层：目标底色 + 可选毛玻璃，随进度淡入 -->
    <view
      class="hr-navbar-bg__surface"
      :class="{ 'is-blur': fadeAppearance.blur }"
      :style="surfaceLayerStyle"
    />
    <!-- 底部细线：浅色目标底色与内容的分界，按进度渐显；常驻搜索栏接管下沿时渲染者变为搜索栏 -->
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

  /* iOS 18 以下需要 -webkit- 前缀；不支持的端仍是不透明度 .86 的目标底色层 */
  &__surface.is-blur {
    -webkit-backdrop-filter: blur(18px) saturate(160%);
    backdrop-filter: blur(18px) saturate(160%);
  }

  &__hairline {
    top: auto;
    height: 1px;
    background-color: rgba(15, 23, 42, 0.08);
  }
}
</style>
