<script setup lang="ts">
import type { ActivityStripEntry, ActivityStripProps } from './types';

import { ref, watch } from 'vue';

import { Delete, Plus } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTooltip,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import LinkUrl from '../common/link-url/index.vue';
import { createActivityStripManualEntries } from './types';

/**
 * 活动入口条属性面板。
 *
 * 与 C 端 diy-activity-strip 同源：
 * · 自动模式（默认）：并行取进行中的秒杀/拼团/折扣各一条，运营无需选活动 ID；
 * · 手动模式（§5.5.4）：手配卡片（标题/钩子/角标/缩略图/跳转链接），
 *   切到手动时预填三类示例卡，最多 10 张。
 *
 * 通用样式（背景/边距/圆角）沿用属性面板的「样式」页，与秒杀/折扣一致，
 * 因此本文件不再单独提供 style-setting.vue。
 */
const props = defineProps<{ modelValue: ActivityStripProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: ActivityStripProps];
}>();
const form = ref(cloneDesignerValue(props.modelValue));

watch(
  () => props.modelValue,
  (value) => {
    if (!isSameDesignerValue(form.value, value))
      form.value = cloneDesignerValue(value);
  },
  { deep: true },
);
watch(
  form,
  (value) => {
    if (!isSameDesignerValue(value, props.modelValue))
      emit('update:modelValue', cloneDesignerValue(value));
  },
  { deep: true },
);

/** 手动模式卡片类型选项：内置三类固定跳对应会场，custom 走自定义链接 */
const entryKeyOptions = [
  { label: '秒杀会场', value: 'seckill' },
  { label: '拼团会场', value: 'group-buy' },
  { label: '折扣会场', value: 'discount' },
  { label: '自定义入口', value: 'custom' },
] as const;

/**
 * 手动模式草稿缓存：切回自动模式时数据结构里不再保留 entries（与 C 端
 * ActivityStripDataSource 判别联合同形），这里在面板内暂存，避免误切一次
 * 就把运营手配的卡片丢掉。
 */
let manualEntriesCache: ActivityStripEntry[] = [];

/**
 * 数据来源切换：切到手动模式时用缓存/三类示例卡填充，
 * 与自动模式的组装结果同形（key/标题/角标/钩子/会场路由一致）。
 */
function onDataSourceChange(mode: unknown) {
  if (mode === 'manual') {
    const entries =
      manualEntriesCache.length > 0
        ? manualEntriesCache
        : createActivityStripManualEntries();
    form.value.dataSource = {
      entries: cloneDesignerValue(entries),
      mode: 'manual',
    };
    return;
  }
  if (form.value.dataSource.mode === 'manual')
    manualEntriesCache = cloneDesignerValue(form.value.dataSource.entries);
  form.value.dataSource = { mode: 'automatic' };
}

/** 新增卡片：默认自定义入口 + 空链接，运营填文案后选链接目标（最多 10 张） */
function addEntry() {
  if (form.value.dataSource.mode !== 'manual') return;
  form.value.dataSource.entries.push({
    badge: '',
    hook: '',
    imageUrl: '',
    key: 'custom',
    link: { params: {}, path: '', type: 'custom' },
    title: '新活动入口',
  });
}

function removeEntry(index: number) {
  if (form.value.dataSource.mode !== 'manual') return;
  form.value.dataSource.entries.splice(index, 1);
}
</script>

<template>
  <ElForm :model="form" label-position="top" class="retail-setting">
    <ElFormItem label="数据来源">
      <ElRadioGroup
        :model-value="form.dataSource.mode"
        @change="onDataSourceChange"
      >
        <ElRadio value="automatic">自动取进行中活动</ElRadio>
        <ElRadio value="manual">手动配置卡片</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElAlert
      v-if="form.dataSource.mode === 'automatic'"
      :closable="false"
      show-icon
      title="并行读取进行中的秒杀 / 拼团 / 折扣各一条，入口固定跳对应会场；某类没有进行中活动时只隐藏该卡。卡片按实际数量自动等宽排布。"
      type="info"
    />
    <template v-else>
      <ElAlert
        :closable="false"
        show-icon
        title="卡片按添加数量自动排布，一行最多三张，超出自动换行；跳转链接默认指向对应会场，也可改成领券中心等任意装修链接。"
        type="info"
      />
      <div class="entry-list">
        <section
          v-for="(entry, index) in form.dataSource.entries"
          :key="index"
          class="entry-item"
        >
          <header>
            <strong>卡片 {{ index + 1 }}</strong>
            <ElTooltip content="删除卡片">
              <ElButton
                :icon="Delete"
                aria-label="删除卡片"
                circle
                size="small"
                type="danger"
                @click="removeEntry(index)"
              />
            </ElTooltip>
          </header>
          <ElFormItem label="卡片类型">
            <ElSelect v-model="entry.key">
              <ElOption
                v-for="option in entryKeyOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="活动名">
            <ElInput
              v-model="entry.title"
              maxlength="6"
              placeholder="如 限时秒杀"
              show-word-limit
            />
          </ElFormItem>
          <ElFormItem label="钩子文案">
            <ElInput
              v-model="entry.hook"
              maxlength="10"
              placeholder="如 午间场·整点开抢（可留空）"
            />
          </ElFormItem>
          <ElFormItem label="角标">
            <ElInput
              v-model="entry.badge"
              maxlength="6"
              placeholder="如 抢 / 省¥4 / 9.5折（可留空）"
            />
          </ElFormItem>
          <ElFormItem label="缩略图地址">
            <ElInput
              v-model="entry.imageUrl"
              clearable
              placeholder="可留空，缺图回落首字色块"
            />
          </ElFormItem>
          <ElFormItem label="跳转链接">
            <LinkUrl v-model="entry.link" />
          </ElFormItem>
        </section>
      </div>
      <ElButton
        :disabled="form.dataSource.entries.length >= 10"
        class="add-entry"
        @click="addEntry"
      >
        <ElIcon><Plus /></ElIcon>添加卡片
      </ElButton>
    </template>
    <ElFormItem label="无数据策略">
      <ElSelect v-model="form.emptyStrategy">
        <ElOption label="隐藏组件" value="hide" />
        <ElOption label="显示占位" value="placeholder" />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="失效数据策略">
      <ElSelect v-model="form.invalidStrategy">
        <ElOption label="隐藏组件" value="hide" />
        <ElOption label="显示占位" value="placeholder" />
      </ElSelect>
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.retail-setting :deep(.el-select),
.retail-setting :deep(.el-input-number),
.add-entry {
  width: 100%;
}

.retail-setting :deep(.el-alert) {
  margin-bottom: 16px;
}

.entry-list {
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.entry-item {
  padding: 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.entry-item header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
</style>
