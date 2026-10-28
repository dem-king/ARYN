<script type="text/babel">
import { computed, ref, toRefs, watchEffect } from 'vue';

import VerifyPoints from './Verify/VerifyPoints.vue';
/**
 * Verify 验证码组件
 * @description 分发验证码使用
 */
import VerifySlide from './Verify/VerifySlide.vue';

export default {
  name: 'Vue2Verify',
  components: {
    VerifySlide,
    VerifyPoints,
  },
  props: {
    captchaType: {
      type: String,
      required: true,
    },
    figure: {
      type: Number,
      default: 0,
    },
    arith: {
      type: Number,
      default: 0,
    },
    mode: {
      type: String,
      default: 'pop',
    },
    vSpace: {
      type: Number,
      default: 5,
    },
    explain: {
      type: String,
      default: '向右滑动完成验证',
    },
    imgSize: {
      type: Object,
      default() {
        return {
          width: '310px',
          height: '155px',
        };
      },
    },
    blockSize: {
      type: Object,
      default() {
        return { height: '50px', width: '50px' };
      },
    },
    barSize: {
      type: Object,
      default() {
        return { height: '40px', width: '310px' };
      },
    },
  },
  setup(props) {
    const { captchaType, mode } = toRefs(props);
    const clickShow = ref(false);
    const verifyType = ref(undefined);
    const componentType = ref(undefined);

    const instance = ref({});

    const showBox = computed(() => {
      return mode.value === 'pop' ? clickShow.value : true;
    });
    /**
     * refresh
     * @description 刷新
     */
    const refresh = () => {
      if (instance.value.refresh) {
        instance.value.refresh();
      }
    };
    const closeBox = () => {
      clickShow.value = false;
      refresh();
    };
    const show = () => {
      if (mode.value === 'pop') {
        clickShow.value = true;
      }
    };
    watchEffect(() => {
      switch (captchaType.value) {
        case 'blockPuzzle': {
          verifyType.value = '2';
          componentType.value = 'VerifySlide';
          break;
        }
        case 'clickWord': {
          verifyType.value = '';
          componentType.value = 'VerifyPoints';
          break;
        }
      }
    });

    return {
      clickShow,
      verifyType,
      componentType,
      instance,
      showBox,
      closeBox,
      show,
    };
  },
};
</script>
<template>
  <div :class="mode === 'pop' ? 'mask' : ''" v-show="showBox">
    <div
      :class="mode === 'pop' ? 'verifybox' : ''"
      :style="{ 'max-width': `${parseInt(imgSize.width) + 40}px` }"
      :role="mode === 'pop' ? 'dialog' : undefined"
      :aria-modal="mode === 'pop' ? 'true' : undefined"
      aria-label="安全验证"
    >
      <div class="verifybox-top" v-if="mode === 'pop'">
        <span class="verifybox-title">请完成安全验证</span>
        <button
          type="button"
          class="verifybox-close"
          aria-label="关闭验证"
          @click="closeBox"
        >
          <svg aria-hidden="true" viewBox="0 0 16 16">
            <path d="M4 4l8 8M12 4l-8 8" />
          </svg>
        </button>
      </div>
      <div
        class="verifybox-bottom"
        :style="{ padding: mode === 'pop' ? '0 20px 20px' : '0' }"
      >
        <!-- 验证码容器 -->
        <component
          v-if="componentType"
          :is="componentType"
          :captcha-type="captchaType"
          :type="verifyType"
          :figure="figure"
          :arith="arith"
          :mode="mode"
          :space="vSpace"
          :explain="explain"
          :img-size="imgSize"
          :block-size="blockSize"
          :bar-size="barSize"
          ref="instance"
        />
      </div>
    </div>
  </div>
</template>
<style scoped>
@keyframes verify-pop-in {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes verify-shake {
  0%,
  100% {
    transform: translateX(0);
  }

  25% {
    transform: translateX(-3px);
  }

  75% {
    transform: translateX(3px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .verifybox,
  :deep(.verify-bar-area .verify-move-block.is-error) {
    animation: none;
  }

  :deep(.verify-tips),
  :deep(.tips-enter-active),
  :deep(.tips-leave-active) {
    transition: none;
  }
}

.verifybox {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  background-color: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  box-shadow:
    0 24px 64px -24px rgb(15 23 42 / 35%),
    0 8px 24px -16px rgb(15 23 42 / 18%);
  animation: verify-pop-in 0.22s cubic-bezier(0.22, 1, 0.36, 1);
}

.verifybox-top {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px 20px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--el-text-color-primary);
}

.verifybox-title {
  min-width: 0;
}

.verifybox-bottom {
  box-sizing: border-box;
}

.verifybox-close {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 999px;
  transition:
    color 0.18s ease,
    background-color 0.18s ease;
}

.verifybox-close:hover {
  color: var(--el-text-color-primary);
  background: var(--el-fill-color);
}

.verifybox-close svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentcolor;
  stroke-width: 1.6;
  stroke-linecap: round;
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 1001;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgb(15 23 42 / 45%);
  backdrop-filter: blur(4px);
}

:deep(.verify-img-panel) {
  position: relative;
  box-sizing: border-box;
  margin: 0;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border-radius: 12px;
  box-shadow: inset 0 0 0 1px rgb(15 23 42 / 6%);
}

:deep(.verify-img-panel .verify-refresh) {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: #fff;
  cursor: pointer;
  background: rgb(15 23 42 / 45%);
  border: none;
  border-radius: 999px;
  backdrop-filter: blur(2px);
  transition:
    background-color 0.18s ease,
    transform 0.18s ease;
}

:deep(.verify-img-panel .verify-refresh:hover) {
  background: rgb(15 23 42 / 65%);
  transform: scale(1.06);
}

:deep(.verify-img-panel .verify-refresh svg) {
  width: 15px;
  height: 15px;
  fill: none;
  stroke: currentcolor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

:deep(.verify-tips) {
  position: absolute;
  bottom: 8px;
  left: 50%;
  z-index: 4;
  width: auto;
  height: auto;
  padding: 5px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #fff;
  white-space: nowrap;
  border-radius: 999px;
  transform: translateX(-50%);
  transition:
    bottom 0.3s ease,
    opacity 0.3s ease;
}

:deep(.suc-bg) {
  background-color: var(--el-color-success);
}

:deep(.err-bg) {
  background-color: var(--el-color-danger);
}

:deep(.tips-enter),
:deep(.tips-leave-to) {
  bottom: -30px;
  opacity: 0;
}

:deep(.tips-enter-active),
:deep(.tips-leave-active) {
  transition:
    bottom 0.3s ease,
    opacity 0.3s ease;
}

/* ---------------------------- */

/* 常规验证码 */
:deep(.verify-code) {
  margin-bottom: 5px;
  font-size: 20px;
  text-align: center;
  cursor: pointer;
  border: 1px solid var(--el-border-color);
}

:deep(.cerify-code-panel) {
  height: 100%;
  overflow: hidden;
}

:deep(.verify-code-area) {
  float: left;
}

:deep(.verify-input-area) {
  float: left;
  width: 60%;
  padding-right: 10px;
}

:deep(.verify-change-area) {
  float: left;
  line-height: 30px;
}

:deep(.varify-input-code) {
  display: inline-block;
  width: 100%;
  height: 25px;
}

:deep(.verify-change-code) {
  color: var(--el-color-primary);
  cursor: pointer;
}

:deep(.verify-btn) {
  width: 200px;
  height: 30px;
  margin-top: 10px;
  color: #fff;
  background-color: var(--el-color-primary);
  border: none;
}

/* 滑动验证码 */
:deep(.verify-bar-area) {
  position: relative;
  box-sizing: border-box;
  text-align: center;
  background: var(--el-fill-color-light);
  border-radius: 10px;
  box-shadow: inset 0 0 0 1px var(--el-border-color-lighter);
}

:deep(.verify-bar-area > .verify-msg) {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  pointer-events: none;
  user-select: none;
}

:deep(.verify-bar-area .verify-left-bar) {
  position: absolute;
  top: 0;
  left: 0;
  box-sizing: border-box;
  cursor: pointer;
  background: var(--el-color-primary-light-9);
  border-radius: 10px;
}

:deep(.verify-bar-area .verify-left-bar.is-success) {
  background: var(--el-color-success-light-9);
}

:deep(.verify-bar-area .verify-left-bar.is-error) {
  background: var(--el-color-danger-light-9);
}

:deep(.verify-bar-area .verify-move-block) {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  touch-action: none;
  cursor: grab;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  box-shadow: 0 2px 8px -2px rgb(15 23 42 / 20%);
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease;
}

:deep(.verify-bar-area .verify-move-block:hover) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

:deep(.verify-bar-area .verify-move-block.is-moving) {
  color: #fff;
  cursor: grabbing;
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  box-shadow: 0 6px 16px -6px rgb(0 0 0 / 35%);
}

:deep(.verify-bar-area .verify-move-block.is-success) {
  color: #fff;
  background: var(--el-color-success);
  border-color: var(--el-color-success);
  box-shadow: 0 6px 16px -6px rgb(0 0 0 / 25%);
}

:deep(.verify-bar-area .verify-move-block.is-error) {
  color: #fff;
  background: var(--el-color-danger);
  border-color: var(--el-color-danger);
  box-shadow: 0 6px 16px -6px rgb(0 0 0 / 25%);
  animation: verify-shake 0.36s ease;
}

:deep(.verify-bar-area .verify-move-block .verify-icon) {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentcolor;
  stroke-width: 2.2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

:deep(.verify-bar-area .verify-move-block .verify-sub-block) {
  position: absolute;
  z-index: 3;
  text-align: center;
  pointer-events: none;
  filter: drop-shadow(0 6px 12px rgb(15 23 42 / 30%));
}

:deep(.verify-bar-area .verify-msg) {
  z-index: 3;
}
</style>
