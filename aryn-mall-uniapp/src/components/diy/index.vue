<script setup lang="ts">
import { computed } from 'vue'

import { getDiyComponent } from './registry'
import { migratePageContent } from './schema/migrate'
import UnknownComponent from './unknown-component.vue'

const props = defineProps<{
  pageContentData: unknown
  pageName?: string
}>()

const document = computed(() => migratePageContent(props.pageContentData))
const components = computed(() => document.value.components)
const title = computed(
  () => document.value.page.navigation.title || props.pageName || '',
)
const pageStyle = computed(() => ({
  backgroundColor: document.value.page.backgroundColor,
  backgroundImage: document.value.page.backgroundImage
    ? `url(${document.value.page.backgroundImage})`
    : undefined,
}))
</script>

<template>
  <view class="diy-page" :style="pageStyle">
    <hr-navbar
      v-if="document.page.navigation.visible"
      :title="title"
      :left-arrow="false"
    />
    <view class="diy-components">
      <view v-for="item in components" :key="item.id">
        <component
          :is="getDiyComponent(item.type) || UnknownComponent"
          :component-type="item.type"
          :show-data="item.props"
        />
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
}
</style>
