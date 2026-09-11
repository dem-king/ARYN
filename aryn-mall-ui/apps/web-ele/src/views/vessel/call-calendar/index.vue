<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import { ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getCallCalendar } from '#/api/promotion/ship-activity';

const state = reactive({
  endDate: '',
  startDate: '',
  tableData: [] as any[],
});
const loading = ref(false);

const statusTag: Record<string, string> = {
  '1': 'info',
  '2': 'success',
  '3': 'warning',
};
const statusLabel: Record<string, string> = {
  '1': '计划中',
  '2': '靠泊中',
  '3': '已完成',
};

function defaultStart() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01 00:00:00`;
}

function defaultEnd() {
  const now = new Date();
  const end = new Date(now.getFullYear(), now.getMonth() + 2, 0);
  return `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')} 23:59:59`;
}

const initPage = async () => {
  loading.value = true;
  try {
    state.tableData = await getCallCalendar(state.startDate, state.endDate);
  } finally {
    loading.value = false;
  }
};

state.startDate = defaultStart();
state.endDate = defaultEnd();
onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true">
        <ElFormItem label="开始">
          <ElInput
            v-model="state.startDate"
            placeholder="yyyy-MM-dd HH:mm:ss"
          />
        </ElFormItem>
        <ElFormItem label="结束">
          <ElInput v-model="state.endDate" placeholder="yyyy-MM-dd HH:mm:ss" />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage">查询</ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="eta" label="到港 ETA" width="170">
          <template #default="scope">
            <span>{{ (scope.row.eta || '').replace('T', ' ') }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="vesselName" label="船舶" min-width="130" />
        <ElTableColumn prop="portName" label="港口" width="120" />
        <ElTableColumn prop="berth" label="泊位" width="100" />
        <ElTableColumn label="时间窗" min-width="230">
          <template #default="scope">
            {{ (scope.row.deliveryWindowStart || '').replace('T', ' ') }} ~
            {{ (scope.row.deliveryWindowEnd || '').replace('T', ' ') }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            <ElTag
              :type="(statusTag[scope.row.status] ?? 'info') as any"
              size="small"
            >
              {{ statusLabel[scope.row.status] ?? scope.row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>
