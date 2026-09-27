<script setup lang="ts">
import type { TemplateMarketItem } from '#/api/promotion/page-design';

import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Refresh, Search } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import {
  downloadTemplateFromMarket,
  getTemplateMarketDetail,
  getTemplateMarketList,
  pageTypeLabel,
} from '#/api/promotion/page-design';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const router = useRouter();
const loading = ref(false);
const list = ref<TemplateMarketItem[]>([]);
const page = reactive({ current: 1, size: 12, total: 0 });
const query = reactive({
  industryTag: '' as string,
  keyword: '',
  sortField: 'downloadCount' as 'createTime' | 'downloadCount',
});

/** 行业标签候选项（第一期静态枚举，后续可由字典接口下发） */
const INDUDRY_OPTIONS = [
  '电商零售',
  '餐饮美食',
  '美业个护',
  '母婴亲子',
  '服装服饰',
];

async function initList() {
  loading.value = true;
  try {
    const res = await getTemplateMarketList({
      industryTag: query.industryTag || undefined,
      keyword: query.keyword || undefined,
      pageNum: page.current,
      pageSize: page.size,
      sortField: query.sortField,
    });
    list.value = res.records ?? [];
    page.total = res.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = '';
  query.industryTag = '';
  page.current = 1;
  void initList();
}

function handleSearch() {
  page.current = 1;
  void initList();
}

/** 预览弹窗：第一期展示模板基本信息，缩略图渲染后续优化 */
const preview = reactive({
  visible: false,
  detail: null as null | TemplateMarketItem,
});

async function handlePreview(item: TemplateMarketItem) {
  try {
    preview.detail = await getTemplateMarketDetail(item.id);
  } catch {
    preview.detail = item;
  }
  preview.visible = true;
}

async function handleDownload(item: TemplateMarketItem) {
  await ElMessageBox.confirm(
    `下载后“${item.templateName}”将复制为你租户下的新模板，是否继续？`,
    '下载模板',
    { confirmButtonText: '下载', cancelButtonText: '取消', type: 'warning' },
  );
  const newTemplateId = await downloadTemplateFromMarket(item.id);
  ElMessage.success('已下载到我的模板');
  // 跳转到微页面（模板）列表
  void router.push('/promotion/page-design');
  return newTemplateId;
}

onMounted(initList);
</script>

<template>
  <div class="hx-layout-container template-market">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true" :model="query" class="filter-bar">
        <ElFormItem label="模板名称">
          <ElInput
            v-model="query.keyword"
            clearable
            placeholder="输入模板名称"
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="行业标签">
          <ElSelect
            v-model="query.industryTag"
            clearable
            placeholder="全部行业"
            style="width: 160px"
          >
            <ElOption
              v-for="tag in INDUDRY_OPTIONS"
              :key="tag"
              :label="tag"
              :value="tag"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="排序">
          <ElSelect v-model="query.sortField" style="width: 140px">
            <ElOption label="按下载量" value="downloadCount" />
            <ElOption label="按最新创建" value="createTime" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton :icon="Search" type="primary" @click="handleSearch">
            查询
          </ElButton>
          <ElButton :icon="Refresh" @click="resetQuery">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <div class="hx-table-toolbar">
        <RightToolbar
          :refresh-btn="true"
          :search-btn="false"
          @refresh="initList"
        />
      </div>

      <div v-loading="loading" class="market-grid">
        <ElEmpty v-if="list.length === 0" description="市场暂无模板" />
        <article v-for="item in list" v-else :key="item.id" class="market-card">
          <div class="card-head">
            <strong class="card-name">{{ item.templateName }}</strong>
            <ElTag effect="plain" size="small" type="warning">
              {{ pageTypeLabel(item.pageType) }}
            </ElTag>
          </div>
          <div class="card-tags">
            <ElTag
              v-if="item.industryTag"
              effect="plain"
              size="small"
              type="info"
            >
              {{ item.industryTag }}
            </ElTag>
            <span class="card-download"
              >下载量 {{ item.downloadCount ?? 0 }}</span
            >
          </div>
          <p v-if="item.description" class="card-desc">
            {{ item.description }}
          </p>
          <p class="card-time">
            {{
              item.createTime
                ? dayjs(item.createTime).format('YYYY-MM-DD')
                : '-'
            }}
          </p>
          <div class="card-actions">
            <ElButton size="small" @click="handlePreview(item)">预览</ElButton>
            <ElButton
              v-access:code="'promotion:pagedesign:template-market:download'"
              size="small"
              type="primary"
              @click="handleDownload(item)"
            >
              下载
            </ElButton>
          </div>
        </article>
      </div>

      <Pagination
        v-model:current="page.current"
        v-model:size="page.size"
        :total="page.total"
        @change="initList"
      />
    </div>

    <ElDialog v-model="preview.visible" title="模板预览" width="480px">
      <div v-if="preview.detail" class="preview-body">
        <h3>{{ preview.detail.templateName }}</h3>
        <p v-if="preview.detail.description">
          {{ preview.detail.description }}
        </p>
        <ul class="preview-list">
          <li>适用页面：{{ pageTypeLabel(preview.detail.pageType) }}</li>
          <li v-if="preview.detail.industryTag">
            行业标签：{{ preview.detail.industryTag }}
          </li>
          <li>下载量：{{ preview.detail.downloadCount ?? 0 }}</li>
          <li v-if="preview.detail.createTime">
            创建时间：{{
              dayjs(preview.detail.createTime).format('YYYY-MM-DD HH:mm')
            }}
          </li>
        </ul>
        <p class="preview-tip">完整装修效果预览将在后续版本提供</p>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
.template-market .filter-bar :deep(.el-select) {
  width: 160px;
}

.market-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  min-height: 240px;
}

.market-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.market-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 12px rgb(0 0 0 / 6%);
}

.card-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.card-name {
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.card-tags {
  display: flex;
  gap: 8px;
  align-items: center;
}

.card-download {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-desc {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-time {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.card-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.preview-body h3 {
  margin: 0 0 8px;
}

.preview-list {
  padding: 0;
  margin: 12px 0;
  list-style: none;
}

.preview-list li {
  padding: 4px 0;
  color: var(--el-text-color-regular);
}

.preview-tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
