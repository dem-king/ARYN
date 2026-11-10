<script lang="ts" setup>
import type { UploadProps, UploadRequestOptions } from 'element-plus';

import type {
  CopyrightClientType,
  CopyrightConfig,
} from '#/api/upms/copyright-config';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTabPane,
  ElTabs,
  ElUpload,
} from 'element-plus';

import {
  getCopyrightConfig,
  saveCopyrightConfig,
} from '#/api/upms/copyright-config';
import { uploadFile } from '#/api/upms/upload';

defineOptions({ name: 'CopyrightConfigPage' });

const route = useRoute();

/**
 * 平台与租户共用一个组件、两个菜单入口：
 *  · /platform/copyright-config（平台管理，sys_key）：商户端 + 平台端，仅平台管理员可见；
 *  · /system/copyright-config（系统设置，app_base）：只有用户端，各租户自己维护。
 * 拆菜单而不是按身份隐藏 Tab：平台专属应用不随套餐下发，租户天然看不到平台入口。
 */
const platformScope = computed(() => route.path.startsWith('/platform'));

/** 页面实例内作用域固定（平台页/租户页是两个菜单入口），保存权限按入口判定 */
const editPermission = platformScope.value
  ? 'upms:copyright:platform:edit'
  : 'upms:copyright:edit';

const CLIENT_LABEL: Record<CopyrightClientType, string> = {
  app: '用户端',
  merchant: '商户端',
  platform: '平台端',
};

const tabs = computed(() => {
  const types: CopyrightClientType[] = platformScope.value
    ? ['merchant', 'platform']
    : ['app'];
  return types.map((type) => ({ label: CLIENT_LABEL[type], value: type }));
});

const activeTab = ref<CopyrightClientType>(
  platformScope.value ? 'merchant' : 'app',
);

const loading = ref(false);
const saving = ref(false);

const emptyForm = (): CopyrightConfig => ({
  clientType: 'merchant',
  copyrightLink: '',
  copyrightText: '',
  icp: '',
  icpLink: '',
  loginIcon: '',
  loginSubtitle: '',
  loginTitle: '',
  logoUrl: '',
  name: '',
});

const state = reactive({ form: emptyForm() });

const formRef = ref<InstanceType<typeof ElForm>>();

const rules = {
  name: [{ max: 30, message: '名称不能超过 30 个字符', trigger: 'blur' }],
  copyrightText: [
    { max: 100, message: '版权信息不能超过 100 个字符', trigger: 'blur' },
  ],
  copyrightLink: [
    {
      message: '请填写 http/https 链接或站内相对路径',
      pattern: /^(https?:\/\/|\/)/i,
      trigger: 'blur',
    },
  ],
  icpLink: [
    {
      message: '请填写 http/https 链接或站内相对路径',
      pattern: /^(https?:\/\/|\/)/i,
      trigger: 'blur',
    },
  ],
  loginTitle: [
    { max: 60, message: '登录页标题不能超过 60 个字符', trigger: 'blur' },
  ],
  loginSubtitle: [
    { max: 128, message: '登录页副标题不能超过 128 个字符', trigger: 'blur' },
  ],
};

/** 当前端是否展示登录页专属字段（只有平台端登录页在登录前渲染） */
const showLoginFields = computed(() => activeTab.value === 'platform');

const scopeTip = computed(() =>
  platformScope.value
    ? '商户端作用于管理后台「个人信息」页底部与左上角品牌「关于」弹窗；平台端作用于管理后台登录页（所有租户共用）。'
    : '用户端作用于 C 端商城的登录页与页脚，仅对当前租户生效。',
);

async function load() {
  loading.value = true;
  try {
    const config = await getCopyrightConfig(activeTab.value);
    state.form = { ...emptyForm(), ...config, clientType: activeTab.value };
  } catch {
    // 读取失败保留空表单：保存即新建，避免把脏值写回
    state.form = { ...emptyForm(), clientType: activeTab.value };
  } finally {
    loading.value = false;
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (valid === false) return;
  saving.value = true;
  try {
    await saveCopyrightConfig({ ...state.form, clientType: activeTab.value });
    ElMessage.success('保存成功');
    await load();
  } finally {
    saving.value = false;
  }
}

function reset() {
  void load();
}

const LOGO_MAX_SIZE_MB = 2;

const beforeImageUpload: UploadProps['beforeUpload'] = (rawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件');
    return false;
  }
  if (rawFile.size / 1024 / 1024 > LOGO_MAX_SIZE_MB) {
    ElMessage.error(`图片大小不能超过 ${LOGO_MAX_SIZE_MB}MB`);
    return false;
  }
  return true;
};

async function uploadImage(
  options: UploadRequestOptions,
  target: 'loginIcon' | 'logoUrl',
) {
  const formData = new FormData();
  formData.append('file', options.file);
  formData.append('type', '1');
  const material = (await uploadFile(formData)) as undefined | { url?: string };
  state.form[target] = material?.url ?? '';
}

const handleLogoUpload = (options: UploadRequestOptions) =>
  uploadImage(options, 'logoUrl');

const handleLoginIconUpload = (options: UploadRequestOptions) =>
  uploadImage(options, 'loginIcon');

watch(activeTab, () => void load(), { immediate: true });
</script>

<template>
  <div class="hx-layout-container copyright-config">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <section v-loading="loading" class="cc-panel">
        <header class="cc-head">
          <div>
            <h3 class="cc-title">版权配置</h3>
            <p class="cc-tip">{{ scopeTip }}</p>
          </div>
        </header>

        <ElTabs v-model="activeTab" class="cc-tabs">
          <ElTabPane
            v-for="tab in tabs"
            :key="tab.value"
            :label="tab.label"
            :name="tab.value"
          />
        </ElTabs>

        <ElForm
          ref="formRef"
          class="cc-form"
          label-width="auto"
          :model="state.form"
          :rules="rules"
          status-icon
        >
          <ElFormItem label="Logo">
            <div class="cc-image">
              <div class="cc-image__box">
                <img
                  v-if="state.form.logoUrl"
                  :alt="state.form.name || 'Logo'"
                  class="cc-image__preview"
                  :src="state.form.logoUrl"
                />
                <span v-else class="cc-image__empty">未设置</span>
              </div>
              <div class="cc-image__actions">
                <ElUpload
                  accept="image/*"
                  action="#"
                  :before-upload="beforeImageUpload"
                  :http-request="handleLogoUpload"
                  :show-file-list="false"
                >
                  <ElButton size="small" type="primary">
                    {{ state.form.logoUrl ? '重新上传' : '上传图片' }}
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
            <div class="cc-hint">
              留空则跟随租户品牌 Logo，建议 1:1、不超过 2MB
            </div>
          </ElFormItem>

          <ElFormItem label="名称" prop="name">
            <ElInput
              v-model="state.form.name"
              maxlength="30"
              placeholder="留空则跟随租户品牌名称"
              show-word-limit
            />
            <div class="cc-hint">
              留空则跟随租户品牌名称（与后台顶栏同一来源），避免两处品牌不一致
            </div>
          </ElFormItem>

          <ElFormItem label="版权信息" prop="copyrightText">
            <ElInput
              v-model="state.form.copyrightText"
              maxlength="100"
              placeholder="如：悦航购 Aetheryn Mall"
              show-word-limit
            />
            <div class="cc-hint">
              版权行按「© 当前年份 + 版权信息」渲染，年份自动取当年，无需填写
            </div>
          </ElFormItem>

          <ElFormItem label="版权链接" prop="copyrightLink">
            <ElInput
              v-model="state.form.copyrightLink"
              maxlength="100"
              placeholder="https://"
              show-word-limit
            />
          </ElFormItem>

          <ElFormItem label="ICP备案号" prop="icp">
            <ElInput
              v-model="state.form.icp"
              maxlength="100"
              placeholder="如：沪ICP备00000000号"
              show-word-limit
            />
          </ElFormItem>

          <ElFormItem label="ICP网站链接" prop="icpLink">
            <ElInput
              v-model="state.form.icpLink"
              maxlength="100"
              placeholder="https://beian.miit.gov.cn/"
              show-word-limit
            />
          </ElFormItem>

          <template v-if="showLoginFields">
            <ElFormItem label="登录页标题" prop="loginTitle">
              <ElInput
                v-model="state.form.loginTitle"
                maxlength="60"
                placeholder="如：账号登录"
                show-word-limit
              />
            </ElFormItem>

            <ElFormItem label="登录页副标题" prop="loginSubtitle">
              <ElInput
                v-model="state.form.loginSubtitle"
                maxlength="128"
                placeholder="登录页标题下方的说明文案"
                show-word-limit
                type="textarea"
              />
            </ElFormItem>

            <ElFormItem label="登录页图标">
              <div class="cc-image">
                <div class="cc-image__box">
                  <img
                    v-if="state.form.loginIcon"
                    alt="登录页图标"
                    class="cc-image__preview"
                    :src="state.form.loginIcon"
                  />
                  <span v-else class="cc-image__empty">未设置</span>
                </div>
                <div class="cc-image__actions">
                  <ElUpload
                    accept="image/*"
                    action="#"
                    :before-upload="beforeImageUpload"
                    :http-request="handleLoginIconUpload"
                    :show-file-list="false"
                  >
                    <ElButton size="small" type="primary">
                      {{ state.form.loginIcon ? '重新上传' : '上传图片' }}
                    </ElButton>
                  </ElUpload>
                  <ElButton
                    v-if="state.form.loginIcon"
                    size="small"
                    text
                    type="danger"
                    @click="state.form.loginIcon = ''"
                  >
                    清除
                  </ElButton>
                </div>
              </div>
            </ElFormItem>
          </template>

          <ElFormItem>
            <ElButton
              v-access:code="editPermission"
              :loading="saving"
              type="primary"
              @click="submit"
            >
              确认
            </ElButton>
            <ElButton :disabled="saving" @click="reset">重置</ElButton>
          </ElFormItem>
        </ElForm>
      </section>
    </div>
  </div>
</template>

<style scoped>
.copyright-config {
  height: 100%;
}

.cc-panel {
  min-height: 100%;
  padding: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.cc-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 4px;
}

.cc-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.cc-tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.cc-tabs {
  margin-bottom: 4px;
}

.cc-form {
  max-width: 640px;
}

.cc-hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.cc-image {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.cc-image__box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  overflow: hidden;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}

.cc-image__preview {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.cc-image__empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.cc-image__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>
