<script setup lang="ts">
/**
 * 装修用搜索栏。
 *
 * 运营可配：占位文案、圆角风格、是否显示扫码。
 *
 * 热词轮播（B 版原型「SKU 编码 · 品名 · 船上补给」）：
 *   运营可在后台配多个热词，未配置时退回占位文案。轮播只在配置了
 *   2 个及以上热词时启动，避免单个热词白白起一个定时器。
 *
 * 消息角标：显示未读总数，未登录或未读为 0 时不渲染。
 *
 * 滚动常驻（组件配置 `sticky=true`，由运营在装修后台开启）：
 *   整页 DIY 的「首个渲染区块的第一个组件」是搜索框时，`DiyPage` 额外渲染一份
 *   `pinned` 实例挂在页面级（本组件的根节点 `position: fixed`，钉在导航栏下方），
 *   区块里原来那一份退化为 `stickyPlaceholder`（visibility:hidden 只保留布局高度）。
 *   这样：① 首屏静置观感与设计稿逐像素一致（材质层初始完全透明）；② 一旦滚动，
 *   搜索框不再随内容滚走，而是与导航栏同一套材质（起始底色 → 目标底色 → 底边细线）
 *   连成一块头图，滚动进度只在本组件子树内消费，装修楼层不重渲染；
 *   ③ 材质跟随逐帧原始滚动值、不做 CSS 时间过渡，变色与内容 1:1 跟手（不滞后）。
 */
import type { NavigationSurfaceAppearance } from '@/components/diy/schema/navigation-scroll'
import type { NavigationAppearance } from '@/components/diy/schema/navigation-style'
import type { NavigationScrollEffect } from '@/components/diy/schema/types'

import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { resolveNavigationScrollAppearance } from '@/components/diy/schema/navigation-scroll'
import { pickReadableIconColor } from '@/components/diy/schema/navigation-style'
import {
  DIY_NAVBAR_RAW_SCROLL_KEY,
  DIY_NAVBAR_SCROLL_KEY,
} from '@/composables/useDiyNavbarScroll'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useAuthStore } from '@/store/authStore'
import { useMallThemeStore } from '@/store/mallThemeStore'
import { useMessageStore } from '@/store/messageStore'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
  /** 宿主区块的环境底色（渐变顶边起色/纯色/页面背景），由 diy 渲染器下发 */
  hostBackground: {
    type: String,
    default: '',
  },
  /**
   * 常驻层实例：由 DiyPage 渲染在页面级（导航栏之下），滚动时保持可见。
   * 只有整页 DIY 宿主页（home / diy-page）才会传，内嵌装修场景永远为 false。
   */
  pinned: {
    type: Boolean,
    default: false,
  },
  /** 占位实例：常驻开启时，区块里的原实例只保留高度，不参与视觉与交互 */
  stickyPlaceholder: {
    type: Boolean,
    default: false,
  },
  /** 常驻层顶边：导航栏占位高度 + 首区块上外边距/上内边距（px，由 DiyPage 计算） */
  pinnedTop: {
    type: Number,
    default: 0,
  },
  /** 常驻层左右避让：首区块左右外边距 + 左右内边距（px，由 DiyPage 计算） */
  pinnedInsetX: {
    type: Number,
    default: 0,
  },
  /** 导航栏起始外观（DiyPage 静态解析，与 hr-navbar 同源） */
  navAppearance: {
    type: Object,
    default: null,
  },
  /** 滚动渐显配置与目标底色外观（静态部分，与 hr-navbar 同源） */
  scrollEffect: {
    type: Object,
    default: null,
  },
  scrollSurface: {
    type: Object,
    default: null,
  },
})

const { t } = useI18n()
const authStore = useAuthStore()
const messageStore = useMessageStore()
/** 全站自动动效总开关（商城主题配置）；关闭时热词不轮换，固定展示首个 */
const mallThemeStore = useMallThemeStore()

const placeholder = computed(() => props.showData.placeholder || t('diy.searchBar.placeholder'))
const style = computed(() => props.showData.style || '1')
const bgColor = computed(() => props.showData.bgColor || '#f5f5f5')
const showScan = computed(() => props.showData.showScan !== false)

const borderRadius = computed(() => (style.value === '1' ? '20px' : '4px'))

/** 通用样式（外边距/内边距/背景/圆角）挂根节点，与设置面板「通用样式」同源 */
const commonStyles = useDiyStyle(computed(() => props.showData.commonStyle))

/** 热词列表：兼容后台给字符串数组或逗号分隔字符串两种写法 */
const hotWords = computed<string[]>(() => {
  const raw = props.showData.hotWords
  if (Array.isArray(raw))
    return raw.map(word => String(word).trim()).filter(Boolean)
  if (typeof raw === 'string')
    return raw.split(/[,，]/).map(word => word.trim()).filter(Boolean)
  return []
})

const hotIndex = ref(0)
let hotTimer: ReturnType<typeof setInterval> | null = null

/** 当前展示文案：有热词时轮播，否则用占位文案 */
const displayText = computed(() => {
  if (hotWords.value.length === 0)
    return placeholder.value
  return hotWords.value[hotIndex.value % hotWords.value.length]
})

const unreadCount = computed(() => (authStore.isLoggedIn ? messageStore.totalUnread : 0))
const unreadText = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

/**
 * 铃铛/扫码图标脚下的环境底色：组件自身背景优先（渐变取起色，与导航栏
 * 「跟随首个区块」同口径），背景图取不了色回落宿主区块底色，再回落白底。
 */
const ambientBackground = computed(() => {
  const common = props.showData.commonStyle
  if (common?.bgPicUrl)
    return props.hostBackground || '#ffffff'
  const own = [common?.bgStartColor, common?.bgEndColor].find(
    color => typeof color === 'string' && color && color !== 'transparent',
  )
  return own || props.hostBackground || '#ffffff'
})

/* ------------------------------------------------------------------ *
 * 滚动常驻
 * ------------------------------------------------------------------ */

/**
 * 页面滚动距离：由整页 DIY 宿主页 `useDiyNavbarScroll()` provide。
 *
 * 优先用**逐帧原始值**（`DIY_NAVBAR_RAW_SCROLL_KEY`）：常驻搜索栏是大面积纯色块，
 * 8px 量化的台阶看得出来，且会与导航栏渐显「差档」；量化值只作为老宿主页兜底。
 * 内嵌装修页（商详/分类/个人中心的装修块）不 provide，inject 为 null，此时常驻层
 * 也不会被渲染（DiyPage 只在整页 DIY 下渲染常驻层）。
 */
const pageRawScrollTop = inject(DIY_NAVBAR_RAW_SCROLL_KEY, null)
const pageScrollTop = inject(DIY_NAVBAR_SCROLL_KEY, null)

/** 常驻层材质消费的滚动值：原始值优先，滚动事件本身即逐帧触发，不做时间过渡 */
const materialScrollTop = computed(() => {
  if (!props.pinned)
    return 0
  return pageRawScrollTop?.value ?? pageScrollTop?.value ?? 0
})

/** 导航栏起始外观：正常由 DiyPage 下发；缺省时按宿主区块底色兜底（仅防御） */
const navBaseAppearance = computed<NavigationAppearance>(() => {
  const appearance = props.navAppearance as NavigationAppearance | null
  if (appearance && appearance.backgroundColor)
    return appearance
  const background = props.hostBackground || '#ffffff'
  return {
    backgroundColor: background,
    textColor: pickReadableIconColor(background, '#ffffff'),
  }
})

const navScrollSurface = computed(
  () => props.scrollSurface as NavigationSurfaceAppearance | null,
)
const navScrollEffect = computed(
  () => props.scrollEffect as NavigationScrollEffect | null,
)

/**
 * 常驻层材质：与 hr-navbar 共用同一纯函数。
 *
 * 传 scrollTop=0 时（占位/普通实例）解析结果就是「起始外观」且 surfaceOpacity=0，
 * 不参与滚动消费；只有 pinned 实例会随页面滚动重算——滚动时仅本组件子树重渲染。
 */
const material = computed(() =>
  resolveNavigationScrollAppearance(
    navBaseAppearance.value,
    navScrollSurface.value ?? {
      color: navBaseAppearance.value.backgroundColor,
      alpha: 1,
      blur: false,
      textColor: navBaseAppearance.value.textColor,
    },
    navScrollEffect.value ?? undefined,
    materialScrollTop.value,
  ),
)

/**
 * 材质「成形」距离（px）。
 *
 * 静置（scrollTop=0）必须完全透明：首屏仍是「搜索框直接坐在区块渐变上」的设计稿观感，
 * 常驻层不能把导航底色（可能 ≠ 首区块渐变）糊上来。开始滚动后**一个量化步（8px）内**
 * 就把材质补满：再长会在起步阶段显得比导航栏「慢半拍」（用户反馈），再短则非
 * `followFirstSection` 页面会在首个滚动事件跳色。
 */
const MATERIALIZE_DISTANCE = 8

const materialize = computed(() => {
  if (!props.pinned)
    return 0
  return Math.min(1, Math.max(0, materialScrollTop.value) / MATERIALIZE_DISTANCE)
})

/** 两层材质的不透明度 = 渐显进度 × 成形进度（与 hr-navbar 的 base/surface 同口径） */
const materialBaseStyle = computed(() =>
  `background-color:${navBaseAppearance.value.backgroundColor};opacity:${(material.value.baseOpacity * materialize.value).toFixed(3)};`,
)
const materialSurfaceStyle = computed(() =>
  `background:var(--diy-nav-surface-bg, rgba(255, 255, 255, 0.86));opacity:${(material.value.surfaceOpacity * materialize.value).toFixed(3)};`,
)
const materialHairlineStyle = computed(() =>
  `background:var(--diy-nav-hairline, rgba(15, 23, 42, 0.08));opacity:${(material.value.hairlineOpacity * materialize.value).toFixed(3)};`,
)
/** 毛玻璃只在渐显目标态启用；与 hr-navbar 的 is-blur 同值 */
const materialSurfaceBlur = computed(() => props.pinned && material.value.blur)

/** 材质已出现：铃铛/扫码图标切换到与合成底色对比的可读色，内层白条补一圈细边保持轮廓 */
const materialized = computed(() => props.pinned && materialize.value > 0)

/**
 * 图标色：静置沿用宿主区块底色（与改造前一致）；常驻材质出现后跟随导航栏
 * 解析出的文字色（滚动到磨砂白后翻成深色），与 hr-navbar 标题同一翻转时机。
 */
const iconColor = computed(() => {
  if (materialized.value)
    return material.value.textColor
  return pickReadableIconColor(ambientBackground.value, '#333333')
})

/**
 * 根节点样式：
 *   · stickyPlaceholder —— 保留原布局（高度/外边距）但不可见、不可点；
 *   · pinned —— 页面级固定层，顶边让出导航栏，左右让出首区块外边距/内边距；
 *   · 普通 —— 与改造前完全一致（只返回通用样式）。
 * 常驻层去掉 overflow 裁切：材质层要铺满导航栏下方整条（圆角只作用于内层白条）。
 */
const dynamicStyles = computed(() => {
  const base = { ...(commonStyles.value as Record<string, string>) }
  if (props.stickyPlaceholder)
    return { ...base, 'visibility': 'hidden', 'pointer-events': 'none' }
  if (!props.pinned)
    return base
  delete base.overflow
  const insetX = Math.max(0, props.pinnedInsetX || 0)
  return {
    ...base,
    'position': 'fixed',
    'top': `${Math.max(0, props.pinnedTop || 0)}px`,
    'left': `${insetX}px`,
    'right': `${insetX}px`,
    'z-index': '480',
  }
})

function handleSearch() {
  uni.navigateTo({ url: '/sub-pages/product/goods-search/index' })
}

function goMessage() {
  uni.navigateTo({ url: '/sub-pages/message/notice/index' })
}

onMounted(() => {
  startRotation()
})

// 总开关可能异步到达（主题拉取晚于挂载）：到达后再决定启停
watch([() => mallThemeStore.autoMotionEnabled, hotWords], () => {
  startRotation()
})

onBeforeUnmount(() => {
  stopRotation()
})

/** 启停热词轮换：需同时满足「配置了 2 个及以上热词」与「全站允许自动动效」 */
function startRotation() {
  stopRotation()
  // 占位实例不可见：不必再跑一个没人看的定时器
  if (props.stickyPlaceholder)
    return
  if (hotWords.value.length < 2)
    return
  if (!mallThemeStore.autoMotionEnabled)
    return
  hotTimer = setInterval(() => {
    hotIndex.value = (hotIndex.value + 1) % hotWords.value.length
  }, 3000)
}

function stopRotation() {
  if (hotTimer) {
    clearInterval(hotTimer)
    hotTimer = null
  }
}
</script>

<template>
  <view
    class="search-bar"
    :class="{
      'search-bar--pinned': pinned,
      'search-bar--materialized': materialized,
    }"
    :style="dynamicStyles"
  >
    <!--
      常驻层材质：起始底色 → 目标底色 → 底边细线，三层与 hr-navbar 同源
      （CSS 变量 --diy-nav-surface-* 由 DiyPage 挂在 .diy-page 根节点上继承）。
      普通/占位实例不渲染，模板契约与改造前一致。
    -->
    <view v-if="pinned" class="search-bar-material">
      <view class="search-bar-material__base" :style="materialBaseStyle" />
      <view
        class="search-bar-material__surface"
        :class="{ 'is-blur': materialSurfaceBlur }"
        :style="materialSurfaceStyle"
      />
      <view class="search-bar-material__hairline" :style="materialHairlineStyle" />
    </view>

    <view class="search-bar-row">
      <view
        class="search-bar-inner"
        :style="{ backgroundColor: bgColor, borderRadius }"
        @click="handleSearch"
      >
        <wd-icon name="search" size="16px" color="#999" />
        <text class="search-bar-placeholder">
          {{ displayText }}
        </text>
      </view>

      <!-- 消息角标：未读为 0 或未登录时不渲染 -->
      <view class="search-bar-bell" @click.stop="goMessage">
        <wd-icon name="notification" size="22px" :color="iconColor" />
        <view v-if="unreadCount > 0" class="search-bar-bell-dot">
          {{ unreadText }}
        </view>
      </view>

      <view v-if="showScan" class="search-bar-scan" @click.stop="handleSearch">
        <wd-icon name="scan" size="22px" :color="iconColor" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.search-bar {
  /* 留白挂内层行容器：根节点 padding 会被通用样式的内联零值覆盖（仓库统一口径） */
  .search-bar-row {
    /* 材质层是绝对定位兄弟节点：行内容必须叠在它上面 */
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    padding: 6px 12px;
  }

  .search-bar-inner {
    flex: 1;
    display: flex;
    align-items: center;
    height: 36px;
    padding: 0 12px;

    .search-bar-placeholder {
      margin-left: 6px;
      overflow: hidden;
      font-size: 14px;
      color: #999;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .search-bar-bell {
    position: relative;
    display: flex;
    margin-left: 10px;
    align-items: center;
  }

  .search-bar-bell-dot {
    position: absolute;
    top: -6px;
    right: -10px;
    box-sizing: border-box;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border-radius: 8px;
    background: var(--wot-color-theme-primary, #ff2237);
    color: #fff;
    font-size: 10px;
    line-height: 16px;
    text-align: center;
  }

  .search-bar-scan {
    margin-left: 10px;
    display: flex;
    align-items: center;
  }

  /* 常驻层材质：绝对铺满根节点（根节点已 position: fixed），行内容在其上 */
  .search-bar-material {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    overflow: hidden;
    pointer-events: none;

    &__base,
    &__surface,
    &__hairline {
      position: absolute;
      top: 0;
      right: 0;
      bottom: 0;
      left: 0;
      /*
       * 不加 transition：时间过渡会让材质比导航栏/内容滞后 ~180ms（用户反馈
       * 「慢一步」）；这里直接跟随 onPageScroll 的逐帧原始值，与内容 1:1 跟手。
       */
    }

    /* iOS 18 以下只认 -webkit- 前缀；不支持的端仍是不透明度 .86 的底色层 */
    &__surface.is-blur {
      -webkit-backdrop-filter: blur(18px) saturate(160%);
      backdrop-filter: blur(18px) saturate(160%);
    }

    &__hairline {
      top: auto;
      height: 1px;
    }
  }

  /*
   * 材质出现后，配置的白色输入条会融进磨砂白头部：补一圈 1px 细边保住轮廓。
   * 深色输入条上这圈边近乎不可见，不需要按颜色分支。
   */
  &--materialized .search-bar-inner {
    box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
  }
}
</style>
