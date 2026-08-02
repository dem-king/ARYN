<script lang="ts" setup name="deliveryTrip">
import type { FormInstance } from 'element-plus';

import type { DeliveryTrip, DeliveryTripStatus } from '#/api/delivery/trip';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getDeliveryTripPage } from '#/api/delivery/trip';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const TripDetail = defineAsyncComponent(() => import('./trip-detail.vue'));

/**
 * 出车单状态选项（含 Tag 着色）
 * 1-待配货 2-配货中 3-配送中 4-已完成
 */
const statusOptions: {
  label: string;
  type: 'danger' | 'info' | 'primary' | 'success' | 'warning';
  value: DeliveryTripStatus;
}[] = [
  { value: '1', label: '待配货', type: 'info' },
  { value: '2', label: '配货中', type: 'warning' },
  { value: '3', label: '配送中', type: 'primary' },
  { value: '4', label: '已完成', type: 'success' },
];

const queryRef = ref<FormInstance>();
const tripDetailRef = ref();
const loading = ref(false);
const showSearch = ref(true);

const state = reactive({
  queryParams: {
    tripNo: '',
    staffId: '',
    status: '' as '' | DeliveryTripStatus,
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [] as DeliveryTrip[],
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
    tripNo: state.queryParams.tripNo || undefined,
    staffId: state.queryParams.staffId || undefined,
    status: state.queryParams.status || undefined,
  };
  await getDeliveryTripPage(params)
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
 * 获取状态标签类型
 */
const getStatusType = (status: DeliveryTripStatus) => {
  return statusOptions.find((item) => item.value === status)?.type ?? 'info';
};

/**
 * 获取状态标签文本
 */
const getStatusLabel = (status: DeliveryTripStatus) => {
  return statusOptions.find((item) => item.value === status)?.label ?? '未知';
};

/**
 * 查看出车单详情
 */
const viewDetail = (row: any) => {
  tripDetailRef.value.open(row.id);
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
        <ElFormItem label="出车单号" prop="tripNo">
          <ElInput
            v-model="state.queryParams.tripNo"
            clearable
            placeholder="请输入出车单号"
          />
        </ElFormItem>
        <ElFormItem label="配送员ID" prop="staffId">
          <ElInput
            v-model="state.queryParams.staffId"
            clearable
            placeholder="请输入配送员ID"
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
        <div></div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <TripDetail ref="tripDetailRef" />

      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="tripNo"
          label="出车单号"
          align="center"
          width="180"
        />
        <ElTableColumn
          prop="staffName"
          label="配送员"
          align="center"
          width="120"
        />
        <ElTableColumn prop="status" label="状态" align="center" width="100">
          <template #default="scope">
            <ElTag :type="getStatusType(scope.row.status)">
              {{ getStatusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="orderCount"
          label="订单数"
          align="center"
          width="100"
        />
        <ElTableColumn
          prop="warehouseAddress"
          label="仓库地址"
          align="center"
          min-width="200"
        />
        <ElTableColumn
          prop="loadStartTime"
          label="开始配货时间"
          align="center"
          width="170"
        />
        <ElTableColumn
          prop="departTime"
          label="出发时间"
          align="center"
          width="170"
        />
        <ElTableColumn
          prop="finishTime"
          label="完成时间"
          align="center"
          width="170"
        />
        <ElTableColumn label="操作" align="center" width="100" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'delivery:trip:get'"
              @click="viewDetail(scope.row)"
            >
              详情
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
