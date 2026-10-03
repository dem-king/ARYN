<script setup lang="ts">
import type { PageDesignTemplateRecord } from '#/api/promotion/page-design';

import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import {
  addObj,
  getTemplates,
  pageTypeLabel,
} from '#/api/promotion/page-design';

import { migratePageContent } from '../../page-designer/schema/migrate';
import {
  mergeTenantTemplates,
  targetPageTypeFor,
} from './template-create-utils';

/**
 * 「从模板新建」弹窗：微页面列表的模板入口。
 *
 * 模板市场下载的副本落在 page_design_template（我的模板），而模板此前唯一的
 * 使用入口藏在设计器的「页面模板」弹窗里——必须先有一个页面才能进设计器，
 * 下载完的用户在微页面列表完全找不到出口。本弹窗把「选模板 → 建页 → 进设计器」
 * 收敛成一步，补上这条断链。
 */
const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{
  created: [pageId: string];
  'update:modelValue': [value: boolean];
}>();

const router = useRouter();
const loading = ref(false);
const creating = ref(false);
const templates = ref<PageDesignTemplateRecord[]>([]);
const keyword = ref('');

/** 模板列表接口按页型过滤（page_type='2' OR 指定值），拉全量需按页型各查一次后去重 */
async function load() {
  if (!props.modelValue) return;
  loading.value = true;
  try {
    const lists = await Promise.all([
      getTemplates('0'),
      getTemplates('1'),
      getTemplates('3'),
      getTemplates('4'),
    ]);
    templates.value = mergeTenantTemplates(lists);
  } finally {
    loading.value = false;
  }
}

watch(() => props.modelValue, load);

const visibleTemplates = () => {
  const kw = keyword.value.trim();
  if (!kw) return templates.value;
  return templates.value.filter(
    (template) =>
      (template.templateName ?? '').includes(kw) ||
      (template.industryTag ?? '').includes(kw),
  );
};

function componentCount(template: PageDesignTemplateRecord) {
  try {
    return migratePageContent(template.templateContent).sections.flatMap(
      (section) => section.components,
    ).length;
  } catch {
    return 0;
  }
}

function confirmText(template: PageDesignTemplateRecord) {
  const target = targetPageTypeFor(template.pageType);
  if (template.pageType === '1') {
    return `首页模板将以「微页面」形式新建（发布后可通过「设为首页」切换），页面名“${template.templateName}”，是否继续？`;
  }
  return `将按模板“${template.templateName}”新建${pageTypeLabel(target)}页面并进入编辑，是否继续？`;
}

async function handleUse(template: PageDesignTemplateRecord) {
  await ElMessageBox.confirm(confirmText(template), '从模板新建', {
    confirmButtonText: '新建并编辑',
    cancelButtonText: '取消',
    type: 'info',
  });
  creating.value = true;
  try {
    // 与设计器「页面模板 → 使用」同口径：先迁移成 v3 文档再落草稿
    const document = migratePageContent(template.templateContent);
    const pageId = await addObj({
      homeStatus: '0',
      pageContent: JSON.stringify(document),
      pageName: template.templateName,
      pageType: targetPageTypeFor(template.pageType),
      schemaVersion: 3,
      status: '0',
    });
    ElMessage.success(`已按模板创建页面“${template.templateName}”`);
    emit('update:modelValue', false);
    emit('created', pageId);
  } finally {
    creating.value = false;
  }
}

function gotoMarket() {
  emit('update:modelValue', false);
  void router.push('/promotion/template-market');
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="从模板新建页面"
    width="760px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="create-filter">
      <ElInput
        v-model="keyword"
        clearable
        placeholder="按模板名称或行业标签筛选"
        size="small"
        style="width: 220px"
      />
      <span class="create-hint">
        模板市场下载的模板会出现在这里；想用更多模板可先到
        <ElLink type="primary" @click="gotoMarket">模板市场</ElLink>
        下载
      </span>
    </div>
    <div v-loading="loading" class="create-grid">
      <ElEmpty
        v-if="visibleTemplates().length === 0"
        :description="loading ? '加载中…' : '暂无可用模板，可先到模板市场下载'"
      />
      <article
        v-for="template in visibleTemplates()"
        v-else
        :key="template.id"
        class="create-card"
      >
        <div class="card-head">
          <strong class="card-name">{{ template.templateName }}</strong>
          <ElTag effect="plain" size="small" type="warning">
            {{ pageTypeLabel(template.pageType) }}
          </ElTag>
          <ElTag v-if="template.systemFlag === '1'" effect="plain" size="small">
            系统
          </ElTag>
        </div>
        <div class="card-tags">
          <ElTag
            v-if="template.industryTag"
            effect="plain"
            size="small"
            type="info"
          >
            {{ template.industryTag }}
          </ElTag>
          <span class="card-meta">{{ componentCount(template) }} 个组件</span>
        </div>
        <div class="card-actions">
          <ElButton
            :loading="creating"
            size="small"
            type="primary"
            @click="handleUse(template)"
          >
            使用
          </ElButton>
        </div>
      </article>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.create-filter {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.create-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  max-height: 420px;
  overflow-y: auto;
}

.create-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.card-head {
  display: flex;
  gap: 6px;
  align-items: center;
}

.card-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.card-tags {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}

.card-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
