<script setup lang="ts">
// 主题色
const themeVars = ref({
  colorTheme: '#FF2237', // 主色
  colorThemePrimary: '#FF2237', // 主色
  colorThemeSecondary: '#FF6B7A', // 辅色
  colorThemeBackground: '#FFF0F0', // 底色
})
</script>

<template>
  <wd-config-provider custom-class="page-wrapper" :theme-vars="themeVars">
    <ku-root-view />
    <wd-notify />
    <wd-message-box />
    <wd-toast />
    <global-loading />
    <global-toast />
    <global-message />
    <!-- #ifdef MP-WEIXIN -->
    <privacy-popup />
    <!-- #endif -->
  </wd-config-provider>
</template>
