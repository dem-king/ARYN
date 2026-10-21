<script setup lang="ts">
import CategoryBrowser from '@/components/category-browser/index.vue'

/**
 * 商详「去逛逛」的压栈分类页。
 *
 * 分类 tab 是 root 页，switchTab/reLaunch 会清空返回栈，商详无法再回来；
 * 这里用普通页面 + 共享分类组件，左上角返回箭头直接 navigateBack 到商详。
 */
definePage({
  name: 'category-browse',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '分类',
  },
})
</script>

<template>
  <CategoryBrowser show-back />
</template>
