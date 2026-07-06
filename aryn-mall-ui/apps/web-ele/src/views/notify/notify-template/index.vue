<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/notify/notify-template';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));

// 消息类型选项
const notifyTypeOptions = [
  { value: 1, label: '订单消息' },
  { value: 2, label: '支付消息' },
  { value: 3, label: '物流消息' },
  { value: 4, label: '营销消息' },
  { value: 5, label: '系统消息' },
  { value: 6, label: '社交消息' },
];

const statusOptions = [
  { value: '1', label: '启用' },
  { value: '0', label: '禁用' },
];

const queryRef = ref<FormInstance>();
const state = reactive({
  queryParams: {
    templateCode: '',
    templateName: '',
    notifyType: '' as number | string,
    status: '',
  },
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

const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};

const add = () => {
  formRef.value.initForm(null);
};

const edit = (row: any) => {
  formRef.value.initForm(row);
};

const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该消息模板，是否继续?', '提示', {
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

const notifyTypeText = (type: number) => {
  return notifyTypeOptions.find((t) => t.value === type)?.label || '-';
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="模板编码" prop="templateCode">
          <ElInput
            v-model="state.queryParams.templateCode"
            clearable
            style="width: 200px"
            placeholder="请输入模板编码"
          />
        </ElFormItem>
        <ElFormItem label="模板名称" prop="templateName">
          <ElInput
            v-model="state.queryParams.templateName"
            clearable
            style="width: 200px"
            placeholder="请输入模板名称"
          />
        </ElFormItem>
        <ElFormItem label="消息类型" prop="notifyType">
          <ElSelect
            v-model="state.queryParams.notifyType"
            clearable
            style="width: 200px"
            placeholder="请选择消息类型"
          >
            <ElOption
              v-for="item in notifyTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            style="width: 200px"
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in statusOptions"
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
          <ElButton @click="resetQuery(queryRef)" :icon="Refresh">
            重置
          </ElButton>
        </ElFormItem>
      </ElForm>
      <Form ref="formRef" @init-page="initPage" />
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            @click="add"
            v-access:code="'notify:template:add'"
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
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="templateCode"
          label="模板编码"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="templateName"
          label="模板名称"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="notifyType" label="消息类型" align="center">
          <template #default="scope">
            {{ notifyTypeText(scope.row.notifyType) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="title"
          label="标题"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="status" label="状态" align="center">
          <template #default="scope">
            <ElTag v-if="scope.row.status === '1'" type="success">启用</ElTag>
            <ElTag v-else type="info">禁用</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              @click="edit(scope.row)"
              v-access:code="'notify:template:edit'"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              @click="del(scope.row.id)"
              v-access:code="'notify:template:del'"
              :icon="Delete"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
