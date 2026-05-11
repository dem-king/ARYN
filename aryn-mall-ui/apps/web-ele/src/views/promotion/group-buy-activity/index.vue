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

import { delObj, getPage } from '#/api/promotion/group-buy-activity';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));

const queryRef = ref<FormInstance>();

const state = reactive({
  queryParams: {
    activityName: '',
    activityStatus: '',
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

const activityStatusOptions = [
  { label: '草稿', value: '0' },
  { label: '进行中', value: '1' },
  { label: '已结束', value: '2' },
];

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
  ElMessageBox.confirm('此操作将删除该拼团活动，是否继续?', '提示', {
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

const statusTagType = (status: string) => {
  if (status === '0') return 'info';
  if (status === '1') return 'success';
  if (status === '2') return 'danger';
  return 'info';
};

const statusLabel = (status: string) => {
  const found = activityStatusOptions.find((item) => item.value === status);
  return found ? found.label : status;
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
        <ElFormItem label="活动名称" prop="activityName">
          <ElInput
            v-model="state.queryParams.activityName"
            clearable
            style="width: 200px"
            placeholder="请输入活动名称"
          />
        </ElFormItem>
        <ElFormItem label="活动状态" prop="activityStatus">
          <ElSelect
            v-model="state.queryParams.activityStatus"
            clearable
            style="width: 200px"
            placeholder="请选择活动状态"
          >
            <ElOption
              v-for="item in activityStatusOptions"
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
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            @click="add"
            v-access:code="'promotion:groupbuy:add'"
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
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="activityName"
          label="活动名称"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="originalPrice"
          label="商品原价"
          align="center"
          width="100"
        />
        <ElTableColumn
          prop="groupPrice"
          label="拼团价"
          align="center"
          width="100"
        />
        <ElTableColumn
          prop="groupNum"
          label="成团人数"
          align="center"
          width="90"
        />
        <ElTableColumn
          prop="limitNum"
          label="限购数量"
          align="center"
          width="90"
        >
          <template #default="scope">
            {{ scope.row.limitNum > 0 ? scope.row.limitNum : '不限' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="活动时间" align="center" width="180">
          <template #default="scope">
            {{ scope.row.startedAt }}<br />
            {{ scope.row.endedAt }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="groupExpireHours"
          label="拼团时效(h)"
          align="center"
          width="110"
        />
        <ElTableColumn
          prop="activityStatus"
          label="活动状态"
          align="center"
          width="90"
        >
          <template #default="scope">
            <ElTag :type="statusTagType(scope.row.activityStatus)">
              {{ statusLabel(scope.row.activityStatus) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              @click="edit(scope.row)"
              v-access:code="'promotion:groupbuy:edit'"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              @click="del(scope.row.id)"
              v-access:code="'promotion:groupbuy:del'"
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
