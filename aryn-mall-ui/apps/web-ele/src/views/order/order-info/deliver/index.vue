<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { computed, defineAsyncComponent, nextTick, reactive, ref } from 'vue';

import {
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getDeliveryStaffList } from '#/api/delivery/staff';
import {
  deliverAssignOrder,
  deliverOrder,
  getById,
} from '#/api/order/order-info';
import { getList as getLogisticsList } from '#/api/upms/logistics-company';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['handleSuccess']);

const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

interface LogisticsCompany {
  code: string;
  name: string;
}
interface DeliveryStaffOption {
  id: string;
  staffName: string;
  staffPhone: string;
}
interface DataState {
  form: {
    id: string;
    logisticsCompanyCode: string;
    logisticsCompanyName: string;
    logisticsNo: string;
    orderId: string;
  };
  assignForm: {
    staffId: string;
  };
  dialog: boolean;
  orderId: string;
  rules: any;
  assignRules: any;
  logisticsCompanys: LogisticsCompany[];
  staffList: DeliveryStaffOption[];
  staffLoadFailed: boolean;
}
const { order_item_status } = useDict('order_item_status');

/** 配送任务状态文案（与配送任务页保持一致） */
const taskStatusOptions: Record<string, { label: string; type: any }> = {
  '1': { label: '待派单', type: 'primary' },
  '2': { label: '待取货', type: 'warning' },
  '3': { label: '配货中', type: 'warning' },
  '4': { label: '待送达', type: 'primary' },
  '5': { label: '已送达', type: 'success' },
  '6': { label: '已签收', type: 'success' },
  '7': { label: '已取消', type: 'info' },
  '8': { label: '异常', type: 'danger' },
  '9': { label: '待退回', type: 'danger' },
};

const orderInfo = ref();
const state = reactive<DataState>({
  form: {
    id: '',
    orderId: '',
    logisticsCompanyCode: '',
    logisticsCompanyName: '',
    logisticsNo: '',
  },
  assignForm: {
    staffId: '',
  },
  dialog: false,
  orderId: '',
  rules: {
    logisticsCompanyCode: [
      {
        required: true,
        message: '请选择快递公司',
        trigger: 'change',
      },
    ],
    logisticsNo: [
      {
        required: true,
        message: '请输入物流单号',
        trigger: 'change',
      },
    ],
  },
  assignRules: {
    staffId: [
      {
        required: true,
        message: '请选择配送员',
        trigger: 'change',
      },
    ],
  },
  logisticsCompanys: [],
  staffList: [],
  staffLoadFailed: false,
});
const formRef = ref();
const assignFormRef = ref();
const tableRef = ref();
const loading = ref(false);

/** 商城配送（3）/公司港口船舶内部配送（4）：由配送任务驱动，无需快递单号 */
const isTaskDriven = computed(() =>
  ['3', '4'].includes(orderInfo.value?.deliveryWay),
);
const deliveryTask = computed(() => orderInfo.value?.deliveryTask ?? null);
/** 待派单（或历史订单缺任务）时可在发货窗口直接派单 */
const taskAssignable = computed(
  () =>
    isTaskDriven.value &&
    (!deliveryTask.value || deliveryTask.value?.status === '1'),
);
const taskStatusView = computed(() =>
  deliveryTask.value
    ? taskStatusOptions[deliveryTask.value.status] ||
      ({ label: deliveryTask.value.status, type: 'info' } as any)
    : null,
);

const initPage = (orderId: string) => {
  state.dialog = true;
  state.orderId = orderId;
  state.assignForm.staffId = '';
  nextTick(() => {
    formRef.value?.resetFields();
  });
  getOrderInfo(orderId);

  getLogisticsList({ status: '0' }).then((response) => {
    state.logisticsCompanys = response;
  });
};
// 获取订单信息
const getOrderInfo = (id: string) => {
  getById(id)
    .then((response) => {
      orderInfo.value = response;
      if (['3', '4'].includes(response?.deliveryWay)) {
        loadStaffList();
      }
    })
    .catch(() => {
      loading.value = false;
    });
};
// 获取可接单的配送员（在线）列表
const loadStaffList = () => {
  state.staffLoadFailed = false;
  getDeliveryStaffList({ status: '1' })
    .then((list) => {
      state.staffList = list ?? [];
    })
    .catch(() => {
      // 常见于角色缺少 delivery:staff:list 权限，界面提示而不打断发货
      state.staffLoadFailed = true;
    });
};
/**
 * 提交按钮：商城配送/内部配送走派单，快递走发货单
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      const request = isTaskDriven.value
        ? deliverAssignOrder({
            orderId: orderInfo.value.id,
            staffId: state.assignForm.staffId,
          })
        : (() => {
            state.form.orderId = orderInfo.value.id;
            return deliverOrder(state.form);
          })();
      request
        .then(() => {
          ElMessage.success(
            isTaskDriven.value
              ? '派单成功，司机取货出发后订单自动转为待收货'
              : '发货成功',
          );
          loading.value = false;
          state.dialog = false;
          emit('handleSuccess');
        })
        .catch(() => {
          loading.value = false;
        });
    }
  });
};
/**
 * 重置表单并关闭抽屉：商城配送/内部配送单不渲染快递表单，formRef 为空也必须能关闭
 */
const resetForm = () => {
  state.form.id = '';
  state.assignForm.staffId = '';
  loading.value = false;
  state.dialog = false;
};

const handleClose = () => {
  resetForm();
};
const logisticsChange = (val: string) => {
  if (val) {
    const logistics = state.logisticsCompanys.find((item) => {
      return item.code === val;
    });
    state.form.logisticsCompanyName = logistics?.name || '';
  } else {
    state.form.logisticsCompanyName = '';
  }
};
defineExpose({
  initPage,
});
</script>
<template>
  <ElDrawer
    v-model="state.dialog"
    title="发货"
    size="60%"
    :before-close="handleClose"
  >
    <div style="padding: 20px">
      <ElCard class="box-card">
        <template #header>
          <div class="card-header">
            <span>商品信息</span>
          </div>
        </template>
        <ElTable
          ref="tableRef"
          row-key="id"
          v-if="orderInfo"
          :data="orderInfo.orderItemList"
          style="width: 100%"
          border
        >
          <ElTableColumn prop="spuName" label="商品信息" min-width="220">
            <template #default="scope">
              <div class="order-item">
                <ElImage
                  class="pic"
                  :src="scope.row.picUrl"
                  fit="cover"
                  :preview-teleported="true"
                />
                <div class="main">
                  <span class="name line-clamp-2">
                    {{ scope.row.spuName }}
                  </span>
                  <p class="specs">{{ scope.row.specsInfo }}</p>
                </div>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn
            property="buyQuantity"
            label="发货数量"
            width="200"
            align="center"
          />
          <ElTableColumn
            property="status"
            label="状态"
            width="200"
            align="center"
          >
            <template #default="scope">
              <DictTag :options="order_item_status" :value="scope.row.status" />
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>

      <ElCard class="box-card margin-top">
        <template #header>
          <div class="card-header">
            <span>收货人信息</span>
          </div>
        </template>
        <!-- 收货人信息展示 -->
        <div v-if="orderInfo">
          <ElDescriptions :column="1" border>
            <ElDescriptionsItem width="150px" label="收货人">
              {{ orderInfo.recipientName }}
            </ElDescriptionsItem>
            <ElDescriptionsItem width="150px" label="联系电话">
              {{ orderInfo.recipientPhone }}
            </ElDescriptionsItem>
            <ElDescriptionsItem width="150px" label="收货地址">
              {{ orderInfo.recipientProvince }} {{ orderInfo.recipientCity }}
              {{ orderInfo.recipientArea }} {{ orderInfo.recipientAddress }}
            </ElDescriptionsItem>
          </ElDescriptions>
          <!-- 内部配送：收货地址常为空，展示船舶/靠港快照 -->
          <ElDescriptions
            v-if="orderInfo.deliveryWay === '4' && orderInfo.vesselName"
            :column="1"
            border
            class="margin-top"
            title="内部配送信息"
          >
            <ElDescriptionsItem width="150px" label="配送船舶">
              {{ orderInfo.vesselName }}
            </ElDescriptionsItem>
            <ElDescriptionsItem width="150px" label="港口泊位">
              {{ orderInfo.portName }} {{ orderInfo.berth }}
            </ElDescriptionsItem>
            <ElDescriptionsItem
              v-if="orderInfo.deliveryWindowStart"
              width="150px"
              label="配送时间窗"
            >
              {{ orderInfo.deliveryWindowStart }} ~
              {{ orderInfo.deliveryWindowEnd }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </div>
      </ElCard>
      <ElCard class="box-card margin-top">
        <template #header>
          <div class="card-header">
            <span>{{ isTaskDriven ? '配送派单' : '物流信息' }}</span>
          </div>
        </template>
        <!-- 商城配送/内部配送：直接派单给司机，无需快递单号 -->
        <template v-if="isTaskDriven">
          <ElForm
            v-if="taskAssignable"
            ref="assignFormRef"
            :model="state.assignForm"
            :rules="state.assignRules"
          >
            <ElFormItem label="配送员" prop="staffId">
              <ElSelect
                value-key="id"
                v-model="state.assignForm.staffId"
                placeholder="请选择配送员"
                style="width: 100%"
              >
                <ElOption
                  v-for="item in state.staffList"
                  :key="item.id"
                  :label="`${item.staffName}（${item.staffPhone}）`"
                  :value="item.id"
                />
              </ElSelect>
            </ElFormItem>
          </ElForm>
          <p v-if="taskAssignable" class="assign-tip">
            本订单由商城自有配送，无需快递单号。派单后司机取货出发，订单自动转为「待收货」。
          </p>
          <p v-if="state.staffLoadFailed" class="assign-tip warn">
            配送员列表加载失败（可能缺少「配送员列表」权限），请刷新重试或联系管理员开通。
          </p>
          <div v-if="!taskAssignable && deliveryTask">
            <ElDescriptions :column="1" border>
              <ElDescriptionsItem width="150px" label="配送任务">
                {{ deliveryTask.taskNo }}
              </ElDescriptionsItem>
              <ElDescriptionsItem width="150px" label="任务状态">
                <ElTag :type="taskStatusView?.type">
                  {{ taskStatusView?.label }}
                </ElTag>
              </ElDescriptionsItem>
              <ElDescriptionsItem
                v-if="deliveryTask.staffName"
                width="150px"
                label="配送员"
              >
                {{ deliveryTask.staffName }}
              </ElDescriptionsItem>
              <ElDescriptionsItem
                v-if="deliveryTask.assignTime"
                width="150px"
                label="派单时间"
              >
                {{ deliveryTask.assignTime }}
              </ElDescriptionsItem>
            </ElDescriptions>
            <p class="assign-tip">
              任务已在配送流程中，请到「配送管理 → 配送任务」跟进处理。
            </p>
          </div>
        </template>
        <!-- 快递配送：物流信息 -->
        <ElForm v-else ref="formRef" :model="state.form" :rules="state.rules">
          <ElFormItem label="快递公司" prop="logisticsCompanyCode">
            <ElSelect
              value-key="code"
              v-model="state.form.logisticsCompanyCode"
              placeholder="请选择快递公司"
              style="width: 100%"
              @change="logisticsChange"
            >
              <ElOption
                v-for="item in state.logisticsCompanys"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="物流单号" prop="logisticsNo">
            <ElInput v-model="state.form.logisticsNo" />
          </ElFormItem>
        </ElForm>
        <span class="dialog-footer" style="padding: 20px">
          <ElButton @click="state.dialog = false">关闭</ElButton>
          <ElButton
            v-if="!isTaskDriven || taskAssignable"
            type="primary"
            @click="submitForm(isTaskDriven ? assignFormRef : formRef)"
            :loading="loading"
          >
            {{ isTaskDriven ? '派单发货' : '发货' }}
          </ElButton>
        </span>
      </ElCard>
    </div>
  </ElDrawer>
</template>
<style lang="scss" scoped>
/* 图片固定宽、文字区自适应：避免窄抽屉下百分比栅格被 60px 图片压住 */
.order-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;

  .pic {
    flex: none;
    width: 60px;
    height: 60px;
  }

  .main {
    min-width: 0;
  }

  .name {
    overflow-wrap: anywhere;
  }

  .specs {
    margin: 4px 0 0;
    font-size: 12px;
    color: #a8abb2;
    overflow-wrap: anywhere;
  }
}

.margin-top {
  margin-top: 40px;
}

.dialog-footer {
  display: flex;
  justify-content: center;
}

.assign-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #a8abb2;

  &.warn {
    color: #e6a23c;
  }
}
</style>
