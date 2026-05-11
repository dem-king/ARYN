<script lang="ts" setup>
import {
  defineAsyncComponent,
  defineEmits,
  defineExpose,
  nextTick,
  reactive,
  ref,
} from 'vue';

import {
  ElButton,
  ElCard,
  ElCol,
  ElDialog,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElRow,
} from 'element-plus';

import { getById, refundObj } from '#/api/order/order-refund';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

const { refund_status } = useDict('refund_status');
const state = reactive({
  rules: {
    refuseReason: [
      {
        required: true,
        message: '请输入拒绝退款原因',
        trigger: 'blur',
      },
    ],
    operateStatus: [
      {
        required: true,
        message: '请选择退款操作',
        trigger: 'blur',
      },
    ],
    returnAddressId: [
      {
        required: true,
        message: '请选择退回地址',
        trigger: 'blur',
      },
    ],
  },
  refundInfo: {
    id: undefined,
    status: '',
    refuseReason: '',
    arrivalStatus: '',
    userReceivedAccount: '',
    refundId: '',
    refundAmount: 0,
    statusDesc: '',
    refundReason: '',
    refundType: '',
  },
  orderItem: {
    picUrl: '',
    spuName: '',
    totalPrice: 0,
    buyQuantity: 0,
    status: '',
    paymentPrice: 0,
  },
  dialog: false,
  loading: false,
  form: {
    id: undefined,
    operateStatus: '',
    refuseReason: '',
    returnLogisticsPhone: '',
    returnAddressId: '',
    returnAddress: '',
  },
});
const formRef = ref();

const initPage = (id: string) => {
  nextTick(() => {
    formRef.value.resetFields();
  });
  getDetail(id);
  state.dialog = true;
};
const getDetail = (id: string) => {
  getById(id).then((response) => {
    state.refundInfo = response;
    state.form.refuseReason = state.refundInfo.refuseReason;
    state.orderItem = response.orderItem;
  });
};
const doSubmit = async () => {
  state.loading = true;
  state.form.id = state.refundInfo.id;
  try {
    await refundObj(state.form);
    ElMessage.success('操作成功');
    state.loading = false;
    state.dialog = false;
    emit('initPage');
  } catch {
    state.loading = false;
  }
};

// 确认退款
const confirmRefund = async () => {
  state.loading = true;
  state.form.id = state.refundInfo.id;
  try {
    await refundObj({ id: state.refundInfo.id, operateStatus: '4' });
    ElMessage.success('操作成功');
    state.loading = false;
    state.dialog = false;
    emit('initPage');
  } catch {
    state.loading = false;
  }
};

defineExpose({
  initPage,
});
</script>
<template>
  <ElDialog
    v-model="state.dialog"
    width="30%"
    :title="state.refundInfo.refundType === '1' ? '操作退款' : '操作退货退款'"
  >
    <ElCard class="box-card">
      <template #header>
        <div class="card-header">
          <span>退款商品</span>
        </div>
      </template>
      <ElRow v-if="state.orderItem">
        <ElCol :span="6">
          <ElImage
            style="width: 80px; height: 80px"
            :src="state.orderItem.picUrl"
            fit="cover"
            :preview-teleported="true"
          />
        </ElCol>
        <ElCol :span="18">
          <div class="overflow-line-clamp-2 name">
            {{ state.orderItem.spuName }}
          </div>
          <p></p>
          <div style="display: flex; justify-content: space-between">
            <span style="color: red">￥{{ state.orderItem.paymentPrice }}</span>
            x{{ state.orderItem.buyQuantity }}
          </div>
        </ElCol>
      </ElRow>
    </ElCard>
    <ElCard class="box-card">
      <ElForm
        ref="formRef"
        :model="state.form"
        :rules="state.rules"
        label-width="130px"
      >
        <ElFormItem label="退款金额：">
          <span style="color: red">￥{{ state.refundInfo.refundAmount }}</span>
        </ElFormItem>
        <ElFormItem
          label="退货数量："
          v-if="state.refundInfo.refundType === '2'"
        >
          <span>{{ state.orderItem.buyQuantity }}</span>
        </ElFormItem>
        <ElFormItem label="退款原因：">
          {{ state.refundInfo.refundReason }}
        </ElFormItem>
        <!-- 只有退款中和退货退款中状态 可以进行退款操作 -->
        <ElFormItem
          label="退款操作："
          prop="operateStatus"
          v-if="state.refundInfo.status === '1'"
        >
          <ElRadioGroup v-model="state.form.operateStatus">
            <ElRadio value="4">
              {{
                state.refundInfo.refundType === '2'
                  ? '同意退货退款'
                  : '同意退款'
              }}
            </ElRadio>
            <ElRadio value="2">拒绝</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <div v-else>
          <ElFormItem label="状态：">
            <DictTag
              :options="refund_status"
              :value="state.refundInfo.status"
            />
          </ElFormItem>
          <ElFormItem
            label="退款入账账户："
            v-if="state.refundInfo.userReceivedAccount"
          >
            <span>{{ state.refundInfo.userReceivedAccount }}</span>
          </ElFormItem>
          <ElFormItem label="退款流水号：" v-if="state.refundInfo.refundId">
            <span>{{ state.refundInfo.refundId }}</span>
          </ElFormItem>
        </div>
        <ElFormItem
          label="拒绝原因："
          prop="refuseReason"
          v-if="state.form.operateStatus === '2'"
        >
          <ElInput
            v-model="state.form.refuseReason"
            :rows="2"
            type="textarea"
            placeholder="请输入拒绝原因"
          />
        </ElFormItem>
      </ElForm>
    </ElCard>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="state.dialog = false">关闭</ElButton>
        <ElButton
          type="primary"
          @click="doSubmit"
          :disabled="state.loading"
          v-if="state.refundInfo.status === '1'"
          >确认
        </ElButton>
        <ElButton
          type="primary"
          @click="confirmRefund"
          :disabled="state.loading"
          v-if="state.refundInfo.status === '4'"
          >确认退款
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
