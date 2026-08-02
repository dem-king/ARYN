<script lang="ts" setup name="deliveryTask">
import type { FormInstance } from 'element-plus';

import type { DeliveryTask, DeliveryTaskStatus } from '#/api/delivery/task';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getDeliveryStaffList } from '#/api/delivery/staff';
import { assignDeliveryTasks, getDeliveryTaskPage } from '#/api/delivery/task';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const TaskDetail = defineAsyncComponent(() => import('./task-detail.vue'));

/**
 * 配送任务状态选项（含 Tag 着色）
 * 待派单(蓝)、待取货(橙)、配货中(橙)、待送达(蓝)、已送达(绿)、已签收(绿)、已取消(灰)
 */
const statusOptions: {
  label: string;
  type: 'danger' | 'info' | 'primary' | 'success' | 'warning';
  value: DeliveryTaskStatus;
}[] = [
  { value: '1', label: '待派单', type: 'primary' },
  { value: '2', label: '待取货', type: 'warning' },
  { value: '3', label: '配货中', type: 'warning' },
  { value: '4', label: '待送达', type: 'primary' },
  { value: '5', label: '已送达', type: 'success' },
  { value: '6', label: '已签收', type: 'success' },
  { value: '7', label: '已取消', type: 'info' },
];

/** 待派单状态值 */
const PENDING_ASSIGN: DeliveryTaskStatus = '1';

interface DeliveryStaffOption {
  id: string;
  name: string;
  phone?: string;
  status: string;
}

const queryRef = ref<FormInstance>();
const taskDetailRef = ref();
const loading = ref(false);
const showSearch = ref(true);
const assignDialogVisible = ref(false);
const assignLoading = ref(false);
const assignStaffId = ref('');
const staffOptions = ref<DeliveryStaffOption[]>([]);
/** 选中的任务ID列表 */
const selectedTaskIds = ref<string[]>([]);
/** 表格引用 */
const tableRef = ref();

const state = reactive({
  queryParams: {
    taskNo: '',
    orderNo: '',
    recipientName: '',
    recipientPhone: '',
    status: '' as '' | DeliveryTaskStatus,
    staffId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [] as DeliveryTask[],
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
    taskNo: state.queryParams.taskNo || undefined,
    orderNo: state.queryParams.orderNo || undefined,
    recipientName: state.queryParams.recipientName || undefined,
    recipientPhone: state.queryParams.recipientPhone || undefined,
    status: state.queryParams.status || undefined,
    staffId: state.queryParams.staffId || undefined,
  };
  await getDeliveryTaskPage(params)
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
const getStatusType = (status: DeliveryTaskStatus) => {
  return statusOptions.find((item) => item.value === status)?.type ?? 'info';
};

/**
 * 获取状态标签文本
 */
const getStatusLabel = (status: DeliveryTaskStatus) => {
  return statusOptions.find((item) => item.value === status)?.label ?? '未知';
};

/**
 * 查看任务详情
 */
const viewDetail = (row: any) => {
  taskDetailRef.value.open(row.id);
};

/**
 * 表格选择行变化
 * 仅允许选中「待派单」状态的任务
 */
const handleSelectionChange = (selection: DeliveryTask[]) => {
  selectedTaskIds.value = selection.map((item) => item.id);
};

/**
 * 行是否可选：只有「待派单」状态可选
 */
const isSelectable = (row: DeliveryTask) => {
  return row.status === PENDING_ASSIGN;
};

/**
 * 打开批量派单弹窗
 */
const openAssignDialog = () => {
  if (selectedTaskIds.value.length === 0) {
    ElMessage.warning('请先选择待派单的任务');
    return;
  }
  assignStaffId.value = '';
  assignDialogVisible.value = true;
  // 加载在线配送员列表
  getDeliveryStaffList({ status: '1' })
    .then((response: any) => {
      staffOptions.value = response || [];
    })
    .catch(() => {});
};

/**
 * 确认批量派单
 */
const confirmAssign = () => {
  if (!assignStaffId.value) {
    ElMessage.warning('请选择配送员');
    return;
  }
  assignLoading.value = true;
  assignDeliveryTasks({
    taskIds: selectedTaskIds.value,
    staffId: assignStaffId.value,
  })
    .then(() => {
      ElMessage.success(`成功派单 ${selectedTaskIds.value.length} 个任务`);
      assignLoading.value = false;
      assignDialogVisible.value = false;
      selectedTaskIds.value = [];
      // 清空表格选择
      tableRef.value?.clearSelection();
      initPage();
    })
    .catch(() => {
      assignLoading.value = false;
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
        <ElFormItem label="任务编号" prop="taskNo">
          <ElInput
            v-model="state.queryParams.taskNo"
            clearable
            placeholder="请输入任务编号"
          />
        </ElFormItem>
        <ElFormItem label="订单号" prop="orderNo">
          <ElInput
            v-model="state.queryParams.orderNo"
            clearable
            placeholder="请输入订单号"
          />
        </ElFormItem>
        <ElFormItem label="收货人" prop="recipientName">
          <ElInput
            v-model="state.queryParams.recipientName"
            clearable
            placeholder="请输入收货人姓名"
          />
        </ElFormItem>
        <ElFormItem label="收货电话" prop="recipientPhone">
          <ElInput
            v-model="state.queryParams.recipientPhone"
            clearable
            placeholder="请输入收货人电话"
          />
        </ElFormItem>
        <ElFormItem label="任务状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择任务状态"
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
        <ElFormItem label="配送员" prop="staffId">
          <ElInput
            v-model="state.queryParams.staffId"
            clearable
            placeholder="请输入配送员ID"
          />
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
            v-access:code="'delivery:task:assign'"
            :disabled="selectedTaskIds.length === 0"
            @click="openAssignDialog"
          >
            批量派单
            <span v-if="selectedTaskIds.length > 0">
              （已选 {{ selectedTaskIds.length }}）
            </span>
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <TaskDetail ref="taskDetailRef" @init-page="initPage" />

      <!-- 列表 -->
      <ElTable
        ref="tableRef"
        v-loading="loading"
        :data="state.tableData"
        border
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn
          type="selection"
          width="55"
          :selectable="isSelectable"
          align="center"
        />
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
        <ElTableColumn label="收货地址" align="center" min-width="240">
          <template #default="scope">
            <span>
              {{ scope.row.recipientProvince }}
              {{ scope.row.recipientCity }}
              {{ scope.row.recipientArea }}
              {{ scope.row.recipientAddress }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" align="center" width="100">
          <template #default="scope">
            <ElTag :type="getStatusType(scope.row.status)">
              {{ getStatusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="staffName"
          label="配送员"
          align="center"
          width="100"
        />
        <ElTableColumn
          prop="assignTime"
          label="派单时间"
          align="center"
          width="170"
        />
        <ElTableColumn label="操作" align="center" width="100" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'delivery:task:get'"
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

      <!-- 批量派单弹窗 -->
      <ElDialog
        v-model="assignDialogVisible"
        title="批量派单"
        width="420px"
        append-to-body
      >
        <div style="margin-bottom: 12px">
          已选择 <b>{{ selectedTaskIds.length }}</b> 个待派单任务
        </div>
        <ElForm label-width="100px">
          <ElFormItem label="配送员">
            <ElSelect
              v-model="assignStaffId"
              placeholder="请选择配送员"
              filterable
              style="width: 100%"
            >
              <ElOption
                v-for="item in staffOptions"
                :key="item.id"
                :label="`${item.name}（${item.phone ?? ''}）`"
                :value="item.id"
              />
            </ElSelect>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <span class="dialog-footer">
            <ElButton @click="assignDialogVisible = false">取 消</ElButton>
            <ElButton
              type="primary"
              :loading="assignLoading"
              @click="confirmAssign"
            >
              确认派单
            </ElButton>
          </span>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
