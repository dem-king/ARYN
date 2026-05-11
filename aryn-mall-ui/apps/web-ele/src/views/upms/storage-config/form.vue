<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/storage-config';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

const { sys_storage_type, status } = useDict('sys_storage_type', 'status');

const state = reactive({
  form: {
    id: '',
    type: '',
    accessKey: '',
    accessSecret: '',
    endpoint: '',
    bucket: '',
    dir: '',
    status: '',
    styleAccessEnabled: '0',
    domain: '',
  },
  rules: {
    type: [
      {
        required: true,
        message: '请选择文件存储类型',
        trigger: 'change',
      },
    ],
    accessKey: [
      {
        required: true,
        message: '请输入AccessKey',
        trigger: 'change',
      },
    ],
    accessSecret: [
      {
        required: true,
        message: '请输入AccessKeySecret',
        trigger: 'change',
      },
    ],
    endpoint: [
      {
        required: true,
        message: '请输入endpoint',
        trigger: 'change',
      },
    ],
    bucket: [
      {
        required: true,
        message: '请输入bucket',
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
    styleAccessEnabled: [
      {
        required: true,
        message: '请选择是否 path-style',
        trigger: 'change',
      },
    ],
    domain: [
      {
        validator: (_rule: any, value: string, callback: any) => {
          const domainValue = value?.trim();
          if (state.form.endpoint?.includes('qiniucs') && !domainValue) {
            callback(new Error('七牛云存储请填写自定义域名'));
            return;
          }
          if (domainValue && !/^https?:\/\//.test(domainValue)) {
            callback(new Error('自定义域名需以 http:// 或 https:// 开头'));
            return;
          }
          callback();
        },
        trigger: 'blur',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const initForm = (row: any) => {
  if (row && row.id) {
    getDetail(row.id);
  }
  dialog.value = true;
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response: any) => {
      loading.value = false;
      state.form = response;
    })
    .catch(() => {
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

defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改存储配置' : '添加存储配置'"
    width="50%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="200px"
      :rules="state.rules"
    >
      <ElFormItem label="存储配置类型" prop="type">
        <ElRadioGroup v-model="state.form.type">
          <ElRadio
            v-for="item in sys_storage_type"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem
        label="AccessKey"
        prop="accessKey"
        v-if="state.form.type === 'oss'"
      >
        <ElInput v-model="state.form.accessKey" />
      </ElFormItem>
      <ElFormItem
        label="AccessKeySecret"
        prop="accessSecret"
        v-if="state.form.type === 'oss'"
      >
        <ElInput v-model="state.form.accessSecret" />
      </ElFormItem>
      <ElFormItem
        label="endpoint"
        prop="endpoint"
        v-if="state.form.type === 'oss'"
      >
        <ElInput v-model="state.form.endpoint" />
      </ElFormItem>
      <ElFormItem label="bucket" prop="bucket">
        <ElInput v-model="state.form.bucket" />
        <div v-if="state.form.type === 'local'" class="form-tip">
          地址示例：Windows：C:\upload；Linux：/home/upload
        </div>
        <div v-else-if="state.form.type === 'oss'" class="form-tip">
          桶名称（Bucket Name）
        </div>
      </ElFormItem>
      <ElFormItem label="是否 path-style" prop="styleAccessEnabled">
        <ElRadioGroup v-model="state.form.styleAccessEnabled">
          <ElRadio value="0">否</ElRadio>
          <ElRadio value="1">是</ElRadio>
        </ElRadioGroup>
        <div class="form-tip path-style-tip">
          <div>使用 path-style：</div>
          <div>阿里云、腾讯云、七牛云必须选择“否”</div>
          <div>MinIO、RustFS 必须选择“是”</div>
        </div>
      </ElFormItem>
      <ElFormItem label="自定义域名" prop="domain">
        <ElInput v-model="state.form.domain" />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio v-for="item in status" :key="item.value" :value="item.value">
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
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
<style scoped>
.pay-tabs {
  margin-bottom: 30px;
}

.pay-form {
  margin-top: 20px;
}

.pay-input {
  width: 400px;
}

.pay-input-sm {
  width: 300px;
}

.upload-group {
  display: flex;
  gap: 12px;
  align-items: center;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.path-style-tip {
  width: 100%;
}
</style>
