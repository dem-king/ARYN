<script setup lang="ts">
import type { GlassTabItem, GlassTabKey } from './tabs'

import { onShow } from '@dcloudio/uni-app'
import { computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { useMallThemeStore } from '@/store/mallThemeStore'
import { useShoppingCartStore } from '@/store/shoppingCartStore'
import { formatCartBadgeText } from '@/utils/cart-badge'

import { GLASS_TAB_ITEMS } from './tabs'

/**
 * 自绘玻璃底栏（仅微信小程序）。
 *
 * 原生 tabBar 只支持纯色底色、没有模糊能力，要做到与顶部导航同一套磨砂玻璃，
 * 只能隐藏原生 tabBar 后自绘。H5 的内置 tabBar 是真实 DOM，直接由全局样式
 * 加同一材质即可（见 App.vue），两端最终观感一致。
 *
 * 与原生 tabBar 的行为对齐：
 *  · 悬浮胶囊：高 52px、圆角 26px、贴底 16px，层级 998（页面弹层 900/901 在其下，全部分类浮层 999 在其上）；
 *  · 选中项图标用 `*-selected.png`、文案取主题色；
 *  · 购物车角标直接读 shoppingCartStore.cartCount（原生角标 API 在隐藏的 bar 上无效）。
 *
 * 首帧衔接：小程序的 tab 切换是换页，本组件要等新页面的 Vue 挂载后才出现。
 * `src/custom-tab-bar/` 里有一份同款原生实现，随页面首帧渲染，兜住「页面首帧
 * （骨架屏）→ Vue 挂载」这段空窗；本组件挂载后把它隐藏并接管，选中态、角标、
 * 多语言与页面浮层级全部回到这里（与改造前一致）。
 *
 * 注意：首帧之前的整屏纯白属于新页面 webview 初始化，custom-tab-bar 与页面
 * 在同一个 webview 里，页面内方案盖不住（录帧实测）。根治只能改回原生 tabBar
 * 或单页承载 4 个 tab，当前按用户决定保持现状。
 */
const props = defineProps<{ active: GlassTabKey }>()

const { t, locale } = useI18n()
const shoppingCartStore = useShoppingCartStore()
const themeStore = useMallThemeStore()

const cartBadge = computed(() => formatCartBadgeText(shoppingCartStore.cartCount))

/**
 * 把当前底栏状态留给「下一个页面」的原生首帧占位组件。
 *
 * 原生组件读不到 Vue store，但同一个逻辑层里可以经 getApp() 传值：
 * 主题色、深色、角标、文案都先写进去，占位栏首帧就不会跳色或缺角标。
 */
function publishNativeTabBarSeed() {
  try {
    const app = getApp() as unknown as Record<string, any> | undefined
    if (!app) {
      return
    }
    app.__glassTabBarSeed = {
      labels: GLASS_TAB_ITEMS.map(item => t(item.labelKey)),
      badge: cartBadge.value ?? '',
      primaryColor: themeStore.primaryColor,
      dark: themeStore.mode === 'dark',
    }
  }
  catch {
    // 首帧优化失败不影响底栏本身
  }
}

/**
 * 让原生首帧占位组件退场，由本组件接管。
 *
 * custom 模式下 `uni.hideTabBar` 不生效（只保留给不支持 custom 的老基础库），
 * 必须拿组件实例 setData。按路由找**本页**的实例而不是页面栈顶：切页瞬间
 * 本页可能已经不是栈顶，用栈顶会误伤新页面的占位栏。
 */
function takeOverNativeTabBar() {
  uni.hideTabBar({ animation: false, fail: () => {} })
  try {
    const expected = GLASS_TAB_ITEMS.find(item => item.key === props.active)?.route
    if (!expected) {
      return
    }
    const pages = getCurrentPages() as unknown as Array<Record<string, any>>
    const page = pages.find(item => item.route === expected)
    const bar = page && typeof page.getTabBar === 'function' ? page.getTabBar() : null
    if (bar && typeof bar.setData === 'function' && !(bar.data && bar.data.hidden)) {
      bar.setData({ hidden: true })
    }
  }
  catch {
    // 非 tab 页上下文 / 极端时序：占位栏继续兜底，不影响功能
  }
}

/** 页面首次的 onShow 早于挂载，此时隐藏会露出空窗，等挂载后再接管 */
let vueMounted = false

onMounted(() => {
  vueMounted = true
  publishNativeTabBarSeed()
  takeOverNativeTabBar()
})

onShow(() => {
  if (!vueMounted) {
    return
  }
  publishNativeTabBarSeed()
  takeOverNativeTabBar()
})

watch(
  [cartBadge, () => themeStore.primaryColor, () => themeStore.mode, locale],
  publishNativeTabBarSeed,
)

/**
 * 切换进行中标记：连点两个 tab（或同一 tab 连点）会在同一 tick 里发出两次
 * `uni.switchTab` —— 微信对重叠的 tab 切换会留下无主的 routeDone，开发者工具报
 * `routeDone with a webviewId xxx is not found`。complete 里复位，失败也复位。
 */
let switching = false

function selectTab(item: GlassTabItem) {
  if (switching || item.key === props.active)
    return
  switching = true
  uni.switchTab({
    url: item.path,
    fail: () => {},
    complete: () => {
      switching = false
    },
  })
}
</script>

<template>
  <view class="glass-tabbar">
    <view
      v-for="item in GLASS_TAB_ITEMS"
      :key="item.key"
      class="glass-tabbar__item"
      :class="{ 'glass-tabbar__item--active': item.key === active }"
      hover-class="glass-tabbar__item--pressed"
      :hover-stay-time="80"
      @click="selectTab(item)"
    >
      <view class="glass-tabbar__icon-box">
        <image
          class="glass-tabbar__icon"
          :src="item.key === active ? item.activeIcon : item.icon"
          mode="aspectFit"
        />
        <text v-if="item.key === 'cart' && cartBadge" class="glass-tabbar__badge">
          {{ cartBadge }}
        </text>
      </view>
      <text class="glass-tabbar__label">
        {{ t(item.labelKey) }}
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.glass-tabbar {
  position: fixed;
  /* 悬浮胶囊：居中、左右留边、底部离屏一条，圆角与浮起阴影由本层承担 */
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 40px);
  max-width: 480px;
  /* 固定 16px：落在 iPhone home indicator 上方，又不至于悬在半空 */
  bottom: 16px;
  /* 与 H5 原生 tabBar 同层：页面弹层(900/901)在其下，「全部分类」浮层(999)在其上 */
  z-index: 998;
  display: flex;
  height: 52px;
  box-sizing: border-box;
  overflow: hidden;
  border-radius: 26px;
  /* 与顶栏磨砂白同一数值（86%），上下两块玻璃看起来才是一套材质 */
  background-color: rgba(255, 255, 255, 0.86);
  /* 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的写法） */
  -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
}

.glass-tabbar__item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  &--pressed {
    opacity: 0.72;
  }
}

.glass-tabbar__icon-box {
  position: relative;
  width: 26px;
  height: 26px;
  margin-bottom: 3px;
}

.glass-tabbar__icon {
  display: block;
  width: 26px;
  height: 26px;
}

.glass-tabbar__label {
  font-size: 11px;
  line-height: 15px;
  color: #7a7e83;
}

.glass-tabbar__item--active .glass-tabbar__label {
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
}

/* 角标与原生 tabBar 的红色角标同色：主题色偏浅时白字会看不清，这里不跟随主题 */
.glass-tabbar__badge {
  position: absolute;
  top: -4px;
  right: -10px;
  box-sizing: border-box;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: 8px;
  background-color: #ff2237;
  color: #fff;
  font-size: 9px;
  line-height: 15px;
  text-align: center;
}

/* 深色模式：与 wot 的 theme 同源（ConfigProvider 把 wot-theme-dark 挂在根节点） */
.wot-theme-dark .glass-tabbar {
  background-color: rgba(26, 26, 26, 0.86);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.46);
}

.wot-theme-dark .glass-tabbar__label {
  color: #a8adb5;
}
</style>
