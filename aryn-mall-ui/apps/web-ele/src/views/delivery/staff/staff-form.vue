<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type { DeliveryStaff, DeliveryStaffStatus } from '#/api/delivery/staff';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import {
  createDeliveryStaff,
  getDeliveryStaffById,
  updateDeliveryStaff,
} from '#/api/delivery/staff';
import { getPage as getUserPage } from '#/api/upms/user';

const emit = defineEmits(['initPage']);

/** 配送员状态选项 */
const statusOptions: { label: string; value: DeliveryStaffStatus }[] = [
  { value: '1', label: '在线' },
  { value: '2', label: '忙碌' },
  { value: '3', label: '离线' },
];

interface BackendUser {
  id: string;
  nickName: string;
  userName: string;
  phonenumber?: string;
}

const state = reactive({
  form: {
    id: '',
    userId: '',
    userName: '',
    staffName: '',
    staffPhone: '',
    vehicleInfo: '',
    status: '1' as DeliveryStaffStatus,
  },
  rules: {
    userId: [
      { required: true, message: '请选择关联后台用户', trigger: 'change' },
    ],
    staffName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    staffPhone: [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      {
        pattern: /^1[3-9]\d{9}$/,
        message: '请输入正确的手机号',
        trigger: 'blur',
      },
    ],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  },
  userOptions: [] as BackendUser[],
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

/**
 * 打开弹窗
 */
const initForm = (row?: DeliveryStaff) => {
  if (row && row.id) {
    getDetail(row.id);
  } else {
    state.form = {
      id: '',
      userId: '',
      userName: '',
      staffName: '',
      staffPhone: '',
      vehicleInfo: '',
      status: '1',
    };
  }
  dialog.value = true;
  loadUsers();
};

/**
 * 加载后台用户列表
 */
const loadUsers = () => {
  getUserPage({ current: 1, size: 100, status: '0' })
    .then((response: any) => {
      state.userOptions = response.records || [];
    })
    .catch(() => {});
};

/**
 * 获取详情
 */
const getDetail = (id: string) => {
  loading.value = true;
  getDeliveryStaffById(id)
    .then((response: any) => {
      loading.value = false;
      state.form = {
        id: response.id,
        userId: response.userId ?? '',
        userName: response.userName ?? '',
        staffName: response.staffName,
        staffPhone: response.staffPhone,
        vehicleInfo: response.vehicleInfo ?? '',
        status: response.status,
      };
    })
    .catch(() => {
      loading.value = false;
    });
};

/**
 * 关联用户变更时同步姓名手机号
 */
const handleUserChange = (userId: string) => {
  const user = state.userOptions.find((item) => item.id === userId);
  if (user) {
    state.form.userName = user.nickName || user.userName;
    if (user.phonenumber && !state.form.staffPhone) {
      state.form.staffPhone = user.phonenumber;
    }
    if (!state.form.staffName) {
      state.form.staffName = user.nickName || user.userName;
    }
  }
};

/**
 * 关闭事件
 */
const handleClose = () => {
  resetForm(formRef.value);
};

/**
 * 重置表单
 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = '';
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};

/**
 * 提交按钮
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      if (state.form.id) {
        update();
      } else {
        add();
      }
    }
  });
};

/**
 * 新增
 */
const add = () => {
  createDeliveryStaff(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

/**
 * 修改
 */
const update = () => {
  updateDeliveryStaff(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '编辑配送员' : '新增配送员'"
    width="520px"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
    >
      <ElFormItem label="关联后台用户" prop="userId">
        <ElSelect
          v-model="state.form.userId"
          placeholder="请选择关联后台用户"
          filterable
          style="width: 100%"
          @change="handleUserChange"
        >
          <ElOption
            v-for="item in state.userOptions"
            :key="item.id"
            :label="item.nickName || item.userName"
            :value="item.id"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="姓名" prop="staffName">
        <ElInput v-model="state.form.staffName" placeholder="请输入姓名" />
      </ElFormItem>
      <ElFormItem label="手机号" prop="staffPhone">
        <ElInput v-model="state.form.staffPhone" placeholder="请输入手机号" />
      </ElFormItem>
      <ElFormItem label="车辆信息" prop="vehicleInfo">
        <ElInput
          v-model="state.form.vehicleInfo"
          placeholder="请输入车辆信息，如：电动车/三轮车-京A12345"
        />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElSelect v-model="state.form.status" style="width: 100%">
          <ElOption
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
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
  </ElDialog>
</template>
