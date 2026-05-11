<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage } from '#/api/product/goods-specs-value';

const props = defineProps({
  specsId: {
    type: String,
    default: '',
  },
});

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));

const state = reactive({
  queryParams: { name: '' },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();
const queryRef = ref();
const initPage = async () => {
  if (props.specsId) {
    loading.value = true;
    const params = {
      current: state.page.currentPage,
      size: state.page.pageSize,
      specsId: props.specsId,
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
  }
};
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
};
/**
 * 新增按钮
 */
const add = () => {
  formRef.value.initForm(null, props.specsId);
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  formRef.value.initForm(row, props.specsId);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该属性值，是否继续?', '提示', {
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
defineExpose({
  initPage,
});
</script>
<template>
  <div class="layout-padding-auto layout-padding-view">
    <!-- 搜索 -->
    <ElForm
      :model="state.queryParams"
      ref="queryRef"
      :inline="true"
      v-show="showSearch"
    >
      <ElFormItem label="属性值" prop="name">
        <ElInput
          v-model="state.queryParams.name"
          clearable
          placeholder="请输入属性值"
        />
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
          v-access:code="'product:goodsspecsvalue:add'"
          @click="add"
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
      <ElTableColumn prop="name" label="规格值" />
      <ElTableColumn prop="createTime" label="创建时间" />
      <ElTableColumn label="操作" width="300" align="center">
        <template #default="scope">
          <ElButton
            link
            type="primary"
            v-access:code="'product:goodsspecsvalue:edit'"
            @click="edit(scope.row)"
            :icon="Edit"
          >
            修改
          </ElButton>
          <ElButton
            link
            type="danger"
            v-access:code="'product:goodsspecsvalue:del'"
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
</template>
