<script lang="ts" setup name="deliveryStaff">
import type { FormInstance } from 'element-plus';

import type { DeliveryStaff, DeliveryStaffStatus } from '#/api/delivery/staff';

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

import {
  deleteDeliveryStaff,
  getDeliveryStaffPage,
  updateDeliveryStaffStatus,
} from '#/api/delivery/staff';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const StaffForm = defineAsyncComponent(() => import('./staff-form.vue'));

/** 配送员状态选项 */
const statusOptions: {
  label: string;
  type: 'danger' | 'info' | 'success' | 'warning';
  value: DeliveryStaffStatus;
}[] = [
  { value: '1', label: '在线', type: 'success' },
  { value: '2', label: '忙碌', type: 'warning' },
  { value: '3', label: '离线', type: 'info' },
];

const queryRef = ref<FormInstance>();
const staffFormRef = ref();
const loading = ref(false);
const showSearch = ref(true);

const state = reactive({
  queryParams: {
    keyword: '',
    status: '' as '' | DeliveryStaffStatus,
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [] as DeliveryStaff[],
});

/**
 * 查询列表
 */
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
    keyword: state.queryParams.keyword || undefined,
    status: state.queryParams.status || undefined,
  };
  await getDeliveryStaffPage(params)
    .then((response: any) => {
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
const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};

/**
 * 新增配送员
 */
const add = () => {
  staffFormRef.value.initForm();
};

/**
 * 编辑配送员
 */
const edit = (row: any) => {
  staffFormRef.value.initForm(row);
};

/**
 * 删除配送员
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该配送员，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    deleteDeliveryStaff(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

/**
 * 切换配送员状态
 */
const toggleStatus = (row: any) => {
  const next: DeliveryStaffStatus = row.status === '1' ? '3' : '1';
  const label = statusOptions.find((item) => item.value === next)?.label;
  ElMessageBox.confirm(`确认将配送员状态切换为「${label}」?`, '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    updateDeliveryStaffStatus(row.id, next)
      .then(() => {
        ElMessage.success('状态切换成功');
        initPage();
      })
      .catch(() => {});
  });
};

/**
 * 获取状态标签类型
 */
const getStatusType = (status: DeliveryStaffStatus) => {
  return statusOptions.find((item) => item.value === status)?.type ?? 'info';
};

/**
 * 获取状态标签文本
 */
const getStatusLabel = (status: DeliveryStaffStatus) => {
  return statusOptions.find((item) => item.value === status)?.label ?? '未知';
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
        <ElFormItem label="姓名/手机号" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="请输入姓名或手机号"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择状态"
            style="width: 200px"
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
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'delivery:staff:add'"
            @click="add"
            :icon="Plus"
          >
            新增配送员
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <StaffForm ref="staffFormRef" @init-page="initPage" />

      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="staffName" label="姓名" align="center" width="120" />
        <ElTableColumn prop="staffPhone" label="手机号" align="center" width="160" />
        <ElTableColumn prop="status" label="状态" align="center" width="100">
          <template #default="scope">
            <ElTag :type="getStatusType(scope.row.status)">
              {{ getStatusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="vehicleInfo"
          label="车辆信息"
          align="center"
          min-width="180"
        />
        <ElTableColumn
          prop="createTime"
          label="创建时间"
          align="center"
          width="180"
        />
        <ElTableColumn label="操作" align="center" width="240" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'delivery:staff:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="warning"
              v-access:code="'delivery:staff:status'"
              @click="toggleStatus(scope.row)"
            >
              切换状态
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'delivery:staff:del'"
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
