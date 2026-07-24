<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage } from '#/api/upms/storage-config';
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
const queryRef = ref();

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
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
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
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="存储类型" prop="type">
          <ElSelect
            style="width: 200px"
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
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'upms:storageconfig:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
          <span style="padding-left: 10px; font-size: 14px">
            当前仅支持启用一种文件存储服务。启用新配置将自动禁用原有存储配置，以确保系统唯一性。
          </span>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <Form ref="formRef" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
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
