<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { computed, reactive, ref, watch } from 'vue';

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
          if (isQiniu.value && !domainValue) {
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
const normalizedType = computed(() => {
  const legacyTypes: Record<string, string> = {
    '1': 'aliyun',
    '2': 'tencent',
    '3': 'qiniu',
    '4': 'minio',
  };
  return legacyTypes[state.form.type] ?? state.form.type;
});
const isObjectStorage = computed(
  () => normalizedType.value !== '' && normalizedType.value !== 'local',
);
const isQiniu = computed(() => normalizedType.value === 'qiniu');
const isMinio = computed(() => normalizedType.value === 'minio');
const bucketLabel = computed(() =>
  normalizedType.value === 'local' ? '本地存储根目录' : 'Bucket',
);

watch(
  () => state.form.type,
  () => {
    if (normalizedType.value === 'local') {
      state.form.styleAccessEnabled = '0';
    } else if (isMinio.value) {
      state.form.styleAccessEnabled = '1';
    } else if (isObjectStorage.value) {
      state.form.styleAccessEnabled = '0';
    }
  },
);
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
      <ElFormItem label="AccessKey" prop="accessKey" v-if="isObjectStorage">
        <ElInput v-model="state.form.accessKey" />
      </ElFormItem>
      <ElFormItem
        label="AccessKeySecret"
        prop="accessSecret"
        v-if="isObjectStorage"
      >
        <ElInput
          v-model="state.form.accessSecret"
          type="password"
          show-password
        />
      </ElFormItem>
      <ElFormItem label="endpoint" prop="endpoint" v-if="isObjectStorage">
        <ElInput v-model="state.form.endpoint" />
      </ElFormItem>
      <ElFormItem :label="bucketLabel" prop="bucket">
        <ElInput
          v-model="state.form.bucket"
          :placeholder="
            normalizedType === 'local'
              ? '例如 /data/aryn/uploads 或 C:\\upload'
              : '请输入 Bucket 名称'
          "
        />
        <div v-if="normalizedType === 'local'" class="form-tip">
          Cloud Docker 推荐：/data/aryn/uploads；Boot 模式填写服务进程可写目录
        </div>
        <div v-else class="form-tip">对象存储桶名称（Bucket Name）</div>
      </ElFormItem>
      <ElFormItem label="对象目录" prop="dir" v-if="isObjectStorage">
        <ElInput v-model="state.form.dir" placeholder="例如 material，可留空" />
        <div class="form-tip">文件在 Bucket 内的可选目录前缀</div>
      </ElFormItem>
      <ElFormItem
        label="是否 path-style"
        prop="styleAccessEnabled"
        v-if="isObjectStorage"
      >
        <ElRadioGroup v-model="state.form.styleAccessEnabled">
          <ElRadio value="0">否</ElRadio>
          <ElRadio value="1">是</ElRadio>
        </ElRadioGroup>
        <div class="form-tip path-style-tip">
          <div>阿里云、腾讯云、七牛云选择“否”</div>
          <div>MinIO、RustFS 选择“是”</div>
        </div>
      </ElFormItem>
      <ElFormItem label="访问域名" prop="domain">
        <ElInput
          v-model="state.form.domain"
          :placeholder="
            normalizedType === 'local'
              ? '可留空；建议填源地址如 http://localhost:9999，系统按运行模式自动补 /upms 或 /boot'
              : '可选，例如 https://cdn.example.com'
          "
        />
        <div v-if="isQiniu" class="form-tip">
          七牛云必须配置已绑定的公开访问域名
        </div>
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
