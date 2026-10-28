<script lang="ts" setup>
import type { Component } from 'vue';

import { computed } from 'vue';

import { LanguageToggle, ThemeToggle } from '@vben/layouts';
import { preferences } from '@vben/preferences';

import { Goods, PriceTag, Tickets, Van } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

import { $t } from '#/locales';

defineOptions({ name: 'AuthPageLayout' });

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
const brandLatin = computed(() => $t('page.auth.brandLatin'));
const year = new Date().getFullYear();

interface BrandFeature {
  desc: string;
  icon: Component;
  title: string;
}

const features = computed((): BrandFeature[] => [
  {
    desc: $t('page.auth.brandFeature1Desc'),
    icon: Tickets,
    title: $t('page.auth.brandFeature1Title'),
  },
  {
    desc: $t('page.auth.brandFeature2Desc'),
    icon: Goods,
    title: $t('page.auth.brandFeature2Title'),
  },
  {
    desc: $t('page.auth.brandFeature3Desc'),
    icon: Van,
    title: $t('page.auth.brandFeature3Title'),
  },
  {
    desc: $t('page.auth.brandFeature4Desc'),
    icon: PriceTag,
    title: $t('page.auth.brandFeature4Title'),
  },
]);
</script>

<template>
  <div class="flex min-h-screen">
    <!-- 左侧品牌区：固定深色底，不随明暗主题切换 -->
    <div
      class="auth-brand relative hidden overflow-hidden lg:flex lg:w-[54%] xl:w-[58%]"
    >
      <div aria-hidden="true" class="auth-brand__grid absolute inset-0"></div>
      <div aria-hidden="true" class="auth-brand__orb"></div>

      <!-- 罗盘式环形刻度：航行/仪表盘的品牌隐喻，只出现在右上角空白处 -->
      <div aria-hidden="true" class="auth-brand__compass hidden xl:block">
        <span class="auth-brand__compass-ring"></span>
        <span
          class="auth-brand__compass-ring auth-brand__compass-ring--inner"
        ></span>
        <span class="auth-brand__compass-rotor">
          <span class="auth-brand__compass-node"></span>
        </span>
        <span class="auth-brand__compass-sweep"></span>
      </div>

      <div
        class="relative z-10 flex flex-1 flex-col justify-between px-14 py-12 xl:px-20"
      >
        <header class="auth-rise flex items-center">
          <div
            v-if="logo"
            class="flex size-12 items-center justify-center rounded-2xl bg-white/95 shadow-lg shadow-black/25 ring-1 ring-white/25"
          >
            <img :alt="appName" :src="logo" class="size-9" />
          </div>
          <div class="ml-3">
            <div class="flex items-center gap-2">
              <div class="text-[22px] font-semibold tracking-wide text-white">
                {{ $t('page.auth.brandName') }}
              </div>
              <span
                class="rounded-full border border-white/15 bg-white/5 px-2 py-0.5 text-[10px] font-medium tracking-[0.08em] text-white/55"
              >
                {{ $t('page.auth.workbench') }}
              </span>
            </div>
            <div
              v-if="brandLatin"
              class="mt-0.5 text-[10px] font-medium uppercase tracking-[0.22em] text-white/40"
            >
              {{ brandLatin }}
            </div>
          </div>
        </header>

        <div class="max-w-[640px]">
          <p class="auth-rise auth-eyebrow" style="

--rise-delay: 80ms">
            <span aria-hidden="true" class="auth-eyebrow__dot"></span>
            {{ $t('page.auth.brandEyebrow') }}
          </p>

          <h1 class="auth-rise auth-title" style="

--rise-delay: 140ms">
            {{ $t('page.auth.brandSloganTitle') }}
          </h1>
          <p class="auth-rise auth-subtitle" style="

--rise-delay: 210ms">
            {{ $t('page.auth.brandSloganSub') }}
          </p>

          <ul class="mt-10 grid grid-cols-1 gap-3 xl:grid-cols-2">
            <li
              v-for="(feature, index) in features"
              :key="feature.title"
              class="auth-rise auth-feature"
              :style="{ '--rise-delay': `${300 + index * 70}ms` }"
            >
              <span class="auth-feature__icon">
                <ElIcon :size="17">
                  <component :is="feature.icon" />
                </ElIcon>
              </span>
              <div class="min-w-0">
                <div class="auth-feature__title">{{ feature.title }}</div>
                <div class="auth-feature__desc">{{ feature.desc }}</div>
              </div>
            </li>
          </ul>
        </div>

        <footer
          class="auth-rise flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-white/40"
          style="

--rise-delay: 620ms"
        >
          <span>© {{ year }} {{ $t('page.auth.copyrightName') }}</span>
          <span aria-hidden="true" class="h-3 w-px bg-white/15"></span>
          <span>{{ $t('page.auth.staffOnly') }}</span>
        </footer>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="auth-panel relative flex min-h-screen flex-1 flex-col">
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
          {{ $t('page.auth.brandName') }} · {{ $t('page.auth.workbench') }}
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

      <footer
        class="text-muted-foreground px-6 pb-6 text-center text-[11px] leading-relaxed lg:hidden"
      >
        © {{ year }} {{ $t('page.auth.copyrightName') }} ·
        {{ $t('page.auth.staffOnly') }}
      </footer>
    </div>
  </div>
</template>

<style scoped>


@keyframes auth-grid-pan {
  from {
    background-position:
      0 0,
      0 0;
  }

  to {
    background-position:
      44px 44px,
      44px 44px;
  }
}

@keyframes auth-orb-float {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }

  50% {
    transform: translate3d(36px, -44px, 0) scale(1.08);
  }
}

@keyframes auth-compass-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes auth-rise {
  from {
    opacity: 0;
    transform: translate3d(0, 16px, 0);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@media (hover: hover) {
  .auth-feature {
    transition:
      transform 0.25s ease,
      border-color 0.25s ease,
      background-color 0.25s ease;
  }

  .auth-feature:hover {
    background: hsl(var(--primary) / 8%);
    border-color: hsl(var(--primary) / 38%);
    transform: translateY(-2px);
  }

  .auth-feature:hover .auth-feature__icon {
    color: hsl(var(--primary));
    background: hsl(var(--primary) / 18%);
    border-color: hsl(var(--primary) / 50%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-rise,
  .auth-brand__grid,
  .auth-brand__orb,
  .auth-brand__compass-rotor,
  .auth-brand__compass-sweep {
    animation: none;
  }

  .auth-feature,
  .auth-feature__icon,
  .auth-fade-enter-active,
  .auth-fade-leave-active {
    transition: none;
  }
}

.auth-brand {
  background:
    radial-gradient(
      ellipse 62% 50% at 18% 4%,
      rgb(255 255 255 / 5%),
      transparent 62%
    ),
    radial-gradient(
      ellipse 70% 55% at 82% 92%,
      hsl(var(--primary) / 14%),
      transparent 70%
    ),
    linear-gradient(158deg, #070d19 0%, #0c1728 52%, #0a1322 100%);
}

.auth-brand__grid {
  background-image:
    linear-gradient(rgb(255 255 255 / 3.5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 3.5%) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(
    ellipse 95% 85% at 45% 35%,
    black 25%,
    transparent 78%
  );
  animation: auth-grid-pan 32s linear infinite;
}

.auth-brand__orb {
  position: absolute;
  bottom: -180px;
  left: -160px;
  width: 520px;
  height: 520px;
  background: hsl(var(--primary));
  border-radius: 9999px;
  opacity: 0.26;
  filter: blur(100px);
  animation: auth-orb-float 18s ease-in-out infinite;
}

/* 罗盘：右上角只露出一段弧，作为品牌区的唯一装饰记忆点 */
.auth-brand__compass {
  position: absolute;
  top: -260px;
  right: -260px;
  width: 620px;
  height: 620px;
  pointer-events: none;
}

.auth-brand__compass-ring {
  position: absolute;
  inset: 0;
  border: 1px solid rgb(255 255 255 / 7%);
  border-radius: 50%;
}

.auth-brand__compass-ring--inner {
  inset: 108px;
  border-color: rgb(255 255 255 / 4%);
}

.auth-brand__compass-rotor {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  animation: auth-compass-spin 48s linear infinite;
}

.auth-brand__compass-node {
  position: absolute;
  top: -3px;
  left: calc(50% - 3px);
  width: 6px;
  height: 6px;
  background: hsl(var(--primary));
  border-radius: 50%;
  box-shadow: 0 0 14px 3px hsl(var(--primary) / 65%);
}

.auth-brand__compass-sweep {
  position: absolute;
  inset: 0;
  background: conic-gradient(
    from 90deg,
    transparent 0deg,
    transparent 296deg,
    hsl(var(--primary) / 55%) 355deg,
    transparent 360deg
  );
  border-radius: 50%;
  mask: radial-gradient(
    closest-side,
    transparent calc(100% - 2px),
    #000 calc(100% - 1px)
  );
  animation: auth-compass-spin 48s linear infinite;
}

.auth-eyebrow {
  display: inline-flex;
  gap: 9px;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  color: rgb(255 255 255 / 55%);
  letter-spacing: 0.14em;
}

.auth-eyebrow__dot {
  width: 5px;
  height: 5px;
  background: hsl(var(--primary));
  border-radius: 9999px;
  box-shadow: 0 0 0 4px hsl(var(--primary) / 14%);
}

.auth-title {
  margin-top: 20px;
  font-size: clamp(34px, 2.8vw, 44px);
  font-weight: 600;
  line-height: 1.3;
  color: #fff;
  letter-spacing: 0.01em;
  text-wrap: balance;
}

.auth-subtitle {
  margin-top: 14px;
  font-size: 15px;
  line-height: 1.7;
  color: rgb(255 255 255 / 55%);
}

.auth-feature {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 13px 14px;
  background: rgb(255 255 255 / 3%);
  border: 1px solid rgb(255 255 255 / 6%);
  border-radius: 14px;
  backdrop-filter: blur(6px);
}

.auth-feature__icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: hsl(var(--primary) / 92%);
  background: hsl(var(--primary) / 12%);
  border: 1px solid hsl(var(--primary) / 30%);
  border-radius: 10px;
  transition:
    color 0.25s ease,
    background-color 0.25s ease,
    border-color 0.25s ease;
}

.auth-feature__title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  color: rgb(255 255 255 / 90%);
}

.auth-feature__desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: rgb(255 255 255 / 52%);
}

.auth-panel {
  background:
    radial-gradient(
      ellipse 62% 45% at 100% 0%,
      hsl(var(--primary) / 6%),
      transparent 68%
    ),
    hsl(var(--background));
}

.auth-rise {
  animation: auth-rise 0.72s cubic-bezier(0.22, 1, 0.36, 1) backwards;
  animation-delay: var(--rise-delay, 0ms);
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
