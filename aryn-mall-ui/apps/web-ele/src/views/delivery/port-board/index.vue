<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import {
  ElCard,
  ElCol,
  ElFormItem,
  ElInput,
  ElRow,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPortBoard } from '#/api/promotion/ship-activity';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

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

/** 波次状态着色：待处理灰、进行中橙、复核/交付蓝、完成绿、取消灰 */
const statusTag: Record<string, string> = {
  '1': 'info',
  '2': 'warning',
  '3': 'primary',
  '4': 'primary',
  '5': 'success',
  '6': 'info',
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

useKeepAliveRefresh(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel :model="state" @reset="initPage" @search="initPage">
        <ElFormItem label="港口" prop="portCode">
          <ElInput
            v-model="state.portCode"
            placeholder="CNSHA"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="日期" prop="date">
          <ElInput
            v-model="state.date"
            placeholder="yyyy-MM-dd"
            @keyup.enter="initPage"
          />
        </ElFormItem>
      </PageFilterPanel>

      <ElRow :gutter="16" class="port-board-stats">
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div class="port-board-stat__label">波次总数</div>
            <div class="port-board-stat__value">
              {{ state.board.waveCount ?? 0 }}
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div class="port-board-stat__label">待拣/拣货中</div>
            <div class="port-board-stat__value">
              {{ state.board.pendingWaves ?? 0 }}
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div class="port-board-stat__label">已交司机</div>
            <div class="port-board-stat__value">
              {{ state.board.handedOverWaves ?? 0 }}
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div class="port-board-stat__label">短装明细</div>
            <div class="port-board-stat__value">
              {{ state.board.shortItemCount ?? 0 }}
            </div>
          </ElCard>
        </ElCol>
      </ElRow>

      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.board.waves ?? []"
        row-key="waveNo"
        border
        empty-text="暂无波次"
      >
        <ElTableColumn prop="waveNo" label="波次编号" width="190" />
        <ElTableColumn prop="portName" label="港口" width="120" />
        <ElTableColumn label="计划交付" width="180">
          <template #default="scope">
            {{ (scope.row.planDeliveryTime || '').replace('T', ' ') }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110" align="center">
          <template #default="scope">
            <ElTag :type="(statusTag[scope.row.status] as any) ?? 'info'">
              {{ statusLabel[scope.row.status] ?? scope.row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="operatorName" label="操作员" width="110" />
      </ElTable>
    </div>
  </div>
</template>

<style scoped lang="scss">
.port-board-stats {
  margin-bottom: 16px;
}

.port-board-stat__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.port-board-stat__value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}
</style>
