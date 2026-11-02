<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted } from 'vue';

import { useElementPlusDesignTokens } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { ElConfigProvider } from 'element-plus';

import { elementLocale } from '#/locales';
import { router } from '#/router';

import SessionInvalidModal from './layouts/session-invalid-modal.vue';
import { startSessionSync } from './store/session-sync';

defineOptions({ name: 'App' });

useElementPlusDesignTokens();

const accessStore = useAccessStore();

/**
 * 刷新页面后首个请求就是 401 时，路由守卫里的 fetchUserInfo 会抛错并中断初始导航，
 * BasicLayout（会话失效弹窗的常规挂载点）永远不会挂载。这里在应用根节点补一个兜底实例，
 * 保证「刷新后首个 401 仍弹窗、不再静默登出」；重登成功后 authStore 会整页重载恢复原页面。
 *
 * <p>与 basic.vue 里的实例互斥：只有路由尚未解析（matched 为空）时根实例才渲染，
 * 正常在布局内失效时由 basic.vue 的实例承载，不会出现两个弹窗。</p>
 */
const showStandaloneSessionInvalid = computed(
  () =>
    accessStore.loginExpired && router.currentRoute.value.matched.length === 0,
);

let stopSessionSync: (() => void) | undefined;

onMounted(() => {
  // 二期多标签页会话同步（契约 §3.1）：应用根组件启动一次监听，
  // 其它标签页失效时同步弹窗、重新登录后整页重载；通道不可用时静默降级。
  stopSessionSync = startSessionSync();
});

onBeforeUnmount(() => {
  stopSessionSync?.();
});
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <RouterView />
    <SessionInvalidModal v-if="showStandaloneSessionInvalid" />
  </ElConfigProvider>
</template>
