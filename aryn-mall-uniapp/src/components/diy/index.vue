<script setup lang="ts">
import { ref, watch } from 'vue'
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
        <template v-if="item.type === 'notice'">
          <diy-notice :show-data="item.formData" />
        </template>
        <template v-if="item.type === 'goods'">
          <diy-goods :show-data="item.formData" />
        </template>
        <template v-if="item.type === 'image-ad'">
          <diy-image :show-data="item.formData" />
        </template>
        <template v-if="item.type === 'title-text'">
          <diy-title-text :show-data="item.formData" />
        </template>
        <template v-if="item.type === 'rich-text'">
          <diy-rich-text :show-data="item.formData" />
        </template>
        <template v-if="item.type === 'gap'">
          <diy-gap :show-data="item.formData" />
        </template>
        <template v-if="item.type === 'tab-nav'">
          <diy-tabnav :show-data="item.formData" />
        </template>
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
