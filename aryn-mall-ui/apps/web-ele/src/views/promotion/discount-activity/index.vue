<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type {
  DiscountActivityQuery,
  DiscountActivityRecord,
} from '#/api/promotion/discount';

import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

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

import { delObj, getPage, updateStatus } from '#/api/promotion/discount';

import {
  activityStatusOptions,
  getDiscountTypeLabel,
  getScopeLabel,
  getStatusLabel,
  getStatusTagType,
} from './data';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const router = useRouter();
const queryRef = ref<FormInstance>();

const state = reactive<{
  page: {
    currentPage: number;
    desc: string;
    pageSize: number;
    total: number;
  };
  queryParams: DiscountActivityQuery;
  tableData: DiscountActivityRecord[];
}>({
  queryParams: {
    activityName: '',
    status: '' as number | string,
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

/** 跳转到新增页 */
const add = () => {
  navigateToEdit();
};

/** 跳转到编辑页 */
const edit = (row: DiscountActivityRecord | Record<string, any>) => {
  navigateToEdit(row.id as string);
};

/** 路由跳转到编辑页 */
const navigateToEdit = (id?: string) => {
  if (id) {
    router.push(`/promotion/discount-activity/edit?id=${id}`);
  } else {
    router.push('/promotion/discount-activity/edit');
  }
};

/** 删除活动 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该折扣活动，是否继续?', '提示', {
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

/** 切换活动状态（启停） */
const toggleStatus = (row: DiscountActivityRecord | Record<string, any>) => {
  const rowStatus = row.status as number;
  const nextStatus = rowStatus === 3 ? 0 : 3;
  const actionText = nextStatus === 3 ? '暂停' : '启用';
  ElMessageBox.confirm(`确认${actionText}该折扣活动?`, '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    updateStatus(row.id as string, nextStatus)
      .then(() => {
        ElMessage.success(`${actionText}成功`);
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
        <ElFormItem label="活动名称" prop="activityName">
          <ElInput
            v-model="state.queryParams.activityName"
            clearable
            style="width: 200px"
            placeholder="请输入活动名称"
          />
        </ElFormItem>
        <ElFormItem label="活动状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
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
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            @click="add"
            v-access:code="'promotion:discount:add'"
            :icon="Plus"
          >
            新增活动
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
        <ElTableColumn label="活动时间" align="center" width="320">
          <template #default="scope">
            {{ scope.row.startTime }}<br />
            至 {{ scope.row.endTime }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="discountType"
          label="折扣类型"
          align="center"
          width="100"
        >
          <template #default="scope">
            {{ getDiscountTypeLabel(scope.row.discountType) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="discountValue"
          label="折扣值"
          align="center"
          width="100"
        />
        <ElTableColumn prop="scope" label="适用范围" align="center" width="110">
          <template #default="scope">
            <ElTag :type="scope.row.scope === 1 ? 'success' : 'warning'">
              {{ getScopeLabel(scope.row.scope) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" align="center" width="100">
          <template #default="scope">
            <ElTag :type="getStatusTagType(scope.row.status)">
              {{ getStatusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="createTime"
          label="创建时间"
          align="center"
          width="180"
        />
        <ElTableColumn label="操作" width="260" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              @click="edit(scope.row)"
              v-access:code="'promotion:discount:edit'"
              :icon="Edit"
            >
              编辑
            </ElButton>
            <ElButton
              link
              :type="scope.row.status === 3 ? 'success' : 'warning'"
              @click="toggleStatus(scope.row)"
              v-access:code="'promotion:discount:status'"
            >
              {{ scope.row.status === 3 ? '启用' : '暂停' }}
            </ElButton>
            <ElButton
              link
              type="danger"
              @click="del(scope.row.id)"
              v-access:code="'promotion:discount:del'"
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
