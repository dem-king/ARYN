<script setup lang="ts">
import type { PageDesignTheme } from '#/api/promotion/page-design';

import { computed, ref, watch } from 'vue';

import { ElButton, ElDialog, ElEmpty, ElTag } from 'element-plus';

import { getThemes } from '#/api/promotion/page-design';

/**
 * 「本页主题」选择器（页级视觉配置）。
 *
 * 2026-10-02 从租户级主题管理降级而来：主题库 CRUD 与「设为商城默认」
 * （全局动作）已迁至独立菜单「商城装修 → 商城主题」。此处只做一件事：
 * 决定**当前装修页面**引用哪个主题，语义与「页面设置 / 区块设置」同级，
 * 避免在单页编辑器里出现影响全商城的操作。
 *
 * 选项语义与 C 端两级结构一致：
 *   · 跟随商城默认 —— 不写 themeRef，页面用商城默认主题；
 *   · 指定主题 —— 写入 themeRef，发布时固化为 themeSnapshot，页面内覆盖全局。
 */

const props = defineProps<{
  modelValue: boolean;
  themeRef?: string;
}>();

const emit = defineEmits<{
  apply: [themeRef: string];
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const themes = ref<PageDesignTheme[]>([]);

const currentId = computed(() => props.themeRef ?? '');

async function loadThemes() {
  if (!props.modelValue) return;
  loading.value = true;
  try {
    themes.value = await getThemes();
  } finally {
    loading.value = false;
  }
}

function choose(themeId: string) {
  emit('apply', themeId);
  emit('update:modelValue', false);
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      void loadThemes();
    }
  },
);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="本页主题"
    width="560px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="dialog-tip">
      只作用于当前装修页面：指定主题后，本页发布时固化为快照，页面内覆盖商城默认配色。
      需要修改配色方案或设置全商城主题，请到「商城装修 → 商城主题」。
    </p>

    <div v-loading="loading" class="theme-options">
      <ElEmpty v-if="themes.length === 0" description="主题库暂无主题" />
      <template v-else>
        <button
          :class="{ 'is-active': !currentId }"
          class="theme-option"
          type="button"
          @click="choose('')"
        >
          <span class="option-main">
            <strong>跟随商城默认</strong>
            <span class="option-meta">页面不引用主题，使用商城默认配色</span>
          </span>
          <ElTag v-if="!currentId" effect="dark" size="small" type="primary">
            当前
          </ElTag>
        </button>

        <button
          v-for="theme in themes"
          :key="theme.id"
          :class="{ 'is-active': currentId === theme.id }"
          class="theme-option"
          type="button"
          @click="choose(theme.id)"
        >
          <span class="option-swatches">
            <span
              :style="{ background: theme.primaryColor }"
              class="swatch swatch-primary"
            ></span>
            <span
              :style="{ background: theme.pageBackgroundColor }"
              class="swatch"
            ></span>
            <span
              :style="{ background: theme.navigationColor }"
              class="swatch"
            ></span>
          </span>
          <span class="option-main">
            <strong>
              {{ theme.themeName }}
              <ElTag
                v-if="theme.mallDefaultFlag === '1'"
                effect="plain"
                size="small"
                type="warning"
              >
                商城默认
              </ElTag>
            </strong>
            <span class="option-meta">
              主色 {{ theme.primaryColor || '—' }}
            </span>
          </span>
          <ElTag
            v-if="currentId === theme.id"
            effect="dark"
            size="small"
            type="primary"
          >
            当前
          </ElTag>
        </button>
      </template>
    </div>

    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">关闭</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.dialog-tip {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.theme-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 420px;
  overflow-y: auto;
}

.theme-option {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.theme-option:hover {
  border-color: var(--el-color-primary-light-5);
}

.theme-option.is-active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.option-swatches {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.swatch {
  width: 18px;
  height: 18px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.swatch-primary {
  width: 24px;
}

.option-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.option-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
