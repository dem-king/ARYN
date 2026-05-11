<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, nextTick, reactive, ref } from 'vue';

import {
  ElButton,
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElOption,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { deliverOrder, getById } from '#/api/order/order-info';
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
interface DataState {
  form: {
    id: string;
    logisticsCompanyCode: string;
    logisticsCompanyName: string;
    logisticsNo: string;
    orderId: string;
  };
  dialog: boolean;
  orderId: string;
  rules: any;
  logisticsCompanys: LogisticsCompany[];
}
const { order_item_status } = useDict('order_item_status');

const orderInfo = ref();
const state = reactive<DataState>({
  form: {
    id: '',
    orderId: '',
    logisticsCompanyCode: '',
    logisticsCompanyName: '',
    logisticsNo: '',
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
  logisticsCompanys: [],
});
const formRef = ref();
const tableRef = ref();
const loading = ref(false);
const initPage = (orderId: string) => {
  state.dialog = true;
  state.orderId = orderId;
  nextTick(() => {
    formRef.value.resetFields();
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
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 提交按钮
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      state.form.orderId = orderInfo.value.id;
      loading.value = true;
      deliverOrder(state.form)
        .then(() => {
          ElMessage.success('发货成功');
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
 * 重置表单
 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = '';
  loading.value = false;
  state.dialog = false;
};

const handleClose = () => {
  resetForm(formRef.value);
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
          <ElTableColumn prop="spuName" label="商品信息">
            <template #default="scope">
              <ElRow>
                <ElCol :span="4">
                  <ElImage
                    style="width: 60px; height: 60px"
                    :src="scope.row.picUrl"
                    fit="cover"
                    :preview-teleported="true"
                  />
                </ElCol>
                <ElCol :span="15">
                  <span class="overflow-line-clamp-2 name">
                    {{ scope.row.spuName }}
                  </span>
                  <p style="font-size: 12px; color: #a8abb2">
                    {{ scope.row.specsInfo }}
                  </p>
                </ElCol>
              </ElRow>
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
        </div>
      </ElCard>
      <ElCard class="box-card margin-top">
        <template #header>
          <div class="card-header">
            <span>物流信息</span>
          </div>
        </template>
        <ElForm ref="formRef" :model="state.form" :rules="state.rules">
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
            type="primary"
            @click="submitForm(formRef)"
            :loading="loading"
            >发货
          </ElButton>
        </span>
      </ElCard>
    </div>
  </ElDrawer>
</template>
<style lang="scss" scoped>
.margin-top {
  margin-top: 40px;
}

.dialog-footer {
  display: flex;
  justify-content: center;
}
</style>
