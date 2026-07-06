<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCol,
  ElDialog,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElRow,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/pay/config';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

const CertUpload = defineAsyncComponent(
  () => import('#/components/cert-upload/index.vue'),
);

const { pay_terminal_type } = useDict('pay_terminal_type');

// 支付类型配置
const PAY_TYPES = [
  {
    value: '1',
    label: '微信支付',
    color: 'success' as const,
    desc: '支持微信小程序、H5、APP等',
  },
  {
    value: '2',
    label: '支付宝支付',
    color: 'primary' as const,
    desc: '支持支付宝小程序、H5、APP等',
  },
];

const state = reactive({
  form: {
    id: '',
    type: '',
    mchId: '',
    apiv3Key: '',
    appId: '',
    mchKey: '',
    certSerialNo: '',
    keyPath: '',
    privateKeyPath: '',
    privateCertPath: '',
    terminalType: '',
    publicKeyId: '',
    publicKeyPath: '',
  },
  rules: {
    type: [
      {
        required: true,
        message: '请选择支付类型',
        trigger: 'change',
      },
    ],
    terminalType: [
      {
        required: true,
        message: '请选择支付端类型',
        trigger: 'change',
      },
    ],
    mchId: [
      {
        required: true,
        message: '请输入商户号',
        trigger: 'change',
      },
    ],
    apiv3Key: [
      {
        required: true,
        message: '请输入apiV3秘钥',
        trigger: 'change',
      },
    ],
    appId: [
      {
        required: true,
        message: '请输入应用ID',
        trigger: 'change',
      },
    ],
    mchKey: [
      {
        required: true,
        message: '请输入支付商户私钥',
        trigger: 'change',
      },
    ],
    publicKeyId: [
      {
        required: true,
        message: '请输入公钥ID',
        trigger: 'change',
      },
    ],
    publicKeyPath: [
      {
        required: true,
        message: '请上传公钥证书',
        trigger: 'change',
      },
    ],
    certSerialNo: [
      {
        required: true,
        message: '请上传API证书序列号',
        trigger: 'change',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const initForm = (
  row: any,
  preSelect?: { mode?: string; payType?: string; terminalType?: string },
) => {
  if (row && row.id) {
    getDetail(row.id);
  } else {
    // 重置表单
    state.form = {
      id: '',
      type: preSelect?.payType || '',
      mchId: '',
      apiv3Key: '',
      appId: '',
      mchKey: '',
      certSerialNo: '',
      keyPath: '',
      privateKeyPath: '',
      privateCertPath: '',
      terminalType: preSelect?.terminalType || '',
      publicKeyId: '',
      publicKeyPath: '',
    };
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
  state.form.apiv3Key = '';
  state.form.certSerialNo = '';
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

const keyPathHandle = (url: string) => {
  state.form.keyPath = url;
};

const privateKeyPathHandle = (url: string) => {
  state.form.privateKeyPath = url;
};

const privateCertPathHandle = (url: string) => {
  state.form.privateCertPath = url;
};
const publicKeyPathHandle = (url: string) => {
  state.form.publicKeyPath = url;
};

defineExpose({
  initForm,
});
</script>

<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改支付配置' : '新增支付配置'"
    width="60%"
    :before-close="handleClose"
    class="pay-config-dialog"
  >
    <div class="form-container">
      <!-- 基本信息 -->
      <ElCard class="form-section">
        <template #header>
          <div class="section-header">
            <h3>基本信息</h3>
            <p>配置支付的基本参数信息</p>
          </div>
        </template>

        <ElForm
          ref="formRef"
          :model="state.form"
          label-width="140px"
          :rules="state.rules"
          class="pay-form"
        >
          <ElRow :gutter="20">
            <ElCol :span="12">
              <ElFormItem label="支付类型" prop="type">
                <ElRadioGroup v-model="state.form.type" class="radio-group">
                  <ElRadio
                    v-for="item in PAY_TYPES"
                    :key="item.value"
                    :value="item.value"
                    class="radio-item"
                  >
                    <div class="radio-content">
                      <span class="radio-label">{{ item.label }}</span>
                    </div>
                  </ElRadio>
                </ElRadioGroup>
              </ElFormItem>
            </ElCol>
          </ElRow>

          <ElRow>
            <ElCol :span="12">
              <ElFormItem label="支付端类型" prop="terminalType">
                <ElRadioGroup
                  v-model="state.form.terminalType"
                  class="radio-group"
                >
                  <ElRadio
                    v-for="item in pay_terminal_type"
                    :key="item.value"
                    :value="item.value"
                    class="radio-item"
                  >
                    <div class="radio-content">
                      <span class="radio-label">{{ item.label }}</span>
                    </div>
                  </ElRadio>
                </ElRadioGroup>
              </ElFormItem>
            </ElCol>
          </ElRow>

          <ElRow :gutter="20">
            <ElCol :span="12">
              <ElFormItem label="商户号" prop="mchId">
                <ElInput
                  v-model="state.form.mchId"
                  maxlength="20"
                  show-word-limit
                  placeholder="请输入商户号"
                  class="form-input"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="应用ID" prop="appId">
                <ElInput
                  v-model="state.form.appId"
                  maxlength="20"
                  show-word-limit
                  placeholder="请输入应用ID"
                  class="form-input"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElForm>
      </ElCard>

      <!-- 密钥配置 -->
      <ElCard class="form-section">
        <template #header>
          <div class="section-header">
            <h3>密钥配置</h3>
            <p>配置支付相关的密钥和证书信息</p>
          </div>
        </template>

        <ElForm
          ref="formRef"
          :model="state.form"
          label-width="140px"
          :rules="state.rules"
          class="pay-form"
        >
          <!-- 微信支付特有字段 -->
          <div v-if="state.form.type === '1'" class="wechat-fields">
            <ElAlert
              title="微信支付配置"
              type="success"
              :closable="false"
              show-icon
              class="field-alert"
            >
              以下字段为微信支付特有配置项
            </ElAlert>

            <ElRow :gutter="20">
              <ElCol :span="12">
                <ElFormItem label="apiV3秘钥" prop="apiv3Key">
                  <ElInput
                    v-model="state.form.apiv3Key"
                    show-word-limit
                    placeholder="请输入apiV3秘钥"
                    class="form-input"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="api证书序列号" prop="certSerialNo">
                  <ElInput
                    v-model="state.form.certSerialNo"
                    show-word-limit
                    placeholder="请输入api证书序列号"
                    class="form-input"
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>
            <ElRow :gutter="20">
              <ElCol :span="24">
                <ElFormItem label="公钥ID" prop="publicKeyId">
                  <ElInput
                    v-model="state.form.publicKeyId"
                    maxlength="50"
                    show-word-limit
                    placeholder="请输入公钥ID"
                    class="form-input"
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>
          </div>

          <!-- 支付宝特有字段 -->
          <div v-if="state.form.type === '2'" class="alipay-fields">
            <ElAlert
              title="支付宝支付配置"
              type="info"
              :closable="false"
              show-icon
              class="field-alert"
            >
              以下字段为支付宝支付特有配置项
            </ElAlert>

            <ElFormItem label="支付宝私钥" prop="mchKey">
              <ElInput
                v-model="state.form.mchKey"
                show-word-limit
                placeholder="请输入支付宝私钥"
                class="form-input"
              />
            </ElFormItem>
          </div>

          <!-- 证书文件上传 -->
          <div class="cert-upload-section">
            <ElDivider content-position="left">证书文件配置</ElDivider>
            <ElFormItem
              label="公钥证书"
              prop="publicKeyPath"
              label-width="200px"
              v-if="state.form.type === '1'"
            >
              <div class="upload-group">
                <CertUpload @handle-success="publicKeyPathHandle" />
                <ElInput
                  v-model="state.form.publicKeyPath"
                  class="upload-input"
                  placeholder="公钥证书文件路径"
                  style="min-width: 400px"
                />
              </div>
            </ElFormItem>

            <ElFormItem
              :label="
                state.form.type === '1'
                  ? '商户apiclient_cert.pem'
                  : '应用公钥证书(AppCertPath)'
              "
              prop="privateCertPath"
              label-width="200px"
            >
              <!-- appCertPublicKey.crt -->
              <div class="upload-group">
                <CertUpload @handle-success="privateCertPathHandle" />
                <ElInput
                  v-model="state.form.privateCertPath"
                  class="upload-input"
                  placeholder="证书文件路径"
                  style="min-width: 400px"
                />
              </div>
            </ElFormItem>

            <ElFormItem
              :label="
                state.form.type === '1'
                  ? '商户apiclient_key.pem'
                  : '支付宝公钥证书(AlipayPublicCertPat)'
              "
              prop="privateKeyPath"
              label-width="200px"
            >
              <div class="upload-group">
                <CertUpload @handle-success="privateKeyPathHandle" />
                <ElInput
                  v-model="state.form.privateKeyPath"
                  class="upload-input"
                  placeholder="证书文件路径"
                  style="min-width: 400px"
                />
              </div>
            </ElFormItem>

            <ElFormItem
              :label="
                state.form.type === '1'
                  ? 'apiclient_cert.p12文件路径'
                  : '支付宝根证书(RootCertPath)'
              "
              prop="keyPath"
              label-width="200px"
            >
              <div class="upload-group">
                <CertUpload @handle-success="keyPathHandle" />
                <ElInput
                  v-model="state.form.keyPath"
                  class="upload-input"
                  placeholder="证书文件路径"
                  style="min-width: 400px"
                />
              </div>
            </ElFormItem>
          </div>
        </ElForm>
      </ElCard>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton
          type="primary"
          :loading="loading"
          @click="submitForm(formRef)"
        >
          {{ state.form.id ? '修改' : '新增' }}
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
/* 响应式设计 */
@media (max-width: 768px) {
  .form-section {
    margin: 16px;
  }

  .radio-group {
    flex-direction: column;
    gap: 8px;
  }

  .upload-group {
    flex-direction: column;
    align-items: stretch;
  }
}

.pay-config-dialog {
  min-width: 800px;
  border-radius: 8px;
}

.form-container {
  max-height: 70vh;
  padding: 0 20px;
  overflow-y: auto;
}

.form-section {
  margin-bottom: 24px;
}

.section-header {
  margin-bottom: 0;
}

.section-header h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-header p {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pay-form {
  margin-top: 16px;
}

.radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.radio-item {
  margin-right: 0 !important;
}

.radio-content {
  display: flex;
  gap: 8px;
  align-items: center;
}

.radio-icon {
  font-size: 16px;
}

.radio-label {
  font-weight: 500;
}

.form-input {
  width: 100%;
}

.form-tip {
  margin-top: 8px;
}

.form-tip :deep(.el-alert) {
  padding: 8px 12px;
}

.form-tip :deep(.el-alert__title) {
  font-size: 12px;
  font-weight: 500;
}

.form-tip :deep(.el-alert__content) {
  font-size: 12px;
  line-height: 1.5;
}

.field-alert {
  margin-bottom: 16px;
}

.wechat-fields,
.alipay-fields {
  margin-bottom: 20px;
}

.cert-upload-section {
  margin-top: 20px;
}

.upload-group {
  display: flex;
  gap: 16px;
  align-items: center;
  width: 100%;
  min-height: 40px;
}

.upload-input {
  flex: 1;
  min-width: 400px;
}

/* 确保上传组件有合适的宽度 */
.upload-group :deep(.el-upload) {
  flex-shrink: 0;
}

.upload-group :deep(.el-upload-dragger) {
  width: 120px;
  height: 40px;
}

/* 证书文件上传区域样式优化 */

/* 证书文件上传区域样式优化 */
.cert-upload-section .el-form-item {
  margin-bottom: 24px;
}

.cert-upload-section .el-form-item__label {
  min-width: 220px;
  padding-right: 16px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--el-text-color-regular);
  text-align: right;
}

.cert-upload-section .el-form-item__content {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
