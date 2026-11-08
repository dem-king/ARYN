<script lang="ts" setup>
import type { UploadProps, UploadRequestOptions } from 'element-plus';

import { Upload } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElMessage, ElUpload } from 'element-plus';

import { uploadMaterial } from '#/api/pay/config';

const props = withDefaults(
  defineProps<{
    buttonText?: string;
    configId?: string;
    disabled?: boolean;
    purpose: string;
  }>(),
  {
    configId: '',
    buttonText: '上传资料',
    disabled: false,
  },
);

const emit = defineEmits<{
  success: [material: any];
}>();

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (!props.configId) {
    ElMessage.warning('请先保存支付配置，再上传资料');
    return false;
  }
  if (file.size > 256 * 1024) {
    ElMessage.error('支付资料不能超过 256KB');
    return false;
  }
  return true;
};

const handleUpload = async (options: UploadRequestOptions) => {
  try {
    const uploaded = await uploadMaterial(
      props.configId,
      props.purpose,
      options.file as File,
    );
    options.onSuccess(uploaded);
    emit('success', uploaded);
    ElMessage.success('资料已加密保存');
  } catch (error) {
    options.onError(error as any);
  }
};
</script>

<template>
  <ElUpload
    action="#"
    :disabled="disabled"
    :show-file-list="false"
    :before-upload="beforeUpload"
    :http-request="handleUpload"
  >
    <ElButton type="primary" :disabled="disabled">
      <ElIcon><Upload /></ElIcon>
      {{ buttonText }}
    </ElButton>
  </ElUpload>
</template>
