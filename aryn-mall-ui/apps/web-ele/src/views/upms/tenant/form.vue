<script lang="ts" setup>
import type {
  FormInstance,
  UploadProps,
  UploadRequestOptions,
} from 'element-plus';

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
  ElUpload,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/tenant';
import { getList } from '#/api/upms/tenant-package';
import { uploadFile } from '#/api/upms/upload';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
interface State {
  form: {
    address: string;
    authBeginTime: string;
    authEndTime: string;
    businessMode: string;
    datatimes: Array<string>;
    email: string;
    id: string;
    logoUrl: string;
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
// 业务模式决定 C 端是否渲染船供入口（首页船舶状态条、靠港配送）
const { business_mode: businessModeOptions } = useDict('business_mode');
const state = reactive<State>({
  form: {
    id: '',
    name: '',
    logoUrl: '',
    status: '1',
    // 默认综合模式：与 db 中 sys_tenant.business_mode 的默认值一致
    businessMode: '1',
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

const LOGO_MAX_SIZE_MB = 2;

const beforeLogoUpload: UploadProps['beforeUpload'] = (rawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.error('Logo 必须是图片文件！');
    return false;
  }
  if (rawFile.size / 1024 / 1024 > LOGO_MAX_SIZE_MB) {
    ElMessage.error(`Logo 大小不能超过 ${LOGO_MAX_SIZE_MB}MB！`);
    return false;
  }
  return true;
};

const handleLogoUpload = async (options: UploadRequestOptions) => {
  const formData = new FormData();
  formData.append('file', options.file);
  formData.append('type', '1');
  const material = (await uploadFile(formData)) as undefined | { url?: string };
  state.form.logoUrl = material?.url ?? '';
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
      label-width="auto"
      :rules="state.rules"
      status-icon
    >
      <ElRow>
        <ElCol :span="12">
          <ElFormItem label="租户名" prop="name">
            <ElInput v-model="state.form.name" show-word-limit maxlength="50" />
          </ElFormItem>
          <ElFormItem label="租户 Logo">
            <div class="tenant-logo">
              <img
                v-if="state.form.logoUrl"
                :src="state.form.logoUrl"
                alt="租户 Logo"
                class="tenant-logo__preview"
              />
              <div class="tenant-logo__actions">
                <ElUpload
                  action="#"
                  accept="image/*"
                  :show-file-list="false"
                  :before-upload="beforeLogoUpload"
                  :http-request="handleLogoUpload"
                >
                  <ElButton size="small" type="primary">
                    {{ state.form.logoUrl ? '重新上传' : '上传 Logo' }}
                  </ElButton>
                </ElUpload>
                <ElButton
                  v-if="state.form.logoUrl"
                  size="small"
                  text
                  type="danger"
                  @click="state.form.logoUrl = ''"
                >
                  清除
                </ElButton>
              </div>
            </div>
            <div class="tenant-logo__tip">
              用于管理端顶部品牌展示，建议 1:1、200×200，支持
              JPG/PNG/WEBP，不超过 2MB
            </div>
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
          <ElFormItem label="业务模式" prop="businessMode">
            <ElRadioGroup v-model="state.form.businessMode">
              <ElRadio
                v-for="item in businessModeOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
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
      <div class="hx-form-footer">
        <ElButton @click="handleClose">取消</ElButton>
        <ElButton
          type="primary"
          @click="submitForm(formRef)"
          :loading="loading"
        >
          确定
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.tenant-logo {
  display: flex;
  gap: 10px;
  align-items: center;
}

.tenant-logo__preview {
  width: 40px;
  height: 40px;
  object-fit: contain;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.tenant-logo__actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.tenant-logo__tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
