<script setup lang="ts">
import type { UploadProps, UploadRequestOptions } from 'element-plus';

import { computed, defineAsyncComponent, reactive, ref, watch } from 'vue';

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

import { getActivityPage } from '#/api/promotion/ship-activity';
import { uploadFile } from '#/api/upms/upload';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import CategoryPicker from '../common/category-picker/index.vue';
import IdPickerField from '../common/id-picker-field/index.vue';
import { DEFAULT_BARGAIN_RATE } from './types';

const props = defineProps<{
  modelValue: Record<string, unknown>;
  type: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>];
}>();

/**
 * 会员权益条会员文案的占位符说明。
 *
 * 放在脚本里而不是模板文本节点：模板文案过长时 Prettier 会在中文之间换行，
 * 渲染时被压缩成一个空格（如「9 折」），导致提示语与约定文案不一致。
 */
const MEMBER_BANNER_HINT =
  '{levelName} 为等级名；{discount} 是会员折扣率权益（0.9→9折），与「会员价活动」是两套体系，默认文案不写折数，避免折数与实价不符。';

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

/**
 * 切换数据源模式：切到 activity 时补齐 activityId=""（契约字段，空串=自动取进行中活动），
 * 避免 C 端/后端读到缺字段；已有指定活动时保留原值。
 */
function updateTabMode(index: number, mode: string) {
  const tab = tabList()[index];
  if (!tab) return;
  const patch: Record<string, unknown> = { mode };
  if (
    mode === 'activity' &&
    typeof tabDataSource(tab).activityId !== 'string'
  ) {
    patch.activityId = '';
  }
  updateTabDataSource(index, patch);
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

function tabMemberBanner(tab: Record<string, unknown>) {
  return (tab.memberBanner ?? {}) as Record<string, unknown>;
}

function updateTabMemberBanner(index: number, patch: Record<string, unknown>) {
  form.tabs = tabList().map((tab, current) =>
    current === index
      ? { ...tab, memberBanner: { ...tabMemberBanner(tab), ...patch } }
      : tab,
  );
  commit();
}

/** 阈值清空时回落默认值，避免把 null / 非法值写进持久化 JSON（后端会拒绝发布） */
function updateTabBargainRate(index: number, value: null | number | undefined) {
  updateTabDataSource(index, {
    bargainRate:
      value === null || value === undefined ? DEFAULT_BARGAIN_RATE : value,
  });
}

/** 会员价活动状态文案（promotion_activity.status；4/5 为管理端按时间窗派生的展示态） */
const MEMBER_ACTIVITY_STATUS_LABEL: Record<string, string> = {
  '1': '草稿',
  '2': '已发布',
  '3': '已暂停',
  '4': '已结束',
  '5': '待开始',
};

const memberActivityOptions = ref<Array<{ id: string; label: string }>>([]);
const memberActivitiesLoading = ref(false);
let memberActivitiesLoaded = false;

/** 时间窗压缩成「MM-DD HH:mm ~ MM-DD HH:mm」，避免下拉标签过长 */
function shortenActivityTime(value: unknown) {
  const text = typeof value === 'string' ? value : '';
  return text.length >= 16 ? text.slice(5, 16) : text;
}

function createMemberActivityLabel(activity: Record<string, unknown>) {
  const name = String(activity.activityName ?? activity.id ?? '未命名活动');
  const status =
    MEMBER_ACTIVITY_STATUS_LABEL[String(activity.status ?? '')] ?? '';
  const window = [
    shortenActivityTime(activity.startTime),
    shortenActivityTime(activity.endTime),
  ]
    .filter(Boolean)
    .join(' ~ ');
  const detail = [status, window].filter(Boolean).join(' · ');
  return detail ? `${name}（${detail}）` : name;
}

/**
 * 会员价活动下拉（type=10）：仅在用到「按会员价活动自动」时按需加载一次。
 *
 * 查询失败/无权限时降级为空列表（只保留「自动」首项），不阻断装修主流程；
 * 失败不置已加载标记，下次切换模式还会重试。
 */
async function loadMemberActivities() {
  if (memberActivitiesLoaded) return;
  memberActivitiesLoaded = true;
  memberActivitiesLoading.value = true;
  try {
    const response = (await getActivityPage({
      activityType: '10',
      current: 1,
      size: 100,
    })) as null | undefined | { records?: Array<Record<string, unknown>> };
    const records = Array.isArray(response?.records) ? response.records : [];
    memberActivityOptions.value = records
      .map((activity) => ({
        createTime: String(activity.createTime ?? ''),
        id: String(activity.id ?? ''),
        label: createMemberActivityLabel(activity),
        priority: Number(activity.priority) || 0,
      }))
      .filter((activity) => Boolean(activity.id))
      .sort(
        (left, right) =>
          right.createTime.localeCompare(left.createTime) ||
          right.priority - left.priority,
      )
      .map(({ id, label }) => ({ id, label }));
  } catch {
    memberActivityOptions.value = [];
    memberActivitiesLoaded = false;
  } finally {
    memberActivitiesLoading.value = false;
  }
}

// 打开已配置「按会员价活动自动」Tab 的存量文档时也把活动列表拉起来（只拉一次）
const hasActivityTab = computed(() =>
  tabList().some((tab) => tabDataSource(tab).mode === 'activity'),
);
watch(
  hasActivityTab,
  (enabled) => {
    if (enabled) loadMemberActivities();
  },
  { immediate: true },
);

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
              @update:model-value="updateTabMode(index, $event as string)"
            >
              <ElRadio value="automatic">按分类/排序自动</ElRadio>
              <ElRadio value="manual">手选商品</ElRadio>
              <ElRadio value="activity">按会员价活动自动</ElRadio>
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
            <template v-else-if="tabDataSource(tab).mode === 'activity'">
              <div class="tab-card__field">
                <span class="tab-card__label">会员价活动</span>
                <ElSelect
                  :loading="memberActivitiesLoading"
                  :model-value="String(tabDataSource(tab).activityId ?? '')"
                  @update:model-value="
                    updateTabDataSource(index, {
                      activityId: String($event ?? ''),
                    })
                  "
                >
                  <ElOption label="自动：取当前进行中的会员价活动" value="" />
                  <ElOption
                    v-for="activity in memberActivityOptions"
                    :key="activity.id"
                    :label="activity.label"
                    :value="activity.id"
                  />
                </ElSelect>
                <span class="tab-editor__hint">
                  专区商品＝活动里配置的商品，顺序随活动；活动加减商品自动同步，无需重新装修。
                </span>
                <span class="tab-editor__hint">
                  建议同时开启「会员权益条」，C 端商品卡才会展示会员价角标。
                </span>
              </div>
            </template>
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
                  <ElOption label="折扣力度优先（低价捡漏）" value="bargain" />
                </ElSelect>
              </div>
              <div
                v-if="tabDataSource(tab).sort === 'bargain'"
                class="tab-card__field"
              >
                <span class="tab-card__label">折扣率阈值</span>
                <ElInputNumber
                  :model-value="
                    Number(
                      tabDataSource(tab).bargainRate ?? DEFAULT_BARGAIN_RATE,
                    )
                  "
                  :min="1.01"
                  :max="9.99"
                  :step="0.01"
                  :precision="2"
                  controls-position="right"
                  @update:model-value="updateTabBargainRate(index, $event)"
                />
                <span class="tab-editor__hint">
                  原价 ≥ 售价 × 阈值 的商品才会进入该 Tab，区间 (1.00,
                  9.99]，缺省 1.15
                </span>
              </div>
            </template>
            <div class="tab-card__field">
              <div class="tab-card__switch-row">
                <ElSwitch
                  :model-value="tabMemberBanner(tab).enabled === true"
                  @update:model-value="
                    updateTabMemberBanner(index, { enabled: $event === true })
                  "
                />
                <span class="tab-card__label">会员权益条（Tab 顶部横幅）</span>
              </div>
              <span class="tab-editor__hint">
                开启后 C
                端在商品流顶部透出会员权益横幅；两段文案留空则使用内置文案。
              </span>
            </div>
            <template v-if="tabMemberBanner(tab).enabled === true">
              <div class="tab-card__field">
                <span class="tab-card__label">会员文案（≤30 字，可留空）</span>
                <ElInput
                  :model-value="
                    (tabMemberBanner(tab).memberCopy as string) ?? ''
                  "
                  maxlength="30"
                  show-word-limit
                  placeholder="内置：您已是 {levelName}，本区享会员专享价"
                  @update:model-value="
                    updateTabMemberBanner(index, { memberCopy: $event })
                  "
                />
                <span class="tab-editor__hint">{{ MEMBER_BANNER_HINT }}</span>
              </div>
              <div class="tab-card__field">
                <span class="tab-card__label">游客文案（≤30 字，可留空）</span>
                <ElInput
                  :model-value="
                    (tabMemberBanner(tab).guestCopy as string) ?? ''
                  "
                  maxlength="30"
                  show-word-limit
                  placeholder="内置：入会享会员价与专属券"
                  @update:model-value="
                    updateTabMemberBanner(index, { guestCopy: $event })
                  "
                />
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
            「低价捡漏」建议用「折扣力度优先」并按阈值圈品；「会员专区」建议用「按会员价活动自动」并开启会员权益条，商品随活动自动同步。
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

.tab-card__switch-row {
  display: flex;
  gap: 8px;
  align-items: center;
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
