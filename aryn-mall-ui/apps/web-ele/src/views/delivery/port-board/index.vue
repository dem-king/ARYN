<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElCard,
  ElCol,
  ElInput,
  ElRow,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPortBoard } from '#/api/promotion/ship-activity';

const state = reactive({
  date: '',
  portCode: 'CNSHA',
  board: {} as any,
});
const loading = ref(false);

const statusLabel: Record<string, string> = {
  '1': '待拣货',
  '2': '拣货中',
  '3': '已复核',
  '4': '已交司机',
  '5': '已完成',
  '6': '已取消',
};

const initPage = async () => {
  loading.value = true;
  try {
    state.board = await getPortBoard(state.portCode, state.date || undefined);
  } finally {
    loading.value = false;
  }
};

state.date = new Date().toISOString().slice(0, 10);
onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true">
        <ElFormItem label="港口">
          <ElInput v-model="state.portCode" placeholder="CNSHA" />
        </ElFormItem>
        <ElFormItem label="日期">
          <ElInput v-model="state.date" placeholder="yyyy-MM-dd" />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage">刷新</ElButton>
        </ElFormItem>
      </ElForm>

      <ElRow :gutter="16" class="mb10">
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div>波次总数</div>
            <div class="text-24px font-bold">
              {{ state.board.waveCount ?? 0 }}
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div>待拣/拣货中</div>
            <div class="text-24px font-bold">
              {{ state.board.pendingWaves ?? 0 }}
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div>已交司机</div>
            <div class="text-24px font-bold">
              {{ state.board.handedOverWaves ?? 0 }}
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div>短装明细</div>
            <div class="text-24px font-bold">
              {{ state.board.shortItemCount ?? 0 }}
            </div>
          </ElCard>
        </ElCol>
      </ElRow>

      <ElTable v-loading="loading" :data="state.board.waves ?? []" border>
        <ElTableColumn prop="waveNo" label="波次编号" width="190" />
        <ElTableColumn prop="portName" label="港口" width="120" />
        <ElTableColumn label="计划交付" width="180">
          <template #default="scope">
            {{ (scope.row.planDeliveryTime || '').replace('T', ' ') }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100">
          <template #default="scope">
            {{ statusLabel[scope.row.status] ?? scope.row.status }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="operatorName" label="操作员" width="110" />
      </ElTable>
    </div>
  </div>
</template>
