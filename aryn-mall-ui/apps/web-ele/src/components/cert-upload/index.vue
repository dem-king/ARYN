<script lang="ts" setup>
import type { UploadProps } from 'element-plus';

import { ref } from 'vue';

import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { Upload } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElMessage, ElUpload } from 'element-plus';

import { parseOpenBoot, rewriteBootUrl } from '#/api/boot-url';

const emit = defineEmits(['handleSuccess']);
const accessStore = useAccessStore();
const headers = ref({
  satoken: accessStore.accessToken,
});

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const openBoot = parseOpenBoot(import.meta.env.VITE_OPEN_BOOT);
const action = ref(
  `${apiURL}${rewriteBootUrl('/pay/payconfig/cert/upload', openBoot) ?? '/pay/payconfig/cert/upload'}`,
);
/**
 * 上传素材前事件
 */
const beforeUploadCert: UploadProps['beforeUpload'] = () => {
  return true;
};
/**
 * 上传成功事件
 */
const handleSuccess: UploadProps['onSuccess'] = (response) => {
  emit('handleSuccess', response.data);
};
/**
 * 上传失败事件
 */
const handleError: UploadProps['onError'] = (error) => {
  ElMessage.error(`${error}`);
};
</script>
<template>
  <div class="cert-upload">
    <ElUpload
      append-to-body
      :action="action"
      :headers="headers"
      :show-file-list="false"
      :before-upload="beforeUploadCert"
      :on-success="handleSuccess"
      :on-error="handleError"
    >
      <ElButton type="primary">
        <ElIcon> <Upload /> </ElIcon>上传证书
      </ElButton>
    </ElUpload>
  </div>
</template>
<style scoped>
.cert-upload {
  margin: 5px;
}
</style>
