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

import { delObj, getPage } from '#/api/upms/logistics-company';
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
// 字典
const { status } = useDict('status');
const state = reactive({
  queryParams: { name: '', code: '' },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
  rules: {
    type: [
      {
        required: true,
        message: 'Please select Activity zone',
        trigger: 'change',
      },
    ],
  },
  loading: false,
});
const queryRef = ref();
const showSearch = ref(true);
const formRef = ref();
const initPage = async () => {
  state.loading = true;
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
      state.loading = false;
    })
    .catch(() => {
      state.loading = false;
    });
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
  ElMessageBox.confirm('此操作将删除该物流公司，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id).then(() => {
      ElMessage.success('删除成功');
      initPage();
    });
  });
};
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
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
        <ElFormItem label="物流名称" prop="name">
          <ElInput
            v-model="state.queryParams.name"
            clearable
            placeholder="请输入物流名称"
          />
        </ElFormItem>
        <ElFormItem label="物流编码" prop="code">
          <ElInput
            v-model="state.queryParams.code"
            clearable
            placeholder="请输入物流编码"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <Form ref="formRef" @init-page="initPage" />
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            v-access:code="'upms:logisticscompany:add'"
            type="primary"
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
      <ElTable :data="state.tableData" v-loading="state.loading" border>
        <ElTableColumn prop="name" label="物流名称" />
        <ElTableColumn prop="code" label="物流编码" />
        <ElTableColumn prop="status" label="状态">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="200" align="center">
          <template #default="scope">
            <ElButton
              v-access:code="'upms:logisticscompany:edit'"
              link
              type="primary"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              v-access:code="'upms:logisticscompany:del'"
              link
              type="danger"
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
<style lang="scss" scoped></style>
