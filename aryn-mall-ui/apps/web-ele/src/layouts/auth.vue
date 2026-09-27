<script lang="ts" setup>
import type { Component } from 'vue';

import { computed } from 'vue';

import { LanguageToggle, ThemeToggle } from '@vben/layouts';
import { preferences } from '@vben/preferences';

import { Box, ShoppingCart, TrendCharts } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

import { $t } from '#/locales';

defineOptions({ name: 'AuthPageLayout' });

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
const year = new Date().getFullYear();

interface BrandFeature {
  desc: string;
  icon: Component;
  title: string;
}

const features = computed((): BrandFeature[] => [
  {
    desc: $t('page.auth.brandFeature1Desc'),
    icon: Box,
    title: $t('page.auth.brandFeature1Title'),
  },
  {
    desc: $t('page.auth.brandFeature2Desc'),
    icon: ShoppingCart,
    title: $t('page.auth.brandFeature2Title'),
  },
  {
    desc: $t('page.auth.brandFeature3Desc'),
    icon: TrendCharts,
    title: $t('page.auth.brandFeature3Title'),
  },
]);
</script>

<template>
  <div class="flex min-h-screen">
    <!-- 左侧品牌区：固定深色底，不随明暗主题切换 -->
    <div
      class="auth-brand relative hidden overflow-hidden lg:flex lg:w-[54%] xl:w-[58%]"
    >
      <!-- 网格纹理与主题色光斑 -->
      <div class="auth-brand__grid absolute inset-0"></div>
      <div class="auth-brand__orb auth-brand__orb--left"></div>
      <div class="auth-brand__orb auth-brand__orb--right"></div>

      <div
        class="relative z-10 flex flex-1 flex-col justify-between px-14 py-12 xl:px-20"
      >
        <div class="flex items-center">
          <div
            v-if="logo"
            class="flex size-12 items-center justify-center rounded-2xl bg-white/95 shadow-lg shadow-black/20"
          >
            <img :alt="appName" :src="logo" class="size-9" />
          </div>
          <div class="ml-3">
            <div class="text-2xl font-semibold tracking-wide text-white">
              悦航购
            </div>
            <div class="text-xs uppercase tracking-[0.3em] text-white/45">
              Aetheryn Mall
            </div>
          </div>
        </div>

        <div class="max-w-xl">
          <h1
            class="text-4xl font-bold leading-snug text-white xl:text-[2.75rem] xl:leading-[1.35]"
          >
            {{ $t('page.auth.brandSloganTitle') }}
          </h1>
          <p class="mt-4 text-base text-white/60">
            {{ $t('page.auth.brandSloganSub') }}
          </p>

          <ul class="mt-12 space-y-6">
            <li
              v-for="feature in features"
              :key="feature.title"
              class="flex items-start"
            >
              <div
                class="flex size-11 shrink-0 items-center justify-center rounded-xl border"
                style="
                  color: hsl(var(--primary) / 90%);
                  background: hsl(var(--primary) / 14%);
                  border-color: hsl(var(--primary) / 35%);
                "
              >
                <ElIcon :size="20">
                  <component :is="feature.icon" />
                </ElIcon>
              </div>
              <div class="ml-4">
                <div class="text-[15px] font-medium text-white/90">
                  {{ feature.title }}
                </div>
                <div class="mt-1 text-sm text-white/45">
                  {{ feature.desc }}
                </div>
              </div>
            </li>
          </ul>
        </div>

        <div class="text-xs text-white/35">
          © {{ year }} 悦航购 Aetheryn Mall · {{ appName }}
        </div>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="bg-background relative flex min-h-screen flex-1 flex-col">
      <!-- 主题 / 语言切换 -->
      <div class="absolute right-6 top-6 z-20 flex items-center gap-2">
        <ThemeToggle />
        <LanguageToggle />
      </div>

      <!-- 移动端品牌行（lg 以下显示） -->
      <div class="flex items-center px-6 pt-6 lg:hidden">
        <div
          v-if="logo"
          class="bg-primary/10 flex size-10 items-center justify-center rounded-xl"
        >
          <img :alt="appName" :src="logo" class="size-7" />
        </div>
        <span class="text-foreground ml-2 text-lg font-semibold">
          {{ appName }}
        </span>
      </div>

      <main class="flex flex-1 items-center justify-center p-6 lg:p-10">
        <div class="w-full max-w-[400px]">
          <RouterView v-slot="{ Component: routeComponent, route }">
            <Transition appear mode="out-in" name="auth-fade">
              <KeepAlive include="Login">
                <component
                  :is="routeComponent"
                  :key="route.fullPath"
                  class="w-full"
                />
              </KeepAlive>
            </Transition>
          </RouterView>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.auth-brand {
  background:
    radial-gradient(
      ellipse 70% 55% at 18% 8%,
      rgb(255 255 255 / 4%),
      transparent 60%
    ),
    linear-gradient(160deg, #0b1220 0%, #0e1a2e 55%, #0b1424 100%);
}

.auth-brand__grid {
  background-image:
    linear-gradient(rgb(255 255 255 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 4%) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(
    ellipse 90% 80% at 50% 40%,
    black 30%,
    transparent 75%
  );
}

.auth-brand__orb {
  position: absolute;
  background: hsl(var(--primary));
  border-radius: 9999px;
  opacity: 0.3;
  filter: blur(90px);
  animation: auth-orb-float 14s ease-in-out infinite;
}

.auth-brand__orb--left {
  bottom: -160px;
  left: -140px;
  width: 480px;
  height: 480px;
}

.auth-brand__orb--right {
  top: -120px;
  right: -80px;
  width: 360px;
  height: 360px;
  animation-delay: -7s;
}

@keyframes auth-orb-float {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }

  50% {
    transform: translate3d(30px, -40px, 0) scale(1.08);
  }
}

.auth-fade-enter-active,
.auth-fade-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}

.auth-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.auth-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
