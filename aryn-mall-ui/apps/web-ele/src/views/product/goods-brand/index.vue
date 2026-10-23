<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/product/goods-brand';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';

const Form = defineAsyncComponent(() => import('./form.vue'));
const formRef = ref();
const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const query = reactive({ current: 1, size: 10, name: '', status: '' });
const showSearch = ref(true);

async function load() {
  loading.value = true;
  try {
    const response = await getPage(query);
    rows.value = response.records;
    total.value = response.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  Object.assign(query, { current: 1, name: '', status: '' });
  load();
}

async function remove(id: string) {
  await ElMessageBox.confirm('确认删除该品牌吗？', '提示', { type: 'warning' });
  await delObj(id);
  ElMessage.success('删除成功');
  load();
}

load();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="query"
        :visible="showSearch"
        @reset="resetQuery"
        @search="load"
      >
        <ElFormItem label="品牌名称" prop="name">
          <ElInput
            v-model="query.name"
            clearable
            placeholder="请输入品牌名称"
            @keyup.enter="load"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect v-model="query.status" clearable placeholder="请选择状态">
            <ElOption label="启用" value="0" />
            <ElOption label="停用" value="1" />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="total" total-label="条品牌">
        <template #left>
          <ElButton
            v-access:code="'product:goodsbrand:add'"
            type="primary"
            :icon="Plus"
            @click="formRef.open()"
          >
            新增
          </ElButton>
        </template>
      </PageToolbar>
      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="rows"
        border
        empty-text="暂无品牌"
      >
        <ElTableColumn prop="name" label="品牌名称" min-width="160" />
        <ElTableColumn prop="logoUrl" label="Logo" width="100" align="center">
          <template #default="scope">
            <ElImage
              v-if="scope.row.logoUrl"
              :src="scope.row.logoUrl"
              fit="contain"
              style="width: 48px; height: 48px"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="description"
          label="描述"
          min-width="220"
          show-overflow-tooltip
        />
        <ElTableColumn prop="status" label="状态" width="90" align="center">
          <template #default="scope">
            <ElTag :type="scope.row.status === '0' ? 'success' : 'info'">
              {{ scope.row.status === '0' ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="sort" label="排序" width="90" align="center" />
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="170" fixed="right" align="center">
          <template #default="scope">
            <ElButton
              v-access:code="'product:goodsbrand:edit'"
              link
              type="primary"
              :icon="Edit"
              @click="formRef.open(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              v-access:code="'product:goodsbrand:del'"
              link
              type="danger"
              :icon="Delete"
              @click="remove(scope.row.id)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElPagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-4 justify-end"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @change="load"
      />
      <Form ref="formRef" @refresh="load" />
    </div>
  </div>
</template>
