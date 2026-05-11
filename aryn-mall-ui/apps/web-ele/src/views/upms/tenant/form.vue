<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElCol,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSelect,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/tenant';
import { getList } from '#/api/upms/tenant-package';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
interface State {
  form: {
    address: string;
    authBeginTime: string;
    authEndTime: string;
    datatimes: Array<string>;
    email: string;
    id: string;
    name: string;
    packageId: string;
    password: string;
    phone: string;
    siteUrl: string;
    status: string;
    username: string;
  };
  rules: any;
  tenantPackageList: Array<any>;
}
// 字典
const { status } = useDict('status');
const state = reactive<State>({
  form: {
    id: '',
    name: '',
    status: '1',
    address: '',
    siteUrl: '',
    email: '',
    phone: '',
    datatimes: [],
    username: '',
    password: '',
    authBeginTime: '',
    authEndTime: '',
    packageId: '',
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入租户名',
        trigger: 'change',
      },
    ],
    address: [
      {
        required: true,
        message: '请输入租户地址',
        trigger: 'change',
      },
    ],
    siteUrl: [
      {
        required: true,
        message: '请输入租户官网',
        trigger: 'change',
      },
    ],
    datatimes: [
      {
        required: true,
        message: '请选择有效期',
        trigger: 'change',
      },
    ],
    email: [
      {
        pattern:
          /[\w!#$%&'*+/=?^`{|}~-]+(?:\.[\w!#$%&'*+/=?^`{|}~-]+)*@(?:\w(?:[\w-]*\w)?\.)+\w(?:[\w-]*\w)?/,
        message: '邮箱格式错误',
        trigger: 'blur',
      },
    ],
    phone: [
      {
        required: true,
        message: '请输入手机号',
        trigger: 'change',
      },
      {
        pattern: /^1[3-9]\d{9}$/,
        message: '手机号格式不对',
        trigger: 'blur',
      },
    ],
    packageId: [
      {
        required: true,
        message: '请选择租户套餐',
        trigger: 'change',
      },
    ],
    status: [
      {
        required: true,
        message: '请选择状态',
        trigger: 'change',
      },
    ],
    username: [
      {
        required: true,
        message: '请输入登录账号',
        trigger: 'change',
      },
    ],
    password: [
      {
        required: true,
        message: '请输入登录密码',
        trigger: 'change',
      },
    ],
  },
  tenantPackageList: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const initForm = (row: any | undefined) => {
  dialog.value = true;
  getTenantPackageList();
  if (row && row.id) {
    getDetail(row.id);
  }
};
const getDetail = (id: string) => {
  loading.value = true;
  getById(id).then((response: any) => {
    state.form = response;
    state.form.datatimes = [response.authBeginTime, response.authEndTime];
    loading.value = false;
  });
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
      state.form.authBeginTime = state.form.datatimes[0] ?? '';
      state.form.authEndTime = state.form.datatimes[1] ?? '';
      if (state.form.id) {
        // 修改
        edit();
      } else {
        // 新增
        add();
      }
    }
  });
};
/**
 * 新增
 */
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
/**
 * 修改
 */
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

const getTenantPackageList = () => {
  getList({ status: '0' }).then((response) => {
    state.tenantPackageList = response;
  });
};
defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改租户' : '添加租户'"
    width="60%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
      status-icon
    >
      <ElRow>
        <ElCol :span="12">
          <ElFormItem label="租户名" prop="name">
            <ElInput v-model="state.form.name" show-word-limit maxlength="50" />
          </ElFormItem>
          <ElFormItem label="官网" prop="siteUrl">
            <ElInput v-model="state.form.siteUrl" />
          </ElFormItem>
          <ElFormItem label="有效时间" prop="datatimes">
            <ElDatePicker
              v-model="state.form.datatimes"
              type="datetimerange"
              range-separator="-"
              start-placeholder="授权开始时间"
              end-placeholder="授权结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </ElFormItem>
          <ElFormItem label="租户套餐" prop="packageId">
            <ElSelect
              v-model="state.form.packageId"
              clearable
              :disabled="!!state.form.id"
              style="width: 100%"
            >
              <ElOption
                v-for="(item, index) in state.tenantPackageList"
                :key="index"
                :label="item.name"
                :value="item.id"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="登录账号" prop="username" v-if="!state.form.id">
            <ElInput v-model="state.form.username" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="地址" prop="address">
            <ElInput v-model="state.form.address" />
          </ElFormItem>
          <ElFormItem label="邮箱" prop="email">
            <ElInput v-model="state.form.email" />
          </ElFormItem>
          <ElFormItem label="手机号" prop="phone">
            <ElInput v-model="state.form.phone" />
          </ElFormItem>
          <ElFormItem label="状态" prop="status">
            <ElRadioGroup v-model="state.form.status">
              <ElRadio
                v-for="item in status"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="登录密码" prop="password" v-if="!state.form.id">
            <ElInput
              type="password"
              show-password
              v-model="state.form.password"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton
          type="primary"
          @click="submitForm(formRef)"
          :loading="loading"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
