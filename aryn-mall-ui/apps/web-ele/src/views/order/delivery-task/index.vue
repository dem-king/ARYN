<script setup lang="ts">
import type { DeliveryTask } from '#/api/order/delivery-task';

import { computed, defineAsyncComponent, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Refresh, Search, UserFilled, View } from '@element-plus/icons-vue';
import {
  ElButton,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElSegmented,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getDeliveryTaskPage } from '#/api/order/delivery-task';

import AssignDialog from './components/AssignDialog.vue';

const router = useRouter();
const route = useRoute();
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const loading = ref(false);
const assignVisible = ref(false);
const assignMode = ref<'ASSIGN' | 'REASSIGN'>('ASSIGN');
const selectedTask = ref<DeliveryTask>();
const activeStatus = ref('');
const state = reactive({
  page: { current: 1, size: 10, total: 0 },
  query: { assigneeId: '', orderNo: '', taskNo: '' },
  records: [] as DeliveryTask[],
});
const statusOptions = [
  { label: '全部', value: '' },
  { label: '待派单', value: 'WAITING_ASSIGNMENT' },
  { label: '待配货', value: 'ASSIGNED' },
  { label: '配货中', value: 'PICKING' },
  { label: '配送中', value: 'DELIVERING' },
  { label: '已送达', value: 'DELIVERED' },
  { label: '异常/退回', value: 'EXCEPTION' },
];
const emptyText = computed(() =>
  activeStatus.value || Object.values(state.query).some(Boolean)
    ? '没有符合筛选条件的配送任务'
    : '支付成功的商城配送订单会出现在这里',
);
const statusMeta: Record<
  string,
  { label: string; type: 'danger' | 'info' | 'primary' | 'success' | 'warning' }
> = {
  ASSIGNED: { label: '待配货', type: 'warning' },
  CLOSED: { label: '已关闭', type: 'info' },
  DELIVERED: { label: '已送达', type: 'success' },
  DELIVERING: { label: '配送中', type: 'primary' },
  EXCEPTION: { label: '异常', type: 'danger' },
  PICKING: { label: '配货中', type: 'warning' },
  RETURN_PENDING: { label: '待退回', type: 'danger' },
  WAITING_ASSIGNMENT: { label: '待派单', type: 'info' },
};

const load = async () => {
  loading.value = true;
  try {
    const response = await getDeliveryTaskPage({
      current: state.page.current,
      size: state.page.size,
      status: activeStatus.value,
      ...state.query,
    });
    state.records = response.records || [];
    state.page.total = response.total || 0;
    const openAssignOrderId = String(route.query.openAssign || '');
    const target = state.records.find(
      (item) => item.orderId === openAssignOrderId,
    );
    if (target?.status === 'WAITING_ASSIGNMENT') {
      openAssignment(target, 'ASSIGN');
      const { openAssign: _openAssign, ...query } = route.query;
      await router.replace({ query });
    }
  } finally {
    loading.value = false;
  }
};
const reset = () => {
  Object.assign(state.query, { assigneeId: '', orderNo: '', taskNo: '' });
  activeStatus.value = '';
  state.page.current = 1;
  load();
};
const openAssignment = (
  task: DeliveryTask | Record<string, any>,
  mode: 'ASSIGN' | 'REASSIGN',
) => {
  selectedTask.value = task as DeliveryTask;
  assignMode.value = mode;
  assignVisible.value = true;
};
const openDetail = (task: DeliveryTask | Record<string, any>) =>
  router.push({ path: '/order/delivery-task/detail', query: { id: task.id } });

state.query.orderNo = String(route.query.orderNo || '');
load();
</script>

<template>
  <div class="hx-layout-container">
    <div
      class="hx-layout-container-auto hx-layout-container-view delivery-page"
    >
      <div class="page-heading">
        <div>
          <h2>商城配送任务</h2>
          <p>
            按履约状态追踪派单、配货、送达与退回，状态变化后刷新即可获得最新版本。
          </p>
        </div>
        <ElButton :icon="Refresh" @click="load">刷新</ElButton>
      </div>

      <ElForm :inline="true" :model="state.query" class="filter-bar">
        <ElFormItem label="任务号"
          ><ElInput v-model="state.query.taskNo" clearable placeholder="任务号"
        /></ElFormItem>
        <ElFormItem label="订单号"
          ><ElInput
            v-model="state.query.orderNo"
            clearable
            placeholder="订单号"
        /></ElFormItem>
        <ElFormItem label="配送员ID"
          ><ElInput
            v-model="state.query.assigneeId"
            clearable
            placeholder="配送员ID"
        /></ElFormItem>
        <ElFormItem>
          <ElButton type="primary" :icon="Search" @click="load">查询</ElButton>
          <ElButton @click="reset">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <div class="status-strip">
        <ElSegmented
          v-model="activeStatus"
          :options="statusOptions"
          @change="load"
        />
      </div>

      <ElTable v-loading="loading" :data="state.records" row-key="id">
        <template #empty><ElEmpty :description="emptyText" /></template>
        <ElTableColumn label="任务 / 订单" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.taskNo }}</strong>
            <div class="muted">订单 {{ row.orderNo }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="120">
          <template #default="{ row }">
            <ElTag :type="statusMeta[row.status]?.type">{{
              statusMeta[row.status]?.label || row.status
            }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="配送员" min-width="180">
          <template #default="{ row }">
            <span v-if="row.assigneeName">{{ row.assigneeName }}</span>
            <span v-else class="muted">尚未派单</span>
            <div v-if="row.assigneeMobile" class="muted">
              {{ row.assigneeMobile }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="attemptNo"
          label="配送尝试"
          width="100"
          align="center"
        />
        <ElTableColumn prop="updateTime" label="最近更新" min-width="180" />
        <ElTableColumn label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :icon="View"
              v-access:code="'order:delivery:get'"
              @click="openDetail(row)"
              >详情</ElButton
            >
            <ElButton
              v-if="row.status === 'WAITING_ASSIGNMENT'"
              link
              type="primary"
              :icon="UserFilled"
              v-access:code="'order:delivery:assign'"
              @click="openAssignment(row, 'ASSIGN')"
              >派单</ElButton
            >
            <ElButton
              v-else-if="
                ['ASSIGNED', 'PICKING', 'EXCEPTION'].includes(row.status)
              "
              link
              type="warning"
              v-access:code="'order:delivery:reassign'"
              @click="openAssignment(row, 'REASSIGN')"
              >改派</ElButton
            >
          </template>
        </ElTableColumn>
      </ElTable>

      <Pagination
        v-model:current="state.page.current"
        v-model:size="state.page.size"
        :total="state.page.total"
        @change="load"
      />
      <AssignDialog
        v-model:visible="assignVisible"
        :mode="assignMode"
        :task="selectedTask"
        @success="load"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.delivery-page {
  gap: 18px;
}
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.page-heading h2 {
  margin: 0;
  font-size: 20px;
}
.page-heading p,
.muted {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.filter-bar {
  padding: 16px 16px 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.status-strip {
  overflow-x: auto;
  padding-bottom: 2px;
}
</style>
