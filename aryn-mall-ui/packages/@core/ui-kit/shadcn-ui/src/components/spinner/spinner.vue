<script lang="ts" setup>
import { ref, watch } from 'vue';

import { cn } from '@vben-core/shared/utils';

interface Props {
  class?: string;
  /**
   * @zh_CN 最小加载时间
   * @en_US Minimum loading time
   */
  minLoadingTime?: number;
  /**
   * @zh_CN loading状态开启
   */
  spinning?: boolean;
  /**
   * @zh_CN 加载提示文字
   * @en_US Loading text
   */
  text?: string;
}

defineOptions({
  name: 'VbenSpinner',
});

const props = withDefaults(defineProps<Props>(), {
  minLoadingTime: 50,
  text: '',
});

/** 同一页面可能存在多个 loader 实例，clipPath 的 id 必须唯一 */
let voyageLoaderSeed = 0;
const waterClipId = `vben-voyage-water-clip-${++voyageLoaderSeed}`;
// const startTime = ref(0);
const showSpinner = ref(false);
const renderSpinner = ref(false);
const timer = ref<ReturnType<typeof setTimeout>>();

watch(
  () => props.spinning,
  (show) => {
    if (!show) {
      showSpinner.value = false;
      clearTimeout(timer.value);
      return;
    }

    // startTime.value = performance.now();
    timer.value = setTimeout(() => {
      // const loadingTime = performance.now() - startTime.value;

      showSpinner.value = true;
      if (showSpinner.value) {
        renderSpinner.value = true;
      }
    }, props.minLoadingTime);
  },
  {
    immediate: true,
  },
);

function onTransitionEnd() {
  if (!showSpinner.value) {
    renderSpinner.value = false;
  }
}
</script>

<template>
  <div
    :class="
      cn(
        'z-100 bg-overlay-content absolute left-0 top-0 flex size-full flex-col items-center justify-center backdrop-blur-sm transition-all duration-300',
        {
          'invisible opacity-0': !showSpinner,
        },
        props.class,
      )
    "
    aria-live="polite"
    role="status"
    @transitionend="onTransitionEnd"
  >
    <div v-if="renderSpinner" class="vben-voyage-loader">
      <span class="vben-voyage-loader__halo"></span>
      <svg
        aria-hidden="true"
        class="vben-voyage-loader__porthole"
        viewBox="0 0 64 64"
      >
        <defs>
          <clipPath :id="waterClipId">
            <circle cx="32" cy="32" r="25.5" />
          </clipPath>
        </defs>
        <circle class="vben-voyage-loader__sea" cx="32" cy="32" r="26.5" />
        <g :clip-path="`url(#${waterClipId})`">
          <g class="vben-voyage-loader__water">
            <path
              class="vben-voyage-loader__wave vben-voyage-loader__wave--back"
              d="M-64 40Q-48 34-32 40T0 40T32 40T64 40T96 40T128 40V72H-64Z"
            />
            <path
              class="vben-voyage-loader__wave vben-voyage-loader__wave--front"
              d="M-64 45Q-48 40-32 45T0 45T32 45T64 45T96 45T128 45V72H-64Z"
            />
          </g>
        </g>
        <circle class="vben-voyage-loader__ring" cx="32" cy="32" r="30" />
      </svg>
      <svg
        aria-hidden="true"
        class="vben-voyage-loader__anchor"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="1.6"
        viewBox="0 0 24 24"
      >
        <circle cx="12" cy="5" r="2.4" />
        <path d="M12 7.6V21" />
        <path d="M7.8 10.4h8.4" />
        <path d="M4.6 14.2c.55 4 3.6 6.8 7.4 6.8s6.85-2.8 7.4-6.8" />
      </svg>
    </div>

    <div v-if="text && renderSpinner" class="vben-voyage-loader__label">
      {{ text }}
      <span aria-hidden="true" class="vben-voyage-loader__dots">
        <i></i><i></i><i></i>
      </span>
    </div>

    <slot></slot>
  </div>
</template>

<style scoped>
.vben-voyage-loader {
  position: relative;
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  color: hsl(var(--primary));
  animation: vben-voyage-enter 0.24s ease-out both;
}

.vben-voyage-loader__halo {
  position: absolute;
  width: 190px;
  height: 190px;
  pointer-events: none;
  background: radial-gradient(
    circle,
    hsl(var(--primary) / 8%) 0%,
    transparent 68%
  );
  border-radius: 50%;
}

.vben-voyage-loader__porthole {
  width: 72px;
  height: 72px;
  overflow: visible;
}

.vben-voyage-loader__sea {
  fill: hsl(var(--primary) / 8%);
}

.vben-voyage-loader__ring {
  fill: none;
  stroke: hsl(var(--primary) / 26%);
  stroke-width: 1.5;
}

.vben-voyage-loader__water {
  animation: vben-voyage-bob 4.6s ease-in-out infinite;
}

.vben-voyage-loader__wave {
  fill: hsl(var(--primary) / 26%);
}

.vben-voyage-loader__wave--back {
  fill: hsl(var(--primary) / 18%);
  animation: vben-voyage-sail 3.8s linear infinite;
}

.vben-voyage-loader__wave--front {
  animation: vben-voyage-sail 2.8s linear infinite;
}

.vben-voyage-loader__anchor {
  position: absolute;
  width: 24px;
  height: 24px;
  animation: vben-voyage-anchor-bob 3.4s ease-in-out infinite;
}

.vben-voyage-loader__label {
  display: flex;
  align-items: baseline;
  margin-top: 18px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  letter-spacing: 0.18em;
  animation: vben-voyage-enter 0.3s ease-out both;
}

.vben-voyage-loader__dots {
  display: inline-flex;
  gap: 3px;
  align-self: center;
  margin-left: 4px;
  letter-spacing: 0;
}

.vben-voyage-loader__dots i {
  width: 3px;
  height: 3px;
  background: currentcolor;
  border-radius: 50%;
  opacity: 0.2;
  animation: vben-voyage-dot 1.2s ease-in-out infinite;
}

.vben-voyage-loader__dots i:nth-child(2) {
  animation-delay: 0.18s;
}

.vben-voyage-loader__dots i:nth-child(3) {
  animation-delay: 0.36s;
}

@keyframes vben-voyage-enter {
  from {
    opacity: 0;
    transform: scale(0.92);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes vben-voyage-sail {
  to {
    transform: translateX(-64px);
  }
}

@keyframes vben-voyage-bob {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-1.6px);
  }
}

@keyframes vben-voyage-anchor-bob {
  0%,
  100% {
    transform: translateY(-2px);
  }

  50% {
    transform: translateY(-4px);
  }
}

@keyframes vben-voyage-dot {
  0%,
  100% {
    opacity: 0.18;
    transform: translateY(0);
  }

  40% {
    opacity: 0.9;
    transform: translateY(-1px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .vben-voyage-loader,
  .vben-voyage-loader__anchor,
  .vben-voyage-loader__dots i,
  .vben-voyage-loader__label,
  .vben-voyage-loader__water,
  .vben-voyage-loader__wave {
    animation: none !important;
  }
}
</style>
