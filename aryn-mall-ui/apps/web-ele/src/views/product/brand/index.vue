<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage } from '#/api/product/brand';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));

const { status } = useDict('status');
const state = reactive({
  queryParams: {
    name: '',
    status: '',
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
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const resetQuery = () => {
  queryRef.value.resetFields();
};

const handleAdd = () => {
  formRef.value.initForm();
};

const handleEdit = (row: any) => {
  formRef.value.initForm(row);
};

const handleDelete = (id: string) => {
  ElMessageBox.confirm('此操作将删除该品牌，是否继续?', '提示', {
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
        <ElFormItem label="品牌名称" prop="name">
          <ElInput
            v-model="state.queryParams.name"
            clearable
            placeholder="请输入品牌名称"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            style="width: 200px"
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in status"
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
            v-access:code="'product:brand:add'"
            @click="handleAdd"
            :icon="Plus"
          >
            新增
          </ElButton>
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
        <ElTableColumn prop="name" label="品牌名称" />
        <ElTableColumn prop="logo" label="品牌Logo" width="80">
          <template #default="scope">
            <ElImage
              v-if="scope.row.logo"
              :src="scope.row.logo"
              style="width: 40px; height: 40px"
              fit="contain"
              :preview-src-list="[scope.row.logo]"
              :preview-teleported="true"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="firstLetter" label="首字母" width="80" />
        <ElTableColumn prop="sort" label="排序" width="80" />
        <ElTableColumn prop="status" label="状态" width="80">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="150" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'product:brand:edit'"
              @click="handleEdit(scope.row)"
              :icon="Edit"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'product:brand:del'"
              @click="handleDelete(scope.row.id)"
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
