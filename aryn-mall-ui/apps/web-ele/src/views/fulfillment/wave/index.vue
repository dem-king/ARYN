<script lang="ts" setup>
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  addOrderToWave,
  createWave,
  getWaveItems,
  getWavePage,
  handOverWave,
  reportShort,
  reviewWave,
  scanPick,
} from '#/api/fulfillment/wave';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { portCode: '', status: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const waveDialogVisible = ref(false);
const waveForm = reactive<any>({
  berth: '',
  planDeliveryTime: '',
  portCode: '',
  portName: '',
  vesselCallId: '',
});
const orderIdInput = ref('');

const itemsDialogVisible = ref(false);
const itemsWave = ref<any>({});
const itemTableData = ref<any[]>([]);

const scanForm = reactive<any>({ itemId: '', quantity: 1, scannedCode: '' });
const shortForm = reactive<any>({
  actualQuantity: 0,
  itemId: '',
  reasonCode: 'OUT_OF_STOCK',
  reasonDesc: '',
});

const statusLabel: Record<string, string> = {
  '1': '待拣货',
  '2': '拣货中',
  '3': '已复核',
  '4': '已交司机',
  '5': '已完成',
  '6': '已取消',
};

const pickStatusLabel: Record<string, string> = {
  '1': '待拣',
  '2': '已拣',
  '3': '短装',
  '4': '替代',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getWavePage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      portCode: state.query.portCode,
      status: state.query.status,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const saveWave = () => {
  if (!waveForm.portCode) {
    ElMessage.warning('港口编码必填');
    return;
  }
  createWave(waveForm)
    .then(() => {
      ElMessage.success('波次已创建');
      waveDialogVisible.value = false;
      initPage();
    })
    .catch(() => {});
};

const openItems = (row: any) => {
  itemsWave.value = row;
  itemsDialogVisible.value = true;
  getWaveItems(row.id).then((list) => {
    itemTableData.value = list;
  });
};

const handleAddOrder = () => {
  if (!orderIdInput.value) {
    ElMessage.warning('请输入订单ID');
    return;
  }
  addOrderToWave(itemsWave.value.id, orderIdInput.value)
    .then(() => {
      ElMessage.success('订单已加入波次');
      orderIdInput.value = '';
      return getWaveItems(itemsWave.value.id);
    })
    .then((list) => {
      itemTableData.value = list;
    })
    .catch(() => {});
};

const handleScan = () => {
  scanPick({
    itemId: scanForm.itemId,
    quantity: scanForm.quantity,
    scannedCode: scanForm.scannedCode,
    waveId: itemsWave.value.id,
  })
    .then(() => getWaveItems(itemsWave.value.id))
    .then((list) => {
      ElMessage.success('扫码确认成功');
      itemTableData.value = list;
    })
    .catch(() => {});
};

const handleShort = () => {
  reportShort({
    actualQuantity: shortForm.actualQuantity,
    itemId: shortForm.itemId,
    reasonCode: shortForm.reasonCode,
    reasonDesc: shortForm.reasonDesc,
    waveId: itemsWave.value.id,
  })
    .then(() => getWaveItems(itemsWave.value.id))
    .then((list) => {
      ElMessage.success('短装已记录');
      itemTableData.value = list;
    })
    .catch(() => {});
};

const handleReview = (row: any) => {
  reviewWave(row.id)
    .then(() => {
      ElMessage.success('复核通过');
      initPage();
    })
    .catch(() => {});
};

const handleHandOver = (row: any) => {
  handOverWave(row.id)
    .then(() => {
      ElMessage.success('已交接司机');
      initPage();
    })
    .catch(() => {});
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true" :model="state.query">
        <ElFormItem label="港口">
          <ElInput
            v-model="state.query.portCode"
            clearable
            placeholder="港口编码"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="state.query.status" clearable style="width: 130px">
            <ElOption
              v-for="(label, value) in statusLabel"
              :key="value"
              :label="label"
              :value="value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage">搜索</ElButton>
          <ElButton
            type="success"
            v-access:code="'fulfillment:wave:save'"
            @click="waveDialogVisible = true"
          >
            新建波次
          </ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="waveNo" label="波次编号" width="180" />
        <ElTableColumn prop="portName" label="港口" width="130" />
        <ElTableColumn prop="planDeliveryTime" label="计划交付" width="170" />
        <ElTableColumn label="状态" width="100">
          <template #default="scope">
            {{ statusLabel[scope.row.status] ?? scope.row.status }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="operatorName" label="操作员" width="110" />
        <ElTableColumn label="操作" width="240">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'fulfillment:wave:page'"
              @click="openItems(scope.row)"
            >
              明细
            </ElButton>
            <ElButton
              link
              type="success"
              v-access:code="'fulfillment:wave:review'"
              :disabled="scope.row.status !== '2'"
              @click="handleReview(scope.row)"
            >
              复核
            </ElButton>
            <ElButton
              link
              type="warning"
              v-access:code="'fulfillment:wave:hand-over'"
              :disabled="scope.row.status !== '3'"
              @click="handleHandOver(scope.row)"
            >
              交接司机
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

      <!-- 新建波次 -->
      <ElDialog v-model="waveDialogVisible" title="新建拣货波次" width="520px">
        <ElForm label-width="110px">
          <ElFormItem label="港口编码" required>
            <ElInput v-model="waveForm.portCode" placeholder="CNSHA" />
          </ElFormItem>
          <ElFormItem label="港口名称">
            <ElInput v-model="waveForm.portName" />
          </ElFormItem>
          <ElFormItem label="靠港计划ID">
            <ElInput v-model="waveForm.vesselCallId" />
          </ElFormItem>
          <ElFormItem label="计划交付时间">
            <ElInput
              v-model="waveForm.planDeliveryTime"
              placeholder="2026-09-15 10:00:00"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="waveDialogVisible = false">取消</ElButton>
          <ElButton type="primary" @click="saveWave">创建</ElButton>
        </template>
      </ElDialog>

      <!-- 波次明细 -->
      <ElDialog
        v-model="itemsDialogVisible"
        :title="`波次明细：${itemsWave.waveNo ?? ''}`"
        width="900px"
      >
        <div class="mb10">
          <ElInput
            v-model="orderIdInput"
            placeholder="订单ID"
            style="width: 220px; margin-right: 8px"
          />
          <ElButton type="primary" @click="handleAddOrder">加入订单</ElButton>
        </div>
        <ElTable :data="itemTableData" border class="mb10">
          <ElTableColumn prop="spuName" label="商品" min-width="160" />
          <ElTableColumn prop="skuName" label="规格" min-width="120" />
          <ElTableColumn
            prop="requiredQuantity"
            label="应拣"
            width="80"
            align="center"
          />
          <ElTableColumn
            prop="pickedQuantity"
            label="实拣"
            width="80"
            align="center"
          />
          <ElTableColumn
            prop="shortQuantity"
            label="短装"
            width="80"
            align="center"
          />
          <ElTableColumn label="状态" width="90">
            <template #default="scope">
              {{
                pickStatusLabel[scope.row.pickStatus] ?? scope.row.pickStatus
              }}
            </template>
          </ElTableColumn>
        </ElTable>
        <ElForm :inline="true" class="mb10">
          <ElFormItem label="明细ID">
            <ElInput
              v-model="scanForm.itemId"
              style="width: 200px"
              @focus="
                () => {
                  shortForm.itemId = scanForm.itemId;
                }
              "
            />
          </ElFormItem>
          <ElFormItem label="条码/SKU">
            <ElInput v-model="scanForm.scannedCode" style="width: 160px" />
          </ElFormItem>
          <ElFormItem label="数量">
            <ElInputNumber
              v-model="scanForm.quantity"
              :min="1"
              controls-position="right"
            />
          </ElFormItem>
          <ElFormItem>
            <ElButton
              type="primary"
              v-access:code="'fulfillment:wave:pick'"
              @click="handleScan"
            >
              扫码确认
            </ElButton>
          </ElFormItem>
        </ElForm>
        <ElForm :inline="true">
          <ElFormItem label="明细ID">
            <ElInput v-model="shortForm.itemId" style="width: 200px" />
          </ElFormItem>
          <ElFormItem label="实际数量">
            <ElInputNumber
              v-model="shortForm.actualQuantity"
              :min="0"
              controls-position="right"
            />
          </ElFormItem>
          <ElFormItem label="原因">
            <ElSelect v-model="shortForm.reasonCode" style="width: 140px">
              <ElOption label="缺货" value="OUT_OF_STOCK" />
              <ElOption label="破损" value="DAMAGED" />
              <ElOption label="错发" value="WRONG_DELIVERY" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="说明">
            <ElInput v-model="shortForm.reasonDesc" style="width: 160px" />
          </ElFormItem>
          <ElFormItem>
            <ElButton
              type="warning"
              v-access:code="'fulfillment:wave:pick'"
              @click="handleShort"
            >
              记录短装
            </ElButton>
          </ElFormItem>
        </ElForm>
      </ElDialog>
    </div>
  </div>
</template>
