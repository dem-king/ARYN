<script lang="ts" setup>
import { onMounted, onUnmounted, ref } from 'vue';

import dayjs from 'dayjs';

interface Props {
  title?: string;
}

withDefaults(defineProps<Props>(), {
  title: '悦航购 · 数据大屏',
});

const currentTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'));
const isFullscreen = ref(false);
let timer: null | ReturnType<typeof setInterval> = null;

const updateTime = () => {
  currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss');
};

const toggleFullscreen = () => {
  if (document.fullscreenElement) {
    document.exitFullscreen();
    isFullscreen.value = false;
  } else {
    document.documentElement.requestFullscreen();
    isFullscreen.value = true;
  }
};

const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement;
};

onMounted(() => {
  updateTime();
  timer = setInterval(updateTime, 1000);
  document.addEventListener('fullscreenchange', handleFullscreenChange);
});

onUnmounted(() => {
  if (timer) {
    clearInterval(timer);
  }
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
});
</script>

<template>
  <div class="screen-header">
    <div class="header-left">
      <div class="header-decoration left"></div>
    </div>
    <div class="header-center">
      <h1 class="header-title">{{ title }}</h1>
      <span class="header-time">{{ currentTime }}</span>
    </div>
    <div class="header-right">
      <button class="fullscreen-btn" @click="toggleFullscreen">
        <svg
          v-if="!isFullscreen"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path
            d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"
          />
        </svg>
        <svg
          v-else
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path
            d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3"
          />
        </svg>
      </button>
      <div class="header-decoration right"></div>
    </div>
  </div>
</template>

<style scoped>
.screen-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 80px;
  padding: 0 30px;
  background: linear-gradient(
    180deg,
    rgb(10 40 80 / 90%) 0%,
    rgb(10 40 80 / 0%) 100%
  );
}

.header-left,
.header-right {
  display: flex;
  flex: 1;
  align-items: center;
}

.header-right {
  justify-content: flex-end;
}

.header-decoration {
  flex: 1;
  height: 2px;
  background: linear-gradient(
    90deg,
    transparent,
    rgb(0 180 255 / 60%),
    transparent
  );
}

.header-decoration.left {
  margin-right: 20px;
}

.header-decoration.right {
  margin-left: 20px;
}

.header-center {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
}

.header-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 6px;
  text-shadow: 0 0 30px rgb(0 180 255 / 30%);
  background: linear-gradient(180deg, #fff 0%, #7ec8ff 100%);
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-time {
  font-size: 14px;
  color: rgb(126 200 255 / 80%);
  letter-spacing: 2px;
}

.fullscreen-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-right: 16px;
  color: #7ec8ff;
  cursor: pointer;
  background: rgb(0 180 255 / 15%);
  border: 1px solid rgb(0 180 255 / 30%);
  border-radius: 6px;
  transition: all 0.3s;
}

.fullscreen-btn:hover {
  background: rgb(0 180 255 / 30%);
  border-color: rgb(0 180 255 / 60%);
}

.fullscreen-btn svg {
  width: 18px;
  height: 18px;
}
</style>
