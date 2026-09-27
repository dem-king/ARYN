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
    <!-- 品牌色底上接管标题渲染，避免 :deep 穿透（weapp 样式隔离不可靠） -->
    <template v-if="tinted" #title>
      <text :style="titleStyle">{{ title }}</text>
    </template>
    <template #right>
      <slot name="right" />
    </template>
  </wd-navbar>
</template>
