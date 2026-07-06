<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/promotion/points-goods';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

interface DataState {
  form: {
    cover: string;
    datatimes: Array<any>;
    endTime: string;
    id: string;
    limitPerUser: number;
    name: string;
    pointsPrice: number;
    startTime: string;
    status: string;
    stock: number;
    targetId: string;
    type: string;
  };
  rules: any;
}

const state = reactive<DataState>({
  form: {
    id: '',
    name: '',
    cover: '',
    type: 'goods',
    targetId: '',
    pointsPrice: 0,
    stock: 0,
    limitPerUser: 0,
    datatimes: [],
    startTime: '',
    endTime: '',
    status: '0',
  },
  rules: {
    name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
    type: [{ required: true, message: '请选择商品类型', trigger: 'change' }],
    pointsPrice: [
      { required: true, message: '请输入积分价格', trigger: 'change' },
    ],
    stock: [{ required: true, message: '请输入库存', trigger: 'change' }],
    datatimes: [
      { required: true, message: '请选择活动时间', trigger: 'change' },
    ],
  },
});

const typeOptions = [
  { label: '实物商品', value: 'goods' },
  { label: '优惠券', value: 'coupon' },
  { label: '赠品', value: 'gift' },
];

const loading = ref(false);
const formRef = ref();
const dialog = ref(false);

const handleClose = () => {
  resetForm(formRef.value);
};

const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = '';
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      if (state.form.pointsPrice <= 0) {
        ElMessage.warning('积分价格必须大于0');
        return;
      }
      if (state.form.stock < 0) {
        ElMessage.warning('库存不能为负数');
        return;
      }
      state.form.startTime = state.form.datatimes[0];
      state.form.endTime = state.form.datatimes[1];
      loading.value = true;
      if (state.form.id) {
        edit();
      } else {
        add();
      }
    }
  });
};

const add = () => {
  addObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

const edit = () => {
  editObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

const initForm = async (row?: any) => {
  if (row?.id) {
    loading.value = true;
    try {
      const res = await getById(row.id);
      state.form = res;
      state.form.datatimes = [state.form.startTime, state.form.endTime];
    } finally {
      loading.value = false;
    }
  } else {
    state.form = {
      id: '',
      name: '',
      cover: '',
      type: 'goods',
      targetId: '',
      pointsPrice: 0,
      stock: 0,
      limitPerUser: 0,
      datatimes: [],
      startTime: '',
      endTime: '',
      status: '0',
    };
  }
  dialog.value = true;
};

defineExpose({ initForm });
</script>
<template>
  <ElDrawer
    v-model="dialog"
    :title="state.form.id ? '修改积分商品' : '新增积分商品'"
    :before-close="handleClose"
    size="50%"
  >
    <ElForm
      ref="formRef"
      class="form"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
      status-icon
      label-position="left"
      v-loading="loading"
    >
      <ElFormItem label="商品名称" prop="name">
        <ElInput v-model="state.form.name" maxlength="128" show-word-limit />
      </ElFormItem>
      <ElFormItem label="商品封面" prop="cover">
        <SelectMaterial v-model="state.form.cover" :can-choose-images-num="1" />
      </ElFormItem>
      <ElFormItem label="商品类型" prop="type">
        <ElSelect v-model="state.form.type" placeholder="请选择商品类型">
          <ElOption
            v-for="item in typeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="关联ID" prop="targetId">
        <div style="display: flex; gap: 8px; align-items: center">
          <ElInput
            v-model="state.form.targetId"
            placeholder="关联商品/优惠券ID"
          />
          <span style="color: var(--el-text-color-secondary)">
            实物商品填SPU ID，优惠券填优惠券ID
          </span>
        </div>
      </ElFormItem>
      <ElFormItem label="积分价格" prop="pointsPrice">
        <ElInputNumber
          v-model="state.form.pointsPrice"
          :min="1"
          :precision="0"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="库存" prop="stock">
        <ElInputNumber
          v-model="state.form.stock"
          :min="0"
          :precision="0"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="每人限购" prop="limitPerUser">
        <div style="display: flex; gap: 8px; align-items: center">
          <ElInputNumber
            v-model="state.form.limitPerUser"
            :min="0"
            :precision="0"
            :controls="false"
          />
          <span style="color: var(--el-text-color-secondary)">0表示不限购</span>
        </div>
      </ElFormItem>
      <ElFormItem label="活动时间" prop="datatimes">
        <ElDatePicker
          v-model="state.form.datatimes"
          type="datetimerange"
          range-separator="-"
          start-placeholder="活动开始时间"
          end-placeholder="活动结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton
          type="primary"
          :loading="loading"
          @click="submitForm(formRef)"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDrawer>
</template>
<style lang="scss" scoped></style>
