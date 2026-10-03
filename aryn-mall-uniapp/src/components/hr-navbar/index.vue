<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

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
}
const props = withDefaults(defineProps<Props>(), {
  title: '',
  bordered: true,
  leftArrow: true,
  backgroundColor: '',
  textColor: '',
})

const isCanBack = ref(true)
const router = useRouter()

const tinted = computed(() => !!props.backgroundColor)
const navbarStyle = computed(() =>
  props.backgroundColor ? `background-color:${props.backgroundColor};` : '',
)
const foreground = computed(() => (tinted.value ? props.textColor || '#ffffff' : ''))
const titleStyle = computed(() =>
  tinted.value ? `color:${foreground.value};` : '',
)

/**
 * 右侧插槽避让微信胶囊。
 *
 * wd-navbar 把 right 插槽绝对定位在 right:0，正好压在微信原生胶囊「… ○」下面：
 * 文字被胶囊盖住看不见、点击也落在胶囊上，购物车页的「管理」入口因此完全不可达。
 * 左移量 = 胶囊左缘到屏幕右缘的距离（胶囊宽度 + 右边距），取值口径与
 * hr-search-navbar 的 capsulePaddingRight 一致。
 */
const rightInset = ref(0)
const rightInsetStyle = computed(() =>
  rightInset.value ? `margin-right:${rightInset.value}px;` : '',
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
  const sys = uni.getSystemInfoSync()
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
  <wd-navbar
    placeholder
    safe-area-inset-top
    fixed
    :bordered="bordered"
    :custom-style="navbarStyle"
    :title="title"
  >
    <template #left>
      <wd-icon
        v-if="leftArrow"
        :name="isCanBack ? 'arrow-left' : 'home1'"
        size="22px"
        :color="tinted ? foreground : undefined"
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
      <text :style="titleStyle">{{ title }}</text>
    </template>
    <template #right>
      <view :style="rightInsetStyle">
        <slot name="right" />
      </view>
    </template>
  </wd-navbar>
</template>
