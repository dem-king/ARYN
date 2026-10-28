<script setup lang="ts">
import CategoryBrowser from '@/components/category-browser/index.vue'
import { useTabBarLocale } from '@/composables/useTabBarLocale'

useTabBarLocale()

definePage({
  name: 'category',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '分类',
  },
})
</script>

<template>
  <CategoryBrowser />
</template>
