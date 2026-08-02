<script lang="ts" setup>
import type { DeliveryTripStatus } from '#/api/delivery/trip';

import { ref } from 'vue';

import {
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getDeliveryTripDetail } from '#/api/delivery/trip';

/**
 * 出车单状态选项（含 Tag 着色）
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

interface TripDetail {
  id: string;
  tripNo: string;
  staffId: string;
  staffName: string;
  status: DeliveryTripStatus;
  taskCount: number;
  warehouseAddress?: string;
  startLoadTime?: string;
  departTime?: string;
  completeTime?: string;
  createTime?: string;
  /** 关联的配送任务列表 */
  taskList?: any[];
  /** 取货清单汇总 */
  pickupSummary?: {
    picUrl?: string;
    quantity: number;
    specsInfo?: string;
    spuName: string;
  }[];
}

const drawerVisible = ref(false);
const loading = ref(false);
const detail = ref<null | TripDetail>(null);

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
 * 打开详情抽屉
 */
const open = (id: string) => {
  drawerVisible.value = true;
  loading.value = true;
  getDeliveryTripDetail(id)
    .then((response: any) => {
      detail.value = response;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

defineExpose({
  open,
});
</script>
<template>
  <ElDrawer v-model="drawerVisible" title="出车单详情" size="60%">
    <div v-if="detail" v-loading="loading" style="padding: 0 8px">
      <!-- 基本信息 -->
      <ElCard class="box-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <ElTag :type="getStatusType(detail.status)">
              {{ getStatusLabel(detail.status) }}
            </ElTag>
          </div>
        </template>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="出车单号">
            {{ detail.tripNo }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="配送员">
            {{ detail.staffName }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="任务数">
            {{ detail.taskCount }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="仓库地址">
            {{ detail.warehouseAddress ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="开始配货时间">
            {{ detail.startLoadTime ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="出发时间">
            {{ detail.departTime ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="完成时间">
            {{ detail.completeTime ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="创建时间">
            {{ detail.createTime ?? '—' }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElCard>

      <!-- 关联配送任务 -->
      <ElCard class="box-card margin-top">
        <template #header>
          <div class="card-header">
            <span>关联配送任务</span>
          </div>
        </template>
        <ElTable
          :data="detail.taskList ?? []"
          border
          row-key="id"
          style="width: 100%"
        >
          <ElTableColumn
            prop="taskNo"
            label="任务编号"
            align="center"
            width="180"
          />
          <ElTableColumn
            prop="orderNo"
            label="订单号"
            align="center"
            width="180"
          />
          <ElTableColumn
            prop="recipientName"
            label="收货人"
            align="center"
            width="100"
          />
          <ElTableColumn
            prop="recipientPhone"
            label="收货电话"
            align="center"
            width="140"
          />
          <ElTableColumn label="收货地址" align="center" min-width="220">
            <template #default="scope">
              <span>
                {{ scope.row.recipientProvince }}
                {{ scope.row.recipientCity }}
                {{ scope.row.recipientArea }}
                {{ scope.row.recipientAddress }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="status"
            label="任务状态"
            align="center"
            width="100"
          />
        </ElTable>
      </ElCard>

      <!-- 取货清单汇总 -->
      <ElCard class="box-card margin-top">
        <template #header>
          <div class="card-header">
            <span>取货清单汇总</span>
          </div>
        </template>
        <ElTable
          :data="detail.pickupSummary ?? []"
          border
          row-key="spuName"
          style="width: 100%"
        >
          <ElTableColumn prop="spuName" label="商品名称" min-width="200" />
          <ElTableColumn prop="specsInfo" label="规格信息" min-width="160" />
          <ElTableColumn
            prop="quantity"
            label="取货数量"
            align="center"
            width="120"
          />
        </ElTable>
      </ElCard>
    </div>
  </ElDrawer>
</template>
<style lang="scss" scoped>
.margin-top {
  margin-top: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
