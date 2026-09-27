<script setup lang="ts">
import type { DecorationDocument } from '../schema/types';

import type {
  PageDesignTemplateRecord,
  PageDesignType,
  TemplateMarketStatus,
} from '#/api/promotion/page-design';

import { computed, ref, watch } from 'vue';

import {
  CircleClose,
  Delete,
  Plus,
  Promotion,
  Star,
  StarFilled,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import {
  createTemplate,
  deleteTemplate,
  getTemplates,
  offlineTemplateFromMarket,
  publishTemplateToMarket,
} from '#/api/promotion/page-design';

import { allLegacyComponentsV2 } from '../fixtures/all-components-v2';
import { migratePageContent } from '../schema/migrate';
import { cloneTemplateDocument } from '../utils/template-utils';

const props = defineProps<{
  currentDocument: DecorationDocument;
  modelValue: boolean;
  pageType: PageDesignType;
}>();
const emit = defineEmits<{
  apply: [document: DecorationDocument];
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const templates = ref<PageDesignTemplateRecord[]>([]);
const industryKeyword = ref('');
const favoritesOnly = ref(false);

/** 模板收藏保存在本地（按模板 ID），跨端同步属后续能力 */
const FAVORITE_KEY = 'decoration:template-favorites';
const favoriteIds = ref<string[]>([]);

function loadFavorites() {
  try {
    favoriteIds.value = JSON.parse(
      localStorage.getItem(FAVORITE_KEY) ?? '[]',
    ) as string[];
  } catch {
    favoriteIds.value = [];
  }
}

function isFavorite(id: string) {
  return favoriteIds.value.includes(id);
}

function toggleFavorite(id: string) {
  favoriteIds.value = isFavorite(id)
    ? favoriteIds.value.filter((item) => item !== id)
    : [...favoriteIds.value, id];
  localStorage.setItem(FAVORITE_KEY, JSON.stringify(favoriteIds.value));
}

const visibleTemplates = computed(() =>
  templates.value.filter((template) => {
    if (favoritesOnly.value && !isFavorite(template.id)) return false;
    if (!industryKeyword.value.trim()) return true;
    return (template.industryTag ?? '').includes(industryKeyword.value.trim());
  }),
);

const systemTemplate: PageDesignTemplateRecord = {
  id: 'system-all-components',
  pageType: '2',
  schemaVersion: allLegacyComponentsV2.schemaVersion,
  systemFlag: '1',
  templateContent: allLegacyComponentsV2 as unknown as Record<string, unknown>,
  templateName: '全组件基础模板',
  templateType: '0',
};

/** 市场状态展示文案与标签类型 */
const MARKET_STATUS_META: Record<
  TemplateMarketStatus,
  { label: string; type: 'danger' | 'info' | 'success' }
> = {
  '0': { label: '未上架', type: 'info' },
  '1': { label: '已上架', type: 'success' },
  '2': { label: '已下架', type: 'danger' },
};

function marketMeta(template: PageDesignTemplateRecord) {
  return MARKET_STATUS_META[template.marketStatus ?? '0'];
}

async function load() {
  if (!props.modelValue) return;
  loadFavorites();
  loading.value = true;
  try {
    templates.value = [systemTemplate, ...(await getTemplates(props.pageType))];
  } finally {
    loading.value = false;
  }
}

function applyTemplate(template: PageDesignTemplateRecord) {
  emit(
    'apply',
    cloneTemplateDocument(migratePageContent(template.templateContent)),
  );
  emit('update:modelValue', false);
}

async function saveCurrent() {
  const { value } = await ElMessageBox.prompt(
    '输入模板名称',
    '保存为租户模板',
    {
      confirmButtonText: '保存',
      inputPattern: /\S+/,
      inputValue: '新模板',
    },
  );
  await createTemplate({
    pageType: props.pageType,
    schemaVersion: props.currentDocument.schemaVersion,
    systemFlag: '0',
    templateContent: props.currentDocument as unknown as Record<
      string,
      unknown
    >,
    templateName: value,
    templateType: '0',
  });
  ElMessage.success('模板已保存');
  await load();
}

async function remove(template: PageDesignTemplateRecord) {
  await ElMessageBox.confirm(
    `删除模板“${template.templateName}”？`,
    '删除模板',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    },
  );
  await deleteTemplate(template.id);
  await load();
}

async function handlePublishMarket(template: PageDesignTemplateRecord) {
  await ElMessageBox.confirm(
    `发布后“${template.templateName}”将出现在模板市场，供其他租户下载使用，是否继续？`,
    '发布到市场',
    { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' },
  );
  await publishTemplateToMarket(template.id);
  ElMessage.success('已发布到模板市场');
  await load();
}

async function handleOfflineMarket(template: PageDesignTemplateRecord) {
  await ElMessageBox.confirm(
    `下架后其他租户将无法在模板市场看到“${template.templateName}”，是否继续？`,
    '从市场下架',
    { confirmButtonText: '下架', cancelButtonText: '取消', type: 'warning' },
  );
  await offlineTemplateFromMarket(template.id);
  ElMessage.success('已从模板市场下架');
  await load();
}

watch(() => props.modelValue, load);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="页面模板"
    width="760px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="template-actions">
      <div class="template-filters">
        <ElInput
          v-model="industryKeyword"
          clearable
          placeholder="按行业标签筛选"
          size="small"
          style="width: 160px"
        />
        <ElCheckbox v-model="favoritesOnly" size="small">只看收藏</ElCheckbox>
      </div>
      <ElButton
        v-access:code="'promotion:pagedesign:template'"
        :icon="Plus"
        type="primary"
        @click="saveCurrent"
      >
        保存当前页面为模板
      </ElButton>
    </div>
    <div v-loading="loading" class="template-grid">
      <ElEmpty v-if="visibleTemplates.length === 0" description="暂无模板" />
      <article
        v-for="template in visibleTemplates"
        v-else
        :key="template.id"
        class="template-item"
      >
        <div>
          <strong>{{ template.templateName }}</strong>
          <ElTag v-if="template.systemFlag === '1'" effect="plain" size="small">
            系统
          </ElTag>
          <ElTag
            v-if="template.industryTag"
            effect="plain"
            size="small"
            type="warning"
          >
            {{ template.industryTag }}
          </ElTag>
          <ElButton
            :icon="isFavorite(template.id) ? StarFilled : Star"
            aria-label="收藏模板"
            circle
            size="small"
            text
            @click="toggleFavorite(template.id)"
          />
        </div>
        <div class="template-meta">
          <span>
            {{
              migratePageContent(template.templateContent).sections.flatMap(
                (section) => section.components,
              ).length
            }}
            个组件
          </span>
          <ElTag
            v-if="template.systemFlag !== '1'"
            effect="plain"
            size="small"
            :type="marketMeta(template).type"
          >
            {{ marketMeta(template).label }}
          </ElTag>
          <span v-if="template.systemFlag !== '1'" class="download-count">
            下载 {{ template.downloadCount ?? 0 }}
          </span>
        </div>
        <div class="template-item-actions">
          <ElButton type="primary" @click="applyTemplate(template)">
            使用
          </ElButton>
          <div class="template-item-side">
            <ElButton
              v-if="
                template.systemFlag !== '1' && template.marketStatus !== '1'
              "
              v-access:code="'promotion:pagedesign:template'"
              :icon="Promotion"
              size="small"
              type="success"
              @click="handlePublishMarket(template)"
            >
              发布到市场
            </ElButton>
            <ElButton
              v-if="
                template.systemFlag !== '1' && template.marketStatus === '1'
              "
              v-access:code="'promotion:pagedesign:template'"
              :icon="CircleClose"
              size="small"
              type="warning"
              @click="handleOfflineMarket(template)"
            >
              下架
            </ElButton>
            <ElButton
              v-if="template.systemFlag !== '1'"
              v-access:code="'promotion:pagedesign:template'"
              :icon="Delete"
              aria-label="删除模板"
              circle
              text
              type="danger"
              @click="remove(template)"
            />
          </div>
        </div>
      </article>
    </div>
  </ElDialog>
</template>

<style scoped>
.template-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.template-filters {
  display: flex;
  gap: 12px;
  align-items: center;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-height: 180px;
}

.template-item {
  display: grid;
  gap: 10px;
  min-height: 140px;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.template-item > div:first-child {
  display: flex;
  gap: 8px;
  align-items: center;
}

.template-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.download-count {
  margin-left: auto;
}

.template-item-actions {
  display: flex;
  align-items: center;
  align-self: end;
  justify-content: space-between;
}

.template-item-side {
  display: flex;
  gap: 4px;
  align-items: center;
}
</style>
