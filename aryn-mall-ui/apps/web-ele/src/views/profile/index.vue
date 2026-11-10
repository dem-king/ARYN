<script lang="ts" setup>
import type {
  FormInstance,
  FormRules,
  UploadProps,
  UploadRequestOptions,
} from 'element-plus';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { Copyright } from '@vben/layouts';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import dayjs from 'dayjs';
import {
  ElAlert,
  ElAvatar,
  ElButton,
  ElDialog,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElUpload,
} from 'element-plus';

import {
  updateMyPassword,
  updateMyPhone,
  updateMyProfile,
  updateMyUsername,
  uploadMyAvatar,
} from '#/api/upms/user';
import PageSection from '#/components/page/page-section.vue';
import { useAuthStore } from '#/store';
import { validatePassword } from '#/utils/password-policy';

defineOptions({ name: 'UserProfile' });

/** 管理端用户信息接口比通用 BasicUserInfo 多返回昵称、手机号等字段 */
interface AdminProfile {
  avatar?: string;
  createTime?: string;
  email?: string;
  nickname?: string;
  phone?: string;
  type?: string;
  username?: string;
}

const AVATAR_MAX_SIZE_MB = 2;

const userStore = useUserStore();
const authStore = useAuthStore();

const profile = computed<AdminProfile>(
  () => (userStore.userInfo ?? {}) as AdminProfile,
);
const displayName = computed(
  () =>
    profile.value.nickname?.trim() || profile.value.username || '未命名用户',
);
const avatarFallback = computed(() => displayName.value.slice(0, 1));
const createdText = computed(() =>
  profile.value.createTime
    ? dayjs(profile.value.createTime).format('YYYY-MM-DD HH:mm')
    : '-',
);

async function refreshProfile() {
  try {
    await authStore.fetchUserInfo();
  } catch {
    // 资料已经落库，刷新失败保持现状，下次进入页面或重新登录会取到最新值
  }
}

/* ------------------------------- 基本资料 ------------------------------- */

const basicFormRef = ref<FormInstance>();
const basicSaving = ref(false);
const basicForm = reactive({
  email: '',
  nickname: '',
});

watch(
  () => [profile.value.nickname, profile.value.email] as const,
  ([nickname, email]) => {
    basicForm.nickname = nickname ?? '';
    basicForm.email = email ?? '';
  },
  { immediate: true },
);

const basicDirty = computed(
  () =>
    basicForm.nickname !== (profile.value.nickname ?? '') ||
    basicForm.email !== (profile.value.email ?? ''),
);

const basicRules: FormRules = {
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 32, message: '昵称不能超过 32 个字符', trigger: 'blur' },
  ],
};

function resetBasic() {
  basicForm.nickname = profile.value.nickname ?? '';
  basicForm.email = profile.value.email ?? '';
  basicFormRef.value?.clearValidate();
}

function handleSaveBasic() {
  basicFormRef.value?.validate((valid) => {
    if (valid) {
      void submitBasic();
    }
  });
}

async function submitBasic() {
  basicSaving.value = true;
  try {
    await updateMyProfile({
      email: basicForm.email.trim(),
      nickname: basicForm.nickname.trim(),
    });
    await refreshProfile();
    ElMessage.success('个人资料已保存');
  } catch {
    // 错误提示由请求层统一展示
  } finally {
    basicSaving.value = false;
  }
}

/* -------------------------------- 头像 -------------------------------- */

const avatarUploading = ref(false);

const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.error('头像必须是图片文件');
    return false;
  }
  if (rawFile.size / 1024 / 1024 > AVATAR_MAX_SIZE_MB) {
    ElMessage.error(`头像大小不能超过 ${AVATAR_MAX_SIZE_MB}MB`);
    return false;
  }
  return true;
};

async function handleAvatarUpload(options: UploadRequestOptions) {
  avatarUploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', options.file);
    await uploadMyAvatar(formData);
    await refreshProfile();
    ElMessage.success('头像已更新');
  } catch {
    // 错误提示由请求层统一展示
  } finally {
    avatarUploading.value = false;
  }
}

/* ----------------------------- 修改登录账号 ----------------------------- */

const usernameDialogOpen = ref(false);
const usernameSaving = ref(false);
const usernameFormRef = ref<FormInstance>();
const usernameForm = reactive({
  password: '',
  username: '',
});

const usernameRules: FormRules = {
  password: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  username: [
    { required: true, message: '请输入新的登录账号', trigger: 'blur' },
    {
      message: '登录账号需为 3-32 个字符，且不能包含空格',
      pattern: /^\S{3,32}$/,
      trigger: 'blur',
    },
  ],
};

function openUsernameDialog() {
  usernameForm.username = '';
  usernameForm.password = '';
  usernameDialogOpen.value = true;
  void nextTick(() => usernameFormRef.value?.clearValidate());
}

function submitUsername() {
  usernameFormRef.value?.validate((valid) => {
    if (valid) {
      void saveUsername();
    }
  });
}

async function saveUsername() {
  usernameSaving.value = true;
  try {
    await updateMyUsername({
      password: usernameForm.password,
      username: usernameForm.username.trim(),
    });
    await refreshProfile();
    usernameDialogOpen.value = false;
    ElMessage.success('登录账号已修改，下次登录请使用新账号');
  } catch {
    // 错误提示由请求层统一展示
  } finally {
    usernameSaving.value = false;
  }
}

/* ------------------------------ 修改手机号 ------------------------------ */

const phoneDialogOpen = ref(false);
const phoneSaving = ref(false);
const phoneFormRef = ref<FormInstance>();
const phoneForm = reactive({
  password: '',
  phone: '',
});

const phoneRules: FormRules = {
  phone: [
    { required: true, message: '请输入新手机号', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号',
      trigger: 'blur',
    },
  ],
  password: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
};

/** 重置弹窗表单（关闭弹窗或重新打开时调用） */
function resetPhoneDialog() {
  phoneForm.phone = '';
  phoneForm.password = '';
  phoneFormRef.value?.clearValidate();
}

function openPhoneDialog() {
  resetPhoneDialog();
  phoneDialogOpen.value = true;
  void nextTick(() => phoneFormRef.value?.clearValidate());
}

function submitPhoneDialog() {
  phoneFormRef.value?.validate((valid) => {
    if (valid) {
      void savePhone();
    }
  });
}

async function savePhone() {
  if (phoneSaving.value) {
    return;
  }
  phoneSaving.value = true;
  try {
    await updateMyPhone({
      password: phoneForm.password,
      phone: phoneForm.phone.trim(),
    });
    phoneDialogOpen.value = false;
    await refreshProfile();
    ElMessage.success('手机号已修改，请使用新手机号登录/接收通知');
  } catch {
    // 错误提示由请求层统一展示，保留弹窗便于修正
  } finally {
    phoneSaving.value = false;
  }
}

/* ------------------------------ 修改密码 ------------------------------ */

const passwordDialogOpen = ref(false);
const passwordSaving = ref(false);
const passwordFormRef = ref<FormInstance>();
const passwordForm = reactive({
  checkPassword: '',
  newPassword: '',
  password: '',
});

const validateNewPassword = (
  _rule: any,
  value: any,
  callback: (error?: Error) => void,
) => {
  const errorMessage = validatePassword(`${value ?? ''}`);
  if (errorMessage) {
    callback(new Error(errorMessage));
    return;
  }
  if (passwordForm.checkPassword !== '') {
    void passwordFormRef.value?.validateField('checkPassword');
  }
  callback();
};

const validateConfirmPassword = (
  _rule: any,
  value: any,
  callback: (error?: Error) => void,
) => {
  if (!value) {
    callback(new Error('请再次输入新密码'));
    return;
  }
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'));
    return;
  }
  callback();
};

const passwordRules: FormRules = {
  checkPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
  newPassword: [{ validator: validateNewPassword, trigger: 'blur' }],
  password: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
};

function openPasswordDialog() {
  passwordForm.password = '';
  passwordForm.newPassword = '';
  passwordForm.checkPassword = '';
  passwordDialogOpen.value = true;
  void nextTick(() => passwordFormRef.value?.clearValidate());
}

function submitPassword() {
  passwordFormRef.value?.validate((valid) => {
    if (valid) {
      void savePassword();
    }
  });
}

async function savePassword() {
  passwordSaving.value = true;
  try {
    await updateMyPassword({
      checkPassword: passwordForm.checkPassword,
      newPassword: passwordForm.newPassword,
      password: passwordForm.password,
    });
    passwordDialogOpen.value = false;
    ElMessage.success('密码已修改，请使用新密码重新登录');
    // 改密成功后主动退出：当前会话保留在服务端，退出接口可正常执行，
    // 避免旧 token 先失效导致退出请求 401。
    await authStore.logout(false);
  } catch {
    // 错误提示由请求层统一展示
  } finally {
    passwordSaving.value = false;
  }
}
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view profile-page">
      <div class="profile-page__inner">
        <!-- 左侧概要卡 -->
        <aside class="profile-summary">
          <ElUpload
            action="#"
            accept=".jpg,.jpeg,.png,.gif,.bmp,.webp"
            class="profile-summary__avatar-upload"
            :before-upload="beforeAvatarUpload"
            :http-request="handleAvatarUpload"
            :show-file-list="false"
          >
            <div v-loading="avatarUploading" class="profile-summary__avatar">
              <ElAvatar :size="88" :src="profile.avatar || undefined">
                {{ avatarFallback }}
              </ElAvatar>
              <span class="profile-summary__avatar-mask">更换头像</span>
            </div>
          </ElUpload>
          <div class="profile-summary__name">{{ displayName }}</div>
          <div class="profile-summary__account">
            @{{ profile.username || '-' }}
          </div>
          <p class="profile-summary__upload-tip">
            点击头像可更换，支持 JPG / PNG / WEBP，不超过
            {{ AVATAR_MAX_SIZE_MB }}MB
          </p>
          <ElDivider />
          <dl class="profile-summary__meta">
            <div class="profile-summary__meta-row">
              <dt>手机号</dt>
              <dd>{{ profile.phone || '未绑定' }}</dd>
            </div>
            <div class="profile-summary__meta-row">
              <dt>账号创建</dt>
              <dd>{{ createdText }}</dd>
            </div>
          </dl>
        </aside>

        <!-- 右侧资料与安全设置 -->
        <div class="profile-main">
          <PageSection
            title="基本资料"
            description="头像、昵称与邮箱会展示在协作和操作记录中"
          >
            <ElForm
              ref="basicFormRef"
              class="profile-form"
              label-width="88px"
              :model="basicForm"
              :rules="basicRules"
            >
              <ElFormItem label="昵称" prop="nickname">
                <ElInput
                  v-model="basicForm.nickname"
                  maxlength="32"
                  placeholder="请输入昵称"
                  show-word-limit
                />
              </ElFormItem>
              <ElFormItem label="邮箱" prop="email">
                <ElInput
                  v-model="basicForm.email"
                  maxlength="128"
                  placeholder="用于接收系统通知，可留空"
                />
              </ElFormItem>
              <ElFormItem label="手机号">
                <ElInput
                  disabled
                  :model-value="profile.phone || ''"
                  placeholder="未绑定"
                />
                <div class="profile-form__tip">
                  手机号用于登录与安全校验，可在下方「账号安全」中修改
                </div>
              </ElFormItem>
              <ElFormItem>
                <div class="hx-form-footer">
                  <ElButton :disabled="!basicDirty" @click="resetBasic">
                    重置
                  </ElButton>
                  <ElButton
                    :disabled="!basicDirty"
                    :loading="basicSaving"
                    type="primary"
                    @click="handleSaveBasic"
                  >
                    保存修改
                  </ElButton>
                </div>
              </ElFormItem>
            </ElForm>
          </PageSection>

          <PageSection
            title="账号安全"
            description="手机号、登录账号与密码的修改需要校验当前密码"
          >
            <div class="security-row">
              <div class="security-row__info">
                <span class="security-row__label">手机号</span>
                <span class="security-row__value">
                  {{ profile.phone || '未绑定' }}
                </span>
                <span class="security-row__tip">
                  手机号用于登录与安全校验，修改后旧手机号立即不可登录
                </span>
              </div>
              <ElButton plain @click="openPhoneDialog">修改</ElButton>
            </div>
            <div class="security-row">
              <div class="security-row__info">
                <span class="security-row__label">登录账号</span>
                <span class="security-row__value">
                  {{ profile.username || '-' }}
                </span>
                <span class="security-row__tip">
                  修改后原账号立即失效，下次请使用新账号登录
                </span>
              </div>
              <ElButton plain @click="openUsernameDialog">修改</ElButton>
            </div>
            <div class="security-row">
              <div class="security-row__info">
                <span class="security-row__label">登录密码</span>
                <span class="security-row__value">已设置</span>
                <span class="security-row__tip">
                  建议定期更换，不要与其他网站使用相同密码
                </span>
              </div>
              <ElButton plain @click="openPasswordDialog">修改</ElButton>
            </div>
          </PageSection>
        </div>
      </div>

      <footer v-if="preferences.copyright.enable" class="profile-page__footer">
        <Copyright v-bind="preferences.copyright" />
      </footer>
    </div>

    <!-- 修改登录账号 -->
    <ElDialog
      v-model="usernameDialogOpen"
      title="修改登录账号"
      width="440px"
      :close-on-click-modal="false"
    >
      <ElAlert
        show-icon
        :closable="false"
        title="登录账号是登录系统的唯一标识，修改后原账号立即失效。"
        type="warning"
      />
      <ElForm
        ref="usernameFormRef"
        class="dialog-form"
        label-width="88px"
        :model="usernameForm"
        :rules="usernameRules"
      >
        <ElFormItem label="新登录账号" prop="username">
          <ElInput
            v-model="usernameForm.username"
            maxlength="32"
            placeholder="请输入新的登录账号"
          />
        </ElFormItem>
        <ElFormItem label="当前密码" prop="password">
          <ElInput
            v-model="usernameForm.password"
            autocomplete="off"
            placeholder="请输入当前密码"
            show-password
            type="password"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="usernameDialogOpen = false">取消</ElButton>
        <ElButton
          :loading="usernameSaving"
          type="primary"
          @click="submitUsername"
        >
          确认修改
        </ElButton>
      </template>
    </ElDialog>

    <!-- 修改手机号 -->
    <ElDialog
      v-model="phoneDialogOpen"
      title="修改手机号"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetPhoneDialog"
    >
      <ElAlert
        show-icon
        :closable="false"
        title="手机号用于登录与安全校验，修改后旧手机号立即不可登录。"
        type="warning"
      />
      <ElForm
        ref="phoneFormRef"
        class="dialog-form"
        label-width="88px"
        :model="phoneForm"
        :rules="phoneRules"
      >
        <ElFormItem label="新手机号" prop="phone">
          <ElInput
            v-model="phoneForm.phone"
            maxlength="11"
            placeholder="请输入 11 位手机号"
          />
        </ElFormItem>
        <ElFormItem label="当前密码" prop="password">
          <ElInput
            v-model="phoneForm.password"
            autocomplete="off"
            placeholder="请输入当前密码"
            show-password
            type="password"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="phoneDialogOpen = false">取消</ElButton>
        <ElButton
          :loading="phoneSaving"
          type="primary"
          @click="submitPhoneDialog"
        >
          确认修改
        </ElButton>
      </template>
    </ElDialog>

    <!-- 修改密码 -->
    <ElDialog
      v-model="passwordDialogOpen"
      title="修改密码"
      width="440px"
      :close-on-click-modal="false"
    >
      <ElAlert
        show-icon
        :closable="false"
        title="修改成功后需要退出并使用新密码重新登录，请提前保存正在编辑的内容。"
        type="warning"
      />
      <ElForm
        ref="passwordFormRef"
        class="dialog-form"
        label-width="88px"
        :model="passwordForm"
        :rules="passwordRules"
        status-icon
      >
        <ElFormItem label="当前密码" prop="password">
          <ElInput
            v-model="passwordForm.password"
            autocomplete="off"
            placeholder="请输入当前密码"
            show-password
            type="password"
          />
        </ElFormItem>
        <ElFormItem label="新密码" prop="newPassword">
          <ElInput
            v-model="passwordForm.newPassword"
            autocomplete="off"
            placeholder="8-64 位，需同时包含字母和数字"
            show-password
            type="password"
          />
        </ElFormItem>
        <ElFormItem label="确认密码" prop="checkPassword">
          <ElInput
            v-model="passwordForm.checkPassword"
            autocomplete="off"
            placeholder="请再次输入新密码"
            show-password
            type="password"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="passwordDialogOpen = false">取消</ElButton>
        <ElButton
          :loading="passwordSaving"
          type="primary"
          @click="submitPassword"
        >
          确认修改
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style lang="scss" scoped>
@media (max-width: 900px) {
  .profile-page__inner {
    grid-template-columns: minmax(0, 1fr);
  }
}

.profile-page__inner {
  display: grid;
  flex: 1;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 14px;
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
}

/* 版权行贴在个人信息页底部；内容较短时由 inner 的 flex 撑开把页脚压到底部 */
.profile-page__footer {
  width: 100%;
  max-width: 1080px;
  padding-top: 16px;
  margin: 24px auto 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.profile-summary {
  height: fit-content;
  padding: 28px 20px 20px;
  text-align: center;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.profile-summary__avatar-upload {
  display: block;
  width: 88px;
  margin: 0 auto;
}

.profile-summary__avatar {
  position: relative;
  width: 88px;
  height: 88px;
  overflow: hidden;
  cursor: pointer;
  border-radius: 50%;
}

.profile-summary__avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background: rgb(0 0 0 / 45%);
  opacity: 0;
  transition: opacity 0.2s;
}

.profile-summary__avatar:hover .profile-summary__avatar-mask {
  opacity: 1;
}

.profile-summary__name {
  margin-top: 14px;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.profile-summary__account {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
}

.profile-summary__upload-tip {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.profile-summary__meta {
  margin: 4px 0 0;
}

.profile-summary__meta-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
}

.profile-summary__meta-row + .profile-summary__meta-row {
  border-top: 1px dashed var(--el-border-color-lighter);
}

.profile-summary__meta-row dt {
  color: var(--el-text-color-secondary);
}

.profile-summary__meta-row dd {
  margin: 0;
  color: var(--el-text-color-primary);
}

.profile-form {
  max-width: 560px;
}

.profile-form__tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.security-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
}

.security-row:first-child {
  padding-top: 0;
}

.security-row + .security-row {
  border-top: 1px solid var(--el-border-color-lighter);
}

.security-row__info {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  min-width: 0;
}

.security-row__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.security-row__value {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.security-row__tip {
  flex-basis: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dialog-form {
  margin-top: 16px;
}
</style>
