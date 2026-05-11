<script lang="ts" setup>
import { defineAsyncComponent, nextTick, reactive, ref } from 'vue';

import {
  Delete,
  Edit,
  Plus,
  Refresh,
  Search,
  View,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage } from '#/api/product/goods-specs';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));
const GoodsSpecsValue = defineAsyncComponent(
  () => import('#/views/product/goods-specs-value/index.vue'),
);
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
const refForm = ref();
const specsValueDrawer = ref(false);
const goodsSpecsValueRef = ref();
const specsId = ref('');
const queryRef = ref();
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
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
  refForm.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  refForm.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该规格，是否继续?', '提示', {
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

const openSpecsValue = (id: string) => {
  specsValueDrawer.value = true;
  specsId.value = id;
  nextTick(() => {
    setTimeout(() => {
      goodsSpecsValueRef.value.initPage();
    }, 100);
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
        <ElFormItem label="规格名" prop="name">
          <ElInput
            v-model="state.queryParams.name"
            clearable
            placeholder="请输入规格名"
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
            v-access:code="'product:goodsspecs:add'"
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
      <Form ref="refForm" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="name" label="规格名" />
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="300" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'product:goodsspecs:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'product:goodsspecs:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'product:goodsspecsvalue:page'"
              @click="openSpecsValue(scope.row.id)"
              :icon="View"
            >
              属性值
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

      <!-- 规格值列表 -->
      <ElDrawer
        v-model="specsValueDrawer"
        title="规格值"
        direction="rtl"
        size="50%"
      >
        <GoodsSpecsValue ref="goodsSpecsValueRef" :specs-id="specsId" />
      </ElDrawer>
    </div>
  </div>
</template>
