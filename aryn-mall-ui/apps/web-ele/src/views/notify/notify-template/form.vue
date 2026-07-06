<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/notify/notify-template';

const emit = defineEmits(['initPage']);

const notifyTypeOptions = [
  { value: 1, label: '订单消息' },
  { value: 2, label: '支付消息' },
  { value: 3, label: '物流消息' },
  { value: 4, label: '营销消息' },
  { value: 5, label: '系统消息' },
  { value: 6, label: '社交消息' },
];

const jumpTypeOptions = [
  { value: 0, label: '不跳转' },
  { value: 1, label: '订单详情' },
  { value: 2, label: '商品详情' },
  { value: 3, label: '活动页' },
  { value: 4, label: '自定义链接' },
];

interface FormState {
  form: {
    content: string;
    id: string | undefined;
    jumpType: number;
    jumpUrl: string;
    notifyType: number | undefined;
    remark: string;
    status: string;
    templateCode: string;
    templateName: string;
    title: string;
  };
  rules: any;
}

const state = reactive<FormState>({
  form: {
    id: undefined,
    templateCode: '',
    templateName: '',
    notifyType: undefined,
    title: '',
    content: '',
    jumpType: 0,
    jumpUrl: '',
    status: '1',
    remark: '',
  },
  rules: {
    templateCode: [
      { required: true, message: '请输入模板编码', trigger: 'change' },
    ],
    templateName: [
      { required: true, message: '请输入模板名称', trigger: 'change' },
    ],
    notifyType: [
      { required: true, message: '请选择消息类型', trigger: 'change' },
    ],
    title: [{ required: true, message: '请输入消息标题', trigger: 'change' }],
    content: [{ required: true, message: '请输入消息内容', trigger: 'change' }],
  },
});

const loading = ref(false);
const formRef = ref();
const dialog = ref(false);

const handleClose = () => {
  resetForm(formRef.value);
};

const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = undefined;
  state.form.templateCode = '';
  state.form.templateName = '';
  state.form.notifyType = undefined;
  state.form.title = '';
  state.form.content = '';
  state.form.jumpType = 0;
  state.form.jumpUrl = '';
  state.form.status = '1';
  state.form.remark = '';
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
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

const initForm = (row: any) => {
  dialog.value = true;
  if (row && row.id) {
    loading.value = true;
    getById(row.id)
      .then((response) => {
        loading.value = false;
        state.form = { ...state.form, ...response };
      })
      .catch(() => {
        loading.value = false;
      });
  }
};

defineExpose({ initForm });
</script>

<template>
  <ElDrawer
    v-model="dialog"
    :title="state.form.id ? '修改消息模板' : '新增消息模板'"
    size="600px"
    @close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      :rules="state.rules"
      label-width="100px"
      v-loading="loading"
    >
      <ElFormItem label="模板编码" prop="templateCode">
        <ElInput
          v-model="state.form.templateCode"
          placeholder="如 ORDER_PAY_SUCCESS"
          :disabled="!!state.form.id"
        />
      </ElFormItem>
      <ElFormItem label="模板名称" prop="templateName">
        <ElInput
          v-model="state.form.templateName"
          placeholder="请输入模板名称"
        />
      </ElFormItem>
      <ElFormItem label="消息类型" prop="notifyType">
        <ElSelect
          v-model="state.form.notifyType"
          placeholder="请选择消息类型"
          style="width: 100%"
        >
          <ElOption
            v-for="item in notifyTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="消息标题" prop="title">
        <ElInput
          v-model="state.form.title"
          type="textarea"
          :rows="2"
          placeholder="支持变量 ${var}，如：订单支付成功"
        />
      </ElFormItem>
      <ElFormItem label="消息内容" prop="content">
        <ElInput
          v-model="state.form.content"
          type="textarea"
          :rows="5"
          placeholder="支持变量 ${var}，如：您的订单 ${orderNo} 已支付成功"
        />
      </ElFormItem>
      <ElFormItem label="跳转类型" prop="jumpType">
        <ElSelect
          v-model="state.form.jumpType"
          placeholder="请选择跳转类型"
          style="width: 100%"
        >
          <ElOption
            v-for="item in jumpTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem
        label="跳转地址"
        prop="jumpUrl"
        v-if="state.form.jumpType !== 0"
      >
        <ElInput
          v-model="state.form.jumpUrl"
          placeholder="支持变量 ${var}，如：/sub-pages/order/order-detail/index?id=${orderId}"
        />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElSwitch
          v-model="state.form.status"
          active-value="1"
          inactive-value="0"
          active-text="启用"
          inactive-text="禁用"
        />
      </ElFormItem>
      <ElFormItem label="备注" prop="remark">
        <ElInput
          v-model="state.form.remark"
          type="textarea"
          :rows="2"
          placeholder="备注"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="submitForm(formRef)" :loading="loading">
        确认
      </ElButton>
    </template>
  </ElDrawer>
</template>
