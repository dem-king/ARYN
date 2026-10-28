<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage } from '#/api/upms/storage-config';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';

const Form = defineAsyncComponent(() => import('./form.vue'));
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const { sys_storage_type, status } = useDict('sys_storage_type', 'status');
const state = reactive({
  queryParams: {
    type: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getPage(
      Object.assign(
        {
          current: state.page.currentPage,
          size: state.page.pageSize,
          asc: state.page.asc,
          desc: state.page.desc,
        },
        state.queryParams,
      ),
    );
    state.tableData = response.records;
    state.page.total = response.total;
    loading.value = false;
  } catch (error) {
    console.error('获取数据失败:', error);
  } finally {
    loading.value = false;
  }
};
/**
 * 新增按钮
 */
const add = () => {
  formRef.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  formRef.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该存储配置，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};
initPage();

useKeepAliveRefresh(initPage);
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.queryParams"
        :visible="showSearch"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="存储类型" prop="type">
          <ElSelect
            v-model="state.queryParams.type"
            clearable
            placeholder="请选择存储类型"
          >
            <ElOption
              v-for="item in sys_storage_type"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="条存储配置">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'upms:storageconfig:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
          <span class="toolbar-hint">
            当前仅支持启用一种文件存储服务。启用新配置将自动禁用原有存储配置，以确保系统唯一性。
          </span>
        </template>
        <template #right>
          <RightToolbar
            :search-btn="true"
            :refresh-btn="true"
            @search="showSearch = !showSearch"
            @refresh="initPage"
          />
        </template>
      </PageToolbar>
      <Form ref="formRef" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        :data="state.tableData"
        class="hx-data-table"
        border
        empty-text="暂无存储配置"
      >
        <ElTableColumn prop="accessKey" label="accessKey" min-width="180" />
        <ElTableColumn
          prop="accessSecret"
          label="accessSecret"
          min-width="180"
        />
        <ElTableColumn prop="type" label="存储类型" width="100">
          <template #default="scope">
            <DictTag :options="sys_storage_type" :value="scope.row.type" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="100">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="endpoint" label="地域节点" min-width="200" />
        <ElTableColumn
          prop="bucket"
          label="Bucket / 本地根目录"
          min-width="180"
        />
        <ElTableColumn prop="createTime" label="创建时间" min-width="180" />
        <ElTableColumn label="操作" align="center" width="200">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'upms:storageconfig:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'upms:storageconfig:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
<style lang="scss" scoped>
.toolbar-hint {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}
</style>
