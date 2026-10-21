<script setup lang="ts">
import type { IdPickerItem, IdPickerKind } from './helpers';

import { computed, reactive, ref, watch } from 'vue';

import { CircleCloseFilled } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElImage, ElMessage } from 'element-plus';

import { invalidItem, mergePickedIds, missingIds } from './helpers';
import { pickerDefinitions } from './registry';

const props = withDefaults(
  defineProps<{
    kind: IdPickerKind;
    /** 手选上限，缺省取该类型组件「展示数量」可设置的最大值 */
    limit?: number;
    modelValue?: string[];
    /**
     * 单选替换语义（链接目标等只有 1 个值的场景）：
     * 已选满时仍可直接打开选择器，选中后覆盖原值，不必先删除再选。
     */
    replaceOnPick?: boolean;
  }>(),
  { limit: undefined, modelValue: () => [], replaceOnPick: false },
);

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>();

const definition = computed(() => pickerDefinitions[props.kind]);
const limit = computed(() => props.limit ?? definition.value.limit);
const ids = computed(() => (props.modelValue ?? []).filter(Boolean));
const remaining = computed(() => Math.max(0, limit.value - ids.value.length));

const itemMap = reactive(new Map<string, IdPickerItem>());
const items = computed(() =>
  ids.value.map((id) => itemMap.get(id) ?? { id, title: id }),
);

const dialogRef = ref();
const dialogBindings = computed(() => {
  // 替换语义下每次都是「重新选一个」，选择器上限按 limit 而不是剩余名额
  const selectable = props.replaceOnPick ? limit.value : remaining.value;
  return definition.value.dialogProps
    ? {
        ...definition.value.dialogProps,
        limitNum: selectable,
        noun: definition.value.noun,
      }
    : { limitNum: selectable };
});

/**
 * 回源补齐已选项名称：配置里只存 ID（targetIds），面板展示靠这里换成人能看懂的名称。
 * 网络失败时先以 ID 文本兜底（不标失效），下次触发再重试。
 */
let hydrateToken = 0;
watch(
  ids,
  async (value) => {
    const missing = missingIds(value, new Set(itemMap.keys()));
    if (missing.length === 0) return;
    const token = ++hydrateToken;
    try {
      const fetched = await definition.value.hydrate(missing);
      if (token !== hydrateToken) return;
      for (const item of fetched) itemMap.set(item.id, item);
      for (const id of missing) {
        if (!itemMap.has(id)) itemMap.set(id, invalidItem(id));
      }
    } catch {
      if (token !== hydrateToken) return;
      for (const id of missing) {
        if (!itemMap.has(id)) itemMap.set(id, { id, title: id });
      }
    }
  },
  { immediate: true },
);

function openPicker() {
  if (!props.replaceOnPick && remaining.value <= 0) {
    ElMessage.warning(
      `最多选择 ${limit.value} 个${definition.value.noun}，请先删除已选项`,
    );
    return;
  }
  dialogRef.value?.initPage();
}

function onPicked(rows: Array<Record<string, any>>) {
  const picked = rows.map((row) => String(row.id ?? '')).filter(Boolean);
  for (const row of rows) {
    const item = definition.value.mapRow(row);
    if (item.id) itemMap.set(item.id, item);
  }
  emit(
    'update:modelValue',
    props.replaceOnPick
      ? picked.slice(0, limit.value)
      : mergePickedIds(ids.value, picked, limit.value),
  );
}

function removeItem(id: string) {
  emit(
    'update:modelValue',
    ids.value.filter((item) => item !== id),
  );
}
</script>

<template>
  <div class="id-picker-field">
    <div v-if="items.length > 0" class="id-picker-field__list">
      <div v-for="item in items" :key="item.id" class="id-picker-field__row">
        <ElImage
          v-if="item.image"
          class="id-picker-field__thumb"
          :src="item.image"
          fit="cover"
        />
        <div class="id-picker-field__meta">
          <span
            class="id-picker-field__title"
            :class="{ 'is-invalid': item.invalid }"
          >
            {{ item.title }}
          </span>
          <span v-if="item.subtitle" class="id-picker-field__subtitle">
            {{ item.subtitle }}
          </span>
        </div>
        <ElButton link type="danger" @click="removeItem(item.id)">
          <ElIcon><CircleCloseFilled /></ElIcon>
        </ElButton>
      </div>
    </div>
    <p v-else class="id-picker-field__empty">{{ definition.emptyText }}</p>
    <ElButton class="id-picker-field__add" @click="openPicker">
      {{ definition.buttonText }}
    </ElButton>
    <component
      :is="definition.dialog"
      ref="dialogRef"
      v-bind="dialogBindings"
      @current-row="onPicked"
    />
  </div>
</template>

<style scoped>
.id-picker-field {
  display: grid;
  gap: 8px;
  width: 100%;
}

.id-picker-field__list {
  display: grid;
  gap: 8px;
}

.id-picker-field__row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 8px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
}

.id-picker-field__thumb {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 4px;
}

.id-picker-field__meta {
  display: grid;
  flex: 1;
  gap: 2px;
  min-width: 0;
}

.id-picker-field__title {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  white-space: nowrap;
}

.id-picker-field__title.is-invalid {
  color: var(--el-color-danger);
}

.id-picker-field__subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.id-picker-field__empty {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.id-picker-field__add {
  width: 100%;
}
</style>
