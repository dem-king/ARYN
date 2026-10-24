<script setup lang="ts">
import type { UploadProps, UploadRequestOptions } from 'element-plus';

import { defineAsyncComponent, reactive, watch } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElUpload,
} from 'element-plus';

import { uploadFile } from '#/api/upms/upload';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import CategoryPicker from '../common/category-picker/index.vue';
import IdPickerField from '../common/id-picker-field/index.vue';

const props = defineProps<{
  modelValue: Record<string, unknown>;
  type: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>];
}>();

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

type SettingValue =
  boolean | number | Record<string, unknown> | string | undefined | unknown[];

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

function targetIds(key = 'dataSource') {
  const dataSource = (form[key] ?? {}) as unknown as Record<string, unknown>;
  return (dataSource.targetIds ?? []) as string[];
}

function updateDataSource(patch: Record<string, unknown>) {
  form.dataSource = {
    ...((form.dataSource ?? {}) as unknown as Record<string, unknown>),
    ...patch,
  };
  commit();
}

function updateTargetIds(value: string[]) {
  updateDataSource({ targetIds: value });
}

function tabList() {
  return (form.tabs ?? []) as unknown as Array<Record<string, unknown>>;
}

function tabDataSource(tab: Record<string, unknown>) {
  return (tab.dataSource ?? {}) as Record<string, unknown>;
}

function updateTab(index: number, patch: Record<string, unknown>) {
  form.tabs = tabList().map((tab, current) =>
    current === index ? { ...tab, ...patch } : tab,
  );
  commit();
}

function updateTabDataSource(index: number, patch: Record<string, unknown>) {
  form.tabs = tabList().map((tab, current) =>
    current === index
      ? { ...tab, dataSource: { ...tabDataSource(tab), ...patch } }
      : tab,
  );
  commit();
}

function addTab() {
  const list = tabList();
  list.push({
    dataSource: {
      cacheTtl: 0,
      categoryId: '',
      mode: 'automatic',
      sort: 'sales',
      targetIds: [],
    },
    id: `tab-${Date.now().toString(36)}`,
    title: `Tab ${list.length + 1}`,
  });
  form.tabs = list;
  commit();
}

function removeTab(index: number) {
  const list = [...tabList()];
  list.splice(index, 1);
  form.tabs = list;
  commit();
}

const VIDEO_MAX_SIZE_MB = 100;

const beforeVideoUpload: UploadProps['beforeUpload'] = (rawFile) => {
  if (rawFile.type && !rawFile.type.startsWith('video/')) {
    ElMessage.error('视频格式必须是 MP4、MOV 或 WEBM！');
    return false;
  }
  if (rawFile.size / 1024 / 1024 > VIDEO_MAX_SIZE_MB) {
    ElMessage.error(`视频大小不能超过 ${VIDEO_MAX_SIZE_MB}MB！`);
    return false;
  }
  return true;
};

const handleVideoUpload = async (options: UploadRequestOptions) => {
  const formData = new FormData();
  formData.append('file', options.file);
  formData.append('type', '2');
  const material = (await uploadFile(formData)) as undefined | { url?: string };
  form.videoUrl = material?.url ?? '';
  commit();
};

function clearVideo() {
  form.videoUrl = '';
  commit();
}
</script>

<template>
  <ElForm label-position="top" class="extension-settings" @submit.prevent>
    <template v-if="type === 'goods-waterfall'">
      <ElFormItem label="数据来源">
        <ElRadioGroup
          :model-value="
            (form.dataSource as unknown as Record<string, unknown> | undefined)
              ?.mode as string
          "
          @update:model-value="updateDataSource({ mode: $event as string })"
        >
          <ElRadio value="automatic">按分类/排行自动</ElRadio>
          <ElRadio value="manual">手选数据</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem
        v-if="(form.dataSource as Record<string, unknown>)?.mode === 'manual'"
        label="手选商品"
      >
        <IdPickerField
          kind="goods"
          :limit="30"
          :model-value="targetIds()"
          @update:model-value="updateTargetIds"
        />
      </ElFormItem>
      <ElFormItem label="分类（自动模式）">
        <CategoryPicker
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
      <ElFormItem label="列数">
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
      <ElFormItem label="显示价格">
        <ElSwitch v-model="form.showPrice as boolean" @change="commit" />
      </ElFormItem>
      <ElFormItem label="显示销量">
        <ElSwitch v-model="form.showSales as boolean" @change="commit" />
      </ElFormItem>
      <ElFormItem label="商品原价">
        <ElSwitch
          v-model="form.showOriginalPrice as boolean"
          @change="commit"
        />
      </ElFormItem>
    </template>

    <template v-else-if="type === 'goods-tabs'">
      <ElFormItem label="Tab 配置">
        <div class="tab-editor">
          <div
            v-for="(tab, index) in tabList()"
            :key="String(tab.id)"
            class="tab-card"
          >
            <div class="tab-card__head">
              <ElInput
                :model-value="tab.title as string"
                maxlength="10"
                placeholder="Tab 标题"
                @update:model-value="updateTab(index, { title: $event })"
              />
              <ElButton
                size="small"
                text
                type="danger"
                :disabled="tabList().length <= 1"
                @click="removeTab(index)"
              >
                删除
              </ElButton>
            </div>
            <ElRadioGroup
              :model-value="tabDataSource(tab).mode as string"
              @update:model-value="
                updateTabDataSource(index, { mode: $event as string })
              "
            >
              <ElRadio value="automatic">按分类/排序自动</ElRadio>
              <ElRadio value="manual">手选商品</ElRadio>
            </ElRadioGroup>
            <div
              v-if="tabDataSource(tab).mode === 'manual'"
              class="tab-card__field"
            >
              <span class="tab-card__label">手选商品</span>
              <IdPickerField
                kind="goods"
                :limit="30"
                :model-value="(tabDataSource(tab).targetIds ?? []) as string[]"
                @update:model-value="
                  updateTabDataSource(index, { targetIds: $event })
                "
              />
            </div>
            <template v-else>
              <div class="tab-card__field">
                <span class="tab-card__label">分类（留空为全部）</span>
                <CategoryPicker
                  :model-value="tabDataSource(tab).categoryId as string"
                  @update:model-value="
                    updateTabDataSource(index, { categoryId: $event })
                  "
                />
              </div>
              <div class="tab-card__field">
                <span class="tab-card__label">排序</span>
                <ElSelect
                  :model-value="tabDataSource(tab).sort as string"
                  @update:model-value="
                    updateTabDataSource(index, { sort: $event as string })
                  "
                >
                  <ElOption label="销量优先" value="sales" />
                  <ElOption label="新品优先" value="create_time" />
                  <ElOption label="价格从低到高" value="sales_price" />
                </ElSelect>
              </div>
            </template>
          </div>
          <ElButton
            size="small"
            :disabled="tabList().length >= 6"
            @click="addTab"
          >
            添加 Tab
          </ElButton>
          <div class="tab-editor__hint">
            「低价捡漏」建议用价格从低到高；「会员专区」建议切为手选商品，
            先以运营货盘验证交互。
          </div>
        </div>
      </ElFormItem>
      <ElFormItem label="每页加载数量">
        <ElInputNumber
          :model-value="form.count as number"
          :min="1"
          :max="30"
          @update:model-value="
            form.count = $event;
            commit();
          "
        />
        <div class="tab-editor__hint">
          自动 Tab 按该数量分批加载；手选 Tab 只展示前 N 个。
        </div>
      </ElFormItem>
      <ElFormItem label="每行列数">
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
      <ElFormItem label="Tab 栏吸顶">
        <ElSwitch v-model="form.stickyTabs as boolean" @change="commit" />
      </ElFormItem>
      <ElFormItem label="显示价格">
        <ElSwitch v-model="form.showPrice as boolean" @change="commit" />
      </ElFormItem>
      <ElFormItem label="显示销量">
        <ElSwitch v-model="form.showSales as boolean" @change="commit" />
      </ElFormItem>
      <ElFormItem label="商品原价">
        <ElSwitch
          v-model="form.showOriginalPrice as boolean"
          @change="commit"
        />
      </ElFormItem>
    </template>

    <template v-else-if="type === 'goods-recommend'">
      <ElFormItem label="标题">
        <ElInput
          v-model="form.title as string"
          maxlength="10"
          placeholder="留空则不显示标题行"
          @update:model-value="commit"
        />
      </ElFormItem>
      <ElFormItem label="数据来源">
        <ElRadioGroup
          :model-value="
            (form.dataSource as unknown as Record<string, unknown> | undefined)
              ?.mode as string
          "
          @update:model-value="updateDataSource({ mode: $event as string })"
        >
          <ElRadio value="automatic">同分类自动推荐</ElRadio>
          <ElRadio value="manual">手选商品</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem
        v-if="(form.dataSource as Record<string, unknown>)?.mode === 'manual'"
        label="手选商品"
      >
        <IdPickerField
          kind="goods"
          :limit="20"
          :model-value="targetIds()"
          @update:model-value="updateTargetIds"
        />
      </ElFormItem>
      <ElFormItem label="展示数量">
        <ElInputNumber
          :model-value="form.count as number"
          :min="1"
          :max="20"
          @update:model-value="
            form.count = $event;
            commit();
          "
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
      <ElFormItem label="显示价格">
        <ElSwitch v-model="form.showPrice as boolean" @change="commit" />
      </ElFormItem>
      <ElFormItem label="显示划线原价">
        <ElSwitch
          v-model="form.showOriginalPrice as boolean"
          @change="commit"
        />
      </ElFormItem>
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
      <ElFormItem v-if="form.mode === 'video'" label="视频">
        <div class="video-upload">
          <video
            v-if="form.videoUrl"
            class="video-upload__preview"
            :src="form.videoUrl as string"
            controls
          ></video>
          <div class="video-upload__actions">
            <ElUpload
              action="#"
              accept="video/mp4,video/webm,video/quicktime,.mp4,.mov,.m4v,.webm"
              :before-upload="beforeVideoUpload"
              :http-request="handleVideoUpload"
              :show-file-list="false"
            >
              <ElButton size="small" type="primary">
                {{ form.videoUrl ? '重新上传' : '上传视频' }}
              </ElButton>
            </ElUpload>
            <ElButton
              v-if="form.videoUrl"
              size="small"
              text
              type="danger"
              @click="clearVideo"
            >
              清除
            </ElButton>
          </div>
          <p class="video-upload__tip">
            支持 MP4 / MOV / M4V / WEBM，单个不超过 100MB
          </p>
        </div>
      </ElFormItem>
      <ElFormItem v-if="form.mode === 'live'" label="直播间 ID">
        <ElInput v-model="form.liveId as string" @update:model-value="commit" />
      </ElFormItem>
      <ElFormItem label="封面图">
        <SelectMaterial
          v-model="form.coverUrl as string"
          :can-choose-images-num="1"
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

.video-upload {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.video-upload__preview {
  width: 100%;
  max-width: 320px;
  border-radius: 6px;
}

.video-upload__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.video-upload__tip {
  margin: 0;
  color: #a8abb2;
}

.tab-editor {
  display: grid;
  gap: 10px;
  width: 100%;
}

.tab-card {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.tab-card__head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tab-card__field {
  display: grid;
  gap: 4px;
}

.tab-card__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tab-editor__hint {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.tab-editor :deep(.el-select) {
  width: 100%;
}
</style>
