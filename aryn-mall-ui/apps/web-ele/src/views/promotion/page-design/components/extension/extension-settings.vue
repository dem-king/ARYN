<script setup lang="ts">
import { reactive, watch } from 'vue';

import {
  ElButton,
  ElColorPicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElRadio,
  ElRadioGroup,
  ElSwitch,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';

const props = defineProps<{
  modelValue: Record<string, unknown>;
  type: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>];
}>();

type SettingValue =
  boolean | number | Record<string, unknown> | string | undefined;

const form = reactive<Record<string, SettingValue>>({});

watch(
  () => props.modelValue,
  (value) => {
    Object.assign(form, cloneDesignerValue(value));
  },
  { immediate: true, deep: true },
);

function commit() {
  const next = cloneDesignerValue(form) as Record<string, unknown>;
  if (!isSameDesignerValue(next, props.modelValue)) {
    emit('update:modelValue', next);
  }
}

function entryList(key: string) {
  return (form[key] ?? []) as unknown as Array<Record<string, unknown>>;
}

function addEntry(key: string, template: Record<string, unknown>) {
  const list = entryList(key);
  list.push({
    ...cloneDesignerValue(template),
    id: `${key}-${list.length + 1}`,
  });
  commit();
}

function removeEntry(key: string, index: number) {
  entryList(key).splice(index, 1);
  commit();
}

function targetIdsText(key = 'dataSource') {
  const dataSource = (form[key] ?? {}) as unknown as Record<string, unknown>;
  return ((dataSource.targetIds ?? []) as string[]).join(',');
}

function updateDataSource(patch: Record<string, unknown>) {
  form.dataSource = {
    ...((form.dataSource ?? {}) as unknown as Record<string, unknown>),
    ...patch,
  };
  commit();
}

function updateTargetIds(value: string) {
  const ids = value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  updateDataSource({ targetIds: ids });
}
</script>

<template>
  <ElForm label-position="top" class="extension-settings" @submit.prevent>
    <template v-if="type === 'goods-waterfall' || type === 'coupon-combo'">
      <ElFormItem label="数据来源">
        <ElRadioGroup
          :model-value="
            (form.dataSource as unknown as Record<string, unknown> | undefined)
              ?.mode as string
          "
          @update:model-value="updateDataSource({ mode: $event as string })"
        >
          <ElRadio value="automatic">
            {{ type === 'coupon-combo' ? '自动领取列表' : '按分类/排行自动' }}
          </ElRadio>
          <ElRadio value="manual">手选数据</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem
        v-if="(form.dataSource as Record<string, unknown>)?.mode === 'manual'"
        label="手选 ID（逗号分隔）"
      >
        <ElInput
          :model-value="targetIdsText()"
          @update:model-value="updateTargetIds"
        />
      </ElFormItem>
      <ElFormItem v-if="type === 'goods-waterfall'" label="分类 ID（自动模式）">
        <ElInput
          :model-value="
            (form.dataSource as unknown as Record<string, unknown> | undefined)
              ?.categoryId as string
          "
          @update:model-value="updateDataSource({ categoryId: $event })"
        />
      </ElFormItem>
      <ElFormItem label="数据缓存（秒，0 不缓存）">
        <ElInputNumber
          :model-value="
            Number(
              (
                form.dataSource as unknown as
                  Record<string, unknown> | undefined
              )?.cacheTtl ?? 0,
            )
          "
          :min="0"
          @update:model-value="updateDataSource({ cacheTtl: $event })"
        />
      </ElFormItem>
      <ElFormItem label="展示数量">
        <ElInputNumber
          :model-value="form.count as number"
          :min="1"
          :max="30"
          @update:model-value="
            form.count = $event;
            commit();
          "
        />
      </ElFormItem>
      <ElFormItem v-if="type === 'goods-waterfall'" label="列数">
        <ElRadioGroup
          :model-value="form.columns as number"
          @update:model-value="
            form.columns = $event;
            commit();
          "
        >
          <ElRadio :value="2">两列</ElRadio>
          <ElRadio :value="3">三列</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <template v-if="type === 'goods-waterfall'">
        <ElFormItem label="显示价格">
          <ElSwitch v-model="form.showPrice as boolean" @change="commit" />
        </ElFormItem>
        <ElFormItem label="显示销量">
          <ElSwitch v-model="form.showSales as boolean" @change="commit" />
        </ElFormItem>
      </template>
      <template v-else>
        <ElFormItem label="显示领取按钮">
          <ElSwitch v-model="form.showReceiveBtn as boolean" @change="commit" />
        </ElFormItem>
        <ElFormItem label="显示使用门槛">
          <ElSwitch v-model="form.showThreshold as boolean" @change="commit" />
        </ElFormItem>
      </template>
    </template>

    <template
      v-else-if="type === 'member-benefits' || type === 'service-promise'"
    >
      <ElFormItem label="标题">
        <ElInput
          v-model="form.title as string"
          maxlength="20"
          @update:model-value="commit"
        />
      </ElFormItem>
      <ElFormItem label="配置项">
        <div class="entry-editor">
          <div
            v-for="(entry, index) in entryList(
              type === 'member-benefits' ? 'entries' : 'items',
            )"
            :key="index"
            class="entry-row"
          >
            <ElInput
              v-model="entry.title as string"
              placeholder="标题"
              @update:model-value="commit"
            />
            <ElInput
              v-model="entry.description as string"
              placeholder="描述（可选）"
              @update:model-value="commit"
            />
            <ElButton
              size="small"
              text
              type="danger"
              @click="
                removeEntry(
                  type === 'member-benefits' ? 'entries' : 'items',
                  index,
                )
              "
            >
              删除
            </ElButton>
          </div>
          <ElButton
            size="small"
            @click="
              addEntry(type === 'member-benefits' ? 'entries' : 'items', {
                description: '',
                iconUrl: '',
                link: { params: {}, path: '', type: 'custom' },
                title: '',
              })
            "
          >
            添加一项
          </ElButton>
        </div>
      </ElFormItem>
    </template>

    <template v-else-if="type === 'bottom-nav'">
      <ElFormItem label="导航项">
        <div class="entry-editor">
          <div
            v-for="(item, index) in entryList('items')"
            :key="index"
            class="entry-row"
          >
            <ElInput
              v-model="item.text as string"
              placeholder="文字"
              @update:model-value="commit"
            />
            <ElButton
              size="small"
              text
              type="danger"
              @click="removeEntry('items', index)"
            >
              删除
            </ElButton>
          </div>
          <ElButton
            size="small"
            @click="
              addEntry('items', {
                iconUrl: '',
                link: { params: {}, path: '', type: 'custom' },
                text: '',
              })
            "
          >
            添加导航项
          </ElButton>
        </div>
      </ElFormItem>
      <ElFormItem label="背景色 / 文字色 / 选中色">
        <div class="color-row">
          <ElColorPicker
            v-model="form.backgroundColor as string"
            @update:model-value="commit"
          />
          <ElColorPicker
            v-model="form.textColor as string"
            @update:model-value="commit"
          />
          <ElColorPicker
            v-model="form.activeColor as string"
            @update:model-value="commit"
          />
        </div>
      </ElFormItem>
    </template>

    <template v-else-if="type === 'video-live'">
      <ElFormItem label="展示形式">
        <ElRadioGroup
          :model-value="form.mode as string"
          @update:model-value="
            form.mode = $event;
            commit();
          "
        >
          <ElRadio value="video">视频</ElRadio>
          <ElRadio value="live">直播入口</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="标题">
        <ElInput
          v-model="form.title as string"
          maxlength="20"
          @update:model-value="commit"
        />
      </ElFormItem>
      <ElFormItem v-if="form.mode === 'video'" label="视频地址">
        <ElInput
          v-model="form.videoUrl as string"
          @update:model-value="commit"
        />
      </ElFormItem>
      <ElFormItem v-if="form.mode === 'live'" label="直播间 ID">
        <ElInput v-model="form.liveId as string" @update:model-value="commit" />
      </ElFormItem>
      <ElFormItem label="封面图">
        <ElInput
          v-model="form.coverUrl as string"
          @update:model-value="commit"
        />
      </ElFormItem>
    </template>
  </ElForm>
</template>

<style scoped>
.entry-editor {
  display: grid;
  gap: 8px;
  width: 100%;
}

.entry-row {
  display: grid;
  gap: 6px;
}

.color-row {
  display: flex;
  gap: 12px;
}
</style>
