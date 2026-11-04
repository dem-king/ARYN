<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { computed, defineAsyncComponent, nextTick, reactive, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElTag,
  ElTreeSelect,
} from 'element-plus';

import { getTreeList } from '#/api/upms/dept';
import { getList as getRoleList } from '#/api/upms/sys-role';
import {
  addObj,
  editObj,
  getById,
  getPhone,
  updateUserPhone,
} from '#/api/upms/user';
import { useDict } from '#/utils/dict';
import { validatePassword } from '#/utils/password-policy';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
/**
 * 受保护配送资格角色编码：只能由配送员管理开通/停用，
 * 员工账号表单不展示复选框，编辑提交时由后端保留原有关联
 */
const PROTECTED_DELIVERY_ROLE_CODE = 'delivery_staff';
interface UserSate {
  rules: object;
  form: {
    avatar: string;
    deptId: string;
    id: string;
    nickname: string;
    password: string;
    phone: string;
    roles: string[];
    shopName: string;
    status: string;
    type: string;
    username: string;
  };
  roleList: any;
  deptList: any;
}
/**
 * 密码校验：新增员工时必填且必须满足后端同款强度策略；
 * 编辑态后端详情不再返回密码 hash，密码输入框禁用且为空，
 * 这里直接放行，避免编辑弹窗被密码规则卡死无法提交。
 */
function validatePasswordRule(
  _rule: any,
  value: any,
  callback: (error?: Error) => void,
) {
  if (state.form.id) {
    callback();
    return;
  }
  const errorMessage = validatePassword(`${value ?? ''}`);
  if (errorMessage) {
    callback(new Error(errorMessage));
    return;
  }
  callback();
}

// 字典
const { status } = useDict('status');
const state = reactive<UserSate>({
  form: {
    id: '',
    password: '',
    deptId: '',
    username: '',
    phone: '',
    nickname: '',
    avatar: '',
    roles: [],
    shopName: '',
    type: '0',
    status: '0',
  },
  rules: {
    nickname: [
      {
        required: true,
        message: '请输入用户昵称',
        trigger: 'change',
      },
    ],
    phone: [
      {
        required: true,
        message: '请输入手机号码',
        trigger: 'change',
      },
    ],
    username: [
      {
        required: true,
        message: '请输入用户名',
        trigger: 'change',
      },
    ],
    password: [
      {
        validator: validatePasswordRule,
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
    roles: [
      {
        required: true,
        message: '请选择角色',
        trigger: 'change',
      },
    ],
    deptId: [
      {
        required: true,
        message: '请选择部门',
        trigger: 'change',
      },
    ],
    avatar: [
      {
        required: true,
        message: '请上传头像',
        trigger: 'change',
      },
    ],
  },
  roleList: [],
  deptList: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

/**
 * 编辑态临时展示完整手机号：详情接口只返回脱敏号码，
 * 点「查看」单独取一次；仅影响本地显示，编辑提交时后端忽略 phone。
 */
const fullPhone = ref('');
const loadingPhone = ref(false);
const phoneDisplay = computed({
  get: () => fullPhone.value || state.form.phone,
  set: (value: string) => {
    state.form.phone = value;
  },
});
const togglePhone = () => {
  if (!state.form.id) {
    return;
  }
  if (fullPhone.value) {
    fullPhone.value = '';
    return;
  }
  loadingPhone.value = true;
  getPhone(state.form.id)
    .then((phone) => {
      fullPhone.value = phone || '';
    })
    .catch(() => {
      fullPhone.value = '';
    })
    .finally(() => {
      loadingPhone.value = false;
    });
};

/* ----------------------------- 修改员工手机号 ----------------------------- */

/** 大陆 11 位手机号格式 */
const PHONE_PATTERN = /^1[3-9]\d{9}$/;

const phoneDialog = ref(false);
const phoneSaving = ref(false);
const phoneFormRef = ref<FormInstance>();
const phoneForm = reactive({
  confirmPhone: '',
  phone: '',
});

/**
 * 确认号码校验：非空且必须与新手机号一致
 */
function validateConfirmPhone(
  _rule: any,
  value: any,
  callback: (error?: Error) => void,
) {
  if (!value) {
    callback(new Error('请再次输入新手机号'));
    return;
  }
  if (value !== phoneForm.phone) {
    callback(new Error('两次输入的新手机号不一致'));
    return;
  }
  callback();
}

const phoneRules: object = {
  phone: [
    { required: true, message: '请输入新手机号', trigger: 'blur' },
    { pattern: PHONE_PATTERN, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  confirmPhone: [{ validator: validateConfirmPhone, trigger: 'blur' }],
};

/**
 * 重置子弹窗表单与校验状态
 */
function resetPhoneForm() {
  phoneForm.phone = '';
  phoneForm.confirmPhone = '';
  phoneFormRef.value?.clearValidate();
}

function openPhoneDialog() {
  resetPhoneForm();
  phoneDialog.value = true;
  void nextTick(() => phoneFormRef.value?.clearValidate());
}

function closePhoneDialog() {
  phoneDialog.value = false;
  resetPhoneForm();
}

/**
 * 手机号由独立接口修改：成功后就地回填脱敏号并通知列表刷新，
 * 不整体重载详情，避免覆盖管理员在本弹窗内尚未保存的其他字段修改。
 * 失败提示由请求层统一展示，弹窗保持打开便于修正。
 */
async function submitPhone() {
  const formEl = phoneFormRef.value;
  if (!formEl || phoneSaving.value) {
    return;
  }
  const valid = await formEl.validate().catch(() => false);
  if (!valid) {
    return;
  }
  const newPhone = phoneForm.phone.trim();
  phoneSaving.value = true;
  try {
    await updateUserPhone(state.form.id, newPhone);
    // 与后端 @Desensitization 口径一致：前 3 位 + **** + 后 4 位
    state.form.phone = newPhone.replace(/^(1[3-9]\d)\d{4}(\d{4})$/, '$1****$2');
    // 旧号的「查看」结果立即失效，按钮回到「查看」态
    fullPhone.value = '';
    closePhoneDialog();
    emit('initPage');
    ElMessage.success('手机号修改成功');
  } catch {
    // 错误提示由请求层统一展示，保留弹窗便于修正
  } finally {
    phoneSaving.value = false;
  }
}

/**
 * 受保护角色ID集合（存在重复编码角色时全部纳入保护）
 */
const protectedRoleIds = computed(() =>
  (state.roleList || [])
    .filter((item: any) => item.roleCode === PROTECTED_DELIVERY_ROLE_CODE)
    .map((item: any) => item.id),
);

/**
 * 是否已具备配送资格（后端返回受保护角色时只读展示）
 */
const hasDeliveryQualification = computed(() =>
  (state.form.roles || []).some((roleId) =>
    protectedRoleIds.value.includes(roleId),
  ),
);

/**
 * 后台角色复选框列表：过滤受保护的配送资格角色
 */
const editableRoleList = computed(() =>
  (state.roleList || []).filter(
    (item: any) => item.roleCode !== PROTECTED_DELIVERY_ROLE_CODE,
  ),
);

const defaultProps = {
  children: 'children',
  label: 'name',
};
const initForm = (row: any | undefined) => {
  dialog.value = true;
  if (row && row.id) {
    getDetail(row.id);
  }

  handleRoleList();
  handleDeptList();
};
const getDetail = (id: string) => {
  loading.value = true;
  fullPhone.value = '';
  getById(id).then((response) => {
    state.form = response;
    // 详情接口只返回不含密码的 VO；显式清空，避免上一次表单残留值被当成新密码提交
    state.form.password = '';
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
  fullPhone.value = '';
  loading.value = false;
  dialog.value = false;
  // 主弹窗关闭时一并收起手机号子弹窗，避免 append-to-body 的弹窗残留在页面上
  closePhoneDialog();
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
      // 受保护角色不在提交列表中：授予/回收只由配送员管理负责，后端编辑时会保留原有关联
      state.form.roles = (state.form.roles || []).filter(
        (roleId) => !protectedRoleIds.value.includes(roleId),
      );
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
  state.form.type = '0';
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
  // 手机号走独立修改接口，通用编辑不提交 phone（详情返回的也是脱敏号）
  const payload: Record<string, any> = { ...state.form };
  delete payload.phone;
  editObj(payload)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 查询全部角色
 */
const handleRoleList = () => {
  getRoleList({}).then((response) => {
    state.roleList = response;
  });
};

/**
 * 查询全部部门
 */
const handleDeptList = () => {
  getTreeList()
    .then((response) => {
      state.deptList = response;
    })
    .catch(() => {});
};
defineExpose({
  initForm,
});
</script>
<template>
  <div class="system-add-user-container">
    <ElDialog
      v-model="dialog"
      :title="state.form.id ? '修改员工账号' : '新增员工账号'"
      width="60%"
      :before-close="handleClose"
    >
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="120px"
        :rules="state.rules"
      >
        <ElRow>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="员工昵称" prop="nickname">
              <ElInput v-model="state.form.nickname" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="手机号码" prop="phone">
              <div class="phone-field">
                <ElInput
                  v-model="phoneDisplay"
                  maxlength="11"
                  :disabled="!!state.form.id"
                  show-word-limit
                >
                  <template v-if="state.form.id" #append>
                    <ElButton
                      v-access:code="'upms:sysuser:phone'"
                      :loading="loadingPhone"
                      link
                      type="primary"
                      @click="togglePhone"
                    >
                      {{ fullPhone ? '隐藏' : '查看' }}
                    </ElButton>
                  </template>
                </ElInput>
                <ElButton
                  v-if="state.form.id"
                  v-access:code="'upms:sysuser:phone:edit'"
                  class="phone-field__edit"
                  link
                  type="primary"
                  @click="openPhoneDialog"
                >
                  修改
                </ElButton>
              </div>
            </ElFormItem>
          </ElCol>
        </ElRow>
        <ElRow>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="登录用户名" prop="username">
              <ElInput
                v-model="state.form.username"
                :disabled="!!state.form.id"
              />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="密码" prop="password">
              <ElInput
                v-model="state.form.password"
                type="password"
                placeholder="8-64位，需同时包含字母和数字"
                :disabled="!!state.form.id"
              />
            </ElFormItem>
          </ElCol>
        </ElRow>
        <ElRow>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="部门" prop="deptId">
              <ElTreeSelect
                style="width: 100%"
                v-model="state.form.deptId"
                :data="state.deptList"
                check-strictly
                :props="defaultProps"
                :render-after-expand="false"
                node-key="id"
              />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12" class="mb20">
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
          </ElCol>
        </ElRow>
        <ElFormItem label="后台角色" prop="roles">
          <div class="role-field">
            <ElCheckboxGroup v-model="state.form.roles">
              <ElCheckbox
                v-for="item in editableRoleList"
                :key="item.id"
                :value="item.id"
              >
                {{ item.roleName }}
              </ElCheckbox>
            </ElCheckboxGroup>
            <ElTag v-if="hasDeliveryQualification" type="success" size="small">
              已具备配送资格（配送员角色，仅配送员管理可变更）
            </ElTag>
            <div class="role-tip">
              配送资格请在 配送管理 &gt; 配送员管理 中开通，此处只管理后台角色
            </div>
          </div>
        </ElFormItem>
        <ElFormItem label="头像" prop="avatar">
          <SelectMaterial
            v-model="state.form.avatar"
            :can-choose-images-num="1"
          />
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

    <!-- 修改手机号：独立子弹窗，append-to-body 保证层级在主弹窗之上 -->
    <ElDialog
      v-model="phoneDialog"
      append-to-body
      title="修改手机号"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetPhoneForm"
    >
      <ElAlert
        show-icon
        :closable="false"
        type="warning"
        title="修改后旧手机号将无法登录；如该员工已开通配送资格，配送员资料中的联系电话会自动同步。"
      />
      <ElForm
        ref="phoneFormRef"
        class="phone-dialog-form"
        label-width="108px"
        :model="phoneForm"
        :rules="phoneRules"
      >
        <ElFormItem label="当前手机号">
          <ElInput
            :model-value="state.form.phone || '未绑定'"
            disabled
            placeholder="未绑定"
          />
        </ElFormItem>
        <ElFormItem label="新手机号" prop="phone">
          <ElInput
            v-model="phoneForm.phone"
            maxlength="11"
            placeholder="请输入 11 位手机号"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="确认新手机号" prop="confirmPhone">
          <ElInput
            v-model="phoneForm.confirmPhone"
            maxlength="11"
            placeholder="请再次输入新手机号"
            show-word-limit
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="closePhoneDialog">取消</ElButton>
        <ElButton type="primary" :loading="phoneSaving" @click="submitPhone">
          确认修改
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>
<style scoped>
.avatar-uploader .avatar {
  display: block;
  width: 140px;
  height: 140px;
}

.role-field {
  width: 100%;
}

.role-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.phone-dialog-form {
  margin-top: 16px;
}

/* 手机号输入框（含「查看」后缀）与「修改」按钮同一行 */
.phone-field {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.phone-field :deep(.el-input) {
  flex: 1;
}

.phone-field__edit {
  flex: 0 0 auto;
}
</style>
