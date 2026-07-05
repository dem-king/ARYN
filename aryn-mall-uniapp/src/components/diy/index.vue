<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import DiyNotice from '@/components/diy/diy-notice/index.vue'
import DiyGoods from '@/components/diy/diy-goods/index.vue'
import DiyImage from '@/components/diy/diy-image/index.vue'
import DiyTitleText from '@/components/diy/diy-titletext/index.vue'
import DiyRichText from '@/components/diy/diy-rich-text/index.vue'
import DiyGap from '@/components/diy/diy-gap/index.vue'
import DiyTabnav from '@/components/diy/diy-tabnav/index.vue'

const props = defineProps<{
  pageContentData: any
  pageName: string
}>()

const components = ref<any[]>([])
const title = ref()

/** DIY 组件类型 → 组件映射表（替代 v-if 链，提升性能和可扩展性） */
const componentMap: Record<string, Component> = {
  'notice': DiyNotice,
  'goods': DiyGoods,
  'image-ad': DiyImage,
  'title-text': DiyTitleText,
  'rich-text': DiyRichText,
  'gap': DiyGap,
  'tab-nav': DiyTabnav,
}

/** 根据类型获取对应组件 */
function getComponent(type: string): Component | undefined {
  return componentMap[type]
}

watch(
  () => props.pageContentData,
  (val) => {
    if (val) {
      components.value = val.components || []
    }
  },
  { immediate: true },
)
watch(
  () => props.pageName,
  (val) => {
    if (val) {
      title.value = val || ''
    }
  },
  { immediate: true },
)
</script>

<template>
  <view>
    <!-- 顶部导航栏 -->
    <hr-navbar :title="title" :left-arrow="false" />
    <!-- 动态组件渲染 -->
    <view v-if="components">
      <view v-for="(item, index) in components" :key="index">
        <component
          :is="getComponent(item.type)"
          v-if="getComponent(item.type)"
          :show-data="item.formData"
        />
      </view>
    </view>
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.navbar-zw {
  height: 44px !important;
}
</style>
