<script setup lang="ts">
import type { CanvasThemeVars } from '../schema/theme-presets';
import type { PageSettings } from '../schema/types';

import { computed, reactive, watch } from 'vue';

import {
  ElColorPicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElSwitch,
} from 'element-plus';

import { cloneDesignerValue, isSameDesignerValue } from '../schema/clone';

const props = defineProps<{
  modelValue: PageSettings;
  /**
   * 当前页面引用的主题名（「本页主题」已指定时传入）：
   * 配色随该主题，取色器与跟随开关一并停用，避免改了不生效的值。
   */
  pageThemeName?: string;
  /**
   * 有效主题配色（与画布 themeVars 同源，页面未指定主题时即商城默认主题）：
   * 跟随态下取色器展示该值并禁用，保证面板与画布/实机所见一致。
   */
  themeVars?: CanvasThemeVars;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: PageSettings] }>();

const form = reactive<PageSettings>(cloneDesignerValue(props.modelValue));

watch(
  () => props.modelValue,
  (value) => {
    if (!isSameDesignerValue(form, value)) {
      Object.assign(form, cloneDesignerValue(value));
    }
  },
  { deep: true },
);
watch(
  form,
  (value) => {
    if (!isSameDesignerValue(value, props.modelValue)) {
      emit('update:modelValue', cloneDesignerValue(value));
    }
  },
  { deep: true },
);

/**
 * 配色来源（三态互斥）：本页主题 > 商城默认主题（跟随，缺省） > 自定义配色。
 * followMallTheme 缺省视为跟随，存量页面与模板行为不变。
 */
const followMallTheme = computed({
  get: () => form.followMallTheme !== false,
  set: (value: boolean) => {
    form.followMallTheme = value;
  },
});

/** 取色器是否被上层接管：跟随商城主题 或 页面已指定主题 时改了也不生效 */
const colorControlled = computed(
  () => followMallTheme.value || !!props.pageThemeName,
);

/**
 * 跟随/主题接管态下取色器展示有效主题色（与画布同源），而非页面自存值；
 * 主题色缺失（主题库加载失败）时回落页面自存值，保持可读。
 */
const pageBackgroundColorModel = computed<null | string | undefined>({
  get: () =>
    colorControlled.value
      ? props.themeVars?.pageBackgroundColor || form.backgroundColor
      : form.backgroundColor,
  set: (value) => {
    form.backgroundColor = value ?? '';
  },
});

const navigationBackgroundColorModel = computed<null | string | undefined>({
  get: () =>
    colorControlled.value
      ? props.themeVars?.navigationColor || form.navigation.backgroundColor
      : form.navigation.backgroundColor,
  set: (value) => {
    form.navigation.backgroundColor = value ?? '';
  },
});

const navigationTextColorModel = computed<null | string | undefined>({
  get: () =>
    colorControlled.value
      ? props.themeVars?.navigationTextColor || form.navigation.textColor
      : form.navigation.textColor,
  set: (value) => {
    form.navigation.textColor = value ?? '';
  },
});
</script>

<template>
  <ElForm :model="form" label-position="top" class="page-settings">
    <ElFormItem label="配色来源">
      <template v-if="pageThemeName">
        <p class="follow-hint">
          本页已指定主题「{{ pageThemeName }}」，页面背景与导航配色跟随该主题，
          可通过顶部「本页主题」改选。
        </p>
      </template>
      <template v-else>
        <div class="follow-row">
          <ElSwitch v-model="followMallTheme" />
          <span class="follow-label">
            {{ followMallTheme ? '跟随商城主题' : '自定义配色' }}
          </span>
        </div>
        <p class="follow-hint">
          {{
            followMallTheme
              ? '页面背景与导航配色由「商城装修 → 商城主题」统一控制，下方颜色暂不生效。'
              : '使用下方自定义配色，不再跟随商城主题；重新开启即可回到全局换肤。'
          }}
        </p>
      </template>
    </ElFormItem>
    <ElFormItem label="页面背景色">
      <ElColorPicker
        v-model="pageBackgroundColorModel"
        :disabled="colorControlled"
        show-alpha
      />
    </ElFormItem>
    <ElFormItem label="页面背景图">
      <ElInput
        v-model="form.backgroundImage"
        clearable
        placeholder="图片地址"
      />
    </ElFormItem>
    <ElFormItem label="显示导航栏">
      <ElSwitch v-model="form.navigation.visible" />
    </ElFormItem>
    <ElFormItem label="导航标题">
      <ElInput v-model="form.navigation.title" maxlength="30" />
    </ElFormItem>
    <ElFormItem label="导航背景 / 文字">
      <div class="color-row">
        <ElColorPicker
          v-model="navigationBackgroundColorModel"
          :disabled="colorControlled"
        />
        <ElColorPicker
          v-model="navigationTextColorModel"
          :disabled="colorControlled"
        />
      </div>
    </ElFormItem>
    <ElFormItem label="分享标题">
      <ElInput v-model="form.share.title" maxlength="60" />
    </ElFormItem>
    <ElFormItem label="分享描述">
      <ElInput
        v-model="form.share.description"
        maxlength="120"
        type="textarea"
      />
    </ElFormItem>
    <ElFormItem label="分享图片">
      <ElInput v-model="form.share.imageUrl" clearable placeholder="图片地址" />
    </ElFormItem>
    <ElFormItem label="下拉刷新">
      <ElSwitch v-model="form.enablePullDownRefresh" />
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.page-settings {
  padding: 16px;
}

.color-row {
  display: flex;
  gap: 12px;
}

.follow-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.follow-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.follow-hint {
  width: 100%;
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}
</style>
